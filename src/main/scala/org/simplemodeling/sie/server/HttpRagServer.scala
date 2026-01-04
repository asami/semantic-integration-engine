package org.simplemodeling.sie.server

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.ember.server.*
import io.circe.{Json, JsonObject}
import org.simplemodeling.sie.service.*
import com.comcast.ip4s.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import fs2.{Pipe, Stream}
import fs2.concurrent.Channel
import org.simplemodeling.sie.mcp.McpWebSocketServer
import org.simplemodeling.sie.config.ServerMode
import org.simplemodeling.sie.interaction.*
import org.simplemodeling.sie.interaction.ProtocolHandler
import org.simplemodeling.sie.interaction.ProtocolHandler.WsInput
import org.simplemodeling.sie.interaction.ProtocolHandler.WsOutput
import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest as GpHttpRequest, HttpResponse as GpHttpResponse, HttpStatus}
import org.goldenport.protocol.{Argument, Property, Request as ProtocolRequest, Response as ProtocolResponse}
import org.goldenport.record.Record

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
  systemStatus: org.simplemodeling.sie.status.Status,
  mcpmergemanifestintoinitialize: Boolean
):

  given EntityEncoder[IO, RagResult] = jsonEncoderOf
  given EntityEncoder[IO, Json] = jsonEncoderOf
  given EntityEncoder[IO, ConceptExplanation] = jsonEncoderOf

  private val sieService = new SieService(service)
  private val cncfComponent =
    org.goldenport.cncf.component.Component.create(org.simplemodeling.sie.protocol.protocol)
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
      _invoke_cncf_http_(req)
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
      Response(status = _to_http4s_status_(_to_http_status_(output.status)))
        .withEntity(output.body)
    )

  private def _invoke_cncf_http_(
    req: Request[IO]
  ): IO[Response[IO]] =
    _to_cncf_http_request_(req).flatMap { cncfReq =>
      cncfComponent.service.invokeHttp(cncfReq) match {
        case Consequence.Success(res) =>
          IO.pure(_from_cncf_http_response_(res))
        case Consequence.Failure(err) =>
          IO.pure(Response(status = _to_http4s_status_(HttpStatus.BadRequest)).withEntity(err.toString))
      }
    }

  private def _to_cncf_http_request_(
    req: Request[IO]
  ): IO[GpHttpRequest] = {
    val url = new java.net.URL(_request_url_(req))
    val method = _request_method_(req)
    val query = Record.create(req.uri.query.params.toVector)
    val headers =
      Record.create(
        req.headers.headers.map(h => h.name.toString -> h.value)
      )

    if req.method == Method.POST then
      req
        .as[UrlForm]
        .map { form =>
          val formRecord = Record.create(form.values.toVector.map { case (k, v) => k -> v })
          GpHttpRequest(url, method, query, formRecord, headers)
        }
        .handleError(_ => GpHttpRequest(url, method, query, Record.empty, headers))
    else
      IO.pure(GpHttpRequest(url, method, query, Record.empty, headers))
  }

  private def _request_url_(
    req: Request[IO]
  ): String =
    s"http://$host:$port${req.uri.renderString}"

  private def _request_method_(
    req: Request[IO]
  ): GpHttpRequest.Method =
    req.method match {
      case Method.GET => GpHttpRequest.GET
      case Method.POST => GpHttpRequest.POST
      case Method.PUT => GpHttpRequest.PUT
      case Method.DELETE => GpHttpRequest.DELETE
      case _ => GpHttpRequest.GET
    }

  private def _from_cncf_http_response_(
    res: GpHttpResponse
  ): Response[IO] = {
    val status = _to_http4s_status_(res.status)
    res.getString match {
      case Some(text) =>
        Response[IO](status = status).withEntity(text)
      case None =>
        res.getBinary match {
          case Some(bin) =>
            val bytes = bin.openInputStream().readAllBytes()
            Response[IO](status = status).withEntity(bytes)
          case None =>
            Response[IO](status = status)
        }
    }
  }

  private def _to_http_status_(
    code: Int
  ): HttpStatus =
    HttpStatus.fromInt(code).getOrElse(HttpStatus.BadRequest)

  private def _to_http4s_status_(
    status: HttpStatus
  ): Status =
    Status.fromInt(status.code).getOrElse(Status.BadRequest)

  private def _handle_mcp_text_(
    text: String
  ): String =
    parse(text) match {
      case Left(err) =>
        _mcp_error_(None, -32600, s"invalid json: ${err.getMessage}")
      case Right(json) =>
        json.as[ProtocolHandler.Mcp.McpRequest] match {
          case Left(err) =>
            _mcp_error_(None, -32600, s"invalid request: ${err.getMessage}")
          case Right(req) =>
            _handle_mcp_request_(req)
        }
    }

  private def _handle_mcp_request_(
    req: ProtocolHandler.Mcp.McpRequest
  ): String = {
    if (req.jsonrpc != "2.0") {
      _mcp_error_(req.id, -32600, s"unsupported jsonrpc version: ${req.jsonrpc}")
    } else {
      req.method match {
        case "initialize" =>
          val base = Json.obj("capabilities" -> Json.obj())
          val body =
            if mcpmergemanifestintoinitialize then
              base.deepMerge(Json.obj("tools" -> _tools_json_(defaultTools)))
            else
              base
          _mcp_result_(req.id, body)

        case "tools/list" =>
          _mcp_result_(req.id, Json.obj("tools" -> _tools_json_(defaultTools)))

        case "get_manifest" =>
          _protocol_engine.getManifest() match {
            case Consequence.Success(json) =>
              _mcp_result_(req.id, json)
            case Consequence.Failure(err) =>
              _mcp_error_(req.id, -32603, err.toString)
          }

        case "tools/call" =>
          _handle_mcp_tool_call_(req)

        case other =>
          _mcp_error_(req.id, -32601, s"unknown method: $other")
      }
    }
  }

  private def _handle_mcp_tool_call_(
    req: ProtocolHandler.Mcp.McpRequest
  ): String =
    req.params match {
      case None =>
        _mcp_error_(req.id, -32602, "missing params")
      case Some(params) =>
        val cursor = params.hcursor
        val name = cursor.get[String]("name").getOrElse("")
        if (name != "query") {
          _mcp_error_(req.id, -32601, s"unknown tool: $name")
        } else {
          val args =
            cursor
              .downField("arguments")
              .focus
              .flatMap(_.asObject)
              .getOrElse(JsonObject.empty)

          val query = args("query").flatMap(_.asString).getOrElse("")
          if (query.isEmpty) {
            _mcp_error_(req.id, -32602, "missing query")
          } else {
            val limit = _parse_limit_(args)
            val request = _build_query_request_(query, limit)
            cncfComponent.service.invokeRequest(request) match {
              case Consequence.Success(res) =>
                _mcp_result_(req.id, _protocol_response_to_json_(res))
              case Consequence.Failure(err) =>
                _mcp_error_(req.id, -32603, err.toString)
            }
          }
        }
    }

  private def _parse_limit_(
    args: JsonObject
  ): Option[Int] =
    args("limit").flatMap(_.asNumber).flatMap(_.toInt)
      .orElse(args("limit").flatMap(_.asString).flatMap(_.toIntOption))

  private def _build_query_request_(
    query: String,
    limit: Option[Int]
  ): ProtocolRequest = {
    val arguments =
      List(
        Argument("query", query, None)
      )
    val properties =
      limit.map(v => Property("limit", v.toString, None)).toList

    ProtocolRequest(
      service = None,
      operation = "query",
      arguments = arguments,
      switches = Nil,
      properties = properties
    )
  }

  private def _protocol_response_to_json_(
    res: ProtocolResponse
  ): Json =
    res match {
      case ProtocolResponse.Json(value) =>
        Json.fromString(value)
      case ProtocolResponse.Scalar(value) =>
        Json.fromString(value.toString)
      case ProtocolResponse.Void() =>
        Json.Null
    }

  private def _tools_json_(
    tools: List[OperationTool]
  ): Json =
    Json.arr(
      tools.map { tool =>
        Json.obj(
          "name" -> Json.fromString(tool.name),
          "description" -> Json.fromString(tool.description),
          "input_schema" -> Json.obj(
            "type" -> Json.fromString("object"),
            "required" -> Json.arr(tool.required.map(Json.fromString)*)
          )
        )
      }*
    )

  private def _mcp_result_(
    id: Option[String],
    result: Json
  ): String =
    Json.obj(
      "jsonrpc" -> Json.fromString("2.0"),
      "id" -> id.map(Json.fromString).getOrElse(Json.Null),
      "result" -> result
    ).noSpaces

  private def _mcp_error_(
    id: Option[String],
    code: Int,
    message: String
  ): String =
    Json.obj(
      "jsonrpc" -> Json.fromString("2.0"),
      "id" -> id.map(Json.fromString).getOrElse(Json.Null),
      "error" -> Json.obj(
        "code" -> Json.fromInt(code),
        "message" -> Json.fromString(message)
      )
    ).noSpaces

  private def handleProtocolMcp(
    channel: Channel[IO, WebSocketFrame]
  ): Pipe[IO, WebSocketFrame, Unit] =
    _.evalMap {
      case WebSocketFrame.Text(text, _) =>
        val output = _handle_mcp_text_(text)
        channel.send(WebSocketFrame.Text(output)).void

      case _ =>
        val output = _mcp_error_(None, -32600, "unsupported frame")
        channel.send(WebSocketFrame.Text(output)).void
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
        (
          httpRoutes <+>
            protocolWebSocketRoutes(wsb) <+>
            websocketRoutes(wsb)
        ).orNotFound
      }
      .build
      .useForever

  private def knowledgeJson(status: org.simplemodeling.sie.status.Status): Json =
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
