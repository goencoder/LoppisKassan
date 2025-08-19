package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;

import java.io.IOException;
import java.util.List;

import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;

public class FileUtils {
    /**
     * Save sold items to file.
     * Note! This method will create a backup, truncate and then save the items to the file (loppiskassan.csv) - it's not appending to the file.
     * @param items
     * @throws IOException
     */
    public static void saveSoldItems(List<SoldItem> items) throws IOException {
        FileHelper.createBackupFile();
        FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(items));
    }

    /**
     * Append sold items to file.
     * Note! This method will append the items to the file (loppiskassan.csv) without creating a backup.
     * @param items
     * @throws IOException
     */
    public static void appendSoldItems(List<SoldItem> items) throws IOException {
        FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(items));
    }


}

