# Issue 004: Offline-start med cachade evenemang

**Status:** Planering  
**Datum:** 2026-02-12  
**Prioritet:** Hög  
**Epic:** iLoppis-läge – offline resiliens  
**Scope:** Enbart LoppisKassan (Java desktop) — inga API/backend-ändringar  
**Breaking:** Nej — additiv funktionalitet, befintliga flöden oförändrade

> **Relation:** Bygger vidare på offline-hantering etablerad i [Issue 003](003-network-stability.md).  
> **Bakgrund:** Om kassan startar medan nätverket är nere eller backend är otillgänglig kan användaren inte öppna kassan – trots att all nödvändig data redan finns cachad från en tidigare session.

---

## 1. Nuläge

### 1.1 Vad som fungerar idag

| Scenario | Status | Mekanism |
|----------|--------|----------|
| Online → offline under session | ✅ Fungerar | `IloppisCashierStrategy` sparar lokalt, `BackgroundSyncManager` synkar var 30:e s |
| Online-start, server tillgänglig | ✅ Fungerar | `DiscoveryTabController.loadAllEvents()` hämtar via API |
| Offline-start, aldrig anslutit | ❌ Blockerad | `loadAllEvents()` failar, inga events i tabell, kassan kan inte öppnas |
| Offline-start, tidigare anslutit | ❌ Blockerad | Samma som ovan – API-data cachas INTE till disk |

### 1.2 Arkitektoniska luckor

**Ingen eventcache:** `DiscoveryTabController.loadAllEvents()` hämtar events via `EventServiceApi.eventServiceFilterEvents()` varje gång. Om anropet failar visas ett felmeddelande (`error.fetch_events.title`) och evenemangslistan förblir tom (förutom lokala events).

**API-nyckel cachas redan:** `ILoppisConfigurationStore.setApiKey()` sparar nyckeln till `config/iloppis-mode.json`. Vid omstart återställs nyckeln i `ApiHelper`-konstruktorn:
```java
if (ILoppisConfigurationStore.getApiKey() != null) {
    setCurrentApiKey(ILoppisConfigurationStore.getApiKey());
}
```

**Eventdata cachas delvis:** `ILoppisConfigurationStore.setEventData()` sparar det *aktuella* eventets JSON. Men detta används bara som fallback i `fromId()` – inte för att populera evenemangslistan.

**Godkända säljare cachas:** `ILoppisConfigurationStore.setApprovedSellers()` sparar en JSON-array med godkända säljarnummer efter `openRegister()`.

**Ingen proaktiv connectivity-check:** Appen detekterar offline-tillstånd *reaktivt* – först när ett API-anrop failar (`ApiException` med `code == 0`).

### 1.3 Befintlig filstruktur

```
config/
├── global.json             # Språkinställning
├── local-mode.json         # Lokal kassa: eventId, eventData, revenueSplit
└── iloppis-mode.json       # iLoppis: eventId, apiKey, apiBaseUrl,
                            #          approvedSellers, revenueSplit, eventData

~/.loppiskassan/
└── events/
    └── {eventId}/
        ├── metadata.json           # LocalEvent-metadata
        ├── pending_items.jsonl     # Ej uppladdade varor
        ├── sold_items.jsonl        # Uppladdade varor
        └── rejected_purchases.jsonl
```

### 1.4 Relevanta klasser (befintliga)

| Klass | Fil | Roll |
|-------|-----|------|
| `DiscoveryTabController` | `controller/DiscoveryTabController.java` | Hämtar events, öppnar kassan |
| `ILoppisConfigurationStore` | `config/ILoppisConfigurationStore.java` | Sparar API-nyckel, approved sellers, eventdata |
| `AppModeManager` | `config/AppModeManager.java` | Hanterar LOCAL/ILOPPIS-läge |
| `BackgroundSyncManager` | `service/BackgroundSyncManager.java` | Bakgrundssynk var 30:e s |
| `IloppisCashierStrategy` | `service/IloppisCashierStrategy.java` | Online-kassastrategi (upload + fallback) |
| `ApiHelper` | `rest/ApiHelper.java` | API-klient singleton, isLikelyNetworkError() |
| `LocalEventRepository` | `storage/LocalEventRepository.java` | CRUD för lokala events |
| `LocalEvent` | `storage/LocalEvent.java` | Domänmodell med JSON-serialisering |
| `LocalEventType` | `storage/LocalEventType.java` | Enum: `LOCAL`, `ONLINE` |
| `LocalEventPaths` | `storage/LocalEventPaths.java` | Sökvägar under `~/.loppiskassan/events/` |
| `AppShellStatusbar` | `ui/AppShellStatusbar.java` | Statusfält: 🟢/🟡/offline-indikator |

