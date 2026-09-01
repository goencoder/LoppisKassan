# 007 Supportpaket kan inte skapas med låsta loggfiler i Windows

## Status

Inte påbörjad.

Skapad: 2026-08-13.

## Ägare

Arkitekt/Team Lead (@ripley), Desktop Engineer (@rosemary), Security Reviewer (@ash), Code Reviewer (@wednesday) och E2E Tester (@norman).

## Mål

Det ska gå att skapa en giltig supportfil från en körande Loppiskassan i Windows. Paketet ska innehålla användbara loggar, men inte Java-loggningens låsfiler, och ett fel i en enskild loggfil ska inte lämna kvar ett missvisande eller trasigt ZIP-arkiv.

## Affärsnytta

Vid en driftstörning behöver supporten snabbt få in underlag från kassadatorn. Om supportfilen inte kan skapas medan kassan körs försvinner den avsedda självservicevägen just när den behövs som mest. Manuell filinsamling eller omstart av kassan tar tid, ökar risken att viktig felsökningsinformation tappas och belastar personalen under loppisen.

## Problembeskrivning

`Main.createLogger()` skapar en roterande `java.util.logging.FileHandler` för `loppiskassan.log` med fem generationer och behåller den ansluten till root-loggern under hela programkörningen (`src/main/java/se/goencoder/loppiskassan/Main.java`). Java skapar då normalt datafiler som `loppiskassan.log.0` och en kontroll-/låsfil som `loppiskassan.log.0.lck`.

`DataBundleExporter.collectLogFiles()` väljer i dag alla vanliga filer vars namn börjar med `loppiskassan.log`. Det inkluderar därmed även `.lck`-filen. Därefter kopierar `addFileEntry()` varje vald fil direkt till ZIP-strömmen med `Files.copy(...)` (`src/main/java/se/goencoder/loppiskassan/controller/DataBundleExporter.java`).

Den mest specifika hypotesen är att exporten i Windows försöker läsa den exklusivt låsta `.lck`-filen och avbryts med `IOException`. Den aktiva dataloggen kan också vara låst beroende på JDK-version, filsystem och Windows share mode. Det senare ska verifieras i en reproduktion och inte antas utan evidens.

Nuvarande `DataBundleExporterTest` använder endast en stängd temporär fil med namnet `loppiskassan.log`. Testet täcker varken Java `FileHandler`:s verkliga filnamn eller låsning medan loggningen är aktiv.

## Berörda repos

- `Loppiskassan`

## Berörda områden

- initiering och livscykel för `java.util.logging.FileHandler`
- urval och ZIP-packning av loggfiler i `DataBundleExporter`
- felhantering och städning av delvis skapade supportfiler
- enhets- och integrationstester för supportpaket
- manuell verifiering på Windows
- vid behov lokaliserade fel- eller varningstexter på supportsidan

## Avgränsningar

- Ingen uppladdning av supportfilen till en server.
- Ingen ändring av supportpaketets event- eller konfigurationsdata.
- Ingen ny loggplattform eller extern loggningsdependency enbart för detta fel.
- Ingen generell lösning för att läsa godtyckliga filer som låses av andra processer.
- Ingen implementation ingår i denna issuefil.

## Begränsningar

- Exporten ska fungera medan Loppiskassan fortsätter köra.
- `.lck` och andra kontrollfiler får aldrig tas med som supportdata.
- Loggning får inte permanent stängas av eller skapa dubbla handlers efter export.
- Filnamn och ZIP-innehåll ska vara deterministiska och inte bero på katalogens listningsordning.
- Källfilerna får inte ändras eller raderas av exporten.
- En misslyckad export ska rapportera ett begripligt, lokaliserat fel och den ofullständiga ZIP-filen ska tas bort eller aldrig publiceras på den valda slutdestinationen.
- Ingen ny dependency får läggas till utan separat godkännande.

## Lösningsalternativ

### Alternativ A — strikt urval av riktiga loggfiler (rekommenderad första åtgärd)

Ändra loggurvalet från ett brett `startsWith("loppiskassan.log")` till ett uttryckligt kontrakt för basfilen och numrerade rotationsfiler. Exkludera alltid `.lck`, temporära filer, kataloger och okända suffix. Flusha loggern före export och kopiera de läsbara datafilerna utan att stänga handlern.

Fördelar:

- liten och lågriskfylld kodändring;
- angriper den mest sannolika direkta orsaken;
- ingen paus eller omkonfigurering av loggningen;
- lätt att enhetstesta.

Nackdelar:

- räcker inte om Windows/JDK-konfigurationen även hindrar läsning av den aktiva dataloggen;
- en loggrad som skrivs samtidigt som filen kopieras kan ge en tidsmässigt inkonsekvent slutpunkt.

Detta alternativ ska väljas om Windows-reproduktionen visar att `.lck` är den enda blockerade filen.

### Alternativ B — kontrollerad checkpoint av `FileHandler`

