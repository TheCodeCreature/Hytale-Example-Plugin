# Review: Load-Update Separation — Blueprint Bench Custom UI

> **Date:** 2026-04-29  
> **Scope:** Staged changes for Load-Update Separation pattern  
> **Files reviewed:** `BlueprintSelectionPage.java`, `BlueprintBookPage.ui`, `SetFilterButton.ui`, `CostCell.ui`, `PlaceholderRow.ui`, `ItemGridTestPage.java`, `design-load-update-separation.md`

## Executive Summary

The Load-Update Separation is **cleanly implemented**. All `cmd.append()` / `cmd.clear()` / `cmd.appendInline()` calls are confined to `build()`. Every `handleDataEvent()` path uses only `cmd.set()` and `sendUpdate(cmd, null, false)`. The `sendUpdate(cmd, null, false)` pattern is confirmed safe — Hytale's own SDK pages (`EntitySpawnPage`, `SelectOverrideRespawnPointPage`) use it.

**8 findings** identified. No critical issues. One high-severity correctness concern (missing `sendUpdate` on same-tab click), one high-severity correctness concern (dead code path), and several medium/low items.

---

## Findings

### 1. Missing `sendUpdate` when selected tab unchanged

| | |
|---|---|
| **Category** | Correctness |
| **Severity** | High |
| **Location** | `BlueprintSelectionPage.java` — `handleDataEvent()`, lines 220–236 |

**Finding:** When `data.selectedTab != null` but equals the current `activeTab`, no `sendUpdate()` is called and no other `else if` branch is entered. If the Hytale UI framework expects a response for every event, this will stall the client.

```java
if (data.selectedTab != null) {
    String tab = data.selectedTab;
    if (!tab.equals(this.activeTab)) {
        // ... update + sendUpdate
    }
    // ← No sendUpdate when tab unchanged. No other branch entered.
}
```

**Recommendation:** Add an unconditional `sendUpdate` after the inner `if` block, or move the `sendUpdate` outside the guard:

```java
if (data.selectedTab != null) {
    String tab = data.selectedTab;
    if (!tab.equals(this.activeTab)) {
        this.activeTab = tab;
        // ... state updates ...
        updateBenchTabs(cmd);
        updateSetFilters(cmd);
        updateRecipeGrid(cmd);
        updateDetailPanel(cmd);
    }
    sendUpdate(cmd, null, false);  // always respond
}
```

---

### 2. Dead code: `data.recipeId` handler path

| | |
|---|---|
| **Category** | Correctness |
| **Severity** | High |
| **Location** | `BlueprintSelectionPage.java` — `handleDataEvent()`, lines 273–277 |

**Finding:** No event binding in `build()` sends a `RecipeId` field. All recipe interactions are handled via `RecipeHover` and `RecipeSelect` actions using `SlotIndex`. The `data.recipeId` path is dead code carried over from a previous implementation.

```java
} else if (data.recipeId != null) {
    this.selectedRecipeId = data.recipeId;
    updateRecipeGrid(cmd);    // unnecessary — grid doesn't reflect selection state
    updateDetailPanel(cmd);
    sendUpdate(cmd, null, false);
}
```

Additionally, this path calls `updateRecipeGrid(cmd)` even though `updateRecipeGrid` does not use `selectedRecipeId` — the grid has no visual selection indicator.

**Recommendation:** Remove the `data.recipeId` branch entirely if no external system sends this field. If it must be kept for forward compatibility, remove the `updateRecipeGrid(cmd)` call.

---

### 3. `RecipeHover` / `RecipeSelect` send empty updates on invalid `slotIndex`

| | |
|---|---|
| **Category** | Performance |
| **Severity** | Medium |
| **Location** | `BlueprintSelectionPage.java` — `handleDataEvent()`, lines 324–338 |

**Finding:** Both handlers call `sendUpdate(cmd, null, false)` unconditionally, even when `slotIndex` is null or out of bounds. This sends an empty command packet to the client — a wasted network round-trip on every invalid hover event.

```java
} else if ("RecipeHover".equals(data.action)) {
    if (data.slotIndex != null && data.slotIndex >= 0 && data.slotIndex < displayedRecipes.size()) {
        // ... update detail panel
    }
    sendUpdate(cmd, null, false);  // sends even when nothing changed
}
```

**Recommendation:** Move `sendUpdate` inside the `if` guard, or return early when slotIndex is invalid. Apply the same fix to `RecipeSelect`.

---

### 4. `PlaceholderDrop` / `PlaceholderClear` send empty updates on validation failure

| | |
|---|---|
| **Category** | Performance |
| **Severity** | Low |
| **Location** | `BlueprintSelectionPage.java` — `handleDataEvent()`, lines 298–322 |

**Finding:** Same pattern as Finding 3. If slot validation fails (bad hotbar index or empty itemStackId), `sendUpdate(cmd, null, false)` sends an empty packet.

**Recommendation:** Guard `sendUpdate` behind the validation check, or return early on invalid input.

---

### 5. `RecipeHover` and `RecipeSelect` handlers are functionally identical

| | |
|---|---|
| **Category** | Maintainability |
| **Severity** | Low |
| **Location** | `BlueprintSelectionPage.java` — `handleDataEvent()`, lines 324–338 |

**Finding:** Both handlers set `selectedRecipeId` from `displayedRecipes.get(slotIndex)` and call `updateDetailPanel`. They are exact duplicates.

