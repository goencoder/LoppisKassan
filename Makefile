# Makefile for LoppisKassan
SHELL := /bin/bash
.ONESHELL:
ROOT_DIR := $(shell pwd)

# ---- Local OpenAPI client ----
CLIENT_JAR := lib/openapi-java-client-0.0.4.jar
GROUP_ID   := se.goencoder.iloppis
ARTIFACT_ID:= iloppis-client
VERSION    := 0.0.4

JAR_NAME := target/LoppisKassan-v2.0.0-jar-with-dependencies.jar

.DEFAULT_GOAL := help
MAVEN ?= mvn
MFLAGS ?= -B -U
PROXY_HOST ?= proxy
PROXY_PORT ?= 8080
export MAVEN_OPTS += -Djava.net.preferIPv4Stack=true -Dhttp.proxyHost=$(PROXY_HOST) -Dhttp.proxyPort=$(PROXY_PORT) -Dhttps.proxyHost=$(PROXY_HOST) -Dhttps.proxyPort=$(PROXY_PORT) -Dhttp.nonProxyHosts=localhost\|127.0.0.1\|::1

proxy:
	mkdir -p ~/.m2
	cp $(ROOT_DIR)/scripts/maven-settings.xml ~/.m2/settings.xml

help: ## Show help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS=":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

java-version: ## Print java & maven versions
	java -version || true
	mvn -v || true

install-client: proxy
	$(MAVEN) $(MFLAGS) org.apache.maven.plugins:maven-install-plugin:install-file \
	  -Dfile=lib/openapi-java-client-0.0.4.jar \
	  -DpomFile=lib/openapi-java-client-0.0.4.pom

build: install-client ## Build fat jar (skip tests for speed)
	mvn -q -DskipTests package

build-codex: install-client
	$(MAVEN) $(MFLAGS) -DskipTests -Dexec.skip=true package

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