Gör den aktiva filhandlern till en livscykelhanterad komponent. Vid export: blockera samtidig handler-omkonfigurering, flusha och stäng handlern, skapa supportpaketet från de nu olåsta datafilerna och öppna sedan en ny handler i ett `finally`-block. Exporten bör skrivas till en temporär fil och flyttas atomiskt till användarens valda destination först när den är komplett.

Fördelar:

- ger en tydlig filcheckpoint och fungerar även om den aktiva dataloggen är låst;
- bevarar befintligt loggformat och rotationsupplägg;
- kräver ingen ny dependency.

Nackdelar:

- mer riskfylld livscykel- och trådhantering;
- loggrader kan tappas under det korta intervallet om loggning inte buffras eller serialiseras korrekt;
- fel vid återöppning kan lämna applikationen utan filloggning och måste därför hanteras explicit.

Detta alternativ ska väljas om Windows-reproduktionen visar att även den aktiva dataloggen inte kan läsas så länge handlern är öppen.

### Alternativ C — best-effort-export med tydlig varning

Exkludera `.lck`, försök kopiera varje tillåten datalogg separat och fortsätt om en fil ger ett lås- eller åtkomstfel. Paketets manifest ska skilja på inkluderade och utelämnade loggar samt ange en säker, icke-känslig felkategori. UI:t ska tydligt säga att supportfilen skapades med en varning. Stängda rotationsgenerationer prioriteras och den aktiva filen försöks sist.

Fördelar:

- en låst fil blockerar inte insamling av eventdata, sanerad konfiguration och läsbara historiska loggar;
- ingen stängning eller omstart av filloggningen;
- felutfallet blir synligt för både användare och support.

Nackdelar:

- den senaste och ofta mest relevanta loggen kan saknas;
- ett partiellt paket kan misstolkas som komplett om manifest och UI-varning inte är entydiga;
- löser inte grundorsaken och ska därför vara en defensiv fallback, inte enda långsiktiga åtgärd.

Rekommendation: implementera alternativ A och kombinera det med alternativ C som defensiv fallback. Gå vidare med alternativ B endast om Windows-verifieringen visar att den aktiva dataloggen fortfarande inte kan läsas efter att `.lck` har exkluderats.

## Tillämpliga repository-instruktioner

Granskad: `AGENTS.md` i roten av `Loppiskassan`.

- **How to build & test in Codex (Linux/headless):** verifiering i Codex ska köra `make ci`; Swing-applikationen och paketering ska inte startas i den headless miljön.
- **JDK:** implementation och tester ska använda Java 21.
- **Decision rule (NO ASSUMPTIONS):** om Windows-reproduktionen inte avgör vilka filer som faktiskt är oläsbara ska detta förbli en öppen fråga; implementationen får inte anta att alla aktiva loggfiler är läsbara.
- **UI Rules / UI Architecture (Model/View/Controller):** eventuell ny UI-text hör hemma i befintlig Swing-vy, medan export-, fil- och loggerlogik ska ligga utanför UI-komponenterna.
- **Internationalization — Critical Rules:** alla nya användarsynliga fel och varningar ska hämtas med `LocalizationManager.tr(...)` och finnas i både svensk och engelsk språkfil.
- **Environment Detection:** den nödvändiga Windows-verifieringen ska köras separat på Windows; CI kan verifiera portabel logik men inte ersätta test av Windows fillåsning.
- **Dependencies:** lösningsalternativen kräver ingen ny extern dependency.

Inga frontend-, backend-, API-, protobuf-, databas- eller deploymentinstruktioner i de andra iLoppis-repona berörs av denna desktopändring.

## Föreslagen plan

1. **@rosemary** reproducerar felet på en supportad Windows- och JDK 21-miljö och dokumenterar exakt filnamn, undantagstyp och om både `.lck` och den aktiva dataloggen är oläsbara.
2. **@ripley** fastställer lösningsval och kontraktet för komplett respektive partiellt supportpaket enligt rekommendationen ovan.
3. **@rosemary** implementerar valt alternativ, strikt filurval, säker hantering av ofullständig ZIP-fil och eventuell loggerlivscykel utan nya dependencies.
4. **@rosemary** utökar `DataBundleExporterTest` och lägger till avgränsade tester för loggerlivscykeln om alternativ B eller C väljs.
5. **@ash** granskar att befintlig API-nyckelsanering bevaras och att manifestets felinformation inte läcker logginnehåll, känsliga sökvägar eller autentiseringsdata.
6. **@wednesday** granskar filmatchning, resursstängning, samtidighet, felvägar och att loggern inte dupliceras eller försvinner efter export.
7. **@norman** kör regressionstest på Windows medan kassan loggar aktivt samt verifierar ett skapat supportpaket med ett oberoende ZIP-verktyg.

## Teststrategi

### Automatiserade tester

