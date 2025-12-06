package org.simplemodeling.sie.concept

import cats.effect.IO

/**
 * High-level service that builds an in-memory ConceptDictionary
 * from a lower-level ConceptLoader (e.g. FusekiConceptLoader).
 *
 * This is the main entry point that RagService (or other services)
 * should depend on, rather than talking directly to SPARQL or RDF.
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
final class ConceptService(
  loader: ConceptLoader
):

  /**
   * Load all concept labels and aggregate them into ConceptDictionary.
   *
   * Multiple labels for the same URI (different locales or aliases)
   * will be merged into a single ConceptEntry.
   */
  def loadDictionary(): IO[ConceptDictionary] =
    loader.load().map { pairs =>
      val grouped: Map[String, Seq[ConceptLabel]] =
        pairs.groupMap(_._1)(_._2)

      val entries: Map[String, ConceptEntry] =
        grouped.map { case (uri, labels) =>
          val localeGroups = labels.groupBy(_.locale).view.mapValues(_.toSet).toMap
          uri -> ConceptEntry(
            uri = uri,
            labels = labels.toSet,
            localeLabels = localeGroups
          )
        }

      ConceptDictionary(entries)
    }
