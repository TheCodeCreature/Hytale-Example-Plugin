# V2 Parametric Voxel Grid Shape Builder — Pipeline Review

## Table of Contents

- [Executive Summary](#executive-summary)
- [Architecture Overview](#architecture-overview)
- [File Inventory](#file-inventory)
- [Core Pipeline Flow](#core-pipeline-flow)
- [Class-by-Class Analysis](#class-by-class-analysis)
  - [ShapeCompositorV2](#shapecompositorv2)
  - [ShapeOperationV2](#shapeoperationv2)
  - [OperationTypeV2](#operationtypev2)
  - [ComposedRegionV2](#composedregionv2)
  - [BlockFillTypeV2 (Interface + Implementations)](#blockfilltypev2-interface--implementations)
  - [DebugStyle](#debugstyle)
  - [ShapeCompositorPresetsV2](#shapecompositorpresetsv2)
  - [CameraTransparencyVolumeV2](#cameratransparencyvolumev2)
- [Supporting Classes (Shared with V1)](#supporting-classes-shared-with-v1)
  - [TransformedShape](#transformedshape)
  - [TransformFlags](#transformflags)
  - [CircularCone](#circularcone)
  - [BlockSnapshot](#blocksnapshot)
- [Design Patterns Identified](#design-patterns-identified)
- [Pipeline Execution Detail](#pipeline-execution-detail)
  - [Operation Priority and Timeline](#operation-priority-and-timeline)
  - [Transform Application Chain](#transform-application-chain)
  - [Diff-Based Rendering](#diff-based-rendering)
- [CAD Analogy Mapping](#cad-analogy-mapping)
- [Strengths](#strengths)
- [Potential Issues and Recommendations](#potential-issues-and-recommendations)

---

## Executive Summary

The V2 system implements a **parametric constructive solid geometry (CSG) pipeline** for voxel grids. It mirrors the workflow of parametric CAD programs (Fusion 360, SolidWorks) where a **timeline of ordered operations** (define, fill, cut, intersect, subtract, exclude) builds complex geometry from simpler primitive shapes. Each operation is named, referenceable, independently toggleable, and carries its own transform flags and visual debug metadata.

The current primary use case is a **camera transparency volume** — making blocks transparent around a third-person camera so that the player's view isn't obstructed — but the underlying shape compositor is general-purpose and could drive any voxel-based shape editing workflow.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    CameraTransparencyVolumeV2                │
│   (Per-player runtime loop: polls position/rotation,        │
│    drives compositor, applies diff to client packets)        │
└──────────────────────────┬──────────────────────────────────┘
                           │ owns
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     ShapeCompositorV2                        │
│   Anchor (world pos) + Yaw/Pitch rotation state             │
│   Ordered map of ShapeOperationV2 entries (the "timeline")  │
│   OperationBuilder (fluent inner class)                     │
│   compose(ChunkStore) → ComposedRegionV2                    │
└──────────────┬──────────────────┬───────────────────────────┘
               │                  │
     ┌─────────▼──────┐   ┌──────▼──────────────────┐
     │ ShapeOperationV2│   │ applyTransformations()  │
     │ (id, type,     │   │ TransformFlags filtering │
     │  shape, fill,  │   │ TransformedShape wrapping│
     │  reference,    │   └─────────────────────────┘
     │  flags, debug) │
     └────────┬───────┘
              │ produces
              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ComposedRegionV2                          │
│   Immutable snapshot of composed result:                    │
│   - originalBlocks (Long → BlockSnapshot)                   │
│   - blockFills     (Long → BlockFillTypeV2)                 │
│   - computedBlockIds (Long → int)                           │
│   - debugStyles    (Long → DebugStyle)                      │
│   - excludedPositions (Set<Long>)                           │
│   - operationRegions (String → Set<Long>)                   │
│   - timeline       (ordered operations list)                │
└─────────────────────────────────────────────────────────────┘

Fill Strategies (BlockFillTypeV2 implementations):
┌──────────────────┐  ┌────────────────┐  ┌──────────────────┐  ┌──────────────────────┐
│ EmptyBlockFillV2 │  │CustomBlockFillV2│  │PlaceholderFillV2 │  │BlockFillTypeV2Wrapper│
│ (air / remove)   │  │(specific block) │  │(hitbox-matching  │  │(Decorator: override  │
│                  │  │                │  │ transparent blk) │  │ debug style on any   │
│                  │  │                │  │                  │  │ fill)                │
└──────────────────┘  └────────────────┘  └──────────────────┘  └──────────────────────┘
```

---

## File Inventory

### V2 Core (`shape/v2/`)

| File | Lines | Role |
|---|---|---|
| `ShapeCompositorV2.java` | ~640 | Central compositor — timeline management, operation execution, composition |
| `ComposedRegionV2.java` | ~110 | Immutable result snapshot of a composed frame |
| `ShapeCompositorPresetsV2.java` | ~130 | Preset shape recipes (LayeredCone, SimpleTestCone) |

### V2 Operations (`shape/v2/operation/`)

| File | Lines | Role |
|---|---|---|
| `ShapeOperationV2.java` | ~200 | Single operation data object with Builder |
| `OperationTypeV2.java` | ~22 | Enum of 7 operation types with default priorities |

### V2 Fill Strategies (`shape/v2/fill/`)

| File | Lines | Role |
|---|---|---|
| `BlockFillTypeV2.java` | ~26 | Strategy interface for block replacement |
| `EmptyBlockFillV2.java` | ~43 | Returns block ID 0 (air) |
| `CustomBlockFillV2.java` | ~45 | Returns a fixed user-specified block ID |
| `PlaceholderFillV2.java` | ~65 | Maps original block's hitbox to a transparent placeholder |
| `BlockFillTypeV2Wrapper.java` | ~40 | Decorator to override debug style on any fill |

### V2 Visual (`shape/v2/visual/`)

| File | Lines | Role |
|---|---|---|
| `DebugStyle.java` | ~100 | Color/opacity/enable metadata for debug cube rendering |

### V2 Camera Integration (`camera/v2/`)

| File | Lines | Role |
|---|---|---|
| `CameraTransparencyVolumeV2.java` | ~310 | Per-player runtime: scheduled loop, diff application, packet sending |

### Shared Dependencies (not in v2/ but integral)

| File | Role |
|---|---|
| `TransformedShape.java` | Shape decorator applying translation + Euler rotation |
| `TransformFlags.java` | Bitfield controlling which transforms apply per-operation |
| `CircularCone.java` | Custom Shape primitive (cone geometry) |
| `BlockSnapshot.java` | Record: `(x, y, z, blockId, filler, rotation)` |
| `TransformedShapeTest.java` | Unit tests validating rotation correctness |

---

## Core Pipeline Flow

The pipeline executes in 4 phases every update tick:

### Phase 1: Timeline Resolution
```
ShapeCompositorV2.getTimeline()
  → Collect operations in insertion order
  → Sort by priority (ascending)
  → Return ordered list
```

### Phase 2: Sequential Operation Execution
```
For each enabled operation in timeline order:
  1. Resolve the shape geometry
  2. Apply TransformFlags-filtered yaw/pitch rotation via TransformedShape wrapping
  3. Iterate the shape's voxels (forEachBlock at anchor position)
  4. Execute type-specific logic (DEFINE, FILL, CUT, INTERSECT, SUBTRACT, FILL_REMAINING, EXCLUDE)
  5. Record affected positions in operationRegions map (for later cross-referencing)
```

### Phase 3: Block ID Computation
```
For each position with a BlockFillTypeV2:
  → Call fillType.getBlockId(originalSnapshot, chunkStore)
  → Store in computedBlockIds map
```

### Phase 4: Diff Application (in CameraTransparencyVolumeV2)
```
Compare new frame vs. previous frame:
  → toAdd: positions in new but not in old
  → toRemove: positions in old but not in new
  → toUpdate: positions in both but block ID changed
  → Send ServerSetBlock packets for diffs only
  → Render debug cubes grouped by color
```

---

## Class-by-Class Analysis

### ShapeCompositorV2

**File:** `shape/v2/ShapeCompositorV2.java`

**Intent:** The central orchestrator. Analogous to the "Feature Tree" / "Timeline" in Fusion 360. It maintains:
- An **anchor** (world-space origin point for all operations)
- **Yaw and pitch rotation** (global orientation, typically from camera direction)
- An **ordered map** of named operations (the parametric timeline)

**Key behaviors:**
- `addOperation()` returns a fluent `OperationBuilder` (inner class) that validates inputs per operation type before committing
- `compose(ChunkStore)` executes the full timeline and returns an immutable `ComposedRegionV2`
- `applyTransformations(Shape, TransformFlags)` wraps shapes in `TransformedShape` layers, applying pitch around an eye-height pivot point (`PITCH_PIVOT_EYE_HEIGHT = 1.8`) — this is specific to the camera use case where pitch should rotate around the player's eye level, not the feet
- Operations can be mutated post-creation: `updateFill()`, `setEnabled()`, `setPriority()`, `removeOperation()`

**Validation:** The `OperationBuilder.validateOperationInputs()` method enforces type-specific constraints:
- DEFINE/FILL/CUT/INTERSECT/SUBTRACT require a shape
- FILL/FILL_REMAINING require a fill type
- INTERSECT/SUBTRACT/FILL_REMAINING require a reference ID

**Execution methods** (one per operation type):
- `executeDefine()` — Only captures blocks that exist (non-air) within the shape. Applies fill if provided.
- `executeFill()` — Like DEFINE but always requires and applies a fill type.
- `executeCut()` — Delegates to `executeFilledShape()` with the static `CUT_FILL` (EmptyBlockFillV2, i.e., air).
- `executeIntersect()` — Only affects positions that are BOTH within the new shape AND within a previously referenced operation's region. Analogous to Boolean Intersection in CAD.
- `executeSubtract()` — Removes positions from a referenced operation's fill maps. Analogous to Boolean Subtract / Cut Body in CAD.
- `executeFillRemaining()` — Fills positions in a reference that are NOT yet owned by another operation. Useful for "fill the leftover" patterns.
- `executeExclude()` — Marks positions as globally excluded. No subsequent operation can affect these. Analogous to a "keep-out zone" or "mask."

---

### ShapeOperationV2

**File:** `shape/v2/operation/ShapeOperationV2.java`

**Intent:** Immutable-ish data object representing a single step in the parametric timeline. Built via a nested `Builder` class.

**Fields:**
| Field | Type | Purpose |
|---|---|---|
| `id` | `String` | Unique identifier for cross-referencing |
| `shape` | `Shape` | The geometric primitive (nullable for FILL_REMAINING) |
| `type` | `OperationTypeV2` | The operation semantics |
| `fillType` | `BlockFillTypeV2` | How affected blocks are replaced (mutable) |
| `referenceId` | `String` | ID of another operation to cross-reference (for INTERSECT, SUBTRACT, FILL_REMAINING) |
| `intersectWith` | `List<String>` | Future: multi-intersection targets (currently unused in compositor) |
| `subtractFrom` | `List<String>` | Future: multi-subtraction targets (currently unused in compositor) |
| `transformFlags` | `TransformFlags` | Controls which global rotations apply to this operation's shape |
| `debugStyleOverride` | `DebugStyle` | Optional per-operation visual override |
| `priority` | `int` | Execution order (lower = earlier, defaults from type) |
| `enabled` | `boolean` | Toggle without removing from timeline |

**Mutable post-build:** `fillType`, `priority`, and `enabled` can be changed at runtime — this enables the "parametric editing" aspect where you don't rebuild the timeline, you tweak individual operations.

---

### OperationTypeV2

**File:** `shape/v2/operation/OperationTypeV2.java`

**Intent:** Enum defining the 7 CSG operation types with default execution priorities.

| Type | Priority | CAD Analogy | Description |
|---|---|---|---|
| `DEFINE` | 10 | Sketch / New Body | Declares a region. Only captures existing non-air blocks within the shape. Optionally fills. |
| `FILL` | 20 | Extrude / Fill | Captures region AND applies a fill strategy. |
| `CUT` | 20 | Cut / Hole | Like FILL but always uses EmptyBlockFillV2 (air). Cuts geometry away. |
| `INTERSECT` | 30 | Intersect Bodies | Keeps only the overlap between this shape and a referenced region. |
| `SUBTRACT` | 40 | Subtract / Boolean Cut | Removes all positions of this shape from a referenced region. |
| `FILL_REMAINING` | 50 | Fill / Shell | Fills any un-owned positions within a referenced region. |
| `EXCLUDE` | 100 | Mask / Keep-out | Globally prevents any operation from affecting these positions. Always runs last by default. |

The priority system allows reordering execution without changing timeline insertion order — a key parametric feature.

---

### ComposedRegionV2

**File:** `shape/v2/ComposedRegionV2.java`

**Intent:** An immutable snapshot of the composed result. Defensive copies of all maps are made in the constructor. All getters return `Collections.unmodifiable*` views.

**Data stored:**
- `originalBlocks` — The world's actual blocks before any replacement. Keyed by packed `long` position.
- `blockFills` — Which fill strategy owns each position.
- `computedBlockIds` — The resolved replacement block IDs (result of calling `fillType.getBlockId()`).
- `debugStyles` — Per-position debug visualization metadata.
- `excludedPositions` — Positions masked by EXCLUDE operations.
- `operationRegions` — Maps operation IDs → the set of positions they affected. This is what enables cross-referencing (INTERSECT references another operation's region).
- `timeline` — The ordered operations list for introspection.

Positions are packed as `long` using `BlockUtil.packUnchecked(x, y, z)` — a standard Hytale API bit-packing strategy for compact position storage and O(1) hash lookups.

---

### BlockFillTypeV2 (Interface + Implementations)

**File:** `shape/v2/fill/BlockFillTypeV2.java`

**Intent:** Strategy pattern interface for determining what block ID replaces an original block.

**Contract:**
```java
int getBlockId(BlockSnapshot original, ChunkStore chunkStore);  // Resolve replacement
boolean requiresPacketUpdate();  // Does client need a special update?
String getDescription();  // Human-readable label
DebugStyle getDebugStyle();  // Default debug visualization
BlockFillTypeV2 withDebugStyle(DebugStyle);  // Decorator shorthand
```

#### EmptyBlockFillV2
Returns block ID `0` (air). Used as the implicit fill for CUT operations. Default debug style: `EMPTY_STYLE` (near-black).

#### CustomBlockFillV2
Returns a fixed, user-specified block ID. For replacing regions with a specific material (e.g., glass, stone). Default debug style: `NONE`.

#### PlaceholderFillV2
The most sophisticated fill. Looks up the original block's **hitbox type** and maps it to a corresponding transparent **placeholder block** via `PlaceholderBlockManager`. This preserves collision geometry while making the block visually transparent — critical for the camera transparency use case. Requires a packet update (`requiresPacketUpdate() = true`). Default debug style: `PLACEHOLDER_STYLE` (cyan).

#### BlockFillTypeV2Wrapper
Decorator pattern. Wraps any `BlockFillTypeV2` implementation and overrides only the `getDebugStyle()` method. Created via the default `withDebugStyle()` method on the interface. Package-private — not meant for external instantiation.

---

### DebugStyle

**File:** `shape/v2/visual/DebugStyle.java`

**Intent:** Value object carrying debug cube rendering metadata: color (RGB as `Vector3f`), opacity, and enabled flag.

**Presets:**
| Constant | Color | Opacity | Use Case |
|---|---|---|---|
| `NONE` | null | 0.0 | No debug rendering |
| `DEFAULT_GREY` | (0.2, 0.2, 0.2) | 0.05 | Standard operation outline |
| `LIGHT_GREY_STYLE` | (0.5, 0.5, 0.5) | 0.15 | Outer shells / secondary |
| `PLACEHOLDER_STYLE` | (0.137, 0.867, 0.882) | 0.05 | Placeholder blocks (cyan) |
| `EMPTY_STYLE` | (0.1, 0.1, 0.1) | 0.05 | Empty/air fills (near-black) |

Includes a Builder and static factory methods (`enabled()`, `disabled()`).

Debug styles flow through the pipeline: each fill type has a default style, each operation can override it, and the compositor resolves the final style per-position.

---

### ShapeCompositorPresetsV2

**File:** `shape/v2/ShapeCompositorPresetsV2.java`

**Intent:** Convenience subclass providing pre-built shape recipes. Demonstrates the intended API usage.

**`LayeredConePreset()`** — Builds a layered cone with 3 concentric shells:
1. **Outer cone** (DEFINE) — full size, light grey debug
2. **Middle subtract** (SUBTRACT from outer) — carves out the middle cone from outer
3. **Middle shell** (DEFINE) — re-defines the middle cone region, dark grey debug
4. **Inner subtract** (SUBTRACT from middle) — carves out the inner cone from middle
5. **Inner shell** (DEFINE) — re-defines the innermost cone, no debug
6. **Floor exclusion** (EXCLUDE) — Box below player, ignore pitch, prevents floor blocks from being affected

This is a textbook CSG shell-building pattern: define a body, subtract a smaller body to create a shell, repeat for inner layers.

**`SimpleTestCone()`** — Minimal preset: single DEFINE cone + floor EXCLUDE.

Both presets use `TransformFlags` to selectively disable pitch on the floor exclusion (so it stays flat even when the camera looks up/down).

---

### CameraTransparencyVolumeV2

**File:** `camera/v2/CameraTransparencyVolumeV2.java`

**Intent:** The **runtime consumer** of the compositor pipeline. One instance per player, managed via a static `ConcurrentHashMap`. Implements a scheduled update loop at 100ms intervals.

**Lifecycle:**
1. `StartTransparencyVolumeLoop()` — Creates instance, replaces existing if stale, starts scheduled task
2. Scheduled task reads player position + look direction → sets compositor anchor + rotation
3. `update()` — Change detection → compose → diff → packet send
4. `shutdown()` — Stops loop, restores all modified blocks, cleans up placeholder transparency

**Change detection:** Only recomposes if anchor, yaw, or pitch changed beyond a threshold (0.01 radians). Otherwise renders cached debug cubes.

**Diff algorithm:**
- `toAdd` = new positions not in previous frame
- `toRemove` = previous positions not in new frame
- `toUpdate` = positions in both frames but with changed block IDs
- Sends `ServerSetBlock` packets only for the diff set → avoids redundant network traffic

**Placeholder transparency handling:** For blocks with `PlaceholderFillV2`, the system further checks the hitbox type and calls `PlaceholderTransparencyUtil.prepareTransparentPlaceholder()` to ensure the client renders the block as transparent while preserving collision. This is a two-tier replacement: `PlaceholderFillV2` handles server-side block swap, `PlaceholderTransparencyUtil` handles client-side transparency packets.

**Block restoration:** On position removal or shutdown, the volume sends packets restoring the **current** chunk store block (not the snapshot), handling the case where the world changed under the transparency volume.

---

## Supporting Classes (Shared with V1)

### TransformedShape

**File:** `shape/TransformedShape.java`

Wraps any `Shape` with translation offsets and Euler angle rotations (yaw/pitch/roll in radians). Implements the full `Shape` interface.

**Rotation model:**
- Forward rotation (for bounding box): Roll → Pitch → Yaw
- Inverse rotation (for `containsPosition`): Yaw⁻¹ → Pitch⁻¹ → Roll⁻¹
- This is mathematically correct: forward and inverse are applied in opposite order.

**`forEachBlock()` strategy:** Computes a rotated axis-aligned bounding box (AABB), iterates all blocks in that AABB, and uses `containsPosition()` (inverse rotation) to test membership. This is the "sample the rotated AABB" approach rather than "rotate each block forward" — it avoids the gaps/holes that occur with forward-rotation rounding (validated by the unit test `regression_nonOrthogonalYaw_shouldIncludeBlocksMissedByRounding`).

**Note on `offsetX` negation:** The constructor negates `offsetX` (`this.offsetX = -offsetX`). This appears to be a coordinate system correction for the camera's left-right axis.

### TransformFlags

**File:** `shape/TransformFlags.java`

Immutable flag set controlling 6 independent axes: anchor X/Y/Z and rotation yaw/pitch/roll. Builder pattern with convenience methods (`ignoreAnchor()`, `ignoreRotations()`, `ignorePitch()`, etc.) and presets (`ALL`, `NONE`, `ROTATIONS_ONLY`, `ANCHOR_ONLY`).

Used per-operation to allow selective transform behavior — e.g., a floor exclusion zone ignores pitch so it stays level.

### CircularCone

**File:** `shape/CircularCone.java`

Custom `Shape` implementation. A cone with base at Z=0, apex at Z=height. Radius tapers linearly: `currentRadius = baseRadius * (1 - z/height)`.

Validates inputs in constructor (`baseRadius > 0`, `height > 0`). Does not support `expand()`.

### BlockSnapshot

**File:** `records/BlockSnapshot.java`

Java record: `(int x, int y, int z, int blockId, short filler, byte rotation)`. Captures the complete state of a block for later restoration.

---

## Design Patterns Identified

| Pattern | Where | Purpose |
|---|---|---|
| **Builder** | `ShapeOperationV2.Builder`, `OperationBuilder`, `TransformFlags.Builder`, `DebugStyle.Builder`, `TransformedShape.Builder` | Fluent construction of complex immutable objects |
| **Strategy** | `BlockFillTypeV2` interface + implementations | Pluggable block replacement logic |
| **Decorator** | `BlockFillTypeV2Wrapper`, `TransformedShape` wrapping `Shape` | Add behavior (debug style override, rotation) without modifying original |
| **Composite / Pipeline** | `ShapeCompositorV2.compose()` | Sequential execution of heterogeneous operations producing a unified result |
| **Snapshot / Memento** | `ComposedRegionV2`, `BlockSnapshot` | Capture immutable state for diffing and restoration |
| **Singleton Registry** | `CameraTransparencyVolumeV2.INSTANCES` | One volume instance per player, globally accessible |
| **Template Method** | `executeFilledShape()` shared by FILL and CUT | Common iteration logic, varying only fill type |
| **Observer / Tick Loop** | `startUpdateLoop()` with `ScheduledExecutorService` | Periodic polling of player state to drive recomposition |
| **Diff Reconciliation** | `update()` with toAdd/toRemove/toUpdate sets | React-style minimal update strategy for network efficiency |

---

## Pipeline Execution Detail

### Operation Priority and Timeline

Operations are inserted in array order via `operationOrder`, but executed sorted by `priority` (ascending). Default priorities create a natural dependency order:

```
DEFINE (10) → FILL/CUT (20) → INTERSECT (30) → SUBTRACT (40) → FILL_REMAINING (50) → EXCLUDE (100)
```

Custom priorities can override this — e.g., setting a SUBTRACT to priority 15 would make it execute before most FILLs.

### Transform Application Chain

```
Compositor global state: anchor (Vector3i), yawRotation, pitchRotation

Per operation:
  1. Read operation's TransformFlags
  2. Compute effectiveYaw = flags.shouldApplyYaw() ? yawRotation : 0.0
  3. Compute effectivePitch = flags.shouldApplyPitch() ? pitchRotation : 0.0
  4. If any effective rotation != 0:
     a. Wrap shape in TransformedShape for roll (if nonzero)
     b. Wrap in TransformedShape for pitch with eye-height pivot:
        - Translate down by PITCH_PIVOT_EYE_HEIGHT
        - Rotate by pitch
        - Translate back up by PITCH_PIVOT_EYE_HEIGHT
     c. Wrap in TransformedShape for yaw
  5. Iterate wrapped shape at anchor position
```

The pitch pivot is a notable detail — it ensures pitch rotations orbit around the player's eye level (1.8 blocks up), not the player's feet. This is correct for a third-person camera system where the rotation center should be at eye height.

### Diff-Based Rendering

```
Frame N-1 state: currentPositions, activeBlocks, activeBlockIds, activeDebugStyles
Frame N compose result: newPositions, newSnapshots, newBlockIds, newDebugStyles

Diff:
  toAdd    = newPositions - currentPositions     → send replacement packets
  toRemove = currentPositions - newPositions     → send restore packets
  toUpdate = intersection where blockId changed  → send update packets

Post-diff:
  Update all tracking maps to reflect Frame N state
  Re-render debug cubes grouped by color
```

---

## CAD Analogy Mapping

| CAD Concept | V2 Equivalent |
|---|---|
| Feature Tree / Timeline | `operationOrder` list + priority sorting |
| Sketch / New Body | `DEFINE` operation |
| Extrude Boss | `FILL` operation |
| Extrude Cut / Hole | `CUT` operation |
| Boolean Intersect | `INTERSECT` operation |
| Boolean Subtract | `SUBTRACT` operation |
| Shell / Fill | `FILL_REMAINING` operation |
| Construction Plane / Mask | `EXCLUDE` operation |
| Material / Appearance | `BlockFillTypeV2` strategy |
| Parametric Dimension | Scalable presets via `_scale`, mutable `fillType`, `priority`, `enabled` |
| Suppress / Unsuppress Feature | `setEnabled(false)` on an operation |
| Feature Reference | `referenceId` linking operations to earlier regions |
| Section View / Debug | `DebugStyle` + `DebugCube` rendering |

---

## Strengths

1. **Clean separation of concerns** — Shape geometry, fill strategy, operation semantics, visual debugging, and runtime application are each independent layers.

2. **True parametric editing** — Operations can be toggled, re-prioritized, and have their fill swapped without rebuilding the timeline. This is the fundamental promise of parametric CAD.

3. **Cross-referencing via operation regions** — The `operationRegions` map enables powerful CSG operations (intersect, subtract, fill-remaining) that depend on the results of earlier operations.

4. **Efficient diff-based updates** — Only changed positions are sent as packets, avoiding redundant network traffic on each frame.

5. **Defensive immutability** — `ComposedRegionV2` makes defensive copies and returns unmodifiable views. `BlockSnapshot` is a record. `TransformFlags` is immutable.

6. **Input validation** — The `OperationBuilder` validates type-specific constraints before committing, catching misconfigurations early.

7. **Debug visualization baked into the pipeline** — Every fill type and every operation carries debug metadata, making the system introspectable at every level.

8. **Per-operation transform control** — `TransformFlags` allows individual operations to opt out of global rotations. This enables mixed reference frames (e.g., floor exclusion stays flat while cone rotates with camera).

---

## Potential Issues and Recommendations

### 1. Operation Order vs. Priority Ambiguity
The timeline has **two ordering mechanisms**: insertion order (`operationOrder`) and priority sorting. The current behavior always sorts by priority, making insertion order irrelevant for operations sharing the same priority. Consider documenting which one is authoritative, or introducing a stable sort that uses insertion order as a tiebreaker.

**Current behavior:** `timeline.sort(Comparator.comparingInt(ShapeOperationV2::getPriority))` — this is NOT a stable sort with `ArrayList.sort()` (actually it is stable in Java, but the intent should be explicit). Operations with equal priority will retain insertion order.

### 2. Unused Fields in ShapeOperationV2
`intersectWith` and `subtractFrom` lists are built and stored but **never read** by the compositor's execution methods. The compositor uses only `referenceId` (singular). These appear to be scaffolding for future multi-reference operations. Consider either implementing them or removing them to avoid confusion.

### 3. `offsetX` Negation in TransformedShape
The constructor does `this.offsetX = -offsetX` while leaving Y and Z unchanged. This coordinate system inversion is undocumented and could be a source of bugs when the class is used outside the camera context. Consider adding a comment or using a named parameter to make intent clear.

### 4. `blockOwners` Map Is Local to `compose()`
The `blockOwners` map (tracking which operation last wrote to a position) is computed during `compose()` but is **not** included in `ComposedRegionV2`. This means the composed result loses per-position ownership information. If future features need "which operation owns this block?", the data would need to be added to the region.

### 5. FILL_REMAINING Ownership Check
`executeFillRemaining()` uses `!blockOwners.containsKey(pos)` to find un-owned positions. Because `SUBTRACT` removes from `blockFills` and `blockOwners`, subtracted positions within a reference region **will** be filled by FILL_REMAINING. This might be intentional (rebuild subtracted areas) but could be surprising. Document the intended interaction.

### 6. Thread Safety
`ShapeCompositorV2` fields (`anchor`, `yawRotation`, `pitchRotation`) are mutated from the scheduled executor thread via `compositor.setAnchor()` and `compositor.setRotation()` inside `world.execute()`. The `operations` map is a `LinkedHashMap` (not thread-safe). If operations are ever modified concurrently with `compose()`, races could occur. Currently safe because all mutation happens inside `world.execute()`, but this constraint is implicit, not enforced.

### 7. Test Coverage
Only `TransformedShapeTest` exists, covering the rotation/translation math. There are no tests for:
- `ShapeCompositorV2.compose()` logic
- Operation type execution (INTERSECT, SUBTRACT, FILL_REMAINING interactions)
- `ComposedRegionV2` immutability guarantees
- Fill type implementations

Given the complexity of the CSG pipeline, operation interaction tests would be high-value additions.

### 8. EXCLUDE Runs Late by Default (Priority 100)
EXCLUDE has the highest default priority (100), meaning it runs **last**. But EXCLUDE's purpose is to mask positions from all other operations. Since the current implementation checks `excludedPositions` inside each operation's execution, EXCLUDE positioned earlier in the timeline works correctly. However, if an EXCLUDE has a higher custom priority, positions masked by it will only be respected by operations that run after it. Consider whether EXCLUDE should be treated as a pre-pass rather than a regular timeline entry.

### 9. No Undo/Redo Stack
For a CAD-like system, an undo/redo stack for timeline manipulation (add/remove/reorder operations) would be expected. The current system supports mutation (`removeOperation`, `setEnabled`, etc.) but has no history tracking.

### 10. Preset Pattern
`ShapeCompositorPresetsV2` extends `ShapeCompositorV2` and modifies `this` — acting as both a factory and an instance. A factory/static method pattern (`ShapeCompositorPresetsV2.createLayeredCone(scale)`) returning a new `ShapeCompositorV2` would be cleaner and avoid the inheritance-based approach.
