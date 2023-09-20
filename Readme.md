# LoppisKassan

## Förutsättningar
För att bygga och köra detta projekt krävs Oracle Java 1.8, eftersom det är denna version som har JavaFX bundlad.

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

**Noteringar**:
Det är viktigt att använda Oracle Java 1.8 eftersom nyare versioner av Java inte längre har JavaFX bundlad. Om du stöter på problem relaterade till JavaFX när du kör applikationen, dubbelkolla att du verkligen använder Oracle Java 1.8.

## Användarmanual
Manual: [LoppisKassan](https://seteddy.wordpress.com/2016/09/20/loppiskassan/)

Tillägg: [Loppiskassan v1.2](https://seteddy.wordpress.com/2018/01/07/loppiskassan-v1-2/)

