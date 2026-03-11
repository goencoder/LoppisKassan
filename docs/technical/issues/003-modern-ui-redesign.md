# Issue 003 – Modern UI Redesign: Loppiskassan 3.0

**Status:** In Progress (Foundation Complete)  
**Priority:** High  
**Created:** 2026-02-09  
**Updated:** 2026-02-09  
**Scope:** Full UX redesign – Lokal kassa + iLoppis  

---

## Implementation Status

### ✅ Completed

**Phase 0: Design Foundation**
- ✅ `AppColors.java` — Centralized color palette (18 semantic constants)
- ✅ `AppButton.java` — Reusable button component (5 variants × 4 sizes)
- ✅ All 13 UI files migrated to AppColors/AppButton (zero hardcoded colors outside palette)
- ✅ Card-based layouts replacing TitledBorder (EventCard with custom paintComponent)
- ✅ Button visibility fixes (Export/Import, Delete buttons now visible with proper styling)
- ✅ Build passing (29/29 tests)
- ✅ History tab performance + layout: localization caching (no per-filter JSON parse) and table auto-resize with tuned column widths/row height

**Del 1: Splash Screen** ✅ **Complete**
- ✅ `ModeSelectionDialog.java` — Mode selection: Lokal kassa vs iLoppis
- ✅ Language selector with flag icons (FlagIcon.java)
- ✅ Custom icons: MonitorIcon, ShopIcon
- ✅ Main.java wired to show splash before AppShellFrame

**Del 4: App Shell Redesign** ✅ **Complete**
- ✅ `AppShellFrame.java` — Complete app shell with BorderLayout
- ✅ `AppShellTopbar.java` — Topbar with app name, event badge (center), language selector (right)
- ✅ `AppShellSidebar.java` — Sidebar navigation with NavigationTarget enum
- ✅ `AppShellStatusbar.java` — Statusbar with connection status placeholders
- ✅ NavigationTarget system (DISCOVERY, CASHIER, HISTORY, EXPORT, ARCHIVE)
- ✅ View switching with validation (event must be selected)
- ✅ Mode-aware navigation (Export/Archive only in local mode)

**Del 2.3: Cashier UX** ✅ **Mostly Complete**
- ✅ Large typography: 36pt bold total, 24pt bold växel
- ✅ Växel calculation with color coding (green ≥0, red <0)
- ✅ Empty state panel with helpful text
- ✅ CardLayout switching between table and empty state
- ✅ Split pane layout: cart (70%) + total panel (30%)
- ✅ Keyboard shortcut labels visible (F2 Swish, F3 Kontant)
- ⚠️ **Partial**: F2/F3 KeyStroke registration not verified

**Del 6: Swedish Date Formatting** ✅ **Infrastructure Complete**
- ✅ `SwedishDateFormatter.java` — Utility with formatters for all patterns
- ✅ Patterns: "8 feb – 9 feb 2026", "10:12", "8 feb 10:12", "8 feb"
- ⚠️ **Partial**: Not yet applied throughout all UI files

**Custom Icons**
- ✅ MonitorIcon, ShopIcon, FlagIcon, ChevronDownIcon (all using AppColors)

### 🚧 Remaining Work

**Del 2.2: Event Selection Polish** (Partially Started)
- ⚠️ LocalDiscoveryTabPanel & DiscoveryTabPanel exist but need Swedish date formatting
- ❌ Event detail panel polish (split layout as per spec)
- ❌ Code entry dialog for iLoppis mode (XXX-XXX format with paste support)

**Del 2.3: Cashier Keyboard-First UX** (Needs Verification/Completion)
- ❌ F2/F3 KeyStroke.getKeyStroke registration + global key bindings
- ❌ Esc key for cancel checkout
- ❌ Delete/Backspace for removing cart items
- ❌ Enter/Tab focus flow enforcement (seller → prices → back to seller)
- ❌ Ctrl+Z undo support

