#! /bin/sh

docker build \
  -f init/Dockerfile.preload-fuseki \
  -t ghcr.io/asami/preload-fuseki:latest \
  .

docker push ghcr.io/asami/preload-fuseki:latest
