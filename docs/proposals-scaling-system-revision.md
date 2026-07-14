# Proposals: Resource Economy Scaling System Revision

**Created:** 2026-05-19  
**Status:** Awaiting Decision  
**Stakeholders:** Product Owner, Architect, Engineer

---

## Executive Summary

The current 12× scaling system has a **critical bug**: it applies the multiplier as a flat pass over ALL recipe inputs, causing exponential cost explosion on multi-tier crafting chains. A recipe requiring 3 bricks (each costing 1 stone) should cost 36 stone at 12×, but currently costs 432 stone (12² = 144×). Every additional crafting tier multiplies the real cost by another 12×.

This document presents 4 proposals to fix this, evaluated by both the Product Owner (vision alignment) and the Architect (technical feasibility).

---

## The Problem Illustrated

```
Vanilla Hytale:
  Mine 1 stone → get 1 cobblestone
  Craft 1 cobblestone → 1 brick  
  Craft 3 bricks → 1 brick stairs
  Total: 3 stone = 1 brick stairs

Current 12× System (BROKEN):
  Mine 1 stone → get 12 cobblestone         (Phase 3b: drops ×12)
  Craft 12 cobblestone → 1 brick            (Phase 1: inputs ×12)
  Craft 36 bricks → 1 brick stairs          (Phase 1: inputs ×12 AGAIN)
  Total: 36 × 12 = 432 cobblestone = 1 brick stairs
  
  That's 144× vanilla, not 12×. Each tier compounds by 12×.

Desired Behavior:
  The total raw material cost of any item should be exactly M× its vanilla cost,
  regardless of how many crafting tiers it passes through.
```

### Why 12× Was Chosen

The multiplier 12 was selected because it's the LCM of common recipe output quantities:

| Output Qty | 12 ÷ Output | Clean Integer? |
|-----------|-------------|----------------|
| 1 | 12 | ✓ |
| 2 | 6 | ✓ |
| 3 | 4 | ✓ |
| 4 | 3 | ✓ |
| 6 | 2 | ✓ |

This ensures `perUnitCost = max(1, scaledInputQty / outputQty)` always produces a whole number, which is critical because the stencil system consumes per-placement costs and can't handle fractions.

### Why 10× Has Integer Problems

| Output Qty | 10 ÷ Output | Clean Integer? |
|-----------|-------------|----------------|
| 1 | 10 | ✓ |
| 2 | 5 | ✓ |
| 3 | 3.33 | **✗** |
| 4 | 2.5 | **✗** |
| 5 | 2 | ✓ |
| 6 | 1.67 | **✗** |

Switching to 10× would break any recipe with 3, 4, or 6 output quantity — the per-unit cost would be fractional, and `Math.max(1, floor(10/4))` = 2, creating a lossy break loop (place costs 2, break returns 2, but 2×4 = 8 ≠ 10).

---

## Inviolable Contracts (Product Owner)

Regardless of which proposal is adopted, these player-facing guarantees must hold:

| # | Contract | Rationale |
|---|----------|-----------|
| **C1** | **Gather→Craft→Place→Break is a lossless closed loop.** Whatever the player spends to place a block, they get back when they break it. | Trust relationship between player and economy. |
| **C2** | **Multi-tier crafting preserves vanilla cost ratios.** A Tier-N item costs M× its vanilla raw material cost, not M^N. | Exponential costs make mid-to-late content inaccessible. |
| **C3** | **Per-placement costs are legible.** The Stencil Crafting and Stencil Radial must display costs a player can mentally trace back to "how many blocks do I mine?" | If costs are incomprehensible, the building tool fails. |
| **C4** | **Granularity enables sub-block precision.** Recipes that produce N items from 1 input must resolve to whole-number per-unit costs. | Stencil per-placement consumption can't handle fractions. |
| **C5** | **The system is invisible to the player.** No lag, no unexplained mismatches, no economy artifacts. | The economy is infrastructure, not a visible feature. |
| **C6** | **Break returns recipe ingredients, not raw materials** (for crafted blocks). Breaking stairs returns bricks, not cobblestone. | Ingredient-return enables fluid rebuilding within the crafting tier. |

---

