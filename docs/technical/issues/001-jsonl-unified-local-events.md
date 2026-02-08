# Issue 001: JSONL-baserad lagring, lokala events och bulk-upload

**Status:** Design  
**Skapad:** 2026-02-08  
**Uppdaterad:** 2026-02-09  
**Prioritet:** Hög  
**Epic:** Kassasystem refaktorering

---

## 1. Nuläge – vad som redan fungerar

### 1.1 CSV-baserad multi-kassör (befintligt stöd)

LoppisKassan har **redan idag stöd för flera kassor** vid samma loppis. Varje instans av LoppisKassan skriver till en egen `loppiskassan.csv`. Efter loppisen importerar man de andra kassornas CSV-filer till en "huvudkassa".

**Så fungerar det idag (ur manualen):**
> *"Om man har en stor loppis kan det vara bra att ha flera kassor öppna för att minska köbildning vid kassan. Man kan efter loppisen välja ut en av kassorna som huvudkassa och importera alla köp från de andra kassorna till huvudkassan."*
> — `docs/manual_v2.md`, avsnitt "Importera kassa"

**Teknisk implementation:**
- `HistoryTabController.importLocalCashRegister()` — öppnar JFileChooser, läser extern CSV
- `HistoryTabController.importItems(importedItems)` — dedupliserar via `itemId` (Set-baserad)
- Dubblettskydd: `existingItemIds.contains(item.getItemId())` → kastar bort redan kända items
- Resultatet sparas till huvudkassans `loppiskassan.csv` via `FileUtils.updateAndBackup()`
- **10 roterande backuper** (`.backup.0` → `.backup.9`) vid varje skrivning

**Dedup-logik (offline import):**
```java
Set<String> existingItemIds = allHistoryItems.stream()
        .map(V1SoldItem::getItemId).collect(Collectors.toSet());
importedItems.stream()
        .filter(item -> !existingItemIds.contains(item.getItemId()))
        .forEach(allHistoryItems::add);
```

**Dedup-logik (online sync):**
Även onlinesynken har deduplicering — en tvåstegs-merge:
1. **Pass 1:** Uppdatera befintliga items (matcha på `itemId`, uppdatera `paidOut`-status)
2. **Pass 2:** Lägg till nya items, men hoppa över "approximativa duplikater" (samma `seller` + `price` inom 60 sekunders tolerans)

**CSV-format (7–8 kolumner):**
```
eventId, itemId, soldTime, seller, price, paidOutTime, paymentMethod, archived
```

> **⚠️ VIKTIGT:** Denna CSV-import-funktion ska **fortsätta fungera** för de som inte vill eller kan använda iLoppis-backend. Inga befintliga flöden får brytas.

### 1.2 Begränsningar i nuläget
1. **Global CSV:** Alla försäljningar (online + offline) hamnar i samma `loppiskassan.csv` — ej separerade per event
2. **Manuell eventlista:** Användaren måste klicka "Hämta loppisar" för att se nya online-events
3. **Inget upload-stöd:** Offline-försäljningar kan inte laddas upp till iLoppis-backend i efterhand
4. **Inga lokala event-metadata:** Lokala events saknar namn, beskrivning, intäktsfördelning — allt styrs av globala inställningar

### 1.3 Vad som ska förbättras
| # | Förbättring | Motivering |
|---|-------------|------------|
| A | JSONL per event istället för global CSV | Separerar data per event, enklare att resonera om |
| B | Flera lokala events med metadata | Varje kassa kan ha eget namn, intäktsfördelning |
| C | Bulk-upload av lokal data till backend | Offline-kassörer kan ladda upp i efterhand |
| D | Automatisk eventlista-hämtning | Smidigare UX, ingen manuell knapp |
| E | Bibehåll CSV-import bakåtkompatibilitet | Befintlig funktion får EJ brytas |

---

## 2. Terminologi

| Term | Beskrivning |
|------|-------------|
| **Online-event** | Event hämtat från iLoppis-backend (kräver API-nyckel + nätverk) |
| **Lokalt event** | Event skapat lokalt i LoppisKassan, utan backend-kontakt. Tidigare kallat "offline-event" |
| **Huvudkassa** | Den kassa som importerar data från andra kassor och gör slutredovisning |
| **Bulk-upload** | Att ladda upp ett lokalt events data till backend i efterhand |

> **Namnändring:** "Offline" → "Lokalt" överallt i kod och UI. "Offline" antyder ett problem, "lokalt" är mer neutralt och korrekt.

---

## 3. Teknisk design

### 3.1 Filstruktur (JSONL per event)

#### Ny katalogstruktur
```
~/.loppiskassan/
├── config.properties
├── loppiskassan.csv                    # ★ KVAR – bakåtkompatibilitet (CSV-import)
└── events/
    ├── {eventId-online}/              # Online iLoppis-event
    │   ├── metadata.json
    │   ├── pending_items.jsonl
    │   ├── rejected_purchases.jsonl
    │   └── sold_items.jsonl (backup)
    ├── local-{uuid-1}/                # Lokalt event 1
    │   ├── metadata.json
    │   ├── pending_items.jsonl
    │   └── sold_items.jsonl (backup)
    └── local-{uuid-2}/                # Lokalt event 2
        ├── metadata.json
        ├── pending_items.jsonl
        └── sold_items.jsonl (backup)
```

