# Sold Items Persistence Strategy: LoppisKassan vs Android

## Executive Summary

| Aspect | LoppisKassan (Java) | Android (Kotlin) |
|--------|---------------------|------------------|
| **Strategy** | API-first, local fallback | Local-first, background sync |
| **Write order** | Try API → on error, save locally | Save locally → trigger background upload |
| **Sync engine** | `java.util.Timer` (in-process) | WorkManager (OS-managed) |
| **Sync interval** | 30 seconds | Immediate + 15 minutes periodic |
| **File format** | JSONL, `uploaded` flag | JSONL, row existence = pending |
| **Seller refresh** | ❌ None (static at login) | ✅ On invalid seller + on rejection |

---

## Pattern 1: LoppisKassan (API-First)

### Flow Diagram
```
User clicks "Pay"
       ↓
┌─────────────────────────────────┐
│ 1. TRY API UPLOAD               │ ← Blocks UI until response or timeout (5s)
└─────────────────────────────────┘
       ↓
   ┌───────┐
   │Success│────────────────────────────────┐
   └───┬───┘                                │
       │                                    ↓
       │              ┌─────────────────────────────────────────┐
       │              │ 2a. Mark uploaded=true                  │
       │              │ 2b. Append to pending_items.jsonl       │
       │              │ 2c. Notify UI (pending count → 0)       │
       │              └─────────────────────────────────────────┘
       │
   ┌───────┐
   │Network│
   │ Error │
   └───┬───┘
       ↓
┌─────────────────────────────────┐
│ 3a. Append to pending_items.jsonl (uploaded=false) │
│ 3b. Start BackgroundSyncManager                    │
│ 3c. Notify UI (pending count > 0)                  │
└─────────────────────────────────┘
       ↓
┌─────────────────────────────────┐
│ BackgroundSyncManager (30s loop)│
│ - Read pending items            │
│ - Batch upload                  │
│ - Mark uploaded=true on success │
│ - Notify UI                     │
└─────────────────────────────────┘
```

### Key Code

**IloppisCashierStrategy.java** (lines 60-120):
```java
public boolean persistItems(List<V1SoldItem> items, ...) throws Exception {
    try {
        // TRY API FIRST
        V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
            .getSoldItemsServiceApi()
            .soldItemsServiceCreateSoldItems(eventId, createSoldItems);
        
        // SUCCESS: mark uploaded, save locally for history
        for (V1SoldItem item : items) item.setUploaded(true);
        JsonlHelper.appendItems(pendingPath, items);
        BackgroundSyncManager.getInstance().notifyPendingCountChanged();
        return true;
        
    } catch (ApiException e) {
        if (ApiHelper.isLikelyNetworkError(e)) {
            // NETWORK ERROR: save locally, start background sync
            JsonlHelper.appendItems(pendingPath, items); // uploaded=false by default
            BackgroundSyncManager.getInstance().start(eventId);
            BackgroundSyncManager.getInstance().notifyPendingCountChanged();
            return true;
        }
        throw e;
    }
}
```

### File Format

**pending_items.jsonl** — each line is a JSON object:
```json
{"itemId":"01KH...","purchaseId":"01KH...","seller":42,"price":100,"paymentMethod":"Kontant","soldTime":"2026-02-11T08:57:03Z","uploaded":true}
{"itemId":"01KH...","purchaseId":"01KH...","seller":15,"price":250,"paymentMethod":"Swish","soldTime":"2026-02-11T08:57:05Z","uploaded":false}
```

- `uploaded=true`: Item is on server, kept for local history view
- `uploaded=false`: Item is pending, needs background sync

---

## Pattern 2: Android (Local-First)

### Flow Diagram
```
User clicks "Pay"
       ↓
┌─────────────────────────────────┐
│ 1. WRITE TO LOCAL FILE FIRST   │ ← Synchronous disk write, blocks until complete
│    (pending_items.jsonl)        │
└─────────────────────────────────┘
       ↓
┌─────────────────────────────────┐
│ 2. Clear UI immediately         │ ← Optimistic, user sees clean cart
│    (show receipt)               │
└─────────────────────────────────┘
       ↓
┌─────────────────────────────────┐
│ 3. Trigger WorkManager sync     │ ← Asynchronous, OS-managed
│    (SyncScheduler.enqueueImm.)  │
└─────────────────────────────────┘
       ↓
┌─────────────────────────────────────────────────────┐
│ SoldItemsSyncWorker (WorkManager)                   │
│ - Read pending items from file                      │
│ - Batch upload to API                               │
│ - On success: DELETE row from file                  │
│ - On DUPLICATE_RECEIPT: DELETE (silent success)    │
│ - On INVALID_SELLER: Set errorText, trigger recov.  │
│ - Emit itemsUpdated flow → UI updates badge         │
└─────────────────────────────────────────────────────┘
```

### Key Code

**CashierViewModel.kt** (checkout):
```kotlin
// STEP 4b: Write to JSONL file SYNCHRONOUSLY with mutex protection
// GUARANTEE: If this succeeds, data is on disk and survives crashes
try {
    Log.d(TAG, "Persisting ${pendingItems.size} pending items locally")
    PendingItemsStore.appendItems(pendingItems)  // BLOCKS until write completes
} catch (t: Throwable) {
    Log.e(TAG, "Failed to persist pending items", t)
}

// STEP 5: Trigger background upload (best-effort)
val syncEnqueued = tryTriggerSync(purchaseId = purchaseId)
```