## Proposal A: Leaf-Only Scaling (RECOMMENDED)

**One-line:** Only scale recipe inputs that are raw materials; skip inputs that are crafted intermediates.

### How It Works

Before scaling a recipe input in Phase 1, check whether that input item is itself produced by another crafting recipe. If yes — it's an intermediate — skip it. If no recipe produces it — it's a raw material — scale by M.

```
Classification at load time:
  cobblestone → no recipe produces it → RAW     → scale ×12
  brick       → recipe produces it    → CRAFTED → skip
  wood_planks → recipe produces it    → CRAFTED → skip

Tier 1 recipe (raw inputs):     12 cobblestone → 1 brick        ✓ (scaled)
Tier 2 recipe (crafted inputs): 3 bricks → 1 brick_stairs       ✓ (NOT scaled)
Total raw cost: 3 × 12 = 36 cobblestone = 12× vanilla           ✓
```

### Integer Math Proof

| Vanilla Recipe | Input Type | Scaled Input | Per-Unit Cost | Drop Qty | Net |
|---|---|---|---|---|---|
| 1 stone → 1 brick | raw | 12 stone → 1 brick | 12 | 12 | ✓ lossless |
| 2 stone → 1 slab | raw | 24 stone → 1 slab | 24 | 24 | ✓ lossless |
| 1 wood → 2 ladders | raw | 12 wood → 2 ladders | 6 | 6 | ✓ lossless |
| 1 wood → 4 sticks | raw | 12 wood → 4 sticks | 3 | 3 | ✓ lossless |
| 3 brick → 1 stairs | crafted | 3 brick → 1 stairs | 3 | 3 | ✓ lossless |
| 4 brick → 1 wall | crafted | 4 brick → 1 wall | 4 | 4 | ✓ lossless |
| **Multi-tier total** | | 1 stairs = 3 brick = 36 stone | | | **12× vanilla** ✓ |

### What Changes

| Component | Change |
|-----------|--------|
| NEW: `RecipeTierClassifier.java` | Builds set of item IDs that are crafting recipe outputs |
| `DropScaler.scaleCraftingCosts()` | Add `classifier.isCraftedItem(inputId)` check — skip if true |
| `ResourceConstants.java` | **No change** — stays at 12 |
| All downstream systems | **No change** — `PlaceBlockCostUtil`, `AbstractBenchProcessor`, `StencilPlacementSystem`, affordability, bench UI all unchanged |

### Downstream Impact: None

All downstream systems read `recipe.getInput()` — they don't care whether the input was scaled or not. They compute `perUnitCost = max(1, inputQty / outputQty)` on whatever values are in the recipe. Because the recipe values are now correct, all downstream math is automatically correct.

### Tradeoffs

| Strengths | Risks |
|-----------|-------|
| Simplest fix — 1 new class, 1 modified method | Must correctly classify `ResourceTypeId` inputs (abstract → concrete resolution) |
| No multiplier change — best integer divisibility (12) | Items that are both natural AND craftable need careful handling |
| All downstream systems untouched | Stencil Crafting shows small numbers for crafted inputs (e.g., "3 brick") next to large raw-input numbers (e.g., "12 stone") — minor UX inconsistency |
| Preserves crafting identity — break returns intermediates | |
| Low risk — easy to roll back | |

### Product Owner Verdict: **ALIGNED**

Fixes the root cause. Preserves all 6 contracts. The only vision contract that needs updating is the one that says "all recipe inputs are scaled by 12×" — it must become "raw material inputs are scaled by 12×; crafted intermediate inputs retain vanilla quantities."

---

## Proposal B: Tier-Aware DAG Scaling

**One-line:** Build a recipe dependency graph, assign tiers, and apply scaling only at the leaf tier.

### How It Works

1. Build a directed acyclic graph (DAG) of recipe dependencies
2. Topological sort to assign tiers: tier 0 = raw materials only, tier 1 = uses tier-0 outputs, etc.
3. Scale tier-0 recipe inputs by M×
4. Tier-N (N>0) crafted inputs keep vanilla quantities

This is mathematically identical to Proposal A but uses a formal graph structure instead of a simple "is this item crafted?" predicate.

### What Changes

