package org.simplemodeling.sie.mcp

import cats.effect.*
import cats.syntax.all.*
import fs2.{Pipe, Stream}
import fs2.concurrent.Channel
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.Json
import org.http4s.client.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import org.simplemodeling.sie.service.RagService
import org.simplemodeling.sie.BuildInfo

import cats.effect.unsafe.implicits.global

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
 * @version Dec.  5, 2025
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

  private def loadResourceJson(path: String): io.circe.Json =
    try
      val raw = scala.io.Source.fromResource(path)("UTF-8").mkString
      parse(raw).getOrElse(io.circe.Json.obj())
    catch
      case _: Throwable =>
        io.circe.Json.obj(
          "error" -> io.circe.Json.fromString(s"resource not found: $path")
        )

  // ----------------------------------------
  // Shared placeholder substitution
  // ----------------------------------------
  private def applyPlaceholders(json: Json): Json =
    json.hcursor
      .withFocus(_.mapObject(obj =>
        obj
          .add("version",
            obj("version") match
              case Some(v) if v.asString.exists(_.contains("__VERSION__")) =>
                Json.fromString(BuildInfo.version)
              case other => other.getOrElse(io.circe.Json.Null)
          )
      ))
      .top
      .getOrElse(json)

  private val defaultMetadata: Json = io.circe.Json.obj(
    "author"   -> Json.fromString("SimpleModeling Team"),
    "homepage" -> Json.fromString("https://www.simplemodeling.org")
  )

  private def buildInfoMetadata: Json =
    io.circe.Json.obj(
      "author"   -> Json.fromString(BuildInfo.author),
      "homepage" -> Json.fromString(BuildInfo.homepage)
    )

  private def loadMetadataFromWeb(): IO[Option[Json]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val base = if BuildInfo.homepage.endsWith("/") then BuildInfo.homepage else BuildInfo.homepage + "/"
      val url = base + "metadata.json"
      client.get(url) { resp =>
        if resp.status.isSuccess then
          resp.as[String].map(s => parse(s).toOption)
        else IO.pure(None)
      }
    }.handleError(_ => None)

  private def loadLocalMetadata(): Option[Json] =
    val json = loadResourceJson("mcp/metadata.json")
    if json.isNull then None else Some(json)

  private def resolveMetadata(): IO[Json] =
    for
      remote <- loadMetadataFromWeb()
      local   = loadLocalMetadata()
    yield remote
      .orElse(local)
      .orElse(Some(buildInfoMetadata))
      .getOrElse(defaultMetadata)

  // ----------------------------------------
  // Metadata caching (avoid unsafeRunSync in handler)
  // ----------------------------------------
  private lazy val cachedMetadata: Json =
    defaultMetadata

  private def loadPatchedJson(path: String): io.circe.Json =
    val base = loadResourceJson(path)
    val baseWithPlaceholders = applyPlaceholders(base)

    // Patch "server" → version & name
    val patchedServer =
      baseWithPlaceholders.hcursor
        .downField("server")
        .withFocus(_.mapObject(obj =>
          obj
            .add("version", io.circe.Json.fromString(BuildInfo.version))
            .add("name", io.circe.Json.fromString(BuildInfo.name))
        ))
        .top
        .getOrElse(baseWithPlaceholders)

    // Patch top-level fields → name, protocolVersion
    val patchedTop =
      patchedServer.hcursor
        .withFocus(_.mapObject(obj =>
          obj
            .add("name", io.circe.Json.fromString(BuildInfo.name))
            .add("protocolVersion", io.circe.Json.fromString("1.0"))
        ))
        .top
        .getOrElse(patchedServer)

    // ----------------------------------------
    // Patch metadata (Web → local → BuildInfo → default)
    // Always create metadata field even if missing
    // ----------------------------------------
    val meta = cachedMetadata

    val authorValue =
      meta.hcursor.downField("author").get[String]("name")
        .orElse(meta.hcursor.get[String]("author"))
        .getOrElse("Unknown")

    val homepageValue =
      meta.hcursor.get[String]("url")
        .orElse(meta.hcursor.get[String]("homepage"))
        .getOrElse("https://example.com/")

    // Ensure metadata object exists
    val ensuredMetadata =
      patchedTop.hcursor.downField("metadata").success match
        case Some(_) => patchedTop
        case None =>
          patchedTop.mapObject(obj => obj.add("metadata", io.circe.Json.obj()))

    val patchedMeta =
      ensuredMetadata.hcursor
        .downField("metadata")
        .withFocus(_.mapObject(obj =>
          obj
            .add("author", authorValue.asJson)
            .add("homepage", homepageValue.asJson)
        ))
        .top
        .getOrElse(ensuredMetadata)

    patchedMeta

  // ----------------------------------------
  // Locale parsing helper (fallback to RagService context)
  // ----------------------------------------
  private def parseLocaleOrDefault(
      raw: Option[String],
      defaultLocale: java.util.Locale
  ): java.util.Locale = {
    raw match {
      case Some(s) if s.trim.nonEmpty =>
        val loc = java.util.Locale.forLanguageTag(s.trim)
        // forLanguageTag returns a Locale even if invalid;
        // detect empty language and fallback
        if (loc.getLanguage == null || loc.getLanguage.isEmpty)
          defaultLocale
        else
          loc
      case _ =>
        defaultLocale
    }
  }

  private def processJsonRpc(msg: String): IO[String] =
    parse(msg) match
      case Left(err) =>
        IO.pure(
          io.circe.Json.obj(
            "type" -> io.circe.Json.fromString("error"),
            "error" -> io.circe.Json.obj(
              "message" -> io.circe.Json.fromString(s"invalid json: ${err.getMessage}")
            )
          ).noSpaces
        )

      case Right(json) =>
        val cursor = json.hcursor
        val messageType = cursor.get[String]("type").getOrElse("")

        messageType match
          case "initialize" =>
            IO.pure(
              loadPatchedJson("initialization.json").noSpaces
            )

          case "get_manifest" =>
            IO.pure(
              io.circe.Json.obj(
                "type" -> io.circe.Json.fromString("manifest"),
                "manifest" -> loadPatchedJson("mcp.json")
              ).noSpaces
            )

          case "call" =>
            val toolName = cursor.downField("tool").get[String]("name").getOrElse("")
            val args     = cursor.downField("tool").downField("arguments")

            toolName match
              case "tools.sie.query" =>
                val query = args.get[String]("query").getOrElse("")
                service.runIO(query).map { rag =>
                  io.circe.Json.obj(
                    "type" -> io.circe.Json.fromString("toolResult"),
                    "tool" -> io.circe.Json.fromString("tools.sie.query"),
                    "result" -> rag.asJson
                  ).noSpaces
                }

              case "tools.sie.explainConcept" =>
                val uri        = args.get[String]("uri").getOrElse("")
                val localeOpt  = args.get[String]("locale").toOption
                val locale     = parseLocaleOrDefault(localeOpt, service.context.defaultLocale)

                service.explainConcept(uri, locale).map { exp =>
                  io.circe.Json.obj(
                    "type"   -> io.circe.Json.fromString("toolResult"),
                    "tool"   -> io.circe.Json.fromString("tools.sie.explainConcept"),
                    "result" -> exp.asJson
                  ).noSpaces
                }

              case "tools.sie.getNeighbors" =>
                val uri = args.get[String]("uri").getOrElse("")
                service.getNeighbors(uri).map { graph =>
                  io.circe.Json.obj(
                    "type" -> io.circe.Json.fromString("toolResult"),
                    "tool" -> io.circe.Json.fromString("tools.sie.getNeighbors"),
                    "result" -> graph.asJson
                  ).noSpaces
                }

              case other =>
                IO.pure(
                  io.circe.Json.obj(
                    "type" -> io.circe.Json.fromString("error"),
                    "error" -> io.circe.Json.obj(
                      "message" -> io.circe.Json.fromString(s"unknown tool: $other")
                    )
                  ).noSpaces
                )

          case other =>
            IO.pure(
              io.circe.Json.obj(
                "type" -> io.circe.Json.fromString("error"),
                "error" -> io.circe.Json.obj(
                  "message" -> io.circe.Json.fromString(s"unknown message type: $other")
                )
              ).noSpaces
            )