- Skapa realistiska filer som `loppiskassan.log.0`, `loppiskassan.log.1` och `loppiskassan.log.0.lck`; verifiera att endast datafilerna hamnar i ZIP-arkivet och i manifestets `logFiles`.
- Verifiera att filer med närliggande men ogiltiga namn, exempelvis `.lck`, `.tmp` och `loppiskassan.log.backup`, inte tas med.
- Verifiera stabil sorteringsordning för rotationsfilerna.
- Verifiera att ZIP-filen kan läsas och att logginnehållet är oförändrat.
- Simulera `IOException` under en loggkopiering och verifiera att ingen ofullständig fil presenteras som en lyckad supportfil.
- Om alternativ B väljs: verifiera flush, close och återöppning även när ZIP-skapandet misslyckas, samt att exakt en aktiv file handler finns efteråt.
- Om alternativ C väljs: simulera en oläsbar datalogg och verifiera att ZIP-filen fortfarande är giltig, att manifestet exakt redovisar inkluderade och utelämnade loggar och att UI:t visar en lokaliserad varning.
- Kör hela den headless verifieringen med `make ci`.

### Manuell Windows-verifiering

1. Starta en paketerad Loppiskassan på Windows med JDK 21-runtime och generera loggrader före och under exporten.
2. Bekräfta att Java har en aktiv `.lck`-fil och dokumentera vilka loggdatafiler som kan respektive inte kan öppnas medan programmet körs.
3. Skapa supportfilen utan att stänga kassan.
4. Öppna ZIP-filen med Windows Explorer och ett oberoende ZIP-verktyg.
5. Bekräfta att manifest, eventdata, sanerad konfiguration och minst en relevant logg finns, att ingen `.lck`-fil finns och att loggning fortsätter efter exporten.
6. Upprepa export samtidigt som loggrotation sker eller provoceras med reducerad rotationsstorlek i testmiljö.

## Acceptanskriterier

- [ ] En supportfil kan skapas på Windows medan Loppiskassan och filloggningen är aktiva.
- [ ] ZIP-arkivet innehåller minst en relevant logg med poster från den aktuella körningen.
- [ ] Ingen fil med suffix `.lck`, `.tmp` eller annat kontrollfilsuffix tas med i ZIP eller manifest.
- [ ] ZIP-filen är komplett och kan öppnas med både Windows Explorer och ett oberoende ZIP-verktyg.
- [ ] Loppiskassan fortsätter skriva loggar efter en lyckad export och efter en framtvingad misslyckad export.
- [ ] Exporten skapar inte dubbla file handlers, tappar inte permanent filloggning och ändrar inte källoggarna.
- [ ] En misslyckad export visar ett begripligt lokaliserat fel och lämnar inte kvar en fil som kan misstas för ett komplett supportpaket.
- [ ] Om best-effort-fallback används framgår det entydigt i både manifest och UI vilka loggar som utelämnades, utan att känsliga sökvägar eller logginnehåll exponeras.
- [ ] Automatiserade regressionstester passerar via `make ci`.
- [ ] Den slutliga lösningen har verifierats manuellt på en supportad Windows-miljö med aktiv loggrotation.

## Risker och motåtgärder

- **Fel rotorsak antas:** reproduktion på Windows genomförs innan alternativ väljs; exakt blockerad fil och exception dokumenteras.
- **Loggrader tappas under export:** alternativ A föredras när det räcker. Alternativ B ska serialisera handlerlivscykeln och garantera återöppning i `finally`.
- **Korrupt eller halvfärdig ZIP:** skriv till en unik temporär fil bredvid destinationen, stäng och validera arkivet och flytta först därefter till slutnamnet.
- **Rotation sker mitt under kopiering:** testa framtvingad rotation; hantera varje källfil med tydlig felpolicy och låt inte manifestet lova filer som saknas i arkivet.
- **Känslig information i loggar:** ändra inte nuvarande logginnehåll i denna issue; befintliga loggningsregler gäller och eventuell separat sanering ska säkerhetsgranskas som eget scope.
- **Plattformsspecifik regression:** behåll portabla enhetstester men kräv dessutom ett verkligt Windows-test eftersom Linux/macOS inte reproducerar samma fillåsning.

## Öppna frågor

1. Är `.lck` den enda fil som utlöser felet i den paketerade Windows-versionen, eller är även den aktiva `loppiskassan.log.0` oläsbar?
2. Vilka Windows- och paketerade JDK-versioner stöds inför nästa loppis och ska ingå i verifieringsmatrisen?
3. Ska exporten misslyckas helt om en roterad historisk logg inte kan läsas, eller ska paketet skapas med en tydlig lista över utelämnade loggar i manifestet?

## Definition of done

- Rotorsaken är reproducerad och dokumenterad på Windows.
- Ett av de tre alternativen är valt med dokumenterad motivering.
- Acceptanskriterierna och relevanta automatiserade tester är uppfyllda.
- `make ci` passerar.
- Manuell Windows-verifiering med aktiv loggning och rotation är godkänd av @norman.
- Koden är granskad av @wednesday och signerad av @ripley.
