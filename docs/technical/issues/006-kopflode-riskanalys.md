# Issue 006: Riskanalys och krisplan för köpflöde

**Status:** Komplett analys med utskrivbar checklista
**Datum:** 2026-03-24
**Syfte:** Djup analys av alla felmeddelanden kassören kan möta, konkreta åtgärder, och beslutsgrund för filinsamling. Se även utskrivbar checklista: `docs/technical/issues/006-kassachecklist-laminerad.md`

---

## 1. Grundprincip

**Keep calm, continue punching in purchases.**

Systemet är byggt local-first: varje köp skrivs till lokal fil (`pending_items.jsonl`) *innan* bakgrundssynk. Så länge appen kan skriva till disk går inga köp förlorade — oavsett nätverksstatus.

Kassören ska **aldrig** behöva fatta beslut om systemet fungerar. Statusindikatorn i nedre vänstra hörnet berättar allt.

---

## 2. Statusfältet — kassörens primära informationskälla

Statusfältet (nedre vänstra hörnet) har tre nivåer:

| Indikator | Färg | Text | Betydelse | Åtgärd |
|-----------|------|------|-----------|--------|
| Grön prick | Grön | **Ansluten till iLoppis** | Allt fungerar, köp synkas | Fortsätt som vanligt |
| Gul prick | Gul | **Väntar på uppladdning (N)** | Nätproblem, köp sparas lokalt | Fortsätt sälja, köp synkas automatiskt |
| Röd prick | Röd | **Avvisade poster - N** | Köp nekade av servern | Klicka för att granska och fixa |

**Klickbart:** Gul och röd prick är klickbara. Gul öppnar pending-dialogen, röd öppnar avvisade-dialogen.

---

## 3. Komplett felkatalog — alla dialogrutor kassören kan möta

### 3.1 Kassaflödesfel (vid registrering)

Dessa fel visas som **modala dialogrutor** (WARNING) och blockerar tills kassören trycker OK. Fälten behåller sitt värde så kassören kan korrigera och försöka igen.

| Dialogrubrik | Meddelande | Orsak | Åtgärd |
|-------------|------------|-------|--------|
| **Felaktigt säljnummer** | "Säljnummer måste vara ett heltal" | Kassören skrev bokstäver eller tomt | Skriv rätt säljnummer (siffror), tryck OK |
| **Felaktigt pris** | "Ange korrekta heltal för priser" | Priset innehåller bokstäver, komma, eller är tomt | Skriv rätt pris (heltal i kr), tryck OK |
| **Felaktigt belopp** | "Ange ett korrekt belopp" | Betalt belopp felaktigt format | Skriv rätt belopp, tryck OK |
| **Säljare ej godkänd** | "Säljaren är inte godkänd för detta evenemang" | Säljnumret finns inte i godkändalistan | Kontrollera numret med säljaren, prova igen |

**Klassificering: NORMALT.** Dessa är vanliga inmatningsfel. Kassören fixar och fortsätter.

### 3.2 Autentiseringsfel (kassakod)

Dessa fel visas som **modala dialogrutor** som blockerar hela appen. Kassören måste lösa dem innan försäljning kan fortsätta.

| Dialogrubrik | Meddelande | Orsak | Åtgärd |
|-------------|------------|-------|--------|
| **Inloggningen är inte giltig** | "API-nyckeln är inte längre giltig. Ange en ny kassakod för att fortsätta." | 401/403 från server, koden har gått ut | **Be loppisansvarig om ny kassakod**, mata in XXX-XXX |
| **Kassakoden kunde inte verifieras** | "Kassakoden du angav är fel eller inte längre giltig. Kontrollera koden och försök igen." | Koden var felaktig (404 från server) | Kontrollera koden med loppisansvarig, försök igen |
| **Kunde inte hämta token** | "Felaktig kassakod? {feldetalj}" | Nätverksfel vid kodvalidering | Kontrollera nätverket. Om offline: tryck Avbryt |
| Kassakodsdialog (vid öppning) | "Ange kassakod för att öppna {loppis}." | Första gången, eller sparad kod saknas | Mata in kassakoden från loppisansvarig |

**Klassificering: KRÄVER HANDLING** men inga köp förloras. Lokalt sparade köp finns kvar.

**Kassakodsdialogen:**
- Helskärmsmodal — inget annat går att klicka
- 6 tecken: `[_][_][_] — [_][_][_]`
- Stödjer klistra in XXXYYY eller XXX-YYY
- Auto-avancerar mellan fält

### 3.3 Nätverks- och synkfel (statusfältet, ingen dialog)

| Statusfältstext | Orsak | Åtgärd |
|-----------------|-------|--------|
| Gul: **"Väntar på uppladdning (5)"** | Nätverket nere, timeout, DNS | **Fortsätt sälja.** Synkas automatiskt |
| Gul: **"Offline - poster väntar på synkronisering"** | Längre nätavbrott | **Fortsätt sälja.** Allt sparas lokalt |