---

## 2. Mål

### 2.1 Primärt mål

Användaren ska kunna starta LoppisKassan och öppna kassan för ett iLoppis-evenemang **även om nätverket är nere** – förutsatt att kassan har öppnats för samma event tidigare (dvs. API-nyckel och eventdata finns cachade).

### 2.2 Krav

| # | Krav | Typ |
|---|------|-----|
| K1 | Vid lyckad `openRegister()` cachas all nödvändig data lokalt | Funktion |
| K2 | Vid offline-start visas cachade iLoppis-events i evenemangslistan | Funktion |
| K3 | Cachade events markeras med visuell indikator (⚠️ offline) | UX |
| K4 | Kassan kan öppnas utan nytt API-anrop om API-nyckel finns cachad | Funktion |
| K5 | Säljare valideras mot cachad lista (redan implementerat) | Befintlig |
| K6 | Försäljningar sparas lokalt och synkas automatiskt när online | Befintlig |
| K7 | Statusfältet visar tydligt att kassan körs i offline-läge | UX |
| K8 | När appen blir online igen refreshas eventdata automatiskt | Funktion |
| K9 | Cache har TTL – data äldre än 7 dagar varnar användaren | UX |
| K10 | Online ska alltid visa färsk data – cache får INTE skymma serversanningen | Kritiskt |

### 2.3 Icke-mål (utanför scope)

- Krypterad lagring av API-nycklar (framtida förbättring)
- "Pure offline"-kassa utan iLoppis-koppling (separat issue)
- Ändringar i backend/API

---

## 3. Teknisk design

### 3.1 Ny klass: `OnlineEventCache`

**Syfte:** Cachar iLoppis-events till disk så de kan visas vid offline-start.

**Fil:** `src/main/java/se/goencoder/loppiskassan/storage/OnlineEventCache.java`

```java
package se.goencoder.loppiskassan.storage;

import se.goencoder.iloppis.model.V1Event;
import java.util.List;

/**
 * Persistent cache for iLoppis online events.
 * Events are cached as LocalEvent entries with type ONLINE under ~/.loppiskassan/events/.
 * Used when the app starts offline to show previously discovered events.
 */
public class OnlineEventCache {

    private static final long CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L; // 7 dagar

    /**
     * Cache an online event after successful register opening.
     * Stores: event metadata, API key, approved sellers, revenue split.
     *
     * @param event       The V1Event from the API
     * @param apiKey      The exchanged API key
     * @param sellers     JSON string of approved sellers
     * @param split       Revenue split JSON
     */
    public static void cacheEvent(V1Event event, String apiKey,
                                  String sellers, String split) { ... }

    /**
     * Load all cached online events from disk.
     * Filters out entries older than CACHE_TTL_MS.
     * @return List of cached events, may be empty
     */
    public static List<CachedOnlineEvent> loadCachedEvents() { ... }

    /**
     * Remove a cached event (e.g., after server confirms deletion).
     */
    public static void removeCache(String eventId) { ... }

    /**
     * Check if a valid (non-expired) cache exists for the given event.
     */
    public static boolean hasCachedEvent(String eventId) { ... }
}
```

### 3.2 Ny datamodell: `CachedOnlineEvent`

**Fil:** `src/main/java/se/goencoder/loppiskassan/storage/CachedOnlineEvent.java`

```java
package se.goencoder.loppiskassan.storage;

import java.time.OffsetDateTime;

/**
 * Represents a cached iLoppis online event with associated credentials.
 * Stored as metadata.json under ~/.loppiskassan/events/{eventId}/.
 */
public class CachedOnlineEvent {
    private String eventId;
    private String eventName;
    private String description;
    private String addressStreet;
    private String addressCity;
    private String marketId;
    private String apiKey;              // Cachad API-nyckel
    private String approvedSellersJson; // "[1,2,3,...]"
    private String revenueSplitJson;    // Serialized V1RevenueSplit
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private OffsetDateTime cachedAt;    // Tidpunkt när cachen skapades

    // Getters, setters, JSON serialization (toJsonString / fromJsonString)

    /** Check if this cache entry is older than maxAge milliseconds. */
    public boolean isExpired(long maxAgeMs) {
        if (cachedAt == null) return true;
        long ageMs = System.currentTimeMillis() -
                     cachedAt.toInstant().toEpochMilli();
        return ageMs > maxAgeMs;
    }
}
```

