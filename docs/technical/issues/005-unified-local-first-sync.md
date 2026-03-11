# Issue 005: Enhetlig local-first synkronisering

**Status:** Planering  
**Datum:** 2026-02-15  
**Syfte:** Förenkla synk-arkitekturen till "alltid skriv lokalt först, en enda tråd sköter fil-I/O + upload"

## Mål

1. **Local-first** – kassan skriver till JSONL direkt (`< 100 ms`), oavsett nätverk.
2. **En synktråd** – all fil-I/O och API-upload sker på en och samma bakgrundstråd (inga race conditions).
3. **En upload-pipeline** – samma logik för upload → classify → collateral-retry → uppdatera fil, oavsett var den triggas.
4. **Periodisk synk** – fortslöpande varje 30 s även när kassan är idle.
5. **Rejected items** – sparas i separat rejected-fil, tas bort ur pending-filen. Kvarvarande items skickas om.

---

## Nuläge – så det faktiskt fungerar

### Fyra separata upload-vägar

| # | Var | Klass / metod | Beskrivning |
|---|-----|---------------|-------------|
| 1 | **Checkout (direkt)** | `IloppisCashierStrategy.persistItems()` | API-upload FÖRST, lokal fil efteråt. Faller tillbaka till lokal JSONL vid nätverksfel. |
| 2 | **Bakgrundssynk (timer)** | `BackgroundSyncManager.attemptSync()` | 30 s-timer läser pending items, laddar upp, uppdaterar fil. |
| 3 | **Degraded catch-up** | `CashierTabController.pushLocalUnsyncedRecords()` | Egen upload-väg med `saveItemsToWeb()`, synkroniserad med `lock`. |
| 4 | **Historik "Uppdatera webb"** | `HistoryTabController.uploadSoldItems()` | Batch-upload i sub-batchar om 100 st med egen logik. |

### Upload-med-retry – duplicerad 3 gånger

Samma collateral-retry-mönster (upload → classify → identifiera invalid-seller → retry collateral items) finns implementerat separat i:

- `IloppisCashierStrategy.uploadPurchaseWithRetry()`
- `BackgroundSyncManager.uploadPurchaseGroupWithRetry()`
- `HistoryTabController.uploadPurchaseGroupWithRetry()`

### Trådmodell (nuvarande)

```
┌─ SwingWorker-tråd (ProgressDialog) ─────┐
│  persistItems() → API-upload (5s timeout)│
│  ↓ nätverksfel? → append JSONL           │
│  → Start BackgroundSyncManager           │
└──────────────────────────────────────────┘

┌─ Timer-tråd ("BackgroundSync-{eventId}") ┐
│  attemptSync() var 30:e sekund            │
│  läser pending JSONL → upload → skriv fil │
└──────────────────────────────────────────┘

┌─ Ad-hoc-tråd ("BackgroundSync-Now") ─────┐
│  triggerSyncNow() spawnar ny Thread       │
│  varje gång – obegränsat antal            │
│  KAN köra parallellt med Timer-tråden     │
└──────────────────────────────────────────┘

┌─ Degraded-tråd (finishCheckoutFlow) ─────┐
│  pushLocalUnsyncedRecords()               │
│  egen upload-logik med saveItemsToWeb()   │
└──────────────────────────────────────────┘
```

---

## Problem / delta mot önskat beteende

### P1: API-först, inte local-first

**Nuläge:** `IloppisCashierStrategy.persistItems()` gör API-upload FÖRST (5 s timeout). Om det lyckas sparas items till JSONL med `uploaded=true`. Om det misslyckas sparas de med `uploaded=false` som fallback.

**Önskat:** Append till JSONL direkt (< 100 ms). Upload sker helt i bakgrunden.

**Konsekvenser av nuläget:**
- Kassan blockeras av `ProgressDialog` i upp till 5 s per köp vid dåligt nät.
- Om appen kraschar efter API-svar men innan lokal skrivning → dataförlust.
- Inget `ProgressDialog` behövs alls om skrivning är lokal.

### P2: Flera trådar skriver till samma JSONL-fil utan synkronisering

**Nuläge:** `triggerSyncNow()` skapar en ny `Thread` varje gång. Timer-tråden och ad-hoc-trådar kan köra `attemptSync()` samtidigt. `attemptSync()` gör `store.readAll()` → mutation → `store.saveAll()` (TRUNCATE_EXISTING). Ingen mutex.

**Risk:**
- Tråd A truncatar filen medan tråd B läser → parse-fel eller tom lista.
- Tråd A skriver uppdaterade items, tråd B skriver över med gammal data → items tappar `uploaded=true`.
- `JsonlHelper.appendItems()` (från kassans checkout) kan ske samtidigt som `saveAll()` → nya items försvinner vid truncate.

