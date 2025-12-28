package org.simplemodeling.sie.protocol

import org.goldenport.Consequence
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.operation.OperationRequest

/*
 * @since   Dec. 26, 2025
 * @version Dec. 26, 2025
 * @author  ASAMI, Tomoharu
 */
val services = ServiceDefinitionGroup(
  Vector(
    SieService.definition
  )
)

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
    given org.goldenport.protocol.Request = req
    take_string("query").map(Query(_))
  }
}
