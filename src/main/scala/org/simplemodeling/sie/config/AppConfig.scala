package org.simplemodeling.sie.config

import pureconfig.*
import pureconfig.error.*
import pureconfig.generic.derivation.default.*
import com.typesafe.config.ConfigFactory

/*
 * @since   Nov. 20, 2025
 *  version Nov. 25, 2025
 * @version Dec. 13, 2025
 * @author  ASAMI, Tomoharu
 */
enum ServerMode:
  case Dev, Demo, Staging, Prod

given ConfigReader[ServerMode] =
  ConfigReader[String].map {
    case s if s.equalsIgnoreCase("dev")     => ServerMode.Dev
    case s if s.equalsIgnoreCase("demo")    => ServerMode.Demo
    case s if s.equalsIgnoreCase("staging") => ServerMode.Staging
    case s if s.equalsIgnoreCase("prod")    => ServerMode.Prod
    case other => throw new Exception(s"Invalid server mode: $other")
  }

enum AgentMode:
  case Off
  case Hybrid
  case McpOnly

given ConfigReader[AgentMode] =
  ConfigReader[String].map {
    case s if s.equalsIgnoreCase("off")       => AgentMode.Off
    case s if s.equalsIgnoreCase("hybrid")    => AgentMode.Hybrid
    case s if s.equalsIgnoreCase("mcp-only")  => AgentMode.McpOnly
    case other =>
      throw new Exception(s"Invalid agent mode: $other")
  }

case class FusekiConfig(endpoint: String) derives ConfigReader
case class ChromaConfig(endpoint: String) derives ConfigReader

enum OperationalControl:
  case Managed
  case Unmanaged

enum VectorDbMode:
  case Stable
  case Auto
  case Force

case class VectorDbLifecycleConfig(
  mode: VectorDbMode = VectorDbMode.Auto
) derives ConfigReader

case class KnowledgeStoreConfig(
  operationalControl: OperationalControl = OperationalControl.Unmanaged
) derives ConfigReader

case class KnowledgeStoresConfig(
  graphdb: KnowledgeStoreConfig = KnowledgeStoreConfig(),
  vectordb: KnowledgeStoreConfig = KnowledgeStoreConfig(),
  vectordbLifecycle: VectorDbLifecycleConfig = VectorDbLifecycleConfig()
) derives ConfigReader

case class ServerConfig(host: String, siePort: Int, mode: ServerMode) derives ConfigReader

case class AgentConfig(
  mode: AgentMode
) derives ConfigReader

case class AppConfig(
  fuseki: FusekiConfig,
  chroma: ChromaConfig,
  server: ServerConfig,
  agent: Option[AgentConfig],
  // Operational control for external knowledge stores (GraphDB / VectorDB).
  knowledge: Option[KnowledgeStoresConfig]
) derives ConfigReader

object AppConfig:

  def load(): AppConfig =
    val raw0 =
      ConfigSource
        .fromConfig(ConfigFactory.load())
        .loadOrThrow[AppConfig]

    val raw =
      raw0.copy(
        agent = raw0.agent.orElse(Some(AgentConfig(AgentMode.Off))),
        knowledge = raw0.knowledge.orElse(Some(KnowledgeStoresConfig()))
          .map(ksc => ksc.copy(vectordbLifecycle = VectorDbLifecycleConfig()))
      )

    // ------------------------------------------------------------
    // DEV mode specification
    // ------------------------------------------------------------
    // ServerMode.Dev semantics:
    //
    // - Execution model:
    //     * SIE runs on the local JVM via `sbt dev` (outside Docker).
    //     * External dependencies are provided by docker-compose.dev.yml.
    //
    // - Endpoint defaults (must match docker-compose.dev.yml):
    //     * GraphDB (Fuseki):  http://localhost:9030/ds
    //     * VectorDB (Chroma / sie-embedding): http://localhost:8081
    //
    // - Knowledge store semantics:
    //     * GraphDB is authoritative and REQUIRED in DEV.
    //       If unreachable, startup is considered degraded/error.
    //     * VectorDB is derived and OPTIONAL at startup.
    //       Empty or missing data is tolerated and may be lazily initialized.
    //
    // - VectorDB lifecycle semantics:
    //     * stable: never initialize
    //     * auto: initialize only if empty
    //     * force: always rebuild
    //
    // - Agent defaults:
    //     * If agent.mode is Off, it is promoted to Hybrid in DEV
    //       (HTTP + MCP enabled by default).
    //
    // - Responsibility boundaries:
    //     * build.sbt selects the config file (e.g. application.dev.conf).
    //     * AppConfig applies semantic defaults based solely on server.mode.
    //     * AppConfig MUST NOT select or merge config files by itself.
    // ------------------------------------------------------------
    // 2. Apply mode-specific defaults
    // ------------------------------------------------------------
    raw.server.mode match
      case ServerMode.Dev =>
        raw.copy(
          fuseki =
            raw.fuseki.copy(
              endpoint =
                sys.env.get("SIE_GRAPHDB_ENDPOINT").getOrElse {
                  raw.fuseki.endpoint match
                    case "" => "http://localhost:9030/ds"
                    case v  => v
                }
            ),

          chroma =
            raw.chroma.copy(
              endpoint =
                sys.env.getOrElse(
                  "SIE_VECTORDB_ENDPOINT",
                  raw.chroma.endpoint match
                    case "" => "http://localhost:8081"
                    case v  => v
                )
            ),

          agent = Some(
            raw.agent.get.copy(
              mode =
                raw.agent.get.mode match
                  case AgentMode.Off =>
                    // DEV default: Agent enabled (HTTP + MCP)
                    AgentMode.Hybrid
                  case v => v
            )
          ),

          knowledge = Some(
            raw.knowledge.get.copy(
              graphdb =
                raw.knowledge.get.graphdb.copy(
                  operationalControl =
                    raw.knowledge.get.graphdb.operationalControl match
                      case OperationalControl.Unmanaged =>
                        OperationalControl.Managed
                      case v => v
                ),
              vectordb =
                raw.knowledge.get.vectordb.copy(
                  operationalControl =
                    raw.knowledge.get.vectordb.operationalControl match
                      case OperationalControl.Unmanaged =>
                        OperationalControl.Managed
                      case v => v
                ),
              vectordbLifecycle = VectorDbLifecycleConfig()
            )
          )
        )

      case ServerMode.Demo =>
        raw.copy(
          agent = Some(
            raw.agent.get.copy(
              mode =
                raw.agent.get.mode match
                  case AgentMode.Off =>
                    // DEMO default: Agent enabled (HTTP + MCP)
                    AgentMode.Hybrid
                  case v => v
            )
          )
        )

      case ServerMode.Prod =>
        raw.copy(
          agent = Some(
            raw.agent.get.copy(
              mode =
                raw.agent.get.mode match
                  case AgentMode.Off =>
                    // PROD default: MCP-only agent
                    AgentMode.McpOnly
                  case v => v
            )
          )
        )

      case _ =>
        raw
