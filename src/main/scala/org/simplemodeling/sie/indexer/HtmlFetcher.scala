package org.simplemodeling.sie.indexer

import cats.effect.{IO, Resource}
import org.simplemodeling.sie.http.SimpleHttpClient

/*
 * Fetch raw HTML from a URL.
 * 
 * @since   Dec.  2, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
class HtmlFetcher {

  def fetch(url: String): IO[String] =
    SimpleHttpClient.get(url).flatMap { resp =>
      if resp.code >= 200 && resp.code < 400 then
        IO.pure(resp.body)
      else
        IO.raiseError(
          new RuntimeException(
            s"Failed to fetch $url: HTTP ${resp.code} body=${resp.body}"
          )
        )
    }
}

object HtmlFetcher:
  def apply(): HtmlFetcher = new HtmlFetcher()
