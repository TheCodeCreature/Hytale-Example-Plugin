# Design: SOLID-Based Package Restructuring

## 1. Overview

Restructure `com.CodeCreature` from its current flat/mixed layout into a **layered architecture** where each package represents a single responsibility tier. The dependency flow runs strictly bottom-up: **resource scaling → recipe registry → crafting services → stencil mechanics → UI**. A developer can trace "how does this resource cost get enforced?" by reading packages from bottom to top.

---

## 2. Design Priorities

1. **Traceability** — the folder tree itself tells the story: resources scale → registries index → services resolve → stencils enforce → UI displays
2. **Single Responsibility** — each package owns one concern; no package mixes infrastructure with UI or scaling with registry
3. **Dependency Direction** — strict bottom-up; lower layers never import upper layers
4. **Minimal Rename Churn** — files keep their names unless the name becomes genuinely redundant in context
5. **Testability** — test tree mirrors source tree 1:1

---

## 3. Proposed Package Structure

```
com/
├── Plugin.java                          ── entry point: registers all systems/interactions
│
└── CodeCreature/
    ├── scaling/                          ── LAYER 0: Resource economy foundation
    │   ├── ResourceConstants.java        ── static: RESOURCE_MULTIPLIER = 12
    │   ├── DropScaler.java               ── single-pass asset modifier: scale all costs
    │   ├── AssetFieldAccessor.java       ── reflection helper for asset field access
    │   ├── BenchCategory.java            ── enum: BUILDERS_ONLY, FURNITURE_ONLY, BOTH
    │   ├── BenchBlockClassifier.java     ── classifies blocks into BenchCategory
    │   ├── BenchCategoryProcessor.java   ── interface for bench-category processing
    │   ├── AbstractBenchProcessor.java   ── base class for bench processors
    │   ├── BuildersProcessor.java        ── Builders bench drop processor
    │   ├── FurnitureProcessor.java       ── Furniture bench drop processor
    │   ├── OverlapProcessor.java         ── blocks-in-both-benches processor
    │   ├── NaturalResourceRegistry.java  ── static registry: natural block types
    │   ├── ResourceTypeResolver.java     ── resolves ResourceTypeId → concrete item ID
    │   ├── PlacementCostScaler.java      ── ECS system: enforce 12x placement cost
    │   └── BreakBlockDiagnostic.java     ── ECS system: debug block-drop logging
    │
    ├── registry/                         ── LAYER 1: Recipe indexing & filtered views
    │   ├── BenchRecipeRegistries.java     ── aggregate coordinator for all bench registries
    │   ├── BenchRecipeRegistry.java       ── per-bench recipe registry
    │   ├── RecipeFilterRegistry.java      ── shared read-only registry of filtered recipes
    │   └── FilteredRecipeEntry.java       ── immutable record: validated recipe entry
    │
    ├── crafting/                          ── LAYER 2: Cost resolution & recipe mutation
    │   ├── PlaceBlockCostUtil.java        ── per-unit placement cost: input / outputQty
    │   ├── RecipeAffordabilityResolver.java ── full resolution chain: cost → resolve → check
    │   ├── ResolvedIngredient.java        ── immutable record: resolved ingredient status
    │   ├── BlueprintBookRecipeMutator.java ── creates shadow Blueprint_ recipes at load
    │   ├── ResourceScanner.java           ── (stub) scan inventory + nearby chests
    │   └── ResourceSnapshot.java          ── immutable record: item counts + sources
    │
    ├── stencil/                          ── LAYER 3: Stencil item mechanics & ECS systems
    │   ├── StencilMetadata.java           ── BSON metadata utility for stencil items
    │   ├── StencilPlacementSystem.java    ── ECS: intercept placement → consume materials
    │   ├── StencilSyncSystem.java         ── ECS: restore stencil qty after engine consumption
    │   ├── StencilDropDestroySystem.java  ── ECS: prevent stencil world item spawn
    │   └── StencilVisualManager.java      ── per-player visual affordability overrides
    │
    ├── ui/                               ── LAYER 4: All user-facing UI
    │   ├── bench/                         ── Blueprint Bench UI
    │   │   ├── BlueprintSelectionPage.java    ── main bench UI: grid, filters, detail, tree
    │   │   ├── BlueprintBookOpenUIInteraction.java ── interaction: opens bench page
    │   │   ├── BlueprintBookPrefs.java       ── serializable bench UI preferences
    │   │   ├── BlueprintBookPrefsStore.java  ── file-based per-player prefs persistence
    │   │   ├── AffordabilityMode.java         ── enum: ALL, INVENTORY, RESOURCE
    │   │   ├── RecipeFilterPipeline.java      ── sequential filter pipeline for display
    │   │   └── ResourceTypeRegistry.java      ── static registry: 78 resource type filters
    │   │
    │   ├── ingredienttree/               ── Ingredient filter tree (sub-component of bench UI)
    │   │   ├── IngredientTree.java            ── tree data structure
    │   │   ├── IngredientTreeBuilder.java     ── builds tree from recipe assets
    │   │   ├── IngredientTreeGridController.java ── UI controller for tree grid
    │   │   ├── IngredientSelectionModel.java  ── selection/check state model
    │   │   ├── IngredientGroup.java           ── meta-group node
    │   │   ├── IngredientResourceType.java    ── resource type node
    │   │   ├── IngredientExactItem.java       ── leaf node
    │   │   ├── IngredientTreeNode.java        ── interface for all tree nodes
    │   │   ├── CheckState.java                ── enum: NONE, SOME, ALL
    │   │   └── NodeType.java                  ── enum: META_GROUP, RESOURCE_TYPE, EXACT_ITEM
    │   │
    │   ├── radial/                        ── Stencil radial menu UI
    │   │   ├── StencilRadialMenuPage.java     ── radial menu page for stencil selection
    │   │   ├── StencilRadialInputListener.java ── detects middle-click → opens menu
    │   │   └── RadialSegmentItem.java         ── data record for radial segments
    │   │
    │   └── blueprintbook/                ── Blueprint book visual feedback
    │       ├── BlueprintBookParticleLoop.java      ── particle effects on targeted block
    │       └── BlueprintBookPickStencilInteraction.java ── raycast → create stencil
    │
    ├── command/                          ── Commands (unchanged structure)
    │   ├── ParticleCommand.java
    │   └── placeblock/
    │       ├── PlaceBlockCommand.java
    │       └── subcommands/
    │           └── StencilSubCommand.java
    │
    └── util/                             ── Cross-cutting utilities
        └── BoundingBoxRayCast.java
```

