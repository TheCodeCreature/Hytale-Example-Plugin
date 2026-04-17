---
topic: "StructuralCrafting Window Crash Analysis"
category: "Crafting / Windows"
updated: 2026-04-16
sources: ["StructuralCraftingWindow.java", "BenchWindow.java", "CraftingWindow.java", "BlockWindow.java", "Window.java", "FieldCraftingWindow.java", "WindowManager.java", "OpenWindow.java", "ItemContainerWindow.java", "MaterialContainerWindow.java", "InventorySection.java"]
---

# StructuralCrafting Window Crash Analysis

## Root Cause

The client crashes because `PortableBenchWindow` sends `WindowType.StructuralCrafting` but is **missing two mandatory packet fields** that the real `StructuralCraftingWindow` always sends: `InventorySection` and `ExtraResources`. The client's StructuralCrafting renderer dereferences these as non-null and crashes.

---

## 1. What EXACT Data Does `StructuralCraftingWindow` Write?

The real `StructuralCraftingWindow` builds its data across four levels of inheritance:

### Layer 1: `BenchWindow` constructor
```java
windowData.addProperty("type", bench.getType().ordinal());           // 3
windowData.addProperty("id", bench.getId());                          // "Builders"
windowData.addProperty("name", item.getTranslationKey());             // "server.items.Bench_Builders.name"
windowData.addProperty("blockItemId", item.getId());                  // actual item asset ID
windowData.addProperty("tierLevel", benchState.getTierLevel());       // 1+
```

### Layer 2: `BenchWindow.onOpen0()`
```java
windowData.addProperty("worldMemoriesLevel", memoriesLevel);
windowData.addProperty("nearbyChestCount", chestCount);
windowData.addProperty("maxChestCount", maxChestCount);
windowData.addProperty("chestHorizontalRadius", horizontalRadius);
windowData.addProperty("chestVerticalRadius", verticalRadius);
```

### Layer 3: `CraftingWindow` constructor
```java
// Builds full categories array from bench.getCategories()
JsonArray categories = new JsonArray();
for (CraftingBench.BenchCategory benchCategory : craftingBench.getCategories()) {
    JsonObject category = new JsonObject();
    category.addProperty("id", benchCategory.getId());
    category.addProperty("name", benchCategory.getName());
    category.addProperty("icon", benchCategory.getIcon());
    // + craftableRecipes array
    // + itemCategories array (if present)
    categories.add(category);
}
windowData.add("categories", categories);
```

### Layer 3b: `CraftingWindow.onOpen0()`
```java
windowData.add("memoriesPerLevel", memoriesPerLevel);  // JsonArray of ints
```

### Layer 4: `StructuralCraftingWindow` constructor
```java
windowData.addProperty("selected", 0);
windowData.addProperty("allowBlockGroupCycling", structuralBench.shouldAllowBlockGroupCycling());
windowData.addProperty("alwaysShowInventoryHints", structuralBench.shouldAlwaysShowInventoryHints());
```

### Layer 4b: `StructuralCraftingWindow.onOpen0()`
```java
windowData.add("inventoryHints", CraftingManager.generateInventoryHints(...));
```

### Complete JSON shape sent by the real window:
```json
{
  "type": 3,
  "id": "Builders",
  "name": "server.items.Bench_Builders.name",
  "blockItemId": "hytale:Bench_Builders",
  "tierLevel": 1,
  "worldMemoriesLevel": 0,
  "nearbyChestCount": 0,
  "maxChestCount": 8,
  "chestHorizontalRadius": 4,
  "chestVerticalRadius": 2,
  "categories": [
    {
      "id": "category_id",
      "name": "Category Name",
      "icon": "icon_reference",
      "craftableRecipes": ["recipe1", "recipe2"],
      "itemCategories": [...]
    }
  ],
  "memoriesPerLevel": [0, 10, 20],
  "selected": 0,
  "allowBlockGroupCycling": true,
  "alwaysShowInventoryHints": true,
  "inventoryHints": [...]
}
```

### What `PortableBenchWindow` sends (your code):
```json
{
  "type": 3,
  "id": "Builders",
  "name": "server.items.Bench_Builders.name",
  "selected": 0,
  "allowBlockGroupCycling": true,
  "alwaysShowInventoryHints": true,
  "tierLevel": 0,
  "blockItemId": ""
}
```

**Missing from JSON:** `categories`, `inventoryHints`, `worldMemoriesLevel`, `nearbyChestCount`, `maxChestCount`, `chestHorizontalRadius`, `chestVerticalRadius`, `memoriesPerLevel`.

---

## 2. The OpenWindow Packet — The Real Crash Cause

The `OpenWindow` packet (ID 200) has this structure:

```java
public class OpenWindow implements Packet {
    public int id;
    public WindowType windowType;
    public String windowData;              // JSON string — nullable
    public InventorySection inventory;     // nullable
    public ExtraResources extraResources;  // nullable
}
```

### How `WindowManager.openWindow()` populates it:

