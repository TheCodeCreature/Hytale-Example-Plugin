# Design: Parameterized Bench Recipe Registry

## 1. Overview

The current `BlockRecipeRegistry` filters recipes by `BenchType.StructuralCrafting`, which inadvertently excludes all Furniture Bench recipes (since the furniture bench declares `BenchType.Crafting` with `id="Furniture_Bench"`). This design replaces the single static registry with a **parameterized `BenchRecipeRegistry`** that filters by `BenchRequirement.id` instead of `BenchType`, and a **`BenchRecipeRegistries` coordinator** that manages one instance per configured bench and provides aggregate cross-bench queries.

The core principle: **one registry instance per workbench, same code, filtered by bench ID**.

## 2. Design Priorities

1. **Simplicity** — shared code via a single parameterized class; no per-bench subclasses
2. **Framework-native patterns** — matches the asset data model (`BenchRequirement.id` is the actual unique identifier)
3. **Extensibility** — adding a new bench is a one-line config change; per-bench behavior can be split later
4. **Testability** — instance-based registry is easier to test in isolation than static state

## 3. Component Diagram

```mermaid
classDiagram
    class BenchRecipeRegistry {
        -String benchId
        -Map~String_CraftingRecipe~ recipesByBlockType
        -Map~String_CraftingRecipe~ recipesById
        -Set~String~ baseBlockRecipeIds
        +BenchRecipeRegistry(String benchId)
        +init() void
        +getRecipeForBlock(String blockTypeId) CraftingRecipe
        +hasRecipe(String blockTypeId) boolean
        +isBaseBlockRecipe(String recipeId) boolean
        +isBaseBlockType(String blockTypeId) boolean
        +getAllRecipesById() Map
        +getAllRecipesByBlockType() Map
        +getBenchId() String
        +resolveInputItemId(MaterialQuantity input)$ String
    }

    class BenchRecipeRegistries {
        -Map~String_BenchRecipeRegistry~ registries$
        +init(String[] benchIds)$ void
        +getRegistry(String benchId)$ BenchRecipeRegistry
        +hasRecipeAnywhere(String blockTypeId)$ boolean
        +getRecipeForBlock(String blockTypeId)$ CraftingRecipe
        +getAllRegistries()$ Collection~BenchRecipeRegistry~
    }

    class DropScaler {
        +apply()$ void
        -applyModifications()$ void
        -scaleCraftingCosts(AssetFieldAccessor f, int multiplier)$ int
        -processRecipeBlock(BlockType bt, String btId, AssetFieldAccessor f)$ boolean
    }

    class NaturalResourceRegistry {
        +init()$ void
        +isNaturalBlock(String blockTypeId)$ boolean
    }

    class RecipeDropListener {
        +onBlockBreak(BreakBlockEvent event, Store store)$ void
    }

    BenchRecipeRegistries "1" *-- "many" BenchRecipeRegistry : manages
    DropScaler ..> BenchRecipeRegistries : queries all registries
    DropScaler ..> NaturalResourceRegistry : queries natural blocks
    NaturalResourceRegistry ..> BenchRecipeRegistries : checks hasRecipeAnywhere
    RecipeDropListener ..> BenchRecipeRegistries : looks up recipe for broken block
```

## 4. Responsibility Map

```mermaid
graph TB
    subgraph Init["Initialization (LoadAssetEvent)"]
        DS[DropScaler.apply]
        NRR[NaturalResourceRegistry.init]
        BRRs[BenchRecipeRegistries.init]
        DS -->|"1. init registries"| NRR
        DS -->|"2. init per benchId"| BRRs
        BRRs -->|creates| BR1["BenchRecipeRegistry(Builders)"]
        BRRs -->|creates| BR2["BenchRecipeRegistry(Furniture_Bench)"]
    end

    subgraph Pipeline["DropScaler Pipeline"]
        P1["Phase 1: Scale crafting costs"]
        P2["Phase 2: Collect ingredient IDs"]
        P4["Phase 4: Single pass over BlockTypes"]
        P4a["Phase 4a: Natural block processing"]
        P4b["Phase 4b: Recipe block processing"]
        P5["Phase 5: Register synthetic drop lists"]
        P6["Phase 6: Scale stack sizes"]
        DS -->|"3. applyModifications"| P1
        P1 --> P2
        P2 --> P4
        P4 -->|"isNatural && !hasRecipeAnywhere"| P4a
        P4 -->|"hasRecipeAnywhere && !baseBlock"| P4b
        P4 --> P5
        P5 --> P6
    end

    subgraph Runtime["Runtime (BreakBlockEvent)"]
        BBE[BreakBlockEvent]
        RDL[RecipeDropListener]
        BRRS[BenchRecipeRegistries.getRecipeForBlock]
        BBE --> RDL
        RDL -->|"lookup across all benches"| BRRS
    end
```

## 5. Sequence Diagram