---

## 4. Layer Diagram

```
┌─────────────────────────────────────────────────────────┐
│                     com.Plugin                          │  Entry point
│              (registers everything)                     │  imports ALL layers
└───────────────────────┬─────────────────────────────────┘
                        │ registers
    ┌───────────────────┼───────────────────────────┐
    │                   │                           │
    ▼                   ▼                           ▼
┌─────────┐   ┌──────────────────┐   ┌──────────────────┐
│ command/ │   │      ui/         │   │    stencil/      │
│          │   │  bench/          │   │  (ECS systems)   │
│          │   │  ingredienttree/ │   │                  │
│          │   │  radial/         │   │                  │
│          │   │  blueprintbook/  │   │                  │
└────┬─────┘   └───────┬─────────┘   └───────┬──────────┘
     │                 │  LAYER 4+3           │ LAYER 3
     │                 │                      │
     │                 ▼                      ▼
     │        ┌──────────────────────────────────────┐
     │        │           crafting/                   │
     │        │  RecipeAffordabilityResolver          │  LAYER 2
     │        │  PlaceBlockCostUtil                   │
     │        │  BlueprintBookRecipeMutator          │
     │        └──────────────────┬───────────────────┘
     │                           │
     │                           ▼
     │        ┌──────────────────────────────────────┐
     │        │           registry/                  │
     │        │  BenchRecipeRegistries               │  LAYER 1
     │        │  RecipeFilterRegistry                │
     │        │  FilteredRecipeEntry                 │
     │        └──────────────────┬───────────────────┘
     │                           │
     ▼                           ▼
┌────────────────────────────────────────────────────┐
│                    scaling/                        │
│  ResourceConstants, DropScaler, NaturalResource-   │  LAYER 0
│  Registry, BenchCategory, ResourceTypeResolver,    │
│  PlacementCostScaler, *Processor, Classifier       │
└────────────────────────────────────────────────────┘
                        │
                        ▼
                  ┌───────────┐
                  │   util/   │  (no CodeCreature imports)
                  └───────────┘

  Arrow direction = "imports from" / "depends on"
  Lower layers NEVER import upper layers.
```

---

## 5. SOLID Mapping

