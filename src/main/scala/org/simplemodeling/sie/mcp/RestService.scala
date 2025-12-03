package org.simplemodeling.sie.mcp

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.client.*
import org.http4s.client.dsl.io.*
import org.http4s.circe.*
import org.http4s.dsl.io.*

/*
 * NOTE:
 * This RestService implements the MCP "process mode" (STDIN/STDOUT)
 * client handler. It is kept for:
 *
 *   - local CLI-based MCP testing,
 *   - integration with non-ChatGPT AI systems,
 *   - development diagnostics of the SIE REST endpoint (/sie/query).
 *
 * ChatGPT DOES NOT use this class. ChatGPT communicates with SIE
 * through the WebSocket-based MCP server, which is the production path.
 *
 * This component acts as a lightweight bridge:
 *   MCP JSON (stdin/stdout) → REST → SIE → Fuseki/Chroma.
 *
 * @since   Nov. 25, 2025
 *          Nov. 26, 2025
 * @version Dec.  4, 2025
 * @author  ASAMI, Tomoharu
 */
class RestService(client: Client[IO], sieQueryUri: Uri):

  private def handle(input: String): Unit =
    parse(input) match
      case Left(parseErr) =>
        println(
          McpResponse(error = Some(McpError(1, parseErr.toString))).asJson.noSpaces
        )

      case Right(json) =>
        json.as[McpRequest] match
          case Left(decodeErr) =>
            println(
              McpResponse(error = Some(McpError(2, decodeErr.toString))).asJson.noSpaces
            )

          case Right(req) =>
            val resp =
              req.method match
                case "initialize" =>
                  val caps = Json.obj(
                    "capabilities" -> Json.obj(
                      "tools" -> Json.arr(
                        Json.obj(
                          "name" -> Json.fromString("sie.query"),
                          "description" -> Json.fromString("Query SIE via REST"),
                          "input_schema" -> Json.obj(
                            "type" -> Json.fromString("object"),
                            "properties" -> Json.obj(
                              "query" -> Json.obj("type" -> Json.fromString("string"))
                            ),
                            "required" -> Json.arr(Json.fromString("query"))
                          )
                        )
                      )
                    )
                  )
                  McpResponse(id = req.id, result = Some(caps))

                case "tools/sie.query" =>
                  val q =
                    req.params.flatMap(_.hcursor.get[String]("query").toOption).getOrElse("")

                  val body = Json.obj("query" -> Json.fromString(q))

                  val result =
                    client
                      .expect[Json](Request[IO](Method.POST, sieQueryUri).withEntity(body))
                      .unsafeRunSync()

                  McpResponse(id = req.id, result = Some(result))

                case other =>
                  McpResponse(id = req.id, error = Some(McpError(404, s"Unknown: $other")))

            println(resp.asJson.noSpaces)
