package org.simplemodeling.sie.mcp

import cats.effect.*
import cats.syntax.all.*
import fs2.{Pipe, Stream}
import fs2.concurrent.Channel
import io.circe.parser.*
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import org.simplemodeling.sie.service.RagService

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

  private def processJsonRpc(msg: String): IO[String] =
    parse(msg) match
      case Left(err) =>
        IO.pure(
          s"""{"jsonrpc":"2.0","error":{"code":-32700,"message":"invalid json: ${err.getMessage}"}}"""
        )

      case Right(json) =>
        val cursor = json.hcursor
        val method = cursor.get[String]("method").getOrElse("")
        val idJson = cursor.get[io.circe.Json]("id").getOrElse(io.circe.Json.Null)

        method match
          case "initialize" =>
            IO.pure(
              io.circe.Json.obj(
                "jsonrpc" -> io.circe.Json.fromString("2.0"),
                "id"      -> idJson,
                "result"  -> io.circe.Json.obj(
                  "capabilities" -> io.circe.Json.obj(
                    "tools" -> io.circe.Json.arr(
                      io.circe.Json.obj(
                        "name" -> io.circe.Json.fromString("tools.sie.query"),
                        "description" -> io.circe.Json.fromString(
                          "Search the SimpleModeling Knowledge Graph using RDF concept lookup, semantic embeddings, and graph extraction. Use this tool when the user asks about knowledge, domain concepts, definitions, or modeling elements."
                        ),
                        "input_schema" -> io.circe.Json.obj(
                          "type" -> io.circe.Json.fromString("object"),
                          "properties" -> io.circe.Json.obj(
                            "query" -> io.circe.Json.obj(
                              "type" -> io.circe.Json.fromString("string"),
                              "description" -> io.circe.Json.fromString(
                                "Natural-language question. Prefer explicit domain terms such as 'SimpleObject', 'domain modeling layers', or 'knowledge activation'."
                              )
                            )
                          ),
                          "required" -> io.circe.Json.arr(io.circe.Json.fromString("query"))
                        )
                      ),
                      io.circe.Json.obj(
                        "name" -> io.circe.Json.fromString("tools.sie.explainConcept"),
                        "description" -> io.circe.Json.fromString(
                          "Explain a single concept identified by its URI. Returns label, definition/description, and a local RDF graph."
                        ),
                        "input_schema" -> io.circe.Json.obj(
                          "type" -> io.circe.Json.fromString("object"),
                          "properties" -> io.circe.Json.obj(
                            "uri" -> io.circe.Json.obj(
                              "type" -> io.circe.Json.fromString("string"),
                              "description" -> io.circe.Json.fromString("Concept URI to explain.")
                            )
                          ),
                          "required" -> io.circe.Json.arr(io.circe.Json.fromString("uri"))
                        )
                      ),
                      io.circe.Json.obj(
                        "name" -> io.circe.Json.fromString("tools.sie.getNeighbors"),
                        "description" -> io.circe.Json.fromString(
                          "Retrieve the local RDF neighbor graph (broader, narrower, related, subclassOf, refersTo, mentions) for a given concept URI."
                        ),
                        "input_schema" -> io.circe.Json.obj(
                          "type" -> io.circe.Json.fromString("object"),
                          "properties" -> io.circe.Json.obj(
                            "uri" -> io.circe.Json.obj(
                              "type" -> io.circe.Json.fromString("string"),
                              "description" -> io.circe.Json.fromString("Concept URI whose neighbors should be retrieved.")
                            )
                          ),
                          "required" -> io.circe.Json.arr(io.circe.Json.fromString("uri"))
                        )
                      )
                    )
                  ),
                  "usage_examples" -> io.circe.Json.arr(
                    io.circe.Json.obj(
                      "query" -> io.circe.Json.fromString("Explain SimpleObject."),
                      "tool"  -> io.circe.Json.fromString("tools.sie.query")
                    ),
                    io.circe.Json.obj(
                      "query" -> io.circe.Json.fromString("Show neighbors for https://www.simplemodeling.org/glossary/domain-modeling/concept-analysis-design."),
                      "tool"  -> io.circe.Json.fromString("tools.sie.getNeighbors")
                    ),
                    io.circe.Json.obj(
                      "query" -> io.circe.Json.fromString("Give detailed explanation for https://www.simplemodeling.org/simplemodelingorg/ontology/0.1-SNAPSHOT#SimpleObject."),
                      "tool"  -> io.circe.Json.fromString("tools.sie.explainConcept")
                    )
                  )
                )
              ).noSpaces
            )

          case "tools.sie.query" =>
            val query =
              cursor.downField("params").get[String]("query").getOrElse("")

            service.runIO(query).map { rag =>
              io.circe.Json.obj(
                "jsonrpc" -> io.circe.Json.fromString("2.0"),
                "id"      -> idJson,
                "result"  -> io.circe.Json.obj(
                  "concepts" -> rag.asJson.hcursor.get[io.circe.Json]("concepts").getOrElse(io.circe.Json.arr()),
                  "passages" -> rag.asJson.hcursor.get[io.circe.Json]("passages").getOrElse(io.circe.Json.arr()),
                  "summary"  -> io.circe.Json.fromString(
                    "This result includes RDF concept matches and semantically relevant passages from the SimpleModeling Knowledge Base."
                  ),
                  "meta" -> io.circe.Json.obj(
                    "query"     -> io.circe.Json.fromString(query),
                    "engine"    -> io.circe.Json.fromString("SIE-0.1"),
                    "timestamp" -> io.circe.Json.fromString(java.time.Instant.now().toString),
                    "sources"   -> io.circe.Json.arr(
                      io.circe.Json.fromString("Fuseki-RDF"),
                      io.circe.Json.fromString("Chroma-Vector")
                    )
                  ),
                  "hints" -> io.circe.Json.arr(
                    io.circe.Json.fromString("Ask me to explain one of the detected RDF concepts."),
                    io.circe.Json.fromString("Try a related keyword such as 'SimpleObject' or 'domain modeling'."),
                    io.circe.Json.fromString("Ask how this passage relates to the overall SimpleModeling method.")
                  ),
                  "explain" -> io.circe.Json.obj(
                    "rdf"   -> io.circe.Json.fromString("Concepts were retrieved using a SPARQL label/definition search in the Fuseki RDF store."),
                    "vector"-> io.circe.Json.fromString("Passages were retrieved using a hybrid vector search over SmartDox embeddings in Chroma."),
                    "notes" -> io.circe.Json.fromString("Graph-based concept traversal is not yet enabled in this demo.")
                  ),
                  "debug" -> io.circe.Json.obj(
                    "conceptCount" -> io.circe.Json.fromInt(
                      rag.asJson.hcursor.get[io.circe.Json]("concepts").getOrElse(io.circe.Json.arr()).asArray.getOrElse(Vector.empty).size
                    ),
                    "passageCount" -> io.circe.Json.fromInt(
                      rag.asJson.hcursor.get[io.circe.Json]("passages").getOrElse(io.circe.Json.arr()).asArray.getOrElse(Vector.empty).size
                    ),
                    "engineLog" -> io.circe.Json.fromString("Fuseki + Chroma query pipeline completed.")
                  ),
                  "graph" -> rag.asJson.hcursor.get[io.circe.Json]("graph").getOrElse(
                    io.circe.Json.obj(
                      "nodes" -> io.circe.Json.arr(),
                      "edges" -> io.circe.Json.arr(),
                      "notes" -> io.circe.Json.fromString("Graph extraction returned no data.")
                    )
                  )
                )
              ).noSpaces
            }

          case "tools.sie.explainConcept" =>
            val uri =
              cursor.downField("params").get[String]("uri").getOrElse("")
            service.explainConcept(uri).map { exp =>
              io.circe.Json.obj(
                "jsonrpc" -> io.circe.Json.fromString("2.0"),
                "id"      -> idJson,
                "result"  -> exp.asJson
              ).noSpaces
            }

          case "tools.sie.getNeighbors" =>
            val uri =
              cursor.downField("params").get[String]("uri").getOrElse("")
            service.getNeighbors(uri).map { graph =>
              io.circe.Json.obj(
                "jsonrpc" -> io.circe.Json.fromString("2.0"),
                "id"      -> idJson,
                "result"  -> graph.asJson
              ).noSpaces
            }

          case other =>
            IO.pure(
              s"""{"jsonrpc":"2.0","error":{"code":-32601,"message":"unknown method: $other"}}"""
            )
