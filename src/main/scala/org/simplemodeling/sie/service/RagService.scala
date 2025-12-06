  package org.simplemodeling.sie.service

import org.simplemodeling.sie.fuseki.FusekiClient
import org.simplemodeling.sie.chroma.ChromaClient
import org.simplemodeling.sie.embedding.EmbeddingEngine
import org.simplemodeling.sie.init.IndexInitializer
import io.circe.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.simplemodeling.sie.concept.ConceptMatcher

/*
 * Integrated RagService
 * - Structured results (ConceptHit, PassageHit)
 * - Hybrid search + fallback
 * - English comments only
 * 
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
final case class ConceptHit(
  uri: String,
  label: String,
  lang: Option[String]
) derives Encoder

final case class PassageHit(
  id: String,
  text: String,
  score: Option[Double]
) derives Encoder

final case class RagResult(
  concepts: List[ConceptHit],
  passages: List[PassageHit],
  graph: GraphResult
) derives Encoder

final case class GraphNode(
  id: String,
  label: String,
  kind: String
) derives Encoder

final case class GraphEdge(
  source: String,
  target: String,
  relation: String
) derives Encoder

final case class GraphResult(
  nodes: List[GraphNode],
  edges: List[GraphEdge]
) derives Encoder

final case class ConceptExplanation(
  uri: String,
  label: Option[String],
  description: Option[String],
  graph: GraphResult
) derives Encoder

final case class GraphDebug(
  uri: String,
  nodeCount: Int,
  edgeCount: Int
) derives Encoder

class RagService(
  val context: RagService.Context,
  fuseki: FusekiClient,
  chroma: ChromaClient,
  embedding: EmbeddingEngine,
  conceptMatcher: ConceptMatcher
) {
  /** Main IO-based entry point */
  def runIO(query: String): IO[RagResult] = {
    val conceptMatchesIO: IO[List[org.simplemodeling.sie.concept.ConceptHit]] =
      conceptMatcher.matchConcepts(query)

    val conceptsIO: IO[List[ConceptHit]] =
      conceptMatchesIO.map { hits =>
        val locale = context.defaultLocale
        hits.map { h =>
          val localized =
            try h.entry.labelFor(locale) catch { case _: Throwable => h.canonicalLabel }

          ConceptHit(
            uri  = h.uri,
            label = localized,
            lang  = Some(locale.getLanguage)
          )
        }
      }

    val passagesIO: IO[List[PassageHit]] =
      IO {
        if (embedding.mode.toString.equalsIgnoreCase("none")) {
          chroma
            .search("simplemodeling", query, 5)
            .fold(_ => Nil, _.map(enrich))
        } else {
          val hybrid  = chroma.hybridSearch("simplemodeling", query, 5)
          val fallback = chroma.search("simplemodeling", query, 5)

          hybrid
            .orElse(fallback)
            .fold(_ => Nil, _.map(enrich))
        }
      }.handleError(_ => Nil)

    for {
      concepts <- conceptsIO
      passages <- passagesIO
      graph    <- extractGraph(concepts, passages)
      } yield RagResult(concepts, passages, graph)
  }

  /** Synchronous wrapper */
  def run(query: String): RagResult =
    runIO(query).unsafeRunSync()

  /** Initialize Chroma index */
  def initChroma(): Json =
    try {
      IndexInitializer.run(fuseki, chroma, embedding)
      Json.obj("status" -> Json.fromString("ok"))
    } catch {
      case e: Throwable =>
        Json.obj("error" -> Json.fromString(e.toString))
    }

  /** Health reporting */
  def health(): IO[Json] = {
    for {
      // Embedding check
      emb <- IO {
        if (embedding.mode.toString.equalsIgnoreCase("none")) {
          Json.obj(
            "enabled"   -> Json.fromBoolean(false),
            "reachable" -> Json.fromBoolean(true)
          )
        } else {
          try {
            val r = embedding.embed(List("health-check")).unsafeRunSync()
            if (r.exists(_.nonEmpty))
              Json.obj(
                "enabled"   -> Json.fromBoolean(true),
                "reachable" -> Json.fromBoolean(true)
              )
            else
              Json.obj(
                "enabled"   -> Json.fromBoolean(true),
                "reachable" -> Json.fromBoolean(false),
                "error"     -> Json.fromString("empty embedding")
              )
          } catch {
            case e: Throwable =>
              Json.obj(
                "enabled"   -> Json.fromBoolean(true),
                "reachable" -> Json.fromBoolean(false),
                "error"     -> Json.fromString(e.getMessage)
              )
          }
        }
      }

      // Chroma check
      chr <- IO {
        chroma.collectionExists("simplemodeling") match {
          case Right(exists) =>
            Json.obj(
              "reachable"        -> Json.fromBoolean(true),
              "collectionExists" -> Json.fromBoolean(exists)
            )
          case Left(err) =>
            Json.obj(
              "reachable" -> Json.fromBoolean(false),
              "error"     -> Json.fromString(err)
            )
        }
      }

      // Fuseki check
      fus <- IO.pure(()) *> fuseki.searchConceptsJson("health-check").attempt.map {
        case Right(_) =>
          Json.obj(
            "reachable" -> Json.fromBoolean(true)
          )
        case Left(e) =>
          Json.obj(
            "reachable" -> Json.fromBoolean(false),
            "error"     -> Json.fromString(e.getMessage)
          )
      }

    } yield {
      val okEmbedding = emb.hcursor.get[Boolean]("reachable").getOrElse(false)
      val okChroma    = chr.hcursor.get[Boolean]("reachable").getOrElse(false)
      val okFuseki    = fus.hcursor.get[Boolean]("reachable").getOrElse(false)

      val status = if (okEmbedding && okChroma && okFuseki) "ok" else "degraded"

      Json.obj(
        "status"   -> Json.fromString(status),
        "embedding"-> emb,
        "chroma"   -> chr,
        "fuseki"   -> fus
      )
    }
  }

