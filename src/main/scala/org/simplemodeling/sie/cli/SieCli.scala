package org.simplemodeling.sie.cli

import org.goldenport.cli.CliEngine
import org.goldenport.cli.logic.CliLogic
import org.goldenport.protocol.spec.{ServiceDefinition, ServiceDefinitionGroup}
import org.goldenport.Consequence
import org.simplemodeling.sie.protocol
import org.simplemodeling.sie.protocol.*

/*
 * @since   Dec. 22, 2025
 * @version Dec. 27, 2025
 * @author  ASAMI, Tomoharu
 */
object SieCliMain {
  /**
   * Entry point for SIE CLI.
   *
   * This implementation delegates argument parsing and
   * OperationRequest construction to Goldenport CLI.
   *
   * Execution is intentionally not performed here.
   */
  def main(args: Array[String]): Unit =
    // Fast-path for demo: positional query
    if (args.length >= 2 && args(0) == "query") {
      val text = args.drop(1).mkString(" ")
      _executeQuery(Query(text)) match {
        case Consequence.Success(_) => ()
        case Consequence.Failure(errs) =>
          Console.err.println(errs.toString)
          sys.exit(1)
      }
      return
    }

    val engine = _build()
    engine.makeRequest(args) match {
      case Consequence.Success(opreq) =>
        opreq match {
          case q: Query =>
            _executeQuery(q) match {
              case Consequence.Success(_) => ()
              case Consequence.Failure(errs) =>
                Console.err.println(errs.toString)
                sys.exit(1)
            }
          case other =>
            Console.err.println(s"Unsupported operation: ${other.getClass.getSimpleName}")
            sys.exit(1)
        }
      case Consequence.Failure(errs) =>
        Console.err.println(errs.toString)
        sys.exit(1)
    }

  private def _build(): CliEngine =
    new CliEngine(
      CliEngine.Config(),
      CliEngine.Specification(
        services = protocol.services
      )
    )

  private def _executeQuery(q: Query): Consequence[Unit] = {
    val json =
      s"""
         |{
         |  "name": "query",
         |  "arguments": {
         |    "query": "${q.query}"
         |  }
         |}
         |""".stripMargin

    val url = _endpoint() + "/api"

    // println(s"[sie-cli] request json = ${json}")
    _postJson(url, json).map { body =>
      println(body)
    }
  }

  private def _postJson(url: String, json: String): Consequence[String] =
    try {
      val conn =
        new java.net.URL(url)
          .openConnection()
          .asInstanceOf[java.net.HttpURLConnection]

      conn.setRequestMethod("POST")
      conn.setDoOutput(true)
      conn.setRequestProperty("Content-Type", "application/json")

      val out = new java.io.OutputStreamWriter(conn.getOutputStream, "UTF-8")
      out.write(json)
      out.flush()
      out.close()

      val body = scala.io.Source.fromInputStream(
        new java.io.BufferedInputStream(
          if (conn.getResponseCode < 400) conn.getInputStream else conn.getErrorStream
        ),
        "UTF-8"
      ).mkString

      Consequence.Success(body)
    } catch {
      case e: Exception =>
        Consequence.failure(e)
    }

  private def _endpoint(): String =
    sys.env.getOrElse(
      "SIE_REST_ENDPOINT",
      "http://sie:8080"
    )
}
