---
topic: "NPC Overview"
category: "NPCs"
updated: 2026-04-16
sources: ["hytale.com blog posts", "hytalemodding.dev"]
---

# NPC System Overview

## Summary

Hytale's NPC framework is primarily data-driven — most NPC behaviors are configured via JSON without writing Java code. The system uses roles, instruction lists, and a combat action evaluator to create complex behaviors.

## Core Concepts

### Roles

Every NPC has a **role** that defines its entire behavior set:

- General behavior (friendly, aggressive, passive)
- Movement patterns
- Items carried
- Visual appearance
- Combat style

Changing an NPC's behavior is as simple as changing its role. Templates like `Template_Animal_Neutral` and `Template_Predator` provide ready-made behavior sets.

### Instruction Lists

The behavior tree equivalent in Hytale. Each instruction has:

1. **Sensor** — queries game state to decide if this instruction can execute
2. **Actions** — what the NPC does if selected
3. **Motions** — how the NPC moves during the action
4. **Nested lists** — deeper decision trees

Instruction lists use **fallback selector** semantics:
- Instructions evaluated in order
- First matching instruction executes
- No further instructions are evaluated (unless flagged otherwise)

### Data-Driven Configuration

All NPC behavior is configured in JSON:

```json
{
  "Role": "Template_Predator",
  "Weapons": ["Sword_Iron"],
  "InstructionList": [
    {
      "Sensor": { "Type": "TargetInRange", "Range": 10 },
      "Action": { "Type": "Attack" },
      "Motion": { "Type": "ChaseTarget" }
    }
  ]
}
```

150+ element types (sensors, actions, motions) are available for combining into behaviors.

## Combat Action Evaluator

For sophisticated combat NPCs, the Combat Action Evaluator provides smart decision-making:

- Each attack has **conditions** (HP thresholds, distance, flanking detection)
- Conditions are weighed against each other
- Creates "fuzzy" behavior — NPCs don't always act predictably

Example: A Skeleton Praetorian can decide between blocking, summoning reinforcements at low health, charging, and basic attacks.

## Extending NPCs with Java

While JSON covers most NPC behavior, Java plugins can:

- Add new element types (sensors, actions, motions)
- Register custom instruction list processors
- Use messages and beacons for adjacent systems (taming, factions)

**Best practice**: Use the asset-driven NPC system first. Use Java only to extend what data can't do.

## Motion Controllers

| Controller | Status | Description |
|------------|--------|-------------|
| Walking | Stable | Ground-based movement |
| Flying | Stable | Aerial movement with takeoff/landing |
| Swimming | Experimental | Water movement (transitions not fully supported) |

## Current Limitations

- **Pathfinding** is slow in fully modifiable voxel worlds — can't pre-populate with jump points
- **NPCs can't break blocks** outside of projectile explosions (but can place them)
- **NPC physics** needs rework to unify with player systems
- **No visual editor** yet for NPC behavior configuration
- **Debugging** requires reading detailed log files

## Debug Commands

See [NPC Debug Commands](./debug-commands.md) for visualization flags.

## See Also

- [Combat Action Evaluator](./combat-evaluator.md)
- [NPC Debug Commands](./debug-commands.md)
- [Official NPC Documentation](https://hytalemodding.dev/en/docs/official-documentation/npc-doc)
