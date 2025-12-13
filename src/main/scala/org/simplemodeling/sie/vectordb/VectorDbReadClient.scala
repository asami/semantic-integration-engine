package org.simplemodeling.sie.vectordb

import cats.effect.IO

/**
 * VectorDbReadClient
 *
 * Read-only capability interface for VectorDB access.
 *
 * This trait represents the ability to perform semantic
 * vector-based retrieval (search / query) against a VectorDB.
 *
 * Design intent:
 *   - Express read responsibility explicitly
 *   - Allow read-only VectorDB backends
 *   - Support read/write split via router-based composition
 *
 * IMPORTANT:
 *   This trait MUST NOT expose any write or lifecycle operations
 *   (e.g. upsert, delete, createCollection).
 *
 * Typical implementations:
 *   - ChromaDbClient                (read + write)
 *   - SieEmbeddingVectorDbClient    (read-only gateway)
 *   - Future SaaS VectorDB clients  (often read-only)
 * 
 * @since   Dec. 12, 2025
 * @version Dec. 12, 2025
 * @author  ASAMI, Tomoharu
 */
trait VectorDbReadClient {

    /**
     * Execute a semantic vector query.
     *
     * This method performs similarity search using
     * vector embeddings and returns ranked results.
     *
     * The exact retrieval strategy (cosine similarity,
     * inner product, ANN index, etc.) is backend-specific
     * and MUST be hidden behind this abstraction.
     *
     * Contract:
     *   - Pure read operation
     *   - No side effects on VectorDB state
     *   - Safe to call repeatedly
     *
     * Error handling:
     *   - Implementations should surface failures via IO
     *   - Callers may choose to degrade gracefully
     *
     * @param collection
     *   Logical collection / namespace to query.
     * @param vector
     *   Embedding vector representing the search query.
     * @param topK
     *   Maximum number of matches to return.
     */
    def query(
        collection: String,
        vector: Vector,
        topK: Int
    ): IO[List[VectorMatch]]

}
