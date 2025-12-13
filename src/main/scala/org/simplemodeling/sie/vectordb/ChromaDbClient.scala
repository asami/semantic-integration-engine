package org.simplemodeling.sie.vectordb

import cats.effect.IO
import cats.syntax.all.*
import org.simplemodeling.sie.chroma.ChromaClient
import org.simplemodeling.sie.embedding.EmbeddingEngine

/**
 * ChromaDbClient
 *
 * VectorDbClient implementation backed by ChromaDB.
 * This client adapts the low-level ChromaClient to the
 * VectorDbClient abstraction used by SIE.
 * 
 * @since   Dec. 12, 2025
 * @version Dec. 13, 2025
 * @author  ASAMI, Tomoharu
 */
final class ChromaDbClient(
    chroma: ChromaClient
) extends VectorDbClient {

    private def liftEither[A](either: Either[String, A]): IO[A] =
        IO.fromEither(either.leftMap(err => new RuntimeException(err)))

    override def collectionExists(collection: String): IO[Boolean] =
        liftEither(
            chroma.collectionExists(collection)
        )

    override def createCollection(collection: String): IO[Unit] =
        liftEither(
            chroma.createCollection(collection).map(_ => ())
        )

    override def upsert(
        collection: String,
        records: List[VectorRecord]
    ): IO[Unit] =
        if records.isEmpty then
            IO.unit
        else
            val ids =
                records.map(_.id)

            val docs =
                records.map { record =>
                    record.text
                        .orElse(record.metadata.get("text"))
                        .getOrElse("")
                }

            val metas =
                records.map(_.metadata)

            liftEither(
                chroma.addDocumentsMap(collection, ids, docs, metas).map(_ => ())
            )

    override def query(
        collection: String,
        vector: Vector,
        topK: Int
    ): IO[List[VectorMatch]] =
        // ChromaClient currently performs embedding internally,
        // so this method is intentionally not supported yet.
        IO.raiseError(
            new UnsupportedOperationException(
                "Vector-based query is not supported by ChromaDbClient. Use text-based search instead."
            )
        )

    override def assertReachable(): IO[Unit] =
      // Reachability check only:
      // perform a harmless exists call; result is ignored.
      liftEither(
        chroma.collectionExists("__reachability_check__").map(_ => ())
      )
}

object ChromaDbClient {

    /**
     * Factory method using environment configuration.
     */
    def fromEnv(embedding: EmbeddingEngine): ChromaDbClient =
        val chromaClient =
            ChromaClient.fromEnv(embedding)
        new ChromaDbClient(chromaClient)

    /**
     * Factory method using an explicit endpoint.
     * Primarily for DEV mode (non-Docker execution).
     */
    def fromEndpoint(
        endpoint: String,
        embedding: EmbeddingEngine
    ): ChromaDbClient = {
        val chromaClient =
            ChromaClient.fromEndpoint(endpoint, embedding)
        new ChromaDbClient(chromaClient)
    }
}
