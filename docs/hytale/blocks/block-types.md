---
topic: "Block Types"
category: "Blocks"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Block Types

## Summary

`BlockType` is the central definition for any block in Hytale. It defines the block's visual appearance, physical properties, gathering behavior, support rules, and more. Block types are defined in JSON item files and loaded into the `AssetMap` at startup.

## BlockType Properties

| Property | Type | Description |
|----------|------|-------------|
| `Material` | String | Physical material class: `Solid`, `Liquid`, `Gas` |
| `DrawType` | String | Rendering mode: `Cube`, `Model`, `Cross`, etc. |
| `Group` | String | Material group for shared behavior: `Stone`, `Wood`, `Leaves` |
| `Opacity` | String | Light behavior: `Solid`, `Transparent`, `Cutout` |
| `HitboxType` | String | Collision shape: `Full`, `Half`, `Fence`, `Bench_Architect`, etc. |
| `Gathering` | Object | How the block is broken and what it drops (see [Gathering](./gathering.md)) |
| `Textures` | Array | Texture definitions with optional weights |
| `CustomModel` | String | Path to `.blockymodel` file for non-cube blocks |
| `CustomModelTexture` | Array | Textures for custom models |
| `VariantRotation` | String | Rotation behavior: `NESW`, `Pipe`, etc. |
| `Flags` | Object | Block behavior flags |
| `Support` | Object | Structural support rules |
| `MaxSupportDistance` | Integer | Maximum distance for support propagation |
| `State` | Object | State definitions for variant blocks (e.g., stripped logs) |
| `BlockParticleSetId` | String | Particle effect when breaking |
| `ParticleColor` | String | Hex color for particles |
| `BlockSoundSetId` | String | Sound set for interactions |
| `BlockBreakingDecalId` | String | Visual breaking overlay |
| `Aliases` | Array | Alternative names for commands |
| `RequiresAlphaBlending` | Boolean | Whether alpha channel is used for transparency |
| `PlacementSettings` | Object | Controls block placement behavior |
| `RandomRotation` | String | Random orientation on placement |
| `Effect` | Array | Visual effects applied: `Wind`, etc. |
| `Bench` | Object | Workbench configuration (see [Bench Types](../crafting/bench-types.md)) |
| `Interactions` | Object | Use/primary/secondary interaction definitions |
| `Beds` | Array | Bed sleeping positions |

## Accessing BlockType at Runtime

```java
// Get the asset map (all loaded block types)
DefaultAssetMap<BlockType> assetMap = BlockType.getAssetMap();

// Get a specific block type by ID
BlockType stone = BlockType.getAssetMap().getAsset("Rock_Stone");

// Iterate all block types
for (var entry : BlockType.getAssetMap().getAssetMap().entrySet()) {
    BlockType bt = entry.getValue();
    String id = bt.getId();
    // ...
}
```

## Special Block Type IDs

| ID | Purpose |
|----|---------|
| `Empty` | Air / no block — skip in iteration |
| `Unknown` | Unresolved block type — skip in iteration |

## Block ID vs Block Type ID

- **Block Type ID** (`String`) — human-readable name like `"Rock_Stone"`, `"Wood_Oak_Trunk"`
- **Block ID** (`int`) — numeric identifier used internally and in packets

Convert between them:
```java
BlockType bt = BlockType.getAssetMap().getAsset(blockTypeId);      // String → BlockType
BlockType bt = BlockType.getAssetMap().getAsset(numericBlockId);   // int → BlockType
```

## Asset Inheritance

Block types can inherit from a parent using the `Parent` field in JSON:

```json
{
  "Parent": "Wood_Softwood_Planks",
  "BlockType": {
    "Textures": [
      { "Weight": 1, "UpDown": "BlockTextures/Wood_Hardwood_Planks.png" }
    ]
  }
}
```

**Critical gotcha**: When blocks inherit via `Parent`, they can share the same Java object instances for sub-objects like `BlockGathering`. Mutating a shared `BlockGathering` instance affects ALL blocks that reference it. Always clone before mutating.

## Texture Definitions

```json
"Textures": [
  {
    "All": "BlockTextures/Rock_Stone.png",
    "Weight": 2
  },
  {
    "Sides": "BlockTextures/Wood_Trunk_Oak_Side.png",
    "UpDown": "BlockTextures/Wood_Trunk_Oak_Top.png",
    "Weight": 1
  }
]
```

| Field | Description |
|-------|-------------|
| `All` | Same texture on all faces |
| `Sides` | Texture for N/S/E/W faces |
| `UpDown` | Texture for top and bottom faces |
| `Weight` | Probability weight when multiple texture variants exist |

## See Also

- [Block Gathering](./gathering.md)
- [Block States & Variants](./states-and-variants.md)
- [Block Type Format](../assets/formats/block-type.md)
