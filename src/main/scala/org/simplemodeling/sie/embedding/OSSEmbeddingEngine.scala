package org.simplemodeling.sie.embedding

import cats.effect.*
import cats.syntax.all.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.circe.*
import org.http4s.Method.POST

/**
 * OSS Embedding engine (HTTP client to FastAPI embedding server)
 *
 * Environment variable:
 *   SIE_OSS_EMBEDDING_URL  (e.g., http://oss-embed:7000/embedding)
 *
 * @since   Dec.  2, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
final class OSSEmbeddingEngine(
    client: Client[IO],
    endpoint: Uri
) extends EmbeddingEngine {

  override def isEnabled: Boolean = true

  override def mode: EmbeddingMode = EmbeddingMode.OSS

  override def embed(texts: List[String]): IO[Option[List[Array[Float]]]] =
    val reqJson = Json.obj(
      "input" -> Json.fromValues(texts.map(Json.fromString))
    )
    val req = POST(reqJson, endpoint)

    client.expect[Json](req).attempt.flatMap {
      case Left(err) =>
        IO.println(s"[OSSEmbeddingEngine] HTTP embedding error: ${err.getMessage}") *>
        IO.pure(None)

      case Right(json) =>
        json.hcursor.downField("vectors").as[List[List[Float]]] match
          case Right(vecs) =>
            IO.pure(Some(vecs.map(_.toArray)))
          case Left(err) =>
            IO.println(s"[OSSEmbeddingEngine] Invalid JSON: $err") *>
            IO.pure(None)
    }
}

object OSSEmbeddingEngine {

  /**
   * Factory: create HTTP client engine from environment variable.
   */
  def fromEnv(httpClient: Client[IO]): IO[EmbeddingEngine] =
    sys.env.get("SIE_OSS_EMBEDDING_URL") match
      case Some(url) =>
        Uri.fromString(url) match
          case Left(_)  =>
            IO.raiseError(new RuntimeException(s"Invalid SIE_OSS_EMBEDDING_URL: $url"))
          case Right(u) =>
            IO.pure(new OSSEmbeddingEngine(httpClient, u))

      case None =>
        IO.raiseError(new RuntimeException(
          "OSSEmbeddingEngine requires SIE_OSS_EMBEDDING_URL to be set (FastAPI embedding server endpoint)"
        ))
}
