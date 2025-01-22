package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;

import java.io.IOException;
import java.util.List;

import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;

public class FileUtils {
    public static void saveSoldItems(List<SoldItem> items) throws IOException {
        FileHelper.createBackupFile();
        FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(items));
    }


}

