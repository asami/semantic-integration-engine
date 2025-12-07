package org.simplemodeling.sie.server

import cats.effect.*
import cats.syntax.all.*
import org.simplemodeling.sie.config.*
import org.simplemodeling.sie.fuseki.*
import org.simplemodeling.sie.chroma.*
import org.simplemodeling.sie.service.*
import org.simplemodeling.sie.indexer.HtmlIndexer
import org.simplemodeling.sie.embedding.*
import org.simplemodeling.sie.init.IndexInitializer
import org.simplemodeling.sie.concept.*
import org.simplemodeling.sie.util.RetryUtil
import scala.concurrent.duration.*

/**
 * RagServerMain
 *
 * Responsibilities:
 * - Load application configuration
 * - Initialize Fuseki / Embedding / Chroma clients
 * - Ensure Chroma collection exists (non-fatal, degraded mode on failure)
 * - Run IndexInitializer once (forced or first-time, non-fatal)
 * - Start background recovery loop (for future self-healing)
 * - Start HTTP RAG server
 *
 * Design notes:
 * - All external calls (Chroma, Fuseki, IndexInitializer) are treated as
 *   non-fatal for process startup. Failures are logged and the server
 *   continues in a degraded mode where possible.
 *
 * @since   Nov. 20, 2025
 *          Nov. 25, 2025
 * @version Dec.  6, 2025
 */
