---
id: "04010000"
type: knowledge-topic
title: "Crafting Input Resolution"
parent: "04000000"
created: 2026-07-22
updated: 2026-07-24
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

## Generic Proxy Items (Design)
Goal: support generic crafting-resource drops as dedicated non-placeable proxy items (for example, a single "Wood" token item) that later morph to concrete items based on container context.

### Why this is needed
Current recipe-block drop generation is static and resolved up-front, so generic recipe inputs become fixed concrete item drops in synthetic drop lists and cannot react to player inventory at break time.

### Evidence
- Static synthetic recipe drop list creation:
  - [src/main/java/com/CodeCreature/scaling/AbstractBenchProcessor.java](../../../../../src/main/java/com/CodeCreature/scaling/AbstractBenchProcessor.java)
  - [src/main/java/com/CodeCreature/scaling/DropScaler.java](../../../../../src/main/java/com/CodeCreature/scaling/DropScaler.java)
- Runtime inventory-change hooks already exist and can be reused for morphing logic:
  - [src/main/java/com/CodeCreature/stencil/StencilSyncSystem.java](../../../../../src/main/java/com/CodeCreature/stencil/StencilSyncSystem.java)
  - [src/main/java/com/CodeCreature/stencil/AffordabilityCoalescer.java](../../../../../src/main/java/com/CodeCreature/stencil/AffordabilityCoalescer.java)
- Existing generic resolution + icon path infrastructure is available:
  - [src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java](../../../../../src/main/java/com/CodeCreature/scaling/ResourceTypeResolver.java)
  - [src/main/java/com/CodeCreature/ui/bench/ResourceTypeRegistry.java](../../../../../src/main/java/com/CodeCreature/ui/bench/ResourceTypeRegistry.java)
  - [src/main/java/com/CodeCreature/ui/common/IconPathResolver.java](../../../../../src/main/java/com/CodeCreature/ui/common/IconPathResolver.java)

### Architecture
1. Asset layer:
   - Generate one non-placeable item asset per chosen generic ResourceType ID (or meta-group ID set), with icon from ResourceTypeRegistry/IconPathResolver and with no blockId.
   - Mark each proxy item with stable metadata fields that map it back to resourceTypeId and optional compatibility policy.
2. Drop layer:
   - For recipe inputs authored as ResourceTypeId, drop proxy token items instead of pre-resolved concrete variants.
   - Keep direct ItemId recipe inputs unchanged.
3. Morph layer (container-aware):
   - Add a new inventory/container sync system (parallel to StencilSyncSystem) that listens to container change events.
   - On each coalesced refresh, scan proxy tokens and attempt conversion using "closest matching" policy:
     - if container has matching resource-type items: morph token into preferred concrete variant;
     - if no matches exist: keep token unchanged.
4. Conversion policy:
   - Deterministic score function should be centralized and configurable. Suggested default:
     - prefer existing stack extension in same container;
     - then highest quantity among matching variants;
     - then stable tie-break using resolver order.
5. Consumption compatibility:
   - Extend affordability/planner checks so proxy items count as satisfying their resourceTypeId identity.
   - At removeMaterials boundary, convert proxy consumptions to concrete item removals via the same score policy.

### Rollout Strategy
1. Add proxy item schema + registry + generated assets.
2. Add drop projection toggle for generic recipe inputs.
3. Add morph system with coalesced event handling and deterministic score policy.
4. Add parity probes and tests for conversion behavior across pickup, drop, transfer, and split-stack actions.

### Risks
- If conversion policy differs from engine expectations, players may observe surprising variant choices.
- Over-eager morphing can cause inventory churn; coalescing is required for stability.
- Proxy item stackability and quality/category metadata must be controlled to avoid accidental crafting loops.

## Runtime Probe Matrix Alignment
Wave 1 parity work now has a concrete capture matrix in:
- [docs/review-resource-typeid-parity.md](../../../../../docs/review-resource-typeid-parity.md)

Execution gates are tracked with a per-wave checkbox list in:
- [docs/review-resource-typeid-parity.md](../../../../../docs/review-resource-typeid-parity.md)

Candidate probe recipe IDs grounded in repository evidence:
- `Furniture_Kweebec_Bed`
- `Wood_Hardwood_Fence`
- `Planks_Oak`
- `Slab_Oak`

