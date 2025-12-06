package org.simplemodeling.sie.concept

import java.util.Locale

/**
 * In-memory concept dictionary.
 *
 * - entries: uri -> ConceptEntry
 *
 * This is the canonical representation for concept metadata.
 * ConceptMatcher can derive its "label -> uri" lookup map from this.
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
final case class ConceptDictionary(
  entries: Map[String, ConceptEntry] // key = uri
):

  def size: Int = entries.size

  def uris: Iterable[String] = entries.keys

  def find(uri: String): Option[ConceptEntry] = entries.get(uri)

  /** Flatten to a simple label->uri map for matcher usage. */
  def toMatcherDictionary: Map[String, String] =
    entries.values.flatMap { entry =>
      entry.labels.map(label => label.text -> entry.uri)
    }.toMap

  /** All labels (text only). */
  def allLabelTexts: Set[String] =
    entries.values.flatMap(_.allTexts).toSet

  /** Filter entries that have at least one label for given locale. */
  def filterByLocale(locale: Locale): ConceptDictionary =
    val filtered = entries.collect {
      case (uri, entry) if entry.labelsFor(locale).nonEmpty => uri -> entry
    }
    copy(entries = filtered)
