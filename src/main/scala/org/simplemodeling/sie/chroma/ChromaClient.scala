package org.simplemodeling.sie.chroma

import io.circe.*
import org.simplemodeling.sie.http.SimpleHttpClient
import org.simplemodeling.sie.embedding.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.*
import cats.syntax.all.*

/*
 * ChromaClient
 *
 * Clean version — no RagService or model classes.
 * Provides:
 *   - collectionExists
 *   - createCollection
 *   - addDocuments / addDocumentsMap
 *   - search (embedding)
 *   - hybridSearch (query_texts + query_embeddings)
 *
 * @since   Nov. 20, 2025
 * @version Dec. 14, 2025
 * @author  ASAMI
 */
// -------------------------
// Models for Chroma decoding
// -------------------------

case class RawPassage(
  id: String,
  text: String,
  metadata: Map[String, String],
  distance: Double
)

case class ChromaResults(
  ids: List[List[String]],
  documents: List[List[String]],
  metadatas: List[List[io.circe.Json]],
  distances: List[List[Double]]
)

object ChromaResults:
  given Decoder[ChromaResults] =
    Decoder.forProduct4("ids", "documents", "metadatas", "distances")(ChromaResults.apply)

case class ChromaResponse(results: ChromaResults)

object ChromaResponse:
  given Decoder[ChromaResponse] =
    Decoder.forProduct1("results")(ChromaResponse.apply)

