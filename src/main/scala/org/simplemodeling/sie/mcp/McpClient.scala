package org.simplemodeling.sie.mcp

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import cats.effect.*
import cats.effect.unsafe.implicits.global
import org.http4s.client.Client

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
 * @version Dec. 17, 2025 (clarified multi-role architecture: client/server/proxy)
 * @author  ASAMI, Tomoharu
 */
trait InitializeHandler:
  def onInitialize(req: McpRequest): Json

class MergedResourceInitializeHandler extends InitializeHandler:
  def loadJsonResource(name: String): Json =
    val stream = getClass.getClassLoader.getResourceAsStream(name)
    if stream == null then throw new RuntimeException(s"Resource not found: $name")
    val text = scala.io.Source.fromInputStream(stream).mkString
    parse(text).fold(throw _, identity)

  def merge(a: Json, b: Json): Json =
    a.deepMerge(b)

  def onInitialize(req: McpRequest): Json =
    val init = loadJsonResource("initialize.json")
    val manifest = loadJsonResource("mcp.json")
    merge(init, manifest)

class McpClient(restUrl: String)(using client: Client[IO]):

  private val initHandler: InitializeHandler = new MergedResourceInitializeHandler()

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
        handle(line)

  private def handle(input: String): Unit =
    parse(input) match
      case Left(parseErr) =>
        println(
          McpResponse(error = Some(McpError(1, parseErr.toString))).asJson.noSpaces
        )

      case Right(json) =>
        json.as[McpRequest] match
          case Left(decodeErr) =>
            println(
              McpResponse(error = Some(McpError(2, decodeErr.toString))).asJson.noSpaces
            )

          case Right(req) =>
            val resp = req.method match
              case "initialize" =>
                val merged = initHandler.onInitialize(req)
                McpResponse(id = req.id, result = Some(merged))

              case "tools/sie.query" =>
                McpResponse(id = req.id, error = Some(McpError(501, "Forwarding not implemented")))

              case other =>
                McpResponse(id = req.id, error = Some(McpError(404, s"Unknown: $other")))

            println(resp.asJson.noSpaces)

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
             |  restUrl     = ${restUrl}
             |""".stripMargin
        )

      case ":initialize" =>
        val init = initHandler.onInitialize(
          McpRequest(
            jsonrpc = "2.0",
            id = Some("meta"),
            method = "initialize",
            params = None
          )
        )
        println(init.spaces2)

      case ":manifest" =>
        val manifest = initHandler
          .onInitialize(
            McpRequest(
              jsonrpc = "2.0",
              id = Some("meta"),
              method = "initialize",
              params = None
            )
          )
          .hcursor
          .downField("capabilities")
          .focus
          .getOrElse(Json.Null)

        println(manifest.spaces2)

      case other =>
        System.err.println(s"[mcp-client] unknown meta command: $other (try :help)")
