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
