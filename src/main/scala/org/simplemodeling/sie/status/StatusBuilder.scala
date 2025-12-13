package org.simplemodeling.sie.status

/*
 * ============================================================
 * StatusBuilder
 * Builds semantic status from observed subsystem states
 * ============================================================
 * 
 * @since   Dec. 14, 2025
 * @version Dec. 14, 2025
 * @author  ASAMI, Tomoharu
 */
final class StatusBuilder {

  def build(
    graphDb: Status.GraphDb,
    vectorDb: Status.VectorDb,
    agent: Status.Agent
  ): Status = {

    val overall = buildOverall(graphDb, vectorDb, agent)

    Status(
      graphDb = graphDb,
      vectorDb = vectorDb,
      agent = agent,
      overall = overall
    )
  }

  // ------------------------------------------------------------
  // Overall status judgment
  // ------------------------------------------------------------

  private def buildOverall(
    graphDb: Status.GraphDb,
    vectorDb: Status.VectorDb,
    agent: Status.Agent
  ): Status.Overall = {

    val reasons = List.newBuilder[String]

    val graphReady   = graphDb.ready
    val documentReady = vectorDb.collections.document.ready
    val agentReady   = agent.ready

    if (!graphReady) {
      reasons += "GraphDB is not ready"
    }

    if (!documentReady) {
      reasons += "Document vector collection is not ready"
    }

    if (!agentReady) {
      reasons += "Agent is not ready"
    }

    val state =
      if (graphReady && documentReady && agentReady)
        Status.OverallState.Healthy
      else if (graphReady)
        Status.OverallState.Degraded
      else
        Status.OverallState.Unavailable

    Status.Overall(
      state = state,
      reason = reasons.result()
    )
  }
}