> **Notera:** `loppiskassan.csv` finns kvar för bakåtkompatibilitet. "Importera kassa"-knappen fortsätter fungera som idag (läser CSV-filer). JSONL-filerna används *parallellt* för det nya per-event-lagringsformatet.

#### metadata.json (per event)
```json
{
  "eventId": "local-550e8400-e29b-41d4-a716-446655440000",
  "eventType": "LOCAL",
  "name": "Sillfest Kassa 1",
  "description": "Lokal kassa för Sillfest 2026",
  "createdAt": "2026-02-08T10:00:00Z",
  "revenueSplit": {
    "marketOwnerPercentage": 10.0,
    "vendorPercentage": 85.0,
    "platformProviderPercentage": 5.0,
    "charityPercentage": 0.0
  },
  "uploadedToBackend": false,
  "uploadedAt": null,
  "backendEventId": null
}
```

#### pending_items.jsonl (oförändrad struktur, en rad per item)
```jsonl
{"itemId":"01HW1K2M3N4P5Q6R7S","purchaseId":"01HW1K2M3N4P5Q6R7T","seller":42,"price":50,"paymentMethod":"CASH","soldTime":"2026-02-08T14:30:00Z","errorText":""}
{"itemId":"01HW1K2M3N4P5Q6R7U","purchaseId":"01HW1K2M3N4P5Q6R7T","seller":42,"price":75,"paymentMethod":"CASH","soldTime":"2026-02-08T14:30:00Z","errorText":""}
```

### 3.2 Eventlista – Kombinerad vy

#### Discovery-vy (omdesignad)
```
┌─────────────────────────────────────────────────────────────┐
│  iLoppis Cash Register v2.0                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Online-events (hämtade från iLoppis)    [🔄 Synkroniserar] │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Event            │ Stad     │ Öppnar    │ Stänger     │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ Sillfest         │ Sillinge │ 2026-02-08│ 2026-02-09│ │  │
│  │ Julmarknad       │ Uppsala  │ 2026-02-15│ 2026-02-15│ │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  Lokala events                             [+ Skapa nytt]   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Namn             │ Skapad   │ Försäljn. │ Status      │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ Sillfest Kassa 1 │ 2026-02-08│ 23 köp   │ ⏳ Pending  │  │
│  │ Sillfest Kassa 2 │ 2026-02-08│ 45 köp   │ ⏳ Pending. │  │
│  │ Testloppis       │ 2026-01-15│ 5 köp    │ ✅ Uploaded │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Statusikoner för lokala events
| Status | Ikon | Beskrivning |
|--------|------|-------------|
| **Pending** | ⏳ | Har pending items, ej uppladdade |
| **Uploaded** | ✅ | Alla items uppladdade till backend |
| **Empty** | 📭 | Inga försäljningar ännu |
| **Error** | ⚠️ | Fel vid uppladdning (se detaljer) |

### 3.3 Automatisk eventlista-hämtning

#### Beteende
```java
// DiscoveryTabController.java

private static final int AUTO_REFRESH_INTERVAL_MS = 60_000;  // 60 sekunder
private ScheduledExecutorService refreshScheduler;

@Override
public void initUIState() {
    // Initial load
    loadAllEvents();
    
    // Schedule auto-refresh
    refreshScheduler = Executors.newSingleThreadScheduledExecutor();
    refreshScheduler.scheduleAtFixedRate(
        this::loadAllEvents,
        AUTO_REFRESH_INTERVAL_MS,
        AUTO_REFRESH_INTERVAL_MS,
        TimeUnit.MILLISECONDS
    );
}

private void loadAllEvents() {
    // 1. Fetch online events from backend
    List<V1Event> onlineEvents = fetchOnlineEventsFromBackend();
    
    // 2. Load local events from disk
    List<LocalEvent> localEvents = LocalEventRepository.loadAll();
    
    // 3. Combine and populate UI
    view.populateEventsTable(onlineEvents, localEvents);
}
```

#### UI-feedback
- 🔄 Spinner i header när synkning pågår
- ⚠️ Varning om nätverksfel (fortsätter visa cached online-events)
- Lokala events laddas alltid (offline-friendly)

### 3.4 Skapa lokalt event – Dialog

#### UI-mockup
```
┌────────────────────────────────────────────┐
│  Skapa lokalt event                    [X] │
├────────────────────────────────────────────┤
│                                            │
│  Namn:                                     │
│  ┌──────────────────────────────────────┐  │
│  │ Sillfest Kassa 1                     │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  Beskrivning (valfritt):                   │
│  ┌──────────────────────────────────────┐  │
│  │ Lokal kassa för Sillfest 2026        │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  Intäktsfördelning:                        │
│  ┌─────────────┬─────────────┬──────────┐  │
│  │ Arrangör (%)│ Säljare (%) │ iLoppis  │  │
│  ├─────────────┼─────────────┼──────────┤  │
│  │     10      │     85      │    5     │  │
│  └─────────────┴─────────────┴──────────┘  │
│                                            │
│  ⚠️ Lokala events kräver inte internet     │
│     men kan laddas upp senare via UI.      │
│                                            │
│         [Avbryt]          [Skapa event]    │
└────────────────────────────────────────────┘
```

#### Java-implementation
```java
public class CreateLocalEventDialog extends JDialog {
    private JTextField nameField;
    private JTextArea descriptionArea;
    private JSpinner marketOwnerSpin;
    private JSpinner vendorSpin;
    private JSpinner platformSpin;
    
