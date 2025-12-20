package org.simplemodeling.sie.interaction

/*
 * @since   Dec. 20, 2025
 * @version Dec. 20, 2025
 * @author  ASAMI, Tomoharu
 */
trait ProtocolIngress[I] {
  def decode(input: I): Either[ProtocolError, OperationRequest]
}

trait ProtocolEgress[O] {
  def encode(result: OperationResult): O
}

sealed trait ProtocolError {
  def code: ProtocolErrorCode
  def message: String
}

final case class SimpleProtocolError(
  code: ProtocolErrorCode,
  message: String
) extends ProtocolError

sealed trait ProtocolErrorCode {
  def value: String
}

object ProtocolErrorCode {
  case object InvalidRequest extends ProtocolErrorCode { val value = "invalid_request" }
  case object MethodNotFound extends ProtocolErrorCode { val value = "method_not_found" }
  case object InvalidParams extends ProtocolErrorCode { val value = "invalid_params" }
  case object InternalError extends ProtocolErrorCode { val value = "internal_error" }
}

final case class OperationRequest(
  requestId: Option[String],
  payload: OperationPayload
)

final case class ProtocolRequest[A](
  input: A
)

sealed trait OperationPayload

object OperationPayload {
  case object Initialize extends OperationPayload
  case object ToolsList extends OperationPayload
  final case class Call(operation: SieOperation) extends OperationPayload
}

final case class OperationResult(
  requestId: Option[String],
  payload: OperationPayloadResult
)

sealed trait OperationPayloadResult

object OperationPayloadResult {
  final case class Initialized(
    serverName: String,
    serverVersion: String,
    capabilities: Map[String, Any]
  ) extends OperationPayloadResult

  final case class Tools(
    tools: List[OperationTool]
  ) extends OperationPayloadResult

  final case class Executed(
    result: SieOperationResult
  ) extends OperationPayloadResult

  final case class Failed(
    error: ProtocolError
  ) extends OperationPayloadResult
}

final case class OperationTool(
  name: String,
  description: String,
  required: List[String] = Nil
)
