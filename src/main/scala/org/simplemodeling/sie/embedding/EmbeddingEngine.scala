package org.simplemodeling.sie.embedding

import cats.effect.IO
import scala.concurrent.duration.*

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

  /**
   * Retry wrapper for embedding calls.
   *
   * @param attempts  Number of retry attempts.
   * @param delayMs   Milliseconds to wait between attempts.
   * @param action    IO action to retry.
   */
  protected def retryIO[T](attempts: Int, delayMs: Long)(action: IO[T]): IO[T] =
    action.handleErrorWith { e =>
      if attempts > 1 then
        IO.sleep(delayMs.millis) *> 
          retryIO(attempts - 1, delayMs)(action)
      else
        IO.raiseError(e)
    }

  /**
   * Wrapper for embedding with retry logic.
   *
   * Engines may call this instead of `embed` directly to ensure
   * embedding servers have booted up.
   */
  def embedWithRetry(
    texts: List[String],
    attempts: Int = 10,
    delayMs: Long = 500
  ): IO[Option[List[Array[Float]]]] =
    retryIO(attempts, delayMs)(embed(texts))

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
