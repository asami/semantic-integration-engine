# Semantic Integration Engine — Fuseki + ChromaDB + SIE Server

This repository provides a fully containerized Semantic Integration Engine (SIE),
combining RDF-based knowledge graphs and vector retrieval for advanced
AI-assisted modeling and knowledge integration.

The system consists of:

- **SIE Server** (Scala) — REST-based Semantic Integration service
- **MCP Client** — allows ChatGPT to access the RAG backend
- **Fuseki** — RDF triple store
- **ChromaDB** — Vector database

All components run inside Docker containers.


## ✨ User Installation (Recommended)

You do **NOT** need to clone this repository.

Download the file:

    docker-compose.yml

Then start:

    docker compose up -d

Services:

    Fuseki     → http://localhost:9030
    ChromaDB   → http://localhost:9040
    SIE Server → http://localhost:9050

REST endpoint:

    http://localhost:9050/sie/query


## 🧠 Using the MCP Client with ChatGPT

Create an `mcpc` script:

    #!/bin/bash
    docker exec -i sie \
      java -cp /app/semantic-integration-engine.jar \
      org.simplemodeling.sie.mcp.McpClientMain

Make it executable:

    chmod +x mcpc

Register in ChatGPT as an MCP command:

    ./mcpc


## 🚀 Deployment Modes (5 Variants)

Select one of the following docker-compose files:

    docker-compose.yml
    docker-compose.plain.yml
    docker-compose.project.yml
    docker-compose.sm-project.yml
    docker-compose.dev.yml

### 1. SimpleModeling Standard Mode
Loads SimpleModeling.org knowledge.

    docker compose -f docker-compose.yml up -d

### 2. Plain Mode (Empty)
Starts with no initial data.

    docker compose -f docker-compose.plain.yml up -d

### 3. Project-only Mode
Loads your project’s BoK only.

    docker compose -f docker-compose.project.yml up -d

Requires:

    project/site.jsonld

### 4. SM + Project Hybrid Mode
Loads:
- SimpleModeling.org knowledge
- Your project’s BoK

    docker compose -f docker-compose.sm-project.yml up -d

### 5. Development Mode
Uses the local JAR under `dist/`.

    docker compose -f docker-compose.dev.yml up -d


## 👨‍💻 Development Setup

Clone the repository:

    git clone https://github.com/YOURNAME/semantic-integration-engine.git
    cd semantic-integration-engine

Build the JAR:

    sbt assembly
    cp target/scala-3.4.2/semantic-integration-engine.jar dist/

Start development environment:

    docker compose -f docker-compose.dev.yml up -d


## 🏗️ Publishing the Production Image (GHCR)

    docker build -t ghcr.io/YOURNAME/semantic-integration-engine:2025-11-26 .
    docker push ghcr.io/YOURNAME/semantic-integration-engine:2025-11-26


## 📁 Project Structure

    semantic-integration-engine/
      docker-compose.yml
      docker-compose.plain.yml
      docker-compose.project.yml
      docker-compose.sm-project.yml
      docker-compose.dev.yml
      dist/semantic-integration-engine.jar
      src/main/scala/...
      Dockerfile
      mcpc
      README.md


## License

Apache-2.0
