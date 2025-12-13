package org.simplemodeling.sie.fuseki

import io.circe.Json
import cats.effect.IO
import org.simplemodeling.sie.http.SimpleHttpClient
import java.net.URLEncoder

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec. 13, 2025
 * @author  ASAMI, Tomoharu
 */
class FusekiClient(val endpoint: String = sys.env.getOrElse("FUSEKI_URL", "http://localhost:3030/ds")):
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
      encoded <- IO.pure("query=" + URLEncoder.encode(sparql, "UTF-8"))

      resp <- SimpleHttpClient.post(
        url,
        encoded,
        Map(
          "Content-Type" -> "application/x-www-form-urlencoded",
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
      encoded <- IO.pure("query=" + URLEncoder.encode(sparql, "UTF-8"))

      resp <- SimpleHttpClient.post(
        url,
        encoded,
        Map(
          "Content-Type" -> "application/x-www-form-urlencoded",
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
              val valueCursor = c.downField(key).downField("value")
              valueCursor.focus match
                case Some(jsonValue) =>
                  jsonValue.asString match
                    case Some(s) => Some(key -> s)        // includes empty string ""
                    case None    => None
                case None => None
            }.toMap
          if row.isEmpty then None else Some(row)
        }

  /**
   * Best-effort health check ensuring Fuseki is reachable and dataset is non-empty.
   *
   * Semantics:
   *   - Reachability: SPARQL endpoint must respond successfully.
   *   - Non-empty: total triple count must be >= minTriples.
   *
   * Design notes:
   *   - Uses a COUNT(*) query to avoid loading actual data.
   *   - Intended for startup fail-fast when GraphDB is Unmanaged.
   */
  def assertReachableAndNonEmpty(minTriples: Int = 1): IO[Unit] =
    val sparql =
      """
      SELECT (COUNT(*) AS ?c)
      WHERE { ?s ?p ?o }
      """

    queryFlat(sparql).flatMap { rows =>
      val countOpt =
        rows.headOption
          .flatMap(_.get("c"))
          .flatMap(s => scala.util.Try(s.toLong).toOption)

      countOpt match
        case Some(c) if c >= minTriples =>
          IO.unit
        case Some(c) =>
          IO.raiseError(
            new IllegalStateException(
              s"Fuseki dataset appears empty or insufficient (triples=$c, expected >= $minTriples)."
            )
          )
        case None =>
          IO.raiseError(
            new IllegalStateException(
              "Failed to read triple count from Fuseki response."
            )
          )
    }

case class FusekiConcept(
  uri: String,
  label: String,
  title: String,
  description: String
)
