---
topic: "Builder Mode — Filtering PocketCrafting Recipes by Inventory"
category: "Crafting / Windows"
updated: 2026-04-17
sources: ["FieldCraftingWindow.java", "CraftingWindow.java", "BenchWindow.java", "Window.java", "WindowManager.java", "Inventory.java", "LivingEntityInventoryChangeEvent.java", "ItemContainer.java", "CraftingManager.java", "PlayerSendInventorySystem.java", "GatherObjectiveTask.java", "ObjectivePlugin.java"]
---

# Builder Mode — Filtering PocketCrafting Recipes by Inventory

## Summary

This document answers whether we can implement a "Builder Mode" on a `WindowType.PocketCrafting` window that shows only recipes the player can actually craft, based on their current inventory contents. **Yes, it is fully feasible** — the server controls the `craftableRecipes` array in each category, `setNeedRebuild()` + `invalidate()` pushes a full UI rebuild to the client, and `LivingEntityInventoryChangeEvent` fires on every inventory change.

---

## Question 1: Can we filter `craftableRecipes` and push the update?

**YES.** This is confirmed by the decompiled engine code.

### How it works

1. The `craftableRecipes` array in each category JSON object controls what recipes the client displays
2. When you modify `windowData` and call `setNeedRebuild()` + `invalidate()`, the `WindowManager` sends an `UpdateWindow` packet on the next tick
3. The client receives the new JSON and, because `"needRebuild": true` is present, **fully rebuilds** the category tabs and recipe lists

### Evidence chain

**`Window.java`** — `setNeedRebuild()` adds the rebuild flag to JSON data:

```java
// com.hypixel.hytale.server.core.entity.entities.player.windows.Window
protected void setNeedRebuild() {
    this.needRebuild.set(true);
    this.getData().addProperty("needRebuild", Boolean.TRUE);
}

protected void invalidate() {
    this.isDirty.set(true);
}
```

**`WindowManager.java`** — `updateWindows()` is called every tick by `PlayerSendInventorySystem`, picks up dirty windows, and sends `UpdateWindow`:

```java
// com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager
public void updateWindows() {
    this.windows.forEach((id, window, _windowManager) -> {
        if (window.consumeIsDirty()) {
            _windowManager.updateWindow(window);
        }
    }, this);
}

public void updateWindow(@Nonnull Window window) {
    // ... builds InventorySection, ExtraResources ...
    this.playerRef.getPacketHandler().writeNoCache(
        new UpdateWindow(window.getId(), window.getData().toString(), section, extraResources)
    );
    window.consumeNeedRebuild();
}
```

**`PlayerSendInventorySystem.java`** — confirms `updateWindows()` runs every tick:

```java
// com.hypixel.hytale.server.core.modules.entity.player.PlayerSendInventorySystem
@Override
public void tick(float dt, int index, ...) {
    // ... inventory dirty check ...
    playerComponent.getWindowManager().updateWindows();
}
```

**Vanilla precedent: `BenchWindow.updateBenchTierLevel()`** — uses exactly this pattern to rebuild the entire window:

```java
// com.hypixel.hytale.builtin.crafting.window.BenchWindow
public void updateBenchTierLevel(int newValue) {
    this.windowData.addProperty("tierLevel", newValue);
    this.updateBenchUpgradeJob(0.0F);
    this.setNeedRebuild();   // ← adds "needRebuild": true to JSON
    this.invalidate();       // ← marks isDirty = true
}
```

### Implementation approach

```java
// In PortableBenchWindow:
private void rebuildFilteredCategories(ItemContainer playerContainer) {
    JsonArray categories = new JsonArray();
    
    for (PortableBenchConfig.CategoryDef categoryDef : config.categories()) {
        JsonObject category = new JsonObject();
        category.addProperty("id", categoryDef.id());
        category.addProperty("name", categoryDef.name());
        category.addProperty("icon", categoryDef.icon());
        
        JsonArray craftableRecipes = new JsonArray();
        for (String recipeCategoryId : categoryDef.recipeCategoryIds()) {
            Set<String> recipeIds = CraftingPlugin.getAvailableRecipesForCategory(
                config.benchId(), recipeCategoryId);
            if (recipeIds != null) {
                for (String recipeId : recipeIds) {
                    if (builderModeEnabled) {
                        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
                        if (recipe != null) {
                            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
                            if (!playerContainer.canRemoveMaterials(inputs)) {
                                continue; // skip — player can't craft this
                            }
                        }
                    }
                    craftableRecipes.add(recipeId);
                }
            }
        }
        category.add("craftableRecipes", craftableRecipes);
        categories.add(category);
    }
    
    this.windowData.add("categories", categories);
    this.setNeedRebuild();
    this.invalidate();
}
```

