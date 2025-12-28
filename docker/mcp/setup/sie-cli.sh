#!/bin/sh
set -e

# ------------------------------------------------------------
# SIE CLI launcher (container internal)
#
# - Wraps Java execution
# - REST-based CLI client
# - NEVER executed directly from host
# ------------------------------------------------------------

CONFIG_FILE="${SIE_CONFIG_FILE:-/app/conf/application.demo.conf}"
REST_ENDPOINT="${SIE_REST_ENDPOINT:-http://sie:9050}"

export SIE_REST_ENDPOINT="${REST_ENDPOINT}"

exec java \
    -Dconfig.file="${CONFIG_FILE}" \
    -cp /app/semantic-integration-engine.jar \
    org.simplemodeling.sie.cli.SieCliMain \
    "$@"
