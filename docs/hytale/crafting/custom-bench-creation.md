---
topic: "Custom Workbench Block Creation"
category: "Crafting"
updated: 2026-04-22
sources: ["Bench_Builders.json", "Bench_Furniture.json", "StructuralCraftingWindow.java", "BenchWindow.java", "CraftingWindow.java", "BlockWindow.java", "OpenBenchPageInteraction.java", "CraftingManager.java", "CraftingPlugin.java", "WindowManager.java", "BlockType asset format", "CraftingRecipe asset format"]
---

# Custom Workbench Block Creation

## Summary

This document answers whether a NEW workbench block can be created by cloning the existing Builders Bench (`Bench_Builders`), what engine mechanisms handle bench registration, recipe assignment, interaction interception, and category merging. The analysis covers both **data-driven** (JSON asset) and **code-driven** (plugin API) aspects.

**Bottom line**: Creating a new bench block via JSON is **fully data-driven** — the engine auto-registers any new `Bench.Id` it encounters. Recipe assignment is also data-driven via `BenchRequirement.Id`. However, combining recipes from multiple existing benches into a single new bench **requires runtime recipe mutation** via plugin code, and intercepting bench behavior is well-supported via ECS events.

---

## 1. Creating a New Bench Block Asset

### Can we create a new item JSON with a different `Bench.Id`?

**YES — fully data-driven.** The engine discovers bench blocks by their JSON configuration. The `Bench_Builders.json` asset demonstrates the pattern:

```json
{
  "BlockType": {
    "Bench": {
      "Type": "StructuralCrafting",
      "Id": "Construction",
      "AllowBlockGroupCycling": true,
      "AlwaysShowInventoryHints": true,
      "HeaderCategories": ["WoodPlanks", "Bricks"],
      "Categories": ["WoodPlanks", "Bricks", "Stairs", "Door", "Chair"]
    },
    "BlockEntity": {
      "Components": {
        "BenchBlock": {}
      }
    }
  }
}
```

### Does the engine auto-register a new bench when it encounters a new `Bench.Id`?

**YES.** The `Bench.Id` is a **string identifier** — there is no enum or pre-registered list of valid bench IDs. The engine reads it as a string from the asset JSON and stores it on the `Bench` config object. When `CraftingPlugin` builds its recipe-to-bench index, it iterates all `CraftingRecipe` assets, reads each `BenchRequirement.Id`, and groups recipes by that string. Similarly, when `OpenBenchPageInteraction.interactWithBlock()` runs, it reads the bench config from the `BlockType` at the target coordinates — the bench ID comes directly from the asset.

**Evidence:** Known bench IDs include `"Builders"`, `"Furniture_Bench"`, `"Fieldcraft"`, `"Workbench"`, and various processing bench IDs — all are just strings, not enum values.

### Do we need to register the bench ID somewhere in code?

**NO.** Bench ID registration is purely data-driven. The only code-level enum is `BenchType` (see below), not `Bench.Id`.

### Can we use `Bench.Type: "StructuralCrafting"`?

**YES.** `BenchType` is an enum with four values:

| BenchType | Ordinal | Window Class | WindowType |
|-----------|---------|--------------|------------|
| `Crafting` | 0 | `SimpleCraftingWindow` | `BasicCrafting(2)` |
| `Processing` | 1 | Processing window | `Processing(5)` |
| `DiagramCrafting` | 2 | `DiagramCraftingWindow` | `DiagramCrafting(3)` |
| `StructuralCrafting` | 3 | `StructuralCraftingWindow` | `StructuralCrafting(4)` |

Using `"StructuralCrafting"` means the bench will open a `StructuralCraftingWindow` with the input-slot-to-variant-grid UI, including block group cycling. This is the same UI as the Builders Bench.

### Required JSON Structure for a New Bench Block

Based on `Bench_Builders.json`, the minimum viable bench block asset is:

