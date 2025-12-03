package org.simplemodeling.sie.mcp

import cats.effect.*
import cats.syntax.all.*
import fs2.{Pipe, Stream}
import fs2.concurrent.Channel
import io.circe.parser.*
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import org.simplemodeling.sie.service.RagService

/*
 * Minimal WebSocket MCP server for ChatGPT integration.
 *
 * ChatGPT connects via WebSocket and sends JSON-RPC messages:
 *
 *   {
 *     "jsonrpc": "2.0",
 *     "id": "...",
 *     "method": "tools.sie.query",
 *     "params": { "query": "SimpleModeling" }
 *   }
 *
 * This server:
 *   1) Receives JSON
 *   2) Dispatches "tools.sie.query" to RagService
 *   3) Returns RagResult as JSON-RPC response
 *
 * NOTE:
 *  - This is a minimal skeleton
 *  - Production version should validate JSON-RPC fully
 *  - Should also add capabilities/initialize responses if needed
 *
 * @since   Dec.  4, 2025
 * @version Dec.  4, 2025
 * @author  ASAMI, Tomoharu
 */
class McpWebSocketServer(service: RagService):

  def routes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "mcp" =>
        for
          channel <- Channel.unbounded[IO, WebSocketFrame]
          socket  <- wsb.build(
            send = channel.stream,
            receive = handleIncoming(channel)
          )
        yield socket
    }

  private def handleIncoming(
      channel: Channel[IO, WebSocketFrame]
  ): Pipe[IO, WebSocketFrame, Unit] =
    _.evalMap {
      case WebSocketFrame.Text(text, _) =>
        for
          response <- processJsonRpc(text)
          _        <- channel.send(WebSocketFrame.Text(response))
        yield ()

      case _ =>
        channel.send(
          WebSocketFrame.Text("""{"error":"unsupported frame"}""")
        ).void
    }

  private def processJsonRpc(msg: String): IO[String] =
    parse(msg) match
      case Left(err) =>
        IO.pure(
          s"""{"jsonrpc":"2.0","error":{"code":-32700,"message":"invalid json: ${err.getMessage}"}}"""
        )

      case Right(json) =>
        val cursor = json.hcursor
        val method = cursor.get[String]("method").getOrElse("")
        val idJson = cursor.get[io.circe.Json]("id").getOrElse(io.circe.Json.Null)

        method match
          case "initialize" =>
            IO.pure(
              io.circe.Json.obj(
                "jsonrpc" -> io.circe.Json.fromString("2.0"),
                "id"      -> idJson,
                "result"  -> io.circe.Json.obj(
                  "capabilities" -> io.circe.Json.obj(
                    "tools" -> io.circe.Json.arr(
                      io.circe.Json.obj(
                        "name" -> io.circe.Json.fromString("tools.sie.query"),
                        "description" -> io.circe.Json.fromString("Query SIE backend"),
                        "input_schema" -> io.circe.Json.obj(
                          "type" -> io.circe.Json.fromString("object"),
                          "properties" -> io.circe.Json.obj(
                            "query" -> io.circe.Json.obj("type" -> io.circe.Json.fromString("string"))
                          ),
                          "required" -> io.circe.Json.arr(io.circe.Json.fromString("query"))
                        )
                      )
                    )
                  )
                )
              ).noSpaces
            )

          case "tools.sie.query" =>
            val query =
              cursor.downField("params").get[String]("query").getOrElse("")

            service.runIO(query).map { rag =>
              io.circe.Json.obj(
                "jsonrpc" -> io.circe.Json.fromString("2.0"),
                "id"      -> idJson,
                "result"  -> rag.asJson
              ).noSpaces
            }

          case other =>
            IO.pure(
              s"""{"jsonrpc":"2.0","error":{"code":-32601,"message":"unknown method: $other"}}"""
            )
