---
area: "Crafting Cost Scaling"
updated: 2026-05-19
---

# Crafting Cost Scaling — Behavioral Contract

## Player Experience Goal

When a player opens a bench and sees a recipe cost, it should make sense relative to what they gathered. "I got 12 stone from one block, and this wall costs 48 stone — so that's 4 blocks worth of stone." The math should always feel proportional and predictable.

## Behavioral Contracts

1. **Each input in a recipe is independently classified and scaled.** Raw material inputs (items that are exclusively natural drops) are scaled ×12. Crafted intermediate inputs (items produced by another recipe) retain their vanilla quantities. This per-input model applies to ALL recipes at registered benches — block outputs AND non-block item recipes (Rope, Bolt_Wool, etc.).
2. **Base recipes (all inputs are raw natural resources) are NOT scaled.** Their inputs are already implicitly 12× because natural blocks drop 12× (a recipe needing 1 wood trunk costs "1 trunk" but the player got 12 from gathering — the economy balances naturally).
3. **Each recipe is scaled exactly once**, regardless of how many bench registries it appears in. Identity-based deduplication prevents double-scaling (the ×144 bug).
4. **Registered benches are configurable.** Currently: `Builders`, `Furniture_Bench`. Future expansion can add `Workbench`, `Farmingbench`, etc.
5. **Salvage recipes are never scaled.** Recipe IDs starting with "Salvage" are excluded from all processing.

## "Base" Recipe Classification Logic

A recipe is classified as "base" if and only if:

For each input `MaterialQuantity`:
- If the input has a direct `ItemId`: check if that item is in the `naturalItemIds` set. If yes → scale ×12. If no (crafted intermediate) → retain vanilla quantity.
- If the input uses a `ResourceTypeId`: if ANY matching item is a raw natural drop → scale ×12. If ALL matching items are crafted intermediates → retain vanilla quantity.

**Key insight — leaf-only scaling:** `Ingredient_Fibre` is NOT in `naturalItemIds`. It is crafted from `Plant_Fiber` at a separate bench. Under the leaf-only model, `Deco_Rope`'s recipe input of `1× Ingredient_Fibre` is classified as a crafted intermediate and retains its vanilla quantity (1×). The scaling was already applied when `Plant_Fiber` was consumed to craft `Ingredient_Fibre` — scaling again would double-count.

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
| Recipe with 1 natural input + 1 processed input | Scale natural input ×12, retain processed input at vanilla quantity | Per-input classification — each input is scaled independently based on its own classification |
| Recipe whose input resolves via ResourceTypeId to both natural and non-natural items | Scale ×12 — if ANY matching item is a raw natural drop, the input is scaled | Generous: if any resolution is natural, the input participates in scaling |
| Non-block recipe with all natural inputs | Classified as base-item — NOT scaled | Same logic as base-block recipes |
| Recipe in both Builders and Furniture_Bench | Scaled once only (dedup) | Prevents ×144 bug |
| Salvage recipe (ID starts with "Salvage") | Never scaled — excluded from all processing | Salvage recipes are recovery paths, not crafting costs |
| Dual-identity item (both dropped by a natural block AND craftable at a bench) | Classified as raw if it appears in `naturalItemIds` | The natural-drop identity takes precedence for scaling purposes |