class ChromaClient(endpoint: String, embeddingEngine: EmbeddingEngine):
  private val baseUri =
    if endpoint.endsWith("/") then endpoint.dropRight(1) else endpoint

  // Simple retry helper for all HTTP calls to the embedding server / Chroma.
  private def retryIO[A](ioa: IO[A], name: String): IO[A] =
    def loop(remaining: Int, delay: FiniteDuration): IO[A] =
      ioa.handleErrorWith { e =>
        if remaining <= 0 then
          IO.raiseError(e)
        else
          IO.println(s"[ChromaClient][retry:$name] remaining=$remaining error=${e.getMessage}") *> 
            IO.sleep(delay) *> 
            loop(remaining - 1, delay * 2)
      }
    // Try up to 3 times with exponential backoff starting at 200ms
    loop(3, 200.millis)

  // -------------------------
  // HTTP Helpers
  // -------------------------

  private def getJson(path: String): IO[Either[String, Json]] =
    val uri = s"$baseUri/chroma/$path"
    IO.println(s"[ChromaClient] GET $uri") *>
      retryIO(
        SimpleHttpClient.get(uri).map { resp =>
          io.circe.parser.parse(resp.body).left.map(_.getMessage)
        },
        s"GET $path"
      )

  private def postJson(path: String, payload: Json): IO[Either[String, Json]] =
    val uri = s"$baseUri/chroma/$path"
    IO.println(s"[ChromaClient] POST $uri") *>
      retryIO(
        SimpleHttpClient.post(uri, payload.noSpaces).map { resp =>
          io.circe.parser.parse(resp.body).left.map(_.getMessage)
        },
        s"POST $path"
      )

  // -------------------------
  // Collections
  // -------------------------

  def collectionExists(name: String): Either[String, Boolean] =
    getJson(s"collections/$name/exists").unsafeRunSync() match {
      case Right(json) =>
        json.hcursor.get[Boolean]("exists") match {
          case Right(v) => Right(v)
          case Left(_)  => Right(false) // defensive default: treat as not existing
        }
      case Left(err) =>
        Left(err)
    }

  def createCollection(name: String): Either[String, Json] =
    postJson(s"collections/$name/create", Json.obj()).unsafeRunSync()

  // -------------------------
  // Add Documents
  // -------------------------

  def addDocumentsMap(
      collection: String,
      ids: List[String],
      docs: List[String],
      metas: List[Map[String, String]]
  ): Either[String, Json] =
    val mjson = metas.map { m =>
      Json.obj(m.toSeq.map { case (k, v) => (k, Json.fromString(v)) }*)
    }
    addDocuments(collection, ids, docs, mjson)

  def addDocuments(
      collection: String,
      ids: List[String],
      docs: List[String],
      metas: List[Json]
  ): Either[String, Json] =
    val embOpt: Option[List[Array[Float]]] =
      if embeddingEngine.mode == EmbeddingMode.None then None
      else
        try embeddingEngine.embed(docs).unsafeRunSync()
        catch case _: Throwable => None

    val embJson =
      embOpt.map { list =>
        Json.fromValues(list.map { vec =>
          Json.fromValues(vec.toList.map(Json.fromFloatOrString))
        })
      }

    val payload =
      Json.obj(
        "ids"        -> Json.fromValues(ids.map(Json.fromString)),
        "documents"  -> Json.fromValues(docs.map(Json.fromString)),
        "metadatas"  -> Json.fromValues(metas)
      ).deepMerge(
        embJson.map(e => Json.obj("embeddings" -> e)).getOrElse(Json.obj())
      )

    postJson(s"collections/$collection/add", payload).unsafeRunSync()

  // -------------------------
  // Passage conversion
  // -------------------------

  private def toRawPassages(resp: ChromaResponse): List[RawPassage] =
    val r = resp.results
    val ids = r.ids.headOption.getOrElse(Nil)
    val docs = r.documents.headOption.getOrElse(Nil)
    val metas = r.metadatas.headOption.getOrElse(Nil)
    val dists = r.distances.headOption.getOrElse(Nil)

    ids.zipWithIndex.map { case (id, idx) =>
      val text = docs.lift(idx).getOrElse("")
      val meta = metas.lift(idx)
        .flatMap(_.asObject.map(_.toMap.map { case (k, v) => (k, v.toString) }))
        .getOrElse(Map.empty)

      val dist = dists.lift(idx).getOrElse(Double.MaxValue)

      RawPassage(id, text, meta, dist)
    }

  // -------------------------
  // Search
  // -------------------------

  /** Text-only search */
  def search(collection: String, text: String, n: Int): Either[String, List[RawPassage]] =
    val payload =
      Json.obj(
        "query_texts" -> Json.arr(Json.fromString(text)),
        "n_results"   -> Json.fromInt(n),
        "include" -> Json.arr(
          Json.fromString("documents"),
          Json.fromString("distances"),
          Json.fromString("metadatas")
        )
      )

    postJson(s"collections/$collection/query", payload).unsafeRunSync() match
      case Left(err) =>
        Left(err)
      case Right(json) =>
        json.as[ChromaResponse] match
          case Left(decErr) => Left(decErr.getMessage)
          case Right(resp)  => Right(toRawPassages(resp))

  /** Hybrid search (query_texts + query_embeddings) */
  def hybridSearch(collection: String, text: String, n: Int): Either[String, List[RawPassage]] =
    val embOpt =
      if embeddingEngine.mode == EmbeddingMode.None then None
      else embeddingEngine.embed(List(text)).unsafeRunSync()

    val payloadBase =
      Json.obj(
        "query_texts" -> Json.arr(Json.fromString(text)),
        "include" -> Json.arr(
          Json.fromString("documents"),
          Json.fromString("distances"),
          Json.fromString("metadatas")
        ),
        "n_results" -> Json.fromInt(n)
      )

    val payload =
      embOpt match
        case Some(list) if list.nonEmpty =>
          val emb = list.head
          payloadBase.deepMerge(
            Json.obj(
              "query_embeddings" ->
                Json.arr(Json.fromValues(emb.toList.map(Json.fromFloatOrString)))
            )
          )
        case _ =>
          payloadBase

    postJson(s"collections/$collection/query", payload).unsafeRunSync() match
      case Left(err) => Left(err)
      case Right(json) =>
        json.as[ChromaResponse] match
          case Left(decErr) => Left(decErr.getMessage)
          case Right(resp)  => Right(toRawPassages(resp))

  def countDocuments(name: String): IO[Int] =
    val uriPath = s"collections/$name/count"
    retryIO(
      getJson(uriPath).map {
        case Right(json) =>
          // Expected response: { "count": <number> }
          json.hcursor.get[Int]("count") match
            case Right(c) => c
            case Left(_)  => 0 // defensive default: treat as empty
        case Left(err) =>
          // Propagate as failure so caller can decide how to handle (Auto mode is best-effort)
          throw new RuntimeException(s"countDocuments failed: $err")
      },
      s"GET $uriPath"
    )

object ChromaClient:
  def fromEnv(embedding: EmbeddingEngine): ChromaClient =
    // VectorDB endpoint (Chroma or other backend)
    val endpoint =
      sys.props.get("SIE_VECTORDB_ENDPOINT")
        .orElse(sys.env.get("SIE_VECTORDB_ENDPOINT"))
        .getOrElse("http://sie-embedding:8081")
    new ChromaClient(endpoint, embedding)

  /**
   * Create ChromaClient from an explicit endpoint.
   * Used by higher-level VectorDB factories (e.g. DEV / non-Docker execution).
   */
  def fromEndpoint(endpoint: String, embedding: EmbeddingEngine): ChromaClient = {
    new ChromaClient(endpoint, embedding)
  }
