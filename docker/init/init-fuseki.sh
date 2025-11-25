#!/bin/sh
set -e

echo "[init-fuseki] Initializing Fuseki dataset..."

# Ensure directory exists
mkdir -p /fuseki/databases/ds

echo "[init-fuseki] Downloading site.jsonld ..."
curl -L -o /fuseki/databases/ds/site.jsonld \
     https://www.simplemodeling.org/site.jsonld

echo "[init-fuseki] Done. Dataset 'ds' is ready."
