#!/bin/sh
set -e

# ------------------------------------------------------------
# SIE Entrypoint
#
# Responsibilities:
#   - Start SIE server ONLY
#   - Select config file by mode (demo / production)
#   - Initialize MCP workspace in demo mode (filesystem only)
#
# Non-responsibilities:
#   - DO NOT start MCP client
#   - DO NOT perform docker exec
#   - DO NOT start external commands
# ------------------------------------------------------------

MODE="${SIE_MODE:-demo}"
WORKSPACE_DIR="${SIE_WORKSPACE_DIR:-/workspace/vscode-mcp}"

echo "[sie] starting Semantic Integration Engine"
echo "[sie] mode: ${MODE}"

if [ "${MODE}" = "demo" ]; then
    echo "[sie] demo mode: initializing MCP workspace"
    mkdir -p "${WORKSPACE_DIR}"
    cd "${WORKSPACE_DIR}"
    /opt/sie/bin/mcp-setup --non-interactive
fi

if [ "${MODE}" = "demo" ]; then
    CONFIG_FILE="/app/conf/application.demo.conf"
else
    CONFIG_FILE="/app/conf/application.conf"
fi

echo "[sie] using config: ${CONFIG_FILE}"

exec java \
    -Dconfig.file="${CONFIG_FILE}" \
    -jar /app/semantic-integration-engine.jar
