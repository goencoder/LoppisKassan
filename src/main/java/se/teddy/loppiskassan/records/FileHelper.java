package se.teddy.loppiskassan.records;

import se.teddy.loppiskassan.Popup;

import java.io.*;
import java.nio.file.*;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardCopyOption.*;

/**
 * Created by gengdahl on 2016-08-18.
 */
public class FileHelper {
    private static Logger logger = Logger.getLogger(FileHelper.class.getName());
    private static String baseDir = System.getProperty("user.dir");
    private static String recordsFileName = "loppiskassan.csv";
    private static String[] backupRecordsFileNames = new String[]{recordsFileName + ".backup.0",
            recordsFileName + ".backup.1",
            recordsFileName + ".backup.2",
            recordsFileName + ".backup.3",
            recordsFileName + ".backup.4",
            recordsFileName + ".backup.5",
            recordsFileName + ".backup.6",
            recordsFileName + ".backup.7",
            recordsFileName + ".backup.8",
            recordsFileName + ".backup.9",};
    private static int backupRecordsIndex = Math.abs(new Random(System.currentTimeMillis()).nextInt() % 10);
    public static void createDirectories() throws IOException {
        try {
            Files.createDirectories(getLogFilePath().getParent());
            Files.createDirectories(getRecordFilePath().getParent());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Misslyckades skapa katalog(er)", e);
            throw e;
        }
    }

    private static int nextBackupIndex(){
        backupRecordsIndex = ++backupRecordsIndex % 10;
        return backupRecordsIndex;

    }
    public static Path getNextRecordsBackupPath(){
        return Paths.get(baseDir, "data", backupRecordsFileNames[nextBackupIndex()]);
    }
    public static Path getRecordFilePath(){
        return Paths.get(baseDir, "data", recordsFileName);

    }
    public static Path getLogFilePath(){
        return Paths.get(baseDir, "logs", "loppiskassan.log");

    }
    public static void assertRecordFileRights(){
        if (Files.notExists(getRecordFilePath())){
            //File does not exist yet, and we do not
            //want to create it just yet.
            //So, let's skip this check until file
            //is created.
            return;
        }

        boolean canRead = Files.isReadable(getRecordFilePath());
        boolean canWrite = Files.isWritable(getRecordFilePath());
        if(!canWrite || !canRead){
            String title = "Kan inte skriva till fil";
            String popUpContent = "Kan inte använda datafil.\n" +
                    "\nKontrollera läs- och skrivrättigheter samt\n" +
                    "att inget annat program har filen öppen";
            String logContent = "Problem med filrättigheter för fil: "
                    + getRecordFilePath() + ". Skrivbar: " + canWrite +
                    " Läsbar: " + canRead;
            logger.warning(logContent);
            Popup.WARNING.showAndWait (title,popUpContent);
        }

    }

    public static void deleteFile() throws IOException {
        try{
            if (Files.exists(getRecordFilePath())){
                Files.delete(getRecordFilePath());
                logger.log(Level.INFO, "Tog bort fil " + getRecordFilePath());
            }
        }catch(IOException ex){
            String info = "Misslyckades ta bort datafil";
            logger.log(Level.SEVERE, info, ex);
            Popup.ERROR.showAndWait(info, ex);
            throw ex;
        }

    }
    public static void saveToFile(String csv) throws IOException {
        try{
            boolean writeHeaders = Files.notExists(getRecordFilePath());
            if (writeHeaders){
                csv = FormatHelper.CVS_HEADERS + FormatHelper.LINE_ENDING + csv;

            }
            OutputStream outputStream = new BufferedOutputStream(
                    Files.newOutputStream(getRecordFilePath(), StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND));
            byte[] byteArray = csv.getBytes("UTF-8");
            outputStream.write(byteArray, 0, byteArray.length);
            outputStream.flush();
            logger.log(Level.INFO, "sparade data: " + csv);
        }catch(IOException ex){
            String title = "Misslyckades spara senast utförda aktivitet till fil";
            logger.log(Level.SEVERE, title + " Data: " + csv, ex);
            Popup.ERROR.showAndWait(title, ex);
            throw ex;
        }



    }
    public static void createBackupFile() throws IOException {
        if (Files.exists(getRecordFilePath())){
            try{
                Path backupFile = getNextRecordsBackupPath();
                Files.move(getRecordFilePath(), backupFile, ATOMIC_MOVE, REPLACE_EXISTING);
                logger.log(Level.INFO, "Skapade backupfil " + backupFile);
            }catch(IOException ex){
                String title = "Misslyckades skapa backupfil";
                logger.log(Level.SEVERE, title, ex);
                Popup.ERROR.showAndWait(title, ex);
                throw ex;
            }
        }
    }
    public static String readFromFile() throws IOException {
        if (!Files.exists(getRecordFilePath())){
            logger.log(Level.INFO,  "Filen " + getRecordFilePath() + " finns inte");
            return "";
        }
        return readFromFile(getRecordFilePath());

    }
    public static String readFromFile(Path path) throws IOException {
        try {
            InputStream inputStream = Files.newInputStream(path);
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(inputStream, "UTF-8"));
            String line = null;
            StringBuffer stringBuffer = new StringBuffer();
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line).append(FormatHelper.LINE_ENDING);
            }
            return stringBuffer.toString();
        }catch (IOException ex){
            String title = "Misslyckades läsa från datafil";
            logger.log(Level.SEVERE, title, ex);
            Popup.ERROR.showAndWait(title, ex);
            throw ex;
        }
    }
}
