package org.simplemodeling.sie.server

import cats.effect.unsafe.implicits.global
import cats.effect.*
import org.simplemodeling.sie.config.*
import org.simplemodeling.sie.fuseki.*
import org.simplemodeling.sie.chroma.*
import org.simplemodeling.sie.service.*
import org.simplemodeling.sie.indexer.HtmlIndexer
import org.simplemodeling.sie.embedding.*
import org.simplemodeling.sie.init.IndexInitializer

/*
 * @since   Nov. 20, 2025
 *          Nov. 25, 2025
 * @version Dec.  2, 2025
 * @author  ASAMI, Tomoharu
 */
object RagServerMain extends IOApp.Simple:

  def run: IO[Unit] = {
    val cfg    = AppConfig.load()
    val fuseki = FusekiClient(cfg.fuseki.endpoint)

    for {
      embedding <- EmbeddingEngineFactory.create()
      chroma = ChromaClient(cfg.chroma.endpoint, embedding)

      _ <- IO.println(s"[RagServerMain] Ensuring Chroma collection '${HtmlIndexer.CollectionName}' exists...")

      collectionExists <- IO(chroma.collectionExists(HtmlIndexer.CollectionName))

      _ <- collectionExists match {
        case Right(true) =>
          IO.println(s"[RagServerMain] Collection '${HtmlIndexer.CollectionName}' already exists.")

        case Right(false) =>
          IO.println(s"[RagServerMain] Creating collection '${HtmlIndexer.CollectionName}'...") *>
            IO.fromEither(
              chroma.createCollection(HtmlIndexer.CollectionName)
                .left.map(err => new Exception(err))
            ).flatMap(_ =>
              IO.println(s"[RagServerMain] Collection created.")
            )

        case Left(err) =>
          IO.println(s"[RagServerMain] Could not verify collection existence: $err")
      }

      forceIndex = sys.env.get("SIE_INDEX_ON_START").exists(_.equalsIgnoreCase("true"))

      _ <-
        if forceIndex then
          IO.println("[RagServerMain] Forced indexing via IndexInitializer (SIE_INDEX_ON_START=true)...") *>
            IO(IndexInitializer.run(fuseki, chroma, embedding)).handleErrorWith { e =>
              IO.println(s"[RagServerMain] IndexInitializer failed: ${e.getMessage}")
            }
        else
          collectionExists match
            case Right(false) =>
              IO.println("[RagServerMain] Running initial indexing via IndexInitializer (collection did not exist)...") *>
                IO(IndexInitializer.run(fuseki, chroma, embedding)).handleErrorWith { e =>
                  IO.println(s"[RagServerMain] IndexInitializer failed: ${e.getMessage}")
                }
            case Right(true) =>
              IO.println("[RagServerMain] Skipping IndexInitializer (collection already exists).")
            case Left(err) =>
              IO.println(s"[RagServerMain] Skipping IndexInitializer due to collection existence error: $err")

      service <- IO.pure(RagService(fuseki, chroma, embedding))

      _ <- HttpRagServer(service, cfg.server.host, cfg.server.siePort).start
    } yield ()
  }
