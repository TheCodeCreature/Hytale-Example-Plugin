---
id: "04010000"
type: knowledge-topic
title: "Crafting Input Resolution"
parent: "04000000"
created: 2026-07-22
updated: 2026-07-22
tags:
  - domain:hytale
  - kind:topic
  - domain:crafting
key_terms:
  - CraftingRecipe
  - MaterialQuantity
  - ResourceTypeId
  - ResourceTypeResolver
  - canRemoveMaterials
---

# Crafting Input Resolution

## Navigation
- [Back to Knowledge Catalog](../../../Catalog.md)
- [Back to Source Overview](../Overview.md)
- [Source Topic Map](../Topics.md)

## Topic Summary
The available plugin evidence shows that Hytale recipe inputs preserve genericity as `MaterialQuantity` values carrying either `itemId` or `resourceTypeId`. This plugin currently uses two different strategies on top of that API shape: native engine container checks where possible, and plugin-defined collapse to one representative concrete item when a system needs a single item ID for display, recursive raw-cost trees, or stencil-safe consumption planning.

## Key Questions
- Where does the plugin currently preserve generic `ResourceTypeId` matching?
- Where does the plugin currently collapse a generic input to a single item choice?
- What is the safest implementation path if we want engine-like generic resolution instead of "pick the first representative"?

## Findings
1. The plugin resolves `ResourceTypeId` inputs through a pre-indexed `resourceTypeIndex` and returns a single concrete item from `resolveByResourceType`, with a two-pass preference system and fallback to the first indexed match.
2. The resolver is deterministic but plugin-defined, not engine-native: it prefers natural or non-natural items depending on caller intent and sorts set-root items ahead of derivatives.
3. Some plugin paths already preserve generic matching semantics by keeping `MaterialQuantity` inputs intact and delegating to `container.canRemoveMaterials(...)`, which the code comments describe as using the engine's native `ResourceTypeId` matching.
4. Other paths deliberately expand a generic input to all matching variants and then greedily consume available variants, but still pick one primary resolved item when they need to derive raw-cost or auto-craft fallback behavior.
5. UI layers also collapse generic resource types to one representative item for icon/display purposes by taking the first indexed match.
6. Available repository evidence does not expose the engine implementation body that decides how `CombinedItemContainer` resolves `ResourceTypeId` during actual removal, so exact engine consumption order is still unknown from the local shared-source mirror and GitHub fallback.

