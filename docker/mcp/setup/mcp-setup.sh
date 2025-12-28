#!/bin/sh
set -e

# ------------------------------------------------------------
# MCP workspace initializer
#
# - Filesystem preparation only
# - No network access
# - Safe to run multiple times
# ------------------------------------------------------------

MODE="interactive"

if [ "$1" = "--non-interactive" ]; then
    MODE="non-interactive"
fi

echo "[mcp-setup] mode: ${MODE}"

mkdir -p .vscode

mkdir -p bin

if [ ! -x "bin/sie-mcp" ]; then
    cp /opt/sie/bin/sie-mcp bin/sie-mcp
    chmod +x bin/sie-mcp
fi

if [ ! -x "bin/sie-cli" ]; then
    cp /opt/sie/bin/sie-cli.template bin/sie-cli
    chmod +x bin/sie-cli
fi

if [ ! -f ".vscode/settings.json" ]; then
    cat > .vscode/settings.json <<EOF
{
  "mcp.servers": {
    "sie": {
      "command": "./bin/sie-mcp"
    }
  }
}
EOF
fi

if [ ! -f ".sie-workspace-initialized" ]; then
    touch .sie-workspace-initialized
fi

if [ "${MODE}" = "interactive" ]; then
    echo "[mcp-setup] MCP workspace initialized"
fi