/** Explain a concept with a specified locale.
    * Locale-aware description generation is applied.
    * If no description is found in ConceptEntry nor Fuseki,
    * a localized fallback message is returned from ResourceBundle.
    */
  def explainConcept(uri: String, locale: java.util.Locale): IO[ConceptExplanation] = {
    for {
      entryOpt <- conceptMatcher.lookupByUri(uri)

      metaRows <- entryOpt match {
        case Some(_) => IO.pure(Nil)
        case None    => fuseki.queryFlat(buildConceptExplainQuery(uri))
      }

      graph <- getNeighbors(uri, locale)

    } yield {
      // Label: currently dictionary canonicalLabel or Fuseki label.
      // (Future work may make this label itself locale-aware.)
      val labelFromEntry: Option[String] =
        entryOpt.map(_.labelFor(locale))

      val labelFromFuseki: Option[String] =
        metaRows.flatMap(_.get("label")).headOption

      val finalLabel: String =
        labelFromEntry
          .orElse(labelFromFuseki)
          .getOrElse(uri)

      // Localized description with fallback message from ResourceBundle
      val bundle = context.bundleFor(locale)

      val descFromEntry: Option[String] =
        entryOpt
          .map { e =>
            generateExplanationLocalized(e, locale)
          }
          .filter(_.nonEmpty)

      val descFromFuseki: Option[String] =
        metaRows.flatMap { r =>
          r.get("definition").orElse(r.get("comment"))
        }.headOption

      val finalDesc: Option[String] =
        descFromEntry
          .orElse(descFromFuseki)
          .orElse(Some(bundle.getString("noDescription")))

      ConceptExplanation(
        uri         = uri,
        label       = Some(finalLabel),
        description = finalDesc,
        graph       = graph
      )
    }
  }

  /** Backward-compatible API (default locale = English). */
  def explainConcept(uri: String): IO[ConceptExplanation] = {
    explainConcept(uri, java.util.Locale.ENGLISH)
  }

  /** Generate a locale-aware explanation string from ConceptEntry.
    * Fallback priority:
    *   1. exact locale match
    *   2. English
    *   3. Japanese
    *   4. any available locale
    * The output also includes synonyms, categories, and relational structure.
    */
  private def generateExplanationLocalized(
    entry: org.simplemodeling.sie.concept.ConceptEntry,
    locale: java.util.Locale
  ): String = {

    val bundle = context.bundleFor(locale)

    def resolve(map: Map[java.util.Locale, String]): Option[String] = {
      val primary = map.get(locale)
      if (primary.isDefined) return primary

      val en = map.get(java.util.Locale.ENGLISH)
      if (en.isDefined) return en

      val ja = map.get(java.util.Locale.JAPANESE)
      if (ja.isDefined) return ja

      map.values.headOption
    }

    val baseText =
      resolve(entry.definitions)
        .orElse(resolve(entry.remarks))
        .getOrElse(bundle.getString("noDescription"))

    val sb = new StringBuilder()
    sb.append(baseText.trim)

    // synonyms
    if (entry.synonyms.nonEmpty) {
      val syns = entry.synonyms.toList
      val joined =
        if (syns.size == 1) syns.head
        else syns.init.mkString(", ") + " " + bundle.getString("and") + " " + syns.last

      sb.append("\n\n")
      sb.append(
        bundle.getString("sentence.synonyms.prefix")
          .replace("{terms}", joined)
      )
    }

    // categories
    if (entry.categories.nonEmpty) {
      val cats = entry.categories.toList
      val joined =
        if (cats.size == 1) cats.head
        else cats.init.mkString(", ") + " " + bundle.getString("and") + " " + cats.last

      sb.append("\n\n")
      sb.append(
        bundle.getString("sentence.categories")
          .replace("{cats}", joined)
      )
    }

    // parents
    if (entry.parents.nonEmpty) {
      val ps = entry.parents.toList
      val joined =
        if (ps.size == 1) ps.head
        else ps.init.mkString(", ") + " " + bundle.getString("and") + " " + ps.last

      sb.append("\n\n")
      sb.append(
        bundle.getString("sentence.parents")
          .replace("{terms}", joined)
      )
    }

    // children
    if (entry.children.nonEmpty) {
      val cs = entry.children.toList
      val joined =
        if (cs.size == 1) cs.head
        else cs.init.mkString(", ") + " " + bundle.getString("and") + " " + cs.last

      sb.append("\n\n")
      sb.append(
        bundle.getString("sentence.children")
          .replace("{terms}", joined)
      )
    }

    // related
    if (entry.related.nonEmpty) {
      val rs = entry.related.toList
      val joined =
        if (rs.size == 1) rs.head
        else rs.init.mkString(", ") + " " + bundle.getString("and") + " " + rs.last

      sb.append("\n\n")
      sb.append(
        bundle.getString("sentence.related")
          .replace("{terms}", joined)
      )
    }

    sb.toString
  }

  /** Produce a locale-aware label for a concept.
    * Priority:
    *  1. definitions(locale)
    *  2. remarks(locale)
    *  3. synonyms
    *  4. canonicalLabel
    *  5. URI
    */
  private def localizedLabel(
    entry: org.simplemodeling.sie.concept.ConceptEntry,
    uri: String,
    locale: java.util.Locale
  ): String = {

    // Resolve definition/remark using existing fallback logic
    def resolveMap(defs: Map[java.util.Locale, String]): Option[String] = {
      val pairs = defs.toList

      val exact =
        pairs.find { case (l, _) => l.getLanguage == locale.getLanguage }.map(_._2)

      val en =
        pairs.find { case (l, _) => l.getLanguage == "en" }.map(_._2)

      val ja =
        pairs.find { case (l, _) => l.getLanguage == "ja" }.map(_._2)

      val any = pairs.headOption.map(_._2)

      exact.orElse(en).orElse(ja).orElse(any)
    }

    val defOpt = resolveMap(entry.definitions)
    val remOpt = resolveMap(entry.remarks)

    defOpt
      .orElse(remOpt)
      .orElse(entry.synonyms.headOption)
      .getOrElse(entry.canonicalLabel)
  }


  /** Locale-aware neighbor graph.
    * Uses ConceptEntry parents / children / related.
    * Labels are resolved through localizedLabel().
    */
  def getNeighbors(uri: String, locale: java.util.Locale): IO[GraphResult] = {
    conceptMatcher.lookupByUri(uri).map { entryOpt =>
      entryOpt match {
        case None =>
          // No dictionary entry; return empty graph
          GraphResult(Nil, Nil)

        case Some(center) =>
          // Collect URIs
          val parentUris  = center.parents
          val childUris   = center.children
          val relatedUris = center.related

          // Localized edge labels
          val bundle = context.bundleFor(locale)
          val relParent  = bundle.getString("relation.parent")
          val relChild   = bundle.getString("relation.child")
          val relRelated = bundle.getString("relation.related")

          // Fetch entries from ConceptDictionary (lookup only; Fuseki not used)
          def entryFor(u: String): Option[org.simplemodeling.sie.concept.ConceptEntry] =
            lookupByUriSync(u)

          // Build node for center
          val centerNode = GraphNode(
            id    = uri,
            label = localizedLabel(center, uri, locale),
            kind  = "concept"
          )

          // Build neighbor nodes
          def mkNode(u: String): GraphNode = {
            val label =
              entryFor(u) match {
                case Some(e) => localizedLabel(e, u, locale)
                case None    => u
              }
            GraphNode(id = u, label = label, kind = "concept")
          }

          val parentNodes  = parentUris.toList.map(mkNode)
          val childNodes   = childUris.toList.map(mkNode)
          val relatedNodes = relatedUris.toList.map(mkNode)

          // Build edges
          val parentEdges =
            parentUris.toList.map { p =>
              GraphEdge(source = p, target = uri, relation = relParent)
            }

          val childEdges =
            childUris.toList.map { c =>
              GraphEdge(source = uri, target = c, relation = relChild)
            }

          val relatedEdges =
            relatedUris.toList.map { r =>
              GraphEdge(source = uri, target = r, relation = relRelated)
            }

          GraphResult(
            nodes = centerNode :: (parentNodes ++ childNodes ++ relatedNodes),
            edges = parentEdges ++ childEdges ++ relatedEdges
          )
      }
    }
  }

  // Synchronous lookup helper
  private def lookupByUriSync(uri: String): Option[org.simplemodeling.sie.concept.ConceptEntry] =
    conceptMatcher.lookupByUri(uri).unsafeRunSync()

  /** Build neighbor graph using ConceptEntry relations.
    * Parent/child/related links come entirely from the dictionary.
    * Old-style Scala syntax with explicit braces for safety.
    */
  def getNeighbors(uri: String): IO[GraphResult] = {
    getNeighbors(uri, java.util.Locale.ENGLISH)
  }

  /** Lightweight debug summary for a concept's local graph */
  def debugGraph(uri: String): IO[GraphDebug] =
    getNeighbors(uri).map { g =>
      GraphDebug(
        uri       = uri,
        nodeCount = g.nodes.size,
        edgeCount = g.edges.size
      )
    }

  // ============================================================
  // RDF Graph Extraction
  // ============================================================

  /** Lightweight concept–passage graph (Option C)
    * - Concepts become nodes
    * - Passages become nodes
    * - Every concept is connected to every passage with relation "related"
    */
  private def extractGraph(concepts: List[ConceptHit], passages: List[PassageHit] = Nil): IO[GraphResult] =
    IO {
      val conceptNodes =
        concepts.map { c =>
          GraphNode(
            id    = c.uri,
            label = c.label,
            kind  = "concept"
          )
        }

      val passageNodes =
        passages.map { p =>
          GraphNode(
            id    = p.id,
            label = p.text.take(80),
            kind  = "passage"
          )
        }

      // Fully connect concepts → passages
      val edges =
        for {
          c <- concepts
          p <- passages
        } yield GraphEdge(
          source    = c.uri,
          target    = p.id,
          relation  = "related"
        )

      GraphResult(
        nodes = conceptNodes ++ passageNodes,
        edges = edges
      )
    }

  private def buildConceptExplainQuery(uri: String): String =
    s"""
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

    SELECT ?label ?lang ?comment ?definition WHERE {
      OPTIONAL {
        <$uri> rdfs:label ?l .
        BIND(STR(?l) AS ?label)
        BIND(LANG(?l) AS ?lang)
      }
      OPTIONAL { <$uri> rdfs:comment   ?comment    . }
      OPTIONAL { <$uri> skos:definition ?definition . }
    }
    """

  private def buildGraphQuery(uri: String): String =
    s"""
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX smorg: <https://www.simplemodeling.org/ontology/simplemodelingorg#>

    SELECT ?source ?related ?label ?rel WHERE {
      VALUES ?source { <$uri> }

      {
        ?source skos:broader ?related .
        BIND("broader" AS ?rel)
      }
      UNION
      {
        ?source skos:narrower ?related .
        BIND("narrower" AS ?rel)
      }
      UNION
      {
        ?source skos:related ?related .
        BIND("related" AS ?rel)
      }
      UNION
      {
        ?source rdfs:subClassOf ?related .
        BIND("subClassOf" AS ?rel)
      }
      UNION
      {
        ?source smorg:refersTo ?related .
        BIND("refersTo" AS ?rel)
      }
      UNION
      {
        ?source smorg:mentions ?related .
        BIND("mentions" AS ?rel)
      }

      OPTIONAL { ?related rdfs:label ?label . }
    }
    """

  // ============================================================
  // Passage Enrichment (initially simple transfer)
  // ============================================================

  private def enrich(raw: org.simplemodeling.sie.chroma.RawPassage): PassageHit =
    PassageHit(
      id    = raw.id,
      text  = raw.text,
      score = Some(raw.distance)
    )

  // ============================================================
  // JSON Parsing Utilities
  // ============================================================

  /** Parse SPARQL JSON results into ConceptHit */
  private def parseConcepts(json: Json): List[ConceptHit] =
    val cursor = json.hcursor
    val results = cursor.downField("results").downField("bindings")

    results.values match {
      case Some(arr) =>
        arr.toList.flatMap { row =>
          val c = row.hcursor
          (for {
            uri <- c.downField("s").get[String]("value").toOption
            label <- c.downField("label").get[String]("value").toOption
          } yield ConceptHit(
            uri = uri,
            label = label,
            lang = c.downField("label").get[String]("xml:lang").toOption
          )).toList
        }
      case None => Nil
    }

  /** Parse Chroma results into PassageHit */
  private def parsePassages(json: Json): List[PassageHit] = {
    // Some Chroma variants return:
    // { "results": { "ids": [[...]], "documents": [[...]], ... } }
    // while others return:
    // { "ids": [[...]], "documents": [[...]], ... }
    //
    // We normalize by choosing "results" if present, otherwise using the root.
    val cursor = json.hcursor

    val results = cursor.downField("results") match {
      case h: HCursor => h
      case _          => cursor
    }

    val idsOpt    = results.get[List[List[String]]]("ids").toOption
    val docsOpt   = results.get[List[List[String]]]("documents").toOption
    val scoresOpt = results.get[List[List[Double]]]("distances").toOption
    val metasOpt  = results.get[List[List[Json]]]("metadatas").toOption

    (idsOpt, docsOpt) match {
      case (Some(idsNested), Some(docsNested)) =>
        val ids    = idsNested.flatten
        val docs   = docsNested.flatten
        val scores = scoresOpt.map(_.flatten).getOrElse(Nil)
        val urls: List[Option[String]] =
          metasOpt
            .map(_.flatten.map { m =>
              m.hcursor.downField("url").as[String].toOption
            })
            .getOrElse(Nil)

        // Use ids length as the base; safely look up docs / scores / urls
        ids.zipWithIndex.map { case (rawId, idx) =>
          val text      = docs.lift(idx).getOrElse("")
          val scoreOpt  = scores.lift(idx)
          val urlOpt    = urls.lift(idx).flatten
          val finalId   = urlOpt.getOrElse(rawId)

          PassageHit(
            id    = finalId,
            text  = text,
            score = scoreOpt
          )
        }

      case _ =>
        Nil
    }
  }
}

