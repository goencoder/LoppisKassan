#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> Checking Java & Maven..."
command -v java >/dev/null 2>&1 || { echo "Java is required in PATH"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "Maven is required in PATH"; exit 1; }

echo "==> Java version:"
java -version || true
echo "==> Maven version:"
mvn -v || true

# ---- Install local OpenAPI client into local Maven repo ----
CLIENT_JAR="lib/openapi-java-client-0.0.4.jar"
GROUP_ID="se.goencoder.iloppis"
ARTIFACT_ID="iloppis-client"
VERSION="0.0.4"

if [[ ! -f "$CLIENT_JAR" ]]; then
  echo "ERROR: Expected local client jar at $CLIENT_JAR but it was not found."
  exit 2
fi

echo "==> Installing local OpenAPI client to ~/.m2 ..."
mvn -q install:install-file \
  -Dfile="$CLIENT_JAR" \
  -DgroupId="$GROUP_ID" \
  -DartifactId="$ARTIFACT_ID" \
  -Dversion="$VERSION" \
  -Dpackaging=jar

# ---- Build & quality gates ----
echo "==> Build (skip tests by default for faster codex runs)"
mvn -q -DskipTests package

echo "==> Unit tests"
mvn -q test

echo "==> Lint (Checkstyle/SpotBugs if configured) & Enforcer"
mvn -q -DskipTests verify || true

echo "==> Security (OWASP dependency-check if configured)"
mvn -q org.owasp:dependency-check-maven:check || true

echo "==> Done."
