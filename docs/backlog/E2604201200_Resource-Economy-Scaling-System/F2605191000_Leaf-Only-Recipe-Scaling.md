---
id: F2605191000
type: feature
title: "Leaf-Only Recipe Scaling"
status: in-progress
priority: critical
epic: E2604201200
created: 2026-05-19
---

# Leaf-Only Recipe Scaling

## Description
Fix the exponential cost explosion in the 12× scaling system. Currently, Phase 1 of DropScaler applies a flat ×12 multiplier to ALL recipe inputs, causing multi-tier crafting chains to compound exponentially (12² = 144× at tier 2, 12³ = 1728× at tier 3). The fix: only scale recipe inputs that are raw materials (natural drops). Crafted intermediate inputs retain their vanilla quantities. The multiplier stays at 12.

## Acceptance Criteria

### Checklist
- [ ] Recipe inputs that are raw materials (natural drops) are scaled ×12
- [ ] Recipe inputs that are crafted intermediates retain vanilla quantities
- [ ] An item is classified as "crafted" if it appears as the output of any non-Salvage recipe
- [ ] Items that are both natural drops AND crafting outputs are treated as raw
- [ ] Salvage recipe outputs do NOT mark an item as "crafted"
- [ ] `ResourceTypeId` inputs: if any matching item is raw, classify as raw and scale
- [ ] Multi-tier crafting chains produce exactly 12× vanilla total raw cost (not exponential)
- [ ] Place-break loop is lossless at every tier
- [ ] All downstream systems unchanged (PlaceBlockCostUtil, AbstractBenchProcessor, StencilPlacementSystem, RecipeAffordabilityResolver, bench UI)
- [ ] No compilation errors

### Scenarios
**Single-Tier Raw Recipe**
- **Given** Vanilla recipe: 1 stone → 1 brick
- **When** DropScaler Phase 1 runs
- **Then** Scaled recipe: 12 stone → 1 brick (stone is raw, scaled ×12)

**Multi-Tier Crafted Input**
- **Given** Vanilla recipe: 3 bricks → 1 brick_stairs
- **When** DropScaler Phase 1 runs
- **Then** Scaled recipe: 3 bricks → 1 brick_stairs (brick is crafted, NOT scaled)
- **And** Total raw cost: 3 × 12 = 36 stone = 12× vanilla ✓

**Mixed Raw + Crafted Inputs**
- **Given** A recipe with 1 raw input and 1 crafted input
- **When** DropScaler Phase 1 runs
- **Then** Raw input is scaled ×12, crafted input stays at vanilla quantity

**Deco_Rope (Crafted Intermediate)**
- **Given** Recipe: 1 Ingredient_Fibre → 1 Deco_Rope
- **When** DropScaler Phase 1 runs
- **Then** Recipe stays: 1 Ingredient_Fibre → 1 Deco_Rope (Ingredient_Fibre is crafted)
- **And** Breaking Deco_Rope returns 1 Ingredient_Fibre

**Raw Recipe with Multi-Output**
- **Given** Vanilla recipe: 1 wood → 4 sticks
- **When** DropScaler Phase 1 runs
- **Then** Scaled recipe: 12 wood → 4 sticks (wood is raw)
- **And** Per-unit cost: 12/4 = 3 wood per stick (clean integer)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605191005 | Build RecipeTierClassifier | done |
| S2605191010 | Modify DropScaler Phase 1 for Leaf-Only Scaling | done |
| S2605191015 | Update Vision Contracts for Leaf-Only Scaling | done |
| S2605191020 | Add Duplicate Recipe Guard to scaleCraftingCosts | done |
| S2605191025 | Remove Dead baseBlockRecipeIds from Test Infrastructure | done |

## Notes
- Design doc: `docs/design-linear-scaling-fix.md`
- Proposals doc: `docs/proposals-scaling-system-revision.md`
- Supersedes and cancels F2604272100 (Simplified Economy Pipeline) — that feature proposed uniform scaling, which conflicts with leaf-only scaling
- Multiplier stays at 12 (best integer divisibility for output quantities 1,2,3,4,6)
