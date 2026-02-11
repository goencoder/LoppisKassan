# Issue 003: Network Stability Testing

**Status:** Planering  
**Datum:** 2026-02-11  
**Syfte:** Verifiera att LoppisKassan hanterar dåliga nätverksförhållanden korrekt  
**Total testvolym:** ~500 000 SEK

## Arkitektur

```
┌──────────────────────────────────────┐
│                                      │
│   LoppisKassan UI (Swing Desktop)    │
│                                      │
│   ┌──────────────┐  ┌────────────┐   │
│   │ Local File   │  │ Statusbar  │   │
│   │ (JSONL)      │  │ 🟢/🟡     │   │
│   │ < 100ms      │  │ pending N  │   │
│   └──────┬───────┘  └────────────┘   │
│          │                           │
│          ▼                           │
│   ┌──────────────┐                   │
│   │ Background   │ ◄─ 30s interval   │
│   │ Upload       │   retry-loop      │
│   └──────┬───────┘                   │
│          │                           │
└──────────┼───────────────────────────┘
           │
           │ HTTP/JSON (via proxy)
           │
           ▼
┌──────────────────────┐
│   Toxiproxy :8081    │
│   Toxic: latency,    │
│   slicer, bandwidth, │
│   timeout, slow_close│
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   iLoppis Backend    │
│   (localhost:8080)   │  ◄── VERIFIERA DATA HÄR!
│   MongoDB + API      │
└──────────────────────┘
```

**Konfiguration:**
- LoppisKassan: `ILOPPIS_API_URL=http://localhost:8081` (via proxy)
- Toxiproxy: Lyssnar på `:8081`, vidarebefordrar till `host.docker.internal:8080`
- **Verifiering:** `curl http://localhost:8080` (direkt till backend, EJ genom proxy)

---

## Testprinciper

1. **UI ska vara snabb** (< 100ms) oavsett nätverk — skriver till lokal JSONL-fil först
2. **Observera statusbaren** i nedre vänstra hörnet:
   - `🟢 Ansluten till iLoppis` = allt uppladdad
   - `🟡 Offline – N poster väntar på synkronisering` = pending items
3. **Vänta tills statusbar visar 🟢** (bakgrundstråd klar, alla items uppladdade)
4. **Verifiera historikvyn** — alla försäljningar ska synas i historik-fliken
5. **Verifiera backend-data** via direkt API-anrop på :8080 (ej genom proxy)
6. **Ingen prestandamätning** — bara dataintegritet och UI-responsivitet

---

## Förberedelser

### Starta miljön

```bash
cd /Users/goranengdahl/IntellijProjects/LoppisKassan

# Starta backend + toxiproxy
cd /Users/goranengdahl/GolandProjects/iloppis/backend
make up-dev
cd /Users/goranengdahl/IntellijProjects/LoppisKassan
make toxiproxy-up
make toxiproxy-setup
```

### Test-infrastruktur

```bash
export EVENT_ID="16d15112-361a-45d5-9056-ec26c3784ef3"
export API_KEY="cf720b68-8f95-443a-9f93-d1e457127cdc"
```

**100 vendors finns redan skapade** (säljare 1-100)

### Starta LoppisKassan med proxy

```bash
ILOPPIS_API_URL=http://localhost:8081 make run
```

**Login:** iLoppis-läge → Event ID → API key → Öppna kassan

### Verifieringskommando (copy-paste för alla tester)

```bash
# Kör detta DIREKT mot backend (:8080), INTE via proxy
curl -s -H "Authorization: Bearer $API_KEY" \
  http://localhost:8080/v1/events/$EVENT_ID/sold-items?page_size=10000 | \
  jq '{
    purchases: [.items[].purchaseId] | unique | length,
    items: .items | length,
    total_sek: [.items[].price] | add
  }'
```

---

## Avläsning av statusindikation

### Statusbaren (nedre vänstra hörnet)