| Component | Change |
|-----------|--------|
| NEW: `RecipeDependencyGraph.java` | Builds DAG, topological sort, tier assignment |
| NEW: `TierAwareScaler.java` | Tier-based scaling logic |
| `DropScaler.scaleCraftingCosts()` | Replace flat scaling with tier-aware |
| `ResourceConstants.java` | Optional — could change to 10 but breaks 1→3, 1→4, 1→6 recipes |

### Tradeoffs

| Strengths | Risks |
|-----------|-------|
| Mathematically rigorous — explicit tier model | Over-engineered — DAG/topological sort for a problem A solves with a predicate |
| Extensible to arbitrary chain depth | Higher complexity, more code to maintain |
| Good debugging visibility (can visualize DAG) | Must handle `ResourceTypeId` resolution within graph edges |

### Product Owner Verdict: **ALIGNED but unnecessary**

Same fix as Proposal A with more complexity. Only justified if Hytale later introduces 4+ tier crafting chains where formal tier analysis adds value.

---

## Proposal C: Flatten All Recipes to Raw Materials

**One-line:** Recursively resolve every recipe's inputs to raw materials, then scale the flat raw cost by M×.

### How It Works

For each recipe, recursively expand crafted-item inputs until only raw materials remain:

```
stairs recipe: 3 bricks
  → expand brick: 1 stone each  
  → 3 × 1 stone = 3 stone (raw)
  
Flattened: 3 stone → 1 stairs
Scaled:    36 stone → 1 stairs
```

All recipes become single-tier with only raw material inputs.

### Critical Problem: Breaks Contract C6

Breaking stairs would return 36 cobblestone — not 3 bricks. This fundamentally changes the gameplay: players never get intermediate items back. The crafting hierarchy becomes invisible. A player who pre-crafted bricks can't use them for stairs because the system only understands raw cobblestone.

### Tradeoffs

| Strengths | Risks |
|-----------|-------|
| Guaranteed linear — impossible to compound | **Destroys crafting identity** — no intermediates in economy |
| Simple mental model — everything costs raw materials | **Massive gameplay change** — all break drops change |
| | **Many downstream systems affected** — drops, affordability, UI |
| | Recursive flattener must handle `ResourceTypeId`, cycles, multi-output |
| | **High risk** — effectively a full economy rewrite |

### Product Owner Verdict: **VIOLATION**

Breaks Contract C6 (break returns intermediates). Destroys the "gather intermediate → use intermediate" flow. Players lose the ability to work with crafted materials.

---

## Proposal D: Resource Token Economy (10×)

**One-line:** Create new "Resource Token" items per material family; natural blocks drop 10 tokens; all recipes consume tokens directly.

### How It Works

1. Create new item assets: `Token_Stone`, `Token_Wood_Oak`, etc.
2. Stack sizes set to 10,000
3. Natural blocks drop 10 tokens of their material type
4. ALL recipe inputs rewritten to consume tokens (flattened to raw):
   ```
   10 Token_Stone → 1 brick
   30 Token_Stone → 1 brick stairs  (flattened: 3 × 10)
   ```
5. Breaking any block returns its token cost

### Critical Problems

1. **Integer math breaks at 10×** — `10/3 = 3.33`, `10/4 = 2.5`, `10/6 = 1.67`. Any recipe producing 3, 4, or 6 outputs has fractional per-unit costs.
2. **Same as Proposal C** — this is Proposal C with a different denomination item. All the same contract violations apply.
3. **Massive scope** — new item assets, new registry, new recipe rewriting system, new UI displays, all existing systems affected.
4. **Natural blocks no longer drop themselves** — placing a stone block would require tokens, not stone items. The entire `PlacementCostScaler` system would need rethinking.

### Tradeoffs

| Strengths | Risks |
|-----------|-------|
| High stack limits solve inventory pressure | **Highest complexity** — new item types, assets, registry |
| Clean mental model — "everything costs tokens" | **Integer math breaks at 10×** for output qty 3, 4, 6 |
| | **Massive gameplay redesign** — not a bug fix, a new economy |
| | Breaks Contract C6 (same as Proposal C) |
| | Existing player inventories need migration |

