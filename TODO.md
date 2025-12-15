# TODO – Semantic Integration Engine (SIE)

This file tracks **intentional leftovers** and future work items.
All items here are deferred by design, not forgotten.

---

## 1. Agent / MCP Related

### 1.1 agent-mode Step B (Behavioral Branching)
- [ ] Disable MCP/WebSocket routes when `agent.mode = off`
- [ ] Support `agent.mode = mcp-only`
  - HTTP endpoints minimized or disabled
  - MCP/WebSocket becomes primary interface
- [ ] Ensure routing behavior matches `/status.agent.mode`

### 1.2 Agent Capability Exposure
- [ ] `/status.agent.capabilities`
  - supported protocols (http, mcp, websocket)
  - read-only vs write-capable
- [ ] Explicit agent readiness status (for ChatGPT / IDE agents)

---

## 2. Knowledge Stores (GraphDB / VectorDB)

### 2.1 VectorDB Rebuild Control
- [ ] Guard `admin/init-chroma` by `rebuildPolicy`
  - DEV: allowed
  - DEMO: gated
  - PROD: rejected
- [ ] Expose `rebuildAllowed: Boolean` in `/status`


### 2.2 Health Semantics Refinement
- [ ] Distinguish liveness vs readiness more clearly
  - `/health` : process liveness only
  - `/status` : semantic readiness (GraphDB + VectorDB)
- [ ] Add reason codes for degraded states

### 2.4 Operational Control vs Status (Design Note)
- [ ] Clarify and document the relationship between **operational control** and **status**
  - operational control expresses **responsibility / authority contracts** (configuration-time)
  - status expresses **observed runtime facts** (reachable, ready, counts, state)
- [ ] Record that `OperationalControl (Managed / Unmanaged)` is **not a status field**
  - it MUST NOT be interpreted as readiness or availability
- [ ] Future direction (not v1): introduce `managedBy` as a configuration-level concept
  - potential exposure via `/configuration`
  - optional mirrored reference fields in `/status` for explanatory purposes

### 2.3 Concept Vectorization (Fuseki → VectorDB)
- [ ] Define Concept → Text projection rules
  - rdfs:label / definition / description aggregation
  - language selection and fallback strategy
- [ ] Implement ConceptIndexer (Fuseki-derived)
  - independent from HtmlIndexer
  - rebuild policy aligned with VectorDB lifecycle
- [ ] Decide VectorDB layout for concept vectors
  - separate collection vs shared collection with `origin=concept` metadata
- [ ] Extend `/status` to be origin-aware
  - distinguish document-derived vs concept-derived vector states
  - expose counts and readiness per origin
- [ ] Ensure vector search semantics are consistent across origins
  - concept–concept similarity (e.g. つけ麺 → ラーメン)
  - document–concept bridging (future)

### 2.5 Concept Network / Concept Space

- [ ] Introduce **Session-local Concept Space**
  - aggregate concepts appearing within a single MCP / ChatGPT session
  - maintain weighted counts with **time-decay**
    - recent mentions have higher influence
    - older mentions gradually attenuate
  - intended to represent the *current conversational semantic focus*
- [ ] Define **time-decay model**
  - exponential decay (e.g. half-life based)
  - configurable window size (e.g. last N minutes / turns)
  - no hard persistence (session-scoped only)
- [ ] Add MCP tool: `tools.sie.describeContext`
  - returns the current session-local concept space
  - includes:
    - top-ranked concepts
    - optional lightweight concept networks per concept
  - designed for *explanatory / reflective* use by ChatGPT
- [ ] Clarify relationship to future context management
  - feeds into future SessionContext / RagContext
  - does NOT modify global knowledge stores
  - purely observational + derived

---

## 3. Runtime Topology / Architecture Modeling

### 3.1 Formalize Runtime Topology Axis
- [ ] Explicit enums or documentation for:
  - AgentPlacement (InProcess / SameHost / External)
  - SiePlacement (InProcess / SameHost / External)
- [ ] Clarify relationship between Topology and agent-mode

### 3.2 Architecture Axis (Abstract)
- [ ] Refine abstract Architecture categories:
  - Embedded / ClientServer / Gateway / Federated
- [ ] Ensure Architecture remains **non-runtime, non-deployment**

---

## 4. Configuration & Defaults

### 4.1 application.*.conf Coverage
- [ ] application.demo.conf
- [ ] application.prod.conf
- [ ] Explicit documentation of override precedence:
  - AppConfig defaults
  - application.*.conf
  - environment variables

### 4.2 Validation & Fail-Fast Rules
- [ ] Centralize config validation messages
- [ ] Improve error messages for invalid agent-mode / vectordb mode

---

## 5. Observability & Logs

### 5.1 Log Structure
- [ ] Structured logging (JSON-ready)
- [ ] Stable log tags:
  - `[Agent]`
  - `[GraphDB.Authoritative]`
  - `[VectorDB.Derived]`
