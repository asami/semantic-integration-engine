package org.simplemodeling.sie.vectordb

import cats.effect.IO
import cats.syntax.all.*
import org.simplemodeling.sie.http.SimpleHttpClient
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

/**
 * SieEmbeddingVectorDbClient
 *
 * VectorDbClient implementation that talks to the VectorDB API
 * exposed by sie-embedding.
 *
 * This client does NOT assume any specific backend implementation
 * (Chroma, Qdrant, etc.). It relies solely on the VectorDB API
 * contract provided by sie-embedding.
 *
 * Expected API (future-facing):
 *
 *   GET  /vectordb/collections/{name}/exists
 *   POST /vectordb/collections/{name}
 *   POST /vectordb/upsert
 *   POST /vectordb/query
 * 
 * Usage in SIE:
 *
 *   This client is typically NOT instantiated directly by application code.
 *   It is assembled and validated by VectorDbClientFactory.
 *
 *   Typical usage patterns:
 *
 *     1) Read-only gateway mode
 *
 *        SIE_VECTORDB_BACKEND=auto
 *        SIE_VECTORDB_ROUTER_MODE=mixed
 *
 *        - read  → SieEmbeddingVectorDbClient
 *        - write → ChromaDbClient
 *
 *        In this mode, sie-embedding acts as a semantic search gateway,
 *        while local VectorDB handles persistent indexing.
 *
 *     2) Full VectorDB mode (if write API is enabled)
 *
 *        SIE_VECTORDB_BACKEND=sie-embedding
 *
 *        - read  → SieEmbeddingVectorDbClient
 *        - write → SieEmbeddingVectorDbClient
 *
 *        This mode is only valid if sie-embedding explicitly
 *        supports VectorDB write operations.
 *
 *   Capability validation:
 *
 *     - VectorDbClientFactory validates read/write capabilities
 *       at startup using runtime checks.
 *     - Invalid configurations fail fast during initialization.
 *
 * Design rule:
 *
 *   This class MUST remain a thin HTTP client that reflects
 *   the official sie-embedding VectorDB API contract.
 *   It MUST NOT embed backend-specific assumptions.
 *
 * @since   Dec. 12, 2025
 * @version Dec. 13, 2025
 * @author  ASAMI, Tomoharu
 */
final class SieEmbeddingVectorDbClient(
    endpoint: String
) extends VectorDbClient {

    private val baseUri =
        if endpoint.endsWith("/") then endpoint.dropRight(1) else endpoint

    override def collectionExists(collection: String): IO[Boolean] =
        val uri =
            s"$baseUri/vectordb/collections/$collection/exists"

        SimpleHttpClient.get(uri).flatMap { resp =>
            parse(resp.body) match
                case Left(err) =>
                    IO.raiseError(
                        new RuntimeException(err.getMessage)
                    )
                case Right(json) =>
                    IO.fromEither(
                        json.hcursor.get[Boolean]("exists")
                    )
        }

    override def createCollection(collection: String): IO[Unit] =
        val uri =
            s"$baseUri/vectordb/collections/$collection"

        SimpleHttpClient.post(uri, Json.obj().noSpaces).void

    override def upsert(
        collection: String,
        records: List[VectorRecord]
    ): IO[Unit] =
        if records.isEmpty then
            IO.unit
        else
            val recordsJsonIO =
                records.traverse { record =>
                    record.vector match
                        case Some(vec) =>
                            val metadataValues =
                                record.metadata ++ record.text
                                    .map(text => Map("text" -> text))
                                    .getOrElse(Map.empty)

                            val metadataJson =
                                Json.obj(
                                    metadataValues.toSeq.map {
                                        case (k, v) => (k, Json.fromString(v))
                                    }*
                                )

                            IO.pure(
                                Json.obj(
                                    "id" ->
                                        Json.fromString(record.id),
                                    "vector" ->
                                        Json.fromValues(
                                            vec.values.map(Json.fromDoubleOrNull)
                                        ),
                                    "metadata" -> metadataJson
                                )
                            )

                        case None =>
                            IO.raiseError(
                                new IllegalArgumentException(
                                    s"Vector is required for sie-embedding upsert (record id=${record.id})"
                                )
                            )
                }

            recordsJsonIO.flatMap { recordsJson =>
                val payload =
                    Json.obj(
                        "collection" ->
                            Json.fromString(collection),
                        "records" ->
                            Json.fromValues(recordsJson)
                    )

                val uri =
                    s"$baseUri/vectordb/upsert"

                SimpleHttpClient.post(uri, payload.noSpaces).void
            }

    override def query(
        collection: String,
        vector: Vector,
        topK: Int
    ): IO[List[VectorMatch]] =
        val payload =
            Json.obj(
                "collection" ->
                    Json.fromString(collection),
                "vector" ->
                    Json.fromValues(
                        vector.values.map(Json.fromDoubleOrNull)
                    ),
                "topK" ->
                    Json.fromInt(topK)
            )

        val uri =
            s"$baseUri/vectordb/query"

        SimpleHttpClient.post(uri, payload.noSpaces).flatMap { resp =>
            parse(resp.body) match
                case Left(err) =>
                    IO.raiseError(
                        new RuntimeException(err.getMessage)
                    )
                case Right(json) =>
                    IO.fromEither(
                        json.hcursor
                            .downField("matches")
                            .as[List[VectorMatch]]
                    )
        }

    override def assertReachable(): IO[Unit] =
      // Reachability check only:
      // call exists endpoint with a dummy collection name.
      collectionExists("__reachability_check__").void
}

object SieEmbeddingVectorDbClient {

    /**
     * Factory method using environment configuration.
     */
    def fromEnv(): SieEmbeddingVectorDbClient =
        val endpoint =
            sys.env.getOrElse(
                "SIE_VECTORDB_ENDPOINT",
                "http://sie-embedding:8081"
            )

        new SieEmbeddingVectorDbClient(endpoint)

    /**
     * Factory method using an explicit endpoint.
     * Primarily for DEV / non-Docker execution where service names
     * like `sie-embedding` must not be resolved.
     */
    def fromEndpoint(endpoint: String): SieEmbeddingVectorDbClient = {
        new SieEmbeddingVectorDbClient(endpoint)
    }
}