    public LocalEvent showDialog() {
        // Show modal dialog
        setVisible(true);
        
        if (confirmed) {
            String eventId = "local-" + UUID.randomUUID().toString();
            LocalEvent event = new LocalEvent(
                eventId,
                nameField.getText(),
                descriptionArea.getText(),
                new V1RevenueSplit(
                    (float) marketOwnerSpin.getValue(),
                    (float) vendorSpin.getValue(),
                    (float) platformSpin.getValue(),
                    0.0f
                )
            );
            
            LocalEventRepository.create(event);
            return event;
        }
        
        return null;
    }
}
```

### 3.5 Bulk-upload av JSONL – UI och flow

#### Bulk-upload dialog
```
┌────────────────────────────────────────────┐
│  Ladda upp lokala försäljningar till       │
│  iLoppis backend                       [X] │
├────────────────────────────────────────────┤
│                                            │
│  Lokalt event:                             │
│  ┌──────────────────────────────────────┐  │
│  │ Sillfest Kassa 1 (23 köp)            │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  Koppling till backend-event:              │
│  ┌──────────────────────────────────────┐  │
│  │ [🔍] Välj event från iLoppis...      │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  Eller välj JSONL-fil från disk:           │
│  ┌──────────────────────────────────────┐  │
│  │ [📁] Bläddra...                      │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  Förhandsgranskning:                       │
│  ┌──────────────────────────────────────┐  │
│  │ 23 items från 8 köp                  │  │
│  │ Tidsspann: 2026-02-08 10:00 - 15:30  │  │
│  │ Total omsättning: 12,450 SEK         │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  ⚠️ Detta kräver API-nyckel med            │
│     rättigheter för valt event.            │
│                                            │
│         [Avbryt]        [Ladda upp]        │
└────────────────────────────────────────────┘
```

#### Backend API-förslag

**Endpoint:** `POST /v1/events/{eventId}/sold-items:bulk-upload`

**Request:**
```json
{
  "items": [
    {
      "itemId": "01HW1K2M3N4P5Q6R7S",
      "purchaseId": "01HW1K2M3N4P5Q6R7T",
      "seller": 42,
      "price": 50,
      "paymentMethod": "KONTANT",
      "soldTime": "2026-02-08T14:30:00Z"
    }
  ],
  "metadata": {
    "source": "loppiskassan-desktop",
    "sourceEventId": "local-550e8400-e29b-41d4-a716-446655440000",
    "sourceEventName": "Sillfest Kassa 1"
  }
}
```

**Response:**
```json
{
  "acceptedItems": [ /* ... */ ],
  "rejectedItems": [
    {
      "item": { /* ... */ },
      "reason": "Duplicate itemId (already uploaded)",
      "errorCode": "DUPLICATE_RECEIPT"
    }
  ],
  "summary": {
    "totalItems": 23,
    "acceptedCount": 21,
    "rejectedCount": 2
  }
}
```

**Deduplicering:**
- Backend kollar om `itemId` redan finns (unique index på event_id + item_id)
- Om duplikat: avvisa med `DUPLICATE_RECEIPT` error code
- Klient markerar accepted items som `uploaded = true` i metadata

#### Java-implementation
```java
public class BulkUploadDialog extends JDialog {
    private JComboBox<LocalEvent> localEventCombo;
    private JComboBox<V1Event> backendEventCombo;
    private JButton fileChooserButton;
    private JLabel previewLabel;
    
    public void performUpload() {
        LocalEvent localEvent = (LocalEvent) localEventCombo.getSelectedItem();
        V1Event backendEvent = (V1Event) backendEventCombo.getSelectedItem();
        
        ProgressDialog.show(
            LocalizationManager.tr("bulk_upload.progress"),
            () -> {
                // 1. Read JSONL file
                List<V1SoldItem> items = PendingItemsStore.readAll(localEvent.getEventId());
                
                // 2. Upload to backend
                V1BulkUploadResponse response = ApiHelper.INSTANCE
                    .getSoldItemsServiceApi()
                    .soldItemsServiceBulkUpload(backendEvent.getId(), items);
                
                // 3. Update local metadata
                if (response.getAcceptedItems().size() > 0) {
                    LocalEventMetadata meta = LocalEventRepository.getMetadata(localEvent.getEventId());
                    meta.setUploadedToBackend(true);
                    meta.setUploadedAt(OffsetDateTime.now());
                    meta.setBackendEventId(backendEvent.getId());
                    LocalEventRepository.saveMetadata(meta);
                    
                    // Delete uploaded items from pending_items.jsonl
                    Set<String> acceptedIds = response.getAcceptedItems().stream()
                        .map(V1SoldItem::getItemId)
                        .collect(Collectors.toSet());
                    
                    PendingItemsStore.deleteByItemIds(localEvent.getEventId(), acceptedIds);
                }
                
                return response;
            },
            response -> {
                showUploadSummary(response);
            },
            error -> {
                Popup.ERROR.showAndWait("Bulk upload misslyckades", error.getMessage());
            }
        );
    }
}
```

---

## 4. UI-flöden

### Flöde 1: Skapa och använda lokalt event

```
Användare öppnar app
    ↓