**Del 2.4: History/Payout Redesign** (Not Started)
⚠️ Partial: filter toolbar spacing/layout improved (auto-resize columns, compact row height)
🔜 Seller-centric summary panel (local mode only): totals + provision + utbetalning using event revenue split
🔜 Compact inline filter toolbar per spec (single row: Säljare / Utbetalt / Betalning / Sök)
🔜 "Kopiera redovisning" button with formatted summary output (use available data: event name, seller, item count, total sum; if provision is present for local events include it; list items with price/payment/time when feasible)
🔜 "Betala ut" button + confirmation dialog (local mode only)
🔜 Provision calculation display (local mode summary)

**Del 2.5: Archive Functionality** (Base Exists)
- ⚠️ ArchiveTabPanel exists but needs polish per spec
- ❌ "Arkivera utbetalda" flow with confirmation

**Del 2.6: Export/Import Polish** (Base Exists)
- ⚠️ ExportImportTabPanel exists but needs spec-compliant layout
- ❌ Merge import with duplicate detection feedback dialog

**Del 3: iLoppis Online Flows** (Infrastructure Started)
- ✅ AppModeManager.isLocalMode() checks throughout
- ❌ Event list with two-panel layout (list + detail card)
- ❌ Code entry dialog (XXX-XXX with paste support for both formats)
- ❌ Auto-sync indicators in statusbar
- ❌ Offline queue display ("🟡 Offline – N poster väntar")

**Del 5: Visual Polish**
- ✅ Color palette, button components
- ❌ 8px spacing system enforcement (xs=4, sm=8, md=16, lg=24, xl=32)
- ❌ Typography hierarchy enforcement (11px help text, 13-14px body, 16px section headers, 20px titles)
- ❌ MigLayout migration (optional, currently using GridBagLayout/BoxLayout)

**Del 6: Interactions**
- ❌ Snackbar component with undo (JPanel overlay + Timer)
- ❌ Destructive confirmation dialogs ("skriv RADERA" for Rensa kassan)
- ❌ Apply SwedishDateFormatter throughout all date displays

**Del 7: Regressions & Cleanup**
- ❌ Search and replace FormatHelper.formatter (ISO format) with SwedishDateFormatter
- ❌ UserInterface.java – can it be removed? (still used or fully replaced by AppShellFrame?)
- ❌ UserInterface.createButton() – dead code removal if all buttons use AppButton

---

## Designprinciper

1. **UX-flöden styr** — Skärmar är konsekvenser av flöden, inte tvärtom
2. **Keyboard-first** — Kassören ska kunna arbeta utan mus
3. **Minimal friktion** — Varje extra knapptryck kostar tid och pengar
4. **Tydlig kontext** — Användaren ser alltid var hen är, vilket evenemang som är aktivt
5. **Svensk UX** — Svenska som primärspråk, engelska som sekundärt

---

## Del 1: Splash – Första valet

### Flöde

Användaren startar appen → ser två tydliga val:

```
┌──────────────────────────────────────────┐
│          iLoppis Kassa                   │
│     Välj hur du vill använda kassan      │
│                                          │
│   ┌──────────┐     ┌──────────┐          │
│   │  Lokal   │     │ iLoppis  │          │
│   │  kassa   │     │          │          │
│   └──────────┘     └──────────┘          │
│                                     🇸🇪 ▼ │
└──────────────────────────────────────────┘
```

**Lokal kassa** → Offline-flödet (Del 2)  
**iLoppis** → Online-flödet (Del 3)

Språkväljare finns i splash. Valt språk sparas och används vid nästa start. Svenska är alltid startspråk vid första körning.

---

## Del 2: Lokal kassa – Offline-flöden

### 2.1 Översikt av moment

En lokal kassa-session består av dessa moment i kronologisk ordning:

1. **Skapa/Välj evenemang** — Definiera loppisen
2. **Kassainmatning** — Registrera försäljningar (huvudflödet, mest tid här)
3. **Utbetalning** — Redovisa per säljare och betala ut
4. **Arkivering** — Arkivera utbetalda poster
5. **Export/Import** — Hantera data mellan kassor

---

