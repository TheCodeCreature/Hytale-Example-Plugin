---
topic: "Creative Inventory Tab System & Replication in Custom UI"
category: "Plugins / Custom UI"
updated: 2026-04-27
sources:
  - "decompiled ItemCategory.java"
  - "decompiled FieldcraftCategory.java"
  - "decompiled AssetRegistryLoader.java"
  - "decompiled ItemLibrary.java"
  - "decompiled ItemBase.java (protocol)"
  - "decompiled Item.java (server config)"
  - "decompiled CraftingRecipe.java (server config)"
  - "decompiled CraftingRecipe.java (protocol)"
  - "decompiled BenchRequirement.java"
  - "decompiled BenchType.java"
  - "decompiled StructuralCraftingWindow.java"
  - "decompiled StructuralCraftingBench.java"
  - "decompiled CraftingWindow.java"
  - "decompiled CraftingBench.java"
  - "decompiled FieldCraftingWindow.java"
  - "decompiled EntitySpawnPage.java"
  - "decompiled CustomUIEventBindingType.java"
  - "decompiled BlockGroup.java"
  - "docs/hytale/plugins/ui-grid-layout-research.md"
  - "docs/hytale/plugins/custom-ui-for-blueprint-bench.md"
---

# Creative Inventory Tab System — Deep Research

## Executive Summary

The creative inventory menu is **client-side hardcoded UI** — it is NOT a Custom UI page and cannot be modified by plugins. However, every data structure it uses (categories, items, sets) is server-defined and sent to the client via network packets. This document provides complete knowledge for **replicating** the tab/category/search pattern in a Custom UI page.

---

## 1. Creative Inventory Tab System

### How the creative menu works

The creative menu is a **built-in C# client UI** that renders the item browser. It is NOT moddable via Custom UI.