Repository evidence for those candidate IDs:
- [src/test/java/com/CodeCreature/scaling/TestDataSet.java#L227](../../../../../src/test/java/com/CodeCreature/scaling/TestDataSet.java#L227)
- [src/test/java/com/CodeCreature/scaling/TestDataSet.java#L293](../../../../../src/test/java/com/CodeCreature/scaling/TestDataSet.java#L293)
- [src/test/java/com/CodeCreature/scaling/TestDataSet.java#L307](../../../../../src/test/java/com/CodeCreature/scaling/TestDataSet.java#L307)

Interpretation rule:
- Treat parity as provisional until matrix cases run in live assets and produce no parity warnings.

Locked acceptance decisions (2026-07-23):
- Parity contract: exact runtime parity for scoped generic-variant cases.
- Generic token morph policy: choose the currently largest matching stack; deterministic tie-break by stable resolver order.
- Go/no-go gate: zero parity warnings across all required probe matrix cases.

Additional hardening (2026-07-23):
- Token fallback acceptance is explicit: if no matching concrete stack exists, token remains generic.
- Probe validity requires reproducible runtime environment conditions documented in [docs/review-resource-typeid-parity.md](../../../../../docs/review-resource-typeid-parity.md).
- Unchecked wave/release gate items are now mapped to explicit evidence artifacts in [docs/review-resource-typeid-parity.md](../../../../../docs/review-resource-typeid-parity.md).

## Item Icon Path Contract (Proxy Drop Follow-up)
Date: 2026-07-23

Finding summary:
1. Stencil detail UI supports two icon channels: concrete item icon via item ID, and generic-resource icon via a direct UI background path.
2. Generic proxy drop items in world/inventory do not use the stencil generic background channel; they rely on the Item asset Icon field.
3. Ingredient-style items (example: Ingredient_Fibre) author Item.Icon as an item icon path under ItemsGenerated, while model/texture data live under Common/Resources.
4. Therefore proxy drop Item.Icon must resolve using item icon normalization behavior (item icon contract), not resource-type icon normalization behavior.

Evidence:
- Stencil detail panel dual-channel behavior:
  - [src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java#L92](../../../../../src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java#L92)
  - [src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java#L100](../../../../../src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java#L100)
  - [src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java#L102](../../../../../src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java#L102)
- IconPathResolver family split:
  - [src/main/java/com/CodeCreature/ui/common/IconPathResolver.java#L57](../../../../../src/main/java/com/CodeCreature/ui/common/IconPathResolver.java#L57)
  - [src/main/java/com/CodeCreature/ui/common/IconPathResolver.java#L87](../../../../../src/main/java/com/CodeCreature/ui/common/IconPathResolver.java#L87)
  - [src/main/java/com/CodeCreature/ui/common/IconPathResolver.java#L140](../../../../../src/main/java/com/CodeCreature/ui/common/IconPathResolver.java#L140)
- Current proxy drop path applies Item.Icon from proxy catalog resolution:
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyCatalog.java#L39](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyCatalog.java#L39)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyCatalog.java#L53](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyCatalog.java#L53)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L105](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L105)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L120](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L120)
- Local shared-source mirror confirms Ingredient_Fibre item JSON contract:
  - local file: C:/src/Code Creature/Hytale Shared Source/hytale-shared-source/HytaleAssets/Server/Item/Items/Ingredient/Ingredient_Fibre.json#L27-L27 (Icon = Icons/ItemsGenerated/Ingredient_Fibre.png)
  - local file: C:/src/Code Creature/Hytale Shared Source/hytale-shared-source/HytaleAssets/Server/Item/Items/Ingredient/Ingredient_Fibre.json#L23-L24 (Texture/Model under Resources/Plants)
- Runtime asset-editor evidence of model/texture location:
  - [run/logs/2026-05-06_10-05-00_server.log#L1139](../../../../../run/logs/2026-05-06_10-05-00_server.log#L1139)
  - [run/logs/2026-05-06_10-05-00_server.log#L1141](../../../../../run/logs/2026-05-06_10-05-00_server.log#L1141)

Operational implication:
- For generic proxy drop items, choose icon candidates from representative Item.Icon values and normalize with item icon rules.
- Do not set proxy Item.Icon from resource-type UI icon paths (ResourceTypes family), because that channel is intended for stencil generic UI backgrounds, not item asset icon serialization.

## Proxy Asset Regeneration + JSON Inspection
Date: 2026-07-23

New runtime debug command:
- `/debug regenproxies`
- `/debug regenproxies all`
- `/debug regenproxiesall` (alias that always runs dump-all mode)
- `/debug regenproxiesa` (alias for dump-all mode; tolerates token variant seen in runtime command logs)
- `/debug regenproxy` (short alias for base mode)

Behavior:
1. Re-initializes resource-type index.
2. Scans bench recipe registries for `ResourceTypeId` inputs.
3. Re-ensures generated proxy assets via `GenericDropProxyAssetLoader`.
4. Writes each generated proxy `Item` asset to a JSON file using `Item.CODEC.encode(...).toJson()`.

Output directory:
- `<plugin-data-dir>/Server/Item/Items/Plugin/GenericDropProxy/` (canonical asset location for startup auto-discovery)

Evidence:
- Command registration:
  - [src/main/java/com/CodeCreature/command/debug/DebugCommand.java#L33](../../../../../src/main/java/com/CodeCreature/command/debug/DebugCommand.java#L33)
  - [src/main/java/com/CodeCreature/command/debug/DebugCommand.java#L34](../../../../../src/main/java/com/CodeCreature/command/debug/DebugCommand.java#L34)
- Command implementation:
  - [src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsSubCommand.java](../../../../../src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsSubCommand.java)
  - [src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsAllSubCommand.java](../../../../../src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsAllSubCommand.java)
  - [src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsAliasSubCommand.java](../../../../../src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsAliasSubCommand.java)
- Data directory source:
  - [src/main/java/com/CodeCreature/registry/BenchRegistry.java#L109](../../../../../src/main/java/com/CodeCreature/registry/BenchRegistry.java#L109)
- Proxy ensure/dump flow:
  - [src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsSubCommand.java#L69](../../../../../src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsSubCommand.java#L69)
  - [src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsSubCommand.java#L119](../../../../../src/main/java/com/CodeCreature/command/debug/RegenerateProxyAssetsSubCommand.java#L119)

## Login Disconnect Failure Signature (2026-07-24)

Observed runtime failure:
1. Client disconnects during login setup at `setup:send-assets` with `client.general.disconnect.loginException`.
2. Server throws an unhandled exception while serializing item init packets.
3. Root cause in stack trace: `Item.toPacket(...)` dereferences null `interactionConfig`.

Evidence:
- Disconnect during send-assets stage:
  - [run/logs/2026-07-23_23-23-29_server.log#L744](../../../../../run/logs/2026-07-23_23-23-29_server.log#L744)
  - [run/logs/2026-07-23_23-23-29_server.log#L812](../../../../../run/logs/2026-07-23_23-23-29_server.log#L812)
- Root exception:
  - [run/logs/2026-07-23_23-23-29_server.log#L764](../../../../../run/logs/2026-07-23_23-23-29_server.log#L764)
  - [run/logs/2026-07-23_23-23-29_server.log#L770](../../../../../run/logs/2026-07-23_23-23-29_server.log#L770)
  - [run/logs/2026-07-23_23-23-29_server.log#L832](../../../../../run/logs/2026-07-23_23-23-29_server.log#L832)
  - [run/logs/2026-07-23_23-23-29_server.log#L838](../../../../../run/logs/2026-07-23_23-23-29_server.log#L838)
- Generated proxy default restoration (required interaction fields):
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L157](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L157)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L158](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L158)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L163](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L163)

Operational note:
- Command registration also reports invalid permission node formatting (spaces in node IDs) during startup, but this is separate from the login NPE.
  - [run/logs/2026-07-23_23-23-29_server.log#L120](../../../../../run/logs/2026-07-23_23-23-29_server.log#L120)
  - [run/logs/2026-07-23_23-23-29_server.log#L150](../../../../../run/logs/2026-07-23_23-23-29_server.log#L150)

## Minimal Ingredient-Style Proxy Asset Contract (2026-07-24)

Refactor intent:
1. Keep generated proxy item JSON as small as possible while still functioning as an ingredient-like asset.
2. Preserve runtime-only defaults required to avoid login-time packet serialization failures.

Author-time fields kept for generated proxy assets:
1. TranslationProperties
2. Icon
3. Categories (ingredient-aligned)
4. Model
5. Texture
6. ResourceTypes

Runtime safety defaults still applied in memory:
1. interactionConfig
2. interactions
3. interactionVars
4. itemEntityConfig
5. utility
6. itemStackContainerConfig
7. playerAnimationsId
8. usePlayerAnimations
9. itemSoundSetId
10. dropOnDeath

Pickup stability requirement:
1. Generated proxy items must have a positive `maxStack` value.
2. `maxStack = -1` causes world-thread crash during item pickup.

Evidence:
- Lean field authoring in generator:
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L115](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L115)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L130](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L130)
- Ingredient category alignment:
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L38](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L38)
- Runtime-required defaults retained:
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L160](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L160)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L169](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L169)
- Pickup crash stack trace (`quantity -1 must be >0`) during item pickup path:
  - [run/logs/2026-07-23_23-43-05_server.log#L763](../../../../../run/logs/2026-07-23_23-43-05_server.log#L763)
  - [run/logs/2026-07-23_23-43-05_server.log#L771](../../../../../run/logs/2026-07-23_23-43-05_server.log#L771)
- Max stack default restored in proxy generator:
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L40](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L40)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L131](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L131)

## Hotbar Selection Diagnostics (2026-07-24)

Purpose:
1. Trace hotbar scroll/selection transitions while testing generated proxy items.
2. Capture proxy item sanity fields at selection time (`maxStack`, `interactionConfig`, interaction maps) without changing gameplay behavior.

Behavior:
1. Emits `HotbarTrace` logs only when slot or held item state changes.
2. Logs selected slot, item ID, quantity, metadata presence.
3. For proxy IDs (`Plugin_GenericDropProxy_RT_*`), logs runtime asset presence and key fields.

Evidence:
- Logger hook in active hotbar polling loop:
  - [src/main/java/com/CodeCreature/ui/stencilbook/StencilBookParticleLoop.java#L155](../../../../../src/main/java/com/CodeCreature/ui/stencilbook/StencilBookParticleLoop.java#L155)
  - [src/main/java/com/CodeCreature/ui/stencilbook/StencilBookParticleLoop.java#L287](../../../../../src/main/java/com/CodeCreature/ui/stencilbook/StencilBookParticleLoop.java#L287)

## Gaps
- Unknown from repository evidence: the exact engine implementation that `CombinedItemContainer.canRemoveMaterials(...)` and `removeMaterials(...)` use to choose among multiple matching variants for a `ResourceTypeId` input.
- Unknown from repository evidence: whether the engine uses inventory order, insertion order, stack order, set-root preference, or another rule when multiple variants satisfy the same generic ingredient during removal.
- Unknown from repository evidence: whether engine-side recursive crafting or preview UIs ever collapse `ResourceTypeId` to a representative display item, and if so whether that choice affects actual material consumption.
- Unknown from repository evidence: whether runtime item-asset registration for new Item assets is supported in this project in the same way as CraftingRecipe and ItemDropList runtime registration; if not, proxy items must be generated as static resource assets before load.

## Proxy Input Recognition Fix (2026-07-24)

Issue:
1. Proxy drops were generated and visible in hotbar, but stencil affordability/consumption did not consistently recognize them as valid ingredients.

Root cause:
1. Stencil generic matching consumed/counts by concrete variant item IDs.
2. Proxy item IDs were not in that concrete variant set, so they were invisible to planner accounting.

Fix:
1. Keep sap-template cloning for proxy asset shape (original server item baseline).
2. Expand proxy `ResourceTypes` to include the full generic family (specific subtype IDs + generic root).
3. Extend generic matcher variant list to include proxy item IDs that explicitly declare the requested `ResourceTypeId`.

Evidence:
- Proxy loader now builds family-wide `ResourceTypes` on cloned sap template:
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L104](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L104)
  - [src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L141](../../../../../src/main/java/com/CodeCreature/scaling/GenericDropProxyAssetLoader.java#L141)
- Generic matcher includes proxy IDs for matching resource type:
  - [src/main/java/com/CodeCreature/crafting/GenericVariantMatcher.java#L126](../../../../../src/main/java/com/CodeCreature/crafting/GenericVariantMatcher.java#L126)
  - [src/main/java/com/CodeCreature/crafting/GenericVariantMatcher.java#L146](../../../../../src/main/java/com/CodeCreature/crafting/GenericVariantMatcher.java#L146)