### 2.2 Skapa/Välj evenemang

#### Flöde: Första gången (inget evenemang finns)

```
Starta appen → Lokal kassa → "Skapa nytt evenemang"
```

Användaren fyller i:
- **Namn**: "Sillfest"
- **Plats**: "Skärkarlsvägen 1, Sillinge"
- **Datum**: 8 feb – 9 feb 2026
- **Försäljningsdelning**: Arrangör 10% / Säljare 85% / iLoppis 5%

→ Evenemang skapas → Kassan öppnas direkt.

#### Flöde: Återkommande (evenemang finns)

```
Starta appen → Lokal kassa → Ser senast använda evenemang → "Starta kassa"
```

Användaren kan byta evenemang via sidomenyn.

#### UX-regler
- Senast använda evenemang väljs automatiskt
- Evenemangets namn + datum syns alltid i topbaren
- Datumformat: `8 feb 13:25 – 9 feb 13:25` (aldrig ISO-format)

---

### 2.3 Kassainmatning (huvudflödet)

Detta är den viktigaste skärmen. Kassören spenderar 90% av tiden här. Varje extra knapptryck multipliceras med hundratals köp.

#### Inmatningsloop

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│  START: Markör i säljnummerfältet                   │
│    ↓                                                │
│  [1] Skriv säljnummer → Enter/Tab                   │
│    ↓                                                │
│  [2] Markör hoppar till prisfältet                  │
│    ↓                                                │
│  [3] Skriv pris(er) "15 25 50" → Enter              │
│    ↓                                                │
│  [4] Varor läggs till i varukorgen                  │
│    ↓                                                │
│  [5] Markör hoppar tillbaka till säljnummerfältet   │
│    ↓                                                │
│  → Tillbaka till [1] — naturlig loop                │
│                                                     │
│  NÄR KÖPET ÄR KLART:                               │
│    F3 = Kontant   F2 = Swish                        │
│    ↓                                                │
│  [6] Betalning registreras                          │
│    ↓                                                │
│  [7] Varukorgen nollställs                          │
│    ↓                                                │
│  [8] Markör tillbaka i säljnummerfältet             │
│    ↓                                                │
│  → Redo för nästa kund                              │
│                                                     │
└─────────────────────────────────────────────────────┘
```

#### Skärmlayout

```
┌──────────────────────────────────────────────────────────────┐
│ iLoppis Kassa   Sillfest • 8–9 feb                    🇸🇪    │
│──────────────────────────────────────────────────────────────│
│ ▸ Kassa     │                                               │
│   Historik  │  Säljnr        Pris(er)                       │
│   Export    │  ┌────────┐    ┌──────────────────────────────┐│
│   Arkiv     │  │   5    │    │  15 25 50                   ││
│             │  └────────┘    └──────────────────────────────┘│
│             │  Enter = Lägg till   Delete = Ta bort   Esc = Avbryt│
│             │────────────────────────────────────────────────│
│             │                                               │
│             │  Varukorg                          Totalt      │
│             │  ┌─────────────────────────┐  ┌────────────┐  │
│             │  │ #  Säljare   Pris       │  │            │  │
│             │  │─────────────────────────│  │ Att betala: │  │
│             │  │ 1    5       15 kr      │  │            │  │
│             │  │ 2    5       25 kr      │  │  382 kr    │  │
│             │  │ 3    5       50 kr      │  │  (stor)    │  │
│             │  │ 4    2       95 kr      │  │            │  │
│             │  │ 5    2       20 kr      │  │ 6 varor    │  │
│             │  │ 6    2       12 kr      │  │            │  │
│             │  │                         │  │ Betalt:    │  │
│             │  │                         │  │ ┌────────┐ │  │
│             │  │                         │  │ │  400   │ │  │
│             │  │                         │  │ └────────┘ │  │
│             │  │                         │  │            │  │
│             │  │                         │  │ Växel:     │  │
│             │  │                         │  │  18 kr 🟢  │  │
│             │  └─────────────────────────┘  └────────────┘  │
│             │────────────────────────────────────────────────│
│             │  [Avbryt köp]      [Kontant F3]    [Swish F2]  │
│──────────────────────────────────────────────────────────────│
│ 🟢 Lokal kassa                                              │
└──────────────────────────────────────────────────────────────┘
```

#### Tangentbordsschema (komplett)

| Tangent | Kontext | Åtgärd |
|---------|---------|--------|
| `Enter` | Säljnummerfält | Flytta till prisfält |
| `Tab` | Säljnummerfält | Flytta till prisfält |
| `Enter` | Prisfält | Lägg till varor, återgå till säljnummer |
| `Delete` | Varukorg (markerad rad) | Ta bort markerad rad |
| `Backspace` | Varukorg (ingen markering) | Ta bort senast tillagda rad |
| `F2` | Kassan | Betala med Swish |
| `F3` | Kassan | Betala med Kontant |
| `Esc` | Kassan | Avbryt köp (bekräftelsedialog) |
| `Ctrl+Z` | Kassan | Ångra senaste åtgärd |

#### Summa- och växelvisning

- **Att betala**: 28–36pt bold, alltid synlig i höger panel
- **Växel**: Grön om ≥ 0, röd om negativ
- **Betalt-fältet**: Frivilligt — om tomt, antas exakt belopp

#### Tom varukorg (empty state)

```
┌──────────────────────────────────────┐
│          🛒                          │
│                                      │
│    Skriv säljnummer och pris,        │
│    tryck Enter för att lägga till    │
│                                      │
│    F2 = Swish  •  F3 = Kontant      │
└──────────────────────────────────────┘
```

#### Efter betalning — snackbar

```
┌──────────────────────────────────────────────────┐
│  ✔ Köp registrerat (Swish, 382 kr)       [Ångra] │
└──────────────────────────────────────────────────┘
```

Visas i 4 sekunder. "Ångra" återställer köpet till varukorgen.

---

### 2.4 Historik och utbetalning

#### Flöde: Utbetalning per säljare

```
Navigera till Historik 
  → Välj säljare i filter
  → Se sammanfattning: totalt, provision, att utbetala
  → Klicka "Betala ut"
  → Bekräftelsedialog
  → Posterna markeras som utbetalda