- [ ] Correlate logs with `/status` fields

### 5.2 OpenTelemetry (Operational Observability)
- [ ] Introduce OpenTelemetry as the **operational observability backbone**
  - traces as primary signal (request / operation lifecycle)
  - metrics as secondary signal (counts, latency, error rates)
  - logs as correlated auxiliary data (trace_id / span_id)
- [ ] Define minimal span model aligned with Component Framework
  - span = component.operation
  - stable attributes: component.name, component.version, operation.name
- [ ] Ensure OpenTelemetry usage is **framework-internal**
  - generated components MUST NOT depend on OTel APIs directly
  - observability emerges via framework hooks
- [ ] Provide DEV-friendly setup (local collector, console exporter)
- [ ] Defer backend choice (Jaeger / Tempo / Prometheus) to deployment layer

### 5.3 Analytical Session Logs (Model Refinement)
- [ ] Introduce **analysis-oriented session logs** separate from operational logs
- [ ] Record semantic events rather than raw transport only
  - MCP semantic events (e.g. explainConcept requested/responded)
  - concept / document / graph usage summaries
- [ ] Capture lightweight reasoning traces
  - selected vs rejected knowledge sources
  - search strategy (graph / vector / hybrid)
- [ ] Persist logs per session (directory-based)
  - intended for post-hoc analysis and model refinement
  - NOT part of runtime health or observability
- [ ] Clearly document separation of concerns:
  - OpenTelemetry = runtime / operational observability
  - Session analysis logs = knowledge & model evolution feedback

### 5.4 Background Knowledge Rebuild & Hot Swap
- [ ] Support **background knowledge rebuild** without impacting active services
  - GraphDB and VectorDB rebuilds MUST run out-of-band
  - no in-place mutation of active knowledge stores
- [ ] Introduce **generation-based knowledge management**
  - each rebuild produces a new immutable generation (e.g. v1, v2, ...)
  - active generation is selected via an explicit pointer
- [ ] Implement **atomic hot swap**
  - switch active generation in a single, fast operation
  - ensure explainConcept / explainContext continue uninterrupted
- [ ] Provide minimal validation before swap
  - basic counts and integrity checks
  - failed generations are discarded safely
- [ ] Expose generation state for diagnostics
  - active generation
  - build-in-progress generation
  - last successful swap timestamp
- [ ] Integrate observability hooks
  - background build duration and outcome
  - swap events as distinct operational traces
- [ ] Defer implementation until after:
  - explainConcept stabilization
  - explainContext introduction

---


## 5.x RagService Operations

### Core (ChatGPT-facing)
- [x] query
  - current run / runIO implementation
  - legacy exploratory query (concept + document)
  - candidate for refactoring into semanticQuery
- [x] explainConcept
  - authoritative explanation for a single concept (URI-based)
  - primary source: prebuilt ConceptDictionary
- [ ] semanticQuery
  - exploratory / probabilistic operation
  - natural-language input
  - returns ranked concepts, documents, and lightweight structure
  - primary entry point for ChatGPT understanding phase
- [ ] explainContext
  - contextual explanation over multiple concepts
  - session-aware narrative explanation
  - successor of repeated explainConcept calls

### Structural / Utility
- [x] getNeighbors
  - retrieve local concept relations (parents / children / related)
  - structural building block for explanations
- [ ] resolveConcept
  - resolve natural-language terms into candidate Concept URIs
  - explicit elevation step before explainConcept

### Advanced / Future
- [ ] compareConcepts
  - structured comparison between two or more concepts
- [ ] traceRelation
  - explain semantic relationship path between concepts
- [ ] conceptDiscovery
  - detect missing or emerging concepts from documents
  - intended for model / BoK refinement
- [ ] explainCoverage
  - assess knowledge coverage for a domain or topic
  - internal quality and completeness analysis

## 6. Documentation & Articles

### 6.1 ChatGPT / MCP Articles
- [ ] Article: sbt dev + DEV runtime walkthrough
- [ ] Article: Semantic Integration vs classic RAG
- [ ] Article: Agent-mode and MCP design rationale

### 6.2 Diagrams
- [ ] Runtime topology diagrams
- [ ] Knowledge flow (GraphDB → VectorDB → Agent)
- [ ] Design Axes (A–F) visual overview

---

## 7. Safety & Future-proofing

- [ ] Ensure no accidental VectorDB rebuild in PROD
- [ ] Explicit comments for AI-assisted refactoring tools
- [ ] Regression checklist for agent-mode changes

---

## Notes

- Step B (agent-mode behavioral branching) is intentionally deferred.
- All TODOs must respect the Design Axes defined in RagServerMain.
- No item here should collapse Architecture / Topology / RuntimeFeatures axes.
