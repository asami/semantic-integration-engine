package org.simplemodeling.sie.init

import org.simplemodeling.sie.fuseki.FusekiClient
import org.simplemodeling.sie.chroma.ChromaClient
import org.simplemodeling.sie.embedding.EmbeddingEngine

import io.circe.Json
import cats.effect.unsafe.implicits.global
import cats.effect.IO

/*
 * IndexInitializer
 *
 * This component initializes the ChromaDB collection by:
 *  1. Loading all domain documents from Fuseki or JSON-LD.
 *  2. Extracting texts, metadata, and identifiers.
 *  3. Generating embeddings if required.
 *  4. Upserting all documents into ChromaDB.
 *
 * The actual document extraction logic depends on the structure
 * of FusekiClient and JSON-LD documents, so this file provides
 * a clear extension point.
 *
 * @since   Dec.  4, 2025
 * @version Dec.  4, 2025
 * @author  ASAMI
 */
object IndexInitializer:

  /*
   * Simple data structure representing a document to index.
   * Each entry corresponds to one knowledge item in Fuseki/JSON-LD.
   */
  case class DocEntry(
    id: String,
    title: String,
    description: String,
    content: String,
    metadata: Map[String, String]
  ):
    def toDocumentText: String =
      List(title, description, content)
        .filter(_.nonEmpty)
        .mkString("\n")

  /*
   * Main entry point: run the full initialization.
   */
  def run(
    fuseki: FusekiClient,
    chroma: ChromaClient,
    embedding: EmbeddingEngine,
    collection: String = "simplemodeling"
  ): Json =
    try
      // ------------------------------------------------------------
      // Step 1: Load documents (placeholder; implement as needed)
      // ------------------------------------------------------------
      val docs: List[DocEntry] = loadDocuments(fuseki)

      val ids        = docs.map(_.id)
      val documents  = docs.map(_.toDocumentText)
      val metadatas  = docs.map(_.metadata)

      // ------------------------------------------------------------
      // Step 2: Generate embeddings (optional)
      // ------------------------------------------------------------
      val vectors: Option[List[Array[Float]]] =
        if embedding.mode.toString.toLowerCase == "none" then
          None
        else
          Some(
            embedding
              .embed(documents)
              .unsafeRunSync()
              .getOrElse(Nil)
          )

      // ------------------------------------------------------------
      // Step 3: Upsert into Chroma (embeddings are optional)
      // ------------------------------------------------------------
      val result = chroma.addDocumentsMap(
        collection,
        ids,
        documents,
        metadatas
      )

      Json.obj(
        "status" -> Json.fromString("ok"),
        "details" -> result.getOrElse(Json.Null)
      )

    catch
      case e: Throwable =>
        Json.obj(
          "error" -> Json.fromString(e.toString)
        )

  /*
   * Placeholder implementation:
   * Extracts knowledge documents from Fuseki.
   *
   * Replace this with real logic:
   *   - fuseki.searchAllEntities()
   *   - fuseki.listDocuments()
   *   - or JSON-LD loader if Fuseki not available
   */
  private def loadDocuments(fuseki: FusekiClient): List[DocEntry] =
    val concepts = fuseki.searchConcepts("").unsafeRunSync()

    concepts.map { c =>
      val id    = c.uri
      val title = c.title
      val desc  = c.description

      val metadata = Map(
        "label" -> title,
        "description" -> desc
      )

      DocEntry(
        id = id,
        title = title,
        description = desc,
        content = "",
        metadata = metadata
      )
    }
