---
topic: "Combat Action Evaluator"
category: "NPCs"
updated: 2026-04-16
sources: ["hytale.com blog posts"]
---

# Combat Action Evaluator

## Summary

The Combat Action Evaluator (CAE) provides intelligent combat decision-making for NPCs. It evaluates conditions for each possible combat action and weighs them to determine the best course of action.

## How It Works

1. Each combat action (attack, block, summon, charge) has a set of **conditions**
2. Conditions query game state: HP level, target distance, flanking status
3. Actions are **weighed** against each other based on condition matches
4. The NPC selects the highest-weighted action

This creates "fuzzy" behavior — NPCs make semi-intelligent decisions rather than following rigid scripts.

## Example: Skeleton Praetorian

The Skeleton Praetorian uses the CAE to decide between:

- **Basic attacks** — default combat action
- **Blocking** — when taking damage
- **Summoning reinforcements** — when HP is low
- **Charging** — when target is at medium range

The summon ability might have a condition like:

```json
{
  "Type": "HealthBelow",
  "Threshold": 0.3,
  "Weight": 5.0
}
```

## Trade-offs

| Advantage | Disadvantage |
|-----------|--------------|
| More interesting combat encounters | Steeper learning curve |
| Less verbose configurations | NPCs may not always act as expected |
| Semi-intelligent decision-making | Less performant than simple instruction lists |
| Emergent behavior from weighted conditions | Harder to debug |

## When to Use

- Boss fights with multiple abilities
- NPCs that need to react differently to different situations
- Combat encounters that should feel "smart"

For simple NPCs (passive animals, basic enemies), instruction lists are sufficient.

## See Also

- [NPC Overview](./overview.md)
- [NPC Debug Commands](./debug-commands.md)
