package org.simplemodeling.sie.protocol

import org.goldenport.Consequence
import org.goldenport.protocol.ProtocolEngine
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.{
  CliHelpProjection,
  McpGetManifestProjection,
  OpenApiProjection,
  ProjectionCollection
}
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.operation.OperationRequest
import org.simplemodeling.sie.action.query.QueryAction

/*
 * @since   Dec. 26, 2025
 * @version Dec. 30, 2025
 * @author  ASAMI, Tomoharu
 */
val services = ServiceDefinitionGroup(
  Vector(
    SieService.definition
  )
)

private lazy val _handler =
  ProtocolHandler(
    ingresses = IngressCollection(Vector.empty),
    egresses = EgressCollection(Vector.empty),
    projections = ProjectionCollection(
      Vector(
        new CliHelpProjection,
        new OpenApiProjection,
        new McpGetManifestProjection
      )
    )
  )

private lazy val _protocol =
  org.goldenport.protocol.Protocol(
    services = services,
    handler = _handler
  )

def protocol: org.goldenport.protocol.Protocol =
  _protocol

def engine: ProtocolEngine =
  ProtocolEngine.create(_protocol)

/**
 * SIE service definition
 */
object SieService {
  val definition: ServiceDefinition =
    ServiceDefinition(
      name = "sie",
      operations = SieOperations.definitions
    )
}

/**
 * Operation definitions for SIE.
 */
object SieOperations {
  import org.goldenport.protocol.spec.OperationDefinitionGroup
  import cats.data.NonEmptyVector

  val definitions: OperationDefinitionGroup =
    OperationDefinitionGroup(
      NonEmptyVector.of(
        Query
      )
    )
}

case class Query(query: String) extends OperationRequest

object Query extends OperationDefinition {
  override val specification: OperationDefinition.Specification =
    OperationDefinition.Specification(
      name = "query",
      request = RequestDefinition(
        parameters = List(
          ParameterDefinition(
            name = "query",
            kind = ParameterDefinition.Kind.Property
          )
        )
      ),
      response = ResponseDefinition(result = Nil)
    )

  override def createOperationRequest(
    req: org.goldenport.protocol.Request
  ): Consequence[OperationRequest] = {
    req.operation match {
      case "query" =>
        QueryAction.parse(req)
      case other =>
        Consequence.failure(s"Unknown operation: $other")
    }
  }
}