### 3.3 Diskstruktur efter caching

```
~/.loppiskassan/
└── events/
    ├── local-abc123/                   # Lokalt event (LOCAL)
    │   ├── metadata.json
    │   └── ...
    └── d50e8356-8deb-428a-a588.../     # Cachat iLoppis-event (ONLINE)
        ├── metadata.json               # CachedOnlineEvent JSON
        ├── pending_items.jsonl          # Ej uppladdade (befintlig)
        ├── sold_items.jsonl             # Uppladdade (befintlig)
        └── rejected_purchases.jsonl
```

**Notera:** `LocalEventType.ONLINE` finns redan i enumen men används inte idag. Denna issue ger den ett syfte.

### 3.4 Ny klass: `ConnectivityChecker`

**Fil:** `src/main/java/se/goencoder/loppiskassan/rest/ConnectivityChecker.java`

```java
package se.goencoder.loppiskassan.rest;

/**
 * Proactive connectivity check for iLoppis backend.
 * Uses a lightweight HEAD request with short timeout.
 */
public class ConnectivityChecker {

    private static final int CHECK_TIMEOUT_MS = 2000; // 2 sekunder
    private static volatile boolean lastKnownOnline = false;

    /**
     * Check if the backend is reachable.
     * Makes a lightweight HTTP request with 2s timeout.
     * Updates lastKnownOnline state.
     *
     * @return true if server responds within timeout
     */
    public static boolean isOnline() { ... }

    /**
     * Get the last known connectivity state without making a new request.
     */
    public static boolean isLastKnownOnline() {
        return lastKnownOnline;
    }
}
```

**Implementation:** Gör ett `GET /v1/events` (med `page_size=1`) mot backend med 2 sekunders timeout. Ingen autentisering krävs för event-listning.

### 3.5 Ändringar i `DiscoveryTabController.loadAllEvents()`

Nuvarande flow:
```
loadAllEvents()
├── LocalEventRepository.loadAll()     → lokala events ✅
└── EventServiceApi.filterEvents()     → online events (FAIL om offline) ❌
```

Ny flow:
```
loadAllEvents()
├── LocalEventRepository.loadAll()         → lokala events ✅
├── ConnectivityChecker.isOnline()?
│   ├── JA: EventServiceApi.filterEvents() → online events ✅
│   │       OnlineEventCache.updateCache() → uppdatera disk
│   └── NEJ: OnlineEventCache.loadCachedEvents() → cachade events ⚠️
│            state.setOfflineMode(true)
└── Kombinera allt i eventList
```

**Pseudokod:**

```java
private void loadAllEvents() {
    List<V1Event> newEventList = new ArrayList<>();
    Map<String, LocalEvent> newLocalEventMap = new HashMap<>();

    // 1. Lokala events (alltid tillgängliga)
    try {
        List<LocalEvent> localEvents = LocalEventRepository.loadAll();
        for (LocalEvent le : localEvents) {
            if (le.getEventType() == LocalEventType.LOCAL) {
                newEventList.add(toLocalV1Event(le));
                newLocalEventMap.put(le.getEventId(), le);
            }
            // ONLINE-typ hanteras separat nedan
        }
    } catch (IOException e) {
        Popup.ERROR.showAndWait(...);
    }

    // 2. Online events
    boolean online = ConnectivityChecker.isOnline();
    state.setOfflineMode(!online);

    if (online) {
        // Hämta färsk data från server
        try {
            V1FilterEventsResponse response = ...;
            newEventList.addAll(response.getEvents());
            // Uppdatera existerande cacher med färsk data
            // (eventnamn, beskrivning etc kan ha ändrats)
            OnlineEventCache.refreshCaches(response.getEvents());
        } catch (ApiException ex) {
            // Fallback till cache vid API-fel trots nätverksanslutning
            loadCachedOnlineEvents(newEventList);
            state.setOfflineMode(true);
        }
    } else {
        // Offline: ladda cachade iLoppis-events
        loadCachedOnlineEvents(newEventList);
    }

    eventList = newEventList;
    localEventMap = newLocalEventMap;
    state.setEvents(newEventList);
    EDT.run(() -> view.populateEventsTable(eventList));
}

private void loadCachedOnlineEvents(List<V1Event> targetList) {
    List<CachedOnlineEvent> cached = OnlineEventCache.loadCachedEvents();
    for (CachedOnlineEvent c : cached) {
        V1Event event = c.toV1Event();
        targetList.add(event);
    }
}
```