Discovery-vy visar automatiskt:
    - Online-events från backend (om nätverk finns)
    - Lokala events från disk
    ↓
Användare klickar [+ Skapa nytt] under "Lokala events"
    ↓
Dialog öppnas: "Skapa lokalt event"
    - Namn: "Sillfest Kassa 1"
    - Beskrivning: ""
    - Intäktsfördelning: 10/85/5
    ↓
Användare klickar [Skapa event]
    ↓
1. LocalEventRepository.create() skapar:
    - events/local-{uuid}/metadata.json
    - events/local-{uuid}/pending_items.jsonl (tom fil)
2. Nytt lokalt event dyker upp i listan
    ↓
Användare väljer "Sillfest Kassa 1" → [Öppna kassa]
    ↓
Kassa-vy öppnas (samma som innan)
    - Alla försäljningar sparas i pending_items.jsonl
    - Ingen bakgrundssync (lokalt event)
```

### Flöde 2: Bulk-upload efter loppis

```
Loppis över (utan internet på plats)
    ↓
3 kassörer har kört LoppisKassan med lokala events:
    - local-{uuid-1}: 23 köp
    - local-{uuid-2}: 45 köp
    - local-{uuid-3}: 31 köp
    ↓
Arrangör samlar ihop alla datorer/USB-pinnar
    ↓
På kontoret (med internet):
1. Öppna LoppisKassan
2. Kopiera JSONL-filer till egen dator:
    - events/local-{uuid-1}/pending_items.jsonl
    - events/local-{uuid-2}/pending_items.jsonl
    - events/local-{uuid-3}/pending_items.jsonl
    ↓
3. För varje lokalt event:
    - Välj event i listan
    - Högerklick → [Ladda upp till iLoppis]
    - Dialog öppnas:
        * Välj backend-event: "Sillfest 2026"
        * Välj API-nyckel (market owner rights)
        * Förhandsgranskning visar:
            - 23 items från 8 köp
            - Tidsspann: 10:00 - 15:30
            - Total: 12,450 SEK
    - Klicka [Ladda upp]
    ↓
4. Backend-respons:
    - acceptedItems: 21
    - rejectedItems: 2 (duplikerade itemId)
    ↓
5. Lokal metadata uppdateras:
    - uploadedToBackend = true
    - backendEventId = "..."
6. Accepterade items raderas från pending_items.jsonl
7. Sammanfattning visas:
    ✅ 21/23 items uppladdade
    ⚠️ 2 items avvisade (duplikat)
```

### Flöde 3: Automatisk eventlista-uppdatering

```
App startar
    ↓
DiscoveryTabController.initUIState()
    ↓
1. Ladda events (initial):
    - Online: fetch från backend
    - Lokala: läs från disk
    ↓
2. Starta ScheduledExecutorService:
    - Interval: 60 sekunder
    - Uppgift: loadAllEvents()
    ↓
Varje 60 sekunder:
    - Visa 🔄 spinner i UI
    - Försök hämta online-events
    - Om lyckas: uppdatera lista
    - Om misslyckas: behåll gamla events, visa varning
    - Ladda om lokala events (kan ha ändrats av annan instans)
    - Dölj spinner
```

---

### Flöde 4: CSV-import (befintligt – oförändrat)

> **Kritiskt:** Detta flöde ska vara identiskt med dagens beteende. Ingen ändring.

```
Stor loppis, 3 kassor, ingen iLoppis-backend
    ↓
Varje kassa kör LoppisKassan med lokalt event
    - Varje kassa har sin egen loppiskassan.csv
    ↓
Efter loppisen:
    - Välj en kassa som "huvudkassa"
    - Samla ihop CSV-filer från övriga kassor
    ↓
I huvudkassan:
    Historik-fliken → klicka "Importera kassa"
    ↓
    JFileChooser öppnas → välj kassa2.csv
    ↓
    importItems():
        - Läs alla items från kassa2.csv
        - Dedup via itemId (Set)
        - Lägg till unika items → spara till huvudkassans CSV
    ↓
    Upprepa för kassa3.csv
    ↓
Huvudkassan har nu alla köp från alla kassor
    → Gör utbetalning per säljare
    → Exportera redovisning
