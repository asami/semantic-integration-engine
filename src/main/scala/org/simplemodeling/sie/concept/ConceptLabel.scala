package org.simplemodeling.sie.concept

import java.util.Locale

/**
 * A single human-readable label for a concept.
 *
 * - text   : the literal label string (e.g. "SimpleObject", "シンプルオブジェクト")
 * - locale : java.util.Locale for language/region (e.g. Locale.ENGLISH, Locale.JAPANESE)
 * - source : optional hint where this label came from (glossary, ontology, article etc.)
 * - preferred : true if this label is the "primary" one for its locale (e.g. skos:prefLabel)
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  7, 2025
 * @author  ASAMI, Tomoharu
 */
final case class ConceptLabel(
  text: String,
  locale: Locale,
  source: Option[String] = None,
  preferred: Boolean = false
)
