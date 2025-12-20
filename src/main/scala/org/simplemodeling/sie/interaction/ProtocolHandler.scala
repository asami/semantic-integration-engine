package org.simplemodeling.sie.interaction

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

final class ProtocolHandler[I, O](
  ingress: ProtocolIngress[I],
  egress: ProtocolEgress[O]
) {
  def handle(
    input: I,
    interaction: InteractionContext
  ): O =
    ingress.decode(input) match {
      case Left(err) =>
        egress.encode(interaction.fail(err))
      case Right(req) =>
        val normalized =
          req.payload match
            case OperationPayload.Call(q: Query) =>
              req.copy(payload = OperationPayload.Call(ProtocolHandler.normalizeQueryForDemo(q)))
            case _ =>
              req
        egress.encode(interaction.execute(normalized))
    }
}

trait InteractionContext {
  def execute(req: OperationRequest): OperationResult
  def fail(err: ProtocolError): OperationResult
}

object ProtocolHandler {
  // Demo-default normalization for Query to match legacy "return everything" behavior.
  private def normalizeQueryForDemo(query: Query): Query =
    query.copy(
      limit = query.limit.orElse(Some(100))
    )

  // Placeholder WebSocket carriers for aggregation
  sealed trait WsInput
  object WsInput {
    final case class Text(message: String) extends WsInput
    final case class ChatGpt(payload: ProtocolHandler.ChatGpt.ChatGptInput) extends WsInput
  }

  final case class WsOutput(
    message: String
  )

  // =========================================================
  // ChatGPT Protocol (migrated from ChatGptIngress.scala,
  // ChatGptAdapter.scala)
  // =========================================================

  object ChatGpt {

    // migrated from ChatGptIngress.scala
    final case class ChatGptToolCall(
      name: String,
      arguments: Map[String, String]
    )

    // migrated from ChatGptIngress.scala
    final case class ChatGptMessage(
      role: String,
      content: String
    )

    // migrated from ChatGptIngress.scala
    sealed trait ChatGptInput

    // migrated from ChatGptIngress.scala
    object ChatGptInput {
      final case class ToolCall(call: ChatGptToolCall) extends ChatGptInput
      final case class Message(message: ChatGptMessage) extends ChatGptInput
    }

    // migrated from ChatGptIngress.scala
    final class ChatGptIngress extends ProtocolIngress[ChatGptInput] {

      override def decode(input: ChatGptInput): Either[ProtocolError, OperationRequest] =
        input match
          case ChatGptInput.ToolCall(call) =>
            decodeToolCall(call)
          case ChatGptInput.Message(_) =>
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.InvalidRequest,
                "chatgpt message input is not supported for operations"
              )
            )

      private def decodeToolCall(call: ChatGptToolCall): Either[ProtocolError, OperationRequest] =
        call.name match
          case "tools/list" =>
            Right(
              OperationRequest(
                requestId = None,
                payload = OperationPayload.ToolsList
              )
            )
          case "query" =>
            val query = call.arguments.getOrElse("query", "")
            if query.isEmpty then
              Left(SimpleProtocolError(ProtocolErrorCode.InvalidParams, "missing query"))
            else
              val limit = call.arguments.get("limit").flatMap(_.toIntOption)
              Right(
                OperationRequest(
                  requestId = None,
                  payload = OperationPayload.Call(Query(query = query, limit = limit))
                )
              )

          case "explainConcept" =>
            val name =
              call.arguments.get("name")
                .orElse(call.arguments.get("id"))
                .orElse(call.arguments.get("uri"))
                .getOrElse("")
            if name.isEmpty then
              Left(SimpleProtocolError(ProtocolErrorCode.InvalidParams, "missing concept name"))
            else
              Right(
                OperationRequest(
                  requestId = None,
                  payload = OperationPayload.Call(ExplainConcept(name = name))
                )
              )