**Klassificering: NORMALT** vid nätproblem. Kassören behöver inte göra något.

### 3.4 Degraderat läge (engångsdialog)

| Dialogrubrik | Meddelande | Åtgärd |
|-------------|------------|--------|
| **Nätverksfel - Kassa i degraderat läge** | "Kunde inte spara till webb. Vi går in i degraderat läge. Alla köp sparas lokalt tills vi kan synkronisera igen." | Tryck OK, **fortsätt sälja** |

**Klassificering: INFORMATIV.** Visas en gång. Köp sparas lokalt.

### 3.5 Offlinestartdialoger

| Dialogrubrik | Meddelande | Åtgärd |
|-------------|------------|--------|
| **Kassa öppnad (offline)** | "Kassan har öppnats med cachad data. Säljarlistan kan vara föråldrad..." | Tryck OK, **fortsätt sälja** |
| **Gammal cachad data** | "Den cachade datan är {tid} gammal. Vill du fortsätta ändå?" | Tryck Ja om det är loppisdag |
| **Ingen cachad data** | "Det finns ingen cachad data. Du måste vara online första gången." | KAN INTE ÖPPNA. Fixa nätverk först |

### 3.6 Avvisade poster (röd statusindikator)

| Trigger | Visning | Orsak | Åtgärd |
|---------|---------|-------|--------|
| Servern nekar ett köp | Röd: "Avvisade poster - N" | Felaktigt säljnummer, felaktig data | **Klicka** röd prick, dialogen öppnas |

**Avvisade-dialogens tabell:**

| Kolumn | Innehåll |
|--------|----------|
| Tid | När köpet registrerades |
| Säljare | Säljnumret |
| Pris | Beloppet |
| Betalmetod | KONTANT / SWISH |
| Artikel-ID | ULID |
| Orsak | INVALID_SELLER / DUPLICATE_RECEIPT / annat |
| Åtgärder | **Ändra** / **Ta bort** |

**Hantering:**
1. Klicka **Ändra** -> Redigeringsdialog
2. Korrigera säljnummer -> Klicka **Spara**
3. Posten synkas automatiskt nästa cykel (30 sek)

**Felkoder:**
- `INVALID_SELLER`: Fixas genom att redigera säljnummer
- `DUPLICATE_RECEIPT`: Ignorera — posten finns redan på servern
- Annat: Dokumentera och rapportera

### 3.7 Filskrivningsfel (KRITISKT — enda scenariot där köp kan förloras)

| Dialogrubrik | Meddelande | Åtgärd |
|-------------|------------|--------|
| **Fel vid skrivning** | "Fel vid skrivning till kassafil: {detalj}" | **STOPPA KASSAN** |
| **Problem med filrättigheter** | "Kontrollera att programmet har rättigheter..." | **STOPPA KASSAN** |
| **Kunde inte skapa kataloger** | "Behöver skapa kataloger..." | **STOPPA KASSAN** |

**Tyst filfel:** Om pending-antal inte ökar trots registrering -> misstänk filfel.

**Vid filfel:**
1. Stoppa denna kassa direkt
2. Flytta kassören till annan dator eller pappersläge
3. Kopiera HELA `~/.loppiskassan/` innan någon annan åtgärd
4. Ring tekniskt ansvarig

---

## 4. Beslutstrappa: när ska datorer och filer samlas in?

### 4.1 Samla INTE in (normal drift)

- Statusfältet visar grönt eller gult
- Dialogrutor om felaktigt säljnummer/pris (-> korrigera och fortsätt)
- Kassakodsdialog (-> mata in ny kod)
- "Kassa i degraderat läge" (-> tryck OK, fortsätt)

### 4.2 Samla in EFTER loppisdag (alltid som rutin)

Oavsett om incidenter inträffade:

```
Från varje kassadator:
~/.loppiskassan/events/<eventId>/pending_items.jsonl
~/.loppiskassan/events/<eventId>/rejected_purchases.jsonl
~/.loppiskassan/logs/loppiskassan.log
~/.loppiskassan/logs/loppiskassan.log.0  (upp till .4)
~/.loppiskassan/config/iloppis-mode.json
```

Zip per kassa:
```bash
KASSA="kassa-1"
DATUM=$(date +%Y%m%d-%H%M)
zip -r "${KASSA}-${DATUM}.zip" ~/.loppiskassan/
```

### 4.3 Samla in OMEDELBART (vid filfel)

Om du ser NÅGON av dessa dialoger:
- "Fel vid skrivning till kassafil"
- "Problem med filrättigheter"
- "Kunde inte skapa kataloger"

Eller om pending-antal inte ökar trots att köp registreras:
1. **Stoppa kassan** på den datorn
2. **Kopiera HELA** `~/.loppiskassan/` direkt
3. **Flytta kassören** till annan dator
4. **Kontakta** tekniskt ansvarig

