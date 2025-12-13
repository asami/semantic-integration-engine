package org.simplemodeling.sie.server

import cats.effect.*
import cats.syntax.all.*
import org.simplemodeling.sie.config.*
import org.simplemodeling.sie.fuseki.*
import org.simplemodeling.sie.chroma.ChromaClient
import org.simplemodeling.sie.vectordb.*
import org.simplemodeling.sie.service.*
import org.simplemodeling.sie.indexer.HtmlIndexer
import org.simplemodeling.sie.embedding.*
import org.simplemodeling.sie.init.IndexInitializer
import org.simplemodeling.sie.concept.*
import org.simplemodeling.sie.util.RetryUtil
import scala.concurrent.duration.*

import org.simplemodeling.sie.status.*

/**
 * RagServerMain
 *
 * Responsibilities:
 * - Load application configuration
 * - Initialize Fuseki / Embedding / Chroma clients
 * - Ensure Chroma collection exists (non-fatal, degraded mode on failure)
 * - Run IndexInitializer once (forced or first-time, non-fatal)
 * - Start background recovery loop (for future self-healing)
 * - Start HTTP RAG server
 *
 * Design notes:
 * - All external calls (Chroma, Fuseki, IndexInitializer) are treated as
 *   non-fatal for process startup. Failures are logged and the server
 *   continues in a degraded mode where possible.
 *
 * @since   Nov. 20, 2025
 *          Nov. 25, 2025
 * @version Dec. 14, 2025
 */
