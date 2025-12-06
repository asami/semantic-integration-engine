package org.simplemodeling.sie.concept

import cats.effect.IO

/**
 * Minimal SPARQL SELECT client abstraction.
 *
 * You can implement this using your existing Fuseki client.
 * The expectation is that each row is a Map from variable name to string value.
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
trait SparqlClient:

  /**
   * Execute a SPARQL SELECT query and return rows as Map(varName -> value).
   */
  def select(query: String): IO[Vector[Map[String, String]]]
