package org.simplemodeling.sie.embedding

import cats.effect.*
import cats.syntax.all.*
import org.http4s.ember.client.EmberClientBuilder
import sttp.client3.*
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

/**
 * Selects an EmbeddingEngine implementation based on configuration.
 *
 * Priority:
 *   1. SIE_EMBEDDING_MODE = "openai" | "oss" | "none"
 *   2. auto-detect:
 *        - if OPENAI_API_KEY exists → openai
 *        - else if OSS paths exist → oss
 *        - else none
 *
 * @since   Dec.  2, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
object EmbeddingEngineFactory:

  def create(): IO[EmbeddingEngine] =
    val mode = sys.env.getOrElse("SIE_EMBEDDING_MODE", "auto").toLowerCase

    mode match
      case "openai" => createOpenAI()
      case "oss"    => createOSS()
      case "none"   => IO.pure(EmbeddingEngine.none)
      case "auto"   => autoDetect()
      case other =>
        IO.raiseError(
          new RuntimeException(
            s"Unknown SIE_EMBEDDING_MODE: $other (expected openai|oss|none|auto)"
          )
        )

  // ---------------------------------------------------------------------------
  // Auto-detect
  // ---------------------------------------------------------------------------

  private def autoDetect(): IO[EmbeddingEngine] = IO.pure(EmbeddingEngine.none)

  private def autoDetectBak(): IO[EmbeddingEngine] =
    (sys.env.get("OPENAI_API_KEY"), ossModelAvailable()) match
      case (Some(_), _) =>
        createOpenAI()

      case (None, true) =>
        createOSS()

      case _ =>
        IO.pure(EmbeddingEngine.none)

  private def ossModelAvailable(): Boolean =
    sys.env.get("SIE_EMBEDDING_MODEL_PATH").isDefined &&
      sys.env.get("SIE_EMBEDDING_TOKENIZER_PATH").isDefined

  // ---------------------------------------------------------------------------
  // OpenAI
  // ---------------------------------------------------------------------------

  private def createOpenAI(): IO[EmbeddingEngine] =
    sys.env.get("OPENAI_API_KEY") match
      case None =>
        IO.raiseError(
          new RuntimeException(
            "SIE_EMBEDDING_MODE=openai, but OPENAI_API_KEY is missing"
          )
        )

      case Some(_) =>
        AsyncHttpClientFs2Backend.resource[IO]().use { backend =>
          given SttpBackend[IO, Any] = backend
          OpenAIEmbeddingEngine.fromEnv()
        }

  // ---------------------------------------------------------------------------
  // OSS
  // ---------------------------------------------------------------------------

  private def createOSS(): IO[EmbeddingEngine] =
    if !ossModelAvailable() then
      IO.raiseError(
        new RuntimeException(
          "SIE_EMBEDDING_MODE=oss, but OSS model paths are missing"
        )
      )
    else
      EmberClientBuilder.default[IO].build.use { client =>
        OSSEmbeddingEngine.fromEnv(client)
      }
