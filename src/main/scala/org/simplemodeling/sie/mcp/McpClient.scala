package org.simplemodeling.sie.mcp

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.simplemodeling.sie.mcp.core.McpContext
import com.typesafe.config.ConfigFactory

import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.CompletableFuture

/*
 * Architectural Role:
 *   - This component is a *client* of the McpWebSocketServer within the SIE architecture.
 *
 * MCP Protocol Role:
 *   - From the perspective of external AI tools (e.g., VSCode, CLI agents),
 *     this process acts as an MCP *server* over STDIN/STDOUT, because it is the
 *     endpoint that receives MCP requests and returns responses according to the protocol.
 *
 * Implementation Role:
 *   - Functionally, this class is a *Stdio Proxy / Gateway*, forwarding MCP
 *     requests to the unified WebSocket-based MCP server (McpWebSocketServer),
 *     ensuring that both WebSocket clients and STDIO-based clients share the same
 *     behavior and tool definitions.
 *
 * Summary:
 *   Internally (SIE): Client
 *   Externally (MCP): Server
 *   Implementation:   Stdio Proxy / Gateway
 *
 * This dual identity is intentional and reflects the architecture:
 *   - The Stdio-facing side exposes an MCP server interface.
 *   - The internal side delegates all logic to the central WebSocket MCP server.
 *
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec. 21, 2025 (clarified multi-role architecture: client/server/proxy)
 * @author  ASAMI, Tomoharu
 */
class McpClient():

  private given McpContext =
    McpContext(
      sessionId = "stdio-session",
      traceId   = java.util.UUID.randomUUID().toString
    )

  private val wsUrl: String =
    sys.env.getOrElse("MCP_WS_URL", "ws://localhost:9050/mcp")

  private val httpClient: HttpClient =
    HttpClient.newHttpClient()

  private def _merge_manifest_into_initialize(): Boolean =
    val config = ConfigFactory.load()
    if config.hasPath("mcp.merge-manifest-into-initialize") then
      config.getBoolean("mcp.merge-manifest-into-initialize")
    else
      sys.env.get("SIE_MCP_MERGE_MANIFEST").exists(_.equalsIgnoreCase("true"))

  private def _with_web_socket_once(input: String): String = {
    val response = new CompletableFuture[String]()

    val listener = new WebSocket.Listener {
      private val buffer = new StringBuilder

      override def onOpen(webSocket: WebSocket): Unit = {
        webSocket.request(1)
      }

      override def onText(
        webSocket: WebSocket,
        data: CharSequence,
        last: Boolean
      ): java.util.concurrent.CompletionStage[?] = {
        buffer.append(data)
        if last then
          response.complete(buffer.toString())
        webSocket.request(1)
        CompletableFuture.completedFuture(())
      }

      override def onError(webSocket: WebSocket, error: Throwable): Unit = {
        response.completeExceptionally(error)
      }
    }

    val ws =
      httpClient
        .newWebSocketBuilder()
        .subprotocols("mcp")
        .buildAsync(URI.create(wsUrl), listener)
        .join()

    try
      ws.sendText(input, true).join()
      response.join()
    finally
      ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join()
  }

  private def _send_web_socket_once(input: String): Unit =
    val result = _with_web_socket_once(input)
    println(result)

  private def _fetch_tools_list(requestid: Option[String]): Option[Json] =
    val toolsrequest =
      Json.obj(
        "jsonrpc" -> Json.fromString("2.0"),
        "id" -> requestid.map(Json.fromString).getOrElse(Json.Null),
        "method" -> Json.fromString("tools/list")
      )

    val response = _with_web_socket_once(toolsrequest.noSpaces)

    parse(response).toOption
      .flatMap(_.hcursor.downField("result").downField("tools").focus)

  private def _render_initialize(requestid: Option[String]): String =
    val base =
      List(
        Some("capabilities" -> Json.obj())
      )

    val merged =
      if _merge_manifest_into_initialize() then
        val toolsjson = _fetch_tools_list(requestid)
          .getOrElse(Json.arr())
        base :+ Some("tools" -> toolsjson)
      else
        base

    McpResponse
      .success(requestid, Json.obj(merged.flatten*))
      .asJson
      .noSpaces

  private def _render_manifest(requestid: Option[String]): String =
    val toolsjson = _fetch_tools_list(requestid).getOrElse(Json.arr())
    McpResponse
      .success(requestid, Json.obj("tools" -> toolsjson))
      .asJson
      .noSpaces

  def start(): Unit =
    // Register shutdown hook to make Ctrl-C / SIGTERM visible and debuggable
    sys.addShutdownHook {
      System.err.println("[mcp-client] shutdown hook called")
    }

    val reader = scala.io.StdIn
    while true do
      val line = reader.readLine()
      if line == null then
        // stdin closed (EOF) -> exit normally
        System.err.println("[mcp-client] stdin closed, exiting")
        sys.exit(0)
      else if line.trim.nonEmpty then
        if line.trim.startsWith(":") then
          handleMetaCommand(line.trim)
        else
          handle(line)

  private def handle(input: String): Unit =
    parse(input) match
      case Left(parseErr) =>
        println(
          Json.obj(
            "error" -> Json.obj(
              "message" -> Json.fromString(parseErr.toString)
            )
          ).noSpaces
        )

      case Right(json) =>
        val cursor = json.hcursor
        val method = cursor.get[String]("method").getOrElse("")
        val requestid = cursor.get[Option[String]]("id").getOrElse(None)

        _send_web_socket_once(input)

  private def handleMetaCommand(cmd: String): Unit =
    cmd match
      case ":exit" | ":quit" =>
        System.err.println("[mcp-client] exit requested by meta-command")
        sys.exit(0)

      case ":help" =>
        System.err.println(
          """[mcp-client] meta commands:
            |  :help         show this help
            |  :status       show client status
            |  :initialize   dump merged initialize response
            |  :manifest     dump MCP manifest (tools definition)
            |  :exit         exit client
            |  :quit         exit client
            |""".stripMargin
        )

      case ":status" =>
        System.err.println(
          s"""[mcp-client] status:
             |  role        = stdio MCP proxy
             |  transport   = stdin/stdout
             |""".stripMargin
        )

      case ":initialize" =>
        val req =
          Json.obj(
            "jsonrpc" -> Json.fromString("2.0"),
            "id" -> Json.fromString("meta-initialize"),
            "method" -> Json.fromString("initialize")
          )
        _send_web_socket_once(req.noSpaces)

      case ":manifest" =>
        val req =
          Json.obj(
            "jsonrpc" -> Json.fromString("2.0"),
            "id" -> Json.fromString("meta-manifest"),
            "method" -> Json.fromString("get_manifest")
          )
        _send_web_socket_once(req.noSpaces)

      case other =>
        System.err.println(s"[mcp-client] unknown meta command: $other (try :help)")
