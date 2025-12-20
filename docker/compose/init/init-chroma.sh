#!/bin/sh
set -e

echo "=== init-chroma start ==="
echo "CHROMA_URL=$CHROMA_URL"
echo "OPENAI_API_KEY=${OPENAI_API_KEY:+(set)}"

apt-get update && apt-get install -y curl

pip install chromadb requests openai

curl -L -o /tmp/site.jsonld https://www.simplemodeling.org/site.jsonld
echo "[OK] downloaded site.jsonld"

python3 /tmp/init-chroma.py

echo "=== init-chroma done ==="
