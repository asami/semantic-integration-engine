package org.simplemodeling.sie.vectordb

/**
 * VectorDbClient is an abstraction over vector database backends.
 *
 * SIE depends only on this trait and must not depend on any concrete
 * vector database implementation (Chroma, Qdrant, Weaviate, etc.).
 *
 * Implementations may access:
 *   - a direct vector database (e.g. ChromaDB)
 *   - a proxy / gateway (e.g. sie-embedding VectorDB API)
 *
 * This abstraction allows backend replacement without changing
 * SIE application logic.
 * 
 * @since   Dec. 12, 2025
 * @version Dec. 13, 2025
 * @author  ASAMI, Tomoharu
 */
trait VectorDbClient extends VectorDbReadClient, VectorDbWriteClient:

  /**
   * Best-effort reachability check for VectorDB.
   *
   * Semantics:
   *   - Verifies that the VectorDB endpoint is reachable.
   *   - MUST NOT check collection existence or data contents.
   *
   * Design notes:
   *   - VectorDB is a derived store; reachability is advisory only.
   *   - Absence of collections or data is NOT an error here.
   *   - This method MUST NOT throw fatal errors for SIE startup.
   */
  def assertReachable(): cats.effect.IO[Unit]