```java
InventorySection section = null;
if (window instanceof ItemContainerWindow itemContainerWindow) {
    section = itemContainerWindow.getItemContainer().toPacket();
}

ExtraResources extraResources = null;
if (window instanceof MaterialContainerWindow materialContainerWindow) {
    extraResources = materialContainerWindow.getExtraResourcesSection().toPacket();
}

return new OpenWindow(id, window.getType(), window.getData().toString(), section, extraResources);
```

### What the real `StructuralCraftingWindow` sends:

| Field | Value | Why |
|-------|-------|-----|
| `windowType` | `StructuralCrafting(4)` | — |
| `windowData` | Full JSON (see above) | — |
| `inventory` | **`InventorySection`** with 65 slots (1 input + 64 options) | Because it implements `ItemContainerWindow` |
| `extraResources` | **`ExtraResources`** with nearby chest data | Because `BenchWindow` implements `MaterialContainerWindow` |

### What your `PortableBenchWindow` sends:

| Field | Value | Why |
|-------|-------|-----|
| `windowType` | `StructuralCrafting(4)` | — |
| `windowData` | Partial JSON (missing categories, hints, etc.) | — |
| `inventory` | **`null`** | Does NOT implement `ItemContainerWindow` |
| `extraResources` | **`null`** | Does NOT implement `MaterialContainerWindow` |

**The client receives `WindowType.StructuralCrafting` and instantiates its StructuralCrafting renderer. That renderer expects the `InventorySection` to be present (to render the input slot and 64 option slots). When it tries to read the null inventory data, the client crashes.**

---

## 3. Does `StructuralCraftingWindow` Implement `ItemContainerWindow`?

**Yes.**

```java
public class StructuralCraftingWindow extends CraftingWindow implements ItemContainerWindow {
    private final SimpleItemContainer inputContainer;        // 1 slot
    private final SimpleItemContainer optionsContainer;      // 64 slots
    private final CombinedItemContainer combinedItemContainer;

    @Override
    public ItemContainer getItemContainer() {
        return this.combinedItemContainer;  // 65 slots total
    }
}
```

The `InventorySection` packet encodes the container's capacity and current items as `Map<Integer, ItemWithAllMetadata>`. The client uses this to render:
- **Slot 0**: the input slot (where you place a block to see structural variants)
- **Slots 1–64**: the output options (structural variants of that block)

Without this data, the client's structural crafting UI has no slot grid to render → crash.

---

## 4. `WindowType.PocketCrafting` + `BenchType.StructuralCrafting` — What Happens?

The `WindowType` determines which **client-side renderer** is instantiated. The `BenchType` in the JSON is secondary metadata.

- `WindowType.PocketCrafting` → client creates a **PocketCrafting renderer** (flat recipe list with category tabs)
- The `"type": 3` (StructuralCrafting) in JSON would just be treated as metadata

**This will NOT crash** — the PocketCrafting renderer doesn't require `InventorySection` or `ExtraResources`. The `FieldCraftingWindow` proves this.

**However**, the UI will be wrong:
- Shows a flat recipe list, not the structural crafting grid (input → variant options)
- No block-in / variants-out interaction
- No block group cycling buttons
- No "place a block to see what you can make" flow

The client simply ignores JSON fields it doesn't understand for that renderer.

---

## 5. `WindowType.PocketCrafting` + `BenchType.Crafting` + Builder's Recipes — The Working Approach

This is the **FieldCraftingWindow pattern** and it is the most reliable portable approach.

### How FieldCraftingWindow encodes recipes:

```java
// WindowType.PocketCrafting, BenchType.Crafting
this.windowData.addProperty("type", BenchType.Crafting.ordinal());  // 0
this.windowData.addProperty("id", "Fieldcraft");
this.windowData.addProperty("name", "server.ui.inventory.fieldcraft.title");

JsonArray categories = new JsonArray();
for (FieldcraftCategory category : FieldcraftCategory.getAssetMap().getAssetMap().values()) {
    JsonObject cat = new JsonObject();
    cat.addProperty("id", category.getId());
    cat.addProperty("icon", category.getIcon());
    cat.addProperty("name", category.getName());
    Set<String> recipes = CraftingPlugin.getAvailableRecipesForCategory("Fieldcraft", category.getId());
    if (recipes != null) {
        JsonArray itemsArray = new JsonArray();
        for (String recipeId : recipes) {
            itemsArray.add(recipeId);
        }
        cat.add("craftableRecipes", itemsArray);
    }
    categories.add(cat);  // BUG NOTE: the decompiled code has a bug — this line is missing
}
this.windowData.add("categories", categories);
```

### To replicate for Builder's bench recipes:

```java
// Use PocketCrafting so the client uses the simple renderer
super(WindowType.PocketCrafting);
this.windowData.addProperty("type", BenchType.Crafting.ordinal());  // 0
this.windowData.addProperty("id", "PortableBuilders");
this.windowData.addProperty("name", "server.items.Bench_Builders.name");

// Populate categories with Builder's bench recipes
JsonArray categories = new JsonArray();
List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(BenchType.StructuralCrafting, "Builders");
// Group by category and build the categories array...
// Each category: { id, icon, name, craftableRecipes: [recipeId, ...] }
this.windowData.add("categories", categories);
```