| Package | Principle | How It's Enforced |
|---|---|---|
| `scaling/` | **S** — Single Responsibility | One concern: define and apply the 12x resource economy. All processors, the scaler, the classifier, the natural-resource registry, and the placement enforcer exist to answer "how do we multiply resource costs?" |
| `scaling/` | **O** — Open/Closed | New bench categories are added via new `BenchCategoryProcessor` implementations without modifying existing processors. `BenchCategory` enum is the extension seam. |
| `scaling/` | **D** — Dependency Inversion | `DropScaler` depends on `BenchCategoryProcessor` interface, not on concrete processor classes. Processors are injected/registered. |
| `registry/` | **S** — Single Responsibility | One concern: index, validate, and serve recipe data. No cost calculation, no UI, no scaling logic. |
| `registry/` | **I** — Interface Segregation | Consumers get `FilteredRecipeEntry` records — a minimal read-only view. They don't see internal registry mutation methods. |
| `crafting/` | **S** — Single Responsibility | One concern: resolve what a recipe costs and whether the player can afford it. Bridges scaling data + registry data into actionable answers. |
| `crafting/` | **D** — Dependency Inversion | `RecipeAffordabilityResolver` depends on abstractions from `scaling/` (`BenchCategory`, `ResourceTypeResolver`, `NaturalResourceRegistry`) — not on `DropScaler` or processors. |
| `stencil/` | **S** — Single Responsibility | One concern: stencil item lifecycle — metadata, placement interception, sync, destruction, visual feedback. |
| `stencil/` | **L** — Liskov Substitution | `StencilMetadata.isStencil()` is a pure predicate on any `ItemStack`. Stencil items are substitutable wherever items are expected; the ECS systems only add behavior. |
| `ui/bench/` | **S** — Single Responsibility | One concern: Blueprint Bench UI rendering and interaction. |
| `ui/ingredienttree/` | **S** — Single Responsibility | One concern: the ingredient filter tree data structure and its UI controller. |
| `ui/radial/` | **S** — Single Responsibility | One concern: stencil radial menu input + rendering. |
| `ui/blueprintbook/` | **S** — Single Responsibility | One concern: blueprint book visual feedback and stencil-creation interaction. |
| `command/` | **S** — Single Responsibility | One concern: chat/console command registration. |

---

## 6. Migration Table

### Layer 0: `scaling/`
| # | Old Path | New Path |
|---|---|---|
| 1 | `resourcecollection/ResourceConstants.java` | `scaling/ResourceConstants.java` |
| 2 | `resourcecollection/DropScaler.java` | `scaling/DropScaler.java` |
| 3 | `resourcecollection/AssetFieldAccessor.java` | `scaling/AssetFieldAccessor.java` |
| 4 | `resourcecollection/BenchCategory.java` | `scaling/BenchCategory.java` |
| 5 | `resourcecollection/BenchBlockClassifier.java` | `scaling/BenchBlockClassifier.java` |
| 6 | `resourcecollection/BenchCategoryProcessor.java` | `scaling/BenchCategoryProcessor.java` |
| 7 | `resourcecollection/AbstractBenchProcessor.java` | `scaling/AbstractBenchProcessor.java` |
| 8 | `resourcecollection/BuildersProcessor.java` | `scaling/BuildersProcessor.java` |
| 9 | `resourcecollection/FurnitureProcessor.java` | `scaling/FurnitureProcessor.java` |
| 10 | `resourcecollection/OverlapProcessor.java` | `scaling/OverlapProcessor.java` |
| 11 | `resourcecollection/NaturalResourceRegistry.java` | `scaling/NaturalResourceRegistry.java` |
| 12 | `resourcecollection/ResourceTypeResolver.java` | `scaling/ResourceTypeResolver.java` |
| 13 | `resourcecollection/PlacementCostScaler.java` | `scaling/PlacementCostScaler.java` |
| 14 | `resourcecollection/BreakBlockDiagnostic.java` | `scaling/BreakBlockDiagnostic.java` |

### Layer 1: `registry/`
| # | Old Path | New Path |
|---|---|---|
| 15 | `resourcecollection/BenchRecipeRegistries.java` | `registry/BenchRecipeRegistries.java` |
| 16 | `resourcecollection/BenchRecipeRegistry.java` | `registry/BenchRecipeRegistry.java` |
| 17 | `resourcecollection/RecipeFilterRegistry.java` | `registry/RecipeFilterRegistry.java` |
| 18 | `resourcecollection/FilteredRecipeEntry.java` | `registry/FilteredRecipeEntry.java` |