```

**Inget ändras** i detta flöde. CSV-importen fortsätter fungera via `HistoryTabController.importLocalCashRegister()`.

---

## 5. Bakåtkompatibilitet och migration

### 5.1 CSV-import lever kvar

| Funktion | Före | Efter |
|----------|------|-------|
| "Importera kassa"-knappen | Importerar extern CSV → dedup → spara | **Oförändrat** |
| CSV-filformat | 8 kolumner, kommaseparerat | **Oförändrat** |
| Dubblettskydd (itemId) | Set-baserad dedup | **Oförändrat** |
| JFileChooser filter | `*.csv` | **Oförändrat** |
| 10 roterande backuper | `.backup.0` → `.backup.9` | **Oförändrat** |
| `HistoryTabController.importLocalCashRegister()` | Befintlig kod | **Oförändrat** |

### 5.2 Vad som läggs till (ej ersätter)

| Ny funktion | Befintlig CSV | Ny JSONL |
|-------------|---------------|----------|
| Per-event-lagring | Nej (global CSV) | Ja (per eventId) |
| Metadata (namn, revenueSplit) | Nej | Ja (metadata.json) |
| Bulk-upload till backend | Nej | Ja |
| Auto-refresh eventlista | Nej | Ja |
| Flera lokala events | Via CSV-import (manuellt) | Inbyggt i UI |

### 5.3 Migration vid uppstart

```java
public class MigrationHelper {
    private static final Path OLD_CSV = Paths.get(
        System.getProperty("user.home"), ".loppiskassan", "loppiskassan.csv"
    );
    
    public static void migrateIfNeeded() {
        if (!Files.exists(OLD_CSV)) return;
        
        List<V1SoldItem> items = FormatHelper.toItems(
            FileHelper.readFromFile(OLD_CSV), true
        );
        if (items.isEmpty()) return;
        
        // Skapa "migrerat" lokalt event
        String eventId = "local-migrated-" + UUID.randomUUID();
        LocalEvent event = new LocalEvent(eventId,
            "Migrerade försäljningar",
            "Data från gammal loppiskassan.csv", null);
        LocalEventRepository.create(event);
        
        // Kopiera till JSONL
        PendingItemsStore store = new PendingItemsStore(eventId);
        store.appendItems(items);
        
        // ★ VIKTIGT: FLYTTA (ej radera) gammal CSV
        Files.move(OLD_CSV, OLD_CSV.resolveSibling("loppiskassan.csv.pre-migration"));
        
        Popup.INFORMATION.showAndWait(
            LocalizationManager.tr("migration.complete.title"),
            LocalizationManager.tr("migration.complete.message")
        );
    }
}
```

**CSV-importen upphör INTE.** `loppiskassan.csv` skapas fortfarande vid checkout (för de som kör utan JSONL). Migrationen kopierar data, den raderar inte CSV-baserad funktion.

### 5.4 Övergångsperiod

Under en övergångsperiod stöds bägge format:

```properties
# config.properties
storage.format=JSONL   # "CSV" | "JSONL" | "BOTH" (default under övergång)
```

| storage.format | Beteende |
|----------------|----------|
| `CSV` | Helt som idag. Inget JSONL. |
| `JSONL` | Nya per-event-filer. CSV-import fungerar fortfarande. |
| `BOTH` | Skriver till bägge (JSONL + CSV). Säkrast under övergång. **Standard.** |

---

## 6. Testfall

### 6.1 Lokala testfall (utan iLoppis-backend)

Dessa tester körs som JUnit-tester i LoppisKassan utan nätverksaccess.

#### T-L01: Skapa lokalt event
| Steg | Förväntat |
|------|-----------|
| Användare klickar [+ Skapa nytt] i Discovery-vyn | Dialog "Skapa lokalt event" öppnas |
| Fyller i namn: "Sillfest Kassa 1", intäktsfördelning 10/85/5 | Fälten accepteras |
| Klickar [Skapa event] | `events/local-{uuid}/metadata.json` skapas med korrekt data |
| Discovery-vyn uppdateras | Nytt lokalt event syns i listan med status 📭 Empty |

#### T-L02: Registrera köp på lokalt event
| Steg | Förväntat |
|------|-----------|
| Välj lokalt event → [Öppna kassa] | Kassavyn öppnas |
| Registrera 3 köp: säljare 5 (100kr), säljare 5 (50kr), säljare 8 (200kr) | Items visas i kassavyn |
| Tryck [Kontant] | Köpet sparas i `pending_items.jsonl` |
| Kontrollera JSONL-filen | 3 rader med korrekt JSON, ULID-baserade itemId/purchaseId |
| Gå tillbaka till Discovery | Eventet visar "3 köp", status ⏳ Pending |

#### T-L03: Kassörflödet – tangentbord (befintligt)
| Steg | Förväntat |
|------|-----------|
| Markören startar i säljnummerfältet | Fokus korrekt |
| Skriv "5" → Tab/Enter | Fokus flyttar till prisfältet |
| Skriv "100" → Enter | Item läggs till. Fokus tillbaka till säljnummerfältet |
| Upprepa 3× | 3 items i listan |

> **Tangentbordsflödet är kritiskt** och får aldrig brytas (se `AGENTS.md`).

#### T-L04: CSV-import (befintlig funktion – regressionstest)
| Steg | Förväntat |
|------|-----------|
| Kassa A: registrera 5 köp, spara CSV | `loppiskassan.csv` innehåller 5 rader |
| Kassa B: registrera 3 köp, spara CSV | `loppiskassan_B.csv` innehåller 3 rader |
| I Kassa A: Historik → Importera kassa → välj kassa_B.csv | 3 nya items importeras |
| Kontrollera: totalt 8 items i historiken | Korrekt |
| Importera kassa_B.csv igen | 0 nya items (dubbletter avvisade) |
| Kontrollera: fortfarande 8 items | Korrekt, dedup fungerar |

#### T-L05: Migration CSV → JSONL
| Steg | Förväntat |
|------|-----------|
| Befintlig `loppiskassan.csv` med 10 items finns | |
| Starta app (ny version med JSONL-stöd) | `MigrationHelper.migrateIfNeeded()` körs |
| Kontrollera: `events/local-migrated-{uuid}/` skapad | `metadata.json` + `pending_items.jsonl` med 10 items |
| Kontrollera: gammal CSV flyttad till `.pre-migration` | Originalfil bevarad |
| Popup visas: "Migration genomförd" | |

#### T-L06: Intäktsfördelning per lokalt event
| Steg | Förväntat |
|------|-----------|
| Skapa lokalt event med split 15/80/5 | metadata.json sparar 15/80/5 |
| Öppna kassan, registrera köp | Intäktsfördelningen visas i UI som 15, 80, 5 |
| Gå till Historik → Betala ut säljare 5 | Utbetalning beräknas med 80% till säljare |

#### T-L07: Flera lokala events samtidigt
| Steg | Förväntat |
|------|-----------|
| Skapa lokalt event "Kassa 1" | Skapas OK |
| Skapa lokalt event "Kassa 2" | Skapas OK |
| Registrera 5 köp på Kassa 1 | JSONL skrivs till `events/local-{uuid-1}/` |
| Byt till Kassa 2, registrera 3 köp | JSONL skrivs till `events/local-{uuid-2}/` |
| Byt tillbaka till Kassa 1 | Visar 5 köp (inte 3, inte 8) |
| Discovery visar bägge events med rätt antal | Kassa 1: 5 köp, Kassa 2: 3 köp |

#### T-L08: Utbetalning och redovisning (lokalt event)
| Steg | Förväntat |
|------|-----------|
| Registrera köp: säljare 5 (100+50), säljare 8 (200) | Totalt 350 SEK |
| Historik → Filtrera säljare 5 | Visar 2 items, total 150 SEK |
| Betala ut | Items markeras som utbetalda |
| Filtrera Utbetalt: Nej | Visar säljare 8 (200 SEK) |
| Betala ut säljare 8 | Alla items utbetalda |
| Filtrera Utbetalt: Alla | 3 items, alla markerade utbetalda |

### 6.2 Integrationstestfall (med iLoppis-backend)

Dessa tester kräver en körande backend (Docker Compose: backend + MongoDB + MailHog) och följer det existerande test-mönstret i `iloppis/tests/integration/`.

#### Infrastruktur

**Docker Compose-tjänster:**
```yaml
services:
  backend:      # iLoppis Go-service på port 8080
  mongodb:      # MongoDB på port 27017
  mailhog:      # MailHog SMTP + API på port 8025
