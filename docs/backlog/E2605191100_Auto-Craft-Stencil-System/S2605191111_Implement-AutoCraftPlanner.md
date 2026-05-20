---
id: S2605191111
type: story
title: "Implement AutoCraftPlanner"
status: backlog
priority: high
feature: F2605191110
epic: E2605191100
created: 2026-05-19
---

# Implement AutoCraftPlanner

## User Story
As a **stencil placement system**, I want **a planner that computes the optimal consumption list for a recipe given the player's inventory** so that **I can atomically consume the right materials**.

## Acceptance Criteria

### Checklist
- [ ] `plan()` returns direct consumption plan on fast path
- [ ] `plan()` computes per-ingredient deficit on slow path
- [ ] Existing intermediates preferred over raw material resolution (Contract #16)
- [ ] ResourceTypeId inputs: enumerate all matching variants, use existing stock of any variant first
- [ ] Multi-recipe items: select cheapest affordable recipe for deficit resolution
- [ ] Deficits for crafted items resolved via `RecipeTreeResolver.resolveItemToRaw()`
- [ ] Raw material needs aggregated across all deficits (handles overlap with direct raw inputs)
- [ ] Plan marked unaffordable when raw materials insufficient
- [ ] Unresolvable items (no recipe path to raw) treated as terminal — must be in inventory
- [ ] Auto-craft chat message: `"§a[Stencil] Placed {block} (auto-crafted {N}× {item} from {M}× {raw})"`

### Scenarios
**Overlapping raw materials**
- **Given** recipe needs 3 bricks + 2 cobblestone, player has 0 bricks, 50 cobblestone
- **When** `plan()` is called
- **Then** total cobblestone consumption = 36 (auto-craft) + 2 (direct) = 38

**Unresolvable intermediate**
- **Given** recipe needs 1 mob-drop-only item, player has 0
- **When** `plan()` is called
- **Then** returns unaffordable plan

## Notes
Skeleton file at `src/main/java/com/CodeCreature/crafting/AutoCraftPlanner.java`.
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §4.2, §6.2
