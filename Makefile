# Makefile for LoppisKassan
SHELL := /bin/bash
.ONESHELL:
ROOT_DIR := $(shell pwd)

# ---- Local OpenAPI client ----
CLIENT_JAR := lib/openapi-java-client-0.0.5.jar
GROUP_ID   := se.goencoder.iloppis
ARTIFACT_ID:= iloppis-client
VERSION    := 0.0.5

JAR_NAME := target/LoppisKassan-v2.0.0-jar-with-dependencies.jar

.DEFAULT_GOAL := help
MAVEN ?= mvn
MFLAGS ?= -B -U

# Detect if we're running in Codex by checking if /workspace exists
IN_CODEX := $(shell test -d /workspace && echo 1 || echo 0)

# Configure Maven options based on environment
ifeq ($(IN_CODEX),1)
    PROXY_HOST ?= proxy
    PROXY_PORT ?= 8080
    export MAVEN_OPTS += -Djava.net.preferIPv4Stack=true -Dhttp.proxyHost=$(PROXY_HOST) -Dhttp.proxyPort=$(PROXY_PORT) -Dhttps.proxyHost=$(PROXY_HOST) -Dhttps.proxyPort=$(PROXY_PORT) -Dhttp.nonProxyHosts=localhost\|127.0.0.1\|::1
    $(info Detected Codex environment, enabling proxy settings)
    MVN_PROXY_FLAGS :=
endif

proxy:
ifeq ($(IN_CODEX),1)
	mkdir -p ~/.m2
	cp $(ROOT_DIR)/scripts/maven-settings.xml ~/.m2/settings.xml
	@echo "Proxy settings configured for Codex environment"
else
	@echo "Skipping proxy configuration for local environment"
endif



install-client: proxy
	$(MAVEN) $(MFLAGS) $(MVN_PROXY_FLAGS) org.apache.maven.plugins:maven-install-plugin:install-file \
	  -Dfile=lib/openapi-java-client-0.0.5.jar \
	  -DpomFile=lib/openapi-java-client-0.0.5.pom

build-codex: install-client ## Build for Codex (no jpackage)
	$(MAVEN) $(MFLAGS) $(MVN_PROXY_FLAGS) -DskipTests package

load-test: install-client ## Run manual load test (ENV=path/to/env)
ifeq ($(strip $(ENV)),)
	$(error ENV file path required, e.g. make load-test ENV=./load-test-local.env)
endif
	set -a; source $(ENV); set +a; \
	$(MAVEN) $(MFLAGS) $(MVN_PROXY_FLAGS) -DskipTests test-compile; \
	$(MAVEN) $(MFLAGS) $(MVN_PROXY_FLAGS) -DskipTests \
	 -Dexec.mainClass=se.goencoder.loppiskassan.tools.LoadTestRunner \
	 -Dexec.classpathScope=test org.codehaus.mojo:exec-maven-plugin:3.5.0:java

setup-test: install-client ## Run market setup (creates market, event, vendors) (ENV=path/to/env)
ifeq ($(strip $(ENV)),)
	$(error ENV file path required, e.g. make setup-test ENV=./load-test-setup.env)
endif
	set -a; source $(ENV); set +a; \
	$(MAVEN) $(MFLAGS) $(MVN_PROXY_FLAGS) -DskipTests test-compile; \
	$(MAVEN) $(MFLAGS) $(MVN_PROXY_FLAGS) -DskipTests \
	 -Dexec.mainClass=se.goencoder.loppiskassan.tools.SetupRunner \
	 -Dexec.classpathScope=test org.codehaus.mojo:exec-maven-plugin:3.5.0:java

## Network chaos testing
toxiproxy-up: ## Start toxiproxy container
	docker-compose -f docker-compose.toxiproxy.yml up -d
	@sleep 2
	@./scripts/toxiproxy-setup.sh

toxiproxy-down: ## Stop toxiproxy container
	docker-compose -f docker-compose.toxiproxy.yml down

toxiproxy-scenario: ## Run network scenario (SCENARIO=slow-3g|unstable|high-latency|packet-loss|timeout|clear|list)
ifeq ($(strip $(SCENARIO)),)
	@./scripts/toxiproxy-scenarios.sh
else
	@./scripts/toxiproxy-scenarios.sh $(SCENARIO)
endif