### 3.6 Ändringar i `DiscoveryTabController.openRegister()`

#### 3.6.1 Cache vid lyckad online-öppning (ny kod)

Direkt efter lyckad `configureOnlineMode()` – spara allt som behövs för offline-start:

```java
// I configureOnlineMode(), efter framgångsrikt API-nyckelutbyte:
OnlineEventCache.cacheEvent(
    event,
    response.getApiKey(),
    ILoppisConfigurationStore.getApprovedSellers(),
    ILoppisConfigurationStore.getRevenueSplit()
);
```

#### 3.6.2 Offline register-öppning (ny branch)

Om `ConnectivityChecker.isOnline()` är `false` och event-ID matchar ett cachat event:

```java
private void configureOnlineMode(String eventId, String cashierCode,
                                 V1Event event, V1RevenueSplit split) {
    if (!ConnectivityChecker.isOnline()) {
        // Försök med cachad data
        if (OnlineEventCache.hasCachedEvent(eventId)) {
            CachedOnlineEvent cached = OnlineEventCache.loadCachedEvent(eventId);

            // Kontrollera cache-ålder
            if (cached.isExpired(OnlineEventCache.CACHE_TTL_MS)) {
                boolean proceed = Popup.CONFIRM.showConfirmDialog(
                    LocalizationManager.tr("offline.cache_expired.title"),
                    LocalizationManager.tr("offline.cache_expired.message",
                        cached.getCacheAgeDescription()));
                if (!proceed) return;
            }

            // Återställ API-nyckel från cache
            ApiHelper.INSTANCE.setCurrentApiKey(cached.getApiKey());
            ILoppisConfigurationStore.setApiKey(cached.getApiKey());
            ILoppisConfigurationStore.setApprovedSellers(cached.getApprovedSellersJson());
            AppModeManager.setEventId(eventId);

            // Visa varning
            Popup.WARNING.showAndWait(
                LocalizationManager.tr("offline.register_opened.title"),
                LocalizationManager.tr("offline.register_opened.message"));

            view.setCashierButtonEnabled(false);
            view.clearCashierCodeField();
            view.setRegisterOpened(true);
            view.showActiveEventInfo(event, split);
            view.setChangeEventButtonVisible(true);

            // Starta BackgroundSyncManager direkt
            BackgroundSyncManager.getInstance().start(eventId);
            return;
        } else {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("offline.no_cache.title"),
                LocalizationManager.tr("offline.no_cache.message"));
            return;
        }
    }

    // ... befintlig online-logik (oförändrad)
}
```

**Notera:** Kassakoden ignoreras vid offline-start – den behövs inte eftersom API-nyckeln redan finns cachad. UI:t ska dölja kassakodsfältet eller visa det som inaktiverat med tooltip "Kassakod behövs ej – cachad session".

### 3.7 Ändringar i `DiscoveryState`

Lägg till:

```java
private boolean offlineMode = false;

public boolean isOfflineMode() { return offlineMode; }
public void setOfflineMode(boolean offline) {
    boolean old = this.offlineMode;
    this.offlineMode = offline;
    firePropertyChange("offlineMode", old, offline);
}
```

### 3.8 Ändringar i `AppShellStatusbar`

Ny status-indikator för offline-start:

```java
public void setOfflineStartStatus() {
    statusLabel.setText("🟠 " + LocalizationManager.tr("status.offline_start"));
    // Orange = offline men med cachad data
}
```

### 3.9 Ändringar i `DiscoveryTabPanel`

Visa visuell skillnad för cachade events:

```java
// I cellRenderer för evenemangstabellen:
if (isCachedOnlineEvent(event)) {
    // Visa ⚠️ ikon + "(offline)" suffix
    setText("⚠️ " + event.getName() + " (offline)");
    setToolTipText(LocalizationManager.tr("offline.cached_event.tooltip",
        cachedEvent.getCacheAgeDescription()));
}
```

