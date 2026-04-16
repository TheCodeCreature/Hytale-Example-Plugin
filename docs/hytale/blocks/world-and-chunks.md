---
topic: "World & Chunks"
category: "Blocks"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# World & Chunks

## Summary

Hytale's voxel world is divided into chunks organized in columns. The `World` class manages all world-level operations, while `ChunkStore` handles the actual voxel data.

## World Hierarchy

```
Universe
  └── World (one or more)
        ├── ChunkStore (voxel data)
        │     └── ChunkColumn (vertical stack of chunks)
        │           └── WorldChunk (16×16×16 block sections)
        └── EntityStore (entities via ECS)
```

## Key Types

| Type | Description |
|------|-------------|
| `World` | A single game world with blocks and entities |
| `ChunkStore` | Per-world voxel data storage |
| `ChunkColumn` | A vertical column of chunks at (x, z) |
| `WorldChunk` | A 16³ cube of blocks |
| `ChunkUtil` | Utility methods for chunk coordinate math |

## Coordinate Systems

### World Coordinates

- `Vector3d` — double-precision position (entity positions)
- `Vector3i` — integer position (block positions)
- `Vector3f` — float-precision (rotations, directions)

### Chunk Coordinates

Blocks are organized into 16×16×16 chunks:

- World position `(x, y, z)` maps to chunk `(x >> 4, y >> 4, z >> 4)`
- Block within chunk: `(x & 15, y & 15, z & 15)`

### Long-Packed Positions

For efficient storage and map keys, block positions are packed into a `long`:

```java
long packedPos = BlockPos.pack(x, y, z);
// Used as HashMap keys for O(1) position lookups
```

## World Operations

### Reading Blocks

```java
ChunkStore chunkStore = world.getChunkStore();
int blockId = chunkStore.getBlock(x, y, z);
BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
```

### Writing Blocks

Must be done on the world thread:

```java
world.execute(() -> {
    chunkStore.setBlock(x, y, z, newBlockTypeId);
});
```

### Sending Block Updates to Clients

Use `ServerSetBlock` packets to update client-side block rendering without modifying the actual world:

```java
ServerSetBlock packet = new ServerSetBlock(x, y, z, visualBlockTypeId);
playerRef.getPacketHandler().send(packet);
```

This is used for visual effects like transparency volumes where the client sees a different block than what's stored server-side.

## See Also

- [Server Architecture](../engine/server-architecture.md)
- [Block Types](./block-types.md)
