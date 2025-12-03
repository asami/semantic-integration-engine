package org.simplemodeling.sie.indexer

import cats.effect._
import cats.syntax.all._
import org.simplemodeling.sie.chroma._
import io.circe.Json

/**
 * Index HTML pages from SimpleModeling.org into Chroma.
 *
 * - collection name: simplemodeling
 * - document id: URL + "#chunk-$i"
 *
 * @since   Dec.  2, 2025
 * @version Dec.  2, 2025
 * @author  ASAMI, Tomoharu
 */
object HtmlIndexer {

  /** Collection name in Chroma */
  val CollectionName: String = "simplemodeling"

  val DefaultChunkSize: Int = 800
  val DefaultBatchSize: Int = 20

  /** Chunk size for splitting HTML text */
  private val ChunkSize: Int = DefaultChunkSize

  /** Structured chunk */
  final case class HtmlChunk(
      id: String,
      url: String,
      index: Int,
      text: String
  )

  /** Main entry point */
  def indexAll(
      chroma: ChromaClient,
      siteJsonldUrl: String = "https://www.simplemodeling.org/site.jsonld",
      force: Boolean = false
  ): IO[Unit] = {

    if (!force) {
      chroma.collectionExists(CollectionName) match {
        case Right(true) =>
          return IO.println(
            s"[HtmlIndexer] Collection '$CollectionName' already exists. Skipping indexing."
          )
        case Right(false) =>
          () // continue
        case Left(errorMessage) =>
          return IO.println(
            s"[HtmlIndexer] Could not verify collection existence: $errorMessage"
          )
      }
    }

    val crawler = SiteJsonCrawler
    val fetcher = HtmlFetcher()

    for {
      urls <- crawler.fetchPageUrls(siteJsonldUrl)
      _    <- IO.println(s"[HtmlIndexer] Found ${urls.size} pages.")

      chunks <- urls.zipWithIndex
        .traverse { case (url, idx) =>
          IO.println(s"[HtmlIndexer] Preparing to fetch $url with chunk size $DefaultChunkSize and batch size $DefaultBatchSize") *>
          indexSinglePage(fetcher, url, idx)
        }
        .map(_.flatten)

      _ <- IO.println(s"[HtmlIndexer] Total chunks to index: ${chunks.size}")

      _ <- indexChunksIntoChroma(chroma, chunks)
    } yield ()
  }

  /** Index one HTML page */
  private def indexSinglePage(
      fetcher: HtmlFetcher,
      url: String,
      pageIndex: Int
  ): IO[List[HtmlChunk]] = {

    for {
      _    <- IO.println(s"[HtmlIndexer] Fetching $url")
      html <- fetcher.fetch(url)

      text = HtmlExtractor.extractMainText(html)

      chunks = splitIntoChunks(text, ChunkSize).zipWithIndex.map {
        case (chunkText, idx) =>
          HtmlChunk(
            id = s"$url#chunk-$idx",
            url = url,
            index = idx,
            text = chunkText
          )
      }

      _ <- IO.println(
             s"[HtmlIndexer] Extracted ${chunks.size} chunks from $url"
           )

    } yield chunks
  }

  /** Split long text into chunks */
  private def splitIntoChunks(
      text: String,
      size: Int
  ): List[String] = {
    text.grouped(size).toList
  }

  /** Batch indexing with retry */
  private def indexChunksIntoChroma(
      chroma: ChromaClient,
      chunks: List[HtmlChunk]
  ): IO[Unit] = {

    if (chunks.isEmpty) {
      return IO.println("[HtmlIndexer] No chunks to index.")
    }

    val batchSize = DefaultBatchSize

    val batched: List[List[HtmlChunk]] = chunks.grouped(batchSize).toList

    def processBatch(batch: List[HtmlChunk], attempt: Int): IO[Unit] = {
      val ids       = batch.map(_.id)
      val documents = batch.map(_.text)
      val metadatas = batch.map { c =>
        Json.obj(
          "url"       -> Json.fromString(c.url),
          "chunk_idx" -> Json.fromInt(c.index)
        )
      }

      val batchInfo =
        s"[HtmlIndexer] Batch info: size=${batch.size}, attempt=$attempt, idRange=${ids.headOption.getOrElse("none")} .. ${ids.lastOption.getOrElse("none")}"

      IO.println(batchInfo) *>
        IO.println(s"[HtmlIndexer] Preview first 2 docs: " +
          documents.take(2).mkString(" / ")
        ) *>
        IO.fromEither(
          chroma
            .addDocuments(
              collection = CollectionName,
              ids = ids,
              documents = documents,
              metadatas = metadatas
            )
            .leftMap(err => new RuntimeException(err))
            .map(_ => ())
        ).attempt.flatMap {
          case Right(_) =>
            IO.println("[HtmlIndexer] Batch OK.")

          case Left(err) if attempt < 3 =>
            IO.println(
              s"[HtmlIndexer] Batch failed (attempt=$attempt). Retrying... error=${err.getMessage}"
            ) *>
            IO.println(s"[HtmlIndexer] StackTrace:\n${err.getStackTrace.mkString("\n")}") *>
            processBatch(batch, attempt + 1)

          case Left(err) =>
            IO.println(
              s"[HtmlIndexer] Batch permanently failed: ${err.getMessage}"
            ) *>
            IO.println(s"[HtmlIndexer] Final StackTrace:\n${err.getStackTrace.mkString("\n")}")
        }
    }

    for {
      _ <- IO.println(s"[HtmlIndexer] Start batch indexing: ${batched.size} batches.")
      _ <- batched.traverse(batch => processBatch(batch, 1))
      _ <- IO.println("[HtmlIndexer] All batches processed.")
    } yield ()
  }

}