```

**Testramverk:** TypeScript + Vitest + Playwright  
**Körning:** `npx vitest --reporter=verbose`

#### Provisionering (följer befintliga mönster)

Varje integrationstestsvit provisionerar sin testdata via API-moduler:

```typescript
// Följer mönstret i tests/api/cashier.spec.ts

// 1. Platform owner login (global setup)
const platformKey = await loginViaApiMagicLink(USERS.PLATFORM_OWNER.email);

// 2. Market owner login
const ownerKey = await loginViaApiMagicLink(USERS.MARKET_OWNERS[0].email);

// 3. Skapa och godkänn marknad
const draft = await createDraftMarket(getDraftMarketPayload(0), ownerKey);
await approveDraftMarket(draft.market.id, platformKey);

// 4. Skapa event
const event = await createEvent({
  marketId: draft.market.id,
  name: "Integration Test Event",
  startsAt: new Date().toISOString(),
  endsAt: new Date(Date.now() + 86400000).toISOString(),
}, ownerKey);

// 5. Skapa API-nyckel (kassör-typ)
const cashierKey = await createApiKey(
  event.event.id,
  'API_KEY_TYPE_WEB_CASHIER',
  ownerKey
);

// 6. Registrera säljare
for (let i = 1; i <= 10; i++) {
  const profile = await createProfile({ firstName: `Seller${i}`, ... });
  const vendor = await createVendorForEvent(event.event.id, profile.id, ownerKey);
  await updateVendorForEvent(event.event.id, vendor.id,
    { status: 'VENDOR_STATUS_APPROVED', sellerNumber: i }, ownerKey);
}
```

#### T-I01: Online-event – registrera köp via API
| Steg | Förväntat |
|------|-----------|
| Provisionera: market → event → API key → 10 säljare | Setup OK |
| Generera 50 köp via `getTestPurchases({seed: 42, count: 50})` | Reproducerbara köp |
| Skicka köp via `submitPurchases(eventId, alias, purchases, cashierKey)` | HTTP 200 |
| Hämta `listSoldItems(eventId, {}, ownerKey)` | 50 items returnerade |
| Verifiera: alla itemId:n finns | Matchning OK |
| Verifiera: total omsättning matchar summan av genererade priser | Summa korrekt |

#### T-I02: Bulk-upload – lokala JSONL → backend
| Steg | Förväntat |
|------|-----------|
| Provisionera: market → event → API key → 10 säljare | Setup OK |
| Skapa lokal JSONL-fil med 30 items (ULID-baserade) | Fil skapad |
| Ladda upp via `submitPurchases()` (gruppera per purchaseId) | HTTP 200 |
| Hämta `listSoldItems()` | 30 items i backend |
| Verifiera: alla ULID:n matchar | Korrekt |
| Ladda upp samma JSONL igen | Duplikater avvisas (DUPLICATE_RECEIPT) |

#### T-I03: Multi-kassör – parallell upload
| Steg | Förväntat |
|------|-----------|
| Provisionera: market → event → 2 API keys (cashier1, cashier2) → 20 säljare | Setup OK |
| Kassör 1: generera + ladda upp 25 köp (seed: 100) | HTTP 200 |
| Kassör 2: generera + ladda upp 25 köp (seed: 200) | HTTP 200 |
| Hämta `listSoldItems()` | 50 items totalt |
| Verifiera: inga duplikater (unika ULID per kassör) | Korrekt |
| Verifiera: försäljning per säljare stämmer | Matchning med genererade data |

#### T-I04: Online sync – degraded mode recovery
| Steg | Förväntat |
|------|-----------|
| Provisionera event (online) | Setup OK |
| Registrera 10 köp med simulerat nätverksfel (degraded mode) | Items sparas lokalt |
| Återställ nätverksåtkomst | |
| Trigga sync (bakgrundsupload) | Items laddas upp till backend |
| `listSoldItems()` | 10 items |
| Verifiera: lokal JSONL och backend matchar | Identiska itemId:n |

#### T-I05: Redovisningsrapport – lokal vs backend
| Steg | Förväntat |
|------|-----------|
| Provisionera: 3 kassörer, 30 köp fördelade på 10 säljare | Setup OK |
| Ladda upp alla köp till backend | 30 items i backend |
| Generera lokal redovisning per säljare | Lokal summa per säljare |
| Hämta backend-rapport per säljare via API | Backend-summa per säljare |
| Jämför: lokal redovisning == backend-rapport | Identiskt |

#### T-I06: Event lifecycle – full end-to-end
| Steg | Förväntat |
|------|-----------|
| 1. Login (platform owner + market owner) | API keys |
| 2. Skapa draft marknad med revenueSplit 10/85/5 | Draft skapad |
| 3. Godkänn marknad | Status: APPROVED |
| 4. Skapa event | eventId |
| 5. Skapa API-nyckel (web cashier) | cashierKey + alias |
| 6. Registrera 30 säljare, godkänn alla | 30 vendors APPROVED |
| 7. LoppisKassan: mata in kassakod (alias) | API key hämtad, online-event kopplat |
| 8. Registrera 100 köp (blandade säljare och betalmetoder) | Items sparas lokalt + laddas upp |
| 9. Historik: betala ut säljare 1-10 | Utbetalning markerad |
| 10. Synka med backend | Items + paidOut-status synkade |
| 11. Verifiera backend: 100 items | Matchning |
| 12. Arkivera utbetalda | Arkivfil skapad |

### 6.3 Testmatriser

#### Matris A: Lagringsformat × Operation
| Operation | CSV (befintlig) | JSONL (ny) | BOTH (övergång) |
|-----------|----------------|------------|-----------------|
| Registrera köp | ✅ Skriv CSV | ✅ Append JSONL | ✅ Bägge |
| CSV-import | ✅ Oförändrat | ✅ Importerar till CSV | ✅ Bägge |
| Utbetalning | ✅ Uppdatera CSV | ✅ Uppdatera JSONL | ✅ Bägge |
| Bulk-upload | ❌ Ej stöd | ✅ JSONL → backend | ✅ JSONL → backend |
| Online sync | ✅ CSV + API | ✅ JSONL + API | ✅ Bägge |

#### Matris B: Eventtyp × Funktion
| Funktion | Online-event | Lokalt event |
|----------|-------------|--------------|
| Registrera köp | ✅ + bakgrundsupload | ✅ Bara lokalt |
| Historik/utbetalning | ✅ | ✅ |
| CSV-import | ✅ (via import-knappen) | ✅ (via import-knappen) |
| Bulk-upload till backend | ❌ (redan online) | ✅ (i efterhand) |
| Auto-refresh | ✅ (hämtar från backend) | ❌ (lokalt, inget att refresha) |

---

## 7. Uppdatering av manual (`docs/manual_v2.md`)

### 7.1 Befintligt avsnitt: "Importera kassa" – KVAR OFÖRÄNDRAT

Det befintliga manualavsnittet ska **inte ändras** utan bara kompletteras med en hänvisning till det nya avsnittet:

```markdown
> **Nytt i v2.1:** Du kan nu även använda lokala events med JSONL-lagring 
> för separerad data per kassa. Se avsnittet "Lokala events" nedan.
```

### 7.2 Nytt manulavsnitt: "Lokala events"

Lägg till efter "Importera kassa"-avsnittet:

```markdown
Lokala events
--------------
Om du inte använder iLoppis-tjänsten kan du skapa lokala events direkt i 
LoppisKassan. Varje lokalt event har sitt eget namn och sin egen datalagring.

