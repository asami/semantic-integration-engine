#!/bin/sh
set -e

# ------------------------------------------------------------
# MCP client launcher (container internal)
#
# - Wraps Java execution
# - STDIO based MCP client
# - NEVER executed directly from host
# ------------------------------------------------------------

CONFIG_FILE="${SIE_CONFIG_FILE:-/app/conf/application.demo.conf}"
WS_URL="${SIE_MCP_WS_URL:-ws://localhost:9051/mcp}"

exec java \
    -Dconfig.file="${CONFIG_FILE}" \
    -cp /app/semantic-integration-engine.jar \
    org.simplemodeling.sie.mcp.McpClientMain