| Status | Ikon | Text | Betydelse |
|--------|------|------|-----------|
| Online | 🟢 | `Ansluten till iLoppis` | Alla items uppladdade, ingen pending |
| Pending | 🟡 | `Offline – N poster väntar på synkronisering` | N items väntar på uppladdning |

**Hur man avläser:**
1. Titta i **nedre vänstra hörnet** av LoppisKassan-fönstret
2. Efter varje försäljning: om uppladdning lyckas direkt → 🟢 kvarstår
3. Vid nätverksfel: statusbar byter till 🟡 med antal pending items
4. BackgroundSyncManager försöker ladda upp var 30:e sekund
5. När alla items uppladdats: statusbar återgår till 🟢

### Historikvyn (sidomenyn "Historik")

1. Klicka på **"Historik"** i vänstermenyn
2. **Alla hittills inslagna köp ska visas** i tabellen
3. Varje rad visar: säljarnummer, pris, betalsätt, tidpunkt
4. Om items nyss laddats upp: `uploaded`-flaggan satt till `true`
5. Klicka "Uppdatera webb" för att synka med backend (hämtar ev. items från andra kassor)

---

## Tester

### Test 1: Baseline (Inget toxic)

**Syfte:** Etablera baseline — snabb lokal skrivning, lyckad uppladdning, historik korrekt

**Scenario:**
```bash
make toxiproxy-scenario SCENARIO=clear
```

**Toxic-konfiguration:** Ingen

**Test-data:**
- 50 försäljningar à 1 item per köp
- Säljare 1-50
- Pris: säljarnummer × 100 SEK (100, 200, 300, ..., 5000)
- **Totalt: 50 purchases, 50 items, 127 500 SEK**

**Åtgärder:**
1. Starta LoppisKassan med proxy-endpoint
2. Logga in i kassan
3. Registrera 50 försäljningar enligt test-data
4. Observera statusbar: ska visa 🟢 hela tiden (direkt upload lyckas)
5. Gå till **Historik** → verifiera att alla 50 försäljningar syns
6. Verifiera backend:
   ```bash
   curl -s -H "Authorization: Bearer $API_KEY" \
     http://localhost:8080/v1/events/$EVENT_ID/sold-items?page_size=10000 | \
     jq '{purchases: [.items[].purchaseId] | unique | length, items: .items | length, total_sek: [.items[].price] | add}'
   ```

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| UI responstid per försäljning | < 100ms (skriver till fil) |
| Statusbar under test | 🟢 `Ansluten till iLoppis` (hela tiden) |
| Historik visar alla köp | ✓ 50 rader |
| Backend purchases | 50 |
| Backend items | 50 |
| Backend total | 127 500 SEK |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| UI responstid | | |
| Statusbar | | 🟢 hela tiden? |
| Historik antal rader | | |
| Backend purchases | | |
| Backend items | | |
| Backend total | | |

**Åtgärd vid avvikelse:**

---

### Test 2: Hög latency

**Syfte:** UI förblir responsiv trots 500ms latency, bakgrundstråd hanterar fördröjning

**Scenario:**
```bash
make toxiproxy-scenario SCENARIO=high-latency
```

**Toxic-konfiguration:**
- `latency`: 500ms ±100ms

**Test-data:**
- 30 försäljningar à 1 item
- Säljare 1-30
- Pris: säljarnummer × 200 SEK (200, 400, ..., 6000)
- **Totalt: 30 purchases, 30 items, 93 000 SEK**

