#! /bin/sh

cd src/main/python/embedding

docker build -t ghcr.io/asami/sie-embedding:latest .

docker push ghcr.io/asami/sie-embedding:latest