Dölj eller inaktivera kassakodsfältet om offline och cache finns:

```java
if (state.isOfflineMode() && OnlineEventCache.hasCachedEvent(selectedEventId)) {
    cashierCodeField.setEnabled(false);
    cashierCodeField.setToolTipText(
        LocalizationManager.tr("offline.code_not_needed.tooltip"));
}
```

---

## 4. Dataflöde

### 4.1 Online-start (oförändrat + caching)

```
┌─────────┐    ┌────────────────┐    ┌─────────────┐
│ Startup │───▶│ loadAllEvents()│───▶│  API: GET    │
│         │    │                │    │  /v1/events  │
└─────────┘    └────────────────┘    └──────┬──────┘
                                            │
                    ┌───────────────────────┤
                    ▼                       ▼
             ┌──────────────┐    ┌──────────────────┐
             │ Visa i UI    │    │ OnlineEventCache  │
             │ (tabell)     │    │ .refreshCaches()  │ ◄── NY
             └──────────────┘    └──────────────────┘

┌──────────────┐    ┌────────────────────┐    ┌─────────────────┐
│ openRegister │───▶│ API: exchange code │───▶│ Cache event:     │
│ (kassakod)   │    │ → API-nyckel       │    │ metadata + key   │ ◄── NY
└──────────────┘    └────────────────────┘    │ + sellers +split │
                                              └─────────────────┘
```

### 4.2 Offline-start (ny path)

```
┌─────────┐    ┌────────────────┐    ┌──────────────────┐
│ Startup │───▶│ loadAllEvents()│───▶│ ConnectivityCheck │ ◄── NY
│         │    │                │    │ isOnline()→ false │
└─────────┘    └────────────────┘    └────────┬─────────┘
                                              │
                    ┌─────────────────────────┘
                    ▼
             ┌────────────────────┐
             │ OnlineEventCache   │ ◄── NY
             │ .loadCachedEvents()│
             └────────┬───────────┘
                      │
                      ▼
             ┌──────────────────────────────┐
             │ UI: Events med ⚠️-markering  │ ◄── NY
             │ Statusbar: 🟠 Offline-läge   │
             └──────────────┬───────────────┘
                            │ Användaren väljer event
                            ▼
             ┌──────────────────────────────┐
             │ openRegister()               │
             │ → Offline branch:            │ ◄── NY
             │   - Ladda cachad API-nyckel  │
             │   - Ladda cachade säljare    │
             │   - Visa varningsdialog      │
             │   - Öppna kassa              │
             └──────────────┬───────────────┘
                            │
                            ▼
             ┌──────────────────────────────┐
             │ Kassa öppen: offline-läge    │
             │ Försäljningar → lokal JSONL  │
             │ BackgroundSyncManager aktiv  │
             │ → auto-retry var 30s         │
             └──────────────────────────────┘
```

### 4.3 Online-återanslutning

```
┌──────────────────┐    ┌────────────────────────────────┐
│ BackgroundSync    │───▶│ Upload pending items:          │
│ triggar var 30s   │    │ → Lyckades!                   │
│                   │    │ → Updatera pending count      │
└──────────────────┘    │ → Toast: "✓ Synkroniserad"    │
                        └────────────────────────────────┘

┌──────────────────┐    ┌────────────────────────────────┐
│ Auto-refresh      │───▶│ loadAllEvents():               │
│ var 60s           │    │ → ConnectivityChecker: online! │
│                   │    │ → Hämta färsk eventdata       │
│                   │    │ → Uppdatera cache + UI        │
│                   │    │ → state.setOfflineMode(false) │
│                   │    │ → Statusbar: 🟢 Online        │
└──────────────────┘    └────────────────────────────────┘
```

---

## 5. Duplikat-säkerhet

Ingen risk för dubbletter vid sync tack vare klientens ID-generering:

- **`itemId`:** Genereras med `UlidGenerator.generate()` – 26-tecken ULID med tidsstämpel + slump
- **`purchaseId`:** Samma ULID-generering
- Backend har unikt index på `(event_id, item_id)` – avvisar dubbletter med `DUPLICATE_RECEIPT`
- `BackgroundSyncManager` kan tryggt re-posta samma batch – servern deduplicerar

---