```

#### Skärmlayout

```
┌──────────────────────────────────────────────────────────────┐
│ HISTORIK                                                     │
│──────────────────────────────────────────────────────────────│
│  Säljare: [ 5 ▼ ]   Utbetalt: [ Nej ▼ ]   Betalning: [Alla ▼]│
│  Sök: [_______________]                                      │
│──────────────────────────────────────────────────────────────│
│  ┌─ Sammanfattning för säljare 5 ────────────────────────┐  │
│  │  Sålda varor: 12       Totalt: 527 kr                 │  │
│  │  Provision (15%): 79 kr     Utbetalas: 448 kr         │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Tid     Pris      Betalning   Utbetalt                │  │
│  │────────────────────────────────────────────────────────│  │
│  │ 10:12    12 kr    Swish        Nej                    │  │
│  │ 10:13   150 kr    Kontant      Nej                    │  │
│  │ 10:14    10 kr    Kontant      Nej                    │  │
│  └────────────────────────────────────────────────────────┘  │
│──────────────────────────────────────────────────────────────│
│  [Kopiera redovisning]   [Betala ut]   [Arkivera utbetalda] │
│──────────────────────────────────────────────────────────────│
│                    [ ⚠ Rensa kassan ]                        │
└──────────────────────────────────────────────────────────────┘
```

#### UX-regler

- **Sammanfattningskortet** uppdateras direkt när säljare väljs
- **Provision** beräknas automatiskt utifrån evenemangets försäljningsdelning
- **Tidsformat**: `10:12` inom evenemangets dag, `8 feb 10:12` om det sträcker sig över flera dagar
- **"Rensa kassan"**: Röd knapp, kräver att användaren skriver `RADERA` i bekräftelsedialog
- **"Kopiera redovisning"**: Kopierar en formaterad sammanfattning per säljare till urklipp

#### Redovisningsformat (kopieras till urklipp)

```
Redovisning – Sillfest
Säljare: 5
Datum: 8 feb 2026

