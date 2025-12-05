#! /bin/sh

cd init

docker build \
  -f Dockerfile.init-fuseki \
  -t ghcr.io/asami/init-fuseki:latest \
  .

docker push ghcr.io/asami/init-fuseki:latest
