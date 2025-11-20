package org.simplemodeling.rag.chroma

import sttp.client3.*
import sttp.client3.circe.*
import io.circe.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 20, 2025
 * @author  ASAMI, Tomoharu
 */
class ChromaClient(endpoint: String):
  private val backend = HttpClientSyncBackend()
  private val base = uri"$endpoint/api/v1"

  def search(collection: String, text: String, n: Int): Option[Json] =
    val payload = Json.obj(
      "query_texts" -> Json.arr(Json.fromString(text)),
      "n_results"   -> Json.fromInt(n)
    )

    basicRequest
      .post(base.addPath("collections", collection, "query"))
      .header("Content-Type", "application/json")
      .body(payload)
      .response(asJson[Json])
      .send(backend)
      .body
      .toOption