**Åtgärder:**
1. Applicera high-latency toxic
2. Registrera 30 försäljningar i snabb följd (~1 per sekund)
3. Observera statusbar: kan visa 🟡 med pending count, bör gradvis sjunka
4. Vänta tills statusbar visar 🟢 (allt uppladdat)
5. Gå till **Historik** → verifiera 30 rader (plus 50 från Test 1 = 80 totalt)
6. Verifiera backend

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| UI responstid per försäljning | < 100ms (INTE påverkat av latency) |
| Statusbar under snabb inmatning | 🟡 med ökande pending count |
| Statusbar efter upload klar | 🟢 `Ansluten till iLoppis` |
| Historik visar alla köp | ✓ 80 rader (50+30) |
| Backend purchases (kumulativt) | 80 |
| Backend items (kumulativt) | 80 |
| Backend total (kumulativt) | 220 500 SEK |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| UI responstid | | UI ska vara snabb oavsett! |
| Statusbar under inmatning | | 🟡 med pending count? |
| Statusbar efter upload | | Tid till 🟢? |
| Historik antal rader | | 80 = 50+30? |
| Backend purchases | | Kumulativt |
| Backend items | | |
| Backend total | | |

**Åtgärd vid avvikelse:**

---

### Test 3: Slow 3G (Mobilt nätverk)

**Syfte:** Systemet fungerar på dålig mobiluppkoppling (realistiskt fältscenario)

**Scenario:**
```bash
make toxiproxy-scenario SCENARIO=slow-3g
```

**Toxic-konfiguration:**
- `bandwidth`: downstream 250 KB/s, upstream 50 KB/s
- `latency`: 200ms ±50ms

**Test-data:**
- 20 försäljningar à 1 item
- Säljare 51-70
- Pris: (säljarnummer - 50) × 500 SEK (500, 1000, ..., 10 000)
- **Totalt: 20 purchases, 20 items, 105 000 SEK**

**Åtgärder:**
1. Applicera slow-3g toxic
2. Registrera 20 försäljningar (typ 1 var 2:a sekund)
3. Observera statusbar: 🟡 med pending count, tömms långsamt
4. Vänta tills statusbar visar 🟢 (kan ta 30-60 sekunder)
5. Gå till **Historik** → verifiera 100 rader (80+20)
6. Verifiera backend

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| UI responstid per försäljning | < 100ms |
| Statusbar under inmatning | 🟡 pending count bygger upp |
| Pending count når 0 | ✓ Inom 30-60 sekunder |
| Offline-läge aktiverat | ✗ Nej (långsamt men lyckas) |
| Historik visar alla köp | ✓ 100 rader (80+20) |
| Backend purchases (kumulativt) | 100 |
| Backend items (kumulativt) | 100 |
| Backend total (kumulativt) | 325 500 SEK |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| UI responstid | | |
| Statusbar | | |
| Pending når 0 | | Timeout? |
| Historik antal rader | | |
| Backend purchases | | |
| Backend items | | |
| Backend total | | |

**Åtgärd vid avvikelse:**

---

### Test 4: Packet Loss

**Syfte:** Fragmenterade paket förhindrar inte dataintegritet, inga dubbelposter

**Scenario:**
```bash
make toxiproxy-scenario SCENARIO=packet-loss
```

**Toxic-konfiguration:**
- `slicer`: average 64 bytes, variation ±32 bytes, delay 50ms
- `latency`: 100ms

**Test-data:**
- 20 försäljningar à 1 item
- Säljare 71-90
- Pris: (säljarnummer - 70) × 400 SEK (400, 800, ..., 8 000)
- **Totalt: 20 purchases, 20 items, 84 000 SEK**

**Åtgärder:**
1. Applicera packet-loss toxic
2. Registrera 20 försäljningar
3. Observera statusbar: 🟡 kan fluktuera pga retry
4. Vänta tills statusbar visar 🟢 (kan ta 30-60s pga retry)
5. Gå till **Historik** → verifiera 120 rader (100+20)
6. Verifiera backend — kolla **inga dubbelposter** (purchaseId unika)

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| UI responstid per försäljning | < 100ms |
| Pending når 0 | ✓ Inom 30-60 sekunder (retry) |
| Dubbelposter i backend | ✗ Nej (ULID + idempotens) |
| Historik visar alla köp | ✓ 120 rader (100+20) |
| Backend purchases (kumulativt) | 120 |
| Backend items (kumulativt) | 120 |
| Backend total (kumulativt) | 409 500 SEK |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| UI responstid | | |
| Pending når 0 | | |
| Dubbelposter | | Kolla unique purchaseIds |
| Historik antal rader | | |
| Backend purchases | | |
| Backend items | | |
| Backend total | | |

