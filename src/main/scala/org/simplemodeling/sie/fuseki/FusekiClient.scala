package org.simplemodeling.sie.fuseki

import io.circe.Json
import cats.effect.IO
import org.simplemodeling.sie.http.SimpleHttpClient

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
class FusekiClient(endpoint: String = sys.env.getOrElse("FUSEKI_URL", "http://localhost:3030/ds")):
  private val url = s"$endpoint/query"

  def searchConceptsIO(text: String): IO[Json] =
    val sparql =
      s"""
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      SELECT ?s ?label WHERE {
        ?s rdfs:label ?label .
        FILTER(CONTAINS(LCASE(?label), LCASE("$text")))
      } LIMIT 10
      """

    for
      resp <- SimpleHttpClient.post(
        url,
        sparql,
        Map(
          "Content-Type" -> "application/sparql-query",
          "Accept"       -> "application/sparql-results+json"
        )
      )
    yield
      io.circe.parser.parse(resp.body).fold(
        err => Json.obj("error" -> Json.fromString(err.getMessage)),
        identity
      )
