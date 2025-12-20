package org.simplemodeling.sie.mcp

import cats.effect.*
import fs2.Pipe
import fs2.concurrent.Channel
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import org.simplemodeling.sie.BuildInfo
import org.simplemodeling.sie.interaction.*

/*
 * Minimal WebSocket MCP server for JSON-RPC 2.0.
 *
 * This server:
 *   1) Receives JSON-RPC
 *   2) Decodes via ProtocolIngress
 *   3) Executes via SieService
 *   4) Encodes via ProtocolEgress
 *
 * @since   Dec.  4, 2025
 * @version Dec. 20, 2025
 * @author  ASAMI, Tomoharu
 */
class McpWebSocketServer(service: SieService):
  import McpWebSocketServer._

  private val _ingress = new McpJsonRpcIngress()
  private val _adapter = new McpJsonRpcEgress(
    servername = BuildInfo.name,
    serverversion = BuildInfo.version
  )

  private val _tools: List[OperationTool] =
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

  def routes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "mcp" =>
        for
          channel <- Channel.unbounded[IO, WebSocketFrame]
          socket  <- wsb.build(
            send = channel.stream,
            receive = _handle_incoming(channel)
          )
        yield socket
    }

  private def _handle_incoming(
      channel: Channel[IO, WebSocketFrame]
  ): Pipe[IO, WebSocketFrame, Unit] =
    _.evalMap {
      case WebSocketFrame.Text(text, _) =>
        for
          response <- _process_json_rpc(text)
          _        <- channel.send(WebSocketFrame.Text(response))
        yield ()

      case _ =>
        channel.send(
          WebSocketFrame.Text(_adapter.encode(_error_result("unsupported frame")).noSpaces)
        ).void
    }

  private def _process_json_rpc(msg: String): IO[String] =
    _ingress.decode(msg) match
      case Left(err) =>
        IO.pure(_adapter.encode(_error_result(err)).noSpaces)

      case Right(request) =>
        val result = request.payload match
          case OperationPayload.Initialize =>
            OperationResult(
              request.requestId,
              OperationPayloadResult.Initialized(
                serverName = BuildInfo.name,
                serverVersion = BuildInfo.version,
                capabilities = Map("tools" -> true)
              )
            )

          case OperationPayload.ToolsList =>
            OperationResult(
              request.requestId,
              OperationPayloadResult.Tools(_tools)
            )

          case OperationPayload.Call(op) =>
            val executed = service.execute(op)
            OperationResult(
              request.requestId,
              OperationPayloadResult.Executed(executed)
            )

        IO.pure(_adapter.encode(result).noSpaces)

  private def _error_result(message: String): OperationResult =
    OperationResult(
      requestId = None,
      payload = OperationPayloadResult.Failed(
        SimpleProtocolError(ProtocolErrorCode.InvalidRequest, message)
      )
    )

  private def _error_result(error: ProtocolError): OperationResult =
    OperationResult(
      requestId = None,
      payload = OperationPayloadResult.Failed(error)
    )

object McpWebSocketServer {
  final class McpJsonRpcIngress extends ProtocolIngress[String] {
    override def decode(input: String): Either[ProtocolError, OperationRequest] =
      parse(input)
        .left.map(err =>
          SimpleProtocolError(
            ProtocolErrorCode.InvalidRequest,
            s"invalid json: ${err.getMessage}"
          )
        )
        .flatMap { json =>
          json.as[McpRequest].left.map(err =>
            SimpleProtocolError(
              ProtocolErrorCode.InvalidRequest,
              s"invalid request: ${err.getMessage}"
            )
          ).flatMap { request =>
            if request.jsonrpc != "2.0" then
              Left(
                SimpleProtocolError(
                  ProtocolErrorCode.InvalidRequest,
                  s"unsupported jsonrpc version: ${request.jsonrpc}"
                )
              )
            else
              request.method match
                case "initialize" =>
                  Right(OperationRequest(request.id, OperationPayload.Initialize))

                case "tools/list" =>
                  Right(OperationRequest(request.id, OperationPayload.ToolsList))

                case "tools/call" =>
                  _decode_tool_call(request)

                case other =>
                  Left(
                    SimpleProtocolError(
                      ProtocolErrorCode.MethodNotFound,
                      s"unknown method: $other"
                    )
                  )
          }
        }