```json
{
  "TranslationProperties": {
    "Name": "server.items.Bench_Construction.name",
    "Description": "server.items.Bench_Construction.description"
  },
  "MaxStack": 1,
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Model",
    "Opacity": "Transparent",
    "CustomModel": "Blocks/Benches/Builder.blockymodel",
    "CustomModelTexture": [
      { "Texture": "Blocks/Benches/Builder_Texture.png", "Weight": 1 }
    ],
    "HitboxType": "Bench_Architect",
    "VariantRotation": "NESW",
    "Bench": {
      "Type": "StructuralCrafting",
      "Id": "Construction",
      "AllowBlockGroupCycling": true,
      "AlwaysShowInventoryHints": true,
      "HeaderCategories": ["WoodPlanks"],
      "Categories": ["WoodPlanks", "Bricks", "Stairs"]
    },
    "BlockEntity": {
      "Components": {
        "BenchBlock": {}
      }
    },
    "Gathering": {
      "Breaking": { "GatherType": "Benches" }
    },
    "Support": {
      "Down": [{ "FaceType": "Full" }]
    }
  },
  "Tags": { "Type": ["Bench"] }
}
```

**Critical fields:**
- `BlockEntity.Components.BenchBlock: {}` — required for the block to have a `BenchState` in the world
- `Bench.Type` — determines which `Window` subclass is instantiated on interaction
- `Bench.Id` — the string that recipes reference via `BenchRequirement.Id`
- `Bench.Categories` — which category tabs appear in the bench UI

---

## 2. Recipe Assignment

### How do recipes get assigned to benches?

**Via the recipe's `BenchRequirement` array matching the bench's `Bench.Id` and `Bench.Type`.** Each recipe specifies which bench(es) it appears at:

```json
"BenchRequirement": [
  {
    "Id": "Builders",
    "Type": "StructuralCrafting",
    "Categories": ["WoodPlanks"]
  }
]
```

The engine's recipe validation (`CraftingManager.isValidBenchForRecipe()`) checks:

```java
for (BenchRequirement benchRequirement : requirements) {
    if (benchRequirement.type == benchType
        && benchName.equals(benchRequirement.id)
        && benchRequirement.requiredTierLevel <= benchTierLevel) {
        meetsRequirements = true;
        break;
    }
}
```

Both `BenchType` and `Bench.Id` must match exactly.

### If we create a bench with `Bench.Id: "Construction"`, do we need new recipes?

**YES, by default.** Existing recipes reference `"Id": "Builders"` — they won't match `"Id": "Construction"` without modification. You have three options:

#### Option A: Create duplicate recipes (data-driven, high effort)
Create new recipe JSONs identical to existing ones but with `BenchRequirement.Id: "Construction"`. This means maintaining two copies of every recipe.

#### Option B: Add `BenchRequirement` entries to existing recipes (data-driven, moderate effort)
Add a second entry to each recipe's `BenchRequirement` array:

```json
"BenchRequirement": [
  { "Id": "Builders", "Type": "StructuralCrafting", "Categories": ["WoodPlanks"] },
  { "Id": "Construction", "Type": "StructuralCrafting", "Categories": ["WoodPlanks"] }
]
```

#### Option C: Mutate recipes at runtime (code-driven, recommended)
In a `LoadAssetEvent` handler, iterate all recipes and add `BenchRequirement` entries for the new bench ID:

```java
// Pseudo-code — add "Construction" requirement to all recipes that have "Builders"
for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
    BenchRequirement[] reqs = recipe.getBenchRequirement();
    for (BenchRequirement req : reqs) {
        if ("Builders".equals(req.getId())) {
            // Add a new BenchRequirement with Id "Construction"
            // Requires reflection since BenchRequirement[] has no public setter
        }
    }
}
```

### Can a recipe have multiple `BenchRequirement` entries?

**YES.** The `BenchRequirement` field is an array. Evidence from `Bench_Builders.json`'s own recipe:

