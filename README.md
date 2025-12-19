# Semantic Integration Engine (SIE)

The **Semantic Integration Engine (SIE)** is a hybrid semantic–vector
retrieval engine designed to integrate structured knowledge graphs,
symbolic reasoning, and modern embedding models.

SIE can be used as a **stand-alone RAG engine**, providing:
- High-quality text–to–knowledge retrieval
- REST and MCP interfaces for AI agents
- Hybrid symbolic + vector reasoning

However, SIE is also developed as part of the  
**SimpleModeling.org / SmartDox ecosystem**, where it achieves its
full potential.

When combined with:
- **SmartDox** (structured, bilingual, semantically enriched documents)
- **SimpleModeling.org Knowledge Graph** (RDF/JSON-LD based BoK)

SIE becomes a *knowledge-grounded AI engine* capable of:
- Understanding domain models and conceptual structures
- Answering questions with explicit semantic context
- Performing retrieval that respects ontology, categories, references,
  and document structure
- Supporting AI-assisted modeling, documentation, and development workflows

In short:
**SIE works standalone, but it becomes dramatically more powerful
when integrated with the semantic knowledge base of SimpleModeling.org.**

---

## 🌐 Architecture Overview

    ┌───────────────────────────────────────────────────────────┐
    │                   Semantic Integration Engine (SIE)       │
    │                                                           │
    │   ┌──────────────┐     ┌────────────────────────────┐    │
    │   │  Fuseki RDF  │<----│  init-fuseki (one-shot KG) │    │
    │   └──────────────┘     └────────────────────────────┘    │
    │          ↑                                                 │
    │          │ SPARQL                                          │
    │          │                                                 │
    │   ┌───────────────┐                                       │
    │   │ sie-embedding │----→ vector search                     │
    │   └───────────────┘                                       │
    │                                                           │
    │ REST API: /sie/query                                      │
    │ MCP API : ws://host:9051/mcp                              │
    └───────────────────────────────────────────────────────────┘

SIE integrates both symbolic and embedding-based retrieval to produce
context-aware answers grounded in the SimpleModeling knowledge graph.

This overview focuses on runtime and deployment components.
MCP protocol design, adapters, and tool semantics are documented separately.

---

## 🔧 Build & Run (Development)

    sbt clean assembly

The fat JAR is generated at:

    dist/semantic-integration-engine.jar

    java -jar dist/semantic-integration-engine.jar

Environment variables:

