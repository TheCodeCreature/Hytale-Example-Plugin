---
area: "Building Tools"
updated: 2026-04-20
source: "docs/product/vision-building-tools.md"
---

# Building Tools — Behavioral Contract

## Contracts

### Placement Behavior
1. The PlaceBlock is never consumed by placement. Only inventory resources are spent.
2. Each placement costs exactly one recipe's worth of scaled inputs — identical to bench crafting.
3. Blocks placed via PlaceBlock are indistinguishable from blocks crafted-then-placed normally.

### Visual Feedback
4. The PlaceBlock's visual state always reflects current inventory truth (blue/green/red). No stale states.

### Assignment
5. Recipe assignment is free and non-destructive — zero material cost to assign, change, or clear.
6. All assignment routes (F-key menu, Assignment Bench, Pocket Bench) produce identical outcomes.

### Safety
7. A PlaceBlock with no recipe (blue) does nothing on right-click. The world is never modified.
8. A PlaceBlock in red state (armed, no resources) does not place. Right-click is cancelled.
9. A PlaceBlock cannot be armed with non-block recipes.

### Persistence
10. PlaceBlock metadata survives disconnection, death, and inventory transfer.

### Economy
11. The PlaceBlock respects all existing Resource Economy contracts (#1–#9).
