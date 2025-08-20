# iLoppis – Agent Guide (Java desktop app)

## How to build & test in Codex (Linux/headless)
- Use: `make ci` (runs `make build-codex && mvn test && mvn verify`)
- Do NOT run: `make run` (headless) or macOS packaging.
- Do NOT call `jpackage` unless on macOS with `-P mac-installer`.

## Local macOS packaging
- `mvn -P mac-installer -DskipTests package` produces a DMG via jpackage.

## JDK
- Target: Java 21 (Temurin).
- No JPMS modules; plain classpath build.
- Build tool: Maven 3.9+.

## Dependencies
- Install local API client from `lib/openapi-java-client-0.0.4.jar` using the sidecar POM:
  `make install-client`

## Architecture
This is a Java Swing desktop application for managing a flea market cash register system.
- Uses OpenAPI client to communicate with iLoppis service.
- GUI is written in Swing only (no JavaFX).
- Requires Java 21.
- GUI components will fail in headless environments (Codex/CI).

## UI Rules
- UI: Swing only (no JavaFX).
- Package: all UI code lives under `se.goencoder.loppiskassan.ui`.
- Always include `import` statements (full FQCNs).
- Follow existing style: one `JPanel` per view component.
- High-DPI friendly rendering (use `RenderingHints` for quality scaling).
- Cashier flow must stay intact:
  1. Cursor starts in seller number field.
  2. Tab or Enter moves to price field.
  3. Enter submits prices and resets fields.
  4. Cursor returns to seller number field.

## Configuration
- Persist UI language and other settings using `ConfigurationStore`.
- Use: `ConfigurationStore.UI_LANGUAGE_STR.getOrDefault("sv")` as the single source of truth.
- Always update both memory and `config.properties` on change.

## Internationalization
- All UI text must come from `LocalizationManager.tr("key")`.
- Keys live under `src/main/resources/lang/{sv,en}.json`.
- Do not hardcode text in Swing components.
- Language selector must:
  - Show flags and labels.
  - Persist selected language.
  - Update UI immediately on change.

## Environment Detection
The Makefile automatically detects Codex vs local environment:
- CI/Codex: Never try to run Swing UI (`make ci` runs headless).
- Local macOS: DMG packaging is allowed (`-P mac-installer`).
- Never call `jpackage` in Codex/CI.

## Codex Prompt Scaffold
When generating code:
- Language: Java 21, Swing UI.
- Project: iLoppis Cash Register desktop app.
- Constraints:
  - Must compile with `mvn verify`.
  - No new external dependencies unless explicitly requested.
  - Persist settings via `ConfigurationStore`.
  - Use `LocalizationManager.tr` for text.
  - Keep cashier keystroke flow intact.
- Deliverables:
  1. Exact diffs with file paths under `src/main/java` or `src/main/resources`.
  2. Full `import` statements.
  3. Assume Maven project structure.
