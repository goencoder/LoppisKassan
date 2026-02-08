# Issue 001: JSONL-baserad lagring och lokala events

**Status:** Design  
**Skapad:** 2026-02-08  
**Uppdaterad:** 2026-02-09  
**Prioritet:** Hög  
**Epic:** Kassasystem refaktorering  
**Scope:** Enbart LoppisKassan (Java desktop) — inga API/backend-ändringar  
**Breaking:** Ja — ny majorversion (v3.0). CSV-lagring ersätts helt av JSONL.

> **Relation:** Bulk-upload av lokal data till iLoppis-backend beskrivs i [Issue 002](002-bulk-upload-jsonl.md).

---

## 1. Nuläge

### 1.1 Vad som finns idag

LoppisKassan lagrar alla försäljningar i en enda global `loppiskassan.csv`. Flera kassörer stöds genom att varje instans skriver en egen CSV som sedan importeras manuellt till en "huvudkassa".

**CSV-format (7–8 kolumner):**
```
eventId, itemId, soldTime, seller, price, paidOutTime, paymentMethod, archived
```

**Multi-kassör-flöde idag:**
1. Varje kassa kör sin instans → skriver `loppiskassan.csv`
2. Efter loppisen: en kassa importerar de andras CSV-filer
3. Dedup via `itemId` (Set-baserad)
4. Huvudkassan har all data → utbetalning/redovisning

### 1.2 Begränsningar
1. **Global CSV** — alla events (online + offline) blandas i samma fil
2. **Manuell eventlista** — kräver knapptryckning "Hämta loppisar"
3. **Inga lokala event-metadata** — namn, intäktsfördelning styrs av globala inställningar
4. **Inget upload-stöd** — offline-data kan inte laddas upp (→ [Issue 002](002-bulk-upload-jsonl.md))
5. **CSV-format** — svårt att utöka, inga typer, inga kommentarer

### 1.3 Vad som ska förbättras (denna issue)

| # | Förbättring | Motivering |
|---|-------------|------------|
| A | JSONL per event istället för global CSV | Separerar data per event, enklare att resonera om |
| B | Flera lokala events med metadata | Varje kassa kan ha eget namn, intäktsfördelning |
| C | Automatisk eventlista-hämtning | Smidigare UX, ingen manuell knapp |
| D | Engångsmigration CSV → JSONL | Befintlig data tas om hand |

---

## 2. Terminologi

| Term | Beskrivning |
|------|-------------|
| **Online-event** | Event hämtat från iLoppis-backend (kräver API-nyckel + nätverk) |
| **Lokalt event** | Event skapat lokalt i LoppisKassan, utan backend-kontakt |
| **Huvudkassa** | Den kassa som konsoliderar data från andra kassor och gör slutredovisning |

> **Namnändring:** "Offline" → "Lokalt" överallt i kod och UI. "Offline" antyder ett problem, "lokalt" är mer neutralt och korrekt.

---

## 3. Teknisk design

### 3.1 Filstruktur (JSONL per event)

```
~/.loppiskassan/
├── config.properties
└── events/
    ├── {eventId-online}/               # Online iLoppis-event
    │   ├── metadata.json
    │   ├── pending_items.jsonl
    │   ├── rejected_purchases.jsonl
    │   └── sold_items.jsonl
    ├── local-{uuid-1}/                 # Lokalt event 1
    │   ├── metadata.json
    │   ├── pending_items.jsonl
    │   └── sold_items.jsonl
    └── local-{uuid-2}/                 # Lokalt event 2
        ├── metadata.json
        ├── pending_items.jsonl
        └── sold_items.jsonl
```