### Product Owner Verdict: **VIOLATION**

Same issues as Proposal C plus integer math failure. This is a full economy redesign, not a fix for the exponential bug. The 10× multiplier specifically was flagged as problematic — 12 was chosen for its superior divisibility.

---

## Comparison Matrix

| Criterion | A: Leaf-Only | B: Tier-Aware | C: Flatten | D: Tokens |
|-----------|:-----------:|:------------:|:----------:|:---------:|
| **Fixes exponential bug** | ✓ | ✓ | ✓ | ✓ |
| **Preserves all contracts** | ✓ | ✓ | ✗ (C6) | ✗ (C4, C6) |
| **Integer math clean** | ✓ (M=12) | Depends on M | ✓ (M=12) | ✗ (M=10) |
| **Downstream systems unchanged** | ✓ | ✓ | ✗ | ✗ |
| **Files changed** | 2 | 3 | 4+ | 8+ |
| **Gameplay feel** | Vanilla-like | Vanilla-like | Different | Very different |
| **Complexity** | Low | Medium-High | High | Very High |
| **Risk** | Low | Medium | High | Very High |
| **Reversibility** | Easy | Easy | Hard | Very Hard |
| **PO Verdict** | **ALIGNED** | ALIGNED | VIOLATION | VIOLATION |

---

## Recommendation

**Proposal A (Leaf-Only Scaling)** is the clear choice:

1. **Fixes the root cause** with minimal blast radius — one new predicate in Phase 1
2. **Keeps the multiplier at 12** — superior integer divisibility for all common recipe shapes
3. **Zero downstream changes** — `PlaceBlockCostUtil`, `AbstractBenchProcessor`, `StencilPlacementSystem`, affordability resolver, bench UI are all completely unchanged
4. **Preserves crafting identity** — breaking stairs returns bricks, not raw stone
5. **Low risk** — if classification is wrong for an edge case, the worst outcome is one recipe being over-scaled (fixable by exception list)

### Why Not 10×?

The 12× multiplier is specifically better because:
- Divisible by 1, 2, 3, 4, 6, 12 — covers all common Hytale recipe output quantities
- 10× fails for output quantities 3, 4, and 6 — creating fractional per-unit costs that break the stencil system
- The exponential problem is caused by **flat scaling**, not the multiplier value — changing from 12 to 10 does not fix the compounding

### Why Not Tokens / Flattening?

- Both destroy the crafting intermediate economy (Contract C6 violation)
- Both are full economy redesigns, not bug fixes
- Tokens add massive content authoring burden (new item assets, icons, names)
- The exponential problem is solvable with a one-predicate fix — a redesign is unnecessary

---

## Open Questions for Decision

1. **Items that are both natural AND craftable** (e.g., processing recipes like stonecutter: cobble → cobble). Should inputs from these self-referential recipes be scaled or not? Current recommendation: treat the *input* as raw if it's a natural drop, regardless of whether it also appears as an output elsewhere.

2. **Salvage recipes**: Outputs of salvage recipes should NOT mark an item as "crafted" — salvage is reverse-crafting, not forward-crafting.

3. **`ResourceTypeId` inputs in multi-tier recipes**: If a recipe uses an abstract `ResourceTypeId` (e.g., "Rock") as input and both raw stone and crafted stone bricks match it, should the input be classified as raw or crafted? Conservative approach: if *any* matching item is raw, classify as raw and scale.

4. **Vision contract updates**: The vision documents that state "all recipe inputs are scaled by 12×" must be revised to "raw material inputs are scaled by 12×; crafted intermediate inputs retain vanilla quantities." This should happen before implementation begins.

5. **Stack size concern**: With Proposal A and M=12, maximum quantities in inventory are `vanilla_qty × 12`, which the current stack-size scaling already handles. No additional stack-size changes are needed. If very high stack limits (10,000+) are still desired for other gameplay reasons, that's a separate feature.

---

## Next Steps (if Proposal A is approved)

1. Update vision contracts (Product Owner)
2. Architect designs `RecipeTierClassifier` and Phase 1 modification
3. Engineer implements
4. Test Designer validates with multi-tier crafting chains
5. Code Reviewer verifies place-break symmetry at all tiers