**SoldItemsSyncWorker.kt** (background):
```kotlin
override suspend fun doWork(): Result {
    val pending = PendingItemsStore.readAll()
    if (pending.isEmpty()) return Result.success()
    
    val response = api.soldItemsServiceCreateSoldItems(eventId, body)
    
    // Success: delete from pending file
    for (accepted in response.acceptedItems) {
        PendingItemsStore.deleteItem(accepted.itemId)
    }
    
    // Handle rejections
    for (rejected in response.rejectedItems) {
        when (rejected.code) {
            "DUPLICATE_RECEIPT" -> PendingItemsStore.deleteItem(rejected.itemId)
            "INVALID_SELLER" -> {
                PendingItemsStore.setError(rejected.itemId, "Ogiltigt säljarnummer")
                PurchaseRecoveryManager.scheduleRecovery()
            }
        }
    }
    
    _itemsUpdated.emit(Unit) // Notify UI
    return Result.success()
}
```

### File Format

**pending_items.jsonl** — row existence = pending:
```json
{"itemId":"01KH...","purchaseId":"01KH...","sellerId":42,"price":100,"errorText":"","timestamp":"2026-02-11T08:57:03Z"}
```

- Row exists with empty `errorText`: Waiting for upload
- Row exists with `errorText`: Has error, needs recovery
- Row deleted: Successfully uploaded (no trace left)

---

## Seller/Vendor Refresh Comparison

### Android: Dynamic Refresh ✅

```kotlin
// VendorRepository.kt
object VendorRepository {
    private var cachedSellers: Set<Int>? = null
    
    fun getCached(): Set<Int>? = cachedSellers
    
    suspend fun refresh(): Set<Int> {
        // Paginated fetch of all approved sellers
        val sellers = mutableSetOf<Int>()
        var pageToken: String? = null
        do {
            val resp = api.vendorServiceListApprovedVendors(eventId, 100, pageToken)
            sellers.addAll(resp.vendors.map { it.sellerNumber })
            pageToken = resp.nextPageToken
        } while (pageToken != null)
        cachedSellers = sellers
        return sellers
    }
}

// CashierViewModel.kt - triggers refresh on:
// 1. Startup
// 2. Invalid seller entered
// 3. Rejected purchase detected
fun handleOk(input: String) {
    val validSellers = VendorRepository.getCached() ?: emptySet()
    if (!validSellers.contains(sellerNum)) {
        // Show error, but also refresh in background
        viewModelScope.launch { VendorRepository.refresh() }
    }
}
```

### LoppisKassan: Static at Login ❌

```java
// ILoppisConfigurationStore.java
public static String getApprovedSellers() { 
    return INSTANCE.config.approvedSellers; // Set once at login, never refreshed
}

// IloppisCashierStrategy.java
public boolean validateSeller(int sellerId) {
    String approvedSellersJson = ILoppisConfigurationStore.getApprovedSellers();
    // Uses stale data if market owner approves new vendors mid-event
    return new JSONObject(approvedSellersJson)
        .getJSONArray("approvedSellers")
        .toList()
        .contains(sellerId);
}
```

**Gap: No refresh mechanism in LoppisKassan.** If a vendor is approved during the event, the cashier must restart the app to see them.

---

## Pros and Cons Comparison

### API-First (LoppisKassan)

| Pros | Cons |
|------|------|
| ✅ Immediate feedback — user knows if upload succeeded | ❌ 5s timeout blocks UI if network is slow |
| ✅ Simpler code path — happy path is synchronous | ❌ Mobile-unfriendly — assumes stable network |
| ✅ Local file always represents history (uploaded=true/false) | ❌ Race condition risk if app crashes mid-upload |
| ✅ Fewer duplicate concerns — upload ack before local save | ❌ No automatic seller refresh |

### Local-First (Android)

| Pros | Cons |
|------|------|
| ✅ Instant UI response — never blocks on network | ❌ More complex: two async steps |
| ✅ Crash-safe — data on disk before any network call | ❌ Must handle DUPLICATE_RECEIPT when item already uploaded |
| ✅ Offline-first — works seamlessly without network | ❌ Harder to debug sync failures |
| ✅ OS-managed WorkManager — survives app death | ❌ Delayed feedback if upload fails (badge shows pending) |
| ✅ Automatic seller refresh on errors | ❌ File grows until sync catches up (memory overhead) |

---

## Recommendation

### For Alignment: Adopt Local-First in LoppisKassan

The Android local-first pattern is more robust for a POS system because:

1. **Crash safety**: Data is on disk before any network I/O
2. **Consistent UX**: Same behavior online/offline — user never waits for network
3. **Seller refresh**: Missing in LoppisKassan, should be added

### Migration Steps

1. **Invert the write order** in `IloppisCashierStrategy.persistItems()`:
   ```java
   // NEW: Write locally FIRST
   JsonlHelper.appendItems(pendingPath, items); // uploaded=false
   
   // Then trigger background upload
   BackgroundSyncManager.getInstance().start(eventId);
   ```

2. **Add seller refresh** on:
   - Validation failure
   - Background sync rejection with INVALID_SELLER
   - Optional: periodic refresh every N minutes

3. **Align sync intervals**: Consider 15-minute periodic like Android, vs current 30s

4. **Handle DUPLICATE_RECEIPT** in `BackgroundSyncManager` (already done in bug fix)

---

## Test Reset Procedure

To re-run `NetworkStabilityIT`, you can either:

**Option A: Drop MongoDB collection**
```bash
# Connect to MongoDB
docker exec -it iloppis-mongo mongosh
> use iloppis
> db.sold_items.deleteMany({event_id: "b93ab4ec-b9c4-4533-8260-7ca52eef120c"})
```

**Option B: Create fresh event**
```bash
make setup-test ENV=./load-test-setup.env
# Update network-test.env with new EVENT_ID and API_KEY
```

The test uses a temp directory for local files, so those are always fresh.
