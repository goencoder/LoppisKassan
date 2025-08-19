SHELL := /usr/bin/env bash
.ONESHELL:
ROOT_DIR := $(shell pwd)

# ---- Local OpenAPI client ----
CLIENT_JAR := lib/openapi-java-client-0.0.4.jar
GROUP_ID   := se.goencoder.iloppis
ARTIFACT_ID:= iloppis-client
VERSION    := 0.0.4

JAR_NAME := target/LoppisKassan-v2.0.0-jar-with-dependencies.jar

.DEFAULT_GOAL := help

help: ## Show help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS=":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

java-version: ## Print java & maven versions
	java -version || true
	mvn -v || true

install-client: ## Install local OpenAPI client jar into ~/.m2
	@if [ ! -f "$(CLIENT_JAR)" ]; then echo "Missing $(CLIENT_JAR)"; exit 2; fi
	mvn -q install:install-file \
	  -Dfile=$(CLIENT_JAR) \
	  -DgroupId=$(GROUP_ID) \
	  -DartifactId=$(ARTIFACT_ID) \
	  -Dversion=$(VERSION) \
	  -Dpackaging=jar

build: install-client ## Build fat jar (skip tests for speed)
	mvn -q -DskipTests package

test: ## Run unit tests
	mvn -q test

verify: ## Lint & enforce rules (Checkstyle/SpotBugs/Enforcer if configured)
	mvn -q -DskipTests verify

security: ## OWASP Dependency-Check (if plugin present)
	mvn org.owasp:dependency-check-maven:check

run: ## Run the packaged app (requires jpackage/fat jar step)
	@if [ ! -f "$(JAR_NAME)" ]; then echo "Jar not found. Run: make build"; exit 3; fi
	java --enable-preview -jar $(JAR_NAME)

clean: ## Clean build artifacts
	mvn -q clean

ci: java-version install-client build test verify  ## What CI (codex) should run (TODO add security her after we get an NVD API Key )
