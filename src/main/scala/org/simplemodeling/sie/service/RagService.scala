package org.simplemodeling.sie.service

import org.simplemodeling.sie.fuseki.FusekiClient
import org.simplemodeling.sie.chroma.ChromaClient
import org.simplemodeling.sie.embedding.EmbeddingEngine
import org.simplemodeling.sie.init.IndexInitializer
import io.circe.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global

/*
 * Integrated RagService
 * - Structured results (ConceptHit, PassageHit)
 * - Hybrid search + fallback
 * - English comments only
 * 
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec.  5, 2025
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
  fuseki: FusekiClient,
  chroma: ChromaClient,
  embedding: EmbeddingEngine
):

  /** Main IO-based entry point */
  def runIO(query: String): IO[RagResult] =
    val conceptsIO: IO[List[ConceptHit]] =
      fuseki
        .searchConceptsJson(query)
        .map(parseConcepts)
        .handleError(_ => Nil)

    val passagesIO: IO[List[PassageHit]] =
      IO {
        if embedding.mode.toString.equalsIgnoreCase("none") then
          chroma
            .search("simplemodeling", query, 5)
            .fold(_ => Nil, _.map(enrich))
        else
          val hybrid  = chroma.hybridSearch("simplemodeling", query, 5)
          val fallback = chroma.search("simplemodeling", query, 5)

          hybrid
            .orElse(fallback)
            .fold(_ => Nil, _.map(enrich))
      }.handleError(_ => Nil)

    for
      concepts <- conceptsIO
      passages <- passagesIO
      graph    <- extractGraph(concepts, passages)
    yield RagResult(concepts, passages, graph)

  /** Synchronous wrapper */
  def run(query: String): RagResult =
    runIO(query).unsafeRunSync()

  /** Initialize Chroma index */
  def initChroma(): Json =
    try
      IndexInitializer.run(fuseki, chroma, embedding)
      Json.obj("status" -> Json.fromString("ok"))
    catch
      case e: Throwable =>
        Json.obj("error" -> Json.fromString(e.toString))

  /** Health reporting */
  def health(): IO[Json] =
    for
      // Embedding check
      emb <- IO {
        if embedding.mode.toString.equalsIgnoreCase("none") then
          Json.obj(
            "enabled"   -> Json.fromBoolean(false),
            "reachable" -> Json.fromBoolean(true)
          )
        else
          try
            val r = embedding.embed(List("health-check")).unsafeRunSync()
            if r.exists(_.nonEmpty) then
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
          catch
            case e: Throwable =>
              Json.obj(
                "enabled"   -> Json.fromBoolean(true),
                "reachable" -> Json.fromBoolean(false),
                "error"     -> Json.fromString(e.getMessage)
              )
      }

      // Chroma check
      chr <- IO {
        chroma.collectionExists("simplemodeling") match
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

      // Fuseki check
      fus <- fuseki.searchConceptsJson("health-check").attempt.map {
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

      status = {
        val okEmbedding = emb.hcursor.get[Boolean]("reachable").getOrElse(false)
        val okChroma    = chr.hcursor.get[Boolean]("reachable").getOrElse(false)
        val okFuseki    = fus.hcursor.get[Boolean]("reachable").getOrElse(false)

        if okEmbedding && okChroma && okFuseki then "ok"
        else "degraded"
      }

    yield Json.obj(
      "status"   -> Json.fromString(status),
      "embedding"-> emb,
      "chroma"   -> chr,
      "fuseki"   -> fus
    )

  /** Explain a single concept identified by its URI */
  def explainConcept(uri: String): IO[ConceptExplanation] =
    val conceptList = List(ConceptHit(uri = uri, label = uri, lang = None))
    for
      metaRows <- fuseki.queryFlat(buildConceptExplainQuery(uri))
      graph    <- extractGraph(conceptList, Nil)
    yield
      val label: Option[String] =
        metaRows.flatMap(_.get("label")).headOption

      val description: Option[String] =
        metaRows.flatMap(r => r.get("definition").orElse(r.get("comment"))).headOption

      ConceptExplanation(
        uri         = uri,
        label       = label,
        description = description,
        graph       = graph
      )

  /** Get neighbor graph for a single concept URI */
  def getNeighbors(uri: String): IO[GraphResult] =
    val conceptList = List(ConceptHit(uri = uri, label = uri, lang = None))
    extractGraph(conceptList, Nil)

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
        for
          c <- concepts
          p <- passages
        yield GraphEdge(
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

    results.values match
      case Some(arr) =>
        arr.toList.flatMap { row =>
          val c = row.hcursor
          for
            uri <- c.downField("s").get[String]("value").toOption
            label <- c.downField("label").get[String]("value").toOption
          yield ConceptHit(
            uri = uri,
            label = label,
            lang = c.downField("label").get[String]("xml:lang").toOption
          )
        }
      case None => Nil

  /** Parse Chroma results into PassageHit */
  private def parsePassages(json: Json): List[PassageHit] =
    // Some Chroma variants return:
    // { "results": { "ids": [[...]], "documents": [[...]], ... } }
    // while others return:
    // { "ids": [[...]], "documents": [[...]], ... }
    //
    // We normalize by choosing "results" if present, otherwise using the root.
    val cursor = json.hcursor

    val results = cursor.downField("results") match
      case h: HCursor => h
      case _          => cursor

    val idsOpt    = results.get[List[List[String]]]("ids").toOption
    val docsOpt   = results.get[List[List[String]]]("documents").toOption
    val scoresOpt = results.get[List[List[Double]]]("distances").toOption
    val metasOpt  = results.get[List[List[Json]]]("metadatas").toOption

    (idsOpt, docsOpt) match
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
