# LoppisKassan

## Förutsättningar
För att bygga och köra detta projekt krävs följande 
- [Git](https://git-scm.com/download/mac)
- [Java](https://www.oracle.com/java/technologies/downloads/)
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
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
```

3. **Bygg projektet**:
  Navigera till projektets rotkatalog och kör följande kommando:

```bash
mvn clean package
```
    
4. **Kör projektet**:
    Efter att ha byggt projektet, kör följande kommando för att starta applikationen:
    
```bash
$JAVA_HOME/bin/java -jar target/LoppisKassan-v2.0.0-jar-with-dependencies.jar
```

## Installationsmanual

För att installera LoppisKassan, följ vår [installationsmanual](docs/installation.md).


## Användarmanual
**Notera!** Här är länken till den senaste versionen av manualen:

Manual: [LoppisKassan v2.0](docs/manual_v2.md)

**Äldre versioner:**
- Manual: [LoppisKassan](https://seteddy.wordpress.com/2016/09/20/loppiskassan/)
- Tillägg: [Loppiskassan v1.2](https://seteddy.wordpress.com/2018/01/07/loppiskassan-v1-2/)