object RagServerMain extends IOApp.Simple:

  override def run: IO[Unit] =
    val cfg    = AppConfig.load()
    val mode = cfg.server.mode
    val isDev    = mode == ServerMode.Dev
    val isDemo   = mode == ServerMode.Demo
    val isStaging= mode == ServerMode.Staging
    val isProd   = mode == ServerMode.Prod

    if isDev then
      println("[RagServerMain] DEV MODE: Skipping Chroma/Fuseki initialization and HtmlIndexer.")
    else if isDemo then
      println("[RagServerMain] DEMO MODE: Using preloaded Fuseki; skipping heavy indexing.")
    else if isStaging then
      println("[RagServerMain] STAGING MODE: Full initialization with debug logging.")
    else
      println("[RagServerMain] PROD MODE: Full initialization.")

    val fusekiClient = FusekiClient(cfg.fuseki.endpoint)

    for
      // -------------------------------------------------------
      // 1. Initialize embedding engine
      // -------------------------------------------------------
      embedding <- EmbeddingEngineFactory.create()

      // Chroma client is always created, but may internally
      // operate in a degraded mode if the underlying endpoint
      // is not reachable.
      chroma = ChromaClient(cfg.chroma.endpoint, embedding)

      _ <-
        if isDev then
          // Skip indexing entirely only in Dev mode
          IO.unit
        else
          for
            _ <- IO.println(
              s"[RagServerMain] Ensuring Chroma collection '${HtmlIndexer.CollectionName}' exists..."
            )

            // -------------------------------------------------------
            // 2. Collection existence (blocking but non-fatal)
            // -------------------------------------------------------
            exists <- IO.pure(chroma.collectionExists(HtmlIndexer.CollectionName))

            _ <- exists match
              case Right(true) =>
                IO.println("[RagServerMain] Collection already exists.")

              case Right(false) =>
                IO.println("[RagServerMain] Creating collection (non-fatal)...") *>
                  IO {
                    chroma.createCollection(HtmlIndexer.CollectionName) match
                      case Right(_)  =>
                        println("[RagServerMain] Collection created.")
                      case Left(err) =>
                        println("[RagServerMain] createCollection failed (continuing): " + err)
                  }

              case Left(err) =>
                IO.println(
                  s"[RagServerMain] collectionExists failed (degraded mode, continuing): $err"
                )

            // -------------------------------------------------------
            // 3. Initial indexing (non-fatal)
            // -------------------------------------------------------
            forceIndex = sys.env
              .get("SIE_INDEX_ON_START")
              .exists(_.equalsIgnoreCase("true"))

            _ <-
              if forceIndex then
                IO.println("[RagServerMain] Forced indexing (non-fatal)...") *>
                runIndexInitializer(fusekiClient, chroma, embedding) *>
                IO.println("[RagServerMain] Scheduling HtmlIndexer in background (non-fatal)...") *>
                startHtmlIndexer(chroma)
              else
                exists match
                  case Right(false) =>
                    IO.println(
                      "[RagServerMain] Initial indexing because collection did not exist (non-fatal)..."
                    ) *> runIndexInitializer(fusekiClient, chroma, embedding) *>
                      IO.println("[RagServerMain] Scheduling HtmlIndexer in background (non-fatal)...") *>
                      startHtmlIndexer(chroma)

                  case Right(true) =>
                    IO.println(
                      "[RagServerMain] Skipping IndexInitializer (collection already exists)."
                    ) *>
                      IO.println("[RagServerMain] Scheduling HtmlIndexer in background (non-fatal)...") *>
                      startHtmlIndexer(chroma)

                  case Left(_) =>
                    IO.println(
                      "[RagServerMain] Skipping IndexInitializer due to collection existence error."
                    )

            // -------------------------------------------------------
            // 4. Start background recovery loop
            // -------------------------------------------------------
            _ <- BackgroundTasks.startRecoveryLoop(chroma, fusekiClient, embedding)
          yield ()

      // -------------------------------------------------------
      // 5. Start HTTP RAG server
      // -------------------------------------------------------
      conceptDict <-
        if isDev then
          IO.pure(ConceptDictionary(Map.empty))
        else
          val loader = FusekiConceptLoader(new org.simplemodeling.sie.concept.FusekiSparqlClient(fusekiClient))
          RetryUtil.retryIO(
            attempts = 15,
            delay = 1.second,
            label = "ConceptLoader initial load"
          ) {
            loader.load()
          }.map { seq =>
            ConceptDictionary(
              seq.map { case (uri, conceptLabel) =>
                uri -> ConceptEntry(
                  uri = uri,
                  labels = Set(conceptLabel),
                  localeLabels = Map(conceptLabel.locale -> Set(conceptLabel)),
                  preferredLabelMap =
                    if conceptLabel.preferred then
                      Map(conceptLabel.locale -> conceptLabel.text)
                    else
                      Map.empty
                )
              }.toMap
            )
          }.handleError { e =>
            println("[RagServerMain] ConceptLoader failed after retries (continuing with empty dictionary): " + e.getMessage)
            ConceptDictionary(Map.empty)
          }

      service = {
        val dictionary: Map[String, String] =
          conceptDict.entries.map { case (label, entry) => label -> entry.uri }

        val embedSingle: String => cats.effect.IO[Array[Float]] = (s: String) =>
          embedding.embed(List(s)).map(_.flatMap(_.headOption).getOrElse(Array.emptyFloatArray))

        val embedSearch: Array[Float] => cats.effect.IO[List[(String, Double)]] = (_: Array[Float]) =>
          IO.pure(Nil)

        val conceptMatcher =
          new ConceptMatcher(
            conceptDict = conceptDict,
            dictionary = dictionary,
            embed = embedSingle,
            embedSearch = embedSearch
          )

        RagService(RagService.Context.default, fusekiClient, chroma, embedding, conceptMatcher)
      }

      _ <- HttpRagServer(service, cfg.server.host, cfg.server.siePort).start

    yield ()

  // ------------------------------------------------------------
  // Helper: safe IndexInitializer wrapper
  // ------------------------------------------------------------
  /**
   * Run IndexInitializer once in a best-effort, non-fatal way.
   *
   * Any Throwable is caught and logged to STDOUT; the caller
   * should treat failures as degraded mode but must not abort
   * the process.
   */
  private def runIndexInitializer(
      fuseki: FusekiClient,
      chroma: ChromaClient,
      embedding: EmbeddingEngine
  ): IO[Unit] =
    IO {
      try
        IndexInitializer.run(fuseki, chroma, embedding)
      catch
        case e: Throwable =>
          println(
            "[RagServerMain] IndexInitializer failed (continuing): " + e.getMessage
          )
    }

  private def startHtmlIndexer(chroma: ChromaClient): IO[Unit] =
    HtmlIndexer
      .indexAll(chroma)
      .handleErrorWith { e =>
        IO.println("[RagServerMain] HtmlIndexer failed (continuing): " + e.getMessage)
      }
      .start
      .void