/*
 * ============================================================
 * SIE Runtime Architecture – Design Axes (Authoritative Spec)
 * ============================================================
 *
 * This comment is the SINGLE SOURCE OF TRUTH for how SIE runtime
 * modes are structured. It is intentionally placed in code so
 * that both humans and AI-based tools (including automated
 * refactoring agents) can rely on it during future changes.
 *
 * The following axes are ORTHOGONAL and MUST NOT be collapsed
 * into a single enum or configuration dimension.
 *
 * ------------------------------------------------------------
 * (A) Architecture (Abstract Structure)
 * ------------------------------------------------------------
 * Describes the ABSTRACT architectural relationship between
 * Agent and SIE, independent of runtime placement, JVM,
 * Docker, or deployment tooling.
 *
 * This axis captures conceptual system structure only.
 *
 * Typical categories:
 *
 *   Architecture:
 *     - Embedded
 *         Agent and SIE form a single conceptual unit.
 *
 *     - ClientServer
 *         Agent acts as a client of SIE.
 *
 *     - Gateway
 *         Agent provides a thin mediation layer in front of SIE.
 *
 *     - Federated
 *         Multiple Agents and/or SIE instances cooperate.
 *
 * NOTE:
 *   Architecture MUST NOT encode where components run.
 *
 * ------------------------------------------------------------
 * (B) Runtime Features (What this JAR starts)
 * ------------------------------------------------------------
 * Describes WHICH logical components are started by THIS JAR.
 *
 *   RuntimeFeatures:
 *     - runAgent : Boolean
 *     - runSIE   : Boolean
 *
 * Possible combinations:
 *   (true,  true)  -> Agent + SIE embedded
 *   (true,  false) -> Agent only
 *   (false, true)  -> SIE only
 *   (false, false) -> relay / proxy / external-only mode
 *
 * This axis controls process behavior only.
 *
 * ------------------------------------------------------------
 * (C) Runtime Topology (Deployment / Placement)
 * ------------------------------------------------------------
 * Describes WHERE Agent and SIE actually run at runtime.
 *
 * This axis includes process, host, and network boundaries.
 *
 *   AgentPlacement:
 *     - InProcess
 *     - SameHost
 *     - External
 *
 *   SiePlacement:
 *     - InProcess
 *     - SameHost
 *     - External
 *
 * NOTE:
 *   Docker, containers, and cloud environments belong here.
 *
 * ------------------------------------------------------------
 * (D) Operational Control (Lifecycle Responsibility)
 * ------------------------------------------------------------
 * Describes whether THIS runtime instance is contractually allowed
 * to take lifecycle responsibility for a component.
 *
 * IMPORTANT:
 *   OperationalControl expresses a RESPONSIBILITY CONTRACT,
 *   not a runtime state.
 *
 *   - Managed   : This runtime MAY initialize, rebuild, or recover
 *                 the component, subject to semantic preconditions.
 *   - Unmanaged : This runtime MUST NOT modify the component lifecycle;
 *                 state is observed and reported only.
 *
 * RELATION TO STATUS:
 *   - OperationalControl is a configuration-time concept.
 *   - Actual availability, readiness, and degradation are reported
 *     exclusively via the Status model (/status endpoint).
 *   - Status values MUST NOT be interpreted as authority to perform
 *     lifecycle operations.
 *
 * NOTE:
 *   A future version may expose `managedBy` (responsibility owner)
 *   as part of a public configuration contract. For now, this axis
 *   remains internal and declarative.
 *
 * ------------------------------------------------------------
 * (E) Knowledge Stores Semantics (Authoritative + Constraints)
 * ------------------------------------------------------------
 * Defines architectural, semantic, and cross-cutting operational
 * rules for core knowledge stores.
 *
 * KnowledgeStoreType:
 *   - GraphDB   // authoritative semantic source
 *   - VectorDB  // derived, reproducible index
 *
 * ------------------------------------------------------------
 * Semantic Roles
 * ------------------------------------------------------------
 * - GraphDB is the PRIMARY and AUTHORITATIVE source of truth.
 * - VectorDB is ALWAYS DERIVED from GraphDB.
 *
 * VectorDB MUST NOT be treated as an independent knowledge source.
 *
 * ------------------------------------------------------------
 * Initialization Responsibility
 * ------------------------------------------------------------
 * - GraphDB initialization is OUT OF SCOPE for SIE by default.
 *   It is assumed to be prepared externally unless explicitly
 *   configured otherwise.
 *
 * - VectorDB initialization MAY be performed by SIE, but ONLY as a
 *   derived artifact from a valid GraphDB.
 *
 * ------------------------------------------------------------
 * Rebuild Preconditions (STRICT)
 * ------------------------------------------------------------
 * VectorDB rebuild is permitted ONLY IF ALL of the following hold:
 *
 *   - GraphDB is reachable
 *   - GraphDB dataset is non-empty
 *   - GraphDB is in a semantically complete and stable state
 *
 * VectorDB rebuild MUST NOT occur:
 *   - if GraphDB is unreachable
 *   - if GraphDB is empty
 *   - during partial, transient, or bootstrap GraphDB states
 *
 * ------------------------------------------------------------
 * Purpose-sensitive Behavior
 * ------------------------------------------------------------
 * - DEV:
 *     * VectorDB rebuild MAY be triggered lazily
 *       as a last-resort recovery mechanism.
 *
 * - DEMO / STAGING:
 *     * VectorDB is expected to be prebuilt.
 *     * Rebuild is allowed only when explicitly enabled.
 *
 * - PROD:
 *     * VectorDB lifecycle is expected to be controlled
 *       externally by operations.
 *
 * ------------------------------------------------------------
 * Failure Semantics
 * ------------------------------------------------------------
 * - Missing or invalid GraphDB is a HARD ERROR unless explicitly
 *   handled by external operational control.
 *
 * - Missing VectorDB data MAY be tolerated if rebuild conditions
 *   are satisfied.
 *
 * ------------------------------------------------------------
 * (F) Purpose (System Intent)
 * ------------------------------------------------------------
 * Describes WHY this configuration exists.
 *
 *   Purpose:
 *     - Dev
 *     - Demo
 *     - Staging
 *     - Prod
 *
 * Purpose may influence defaults and safety checks, but MUST
 * NOT change architectural meaning.
 *
 * ------------------------------------------------------------
 * Design Principles
 * ------------------------------------------------------------
 * - Architecture defines conceptual structure.
 * - Topology defines runtime placement.
 * - RuntimeFeatures define process behavior.
 * - OperationalControl defines responsibility boundaries.
 *
 * These axes must remain independent.
 *
 * ============================================================
 */
