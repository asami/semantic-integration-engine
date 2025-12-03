package org.simplemodeling.sie.indexer

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import org.simplemodeling.sie.http.SimpleHttpClient

/*
 * Fetches site.jsonld and extracts article/page URLs.
 * 
 * @since   Dec.  2, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
object SiteJsonCrawler:

  // You can also make this configurable via AppConfig if you like
  private val DefaultSiteJsonldUrl =
    "https://www.simplemodeling.org/site.jsonld"

  def fetchPageUrls(
      siteJsonldUrl: String = DefaultSiteJsonldUrl
  ): IO[List[String]] =
    SimpleHttpClient.get(siteJsonldUrl).flatMap { resp =>
      parse(resp.body) match
        case Left(err) =>
          IO.raiseError(new RuntimeException(s"Failed to parse site.jsonld: $err"))
        case Right(json) =>
          IO.pure(extractUrls(json))
    }

  private def extractUrls(json: Json): List[String] =
    // Very naive: assume @graph is an array of nodes with @id as URL
    val cursor = json.hcursor
    val graph  = cursor.downField("@graph")

    graph
      .as[List[Json]]
      .getOrElse(Nil)
      .flatMap { node =>
        val c = node.hcursor
        c.get[String]("@id").toOption
      }
      // 一応 SimpleModeling.org の HTML に絞る
      .filter(_.startsWith("https://www.simplemodeling.org/"))
      .filter(_.endsWith(".html"))
      .distinct
