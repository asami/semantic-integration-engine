package org.simplemodeling.sie.vectordb

import cats.effect.IO

/**
 * VectorDbWriteClient
 *
 * Write-capability interface for VectorDB access.
 *
 * This trait represents the ability to modify VectorDB state,
 * such as creating or updating vector indexes.
 *
 * Design intent:
 *   - Express write responsibility explicitly
 *   - Isolate VectorDB lifecycle semantics
 *   - Prevent write operations on read-only backends
 *
 * IMPORTANT:
 *   This trait owns all assumptions about VectorDB mutability.
 *   Application-level components MUST NOT manage
 *   VectorDB lifecycle or persistence details.
 *
 * Typical implementations:
 *   - ChromaDbClient             (local persistent VectorDB)
 *   - Future managed VectorDBs   (Qdrant, Weaviate, etc.)
 *
 * Non-implementations:
 *   - Read-only gateways (e.g. sie-embedding read-only mode)
 * 
 * @since   Dec. 12, 2025
 * @version Dec. 12, 2025
 * @author  ASAMI, Tomoharu
 */
trait VectorDbWriteClient {

    /**
     * Check whether a collection (or index / namespace)
     * exists in the VectorDB.
     *
     * This method is used by index orchestration logic
     * (e.g. IndexInitializer) to make safe, idempotent decisions.
     *
     * Contract:
     *   - Pure metadata query
     *   - No side effects
     *   - Safe to call repeatedly
     *
     * Backend notes:
     *   - "collection" may map to different concepts:
     *       * Chroma: collection
     *       * Qdrant: collection
     *       * Weaviate: class
     *
     * The abstraction intentionally hides these differences.
     *
     * @param name
     *   Logical collection name
     *
     * @return
     *   true  if the collection exists
     *   false otherwise
     */
    def collectionExists(
        name: String
    ): IO[Boolean]

    /**
     * Creates a collection if it does not exist.
     *
     * Implementations should provide an idempotent operation.
     */
    def createCollection(
        name: String
    ): IO[Unit]

    /**
     * Insert or update vector records into a collection.
     *
     * This method MUST be idempotent.
     *
     * Design rationale:
     *   - Allows full or incremental re-indexing
     *   - Prevents backend-specific existence checks
     *   - Enables safe retry on partial failures
     *
     * Contract:
     *   - Creates the collection implicitly if required
     *     (or ensures it exists)
     *   - Overwrites existing records with the same IDs
     *   - Does NOT fail if the collection already exists
     *
     * Error handling:
     *   - Fatal backend errors should fail the IO
     *   - Callers may recover and continue in degraded mode
     *
     * @param collection
     *   Logical collection name
     *
     * @param records
     *   Vector records including:
     *     - unique ID
     *     - embedding vector
     *     - associated metadata
     */
    def upsert(
        collection: String,
        records: List[VectorRecord]
    ): IO[Unit]

}
