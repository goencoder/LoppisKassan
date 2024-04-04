# LoppisKassan

## Förutsättningar
För att bygga och köra detta projekt krävs Oracle Java 1.8, eftersom det är denna version som har JavaFX bundlad.

### Nödvändig mjukvara:
- [Git](https://git-scm.com/download/mac)
- [Oracle Java 1.8](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html)
- [Maven (mvn)](https://maven.apache.org/download.cgi)

## Klona projektet
För att börja arbeta med projektet, klonar du det först till din lokala dator. Öppna en terminal och kör:

```bash
git clone https://github.com/goencoder/LoppisKassan.git
cd LoppisKassan
```

## Bygginstruktioner

1. **Installera Oracle Java 1.8**:  
   Ladda ner och installera Oracle Java 1.8 (JDK) från Oracles officiella hemsida.

2. **Ställ in JAVA_HOME**:
   Öppna en terminal och kör följande kommando:

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
$JAVA_HOME/bin/java -jar target/LoppisKassan-1.0-SNAPSHOT-jar-with-dependencies.jar
```


## Användarmanual
**Notera!** Nedan länkar är till en äldre version av programmet, men funktionaliteten är densamma.
Manual: [LoppisKassan](https://seteddy.wordpress.com/2016/09/20/loppiskassan/)

Tillägg: [Loppiskassan v1.2](https://seteddy.wordpress.com/2018/01/07/loppiskassan-v1-2/)

