package org.simplemodeling.rag.mcp

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

class RestService(client: Client[IO], ragQueryUri: Uri):

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
                          "name" -> Json.fromString("rag.query"),
                          "description" -> Json.fromString("Query RAG via REST"),
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

                case "tools/rag.query" =>
                  val q =
                    req.params.flatMap(_.hcursor.get[String]("query").toOption).getOrElse("")

                  val body = Json.obj("query" -> Json.fromString(q))

                  val result =
                    client
                      .expect[Json](Request[IO](Method.POST, ragQueryUri).withEntity(body))
                      .unsafeRunSync()

                  McpResponse(id = req.id, result = Some(result))

                case other =>
                  McpResponse(id = req.id, error = Some(McpError(404, s"Unknown: $other")))

            println(resp.asJson.noSpaces)