    private def _decode_tool_call(
      request: McpRequest
    ): Either[ProtocolError, OperationRequest] =
      request.params match
        case None =>
          Left(
            SimpleProtocolError(
              ProtocolErrorCode.InvalidParams,
              "missing params"
            )
          )
        case Some(params) =>
          val cursor = params.hcursor
          val name = cursor.get[String]("name").getOrElse("")
          if name.isEmpty then
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.InvalidParams,
                "missing tool name"
              )
            )
          else
            val args = cursor.downField("arguments").focus
              .flatMap(_.asObject)
              .getOrElse(JsonObject.empty)
            _decode_tool_arguments(request.id, name, args)

    private def _decode_tool_arguments(
      requestid: Option[String],
      name: String,
      args: JsonObject
    ): Either[ProtocolError, OperationRequest] =
      name match
        case "query" =>
          val query = args("query").flatMap(_.asString).getOrElse("")
          if query.isEmpty then
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.InvalidParams,
                "missing query"
              )
            )
          else
            val limit = args("limit").flatMap(_.asNumber).flatMap(_.toInt)
            Right(
              OperationRequest(
                requestid,
                OperationPayload.Call(Query(query = query, limit = limit))
              )
            )

        case "explainConcept" =>
          val namevalue =
            args("name").flatMap(_.asString)
              .orElse(args("id").flatMap(_.asString))
              .orElse(args("uri").flatMap(_.asString))
              .getOrElse("")

          if namevalue.isEmpty then
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.InvalidParams,
                "missing concept name"
              )
            )
          else
            Right(
              OperationRequest(
                requestid,
                OperationPayload.Call(ExplainConcept(name = namevalue))
              )
            )

        case other =>
          Left(
            SimpleProtocolError(
              ProtocolErrorCode.MethodNotFound,
              s"unknown tool: $other"
            )
          )
  }

  final class McpJsonRpcEgress(
    servername: String,
    serverversion: String
  ) extends ProtocolEgress[Json] {

    override def encode(result: OperationResult): Json =
      result.payload match
        case OperationPayloadResult.Initialized(_, _, capabilities) =>
          val body = Json.obj(
            "serverName" -> Json.fromString(servername),
            "serverVersion" -> Json.fromString(serverversion),
            "capabilities" -> _encode_capabilities(capabilities)
          )
          McpResponse(id = result.requestId, result = Some(body)).asJson

        case OperationPayloadResult.Tools(tools) =>
          val toolsJson = Json.arr(
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

          val body = Json.obj("tools" -> toolsJson)
          McpResponse(id = result.requestId, result = Some(body)).asJson

        case OperationPayloadResult.Executed(opResult) =>
          val body = _encode_operation_result(opResult)
          McpResponse(id = result.requestId, result = Some(body)).asJson

        case OperationPayloadResult.Failed(error) =>
          val code = _map_error_code(error.code)
          val err = McpError(code, error.message)
          McpResponse(id = result.requestId, error = Some(err)).asJson

    private def _encode_capabilities(capabilities: Map[String, Any]): Json =
      Json.obj(
        capabilities.toSeq.map { case (key, value) =>
          val jsonvalue = value match
            case s: String => Json.fromString(s)
            case b: Boolean => Json.fromBoolean(b)
            case n: Int => Json.fromInt(n)
            case n: Long => Json.fromLong(n)
            case n: Double => Json.fromDoubleOrNull(n)
            case n: Float => Json.fromFloatOrNull(n)
            case n: BigDecimal => Json.fromBigDecimal(n)
            case n: BigInt => Json.fromBigInt(n)
            case other => Json.fromString(other.toString)
          key -> jsonvalue
        }*
      )

    private def _encode_operation_result(result: SieOperationResult): Json =
      result match
        case QueryResult(concepts, passages, graph) =>
          Json.obj(
            "concepts" -> concepts.asJson,
            "passages" -> passages.asJson,
            "graph" -> parse(graph).fold(_ => Json.fromString(graph), identity)
          )

        case ExplainConceptResult(description) =>
          Json.obj(
            "description" -> Json.fromString(description)
          )

        case other =>
          Json.obj(
            "result" -> Json.fromString(other.toString)
          )

    private def _map_error_code(code: ProtocolErrorCode): Int =
      code match
        case ProtocolErrorCode.InvalidRequest => -32600
        case ProtocolErrorCode.MethodNotFound => -32601
        case ProtocolErrorCode.InvalidParams  => -32602
        case ProtocolErrorCode.InternalError  => -32603
  }
}
