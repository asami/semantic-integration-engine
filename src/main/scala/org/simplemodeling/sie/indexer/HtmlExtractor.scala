package org.simplemodeling.sie.indexer

import org.jsoup.Jsoup

/*
 * Extract main text from HTML.
 * 
 * @since   Dec.  2, 2025
 * @version Dec.  2, 2025
 * @author  ASAMI, Tomoharu
 */
object HtmlExtractor:

  def extractMainText(html: String): String =
    val doc = Jsoup.parse(html)

    // Try <article>, <main> first, otherwise fallback to body
    val candidates = List("article", "main", "div#content")

    val mainElemOpt =
      candidates
        .toStream
        .flatMap(css => Option(doc.selectFirst(css)))
        .headOption

    val text =
      mainElemOpt
        .map(_.text())
        .getOrElse(doc.body().text())

    text.trim