Sålda varor: 12
Totalt: 527 kr
Provision (15%): 79 kr
Utbetalas: 448 kr

Varor:
  12 kr (Swish, 10:12)
  150 kr (Kontant, 10:13)
  10 kr (Kontant, 10:14)
  ...
```

---

### 2.5 Arkivering (enbart lokal kassa)

#### Flöde

```
Historik → Filtrera på "Utbetalt: Ja"
  → Klicka "Arkivera utbetalda"
  → Bekräftelsedialog: "Arkivera 47 utbetalda poster?"
  → Posterna flyttas till arkivfil
  → Historiktabellen rensas på dessa poster
```

#### UX-regler
- Arkiverade poster försvinner från historiken men finns kvar på disk
- Statusraden bekräftar: "47 poster arkiverade"
- Arkiverade poster kan inte återställas via UI (medveten förenkling)

---

### 2.6 Export och import (multi-kassa, lokal)

#### Flöde: Export

```
Navigera till Export → Exportera kassadata
  → Fil sparas med evenemangsnamn: "sillfest_kassa1.jsonl"
  → Statusrad: "Exporterad: 124 poster"
```

#### Flöde: Import (sammanslå kassor)

```
Navigera till Export → Importera kassadata
  → Välj fil (filechooser)
  → Poster mergas in i aktuellt evenemang
  → Dubletter (samma post-ID) ignoreras automatiskt
  → Resultatdialog: "95 nya poster importerade, 3 dubletter ignorerade"
```

Resultatdialogen ger direkt feedback om vad som hände. Efter att dialogen stängts finns ingen spårbarhet kring vilken fil en post kom ifrån — alla poster är likvärdiga.

#### Skärmlayout

```
┌──────────────────────────────────────────────────────────────┐
│ EXPORT / IMPORT                                              │
│──────────────────────────────────────────────────────────────│
│  Aktuellt evenemang: Sillfest (124 poster)                   │
│──────────────────────────────────────────────────────────────│
│                                                              │
│  Exportera all kassadata till en fil som kan importeras      │
│  i en annan kassa för sammanställning.                       │
│                                                              │
│  Vid import mergas posterna in. Dubletter ignoreras          │
│  automatiskt baserat på post-ID.                             │
│                                                              │
│──────────────────────────────────────────────────────────────│
│  [Importera kassadata…]              [Exportera kassadata…]  │
└──────────────────────────────────────────────────────────────┘
```

---

## Del 3: iLoppis – Online-flöden

### 3.1 Översikt av moment

En iLoppis-session består av:

1. **Anslut till evenemang** — Hämta evenemang + ange kassakod
2. **Kassainmatning** — Identisk med lokal kassa (samma UX-loop)
3. **Synkronisering** — Automatisk uppladdning av försäljningar
4. **Historik** — Visa + filtrera (utan utbetalning, arkivering eller rensning)

Utbetalning, arkivering och export/import hanteras av iLoppis webbgränssnitt. Desktop-kassan fokuserar på inmatning och synkronisering.

---

### 3.2 Anslut till evenemang

#### Flöde

```
Starta appen → iLoppis
  → Hämta tillgängliga evenemang (API)
  → Välj evenemang i listan
  → Se evenemangsdetaljer (namn, plats, datum, delning)
  → Klicka "Starta kassa"
  → Ange kassakod (XXX-XXX) i dialog
  → Kod valideras mot API
  → Kassan öppnas