## Evidence
- Single-item collapse in the plugin resolver:
  - [src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java](../../../../../src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java) defines the indexed lookup and single-result resolution entry points.
  - [src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L101](../../../../../src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L101) routes `MaterialQuantity` inputs through `resolveByResourceType(...)` when `resourceTypeId` is present.
  - [src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L135](../../../../../src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L135) performs the two-pass selection.
  - [src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L145](../../../../../src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L145) falls back to the first indexed match.
  - [src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L163](../../../../../src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java#L163) exposes all matching concrete item IDs in sorted order.
- Tests proving the current policy is representative-choice based rather than ambiguity-preserving:
  - [src/test/java/com/CodeCreature/scaling/ResourceTypeResolverTest.java#L84](../../../../../src/test/java/com/CodeCreature/scaling/ResourceTypeResolverTest.java#L84) expects `Wood_Hardwood_Planks` for `Wood_Hardwood` when builders prefer non-natural.
  - [src/test/java/com/CodeCreature/scaling/ResourceTypeResolverTest.java#L146](../../../../../src/test/java/com/CodeCreature/scaling/ResourceTypeResolverTest.java#L146) verifies fallback to natural when no non-natural option exists.
  - [src/test/java/com/CodeCreature/scaling/ResourceTypeResolverTest.java#L161](../../../../../src/test/java/com/CodeCreature/scaling/ResourceTypeResolverTest.java#L161) expects natural preference to win for furniture-like contexts.
  - [src/test/java/com/CodeCreature/scaling/TestDataSet.java#L103](../../../../../src/test/java/com/CodeCreature/scaling/TestDataSet.java#L103) and [src/test/java/com/CodeCreature/scaling/TestDataSet.java#L113](../../../../../src/test/java/com/CodeCreature/scaling/TestDataSet.java#L113) define test recipes that use `ResourceTypeId` values such as `Wood_All` and `Wood_Hardwood`.
- Engine-preserving path in UI affordability:
  - [src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L1321](../../../../../src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java#L1321) checks affordability with `container.canRemoveMaterials(materials)` using the original per-unit `MaterialQuantity` list.
- Engine-native matching acknowledged, but bypassed in stencil-safe planning:
  - [src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L27](../../../../../src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L27) states that `container.canRemoveMaterials(...)` uses the engine's native `ResourceTypeId` matching.
  - [src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L124](../../../../../src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L124) explains why the planner cannot directly rely on that path in stencil contexts.
  - [src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L154](../../../../../src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L154) greedily fills across all matching variants.
  - [src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L194](../../../../../src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java#L194) still selects one primary resolved item for raw-cost lookup and deficit handling.
- Affordability logic that sums all variants but reports one resolved item:
  - [src/main/java/com/CodeCreature/crafting/RecipeAffordabilityResolver.java#L104](../../../../../src/main/java/com/CodeCreature/crafting/RecipeAffordabilityResolver.java#L104) resolves each input to one concrete item ID.
  - [src/main/java/com/CodeCreature/crafting/RecipeAffordabilityResolver.java#L121](../../../../../src/main/java/com/CodeCreature/crafting/RecipeAffordabilityResolver.java#L121) sums inventory across all variants sharing the same `resourceTypeId`.
- Display collapse for ingredient UI:
  - [src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTreeBuilder.java#L202](../../../../../src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTreeBuilder.java#L202) resolves a representative item for a resource type.
  - [src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTreeBuilder.java#L205](../../../../../src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTreeBuilder.java#L205) returns the first indexed match for display.
- API shape evidence from local code against the Hytale classes:
  - [src/main/java/com/CodeCreature/scaling/AssetFieldAccessor.java#L76](../../../../../src/main/java/com/CodeCreature/scaling/AssetFieldAccessor.java#L76) reflects the `CraftingRecipe.input` field directly.
  - [src/main/java/com/CodeCreature/crafting/StencilBookRecipeMutator.java#L72](../../../../../src/main/java/com/CodeCreature/crafting/StencilBookRecipeMutator.java#L72) constructs a `MaterialQuantity` using `resourceTypeId` without needing a concrete item ID.
  - [src/main/java/com/CodeCreature/crafting/StencilBookRecipeMutator.java#L117](../../../../../src/main/java/com/CodeCreature/crafting/StencilBookRecipeMutator.java#L117) logs the shadow recipe's `resourceTypeId`, confirming the generic input survives as authored.

## Implementation Plan
1. Separate "generic ingredient identity" from "representative display item" in the crafting model. `ResourceTypeId` should remain first-class through planning and affordability, while the current representative-item logic should become display-only.
2. Introduce a resolver result type for generic inputs. It should preserve `resourceTypeId`, ordered matching variants, and an optional representative item for UI. This lets the planner and raw-cost code stop overloading a single `String itemId` as both identity and choice.
3. Replace single-choice recursion boundaries in `RecipeAffordabilityResolver` and `AutoCraftPlanner` with variant-aware accounting. Direct affordability can already reason across all variants; the next step is to carry that same variant set into deficit calculation and consumption materialization.
4. Defer any "pick one concrete item" decision until an operation truly requires it. Candidate boundaries are icon rendering, debug output, and any final call site that must hand the engine a concrete removal list because stencil filtering prevents direct `canRemoveMaterials` use.
5. For recursive raw-cost resolution, decide the intended parity rule explicitly before coding:
   - Strict engine-parity path: preserve ambiguity and branch by matching variant, which may require multiple possible raw-cost trees or a deterministic engine-like choice rule.
   - Pragmatic plugin path: keep one deterministic representative for raw-cost display only, but do not let that representative control affordability or actual consumption.
6. Add tests before runtime edits:
   - multi-variant direct affordability where any wood variant should satisfy the input;
   - mixed-inventory greedy consumption across several matching wood items;
   - raw-cost display behavior for ambiguous `ResourceTypeId` inputs;
   - stencil-safe removal behavior to confirm non-stencil filtering still works.
7. Only after those tests are in place should `ResourceTypeResolver.resolveInputItemId(...)` be narrowed or deprecated. The safer direction is to add a new richer API and migrate callers incrementally rather than rewriting all current call sites in one pass.

## Gaps
- Unknown from repository evidence: the exact engine implementation that `CombinedItemContainer.canRemoveMaterials(...)` and `removeMaterials(...)` use to choose among multiple matching variants for a `ResourceTypeId` input.
- Unknown from repository evidence: whether the engine uses inventory order, insertion order, stack order, set-root preference, or another rule when multiple variants satisfy the same generic ingredient during removal.
- Unknown from repository evidence: whether engine-side recursive crafting or preview UIs ever collapse `ResourceTypeId` to a representative display item, and if so whether that choice affects actual material consumption.