## 6. Implementationsplan

### Fas 1: Grundläggande cache-infrastruktur

| # | Uppgift | Fil(er) | Beroenden |
|---|---------|---------|-----------|
| 1.1 | Skapa `CachedOnlineEvent` med JSON-serialisering | `storage/CachedOnlineEvent.java` | – |
| 1.2 | Skapa `OnlineEventCache` (cacheEvent, loadCachedEvents, removeCache, hasCachedEvent) | `storage/OnlineEventCache.java` | 1.1 |
| 1.3 | Unit-tester för cache-klasser | `test/.../storage/OnlineEventCacheTest.java` | 1.1, 1.2 |

### Fas 2: Connectivity check

| # | Uppgift | Fil(er) | Beroenden |
|---|---------|---------|-----------|
| 2.1 | Skapa `ConnectivityChecker` med timeout-baserad HTTP-check | `rest/ConnectivityChecker.java` | – |
| 2.2 | Unit-test med mocked HTTP-klient | `test/.../rest/ConnectivityCheckerTest.java` | 2.1 |

### Fas 3: Integration i DiscoveryTabController

| # | Uppgift | Fil(er) | Beroenden |
|---|---------|---------|-----------|
| 3.1 | Modifiera `loadAllEvents()`: connectivity-check + cache fallback | `controller/DiscoveryTabController.java` | 1.2, 2.1 |
| 3.2 | Modifiera `configureOnlineMode()`: cacha vid lyckad öppning | `controller/DiscoveryTabController.java` | 1.2 |
| 3.3 | Modifiera `configureOnlineMode()`: offline-branch med cachad data | `controller/DiscoveryTabController.java` | 1.2, 2.1 |
| 3.4 | Lägg till `offlineMode` i `DiscoveryState` | `model/discovery/DiscoveryState.java` | – |

### Fas 4: UI-ändringar

| # | Uppgift | Fil(er) | Beroenden |
|---|---------|---------|-----------|
| 4.1 | Offline-indikator i statusfältet: 🟠 | `ui/AppShellStatusbar.java` | 3.4 |
| 4.2 | ⚠️-markering på cachade events i evenemangstabellen | `ui/DiscoveryTabPanel.java` | 3.1 |
| 4.3 | Dölj/inaktivera kassakodsfält vid offline + cache | `ui/DiscoveryTabPanel.java` | 3.4 |
| 4.4 | Varningsdialoger: offline-start, expired cache | `ui/DiscoveryTabPanel.java` | 3.3 |

### Fas 5: Lokalisering

| # | Uppgift | Fil(er) | Beroenden |
|---|---------|---------|-----------|
| 5.1 | Lägg till svenska strängar | `resources/lang/sv.json` | 4.* |
| 5.2 | Lägg till engelska strängar | `resources/lang/en.json` | 4.* |

---

## 7. Nya lokaliseringsnycklar

### 7.1 Svenska (`sv.json`)

```json
{
  "status.offline_start": "Offline-läge – använder cachad data",
  "status.offline_reconnected": "Ansluten igen – synkroniserar...",

  "offline.cached_event.tooltip": "Cachad data (senast uppdaterad {0})",
  "offline.code_not_needed.tooltip": "Kassakoden behövs ej i offline-läge – cachad session",

  "offline.register_opened.title": "Kassa öppnad (offline)",
  "offline.register_opened.message": "Kassan har öppnats med cachad data.\n\n⚠️ Observera:\n• Säljarlistan kan vara föråldrad\n• Evenemangsinformation kan ha ändrats\n• Försäljningar sparas lokalt och synkas automatiskt\n  när anslutningen återupprättas",

  "offline.cache_expired.title": "Gammal cachad data",
  "offline.cache_expired.message": "Den cachade datan är {0} gammal.\nSäljarlistan och evenemangsinformation kan vara föråldrade.\n\nVill du fortsätta ändå?",

  "offline.no_cache.title": "Ingen cachad data",
  "offline.no_cache.message": "Det finns ingen cachad data för detta evenemang.\nDu måste vara online för att öppna kassan första gången.",

  "offline.cache_age.hours": "{0} timmar",
  "offline.cache_age.days": "{0} dagar"
}
```

### 7.2 Engelska (`en.json`)