**Åtgärd vid avvikelse:**

---

### Test 5: Random Timeouts

**Syfte:** Timeout förhindrar inte lokal försäljning, offline-läge aktiveras, auto-synk fungerar

**Scenario:**
```bash
make toxiproxy-scenario SCENARIO=timeout
```

**Toxic-konfiguration:**
- `timeout`: 30000ms, toxicity 0.05 (5% av requests)

**Test-data:**
- 10 försäljningar à 1 item
- Säljare 91-100
- Pris: (säljarnummer - 90) × 1000 SEK (1000, 2000, ..., 10 000)
- **Totalt: 10 purchases, 10 items, 55 000 SEK**

**Åtgärder:**
1. Applicera timeout toxic (5% av requests timeout:ar)
2. Registrera 10 försäljningar
3. Observera statusbar: 🟡 vid timeout, pending count visar antal
4. **KRITISKT:** Om statusbar visar 🟡 — fortsätt registrera nya försäljningar!
5. Vänta tills 🟢 (eller ge upp efter 5 min och gå till steg 6)
6. Om pending ≠ 0: `make toxiproxy-scenario SCENARIO=clear` → vänta → 🟢
7. Gå till **Historik** → verifiera 130 rader (120+10)
8. Verifiera backend

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| UI responstid per försäljning | < 100ms (alltid) |
| Statusbar vid timeout | 🟡 `Offline – N poster väntar...` |
| Pending når 0 (efter clear) | ✓ Ja (auto-synk) |
| Historik visar alla köp | ✓ 130 rader (120+10) |
| Backend purchases (kumulativt) | 130 |
| Backend items (kumulativt) | 130 |
| Backend total (kumulativt) | 464 500 SEK |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| UI responstid | | |
| Statusbar vid timeout | | 🟡 visas? |
| Pending når 0 | | Efter hur lång tid? |
| Historik antal rader | | |
| Backend purchases | | |
| Backend items | | |
| Backend total | | |

**Åtgärd vid avvikelse:**

---

### Test 6: Unstable (Kombinerat worst-case)

**Syfte:** Stress-test, offline-läge aktiveras, fortsättning att sälja möjlig

**Scenario:**
```bash
make toxiproxy-scenario SCENARIO=unstable
```

**Toxic-konfiguration:**
- `latency`: 200ms ±100ms
- `slicer`: packet fragmentation
- `bandwidth`: upstream 50 KB/s
- `timeout`: 10000ms, toxicity 0.1 (10%)

**Test-data:**
- 10 försäljningar à 1 item
- Säljare 1-10 (återanvänder)
- Pris: säljarnummer × 500 SEK (500, 1000, ..., 5 000)
- **Totalt: 10 purchases, 10 items, 27 500 SEK**

**Åtgärder:**
1. Applicera unstable toxic
2. Registrera 10 försäljningar, en i taget
3. Observera: UI ska vara snabb, statusbar visar 🟡
4. **KRITISKT:** Fortsätt sälja i offline-läge!
5. `make toxiproxy-scenario SCENARIO=clear`
6. Vänta tills statusbar visar 🟢 (auto-synk)
7. Gå till **Historik** → verifiera 140 rader (130+10)
8. Verifiera backend

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| UI responstid per försäljning | < 100ms (alltid) |
| Offline-läge aktiveras | ✓ 🟡 inom 10-30 sekunder |
| Fortsätt sälja i offline | ✓ Möjligt |
| Pending når 0 efter clear | ✓ Inom 30s |
| Historik visar alla köp | ✓ 140 rader |
| Backend purchases (kumulativt) | 140 |
| Backend items (kumulativt) | 140 |
| Backend total (kumulativt) | 492 000 SEK |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| UI responstid | | |
| Offline-läge | | Trigger? |
| Sälja i offline | | |
| Pending efter clear | | |
| Historik antal rader | | |
| Backend purchases | | |
| Backend items | | |
| Backend total | | |

