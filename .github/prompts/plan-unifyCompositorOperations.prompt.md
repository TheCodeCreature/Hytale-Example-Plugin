## Plan: Unify Compositor Operations into Generic Pipeline

Refactor the 7 separate `execute*` methods and 5 parallel maps in `ShapeCompositorV2` into a single `Map<Long, VoxelEntry>` and one generic execution method driven by composable behavioral flags per operation type.

---

### Analysis: Current Duplication

I broke every operation down into 3 behavioral axes. Here's what they actually do:

**1. Position Sourcing** — where do candidate positions come from?
- **Iterate shape** at anchor: DEFINE, FILL, CUT, INTERSECT, SUBTRACT, EXCLUDE
- **Iterate reference region**: FILL_REMAINING

**2. Per-position Filtering** — which candidates survive?

| Filter | DEFINE | FILL | CUT | INTERSECT | SUBTRACT | FILL_REMAINING | EXCLUDE |
|---|---|---|---|---|---|---|---|
| Skip if excluded | ✓ | ✓ | ✓ | ✓ | | | |
| Require in reference | | | | ✓ | ✓ | ✓ | |
| Require not owned | | | | | | ✓ | |
| Require non-air block | ✓ | ✓ | ✓ | ✓ | | ✓ | |

**3. Per-position Action** — what happens to survivors?
- **WRITE** (upsert entry): DEFINE, FILL, CUT, INTERSECT, FILL_REMAINING
- **REMOVE** (clear if owned by ref): SUBTRACT
- **MARK_EXCLUDED**: EXCLUDE

Every operation is just: **source → filter → action**. The 7 methods are 7 hardcoded combinations of these 3 axes.

**5 parallel maps** (`originalBlocks`, `blockFills`, `blockOwners`, `debugStyles`, `excludedPositions`) all keyed by the same packed `long` — these are really one object per position.

---

### Steps

**Phase 1: Introduce `VoxelEntry`** — unified per-position state

1. Create `VoxelEntry.java` — mutable class holding: `BlockSnapshot original`, `BlockFillTypeV2 fill`, `String ownerId`, `DebugStyle debugStyle`, `boolean excluded`. Convenience methods: `hasFill()`, `isOwnedBy(String)`, `clear()` (resets fill/owner/debug).

2. Replace the 5 parallel maps in `compose()` with `Map<Long, VoxelEntry> voxelMap`. The `excludedPositions` set becomes entries where `entry.excluded == true`. The `blockOwners` map — currently lost after compose — now survives as `entry.ownerId`.

**Phase 2: Encode behaviors on `OperationTypeV2`** (*parallel with Phase 1*)

3. Add behavioral flags to `OperationTypeV2` enum values:

| Type | Source | Skip Excluded | Require Ref | Require Unowned | Require Non-Air | Action |
|---|---|---|---|---|---|---|
| DEFINE | SHAPE | ✓ | | | ✓ | WRITE |
| FILL | SHAPE | ✓ | | | ✓ | WRITE |
| CUT | SHAPE | ✓ | | | ✓ | WRITE |
| INTERSECT | SHAPE | ✓ | ✓ | | ✓ | WRITE |
| SUBTRACT | SHAPE | | ✓ | | | REMOVE |
| FILL_REMAINING | REFERENCE | | ✓ | ✓ | ✓ | WRITE |
| EXCLUDE | SHAPE | | | | | MARK_EXCLUDED |

Each enum value declares its complete behavior — no switch statements needed downstream.

**Phase 3: Single generic `executeOperation()`** (*depends on 1 + 2*)

4. Replace all 7 `execute*` methods + `executeFilledShape()` with one method:
   - Resolve fill type (CUT overrides to `CUT_FILL` — only type-specific special case)
   - Resolve debug style
   - Get positions: if `SHAPE` → transform shape + `forEachBlock`; if `REFERENCE` → iterate reference set
   - Per position: run the filter chain read from the type's flags
   - Per position: execute action (`WRITE` → upsert VoxelEntry, `REMOVE` → clear if owned by ref, `MARK_EXCLUDED` → set flag)
   - Return affected positions for `operationRegions`

5. Simplify `compose()` — the entire switch statement becomes a single `executeOperation()` call per timeline entry.

**Phase 4: Update `ComposedRegionV2`** (*depends on Phase 1*)

6. Change `ComposedRegionV2` to store `Map<Long, VoxelEntry>` internally. Keep existing getter signatures implemented as views over VoxelEntry for backwards compatibility. Add `getVoxelMap()` for direct access and `getOwner(long pos)` (previously lost data).

**Phase 5: Update `CameraTransparencyVolumeV2`** (*depends on Phase 4*)

7. Adapt `CameraTransparencyVolumeV2` — minimal changes since getters stay compatible. Can optionally iterate `getVoxelMap()` instead of the separate maps.

**Phase 6: Remove dead code**

8. Delete the 7 individual `execute*` methods from `ShapeCompositorV2`
9. Remove unused `intersectWith`/`subtractFrom` lists from `ShapeOperationV2`
10. Derive validation from type flags (e.g. source=SHAPE → require shape, action=WRITE && !fillOptional → require fill) instead of the manual `validateOperationInputs()` switch

---

### Relevant Files

- `src/main/java/com/UnobstructedThirdPerson/shape/v2/ShapeCompositorV2.java` — main refactor: replace 7 execute methods + 5 maps with generic method + VoxelEntry map
- `src/main/java/com/UnobstructedThirdPerson/shape/v2/operation/OperationTypeV2.java` — add behavioral flags per enum value
- `src/main/java/com/UnobstructedThirdPerson/shape/v2/operation/ShapeOperationV2.java` — remove unused `intersectWith`/`subtractFrom`
- `src/main/java/com/UnobstructedThirdPerson/shape/v2/ComposedRegionV2.java` — switch to VoxelEntry storage, keep getter compat
- `src/main/java/com/UnobstructedThirdPerson/camera/v2/CameraTransparencyVolumeV2.java` — adapt to new API
- **New:** `src/main/java/com/UnobstructedThirdPerson/shape/v2/VoxelEntry.java`

---

### Verification

1. Existing `TransformedShapeTest` still passes (rotation math untouched)
2. Test `LayeredConePreset` in-game — outer cone, subtract carve, floor exclusion all work
3. Test `SimpleTestCone` — basic DEFINE + EXCLUDE
4. Debug cubes render correctly per-operation
5. Shutdown restores blocks correctly
6. Add unit tests for generic `executeOperation()` — compose a small voxelMap and verify entries after each operation type

---

### Decisions

- **VoxelEntry is mutable** — used as working state during compose, then defensively copied into ComposedRegionV2
- **ComposedRegionV2 getters stay backwards-compatible** — views over VoxelEntry, no forced changes in consumers
- **CUT's implicit CUT_FILL override** is the only type-specific special case, resolved as a one-liner at the top of the generic method
- **`computedBlockIds` stays as a post-pass** — depends on ChunkStore, separate concern from the operation pipeline
- **DEFINE with fill=null** still registers the region but skips the fill/owner/debug write (matching current behavior)

---

### Further Considerations

1. **Should the behavioral flags live on `OperationTypeV2` (the enum) or on a separate `OperationBehavior` class?** Putting them on the enum is simpler and the enum already has `defaultPriority`. Recommend: on the enum, since the behaviors are fixed per type.

2. **Should `VoxelEntry` be a record or a mutable class?** During compose it's mutated by multiple operations. Recommend: mutable class with a `copy()` method for defensive copies in ComposedRegionV2.
