package org.simplemodeling.sie.vectordb

import io.circe.Decoder
import io.circe.HCursor

/**
 * Shared data structures for the VectorDB abstraction layer.
 */

/**
 * Simple value class representing an embedding vector.
 *
 * Using an explicit type instead of Scala collections prevents
 * accidental coupling with backend-specific representations and
 * enables us to evolve the underlying structure later.
 */
final case class Vector(values: List[Double])

/**
 * Logical record stored in a VectorDB.
 *
 * @param id
 *   Stable identifier referencing the source document or chunk
 * @param text
 *   Optional raw text used by backends that perform embedding internally
 * @param vector
 *   Optional embedding vector for pre-computed ingestion workflows
 * @param metadata
 *   Arbitrary metadata associated with the record
 */
final case class VectorRecord(
    id: String,
    text: Option[String] = None,
    vector: Option[Vector] = None,
    metadata: Map[String, String] = Map.empty
)

/**
 * Match result returned by a similarity search.
 */
final case class VectorMatch(
    id: String,
    score: Double,
    text: Option[String] = None,
    metadata: Map[String, String] = Map.empty
)

object VectorMatch {

    /**
     * Lightweight Circe decoder for sie-embedding VectorDB responses.
     */
    given Decoder[VectorMatch] = new Decoder[VectorMatch] {
        override def apply(c: HCursor) =
            for
                id <- c.get[String]("id")
                score <- c.get[Double]("score")
                text <- c.get[Option[String]]("text")
                metadata <- c
                    .get[Option[Map[String, String]]]("metadata")
                    .map(_.getOrElse(Map.empty))
            yield VectorMatch(
                id = id,
                score = score,
                text = text,
                metadata = metadata
            )
    }
}
