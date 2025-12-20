package org.simplemodeling.sie.mcp.core

import io.circe.{Json, JsonObject}

/**
 * MCP Core
 *
 * - Protocol-agnostic
 * - Transport-agnostic
 * - ChatGPT / JSON-RPC unaware
 *
 * This file intentionally contains all MCP Core definitions
 * to keep the semantic model visible in one place.
 */
/*
 * @since   Dec. 19, 2025
 * @version Dec. 20, 2025
 * @author  ASAMI, Tomoharu
 */

/* ============================================================
 * Context
 * ============================================================
 */

final case class McpContext(
  sessionId: String,
  traceId: String
)

/* ============================================================
 * Result / Error
 * ============================================================
 */

sealed trait McpResult[+A]

object McpResult {
  final case class Success[A](value: A) extends McpResult[A]
  final case class Failure(error: McpError) extends McpResult[Nothing]
}

sealed trait McpError {
  def code: McpErrorCode
  def message: String
  def detail: Option[Any]
}

final case class SimpleMcpError(
  code: McpErrorCode,
  message: String,
  detail: Option[Any] = None
) extends McpError

sealed trait McpErrorCode {
  def value: String
}

object McpErrorCode {
  case object InvalidRequest extends McpErrorCode { val value = "invalid_request" }
  case object MethodNotFound extends McpErrorCode { val value = "method_not_found" }
  case object InvalidParams extends McpErrorCode { val value = "invalid_params" }
  case object ToolExecutionFailed extends McpErrorCode { val value = "tool_execution_failed" }
  case object InternalError extends McpErrorCode { val value = "internal_error" }
}

/* ============================================================
 * Requests
 * ============================================================
 */

sealed trait McpRequest

object McpRequest {

  final case class Initialize(
    protocolVersion: String,
    clientName: String,
    clientVersion: Option[String]
  ) extends McpRequest

  case object ToolsList extends McpRequest

  final case class ToolsCall(
    name: String,
    arguments: JsonObject
  ) extends McpRequest

  case object ResourcesList extends McpRequest
}

/* ============================================================
 * Responses
 * ============================================================
 */

sealed trait McpResponse

object McpResponse {

  final case class Initialized(
    serverName: String,
    serverVersion: String,
    capabilities: Map[String, Any]
  ) extends McpResponse

  final case class ToolsList(
    tools: List[McpTool]
  ) extends McpResponse

  final case class ToolResult(
    content: McpContent
  ) extends McpResponse

  final case class ResourcesList(
    resources: List[McpResource]
  ) extends McpResponse
}

/* ============================================================
 * Tool / Content Model
 * ============================================================
 */

final case class McpTool(
  name: String,
  description: String,
  inputSchema: JsonSchema
)

/**
 * Placeholder for JSON Schema representation.
 * Can be replaced with a real schema model later.
 */
final case class JsonSchema(
  raw: String,
  required: List[String] = Nil
) {
  def validate(args: JsonObject): Boolean =
    required.forall(args.contains)
}

final case class McpContent(
  items: List[McpItem]
)

sealed trait McpItem {
  def kind: String
}

/* ---- Standard Item Types ---- */

final case class ConceptItem(
  id: String,
  title: String,
  summary: String
) extends McpItem {
  val kind: String = "concept"
}

final case class PassageItem(
  documentId: String,
  text: String,
  score: Double
) extends McpItem {
  val kind: String = "passage"
}

final case class JsonItem(
  override val kind: String,
  value: Json
) extends McpItem

/* Placeholder */
final case class McpResource(
  id: String,
  kind: String,
  attributes: Map[String, Any] = Map.empty
)

/* ============================================================
 * MCP Core API
 * ============================================================
 */

trait McpCore {

  def initialize(
    req: McpRequest.Initialize
  )(using ctx: McpContext): McpResult[McpResponse.Initialized]

  def toolsList()(
    using ctx: McpContext
  ): McpResult[McpResponse.ToolsList]

  def callTool(
    req: McpRequest.ToolsCall
  )(using ctx: McpContext): McpResult[McpResponse.ToolResult]

  def resourcesList()(
    using ctx: McpContext
  ): McpResult[McpResponse.ResourcesList]
}

/* ============================================================
 * Default Implementation (Stub)
 * ============================================================
 */

@deprecated("MCP execution is handled by ProtocolIngress/ProtocolEgress", "2025-12-20")
final class DefaultMcpCore(
  tools: List[McpTool]
) extends McpCore {

  private val builtinTools: List[McpTool] =
    List(
      McpTool(
        name = "query",
        description = "Semantic query using existing query implementation",
        inputSchema = JsonSchema(
          raw = "{}",
          required = List("query")
        )
      )
    )

  private def normalizeTool(tool: McpTool): McpTool =
    tool match
      case t if t.name == "explainConcept" && t.inputSchema.required.isEmpty =>
        t.copy(
          inputSchema = t.inputSchema.copy(
            required = List("id")
          )
        )
      case other =>
        other

  private val registeredTools: List[McpTool] =
    val normalized = tools.map(normalizeTool)
    val existingNames = normalized.map(_.name).toSet
    val builtin =
      builtinTools
        .map(normalizeTool)
        .filterNot(tool => existingNames.contains(tool.name))
    normalized ++ builtin


  override def initialize(
    req: McpRequest.Initialize
  )(using ctx: McpContext): McpResult[McpResponse.Initialized] =
    McpResult.Success(
      McpResponse.Initialized(
        serverName = "semantic-integration-engine",
        serverVersion = "0.1.0",
        capabilities = Map(
          "tools" -> true,
          "resources" -> true
        )
      )
    )

  override def toolsList()(
    using ctx: McpContext
  ): McpResult[McpResponse.ToolsList] =
    McpResult.Success(
      McpResponse.ToolsList(registeredTools)
    )

  override def callTool(
    req: McpRequest.ToolsCall
  )(using ctx: McpContext): McpResult[McpResponse.ToolResult] =
    registeredTools.find(_.name == req.name) match
      case Some(tool) =>
        if !tool.inputSchema.validate(req.arguments) then
          return McpResult.Failure(
            SimpleMcpError(
              McpErrorCode.InvalidParams,
              s"Invalid parameters for tool: ${req.name}"
            )
          )
      case None =>
        ()

    McpResult.Failure(
      SimpleMcpError(
        McpErrorCode.MethodNotFound,
        "McpCore is deprecated; use ProtocolIngress/ProtocolEgress with SieService."
      )
    )

  override def resourcesList()(
    using ctx: McpContext
  ): McpResult[McpResponse.ResourcesList] =
    McpResult.Success(
      McpResponse.ResourcesList(Nil)
    )
}
