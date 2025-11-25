package org.simplemodeling.sie.server

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.ember.server.*
import io.circe.Json
import org.simplemodeling.sie.service.*
import com.comcast.ip4s.*

/*
 * @since   Nov. 20, 2025
 * @version Nov. 25, 2025
 * @author  ASAMI, Tomoharu
 */
class HttpRagServer(service: RagService, host: String, port: Int):

  given EntityEncoder[IO, RagResult] = jsonEncoderOf
  given EntityEncoder[IO, Json] = jsonEncoderOf

  private val routes = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok")))

    case req @ POST -> Root / "rag" / "query" =>
      for
        json  <- req.as[Json]
        query <- IO(json.hcursor.get[String]("query").getOrElse(""))
        result = service.run(query)
        resp  <- Ok(result)
      yield resp
  }.orNotFound

  def start: IO[Nothing] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(port).getOrElse(port"8080"))
      .withHttpApp(routes)
      .build
      .useForever

