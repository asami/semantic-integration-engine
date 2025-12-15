FROM eclipse-temurin:17-jre

WORKDIR /app

# copy fat jar
COPY dist/semantic-integration-engine.jar /app/semantic-integration-engine.jar

# copy external config files
RUN mkdir -p /app/conf

COPY src/main/resources/*.conf /app/conf/

# MCP setup assets (demo distribution)
RUN mkdir -p /opt/sie/bin /opt/sie/mcp-setup
COPY docker/mcp/setup/ /opt/sie/mcp-setup/
COPY docker/mcp/setup/mcp-setup.sh /opt/sie/bin/mcp-setup
RUN chmod +x /opt/sie/bin/mcp-setup

# MCP client launcher (container internal)
COPY docker/mcp/setup/mcp-client.sh /opt/sie/bin/mcp-client
RUN chmod +x /opt/sie/bin/mcp-client

# MCP launcher for VS Code (exported to workspace by mcp-setup)
COPY docker/mcp/setup/sie-mcp.template /opt/sie/bin/sie-mcp
RUN chmod +x /opt/sie/bin/sie-mcp

# SIE entrypoint (shared for demo / production)
COPY docker/sie-entrypoint.sh /opt/sie/bin/sie-entrypoint.sh
RUN chmod +x /opt/sie/bin/sie-entrypoint.sh

# environment variables (override by compose)
ENV FUSEKI_URL=http://fuseki:3030/ds
ENV CHROMA_URL=http://chromadb:8000
ENV SIESERVER_PORT=9050

# expose port for clarity (optional)
EXPOSE 9050

ENTRYPOINT ["/opt/sie/bin/sie-entrypoint.sh"]
