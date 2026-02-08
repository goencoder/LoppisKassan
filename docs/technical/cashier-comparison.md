# Kassasystem – Jämförelse och Analys

**Skapad:** 2026-02-08  
**Syfte:** Dokumentera skillnader mellan webb-, Android- och Java-kassaapplikationer, särskilt fokus på offline-stöd inför refaktorering av LoppisKassan.

---

## Sammanfattning

| Plattform | Online-stöd | Offline-stöd | Persistent lagring | Bakgrundssync | ID-generering |
|-----------|-------------|--------------|-------------------|---------------|---------------|
| **Webb** | ✅ | ❌ | Ingen (sessionState) | ❌ | Ingen (backend genererar) |
| **Android** | ✅ | ✅ | JSONL-filer (per event) | ✅ (WorkManager) | ULID (klient) |
| **Java-app** | ✅ | ✅ | CSV-fil (global) | ⚠️ (primitiv tråd) | ULID (klient) |

**Rekommendation:** Java-appen bör migreras till Android-modellen med JSONL-baserad lagring per event och robust bakgrundssync.

---

## 1. Webb (React/JavaScript)

### 1.1 Arkitektur

**Fil:** `frontend/src/components/cashier/CashierView.jsx`

- **State management:** React hooks (`useState`, `useEffect`)
- **Lagring:** Endast i minne (session state)
- **Nätverkskrav:** Måste vara online för att fungera
- **API-klient:** Axios via `SoldItemsService.js`

### 1.2 Försäljningsflöde

```
Användare matar in säljare → pris → OK
    ↓
Validering lokalt (säljare finns i listan?)
    ↓
Lägg till i transactions-array (React state)
    ↓
Användare klickar "Kontant" eller "Swish"
    ↓
POST /v1/events/{eventId}/sold-items
    - purchaseId: tomt (backend genererar)
    - itemId: tomt (backend genererar)
    - seller: antal
    - price: SEK
    - paymentMethod: "KONTANT" | "SWISH"
    ↓
Backend svarar med acceptedItems + rejectedItems
    ↓
Vid framgång: Visa kvitto, rensa transaktionslistan
Vid delvis framgång: Visa varning för avvisade items
Vid fel: Visa felmeddelande
```

### 1.3 ID-generering

**Backend ansvarar för:**
- `itemId` (UUID)
- `purchaseId` (ULID – grupperar items i samma köp)
- `soldTime` (serverns timestamp om inte skickad)

**Klienten skickar:**
```json
{
  "items": [
    {
      "seller": 42,
      "price": 50,
      "paymentMethod": "SWISH"
    }
  ]
}
```

### 1.4 Offline-stöd

❌ **Inget offline-stöd**

- Vid nätverksfel: användaren får felmeddelande
- Ingen lokal lagring av misslyckade försäljningar
- Ingen retry-mekanism

### 1.5 Validering

**Klientsidan:**
- Säljarnummer finns i förladdad lista (från `/v1/events/{eventId}/vendors`)
- Pris > 0

**Backend:**
- Säljare är godkänd för eventet
- Pris inom gränsvärden (100 ≤ pris ≤ 100000 SEK enligt proto)
- Event är öppet (OPEN state)

---

## 2. Android (Kotlin/Jetpack Compose)

### 2.1 Arkitektur

**Filer:**
- `CashierViewModel.kt` – Business logic + state management
- `CashierScreen.kt` – UI composition (Jetpack Compose)
- `PendingItemsStore.kt` – Persistent JSONL-lagring
- `SoldItemsSyncWorker.kt` – Bakgrundssync (WorkManager)

**Lagring:** `<filesDir>/events/{eventId}/pending_items.jsonl`

### 2.2 Försäljningsflöde (Online Mode)