**Åtgärd vid avvikelse:**

---

### Test 7: Network Recovery (Auto-synk)

**Syfte:** Offlinedata synkas automatiskt när nätverket återvänder

**Scenario:**
```bash
# Steg 1: unstable
make toxiproxy-scenario SCENARIO=unstable
# Registrera försäljningar
# Steg 2: clear
make toxiproxy-scenario SCENARIO=clear
```

**Test-data:**
- 5 försäljningar i offline-läge
- Säljare 11-15
- Pris: 800, 1600, 2400, 3200, 4000 SEK
- **Totalt: 5 purchases, 5 items, 12 000 SEK**

**Åtgärder:**
1. Vänta tills statusbar visar 🟡 (unstable fortfarande aktivt från test 6, eller återaktivera)
2. Registrera 5 försäljningar i offline-läge
3. Kontrollera statusbar: 🟡 med pending count ≥ 5
4. `make toxiproxy-scenario SCENARIO=clear`
5. Observera statusbar: 🟡 count sjunker → 🟢
6. Gå till **Historik** → verifiera 145 rader (140+5)
7. Verifiera backend
8. **EXTRA:** Klicka "Uppdatera webb" i historik

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| Pending items före clear | ≥ 5 (🟡) |
| Auto-synk efter clear | ✓ Inom 30s |
| Statusbar efter synk | 🟢 `Ansluten till iLoppis` |
| Historik visar alla köp | ✓ 145 rader |
| Backend purchases (kumulativt) | 145 |
| Backend items (kumulativt) | 145 |
| Backend total (kumulativt) | 504 000 SEK |
| "Uppdatera webb" matchar | ✓ Data konsistent |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| Pending före clear | | 🟡 count? |
| Auto-synk trigger | | Fördröjning? |
| Statusbar efter synk | | |
| Historik antal rader | | |
| Backend purchases | | |
| Backend items | | |
| Backend total | | |
| "Uppdatera webb" | | |

**Åtgärd vid avvikelse:**

---

### Test 8: Slow Close (Connection pool)

**Syfte:** Långsamma connection-closes blockerar inte uppladdning

**Scenario:**
```bash
make toxiproxy-scenario SCENARIO=slow-close
```

**Toxic-konfiguration:**
- `slow_close`: 5000ms delay

**Test-data:** Inget nytt — verifierar bara att systemet fungerar med slow-close

**Åtgärder:**
1. Applicera slow-close toxic
2. Registrera 3 snabba försäljningar (valfria säljare/priser)
3. Observera statusbar
4. Vänta tills pending = 0
5. `make toxiproxy-scenario SCENARIO=clear`

**Förväntat resultat:**

| Metrik | Förväntat |
|--------|-----------|
| UI responstid | < 100ms |
| Pending når 0 | ✓ Inom 30-60s |
| Connection pool blockerad | ✗ Nej |

**Faktiskt resultat:**

| Metrik | Faktiskt värde | Kommentar |
|--------|----------------|-----------|
| UI responstid | | |
| Pending når 0 | | |
| Connection pool | | |

---

## Kumulativ kontrollsumma

