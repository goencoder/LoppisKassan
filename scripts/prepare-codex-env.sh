#!/usr/bin/env bash
set -euo pipefail
cd /workspace/LoppisKassan

# Proxy env (Codex proxy)
export PROXY_HOST=${PROXY_HOST:-proxy}
export PROXY_PORT=${PROXY_PORT:-8080}
export http_proxy="http://${PROXY_HOST}:${PROXY_PORT}"
export https_proxy="$http_proxy"
export HTTP_PROXY="$http_proxy"
export HTTPS_PROXY="$http_proxy"
export MAVEN_OPTS="${MAVEN_OPTS:-} -Djava.net.preferIPv4Stack=true \
  -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} \
  -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT} \
  -Dhttp.nonProxyHosts=localhost|127.0.0.1|::1"

# Let Makefile do the rest
make build-codex
