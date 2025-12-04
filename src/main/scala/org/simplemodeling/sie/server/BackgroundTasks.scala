package org.simplemodeling.sie.server

import cats.effect.*
import cats.syntax.all.*
import scala.concurrent.duration.*
import org.simplemodeling.sie.fuseki.FusekiClient
import org.simplemodeling.sie.chroma.ChromaClient
import org.simplemodeling.sie.embedding.EmbeddingEngine
import org.simplemodeling.sie.indexer.HtmlIndexer
import org.simplemodeling.sie.init.IndexInitializer

/**
 * BackgroundTasks
 *
 * Periodic health-check and recovery loop for:
 *   - Chroma availability (via collectionExists)
 *   - Embedding availability (via a test embed)
 *
 * If both become healthy, IndexInitializer.run() is invoked once.
 *
 * @since   Dec.  4, 2025
 * @version Dec.  4, 2025
 * @author  ASAMI, Tomoharu
 */
object BackgroundTasks:

  // -----------------------------------------------
  // Tunable constants
  // -----------------------------------------------

  /** Interval between health-check cycles. */
  val LoopInterval: FiniteDuration = 20.seconds

  /** Test text to probe embedding engine. */
  val EmbedTestText: String = "healthcheck"

  // -----------------------------------------------
  // Ref (state)
  // -----------------------------------------------

  /** ensures IndexInitializer is executed once */
  private val indexingFlag: IO[Ref[IO, Boolean]] =
    Ref.of[IO, Boolean](false)

  // -----------------------------------------------
  // Public API
  // -----------------------------------------------

  /** Start background recovery loop as a fiber. */
  def startRecoveryLoop(
      chroma: ChromaClient,
      fuseki: FusekiClient,
      embedding: EmbeddingEngine
  ): IO[FiberIO[Unit]] =
    indexingFlag.flatMap { ref =>
      loop(ref, chroma, fuseki, embedding)
        .handleErrorWith { e =>
          IO.println(s"[BackgroundTasks] loop error (continuing): ${e.getMessage}") *>
            IO.sleep(LoopInterval) *> startRecoveryLoop(chroma, fuseki, embedding).void
        }
        .start
    }

  // -----------------------------------------------
  // Main loop
  // -----------------------------------------------

  private def loop(
      ref: Ref[IO, Boolean],
      chroma: ChromaClient,
      fuseki: FusekiClient,
      embedding: EmbeddingEngine
  ): IO[Unit] =
    for
      _ <- IO.sleep(LoopInterval)
      _ <- IO.println("[BackgroundTasks] Health-check cycle...")

      // ---- 1. Check Chroma
      chromaOk <- IO {
        chroma.collectionExists(HtmlIndexer.CollectionName) match
          case Right(_) =>
            println("[BackgroundTasks] Chroma OK.")
            true
          case Left(err) =>
            println(s"[BackgroundTasks] Chroma error: $err")
            false
      }

      // ---- 2. Check embedding
      embeddingOk <- (
        embedding.mode match
          case m if m.toString.equalsIgnoreCase("none") =>
            IO.pure(true)
          case _ =>
            embedding.embed(List(EmbedTestText)).attempt.map {
              case Right(Some(_)) =>
                println("[BackgroundTasks] Embedding OK.")
                true
              case Right(None) =>
                println("[BackgroundTasks] Embedding returned None.")
                false
              case Left(err) =>
                println(s"[BackgroundTasks] Embedding error: ${err.getMessage}")
                false
            }
      )

      // ---- 3. If recovered and indexing not yet done → do indexing
      _ <-
        if chromaOk && embeddingOk then
          for
            done <- ref.get
            _ <-
              if !done then
                IO.println("[BackgroundTasks] Recovery detected → running IndexInitializer once.") *>
                  IO {
                    try IndexInitializer.run(fuseki, chroma, embedding)
                    catch case e: Throwable =>
                      println("[BackgroundTasks] IndexInitializer failed: " + e.getMessage)
                  } *>
                  ref.set(true)
              else IO.unit
          yield ()
        else IO.unit

      // ---- Continue looping
      _ <- loop(ref, chroma, fuseki, embedding)

    yield ()