object RagServerMain extends IOApp.Simple:

  override def run: IO[Unit] =
    val cfg    = AppConfig.load()
    val mode   = cfg.server.mode

    // AppConfig.load() guarantees these are defined
    val agentCfg     = cfg.agent.get
    val knowledgeCfg = cfg.knowledge.get

    val isDev     = mode == ServerMode.Dev
    val isDemo    = mode == ServerMode.Demo
    val isStaging = mode == ServerMode.Staging
    val isProd    = mode == ServerMode.Prod

    val graphDbControl  = knowledgeCfg.graphdb.operationalControl
    val vectorDbControl = knowledgeCfg.vectordb.operationalControl

    // VectorDB lifecycle mode (separate from OperationalControl).
    // - Stable: never auto-initialize or rebuild VectorDB at runtime
    // - Auto  : initialize only when VectorDB is in the initial state
    // - Force : always rebuild at startup (explicitly destructive / expensive)
    //
    // NOTE: This mode is introduced to replace the old `embedding.enabled` gate.
    // HtmlIndexer MUST NOT skip work just because an embedding engine is disabled.
    // The decision is made here by VectorDB mode.
    val vectorDbMode: VectorDbMode = knowledgeCfg.vectordbLifecycle.mode

    val fusekiClient = FusekiClient(cfg.fuseki.endpoint)

    val graphDbCheck: IO[Unit] =
      if graphDbControl == OperationalControl.Unmanaged then
        IO.println(
          "[RagServerMain][E:GraphDB.Authoritative] GraphDB is Unmanaged: enforcing reachability and NON-EMPTY dataset (authoritative source required)."
        ) *>
          fusekiClient.assertReachableAndNonEmpty()
      else
        IO.println(
          "[RagServerMain][E:GraphDB.Authoritative] GraphDB is Managed: lifecycle controlled externally; SIE will NOT initialize GraphDB."
        )

    val modeLog: IO[Unit] =
      if isDev then
        IO.println("[RagServerMain] DEV MODE: Fuseki is prepared externally; Chroma will be reused or built from Fuseki if missing.")
      else if isDemo then
        IO.println("[RagServerMain] DEMO MODE: Using preloaded Fuseki; skipping heavy indexing.")
      else if isStaging then
        IO.println("[RagServerMain] STAGING MODE: Full initialization with debug logging.")
      else if isProd then
        IO.println("[RagServerMain] PROD MODE: Full initialization.")
      else IO.unit

    val agentModeLog: IO[Unit] =
      IO.println(
        s"[RagServerMain][Agent] mode=${agentCfg.mode.toString.toLowerCase} (exposed via /status; behavior unchanged in Step A)"
      )

    for
      _ <- IO.println(
        s"""[RagServerMain] Startup Configuration
           |  mode                       = ${mode}
           |  graphdb.operationalControl = ${graphDbControl}
           |  vectordb.operationalControl= ${vectorDbControl}
           |  vectordbLifecycle.mode     = ${vectorDbMode}
           |""".stripMargin
      )
      _ <- modeLog
      _ <- agentModeLog
      _ <- graphDbCheck

      // -------------------------------------------------------
      // 1. Initialize embedding engine
      // -------------------------------------------------------
      embedding <- EmbeddingEngineFactory.create()

      // -------------------------------------------------------
      // VectorDB / Chroma client initialization
      // -------------------------------------------------------
      // DEV-only override: SIE runs outside Docker, so force localhost endpoint and set legacy compatibility props
      _ <- IO.whenA(isDev) {
        IO {
          // DEV runs SIE on the host JVM; dependencies are exposed on localhost.
          sys.props.update("SIE_VECTORDB_ENDPOINT", "http://localhost:8081")

          // Backward-compatible toggles for older code paths.
          // HtmlIndexer should no longer depend on these, but setting them here
          // avoids silent no-ops if any legacy guard remains.
          sys.props.update("SIE_CHROMA_ENABLED", "true")
          sys.props.update("SIE_VECTORDB_MODE", vectorDbMode.toString) // legacy compatibility
        }
      }

      vectordb = VectorDbClientFactory.fromEnv(embedding)

      chromaClient = ChromaClient.fromEnv(embedding)

      docCount <-
        if vectorDbControl == OperationalControl.Unmanaged then
          IO.println(
            "[RagServerMain][E:VectorDB.Derived] VectorDB is Unmanaged: skipping auto-create/rebuild; derived index assumed externally managed."
          ) *>
            vectordb.collectionExists(HtmlIndexer.CollectionName).attempt.void *>
            IO.pure(-1)
        else
          for
            _ <- IO.println(
              s"[RagServerMain][E:VectorDB.Derived] VectorDB is Managed: ensuring collection '${HtmlIndexer.CollectionName}' exists (derived from GraphDB; rebuild subject to strict preconditions)."
            )
            existsE <- vectordb.collectionExists(HtmlIndexer.CollectionName).attempt

            created <- existsE match
              case Right(false) =>
                IO.println("[RagServerMain] Creating collection (managed mode, non-fatal)...") *>
                  vectordb.createCollection(HtmlIndexer.CollectionName).attempt.void *>
                  IO.pure(true)
              case Right(true) =>
                IO.pure(false)
              case Left(err) =>
                IO.println(
                  s"[RagServerMain][E:VectorDB.Init] collectionExists failed: ${err.getMessage} (continuing without auto-index)"
                ) *>
                  IO.pure(false)

            docCountE <-
              existsE match
                case Right(true) =>
                  chromaClient.countDocuments(HtmlIndexer.CollectionName).attempt
                case _ =>
                  IO.pure(Right(0))

            docCountValue =
              docCountE match
                case Right(c) => c
                case Left(err) =>
                  println(
                    s"[RagServerMain][E:VectorDB.Init] countDocuments failed: ${err.getMessage} (treating as unknown; Auto init will not be triggered by emptiness)"
                  )
                  -1

            legacyForceIndex = sys.env
              .get("SIE_INDEX_ON_START")
              .exists(_.equalsIgnoreCase("true"))

            forceIndex = (vectorDbMode == VectorDbMode.Force) || legacyForceIndex

            autoInit =
              (vectorDbMode == VectorDbMode.Auto) &&
                (created || (docCountValue == 0))

            _ <-
              if vectorDbMode == VectorDbMode.Stable then
                IO.println(
                  "[RagServerMain][E:VectorDB.Mode] VectorDB mode=Stable: skipping runtime indexing/initialization."
                )
              else if forceIndex then
                IO.println(
                  "[RagServerMain][E:VectorDB.Rebuild] VectorDB mode=Force (or legacy SIE_INDEX_ON_START): rebuilding VectorDB FROM GraphDB (requires GraphDB reachable & non-empty)."
                ) *>
                  runIndexInitializer(fusekiClient, chromaClient, embedding) *>
                  startHtmlIndexer(chromaClient)
              else if autoInit then
                IO.println(
                  "[RagServerMain][E:VectorDB.Init] VectorDB mode=Auto and VectorDB is initial state (collection missing OR empty): running HtmlIndexer."
                ) *>
                  startHtmlIndexer(chromaClient)
              else
                IO.println(
                  s"[RagServerMain][E:VectorDB.Mode] VectorDB mode=${vectorDbMode}: collection exists (docCount=${docCountValue}); skipping HtmlIndexer."
                )

            _ <- BackgroundTasks.startRecoveryLoop(chromaClient, fusekiClient, embedding)
          yield docCountValue

      // -------------------------------------------------------
      // Build semantic status (/status contract)
      // -------------------------------------------------------
      statusBuilder = new StatusBuilder()

      graphDbStatus = Status.GraphDb(
        reachable = true, // reachability already asserted or logged
        dataset = Some("ds"),
        tripleCount = None,
        ready = graphDbControl == OperationalControl.Managed || true
      )

      vectorDbStatus = Status.VectorDb(
        endpoint = Some("http://localhost:8081"),
        collections = Status.VectorCollections(
          document = Status.VectorCollection(
            name = HtmlIndexer.CollectionName,
            origin = Status.VectorOrigin.Document,
            count = docCount.max(0),
            ready = docCount > 0,
            rebuildPolicy = vectorDbMode match
              case VectorDbMode.Auto  => Status.RebuildPolicy.Auto
              case VectorDbMode.Force => Status.RebuildPolicy.Force
              case VectorDbMode.Stable=> Status.RebuildPolicy.Manual
          ),
          concept = Status.VectorCollection(
            name = HtmlIndexer.CollectionName + "-concept",
            origin = Status.VectorOrigin.Concept,
            count = 0,
            ready = false,
            rebuildPolicy = Status.RebuildPolicy.Manual
          )
        )
      )

      agentStatus = Status.Agent(
        mode = agentCfg.mode match
          case AgentMode.Off      => Status.AgentMode.Off
          case AgentMode.McpOnly  => Status.AgentMode.McpOnly
          case AgentMode.Hybrid   => Status.AgentMode.Hybrid,
        capabilities = Set(Status.AgentCapability.Http, Status.AgentCapability.Mcp),
        ready = true
      )

      status = statusBuilder.build(graphDbStatus, vectorDbStatus, agentStatus)

      // -------------------------------------------------------
      // 5. Start HTTP RAG server
      // -------------------------------------------------------
      conceptDict <-
        {
          val loader =
            FusekiConceptLoader(
              new org.simplemodeling.sie.concept.FusekiSparqlClient(fusekiClient),
              mode
            )

          RetryUtil.retryIO(
            attempts = 15,
            delay = 1.second,
            label = "ConceptLoader initial load"
          ) {
            loader.load()
          }.map { seq =>
            ConceptDictionary(
              seq.map { case (uri, conceptLabel) =>
                uri -> ConceptEntry(
                  uri = uri,
                  labels = Set(conceptLabel),
                  localeLabels = Map(conceptLabel.locale -> Set(conceptLabel)),
                  preferredLabelMap =
                    if conceptLabel.preferred then
                      Map(conceptLabel.locale -> conceptLabel.text)
                    else
                      Map.empty
                )
              }.toMap
            )
          }.handleError { e =>
            println(
              "[RagServerMain] ConceptLoader failed after retries (continuing with empty dictionary): " +
                e.getMessage
            )
            ConceptDictionary(Map.empty)
          }
        }

      service = {
        val dictionary: Map[String, String] =
          conceptDict.entries.map { case (label, entry) => label -> entry.uri }

        val embedSingle: String => cats.effect.IO[Array[Float]] = (s: String) =>
          embedding.embed(List(s)).map(_.flatMap(_.headOption).getOrElse(Array.emptyFloatArray))

        val embedSearch: Array[Float] => cats.effect.IO[List[(String, Double)]] = (_: Array[Float]) =>
          IO.pure(Nil)

        val conceptMatcher =
          new ConceptMatcher(
            conceptDict = conceptDict,
            dictionary = dictionary,
            embed = embedSingle,
            embedSearch = embedSearch
          )

        RagService(
          RagService.Context.default,
          fusekiClient,
          chromaClient,
          embedding,
          conceptMatcher,
          agentCfg.mode
        )
      }

      _ <- HttpRagServer(
        service,
        cfg.server.host,
        cfg.server.siePort,
        mode,
        knowledgeCfg,
        status
      ).start

    yield ()

  // ------------------------------------------------------------
  // Helper: safe IndexInitializer wrapper
  // ------------------------------------------------------------
  /**
   * Run IndexInitializer once in a best-effort, non-fatal way.
   *
   * Any Throwable is caught and logged to STDOUT; the caller
   * should treat failures as degraded mode but must not abort
   * the process.
   */
  private def runIndexInitializer(
      fuseki: FusekiClient,
      chroma: ChromaClient,
      embedding: EmbeddingEngine
  ): IO[Unit] =
    IO {
      try
        IndexInitializer.run(fuseki, chroma, embedding)
      catch
        case e: Throwable =>
          println(
            "[RagServerMain] IndexInitializer failed (continuing): " + e.getMessage
          )
    }

  private def startHtmlIndexer(chroma: ChromaClient): IO[Unit] =
    HtmlIndexer
      .indexAll(chroma)
      .handleErrorWith { e =>
        IO.println("[RagServerMain] HtmlIndexer failed (continuing): " + e.getMessage)
      }
      .start
      .void
