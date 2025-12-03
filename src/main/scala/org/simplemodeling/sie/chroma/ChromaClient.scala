package org.simplemodeling.sie.chroma

import io.circe.*
import org.simplemodeling.sie.http.SimpleHttpClient
import org.simplemodeling.sie.embedding.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.nio.charset.StandardCharsets

/*
 * @since   Nov. 20, 2025
 *          Nov. 25, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
class ChromaClient(endpoint: String, embeddingEngine: EmbeddingEngine):
  private val baseUri: String = if endpoint.endsWith("/") then endpoint.dropRight(1) else endpoint

  private def getJson(path: String): IO[Either[String, Json]] =
    val parts = "chroma" +: path.split("/").toSeq
    val uriStr = s"$baseUri/${parts.mkString("/")}"
    IO.println(s"[ChromaClient] GET $uriStr") *>
      SimpleHttpClient.get(uriStr).map { resp =>
        io.circe.parser.parse(resp.body).left.map(_.getMessage)
      }

  private def postJson(path: String, json: Json): IO[Either[String, Json]] =
    val parts = "chroma" +: path.split("/").toSeq
    val uriStr = s"$baseUri/${parts.mkString("/")}"
    val bodyStr = json.noSpaces
    IO.println(s"[ChromaClient] POST $uriStr payload=${bodyStr.take(200)}...") *>
      SimpleHttpClient.post(uriStr, bodyStr).map { resp =>
        io.circe.parser.parse(resp.body).left.map(_.getMessage)
      }

  private def deletePath(path: String): IO[Either[String, Json]] =
    val parts = "chroma" +: path.split("/").toSeq
    val uriStr = s"$baseUri/${parts.mkString("/")}"
    IO.println(s"[ChromaClient] DELETE $uriStr") *>
      SimpleHttpClient.delete(uriStr).map { resp =>
        io.circe.parser.parse(resp.body).left.map(_.getMessage)
      }

  def collectionExists(collection: String): Either[String, Boolean] =
    getJson(s"collections/$collection/exists").unsafeRunSync() match
      case Right(json) =>
        val cursor = json.hcursor
        cursor.get[Boolean]("exists") match
          case Right(v) => Right(v)
          case Left(_)  =>
            println(s"[ChromaClient] collectionExists($collection): missing 'exists' field, treating as false")
            Right(false)
      case Left(err) =>
        val lowered = err.toLowerCase
        if lowered.contains("not found") || lowered.contains("does not exist") then
          println(s"[ChromaClient] collectionExists($collection): Not Found (treated as false). rawError=$err")
          Right(false)
        else
          println(s"[ChromaClient] collectionExists($collection): unexpected error: $err")
          Left(err)

  def createCollection(collection: String): Either[String, Json] =
    postJson(s"collections/$collection/create", Json.obj()).unsafeRunSync()

  def listCollections(): Either[String, Json] =
    Left("listCollections is not supported by the current Python-based embedding server")

  def deleteCollection(collection: String): Either[String, Json] =
    Left("deleteCollection is not supported by the current Python-based embedding server")

  def addDocuments(
      collection: String,
      ids: List[String],
      documents: List[String],
      metadatas: List[Json]
  ): Either[String, Json] =
    import cats.effect.unsafe.implicits.global

    val batchSize =
      sys.env.get("SIE_CHROMA_BATCH_SIZE").flatMap(_.toIntOption).getOrElse(1)

    val grouped = ids.zip(documents).zip(metadatas).grouped(batchSize).toList

    val results = grouped.zipWithIndex.map { case (group, batchIndex) =>
      val batchIds   = group.map(_._1._1)
      val batchDocs  = group.map(_._1._2)
      val batchMetas = group.map(_._2)

      val batchEmbs: Option[List[Array[Float]]] =
        if embeddingEngine.mode == EmbeddingMode.None then None
        else
          try
            embeddingEngine.embed(batchDocs).unsafeRunSync()
          catch
            case e: Throwable =>
              println(s"[ChromaClient] EMBEDDING_EXCEPTION(batch=$batchIndex): ${e.getMessage}")
              None

      val embJson =
        batchEmbs match
          case None => None
          case Some(list) =>
            Some(Json.fromValues(list.map { vec =>
              val floats: List[Float] = vec.toList
              Json.fromValues(floats.map(f => Json.fromFloatOrString(f)))
            }))

      val payload =
        Json.obj(
          "collection" -> Json.fromString(collection),
          "ids"        -> Json.fromValues(batchIds.map(Json.fromString)),
          "documents"  -> Json.fromValues(batchDocs.map(Json.fromString)),
          "metadatas"  -> Json.fromValues(batchMetas)
        ).deepMerge(
          embJson.map(e => Json.obj("embeddings" -> Json.fromValues(e.asArray.get))).getOrElse(Json.obj())
        )

      val payloadStr = payload.noSpaces
      val size = payloadStr.getBytes(StandardCharsets.UTF_8).length
      println(s"[ChromaClient] Batch $batchIndex payload size: $size")

      Thread.sleep(50)

      postJson(s"collections/$collection/add", payload).unsafeRunSync() match
        case Left(err) =>
          println(s"[ChromaClient] Batch $batchIndex failed: $err")
          Left(err)
        case Right(json) =>
          println(s"[ChromaClient] Batch $batchIndex OK")
          Right(json)
    }

    results.find(_.isLeft) match
      case Some(left) => left
      case None       => results.lastOption.getOrElse(Right(Json.Null))

  def search(collection: String, text: String, n: Int): Option[Json] =
    embeddingEngine.mode match
      case EmbeddingMode.None =>
        // Embedding disabled → treat as empty result instead of failing
        Some(
          Json.obj(
            "results" -> Json.arr()
          )
        )
      case _ =>
        val embOpt: Option[List[Array[Float]]] =
          embeddingEngine.embed(List(text)).unsafeRunSync()

        embOpt match
          case None => None
          case Some(emb) =>
            if emb.isEmpty then None
            else
              val payload = Json.obj(
                "collection" -> Json.fromString(collection),
                "query_embeddings" ->
                  Json.arr(
                    Json.fromValues(
                      emb.head.toList.map(f => Json.fromFloatOrString(f))
                    )
                  ),
                "n_results" -> Json.fromInt(n)
              )
              postJson(s"collections/$collection/query", payload).map(_.toOption).unsafeRunSync()

  def search(
      collection: String,
      text: String,
      n: Int,
      vec: Option[Array[Float]]
  ): Option[Json] =
    vec match
      case None =>
        // No vector → return empty results
        Some(Json.obj("results" -> Json.arr()))
      case Some(v) =>
        val payload = Json.obj(
          "collection" -> Json.fromString(collection),
          "query_embeddings" ->
            Json.arr(
              Json.fromValues(
                v.toList.map(f => Json.fromFloatOrString(f))
              )
            ),
          "n_results" -> Json.fromInt(n)
        )
        postJson(s"collections/$collection/query", payload).map(_.toOption).unsafeRunSync()

object ChromaClient:
  def fromEnv(embeddingEngine: EmbeddingEngine): ChromaClient =
    val endpoint = sys.env.getOrElse("SIE_EMBEDDING_ENDPOINT", "http://sie-embedding:8081")
    new ChromaClient(endpoint, embeddingEngine)
