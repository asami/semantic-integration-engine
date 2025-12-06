package org.simplemodeling.sie.concept

import java.util.Locale

/**
 * A conceptual node in the knowledge graph, identified by its URI,
 * with multi-lingual labels attached.
 *
 * This is intentionally minimal for now, but can be extended later
 * with definitions, categories, relations, etc.
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  6, 2025
 * @author  ASAMI, Tomoharu
 */
final case class ConceptEntry(
  uri: String,
  labels: Set[ConceptLabel],
  localeLabels: Map[java.util.Locale, Set[ConceptLabel]],
  preferredLabelMap: Map[java.util.Locale, String] = Map.empty,

  // --- extended fields ---
  definitions: Map[java.util.Locale, String] = Map.empty,
  remarks: Map[java.util.Locale, String] = Map.empty,
  categories: Set[String] = Set.empty,
  synonyms: Set[String] = Set.empty,
  related: Set[String] = Set.empty,
  parents: Set[String] = Set.empty,
  children: Set[String] = Set.empty
):

  /** All label texts (across all locales). */
  def allTexts: Set[String] = labels.map(_.text)

  /** Filter labels by locale. */
  def labelsFor(locale: Locale): Set[ConceptLabel] =
    labels.filter(_.locale == locale)

  /** Preferred labels only (for all locales). */
  def preferredLabels: Set[ConceptLabel] =
    labels.filter(_.preferred)

  /** Preferred labels for a specific locale. */
  def preferredLabelsFor(locale: Locale): Set[ConceptLabel] =
    labelsFor(locale).filter(_.preferred)

  /** Return a single canonical label for this concept.
    *
    * Priority:
    *   1. preferredLabels (for any locale)
    *   2. any available label
    *   3. fallback: URI
    */
  def canonicalLabel: String =
    preferredLabels.headOption
      .map(_.text)
      .orElse(labels.headOption.map(_.text))
      .getOrElse(uri)

  /** Extract the first available definition text,
    * ignoring locales. Returns None if empty.
    */
  def firstDefinition: Option[String] =
    definitions.values.headOption

  /** Extract the first available remark text,
    * ignoring locales. Returns None if empty.
    */
  def firstRemark: Option[String] =
    remarks.values.headOption

  /** Select text by locale with fallback:
    * 1. requested locale
    * 2. English
    * 3. Japanese
    * 4. first available
    */
  def resolveByLocale(
      map: Map[Locale, String],
      locale: Locale
  ): Option[String] = {
    val primary = map.get(locale)
    if (primary.isDefined) return primary

    val en = map.get(Locale.ENGLISH)
    if (en.isDefined) return en

    val ja = map.get(Locale.JAPANESE)
    if (ja.isDefined) return ja

    map.values.headOption
  }

  /** Locale-aware preferred label resolution.
    * Priority:
    *  1. preferredLabels(locale)
    *  2. preferred ConceptLabel (preferredLabelsFor)
    *  3. labelsFor(locale)
    *  4. definitions(locale)
    *  5. remarks(locale)
    *  6. canonicalLabel
    */
  def labelFor(locale: Locale): String = {
    // 1. explicit preferredLabels map
    resolveByLocale(preferredLabelMap, locale) match {
      case Some(v) => return v
      case None    =>
    }

    // 2. preferred ConceptLabel objects
    val preferredLocale = preferredLabelsFor(locale).headOption.map(_.text)
    if (preferredLocale.isDefined) return preferredLocale.get

    // 3. any label for locale
    val lblLocale = labelsFor(locale).headOption.map(_.text)
    if (lblLocale.isDefined) return lblLocale.get

    // 4. definitions(locale)
    val defLocale = resolveByLocale(definitions, locale)
    if (defLocale.isDefined) return defLocale.get

    // 5. remarks(locale)
    val remLocale = resolveByLocale(remarks, locale)
    if (remLocale.isDefined) return remLocale.get

    // 6. fallback to canonicalLabel
    canonicalLabel
  }
