package org.simplemodeling.sie.fuseki

import io.circe.Json
import cats.effect.IO
import org.simplemodeling.sie.http.SimpleHttpClient

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec.  5, 2025
 * @author  ASAMI, Tomoharu
 */
class FusekiClient(endpoint: String = sys.env.getOrElse("FUSEKI_URL", "http://localhost:3030/ds")):
  private val url = s"$endpoint/query"

  def searchConceptsJson(text: String): IO[Json] =
    val sparql =
      s"""
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      PREFIX dcterms: <http://purl.org/dc/terms/>
      SELECT ?s ?label ?desc WHERE {
        ?s rdfs:label ?label .
        OPTIONAL { ?s dcterms:description ?desc }
        FILTER(CONTAINS(LCASE(?label), LCASE("$text")))
      } LIMIT 50
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

  def searchConcepts(text: String): IO[List[FusekiConcept]] =
    searchConceptsJson(text).map { json =>
      val cursor = json.hcursor
      val bindings = cursor.downField("results").downField("bindings").as[List[Json]].getOrElse(Nil)
      bindings.flatMap { b =>
        val c = b.hcursor
        for
          uri   <- c.downField("s").downField("value").as[String].toOption
          label <- c.downField("label").downField("value").as[String].toOption
        yield
          val desc = c.downField("desc").downField("value").as[String].getOrElse("")
          FusekiConcept(uri, label, label, desc)
      }
    }

  /**
   * Execute a SPARQL query and return flattened bindings.
   * Each binding row is represented as Map[String, String].
   */
  def queryFlat(sparql: String): IO[List[Map[String, String]]] =
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
      io.circe.parser.parse(resp.body).toOption
        .flatMap { json =>
          val cursor = json.hcursor
          cursor
            .downField("results")
            .downField("bindings")
            .as[List[Json]]
            .toOption
        }
        .getOrElse(Nil)
        .flatMap { binding =>
          val c = binding.hcursor
          val keys = c.keys.getOrElse(Nil)
          val row: Map[String, String] =
            keys.flatMap { key =>
              c.downField(key).downField("value").as[String].toOption.map(v => key -> v)
            }.toMap
          if row.isEmpty then None else Some(row)
        }

case class FusekiConcept(
  uri: String,
  label: String,
  title: String,
  description: String
)