```
Användare matar in säljare → pris → "Slutför köp"
    ↓
STEG 1: Generera IDs (KLIENT-SIDAN)
    - purchaseId = ULID.random()
    - itemId = ULID.random() (per item)
    ↓
STEG 2: SPARA LOKALT FÖRST (KRITISKT!)
    - PendingItemsStore.appendItems() [BLOCKING]
    - Fil: events/{eventId}/pending_items.jsonl
    - Rad = pending, raderad rad = uppladdad
    ↓
STEG 3: Rensa UI omedelbart (optimistic UI)
    - Töm seller/price-fält
    - Återställ transaktionslista
    - Fokusera på seller-fält
    - Redo för nästa köp – inget kvitto visas
    ↓
STEG 4: Bakgrundsuppladdning (WorkManager)
    - SoldItemsSyncWorker.enqueueNow()
    - Best-effort sync, återförsök automatiskt vid fel
```

### 2.3 ID-generering

**Klient-genererade IDs (ULID):**
```kotlin
import com.guepardoapps.kulid.ULID

val itemId = ULID.random()      // 26 tecken: 01KF9A3M5GT7F10X0GBT60MPDJ
val purchaseId = ULID.random()  // Grupperar items i samma köp
```

**Kollisionssäkerhet:**
- Säkert för parallell användning med många kassörer (testat med 10+ samtidiga)
- Kollisionsrisk: ~0.00000000000000004% även om genererat i samma millisekund
- MongoDB unique index (event_id, item_id) ger extra skydd
- Teoretisk duplikat → backend avvisar med `DUPLICATE_RECEIPT` error code

### 2.4 JSONL-format (pending_items.jsonl)

```jsonl
{"itemId":"01HW1K2M3N4P5Q6R7S","purchaseId":"01HW1K2M3N4P5Q6R7S","sellerId":42,"price":50,"paymentMethod":"CASH","soldTime":"2026-01-18T14:30:00Z","errorText":""}
{"itemId":"01HW1K2M3N4P5Q6R7T","purchaseId":"01HW1K2M3N4P5Q6R7S","sellerId":42,"price":75,"paymentMethod":"CASH","soldTime":"2026-01-18T14:30:00Z","errorText":""}
```

**Fältbeskrivningar:**
| Fält | Typ | Beskrivning |
|------|-----|-------------|
| `itemId` | String (ULID) | Unikt item-ID, klient-genererat |
| `purchaseId` | String (ULID) | Grupperar items i samma köp |
| `sellerId` | Int | Säljarnummer |
| `price` | Int | Pris i SEK (heltal) |
| `paymentMethod` | String | "CASH" eller "SWISH" |
| `soldTime` | String (ISO-8601) | Försäljningstid |
| `errorText` | String | Fel från server (tom = inget fel) |

**Radhantering:**
- Rad finns = pending (väntar på uppladdning)
- Rad borttagen = uppladdad framgångsrikt
- Rad med errorText = avvisad av backend (behöver åtgärd)

### 2.5 Bakgrundssync (WorkManager)

**Fil:** `SoldItemsSyncWorker.kt`

```kotlin
// Triggad automatiskt efter varje köp
SoldItemsSyncWorker.enqueueNow()

// Läser pending_items.jsonl
val items = PendingItemsStore.readAll()

// Grupperar per purchaseId
val byPurchase = items.groupBy { it.purchaseId }

// Upload per köp (parallellt/sekvensiellt beroende på impl)
for ((purchaseId, purchaseItems) in byPurchase) {
    val response = api.createSoldItems(eventId, purchaseItems)
    
    // Accepterade: radera från fil
    val acceptedIds = response.acceptedItems.map { it.itemId }.toSet()
    PendingItemsStore.updateItems(purchaseId) { item ->
        if (item.itemId in acceptedIds) null else item
    }
    
    // Avvisade: uppdatera errorText
    response.rejectedItems.forEach { rejected ->
        PendingItemsStore.updateItems(purchaseId) { item ->
            if (item.itemId == rejected.item.itemId) {
                item.copy(errorText = rejected.reason)
            } else item
        }
    }
}
```

