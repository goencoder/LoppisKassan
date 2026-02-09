# Issue 002: Bulk-upload av JSONL till iLoppis-backend

**Status:** Design → Proto changes ✅ → Implementation  
**Skapad:** 2026-02-09  
**Uppdaterad:** 2026-02-09  
**Prioritet:** Medel  
**Epic:** Kassasystem refaktorering  
**Scope:** LoppisKassan (Java desktop) + iLoppis backend (Go)  
**Beroende:** [Issue 001](001-jsonl-unified-local-events.md) — JSONL-lagring och lokala events

#### Proto-ändringar (2026-02-09) ✅
- Added `BulkUploadResult` message for explicit partial-success handling
- Enhanced documentation for `CreateSoldItems` with bulk-upload use case
- Clarified `DUPLICATE_RECEIPT` error code behavior for idempotent uploads
- Reserved future `BulkUploadSoldItems` endpoint for potential optimization
- ✅ `buf generate` successfully regenerated Go code

---

## 1. Bakgrund

Efter att [Issue 001](001-jsonl-unified-local-events.md) implementerats lagrar LoppisKassan alla försäljningar i JSONL-filer per event. Lokala events fungerar helt offline, men arrangörer vill ofta ladda upp data till iLoppis-backend i efterhand — för rapporter, statistik och centraliserad redovisning.

Denna issue beskriver bulk-upload-flödet: klient-UI i LoppisKassan, backend-endpoint i iLoppis, deduplicering och felhantering.

---

## 2. Terminologi

| Term | Beskrivning |
|------|-------------|
| **Bulk-upload** | Att ladda upp ett lokalt events alla försäljningar till backend i en batch |
| **Backend-event** | Ett event i iLoppis som redan har marknad, säljare, API-nycklar etc. |
| **Lokalt event** | Event skapat i LoppisKassan utan backend (från Issue 001) |
| **Partial success** | En upload där vissa items accepteras och andra avvisas |

---

## 3. Klient: Bulk-upload dialog (LoppisKassan)

### 3.1 UI-mockup

```
┌────────────────────────────────────────────┐
│  Ladda upp till iLoppis                [X] │
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
│  Kassakod (XXX-XXX):                       │
│  ┌──────────────────────────────────────┐  │
│  │ ABC-123                               │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  Förhandsgranskning:                       │
│  ┌──────────────────────────────────────┐  │
│  │ 23 items från 8 köp                  │  │
│  │ Tidsspann: 2026-02-08 10:00 – 15:30  │  │
│  │ Total omsättning: 12 450 SEK         │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  ⚠️ Kräver API-nyckel med rättigheter     │
│     för valt event.                        │
│                                            │
│         [Avbryt]        [Ladda upp]        │
└────────────────────────────────────────────┘
```

### 3.2 Öppna dialogen

Två vägar in:
1. Discovery-vyn: högerklicka lokalt event → **[Ladda upp till iLoppis]**
2. Historik-fliken (för aktivt lokalt event): knapp **[Ladda upp]**

### 3.3 Java-implementation