```json
"BenchRequirement": [
  { "Id": "Fieldcraft", "Type": "Crafting", "Categories": ["Tools"] },
  { "Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"] }
]
```

The engine checks ANY entry matches — it's an OR condition. A recipe with both `"Builders"` and `"Construction"` requirements will appear at BOTH benches.

### How to get BOTH Builders AND Furniture recipes at this new bench?

**Runtime mutation is required.** The Furniture Bench uses `BenchType: "Crafting"` and the Builders Bench uses `BenchType: "StructuralCrafting"`. These are different `BenchType` enums — the validation checks both `type` AND `id`.

**BLOCKER: BenchType mismatch.** If the new bench is `StructuralCrafting`, Furniture recipes (which specify `"Type": "Crafting"`) will NOT pass `isValidBenchForRecipe()` because `BenchRequirement.type != benchType`. Options:

1. **Make the new bench `Crafting` type** — gives `SimpleCraftingWindow` (flat recipe list), but loses the structural crafting grid UI. Furniture recipes work, Builders recipes do not (they're `StructuralCrafting` type).

2. **Mutate Furniture recipes to add a `StructuralCrafting` requirement** — add a new `BenchRequirement` entry with `"Type": "StructuralCrafting", "Id": "Construction"`. This bypasses the type mismatch because the recipe now has a matching entry. **Requires testing** whether Furniture recipes (which use `Crafting`-style flat recipe lists) render correctly in a `StructuralCrafting` window UI.

3. **Bypass `isValidBenchForRecipe()` entirely** — intercept `CraftRecipeEvent.Pre` or replicate the crafting logic manually using the public static helpers (`CraftingManager.getInputMaterials()`, `CraftingManager.getOutputItemStacks()`, `SimpleItemContainer.addOrDropItemStacks()`). This is the approach documented in [craftitem-bypass-analysis.md](./craftitem-bypass-analysis.md).

4. **Use two windows** — open BOTH a `StructuralCraftingWindow` (for Builders recipes) and a separate window (for Furniture recipes) on the same `Page.Bench`. The `PageManager.setPageWithWindows()` method accepts variadic `Window...` parameters. **Unknown if the client supports two crafting windows simultaneously.**

---

## 3. Intercepting Bench Behavior

### What happens when a player right-clicks a StructuralCrafting bench?

The interaction chain is:

```
Player right-clicks bench block
    │
    ▼
Client resolves BlockType.Interactions.Secondary → RootInteraction
    │
    ▼
InteractionManager processes the chain
    │
    ▼
OpenBenchPageInteraction.interactWithBlock() runs:
    1. Gets CraftingManager component
    2. Checks no bench already set: craftingManager.hasBenchSet()
    3. Reads BenchState: world.getState(x, y, z, true)
    4. Creates window based on pageType:
       STRUCTURAL_CRAFTING → new StructuralCraftingWindow(benchState)
    5. Registers multi-user tracking: benchState.getWindows().putIfAbsent(uuid, window)
    6. Opens: pageManager.setPageWithWindows(ref, store, Page.Bench, true, window)
```

The engine auto-opens a `StructuralCraftingWindow` for `StructuralCrafting` benches. There is no intermediate event between "player interacts with bench block" and "window opens" — the `OpenBenchPageInteraction` handles it in one step.

### Can a plugin intercept the bench open event?

**YES, via `UseBlockEvent.Pre`.** This is a cancellable ECS event that fires BEFORE the block interaction completes:

```java
public class BenchInterceptSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    public BenchInterceptSystem() { super(UseBlockEvent.Pre.class); }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> cmd,
                       UseBlockEvent.Pre event) {
        // Check if target block is our custom bench
        // Cancel the default interaction
        event.cancel();
        // Open a custom window instead
    }
}
```

**Note:** There is no dedicated `BenchOpenEvent`. The `UseBlockEvent.Pre`/`Post` pair is the hook point.

### Can we intercept `CraftRecipeEvent.Pre`?

**YES.** `CraftRecipeEvent.Pre` is a `CancellableEcsEvent` that fires inside `CraftingManager.craftItem()`:

```java
CraftRecipeEvent.Pre preEvent = new CraftRecipeEvent.Pre(recipe, quantity);
componentAccessor.invoke(ref, preEvent);
if (preEvent.isCancelled()) return false;
```

A plugin can register an `EntityEventSystem<EntityStore, CraftRecipeEvent.Pre>` to:
- Inspect the recipe being crafted
- Cancel crafting based on custom conditions
- Read the player's current window state

### Can we detect what item is in the input slot?

**YES, but indirectly.** The `CraftRecipeEvent.Pre` event contains the `CraftingRecipe` being crafted, but not the input slot contents directly. However:

1. From the event handler, you can access the player's `WindowManager`
2. The current `Window` can be retrieved
3. If it's a `StructuralCraftingWindow`, its `inputContainer` is accessible
4. Alternatively, the recipe itself tells you what input is needed (via `recipe.getInput()`)

### Can we prevent crafting when `Block_Placeholder` is in the input slot?

**YES.** In a `CraftRecipeEvent.Pre` handler:

```java
// Check if the recipe input involves a placeholder
MaterialQuantity[] inputs = event.getCraftedRecipe().getInput();
for (MaterialQuantity input : inputs) {
    if ("Block_Placeholder".equals(input.getItemId())) {
        event.cancel();
        return;
    }
}
```

Or more directly, check the window's input container for a placeholder item.

---

## 4. StructuralCrafting Window Customization

### Can we create a custom window that uses `StructuralCraftingWindow` behavior?

**YES, with caveats.** There are two approaches:

#### Approach A: WITH a physical bench block (recommended for this use case)

If the new bench block exists in the world (which is the scenario being researched), the standard `StructuralCraftingWindow(benchState)` constructor works normally. The `OpenBenchPageInteraction` will automatically create one when the player interacts with the bench.

**This avoids the crash documented in `structural-window-crash-analysis.md`.** That crash was caused by sending `WindowType.StructuralCrafting` WITHOUT `InventorySection` and `ExtraResources`. With a real bench block:
- `BenchState` is available from the world
- `StructuralCraftingWindow` extends `BlockWindow` → `BenchWindow` → has all dependencies
- `InventorySection` (65 slots) is sent via `ItemContainerWindow` interface
- `ExtraResources` (nearby chests) is sent via `MaterialContainerWindow` interface
- **No crash — this is the normal engine flow**

#### Approach B: Custom `Window` subclass with `WindowType.StructuralCrafting`

Possible but complex — essentially reimplementing `StructuralCraftingWindow` without extending `BenchWindow`. See [portable-bench-feasibility.md](./portable-bench-feasibility.md) Section 9, Option B.

### Can we add custom behavior to `handleAction()`?

**Not directly on the engine's `StructuralCraftingWindow`** — the class is not designed for extension by plugins. However:

1. **Intercept via events**: `CraftRecipeEvent.Pre` fires for every craft action, allowing cancellation
2. **Replace the window**: Use `UseBlockEvent.Pre` to cancel the default bench interaction, then open your own custom `Window` subclass that handles `SelectSlotAction`, `CraftRecipeAction`, and `ChangeBlockAction` with custom logic
3. **Post-process**: `CraftRecipeEvent.Post` fires after crafting, allowing additional side effects

### How does `AllowBlockGroupCycling` work?

When enabled, the StructuralCrafting UI adds cycling arrows that let the player switch between resource type variants. For example, if the player puts "Oak Planks" in the input slot, cycling shows "Birch Planks", "Darkwood Planks", etc.

The server handles this via `ChangeBlockAction`:

```java
case ChangeBlockAction changeBlockAction:
    this.changeBlockType(ref, changeBlockAction.down, store);
    break;
```

`changeBlockType()` cycles through items in the same `ResourceType` group and updates the input slot, which triggers `updateRecipes()` to refresh the option grid.

This is controlled by the `AllowBlockGroupCycling` field in the bench JSON. It does NOT affect placeholder behavior — it only controls whether the cycling UI arrows appear.

---

## 5. Bench Categories — Combining Builders + Furniture

### Category format differs between bench types

**Builders Bench** (`StructuralCrafting`) uses string arrays:
```json
"Categories": ["WoodPlanks", "Bricks", "Stairs", "Door", "Chair"]
```

**Furniture Bench** (`Crafting`) uses object arrays with display info:
```json
"Categories": [
  { "Id": "Furniture_Storage", "Icon": "Icons/.../Storage.png", "Name": "server.benchCategories.furniture.storage" },
  { "Id": "Furniture_Beds", "Icon": "Icons/.../Beds.png", "Name": "server.benchCategories.furniture.beds" }
]
```

The engine deserializes both into `CraftingBench.BenchCategory` objects. The `StructuralCrafting` format uses the category ID as the display name; the `Crafting` format provides explicit icons and translation keys.

### Can a single bench include categories from both?

**YES, in the JSON definition.** You can include both Builders-style and Furniture-style category IDs in the `Categories` array of a single bench:

```json
"Categories": [
  "WoodPlanks", "Bricks", "Stairs",
  { "Id": "Furniture_Beds", "Icon": "Icons/.../Beds.png", "Name": "server.benchCategories.furniture.beds" }
]
```

**HOWEVER, this only controls which UI TABS appear.** The recipes that populate each tab are determined by whether recipes have a matching `BenchRequirement` for this bench's `Id` + `Type`. See Section 2 above.

### What determines which recipes appear in which category?

The `CraftingWindow` constructor builds the category tab list:

```java
for (CraftingBench.BenchCategory benchCategory : craftingBench.getCategories()) {
    Set<String> recipes = CraftingPlugin.getAvailableRecipesForCategory(bench.getId(), benchCategory.getId());
    // ... adds to categories JSON
}
```

`CraftingPlugin.getAvailableRecipesForCategory(benchId, categoryId)` looks up recipes where:
1. The recipe's `BenchRequirement` has a matching `Id` (bench ID)
2. The matched `BenchRequirement` entry has the category in its `Categories` array

**For StructuralCraftingWindow specifically**, categories are NOT displayed as tabs — instead, recipes are resolved dynamically based on the input item. The `Categories` array on a `StructuralCrafting` bench controls which recipe categories are *eligible*, and the `HeaderCategories` array controls visual grouping of the options.

### BLOCKER: BenchType mismatch for combined categories

If the new bench is `StructuralCrafting`, Furniture recipes (which are `Crafting` type) won't pass validation. See Section 2 for workarounds.

If the new bench is `Crafting` (to accommodate Furniture recipes), the UI becomes a `SimpleCraftingWindow` (flat recipe list), losing the structural crafting grid entirely.

**There is no native engine support for a bench that uses multiple `BenchType` renderers simultaneously.**

---

## 6. Nearby Chest Access from a Bench

### Does chest scanning work automatically for any bench?

**YES.** `CraftingManager.getContainersAroundBench()` uses a spatial index (`KDTree`) over all `ItemContainerState` blocks. Any block with `BlockEntity.Components.BenchBlock: {}` that creates a `BenchState` will trigger chest scanning when its `BenchWindow.onOpen0()` runs.

The flow:

```
BenchWindow.onOpen0()
    → CraftingManager.setBench(x, y, z, blockType)
    → CraftingManager.feedExtraResourcesSection(extraResourcesSection)
        → getContainersAroundBench(benchState)
            → SpatialResource<ItemContainerState>.ordered3DAxis(pos, hRadius, vRadius)
            → Returns nearby chests within radius
        → Wraps each chest's ItemContainer in DelegateItemContainer(ALLOW_OUTPUT_ONLY)
        → Combines into CombinedItemContainer
        → Populates ExtraResources for the OpenWindow packet
```

### Is the chest radius controlled by the bench asset or code?

**Code-controlled, globally.** The radius comes from `CraftingConfig`:

| Config | Default | Source |
|--------|---------|--------|
| `chestHorizontalRadius` | 4 blocks | `CraftingConfig.getBenchMaterialChestHorizontalRadius()` |
| `chestVerticalRadius` | 2 blocks | `CraftingConfig.getBenchMaterialChestVerticalRadius()` |
| `maxChestCount` | 8 chests | `CraftingConfig.getBenchMaterialChestLimit()` |

These are **server-wide settings**, not per-bench. All benches use the same radius.

### Can the PlaceBlock tool reuse chest-scanning from the new bench?

**YES.** The `CraftingManager.getContainersAroundBench()` method is accessible and can be called from plugin code if you have a `BenchState` reference. Alternatively, you can directly query the spatial index:

```java
SpatialResource<Ref<ChunkStore>, ChunkStore> spatial =
    store.getResource(BlockStateModule.get().getItemContainerSpatialResourceType());
ObjectList<Ref<ChunkStore>> results = SpatialResource.getThreadLocalReferenceList();
spatial.getSpatialStructure().ordered3DAxis(benchPos, hRadius, vRadius, hRadius, results);
```

The PlaceBlock tool can use the same mechanism when the player is standing at the new bench — query for nearby chests, aggregate their inventories, and check material availability.

---

## 7. Complete Approach Summary

### Approach: New Bench Block with Custom Interaction

| Step | Method | Difficulty |
|------|--------|------------|
| **Create bench block asset** | New JSON file with `Bench.Id: "Construction"`, `Bench.Type: "StructuralCrafting"` | Easy — data-driven |
| **Assign Builders recipes** | Runtime mutation: add `BenchRequirement { Id: "Construction", Type: "StructuralCrafting" }` to all Builders recipes | Medium — reflection required |
| **Assign Furniture recipes** | Runtime mutation: add `BenchRequirement { Id: "Construction", Type: "StructuralCrafting" }` to all Furniture recipes | Medium — reflection + BenchType mismatch concern |
| **Intercept bench open** | `UseBlockEvent.Pre` handler to cancel default and open custom window | Medium |
| **Block group cycling** | Set `AllowBlockGroupCycling: true` in bench JSON | Easy — data-driven |
| **Nearby chest access** | Automatic — `BenchBlock: {}` enables it | Free |
| **Custom crafting logic** | `CraftRecipeEvent.Pre` handler or bypass via public static helpers | Medium |

### Key Blockers

| # | Blocker | Severity | Workaround |
|---|---------|----------|------------|
| 1 | **BenchType mismatch** — Furniture recipes are `Crafting` type, Builders recipes are `StructuralCrafting` type. A single bench can only be one `BenchType`. | **HIGH** | Mutate recipe `BenchRequirement` arrays at runtime to add entries matching the new bench's type+ID. The `isValidBenchForRecipe()` check only needs ONE matching entry in the array. |
| 2 | **StructuralCrafting UI incompatibility with Crafting recipes** — `StructuralCraftingWindow` resolves recipes based on input-slot items (1:N mapping). Furniture recipes use different patterns (multi-ingredient, non-block outputs). They may not render correctly in the structural UI. | **HIGH** | Use `UseBlockEvent.Pre` to intercept and open a custom window with tabs — one tab for structural crafting (Builders), another for flat recipe list (Furniture). Alternatively, open two windows on the same page. |
| 3 | **Recipe `BenchRequirement` has no public setter** — mutating the array requires reflection on the `benchRequirement` field. | **LOW** | Standard reflection pattern, same as recipe input scaling. Already demonstrated in the codebase. |
| 4 | **No per-bench chest radius** — all benches share the same global radius. | **NONE** | Acceptable for this use case. |
| 5 | **Category format inconsistency** — Builders uses string categories, Furniture uses object categories. Merging them into one bench requires normalizing the format. | **LOW** | When building the JSON `Categories` array for the new bench, use the object format for all entries (both Builders and Furniture categories). |

### Recommended Architecture

```
┌─────────────────────────────────────────────────────┐
│             Bench_Construction.json                  │
│  Bench.Type: "StructuralCrafting"                   │
│  Bench.Id: "Construction"                           │
│  Categories: [Builders + Furniture categories]       │
│  BlockEntity.Components.BenchBlock: {}              │
└────────────────────┬────────────────────────────────┘
                     │ Player right-clicks
                     ▼
┌─────────────────────────────────────────────────────┐
│         UseBlockEvent.Pre handler                    │
│  1. Cancel default OpenBenchPageInteraction          │
│  2. Read BenchState from world                      │
│  3. Open custom window(s) via PageManager           │
└────────────────────┬────────────────────────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
┌──────────────────┐  ┌──────────────────────────┐
│ StructuralCraft  │  │  PocketCrafting Window    │
│ Window           │  │  (Furniture recipes)      │
│ (Builders recs)  │  │  Or: tabs within a single │
│                  │  │  custom window             │
└──────────────────┘  └──────────────────────────┘
          │                     │
          └──────────┬──────────┘
                     ▼
┌─────────────────────────────────────────────────────┐
│      CraftRecipeEvent.Pre handler                    │
│  - Block placeholder crafting prevention             │
│  - Custom validation logic                          │
└─────────────────────────────────────────────────────┘
```

### Alternative: Keep It Simple

If the goal is just to have a single bench where players access both building blocks AND furniture:

1. **Create the bench block** with `Bench.Type: "Crafting"` and `Bench.Id: "Construction"`
2. **At `LoadAssetEvent`**, mutate ALL Builders + Furniture recipes to add `BenchRequirement { Id: "Construction", Type: "Crafting" }`
3. **Result**: A `SimpleCraftingWindow` (flat recipe list with category tabs) showing all recipes from both benches
4. **Trade-off**: Loses the structural crafting grid (input → variant), block group cycling, and inventory hints. But works with zero custom window code.

---

## 8. Gotchas

- **Asset inheritance**: If the new bench block uses `"Parent": "Bench_Builders"`, the `Bench` config is inherited as a shared Java object. Mutating it will affect the parent. Always ensure the new block's JSON explicitly defines its own `Bench` section.
- **Translation keys**: The bench window title uses `item.getTranslationKey()` from the block type's parent item. New items need translation entries.
- **Model/texture**: Can reuse the Builders Bench model (`Blocks/Benches/Builder.blockymodel`) initially, but a unique model avoids player confusion.
- **Tier levels**: `BenchState.getTierLevel()` is read from the world block state. Recipes with `requiredTierLevel > 0` will fail if the bench hasn't been upgraded. Ensure the new bench supports the same tier upgrade mechanism.
- **Multi-user**: `BenchState.getWindows()` tracks which players have the bench open. This works automatically for standard `StructuralCraftingWindow`. Custom windows need to register with `benchState.getWindows().putIfAbsent(uuid, window)` and clean up on close.

## See Also

- [Bench Types](./bench-types.md)
- [Crafting Recipes](./recipes.md)
- [Crafting Window Architecture](./crafting-window-architecture.md)
- [StructuralCrafting Window Crash Analysis](./structural-window-crash-analysis.md)
- [Portable Bench Feasibility](./portable-bench-feasibility.md)
- [CraftItem Bypass Analysis](./craftitem-bypass-analysis.md)
- [Builder Mode Recipe Filtering](./builder-mode-recipe-filtering.md)