**Retry-strategi:**
- WorkManager automatisk retry med exponentiell backoff
- Max 3 försök per 15 minuter
- Ger upp efter 24 timmar om fortfarande misslyckas
- Användaren ser varningar i UI om pending items växer

### 2.6 Offline-stöd

✅ **Fullt offline-stöd**

**Scenario 1: Tillfälligt nätverksavbrott**
1. Försäljning sparas lokalt (pending_items.jsonl)
2. UI visar varning: "X köp väntar på uppladdning"
3. WorkManager försöker synca i bakgrunden
4. När nätverk återkommer: automatisk sync
5. Framgångsrik sync: rader raderas från fil

**Scenario 2: Längre offline-period**
- Alla försäljningar sparas i JSONL
- Appen fortsätter fungera normalt
- Vid uppstart/nätverksåterkomst: batch-upload av alla pending
- Partiallt framgång hanteras: accepterade raderas, avvisade får errorText

**Scenario 3: Backend-avvisning**
- Item med errorText kvarstår i fil
- UI visar kritisk varning med detaljer
- Användaren måste åtgärda (t.ex. ogiltigt säljarnummer)

---

## 3. Java-app (LoppisKassan)

### 3.1 Arkitektur

**Filer:**
- `CashierTabController.java` – Business logic
- `CashierTabPanel.java` – Swing UI
- `FileHelper.java` – CSV-filhantering
- `ApiHelper.java` – REST API-klient

**Lagring:** `~/.loppiskassan/loppiskassan.csv` (GLOBAL för alla events!)

### 3.2 Försäljningsflöde (iLoppis-event)

```
Användare matar in säljare → pris → Enter
    ↓
Validering lokalt (JSON-lista av godkända säljare)
    ↓
Lägg till i items-list (ArrayList<V1SoldItem>)
    ↓
Användare klickar "Kontant" eller "Swish"
    ↓
STEG 1: Generera IDs (KLIENT-SIDAN)
    - purchaseId = UlidGenerator.generate()
    - itemId = UlidGenerator.generate() (per item)
    ↓
STEG 2: Försök POST /v1/events/{eventId}/sold-items
    - Om framgång: markera items.uploaded = true
    - Om nätverksfel: degradedMode = true, items.uploaded = false
    - Om partiell framgång: accepterade = true, avvisade = false
    ↓
STEG 3: SPARA LOKALT (alltid, efter nätverksförsök)
    - FileUtils.appendSoldItems(items)
    - CSV-format: purchaseId,itemId,soldTime,seller,price,collectedTime,paymentMethod,uploaded
    ↓
STEG 4: Rensa UI
    - items.clear()
    - view.clearView()
    ↓
STEG 5: Om degradedMode = true, starta bakgrundstråd
    - new Thread(() -> pushLocalUnsyncedRecords()).start()
    - Om framgång: degradedMode = false
```

### 3.3 CSV-format (loppiskassan.csv)

```csv
purchaseId,itemId,soldTime,seller,price,collectedTime,paymentMethod,uploaded
01HW1K2M3N4P5Q6R7S,01HW1K2M3N4P5Q6R7T,2026-01-18T14:30:00,42,50,null,Kontant,true
01HW1K2M3N4P5Q6R7S,01HW1K2M3N4P5Q6R7U,2026-01-18T14:30:00,42,75,null,Kontant,true
```

**Fältbeskrivningar:**
| Fält | Typ | Beskrivning |
|------|-----|-------------|
| `purchaseId` | String (ULID) | Grupperar items i samma köp |
| `itemId` | String (ULID) | Unikt item-ID |
| `soldTime` | LocalDateTime | Försäljningstid (lokal tid) |
| `seller` | Int | Säljarnummer |
| `price` | Int | Pris i SEK |
| `collectedTime` | LocalDateTime/null | När säljaren hämtade pengar (offline-mode) |
| `paymentMethod` | Enum | Kontant/Swish |
| `uploaded` | Boolean | true = uppladdad till backend |

