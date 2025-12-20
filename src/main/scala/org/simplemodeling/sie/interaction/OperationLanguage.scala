package org.simplemodeling.sie.interaction

import io.circe.*

import io.circe.*

/*
 * @since   Dec. 20, 2025
 * @version Dec. 20, 2025
 * @author  ASAMI, Tomoharu
 */
sealed trait SieOperation
sealed trait SieCommand extends SieOperation
sealed trait SieQuery extends SieOperation

// ---- Queries ----

final case class Query(
  query: String,
  limit: Option[Int] = None
) extends SieQuery

final case class ExplainConcept(
  name: String
) extends SieQuery

// ---- Results ----

trait SieOperationResult

final case class Concept(
  uri: String,
  label: String,
  lang: Option[String]
) derives Encoder

final case class Passage(
  id: String,
  text: String,
  score: Option[Double] = None
) derives Encoder

final case class QueryResult(
  concepts: Seq[Concept] = Nil,
  passages: Seq[Passage] = Nil,
  graph: String = "{}"
) extends SieOperationResult

final case class ExplainConceptResult(
  description: String = ""
) extends SieOperationResult
