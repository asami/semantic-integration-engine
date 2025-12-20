package org.simplemodeling.sie.interaction

import org.simplemodeling.sie.service.RagService
import io.circe.syntax.*

/*
 * @since   Dec. 20, 2025
 * @version Dec. 20, 2025
 * @author  ASAMI, Tomoharu
 */
trait SieOperationCall[
  Op <: SieOperation,
  Res <: SieOperationResult
] {

  def execute(op: Op): Res
}

/**
 * OperationCall implementation for Query operation.
 *
 * This class represents the executable form of a Query intent.
 * It is responsible for invoking the underlying RagService.
 *
 * NOTE:
 * - RagService is injected from outside.
 * - No protocol-specific logic should be added here.
 * - This class is designed to be replaceable by CNCF OperationCall.
 */
final class QueryOperationCall(
  ragService: RagService
) extends SieOperationCall[Query, QueryResult] {

  override def execute(op: Query): QueryResult = {
    val rag = ragService.run(op.query)

    QueryResult(
      concepts = rag.concepts.map(_.label),
      passages = rag.passages.map(_.text),
      graph    = rag.graph.asJson.noSpaces
    )
  }
}

/**
 * OperationCall implementation for ExplainConcept operation.
 *
 * This class represents the executable form of an ExplainConcept intent.
 * It is responsible for invoking the underlying RagService.
 *
 * NOTE:
 * - RagService is injected from outside.
 * - No protocol-specific logic should be added here.
 * - This class is designed to be replaceable by CNCF OperationCall.
 */
final class ExplainConceptOperationCall(
  ragService: RagService
) extends SieOperationCall[ExplainConcept, ExplainConceptResult] {

  override def execute(op: ExplainConcept): ExplainConceptResult = {
    // TODO:
    // Delegate to RagService explainConcept APIs and compose the result.
    //
    // Example (to be wired later):
    // val description = ragService.explainConcept(op.name)
    //
    // ExplainConceptResult(description)

    ExplainConceptResult()
  }
}