### Trade-offs:

| Aspect | FieldCrafting-style | Structural-style |
|--------|-------------------|-----------------|
| Client crash | **No** — PocketCrafting renderer is safe | **Yes** — needs InventorySection |
| UI type | Flat recipe list with tabs | Input slot → variant grid |
| Block group cycling | No | Yes |
| Input slot interaction | No — just click a recipe | Yes — place block, see variants |
| Crafting mechanism | `craftSimpleItem()` — instant from inventory | `queueCraft()` — timed, from input slot |
| Complexity | Low — just JSON data | High — needs ItemContainerWindow + containers |

---

## 6. CLIENT_REQUESTABLE Registration

`CLIENT_REQUESTABLE_WINDOW_TYPES` is **only** for client-initiated windows (e.g., player opens inventory → crafting tab). Server-opened windows via `windowManager.openWindow()` do NOT need to be registered here.

Currently registered:
```java
Window.CLIENT_REQUESTABLE_WINDOW_TYPES.put(WindowType.PocketCrafting, FieldCraftingWindow::new);
Window.CLIENT_REQUESTABLE_WINDOW_TYPES.put(WindowType.Memories, MemoriesWindow::new);
```

You **could** register a supplier for `PocketCrafting` to override the default `FieldCraftingWindow`, but this would break normal fieldcrafting for all players. Not recommended.

For a server-initiated portable bench, you don't need this mechanism at all — just call `windowManager.openWindow()` or `pageManager.setPageWithWindows()`.

---

## 7. Exact Data Format Differences

### FieldCraftingWindow (WORKS — no crash)

**Packet:**
```
OpenWindow {
  id: <auto>,
  windowType: PocketCrafting(1),
  windowData: '{"type":0,"id":"Fieldcraft","name":"...","categories":[...]}',
  inventory: null,
  extraResources: null
}
```

**Key traits:**
- Extends `Window` directly
- NOT `ItemContainerWindow` → no InventorySection
- NOT `MaterialContainerWindow` → no ExtraResources
- Client PocketCrafting renderer handles null inventory gracefully

### StructuralCraftingWindow (REQUIRES block — crashes without InventorySection)

**Packet:**
```
OpenWindow {
  id: <auto>,
  windowType: StructuralCrafting(4),
  windowData: '{"type":3,"id":"Builders","name":"...","categories":[...],"selected":0,...}',
  inventory: InventorySection { capacity: 65, items: {...} },
  extraResources: ExtraResources { ... }
}
```

**Key traits:**
- Extends `CraftingWindow → BenchWindow → BlockWindow → Window`
- IS `ItemContainerWindow` → sends InventorySection (65 slots)
- IS `MaterialContainerWindow` (via BenchWindow) → sends ExtraResources
- Client StructuralCrafting renderer **requires** the inventory data to render

### Your PortableBenchWindow (CRASHES)

**Packet:**
```
OpenWindow {
  id: <auto>,
  windowType: StructuralCrafting(4),     ← tells client to use structural renderer
  windowData: '{"type":3,"id":"Builders","name":"...","selected":0,...}',
  inventory: null,                        ← CRASH: renderer expects this
  extraResources: null                    ← possibly also crash
}
```

---

## 8. Recommendation

### Most Reliable: PocketCrafting with Builder's Recipes

Use `WindowType.PocketCrafting` + `BenchType.Crafting` and populate the `categories` array with Builder's bench recipes. This gives you:

- **No crash** — the PocketCrafting renderer handles null inventory/extraResources
- **Working recipe browsing** — categories with recipe lists render correctly
- **Working crafting** — `CraftingWindow.craftSimpleItem()` crafts from player inventory
- **No block dependency** — extends `Window` directly like `FieldCraftingWindow`

**Limitations:**
- No structural crafting grid (input slot → variant list)
- No block group cycling
- Flat recipe list UI instead of the specialized structural UI
- Recipes that require an input block will need adaptation (structural recipes take 1 block in and produce a variant — you'd need to present these differently in a flat list)

### Alternative: Implement ItemContainerWindow

If you absolutely need the structural crafting UI, your `PortableBenchWindow` would need to:
1. Implement `ItemContainerWindow` and provide a `CombinedItemContainer` (1 input + 64 options)
2. Implement `MaterialContainerWindow` (or at minimum ensure the client handles null `ExtraResources` — which is less certain)
3. Populate `categories` from the bench config
4. Handle `SelectSlotAction`, `CraftRecipeAction`, `ChangeBlockAction`
5. Implement recipe matching logic (what goes in the input slot → what appears in options)

This is essentially reimplementing `StructuralCraftingWindow` without the block dependency — high complexity, high risk of hitting other client-side assumptions about block state.

### Recommendation Order

1. **PocketCrafting + categories approach** — works today, simple, no crash risk
2. **PocketCrafting + ItemContainerWindow** — experimental, may give structural-like behavior if client supports it (untested)
3. **StructuralCrafting + full ItemContainerWindow + MaterialContainerWindow** — maximum fidelity, highest crash risk from unknown client assumptions
