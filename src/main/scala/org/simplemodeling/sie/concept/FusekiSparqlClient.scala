package org.simplemodeling.sie.concept

import cats.effect.IO
import org.simplemodeling.sie.fuseki.FusekiClient

/**
 * Adapter that wraps the existing FusekiClient and exposes it as a SparqlClient.
 *
 * The existing FusekiClient likely provides a method such as:
 *
 *   def query(select: String): IO[List[Map[String, String]]]
 *
 * This adapter normalizes the return type and satisfies the SparqlClient interface.
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  7, 2025
 * @author  ASAMI, Tomoharu
 */
final class FusekiSparqlClient(
  fuseki: FusekiClient
) extends SparqlClient:

  /**
   * Execute a SPARQL SELECT query using the underlying FusekiClient.
   */
  override def select(query: String): IO[Vector[Map[String, String]]] =
    for
      _ <- IO.println(s"[DEBUG] FusekiSparqlClient.select() endpoint = ${fuseki.endpoint}")
      _ <- IO.println(s"[DEBUG] FusekiSparqlClient.select() query:\n$query")
      result <- fuseki.queryFlat(query)
    yield result.toVector