```json
{
  "status.offline_start": "Offline mode – using cached data",
  "status.offline_reconnected": "Reconnected – syncing...",

  "offline.cached_event.tooltip": "Cached data (last updated {0})",
  "offline.code_not_needed.tooltip": "Cashier code not needed in offline mode – cached session",

  "offline.register_opened.title": "Register opened (offline)",
  "offline.register_opened.message": "The register has been opened with cached data.\n\n⚠️ Note:\n• The seller list may be outdated\n• Event information may have changed\n• Sales are saved locally and will sync automatically\n  when connectivity is restored",

  "offline.cache_expired.title": "Outdated cached data",
  "offline.cache_expired.message": "The cached data is {0} old.\nThe seller list and event information may be outdated.\n\nDo you want to continue anyway?",

  "offline.no_cache.title": "No cached data",
  "offline.no_cache.message": "There is no cached data for this event.\nYou must be online to open the register for the first time.",

  "offline.cache_age.hours": "{0} hours",
  "offline.cache_age.days": "{0} days"
}
```

---

## 8. Testning

### 8.1 Unit-tester

| Test | Klass | Vad som verifieras |
|------|-------|--------------------|
| `testCacheEventCreatesMetadata` | `OnlineEventCacheTest` | `cacheEvent()` skapar `metadata.json` med rätt innehåll |
| `testLoadCachedEventsFiltersExpired` | `OnlineEventCacheTest` | `loadCachedEvents()` filtrerar bort events äldre än TTL |
| `testLoadCachedEventsReturnsNonExpired` | `OnlineEventCacheTest` | Events inom TTL returneras korrekt |
| `testRemoveCacheDeletesFiles` | `OnlineEventCacheTest` | `removeCache()` tar bort eventmappen |
| `testHasCachedEventReturnsTrueWhenCached` | `OnlineEventCacheTest` | `hasCachedEvent()` returnerar true för cachat event |
| `testHasCachedEventReturnsFalseWhenNotCached` | `OnlineEventCacheTest` | `hasCachedEvent()` returnerar false för okänt event |
| `testCachedOnlineEventSerialization` | `CachedOnlineEventTest` | JSON round-trip: `toJsonString()` → `fromJsonString()` bevarar alla fält |
| `testCachedOnlineEventIsExpired` | `CachedOnlineEventTest` | `isExpired()` returnerar true/false baserat på ålder |
| `testConnectivityCheckerOnline` | `ConnectivityCheckerTest` | Returnerar true när server svarar |
| `testConnectivityCheckerOffline` | `ConnectivityCheckerTest` | Returnerar false vid timeout/connection refused |

### 8.2 Integrationstester

| Test | Beskrivning | Förutsättningar |
|------|-------------|-----------------|
| `testOfflineStartWithCachedEvent` | 1. Öppna kassa online (skapar cache). 2. Stäng. 3. Starta offline. 4. Verifiera att cachat event visas. 5. Öppna kassa. 6. Registrera försäljning. 7. Starta online. 8. Verifiera synk. | `BackgroundSyncManager` + mock API |
| `testOnlineStartRefreshesCache` | 1. Cacha event med namn "Gammalt namn". 2. Starta online med servern returnerar "Nytt namn". 3. Verifiera att UI visar "Nytt namn". 4. Verifiera att cachen uppdaterats. | Mock API |
| `testExpiredCacheWarnsUser` | 1. Cacha event med `cachedAt` 8 dagar tillbaka. 2. Starta offline. 3. Försök öppna kassa. 4. Verifiera att varningsdialog visas. | Cachad fil med gammal timestamp |
| `testCacheNotUsedWhenOnline` | 1. Cacha event. 2. Starta online. 3. Verifiera att API-data visas, inte cachad data. | Mock API med annorlunda data |
| `testNoCacheShowsErrorOnOfflineStart` | 1. Rensa all cache. 2. Starta offline. 3. Verifiera att evenemangslistan är tom (förutom lokala). 4. Försök öppna kassa → felmeddelande. | Ingen cache |

### 8.3 Manuell testmetod

#### Scenario A: Grundläggande offline-start

1. **Förberedelse:**
   - Starta backend + LoppisKassan
   - Välj ett iLoppis-event, ange kassakod, öppna kassan
   - Registrera minst 1 försäljning (verifiera att den synkas)
   - Stäng LoppisKassan