```mermaid
sequenceDiagram
    participant Plugin as UnobstructedThirdPersonPlugin
    participant DS as DropScaler
    participant NRR as NaturalResourceRegistry
    participant BRRs as BenchRecipeRegistries
    participant BR1 as BenchRecipeRegistry[Builders]
    participant BR2 as BenchRecipeRegistry[Furniture_Bench]

    Plugin->>DS: apply()
    DS->>NRR: init()
    DS->>BRRs: init("Builders", "Furniture_Bench")
    BRRs->>BR1: new BenchRecipeRegistry("Builders")
    BR1->>BR1: init() — scan recipes, filter by benchId
    BRRs->>BR2: new BenchRecipeRegistry("Furniture_Bench")
    BR2->>BR2: init() — scan recipes, filter by benchId
    DS->>DS: applyModifications()
    Note over DS: Phase 1 — scale crafting costs from ALL registries
    Note over DS: Phase 4 — single pass over BlockTypes
    DS->>BRRs: hasRecipeAnywhere(blockTypeId)?
    BRRs->>BR1: hasRecipe(blockTypeId)?
    BRRs->>BR2: hasRecipe(blockTypeId)?
    BRRs-->>DS: true/false
```

## 6. Package Structure

```
src/main/java/com/UnobstructedThirdPerson/resourcecollection/
├── BenchRecipeRegistry.java          ← NEW (replaces BlockRecipeRegistry)
├── BenchRecipeRegistries.java        ← NEW (static coordinator)
├── AssetFieldAccessor.java           (unchanged)
├── BreakBlockRecipeSystem.java       (unchanged)
├── DropScaler.java                   (modified — uses BenchRecipeRegistries)
├── NaturalResourceRegistry.java      (modified — uses BenchRecipeRegistries)
├── RecipeDropListener.java           (modified — uses BenchRecipeRegistries)
├── ResourceConstants.java            (unchanged)
├── BlockRecipeRegistry.java          ← DEPRECATED (thin facade, then delete)
├── CraftingCostModifier.java         ← DEAD CODE (already unused)
├── RecipeDropModifier.java           ← DEAD CODE (already unused)
├── NaturalDropModifier.java          ← DEAD CODE (already unused)
├── IngredientDropModifier.java       ← DEAD CODE (already unused)
├── PlacedBlockDropModifier.java      ← DEAD CODE (already unused)
├── NaturalStackSizeModifier.java     ← DEAD CODE (already unused)
└── BreakBlockNaturalSystem.java      ← DEAD CODE (already unused)
```

## 7. Integration Changes Required

### `DropScaler.java`
- Replace `BlockRecipeRegistry.init()` with `BenchRecipeRegistries.init("Builders", "Furniture_Bench")`
- Replace all `BlockRecipeRegistry.*` calls with `BenchRecipeRegistries.*` equivalents
- Phase 1 (`scaleCraftingCosts`): iterate `BenchRecipeRegistries.getAllRegistries()` instead of `BlockRecipeRegistry.getAllRecipesById()`
- Phase 4: use `BenchRecipeRegistries.hasRecipeAnywhere(btId)` and `BenchRecipeRegistries.isBaseBlockTypeAnywhere(btId)`
- `processRecipeBlock`: use `BenchRecipeRegistries.getRecipeForBlock(btId)` which searches all benches
- `collectIngredientItemIds`: iterate all registries

### `NaturalResourceRegistry.java`
- Replace `isCraftingBench(recipe)` filter with a bench-ID-based check using `BenchRecipeRegistries`, OR keep the self-contained filter but switch to checking `BenchRequirement.id` against the configured bench IDs
- Simplest: use `BenchRecipeRegistries.hasRecipeAnywhere(blockTypeId)` in the natural-vs-crafted classification

### `RecipeDropListener.java`
- Replace `BlockRecipeRegistry.getRecipeForBlock(blockTypeId)` with `BenchRecipeRegistries.getRecipeForBlock(blockTypeId)`
- Remove the duplicated `resolveByBlockGroup()` method; use `BenchRecipeRegistry.resolveInputItemId()` instead

### `BlockRecipeRegistry.java`
- Can be deleted once all callsites are migrated. Alternatively, convert to a thin facade that delegates to `BenchRecipeRegistries` for backward compatibility during migration.

### `AssetTestHelper.java` (test)
- Add overloaded `recipe()` method that accepts a `String benchId` parameter to construct `BenchRequirement` with the ID field set
- Update existing test data sets to use bench IDs

## 8. Open Questions

1. **Are there bench IDs beyond `"Builders"` and `"Furniture_Bench"` that should be included?** The `Workbench` and `Fieldcraft` benches produce items too (e.g., tools, workbenches themselves). If those items have `blockId` set, they'd need registries too. For now the design only covers the two requested benches, but adding more is trivial.

2. **Should `BenchRecipeRegistries.getRecipeForBlock()` have a priority order when multiple benches produce the same block?** Currently `BlockRecipeRegistry` uses `putIfAbsent`, so first-seen wins. The coordinator should define whether bench order matters. Recommendation: iterate in the order benches were registered; first match wins.

3. **`NaturalResourceRegistry` initialization order** — Currently `NaturalResourceRegistry.init()` runs before `BlockRecipeRegistry.init()` and has its own `isCraftingBench()` filter. After this change, it should run *after* `BenchRecipeRegistries.init()` so it can use `hasRecipeAnywhere()` instead of duplicating the bench filter logic. `DropScaler.apply()` already controls this order.