```java
public class BulkUploadDialog extends JDialog {
    private JComboBox<V1Event> backendEventCombo;
    private JTextField codeField;
    private JLabel previewLabel;
    private final LocalEvent localEvent;

    public BulkUploadDialog(Frame owner, LocalEvent localEvent) {
        super(owner, LocalizationManager.tr("bulk_upload.title"), true);
        this.localEvent = localEvent;
        initComponents();
        loadPreview();
    }

    private void loadPreview() {
        List<V1SoldItem> items = PendingItemsStore.readAll(localEvent.getEventId());
        long purchaseCount = items.stream()
            .map(V1SoldItem::getPurchaseId)
            .distinct().count();

        previewLabel.setText(String.format(
            LocalizationManager.tr("bulk_upload.preview"),
            items.size(), purchaseCount,
            items.stream().mapToInt(V1SoldItem::getPrice).sum()
        ));
    }

    public void performUpload() {
        V1Event backendEvent = (V1Event) backendEventCombo.getSelectedItem();
        String code = codeField.getText().trim();

        ProgressDialog.show(
            LocalizationManager.tr("bulk_upload.progress"),
            () -> {
                // 1. Hämta API-nyckel via kassakod
                String apiKey = ApiHelper.INSTANCE
                    .getApiKeysServiceApi()
                    .exchangeCode(backendEvent.getId(), code);

                // 2. Läs alla items från JSONL
                List<V1SoldItem> items = PendingItemsStore
                    .readAll(localEvent.getEventId());

                // 3. Gruppera per purchaseId och ladda upp
                Map<String, List<V1SoldItem>> byPurchase = items.stream()
                    .collect(Collectors.groupingBy(V1SoldItem::getPurchaseId));

                BulkUploadResult result = new BulkUploadResult();

                for (var entry : byPurchase.entrySet()) {
                    try {
                        ApiHelper.INSTANCE.getSoldItemsServiceApi()
                            .submitPurchase(backendEvent.getId(), entry.getValue(), apiKey);
                        result.accepted.addAll(entry.getValue());
                    } catch (ApiException e) {
                        if (isDuplicateError(e)) {
                            result.duplicates.addAll(entry.getValue());
                        } else {
                            result.failed.addAll(entry.getValue());
                            result.errors.add(e.getMessage());
                        }
                    }
                }

                // 4. Uppdatera lokal metadata
                if (!result.accepted.isEmpty()) {
                    LocalEventMetadata meta = LocalEventRepository
                        .getMetadata(localEvent.getEventId());
                    meta.setUploadedToBackend(true);
                    meta.setUploadedAt(OffsetDateTime.now());
                    meta.setBackendEventId(backendEvent.getId());
                    LocalEventRepository.saveMetadata(meta);
                }

                return result;
            },
            this::showUploadSummary,
            error -> Popup.ERROR.showAndWait(
                LocalizationManager.tr("bulk_upload.error.title"),
                error.getMessage()
            )
        );
    }

    private void showUploadSummary(BulkUploadResult result) {
        String message = String.format(
            LocalizationManager.tr("bulk_upload.summary"),
            result.accepted.size(),
            result.duplicates.size(),
            result.failed.size()
        );
        Popup.INFORMATION.showAndWait(
            LocalizationManager.tr("bulk_upload.summary.title"),
            message
        );
    }
}
```

### 3.4 Resultat-modell

```java
public class BulkUploadResult {
    public final List<V1SoldItem> accepted = new ArrayList<>();
    public final List<V1SoldItem> duplicates = new ArrayList<>();
    public final List<V1SoldItem> failed = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();

    public boolean isFullSuccess() {
        return failed.isEmpty() && duplicates.isEmpty();
    }

    public boolean isPartialSuccess() {
        return !accepted.isEmpty() && (!failed.isEmpty() || !duplicates.isEmpty());
    }
}
```

---

## 4. Backend: API-endpoint

### 4.1 Återanvänd befintlig endpoint

**Rekommendation:** Återanvänd befintlig `POST /v1/events/{eventId}/sold-items` (grupperat per purchaseId) istället för ny `:bulk-upload` endpoint.

**Motivering:**
- Redan stödjer batch-insert
- Har deduplicering via unique index (`event_id` + `item_id`)
- Returnerar `DUPLICATE_RECEIPT` vid dubbletter
- Mindre backend-arbete

### 4.2 Dedup-garanti

Backend har unique index på `(event_id, item_id)` i MongoDB. Försök att infoga duplikat ger:
- HTTP 409 Conflict
- Error code: `DUPLICATE_RECEIPT`
- Klienten hanterar detta som "redan uppladdat" (inte som fel)

### 4.3 Request/Response-format (befintligt)