2. **Test:**
   - Stoppa backend (`pkill -f "bin/service"`)
   - Starta LoppisKassan
   - **Förväntat:** Event syns i listan med ⚠️ offline-markering
   - **Förväntat:** Kassakodsfältet är grått/dolt
   - **Förväntat:** Statusfält visar 🟠 "Offline-läge – använder cachad data"
   - Välj det cachade eventet, tryck "Öppna kassa"
   - **Förväntat:** Varningsdialog om offline-läge
   - Acceptera → kassan öppnas
   - Registrera 3 försäljningar (blandade säljarnummer)
   - **Förväntat:** Försäljningar sparas lokalt utan fel

3. **Återanslutning:**
   - Starta backend igen (`make dev-backend`)
   - Vänta 30-60 sekunder
   - **Förväntat:** Statusfält ändras till 🟢 "Ansluten till iLoppis"
   - **Förväntat:** Pending count minskar till 0
   - Verifiera i backend: `curl ... /sold-items:aggregate`

#### Scenario B: Expired cache

1. Öppna `~/.loppiskassan/events/{eventId}/metadata.json`
2. Ändra `cachedAt` till 8 dagar tillbaka
3. Stoppa backend, starta LoppisKassan
4. Välj eventet, tryck "Öppna kassa"
5. **Förväntat:** Dialog: "Gammal cachad data – 8 dagar gammal. Fortsätt?"
6. Klicka "Ja" → kassan öppnas med varning

#### Scenario C: Inget cachat event

1. Rensa `~/.loppiskassan/events/` (ta bort alla UUID-mappar, behåll `local-*`)
2. Stoppa backend, starta LoppisKassan
3. **Förväntat:** Inga iLoppis-events i listan
4. **Förväntat:** Statusfält: 🟠 Offline-läge
5. Försök klicka "Öppna kassa" utan valt event
6. **Förväntat:** Felmeddelande "Välj en loppis i listan"

#### Scenario D: Online efter offline-start

1. Kör scenario A (offline-start, registrera försäljningar)
2. Starta backend
3. Vänta tills auto-refresh körs (60s)
4. **Förväntat:** ⚠️-markering försvinner
5. **Förväntat:** Evenemangsinformation uppdateras till serverns version
6. **Förväntat:** 🟠 → 🟢 i statusfältet

---

## 9. Edge cases och felhantering

| # | Scenario | Handling |
|---|----------|----------|
| E1 | API-nyckel har revokerats på servern | BackgroundSync får 401 → visa varning "API-nyckel ogiltig, ange ny kassakod" |
| E2 | Event har raderats på servern | loadAllEvents() returnerar inte eventet → ta bort det ur cache, visa info |
| E3 | Säljarstatus ändrad under offline | Cachad lista validerar → backend re-validerar vid sync → rejected items loggas |
| E4 | Disk full → kan inte skriva cache | `cacheEvent()` fångar `IOException` → logga varning, fortsätt utan cache |
| E5 | Korrupt metadata.json i cache | `fromJsonString()` fångar parse-fel → ignorera cachat event |
| E6 | Flera instanser av LoppisKassan | Varje instans har samma cache-sökväg → last-writer-wins (acceptabelt) |
| E7 | System-klocka ändrad (NTP-hopp) | `isExpired()` kan ge fel resultat → acceptabel risk |

---

## 10. Definitioner

| Term | Betydelse |
|------|-----------|
| **Cache** | Lokal kopia av serverdata under `~/.loppiskassan/events/{eventId}/metadata.json` |
| **TTL** | Time-To-Live: hur länge cachen anses giltig (7 dagar) |
| **Offline-start** | App startar utan nätverksanslutning till iLoppis-backend |
| **Cachad session** | Ett event som tidigare öppnats online och vars data finns lokalt |
| **BackgroundSync** | `BackgroundSyncManager` – synkar pending items var 30:e sekund |

---

## 11. Framtida förbättringar (utanför scope)

| Förbättring | Beskrivning |
|-------------|-------------|
| Krypterad API-nyckellagring | Använd OS Keychain (macOS) / Credential Manager (Windows) |
| Cache-invalidering från server | Push-meddelande om event ändrats → invalidera cache |
| Pure offline-kassa | Starta kassa utan iLoppis-koppling → manuell CSV-export |
| Konfigurerbar TTL | Inställning i UI för cache-livslängd |
| Multi-event cache | Cacha alla events, inte bara de som öppnats (lägre prio) |
