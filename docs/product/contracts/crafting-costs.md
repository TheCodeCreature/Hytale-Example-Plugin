---
area: "Crafting Cost Scaling"
updated: 2026-04-18
---

# Crafting Cost Scaling — Behavioral Contract

## Player Experience Goal

When a player opens a bench and sees a recipe cost, it should make sense relative to what they gathered. "I got 12 stone from one block, and this wall costs 48 stone — so that's 4 blocks worth of stone." The math should always feel proportional and predictable.

## Behavioral Contracts

1. **All non-base recipes at registered benches have their input quantities multiplied by 12.** This includes block recipes AND non-block item recipes (Rope, Bolt_Wool, etc.).
2. **Base recipes (all inputs are raw natural resources) are NOT scaled.** Their inputs are already implicitly 12× because natural blocks drop 12× (a recipe needing 1 wood trunk costs "1 trunk" but the player got 12 from gathering — the economy balances naturally).
3. **Each recipe is scaled exactly once**, regardless of how many bench registries it appears in. Identity-based deduplication prevents double-scaling (the ×144 bug).
4. **Registered benches are configurable.** Currently: `Builders`, `Furniture_Bench`. Future expansion can add `Workbench`, `Farmingbench`, etc.
5. **Salvage recipes are never scaled.** Recipe IDs starting with "Salvage" are excluded from all processing.

## "Base" Recipe Classification Logic

A recipe is classified as "base" if and only if:

For each input `MaterialQuantity`:
- If the input has a direct `ItemId`: that item must be in the `naturalItemIds` set (items dropped by natural blocks)
- If the input uses a `ResourceTypeId`: ALL items matching that resource type must be natural (checked via `isResourceTypeExclusivelyNatural`)

**Key insight from the Deco_Rope bug:** `Ingredient_Fibre` is NOT in `naturalItemIds`. No natural block drops `Ingredient_Fibre` — it is crafted from `Plant_Fiber` at a separate bench. Therefore `Deco_Rope`'s recipe (input: `1x Ingredient_Fibre`) should NOT be classified as base, and its cost MUST be scaled.

**Current bug confirmed by logs:**
```
[Builders] Base block recipes: [..., Deco_Rope_Recipe_Generated_0, ..., Deco_Rope_Diagonal_Recipe_Generated_0, ...]
Phase1 SKIP (baseBlock): Deco_Rope_Recipe_Generated_0
Phase1 SKIP (baseBlock): Deco_Rope_Diagonal_Recipe_Generated_0
```
The Deco_Rope recipe (input: `1x Ingredient_Fibre`) was classified as a base block recipe, meaning `allInputsNatural()` returned true for `Ingredient_Fibre`. This indicates `Ingredient_Fibre` is present in the `naturalItemIds` set — likely because some natural block has a gathering path that drops `Ingredient_Fibre` (or a drop list containing it). The classification is incorrect: `Ingredient_Fibre` is a processed crafting ingredient, and recipes using it should have their costs scaled.

## Edge Cases & Decisions

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| Recipe with 1 natural input + 1 processed input | NOT base — scale all inputs ×12 | "Base" requires ALL inputs to be natural |
| Recipe whose input resolves via ResourceTypeId to both natural and non-natural items | NOT base — `isResourceTypeExclusivelyNatural` returns false | Conservative: if any resolution is non-natural, it's not base |
| Non-block recipe with all natural inputs | Classified as base-item — NOT scaled | Same logic as base-block recipes |
| Recipe in both Builders and Furniture_Bench | Scaled once only (dedup) | Prevents ×144 bug |
