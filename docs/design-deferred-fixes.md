# Design: Deferred Fix Batch (Items #5, #9, #11, #17)

Concrete implementation plans for 4 deferred items from the code review.

---

## Item #5: BlueprintBookParticleLoop Performance Redesign

### Approach

Replace the 100ms fire-and-forget effect with `addInfiniteEffect()` so the highlight entity persists without re-application. Reduce the scheduled loop from 100ms to 500ms — its only job becomes detecting stale targets (block changed, item unequipped, affordability flipped). Add an `affordableState` diff check so the entity is only destroyed and re-spawned when the affordability boolean actually changes, not on every tick. The block-model-as-entity approach is preserved because it's the core visual value.

### Files to Modify

| File | Change |
|------|--------|
| `src/main/java/com/CodeCreature/ui/blueprintbook/BlueprintBookParticleLoop.java` | Change `UPDATE_INTERVAL_MILLIS` from 100 → 500. Store `lastAffordable` boolean field. Replace `addEffect(..., UPDATE_INTERVAL_MILLIS, ...)` with `addInfiniteEffect(...)`. Add affordability diff check before entity replacement. |

### Acceptance Criteria

1. **Loop interval is 500ms** — the `scheduleAtFixedRate` period must be 500.
2. **Entity is not replaced when target block + affordability are unchanged** — verified by `FINE` log count: holding the book while looking at the same affordable block for 5s should produce 0 "Spawning highlight entity" logs after the initial spawn.
3. **Entity IS replaced when affordability flips** — crafting away the last resource mid-aim must cause exactly 1 respawn with the red effect.
4. **Entity IS replaced when target block changes** — moving crosshair to a different block produces exactly 1 respawn.
5. **Effect does not expire** — entity remains visible indefinitely while aim is steady (no flickering at the old 100ms boundary).
6. **No regression on cleanup** — unequipping the book or disconnecting still removes the entity within 500ms.

### Execution Waves

#### Wave 1 (single file, no dependencies)

| Unit | Description | Done When |
|------|-------------|-----------|
| `BlueprintBookParticleLoop.java` | (a) Change constant to 500. (b) Add `private boolean lastAffordable` field. (c) In the loop body, after confirming `target.equals(lastTargetBlock) && activeEntity.isValid()`, compute `affordable` and compare to `lastAffordable` — if same, return early; if different, fall through to respawn. (d) Replace `addEffect(ref, effect, UPDATE_INTERVAL_MILLIS, OverlapBehavior.OVERWRITE, store)` with `addInfiniteEffect(ref, effect, OverlapBehavior.OVERWRITE, store)`. (e) Set `lastAffordable = affordable` after spawn. | Plugin compiles, in-game highlight persists without flickering, entity replaced only on target-change or affordability-flip. |

### Risk and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| `addInfiniteEffect()` API doesn't exist or has a different signature | Build fails | Check `EffectControllerComponent` method signatures first. Fallback: use `addEffect(ref, effect, Integer.MAX_VALUE, ...)` as a pseudo-infinite duration. |
| 500ms feels sluggish for target detection | UX regression | Interval is a constant — can tune to 250ms as a compromise. The key win is removing the per-tick entity respawn, not the exact interval. |

### Estimated Complexity: **S** (single file, ~15 lines changed)

---

## Item #9: StencilSyncSystem Listener Deregistration

### Approach

Replace the `ConcurrentHashMap<UUID, Boolean>` with `ConcurrentHashMap<UUID, EventRegistration[]>` (3-element array: hotbar, backpack, storage). On `register()`, store all three `EventRegistration` handles returned by `registerChangeEvent()`. On `unregister()`, call `handle.unregister()` on each stored handle before removing the entry. This stops the stencil lambda from firing on every inventory change for the entire server session after the feature is no longer needed.

### Files to Modify

| File | Change |
|------|--------|
| `src/main/java/com/CodeCreature/stencil/StencilSyncSystem.java` | Change map value type from `Boolean` to `EventRegistration[]`. Store the 3 handles. Call `unregister()` in the cleanup method. |

### Acceptance Criteria

1. **Listeners are deregistered on disconnect** — after `onPlayerDisconnect` fires, inventory changes on the (now-disconnected) player's containers produce zero invocations of `restoreStencils` or `refreshAffordability`.
2. **No functional regression** — stencils placed while connected are still restored to qty 2 on each placement.
3. **`registeredPlayers` map is empty after all players disconnect** — no handle references retained.

### Execution Waves

#### Wave 1 (single file, no dependencies)

