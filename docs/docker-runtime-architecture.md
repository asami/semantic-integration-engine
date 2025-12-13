# SIE Runtime Architecture with Docker:
# Separation of Embedding API and VectorDB API

## 1. Overview

The Semantic Integration Engine (SIE), when deployed via Docker, consists of
the following core services:

    - sie: Main SIE runtime (RAG API, explainConcept, MCP WebSocket server)
    - sie-embedding: Provides Embedding API and VectorDB API
    - fuseki: RDF Triple Store (SPARQL endpoint)

This document explains the runtime architecture of SIE with a focus on
separating the Embedding API and the VectorDB API, which is essential for
clean layering, backend independence, and future extensibility.


## 2. API Layers inside `sie-embedding`

The `sie-embedding` container exposes two independent APIs:

    A. Embedding API (`/embed`)
        - Generates vector embeddings
        - Backend may be OSS or OpenAI
        - Used by SIE during RAG operations

    B. VectorDB API (`/chroma/...`)
        - REST access to a vector database
        - Internally backed by ChromaDB
        - Handles collection creation, upserts, similarity search

Runtime relationship:

    [SIE]
      ├── /embed          → sie-embedding (Embedding Engine)
      └── /chroma/...     → sie-embedding (VectorDB REST API)

SIE never communicates directly with ChromaDB or any physical vector store.


## 3. Environment Variables in Docker Compose

To keep responsibilities clean, SIE uses two distinct endpoints:

    1. Embedding API endpoint
        SIE_EMBEDDING_ENDPOINT=http://sie-embedding:8081/embed

    2. VectorDB API endpoint (abstract; backend-independent)
        SIE_VECTORDB_ENDPOINT=http://sie-embedding:8081

Why separate them?

    - Embedding and VectorDB are different responsibilities
    - VectorDB backend may change (Chroma → Qdrant → Weaviate → Milvus)
    - SIE should not depend on implementation-specific names
    - Docker configuration remains stable across backend migrations
    - sie-embedding may internally replace ChromaDB in the future


## 4. Startup Order in Docker Compose

Correct startup sequence:

    fuseki → init-fuseki → sie-embedding (healthy) → sie

Example:

    depends_on:
      fuseki:
        condition: service_started
      init-fuseki:
        condition: service_completed_successfully
      sie-embedding:
        condition: service_healthy


## 5. Persistence of the VectorDB (inside sie-embedding)

`sie-embedding` stores vector database files internally. Persistence:

    sie-embedding:
      volumes:
        - ./chroma-data:/root/.local/share/chroma

Benefits:

    - Immediate availability of vector search after restart
    - No need to re-index on every boot
    - Faster health checks (collectionExists=true)


## 6. SIE Container Configuration

Example:

    environment:
      FUSEKI_URL: http://fuseki:3030/ds
      SIE_EMBEDDING_MODE: "oss"
      SIE_EMBEDDING_ENDPOINT: "http://sie-embedding:8081/embed"
      SIE_VECTORDB_ENDPOINT: "http://sie-embedding:8081"
      SIE_CONFIG_MODE: "dev"

Key point:

    SIE depends only on sie-embedding and fuseki, never on a standalone
    ChromaDB container.


## 7. `/health` Behavior

SIE checks:

    1. Embedding API reachable?
    2. VectorDB API reachable?
    3. VectorDB collection exists?
    4. Fuseki reachable?

With correct separation:

    - SIE_VECTORDB_ENDPOINT ensures VectorDB availability
    - collectionExists=true becomes reliable
    - RAG queries remain consistent across restarts


## 8. Future Extensions Enabled by This Architecture

    ✔ Replace the VectorDB backend (Chroma → Qdrant → Weaviate → Milvus)
    ✔ Swap embedding backend (OSS/OpenAI)
    ✔ Create a unified `/vectordb/...` API in the future
    ✔ Support production deployments with managed vector stores


## 9. Summary (Key Principles)

    - Separate embedding from vector storage
    - Avoid backend-specific naming
    - Encapsulate all vector store logic inside sie-embedding
    - Maintain stable Docker configuration
    - Enable future backend migrations seamlessly


## 10. Recommended File Placement

    semantic-integration-engine/docs/docker-runtime-architecture.md

Or as part of a broader API reference:

    semantic-integration-engine/docs/api/runtime-architecture.md
