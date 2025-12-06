package org.simplemodeling.sie.concept

import cats.effect.IO

/**
 * Low-level loader that fetches (uri, label) pairs from some backing store
 * (e.g. Fuseki / ontology / glossary graph).
 *
 * - It does NOT aggregate labels into ConceptEntry.
 *   That is the responsibility of ConceptService.
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
trait ConceptLoader:

  /**
   * @return sequence of (conceptUri, ConceptLabel) pairs
   */
  def load(): IO[Seq[(String, ConceptLabel)]]