```

#### Skärmlayout: Evenemangsval

```
┌──────────────────────────────────────────────────────────────┐
│ VÄLJ EVENEMANG                                               │
│──────────────────────────────────────────────────────────────│
│  Datum: [senaste 30 dagar ▼]   [Hämta evenemang]             │
│  Sök: [_______________]                                      │
│──────────────────────────────────────────────────────────────│
│  ┌────────────────────┐   ┌──────────────────────────────┐  │
│  │ Evenemangslista    │   │ Evenemangsdetaljer           │  │
│  │                    │   │                              │  │
│  │ ▸ Sillfest         │   │ 📍 Skärkarlsvägen 1, Sillinge │  │
│  │   Sillinge         │   │ 📅 8 feb 13:25 – 9 feb 13:25 │  │
│  │   8–9 feb          │   │                              │  │
│  │                    │   │ Allt för att fånga sill!     │  │
│  │   Vårmarknad       │   │                              │  │
│  │   Malmö            │   │ Arrangör  10%               │  │
│  │   15–16 mar        │   │ Säljare   85%  ██████████▓  │  │
│  │                    │   │ iLoppis    5%               │  │
│  │                    │   │                              │  │
│  │                    │   │      [ Starta kassa → ]      │  │
│  └────────────────────┘   └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

#### Kassakod-dialog (efter klick på "Starta kassa")

```
┌──────────────────────────────────┐
│  Ange kassakod                   │
│                                  │
│  ┌────────────────────────────┐  │
│  │      XXX-XXX               │  │
│  └────────────────────────────┘  │
│                                  │
│  Kassakoden delas ut av          │
│  evenemangets arrangör.          │
│                                  │
│  [Avbryt]    [Anslut →]         │
└──────────────────────────────────┘
```

#### UX-regler
- Kassakoden stödjer inklistring (Ctrl+V) i båda formaten: `XXX-XXX` och `XXXXXX`
- Felaktig kod → tydligt felmeddelande, fältet rensas inte
- Lyckad anslutning → kassan öppnas, evenemangsbadge syns i topbaren

---

### 3.3 Kassainmatning (identisk UX)

Kassainmatningen i iLoppis-läge använder **exakt samma skärm och UX-loop** som lokal kassa (se avsnitt 2.3).

Enda skillnaderna:

| Aspekt | Lokal kassa | iLoppis |
|--------|------------|---------|
| Statusrad | `🟢 Lokal kassa` | `🟢 Ansluten till iLoppis` |
| Synk | Ingen | Automatisk uppladdning |
| Synk-indikator | Ingen | Spinner vid aktiv synk |
| Offline-hantering | Alltid offline | Köar poster vid tappad anslutning |

Vid tappat nätverk:
```
┌──────────────────────────────────────────────────────────────┐
│ 🟡 Offline – 3 poster väntar på synkronisering              │
└──────────────────────────────────────────────────────────────┘
```

Kassören kan fortsätta arbeta. Poster laddas upp automatiskt när anslutningen återkommer.

---

### 3.4 Synkronisering

#### Flöde (automatiskt)

```
Kassainmatning → Betalning registreras
  → Post sparas lokalt (offline-first)
  → Bakgrundstråd laddar upp till iLoppis API
  → Statusrad uppdateras: "Synkad" / "3 poster väntar"
```

#### Manuell synk

Klicka synk-ikonen i statusraden → tvångssynkronisera alla väntande poster.

---

### 3.5 Historik (iLoppis-läge)

Samma skärm som lokal historik (avsnitt 2.4) med dessa skillnader:

| Funktion | Lokal kassa | iLoppis |
|----------|------------|---------|
| Filtrera/söka | ✔ | ✔ |
| Kopiera redovisning | ✔ | ✔ |
| Betala ut | ✔ (lokal beräkning) | ❌ (webbgränssnittet) |
| Arkivera utbetalda | ✔ | ❌ (backend) |
| Rensa kassan | ✔ | ❌ (data synkas) |

---

## Del 4: Global struktur – App Shell

### Layout

```
┌──────────────────────────────────────────────────────────────┐
│  iLoppis Kassa   Sillfest • 8–9 feb 2026     🟢 Online   🇸🇪 │
│──────────────────────────────────────────────────────────────│
│  ▸ Kassa      │                                             │
│    Historik   │                                             │
│    Export     │          (huvudyta – aktuell vy)             │
│    Arkiv      │                                             │
│               │                                             │
│               │                                             │
│──────────────────────────────────────────────────────────────│
│  🟢 Lokal kassa  •  Senaste åtgärd: 14:25                   │
└──────────────────────────────────────────────────────────────┘
```

