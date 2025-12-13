#!/bin/sh
set -e
echo "[init-fuseki] Waiting for Fuseki endpoint..."

# Wait for Fuseki HTTP server
until curl -s http://fuseki:3030/ >/dev/null 2>&1; do
  echo "[init-fuseki] Fuseki not ready..."
  sleep 2
done

echo "[init-fuseki] Waiting for dataset /ds..."
until curl -s http://fuseki:3030/ds >/dev/null 2>&1; do
  echo "[init-fuseki] Dataset /ds not yet ready..."
  sleep 2
done

echo "[init-fuseki] Checking existing data in Fuseki..."
COUNT=$(curl -s http://fuseki:3030/ds/query \
  --data-urlencode 'query=SELECT (COUNT(*) AS ?c) WHERE { ?s ?p ?o }' \
  -H 'Accept: application/sparql-results+json' \
  | grep -o '"value"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"value"[[:space:]]*:[[:space:]]*"//;s/"//')

if [ -n "$COUNT" ] && [ "$COUNT" != "0" ]; then
  echo "[init-fuseki] Fuseki already contains data ($COUNT triples). Skipping initialization."
  exit 0
fi


############################################
# Load Site JSON-LD (ABox)
############################################
echo "[init-fuseki] Downloading site.jsonld..."
curl -L -o /tmp/site.jsonld https://www.simplemodeling.org/site.jsonld

echo "[init-fuseki] Loading site.jsonld..."
curl -X POST \
  -H "Content-Type: application/ld+json" \
  --data-binary @/tmp/site.jsonld \
  http://fuseki:3030/ds/data

echo "[init-fuseki] ✓ site.jsonld loaded."


############################################
# Load Ontology TTLs (TBox)
############################################
ONTO_BASE="https://www.simplemodeling.org"

ONTO_PATHS="
  simplemodelingorg/ontology/0.1-SNAPSHOT/index.ttl
  simplemodel/ontology/0.1-SNAPSHOT/index.ttl
  glossary/ontology/0.1-SNAPSHOT/index.ttl
  category/ontology/0.1-SNAPSHOT/index.ttl
  bok/ontology/0.1-SNAPSHOT/index.ttl
  smartdox/ontology/0.1-SNAPSHOT/index.ttl
  ontology/simplemodelingorg.ttl
"

echo "[init-fuseki] Loading TBox ontologies..."

for P in $ONTO_PATHS; do
  URL="$ONTO_BASE/$P"

  echo "[init-fuseki] Downloading $URL..."
  curl -s -I "$URL" | grep -qi "text/turtle"
  if [ $? -ne 0 ]; then
    echo "[init-fuseki] ⚠ SKIP non-turtle: $URL"
    continue
  fi

  TMP=$(mktemp)
  curl -s -L "$URL" -o "$TMP"

  echo "[init-fuseki] Loading TTL into Fuseki: $URL"
  curl -X POST \
    -H "Content-Type: text/turtle" \
    --data-binary @"$TMP" \
    http://fuseki:3030/ds/data

  echo "[init-fuseki] ✓ Loaded $URL"
done

echo "[init-fuseki] Ontology loading complete."
