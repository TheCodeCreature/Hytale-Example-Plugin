---
topic: "Door Block Architecture"
category: "Blocks"
updated: 2026-04-19
sources: ["DoorInteraction.java (decompiled)", "BlockType.java (decompiled)", "BlockHarvestUtils.java (decompiled)", "FillerBlockUtil.java (decompiled)", "WorldChunk.java (decompiled)", "BlockIdWithoutHasBlockTypeTest.java", "TestDataSet.java"]
---

# Door Block Architecture

## Summary

Doors in Hytale are multi-block structures implemented via the **filler block** system. A door occupies multiple block positions (e.g., top and bottom halves) but is logically a **single BlockType**. The engine manages this through filler offsets stored per-block-position, not through separate block type definitions for each half. Doors have special properties (`IsDoor`, state-based variants, `DoorInteraction`) and use `hasBlockType=false` on their items — a pattern that has historically caused classification bugs.

## 1. Multi-Block Structure via Filler System

Doors are **NOT** implemented as separate top/bottom block types. Instead, they use Hytale's **filler block** system:

- A door is placed as a single `BlockType` at the **origin position** (typically the bottom block)
- The block's `HitboxType` defines a bounding box that protrudes beyond a single 1×1×1 cube (e.g., 1×2×1 for a standard door)
- `FillerBlockUtil` iterates over all block positions within the hitbox and fills each non-origin cell with the **same block type ID** plus a **packed filler offset** (x, y, z relative to origin)
- The filler value is stored per-block in `BlockSection.getFiller(x, y, z)`

```
┌─────────────┐  y+1  filler = pack(0, 1, 0)  ← same BlockType ID as origin
│  Door Top   │
│  (filler)   │
├─────────────┤
│ Door Bottom │  y+0  filler = 0 (NO_FILLER)  ← origin block
│  (origin)   │
└─────────────┘
```

**Source**: `FillerBlockUtil.java` — filler values are packed as 5-bit signed offsets per axis:
```java
// FillerBlockUtil constants
private static final int BITS_PER_AXIS = 5;
private static final int MASK = 31;
public static final int NO_FILLER = 0;
```

## 2. Breaking a Door — Filler Resolution

When a player breaks any part of a multi-block door, the engine resolves to the **origin block** before processing drops:

### In `BlockHarvestUtils.performBlockDamage()`:
```java
int filler = targetSection.getFiller(targetBlockPos.x, targetBlockPos.y, targetBlockPos.z);
int fillerX = FillerBlockUtil.unpackX(filler);
int fillerY = FillerBlockUtil.unpackY(filler);
int fillerZ = FillerBlockUtil.unpackZ(filler);
if (fillerX != 0 || fillerY != 0 || fillerZ != 0) {
    originBlock = originBlock.clone().subtract(fillerX, fillerY, fillerZ);
    // ... redirects damage to origin block
    blockGathering = targetBlockType.getGathering();  // re-fetches from origin
}
```

### In `WorldChunk.setBlock()` — filler cleanup on break:
When the origin block is removed, all filler blocks are automatically broken:
```java
FillerBlockUtil.forEachFillerBlock(hitboxAssetMap.getAsset(oldBlockType.getHitboxTypeIndex()).get(oldRotation), (x1, y1, z1) -> {
    if (x1 != 0 || y1 != 0 || z1 != 0) {
        // Break matching filler blocks
        this.breakBlock(blockX, blockY, blockZ, settingsWithoutFiller);
    }
});
```

**Key finding**: The filler blocks are broken with `settingsWithoutFiller` which includes the `| 16` flag (skip filler processing). This means **drops only come from the origin block** — filler blocks are silently removed without generating additional drops.

### Drop Flow for Multi-Block Doors
```
Player hits any block position of door
  ├── Is it a filler position?
  │     └── YES → Resolve to origin block position
  │     └── NO  → Already at origin
  ├── Apply damage to origin block
  ├── Block destroyed?
  │     ├── naturallyRemoveBlock() on origin
  │     │     ├── removeBlock() → WorldChunk.breakBlock(origin)
  │     │     │     └── Iterates filler positions → breaks them (no drops, settings | 16)
  │     │     └── getDrops() → single drop from origin's BlockGathering
  │     └── Result: ONE set of drops, all fillers cleaned up
  └── NOT destroyed → continue damaging
```

## 3. Asset Inheritance — Shared BlockGathering Instances

The `BlockType` codec for `Gathering` uses direct reference assignment on inheritance:

```java
// BlockType.java CODEC definition
.appendInherited(
    new KeyedCodec<>("Gathering", BlockGathering.CODEC),
    (blockType, s) -> blockType.gathering = s,
    blockType -> blockType.gathering,
    (blockType, parent) -> blockType.gathering = parent.gathering  // ← SHARED REFERENCE
)
```

**This means**: If `Door_Wood_Oak` inherits from a parent `Door_Wood` (or any parent that defines `Gathering`), and the child does NOT override `Gathering` in its JSON, **both parent and child share the exact same `BlockGathering` Java object instance**.

Similarly, `IsDoor` is inherited:
```java
(blockType, parent) -> blockType.isDoor = parent.isDoor
```

### Implications for Drop Modification

If a plugin mutates the `BlockGathering` on one door variant (e.g., setting `useDefaultDropWhenPlaced = true`), **all door variants sharing that gathering instance are affected**. The `NaturalDropModifier` in this codebase clones gatherings before mutation specifically to avoid this contamination.

## 4. UseDefaultDropWhenPlaced Behavior

Whether doors have `UseDefaultDropWhenPlaced` set in their asset JSON is **not confirmed from the raw JSON files in this workspace** — no door JSON files exist in `docs/Resources/`. However, the engine behavior is clear:

