---
topic: "Block Support & Physics"
category: "Blocks"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Block Support & Physics

## Summary

Hytale's block physics system handles structural support — when a block loses its support, it breaks and drops items. This creates cascading destruction when load-bearing blocks are removed.

## Support Rules

Blocks define support rules that determine what holds them in place:

```json
"MaxSupportDistance": 5,
"Support": {
  "Down": [
    {
      "AllowSupportPropagation": false,
      "FaceType": "Full",
      "Rotate": false
    }
  ],
  "Horizontal": [
    {
      "TagId": "Type=Trunk",
      "Rotate": false,
      "Support": "Ignored"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `MaxSupportDistance` | How far support can propagate from a foundation |
| `Down` | Support rules for the block below |
| `Horizontal` | Support rules for adjacent blocks |
| `FaceType` | Required face type for support: `Full`, etc. |
| `AllowSupportPropagation` | Whether support passes through this connection |
| `TagId` | Tag filter for which blocks count as support |
| `Support` | Override: `Ignored` means this direction doesn't matter |

## Physics Cascade

When a block loses support:

1. Block is destroyed via `BlockHarvestUtils.naturallyRemoveBlock()`
2. Physics drops are generated (using `PhysicsDropType` from gathering config)
3. Adjacent blocks re-check their support
4. If those blocks also lost support, the cascade continues

## SetBlockSettings Flags

When programmatically placing/removing blocks, flags control side effects:

| Flag | Description |
|------|-------------|
| `PERFORM_BLOCK_UPDATE` | Trigger support recalculation and physics |
| `NO_DROP_ITEMS` | Suppress item drops when breaking |

## BlockPhysics (Deco Tracking)

- `BlockPhysics.isDeco()` — returns `true` if a block was placed by a player
- `BlockPhysics.markDeco()` — marks a block as player-placed
- Used by `UseDefaultDropWhenPlaced` to differentiate world-generated vs player-placed blocks

## See Also

- [Block Types](./block-types.md)
- [Block Gathering](./gathering.md)
- [Drop Resolution](./drop-resolution.md)
