# Issue 002: Sprint 3 Implementation Plan - Bulk Upload UI & Logic

**Status:** Starting Sprint 3  
**Proto-changes:** ✅ Complete  
**Target:** LoppisKassan bulk-upload dialog + local upload logic

---

## Completed: Proto Changes (2026-02-09)

Backend (iloppis/proto):
- ✅ Added `BulkUploadResult` message (accepted_items, failed_items, duplicate_items)
- ✅ Enhanced `CreateSoldItemsRequest/Response` documentation for bulk-upload
- ✅ Clarified `DUPLICATE_RECEIPT` error code for idempotent re-upload
- ✅ Reserved future `BulkUploadSoldItems` endpoint
- ✅ `buf generate` successfully regenerated Go stubs

---

## Remaining: LoppisKassan Implementation (Sprint 3)

### Part 1: BulkUploadDialog UI & State Management

**File:** `src/main/java/se/goencoder/loppiskassan/ui/dialogs/BulkUploadDialog.java`

```java
public class BulkUploadDialog extends JDialog {
    private final LocalEvent localEvent;
    private JComboBox<V1Event> backendEventCombo;
    private JTextField codeField;
    private JLabel previewLabel;

    public BulkUploadDialog(Frame owner, LocalEvent localEvent) {
        super(owner, LocalizationManager.tr("bulk_upload.title"), true);
        this.localEvent = localEvent;
        initComponents();
        loadPreview();
    }

    private void loadPreview() {
        List<V1SoldItem> items = PendingItemsStore.readAll(localEvent.getEventId());
        long purchaseCount = items.stream()
            .map(V1SoldItem::getPurchaseId)
            .distinct().count();

        int totalPrice = items.stream()
            .mapToInt(V1SoldItem::getPrice)
            .sum();

        previewLabel.setText(String.format(
            LocalizationManager.tr("bulk_upload.preview"),
            items.size(), purchaseCount, totalPrice
        ));
    }

    public void performUpload() {
        // Implementation in Part 3
    }
}
```

**Localizations needed:**
- `bulk_upload.title` → "Ladda upp till iLoppis"
- `bulk_upload.backend_event` → "Backend-event"
- `bulk_upload.code` → "Kassakod (XXX-XXX)"
- `bulk_upload.preview` → "{0} items från {1} köp, {2} SEK"
- `bulk_upload.summary.title` → "Upload-sammanfattning"
- `bulk_upload.summary` → "✅ {0} items\n⚠️ {1} dubbletter\n❌ {2} misslyckade"

---

### Part 2: BulkUploadResult Model

**File:** `src/main/java/se/goencoder/loppiskassan/model/BulkUploadResult.java`

```java
public class BulkUploadResult {
    public final List<V1SoldItem> acceptedItems;
    public final List<RejectedSoldItem> failedItems;
    public final List<RejectedSoldItem> duplicateItems;
    public final List<String> errors;

    public BulkUploadResult() {
        this.acceptedItems = new ArrayList<>();
        this.failedItems = new ArrayList<>();
        this.duplicateItems = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public boolean isFullSuccess() {
        return failedItems.isEmpty() && duplicateItems.isEmpty();
    }

    public boolean isPartialSuccess() {
        return !acceptedItems.isEmpty() && (!failedItems.isEmpty() || !duplicateItems.isEmpty());
    }

    public boolean isComplete() {
        return !acceptedItems.isEmpty() || !failedItems.isEmpty() || !duplicateItems.isEmpty();
    }
}
```

---

### Part 3: Upload Logic Controller

**File:** `src/main/java/se/goencoder/loppiskassan/controller/BulkUploadController.java`

Flow:
1. Load backend events from API
2. Exchange code for API key
3. Read local items from JSONL
4. Group by purchaseId
5. Upload sequentially (100ms delay between groups)
6. Collect results (accepted/duplicates/failed)
7. Update local metadata with upload status