          case other =>
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.MethodNotFound,
                s"unsupported tool: $other"
              )
            )
    }

    // migrated from ChatGptAdapter.scala
    final case class ChatGptResponse(
      content: String
    )

    // migrated from ChatGptAdapter.scala
    final class ChatGptAdapter extends ProtocolEgress[ChatGptResponse] {

      override def encode(result: OperationResult): ChatGptResponse =
        result.payload match
          case OperationPayloadResult.Tools(tools) =>
            val text = tools.map { tool =>
              s"- ${tool.name}: ${tool.description}"
            }.mkString("Available tools:\n", "\n", "")
            ChatGptResponse(text)

          case OperationPayloadResult.Executed(opResult) =>
            ChatGptResponse(encodeOperationResult(opResult))

          case OperationPayloadResult.Failed(error) =>
            ChatGptResponse(s"error: ${error.message}")

          case other =>
            ChatGptResponse(s"unsupported result: ${other.toString}")

      private def encodeOperationResult(result: SieOperationResult): String =
        result match
          case QueryResult(concepts, passages, graph) =>
            val conceptsText =
              if concepts.nonEmpty then concepts.map(_.label).mkString("concepts: ", ", ", "") else ""
            val passagesText =
              if passages.nonEmpty then passages.mkString("passages: ", ", ", "") else ""
            val graphText =
              if graph.nonEmpty then s"graph: $graph" else ""

            List(conceptsText, passagesText, graphText)
              .filter(_.nonEmpty)
              .mkString("\n")

          case ExplainConceptResult(description) =>
            description

          case other =>
            other.toString
    }

    object Ingress extends ProtocolIngress[WsInput] {
      private val delegate = new ChatGptIngress()

      override def decode(input: WsInput): Either[ProtocolError, OperationRequest] =
        input match
          case WsInput.ChatGpt(payload) =>
            delegate.decode(payload)
          case _ =>
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.InternalError,
                "ChatGPT ProtocolIngress not wired yet"
              )
            )
    }

    object Egress extends ProtocolEgress[WsOutput] {
      private val delegate = new ChatGptAdapter()

      override def encode(result: OperationResult): WsOutput =
        WsOutput(delegate.encode(result).content)
    }

    val handler =
      new ProtocolHandler[WsInput, WsOutput](Ingress, Egress)
  }

  // =========================================================
  // MCP JSON-RPC Protocol (migrated from McpJsonRpcIngress.scala,
  // McpJsonRpcAdapter.scala)
  // =========================================================

  object Mcp {

    // migrated from McpProtocol.scala
    final case class McpRequest(
      jsonrpc: String,
      id: Option[String],
      method: String,
      params: Option[Json]
    )

    // migrated from McpProtocol.scala
    final case class McpError(
      code: Int,
      message: String
    )

    // migrated from McpProtocol.scala
    final case class McpResponse(
      jsonrpc: String = "2.0",
      id: Option[String],
      result: Option[Json] = None,
      error: Option[McpError] = None
    )

    // migrated from McpProtocol.scala
    given Decoder[McpRequest] = Decoder.instance { cursor =>
      for {
        jsonrpc <- cursor.get[String]("jsonrpc")
        id      <- cursor.get[Option[String]]("id")
        method  <- cursor.get[String]("method")
        params  <- cursor.get[Option[Json]]("params")
      } yield McpRequest(jsonrpc, id, method, params)
    }

    // migrated from McpProtocol.scala
    given Encoder[McpError] = Encoder.instance { err =>
      Json.obj(
        "code" -> Json.fromInt(err.code),
        "message" -> Json.fromString(err.message)
      )
    }

    // migrated from McpProtocol.scala
    given Encoder[McpResponse] = Encoder.instance { res =>
      val base =
        List(
          Some("jsonrpc" -> Json.fromString(res.jsonrpc)),
          Some("id" -> res.id.map(Json.fromString).getOrElse(Json.Null)),
          res.result.map(r => "result" -> r),
          res.error.map(e => "error" -> e.asJson)
        ).flatten

      Json.obj(base*)
    }

    // migrated from McpJsonRpcIngress.scala
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
            ).flatMap { req =>
              if req.jsonrpc != "2.0" then
                Left(
                  SimpleProtocolError(
                    ProtocolErrorCode.InvalidRequest,
                    s"unsupported jsonrpc version: ${req.jsonrpc}"
                  )
                )
              else
                req.method match
                  case "initialize" =>
                    Right(OperationRequest(req.id, OperationPayload.Initialize))

                  case "tools/list" =>
                    Right(OperationRequest(req.id, OperationPayload.ToolsList))

                  case "tools/call" =>
                    decodeToolCall(req)

                  case other =>
                    Left(
                      SimpleProtocolError(
                        ProtocolErrorCode.MethodNotFound,
                        s"unknown method: $other"
                      )
                    )
            }
          }

      private def decodeToolCall(req: McpRequest): Either[ProtocolError, OperationRequest] =
        val cursorOpt = req.params.map(_.hcursor)
        cursorOpt match
          case None =>
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.InvalidParams,
                "missing params"
              )
            )
          case Some(cursor) =>
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
                .getOrElse(io.circe.JsonObject.empty)

              decodeToolArguments(req.id, name, args)

      private def decodeToolArguments(
        requestId: Option[String],
        name: String,
        args: io.circe.JsonObject
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
                  requestId,
                  OperationPayload.Call(Query(query = query, limit = limit))
                )
              )

          case "explainConcept" =>
            val nameValue =
              args("name").flatMap(_.asString)
                .orElse(args("id").flatMap(_.asString))
                .orElse(args("uri").flatMap(_.asString))
                .getOrElse("")

            if nameValue.isEmpty then
              Left(
                SimpleProtocolError(
                  ProtocolErrorCode.InvalidParams,
                  "missing concept name"
                )
              )
            else
              Right(
                OperationRequest(
                  requestId,
                  OperationPayload.Call(ExplainConcept(name = nameValue))
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

    // migrated from McpJsonRpcAdapter.scala
    final class McpJsonRpcAdapter(
      serverName: String,
      serverVersion: String
    ) extends ProtocolEgress[Json] {

      override def encode(result: OperationResult): Json =
        result.payload match
          case OperationPayloadResult.Initialized(_, _, capabilities) =>
            val body = Json.obj(
              "serverName" -> Json.fromString(serverName),
              "serverVersion" -> Json.fromString(serverVersion),
              "capabilities" -> encodeCapabilities(capabilities)
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
            val body = encodeOperationResult(opResult)
            McpResponse(id = result.requestId, result = Some(body)).asJson

          case OperationPayloadResult.Failed(error) =>
            val code = mapErrorCode(error.code)
            val err = McpError(code, error.message)
            McpResponse(id = result.requestId, error = Some(err)).asJson

      private def encodeCapabilities(capabilities: Map[String, Any]): Json =
        Json.obj(
          capabilities.toSeq.map { case (key, value) =>
            key -> Json.fromString(value.toString)
          }*
        )

      private def encodeOperationResult(result: SieOperationResult): Json =
        result match
          case QueryResult(concepts, passages, graph) =>
            Json.obj(
              "concepts" -> concepts.asJson,
              "passages" -> passages.asJson,
              "graph"    -> parse(graph).getOrElse(Json.fromString(graph))
            )

          case ExplainConceptResult(description) =>
            Json.obj(
              "description" -> Json.fromString(description)
            )

          case other =>
            Json.obj(
              "result" -> Json.fromString(other.toString)
            )

      private def mapErrorCode(code: ProtocolErrorCode): Int =
        code match
          case ProtocolErrorCode.InvalidRequest => -32600
          case ProtocolErrorCode.MethodNotFound => -32601
          case ProtocolErrorCode.InvalidParams  => -32602
          case ProtocolErrorCode.InternalError  => -32603
    }

    object Ingress extends ProtocolIngress[WsInput] {
      private val delegate = new McpJsonRpcIngress()

      override def decode(input: WsInput): Either[ProtocolError, OperationRequest] =
        input match
          case WsInput.Text(message) =>
            delegate.decode(message)
          case _ =>
            Left(
              SimpleProtocolError(
                ProtocolErrorCode.InternalError,
                "MCP ProtocolIngress not wired yet"
              )
            )
    }

    object Egress extends ProtocolEgress[WsOutput] {
      private val delegate = new McpJsonRpcAdapter(
        serverName = "semantic-integration-engine",
        serverVersion = "0.1.0"
      )

      override def encode(result: OperationResult): WsOutput =
        WsOutput(delegate.encode(result).noSpaces)
    }

    val handler =
      new ProtocolHandler[WsInput, WsOutput](Ingress, Egress)
  }

  // =========================================================
  // HTTP REST Protocol (migrated from RestProtocol.scala)
  // =========================================================

  object Http {

    final case class HttpInput(
      method: String,
      path: String,
      headers: Map[String, String],
      queryParams: Map[String, String],
      body: Json
    )

    final case class HttpOutput(
      status: Int,
      body: Json
    )

    // migrated from RestProtocol.scala
    final class RestIngress extends ProtocolIngress[HttpInput] {

      override def decode(input: HttpInput): Either[ProtocolError, OperationRequest] =
        val cursor = input.body.hcursor

        cursor.get[String]("method").toOption match
          case Some("tools/list") =>
            Right(
              OperationRequest(
                requestId = None,
                payload = OperationPayload.ToolsList
              )
            )
          case _ =>
            val path = input.path
            (input.method, path) match
              case (m, p) if p.endsWith("/api/query") && (m == "POST" || m == "GET") =>
                val query =
                  input.queryParams.get("query")
                    .orElse(input.body.hcursor.get[String]("query").toOption)
                    .getOrElse("")

                if query.isEmpty then
                  Left(SimpleProtocolError(ProtocolErrorCode.InvalidParams, "missing query"))
                else
                  val limit =
                    input.queryParams.get("limit").flatMap(_.toIntOption)
                      .orElse(input.body.hcursor.get[Int]("limit").toOption)

                  Right(
                    OperationRequest(
                      requestId = None,
                      payload = OperationPayload.Call(Query(query = query, limit = limit))
                    )
                  )

              case ("POST", p) if p.endsWith("/api/explainConcept") =>
                val name =
                  input.queryParams.get("name")
                    .orElse(input.queryParams.get("uri"))
                    .orElse(input.body.hcursor.get[String]("name").toOption)
                    .orElse(input.body.hcursor.get[String]("uri").toOption)
                    .orElse(input.body.hcursor.get[String]("id").toOption)
                    .getOrElse("")

                if name.isEmpty then
                  Left(SimpleProtocolError(ProtocolErrorCode.InvalidParams, "missing concept name"))
                else
                  Right(
                    OperationRequest(
                      requestId = None,
                      payload = OperationPayload.Call(ExplainConcept(name = name))
                    )
                  )

              case _ =>
                Left(SimpleProtocolError(ProtocolErrorCode.MethodNotFound, s"unsupported route: $path"))
    }

    // migrated from RestProtocol.scala
    final class RestAdapter extends ProtocolEgress[HttpOutput] {

      override def encode(result: OperationResult): HttpOutput =
        result.payload match
          case OperationPayloadResult.Tools(tools) =>
            val toolsJson =
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
            HttpOutput(
              200,
              Json.obj("tools" -> toolsJson)
            )

          case OperationPayloadResult.Executed(opResult) =>
            HttpOutput(200, encodeOperationResult(opResult))

          case OperationPayloadResult.Failed(error) =>
            HttpOutput(
              400,
              Json.obj("error" -> Json.fromString(error.message))
            )

          case _ =>
            HttpOutput(
              400,
              Json.obj("error" -> Json.fromString("unsupported request"))
            )

      private def encodeOperationResult(result: SieOperationResult): Json =
        result match
          case QueryResult(concepts, passages, graph) =>
            Json.obj(
              "concepts" -> concepts.asJson,
              "passages" -> passages.asJson,
              "graph"    -> graph.asJson
            )

          case ExplainConceptResult(description) =>
            Json.obj(
              "description" -> Json.fromString(description)
            )

          case other =>
            Json.obj(
              "result" -> Json.fromString(other.toString)
            )
    }

    object Ingress extends ProtocolIngress[HttpInput] {
      private val delegate = new RestIngress()

      override def decode(input: HttpInput): Either[ProtocolError, OperationRequest] =
        delegate.decode(input)
    }

    object Egress extends ProtocolEgress[HttpOutput] {
      private val delegate = new RestAdapter()

      override def encode(result: OperationResult): HttpOutput =
        delegate.encode(result)
    }

    val handler =
      new ProtocolHandler[HttpInput, HttpOutput](Ingress, Egress)
  }
}
