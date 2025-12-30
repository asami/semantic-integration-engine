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
import fs2.{Pipe, Stream}
import fs2.concurrent.Channel
import org.simplemodeling.sie.mcp.McpWebSocketServer
import org.simplemodeling.sie.config.ServerMode
import org.simplemodeling.sie.status.Status
import org.simplemodeling.sie.interaction.*
import org.simplemodeling.sie.interaction.ProtocolHandler
import org.simplemodeling.sie.interaction.ProtocolHandler.WsInput
import org.simplemodeling.sie.interaction.ProtocolHandler.WsOutput
import org.goldenport.Consequence

import cats.syntax.semigroupk.*

import org.simplemodeling.sie.BuildInfo
import io.circe.syntax.*
import io.circe.parser.parse

/*
 * ============================================================
 * /status API Schema (STABLE)
 * ============================================================
 *
 * This endpoint provides operational and readiness information.
 * It is intended to be consumed by:
 *   - operators
 *   - monitoring systems
 *   - external tools (including AI agents)
 *
 * Stability rules:
 *   - Field names are stable once published.
 *   - New fields may be added.
 *   - Existing fields MUST NOT be removed or change semantics
 *     without a major version bump.
 *
 * Schema (v1):
 *
 * {
 *   "schemaVersion": "v1",
 *   "mode": "Dev" | "Demo" | "Staging" | "Prod",
 *   "version": "<semantic version>",
 *   "build": "<git commit>",
 *   "rebuildPolicy": "DEV" | "DEMO" | "PROD",
 *
 *   "agent": {
 *     "mode": "off" | "hybrid" | "mcp-only"
 *   },
 *
 *   "chroma": {
 *     "reachable": boolean,
 *     "collectionExists": boolean,
 *     "error"?: string
 *   },
 *
 *   "fuseki": {
 *     "reachable": boolean,
 *     "error"?: string
 *   },
 *
 *   "knowledge": {
 *     "graphdb": {
 *       "role": "authoritative",
 *       "operationalControl": "Managed" | "Unmanaged",
 *       "health": {
 *         "status": "OK" | "ERROR",
 *         "message"?: string
 *       }
 *     },
 *     "vectordb": {
 *       "role": "derived",
 *       "operationalControl": "Managed" | "Unmanaged",
 *       "health": {
 *         "status": "OK" | "DEGRADED" | "ERROR",
 *         "message"?: string
 *       }
 *     }
 *   }
 * }
 *
 * rebuildPolicy semantics:
 *   - DEV  : VectorDB may be rebuilt lazily or manually by SIE.
 *   - DEMO : VectorDB is expected to be prebuilt; rebuild is normally disabled.
 *   - PROD : VectorDB rebuild is strictly external and controlled by operations.
 *
 * ============================================================
 */

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec. 30, 2025
 * @author  ASAMI, Tomoharu
 */
