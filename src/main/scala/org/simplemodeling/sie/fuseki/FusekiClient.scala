package org.simplemodeling.sie.fuseki

import sttp.client3.*
import sttp.client3.circe.*
import io.circe.Json

/*
 * @since   Nov. 20, 2025
 * @version Nov. 25, 2025
 * @author  ASAMI, Tomoharu
 */
class FusekiClient(endpoint: String = sys.env.getOrElse("FUSEKI_URL", "http://localhost:3030/ds")):
  private val url = s"$endpoint/query"
  private val backend = HttpClientSyncBackend()

  def searchConcepts(text: String): Option[Json] =
    val sparql =
      s"""
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      SELECT ?s ?label WHERE {
        ?s rdfs:label ?label .
        FILTER(CONTAINS(LCASE(?label), LCASE("$text")))
      } LIMIT 10
      """

    basicRequest
      .get(uri"$url?query=$sparql")
      .header("Accept", "application/sparql-results+json")
      .response(asJson[Json])
      .send(backend)
      .body
      .toOption
