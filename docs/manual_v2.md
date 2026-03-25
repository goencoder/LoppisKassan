# Användarmanual för LoppisKassan

## Vad är LoppisKassan?
LoppisKassan är kassaprogrammet för inlämningsloppisar. Programmet används för att registrera köp, följa försäljning och hantera underlag för utbetalning eller felsökning.

LoppisKassan kan användas i två olika lägen:

- **iLoppis-evenemang**: rekommenderat läge när evenemanget finns i iLoppis och kassorna ska vara synkade.
- **Lokalt evenemang**: används när man behöver arbeta utan iLoppis.

De två lägena fungerar olika och har därför delats upp i separata delar i den här manualen.

## Innan du börjar
När programmet startar väljer du om du vill arbeta i **iLoppis** eller **Lokalt läge**.

- I båda lägena väljer du evenemang via sidan **Evenemang**.
- Språk kan ändras uppe till höger.
- **Inställningar** visar vilka filer och mappar programmet använder lokalt på datorn.

## Gemensamt kassaflöde
Själva kassaarbetet fungerar i stort sett likadant i båda lägena.

1. Skriv säljarens nummer i fältet för säljnummer
2. Tryck på TAB för att komma till nästa fält.
3. Skriv ett eller flera priser i prisfältet.
4. Tryck `Enter` för att lägga till varorna i köpet.
5. Upprepa tills alla varor i köpet är registrerade.
6. Slutför köpet med **Kontant** eller **Swish**.

### Tips i kassan
- Du kan skriva flera priser från samma säljare i samma rad, till exempel `50 100 75`.
- Återköp medges ej, så kontrollera att allt är rätt inslaget
- Om du skrev fel innan köpet är klart kan du markera en rad och trycka `Delete`.
- Om kunden betalar med ett annat belopp än det föreslagna kan du skriva in det i fältet **Betalt** för att få rätt växel.
- **Avbryt köp** rensar hela det pågående köpet.

---

## iLoppis-evenemang

### När ska detta läge användas?
Det här är normalläget och det som ska användas när evenemanget finns i iLoppis.

Fördelar:

- flera kassor synkas mot samma evenemang
- säljare valideras mot iLoppis
- live-statistik visas direkt
- supportfil kan skapas för felsökning

### Öppna en kassa för ett iLoppis-evenemang
1. Starta programmet i **iLoppis-läge**.
2. Gå till **Evenemang**.
3. Välj rätt evenemang i listan.
4. Öppna kassan och ange kassakoden för den aktuella kassan.
5. Välj Kassa i vänstermenyn.


### Säljarnummer i iLoppis-läge
I iLoppis-läge kontrolleras säljarnumret mot godkända säljare för evenemanget.

- Om säljaren inte är godkänd går varan ej läggas till i köpet.
- Säljlistan uppdateras automatiskt under loppisen om en ny säljare skulle läggas till.

### Sidan Kassa
På sidan **Kassa** registrerar du alla köp och slutför dem med **Kontant** eller **Swish**.

Efter ett genomfört köp:

- köpet sparas lokalt först
- uppladdning till iLoppis sker i bakgrunden
- kassan rensas för nästa kund

### Sidan Senaste köp
Sidan **Senaste köp** är en läsvy för den aktuella kassan.

Den visar:

- de **10 senaste köpen**
- en rubrikrad per köp, till exempel `Köp kl 13:14`
- alla varor i samma köp med samma bakgrundsfärg
- en **fet totalrad** längst ned i varje köpblock

Den här sidan är bara till för att titta på de senaste registreringarna. Inget kan ändras där.

### Sidan Live
Sidan **Live** visar en ögonblicksbild för evenemanget, till exempel:

- antal köp
- antal sålda varor
- total omsättning
- aktiva kassor

Den används som överblick, inte för redigering.

### Sidan Support
Sidan **Support** används bara för felsökning i iLoppis-läge.

Här kan du skapa en supportfil om du misstänker att köp har registrerats i kassan men inte kommit fram till iLoppis.

Supportfilen:

- är till för **iLoppis support**
- är **inte** avsedd för import i en annan kassa
- ska skickas till `support@iloppis.se` med en beskrivning av vad som gått fel.

### Status längst ned
I iLoppis-läge kan statusraden visa:

- väntande poster som ännu inte synkats
- avvisade poster

Om något inte gått att ladda upp kan du använda statusraden och tillhörande dialoger för att se vad som väntar eller har avvisats.

---

## Lokala evenemang

### När ska detta läge användas?
Lokalt läge används när du inte ska köra mot iLoppis, till exempel:

- om du vill testa eller öva lokalt
- om du medvetet vill köra en helt lokal kassa

### Skapa ett lokalt evenemang
1. Starta programmet i **Lokalt läge**.
2. Gå till **Evenemang**.
3. Skapa ett nytt lokalt evenemang.
4. Fyll i namn och övriga uppgifter för evenemanget.
5. Öppna kassan.

Lokala evenemang kräver ingen kassakod.

### Sidan Kassa
Kassaflödet är detsamma som i iLoppis-läge, men all data lagras lokalt för det valda evenemanget.

### Sidan Historik
Sidan **Historik** används i lokalt läge för att arbeta med redan registrerade poster.

Du kan filtrera på:

- utbetalt
- säljnummer
- betalningsmedel

Typiska användningar:

- hitta allt som tillhör en viss säljare
- markera poster som utbetalda
- kopiera säljsammanställning till urklipp

### Export och import mellan lokala kassor
Sidan **Export och import** används för att slå samman flera lokala kassor.

Använd de översta knapparna:

- **Exportera kassadata**
- **Exportera till Excel**
- **Importera kassadata**

Typiskt arbetssätt:

1. Exportera kassadata från en lokal kassa.
2. Flytta filen till en annan dator.
3. Importera kassadata i huvudkassan.

Detta gäller bara lokala evenemang.

### Sidan Arkiv
Sidan **Arkiv** visar arkivfiler som skapats från lokal historik.

Arkivet används för att hålla ordning när poster redan är utbetalda och du vill skilja dem från det som återstår.

---

## Skillnaden mellan lägena i korthet

| Funktion | iLoppis-evenemang | Lokalt evenemang |
|---|---|---|
| Kräver kassakod | Ja | Nej |
| Validerar säljare mot iLoppis | Ja | Nej |
| Synkar mellan flera kassor | Ja | Nej, manuell export/import |
| Live-översikt | Ja | Nej |
| Senaste köp-vy | Ja | Nej |
| Supportfil | Ja | Nej |
| Historik med utbetalning i appen | Nej | Ja |
| Export/import mellan kassor | Nej | Ja |
| Arkiv | Nej | Ja |

## Rekommendation
Använd **iLoppis-evenemang** när loppisen administreras via iLoppis, annars **Lokalt evenemang**.
