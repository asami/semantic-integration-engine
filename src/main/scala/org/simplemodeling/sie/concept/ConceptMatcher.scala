package org.simplemodeling.sie.concept

import cats.effect.*
import cats.syntax.all.*
import io.circe.Json
import org.simplemodeling.sie.service.segmentation.TinySegmenter
import java.util.Locale

/*
 * @since   Dec.  6, 2025
 * @version Dec.  7, 2025
 * @author  ASAMI, Tomoharu
 */
// ----------------------------------------------
// Data model
// ----------------------------------------------
case class UriMatch(uri: String, label: String, score: Double)

case class ConceptHit(
  uri: String,
  canonicalLabel: String,
  matchedLabel: String,
  locale: java.util.Locale,
  score: Double,
  entry: ConceptEntry
)

class ConceptMatcher(
  conceptDict: ConceptDictionary,
  dictionary: Map[String, String],          // unified label → URI
  embed: String => IO[Array[Float]],        // text → embedding vector
  embedSearch: Array[Float] => IO[List[(String, Double)]] // embedding → (uri, score)
):

  // ------------------------------------------------------------
  // Detect language: jp if contains CJK
  // ------------------------------------------------------------
  private def detectLang(text: String): String =
    if (text.exists(c =>
      Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
    )) "ja"
    else "en"

  private def toLocale(lang: String): Locale =
    if lang == "ja" then Locale.JAPANESE else Locale.ENGLISH

  // ------------------------------------------------------------
  // Tokenization for Japanese
  // Simplified fallback version (TinySegmenter recommended)
  // ------------------------------------------------------------
  private def tokenizeJa(text: String): List[String] =
    // Use TinySegmenter for better Japanese segmentation when available
    try
      val seg = TinySegmenter()
      seg.segment(text)
    catch
      case _: Throwable =>
        text
          .replaceAll("[\\p{Punct}]", " ")
          .split("\\s+")
          .filter(_.nonEmpty)
          .toList

  // ------------------------------------------------------------
  // Tokenization for English
  // ------------------------------------------------------------
  private def tokenizeEn(text: String): List[String] =
    text
      .replaceAll("[^a-zA-Z0-9#:/_-]", " ")
      .split("\\s+")
      .filter(_.nonEmpty)
      .toList

  // ------------------------------------------------------------
  // Unified keyword extractor
  // ------------------------------------------------------------
  private def extractKeywords(text: String): List[String] =
    detectLang(text) match
      case "ja" => tokenizeJa(text)
      case _    => tokenizeEn(text)

  // ------------------------------------------------------------
  // Matching strategies
  // ------------------------------------------------------------

  private def exactMatches(keys: List[String], queryLocale: Locale): List[UriMatch] =
    keys.flatMap { k =>
      dictionary.get(k).map { uri =>
        buildMatch(uri, k, isExact = true, queryLocale)
      }
    }

  private def partialMatches(keys: List[String], queryLocale: Locale): List[UriMatch] =
    for
      k <- keys
      (label, uri) <- dictionary
      if label.toLowerCase.contains(k.toLowerCase) && k.length >= 3
    yield buildMatch(uri, label, isExact = false, queryLocale)

  private def buildMatch(
    uri: String,
    matchedText: String,
    isExact: Boolean,
    queryLocale: Locale
  ): UriMatch =
    val entryOpt = conceptDict.entries.get(uri)
    val matchedLabelOpt = entryOpt.flatMap { e =>
      e.labels.find(l => l.text.equalsIgnoreCase(matchedText))
    }

    val base =
      if isExact then 150.0
      else 40.0

    val prefBonus =
      matchedLabelOpt.filter(_.preferred).fold(0.0)(_ => if isExact then 50.0 else 40.0)

    val localeBonus =
      (for
        ml <- matchedLabelOpt
        if ml.locale == queryLocale
      yield 30.0).getOrElse(0.0)

    val altPenalty =
      matchedLabelOpt.filter(!_.preferred).fold(0.0)(_ => -10.0)

    val score = base + prefBonus + localeBonus + altPenalty

    val labelForResult =
      matchedLabelOpt.map(_.text).getOrElse(matchedText)

    UriMatch(uri, labelForResult, score)

  // ------------------------------------------------------------
  // Embedding-based matches
  // ------------------------------------------------------------
  private def embeddingMatches(keys: List[String], queryLocale: Locale): IO[List[UriMatch]] =
    keys.traverse { k =>
      for
        vec <- embed(k)
        sims <- embedSearch(vec)  // returns List[(uri, score)]
      yield sims.map { case (uri, s) =>
        val entryOpt = conceptDict.entries.get(uri)
        val label =
          entryOpt.map(_.labelFor(queryLocale)).getOrElse(uri)
        // embedding scoreは0〜1と仮定し、最大10pt換算
        UriMatch(uri, label, s * 10)
      }
    }.map(_.flatten)

  // ------------------------------------------------------------
  // Ranking
  // ------------------------------------------------------------
  private def rank(all: List[UriMatch]): List[UriMatch] =
    all
      .groupBy(_.uri)
      .map { case (uri, xs) =>
        val sumScore = xs.map(_.score).sum
        val label = xs.head.label
        UriMatch(uri, label, sumScore)
      }
      .toList
      .sortBy(-_.score)

  // ------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------

  /** Locale-aware concept matching.
    * Uses ConceptEntry.labelFor(locale) as the canonical label.
    */
  def matchConcepts(text: String): IO[List[ConceptHit]] = {
    val keys        = extractKeywords(text)
    val lang        = detectLang(text)
    val queryLocale = toLocale(lang)

    for {
      // embedding-based similarity
      emb <- embeddingMatches(keys, queryLocale)

      // dictionary exact/partial matches
      dictMatches =
        exactMatches(keys, queryLocale) ++
        partialMatches(keys, queryLocale)

      ranked  = rank(dictMatches ++ emb)

      hits = ranked.flatMap { m =>
        conceptDict.entries.get(m.uri).map { entry =>
          val canonical = entry.labelFor(queryLocale)

          ConceptHit(
            uri            = m.uri,
            canonicalLabel = canonical,
            matchedLabel   = m.label,
            locale         = queryLocale,
            score          = m.score,
            entry          = entry
          )
        }
      }
    } yield hits.take(3)
  }

  /** Lookup a concept entry by its URI.
    * Returns None when the dictionary does not contain the entry.
    */
  def lookupByUri(uri: String): IO[Option[ConceptEntry]] =
    IO.pure(conceptDict.entries.get(uri))
