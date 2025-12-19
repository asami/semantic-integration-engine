package org.simplemodeling.sie.mcp

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import org.simplemodeling.sie.mcp.core.*

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
 * @version Dec. 20, 2025 (clarified multi-role architecture: client/server/proxy)
 * @author  ASAMI, Tomoharu
 */
trait InitializeHandler:
  def onInitialize(): Json

class MergedResourceInitializeHandler extends InitializeHandler:
  def loadJsonResource(name: String): Json =
    val stream = getClass.getClassLoader.getResourceAsStream(name)
    if stream == null then throw new RuntimeException(s"Resource not found: $name")
    val text = scala.io.Source.fromInputStream(stream).mkString
    parse(text).fold(throw _, identity)

  def merge(a: Json, b: Json): Json =
    a.deepMerge(b)

  def onInitialize(): Json =
    val init = loadJsonResource("initialize.json")
    val manifest = loadJsonResource("mcp.json")
    merge(init, manifest)

class McpClient():

  private val initHandler: InitializeHandler = new MergedResourceInitializeHandler()

  private given McpContext =
    McpContext(
      sessionId = "stdio-session",
      traceId   = java.util.UUID.randomUUID().toString
    )

  private val wsUrl: String =
    sys.env.getOrElse("MCP_WS_URL", "ws://localhost:9050/mcp")

  private val httpClient: HttpClient =
    HttpClient.newHttpClient()

  private def withWebSocketOnce(input: String): Unit = {
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
      val result = response.join()
      println(result)
    finally
      ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join()
  }

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

        method match
          case "initialize" =>
            val init = initHandler.onInitialize()
            println(init.noSpaces)

          case "tools/list" | "tools/call" =>
            withWebSocketOnce(input)

          case other =>
            println(
              Json.obj(
                "error" -> Json.obj(
                  "message" -> Json.fromString(s"Unsupported method: $other")
                )
              ).noSpaces
            )

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
        val init = initHandler.onInitialize()
        println(init.spaces2)

      case ":manifest" =>
        val manifest = initHandler
          .onInitialize()
          .hcursor
          .downField("capabilities")
          .focus
          .getOrElse(Json.Null)

        println(manifest.spaces2)

      case other =>
        System.err.println(s"[mcp-client] unknown meta command: $other (try :help)")