**Request:** `POST /v1/events/{eventId}/sold-items`
```json
{
  "items": [
    {
      "item_id": "01HW1K2M3N4P5Q6R7S",
      "purchase_id": "01HW1K2M3N4P5Q6R7T",
      "seller_number": 42,
      "price": 50,
      "payment_method": "PAYMENT_METHOD_CASH",
      "sold_time": "2026-02-08T14:30:00Z"
    }
  ]
}
```

**Response (success):** HTTP 200
```json
{
  "items": [ /* accepted items */ ]
}
```

**Response (duplicate):** HTTP 409
```json
{
  "code": "DUPLICATE_RECEIPT",
  "message": "Item with id 01HW1K2M3N4P5Q6R7S already exists"
}
```

### 4.4 Eventuell framtida bulk-endpoint

Om volymerna kräver det kan en dedikerad bulk-endpoint skapas senare:

```
POST /v1/events/{eventId}/sold-items:bulk-upload
```

Med sammanfattande respons (accepted/rejected per item). Men detta är **inte nödvändigt** för initial implementation — befintlig endpoint räcker.

---

## 5. Flöden

### Flöde 1: Bulk-upload av ett lokalt event

```
Loppis över (genomförd utan internet)
    ↓
Arrangör har 3 lokala events i LoppisKassan:
    - Kassa 1: 23 köp
    - Kassa 2: 45 köp
    - Kassa 3: 31 köp
    ↓
På kontoret (med internet):
1. Öppna LoppisKassan
2. Discovery-vyn visar lokala events
    ↓
3. Högerklicka "Kassa 1" → [Ladda upp till iLoppis]
    ↓
4. Dialog öppnas:
    - Välj backend-event: "Sillfest 2026"
    - Ange kassakod: ABC-123
    - Förhandsgranskning: 23 items, 8 köp, 12 450 SEK
    ↓
5. Klicka [Ladda upp]
    ↓
6. Upload per purchaseId:
    - Purchase 1: 3 items → HTTP 200 ✅
    - Purchase 2: 2 items → HTTP 200 ✅
    - Purchase 3: 3 items → HTTP 409 (duplikat) ⚠️
    - ...
    ↓
7. Sammanfattning:
    ✅ 21 items uppladdade
    ⚠️ 2 items var dubbletter (ignorerade)
    ↓
8. Metadata uppdateras:
    - uploadedToBackend = true
    - backendEventId = "..."
    ↓
9. Upprepa för Kassa 2 och 3
```

### Flöde 2: Upload av extern JSONL-fil

```
Arrangör fick JSONL-fil från annan kassör (USB/e-post)
    ↓
1. Discovery → [Ladda upp JSONL-fil till iLoppis]
    ↓
2. Dialog:
    - Välj fil: pending_items.jsonl (via JFileChooser)
    - Välj backend-event + kassakod
    - Förhandsgranskning
    ↓
3. Klicka [Ladda upp] → samma flow som ovan
```

### Flöde 3: Idempotent re-upload

```
Arrangör osäker om upload lyckades (nätverksfel halvvägs)
    ↓
Högerklicka samma event → [Ladda upp till iLoppis] igen
    ↓
Backend avvisar redan kända items (DUPLICATE_RECEIPT)
    ↓
Sammanfattning:
    ✅ 0 nya items
    ⚠️ 23 items redan uppladdade
    ↓
Trygg re-upload — inga dubbletter skapas
```

---

## 6. API-nyckelhantering

### 6.1 Kassakod vid upload

Bulk-upload kräver en API-nyckel med rättigheter för måleventet. Användaren anger kassakod (XXX-XXX) i dialogen.

```java
// Hämta API-nyckel via kassakod
String apiKey = ApiHelper.INSTANCE
    .getApiKeysServiceApi()
    .apiKeysServiceGetApiKeyByAlias(backendEvent.getId(), code)
    .getApiKey();
```

### 6.2 Nyckeltyper som stöds