> Source: [ui-grid-layout-research.md](./ui-grid-layout-research.md#L182)
> "Built-in interfaces controlled by the C# game client... You cannot modify these."

**Server-side data flow:**

1. `AssetRegistryLoader` registers `ItemCategory` assets from path `"Item/Category/CreativeLibrary"` ([AssetRegistryLoader.java](.tmp_hytale_src/com/hypixel/hytale/server/core/asset/AssetRegistryLoader.java#L648))
2. The `ItemCategoryPacketGenerator` serializes all `ItemCategory` objects and sends them to the client during asset setup
3. The `ItemLibrary` protocol packet sends the full `ItemBase[]` array (all items) to the client
4. Each `ItemBase` has `String[] categories` and `String set` fields — the client uses these to filter items into category tabs

### ItemCategory asset structure

**Server config class:** `ItemCategory.java` ([ItemCategory.java](.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemCategory.java))

```
Asset path: Server/Item/Category/CreativeLibrary/
```

| Field | Type | Purpose |
|-------|------|---------|
| `Id` | `String` | Unique category identifier |
| `Name` | `String` | Display name (localization key) |
| `Icon` | `String` | Icon asset path for the tab |
| `Order` | `int` | Sort order for tab display |
| `InfoDisplayMode` | `ItemGridInfoDisplayMode` | How items show info (Tooltip, Adjacent, None) |
| `Children` | `ItemCategory[]` | **Nested sub-categories** (recursive tree) |

**Key insight:** Categories are a **tree structure** — a top-level category (tab) can have nested `Children` categories (sub-tabs/filters). Children are sorted by `order` after deserialization.

**Protocol class:** `ItemCategory` (protocol) has identical fields and is sent directly to the client.

### ItemGridInfoDisplayMode enum

```java
// ItemGridInfoDisplayMode.java
Tooltip(0),    // Show item info as tooltip on hover
Adjacent(1),   // Show item info adjacent to the grid
None(2)        // No info display
```

### How items reference categories

Each item has a `Categories` field — a `String[]` array of category IDs:

```java
// Item.java line 99
new KeyedCodec<>("Categories", new ArrayCodec<>(Codec.STRING, String[]::new))
```

Documentation from the codec:
> "A list of categories this item will be shown in on the creative library menu."

The protocol packet `ItemBase` also carries `String[] categories` and `String set`.

---

## 2. Item "Set" Field

### How `Set` works

The `Set` field on an `Item` is a **grouping string** that associates related items:

```java
// Item.java line 173
.appendInherited(
    new KeyedCodec<>("Set", Codec.STRING),
    (item, s) -> item.set = s,
    item -> item.set,
    (item, parent) -> item.set = parent.set
)
```

- `Set` is inherited from parent items
- In protocol (`ItemBase`), it's sent as `public String set`
- The `BlockGroup` class uses sets for cycling between block variants in the structural crafting bench

### BlockGroup and Set cycling

`BlockGroup.findItemGroup(item)` returns the `BlockGroup` containing all items that share the same set. The `StructuralCraftingWindow` uses this for the block group cycling feature:

```java
// StructuralCraftingWindow.java line 187-213
BlockGroup set = BlockGroup.findItemGroup(item.getItem());
// ... cycles through set members when player presses up/down arrows
```

The set is a **flat grouping mechanism** — not hierarchical like categories. Example set values would be like `"Wood_Hardwood_Planks"` grouping all plank variants (oak, birch, etc.).

---

## 3. Creative Menu Search

The creative menu search is **entirely client-side**. The server sends all items via `ItemLibrary`, and the client performs local filtering. There is no server-side search API for the creative menu.

For a Custom UI page, search must be implemented server-side:
- Bind a `TextField` with `ValueChanged` event
- Filter items on the server using fuzzy matching (the engine uses `StringCompareUtil.getFuzzyDistance()` — see EntitySpawnPage)
- Send filtered results back via `UICommandBuilder`

---

## 4. Bench-Specific Recipe Organization

### BenchType enum

```java
// BenchType.java
Crafting(0),              // Standard crafting bench
Processing(1),            // Processing bench (furnace, etc.)
DiagramCrafting(2),       // Diagram-based crafting
StructuralCrafting(3)     // Structural crafting (builder's bench)
```

### BenchRequirement on CraftingRecipe

Every recipe has a `BenchRequirement[]` array:

```java
// BenchRequirement fields:
BenchType type;           // Which bench type (enum above)
String id;                // Bench identifier (e.g. "Builders", "Blueprint", "Fieldcraft")
String[] categories;      // Category tags for sorting within the bench
int requiredTierLevel;    // Minimum bench tier required
```

**How recipes are associated with benches:**

```java
// CraftingRecipe.java codec fields:
"Input"              → MaterialQuantity[]     // Required input materials
"Output"             → MaterialQuantity[]     // Additional outputs
"PrimaryOutput"      → MaterialQuantity       // Main output item
"OutputQuantity"     → int                    // Primary output quantity
"BenchRequirement"   → BenchRequirement[]     // Which bench(es) can craft this
"TimeSeconds"        → float                  // Crafting duration
"KnowledgeRequired"  → boolean                // Requires knowledge/discovery
"RequiredMemoriesLevel" → int                 // Memories level (min 1)
```

### How StructuralCraftingWindow organizes recipes

1. `CraftingPlugin.getBenchRecipes(benchType, benchId)` returns all recipes for a specific bench
2. `getMatchingRecipes(inputStack)` filters to recipes whose single input matches the item in the input slot
3. `sortRecipes()` sorts by:
   - **Header categories first** — recipes tagged with a `headerCategory` appear at the top
   - **Then by category index** — `bench.getCategoryIndex(category)` returns the sort position
   - **Then by recipe ID** (alphabetical)
4. A `dividerIndex` separates header-category recipes from the rest

### StructuralCraftingBench configuration

```java
// StructuralCraftingBench.java codec fields:
"Categories"              → String[]     // Ordered list of category names (defines sort order)
"HeaderCategories"        → String[]     // Categories that appear above the divider
"AlwaysShowInventoryHints" → boolean     // Show material availability hints
"AllowBlockGroupCycling"  → boolean      // Allow cycling through block group variants
```

The `categoryToIndexMap` maps category name → index for sort ordering.

### CraftingBench (non-structural) category system

Standard `CraftingBench` has a richer category model:

```java
// CraftingBench.BenchCategory fields:
"Id"              → String
"Name"            → String (localization key)
"Icon"            → String (icon asset path)
"ItemCategories"  → BenchItemCategory[] (optional sub-categories)

// CraftingBench.BenchItemCategory fields:
"Id"              → String
"Name"            → String
"Icon"            → String
"Diagram"         → String (UI diagram reference)
"Slots"           → int (default 1)
"SpecialSlot"     → boolean (default true)
```

### How CraftingWindow sends categories to the client

The `CraftingWindow` constructor builds a `windowData` JSON with categories:

```java
// CraftingWindow.java lines 40-75
JsonArray categories = new JsonArray();
for (BenchCategory benchCategory : craftingBench.getCategories()) {
    JsonObject category = new JsonObject();
    category.addProperty("id", benchCategory.getId());
    category.addProperty("name", benchCategory.getName());
    category.addProperty("icon", benchCategory.getIcon());
    
    Set<String> recipes = CraftingPlugin.getAvailableRecipesForCategory(
        this.bench.getId(), benchCategory.getId());
    if (recipes != null) {
        JsonArray recipesArray = new JsonArray();
        for (String recipeId : recipes) recipesArray.add(recipeId);
        category.add("craftableRecipes", recipesArray);
    }
    
    // Also includes itemCategories if present
    categories.add(category);
}
this.windowData.add("categories", categories);
```

### FieldCraftingWindow (pocket crafting) pattern

```java
// FieldCraftingWindow.java
this.windowData.addProperty("type", BenchType.Crafting.ordinal());
this.windowData.addProperty("id", "Fieldcraft");
this.windowData.addProperty("name", "server.ui.inventory.fieldcraft.title");
// Loads FieldcraftCategory assets from "Item/Category/Fieldcraft"
// Each category: { id, icon, name, craftableRecipes: [...recipeIds] }
```

---

## 5. Custom UI Tab Implementation

### SelectedTabChanged event binding

`CustomUIEventBindingType.SelectedTabChanged(23)` exists in the protocol enum ([CustomUIEventBindingType.java](.tmp_hytale_src/com/hypixel/hytale/protocol/packets/interface_/CustomUIEventBindingType.java#L29)), but **no engine pages use it**. All engine tab implementations use `Activating` events on `TextButton` elements instead.

### EntitySpawnPage tab pattern (the proven approach)

The `EntitySpawnPage` implements tabs as follows:

**1. Define tab button styles:**
```java
private static final Value<String> TAB_STYLE_ACTIVE =
    Value.ref("Common.ui", "DefaultTextButtonStyle");
private static final Value<String> TAB_STYLE_INACTIVE =
    Value.ref("Common.ui", "SecondaryTextButtonStyle");
```

**2. Bind Activating events to tab buttons:**
```java
// EntitySpawnPage.java lines 130-133
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Activating, "#TabNPC",
    new EventData().append("Type", "TabSwitch").append("Tab", "NPC"), false);
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Activating, "#TabItems",
    new EventData().append("Type", "TabSwitch").append("Tab", "Items"), false);
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Activating, "#TabModel",
    new EventData().append("Type", "TabSwitch").append("Tab", "Model"), false);
```

**3. Handle tab switch in event handler:**
```java
case "TabSwitch":
    if (data.tab != null && !data.tab.equals(this.activeTab)) {
        this.activeTab = data.tab;
        this.searchQuery = "";
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        this.updateTabVisibility(commandBuilder);
        commandBuilder.set("#SearchInput.Value", "");
        this.buildList(ref, store, commandBuilder, eventBuilder);
        this.sendUpdate(commandBuilder, eventBuilder, false);
    }
```

**4. Toggle visibility and active styling:**
```java
// EntitySpawnPage.java lines 362-376
private void updateTabVisibility(UICommandBuilder commandBuilder) {
    commandBuilder.set("#NPCContent.Visible", this.activeTab.equals("NPC"));
    commandBuilder.set("#ItemsContent.Visible", this.activeTab.equals("Items"));
    commandBuilder.set("#ModelContent.Visible", this.activeTab.equals("Model"));
    commandBuilder.set("#TabNPC.Style",
        this.activeTab.equals("NPC") ? TAB_STYLE_ACTIVE : TAB_STYLE_INACTIVE);
    commandBuilder.set("#TabItems.Style",
        this.activeTab.equals("Items") ? TAB_STYLE_ACTIVE : TAB_STYLE_INACTIVE);
    commandBuilder.set("#TabModel.Style",
        this.activeTab.equals("Model") ? TAB_STYLE_ACTIVE : TAB_STYLE_INACTIVE);
}
```

**5. Search implementation:**
```java
// EntitySpawnPage.java — search with fuzzy matching
eventBuilder.addEventBinding(
    CustomUIEventBindingType.ValueChanged, "#SearchInput",
    EventData.of("@SearchQuery", "#SearchInput.Value"), false);

// In handler:
if (data.searchQuery != null) {
    this.searchQuery = data.searchQuery.trim().toLowerCase();
    // Rebuild filtered list
}

// Fuzzy matching:
int fuzzyDistance = StringCompareUtil.getFuzzyDistance(value, searchQuery, Locale.ENGLISH);
if (fuzzyDistance > 0) { map.put(value, fuzzyDistance); }
// Sort by fuzzy distance descending, limit results
```

---

## 6. Category Filter Buttons

Category filter buttons within a tab are implemented as **TextButtons with toggled styles**. The pattern is:

1. In the `.ui` template, define a horizontal row of `TextButton` elements
2. Bind `Activating` events with category ID in the `EventData`
3. On activation, update the active category, change button styles (active/inactive), and rebuild the content list
4. Use `commandBuilder.set("#ButtonId.Style", activeStyle)` to toggle visual state

There is **no dedicated toggle button element** in Custom UI. Toggle state is managed server-side by tracking which category is active and swapping styles.

---

## 7. CraftingRecipe Complete Field Reference

### Server config (`CraftingRecipe.java`)

| JSON Field | Java Type | Purpose |
|------------|-----------|---------|
| `Input` | `MaterialQuantity[]` | Required input materials |
| `Output` | `MaterialQuantity[]` | Additional outputs (beyond primary) |
| `PrimaryOutput` | `MaterialQuantity` | Main output item |
| `OutputQuantity` | `int` | Quantity of primary output (default 1) |
| `BenchRequirement` | `BenchRequirement[]` | Which bench(es) can craft this recipe |
| `TimeSeconds` | `float` | Crafting duration (0 = instant) |
| `KnowledgeRequired` | `boolean` | Whether recipe requires discovery |
| `RequiredMemoriesLevel` | `int` | Minimum memories level (≥1) |

### BenchRequirement sub-object

| JSON Field | Java Type | Purpose |
|------------|-----------|---------|
| `Type` | `BenchType` | Enum: Crafting, Processing, DiagramCrafting, StructuralCrafting |
| `Id` | `String` | Bench identifier (e.g. "Builders", "Blueprint") |
| `Categories` | `String[]` | Category tags for sorting within the bench |
| `RequiredTierLevel` | `int` | Minimum bench upgrade tier |

### Protocol packet (`CraftingRecipe` protocol)

Same fields as above, serialized via the protocol system:
- `String id`, `MaterialQuantity[] inputs`, `MaterialQuantity[] outputs`
- `MaterialQuantity primaryOutput`, `BenchRequirement[] benchRequirement`
- `boolean knowledgeRequired`, `float timeSeconds`, `int requiredMemoriesLevel`

---

## 8. Item Asset Categorization Fields

### From `Item.java` codec

| JSON Field | Java Type | Purpose |
|------------|-----------|---------|
| `Categories` | `String[]` | Creative library category IDs this item appears in |
| `Set` | `String` | Group identifier for related items (e.g. "Wood_Hardwood_Planks") |
| `Icon` | `String` | Icon asset path (e.g. "Icons/ItemsGenerated/{assetId}.png") |
| `Quality` | `String` | Quality tier reference |
| `ItemLevel` | `int` | Item level |
| `Variant` | `boolean` | Whether this is a variant of a parent item |

### From `ItemBase` (protocol packet)

All the above plus many more rendering/interaction fields. Key categorization fields sent to client:
- `String set` — group identifier
- `String[] categories` — creative library category IDs
- `int[] tagIndexes` — tag indices for filtering/matching

---

## 9. Architecture Diagram: Creative Inventory Data Flow

```
Server                                    Client
──────                                    ──────
ItemCategory assets                  ──→  Tab definitions
  (Item/Category/CreativeLibrary/)        (top-level = tabs, children = sub-filters)

Item assets                          ──→  Item catalog
  (Item.categories[] field)               (filtered into categories by matching IDs)
  (Item.set field)                        (grouped by set for variant cycling)

ItemLibrary protocol packet          ──→  Full item list
  (ItemBase[] items)                      (client renders grid, applies category filter)
  (Map<Integer,String>[] blockMap)        (block ID → item ID mapping)
```

---

## 10. Replicating in Custom UI — Implementation Pattern

### Recommended approach

Since the creative menu is client-hardcoded, replicate it using `InteractiveCustomUIPage`:

```
[TabBar: TextButtons with Activating events]
[Optional: Sub-category filter buttons]
[TextField #SearchInput with ValueChanged event]
[TopScrolling > LeftCenterWrap #RecipeGrid > RecipeIconCell.ui template × N]
```

**Tab bar:** Row of TextButtons, one per category. Toggle `Style` between active/inactive.

**Search:** `TextField` with `ValueChanged` → server filters recipes → clears and repopulates grid.

**Icon grid:** `LeftCenterWrap` layout inside a `TopScrolling` parent. Each cell is an `ItemSlotButton` with an `ItemIcon` child (80×80 cells with 4px padding for 72×72 effective icon display, or 56×56 cells for standard inventory-size icons).

**Item display:** Use `commandBuilder.set("#RecipeGrid[i] #CellIcon.ItemId", itemId)` to set item icons, or `commandBuilder.set("#GridElement.Slots", itemGridSlots)` for `ItemGrid` elements.

### Data model for server-side tab/category state

```java
// Track active tab and filter state
private String activeTab = "All";
private String searchQuery = "";
private Map<String, List<RecipeInfo>> categoryToRecipes; // populated at page open

// On tab switch:
// 1. Update activeTab
// 2. Filter recipes by category
// 3. Clear and rebuild #RecipeGrid
// 4. Update tab button styles

// On search:
// 1. Update searchQuery
// 2. Filter current category's recipes by fuzzy match
// 3. Clear and rebuild #RecipeGrid
```

---

## See Also

- [ui-grid-layout-research.md](./ui-grid-layout-research.md) — Grid layout options (LeftCenterWrap, ItemGrid)
- [custom-ui-for-blueprint-bench.md](./custom-ui-for-blueprint-bench.md) — Blueprint bench custom UI design
- [api-reference-interactive-custom-ui.md](./api-reference-interactive-custom-ui.md) — Full Custom UI API reference
- [custom-ui-options.md](./custom-ui-options.md) — Custom UI page options and event types
