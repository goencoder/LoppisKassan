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
- Install local API client from `lib/openapi-java-client-0.0.6.jar` using the sidecar POM:
  `make install-client`
- **Version update:** Makefile declares `VERSION := 0.0.5` (line 10) but `install-client` target installs `0.0.6` (lines 43-44). The POM correctly references `0.0.6`. This is a known inconsistency—always use the version in `pom.xml` and `install-client` as source of truth.

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
  - **Offline resilience:** `OnlineEventCache` stores event metadata + API credentials for offline start; `BackgroundSyncManager` retries failed uploads every 30 seconds.

## Network Resilience & Persistence
- **Strategy:** Local-first with background upload. `IloppisCashierStrategy` calls `BackgroundSyncManager.enqueueItems()`, which writes items to `pending_items.jsonl` immediately (blocking on disk write), then triggers background upload worker.
- **Background sync:** `BackgroundSyncManager` runs on a single-threaded executor. Sales are queued in-memory, flushed to disk via `flushQueueToDisk()`, then uploaded every 30 seconds (`SYNC_INTERVAL_MS`). On successful upload, items are marked `uploaded=true`.
- **JSONL format:** Each line in `pending_items.jsonl` is a JSON object (via `JsonlHelper`). Fields: `itemId`, `purchaseId`, `seller`, `price`, `paymentMethod`, `soldTime`, `paidOutTime`, `uploaded`. Items with `uploaded=false` are retried on next sync cycle.
- **Duplicate prevention:** Client generates ULIDs for `itemId` and `purchaseId`; backend deduplicates via unique index on `(event_id, item_id)`, returning `DUPLICATE_RECEIPT` error code for idempotent retries.
- **Rejected items:** API rejections (e.g., `INVALID_SELLER`) are logged to `rejected_items.jsonl` via `RejectedItemsHelper` and can be edited/retried via UI dialogs (`RejectedItemEditDialog`).
- **Connectivity checks:** `ConnectivityChecker` probes API health with lightweight requests; status reflected in UI via `AppShellStatusbar`.
- **Single-writer guarantee:** All file I/O and API uploads run on the sync thread to prevent race conditions. The UI never writes to `pending_items.jsonl` directly.
- **Chaos testing:** Use `make toxiproxy-up` and `make toxiproxy-scenario SCENARIO=<name>` (e.g., `slow-3g`, `unstable`, `timeout`) to simulate network conditions. See `docs/technical/NETWORK_CHAOS.md` and `docs/technical/PERSISTENCE_STRATEGY_COMPARISON.md` for details.

## API Client & Authentication (CRITICAL)

### Architecture: OkHttp Interceptor Pattern
Authentication is handled **implicitly** via an OkHttp `Interceptor` — **no code should manually set Authorization headers**.

- **`AuthInterceptor`** (package-private, `rest/AuthInterceptor.java`): Reads `ApiHelper.INSTANCE.getCurrentApiKey()` at request time and injects `Authorization: Bearer <key>`. Skips if key is null/blank or if the request already has an Authorization header.
- **`FixedApiClient`** adds `AuthInterceptor` (and `HttpLoggingInterceptor`) to the shared OkHttpClient chain in its constructor.
- **`ApiHelper`** holds the single source of truth: `volatile String currentApiKey`. Simple `setCurrentApiKey()` / `clearCurrentApiKey()` / `getCurrentApiKey()` — no header manipulation at all.

### Flow
1. User enters cashier code → `AuthErrorHandler` calls `ApiHelper.INSTANCE.setCurrentApiKey(key)`.
2. Any subsequent API call (service API or raw OkHttp) → `AuthInterceptor` reads the volatile field and injects the header.
3. On 401 → `AuthErrorHandler` calls `clearCurrentApiKey()`, prompts re-auth, then `setCurrentApiKey(newKey)`.

### Rules
- **All API calls in production code MUST use `ApiHelper.INSTANCE`** — never create standalone `FixedApiClient` or `ApiClient` instances.
- **Never manually set `Authorization` headers** — the interceptor handles this.
- `ILoppisConfigurationStore.getApiKey()` is only for **persistence** (disk cache). Never read it as the source of truth for the current API key at call time.
- For raw OkHttp requests (e.g., heartbeats), use `ApiHelper.INSTANCE.getHttpClient()` and `ApiHelper.INSTANCE.getBasePath()` — the shared client already has the interceptor installed.

### Available Service APIs
Obtain API instances via `ApiHelper.INSTANCE`:
- `getSoldItemsServiceApi()` — sales CRUD
- `getApiKeyServiceApi()` — code exchange
- `getEventServiceApi()` — event metadata
- `getVendorServiceApi()` — vendor lookup
- `getApprovedMarketServiceApi()` — market data
- `getStatsServiceApi()` — live stats