---

## 5. Vad används filerna till?

Inställningsdialogen visar flera filer under `~/.loppiskassan/events/<eventId>/`. Alla är inte lika viktiga i drift. Nedan är den faktiska användningen i koden.

| Fil / katalog | Användning | Status |
|---------------|------------|--------|
| `events/<eventId>/` | Rotkatalog för allt event-specifikt tillstånd. Alla övriga filer för eventet ligger här under. | **Aktiv** |
| `local_metadata.json` | Lokalt evenemangs metadata. Skapas, laddas och sparas av `LocalEventRepository`. Används främst för lokala evenemang och när appen listar/sparar lokala kassor. | **Aktiv, lokal-läge** |
| `iloppis_metadata.json` | Cachad iLoppis-metadata för offline-start och visning av tidigare kända iLoppis-evenemang. Hanteras av `OnlineEventCache`. | **Aktiv, iLoppis-cache** |
| `pending_items.jsonl` | Den centrala driftfilen och den fullständiga köploggen. Den innehåller både poster som redan synkats och poster som fortfarande väntar på uppladdning. Lokalt läge använder den som historikfil. I iLoppis-läge läses den av bakgrundssynk, historik, CSV-export och vyn för senaste köp. | **Kritisk, aktiv** |
| `rejected_purchases.jsonl` | Poster som backend avvisat. Används av `RejectedItemsStore`, den röda statusindikatorn och dialogen där poster kan rättas eller tas bort. | **Aktiv, iLoppis-felsökning** |
| `archive/` | Katalog för arkiverade CSV-filer efter utbetalning. Används bara för lokala evenemang. iLoppis använder inte detta flöde. | **Aktiv, lokal-läge** |

### 5.1 Viktig tolkning

- `pending_items.jsonl` är den enda filen som är direkt affärskritisk för köpflödet.
- `pending_items.jsonl` ska tolkas som hela köploggen, inte bara "väntande" poster.
- `archive/` är relevant för lokala evenemang men inte för iLoppis.

### 5.2 Känslig data

- API-nyckeln ska inte ligga i rotkonfigurationen.
- API-nyckeln lagras nu event-specifikt i `iloppis_credentials.json`, separat från `iloppis_metadata.json`.
- `iloppis_credentials.json` visas inte i inställningsdialogen och ska inte skickas till support.
- När supportbundle skapas saneras `iloppis_metadata.json` och `iloppis-mode.json` så att `apiKey` inte följer med även om äldre filer skulle innehålla den.

### 5.3 Konsekvens för felsökning

- Vid misstanke om tappade köp är `pending_items.jsonl` den primära sanningskällan.
- `rejected_purchases.jsonl` är sekundär men viktig när servern har nekat poster.
- `iloppis_metadata.json` och `local_metadata.json` behövs för kontext om vilket evenemang som kördes.

---

## 6. Systemets interna felhantering (för tekniskt ansvarig)

### 6.1 BackgroundSyncManager-flödet

```
Kassör -> CashierTabController.persistItems()
   -> IloppisCashierStrategy.persistItems()
      -> BackgroundSyncManager.enqueueItems()
         -> flushQueueToDisk()  <- BLOCKERAR tills fil skriven
         -> notifyPendingCountChanged()  <- uppdaterar statusfält

Bakgrundstråd (var 30 sek):
   -> syncOnceInternal()
      -> POST /v1/events/{id}/sold-items
      -> Vid 200 OK: markera items som uploaded=true
      -> Vid 401/403: AuthErrorHandler -> kassakodsdialog
      -> Vid nätfel: sätter networkError=true, retries
      -> Vid rejected: sparar till rejected_purchases.jsonl
```

### 6.2 Felkoder från backend

| Felkod | Betydelse | Konsekvens |
|--------|-----------|------------|
| `INVALID_SELLER` | Säljnummer finns inte | Post -> rejected_purchases.jsonl |
| `DUPLICATE_RECEIPT` | Samma item_id redan mottagen | Idempotent — markeras uploaded |
| `UNSPECIFIED` | Okänt serverfel | Post -> rejected, manuell granskning |

### 6.3 Replay-procedur (efter incident)

1. Ta skrivskyddad kopia av allt insamlat
2. Identifiera alla `uploaded: false` poster i pending_items.jsonl
3. Kör replay via BulkUploadDialog
4. Hantera rejected-poster separat
5. Verifiera totaler mot backend
6. Dubblettskydd: server avvisar med DUPLICATE_RECEIPT — säkert att skicka igen

---

## 7. Konsolideringsnotering

Ersätter Issue 007 och 008. Baserad på fullständig kodgenomgång av alla JOptionPane/JDialog-klasser, AppShellStatusbar, BackgroundSyncManager, AuthErrorHandler, RejectedItemsHelper, ConnectivityChecker, FileHelper/JsonlHelper, och alla relevanta nycklar i sv.json.