| Nyckeltyp | Kan bulk-uploada? |
|-----------|-------------------|
| `API_KEY_TYPE_WEB_CASHIER` | ✅ Ja |
| `API_KEY_TYPE_MARKET_OWNER` | ✅ Ja |
| `API_KEY_TYPE_SCANNER` | ❌ Nej (bara scan-rättigheter) |

### 6.3 Felhantering

| Scenario | Hantering |
|----------|-----------|
| Ogiltig kassakod | Popup: "Ogiltig kassakod. Kontrollera och försök igen." |
| Nyckel saknar rättigheter | Popup: "Denna kassakod har inte rättigheter att registrera köp." |
| Backend nere | Popup: "Kunde inte nå iLoppis. Kontrollera internet och försök igen." |
| Timeout | Retry med exponential backoff (max 3 försök per purchase) |

---

## 7. Integrationstestfall

Dessa tester kräver en körande backend (Docker Compose: backend + MongoDB + MailHog) och följer testmönstret i `iloppis/tests/integration/`.

**Infrastruktur:**
```yaml
services:
  backend:      # iLoppis Go-service port 8080
  mongodb:      # MongoDB port 27017
  mailhog:      # MailHog SMTP + API port 8025
```

**Testramverk:** TypeScript + Vitest + Playwright  
**Körning:** `npx vitest --reporter=verbose`

### Provisionering (per testsvit)

```typescript
// 1. Platform owner + market owner login
const platformKey = await loginViaApiMagicLink(USERS.PLATFORM_OWNER.email);
const ownerKey = await loginViaApiMagicLink(USERS.MARKET_OWNERS[0].email);

// 2. Skapa och godkänn marknad
const draft = await createDraftMarket(getDraftMarketPayload(0), ownerKey);
await approveDraftMarket(draft.market.id, platformKey);

// 3. Skapa event
const event = await createEvent({
  marketId: draft.market.id,
  name: "Bulk Upload Test Event",
  startsAt: new Date().toISOString(),
  endsAt: new Date(Date.now() + 86400000).toISOString(),
}, ownerKey);

// 4. Skapa API-nyckel
const cashierKey = await createApiKey(
  event.event.id, 'API_KEY_TYPE_WEB_CASHIER', ownerKey
);

// 5. Registrera säljare
for (let i = 1; i <= 10; i++) {
  const profile = await createProfile({ firstName: `Seller${i}` });
  const vendor = await createVendorForEvent(event.event.id, profile.id, ownerKey);
  await updateVendorForEvent(event.event.id, vendor.id,
    { status: 'VENDOR_STATUS_APPROVED', sellerNumber: i }, ownerKey);
}
```

### T-I01: Bulk-upload – lokal JSONL → backend
| Steg | Förväntat |
|------|-----------|
| Provisionera: market → event → API key → 10 säljare | Setup OK |
| Skapa JSONL med 30 items (ULID-baserade) | Data skapad |
| Ladda upp grupperat per purchaseId | HTTP 200 per grupp |
| Hämta `listSoldItems()` | 30 items i backend |
| Verifiera: alla ULID:n matchar | Korrekt |

### T-I02: Idempotent re-upload
| Steg | Förväntat |
|------|-----------|
| Ladda upp 30 items (som i T-I01) | 30 accepted |
| Ladda upp samma 30 items igen | 0 accepted, 30 duplicates (DUPLICATE_RECEIPT) |
| `listSoldItems()` | Fortfarande 30 items (inga dubbletter) |

### T-I03: Multi-kassör – parallell upload
| Steg | Förväntat |
|------|-----------|
| Provisionera: 2 API keys (cashier1, cashier2) → 20 säljare | Setup OK |
| Kassör 1: ladda upp 25 köp | HTTP 200 |
| Kassör 2: ladda upp 25 köp | HTTP 200 |
| `listSoldItems()` | 50 items totalt |
| Verifiera: inga duplikater (unika ULID per kassör) | Korrekt |