### 3.4 Offline-stöd (iLoppis-event med nätverksavbrott)

⚠️ **Partiellt offline-stöd (primitiv implementering)**

**Degraded Mode:**
```java
private static boolean degradedMode = false;

if (isLikelyNetworkError(ex)) {
    degradedMode = true;
    Popup.warn("warning.degraded_mode");
}
```

**Bakgrundssynk (primitiv):**
```java
new Thread(() -> {
    boolean success = pushLocalUnsyncedRecords();
    if (success) {
        degradedMode = false;
    }
}).start();
```

**Svagheter:**
1. ❌ Endast EN retry-försök per checkout
2. ❌ Ingen persistent retry-kö (om appen stängs ner försvinner försöken)
3. ❌ Ingen exponentiell backoff
4. ❌ Tråd-säkerhet beroende av `synchronized(lock)` – riskabelt
5. ❌ Ingen UI-feedback om pending uploads utanför degraded mode

### 3.5 Offline-mode (dedikerat offline-event)

✅ **Fullt offline-stöd (separat event-typ)**

**Konfiguration:**
```java
ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(true);
ConfigurationStore.EVENT_ID_STR.set("offline");
```

**Flöde:**
1. Användaren väljer "Offline event" vid start
2. Alla försäljningar sparas direkt till CSV (ingen nätverksuppladdning)
3. `uploaded = false` för alla items
4. Säljarlista är tom – ingen validering
5. Revenue split är redigerbart (standardvärden: 85% säljare, 10% arrangör, 5% plattform)

**Efterbehandling:**
- Manuell CSV-export → skicka till arrangör
- Eller: manuell bulk-import via webb-gränssnitt senare

---

## 4. Jämförelse: Nyckelfunktioner

### 4.1 ID-generering

| Plattform | purchaseId | itemId | Genereras av |
|-----------|-----------|--------|--------------|
| Webb | ❌ tom | ❌ tom | Backend (UUID/ULID) |
| Android | ✅ ULID | ✅ ULID | Klient |
| Java-app | ✅ ULID | ✅ ULID | Klient |

**Anledning till klient-generering:**
- **Idempotens:** Samma request kan skickas flera gånger utan dubbletter
- **Offline-stöd:** ID finns innan backend-kontakt
- **Spårbarhet:** Loggar kan korrelera klient-ID med server-svar

### 4.2 Lokal lagring

| Plattform | Format | Plats | Scope |
|-----------|--------|-------|-------|
| Webb | Ingen | - | - |
| Android | JSONL | `<filesDir>/events/{eventId}/` | **Per event** |
| Java-app | CSV | `~/.loppiskassan/` | **Global (alla events!)** |

**Problem med global CSV (Java-app):**
1. ❌ Blandade försäljningar från olika events i samma fil
2. ❌ Svårt att identifiera vilka rader som hör till vilket event
3. ❌ Risk för dataläckage om filen delas av misstag
4. ❌ Svårt att rensa gammal data (måste filtrera per eventId som inte sparas)

**Rekommendation:**
- Migrera till Android-modellen: `~/.loppiskassan/events/{eventId}/pending_items.jsonl`
- Separata filer per event för isolering och säkerhet

### 4.3 Bakgrundssync

| Plattform | Mekanism | Retry | Persistent kö | UI-feedback |
|-----------|----------|-------|---------------|-------------|
| Webb | Ingen | - | - | - |
| Android | WorkManager | ✅ Exponentiell backoff (max 24h) | ✅ Ja | ✅ Varning + räknare |
| Java-app | Thread | ❌ Ett försök | ❌ Nej (tråd dör) | ⚠️ Degraded mode popup |

