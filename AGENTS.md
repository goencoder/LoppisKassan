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

## Decision rule (NO ASSUMPTIONS)
- Always ask the user when information is missing or uncertain; never introduce defaults, fallbacks, or inferred values that could be wrong. If data is absent or unclear, fail fast and surface the ambiguity instead of guessing.

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

## UI Architecture (Model/View/Controller)
- Views are Swing panels implementing `*PanelInterface` in `se.goencoder.loppiskassan.ui`.
- Controllers live in `se.goencoder.loppiskassan.controller` and own logic, validation, and I/O.
- Views call controller methods on user actions; controllers update views through their interfaces.
- Keep business logic and storage out of UI classes; use services/interactors where available.

## UI Design System (Issue 003 – Modern UI Redesign)
- Use `AppButton` for all buttons. Do not create ad‑hoc `JButton` styles.
- Use `AppColors` for all colors. No raw hex values or named constants like `YELLOW`.
- Prefer cards (plain `JPanel` + rounded border + padding) over `TitledBorder`/GroupBox.
- Spacing tokens: `xs=4`, `sm=8`, `md=16`, `lg=24`, `xl=32` px.
- Typography: 20px titles, 16px section headers, 13–14px body, 11px help text, 28–36px totals.
- Modern light UI across the app. Avoid gray form fields or heavy borders.
- Date/time must use `SwedishDateFormatter` (never ISO strings in UI).
- Icons and custom painting should use `AppColors` and `RenderingHints` for crisp HiDPI output.
- MigLayout is optional and requires explicit approval (new dependency).

## Local vs iLoppis Modes (Behavioral Differences)
- Mode is driven by `AppModeManager` and the selected event.
- Local mode:
  - Events come from `LocalEventRepository` (disk, JSONL per event).
  - Register opens without cashier code.
  - Seller validation is skipped (all sellers accepted).
  - Sales persist to local JSONL immediately.
  - Export/Import and Archive views are visible.
  - Web sync actions are hidden/disabled.
- iLoppis mode:
  - Events come from backend API.
  - Register requires a valid cashier code.
  - Seller validation is enforced via API.
  - Sales upload to API (with local fallback on network errors).
  - Export/Import and Archive are hidden.

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
