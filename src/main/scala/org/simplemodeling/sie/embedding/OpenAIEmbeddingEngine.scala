package org.simplemodeling.sie.embedding

import cats.effect.*
import cats.syntax.all.*
import io.circe.*
import io.circe.parser.*
import io.circe.generic.semiauto.*
import sttp.client3.*
import sttp.model.MediaType

/**
 * OpenAI embeddings backend.
 *
 * Uses /v1/embeddings endpoint.
 *
 * @since   Dec.  2, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
final class OpenAIEmbeddingEngine(
    apiKey: String,
    model: String = "text-embedding-3-small"
)(using backend: SttpBackend[IO, Any])
    extends EmbeddingEngine:

  // ------------------------------------------------------------
  // Circe models
  // ------------------------------------------------------------
  private case class EmbeddingData(embedding: Vector[Float])
  private case class EmbeddingResponse(data: List[EmbeddingData])

  private given Decoder[EmbeddingData] = deriveDecoder
  private given Decoder[EmbeddingResponse] = deriveDecoder

  private val endpoint = uri"https://api.openai.com/v1/embeddings"

  /** This engine always performs embeddings when selected. */
  override def isEnabled: Boolean = true

  /** OpenAI-based embedding mode. */
  def mode: EmbeddingMode = EmbeddingMode.OpenAI

  // ------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------

  /** Embed a list of texts (EmbeddingEngine unified API). */
  override def embed(texts: List[String]): IO[Option[List[Array[Float]]]] =
    if texts.isEmpty then IO.pure(Some(Nil))
    else callApi(texts).map(Some(_))

  // ------------------------------------------------------------
  // Internal helpers
  // ------------------------------------------------------------

  private def callApi(inputs: List[String]): IO[List[Array[Float]]] =
    val json = Json.obj(
      "input" -> Json.fromValues(inputs.map(Json.fromString)),
      "model" -> Json.fromString(model)
    )

    val req = basicRequest
      .post(endpoint)
      .contentType(MediaType.ApplicationJson)
      .auth.bearer(apiKey)
      .body(json.noSpaces)

    for
      resp <- req.send(backend)
      body <- IO.fromEither(
        resp.body.leftMap(err =>
          new RuntimeException(s"OpenAI embedding HTTP error: $err")
        )
      )
      parsed <- IO.fromEither(
        parse(body).leftMap(err =>
          new RuntimeException(
            s"OpenAI embedding JSON parse error: $err"
          )
        )
      )
      decoded <- IO.fromEither(
        parsed.as[EmbeddingResponse].leftMap(err =>
          new RuntimeException(
            s"OpenAI embedding decode error: $err\nJSON: $parsed"
          )
        )
      )
      result <- decoded.data match
        case Nil =>
          IO.raiseError(
            new RuntimeException(
              s"OpenAI embedding response has empty data array: $body"
            )
          )
        case many =>
          IO.pure(many.map(_.embedding.toArray))
    yield result

object OpenAIEmbeddingEngine:

  /**
   * Factory for environment-based loading.
   *
   * Required:
   *   OPENAI_API_KEY
   *
   * Optional:
   *   SIE_EMBEDDING_MODEL
   */
  def fromEnv()(using backend: SttpBackend[IO, Any]): IO[OpenAIEmbeddingEngine] =
    IO(sys.env.get("OPENAI_API_KEY")).flatMap {
      case None =>
        IO.raiseError(
          new RuntimeException(
            "OPENAI_API_KEY is required for OpenAIEmbeddingEngine."
          )
        )
      case Some(key) =>
        val model =
          sys.env.getOrElse("SIE_EMBEDDING_MODEL", "text-embedding-3-small")
        IO(new OpenAIEmbeddingEngine(key, model))
    }