> **Notera:** `loppiskassan.csv` finns **inte** kvar. All lagring sker i JSONL. Engångsmigration konverterar befintlig CSV-data (se avsnitt 5).

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
  }
}
```

#### pending_items.jsonl (en rad per item)
```jsonl
{"itemId":"01HW1K2M3N4P5Q6R7S","purchaseId":"01HW1K2M3N4P5Q6R7T","seller":42,"price":50,"paymentMethod":"CASH","soldTime":"2026-02-08T14:30:00Z","errorText":""}
{"itemId":"01HW1K2M3N4P5Q6R7U","purchaseId":"01HW1K2M3N4P5Q6R7T","seller":42,"price":75,"paymentMethod":"CASH","soldTime":"2026-02-08T14:30:00Z","errorText":""}
```

### 3.2 Eventlista – Kombinerad vy

#### Discovery-vy (omdesignad)
```
┌─────────────────────────────────────────────────────────────┐
│  iLoppis Cash Register v3.0                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Online-events (hämtade från iLoppis)    [🔄 Synkroniserar] │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Event            │ Stad     │ Öppnar    │ Stänger     │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ Sillfest         │ Sillinge │ 2026-02-08│ 2026-02-09  │  │
│  │ Julmarknad       │ Uppsala  │ 2026-02-15│ 2026-02-15  │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  Lokala events                             [+ Skapa nytt]   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Namn             │ Skapad   │ Försäljn. │ Status      │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │ Sillfest Kassa 1 │ 2026-02-08│ 23 köp   │ Aktiv       │  │
│  │ Sillfest Kassa 2 │ 2026-02-08│ 45 köp   │ Aktiv       │  │
│  │ Testloppis       │ 2026-01-15│ 5 köp    │ Stängd      │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Statusikoner för lokala events
| Status | Ikon | Beskrivning |
|--------|------|-------------|
| **Aktiv** | 🟢 | Har försäljningar, pågående |
| **Tom** | 📭 | Inga försäljningar ännu |
| **Stängd** | 🔒 | Manuellt stängd av användaren |

### 3.3 Automatisk eventlista-hämtning

```java
// DiscoveryTabController.java

private static final int AUTO_REFRESH_INTERVAL_MS = 60_000;  // 60 sekunder
private ScheduledExecutorService refreshScheduler;

@Override
public void initUIState() {
    loadAllEvents();

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

**UI-feedback:**
- 🔄 Spinner i header när synkning pågår
- ⚠️ Varning om nätverksfel (fortsätter visa cached online-events)
- Lokala events laddas alltid (offline-friendly)

### 3.4 Skapa lokalt event – Dialog

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
│     men kan laddas upp senare.             │
│                                            │
│         [Avbryt]          [Skapa event]    │
└────────────────────────────────────────────┘
```

```java
public class CreateLocalEventDialog extends JDialog {
    private JTextField nameField;
    private JTextArea descriptionArea;
    private JSpinner marketOwnerSpin;
    private JSpinner vendorSpin;
    private JSpinner platformSpin;

    public LocalEvent showDialog() {
        setVisible(true);

        if (confirmed) {
            String eventId = "local-" + UUID.randomUUID();
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

### 3.5 Multi-kassör med lokala events

Istället för CSV-import skapas separata lokala events per kassör:

```
Stor loppis, 3 kassörer
    ↓
Kassör 1: skapar lokalt event "Sillfest Kassa 1" → pending_items.jsonl
Kassör 2: skapar lokalt event "Sillfest Kassa 2" → pending_items.jsonl
Kassör 3: skapar lokalt event "Sillfest Kassa 3" → pending_items.jsonl
    ↓
Efter loppisen: Arrangör kopierar JSONL-filer till huvuddator
    ↓
Huvudkassa importerar JSONL-filer via:
    Historik → Importera JSONL → välj fil(er)
    ↓
Dedup via itemId (samma logik som idag, men på JSONL)
    ↓
Alla köp samlade → utbetalning/redovisning
```

**Alternativ:** Om arrangören har internet kan varje kassör ladda upp till backend istället (→ [Issue 002](002-bulk-upload-jsonl.md)).

### 3.6 JSONL-import (ersätter CSV-import)

Den befintliga "Importera kassa"-funktionen skrivs om till att läsa JSONL istället för CSV:

```java
public class HistoryTabController {

    public void importLocalCashRegister() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSONL files", "jsonl"));
        chooser.setMultiSelectionEnabled(true);

