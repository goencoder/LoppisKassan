# iLoppis – Agent Guide (Java desktop app)

## How to build & test in Codex (Linux/headless)
- Use: `make ci` (runs `make build-codex && mvn test && mvn verify`)
- Do NOT run: `make run` (headless) or macOS packaging.
- Do NOT call `jpackage` unless on macOS with `-P mac-installer`.

## Local macOS packaging
- `mvn -P mac-installer -DskipTests package` produces a DMG via jpackage.

## Dependencies
- Install local API client from `lib/openapi-java-client-0.0.4.jar` using the sidecar POM:
  `make install-client`

## Architecture
This is a Java Swing desktop application for managing a flea market cash register system.
- Uses OpenAPI client to communicate with iLoppis service
- Requires Java 21+ with preview features enabled
- GUI components will fail in headless environments (Codex/CI)

## Environment Detection
The Makefile automatically detects Codex vs local environment:
- Codex: Uses proxy settings, skips GUI operations
- Local: Direct connections, full functionality including DMG packaging on macOS