| Unit | Description | Done When |
|------|-------------|-----------|
| `StencilSyncSystem.java` | (a) Import `EventRegistration`. (b) Change `ConcurrentHashMap<UUID, Boolean>` → `ConcurrentHashMap<UUID, EventRegistration[]>`. (c) In `register()`, capture all 3 return values of `registerChangeEvent()` into `new EventRegistration[3]` and store in map. (d) In `unregister()`, retrieve the array, loop and call `.unregister()` on each non-null handle, then remove from map. | Compiles, manual test confirms stencil restore works during session, logs confirm no stencil activity post-disconnect. |

### Risk and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| `registerChangeEvent()` return type isn't `EventRegistration` in this SDK version | Build fails | Verify return type in decompiled `ItemContainer.java`. If void, wrap in a guard boolean checked inside the lambda instead (less clean but functional). |
| Calling `unregister()` on an already-GC'd container throws | NPE on disconnect | Null-check the handle before calling; wrap in try-catch per handle. |

### Estimated Complexity: **S** (single file, ~20 lines changed)

---

## Item #11: Plugin.java Package Move

### Approach

Move `Plugin.java` from `package com;` to `package com.CodeCreature;`. This aligns the entry point with the rest of the codebase. The only external reference is in `gradle.properties` (`plugin_main_entrypoint=com.Plugin`), which becomes `com.CodeCreature.Plugin`. Zero Java source files import `com.Plugin` directly. Binary failure mode: the plugin either loads or crashes on startup, immediately verifiable.

### Files to Modify

| File | Change |
|------|--------|
| `src/main/java/com/Plugin.java` | **Delete** (move) |
| `src/main/java/com/CodeCreature/Plugin.java` | **Create** — same content with `package com.CodeCreature;` |
| `gradle.properties` | Change `plugin_main_entrypoint=com.Plugin` → `plugin_main_entrypoint=com.CodeCreature.Plugin` |

### Acceptance Criteria

1. **`gradle build` succeeds** — no compile errors from the package change.
2. **Plugin loads on server start** — `[Plugin] Resource scaling active.` message appears for connected players.
3. **Old file removed** — `src/main/java/com/Plugin.java` no longer exists.
4. **No other Java files modified** — grep for `import com.Plugin` returns zero hits (already confirmed by research).

### Execution Waves

#### Wave 1 (atomic operation, no dependencies)

| Unit | Description | Done When |
|------|-------------|-----------|
| Package move | (a) Create `src/main/java/com/CodeCreature/Plugin.java` with `package com.CodeCreature;` and identical body. (b) Delete `src/main/java/com/Plugin.java`. (c) Update `gradle.properties` entrypoint. | `gradle build` passes, server loads plugin successfully. |

### Risk and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Manifest generation uses a different property than `plugin_main_entrypoint` | Plugin fails to load at runtime | Inspect `build.gradle.kts` manifest substitution task to confirm property name before changing. |
| IDE caches stale class reference | False compile errors in IDE | Rebuild/invalidate caches after move. |

### Estimated Complexity: **S** (3 files, trivial change, immediately verifiable)

---

## Item #17: BlueprintSelectionPage God Class Decomposition

### Approach

Extract two "render-only" controllers from `BlueprintSelectionPage` following the proven `IngredientTreeGridController` pattern: each controller receives data and a `UICommandBuilder`, produces UI commands, and has no event routing responsibility (the page remains the sole event entry point per `InteractiveCustomUIPage` contract). Wave 1 extracts `GridLayoutController` which owns the grid rendering logic (~150 lines: `updateRecipeGrid` + `hideRemainingCells` + the indirection map fields). Wave 2 extracts `DetailPanelController` which owns the detail/cost panel rendering (~100 lines: `updateDetailPanel` + cost-grid population logic).

### Files to Create

| File | Responsibility |
|------|----------------|
| `src/main/java/com/CodeCreature/ui/bench/GridLayoutController.java` | Owns `updateRecipeGrid()`, `hideRemainingCells()`, `buildRecipeGridBindings()`, and all grid layout state (`cellSlotToRecipeIndex`, `groupCellOffset`, `cellsPerSet`, `setNameToGroupIndex`, `totalSetCount`, `maxLayoutSetNames`). Exposes `resolveRecipeIndex(int slotIdx)` for the page to use in event handling. |
| `src/main/java/com/CodeCreature/ui/bench/DetailPanelController.java` | Owns `updateDetailPanel()` logic. Takes selected recipe entry + affordability mode + inventory container → produces `UICommandBuilder` commands. |

