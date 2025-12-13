package org.simplemodeling.sie.vectordb

import cats.effect.IO
import cats.syntax.flatMap.*

/**
 * VectorDbClientRouter
 *
 * Composite / Router implementation of VectorDbClient.
 *
 * This class represents a *logical* VectorDB frontend that
 * delegates operations to underlying VectorDbClient instances
 * according to a routing policy.
 *
 * Design intent:
 *   - Separate application logic from backend selection
 *   - Support read / write VectorDB split
 *   - Enable gateway-style VectorDB architectures
 *
 * Routing principles:
 *   - collectionExists → write-side semantics
 *   - upsert            → write backend
 *   - query             → read backend
 *
 * This router MUST contain all VectorDB routing decisions.
 * Application-level components (RagServiceMain, IndexInitializer,
 * HtmlIndexer) MUST NOT branch on backend type or mode.
 *
 * Typical use cases:
 *   - Use ChromaDbClient for local persistent indexing
 *   - Use SieEmbeddingVectorDbClient as a read-only search gateway
 *   - Switch VectorDB behavior via environment variables
 *   - Compose heterogeneous VectorDB backends safely
 *
 * NOTE:
 *   This router does not implement VectorDB semantics itself.
 *   It only delegates responsibilities to capability-compatible
 *   VectorDbClient implementations.
 *
 * @since   Dec. 12, 2025
 * @version Dec. 13, 2025
 * @author  ASAMI, Tomoharu
 */
final class VectorDbClientRouter(
    existsClient: VectorDbWriteClient,
    writeClient: VectorDbWriteClient,
    readClient: VectorDbReadClient
) extends VectorDbClient {

    override def collectionExists(collection: String): IO[Boolean] =
        existsClient.collectionExists(collection)

    override def createCollection(collection: String): IO[Unit] =
        writeClient.createCollection(collection)

    override def upsert(
        collection: String,
        records: List[VectorRecord]
    ): IO[Unit] =
        writeClient.upsert(collection, records)

    override def query(
        collection: String,
        vector: Vector,
        topK: Int
    ): IO[List[VectorMatch]] =
        readClient.query(collection, vector, topK)

    override def assertReachable(): IO[Unit] =
      // Delegate to a full VectorDbClient implementation
      (existsClient match {
        case c: VectorDbClient => c.assertReachable()
        case _                 => IO.unit
      })
}

object VectorDbClientRouter {

    /**
     * Create a router that delegates all read and write operations
     * to a single VectorDbClient backend.
     *
     * This mode is suitable for:
     *   - Simple deployments
     *   - Local development
     *   - Backends that support both read and write operations
     */
    def single(client: VectorDbClient): VectorDbClient =
        new VectorDbClientRouter(
            existsClient = client,
            writeClient  = client,
            readClient   = client
        )

    /**
     * Create a router with separate write and read backends.
     *
     * Typical use case:
     *   - write → local persistent VectorDB (e.g. Chroma)
     *   - read  → remote gateway or optimized search service
     *
     * The write backend is also used for collection existence checks,
     * as collection lifecycle is considered a write-side concern.
     */
    def readWrite(
        write: VectorDbWriteClient,
        read: VectorDbReadClient
    ): VectorDbClient =
        new VectorDbClientRouter(
            existsClient = write,
            writeClient  = write,
            readClient   = read
        )

    /**
     * Create a VectorDbClientRouter based on environment variables.
     *
     * Supported modes:
     *   - chroma
     *       read  = chroma
     *       write = chroma
     *
     *   - sie-embedding
     *       read  = sie-embedding
     *       write = sie-embedding
     *       (only valid if sie-embedding supports write operations)
     *
     *   - mixed
     *       read  = sie-embedding
     *       write = chroma
     *
     * Environment variables:
     *   - SIE_VECTORDB_ROUTER_MODE
     *
     * This method centralizes all runtime VectorDB wiring logic.
     * No other component should interpret VectorDB mode flags.
     */
    def fromEnv(
        chroma: VectorDbClient,
        sieEmbedding: VectorDbClient
    ): VectorDbClient =
        sys.env
            .get("SIE_VECTORDB_ROUTER_MODE")
            .map(_.toLowerCase) match
            case Some("mixed") =>
                readWrite(
                    write = chroma,
                    read  = sieEmbedding
                )

            case Some("sie-embedding") =>
                single(sieEmbedding)

            case Some("chroma") =>
                single(chroma)

            case _ =>
                // default: safest option
                single(chroma)
}
