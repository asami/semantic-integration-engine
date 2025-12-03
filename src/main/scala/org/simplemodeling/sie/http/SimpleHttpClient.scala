package org.simplemodeling.sie.http

import cats.effect.IO
import java.net.{HttpURLConnection, URL}
import java.io.{BufferedReader, InputStreamReader, OutputStream}
import scala.jdk.CollectionConverters.*

/*
 * @since   Dec.  3, 2025
 * @version Dec.  3, 2025
 * @author  ASAMI, Tomoharu
 */
object SimpleHttpClient {

  final case class HttpResponse(
    code: Int,
    headers: Map[String, List[String]],
    body: String
  )

  def get(url: String, headers: Map[String, String] = Map.empty): IO[HttpResponse] =
    IO.blocking {
      val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("GET")
      headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }

      // disconnect()は絶対に呼ばない
      val code = conn.getResponseCode
      val stream =
        if (code >= 200 && code < 400) conn.getInputStream
        else conn.getErrorStream

      val body =
        if (stream != null) {
          val source = scala.io.Source.fromInputStream(stream, "UTF-8")
          try source.mkString
          finally source.close()
        } else ""

      HttpResponse(
        code,
        conn.getHeaderFields.asScala.view.mapValues(_.asScala.toList).toMap,
        body
      )
    }

  def post(url: String, json: String, headers: Map[String, String] = Map.empty): IO[HttpResponse] =
    IO.blocking {
      val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Type", "application/json")
      headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }
      conn.setDoOutput(true)

      val os: OutputStream = conn.getOutputStream
      os.write(json.getBytes("UTF-8"))
      os.flush()

      val code = conn.getResponseCode
      val stream =
        if (code >= 200 && code < 400) conn.getInputStream
        else conn.getErrorStream

      val body =
        if (stream != null) {
          val source = scala.io.Source.fromInputStream(stream, "UTF-8")
          try source.mkString
          finally source.close()
        } else ""

      HttpResponse(
        code,
        conn.getHeaderFields.asScala.view.mapValues(_.asScala.toList).toMap,
        body
      )
    }

  def delete(url: String, headers: Map[String, String] = Map.empty): IO[HttpResponse] =
    IO.blocking {
      val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
      conn.setRequestMethod("DELETE")
      headers.foreach { case (k, v) => conn.setRequestProperty(k, v) }

      val code = conn.getResponseCode
      val stream =
        if (code >= 200 && code < 400) conn.getInputStream
        else conn.getErrorStream

      val body =
        if (stream != null) {
          val source = scala.io.Source.fromInputStream(stream, "UTF-8")
          try source.mkString
          finally source.close()
        } else ""

      HttpResponse(
        code,
        conn.getHeaderFields.asScala.view.mapValues(_.asScala.toList).toMap,
        body
      )
    }
}
