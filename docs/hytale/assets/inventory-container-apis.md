---
topic: "Inventory Container APIs — removeMaterials & Slot Protection"
category: "Assets / Inventory"
updated: 2026-04-26
sources: [
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/ItemContainer.java",
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/CombinedItemContainer.java",
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/SimpleItemContainer.java",
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/InternalContainerUtilMaterial.java",
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/InternalContainerUtilItemStack.java",
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/filter/SlotFilter.java",
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/filter/FilterActionType.java",
  ".tmp_hytale_src/com/hypixel/hytale/server/core/inventory/Inventory.java"
]
---

# Inventory Container APIs — removeMaterials & Slot Protection

## Summary

This document answers specific technical questions about how to consume recipe materials from a player's inventory **without touching the active hotbar slot** (the armed PlaceBlock placeholder). The core problem: `removeMaterials()` on a combined container iterates slots linearly and will eat items from any matching slot, including the active hotbar slot.

---

## Q1: Can We Exclude Specific Slots from a Combined Container?

**Answer: No built-in slot-exclusion API exists on `removeMaterials`, but YES via `SlotFilter`.**

### What's NOT available:
- No `removeMaterials` overload accepts a slot exclusion list
- No `CombinedItemContainer` method creates a filtered view/sub-container
- No parameter on any removal method accepts "skip these slots"

### What IS available — `SlotFilter` on `REMOVE`:

`SimpleItemContainer` supports per-slot filters via:

```java
// SimpleItemContainer.java:227
void setSlotFilter(FilterActionType actionType, short slot, @Nullable SlotFilter filter)
```

Where `SlotFilter` is a functional interface:
```java
// SlotFilter.java
public interface SlotFilter {
    SlotFilter ALLOW = (actionType, container, slot, itemStack) -> true;
    SlotFilter DENY  = (actionType, container, slot, itemStack) -> false;
    boolean test(FilterActionType actionType, ItemContainer container, short slot, @Nullable ItemStack itemStack);
}
```

And `FilterActionType` is:
```java
public enum FilterActionType {
    ADD,
    REMOVE,
    DROP;
}
```

**How it works in the removal path:**

In `InternalContainerUtilItemStack.testRemoveItemStackFromItems()` (line ~568):
```java
for (short i = 0; i < container.getCapacity() && testQuantityRemaining > 0; i++) {
    if (!filter || !container.cantRemoveFromSlot(i)) {  // <-- CHECKS SLOT FILTER
        ItemStack slotItemStack = container.internal_getSlot(i);
        // ... removal logic
    }
}
```

`cantRemoveFromSlot` in `SimpleItemContainer` (line 159) checks the slot's `REMOVE` filter:
```java
protected boolean cantRemoveFromSlot(short slot) {
    return !this.globalFilter.allowOutput() ? true : this.testFilter(FilterActionType.REMOVE, slot, null);
}
```

**Strategy: Set a `SlotFilter.DENY` on the active hotbar slot's `REMOVE` action, call `removeMaterials`, then clear the filter.**

```java
// Pseudocode:
ItemContainer hotbar = inventory.getHotbar();
short activeSlot = inventory.getActiveHotbarSlot(); // byte -> short
hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, SlotFilter.DENY);
try {
    combined.removeMaterials(materials, true, true, true);
} finally {
    hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, null); // clear filter
}
```

**CRITICAL**: The `filter` boolean parameter in `removeMaterials(materials, allOrNothing, exactAmount, filter)` must be `true` for slot filters to be respected. If `filter=false`, `cantRemoveFromSlot` is skipped entirely.

---

## Q2: What Combined Containers Are Available?

From `Inventory.buildCombinedContains()` (line 796):

| Getter | Composition (ordered) | Has Hotbar? |
|--------|----------------------|-------------|
| `getCombinedHotbarFirst()` | hotbar, storage | YES (first) |
| `getCombinedStorageFirst()` | storage, hotbar | YES (second) |
| `getCombinedBackpackStorageHotbar()` | backpack, storage, hotbar | YES (last) |
| `getCombinedArmorHotbarStorage()` | armor, hotbar, storage | YES (middle) |
| `getCombinedArmorHotbarUtilityStorage()` | armor, hotbar, utility, storage | YES |
| `getCombinedHotbarUtilityConsumableStorage()` | hotbar, utility, storage | YES (first) |
| `getCombinedEverything()` | armor, hotbar, utility, storage, backpack | YES |