- `FUSEKI_URL` – RDF endpoint (default: http://localhost:3030/ds)
- `SIE_EMBEDDING_URL` – embedding backend
- `SIESERVER_PORT` – REST/MCP server port

---

## 🧪 Development Startup Flow (Recommended)

In DEV mode, SIE is designed to run **outside Docker** on the local JVM,
while external knowledge stores are provided by Docker.

This enables fast iteration, debugging, and safe reset of local data.

### Architecture (DEV)

    Local JVM (sbt dev)
        └─ Semantic Integration Engine (SIE)
             ├─ GraphDB  → Fuseki (Docker)
             └─ VectorDB → sie-embedding / Chroma (Docker)

### Step-by-step

1. Start external dependencies (GraphDB / VectorDB):

       docker compose -f docker-compose.dev.yml up -d

   This starts:
   - Apache Jena Fuseki (RDF / Knowledge Graph)
   - sie-embedding (Embedding + VectorDB backed by Chroma)

2. Start SIE locally:

       sbt dev

   SIE connects to Docker-managed services via localhost.

3. Verify status:

       curl http://localhost:9050/status | jq

   You should see:
   - mode = DEV
   - graphDb / vectorDb roles and readiness
   - overall.state = healthy | degraded | unavailable

   Note:
   - `healthy`   : all required subsystems are ready
   - `degraded`  : system is operational with limited capabilities (e.g. vector index initializing)
   - `unavailable`: semantic queries cannot be served

### Endpoint resolution (DEV)

In DEV mode, endpoints are resolved as follows:

- Default endpoints (no configuration required):
    - Fuseki    → http://localhost:9030/ds
    - VectorDB  → http://localhost:8081
    - Embedding → http://localhost:8081/embed

- Overrides (advanced use only):
    - Environment variables:
        - FUSEKI_URL
        - SIE_VECTORDB_ENDPOINT
        - SIE_EMBEDDING_ENDPOINT
    - Explicit definitions in `application.dev.conf`

See comments in `application.dev.conf` and `AppConfig.scala`
for the full DEV resolution policy.

---

## 🐳 Build Docker Image (using existing Dockerfile)

    docker build -t ghcr.io/asami/sie:latest .
    docker push ghcr.io/asami/sie:latest

---

# Demo Environment (Optional)

A full demo environment—including Fuseki, the embedding backend, automatic
RDF loader, and SIE itself—is provided via:

- `docker-compose.demo.yml`
- GHCR-hosted images:
    - `ghcr.io/asami/init-fuseki:latest`
    - `ghcr.io/asami/sie-embedding:latest`
    - `ghcr.io/asami/sie:latest`

The demo is designed for:
- evaluating the hybrid retrieval pipeline
- exploring semantic queries over the SimpleModeling knowledge graph
- demonstrating MCP tool integration with ChatGPT
- reproducing examples from SimpleModeling.org articles

---

## 🚀 Running the Demo

    docker compose -f docker-compose.demo.yml up -d

Available endpoints:

- Fuseki UI: http://localhost:9030
- SIE REST API: http://localhost:9050
- MCP WebSocket: ws://localhost:9051/mcp

### Example REST query

    curl -X POST http://localhost:9050/sie/query \
      -H "Content-Type: application/json" \
      -d '{"query": "Explain SimpleModelObject."}'

### Example MCP session (wscat)

    wscat -c ws://localhost:9051/mcp

    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}

---

## Recreating Containers to Ensure Updated Images

When you rebuild Docker images locally (for demo or production verification),
Docker Compose may continue using existing containers based on older images.

To ensure that the latest locally built image is actually used,
it is recommended to recreate containers explicitly.

Recommended command:

    docker compose up -d --force-recreate

This command ensures that:
- Existing containers are stopped and recreated
- The latest local Docker images are used
- No stale containers remain from previous runs

Use --force-recreate when:
- You rebuilt the SIE Docker image locally
- You updated MCP setup scripts or templates
- You want to verify demo or production images reliably

This practice is especially useful when validating demo environments
or testing production images before publishing.


## 🔀 Choose Your MCP Client

SIE supports multiple MCP clients by separating the MCP Core
from protocol-specific adapters (ChatGPT / JSON-RPC).

## 🧰 MCP Tool Naming Conventions (SIE)

```
SIE exposes its MCP tools as a **stable semantic API** shared by both
ChatGPT and VS Code MCP clients.

Tool names are designed to be:
- Semantically meaningful
- Predictable for LLM tool selection
- Stable across versions
- Independent of transport or implementation details

### Naming Rules

- Tool names use **camelCase**
- Structure: `<verb><Object>`
- Verbs represent **intent / operation**
- Objects represent **SIE domain vocabulary**
- Tool names are treated as **stable APIs** (avoid breaking changes)

### Allowed Verbs

| Verb | Meaning |
|------|--------|
| `explain` | Explain a concept, model, or document |
| `query` | Perform conditional or exploratory queries |
| `list` | List available resources |
| `get` | Retrieve a single resource by identifier |
| `resolve` | Resolve references or relationships |
| `analyze` | Perform analysis or evaluation |
| `validate` | Validate structure or constraints |
| `generate` | Generate derived artifacts |

Avoid vague verbs such as `do`, `run`, `execute`, or `process`.

### Standard SIE Objects

| Object | Meaning |
|--------|--------|
| `Concept` | Conceptual entity (LexiDox / CML) |
| `Graph` | Semantic knowledge graph |
| `Document` | Structured document |
| `Passage` | Document fragment |
| `Model` | Domain or system model |
| `Entity` | Domain entity |
| `Vocabulary` | Controlled vocabulary |
| `Ontology` | RDF / OWL structure |

### Example Tool Names

- `explainConcept`
- `queryGraph`
- `listDocuments`
- `getDocument`
- `resolveConcept`
- `analyzeModel`

### Versioning Policy

- Tool names are **not renamed** once published
- Breaking changes require a version suffix:

```
explainConceptV2
```

- Older versions may coexist during migration

### Design Rationale

- Tool names represent **semantic intent**, not implementation
- Transport details (`ws`, `stdio`, `mcp`) are intentionally excluded
- Internal technologies (`rdf`, `sparql`, `vector`, `embedding`) are hidden
- This allows the same tool set to be used consistently from:
  - ChatGPT (WebSocket-based MCP)
  - VS Code (stdio-based MCP)
```

