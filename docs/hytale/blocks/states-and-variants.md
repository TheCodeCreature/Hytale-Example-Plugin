---
topic: "Block States & Variants"
category: "Blocks"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Block States & Variants

## Summary

Blocks can have multiple states that change their visual appearance, hitbox, gathering behavior, and other properties. States are defined in the block type JSON and can be triggered by tool interactions or other game events.

## State Definitions

States are defined under `BlockType.State.Definitions`:

```json
"State": {
  "Definitions": {
    "Stripped": {
      "Textures": [
        {
          "Sides": "BlockTextures/Stripped_Log_Side.png",
          "UpDown": "BlockTextures/Stripped_Log_Side.png"
        }
      ],
      "Gathering": {
        "Breaking": {
          "GatherType": "Woods",
          "ItemId": "Wood_Stripped_Deco"
        }
      }
    },
    "Corner": {
      "CustomModel": "Blocks/Structures/Fences/Fence_Corner.blockymodel",
      "HitboxType": "Fence_Corner_Thin",
      "FlipType": "OrthogonalInverse"
    },
    "T": {
      "CustomModel": "Blocks/Structures/Fences/Fence_T.blockymodel",
      "FlipType": "Symmetric"
    }
  }
}
```

Each state can override:
- `Textures` — different visual appearance
- `Gathering` — different drop behavior in this state
- `CustomModel` — different 3D model
- `HitboxType` — different collision shape
- `FlipType` — symmetry behavior for placement

## Tool-Triggered States

Tools can transition a block to a different state:

```json
"Gathering": {
  "Tools": [
    {
      "Type": "Scraper",
      "State": "Stripped",
      "DropList": "Bark"
    }
  ]
}
```

Using a Scraper tool on an oak trunk:
1. Drops items from the "Bark" drop list
2. Transitions the block to the "Stripped" state
3. The stripped state has its own textures and gathering behavior

## Variant Rotation

The `VariantRotation` field controls how blocks orient when placed:

| Value | Description |
|-------|-------------|
| `NESW` | 4 cardinal directions (furniture, benches) |
| `Pipe` | 3-axis rotation for logs/pillars |
| `YawPitchRollStep1` | Full rotation freedom |

## See Also

- [Block Types](./block-types.md)
- [Block Gathering](./gathering.md)