**Android WorkManager fördelar:**
- Överlever app-omstart
- Hanterar batterisparläge
- Automatisk retry vid nätverksåterkomst
- Respekterar systemresurser

**Java-app svagheter:**
- Thread dör om appen stängs – förlorar retry-kön
- Ingen smart retry-strategi
- Ingen observerbar state för pending uploads

### 4.4 Felhantering

| Scenario | Webb | Android | Java-app (iLoppis) | Java-app (offline) |
|----------|------|---------|--------------------|--------------------|
| **Nätverksfel vid checkout** | ❌ Fel, data förlorad | ✅ Sparat, bakgrundssync | ⚠️ Degraded mode, ett retry | N/A |
| **Backend-avvisning (ogiltlig säljare)** | ⚠️ Popup, data förlorad | ✅ errorText i fil, UI-varning | ⚠️ Partial upload warning | N/A |
| **App kraschar under checkout** | ❌ Data förlorad | ✅ Sparat i JSONL | ❌ Data förlorad (om före CSV-save) | ✅ Sparat i CSV |
| **Långvarigt offline** | ❌ Oanvändbar | ✅ Fungerar normalt | ⚠️ Fungerar men primitiv sync | ✅ Fungerar (offline-mode) |

---

## 5. Kritiska skillnader: Android vs Java-app

### 5.1 Spara först, ladda upp sedan

**Android:**
```kotlin
// STEP 1: Spara FÖRST (blocking)
PendingItemsStore.appendItems(items)  // Väntar tills skriven till disk

// STEP 2: Rensa UI omedelbart
state.transactions = []
state.sellerNumber = ""

// STEP 3: Bakgrund (non-blocking)
SoldItemsSyncWorker.enqueueNow()
```

**Java-app:**
```java
// STEP 1: Försök ladda upp FÖRST (blocking)
ProgressDialog.show(() -> saveItemsToWeb(items))  // Kan ta 5+ sekunder

// STEP 2: Spara lokalt EFTER
FileUtils.appendSoldItems(items)

// STEP 3: Rensa UI
items.clear()
```

**Konsekvens:**
- Android: Användaren ser omedelbar respons (optimistic UI)
- Java-app: Användaren väntar på nätverksförfrågan (progress dialog)

**Risk i Java-app:**
- Om appen kraschar under `saveItemsToWeb()` → data förlorad (inte sparad lokalt ännu)

### 5.2 File scope: Per event vs Global

**Android:**
```
<filesDir>/events/
├── {eventId-1}/
│   ├── pending_items.jsonl
│   ├── rejected_purchases.jsonl
│   └── sold_items.jsonl (backup)
├── {eventId-2}/
│   └── pending_items.jsonl
```

**Java-app:**
```
~/.loppiskassan/
├── loppiskassan.csv  (ALLA events blandade!)
└── config.properties
```

**Problem:**
- CSV innehåller rader från olika events
- Ingen eventId-kolumn → omöjligt att filtrera
- Måste läsa hela filen för att hitta specifika events försäljningar

### 5.3 Retry-strategier

**Android (WorkManager):**
```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

val request = OneTimeWorkRequestBuilder<SoldItemsSyncWorker>()
    .setConstraints(constraints)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
    .build()
```

- Automatisk retry vid nätverksåterkomst
- Exponentiell backoff: 15min → 30min → 1h → ...
- Max 24h, sedan ger upp

**Java-app (primitiv Thread):**
```java
new Thread(() -> {
    boolean success = pushLocalUnsyncedRecords();
    if (success) degradedMode = false;
}).start();
```

- Endast ETT försök
- Om misslyckas: tråden dör, ingen mer retry
- Om appen stängs: förlorar kön

### 5.4 UI-feedback för pending uploads

