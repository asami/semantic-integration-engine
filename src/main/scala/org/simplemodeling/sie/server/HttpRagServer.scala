package org.simplemodeling.sie.server

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.ember.server.*
import io.circe.Json
import org.simplemodeling.sie.service.*
import com.comcast.ip4s.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import fs2.Stream
import org.simplemodeling.sie.mcp.McpWebSocketServer

import cats.syntax.semigroupk.*

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec.  4, 2025
 * @author  ASAMI, Tomoharu
 */
class HttpRagServer(service: RagService, host: String, port: Int):

  given EntityEncoder[IO, RagResult] = jsonEncoderOf
  given EntityEncoder[IO, Json] = jsonEncoderOf

  private def httpRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      for
        report <- service.health()
        resp   <- Ok(report)
      yield resp

    case POST -> Root / "admin" / "init-chroma" =>
      for
        result <- IO(service.initChroma())
                     .handleError(e => Json.obj("error" -> Json.fromString(e.toString)))
        resp   <- Ok(result)
      yield resp

    case req @ POST -> Root / "sie" / "query" =>
      for
        json  <- req.as[Json]
        query <- IO(json.hcursor.get[String]("query").getOrElse(""))
        result <- IO(service.run(query))
                    .handleError { e =>
                      println(s"[HttpRagServer] error in /sie/query: ${e.toString}")
                      RagResult(Nil, Nil)
                    }
        resp  <- Ok(result)
      yield resp
  }

  private def websocketRoutes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] =
    new McpWebSocketServer(service).routes(wsb)

  def start: IO[Nothing] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(port).getOrElse(port"8080"))
      .withHttpWebSocketApp { wsb =>
        (httpRoutes <+> websocketRoutes(wsb)).orNotFound
      }
      .build
      .useForever
