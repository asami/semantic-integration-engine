package org.simplemodeling.sie.status

import io.circe.Encoder
import io.circe.generic.semiauto.*

/*
 * ============================================================
 * Status
 * Semantic readiness contract of SIE
 * ============================================================
 *
 * NOTE:
 *   If you modify this Status structure,
 *   you MUST review and update:
 *     - src/main/resources/status/schema-v1.json
 *
 *   Status is the canonical semantic contract.
 *   The JSON Schema is a derived artifact.
 *
 * @since   Dec. 14, 2025
 * @version Dec. 14, 2025
 * @author  ASAMI, Tomoharu
 */
final case class Status(
  graphDb: Status.GraphDb,
  vectorDb: Status.VectorDb,
  agent: Status.Agent,
  overall: Status.Overall
)

object Status {

  // ------------------------------------------------------------
  // Schema (Semantic contract reference)
  // ------------------------------------------------------------

  /** Canonical JSON Schema resource for /status v1 */
  val SchemaResourcePath: String =
    "/status/schema-v1.json"

  /** Schema version this Status corresponds to */
  val SchemaVersion: String =
    "v1"

  // ------------------------------------------------------------
  // GraphDB (Authoritative)
  // ------------------------------------------------------------

  final case class GraphDb(
    role: GraphDbRole = GraphDbRole.Authoritative,
    reachable: Boolean,
    dataset: Option[String],
    tripleCount: Option[Long],
    ready: Boolean
  )

  enum GraphDbRole {
    case Authoritative
  }

  // ------------------------------------------------------------
  // VectorDB (Derived)
  // ------------------------------------------------------------

  final case class VectorDb(
    role: VectorDbRole = VectorDbRole.Derived,
    endpoint: Option[String],
    collections: VectorCollections
  )

  enum VectorDbRole {
    case Derived
  }

  final case class VectorCollections(
    document: VectorCollection,
    concept: VectorCollection
  )

  final case class VectorCollection(
    name: String,
    origin: VectorOrigin,
    count: Long,
    ready: Boolean,
    rebuildPolicy: RebuildPolicy
  )

  enum VectorOrigin {
    case Document
    case Concept
  }

  enum RebuildPolicy {
    case Auto
    case Manual
    case Force
  }

  // ------------------------------------------------------------
  // Agent
  // ------------------------------------------------------------

  final case class Agent(
    mode: AgentMode,
    capabilities: Set[AgentCapability],
    ready: Boolean
  )

  enum AgentMode {
    case Off
    case McpOnly
    case Hybrid
  }

  enum AgentCapability {
    case Http
    case Mcp
    case WebSocket
  }

  // ------------------------------------------------------------
  // Overall (Aggregated semantic status)
  // ------------------------------------------------------------

  /**
   * Overall aggregated semantic status of SIE.
   *
   * Healthy:
   *   All required subsystems are ready and consistent.
   *
   * Degraded:
   *   System is operational but with limited capabilities
   *   (e.g. missing derived indices).
   *
   * Unavailable:
   *   System cannot serve semantic queries.
   *
   * NOTE:
   *   This enum-based model intentionally replaces the previous
   *   (ready, degraded) boolean combination to avoid ambiguous states.
   */
  enum OverallState {
    case Healthy
    case Degraded
    case Unavailable
  }

  final case class Overall(
    state: OverallState,
    reason: List[String]
  )

  private def encodeEnumLower[A](extract: A => String): Encoder[A] =
    Encoder.encodeString.contramap(a => extract(a).toLowerCase)

  given Encoder[GraphDbRole] = encodeEnumLower(_.toString)
  given Encoder[VectorDbRole] = encodeEnumLower(_.toString)
  given Encoder[VectorOrigin] = encodeEnumLower(_.toString)
  given Encoder[RebuildPolicy] = encodeEnumLower(_.toString)
  given Encoder[AgentMode] = encodeEnumLower(_.toString)
  given Encoder[AgentCapability] = encodeEnumLower(_.toString)
  given Encoder[OverallState] = encodeEnumLower(_.toString)

  given Encoder[GraphDb] = deriveEncoder
  given Encoder[VectorCollection] = deriveEncoder
  given Encoder[VectorCollections] = deriveEncoder
  given Encoder[VectorDb] = deriveEncoder
  given Encoder[Agent] = deriveEncoder
  given Encoder[Overall] = deriveEncoder
  given Encoder[Status] = deriveEncoder
}