---

## Question 2: Inventory change event for auto-updating

**YES.** `LivingEntityInventoryChangeEvent` fires on every inventory container change.

### Event class

```java
// com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent
public class LivingEntityInventoryChangeEvent extends EntityEvent<LivingEntity, String> {
    private ItemContainer itemContainer;   // which container changed
    private Transaction transaction;       // what changed

    public ItemContainer getItemContainer() { return this.itemContainer; }
    public Transaction getTransaction() { return this.transaction; }
}
```

### When it fires

The `Inventory` class registers change listeners on **every** container (storage, hotbar, armor, utility, tools, backpack). Each listener dispatches `LivingEntityInventoryChangeEvent` via the event bus, scoped to the **world name**:

```java
// com.hypixel.hytale.server.core.inventory.Inventory.registerChangeEvents()
this.storageChange = this.storage.registerChangeEvent(e -> {
    this.markChanged();
    IEventDispatcher<LivingEntityInventoryChangeEvent, LivingEntityInventoryChangeEvent> dispatcher =
        HytaleServer.get().getEventBus()
            .dispatchFor(LivingEntityInventoryChangeEvent.class, this.entity.getWorld().getName());
    if (dispatcher.hasListener()) {
        dispatcher.dispatch(new LivingEntityInventoryChangeEvent(
            this.entity, e.container(), e.transaction()));
    }
});
// Same pattern for: hotbarChange, armorChange, utilityChange, toolChange, backpackChange
```

### How to register

Two registration patterns exist in vanilla:

**Pattern A — World-scoped (recommended):**
```java
// From GatherObjectiveTask.java
this.eventRegistry.register(
    LivingEntityInventoryChangeEvent.class,
    world.getName(),  // scope to specific world
    event -> { /* handler */ }
);
```

**Pattern B — Global:**
```java
// From ObjectivePlugin.java
this.getEventRegistry().registerGlobal(
    LivingEntityInventoryChangeEvent.class,
    this::onLivingEntityInventoryChange
);
```

### Thread safety warning

The event fires on the **entity store tick thread**. The `GatherObjectiveTask` example shows the correct pattern — it uses `world.execute(() -> { ... })` for deferred work if needed. However, since `windowData` mutation and `invalidate()` use `AtomicBoolean`, they are safe to call from the event handler directly. The `WindowManager.updateWindows()` picks up dirty windows on the next tick cycle, which naturally synchronizes the update.

### Key consideration: filtering the event

The event fires for **all** `LivingEntity` inventory changes (not just the player who has the window open). You must filter:

```java
event -> {
    LivingEntity entity = event.getEntity();
    if (!(entity instanceof Player)) return;
    
    // Check if this is the player whose window we're tracking
    Ref<EntityStore> ref = entity.getReference();
    if (ref == null || !ref.isValid()) return;
    
    // Only update if this player has our window open
    // (use a stored playerRef or UUID check)
}
```

---

## Question 3: Does PocketCrafting gray out uncraftable recipes?

**YES, partially — but it's 100% client-side and not controllable from the server.**

### What the client does

The PocketCrafting client renderer (and all crafting windows) receives:
- The `craftableRecipes` array from the window JSON (which recipes to show)
- The player's local inventory state (synced via `UpdatePlayerInventory` packets)
- The `UpdateKnownRecipes` packet data (which recipes the player has discovered)

The client performs its own ingredient-availability check **locally**:
- Recipes the player has ingredients for are shown normally
- Recipes the player lacks ingredients for are shown **grayed out** with red ingredient counts
- Recipes the player hasn't "discovered" (knowledge-required recipes) may be hidden entirely depending on the `worldMemoriesLevel`

### Server has NO control over graying

There is no server-side JSON field like `"available": true/false` or `"grayed": true` in the window data. The client decides independently which recipes to gray out based on its local copy of the player's inventory.

