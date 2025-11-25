package org.simplemodeling.sie.service

import org.simplemodeling.sie.fuseki.FusekiClient
import org.simplemodeling.sie.chroma.ChromaClient
import io.circe.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 25, 2025
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
    val concepts =
      try fuseki.searchConcepts(query).getOrElse(Json.arr())
      catch case e: Throwable =>
        Json.obj("error" -> Json.fromString(e.toString))

    val passages =
      try chroma.search("simplemodeling", query, 5).getOrElse(Json.arr())
      catch case e: Throwable =>
        Json.obj("error" -> Json.fromString(e.toString))

    RagResult(concepts, passages)
