---
topic: "NPC Debug Commands"
category: "NPCs"
updated: 2026-04-16
sources: ["hytale.com blog posts"]
---

# NPC Debug Commands

## Summary

Hytale provides debug visualization flags for NPC development. These render in-world visualizations showing NPC state, targeting, sensor ranges, and more.

## Usage

```
/npc debug set <flag>
/npc debug presets       ← lists all available flags and preset sets
```

Flags are applied to individual NPCs while keeping them in your view. You can also add flags as a comma-separated list in the `Debug` property of the NPC role JSON.

## Visualization Flags

### VisAiming

Shows what NPCs are aiming at. Useful for NPCs with aim-based instructions (archers, ranged attackers).

### VisMarkedTargets

Visualizes all current marked targets locked onto by the NPC. Most standard templates use a single `LockedTarget` — the entity they're actively fighting. Useful for understanding target switching in crowds.

### VisSensorRanges

Shows all currently checked sensor ranges as rings and view sectors:

- **Hearing range** — circular ring
- **Vision range** — cone-shaped sector
- **Absolute detection** — spherical ring

Shows which entities are being matched by which sensors. Displayed as 2D rings/sectors but technically represent 3D spheres/cones.

Example: A bear sleeping shows only one sensor active. When awake, it shows hearing, vision, and absolute detection ranges.

### VisLeashPosition

Shows the NPC's current leash position — the point it returns to when disengaging.

### VisFlock

Visualizes the flock (group) associated with this NPC:

- Marks the flock leader
- Shows all NPCs currently in the flock

## See Also

- [NPC Overview](./overview.md)
- [Combat Action Evaluator](./combat-evaluator.md)