### From `BlockHarvestUtils.performBlockDamage()`:
```java
if (targetBlockType.getGathering().shouldUseDefaultDropWhenPlaced()) {
    BlockPhysics decoBlocks = chunkStore.getComponent(..., BlockPhysics.getComponentType());
    boolean isDeco = decoBlocks != null && decoBlocks.isDeco(targetBlockPos.x, targetBlockPos.y, targetBlockPos.z);
    if (isDeco) {
        itemId = null;       // ← forces default drop (block's own item)
        dropListId = null;
    }
}
```

### From `BlockType.canBePlacedAsDeco()`:
```java
public boolean canBePlacedAsDeco() {
    return this.ignoreSupportWhenPlaced
        || this.gathering != null && this.gathering.shouldUseDefaultDropWhenPlaced();
}
```

**Likely status**: Doors are craftable blocks — they are NOT natural resources. Based on the plugin's test data, doors are modeled with breaking configs that drop 1× of themselves (`"Door_Wood"` / `"Door_Wood_Oak"`). Whether vanilla door JSON sets `UseDefaultDropWhenPlaced` is unknown, but if the plugin's `DropScaler` applies `useDefaultDropWhenPlaced` to non-recipe blocks and doors have recipes, doors should be excluded from that modification.

## 5. Door Items — hasBlockType=false Pattern

Door items are a known special case where:
- `item.hasBlockType()` returns `false`
- `item.getBlockId()` returns a valid block type ID (e.g., `"Door_Wood"`)

### From test data:
```java
doorItem = item("Door_Wood", "Door_Wood", false, 100);  // hasBlockType=false
```

This means the item does NOT have an inline `BlockType` definition — it references an **external** block type definition. This is the same pattern used by rails.

### Historical bug:
Both `NaturalResourceRegistry` and `BlockRecipeRegistry` previously relied on `item.hasBlockType()` to detect block-placing items. Items with `hasBlockType=false` but a valid `blockId` were misclassified as non-block items, making their blocks appear "natural" and receive 12× scaled drops instead of 1× self-drops. This was fixed by also checking `item.getBlockId() != null`.

## 6. Door State Machine

Doors use Hytale's block state system with a `"Door"` state ID:

```java
// BlockType.processConfig() — doors get usable flag
if (this.state != null && "Door".equalsIgnoreCase(this.state.getId())) {
    this.flags.isUsable = true;
}
```

### Door States (from DoorInteraction.DoorState enum):
| State | Description |
|-------|-------------|
| `CLOSED` | Default state |
| `OPENED_IN` | Door opened inward relative to player |
| `OPENED_OUT` | Door opened outward relative to player |

### State transitions produce different block type variants:
```java
BlockType newBlockType = currentBlockType.getBlockForState(interactionStateToSend);
```

Each state (`OpenDoorIn`, `OpenDoorOut`, `CloseDoorIn`, `CloseDoorOut`, `DoorBlocked`) maps to a different `BlockType` variant that has different hitbox geometry but **shares the same base definition and gathering config**.

## 7. Double Doors

The engine supports **double doors** — two doors placed side-by-side that open/close together:

```java
private boolean checkForDoubleDoor(World world, Vector3i blockPosition, BlockType blockType,
    int rotation, DoorState fromState, DoorState stateDoubleDoor) {
    DoorInfo doorToOpen = getDoubleDoor(world, blockPosition, blockType, rotation, doorStateToCheck);
    // ... activates adjacent door with matching hitbox
}
```

Double doors are detected by checking for adjacent blocks with:
- Same `hitboxTypeIndex`
- Matching rotation (flipped yaw)
- `filler == 0` (must be an origin block, not a filler)
- `isDoor() == true`

## 8. Horizontal vs Vertical Doors

`DoorInteraction` has a `horizontal` property:

```java
private boolean horizontal;
// When horizontal=false: standard vertical door (detects player side for in/out)
// When horizontal=true: gate-style door (no in/out distinction)
```

Gates use the same `DoorInteraction` system with `horizontal=true`.

## 9. Recipe Category

Doors and trapdoors are crafted at the **Builders** bench under the `"Door"` and `"Trapdoor"` recipe categories:

```json
// From Bench_Builders.json
"recipeCategoryIds": ["Door", "Trapdoor"]
```

```json
// From PortableBench_Builders.json
"recipeCategoryIds": ["Gate", "Ladder", "Window", "Door", "Trapdoor", "Wardrobe"]
```

## Gotchas

1. **Shared BlockGathering**: Door variants inheriting from a parent share the same `BlockGathering` instance. Mutating one affects all. Always clone before modifying.

2. **hasBlockType=false**: Door items use external block references. Code that checks `hasBlockType()` to detect block-placing items will miss doors unless it also checks `getBlockId() != null`.

3. **Filler blocks don't produce drops**: Breaking a door only produces drops from the origin block. Filler positions are cleaned up silently. You will NOT see duplicate drops from top+bottom halves.

4. **State variants share gathering**: Door states (`OpenDoorIn`, `CloseDoorOut`, etc.) map to different `BlockType` variants via `getBlockForState()`. These variants likely share the same `BlockGathering` since they inherit from the same base definition.

5. **IsDoor is inherited**: The `isDoor` flag propagates through parent inheritance, so all variants of a door type correctly identify as doors.

## See Also

- [Block Types](./block-types.md) — BlockType inheritance and shared instances
- [Block Gathering](./gathering.md) — Drop resolution and UseDefaultDropWhenPlaced
- [Support & Physics](./support-and-physics.md) — Physics cascade and deco marking
- [Items](../items/items.md) — hasBlockType=false pattern