### P3: `degradedMode` + `pushLocalUnsyncedRecords()` är i praktiken dead code

**Nuläge:** `degradedMode` sätts till `true` i `onFailure`-hanteraren i `checkout()`. Men `persistItems()` fångar nätverksfel internt och returnerar `true` (success), så `onFailure` körs aldrig för normala nätverksfel.

`pushLocalUnsyncedRecords()` — en helt egen upload-väg med `saveItemsToWeb()` + `FileUtils.saveSoldItems()` — triggas bara om `degradedMode == true`. I praktiken körs den extremt sällan (bara om OpenAPI-klienten kastar icke-`ApiException`).

### P4: Tre identiska upload-retry-implementationer

Samma mönster (upload → classify → isInvalidSeller → identifiera collateralIds → retry) finns i tre klasser. Kopierad logik som kan drifta isär vid ändringar.

### P5: HistoryTab har egen upload-pipeline

`HistoryTabController.uploadSoldItems()` har:
- Egen batch-logik (sub-batchar om 100)
- Egen rejected-hantering (tar bort från `allHistoryItems`)
- Sparar via `saveHistoryToFile()` (annan metod än `PendingItemsStore.saveAll()`)
- Egen auth-error-hantering (`AuthErrorHandler.handleAuthStatus()` direkt)

Denna pipeline är helt separat från `BackgroundSyncManager`.

### P6: Ingen garanti att synk körs vid omstart med kvarvarande pending

`BackgroundSyncManager.ensureRunning()` anropas vid:
- Register öppnas (DiscoveryTabController)
- Varje checkout (CashierTabController.finishCheckoutFlow)
- Auth-återställning (AuthErrorHandler)

Om appen startas om med pending items från föregående session, körs INTE synken förrän användaren öppnar registret igen. (Inget auto-start vid app-boot.)

---

## Föreslagen ändring

### Ny trådmodell

```
┌─ EDT (Swing) ────────────────────────────┐
│  checkout() → appendItems() direkt       │
│  → enqueue notis till synktråd           │
│  Kassan redo direkt (< 100 ms)           │
└──────────────────────────────────────────┘

┌─ EN synktråd (ScheduledExecutorService) ─┐
│  Periodisk (var 30 s) + "sync now"       │
│  1. Läs pending items från JSONL         │
│  2. Gruppera per purchaseId              │
│  3. Upload → classify → retry collateral │
│  4. Rejected → append rejected-fil       │
│  5. Uppdatera pending-fil               │
│  6. Notify UI (pending count, rejected)  │
│                                          │
│  ALL fil-I/O sker på denna tråd.         │
│  Kassans EDT appendar via synktråden     │
│  (eller med en kortvarig lås/append-op). │
└──────────────────────────────────────────┘
```

### Ändringsförslag per komponent

#### 1. `IloppisCashierStrategy.persistItems()`

| Nu | Efteråt |
|----|---------|
| API-upload först | Append till JSONL direkt (`uploaded=false`) |
| Vid success: append med `uploaded=true` | Trigger `BackgroundSyncManager.triggerSyncNow()` |
| Vid nätverksfel: append med `uploaded=false` | Return direkt (< 100 ms) |
| Wrappas i `ProgressDialog` | Ingen `ProgressDialog` behövs |

#### 2. `CashierTabController.checkout()`

| Nu | Efteråt |
|----|---------|
| iLoppis: `ProgressDialog.runTask()` (asynkron) | Synkron: `persistItems()` + `finishCheckoutFlow()` |
| `degradedMode` + `pushLocalUnsyncedRecords()` | Ta bort helt |
| `onFailure` → sätter `degradedMode` | Inte relevant |

#### 3. `BackgroundSyncManager`

| Nu | Efteråt |
|----|---------|
| `java.util.Timer` + ad-hoc `Thread`s | `ScheduledExecutorService` med en tråd |
| `triggerSyncNow()` → ny Thread varje gång | Submittar körning till executorn (seriekörning) |
| Ingen synkronisering på `attemptSync()` | Garanterad single-threaded av executor |
| Egen `uploadPurchaseGroupWithRetry()` | Delad `SoldItemsUploader.uploadWithRetry()` |
| `uploadItemsToApi()` duplicerad | Delar samma metod |

#### 4. Ny klass: `SoldItemsUploader` (extrahera)

En stateless hjälpklass med:
```java
UploadOutcome uploadPurchaseGroupWithRetry(String eventId, List<V1SoldItem> items)
```
Anropas från `BackgroundSyncManager.attemptSync()` och `HistoryTabController.uploadBatch()`.
Ersätter de tre duplicerade implementationerna.

#### 5. `HistoryTabController.uploadSoldItems()`

