# TODO Inventory - LoppisKassan

This document lists all TODOs found in the codebase, grouped by category.

**Goal:** Get to zero TODOs by implementing all items or removing obsolete ones.

**Status:** ✅ **7 of 10 completed** | 🟡 **0 in progress** | ⚪ **3 remaining**

---

## ✅ Completed TODOs (7)

### 1. ✅ API Request Timeout Configuration
**Status:** DONE  
**Commit:** Configure 5s timeout for sold items upload  
**Changes:** Added timeout configuration in ApiHelper constructor
```java
this.apiClient.setConnectTimeout(5000);  // 5 seconds
this.apiClient.setReadTimeout(5000);
this.apiClient.setWriteTimeout(5000);
```

### 2. ✅ Payment Button State Management  
**Status:** DONE (Already Implemented)  
**Finding:** The TODO comment was obsolete - functionality already exists in `CashierTabPanel.updateSummary()`
```java
enableCheckoutButtons(itemsCount > 0);  // Line 514
```
**Action:** Removed obsolete TODO comment

### 3. ✅ Upload Success/Error Message Logic
**Status:** DONE  
**Commit:** Better error handling for partial sync success/failure  
**Changes:**
- Split upload/download error handling in `HistoryTabController.handleImportAction()`
- Added specific success messages for each operation
- New localization keys: `info.upload_success`, `info.download_success`, `info.sync_complete`, `error.sync_failed`

### 4. ✅ Event Dates Display in Topbar  
**Status:** DONE  
**Commit:** Display event start/end dates in topbar badge  
**Changes:**
- Added `formatEventDateRange()` and `formatSingleDate()` methods
- Reads `startTime`/`endTime` from iLoppis event data (online mode)
- Shows formatted date range (e.g., "8 Feb - 10 Feb" or "8 Feb" if same day)
- Local events show no dates (only have createdAt, not event dates)

### 5. ✅ Event Name Optimization  
**Status:** DONE (Already Implemented)  
**Finding:** The TODO comment was obsolete - code already reads from event-data cache
**File:** AppShellTopbar.java line 68  
**Action:** Removed obsolete TODO comment, updated to reflect current implementation

### 6. ✅ Rejected Items Handling
**Status:** DONE  
**Commit:** Save rejected items to log + show popup to cashier  
**Changes:**
- Created `RejectedItemsHelper` utility class
- Saves rejected items to `rejected_purchases.jsonl` with full details
- Shows warning popup to cashier with rejection details
- Uses existing localization keys: `error.upload_rejected`, `error.upload_rejected.header`, `error.upload_rejected.entry`

### 7. ✅ Background Sync Trigger
**Status:** DONE  
**Commit:** Auto-retry pending uploads with background timer  
**Changes:**
- Created `BackgroundSyncManager` singleton service
- Checks every 30 seconds for pending items  
- Automatically retries upload on network failure
- Starts when items saved locally due to network error
- Handles rejected items + clears pending file on success

---

## ⚪ Remaining TODOs (3)

### Phase 4 Refactoring (Low Priority)

#### 8. LocalEventService Full Implementation
**File:** [LocalEventService.java:14](src/main/java/se/goencoder/loppiskassan/service/LocalEventService.java#L14)
```java
/**
 * Local-only event service implementation.
 * All operations work with local files, no network calls.
 * 
 * TODO: Full implementation in Phase 4
 */
```

**Context:** LocalEventService interface implementation incomplete. Currently operations are scattered in controllers.

**Priority:** Low (future refactoring)  
**Effort:** High (move logic from controllers to service layer)

---

### 4.2 OnlineEventService Full Implementation
**File:** [OnlineEventService.java:20,28](src/main/java/se/goencoder/loppiskassan/service/OnlineEventService.java#L20)
```java
/**
 * Online event service implementation.
 * Operations interact with the iLoppis API and maintain local cache/fallback.
 * 
 * TODO: Full implementation in Phase 4
 */
...
public void saveSoldItems(...) {
    throw new UnsupportedOperationException("TODO: Phase 4 implementation");
}
```

**Context:** OnlineEventService interface implementation incomplete. saveSoldItems() throws UnsupportedOperationException.

**Priority:** Low (future refactoring)  
**Effort:** High (move logic from controllers to service layer)

---

## Summary by Priority

### 🔴 High Priority (User-Facing Issues)
1. **API Request Timeout** - Configure 5s timeout for better responsiveness
2. **Upload Success Message Bug** - Fix incorrect error message on successful upload
3. **Upload Error Notification** - Show popup when sync fails due to invalid data

### 🟡 Medium Priority (Feature Completeness)
4. **Payment Button State** - Disable buttons until items added
5. **Background Sync Trigger** - Auto-retry failed uploads
6. **Rejected Items Handling** - Save and show rejected items to user

### 🟢 Low Priority (Polish & Future Work)
7. **Event Name in Topbar** - Read from event-data cache
8. **Event Dates in Topbar** - Display actual dates
9. **LocalEventService Phase 4** - Refactor to service layer
10. **OnlineEventService Phase 4** - Refactor to service layer

---

## Implementation Order Recommendation

1. **API Request Timeout** (Quick win, improves UX)
2. **Payment Button State** (Prevents user errors)
3. **Upload Success/Error Messages** (Fixes confusing messages)
4. **Rejected Items Handling** (Completes error handling)
5. **Background Sync Trigger** (Improves offline reliability)
6. **Event Display Improvements** (Polish)
7. **Phase 4 Refactoring** (When time permits)

---

**Last Updated:** 2026-02-10  
**Total TODOs:** 10
