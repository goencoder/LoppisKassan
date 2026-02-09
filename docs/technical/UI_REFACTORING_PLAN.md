# LoppisKassan UI — Design Pattern Audit & Refactoring Plan

> **Date:** 2026-02-09
> **References:**
> - [PragmaticCoding — Unravelling MVC, MVP and MVVM (MVCI)](https://www.pragmaticcoding.ca/javafx/Frameworks/)
> - [Refactoring.Guru — Design Patterns in Java](https://refactoring.guru/design-patterns/java)

---

## 1. Executive Summary

The LoppisKassan Swing codebase has a recognisable **controller–view split** and some good practices (controller interfaces, localization listeners, a dedicated `EDT` helper). However, a close reading reveals that the architecture is a **partial MVP** rather than a clean implementation of any single pattern. Business logic, UI wiring, data formatting, and state management are interleaved across layers, creating tight coupling and making the code "tedious" to work with.

This document catalogues the specific anti-patterns found, maps them to established design-pattern solutions, and proposes a concrete, incremental refactoring plan inspired by the **MVCI (Model-View-Controller-Interactor)** pattern described by PragmaticCoding, adapted for Swing.

---

## 1.1 Target Package Structure (Hierarchical)

The current codebase is flat. This refactor standardizes a hierarchical package structure while keeping UI classes under `se.goencoder.loppiskassan.ui`.

```
se.goencoder.loppiskassan
├── app
│   ├── AppController.java
│   ├── AppEventBus.java
│   └── AppEvents.java
├── controller
│   ├── DiscoveryTabController.java
│   ├── CashierTabController.java
│   └── HistoryTabController.java
├── interactor
│   ├── discovery
│   │   ├── DiscoveryInteractor.java
│   │   ├── LocalEventService.java
│   │   └── OnlineEventService.java
│   ├── cashier
│   │   └── CashierInteractor.java
│   └── history
│       └── HistoryInteractor.java
├── model
│   ├── discovery
│   │   └── DiscoveryState.java
│   ├── cashier
│   │   └── CashierState.java
│   └── history
│       └── HistoryState.java
├── service
│   ├── dialog
│   │   └── DialogService.java
│   ├── format
│   │   └── FormatService.java
│   └── time
│       └── Clock.java
├── storage
│   ├── JsonlHelper.java
│   ├── LocalEventRepository.java
│   └── PendingItemsStore.java
└── ui
    ├── DiscoveryTabPanel.java
    ├── CashierTabPanel.java
    ├── HistoryTabPanel.java
    └── dialogs
        └── CreateLocalEventDialog.java
```

**Rules:**
- Views remain in `ui` and are layout-only.
- Controllers orchestrate and delegate to Interactors.
- Interactors own business logic, I/O, and formatting.
- Models (`*State`) hold presentation-ready data only.

---

## 2. Current Architecture at a Glance

```
Main
 └─ UserInterface (JFrame)
     ├─ DiscoveryTabPanel (JPanel, implements DiscoveryPanelInterface)
     │    └─ DiscoveryTabController (singleton, implements DiscoveryControllerInterface)
     ├─ CashierTabPanel (JPanel, implements CashierPanelInterface)
     │    └─ CashierTabController (singleton, implements CashierControllerInterface)
     └─ HistoryTabPanel (JPanel, implements HistoryPanelInterface)
          └─ HistoryTabController (singleton, implements HistoryControllerInterface)
```

### What Works Today

| Aspect | Current State | Verdict |
|--------|--------------|---------|
| Controller interfaces | `CashierControllerInterface`, `HistoryControllerInterface`, `DiscoveryControllerInterface` exist | ✅ Good abstraction boundary |
| View interfaces | `CashierPanelInterface`, `HistoryPanelInterface`, `DiscoveryPanelInterface` exist | ✅ Allows mocking in tests |
| Localization | `LocalizationAware` + listener pattern | ✅ Clean and extensible |
| EDT safety helper | `EDT.run()` | ✅ Neat utility |
| Theme | Centralized `Theme.install()` via FlatLaf | ✅ Good practice |
| Table model | Custom `SoldItemsTableModel` extends `AbstractTableModel` | ✅ Correct Swing idiom |

---

## 3. Anti-Patterns & Violations Found

### 3.1 — No Explicit State / Presentation Model (violates SRP & MVCI)

**Problem:** There is no dedicated "State" or "Model" object for any screen. UI state is scattered across:
- Swing component fields in the panels (`sellerField.getText()`, `paidFilterDropdown.getSelectedIndex()`)
- Primitive fields in panels (`itemsCount`, `sumValue`, `totalSum`)
- Primitive fields in controllers (`degradedMode`, `allHistoryItems`, `eventList`)
- `ConfigurationStore` static singletons acting as both persisted config and runtime state

**Impact:** Controllers must "scrape" the view to obtain state (e.g., `view.getAndClearSellerPrices()`, `view.getPaidFilter()`), and views must format data themselves (e.g., `Money.formatAmount` in `CashierTabPanel.updateSummary()`). This couples both layers to each other and violates the Single Responsibility Principle.

**MVCI remedy:** Extract a **Model** (state POJO) per screen. The Model holds all presentation-ready data. The View reads from it; the Interactor (business logic) writes to it. Neither scrapes the other.

**Refactoring.Guru patterns:** [State](https://refactoring.guru/design-patterns/state), [Memento](https://refactoring.guru/design-patterns/memento) (for undo/snapshots of state).

---

### 3.2 — View Creates and Wires Its Own Controller (violates IoC / DIP)

**Problem:** Some panels instantiate their own controller internally:

```java
// HistoryTabPanel.java
private final HistoryControllerInterface controller = HistoryTabController.getInstance();

// DiscoveryTabPanel.java
controller = DiscoveryTabController.getInstance();
```

While `CashierTabPanel` receives its controller via constructor injection, the other two hard-wire a singleton. This makes testing difficult, prevents alternative controller implementations, and breaks **Dependency Inversion**: the View depends on a concrete class via `getInstance()`.

**Remedy:** Use **constructor injection** consistently for all three panels, just like `CashierTabPanel` already does.

---

### 3.3 — Controller Takes Swing Dependencies (JButton, JTextField)

**Problem:** `CashierControllerInterface` requires `javax.swing.JButton` and `javax.swing.JTextField` as parameters:

```java
void setupCheckoutCashButtonAction(JButton checkoutCashButton);
void setupCheckoutSwishButtonAction(JButton checkoutSwishButton);
void setupCancelCheckoutButtonAction(JButton cancelCheckoutButton);
void setupPricesTextFieldAction(JTextField pricesTextField);
```

The controller is reaching **into** the View to wire action listeners directly on Swing components. This is a classic MVP anti-pattern: the Presenter and View become "one object divided into two classes," tightly coupled.

**Remedy:** The View should expose **actions/events** (callbacks, `Consumer<Event>`, or an event bus), and the Controller should respond to abstract events. The controller should never touch `JButton`, `JTextField`, or any `javax.swing.*` type.

```java
// Instead of:
void setupCheckoutCashButtonAction(JButton button);

// Prefer:
// View calls controller.onCheckout(PaymentMethod.CASH) in its own ActionListener
```

---

### 3.4 — View Contains Business Logic (violates View-as-pure-layout)

**Problem:** Views perform validation, data parsing, and business computation that belongs in the controller or a dedicated interactor:

| File | Business Logic in View |
|------|----------------------|
| `CashierTabPanel.getAndClearSellerPrices()` | Parses seller ID, validates seller approval via controller, parses price list, shows error popups |
| `CashierTabPanel.updateSummary()` | Iterates `SoldItemsTableModel`, sums prices, formats currency |
| `HistoryTabPanel.updateHistoryTable()` | Formats dates, translates payment methods, builds table rows |
| `HistoryTabPanel.reloadTexts()` | Logic to check `ConfigurationStore.LOCAL_EVENT_BOOL` and conditionally choose button text |
| `DiscoveryTabPanel.getTokenButton` listener | Validates eventId, checks empty cashier code, shows error |

**Remedy:** Move all business logic into the Controller/Interactor. The View should only:
1. Bind/display data from the Model.
2. Forward raw user gestures (clicks, key events) to the Controller.

---

### 3.5 — God-Class Singletons for Controllers

**Problem:** All three controllers are **eager singletons** (`private static final … instance = new …()`). They carry:
- Business logic (validation, filtering, payout)
- API calls (network I/O)
- File I/O (JSONL read/write)
- Threading (`ProgressDialog.runTask`, background `Thread`)
- State management (`allHistoryItems`, `degradedMode`, `items`)

`HistoryTabController` is 612 lines; `DiscoveryTabController` is 549 lines. These are textbook "God classes."

**MVCI remedy:** Split each controller into:
- **Controller** — orchestrates flow, handles threading, delegates to Interactor
- **Interactor** — business logic, API calls, file I/O, data transformation
- **Model** — presentation state

**Refactoring.Guru patterns:**
- [Facade](https://refactoring.guru/design-patterns/facade) — Controller as a thinner facade over deeper services
- [Strategy](https://refactoring.guru/design-patterns/strategy) — swap online/local interactors without changing the controller
- [Command](https://refactoring.guru/design-patterns/command) — encapsulate button actions as command objects

---

### 3.6 — `ConfigurationStore` Is a Global Mutable State Bag

**Problem:** `ConfigurationStore` is used as both:
1. **Persistent settings** (language, last event ID)
2. **Runtime state** (current event JSON, revenue split JSON, approved sellers JSON, `LOCAL_EVENT_BOOL`)

Every layer reads and writes these statics freely, creating invisible coupling. Any class can call `ConfigurationStore.EVENT_ID_STR.get()` and change behavior globally. This is effectively global mutable state — the opposite of the "Model" concept in any MVC-family pattern.

**Remedy:**
- Keep `ConfigurationStore` for truly persistent preferences.
- Move runtime state into per-screen Model objects that are passed explicitly.

---

### 3.7 — String-Keyed Button Dispatch (fragile, no type safety)

**Problem:** `HistoryTabController.buttonAction(String actionCommand)` dispatches on string constants:

```java
case BUTTON_ERASE -> clearData();
case BUTTON_IMPORT -> handleImportAction();
```

This is fragile and offers no compile-time safety if a constant is renamed or a new action is added without updating the switch.

**Remedy:** Use the **Command pattern** — each button action becomes a `Runnable` or `Command` object registered in a `Map<Action, Runnable>`, or use an `enum` with an `execute()` method.

---

### 3.8 — View Panels Expose `Component getComponent()`

**Problem:** `CashierPanelInterface`, `HistoryPanelInterface` extend `UiComponent` which has `Component getComponent()`. The controllers call `view.getComponent()` to parent dialogs (`ProgressDialog.runTask(view.getComponent(), …)`).

This leaks Swing implementation details through the interface boundary. The interactor/controller should not need a reference to the raw `Component`.

**Remedy:** Have the Controller supply the parent `Component` (it created the View), or use a `DialogService` that knows the window hierarchy.

---

### 3.9 — Missing Observer / Publish-Subscribe for Cross-Cutting Concerns

**Problem:** When the register is opened in `DiscoveryTabController`, it directly writes to `ConfigurationStore` and assumes the other tabs will read it later. There is no event or notification for "register opened" / "event changed" that other components can subscribe to.

**Remedy:** Introduce a lightweight **Observer / Event Bus** for application-wide events:

```java
public enum AppEvent { REGISTER_OPENED, EVENT_CHANGED, LANGUAGE_CHANGED }
```

This replaces the implicit coupling through `ConfigurationStore` reads.

---

### 3.10 — Online vs Local Mode: Missed Strategy Pattern

**Problem:** Throughout the controllers, online vs local mode is handled with `if (isLocal)` branches interleaving two different flows in the same methods:

```java
// CashierTabController
if (!isLocal && !degradedMode) { … } else { … }

// HistoryTabController.handleImportAction()
if (ConfigurationStore.LOCAL_EVENT_BOOL.getBooleanValueOrDefault(false)) {
    importData();
} else { … }

// DiscoveryTabController.openRegister()
if (isLocal) { configureLocalMode(…); } else { configureOnlineMode(…); }
```

This makes every method harder to read and test.

**Remedy:** Apply the **[Strategy pattern](https://refactoring.guru/design-patterns/strategy)**: define an `EventService` interface with `OnlineEventService` and `LocalEventService` implementations. The controller delegates to the active strategy without branching.

---

### 3.11 — Duplicated Formatting & Rendering Logic

**Problem:**
- Currency formatting (`Money.formatAmount`) is called in multiple places across views.
- Payment method translation (`V1PaymentMethod.Kontant ? "payment.cash" : "payment.swish"`) is duplicated in `HistoryTabPanel`.
- Date formatting is done inline.

**Remedy:** Centralize in the **Interactor** or a `FormatService`. The Model should hold pre-formatted strings, so the View never formats.

---

### 3.12 — Popup Calls From Both Views and Controllers

**Problem:** Error/warning/confirmation dialogs (`Popup.*`) are called from:
- Views: `CashierTabPanel.getAndClearSellerPrices()`, `DiscoveryTabPanel` button listener
- Controllers: `CashierTabController.finishCheckoutFlow()`, `HistoryTabController.clearData()`

This scatters UI interaction responsibility and makes unit-testing controllers difficult (they trigger modal dialogs).

---

## 4. Incremental Refactoring Phases (Stable Checkpoints)

Each phase must compile, run, and pass tests before moving on. No phase should leave the UI in a broken or half-connected state.

### Phase 0 — Safety Baseline (Checkpoint A)
**Goal:** Lock in current behavior with minimal tests.

**Tasks:**
- Add/verify smoke tests for basic flows (Discovery, Cashier, History).
- Ensure `mvn test` is green and reproducible.

**Checkpoint A = Done when:**
- `mvn test` passes.
- App launches and the three tabs render.

---

### Phase 1 — Introduce Models (Checkpoint B)
**Goal:** Add per-screen state objects (`*State`) without changing UI behavior.

**Tasks:**
- Create `DiscoveryState`, `CashierState`, `HistoryState`.
- Controllers populate state objects.
- Views read from state objects (no direct business logic).

**Checkpoint B = Done when:**
- App looks/behaves the same.
- Tests still pass.

---

### Phase 2 — Extract Interactors (Checkpoint C)
**Goal:** Move business logic out of controllers into interactors.

**Tasks:**
- Create `DiscoveryInteractor`, `CashierInteractor`, `HistoryInteractor`.
- Move I/O, validation, filtering, formatting logic into interactors.
- Controllers become thin orchestration layers.

**Checkpoint C = Done when:**
- No controller exceeds ~200 LOC.
- Tests pass and app flows are intact.

---

### Phase 3 — UI Event Boundary (Checkpoint D)
**Goal:** Controllers no longer depend on Swing components.

**Tasks:**
- Replace `setup...Action(JButton)` with view-owned listeners.
- Controllers expose intent methods (e.g., `onCheckoutCash()`).
- View passes raw input data, not Swing objects.

**Checkpoint D = Done when:**
- Controllers import zero `javax.swing.*` classes.
- Views compile without changes in behavior.

---

### Phase 4 — Local vs Online Strategy (Checkpoint E)
**Goal:** Remove branching logic for local/online flows.

**Tasks:**
- Implement `LocalEventService` and `OnlineEventService`.
- Controllers delegate to the active strategy.

**Checkpoint E = Done when:**
- No controller contains `if (isLocal)` branches for core flows.
- Both modes remain functional.

---

### Phase 5 — App Event Bus (Checkpoint F)
**Goal:** Remove implicit coupling via `ConfigurationStore`.

**Tasks:**
- Add `AppEventBus` and `AppEvents`.
- Publish/subscribe for event changes, language changes, register open/close.

**Checkpoint F = Done when:**
- Tabs update via events, not direct `ConfigurationStore` reads.
- Manual smoke test passes for mode switching.

**Remedy:** The Controller should be the sole owner of dialog decisions. Inject a `DialogService` interface that can be mocked in tests:

```java
public interface DialogService {
    void showError(String title, String message);
    boolean confirm(String title, String message);
    void showWarning(String key, Object... args);
}
```

---

## 4. Proposed Target Architecture: MVCI for Swing

Adapting the **Model-View-Controller-Interactor** pattern from PragmaticCoding's JavaFX guide to Swing:

```
┌──────────────────────────────────────────────────────┐
│                   Controller                         │
│  - Instantiates Model, View, Interactor              │
│  - Wires callbacks from View to Interactor           │
│  - Manages threading (SwingWorker / EDT)             │
│  - Owns DialogService calls                          │
│  - Exposes getView() → JPanel                        │
├──────────────────────────────────────────────────────┤
│          ▲                           │               │
│          │ callbacks                 │ calls         │
│          │                           ▼               │
│   ┌──────────────┐          ┌──────────────┐         │
│   │     View     │◄────────►│    Model     │         │
│   │  (JPanel)    │ reads    │   (POJO)     │         │
│   │  Pure layout │          │  UI state    │         │
│   └──────────────┘          └──────────────┘         │
│                                      ▲               │
│                                      │ mutates       │
│                              ┌───────┴──────┐        │
│                              │  Interactor  │        │
│                              │  Biz logic   │        │
│                              │  API calls   │        │
│                              │  File I/O    │        │
│                              └──────────────┘        │
└──────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Swing Adaptation |
|-----------|---------------|------------------|
| **Model** | Immutable/mutable POJO holding all presentation state: field values, button enabled states, table data, error messages | Plain Java class with getters/setters + `PropertyChangeSupport` for observability |
| **View** | Layout, styling, binding to Model properties, forwarding user gestures via callbacks | `JPanel` subclass. Registers `PropertyChangeListener` on Model to update UI. Calls `Runnable`/`Consumer` callbacks — never calls Controller or Interactor directly |
| **Controller** | Creates M, V, I; wires callbacks; manages threading; owns `DialogService` | Lightweight class. `SwingWorker` or `EDT.run()` for thread management |
| **Interactor** | All business logic, API calls, file I/O, data transformation. Reads/writes Model. Never touches Swing classes | Pure Java class, fully unit-testable |

### Key Differences From Current Code

1. **Model exists** — no scraping state from components
2. **View never sees Controller or Interactor** — receives `Consumer<Action>` callbacks only
3. **Controller never sees JButton/JTextField** — works with abstract actions
4. **Interactor is separated** — business logic extracted from controller singletons
5. **Strategy replaces `if (isLocal)`** — online/local services injected into Interactor

---

## 5. Refactoring Plan — Incremental Steps

### Phase 1: Extract State Models (Low Risk)

**Goal:** Create a Model POJO for each screen without changing behavior.

1. **Create `CashierModel`**
   - Fields: `List<V1SoldItem> items`, `int totalSum`, `int itemCount`, `int paidAmount`, `int change`, `String formattedSum`, `String formattedChange`, `boolean checkoutEnabled`
   - Add `PropertyChangeSupport` for observable fields

2. **Create `HistoryModel`**
   - Fields: `List<V1SoldItem> allItems`, `List<V1SoldItem> filteredItems`, `int itemCount`, `int totalSum`, `String paidFilter`, `String sellerFilter`, `String paymentMethodFilter`, `Set<String> distinctSellers`, `boolean payoutEnabled`, `boolean archiveEnabled`

3. **Create `DiscoveryModel`**
   - Fields: `List<V1Event> events`, `V1Event selectedEvent`, `V1RevenueSplit revenueSplit`, `boolean registerOpened`, `String cashierCode`, `boolean localMode`, `String dateFrom`

4. **Wire Models into existing code** — Controllers write to Models, Views read from Models. Keep existing interfaces for now.

**Estimated effort:** 1–2 days per screen.

---

### Phase 2: Purify the Views (Medium Risk)

**Goal:** Remove business logic from View panels.

1. **Move `getAndClearSellerPrices()` logic** to `CashierController`:
   - View exposes `String getSellerText()` and `String getPricesText()` only
   - Controller parses, validates, shows errors

2. **Move `updateSummary()` formatting** to Controller/Interactor:
   - Controller computes totals and formatted strings → writes to Model
   - View observes Model and updates labels

3. **Move table row construction** from `HistoryTabPanel.updateHistoryTable()` to Interactor:
   - Interactor returns formatted `Object[][]` rows
   - View just calls `model.setRowCount(0)` + `model.addRow()`

4. **Remove all `Popup.*` calls from Views** — replace with callbacks to Controller.

5. **Remove `ConfigurationStore` reads from Views** — inject needed config via Model.

**Estimated effort:** 2–3 days per screen.

---

### Phase 3: Extract Interactors (Medium Risk)

**Goal:** Separate business logic from Controllers into Interactors.

1. **`CashierInteractor`** — owns `items` list, `addItem()`, `checkout()`, `saveItemsToWeb()`, `pushLocalUnsyncedRecords()`, `prepareItemsForCheckout()`, seller approval check

2. **`HistoryInteractor`** — owns `allHistoryItems`, `applyFilters()`, `loadHistory()`, `payout()`, `importData()`, `archiveFilteredItems()`, `copyToClipboard()`, `downloadSoldItems()`

3. **`DiscoveryInteractor`** — owns `eventList`, `localEventMap`, `loadAllEvents()`, `openRegister()`, `saveLocalEventEdits()`, `uploadLocalEventRequested()`

4. **Controllers become thin** — just wire callbacks, manage threading, call interactor methods.

**Estimated effort:** 2–3 days per screen.

---

### Phase 4: Apply Strategy for Online/Local Mode

**Goal:** Eliminate `if (isLocal)` branches.

1. Define `EventService` interface with methods common to both modes:
   ```java
   public interface EventService {
       List<V1Event> discoverEvents(String dateFrom);
       void openRegister(String eventId, String cashierCode);
       void saveSoldItems(List<V1SoldItem> items) throws IOException;
       List<V1SoldItem> loadHistory(String eventId) throws IOException;
       boolean isSellerApproved(int sellerId);
   }
   ```

2. Implement `OnlineEventService` and `LocalEventService`.

3. Inject the correct implementation at startup based on `AppModeManager.isLocalMode()`.

**Estimated effort:** 2 days.

---

### Phase 5: Decouple Controller from Swing Types

**Goal:** Controller interfaces have zero `javax.swing` imports.

1. Replace `setupCheckoutCashButtonAction(JButton)` with the View calling `controller.onCheckout(PaymentMethod)`.

2. Replace `view.getComponent()` in `ProgressDialog.runTask()` with a `DialogService` that the Controller owns.

3. Remove all `javax.swing` and `java.awt` imports from controller interfaces and implementations.

**Estimated effort:** 1 day.

---

### Phase 6: Introduce Command / Action Pattern for Buttons

**Goal:** Replace string-dispatched `buttonAction(String)` in `HistoryTabController`.

```java
public enum HistoryAction {
    ERASE, IMPORT, PAY_OUT, COPY_TO_CLIPBOARD, ARCHIVE
}

// Controller
public void onAction(HistoryAction action) { … }
```

**Estimated effort:** Half day.

---

### Phase 7: Introduce Application Event Bus

**Goal:** Replace implicit cross-tab communication via `ConfigurationStore`.

1. Create a simple `EventBus`:
   ```java
   public final class AppEventBus {
       public enum Event { REGISTER_OPENED, EVENT_CHANGED }
       public static void subscribe(Event e, Runnable handler) { … }
       public static void publish(Event e) { … }
   }
   ```

2. When `DiscoveryController` opens the register, it publishes `REGISTER_OPENED`.
3. `CashierController` and `HistoryController` subscribe and react accordingly.

**Estimated effort:** 1 day.

---

## 6. Relevant Design Patterns Summary

| Pattern | Where to Apply | Benefit |
|---------|---------------|---------|
| **MVCI** (Model-View-Controller-Interactor) | Overall architecture for each screen | Clean separation of layout, state, orchestration, and business logic |
| **Strategy** | Online vs Local mode services | Eliminates conditional branching; each mode is a swappable implementation |
| **Observer** | Model → View updates; Application Event Bus | Decouples state changes from UI updates |
| **Command** | Button actions in History tab (and others) | Type-safe action dispatch; supports undo, logging |
| **Facade** | Controller as simplified interface to Interactor + Model | Reduces surface area exposed to View |
| **Singleton → DI** | Controller lifecycle | Enables testability; controllers receive dependencies, not `getInstance()` |
| **Builder** | Complex panel construction (Discovery detail forms) | Readable, step-by-step UI construction |
| **Template Method** | Common tab lifecycle (selected, reloadTexts, addNotify/removeNotify) | Reduces boilerplate across tab panels |

---

## 7. Migration Safety

| Concern | Mitigation |
|---------|-----------|
| Cashier keystroke flow must stay intact | Test with manual scenario after each phase. The flow (seller field → price field → Enter → reset) is defined by the View, which Phase 2 does not change structurally |
| Localization listener lifecycle | `addNotify`/`removeNotify` pattern is preserved; just ensure Models do not hold translated text (they hold keys; View translates) |
| `ProgressDialog.runTask` threading | Keep as-is in Phase 1–3; extract to `DialogService` in Phase 5 |
| ConfigurationStore persistence | Phase 1 only *copies* store values into Models; actual persist logic stays unchanged |
| Existing tests (controller tests) | Phase 3 will break controller tests — rewrite them to test Interactors directly (simpler, no UI dependency) |

---

## 8. Priority Recommendation

| Priority | Phase | Reason |
|----------|-------|--------|
| 🔴 High | Phase 1 (State Models) | Foundation for all other improvements; lowest risk |
| 🔴 High | Phase 2 (Purify Views) | Biggest pain-point reduction; removes logic from UI code |
| 🟡 Medium | Phase 3 (Extract Interactors) | Enables real unit testing of business logic |
| 🟡 Medium | Phase 4 (Strategy) | Reduces cognitive load for mode-dependent code |
| 🟢 Low | Phase 5 (Decouple Swing types) | Clean architecture ideal, less urgent for shipping |
| 🟢 Low | Phase 6 (Command pattern) | Nice-to-have, small effort |
| 🟢 Low | Phase 7 (Event Bus) | Valuable as the app grows, not critical today |

---

## 9. Example: CashierTab Before & After

### Before (Current)

```
CashierTabPanel (550 lines)
 ├─ Layout code
 ├─ Business logic (getAndClearSellerPrices, updateSummary)
 ├─ Data formatting (Money.formatAmount)
 ├─ Validation (seller approval, price parsing)
 └─ Popup calls (error dialogs)

CashierTabController (389 lines)
 ├─ Button wiring (takes JButton params)
 ├─ Business logic (checkout, addItem, cancelCheckout)
 ├─ API calls (saveItemsToWeb)
 ├─ File I/O (appendSoldItems, pushLocalUnsyncedRecords)
 ├─ Threading (ProgressDialog.runTask, new Thread)
 └─ State management (items list, degradedMode)
```

### After (MVCI)

```
CashierModel (40 lines) — POJO
 ├─ items: List<V1SoldItem>
 ├─ totalSum, itemCount, paidAmount, change
 ├─ formattedSum, formattedChange
 ├─ checkoutEnabled: boolean
 └─ PropertyChangeSupport

CashierView (200 lines) — Pure layout
 ├─ Reads from CashierModel via PropertyChangeListener
 ├─ Forwards clicks → Consumer<CashierAction>
 └─ Zero business logic, zero Popup calls

CashierController (80 lines) — Orchestrator
 ├─ Creates Model, View, Interactor
 ├─ Wires View actions → Interactor methods
 ├─ Manages threading
 └─ Owns DialogService for confirmations/errors

CashierInteractor (180 lines) — Business logic
 ├─ addItem(), checkout(), cancelCheckout()
 ├─ saveItemsToWeb() via EventService (Strategy)
 ├─ pushLocalUnsyncedRecords()
 ├─ isSellerApproved()
 └─ Reads/writes CashierModel directly
```

**Total lines:** ~500 (vs ~940 today), spread across 4 focused files instead of 2 tangled ones.

---

## 10. Conclusion

The current codebase has a solid foundation — the controller/view interface split, localization system, and theme configuration are genuine strengths. The main issue is that the architecture is **partially implemented**: it has the *shape* of MVP but lacks the *discipline* of a complete pattern. Business logic leaks into views, controllers take Swing dependencies, state is scattered globally, and the online/local split is handled with conditionals rather than polymorphism.

Applying MVCI with Interactors and Strategy-based mode-switching will make the code:
- **Easier to read** (each class has one job)
- **Easier to test** (Interactors and Models need no Swing)
- **Easier to extend** (new modes or screens follow the same template)
- **Less tedious** (no more hunting through 600-line controllers to find where a value is computed)

The refactoring can be done incrementally, phase by phase, without rewriting everything at once.
