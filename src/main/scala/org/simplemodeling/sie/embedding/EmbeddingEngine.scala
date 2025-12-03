package org.simplemodeling.sie.embedding

import cats.effect.IO

/**
 * Unified interface for embedding generation.
 *
 * Supports:
 *   - OpenAI-based embedding
 *   - OSS local embedding (e.g., bge-small-en-v1.5)
 *   - No-embedding (BM25-only mode)
 *
 * @since   Dec.  2, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
enum EmbeddingMode:
  case None, OpenAI, OSS

trait EmbeddingEngine:
  import EmbeddingMode.*

  /** Current embedding mode. */
  def mode: EmbeddingMode

  /** Returns true when this engine actually produces embeddings. */
  def isEnabled: Boolean = mode != EmbeddingMode.None

  /**
   * Generate embeddings for the given texts.
   *
   * Each input text must correspond to exactly one embedding vector.
   */
  def embed(texts: List[String]): IO[Option[List[Array[Float]]]]

object EmbeddingEngine:

  /**
   * Convenience factory for No-Embedding mode.
   */
  val none: EmbeddingEngine = NoneEngine

/**
 * No-embedding implementation: returns empty vectors for all inputs.
 *
 * This is used when we want to disable embedding completely and rely on
 * BM25 / keyword search only.
 */
object NoneEngine extends EmbeddingEngine:
  override val mode: EmbeddingMode = EmbeddingMode.None

  override def embed(texts: List[String]): IO[Option[List[Array[Float]]]] =
    IO.pure(Some(texts.map(_ => Array.emptyFloatArray)))
