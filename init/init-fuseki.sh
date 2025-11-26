#!/bin/sh
set -e

echo "[init-fuseki] Waiting for Fuseki endpoint..."
until curl -s -f "http://fuseki:3030/" > /dev/null; do
  echo "[init-fuseki] Fuseki not ready..."
  sleep 2
done

echo "[init-fuseki] Waiting for dataset /ds..."
until curl -s -f "http://fuseki:3030/ds" > /dev/null; do
  echo "[init-fuseki] Dataset /ds not ready..."
  sleep 2
done

echo "[init-fuseki] Downloading SimpleModeling RDF..."
curl -L -o /tmp/site.jsonld https://www.simplemodeling.org/site.jsonld

echo "[init-fuseki] Loading site.jsonld into Fuseki..."
curl -f -X POST \
  -H "Content-Type: application/ld+json" \
  --data-binary @/tmp/site.jsonld \
  "http://fuseki:3030/ds/data?default"

echo "[init-fuseki] Load completed successfully."