### Skapa ett lokalt event
1. I Upptäck-fliken, klicka på **[+ Skapa nytt]** under "Lokala events"
2. Fyll i namn (t.ex. "Sillfest Kassa 1")
3. Ange intäktsfördelning (standard: 10/85/5)
4. Klicka **[Skapa event]**

### Använda flera kassor med lokala events
Istället för att importera CSV-filer kan du nu skapa separata lokala events 
för varje kassa:
- "Sillfest Kassa 1" för kassör 1
- "Sillfest Kassa 2" för kassör 2
- osv.

Varje event har sin egen datalagring och blandas inte ihop.

> **Tips!** CSV-import ("Importera kassa") fungerar fortfarande som tidigare.
> Använd den metoden om du föredrar det.

### Ladda upp lokalt event till iLoppis (valfritt)
Om du vill använda iLoppis-tjänstens rapporter och statistik kan du ladda 
upp ett lokalt events data till backend:
1. Högerklicka på det lokala eventet i listan
2. Välj **[Ladda upp till iLoppis]**
3. Välj vilket backend-event att koppla till
4. Klicka **[Ladda upp]**

Uppladdningen är idempotent – du kan ladda upp samma data flera gånger 
utan risk för dubbletter.
```

### 7.3 Nytt manulavsnitt: "Automatisk uppdatering"

```markdown
Automatisk uppdatering av eventlista
--------------------------------------
Online-events från iLoppis hämtas automatiskt var 60:e sekund. Du behöver 
inte längre klicka på "Hämta loppisar" för att se nya events.

