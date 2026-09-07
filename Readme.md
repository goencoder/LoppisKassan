# LoppisKassan


## Installationsmanual

För att installera LoppisKassan, följ vår [installationsmanual](../../../goencoder-dev-team/teams/iloppis/docs/guides/development.md).


## Användarmanual
**Notera!** Här är länken till den senaste versionen av manualen:

Manual: [LoppisKassan v3.0](../../../goencoder-dev-team/teams/iloppis/docs/guides/cashier-operations.md)

**Äldre versioner:**
- Manual: [LoppisKassan](https://seteddy.wordpress.com/2016/09/20/loppiskassan/)
- Tillägg: [Loppiskassan v1.2](https://seteddy.wordpress.com/2018/01/07/loppiskassan-v1-2/)


## Bygga LoppisKassan från källkod
För att bygga och köra detta projekt krävs följande 
- [Git](https://git-scm.com/download/mac)
- [Java 21](https://www.oracle.com/java/technologies/downloads/)
- [Maven (mvn)](https://maven.apache.org/download.cgi)

## Klona projektet
För att börja arbeta med projektet, klonar du det först till din lokala dator. Öppna en terminal och kör:

```bash
git clone https://github.com/goencoder/LoppisKassan.git
cd LoppisKassan
```

## Bygginstruktioner

1. **Installera Java**:  
   Ladda ner och installera Java från Oracles officiella hemsida.

2. **Ställ in JAVA_HOME**:
   Öppna en terminal och kör följande kommando (uppdatera sökvägen till din lokala javainstallation):

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

3. **Bygg projektet**:
  Navigera till projektets rotkatalog och kör följande kommando:

```bash
mvn clean package
```
    
4. **Kör projektet**:
    Efter att ha byggt projektet, kör följande kommando för att starta applikationen:
    
```bash
$JAVA_HOME/bin/java -jar target/LoppisKassan-v3.0.0-jar-with-dependencies.jar
```
## Licens

Detta projekt är licensierat under MIT-licensen – se [LICENSE](../../../goencoder-dev-team/teams/iloppis/repos/Loppiskassan/LICENSE) för mer information.

## Teamets dokumentation

Issues, buggar, guider och gemensam kunskap för alla iLoppis-repos förvaltas i
[goencoder-dev-team / iLoppis](../../../goencoder-dev-team/teams/iloppis/docs/README.md). Skapa och uppdatera dokument där.
Gamla dokumentplatser innehåller flytthänvisningar med nya teamnummer.
Länken gäller den lokala arbetsytan; i andra checkouts finns ingången på
`goencoder-dev-team/teams/iloppis/docs/README.md`.