### Layer 2: `crafting/`
| # | Old Path | New Path |
|---|---|---|
| 19 | `placeblock/PlaceBlockCostUtil.java` | `crafting/PlaceBlockCostUtil.java` |
| 20 | `placeblock/RecipeAffordabilityResolver.java` | `crafting/RecipeAffordabilityResolver.java` |
| 21 | `placeblock/ResolvedIngredient.java` | `crafting/ResolvedIngredient.java` |
| 22 | `placeblock/BlueprintBookRecipeMutator.java` | `crafting/BlueprintBookRecipeMutator.java` |
| 23 | `placeblock/ResourceScanner.java` | `crafting/ResourceScanner.java` |
| 24 | `placeblock/ResourceSnapshot.java` | `crafting/ResourceSnapshot.java` |

### Layer 3: `stencil/`
| # | Old Path | New Path | Notes |
|---|---|---|---|
| 25 | `stencil/StencilMetadata.java` | `stencil/StencilMetadata.java` | unchanged |
| 26 | `stencil/StencilPlacementSystem.java` | `stencil/StencilPlacementSystem.java` | unchanged |
| 27 | `stencil/StencilSyncSystem.java` | `stencil/StencilSyncSystem.java` | unchanged |
| 28 | `stencil/StencilDropDestroySystem.java` | `stencil/StencilDropDestroySystem.java` | unchanged |
| 29 | `stencil/StencilVisualManager.java` | `stencil/StencilVisualManager.java` | unchanged |

### Layer 4: `ui/`
| # | Old Path | New Path |
|---|---|---|
| 30 | `placeblock/ui/BlueprintSelectionPage.java` | `ui/bench/BlueprintSelectionPage.java` |
| 31 | `placeblock/ui/BlueprintBookOpenUIInteraction.java` | `ui/bench/BlueprintBookOpenUIInteraction.java` |
| 32 | `placeblock/ui/BlueprintBookPrefs.java` | `ui/bench/BlueprintBookPrefs.java` |
| 33 | `placeblock/ui/BlueprintBookPrefsStore.java` | `ui/bench/BlueprintBookPrefsStore.java` |
| 34 | `placeblock/ui/AffordabilityMode.java` | `ui/bench/AffordabilityMode.java` |
| 35 | `placeblock/ui/RecipeFilterPipeline.java` | `ui/bench/RecipeFilterPipeline.java` |
| 36 | `placeblock/ui/ResourceTypeRegistry.java` | `ui/bench/ResourceTypeRegistry.java` |
| 37 | `placeblock/ui/ingredienttree/IngredientTree.java` | `ui/ingredienttree/IngredientTree.java` |
| 38 | `placeblock/ui/ingredienttree/IngredientTreeBuilder.java` | `ui/ingredienttree/IngredientTreeBuilder.java` |
| 39 | `placeblock/ui/ingredienttree/IngredientTreeGridController.java` | `ui/ingredienttree/IngredientTreeGridController.java` |
| 40 | `placeblock/ui/ingredienttree/IngredientSelectionModel.java` | `ui/ingredienttree/IngredientSelectionModel.java` |
| 41 | `placeblock/ui/ingredienttree/IngredientGroup.java` | `ui/ingredienttree/IngredientGroup.java` |
| 42 | `placeblock/ui/ingredienttree/IngredientResourceType.java` | `ui/ingredienttree/IngredientResourceType.java` |
| 43 | `placeblock/ui/ingredienttree/IngredientExactItem.java` | `ui/ingredienttree/IngredientExactItem.java` |
| 44 | `placeblock/ui/ingredienttree/IngredientTreeNode.java` | `ui/ingredienttree/IngredientTreeNode.java` |
| 45 | `placeblock/ui/ingredienttree/CheckState.java` | `ui/ingredienttree/CheckState.java` |
| 46 | `placeblock/ui/ingredienttree/NodeType.java` | `ui/ingredienttree/NodeType.java` |
| 47 | `stencil/StencilRadialMenuPage.java` | `ui/radial/StencilRadialMenuPage.java` |
| 48 | `stencil/StencilRadialInputListener.java` | `ui/radial/StencilRadialInputListener.java` |
| 49 | `stencil/RadialSegmentItem.java` | `ui/radial/RadialSegmentItem.java` |
| 50 | `stencil/BlueprintBookParticleLoop.java` | `ui/blueprintbook/BlueprintBookParticleLoop.java` |
| 51 | `stencil/BlueprintBookPickStencilInteraction.java` | `ui/blueprintbook/BlueprintBookPickStencilInteraction.java` |