En 🔄-ikon visas i listan medan uppdatering pågår. Om nätverket inte 
fungerar behålls den senaste listan och en varningsikon visas.
```

---

## 8. Öppna frågor

### 8.1 Backend bulk-upload endpoint
**Fråga:** Ska vi återanvända befintlig `POST /v1/events/{eventId}/sold-items` eller skapa ny `:bulk-upload`?

**Rekommendation:** Återanvänd befintlig endpoint
- Redan stödjer batch-insert (grupperat per purchaseId)
- Har deduplicering via unique index (event_id + item_id)
- Mindre backend-arbete

### 8.2 Hantering av API-nycklar vid bulk-upload
**Scenario:** Bulk-upload kräver API-nyckel med rättigheter för måleventet

**Nuvarande:** Användaren matar in kassakod (XXX-XXX) vid eventval

**Lösning vid bulk-upload:**
- Be om kassakod i bulk-upload-dialogen
- Hämta API-nyckel via `GET /v1/events/{eventId}/api-keys/alias/{code}`
- Cachea ej (engångsanvändning)

### 8.3 Rensning av gamla lokala events
**Förslag:**
- Manuell rensning: högerklicka → [Radera lokalt event]
- Ingen automatisk rensning (användaren bestämmer)
- Bekräftelsedialog: "X items kommer att raderas. Fortsätt?"

### 8.4 Export till CSV (lokalt event)
**Scenario:** Arrangörer vill analysera i Excel

**Lösning:** Högerklicka lokalt event → [Exportera till CSV]
- Genererar `{eventnamn}-{datum}.csv`
- Samma CSV-format som `loppiskassan.csv` (bakåtkompatibelt)

---

## 9. Implementationsplan

### Sprint 1: Grundläggande JSONL-migration
- [ ] `PendingItemsStore` (Java-version av Android-koden)
- [ ] `LocalEventRepository` för metadata.json-hantering
- [ ] `MigrationHelper` för CSV → JSONL
- [ ] `CashierTabController` — valfri JSONL-lagring (config.properties: `storage.format`)
- [ ] `FormatHelper` — JSONL serialisering/deserialisering
- [ ] JUnit-tester: T-L01, T-L02, T-L05

### Sprint 2: Lokala events i Discovery-vy
- [ ] `DiscoveryTabController` — kombinerad lista (online + lokala)
- [ ] UI: visa lokala events i tabell med statusikoner
- [ ] Dialog: "Skapa lokalt event" (`CreateLocalEventDialog`)
- [ ] Auto-refresh: `ScheduledExecutorService` med 60s intervall
- [ ] JUnit-tester: T-L03, T-L06, T-L07

### Sprint 3: Bulk-upload + bakåtkompatibilitet
- [ ] Dialog: "Ladda upp till iLoppis" (`BulkUploadDialog`)
- [ ] Integration med `submitPurchases()` (återanvänd befintlig endpoint)
- [ ] Hantering av partial success (accepted + rejected)
- [ ] CSV-import regressionstest: T-L04
- [ ] JUnit-tester: T-L08
- [ ] Integrationstester: T-I01, T-I02, T-I03

### Sprint 4: End-to-end + dokumentation
- [ ] Integrationstester: T-I04, T-I05, T-I06
- [ ] Uppdatera `docs/manual_v2.md` (se avsnitt 7)
- [ ] Uppdatera `docs/installation.md` med eventuella nya prereqs
- [ ] Uppdatera lokaliseringsfiler: `sv.json`, `en.json`
- [ ] End-to-end test av offline → upload → verifiera scenario

---

## 10. Framtida förbättringar (ej del av denna issue)

1. **Multi-event kassaläge:** Växla mellan events utan att starta om appen
2. **Real-time sync mellan kassörer:** WebSocket-baserad synk
3. **Mobile app integration:** Scanna QR-kod för att dela lokalt event
4. **Offline-först design:** Cache vendor-listor och events för 100% offline

---

**Dokumentslut**