### T-I04: Partial failure – nätverksfel
| Steg | Förväntat |
|------|-----------|
| 10 purchases att ladda upp | |
| Simulera nätverksfel efter purchase 6 | 6 accepted, 4 failed |
| Re-upload alla 10 | 4 nya accepted, 6 duplicates |
| `listSoldItems()` | 10 items totalt |

### T-I05: Redovisning – lokal vs backend
| Steg | Förväntat |
|------|-----------|
| 3 kassörer, 30 köp totalt fördelade på 10 säljare | Setup OK |
| Ladda upp alla till backend | 30 items |
| Beräkna lokal summa per säljare | |
| Hämta backend-summa per säljare via API | |
| Jämför | Identiskt |

### T-I06: End-to-end lifecycle
| Steg | Förväntat |
|------|-----------|
| 1. Login (platform + market owner) | API keys |
| 2. Skapa marknad med revenueSplit 10/85/5 | APPROVED |
| 3. Skapa event | eventId |
| 4. Skapa API-nyckel + alias | cashierKey |
| 5. Registrera 30 säljare | APPROVED |
| 6. Skapa lokal JSONL med 100 köp | Fil skapad |
| 7. Bulk-upload till backend | 100 accepted |
| 8. `listSoldItems()` | 100 items |
| 9. Re-upload | 0 nya, 100 duplicates |
| 10. Verifiera per-säljare-summor | Korrekt |

---

## 8. Metadata-uppdatering vid upload

Efter lyckad (hel eller delvis) upload uppdateras lokalt events `metadata.json`:

```json
{
  "eventId": "local-550e8400-...",
  "eventType": "LOCAL",
  "name": "Sillfest Kassa 1",
  "uploadedToBackend": true,
  "uploadedAt": "2026-02-09T18:30:00Z",
  "backendEventId": "d50e8356-8deb-428a-..."
}
```

I Discovery-vyn visas statusen:

| Status | Ikon | Beskrivning |
|--------|------|-------------|
| **Uppladdad** | ☁️ | Alla items uppladdade |
| **Delvis uppladdad** | ⚠️ | Vissa items misslyckades |

---

## 9. Öppna frågor

### 9.1 Dedikerad bulk-endpoint?
**Nuvarande plan:** Återanvänd `POST /v1/events/{eventId}/sold-items` per purchaseId-grupp.

**Alternativ:** `POST /v1/events/{eventId}/sold-items:bulk-upload` med sammanfattande respons (accepted + rejected arrays).

**Decision:** Börja med befintlig endpoint. Utvärdera efter initial implementation om latency/UX kräver dedikerad endpoint.

### 9.2 Rate limiting
Backend har rate limiting. Vid stora uploads (100+ köp) kan throttling inträffa.

**Lösning:** Klienten lägger in 100ms delay mellan varje purchaseId-grupp. Exponential backoff vid 429-respons.

### 9.3 Progressbar
Vid stora uploads bör en progressbar visas:

```
Laddar upp: ████████░░░░ 67% (8/12 köp)
```

---

## 10. Implementationsplan

### Sprint 3 (efter Issue 001, sprint 1–2)
- [ ] `BulkUploadDialog` UI
- [ ] `BulkUploadResult` modell
- [ ] Upload-logik: gruppera per purchaseId, skicka sekventiellt
- [ ] Felhantering: duplicates, timeout, auth
- [ ] Metadata-uppdatering efter upload
- [ ] Progressbar

### Sprint 4
- [ ] Integrationstester: T-I01, T-I02, T-I03
- [ ] Integrationstester: T-I04, T-I05, T-I06
- [ ] Upload av extern JSONL-fil (flöde 2)
- [ ] Uppdatera manual med upload-avsnitt
- [ ] Uppdatera lokaliseringsfiler: `sv.json`, `en.json`

---

**Dokumentslut**
