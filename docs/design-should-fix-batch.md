# Design: Should-Fix Batch (Fixes #3, #4, #10, #15, #16, #18, #20)

## 1. Overview

Seven low-to-medium risk fixes addressing logging consistency, race conditions, dead code, reflection duplication, excessive log verbosity, and a lifecycle leak. All fixes are independent except #3 and #4 which share a prerequisite (expanding `AssetFieldAccessor`).

## 2. Design Priorities

1. **Correctness** — eliminate race condition, dead code, and lifecycle leak
2. **Consistency** — unify all logging behind `java.util.logging.Logger`
3. **Simplicity** — minimal changes, no new abstractions beyond what already exists
4. **Fail-fast** — reflection consolidation must preserve startup-time validation

## 3. Cross-Cutting Decision: AssetFieldAccessor Visibility

**Decision: Make `AssetFieldAccessor` public.**

Rationale:
- Currently package-private in `com.CodeCreature.scaling`
- Needed by `com.CodeCreature.crafting.StencilBookRecipeMutator` (Fix #3) and `com.CodeCreature.registry.RecipeFilterRegistry` (Fix #4)
- Creating a second accessor class would duplicate the resolution pattern
- Moving the class to a `util` package is a larger refactor than warranted
- Making it public with a clear Javadoc contract ("internal plugin class, not API") is the simplest path

**New fields to add:**
| Field | Class | Name | Consumer |
|-------|-------|------|----------|
| `recipeId` | `CraftingRecipe` | `"id"` | StencilBookRecipeMutator |
| `recipeBenchRequirement` | `CraftingRecipe` | `"benchRequirement"` | StencilBookRecipeMutator |
| `recipeKnowledgeRequired` | `CraftingRecipe` | `"knowledgeRequired"` | StencilBookRecipeMutator |
| `recipeMemoriesLevel` | `CraftingRecipe` | `"requiredMemoriesLevel"` | StencilBookRecipeMutator |
| `itemSet` | `Item` | `"set"` | ResourceTypeResolver, RecipeFilterRegistry |

---

## 4. Fix Designs

---

### Fix #20: Logging Consistency

**Approach:** Keep the `private static void log(String msg)` helper in each file — it provides a consistent tag prefix and keeps call sites clean. Change the method body to delegate to a `Logger` instance instead of `System.out.println`. For `AbstractBenchProcessor` (inline call) and `BreakBlockDiagnostic` (StringBuilder dump), add a Logger field and replace the println directly.

**Files to modify:**

| File | Change |
|------|--------|
| `scaling/NaturalResourceRegistry.java` | Add `private static final Logger LOGGER = Logger.getLogger("NaturalResourceRegistry");` <br> Change `log()` body → `LOGGER.info(msg);` |
| `scaling/DropScaler.java` | Add `private static final Logger LOGGER = Logger.getLogger("DropScaler");` <br> Change `log()` body → `LOGGER.info(msg);` |
| `scaling/BreakBlockDiagnostic.java` | Add `private static final Logger LOGGER = Logger.getLogger("BreakBlockDiagnostic");` <br> Line 190: `System.out.println(sb)` → `LOGGER.info(sb.toString())` |
| `scaling/AbstractBenchProcessor.java` | Add `private static final Logger LOGGER = Logger.getLogger("AbstractBenchProcessor");` (or protected instance) <br> Line 133: `System.out.println(...)` → `LOGGER.warning(...)` (it's an error path) |
| `registry/RecipeFilterRegistry.java` | Add `private static final Logger LOGGER = Logger.getLogger("RecipeFilterRegistry");` <br> Change `log()` body → `LOGGER.info(msg);` |
| `registry/BenchRecipeRegistry.java` | Add `private static final Logger LOGGER = Logger.getLogger("BenchRecipeRegistry");` <br> Change `log()` body → `LOGGER.info(msg);` |
| `registry/BenchRecipeRegistries.java` | Add `private static final Logger LOGGER = Logger.getLogger("BenchRecipeRegistries");` <br> Change `log()` body → `LOGGER.info(msg);` |
| `crafting/StencilBookRecipeMutator.java` | Add `private static final Logger LOGGER = Logger.getLogger("StencilBookRecipeMutator");` <br> Change `log()` body → `LOGGER.info(msg);` |

**Risk:** Low — behavioral change is only output destination (stdout → Logger handler). All existing log messages preserved verbatim.

**Order dependency:** None. Can be done first or in any order.

---

### Fix #10: BreakBlockDiagnostic.toggle() Race Condition

**Approach:** Replace the TOCTOU `enabled.getAndSet(!enabled.get())` with a CAS loop using `AtomicBoolean.compareAndSet()`. This is a textbook fix — loop until CAS succeeds. Alternatively, since `toggle()` is only called from a command handler (single server thread), we could simplify to `!enabled.getAndSet(!enabled.get())` → but the CAS loop is more correct and costs nothing.

**Replacement code:**
```java
public static boolean toggle() {
    boolean prev, next;
    do {
        prev = enabled.get();
        next = !prev;
    } while (!enabled.compareAndSet(prev, next));
    return next; // returns new state
}
```

**Files to modify:**

| File | Change |
|------|--------|
| `scaling/BreakBlockDiagnostic.java` | Replace `toggle()` method body (lines ~56-58) with CAS loop |

**Risk:** Low — the race window was unlikely to matter in practice (command handler is single-threaded), but the fix is trivially correct and costs nothing.

**Order dependency:** None.

---

### Fix #18: BreakBlockDiagnostic Dead BenchBlockClassifier Instantiation

**Approach:** Delete the line `BenchBlockClassifier classifier = new BenchBlockClassifier();` — it creates an unpopulated instance that's never used. The subsequent `BenchRecipeRegistries.getRecipeForBlock(btId)` call already provides the needed information. This was likely leftover from an earlier iteration.

**Files to modify:**

| File | Change |
|------|--------|
| `scaling/BreakBlockDiagnostic.java` | Delete line ~84: `BenchBlockClassifier classifier = new BenchBlockClassifier();` <br> Delete comment on line ~85: `// We can't re-classify here, but we can check the recipe registries` |

**Risk:** Low — the variable `classifier` is never read. Pure dead code removal.

**Order dependency:** None.

---

### Fix #4: Duplicate Item.set Reflection

**Approach:** Add an `itemSet` field to `AssetFieldAccessor`. Both `ResourceTypeResolver` and `RecipeFilterRegistry` delete their local `SET_FIELD`/`ITEM_SET_FIELD` static initializers and instead read from a shared `AssetFieldAccessor` instance. Since `AssetFieldAccessor` is currently instantiated by `DropScaler`, we need a singleton or static accessor. **Proposed:** Add a `public static final AssetFieldAccessor INSTANCE` field initialized eagerly, making the class self-initializing.

**Files to modify:**

| File | Change |
|------|--------|
| `scaling/AssetFieldAccessor.java` | 1. Change visibility: `final class` → `public final class` <br> 2. Add field: `final Field itemSet;` (resolves `Item.class, "set"`) <br> 3. Add: `public static final AssetFieldAccessor INSTANCE = new AssetFieldAccessor();` <br> 4. Update error message to include field name: `"Failed to resolve " + name + " on " + clazz.getSimpleName()` |
| `scaling/ResourceTypeResolver.java` | Remove `SET_FIELD` static initializer block (lines 43-49). Replace `SET_FIELD.get(item)` usage (line 183) with `AssetFieldAccessor.INSTANCE.itemSet.get(item)` |
| `registry/RecipeFilterRegistry.java` | Remove `ITEM_SET_FIELD` static initializer block (lines 67-76). Replace `ITEM_SET_FIELD.get(item)` usage (line 322) with `AssetFieldAccessor.INSTANCE.itemSet.get(item)`. Remove null-check at line 320 (fail-fast is now guaranteed at startup). |

**Risk:** Medium — changes static initialization order. `AssetFieldAccessor.INSTANCE` will initialize when the class is first loaded. If `ResourceTypeResolver` or `RecipeFilterRegistry` is loaded before the Hytale API classes are available, this could fail. However, both are currently called only from `DropScaler.apply()` which runs after `LoadAssetEvent`, so the order is safe.

**Order dependency:** Must complete before Fix #3 (which adds more fields to `AssetFieldAccessor`). Alternatively, do #3 and #4 together as one edit to `AssetFieldAccessor`.

---

### Fix #3: StencilBookRecipeMutator Independent Reflection

**Approach:** Add four fields to `AssetFieldAccessor` (`recipeId`, `recipeBenchRequirement`, `recipeKnowledgeRequired`, `recipeMemoriesLevel`). Remove the 5-field local resolution from `StencilBookRecipeMutator.mutate()` and read from `AssetFieldAccessor.INSTANCE`. Note: `recipeInput` already exists in `AssetFieldAccessor`.

**Files to modify:**

| File | Change |
|------|--------|
| `scaling/AssetFieldAccessor.java` | Add 4 new fields under the `// CraftingRecipe` section: <br> `final Field recipeId;` → `resolve(CraftingRecipe.class, "id")` <br> `final Field recipeBenchRequirement;` → `resolve(CraftingRecipe.class, "benchRequirement")` <br> `final Field recipeKnowledgeRequired;` → `resolve(CraftingRecipe.class, "knowledgeRequired")` <br> `final Field recipeMemoriesLevel;` → `resolve(CraftingRecipe.class, "requiredMemoriesLevel")` |
| `crafting/StencilBookRecipeMutator.java` | Remove local field resolution in `mutate()` (lines 38-50). Replace with: <br> `AssetFieldAccessor f = AssetFieldAccessor.INSTANCE;` <br> Use `f.recipeId`, `f.recipeInput`, `f.recipeBenchRequirement`, `f.recipeKnowledgeRequired`, `f.recipeMemoriesLevel` at each `.set()` call. <br> Remove the early-return error path (fail-fast now happens at startup). Add import for `AssetFieldAccessor`. |

**Risk:** Medium — same initialization-order consideration as Fix #4. The mutator runs after `DropScaler.apply()` which already uses `AssetFieldAccessor`, so the instance is guaranteed to exist.

**Order dependency:** Fix #4 must be done first (or simultaneously) because #4 introduces the `INSTANCE` pattern and makes the class public.

---

### Fix #16: StencilVisualManager Excessive INFO Logging

**Approach:** Downgrade per-operation `LOGGER.info(...)` calls to `LOGGER.fine(...)`. Keep `LOGGER.warning(...)` and `LOGGER.info(...)` for one-time events (player connect, quality not found). The specific lines firing per hotbar mutation are in `scanAndSend()` and `applyVisuals()`.

**Lines to downgrade (INFO → FINE):**

| Line | Current | Rationale for downgrade |
|------|---------|------------------------|
| `applyVisuals` L110 | `"applyVisuals called for"` | Fires on every connect — keep as INFO (one-time) |
| `applyVisuals` L112 | `"Quality indices: affordable=..."` | Fires on every connect — downgrade to FINE (diagnostic detail) |
| `sendCustomQualities` L164 | `"Sending UpdateItemQualities..."` | One-time per connect — keep as INFO |
| `scanAndSend` L222 | `"scanAndSend: hotbar capacity=..."` | Fires on every hotbar mutation — downgrade to FINE |
| `scanAndSend` L241 | `"slot X: stencil found..."` | Fires per stencil per mutation — downgrade to FINE |
| `scanAndSend` L258 | `"scanAndSend: found X stencils..."` | Fires on every mutation — downgrade to FINE |
| `scanAndSend` L261 | `"Sending UpdateItems with..."` | Fires when state changes — keep as INFO (meaningful event) |

**Files to modify:**

| File | Change |
|------|--------|
| `stencil/StencilVisualManager.java` | Change 4 `LOGGER.info(...)` calls to `LOGGER.fine(...)` as identified above |

**Risk:** Low — purely reduces log noise. No behavioral change. Diagnostic info still available via Logger level configuration.

**Order dependency:** None.

---

### Fix #15: StencilBookParticleLoop Lifecycle Leak

**Approach:** Add a staleness check inside the `startUpdateLoop()` scheduled task. When `active` is set to `false` (player ref became invalid), the task should also call `INSTANCES.remove(playerRef.getUuid())` and cancel itself. This ensures cleanup happens even if `remove()` is never called externally (player crash without disconnect event). Additionally, cancel the `ScheduledFuture` from within.

**Design detail:** The constraint says "must not cause particle highlight to disappear prematurely during normal use." The `active = false` code path only triggers when `ref == null || !ref.isValid()` or `player == null` — these indicate the player is gone, not just switching items. The normal "no longer holding book" path calls `removeHighlightEntity()` but does NOT set `active = false`, so the loop continues polling. This is safe.

**Implementation:**
```java
// Inside the scheduled task, after setting active = false:
if (!active) {
    INSTANCES.remove(playerRef.getUuid());
    updateTask.cancel(false);
    return;
}
```

But there's a subtlety: the task references `updateTask` which may not be assigned yet (race between `scheduleAtFixedRate` return and first execution). Solution: check `active` at the TOP of the task body (already done), and perform cleanup at the end of the execution that sets `active = false`.

**Revised approach — two changes:**

1. After the lines that set `active = false` (two locations in the `world.execute()` lambda), add:
   ```java
   active = false;
   INSTANCES.remove(playerRef.getUuid());
   // Cancel is handled by the outer active check
   return;
   ```

2. At the top of the scheduled task (before `world.execute()`), add:
   ```java
   if (!active) {
       updateTask.cancel(false);
       return;
   }
   ```
   This ensures the next tick after `active = false` cancels the future and prevents further scheduling.

**Files to modify:**

| File | Change |
|------|--------|
| `ui/stencilbook/StencilBookParticleLoop.java` | 1. After each `active = false;` (2 locations inside `world.execute()`), add `INSTANCES.remove(playerRef.getUuid());` <br> 2. At the start of the scheduled task lambda body (before `world.execute()`), add early-exit: `if (!active) { updateTask.cancel(false); return; }` |

**Risk:** Medium — modifying concurrent lifecycle code. The `INSTANCES.remove()` call from inside the task may race with an external `start()` call for the same player. However, `start()` already handles stale entries by calling `shutdown()` on them, and `ConcurrentHashMap.remove()` is atomic, so the worst case is a redundant no-op remove. The `updateTask.cancel(false)` at the top prevents the no-op task from running indefinitely.

**Order dependency:** None.

---

## 5. Execution Order

```
Wave 1 (independent — can be done in parallel):
  • Fix #20 (logging consistency)
  • Fix #10 (toggle race condition)
  • Fix #18 (dead code removal)
  • Fix #16 (log level downgrade)
  • Fix #15 (lifecycle leak)

Wave 2 (depends on each other, do together):
  • Fix #4 (Item.set consolidation) — makes AssetFieldAccessor public, adds INSTANCE + itemSet
  • Fix #3 (StencilBookRecipeMutator consolidation) — adds 4 more fields, consumes INSTANCE
```

Recommended: Do Fix #4 and Fix #3 as a single commit since they both modify `AssetFieldAccessor`.

---

## 6. Summary Table

| Fix | Risk | LOC Δ (est) | Order Dep | Compiler-Verifiable |
|-----|------|-------------|-----------|---------------------|
| #20 Logging | Low | +16, −8 | None | Yes |
| #10 Toggle race | Low | +6, −2 | None | Yes |
| #18 Dead classifier | Low | +0, −2 | None | Yes |
| #4 Item.set dup | Medium | +5, −20 | None | Yes |
| #3 Mutator reflection | Medium | +8, −15 | #4 | Yes |
| #16 Log levels | Low | +0, −0 (edits) | None | Yes |
| #15 Lifecycle leak | Medium | +6, −0 | None | Yes (runtime verify needed) |

---

## 7. Open Questions

1. **Logger names:** Should logger names match the class simple name (e.g., `"DropScaler"`) or use the fully qualified class name convention (`DropScaler.class.getName()`)? Current codebase uses short names (e.g., `"StencilBookParticleLoop"`, `"StencilVisualManager"`). **Recommendation:** Keep short names for consistency with existing code.

2. **RecipeFilterRegistry soft-failure:** The current `ITEM_SET_FIELD` resolution in `RecipeFilterRegistry` uses a soft-failure pattern (sets field to null, logs to stderr, continues). After consolidation into `AssetFieldAccessor`, this becomes fail-fast. Is this acceptable? **Recommendation:** Yes — if `Item.set` doesn't exist, the entire resource-type filter system is broken anyway. Fail-fast is more honest.

---

## Handoff Checklist
- [x] All 7 fixes have concrete approach descriptions
- [x] Files to modify listed per fix
- [x] Risk assessment provided per fix
- [x] Order dependencies documented
- [x] Cross-cutting AssetFieldAccessor visibility decision made
- [x] Open questions documented
- [x] Execution waves defined

→ @Engineer implement `docs/design-should-fix-batch.md`