| Efter test | Purchases | Items | Total SEK |
|-----------|-----------|-------|-----------|
| Test 1 | 50 | 50 | 127 500 |
| Test 2 | 80 | 80 | 220 500 |
| Test 3 | 100 | 100 | 325 500 |
| Test 4 | 120 | 120 | 409 500 |
| Test 5 | 130 | 130 | 464 500 |
| Test 6 | 140 | 140 | 492 000 |
| Test 7 | 145 | 145 | 504 000 |
| Test 8 | ~148 | ~148 | ~504 000+ |

---

## Loggning och debugging

**Inspektera aktiva toxics:**
```bash
curl -s http://localhost:8474/proxies/iloppis-backend/toxics | python3 -m json.tool
```

**Verifiera backend-data (DIREKT :8080):**
```bash
curl -s -H "Authorization: Bearer $API_KEY" \
  http://localhost:8080/v1/events/$EVENT_ID/sold-items?page_size=10000 | \
  jq '{
    purchases: [.items[].purchaseId] | unique | length,
    items: .items | length,
    total_sek: [.items[].price] | add
  }'
```

---

## Bugfixar genomförda innan testning

| # | Bug | Fix | Fil |
|---|-----|-----|-----|
| 1 | **Historik tom efter iLoppis-försäljning** — lyckad API-upload skrev INTE till lokal fil → `loadHistory()` hittade inga items | Nu sparar `IloppisCashierStrategy` items till JSONL med `uploaded=true` även vid lyckad upload | `IloppisCashierStrategy.java` |
| 2 | **Statusbar pending-indikator aldrig kallad** — `setOfflineStatus()`/`setOnlineStatus()` var döda metoder | Nu kopplade via `BackgroundSyncManager.PendingCountListener` → `AppShellFrame` | `BackgroundSyncManager.java`, `AppShellFrame.java` |
| 3 | **BackgroundSync raderade hela pending-filen** — `Files.deleteIfExists(pendingPath)` kunde förlora items tillagda under upload | Nu markerar items som `uploaded=true` och sparar tillbaka filen | `BackgroundSyncManager.java` |
| 4 | **BackgroundSync uppdaterade aldrig UI** — efter lyckad sync, ingen statusbar-update | Nu anropar `notifyPendingCountChanged()` → statusbar uppdateras via listener | `BackgroundSyncManager.java` |

---

## Acceptanskriterier

### MUST (Blockerar produktion)

- [ ] **UI-responsivitet:** Försäljningar registreras < 100ms oavsett nätverksförhållanden
- [ ] **Dataintegritet:** Alla 145+ försäljningar finns i backend (504 000 SEK)
- [ ] **Statusbar pending:** 🟡 med korrekt antal visas vid nätverksfel
- [ ] **Statusbar online:** 🟢 återställs efter lyckad upload
- [ ] **Historik korrekt:** Alla hittills inslagna köp visas i historikvyn
- [ ] **Auto-synk:** Pending items laddas upp automatiskt när nätverk OK

### SHOULD

- [ ] **Offline-läge:** Cashier kan fortsätta sälja utan nätverk
- [ ] **Retry-logik:** BackgroundSyncManager retryar var 30:e sekund
- [ ] **Inga dubbelposter:** ULID + idempotens förhindrar duplicates

### COULD

- [ ] **"Uppdatera webb" i historik:** Synk fungerar genom proxy
- [ ] **Loggning:** Tydliga loggar vid nätverksfel och retry

---

## Problemsammanfattning

| # | Problem | Severity | Test | Status | Notes |
|---|---------|----------|------|--------|-------|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

---

## Sammanfattning

| Test | Pass/Fail | Notes |
|------|-----------|-------|
| 1: Baseline | | |
| 2: Hög latency | | |
| 3: Slow 3G | | |
| 4: Packet Loss | | |
| 5: Random Timeouts | | |
| 6: Unstable | | |
| 7: Network Recovery | | |
| 8: Slow Close | | |

**MUST criteria:** [ ] 0/6
**Backend total:** ______ SEK (förväntat ≥ 504 000)

**Slutsats:** _[Skriv efter testgenomförande]_