There is also an internal field `combinedStorageHotbarBackpack` (storage, hotbar, backpack) — **but it has NO public getter**. It is only used in `buildCombinedContains()`.

**No pre-built combined container excludes the hotbar.**

### Can you construct one ad-hoc?

Yes. `CombinedItemContainer` has a public constructor:
```java
public CombinedItemContainer(ItemContainer... containers)
```

So you can do:
```java
CombinedItemContainer storageAndBackpack = new CombinedItemContainer(
    inventory.getBackpack(), inventory.getStorage()
);
```

**However**, ad-hoc containers won't send inventory change events to the player client properly because they lack the registered change event wiring that `Inventory.registerChangeEvents()` sets up on the real containers. The underlying `SimpleItemContainer` instances fire their own change events regardless, so the changes DO propagate — but the transaction result's `sendUpdate` may not trigger UI sync correctly.

**Recommended approach**: Use a `SlotFilter` on the hotbar's active slot (Q1) with one of the standard combined containers, rather than constructing an ad-hoc one.

---

## Q3: Can We Consume from Storage Only, Then Hotbar Minus Active Slot?

### `inventory.getStorage()` — YES, full `ItemContainer`

```java
ItemContainer storage = inventory.getStorage();  // Inventory.java:510
```
Returns a `SimpleItemContainer` (or `EmptyItemContainer`). It has ALL `ItemContainer` methods:
- `canRemoveMaterials(List<MaterialQuantity>)` ✅
- `canRemoveMaterials(List<MaterialQuantity>, exactAmount, filter)` ✅
- `removeMaterials(List<MaterialQuantity>)` ✅
- `removeMaterials(List<MaterialQuantity>, allOrNothing, exactAmount, filter)` ✅

### `inventory.getHotbar()` — YES, full `ItemContainer`

```java
ItemContainer hotbar = inventory.getHotbar();  // Inventory.java:518
```
Same capabilities as storage. You can call `removeMaterials` on it directly.

### Protecting the active slot on hotbar

Use `setSlotFilter(FilterActionType.REMOVE, activeSlot, SlotFilter.DENY)` as described in Q1. The hotbar is a `SimpleItemContainer` and supports per-slot filters.

### Two-phase approach (storage first, then hotbar):

```java
// Phase 1: Try storage
ItemContainer storage = inventory.getStorage();
ItemContainer backpack = inventory.getBackpack();
CombinedItemContainer storageBackpack = new CombinedItemContainer(backpack, storage);

if (storageBackpack.canRemoveMaterials(materials)) {
    storageBackpack.removeMaterials(materials, true, true, true);
} else {
    // Phase 2: Need hotbar too — protect active slot
    ItemContainer hotbar = inventory.getHotbar();
    hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, SlotFilter.DENY);
    try {
        CombinedItemContainer all = new CombinedItemContainer(backpack, storage, hotbar);
        if (all.canRemoveMaterials(materials)) {
            all.removeMaterials(materials, true, true, true);
        }
    } finally {
        hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, null);
    }
}
```

**Simpler single-pass approach** (recommended):

```java
ItemContainer hotbar = inventory.getHotbar();
hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, SlotFilter.DENY);
try {
    CombinedItemContainer combined = inventory.getCombinedBackpackStorageHotbar();
    if (combined.canRemoveMaterials(materials)) {
        combined.removeMaterials(materials, true, true, true);
    }
} finally {
    hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, null);
}
```

This is simpler because `getCombinedBackpackStorageHotbar()` already orders backpack→storage→hotbar, so hotbar is searched last anyway, and the active slot in hotbar is locked by the filter.

---

## Q4: What Happens When `allOrNothing=true` Fails Mid-Way?

### Answer: It doesn't fail mid-way — it does a DRY RUN first.

From `InternalContainerUtilMaterial.internal_removeMaterials()` (line 86):