```java
} else if ("RecipeHover".equals(data.action)) {
    if (data.slotIndex != null && ...) {
        this.selectedRecipeId = entry.recipeId();
        updateDetailPanel(cmd);
    }
    sendUpdate(cmd, null, false);
} else if ("RecipeSelect".equals(data.action)) {
    if (data.slotIndex != null && ...) {          // identical body
        this.selectedRecipeId = entry.recipeId();
        updateDetailPanel(cmd);
    }
    sendUpdate(cmd, null, false);
}
```

**Recommendation:** Acceptable to keep separate if you plan to differentiate them later (e.g., hover shows preview, click confirms). If not, merge into a single condition:

```java
} else if ("RecipeHover".equals(data.action) || "RecipeSelect".equals(data.action)) {
```

---

### 6. Linear scan in `updatePlaceholderList` to resolve `outputItemId`

| | |
|---|---|
| **Category** | Performance |
| **Severity** | Low |
| **Location** | `BlueprintSelectionPage.java` — `updatePlaceholderList()`, lines 498–522 |

**Finding:** For each armed placeholder row, a linear scan of `allRecipes` finds the matching `outputItemId` by `blockTypeId`. With 9 hotbar slots × N recipes, this is O(9N) per update. The same linear scan exists in `armPlaceholder()`.

```java
for (RecipeEntry entry : allRecipes) {
    if (blockTypeId.equals(entry.blockTypeId())) {
        outputItemId = entry.outputItemId();
        break;
    }
}
```

**Recommendation:** Pre-build a `Map<String, String>` (blockTypeId → outputItemId) during `loadRecipes()`. Not urgent — N is small and the loop breaks early — but would simplify both call sites.

---

### 7. SetFilter index-based resolution silently drops unrecognized payloads

| | |
|---|---|
| **Category** | Robustness |
| **Severity** | Low |
| **Location** | `BlueprintSelectionPage.java` — `handleDataEvent()`, lines 238–260 |

**Finding:** If a `SetFilter:` action doesn't match `"All"` and doesn't start with `"idx:"`, the code falls through to `applyFilter()` + `sendUpdate()` without modifying any state. This wastes a filter recalculation for payloads that can't exist under normal operation, but also silently swallows unexpected data.

```java
if (ALL_FILTER.equals(filterPayload)) {
    activeSetFilters.clear();
} else if (filterPayload.startsWith("idx:")) {
    // ... resolve index
}
// No else — falls through to applyFilter + sendUpdate
this.selectedRecipeId = null;
applyFilter();
```

**Recommendation:** Add an `else { return; }` or log a warning to aid debugging if an unexpected payload arrives.

---

### 8. Design doc does not match implementation approach

| | |
|---|---|
| **Category** | Maintainability |
| **Severity** | Info |
| **Location** | `docs/design-load-update-separation.md` — Sections 6.2–6.4 vs actual `.ui` / `.java` |

**Finding:** The design doc (Section 6) describes fully pre-allocating all dynamic nodes with unique IDs directly in `BlueprintBookPage.ui` (e.g., `#FilterAll`, `#Filter0`–`#Filter19`, `#Row0`–`#Row8`, `#Cost0`–`#Cost7`). The actual implementation takes a different (and better) approach: keeping containers empty in the `.ui` and appending reusable component templates (`SetFilterButton.ui`, `CostCell.ui`, `PlaceholderRow.ui`) during `build()`, addressed via indexed selectors (`#SetFilters[0]`, `#CostGrid[N]`, `#PlaceholderList[N]`).

This means:
- Section 6 (981 lines of pre-allocated .ui markup) is misleading — that code was never implemented
- Section 7 references selectors like `#FilterAll`, `#Filter0` that don't exist in the actual code
- Anyone reading the design doc will expect a different DOM structure than what's deployed

**Recommendation:** Update the design doc to reflect the actual implementation approach (append-during-build with indexed selectors), or mark Sections 6–7 as "superseded" with a note pointing to the actual `.ui` files and Java code.

---

## Architecture Assessment

| Aspect | Status |
|---|---|
| Load-Update separation clean | ✅ Zero `append`/`clear`/`appendInline` in update paths |
| All events bound in `build()` only | ✅ 46 bindings, all one-time |
| `sendUpdate(cmd, null, false)` safe | ✅ Confirmed via Hytale SDK precedent |
| `#NoPlaceholdersLabel` outside `#PlaceholderList` | ✅ No index offset |
| Index consistency (filters, cost, rows) | ✅ All correct |
| Reusable `.ui` components with `Visible: false` default | ✅ Clean hide/show pattern |

---

## Summary Table

| # | Category | Severity | Finding |
|---|---|---|---|
| 1 | Correctness | 🔴 High | Missing `sendUpdate` on same-tab click |
| 2 | Correctness | 🔴 High | Dead `data.recipeId` handler + unnecessary `updateRecipeGrid` call |
| 3 | Performance | 🟡 Medium | Empty `sendUpdate` on invalid RecipeHover/RecipeSelect |
| 4 | Performance | 🔵 Low | Empty `sendUpdate` on invalid PlaceholderDrop/Clear |
| 5 | Maintainability | 🔵 Low | Duplicate RecipeHover/RecipeSelect handlers |
| 6 | Performance | 🔵 Low | Linear scan for blockTypeId → outputItemId |
| 7 | Robustness | 🔵 Low | Silent fall-through on unrecognized SetFilter payload |
| 8 | Maintainability | ℹ️ Info | Design doc describes pre-allocation approach that was not implemented |

→ **Findings 1–2 should be addressed before merge.** Findings 3–7 are recommended but not blocking.