### Files to Modify

| File | Change |
|------|--------|
| `src/main/java/com/CodeCreature/ui/bench/BlueprintSelectionPage.java` | Remove extracted methods and fields. Delegate to new controllers. Keep event routing in `handleDataEvent`. |

### Acceptance Criteria

1. **`BlueprintSelectionPage` drops below 800 lines** — at least 250 lines extracted.
2. **Grid rendering is identical** — visual regression test: open bench, switch tabs, search, filter — all cells render with correct icons, selection highlight, and dim overlays.
3. **Detail panel is identical** — selecting any recipe shows correct output icon, name, cost grid with affordability coloring.
4. **Event routing unchanged** — `handleDataEvent` still dispatches `RecipeSelect:idx:*` to the grid controller's `resolveRecipeIndex()` and updates `selectedRecipeId`.
5. **Pattern consistency** — new controllers follow same structure as `IngredientTreeGridController`: constructor takes data model, `buildUI(cmd, evt)` for one-time init, `updateUI(cmd)` for state refresh.
6. **Build passes** — `gradle build` clean.

### Execution Waves

#### Wave 1: GridLayoutController (no dependencies on Wave 2)

| Unit | Methods | Done When |
|------|---------|-----------|
| `GridLayoutController.java` | `GridLayoutController(maxLayout)`, `buildUI(cmd, evt)` (appends grid containers + binds cell events), `updateUI(cmd, displayedRecipes, selectedRecipeId)` (the full grid render loop), `resolveRecipeIndex(int slotIdx)` → `int` | Grid renders identically. `BlueprintSelectionPage` no longer contains `updateRecipeGrid`, `hideRemainingCells`, `buildRecipeGridBindings`, or the 7 grid-layout fields. |

#### Wave 2: DetailPanelController (no compile dependency on Wave 1)

| Unit | Methods | Done When |
|------|---------|-----------|
| `DetailPanelController.java` | `DetailPanelController(affordabilityMode, maxCostCells)`, `updateUI(cmd, selectedRecipeId, allRecipes, playerInventory, affordabilityMode)` | Detail panel renders identically. `BlueprintSelectionPage` no longer contains `updateDetailPanel` body (only a one-line delegation call). |

#### Wave 3: Integration wiring (depends on Waves 1+2)

| Unit | Description | Done When |
|------|-------------|-----------|
| `BlueprintSelectionPage.java` cleanup | Instantiate both controllers in `build()`. Replace all call sites of removed methods with delegation calls. Verify `handleDataEvent` routes correctly. Remove dead fields. | Full build passes, all 5 acceptance criteria met. |

### Risk and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Grid controller needs access to page-level fields (`displayedRecipes`, `selectedRecipeId`) | Tight coupling or excessive parameter passing | Pass as method parameters (same pattern as `IngredientTreeGridController.updateUI(cmd)` which receives model state). Don't store page reference in controller. |
| `buildRecipeGridBindings` relies on `totalSetCount`/`cellsPerSet` computed in page's `build()` | Initialization order dependency | Compute `MaxLayoutInfo` in page, pass to `GridLayoutController` constructor. Controller owns derived arrays. |
| Splitting breaks `sendUpdate()` calls | Rendering fails silently | Controllers never call `sendUpdate()` — they only write to the `UICommandBuilder`. The page calls `sendUpdate(cmd, null, false)` after all controllers finish. |

### Estimated Complexity: **M** (3 files, 2 new + 1 heavily modified, ~250 lines moved)

---

## Summary Table

| Item | Scope | Complexity | Key Risk | Parallelizable With |
|------|-------|-----------|----------|---------------------|
| #5 Particle Loop | 1 file | S | API signature uncertainty | All others |
| #9 Listener Dereg | 1 file | S | Return type assumption | All others |
| #11 Package Move | 3 files | S | Manifest property name | All others |
| #17 God Class | 3 files | M | Grid state coupling | Items #5, #9, #11 |

All four items are independent — they can be executed in parallel as separate work units. Item #17 has internal sequencing (Wave 1 → Wave 2 → Wave 3) but no dependency on the other three.

---

## Handoff Checklist

- [x] Approach documented for each item
- [x] Files to create/modify listed
- [x] Acceptance criteria defined (testable behaviors)
- [x] Execution waves with dependencies specified
- [x] Risk and mitigation identified
- [x] Complexity estimated
- [x] All items parallelizable at the top level

---

→ **@Engineer** implement `docs/design-deferred-fixes.md` — all 4 items can start in parallel.