This means:
- **Sending only craftable recipe IDs in `craftableRecipes`** will hide uncraftable recipes entirely — the client won't show them at all
- **Sending all recipe IDs** will show them all, with the client graying out uncraftable ones on its own
- **There is no middle ground** where the server controls the graying without hiding

### Implication for Builder Mode

You have two design choices:

| Approach | Behavior | User Experience |
|----------|----------|-----------------|
| **Filter (hide)** | Only send recipe IDs the player can craft | Clean list, but recipes appear/disappear as inventory changes |
| **Show all** (default) | Send all recipe IDs | Client grays out uncraftable ones automatically — always visible |

The "Builder Mode" toggle would switch between these two states.

---

## Question 4: Inventory change listener registration

**YES, fully supported.** See Question 2 above for the complete API.

### Registration API summary

| Method | Scope | Package |
|--------|-------|---------|
| `eventRegistry.register(LivingEntityInventoryChangeEvent.class, worldName, handler)` | Per-world | `com.hypixel.hytale.event.EventRegistration` |
| `eventRegistry.registerGlobal(LivingEntityInventoryChangeEvent.class, handler)` | Global (all worlds) | `com.hypixel.hytale.event.EventRegistration` |

### Full class paths

| Class | Package |
|-------|---------|
| `LivingEntityInventoryChangeEvent` | `com.hypixel.hytale.server.core.event.events.entity` |
| `Inventory` | `com.hypixel.hytale.server.core.inventory` |
| `ItemContainer` | `com.hypixel.hytale.server.core.inventory.container` |
| `MaterialQuantity` | `com.hypixel.hytale.server.core.inventory` |
| `CraftingManager` | `com.hypixel.hytale.builtin.crafting.component` |
| `CraftingRecipe` | `com.hypixel.hytale.server.core.asset.type.item.config` |
| `Window` | `com.hypixel.hytale.server.core.entity.entities.player.windows` |
| `WindowManager` | `com.hypixel.hytale.server.core.entity.entities.player.windows` |
| `UpdateWindow` | `com.hypixel.hytale.protocol.packets.window` (packet ID 201) |

---

## Key APIs for Implementation

### Checking if player can craft a recipe

```java
// Get the recipe's input requirements
List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
// static method: com.hypixel.hytale.builtin.crafting.component.CraftingManager

// Check if container has the materials
boolean canCraft = playerContainer.canRemoveMaterials(inputs);
// method on: com.hypixel.hytale.server.core.inventory.container.ItemContainer
```

### Getting the player's combined container

```java
Player player = store.getComponent(ref, Player.getComponentType());
Inventory inventory = player.getInventory();
ItemContainer container = inventory.getCombinedBackpackStorageHotbar();
// Includes: backpack + storage + hotbar (the standard crafting pool)
```

### Pushing window updates

```java
// Mutate the JSON
this.windowData.add("categories", newCategoriesArray);

// Signal full rebuild + dirty
this.setNeedRebuild();  // adds "needRebuild":true to JSON
this.invalidate();       // sets isDirty flag

// WindowManager.updateWindows() picks this up next tick automatically
// (called by PlayerSendInventorySystem every tick)
```

---

## Performance Considerations

### Rebuild cost per inventory change

Each `LivingEntityInventoryChangeEvent` would trigger:
1. Iterating all recipes in all categories
2. Calling `CraftingManager.getInputMaterials(recipe)` per recipe (creates a small `List<MaterialQuantity>`)
3. Calling `container.canRemoveMaterials(inputs)` per recipe (read-only scan of container slots)
4. Rebuilding the JSON categories array
5. Serializing the JSON to string in `UpdateWindow` packet

### Mitigation strategies

1. **Debounce:** Don't rebuild on every single inventory change. Use a `lastRebuildTime` check (similar to `BenchWindow.checkProgressInvalidate()` which uses a 500ms interval and 5% threshold)
2. **Dirty flag:** Set a `needsFilterRebuild` flag on inventory change, let the window tick consume it
3. **Cache recipe checks:** Pre-compute `Map<String, List<MaterialQuantity>>` for all recipes at window open, only re-check on change
4. **Only rebuild if changed:** After filtering, compare new recipe ID set to previous set — skip `setNeedRebuild()` if identical

### Recommended approach: Tick-based debounced rebuild