| Nu | Efteråt |
|----|---------|
| Egen upload pipeline | Trigger `BackgroundSyncManager.triggerSyncNow()` |
| Egen batch-logik (100 st) | Eller: behåll men använd `SoldItemsUploader` |
| Egen filskrivning | Läs/uppdatera via `PendingItemsStore` |

**Notera:** "Uppdatera webb"-knappen i historikfliken kan antingen:
- (a) Bara trigga BackgroundSyncManager (enklast), eller
- (b) Behålla manuell upload men använda samma `SoldItemsUploader` (mer kontroll, visar resultat direkt).

#### 6. Fil-I/O-serialisering

Kassans checkout (EDT) gör `appendItems()` → APPEND-mode (adderar i slutet).
Synktråden gör `readAll()` + `saveAll()` → TRUNCATE_EXISTING (omskrivning).

Dessa kan krocka om de körs samtidigt: ett kassaappend mellan `readAll` och `saveAll` försvinner vid truncate.

**Lösning:** Synktråden äger all mutation. Kassan skickar items till synktråden via en `ConcurrentLinkedQueue<List<V1SoldItem>>`:
1. Kassan: `syncManager.enqueue(items)` (trådsäker, < 1 ms).
2. Synktråden: vaknar → `dequeue()` → `appendItems()` → upload-loop.
3. All JSONL-läsning + skrivning sker enbart på synktråden.

Alternativ: behåll `appendItems()` på EDT (APPEND-mode är lågkolliderat), men lägg en `ReentrantLock` runt synk-trådens read-update-write-cykel som kassans append också tar.

---

## Ta-bort-lista

| Kod | Anledning |
|-----|-----------|
| `CashierTabController.degradedMode` | Konceptet försvinner med local-first |
| `CashierTabController.pushLocalUnsyncedRecords()` | Ersätts av BackgroundSyncManager |
| `CashierTabController.saveItemsToWeb()` | Ersätts av `SoldItemsUploader` |
| `CashierTabController.updateLocalItemsStatus()` | Används bara av ovan |
| `CashierTabController.lock` | Inte relevant längre |
| `IloppisCashierStrategy.uploadPurchaseWithRetry()` | Ersätts av `SoldItemsUploader` |
| `IloppisCashierStrategy.uploadItemsToApi()` | Flyttas till `SoldItemsUploader` |
| `BackgroundSyncManager.uploadPurchaseGroupWithRetry()` | Ersätts av `SoldItemsUploader` |
| `BackgroundSyncManager.uploadItemsToApi()` | Flyttas till `SoldItemsUploader` |
| `HistoryTabController.uploadPurchaseGroupWithRetry()` | Ersätts av `SoldItemsUploader` |
| `ProgressDialog.runTask()` i checkout-flödet | Inte nödvändig med lokal skrivning |

---

## Fil/tråd-ägarskap efter ändring

| Resurs | Ägare (tråd) | Åtkomst |
|--------|-------------|---------|
| `pending_items.jsonl` | Synktråden (ensamägare) | Inga andra trådar skriver |
| `rejected_purchases.jsonl` | Synktråden (append) | Läsning från EDT vid dialog |
| API-upload | Synktråden | Inga andra anrop |
| UI-notifieringar | EDT via `invokeLater` | Synktråden triggar |

---

## Statusbar-beteende (oförändrat, bekräftat fungerande)

| Tillstånd | Pending | Rejected | Statusbar |
|-----------|---------|----------|-----------|
| Allt uppladdat | 0 | 0 | 🟢 Ansluten |
| Väntar på synk | > 0 | 0 | 🟡 N poster väntar |
| Items rejected | 0 | > 0 | 🟢 + 🔴 N rejected |
| Offline | > 0 | ≥ 0 | 🟡 + ev 🔴 |

---

## Steg-för-steg implementationsplan

1. **Extrahera `SoldItemsUploader`** – flytta upload + classify + collateral-retry dit.
2. **Gör `BackgroundSyncManager` single-threaded** – byt Timer + ad-hoc-Thread till `ScheduledExecutorService(1)`. `triggerSyncNow()` → `executor.submit()`.
3. **Ändra `IloppisCashierStrategy.persistItems()` till local-first** – append JSONL direkt, trigga synk.
4. **Ändra `CashierTabController.checkout()`** – synkront anrop (ingen ProgressDialog), ta bort `degradedMode` + `pushLocalUnsyncedRecords()`.
5. **Ändra fil-I/O-serialisering** – antingen kö-baserad eller `ReentrantLock` för append vs read-write-cykeln.
6. **Uppdatera `HistoryTabController`** – använd `SoldItemsUploader` eller trigga BackgroundSyncManager.
7. **Verifiera** – testa scenarion från Issue 003 (nätverksstabilitet).