### Unchanged
| # | Old Path | New Path |
|---|---|---|
| 52 | `command/ParticleCommand.java` | `command/ParticleCommand.java` |
| 53 | `command/placeblock/PlaceBlockCommand.java` | `command/placeblock/PlaceBlockCommand.java` |
| 54 | `command/placeblock/subcommands/StencilSubCommand.java` | `command/placeblock/subcommands/StencilSubCommand.java` |
| 55 | `util/BoundingBoxRayCast.java` | `util/BoundingBoxRayCast.java` |
| 56 | `Plugin.java` (in `com/`) | `Plugin.java` |

### Test Migration
| # | Old Test Path | New Test Path |
|---|---|---|
| T1 | `resourcecollection/ResourceConstantsTest.java` | `scaling/ResourceConstantsTest.java` |
| T2 | `resourcecollection/ResourceTypeResolverTest.java` | `scaling/ResourceTypeResolverTest.java` |
| T3 | `resourcecollection/ResourceScalingIntegrationTest.java` | `scaling/ResourceScalingIntegrationTest.java` |
| T4 | `resourcecollection/DropBehaviorScenarioTest.java` | `scaling/DropBehaviorScenarioTest.java` |
| T5 | `resourcecollection/SharedInstanceDropBugTest.java` | `scaling/SharedInstanceDropBugTest.java` |
| T6 | `resourcecollection/BlockIdWithoutHasBlockTypeTest.java` | `scaling/BlockIdWithoutHasBlockTypeTest.java` |
| T7 | `resourcecollection/AssetTestHelper.java` | `scaling/AssetTestHelper.java` |
| T8 | `resourcecollection/TestDataSet.java` | `scaling/TestDataSet.java` |
| T9 | `resourcecollection/BlockRecipeRegistryTest.java` | `registry/BlockRecipeRegistryTest.java` |
| T10 | `placeblock/ui/AffordabilityModeTest.java` | `ui/bench/AffordabilityModeTest.java` |
| T11 | `placeblock/ui/ResourceTypeRegistryTest.java` | `ui/bench/ResourceTypeRegistryTest.java` |
| T12 | `stencil/StencilMetadataTest.java` | `stencil/StencilMetadataTest.java` |

---

## 7. File Rename Recommendations

Only files where the current name is genuinely confusing in context:

| File | Current Name | Recommended Name | Reason |
|---|---|---|---|
| — | — | — | No renames recommended. |

**Rationale:** All current file names remain clear in their new package context:

- `BenchRecipeRegistries` in `registry/` — still specific enough, distinguishes from `RecipeFilterRegistry`
- `RecipeFilterRegistry` in `registry/` — the "Filter" qualifier differentiates it from `BenchRecipeRegistry`, so it should NOT be shortened
- `PlaceBlockCostUtil` in `crafting/` — the "PlaceBlock" prefix still adds clarity since this is per-block-placement cost, not generic recipe cost
- `BlueprintBookRecipeMutator` in `crafting/` — "BlueprintBook" prefix is essential; it's specifically for Blueprint_ shadow recipes

No file needs renaming. The package context already provides the disambiguation that was missing before.

---

## 8. Dependency Rules

Each package's **allowed imports** (from `com.CodeCreature.*`):

