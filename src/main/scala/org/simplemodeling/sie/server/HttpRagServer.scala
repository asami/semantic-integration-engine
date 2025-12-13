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
import fs2.Stream
import org.simplemodeling.sie.mcp.McpWebSocketServer
import org.simplemodeling.sie.config.ServerMode
import org.simplemodeling.sie.status.Status

import cats.syntax.semigroupk.*

import org.simplemodeling.sie.build.BuildInfo
import io.circe.syntax.*

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
 * @version Dec. 13, 2025
 * @author  ASAMI, Tomoharu
 */
class HttpRagServer(
  service: RagService,
  host: String,
  port: Int,
  mode: ServerMode,
  knowledgeControl: org.simplemodeling.sie.config.KnowledgeStoresConfig,
  systemStatus: Status
):

  given EntityEncoder[IO, RagResult] = jsonEncoderOf
  given EntityEncoder[IO, Json] = jsonEncoderOf
  given EntityEncoder[IO, ConceptExplanation] = jsonEncoderOf

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
      val build   = BuildInfo.gitCommit

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

    case GET -> Root / "status" / "v0.1" =>
      // Legacy status (v0.1): preserved as-is for backward compatibility
      for {
        report <- service.health()
        sanitizedReport =
          report
            .mapObject(_.remove("embedding"))
            .mapObject(_.remove("status"))

        version = BuildInfo.version
        build   = BuildInfo.gitCommit

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

    case req @ POST -> Root / "sie" / "query" =>
      for
        json  <- req.as[Json]
        query <- IO(json.hcursor.get[String]("query").getOrElse(""))
        result <- IO(service.run(query))
                    .handleError { e =>
                      println(s"[HttpRagServer] error in /sie/query: ${e.toString}")
                      RagResult(
                        concepts = Nil,
                        passages = Nil,
                        graph = org.simplemodeling.sie.service.GraphResult(Nil, Nil)
                      )
                    }
        resp  <- Ok(result)
      yield resp

    case req @ POST -> Root / "sie" / "explain" =>
      for {
        json      <- req.as[Json]
        uri       <- IO(json.hcursor.get[String]("uri").getOrElse(""))
        localeStr <- IO(json.hcursor.get[String]("locale").getOrElse("en"))
        locale = java.util.Locale.forLanguageTag(localeStr)
        result <- service.explainConcept(uri, locale)
                    .handleError { e =>
                      println(s"[HttpRagServer] error in /sie/explain: ${e.toString}")
                      ConceptExplanation(
                        uri         = uri,
                        label       = Some("Error"),
                        description = Some(e.toString),
                        graph       = GraphResult(Nil, Nil)
                      )
                    }
        resp <- Ok(result)
      } yield resp
  }

  private def websocketRoutes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] =
    new McpWebSocketServer(service).routes(wsb)

  def start: IO[Nothing] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(port).getOrElse(port"8080"))
      .withHttpWebSocketApp { wsb =>
        (httpRoutes <+> websocketRoutes(wsb)).orNotFound
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
