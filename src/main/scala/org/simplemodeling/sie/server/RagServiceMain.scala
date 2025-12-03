package org.simplemodeling.sie.server

import cats.effect.unsafe.implicits.global
import cats.effect.*
import org.simplemodeling.sie.config.*
import org.simplemodeling.sie.fuseki.*
import org.simplemodeling.sie.chroma.*
import org.simplemodeling.sie.service.*
import org.simplemodeling.sie.indexer.HtmlIndexer
import org.simplemodeling.sie.embedding.*

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

      service <- IO.pure(RagService(fuseki, chroma, embedding))

      forceIndex = sys.env.get("SIE_INDEX_ON_START").exists(_.equalsIgnoreCase("true"))

      indexIO =
        if (forceIndex)
          IO.println("[RagServerMain] Forced indexing (SIE_INDEX_ON_START=true)...") *>
            HtmlIndexer.indexAll(chroma, force = true).handleErrorWith { e =>
              IO.println(s"[RagServerMain] Indexing failed: ${e.getMessage}")
            }
        else
          IO.println("[RagServerMain] Initial-only indexing (force = false).") *>
            HtmlIndexer.indexAll(chroma, force = false).handleErrorWith { e =>
              IO.println(s"[RagServerMain] Indexing failed: ${e.getMessage}")
            }

      _ <- indexIO
      _ <- HttpRagServer(service, cfg.server.host, cfg.server.siePort).start
    } yield ()
  }