**Android:**
```kotlin
// CashierViewModel.kt
val pendingItemsCount by lazy { 
    PendingItemsStore.readAll().groupBy { it.purchaseId }.size 
}

// CashierScreen.kt
if (pendingItemsCount > 0) {
    WarningBanner(
        text = "$pendingItemsCount köp väntar på uppladdning",
        severity = if (hasErrors) Severity.CRITICAL else Severity.INFO
    )
}
```

**Java-app:**
- Ingen persistent räknare
- Endast popup vid aktivering av degraded mode
- Ingen varning om appen startas om med unsynced data i CSV

---

## 6. Rekommendationer för Java-app refaktorering

### 6.1 Prioriterade förändringar

#### 1. Migrera till JSONL per event (HÖSGT PRIO)

**Nuvarande:**
```
~/.loppiskassan/loppiskassan.csv  (global)
```

**Ny struktur:**
```
~/.loppiskassan/events/
├── {eventId}/
│   ├── pending_items.jsonl
│   ├── rejected_purchases.jsonl  (optional)
│   └── metadata.json
```

**Fördelar:**
- Isolering per event
- Enklare rensning av gammal data
- Kompatibel med Android-modellen
- GDPR-vänlig (radera event-mapp = radera all data)

#### 2. Spara först, ladda upp sedan (HÖSGT PRIO)

**Ny flödesordning:**
```java
checkout(V1PaymentMethod method) {
    String purchaseId = UlidGenerator.generate();
    
    // STEP 1: Spara lokalt FÖRST (blocking)
    synchronized (lock) {
        PendingItemsStore.appendItems(eventId, items);
    }
    
    // STEP 2: Rensa UI omedelbart
    items.clear();
    view.clearView();
    
    // STEP 3: Bakgrundssync (non-blocking)
    if (isOnlineMode()) {
        SyncScheduler.enqueueSyncNow(eventId);
    }
}
```

#### 3. Implementera robust bakgrundssync (MEDEL PRIO)

**Använd ScheduledExecutorService istället för Thread:**
```java
public class SyncScheduler {
    private static final ScheduledExecutorService executor = 
        Executors.newSingleThreadScheduledExecutor();
    
    public static void enqueueSyncNow(String eventId) {
        executor.submit(() -> syncPendingItems(eventId));
    }
    
    private static void syncPendingItems(String eventId) {
        List<V1SoldItem> pending = PendingItemsStore.readAll(eventId);
        
        for (Map.Entry<String, List<V1SoldItem>> purchase : groupByPurchaseId(pending)) {
            try {
                V1CreateSoldItemsResponse resp = uploadPurchase(eventId, purchase.getValue());
                handlePartialSuccess(eventId, purchase.getKey(), resp);
            } catch (NetworkException e) {
                // Retry med exponential backoff
                scheduleRetry(eventId, purchase.getKey(), retryCount + 1);
            }
        }
    }
}
```

#### 4. UI-feedback för pending uploads (MEDEL PRIO)

**Lägg till status-bar:**
```java
// CashierTabPanel.java
private JLabel pendingUploadsLabel;

private void updatePendingUploadsCount() {
    int count = PendingItemsStore.getPendingPurchaseCount(eventId);
    if (count > 0) {
        pendingUploadsLabel.setText(
            LocalizationManager.tr("cashier.pending_uploads", count)
        );
        pendingUploadsLabel.setForeground(Color.ORANGE);
    } else {
        pendingUploadsLabel.setText("");
    }
}
```

### 6.2 Bevara offline-mode (separat event)

Nuvarande offline-mode fungerar bra för:
- Loppis utan internetuppkoppling
- Testmiljöer
- Backup om backend är nere

**Förslag:** Behåll offline-mode men synkronisera beteendet:
- Använd samma JSONL-lagring
- Markera event som offline i metadata
- Tillåt manuell export/import av JSONL-fil

### 6.3 Backwards-kompatibilitet

