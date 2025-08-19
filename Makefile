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
	  -Dfile=lib/openapi-java-client-0.0.4.jar \
	  -DpomFile=lib/openapi-java-client-0.0.4.pom

build-codex: install-client ## Build for Codex (no jpackage)
	$(MAVEN) $(MFLAGS) $(MVN_PROXY_FLAGS) -DskipTests package
