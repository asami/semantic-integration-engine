package org.simplemodeling.rag.service

import org.simplemodeling.rag.fuseki.FusekiClient
import org.simplemodeling.rag.chroma.ChromaClient
import io.circe.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 20, 2025
 * @author  ASAMI, Tomoharu
 */
case class RagResult(
  concepts: Json,
  passages: Json
) derives Encoder

class RagService(
  fuseki: FusekiClient,
  chroma: ChromaClient
):
  def run(query: String): RagResult =
    RagResult(
      concepts = fuseki.searchConcepts(query).getOrElse(Json.arr()),
      passages = chroma.search("simplemodeling", query, 5).getOrElse(Json.arr())
    )
