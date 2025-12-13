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

---

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