```java
private volatile boolean inventoryDirty = false;
private long lastFilterRebuildMs = 0;
private static final long FILTER_REBUILD_INTERVAL_MS = 500; // 500ms debounce

// In inventory change listener:
this.inventoryDirty = true;

// In a tick method or checked before updateWindows():
if (inventoryDirty && System.currentTimeMillis() - lastFilterRebuildMs > FILTER_REBUILD_INTERVAL_MS) {
    inventoryDirty = false;
    lastFilterRebuildMs = System.currentTimeMillis();
    rebuildFilteredCategories(playerContainer);
}
```

**Note:** `Window` has no built-in `tick()` method. You'd need to either:
- Use the inventory change event directly (with debounce logic)
- Register an ECS system that ticks your window
- Use `world.execute()` with a delayed task (if available)

The simplest approach is debouncing directly in the event handler.

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────┐
│ Inventory Change Flow                                           │
│                                                                 │
│  Player picks up / drops item                                   │
│       │                                                         │
│       ▼                                                         │
│  ItemContainer.registerChangeEvent fires                        │
│       │                                                         │
│       ▼                                                         │
│  Inventory dispatches LivingEntityInventoryChangeEvent          │
│  (scoped to world name)                                         │
│       │                                                         │
│       ▼                                                         │
│  Our listener in PortableBenchWindow (registered on open)       │
│       │                                                         │
│       ├─ Filter: is this our player? is builder mode on?        │
│       ├─ Debounce: has 500ms passed since last rebuild?         │
│       │                                                         │
│       ▼                                                         │
│  rebuildFilteredCategories(playerContainer)                     │
│       │                                                         │
│       ├─ For each category:                                     │
│       │    For each recipe ID:                                  │
│       │      inputs = CraftingManager.getInputMaterials(recipe) │
│       │      if container.canRemoveMaterials(inputs) → include  │
│       │                                                         │
│       ├─ windowData.add("categories", newArray)                 │
│       ├─ setNeedRebuild()   ← adds "needRebuild":true to JSON  │
│       └─ invalidate()       ← sets isDirty = true               │
│                                                                 │
│  Next tick: PlayerSendInventorySystem                           │
│       │                                                         │
│       ▼                                                         │
│  WindowManager.updateWindows()                                  │
│       │                                                         │
│       ▼                                                         │
│  Sends UpdateWindow(id, jsonData, null, null) to client         │
│       │                                                         │
│       ▼                                                         │
│  Client re-renders PocketCrafting UI with filtered recipes      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Gotchas

1. **Event unregistration:** You MUST unregister the `LivingEntityInventoryChangeEvent` listener when the window closes. Use `registerCloseEvent()` on the Window to clean up. Failing to do so will leak listeners and cause NPEs when the window reference goes stale.

2. **`canRemoveMaterials` with `ResourceTypeId`:** Some recipes use `resourceTypeId` instead of `itemId` for inputs (e.g., "any wood plank"). `canRemoveMaterials` handles this correctly — it checks both `itemId` match and `resourceTypeId` match against `ItemResourceType` entries on items.

3. **No `Ref<EntityStore>` in change event:** The `LivingEntityInventoryChangeEvent` gives you the `LivingEntity`, not a `Ref<EntityStore>`. You need `entity.getReference()` to get the ref, and verify `ref.isValid()` before use.

4. **Empty categories:** If filtering removes ALL recipes from a category, the client will show an empty tab. Consider hiding empty categories entirely by not adding them to the JSON array.

5. **Craft action race condition:** When the player clicks craft, the client sends `CraftRecipeAction`. Between the click and the server receiving it, the inventory may have changed (another craft completed, item picked up). The server-side `handleAction()` already validates materials via `container.canRemoveMaterials(inputs)`, so this is safe — it just means the filtered list may briefly show a recipe that's no longer craftable.

6. **Shared `windowData` reference:** `getData()` returns the same `JsonObject` reference. Calling `windowData.add("categories", ...)` replaces the old array. This is the same pattern used by `BenchWindow` for progress/tier updates — safe and expected.

## See Also

- [Window Actions and Updates](../plugins/window-actions-and-updates.md)
- [Portable Bench Feasibility](./portable-bench-feasibility.md)
- [Structural Window Crash Analysis](./structural-window-crash-analysis.md)