```java
if (allOrNothing || exactAmount) {
    // DRY RUN: test ALL materials FIRST
    for (MaterialQuantity material : materials) {
        int testQuantityRemaining = testRemoveMaterialFromItems(
            itemContainer, material, material.getQuantity(), filter
        );
        if (testQuantityRemaining > 0) {
            return new ListTransaction<>(false, ...); // FAILS — nothing removed
        }
        if (exactAmount && testQuantityRemaining < 0) {
            return new ListTransaction<>(false, ...); // FAILS — nothing removed
        }
    }
}
// Only if dry run passes: actually remove
for (MaterialQuantity material : materials) {
    transactions.add(internal_removeMaterial(itemContainer, material, ...));
}
```

**Key insight**: When `allOrNothing=true`, the engine first calls `testRemoveMaterialFromItems()` for EVERY material in the list. This is a **read-only test** that simulates removal without mutating the container. Only if ALL materials pass the test does it proceed to actual removal.

### But the test has a subtle bug for multi-material recipes

The dry-run test for each material is independent — it doesn't account for slot depletion from previous materials. If material A and material B both need items from the same slot, the test could pass for each independently but the actual removal could leave material B short. However, the engine handles this by testing per-material within `internal_removeMaterial` too (which has its own `allOrNothing` check).

### Two-container split: No automatic rollback

If you split consumption across two containers (storage first, then hotbar), there is **no built-in rollback mechanism**. `ListTransaction` has `succeeded()` but no `undo()` or `rollback()` method. You would need to:

1. Call `canRemoveMaterials` on the combined container FIRST to verify affordability
2. Then call `removeMaterials` knowing it will succeed

**Or**: Use the `SlotFilter` approach with a single `removeMaterials(materials, true, true, true)` call on the combined container. The `allOrNothing` dry-run handles everything atomically.

---

## Q5: Slot-Level Removal APIs

### Direct slot removal:

```java
// Remove entire stack from slot
SlotTransaction removeItemStackFromSlot(short slot)
SlotTransaction removeItemStackFromSlot(short slot, boolean filter)

// Remove N items from slot
ItemStackSlotTransaction removeItemStackFromSlot(short slot, int quantityToRemove)
ItemStackSlotTransaction removeItemStackFromSlot(short slot, int quantityToRemove, boolean allOrNothing, boolean filter)

// Remove specific item type from slot
ItemStackSlotTransaction removeItemStackFromSlot(short slot, ItemStack itemStackToRemove, int quantityToRemove)
ItemStackSlotTransaction removeItemStackFromSlot(short slot, ItemStack itemStackToRemove, int quantityToRemove, boolean allOrNothing, boolean filter)

// Remove material from specific slot
MaterialSlotTransaction removeMaterialFromSlot(short slot, MaterialQuantity material)
MaterialSlotTransaction removeMaterialFromSlot(short slot, MaterialQuantity material, boolean allOrNothing, boolean exactAmount, boolean filter)
```

### Get/set for snapshot-restore:

```java
// Read a slot
ItemStack getItemStack(short slot)

// Overwrite a slot (replaces whatever is there)
ItemStackSlotTransaction setItemStackForSlot(short slot, ItemStack itemStack)
ItemStackSlotTransaction setItemStackForSlot(short slot, ItemStack itemStack, boolean filter)

// Replace with type-check
ItemStackSlotTransaction replaceItemStackInSlot(short slot, ItemStack itemStackToRemove, ItemStack replacement)
```

### Snapshot-restore pattern:

```java
ItemContainer hotbar = inventory.getHotbar();
short activeSlot = (short) inventory.getActiveHotbarSlot();

// Snapshot
ItemStack snapshot = hotbar.getItemStack(activeSlot);

// Do removal on combined container
combined.removeMaterials(materials, true, true, true);

// Restore if consumed
ItemStack afterRemoval = hotbar.getItemStack(activeSlot);
if (!ItemStack.isEquivalentType(snapshot, afterRemoval) || 
    (snapshot != null && afterRemoval != null && snapshot.getQuantity() != afterRemoval.getQuantity())) {
    hotbar.setItemStackForSlot(activeSlot, snapshot, false); // filter=false to bypass filters
}
```

**Warning**: The snapshot-restore approach is racy and semantically wrong — it doesn't actually prevent the removal, it patches it up after the fact. Items consumed from the active slot would need to be re-removed from elsewhere, creating accounting headaches. **Use `SlotFilter` instead.**

---