        if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                List<V1SoldItem> importedItems = JsonlHelper.readItems(file);
                importItems(importedItems);
            }
        }
    }

    private void importItems(List<V1SoldItem> importedItems) {
        Set<String> existingIds = allHistoryItems.stream()
            .map(V1SoldItem::getItemId)
            .collect(Collectors.toSet());

        long added = importedItems.stream()
            .filter(item -> !existingIds.contains(item.getItemId()))
            .peek(allHistoryItems::add)
            .count();

        saveToJsonl();
        Popup.INFORMATION.showAndWait(
            LocalizationManager.tr("import.complete.title"),
            LocalizationManager.tr("import.complete.message", added, importedItems.size())
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
Dialog: "Skapa lokalt event"
    - Namn: "Sillfest Kassa 1"
    - Intäktsfördelning: 10/85/5
    ↓
Klickar [Skapa event]
    ↓
1. LocalEventRepository.create() skapar:
    - events/local-{uuid}/metadata.json
    - events/local-{uuid}/pending_items.jsonl (tom fil)
2. Nytt event dyker upp i listan
    ↓
Användare väljer "Sillfest Kassa 1" → [Öppna kassa]
    ↓
Kassavyn öppnas
    - Alla försäljningar sparas i pending_items.jsonl
    - Ingen bakgrundssync (lokalt event)
```

### Flöde 2: Multi-kassör med JSONL-import

```
Stor loppis, 3 kassor, utan iLoppis-backend
    ↓
Varje kassa kör LoppisKassan med eget lokalt event
    - Kassa 1: events/local-{uuid-1}/pending_items.jsonl
    - Kassa 2: events/local-{uuid-2}/pending_items.jsonl
    - Kassa 3: events/local-{uuid-3}/pending_items.jsonl
    ↓
Efter loppisen:
    - Välj en kassa som "huvudkassa"
    - Samla ihop JSONL-filer från övriga kassor (USB/delning)
    ↓
I huvudkassan:
    Historik → [Importera JSONL]
    ↓
    Välj pending_items.jsonl från kassa 2 och 3
    ↓
    importItems():
        - Läs alla items från JSONL
        - Dedup via itemId
        - Lägg till unika items
    ↓
Huvudkassan har alla köp → utbetalning/redovisning
```

### Flöde 3: Automatisk eventlista-uppdatering

```
App startar
    ↓
DiscoveryTabController.initUIState()
    ↓
1. Initial load:
    - Online: fetch från backend
    - Lokala: läs från disk
    ↓
2. Starta ScheduledExecutorService (60s intervall)
    ↓
Varje 60 sekunder:
    - Visa 🔄 spinner
    - Försök hämta online-events
    - Ladda om lokala events
    - Uppdatera UI
```

---

## 5. Migration CSV → JSONL (engångs)

Vid första start med v3.0 konverteras befintlig `loppiskassan.csv` till JSONL.

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
        if (items.isEmpty()) {
            Files.delete(OLD_CSV);
            return;
        }

        // Skapa "migrerat" lokalt event
        String eventId = "local-migrated-" + UUID.randomUUID();
        LocalEvent event = new LocalEvent(eventId,
            LocalizationManager.tr("migration.event.name"),
            LocalizationManager.tr("migration.event.description"),
            null);
        LocalEventRepository.create(event);

        // Skriv items till JSONL
        PendingItemsStore store = new PendingItemsStore(eventId);
        store.appendItems(items);

        // Radera gammal CSV och alla backup-filer
        Files.delete(OLD_CSV);
        for (int i = 0; i < 10; i++) {
            Path backup = OLD_CSV.resolveSibling("loppiskassan.csv.backup." + i);
            Files.deleteIfExists(backup);
        }

        Popup.INFORMATION.showAndWait(
            LocalizationManager.tr("migration.complete.title"),
            LocalizationManager.tr("migration.complete.message")
        );
    }
}
```

**Beteende:**
- CSV läses → konverteras till JSONL under `events/local-migrated-{uuid}/`
- CSV-filen och alla `.backup.*`-filer raderas
- Popup meddelar användaren
- **Ingen väg tillbaka** — detta är en engångsmigration

---

## 6. Testfall

### T-L01: Skapa lokalt event
| Steg | Förväntat |
|------|-----------|
| Klicka [+ Skapa nytt] i Discovery-vyn | Dialog "Skapa lokalt event" öppnas |
| Fyll i namn: "Sillfest Kassa 1", intäktsfördelning 10/85/5 | Fälten accepteras |
| Klicka [Skapa event] | `events/local-{uuid}/metadata.json` skapas |
| Discovery-vyn uppdateras | Nytt lokalt event syns med status 📭 Tom |

### T-L02: Registrera köp på lokalt event
| Steg | Förväntat |
|------|-----------|
| Välj lokalt event → [Öppna kassa] | Kassavyn öppnas |
| Registrera 3 köp: säljare 5 (100kr), säljare 5 (50kr), säljare 8 (200kr) | Items visas i kassavyn |
| Tryck [Kontant] | Köpet sparas i `pending_items.jsonl` |
| Kontrollera JSONL-filen | 3 rader med korrekt JSON, ULID-baserade itemId/purchaseId |
| Gå tillbaka till Discovery | Eventet visar "3 köp", status 🟢 Aktiv |

### T-L03: Kassörflödet – tangentbord
| Steg | Förväntat |
|------|-----------|
| Markören startar i säljnummerfältet | Fokus korrekt |
| Skriv "5" → Tab/Enter | Fokus flyttar till prisfältet |
| Skriv "100" → Enter | Item läggs till. Fokus tillbaka till säljnummerfältet |
| Upprepa 3× | 3 items i listan |

> **Tangentbordsflödet är kritiskt** och får aldrig brytas (se `AGENTS.md`).

### T-L04: JSONL-import (multi-kassör)
| Steg | Förväntat |
|------|-----------|
| Kassa A: registrera 5 köp → JSONL | `pending_items.jsonl` innehåller 5 rader |
| Kassa B: registrera 3 köp → JSONL | `pending_items.jsonl` innehåller 3 rader |
| I Kassa A: Historik → Importera JSONL → välj Kassa B:s fil | 3 nya items importeras |
| Totalt 8 items i historiken | Korrekt |
| Importera Kassa B:s fil igen | 0 nya items (duplikater avvisade via itemId) |
| Fortfarande 8 items | Korrekt, dedup fungerar |

### T-L05: Migration CSV → JSONL
| Steg | Förväntat |
|------|-----------|
| Befintlig `loppiskassan.csv` med 10 items finns | |
| Starta app (v3.0) | `MigrationHelper.migrateIfNeeded()` körs |
| `events/local-migrated-{uuid}/` skapad | `metadata.json` + `pending_items.jsonl` med 10 items |
| `loppiskassan.csv` raderad | Borta, liksom `.backup.*`-filer |
| Popup: "Migration genomförd" | |

### T-L06: Intäktsfördelning per lokalt event
| Steg | Förväntat |
|------|-----------|
| Skapa lokalt event med split 15/80/5 | metadata.json sparar 15/80/5 |
| Öppna kassan, registrera köp | Intäktsfördelningen visas korrekt i UI |
| Historik → Betala ut säljare 5 | Utbetalning beräknas med 80% till säljare |

### T-L07: Flera lokala events samtidigt
| Steg | Förväntat |
|------|-----------|
| Skapa "Kassa 1" och "Kassa 2" | Bägge skapas OK |
| Registrera 5 köp på Kassa 1 | JSONL skrivs till `events/local-{uuid-1}/` |
| Byt till Kassa 2, registrera 3 köp | JSONL skrivs till `events/local-{uuid-2}/` |
| Byt tillbaka till Kassa 1 | Visar 5 köp (inte 3, inte 8) |
| Discovery visar bägge med rätt antal | Kassa 1: 5 köp, Kassa 2: 3 köp |

### T-L08: Utbetalning och redovisning
| Steg | Förväntat |
|------|-----------|
| Registrera köp: säljare 5 (100+50), säljare 8 (200) | Totalt 350 SEK |
| Historik → Filtrera säljare 5 | Visar 2 items, total 150 SEK |
| Betala ut | Items markeras som utbetalda |
| Filtrera Utbetalt: Nej | Visar säljare 8 (200 SEK) |
| Betala ut säljare 8 | Alla items utbetalda |

### Testmatris: Eventtyp × Funktion

| Funktion | Online-event | Lokalt event |
|----------|-------------|--------------|
| Registrera köp | ✅ + bakgrundsupload | ✅ Bara lokalt |
| Historik/utbetalning | ✅ | ✅ |
| JSONL-import | ✅ | ✅ |
| Auto-refresh | ✅ (från backend) | ❌ (lokalt) |
| Bulk-upload till backend | ❌ (redan online) | ✅ (→ [Issue 002](002-bulk-upload-jsonl.md)) |

---

## 7. Uppdatering av manual (`docs/manual_v2.md` → `docs/manual_v3.md`)

### 7.1 Ta bort: "Importera kassa" (CSV)
Det gamla CSV-importavsnittet tas bort och ersätts med nytt avsnitt om JSONL-import.

### 7.2 Nytt avsnitt: "Lokala events"

```markdown
Lokala events
--------------
Skapa lokala events direkt i LoppisKassan — utan internet eller iLoppis-konto.

### Skapa ett lokalt event
1. I Upptäck-fliken, klicka på **[+ Skapa nytt]** under "Lokala events"
2. Fyll i namn (t.ex. "Sillfest Kassa 1")
3. Ange intäktsfördelning (standard: 10/85/5)
4. Klicka **[Skapa event]**

### Använda flera kassor
Skapa ett lokalt event per kassör:
- "Sillfest Kassa 1" för kassör 1
- "Sillfest Kassa 2" för kassör 2

Varje event har sin egen datalagring och blandas inte ihop.

### Importera data från annan kassa
1. Gå till Historik-fliken
2. Klicka **[Importera JSONL]**
3. Välj en eller flera `.jsonl`-filer från andra kassor
4. Dubbletter avvisas automatiskt (baserat på itemId)
```

### 7.3 Nytt avsnitt: "Automatisk uppdatering"

```markdown
Automatisk uppdatering av eventlista
--------------------------------------
Online-events från iLoppis hämtas automatiskt var 60:e sekund.
En 🔄-ikon visas medan uppdatering pågår. Om nätverket inte
fungerar behålls den senaste listan och en varningsikon visas.
```

### 7.4 Nytt avsnitt: "Migration från v2"

```markdown
Uppgradering från v2
---------------------
Vid första start med v3.0 konverteras din befintliga data automatiskt
från CSV till JSONL-format. Din data finns kvar — bara lagringsformatet
ändras. En popup meddelar dig när migrationen är klar.

⚠️ Nedgradering till v2 stöds inte. Gör en backup innan uppgradering.
```

---

## 8. Öppna frågor

### 8.1 Rensning av gamla lokala events
**Förslag:**
- Manuell rensning: högerklicka → [Radera lokalt event]
- Bekräftelsedialog: "X items kommer att raderas. Fortsätt?"
- Ingen automatisk rensning

### 8.2 Export till CSV (lokalt event)
**Scenario:** Arrangörer vill analysera i Excel

**Lösning:** Högerklicka lokalt event → [Exportera till CSV]
- Genererar `{eventnamn}-{datum}.csv`
- Standard CSV-format

### 8.3 Stänga lokalt event
**Scenario:** Markera att loppisen är slut

**Lösning:** Högerklicka → [Stäng event]
- Ändrar status till 🔒 Stängd
- Kassavyn kan inte öppnas (skydd mot oavsiktlig registrering)
- Kan återöppnas: högerklicka → [Återöppna]

---

## 9. Implementationsplan

### Sprint 1: JSONL-lagring
- [ ] `PendingItemsStore` (Java-version av Android-koden)
- [ ] `LocalEventRepository` för metadata.json-hantering
- [ ] `JsonlHelper` för serialisering/deserialisering
- [ ] `MigrationHelper` för engångs CSV → JSONL
- [ ] JUnit-tester: T-L01, T-L02, T-L05

### Sprint 2: Lokala events i Discovery-vy
- [ ] `DiscoveryTabController` — kombinerad lista (online + lokala)
- [ ] UI: visa lokala events i tabell med statusikoner
- [ ] Dialog: `CreateLocalEventDialog`
- [ ] Auto-refresh: `ScheduledExecutorService` 60s
- [ ] JUnit-tester: T-L03, T-L06, T-L07

### Sprint 3: Import + redovisning
- [ ] JSONL-import i `HistoryTabController` (ersätter CSV-import)
- [ ] Utbetalning/redovisning per lokalt event
- [ ] JUnit-tester: T-L04, T-L08
- [ ] Uppdatera `docs/manual_v3.md`
- [ ] Uppdatera lokaliseringsfiler: `sv.json`, `en.json`

---

## 10. Framtida förbättringar (ej del av denna issue)

1. **Bulk-upload till iLoppis-backend** — [Issue 002](002-bulk-upload-jsonl.md)
2. **Multi-event kassaläge** — växla mellan events utan omstart
3. **Real-time sync mellan kassörer** — WebSocket-baserad synk
4. **Offline-först design** — cache vendor-listor och events för 100% offline

---

**Dokumentslut**
