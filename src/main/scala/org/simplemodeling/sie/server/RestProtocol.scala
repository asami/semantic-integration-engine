package org.simplemodeling.sie.server

import io.circe.Json
import io.circe.syntax.*
import org.http4s.{Method, Status, Uri}
import org.simplemodeling.sie.interaction.*

/*
 * @since   Dec. 20, 2025
 * @version Dec. 20, 2025
 * @author  ASAMI, Tomoharu
 */
final case class RestInput(
  method: Method,
  path: Uri.Path,
  body: Option[Json]
)

final case class RestResponse(
  status: Status,
  body: Json
)

final class RestIngress extends ProtocolIngress[RestInput] {

  override def decode(input: RestInput): Either[ProtocolError, OperationRequest] =
    val path = input.path.renderString
    (input.method, path) match
      case (Method.POST, "/api/query") =>
        val query =
          input.body
            .flatMap(_.hcursor.get[String]("query").toOption)
            .getOrElse("")

        if query.isEmpty then
          Left(SimpleProtocolError(ProtocolErrorCode.InvalidParams, "missing query"))
        else
          val limit = input.body.flatMap(_.hcursor.get[Int]("limit").toOption)
          Right(
            OperationRequest(
              requestId = None,
              payload = OperationPayload.Call(Query(query = query, limit = limit))
            )
          )

      case (Method.POST, "/api/explain") =>
        val name =
          input.body
            .flatMap { json =>
              val cursor = json.hcursor
              cursor.get[String]("name").toOption
                .orElse(cursor.get[String]("uri").toOption)
                .orElse(cursor.get[String]("id").toOption)
            }
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

final class RestAdapter extends ProtocolEgress[RestResponse] {

  override def encode(result: OperationResult): RestResponse =
    result.payload match
      case OperationPayloadResult.Executed(opResult) =>
        RestResponse(Status.Ok, encodeOperationResult(opResult))

      case OperationPayloadResult.Failed(error) =>
        RestResponse(
          Status.BadRequest,
          Json.obj("error" -> Json.fromString(error.message))
        )

      case _ =>
        RestResponse(
          Status.BadRequest,
          Json.obj("error" -> Json.fromString("unsupported request"))
        )

  private def encodeOperationResult(result: SieOperationResult): Json =
    result match
      case QueryResult(concepts, passages, graph) =>
        Json.obj(
          "concepts" -> concepts.asJson,
          "passages" -> passages.asJson,
          "graph" -> Json.fromString(graph)
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