object RagService {
  /** Lazy-loading, caching manager for ResourceBundles used for labels. */
  final class LabelBundleManager(
    baseName: String,
    val defaultLocale: java.util.Locale
  ) {
    private val cache =
      new java.util.concurrent.ConcurrentHashMap[java.util.Locale, java.util.ResourceBundle]()

    private def load(locale: java.util.Locale): java.util.ResourceBundle = {
      java.util.ResourceBundle.getBundle(baseName, locale)
    }

    /** Get ResourceBundle for the given locale, falling back to defaultLocale. */
    def bundleFor(locale: java.util.Locale): java.util.ResourceBundle = {
      val key = if (locale == null) defaultLocale else locale
      val cached = cache.get(key)
      if (cached != null) cached
      else {
        val loaded = load(key)
        cache.put(key, loaded)
        loaded
      }
    }
  }

  /** Context for RagService.
    * RagService itself stays locale-neutral.
    * ResourceBundles are resolved per request.
    */
  case class Context() {
    private val labelBundleManager =
      new LabelBundleManager("sie_labels", java.util.Locale.ENGLISH)

    /** ResourceBundle for a specific locale (with fallback to default locale). */
    def bundleFor(locale: java.util.Locale): java.util.ResourceBundle = {
      labelBundleManager.bundleFor(locale)
    }

    /** Default bundle (typically English). */
    def defaultBundle: java.util.ResourceBundle = {
      labelBundleManager.bundleFor(labelBundleManager.defaultLocale)
    }

    /** Expose the default locale used by the bundle manager. */
    def defaultLocale: java.util.Locale = labelBundleManager.defaultLocale
  }

  object Context {
    val default: Context = Context()
  }
}