**Migration-strategi för befintlig CSV:**
1. Vid första uppstart med ny version: läs `loppiskassan.csv`
2. Om ingen eventId-kolumn: migrera till `events/unknown/pending_items.jsonl`
3. Visa varning till användare: "Gammal data migrerad, kontrollera events/unknown/"
4. Ta backup av original-CSV som `loppiskassan.csv.backup`

---

## 7. Teknisk implementation: JSONL PendingItemsStore

### 7.1 Java-implementation (föreslagen)

```java
public class PendingItemsStore {
    private static final String BASE_DIR = ".loppiskassan/events";
    
    private final String eventId;
    private final Path jsonlFile;
    private final Object lock = new Object();
    
    public PendingItemsStore(String eventId) {
        this.eventId = eventId;
        Path eventsDir = Paths.get(System.getProperty("user.home"), BASE_DIR, eventId);
        eventsDir.toFile().mkdirs();
        this.jsonlFile = eventsDir.resolve("pending_items.jsonl");
    }
    
    public void appendItems(List<V1SoldItem> items) throws IOException {
        synchronized (lock) {
            try (BufferedWriter writer = Files.newBufferedWriter(jsonlFile, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (V1SoldItem item : items) {
                    String json = item.toJson();
                    writer.write(json);
                    writer.newLine();
                }
            }
        }
    }
    
    public List<V1SoldItem> readAll() throws IOException {
        synchronized (lock) {
            if (!Files.exists(jsonlFile)) {
                return Collections.emptyList();
            }
            
            return Files.lines(jsonlFile)
                .filter(line -> !line.isBlank())
                .map(line -> {
                    try {
                        return V1SoldItem.fromJson(line);
                    } catch (Exception e) {
                        log.warning("Skipping malformed line: " + e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
    }
    
    public void updateItems(String purchaseId, Function<V1SoldItem, V1SoldItem> updater) 
            throws IOException {
        synchronized (lock) {
            List<V1SoldItem> allItems = readAll();
            List<V1SoldItem> updated = allItems.stream()
                .map(item -> {
                    if (item.getPurchaseId().equals(purchaseId)) {
                        return updater.apply(item);
                    }
                    return item;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            // Rewrite entire file
            Files.write(jsonlFile, updated.stream()
                .map(V1SoldItem::toJson)
                .collect(Collectors.toList()));
        }
    }
    
    public void deleteByPurchaseId(String purchaseId) throws IOException {
        updateItems(purchaseId, item -> null);  // Return null = delete
    }
}
```

### 7.2 Användning i CashierTabController

```java
private PendingItemsStore pendingStore;

@Override
public void registerView(CashierPanelInterface view) {
    this.view = view;
    String eventId = ConfigurationStore.EVENT_ID_STR.get();
    this.pendingStore = new PendingItemsStore(eventId);
}

private void checkout(V1PaymentMethod paymentMethod) {
    String purchaseId = UlidGenerator.generate();
    LocalDateTime now = LocalDateTime.now();
    
    prepareItemsForCheckout(items, purchaseId, paymentMethod, now);
    
    // STEP 1: Save locally FIRST
    try {
        pendingStore.appendItems(items);
    } catch (IOException e) {
        Popup.ERROR.showAndWait("Failed to save locally", e.getMessage());
        return;
    }
    
    // STEP 2: Clear UI immediately
    items.clear();
    view.clearView();
    
    // STEP 3: Background sync (if online)
    if (!ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false)) {
        SyncScheduler.enqueueSyncNow(ConfigurationStore.EVENT_ID_STR.get());
    }
}
```

---

## 8. Sammanfattning: Vad Java-appen ska lära av Android

### 8.1 Kritiska lärdomar