### Exception: Test Tooling
`LoadTestRunner` and `SetupRunner` (under `src/test/`) create their own clients — this is acceptable since they run standalone with explicit credentials.

```java
// ✅ CORRECT — use service API (auth injected automatically)
StatsServiceApi stats = ApiHelper.INSTANCE.getStatsServiceApi();
stats.statsServiceGetEventLiveOpsStats(eventId);

// ✅ CORRECT — raw OkHttp with shared client (interceptor injects auth)
OkHttpClient client = ApiHelper.INSTANCE.getHttpClient();
Request request = new Request.Builder().url(ApiHelper.INSTANCE.getBasePath() + "/v1/...").build();
client.newCall(request).execute();

// ❌ WRONG — manually setting auth header (interceptor already does this)
request.addHeader("Authorization", "Bearer " + apiKey);

// ❌ WRONG — standalone client bypasses interceptor and loses auth after re-auth
FixedApiClient client = new FixedApiClient();
client.addDefaultHeader("Authorization", "Bearer " + ILoppisConfigurationStore.getApiKey());
```

## Configuration
- **Three-tier configuration system:**
  - `GlobalConfigurationStore`: Application-wide settings (UI language, window size) persisted across mode switches. Stored in `~/.loppiskassan/config/global.json`.
  - `LocalConfigurationStore`: Local mode settings (event ID). Stored in `~/.loppiskassan/config/local-mode.json`.
  - `ILoppisConfigurationStore`: iLoppis mode settings (event ID, API key, API base URL, cached sellers/revenue split). Stored in `~/.loppiskassan/config/iloppis-mode.json`.
- **Usage:** `AppModeManager.getEventId()` / `setEventId()` delegates to the correct store based on current mode.
- Use: `GlobalConfigurationStore.getLanguage()` as the single source of truth for UI language (defaults to `"sv"`).
- Always update both memory and disk on change via store's `set*()` methods.
- **API URL selection (staging vs production):** `ILoppisConfigurationStore.getApiBaseUrl()` checks (in order): 1) `ILOPPIS_API_URL` env var, 2) `apiBaseUrl` field in `iloppis-mode.json`, 3) defaults to `https://iloppis-staging.fly.dev`. Set env var for quick switching: `ILOPPIS_API_URL=https://iloppis.fly.dev java -jar ...`

## Internationalization

### Critical Rules
- **All UI text must come from `LocalizationManager.tr("key")`** — never hardcode strings.
- **Never use `String.format()` with localized text** — always pass parameters directly to `tr()`.
- Keys live under `src/main/resources/lang/{sv,en}.json`.
- Do not hardcode text in Swing components.

### Parameter Formatting (MessageFormat)
`LocalizationManager.tr()` uses Java's `MessageFormat.format()` internally:

**✅ CORRECT - Pass parameters to tr():**
```java
LocalizationManager.tr("discovery.delete.confirm", eventName)
LocalizationManager.tr("history.summary.items", itemCount, totalAmount)
```

**❌ WRONG - Using String.format():**
```java
// WRONG! This will show literal {0} in the UI
String.format(LocalizationManager.tr("discovery.delete.confirm"), eventName)
```

**Language file format:**
```json
{
  "discovery.delete.confirm": "Är du säker på att du vill radera ''{0}''?",
  "history.summary.items": "{0} varor sålda för totalt {1} SEK"
}
```

**Parameter placeholders:**
- Use `{0}`, `{1}`, `{2}`, etc. (NOT `%s`, `%d`, etc.)
- MessageFormat handles all formatting automatically
- Supports multiple parameters in any order

**⚠️ CRITICAL: Single Quote Escaping in MessageFormat**
MessageFormat uses single quotes (`'`) to escape text. This is the most common localization bug:

```json
// ❌ WRONG - Single quotes prevent parameter substitution
"message": "Delete '{0}'?"     // Shows literal: Delete {0}?

// ✅ CORRECT - Escape single quotes with double single-quotes
"message": "Delete ''{0}''?"   // Shows: Delete 'EventName'?

// ✅ ALSO CORRECT - No quotes around parameter
"message": "Delete {0}?"       // Shows: Delete EventName?
```

**Rule:** If you want to show a single quote (`'`) in the formatted message, write it as `''` (two single quotes).

### Language Selector
- Show flags and labels
- Persist selected language via `ConfigurationStore`
- Update UI immediately on change via `LocalizationManager.setLanguage()`

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