You can use **VS Code** or **ChatGPT** as an MCP client.

In addition to MCP-based clients, SIE also exposes a traditional REST API.
This allows you to use SIE **without MCP**, using standard HTTP tools.

---

## Option C: REST API (Direct HTTP Access)

The REST API is useful for:
- Quick testing with `curl` or HTTP clients
- Integration with existing applications
- Understanding SIE behavior without MCP tooling

### REST Endpoint

http://localhost:9050/sie/query

### Example REST query

```bash
curl -X POST http://localhost:9050/sie/query \
  -H "Content-Type: application/json" \
  -d '{ "query": "Explain SimpleModelObject." }'
```

The REST API and MCP API share the same underlying semantic engine.
The REST interface is intentionally kept simple and stateless.

---

## MCP Client (stdio-based CLI)

The `mcp-client` can be used as a standalone **stdio-based CLI**.

It accepts:
- MCP JSON-RPC messages via STDIN
- Interactive **meta-commands** starting with `:` for client control

This allows developers to inspect and debug MCP initialization and capabilities
without relying on VS Code or other MCP runtimes.

---

## Current status of VS Code MCP integration

At the time of writing, VS Code does not automatically invoke
the MCP stdio server defined in `.vscode/settings.json`.

Although the configuration is syntactically correct,
the MCP runtime is not activated, and `initialize` / `get_manifest`
are not triggered from VS Code.

This repository therefore provides a **CLI-based workflow**
as a practical and reproducible alternative.

---

## MCP Client Meta Commands

The following meta-commands are supported by `mcp-client`:

- `:help`  
  Show available meta-commands.

- `:status`  
  Show client role, transport, and REST endpoint.

- `:initialize`  
  Dump the merged MCP initialize response (`initialize.json` + `mcp.json`).

- `:manifest`  
  Dump the MCP capabilities (tools definition).

- `:exit`, `:quit`  
  Exit the client immediately.

---

## Exiting the MCP client

Because this client runs in stdio mode, signal handling may vary
depending on the execution environment (terminal, Docker, Emacs, etc.).

The following meta-commands are always supported and recommended:

- `:exit`
- `:quit`

These commands terminate the client reliably, even when Ctrl-C or EOF
are not propagated correctly.

---

## Why a CLI?

MCP is designed as a tool-connection protocol, but its runtime support
in editors is still evolving.

Providing a CLI allows:
- deterministic inspection of MCP initialization
- debugging without editor-specific behavior
- reproducible demos and documentation

This CLI is therefore **not a fallback**, but a deliberate design choice.

---

## 📄 License

### Software (code)
Licensed under the **Apache License 2.0**  
https://www.apache.org/licenses/LICENSE-2.0

### Documentation (text, examples, diagrams)
Licensed under **CC-BY-SA 4.0**  
https://creativecommons.org/licenses/by-sa/4.0/

---

## 🤝 Contributions

Contributions, issues, and feature requests are welcome.  
Please open an issue or pull request on GitHub.