```java
public class BulkUploadController {
    public static BulkUploadResult uploadLocalEventData(
        LocalEvent localEvent,
        V1Event backendEvent,
        String code
    ) throws Exception {
        BulkUploadResult result = new BulkUploadResult();

        // 1. Exchange code for API key
        String apiKey = ApiHelper.INSTANCE
            .getApiKeysServiceApi()
            .apiKeysServiceGetApiKeyByAlias(backendEvent.getId(), code)
            .getApiKey();

        // 2. Read local items
        List<V1SoldItem> items = JsonlHelper.readItems(
            LocalEventPaths.getPendingItemsPath(localEvent.getEventId())
        );

        // 3. Group by purchaseId
        Map<String, List<V1SoldItem>> grouped = items.stream()
            .collect(Collectors.groupingBy(V1SoldItem::getPurchaseId));

        // 4. Upload each group
        for (var entry : grouped.entrySet()) {
            try {
                Thread.sleep(100); // Rate limiting
                
                CreateSoldItemsResponse response = ApiHelper.INSTANCE
                    .getSoldItemsServiceApi()
                    .createSoldItems(backendEvent.getId(), entry.getValue(), apiKey);

                result.acceptedItems.addAll(response.getAcceptedItems());
                
                // Separate duplicates from other errors
                for (var rejected : response.getRejectedItems()) {
                    if (rejected.getErrorCode() == SoldItemErrorCode.DUPLICATE_RECEIPT) {
                        result.duplicateItems.add(rejected);
                    } else {
                        result.failedItems.add(rejected);
                    }
                }
            } catch (IOException e) {
                result.errors.add(e.getMessage());
                result.failedItems.addAll(entry.getValue());
            }
        }

        // 5. Update local metadata
        if (!result.acceptedItems.isEmpty()) {
            updateLocalMetadata(localEvent, backendEvent);
        }

        return result;
    }

    private static void updateLocalMetadata(LocalEvent localEvent, V1Event backendEvent) {
        LocalEvent updated = new LocalEvent(
            localEvent.getEventId(),
            localEvent.getEventType(),
            localEvent.getName(),
            localEvent.getDescription(),
            localEvent.getAddressStreet(),
            localEvent.getAddressCity(),
            localEvent.getCreatedAt(),
            localEvent.getRevenueSplit(),
            true, // uploadedToBackend
            OffsetDateTime.now(), // uploadedAt
            backendEvent.getId() // backendEventId
        );
        LocalEventRepository.save(updated);
    }
}
```

---

### Part 4: Integration with Discovery Tab

**File:** `src/main/java/se/goencoder/loppiskassan/ui/DiscoveryTabPanel.java`

Changes:
1. Add right-click context menu on local events: **"Ladda upp till iLoppis"**
2. Show upload status icon (☁️ = uploaded, ⚠️ = partial)
3. Disable upload for events without sales
4. Show progress dialog during upload
5. Show result summary after completion

```java
// In DiscoveryTabPanel
private void setupLocalEventContextMenu() {
    JPopupMenu contextMenu = new JPopupMenu();
    
    JMenuItem uploadMenuItem = new JMenuItem(
        LocalizationManager.tr("local_event.upload")
    );
    uploadMenuItem.addActionListener(e -> {
        int selectedRow = localEventsTable.getSelectedRow();
        if (selectedRow >= 0) {
            LocalEvent event = localEventsList.get(selectedRow);
            openBulkUploadDialog(event);
        }
    });
    
    contextMenu.add(uploadMenuItem);
    localEventsTable.setComponentPopupMenu(contextMenu);
}

private void openBulkUploadDialog(LocalEvent localEvent) {
    BulkUploadDialog dialog = new BulkUploadDialog(
        (Frame) SwingUtilities.getWindowAncestor(this),
        localEvent
    );
    dialog.setVisible(true);
    
    BulkUploadResult result = dialog.getResult();
    if (result != null && result.isComplete()) {
        showUploadSummary(result);
        refreshLocalEventsList();
    }
}
```

---

### Part 5: Error Handling

**Scenarios:**
- Invalid code → User sees: "Kassakoden är ogiltig. Kontrollera och försök igen."
- No network → "Kunde inte nå iLoppis. Kontrollera internet."
- Permission denied → "Denna kassakod har inte rättigheter."
- Timeout → Retry logic (max 3 times, exponential backoff)
- Partial success → Show summary with counts

---

### Part 6: Test Cases

**Unit Tests:**
- `BulkUploadResultTest` — Test full/partial/failed scenarios
- `BulkUploadControllerTest` — Test grouping, sequence, dedup

**Integration Tests (Sprint 4):**
- T-I01: Bulk-upload → backend (30 items acceptance)
- T-I02: Idempotent re-upload (duplicates ignored)
- T-I03: Multi-kassör parallel upload
- T-I04: Partial failure + retry
- T-I05: Redovisning parity (local vs backend)
- T-I06: End-to-end lifecycle

---

## Implementation Checklist

- [ ] Part 1: BulkUploadDialog UI + localizations
- [ ] Part 2: BulkUploadResult model
- [ ] Part 3: BulkUploadController logic
- [ ] Part 4: DiscoveryTabPanel integration (context menu)
- [ ] Part 5: Error handling + dialogs
- [ ] Part 6: Unit tests
- [ ] Compilation + CI pass
- [ ] Manual testing: single local event upload
- [ ] Manual testing: partial failure scenario
- [ ] Code review + merge

---

**Next Step:** Begin with Part 1 (BulkUploadDialog UI)
