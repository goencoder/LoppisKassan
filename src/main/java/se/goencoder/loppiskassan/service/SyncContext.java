package se.goencoder.loppiskassan.service;

import java.awt.Component;
import java.util.concurrent.Callable;

/**
 * Context object providing callbacks and UI components for synchronization operations.
 * Controllers use this to encapsulate mode-specific sync logic (local import vs online upload/download).
 */
public class SyncContext {
    private final Component parentComponent;
    private final Runnable localImportOperation;
    private final Callable<Void> onlineUploadDownloadOperation;
    private final String progressTitle;
    private final String progressMessage;
    
    public SyncContext(
        Component parentComponent,
        Runnable localImportOperation,
        Callable<Void> onlineUploadDownloadOperation,
        String progressTitle,
        String progressMessage
    ) {
        this.parentComponent = parentComponent;
        this.localImportOperation = localImportOperation;
        this.onlineUploadDownloadOperation = onlineUploadDownloadOperation;
        this.progressTitle = progressTitle;
        this.progressMessage = progressMessage;
    }
    
    public Component getParentComponent() {
        return parentComponent;
    }
    
    public Runnable getLocalImportOperation() {
        return localImportOperation;
    }
    
    public Callable<Void> getOnlineUploadDownloadOperation() {
        return onlineUploadDownloadOperation;
    }
    
    public String getProgressTitle() {
        return progressTitle;
    }
    
    public String getProgressMessage() {
        return progressMessage;
    }
}