| Package | May Import | Must NOT Import |
|---|---|---|
| `util/` | nothing | everything |
| `scaling/` | `util/` | `registry/`, `crafting/`, `stencil/`, `ui/`, `command/` |
| `registry/` | `scaling/` | `crafting/`, `stencil/`, `ui/`, `command/` |
| `crafting/` | `scaling/`, `registry/` | `stencil/`, `ui/`, `command/` |
| `stencil/` | `scaling/`, `registry/`, `crafting/` | `ui/`, `command/` |
| `ui/bench/` | `scaling/`, `registry/`, `crafting/`, `stencil/` | `command/` |
| `ui/ingredienttree/` | `scaling/`, `registry/`, `ui/bench/` | `crafting/`, `stencil/`, `command/` |
| `ui/radial/` | `scaling/`, `registry/`, `crafting/` | `stencil/`*, `command/` |
| `ui/blueprintbook/` | `scaling/`, `registry/`, `crafting/`, `util/` | `stencil/`*, `command/` |
| `command/` | `stencil/`, `ui/blueprintbook/` | `scaling/`, `registry/`, `crafting/`, `ui/bench/` |
| `Plugin.java` | ALL | — (it's the composition root) |

> \* **UI subpackages and `stencil/`:** The radial and blueprintbook files currently import from `resourcecollection` and `placeblock`, which map cleanly to `registry/` and `crafting/`. They do NOT import `stencil/` package types — the stencil UI components were IN `stencil/` before, but they only consume data from lower layers. After the move, they should not need `stencil/` imports.

### Existing Dependency Violation to Fix During Migration

**`PlacementCostScaler.java`** (Layer 0 `scaling/`) currently imports `StencilMetadata` (Layer 3 `stencil/`). This is a **lower layer importing an upper layer** — a circular dependency.

**Fix:** `PlacementCostScaler` only calls `StencilMetadata.isStencil(ItemStack)`, which checks BSON metadata on the item. Extract a small static utility method or predicate:

- **Option A (recommended):** Move `StencilMetadata.java` to `scaling/` since it's a low-level item predicate used across many layers. But `StencilMetadata` also has `createStencil()` and `getRecipeId()` which are stencil-specific.
- **Option B (cleaner):** Add a static method `isStencilItem(ItemStack)` directly in `PlacementCostScaler` that checks the BSON tag without importing `StencilMetadata`. This is a 3-line inline check.
- **Option C (if StencilMetadata is used by many layers):** Move `StencilMetadata` into `util/` or a new `item/` package at Layer 0, since it's fundamentally an item-metadata reader that multiple layers depend on.

**Recommendation: Option C.** `StencilMetadata` is imported by `PlacementCostScaler` (scaling), `BlueprintSelectionPage` (UI), and `StencilSubCommand` (command). It's a cross-cutting item utility. Move it to `util/StencilMetadata.java` and let all layers access it from there.

Updated migration for this fix:

| # | Old Path | New Path | Notes |
|---|---|---|---|
| 25* | `stencil/StencilMetadata.java` | **`util/StencilMetadata.java`** | Cross-cutting item metadata; breaks circular dep |

---

## 9. Summary of Package Counts

| Package | File Count | Responsibility |
|---|---|---|
| `scaling/` | 14 | Resource economy: constants, scaling, processors, classifiers, registries, enforcement |
| `registry/` | 4 | Recipe indexing: per-bench registries, filter registry, filtered entry records |
| `crafting/` | 6 | Cost resolution: affordability, cost calculation, recipe mutation, inventory scanning |
| `stencil/` | 4 | Stencil lifecycle: placement, sync, drop destruction, visual overrides |
| `ui/bench/` | 7 | Blueprint Bench UI: page, interaction, prefs, filters |
| `ui/ingredienttree/` | 10 | Ingredient tree: data structure, builder, controller, nodes, enums |
| `ui/radial/` | 3 | Radial menu: page, input listener, segment data |
| `ui/blueprintbook/` | 2 | Blueprint book: particle effects, pick interaction |
| `command/` | 3 | Commands (unchanged) |
| `util/` | 2 | Cross-cutting utilities (BoundingBoxRayCast + StencilMetadata) |
| **Total** | **55** | |

---

## 10. How to Read the Dependency Flow

Starting from "I want to understand how resource costs work":

```
1. Open scaling/
   → ResourceConstants.java tells you the multiplier is 12
   → DropScaler.java tells you how all asset costs get multiplied
   → NaturalResourceRegistry.java tells you which blocks are natural
   → PlacementCostScaler.java tells you how placement enforces the cost

2. Open registry/
   → BenchRecipeRegistries.java tells you how recipes are indexed per-bench
   → RecipeFilterRegistry.java tells you how recipes are validated and filtered

3. Open crafting/
   → PlaceBlockCostUtil.java tells you per-unit cost = input / outputQty
   → RecipeAffordabilityResolver.java tells you the full chain: cost → resolve → check inventory

4. Open stencil/
   → StencilPlacementSystem.java tells you how stencils consume materials
   → StencilVisualManager.java tells you how affordability affects visuals

5. Open ui/
   → bench/BlueprintSelectionPage.java shows how it all displays to the player
```

That's the trace the user asked for: **resources scale at the bottom, crafting management in the middle, and it's easy to follow down through the files.**
