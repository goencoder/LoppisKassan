# KASSACHECKLIST — skriv ut dubbelsidig och laminera

---

# SIDA 1: NORMALT KASSABETEENDE

## Så fungerar kassan

Kassan sparar varje köp **lokalt på datorn** direkt.
Uppladdning till servern sker i bakgrunden var 30:e sekund.
Om nätet går ner fortsätter kassan att fungera — köp sparas lokalt.

## Statusfältet (nedre vänstra hörnet)

```
  [Grön prick]  Ansluten till iLoppis      = Allt OK
  [Gul prick]   Väntar på uppladdning (N)   = Nätproblem, men köp sparas
  [Röd prick]   Avvisade poster - N         = Klicka och fixa
```

## Normal arbetsordning

```
  1. Skriv säljnummer     ->  TAB
  2. Skriv pris           ->  ENTER
  3. Upprepa för fler varor
  4. Välj betalmetod (Kontant / Swish)
  5. Slutför köp
```

Markören hoppar automatiskt tillbaka till säljnummer efter varje köp.

## Vanliga dialogrutor (inget farligt)

```
  "Felaktigt säljnummer"
  -> Du skrev fel. Skriv rätt säljnummer (bara siffror). Tryck OK.

  "Felaktigt pris"
  -> Priset måste vara ett heltal (inga kommatecken). Tryck OK.

  "Säljare ej godkänd"
  -> Numret finns inte i listan. Kontrollera med säljaren.

  "Kassakoden kunde inte verifieras"
  -> Fel kod. Be loppisansvarig om rätt kod. Försök igen.
```

Alla dessa är **normala misstag**. Korrigera och fortsätt.

## Kassakodsdialog

```
  "Ange kassakod för att öppna..."
  eller
  "Inloggningen är inte giltig. Ange ny kassakod."
```

-> Be loppisansvarig om koden (XXX-XXX).
-> Mata in koden. Kassan öppnar.
-> Inga köp förloras — de finns kvar lokalt.

## Gult statusfält / Degraderat läge

```
  "Nätverksfel - Kassa i degraderat läge"
  "Väntar på uppladdning (N)"
  "Offline - poster väntar på synkronisering"
```

-> Tryck OK om dialog visas.
-> **Fortsätt sälja som vanligt.**
-> Köp sparas lokalt och synkas automatiskt senare.

## Offlinestart

```
  "Kassa öppnad (offline)"
  "Gammal cachad data - fortsätta ändå?"
```

-> Tryck OK / Ja.
-> Kassan fungerar men säljarlistan KAN vara gammal.
-> Fortsätt sälja.

---

# SIDA 2: RISK / ALERT

## KRITISKA FELMEDDELANDEN — kräver åtgärd NU

```
  !! "Fel vid skrivning till kassafil"        !!
  !! "Problem med filrättigheter"             !!
  !! "Kunde inte skapa kataloger"             !!
```

### Om du ser NÅGOT av dessa:

```
  1. STOPPA kassaregistrering på denna dator
  2. RÖR INGENTING mer på datorn
  3. INFORMERA loppisansvarig omedelbart
  4. BYT till annan dator eller pappersläge
```

## TYST FEL — Kontrollera detta

Om du registrerar köp men **siffran i gula statusfältet inte ökar**:

```
  Förväntat:  Registrera köp -> siffran ökar med 1
  Problem:    Siffran står stilla trots nya köp
```

-> Misstänk filfel. Följ samma åtgärd som ovan (STOPPA kassan).

## RÖDA PRICKEN — Avvisade poster

```
  [Röd prick] "Avvisade poster - 2"
```

-> Klicka på röda pricken.
-> Dialogen visar vilka köp som nekades och varför.

```
  Orsak: INVALID_SELLER
  -> Klicka "Ändra", skriv rätt säljnummer, klicka "Spara"
  -> Posten skickas automatiskt igen inom 30 sek

  Orsak: DUPLICATE_RECEIPT
  -> Ignorera. Köpet finns redan registrerat.

  Orsak: annat
  -> Skriv ner detaljerna. Rapportera till loppisansvarig.
```

## NÄR SKA DATORER SAMLAS IN?

### Samla INTE in under normal drift

Grönt eller gult i statusfältet = allt är OK.
Vanliga felmeddelanden (säljnummer/pris/kod) = korrigera och fortsätt.

### Samla in OMEDELBART vid filfel

Om du såg "Fel vid skrivning" / "filrättigheter" / "kataloger":

```
  1. Stäng INTE programmet
  2. Kopiera hela mappen:  ~/.loppiskassan/
  3. Packa i zip: kassa-X-datum-tid.zip
  4. Ge till tekniskt ansvarig
```

### Samla in EFTER loppisdag (alltid)

```
  Från VARJE kassadator, kopiera:

  ~/.loppiskassan/events/<eventId>/pending_items.jsonl
  ~/.loppiskassan/events/<eventId>/rejected_purchases.jsonl
  ~/.loppiskassan/events/<eventId>/sold_items.jsonl
  ~/.loppiskassan/logs/loppiskassan.log
  ~/.loppiskassan/config/iloppis-mode.json

  Zip:  kassa-1-20260324-1530.zip
        kassa-2-20260324-1530.zip
        ...osv

  Skicka till tekniskt ansvarig.
```

## KONTAKTLISTA

| Roll | Namn | Telefon |
|------|------|---------|
| Loppisansvarig | _____________ | _____________ |
| Tekniskt ansvarig | _____________ | _____________ |
| Backup-kontakt | _____________ | _____________ |

## SAMMANFATTNING

```
  GRÖNT/GULT = Fortsätt sälja
  RÖD PRICK  = Klicka och fixa avvisade poster
  FILFEL     = STOPPA kassan, kopiera filer, byt dator
  VID TVIVEL = Ring loppisansvarig
```