### Topbar

| Position | Innehåll |
|----------|----------|
| Vänster | Appnamn: "iLoppis Kassa" |
| Mitten | Aktivt evenemang: `Sillfest • 8–9 feb 2026` |
| Höger | Online/offline-status, språkväljare |

### Sidebar

Navigeringspunkter per läge:

| Lokal kassa | iLoppis |
|-------------|---------|
| Kassa | Kassa |
| Historik | Historik |
| Export / Import | — |
| Arkiv | — |

### Statusrad

| Lokal kassa | iLoppis |
|-------------|---------|
| `🟢 Lokal kassa` | `🟢 Ansluten till iLoppis` |
| Senaste åtgärd: tidstämpel | Senaste synk: tidstämpel |
| — | `🟡 Offline – N poster väntar` (vid tappat nätverk) |

---

## Del 5: Visuell design

### Spacing (8px-system)

| Token | Värde | Användning |
|-------|-------|------------|
| `xs` | 4px | Minimala mellanrum |
| `sm` | 8px | Inuti komponenter |
| `md` | 16px | Mellan komponenter |
| `lg` | 24px | Mellan sektioner |
| `xl` | 32px | Sidmarginaler |

### Typografi

| Roll | Storlek | Vikt | Exempel |
|------|---------|------|---------|
| Sidtitel | 20px | Bold | "KASSA", "HISTORIK" |
| Sektionsrubrik | 16px | Bold | "Varukorg", "Sammanfattning" |
| Brödtext | 13–14px | Regular | Etiketter, beskrivningar |
| Totalsumma | 28–36px | Bold | "382 kr" |
| Hjälptext | 11px | Regular | Tangentbordsgenvägar, tidsangivelser |

### Färger

| Roll | Hex | Användning |
|------|-----|------------|
| Primär | `#4A90D9` | Primära knappar, markerad menypost |
| Swish-accent | `#5A2D82` | Swish-knapp |
| Kontant | `#6B7280` | Kontant-knapp |
| Positiv | `#4CAF50` | Positiv växel, bekräftelser |
| Destruktiv | `#E53E3E` | Avbryt, radera, rensa kassan |
| Bakgrund | `#F5F5F5` | Huvudinnehållsyta |
| Kort | `#FFFFFF` | Kortbakgrunder |
| Text primär | `#2D3748` | Rubriker, brödtext |
| Text sekundär | `#718096` | Hjälptext, tidsangivelser |

### Ytor och kort

- **Kort** istället för `TitledBorder`/GroupBox
- Vit bakgrund, 1px `#E2E8F0` kant, 8px hörnradie
- `EmptyBorder(16, 16, 16, 16)` inre padding
- Mer luft, färre ramar

### FlatLaf-konfiguration

```java
FlatLightLaf.setup();
UIManager.put("Button.arc", 8);
UIManager.put("Component.arc", 8);
UIManager.put("TextComponent.arc", 6);
UIManager.put("ScrollBar.width", 10);
```

---

## Del 6: Interaktioner

### Snackbar (bekräftelse/ångra)

```
┌──────────────────────────────────────────────────┐
│  ✔ Köp registrerat (Swish, 382 kr)       [Ångra] │
└──────────────────────────────────────────────────┘
```

- Visas i 4 sekunder, auto-dismiss
- "Ångra" återställer åtgärden
- Implementation: `JPanel`-overlay + `javax.swing.Timer`

### Destruktiva åtgärder

"Rensa kassan" kräver:
1. Röd knappstil
2. Bekräftelsedialog: "Skriv RADERA för att bekräfta"
3. Knappen aktiveras först när texten matchar

### Datumformat

| Kontext | Format | Exempel |
|---------|--------|---------|
| Evenemangsperiod | `d MMM – d MMM yyyy` | `8 feb – 9 feb 2026` |
| Tid inom evenemang | `HH:mm` | `10:12` |
| Tid med datum | `d MMM HH:mm` | `8 feb 10:12` |