class HttpRagServer(
  service: RagService,
  host: String,
  port: Int,
  mode: ServerMode,
  knowledgeControl: org.simplemodeling.sie.config.KnowledgeStoresConfig,
  systemStatus: Status,
  mcpmergemanifestintoinitialize: Boolean
):

  given EntityEncoder[IO, RagResult] = jsonEncoderOf
  given EntityEncoder[IO, Json] = jsonEncoderOf
  given EntityEncoder[IO, ConceptExplanation] = jsonEncoderOf

  private val sieService = new SieService(service)
  private val restIngress = new RestIngress()
  private val restAdapter = new RestAdapter()
  private val _protocol_engine = org.simplemodeling.sie.protocol.engine
  private val interactionContext = new InteractionContext {
    override def execute(req: OperationRequest): OperationResult =
      req.payload match
        case OperationPayload.Initialize =>
          OperationResult(
            req.requestId,
            OperationPayloadResult.Initialized(
              serverName = "semantic-integration-engine",
              serverVersion = BuildInfo.version,
              capabilities = Map("tools" -> true)
            )
          )
        case OperationPayload.ToolsList =>
          OperationResult(
            req.requestId,
            OperationPayloadResult.Tools(defaultTools)
          )
        case OperationPayload.GetManifest =>
          _protocol_engine.getManifest() match
            case Consequence.Success(json) =>
              OperationResult(
                req.requestId,
                OperationPayloadResult.Manifest(json)
              )
            case Consequence.Failure(errors) =>
              OperationResult(
                req.requestId,
                OperationPayloadResult.Failed(
                  SimpleProtocolError(
                    ProtocolErrorCode.InternalError,
                    errors.toString
                  )
                )
              )
        case OperationPayload.Call(op) =>
          OperationResult(
            req.requestId,
            OperationPayloadResult.Executed(sieService.execute(op))
          )

    override def fail(err: ProtocolError): OperationResult =
      OperationResult(
        requestId = None,
        payload = OperationPayloadResult.Failed(err)
      )
  }

  private val defaultTools: List[OperationTool] =
    List(
      OperationTool(
        name = "query",
        description = "Semantic query using existing query implementation",
        required = List("query")
      ),
      OperationTool(
        name = "explainConcept",
        description = "Explain a concept using the SimpleModeling knowledge base",
        required = List("name")
      )
    )

  private val _mcp_handler =
    ProtocolHandler.Mcp.handlerWithTools(
      defaultTools,
      mcpmergemanifestintoinitialize
    )

  private def unwrapJsonRpcResult(json: Json): Json =
    json.hcursor.downField("result").focus.getOrElse(json)

  private def httpRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "status" / "schema" =>
      StaticFile.fromResource("/status/schema-v1.json", Some(req))
        .getOrElseF(NotFound())

    // ------------------------------------------------------------
    // /health
    //
    // Liveness endpoint.
    //
    // Semantics:
    //   - Indicates that the HTTP process is running.
    //   - MUST be lightweight and fast.
    //   - MUST NOT perform external dependency checks
    //     (GraphDB, VectorDB, Embedding, etc.).
    //
    // Notes:
    //   - Readiness / dependency status is exposed via /status.
    //   - This separation allows safe use by container orchestrators
    //     and monitoring systems.
    // ------------------------------------------------------------
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("UP")))

    case GET -> Root / "status" =>
      val version = BuildInfo.version
      val build   = BuildInfo.build

      Ok(
        Json.obj(
          "schemaVersion" -> Json.fromString("v1"),
          "meta" -> Json.obj(
            "mode"    -> Json.fromString(mode.toString),
            "version" -> Json.fromString(version),
            "build"   -> Json.fromString(build)
          ),
          "status" -> systemStatus.asJson
        )
      )

    // ------------------------------------------------------------
    // /version
    //
    // Lightweight version/build info endpoint for REST clients.
    // Intended for CLI and demo tooling.
    // ------------------------------------------------------------
    case GET -> Root / "version" =>
      Ok(
        Json.obj(
          "name"    -> Json.fromString("semantic-integration-engine"),
          "version" -> Json.fromString(BuildInfo.version),
          "build"   -> Json.fromString(BuildInfo.build)
        )
      )

    case GET -> Root / "status" / "v0.1" =>
      // Legacy status (v0.1): preserved as-is for backward compatibility
      for {
        report <- service.health()
        sanitizedReport =
          report
            .mapObject(_.remove("embedding"))
            .mapObject(_.remove("status"))

        version = BuildInfo.version
        build   = BuildInfo.build

        resp <- Ok(
          sanitizedReport.deepMerge(
            Json.obj(
              "schemaVersion" -> Json.fromString("v0.1"),
              "mode"          -> Json.fromString(mode.toString),
              "version"       -> Json.fromString(version),
              "build"         -> Json.fromString(build),
              "rebuildPolicy" ->
                Json.fromString(
                  mode match
                    case ServerMode.Dev  => "DEV"
                    case ServerMode.Demo => "DEMO"
                    case _               => "PROD"
                ),
              "agent" -> Json.obj(
                "mode" -> Json.fromString(service.agentMode.toString.toLowerCase)
              ),
              "knowledge" -> knowledgeJson(systemStatus)
            )
          ).deepMerge(
            Json.obj(
              "status" -> systemStatus.asJson
            )
          )
        )
      } yield resp

    case POST -> Root / "admin" / "init-chroma" =>
      for
        result <- IO(service.initChroma())
                     .handleError(e => Json.obj("error" -> Json.fromString(e.toString)))
        resp   <- Ok(result)
      yield resp


    case req if req.uri.path.renderString.startsWith("/api") =>
      val jsonIO: IO[Json] =
        if req.method == Method.POST then
          req.attemptAs[Json].getOrElse(Json.obj())
        else
          IO.pure(Json.obj())

      jsonIO.flatMap { body =>
        val toolName =
          body.hcursor.get[String]("name").getOrElse("")
        val arguments =
          body.hcursor
            .downField("arguments")
            .focus
            .flatMap(_.asObject)
            .map { obj =>
              obj.toMap.view.mapValues { v =>
                v.asString.getOrElse(v.noSpaces)
              }.toMap
            }
            .getOrElse(Map.empty)

        // Use MCP JSON-RPC path (same as mcp-client) so the response is JSON.
        val mcpRequestJson =
          Json.obj(
            "jsonrpc" -> Json.fromString("2.0"),
            "id"      -> Json.Null,
            "method"  -> Json.fromString("tools/call"),
            "params"  -> Json.obj(
              "name"      -> Json.fromString(toolName),
              "arguments" -> arguments.asJson
            )
          )

        val input = WsInput.Text(mcpRequestJson.noSpaces)

        val output =
          _mcp_handler.handle(input, interactionContext)

        parse(output.message) match
          case Right(json) =>
            Ok(unwrapJsonRpcResult(json))
          case Left(_) =>
            Ok(Json.obj("message" -> Json.fromString(output.message)))
      }
  }

  private def websocketRoutes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] =
    new McpWebSocketServer(sieService, mcpmergemanifestintoinitialize).routes(wsb)

  private def protocolWebSocketRoutes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "mcp" =>
        for
          channel <- Channel.unbounded[IO, WebSocketFrame]
          socket  <- wsb.build(
            send = channel.stream,
            receive = handleProtocolMcp(channel)
          )
        yield socket

      case GET -> Root / "chatgpt" =>
        for
          channel <- Channel.unbounded[IO, WebSocketFrame]
          socket  <- wsb.build(
            send = channel.stream,
            receive = handleProtocolChatGpt(channel)
          )
        yield socket
    }

  private def handleRest(input: RestInput): IO[Response[IO]] =
    restIngress.decode(input) match
      case Left(err) =>
        val result = OperationResult(
          requestId = None,
          payload = OperationPayloadResult.Failed(err)
        )
        val RestResponse(status, body) = restAdapter.encode(result)
        IO.pure(Response(status = status).withEntity(body))

      case Right(request) =>
        request.payload match
          case OperationPayload.Call(op) =>
            val executed = sieService.execute(op)
            val result = OperationResult(
              requestId = request.requestId,
              payload = OperationPayloadResult.Executed(executed)
            )
            val RestResponse(status, body) = restAdapter.encode(result)
            IO.pure(Response(status = status).withEntity(body))

          case other =>
            val result = OperationResult(
              requestId = request.requestId,
              payload = OperationPayloadResult.Failed(
                SimpleProtocolError(
                  ProtocolErrorCode.MethodNotFound,
                  s"unsupported request: $other"
                )
              )
            )
            val RestResponse(status, body) = restAdapter.encode(result)
            IO.pure(Response(status = status).withEntity(body))

  private def handleProtocolHttp(
    req: Request[IO],
    json: Json
  ): IO[Response[IO]] =
    val input = ProtocolHandler.Http.HttpInput(
      method = req.method.name,
      path = req.uri.path.renderString,
      headers = req.headers.headers.map(h => h.name.toString -> h.value).toMap,
      queryParams = req.uri.query.params,
      body = json
    )

    val output =
      ProtocolHandler.Http.handler.handle(input, interactionContext)

    IO.pure(
      Response(
        status =
          org.http4s.Status
            .fromInt(output.status)
            .getOrElse(org.http4s.Status.BadRequest)
      ).withEntity(output.body)
    )

  private def handleProtocolMcp(
    channel: Channel[IO, WebSocketFrame]
  ): Pipe[IO, WebSocketFrame, Unit] =
    _.evalMap {
      case WebSocketFrame.Text(text, _) =>
        // NOTE:
        // MCP currently reuses REST-style JSON tool invocation.
        // ProtocolIngress is intentionally bypassed here.
        val input = WsInput.Text(text)
        val output =
          _mcp_handler.handle(input, interactionContext)
        channel.send(WebSocketFrame.Text(output.message)).void

      case _ =>
        val output = WsOutput("unsupported frame")
        channel.send(WebSocketFrame.Text(output.message)).void
    }

  private def handleProtocolChatGpt(
    channel: Channel[IO, WebSocketFrame]
  ): Pipe[IO, WebSocketFrame, Unit] =
    _.evalMap {
      case WebSocketFrame.Text(text, _) =>
        val payload = parseChatGptInput(text)
        val input = WsInput.ChatGpt(payload)
        val output =
          ProtocolHandler.ChatGpt.handler.handle(input, interactionContext)
        channel.send(WebSocketFrame.Text(output.message)).void

      case _ =>
        val output = WsOutput("unsupported frame")
        channel.send(WebSocketFrame.Text(output.message)).void
    }

  private def parseChatGptInput(
    text: String
  ): ProtocolHandler.ChatGpt.ChatGptInput =
    parse(text) match
      case Left(_) =>
        ProtocolHandler.ChatGpt.ChatGptInput.Message(
          ProtocolHandler.ChatGpt.ChatGptMessage(
            role = "user",
            content = text
          )
        )
      case Right(json) =>
        val cursor = json.hcursor
        val name = cursor.get[String]("name").getOrElse("")
        val arguments =
          cursor
            .downField("arguments")
            .focus
            .flatMap(_.asObject)
            .map { obj =>
              obj.toMap.view.mapValues { value =>
                value.asString.getOrElse(value.noSpaces)
              }.toMap
            }
            .getOrElse(Map.empty)

        if name.nonEmpty then
          ProtocolHandler.ChatGpt.ChatGptInput.ToolCall(
            ProtocolHandler.ChatGpt.ChatGptToolCall(
              name = name,
              arguments = arguments
            )
          )
        else
          ProtocolHandler.ChatGpt.ChatGptInput.Message(
            ProtocolHandler.ChatGpt.ChatGptMessage(
              role = "user",
              content = text
            )
          )

  def start: IO[Nothing] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(port).getOrElse(port"8080"))
      .withHttpWebSocketApp { wsb =>
        (httpRoutes <+> protocolWebSocketRoutes(wsb) <+> websocketRoutes(wsb)).orNotFound
      }
      .build
      .useForever

  private def knowledgeJson(status: Status): Json =
    val doc = status.vectorDb.collections.document
    val concept = status.vectorDb.collections.concept

    def readinessJson(ready: Boolean, message: Option[String] = None): Json =
      Json.obj(
        "status" -> Json.fromString(if ready then "OK" else "ERROR")
      ).deepMerge(
        message
          .map(msg => Json.obj("message" -> Json.fromString(msg)))
          .getOrElse(Json.obj())
      )

    Json.obj(
      "graphdb" ->
        Json.obj(
          "role" -> Json.fromString(status.graphDb.role.toString.toLowerCase),
          "operationalControl" ->
            Json.fromString(knowledgeControl.graphdb.operationalControl.toString),
          "reachable" -> Json.fromBoolean(status.graphDb.reachable),
          "dataset" -> status.graphDb.dataset
            .map(Json.fromString)
            .getOrElse(Json.Null),
          "health" -> readinessJson(status.graphDb.ready)
        ),
      "vectordb" ->
        Json.obj(
          "role" -> Json.fromString(status.vectorDb.role.toString.toLowerCase),
          "operationalControl" ->
            Json.fromString(knowledgeControl.vectordb.operationalControl.toString),
          "collections" -> Json.obj(
            "document" ->
              Json.obj(
                "name" -> Json.fromString(doc.name),
                "origin" -> Json.fromString(doc.origin.toString.toLowerCase),
                "count" -> Json.fromLong(doc.count),
                "ready" -> Json.fromBoolean(doc.ready),
                "rebuildPolicy" -> Json.fromString(doc.rebuildPolicy.toString.toLowerCase)
              ),
            "concept" ->
              Json.obj(
                "name" -> Json.fromString(concept.name),
                "origin" -> Json.fromString(concept.origin.toString.toLowerCase),
                "count" -> Json.fromLong(concept.count),
                "ready" -> Json.fromBoolean(concept.ready),
                "rebuildPolicy" -> Json.fromString(concept.rebuildPolicy.toString.toLowerCase)
              )
          ),
          "health" -> readinessJson(doc.ready || concept.ready)
        )
    )