## Q6: The `filter` Parameter in `removeMaterials(materials, allOrNothing, exactAmount, filter)`

### What it does:

The `filter` boolean controls whether **slot filters** (`cantRemoveFromSlot`, `cantAddToSlot`) are respected during the operation.

- **`filter=true`** (default): Before removing from any slot, the engine calls `cantRemoveFromSlot(slot)`. If the slot has a `SlotFilter` for `FilterActionType.REMOVE` that returns `false`, that slot is **skipped**.

- **`filter=false`**: Slot filters are **completely bypassed**. The engine removes from any slot with matching items regardless of filters.

### Where it's checked (InternalContainerUtilItemStack.java):

```java
// Line 568 — test (dry run)
if (!filter || !container.cantRemoveFromSlot(i)) {
    // consider this slot for removal
}

// Line 172, 189, 265 — actual removal
if (filter && itemContainer.cantRemoveFromSlot(slot)) {
    // skip this slot
}
```

### Can it filter by item category or resource type?

**No.** The `filter` parameter is a simple boolean that gates the `SlotFilter` check. It does NOT accept a predicate for filtering by item properties. The `SlotFilter` interface receives the `FilterActionType`, container, slot index, and (for ADD) the item stack, but for REMOVE operations the `itemStack` parameter is `null`:

```java
// SimpleItemContainer.java:159
protected boolean cantRemoveFromSlot(short slot) {
    return !this.globalFilter.allowOutput() ? true : this.testFilter(FilterActionType.REMOVE, slot, null);
}
```

So you **cannot** use the `SlotFilter` to filter by item type during removal — it only knows the slot index. This means you can't use it to say "skip slots containing PlaceBlock items" generically. You CAN use it to say "skip slot #3 specifically."

### Does `canRemoveMaterials` also respect filters?

Yes. `canRemoveMaterials(materials, exactAmount, filter)` also calls `testRemoveMaterialFromItems` which checks `cantRemoveFromSlot` when `filter=true`:

```java
// ItemContainer.java:1058
public boolean canRemoveMaterials(@Nullable List<MaterialQuantity> materials, boolean exactAmount, boolean filter) {
    // ... calls testRemoveMaterialFromItems(this, material, material.getQuantity(), filter)
}
```

So `canRemoveMaterials` and `removeMaterials` behave consistently with the `filter` flag.

---

## Recommended Solution

**Use `SlotFilter.DENY` on the active hotbar slot during material consumption:**

```java
Inventory inventory = player.getInventory();
ItemContainer hotbar = inventory.getHotbar();
byte activeSlotByte = inventory.getActiveHotbarSlot();
short activeSlot = (short)(activeSlotByte & 0xFF);

// Lock the active slot
hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, SlotFilter.DENY);
try {
    CombinedItemContainer combined = inventory.getCombinedBackpackStorageHotbar();
    
    // canRemoveMaterials respects the filter too
    if (combined.canRemoveMaterials(materials, true, true)) { // exactAmount=true, filter=true
        ListTransaction<MaterialTransaction> tx = combined.removeMaterials(materials, true, true, true);
        // allOrNothing=true, exactAmount=true, filter=true
        if (!tx.succeeded()) {
            // should not happen if canRemoveMaterials passed, but handle defensively
        }
    }
} finally {
    // Always clear the filter
    hotbar.setSlotFilter(FilterActionType.REMOVE, activeSlot, null);
}
```

**Why this works:**
1. `getCombinedBackpackStorageHotbar()` → backpack, storage, hotbar — hotbar is checked **last**
2. `SlotFilter.DENY` on the active slot makes `cantRemoveFromSlot(activeSlot)` return `true`
3. Both `canRemoveMaterials` and `removeMaterials` respect slot filters when `filter=true`
4. The `allOrNothing=true` dry-run ensures atomic consumption
5. The `finally` block ensures the filter is always cleared even on exceptions

**Edge case**: If the ONLY matching items for a recipe material are in the active hotbar slot, `canRemoveMaterials` will correctly return `false` — the placement will be denied, which is the correct behavior (the placeholder shouldn't be consumed as material).

---

## See Also
- [inventory-hotbar-events.md](../inventory-hotbar-events.md) — hotbar change events
- [server-client-boundary.md](../server-client-boundary.md) — what's server-modifiable
