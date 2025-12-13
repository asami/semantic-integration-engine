package org.simplemodeling.sie.concept

import cats.effect.IO
import java.util.Locale
import org.simplemodeling.sie.config.ServerMode

/**
 * ConceptLoader implementation that reads concept labels from a SPARQL endpoint
 * (e.g. Fuseki) based on the SmartDox glossary ontology structure.
 *
 * Input source is the ontology/glossary graph of SimpleModeling.org.
 * 
 * @since   Dec.  6, 2025
 * @version Dec.  7, 2025
 * @author  ASAMI, Tomoharu
 */
final class FusekiConceptLoader(
  client: SparqlClient,
  mode: org.simplemodeling.sie.config.ServerMode
) extends ConceptLoader:

  override def load(): IO[Seq[(String, ConceptLabel)]] =
    // DEV mode relaxes Concept semantics for local iteration.
    // Demo / Prod preserve strict BoK / ontology-based Concept definitions.
    val query =
      mode match {
        case ServerMode.Dev => devConceptQuery
        case _              => strictConceptQuery
      }

    client.select(query).map { rows =>
      rows.collect {
        case row if row.contains("concept") && row.contains("label") =>
          val uri   = row("concept")
          val text  = row("label")
          val lang  = row.getOrElse("lang", "")
          val kind  = row.getOrElse("kind","alt")
          val locale = langToLocale(lang, text)
          val preferred = (kind == "pref" || kind == "rdfs")
          uri -> ConceptLabel(text = text, locale = locale, source = Some("glossary"), preferred = preferred)
      }
    }

  private def strictConceptQuery: String =
    """
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX dm:   <https://www.simplemodeling.org/documentmodel/ontology/0.1-SNAPSHOT#>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

    SELECT ?concept ?label (LANG(?label) AS ?lang) ?kind WHERE {
      ?concept a dm:Concept .

      {
        ?concept rdfs:label ?label .
        BIND("rdfs" AS ?kind)
      } UNION {
        ?concept skos:prefLabel ?label .
        BIND("pref" AS ?kind)
      } UNION {
        ?concept skos:altLabel ?label .
        BIND("alt" AS ?kind)
      }
    }
    """

  private def devConceptQuery: String =
    """
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

    SELECT DISTINCT ?concept ?label (LANG(?label) AS ?lang) WHERE {
      ?concept rdfs:label ?label .
    }
    """

  /**
   * Map SPARQL LANG() code + literal content to java.util.Locale.
   *
   * For now we only explicitly recognize English and Japanese,
   * but the structure is open for arbitrary language tags in the future.
   */
  private def langToLocale(lang: String, literal: String): Locale =
    val trimmed = lang.trim
    if trimmed.nonEmpty then
      // e.g. "en", "en-US", "ja", "ja-JP"
      Locale.forLanguageTag(trimmed)
    else
      // No explicit lang tag → simple heuristic fallback
      if containsCjk(literal) then Locale.JAPANESE
      else Locale.ENGLISH

  private def containsCjk(text: String): Boolean =
    text.exists { ch =>
      val block = Character.UnicodeBlock.of(ch)
      block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
      block == Character.UnicodeBlock.HIRAGANA ||
      block == Character.UnicodeBlock.KATAKANA
    }