| Aspekt | Android-modell | Java-app (aktuell) | Action |
|--------|---------------|-------------------|---------|
| **Lagring** | JSONL per event | CSV global | ✅ Migrera till JSONL per event |
| **Flödesordning** | Spara → Rensa UI → Synk | Synk → Spara → Rensa UI | ✅ Vända ordning |
| **Bakgrundssync** | WorkManager (persistent) | Thread (ephemeral) | ✅ ScheduledExecutorService + persistent queue |
| **UI-feedback** | Status-bar + varningar | Endast degraded popup | ✅ Visa pending uploads-räknare |
| **Retry-logik** | Exponentiell backoff, max 24h | Ett försök | ✅ Implementera smart retry |
| **Felhantering** | errorText i fil + UI-varning | Partial upload popup | ✅ Persistent error state |

### 8.2 Offline-stöd: Två scenarios

**Scenario 1: iLoppis-event med tillfälligt nätverksavbrott**
- Android-modell: Spara lokalt, synk i bakgrund
- Java-app (nu): Degraded mode, primitiv sync
- **Förbättring:** Implementera Android-liknande persistent sync-kö

**Scenario 2: Dedikerat offline-event**
- Android-modell: Inget offline-event-koncept (ännu)
- Java-app (nu): Fungerar bra, separata konfigurering
- **Behåll:** Offline-mode är värdefullt för edge cases

### 8.3 Migrationsstrategi

**Fas 1 (Sprint 1):**
- Implementera `PendingItemsStore` med JSONL per event
- Migrera från global CSV till event-specifika filer
- Backward compatibility: läs gammal CSV vid uppstart

**Fas 2 (Sprint 2):**
- Implementera `SyncScheduler` med ScheduledExecutorService
- Exponentiell backoff retry-logik
- Persistent error state (errorText i JSONL)

**Fas 3 (Sprint 3):**
- UI-förbättringar: pending uploads-räknare
- Varningar för kritiska fel (ogiltlig säljare etc.)
- Manuell retry-knapp i UI

---

## 9. Appendix: API-format

### 9.1 POST /v1/events/{eventId}/sold-items

**Request:**
```json
{
  "items": [
    {
      "itemId": "01HW1K2M3N4P5Q6R7S8T9V0WX1",  // ULID (klient-genererad)
      "purchaseId": "01HW1K2M3N4P5Q6R7S8T9V0WXY",  // ULID (grupperar köp)
      "seller": 42,
      "price": 50,
      "paymentMethod": "SWISH"  // "SWISH" | "KONTANT"
    }
  ]
}
```

**Response (framgång):**
```json
{
  "acceptedItems": [
    {
      "itemId": "01HW1K2M3N4P5Q6R7S8T9V0WX1",
      "eventId": "d50e8356-8deb-428a-a588-afaa0d4f1214",
      "cashierAlias": "Kassa 1",
      "purchaseId": "01HW1K2M3N4P5Q6R7S8T9V0WXY",
      "seller": 42,
      "price": 50,
      "paymentMethod": "SWISH",
      "soldTime": "2026-01-18T14:30:00Z",
      "collectedBySeller": false,
      "isArchived": false
    }
  ],
  "rejectedItems": []
}
```

**Response (partiell framgång):**
```json
{
  "acceptedItems": [ /* ... */ ],
  "rejectedItems": [
    {
      "item": {
        "itemId": "01HW1K2M3N4P5Q6R7S8T9V0WX2",
        "seller": 999
      },
      "reason": "seller 999 is not approved for this event",
      "errorCode": "INVALID_SELLER"
    }
  ]
}
```

### 9.2 Error Codes (från proto)

```protobuf
enum SoldItemErrorCode {
  SOLD_ITEM_ERROR_CODE_UNSPECIFIED = 0;
  INVALID_SELLER = 1;          // Säljare inte godkänd
  INVALID_PRICE = 2;           // Pris utanför gränser
  EVENT_NOT_OPEN = 3;          // Event inte öppet
  DUPLICATE_RECEIPT = 4;       // itemId redan finns
  UNAUTHORIZED = 5;            // Ingen API-nyckel/ogiltig
}
```

---

**Dokumentslut**
