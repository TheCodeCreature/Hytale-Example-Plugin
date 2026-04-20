---
area: "Block Breaking & Crafting Loop"
updated: 2026-04-18
---

# Block Breaking — Behavioral Contract

## Player Experience Goal

When a player breaks any block, the result should feel **predictable and fair**. Natural blocks give generous yields. Crafted blocks give back what was put in. The player should never feel confused about why they got a different amount than expected.

## Behavioral Contracts

1. **When a player breaks a natural block**, they receive the block's configured drop quantity (scaled to 12×). This is the block's gathering `Breaking.Quantity` after modification.
2. **When a player breaks a crafted block** (one with a bench recipe), they receive the recipe's input materials at the scaled quantity, divided by the recipe's output quantity. If a recipe produces 2 walls from 24 stone, each wall drops 12 stone.
3. **When a player breaks a "base" crafted block** (recipe inputs are all raw natural resources), the block drops its own item at 1× quantity. The cost to place was already implicit in the natural gather scaling.
4. **Drop behavior is identical regardless of who placed the block.** There is no "player-placed vs. world-generated" distinction for crafted blocks in terms of what drops.
5. **Physics-cascade destruction produces the same drops as manual breaking.** Blocks don't vanish.
6. **Non-block items (Rope, Fibre, tools) do not have a "break" behavior** — they are inventory items. Their economy participation is limited to recipe cost scaling (Contract #3 in vision.md).

## Classification: What Is a "Natural" vs. "Crafted" Block?

| Classification | Definition | Example |
|---------------|------------|---------|
| **Natural block** | A block type with NO non-Salvage recipe at any registered crafting bench | `Rock_Stone`, `Wood_Oak_Trunk`, `Soil_Dirt` |
| **Crafted block (base)** | A block with a recipe where ALL inputs are exclusively raw natural resources | `Rock_Stone_Cobble` (from Rock_Stone), `Wood_Oak_Planks` (from Wood_Oak_Trunk) |
| **Crafted block (non-base)** | A block with a recipe where at least one input is a processed/crafted item | `Furniture_Temple_Wind_Chandelier` (requires Deco_Rope + Ingredient_Bar_Copper) |

### Critical Distinction: "Natural Item" vs "Base Recipe Input"

The `NaturalResourceRegistry.isNaturalItem()` check tells you if an item can be *dropped* by a natural block. This is necessary but NOT sufficient for classifying a recipe as "base."

A recipe is "base" only if ALL of its inputs resolve to items that are **exclusively** natural — meaning the item itself is a raw natural drop, not a processed intermediate.

**Example of the bug this contract prevents:**
- `Ingredient_Fibre` is listed as a natural item because some natural block drops it? **NO** — `Ingredient_Fibre` is NOT in `naturalItemIds`. It is a crafted ingredient.
- `Deco_Rope` has input `1x Ingredient_Fibre`. Is this a base recipe? **NO** — `Ingredient_Fibre` is a processed item, not a raw natural drop. `Deco_Rope`'s recipe cost MUST be scaled ×12.

## Edge Cases & Decisions

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| `Deco_Rope` recipe uses `Ingredient_Fibre` (a processed item) | Recipe cost scaled ×12, break drops 12× Ingredient_Fibre | Ingredient_Fibre is NOT a natural item — it's crafted from Plant_Fiber |
| `Deco_Rope_Diagonal` has same recipe pattern | Same treatment as `Deco_Rope` | Consistency across rope variants |
| `Thatch_Block` recipe uses `4x Plant_Fiber` (a raw natural drop) | Base recipe — cost NOT scaled, block drops itself at 1× | Plant_Fiber IS a natural item; all inputs are exclusively natural |
| Recipe at `Farmingbench` (e.g., Ingredient_Hay) | NOT scaled — Farmingbench is not a registered bench | Only registered benches participate |
| Block with recipe using `ResourceTypeId` (e.g., "Wood_Hardwood") | Resolved to a concrete item, then classified | ResourceTypeId resolution is independent of base classification |

## Anti-Patterns to Reject

- **Dropping the crafted block item itself** when a non-base crafted block is broken. The player should get ingredients back.
- **Using `allInputsNatural()` with the full `naturalItemIds` set** for base-recipe classification. The correct check must verify inputs against EXCLUSIVELY natural items, not items that happen to share an ID with a natural drop.
- **Treating `Ingredient_Fibre` as equivalent to `Plant_Fiber`** in any classification logic. They are distinct items with different sources.