Använd `DateTimeFormatter.ofPattern("d MMM HH:mm", new Locale("sv"))`.

---

## Del 7: Före / Efter

| Område | Idag | Nytt |
|--------|------|------|
| Navigation | `JTabbedPane` (3 flikar) | App shell: sidebar + topbar |
| Evenemangskontext | Dold bakom flik | Alltid synlig i topbar |
| Tidsformat | `2026-02-08T13:25Z` | `8 feb 13:25` |
| "Öppna kassa" | Frikopplad knapp | "Starta kassa →" med kassakod-flöde |
| Kassans total | Liten text inline | 28–36pt bold, egen panel |
| Växel | Liten text | Stor, färgkodad (grön/röd) |
| Betalningsbekräftelse | Ingen | Snackbar med ångra |
| Historikfilter | 4 vertikala rader | Kompakt enrads-toolbar |
| Historiksammanfattning | Antal + summa | Säljar-centrerad: total, provision, utbetalning |
| "Rensa kassan" | Samma stil som andra knappar | Röd + "skriv RADERA" |
| Import | Gömd bakom knapp | Egen vy med statustabell |
| Tomma ytor | Tom yta | Hjälpande text + ikoner |
| Filformat | Exponerat i UI | Dolt (JSONL hanteras internt) |

---

## Del 8: Implementationsplan

### Fas 1: App Shell + Navigation (2–3 dagar)
- `AppShellFrame` ersätter `UserInterface`
- Sidebar-navigering
- Topbar med evenemangsbadge
- Statusrad

### Fas 2: Kassainmatning – Modernisering (3–4 dagar)
- Delad layout: varukorg (vänster) + totalpanel (höger)
- Stor typografi för summor
- Tangentbordsgenvägar synliga
- Snackbar/ångra-system
- Empty state

### Fas 3: Evenemangsvy (2 dagar)
- Tvåpanelslayout: lista + detaljkort
- Svenska datumformat
- "Starta kassa"-flöde med kassakod-dialog (iLoppis)

### Fas 4: Historik och utbetalning (2–3 dagar)
- Kompakt filter-toolbar
- Säljar-centrerad sammanfattning med provision
- Destruktiv bekräftelse för "Rensa kassan"

### Fas 5: Export/Import/Arkiv – Lokal kassa (1–2 dagar)
- Dedikerad export/import-vy
- Merge-import med dublettfiltrering (post-ID)

### Fas 6: Polish (2 dagar)
- Genomgående designtokens
- Mörkt läge (FlatLaf inbyggt)
- SVG-ikoner (FlatLaf IntelliJ-ikoner)

**Total uppskattad insats: 12–16 dagar**

---

## Del 9: Swing-komponentmappning

| UI-koncept | Swing-implementation |
|------------|---------------------|
| App shell | `JFrame` + `BorderLayout` (NORTH=topbar, WEST=sidebar, CENTER=content, SOUTH=statusrad) |
| Sidebar | `JPanel` + `BoxLayout.Y_AXIS`, custom toggle-knappar |
| Kort | `JPanel` med FlatLaf-rundad kant + `EmptyBorder`-padding |
| Topbar | `JPanel` + `BorderLayout` med `JLabel`-badges |
| Snackbar | `JPanel`-overlay, `javax.swing.Timer` för auto-dismiss |
| Sammanfattningskort | `JPanel` med `GridBagLayout` |
| Kassainmatning | `JTextField` med `ActionListener` + `FocusListener` |
| Varukorg | `JTable` med custom `TableModel` |
| Totalpanel | `JPanel` med stor `JLabel` (28–36pt font) |
| Filter-toolbar | `JPanel` med `JComboBox` + `JTextField` inline |

### Beroenden
- **FlatLaf** — redan i projektet, inget nytt beroende
- **MigLayout** — ersätter `GridBagLayout` (renare syntax, läggs till som beroende)
