# Semantic Integration Engine — Fuseki + ChromaDB + SIE Server

This repository provides a fully containerized Semantic Integration Engine (SIE), combining RDF-based knowledge graphs and vector retrieval for advanced AI-assisted modeling and knowledge integration.

The system consists of:

- **SIE Server** (Scala) — REST-based Semantic Integration service
- **MCP Client** — allows ChatGPT to access the RAG backend
- **Fuseki** — RDF triple store
- **ChromaDB** — Vector database

All components run inside Docker containers.

## ✨ User Installation (Recommended)

You do **NOT** need to clone this repository.

Simply copy the following file and run Docker:

    docker/release/docker-compose.yml

Then start the system:

    docker compose up -d

Services:

| Service    | Port | Description                      |
|------------|------|--------------------------------|
| Fuseki     | 9030 | SPARQL endpoint                |
| ChromaDB   | 9040 | Vector database                |
| SIE Server | 9050 | REST Semantic Integration API |

REST endpoint:

    http://localhost:9050/sie/query

## 🧠 Using the MCP Client with ChatGPT

Create an `mcpc` script in the project root:

    #!/bin/bash
    docker exec -i sie \
      java -cp /app/semantic-integration-engine.jar org.simplemodeling.sie.mcp.McpClientMain

Make it executable:

    chmod +x mcpc

Add this file as an MCP server command in ChatGPT:

    ./mcpc

## 👨‍💻 Development Setup

Clone the repository:

    git clone https://github.com/YOURNAME/semantic-integration-engine.git
    cd semantic-integration-engine

Build the fat JAR:

    sbt assembly
    cp target/scala-3.4.2/semantic-integration-engine.jar dist/

Start the development environment:

    docker compose -f docker/dev/docker-compose.dev.yml up -d

This will:

- Build the Docker image from the root Dockerfile
- Use the latest `dist/semantic-integration-engine.jar`
- Run Fuseki, ChromaDB, and SIE Server

## 🏗️ Publishing the Production Image (GHCR)

    docker build -t ghcr.io/YOURNAME/semantic-integration-engine:2025-11-21 .
    docker push ghcr.io/YOURNAME/semantic-integration-engine:2025-11-21

## 📁 Project Structure

    semantic-integration-engine/
      docker/
        dev/docker-compose.dev.yml
        release/docker-compose.yml
      dist/semantic-integration-engine.jar
      Dockerfile
      src/main/scala/...
      mcpc
      README.md

## License

Apache-2.0
