FROM eclipse-temurin:17-jre

# Working directory inside the container
WORKDIR /app

# Copy the fat JAR that includes RagServerMain and McpClientMain
COPY dist/semantic-integration-engine.jar /app/semantic-integration-engine.jar

# Environment variables (can be overridden by docker-compose.yml)
ENV FUSEKI_URL=http://fuseki:9030/ds
ENV CHROMA_URL=http://chroma:9040
ENV SIESERVER_PORT=9050

# Default command:
# Run the REST RAG Server as the main container process.
# The MCP client will be executed via `docker exec` from the host
# when ChatGPT calls the MCP tool.
CMD ["java", "-cp", "/app/semantic-integration-engine.jar", "org.simplemodeling.sie.server.RagServerMain"]
