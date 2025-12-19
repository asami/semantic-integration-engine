package org.simplemodeling.sie.interaction

import org.simplemodeling.sie.service.RagService

/**
 * SieService represents the Component Interaction Contract of SIE.
 *
 * All external protocols (MCP / REST / CLI) must interact with SIE
 * exclusively through this service.
 *
 * This interaction layer is designed to be replaceable by CNCF
 * in the future.
 */
/*
 * @since   Dec. 20, 2025
 * @version Dec. 20, 2025
 * @author  ASAMI, Tomoharu
 */
final class SieService(
  ragService: RagService
) {

  def execute(op: SieOperation): SieOperationResult =
    op match {
      case q: Query =>
        query(q)

      case e: ExplainConcept =>
        explainConcept(e)
    }

  /**
   * Type-safe query API for direct callers.
   *
   * This method is intended for in-process usage where the
   * concrete result type (QueryResult) is required.
   */
  def query(op: Query): QueryResult =
    new QueryOperationCall(ragService).execute(op)

  /**
   * Type-safe explainConcept API for direct callers.
   *
   * This method provides a stable, explicit return type
   * while sharing the same execution model as execute(op).
   */
  def explainConcept(op: ExplainConcept): ExplainConceptResult =
    new ExplainConceptOperationCall(ragService).execute(op)
}
