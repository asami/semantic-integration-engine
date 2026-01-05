package org.simplemodeling.sie.action.query

import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.{Response as ProtocolResponse}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, Query, ResourceAccess}
import org.simplemodeling.sie.context.SieContext
import io.circe.syntax.*

final case class QueryAction(
  query: String,
  limit: Int
) extends Query("query") {

  override def createCall(core: ActionCall.Core): ActionCall =
    QueryActionCall(query, limit, core)
}

object QueryAction {
  def parse(req: Request): Consequence[QueryAction] =
    _parse_query(req).zipWith(_parse_limit(req))(QueryAction.apply)

  private def _parse_query(req: Request): Consequence[String] =
    _first_argument(req).orElse(_property_value(req, "query")) match {
      case Some(value) if value.nonEmpty =>
        Consequence.success(value)
      case _ =>
        Consequence.failure("query parameter is required")
    }

  private def _parse_limit(req: Request): Consequence[Int] =
    _second_argument(req).orElse(_property_value(req, "limit")) match {
      case Some(value) =>
        _parse_int(value, "limit")
      case None =>
        Consequence.success(10)
    }

  private def _first_argument(req: Request): Option[String] =
    req.arguments.headOption.map(_argument_value)

  private def _second_argument(req: Request): Option[String] =
    req.arguments.drop(1).headOption.map(_argument_value)

  private def _argument_value(arg: Argument): String =
    Option(arg.value).map(_.toString).getOrElse("")

  private def _property_value(req: Request, name: String): Option[String] =
    req.properties.find(_.name == name).map(_property_value)

  private def _property_value(prop: Property): String =
    Option(prop.value).map(_.toString).getOrElse("")

  private def _parse_int(value: String, name: String): Consequence[Int] =
    try {
      Consequence.success(value.toInt)
    } catch {
      case _: NumberFormatException =>
        Consequence.failure(s"invalid $name: $value")
    }
}

final case class QueryActionCall(
  query: String,
  limit: Int,
  actioncore: ActionCall.Core
) extends ActionCall {

  override def core: ActionCall.Core = actioncore

  override def action: Action = actioncore.action

  override def accesses: Seq[ResourceAccess] = Nil

  override def execute(): Consequence[OperationResponse] =
    Consequence {
      val sie = application_context[SieContext]
      val ragResult = sie.ragService.run(query)
      val limited =
        ragResult.copy(
          concepts = ragResult.concepts.take(limit),
          passages = ragResult.passages.take(limit)
        )
      new OperationResponse {
        def toResponse: ProtocolResponse =
          ProtocolResponse.Json(limited.asJson.noSpaces)
      }
    }
}
