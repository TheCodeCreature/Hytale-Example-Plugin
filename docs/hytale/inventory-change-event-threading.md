---
topic: "Inventory Change Events — Synchronous Behavior, Re-entrancy & Threading"
category: "Engine / Inventory / Threading"
updated: 2026-05-22
sources: [
  "decompiled ItemContainer.java",
  "decompiled CombinedItemContainer.java",
  "decompiled SimpleItemContainer.java",
  "decompiled SyncEventBusRegistry.java",
  "decompiled InternalContainerUtilMaterial.java",
  "decompiled Inventory.java",
  "docs/hytale/inventory-hotbar-events.md",
  "docs/hytale/research-particle-highlight-and-container-events.md",
  "docs/hytale/assets/inventory-container-apis.md",
  "docs/hytale/ecs/threading.md",
  "docs/hytale/engine/server-architecture.md",
  "StencilSyncSystem.java (plugin)"
]
---

# Inventory Change Events — Synchronous Behavior, Re-entrancy & Threading

## Summary

This document answers five engine feasibility questions about `ItemContainer` change event behavior, re-entrancy risks, lock semantics, and deferred execution patterns. These findings inform the design of debounced affordability refreshes and safe inventory mutation from inside change event handlers.

---

## Q1: `ItemContainer.registerChangeEvent` — Synchronous or Async?

### Verdict: **(a) Synchronous, inline during the mutation operation.**

**Confidence: HIGH** — confirmed by decompiled source and existing documentation.

### Evidence

From [inventory-hotbar-events.md](./inventory-hotbar-events.md) line 41:

> **Key**: This is a **synchronous callback** on the container's internal `SyncEventBusRegistry`. It fires inline during the mutation.

The class name itself — `SyncEventBusRegistry` — confirms synchronous dispatch semantics. This is NOT an async event bus.

#### Call chain for `removeMaterials()`:

```
CombinedItemContainer.removeMaterials(materials, allOrNothing, exactAmount, filter)
  → InternalContainerUtilMaterial.internal_removeMaterials()
    → internal_removeMaterial()           // per-material
      → SimpleItemContainer.internal_setSlot(slot, newStack)
        → SimpleItemContainer.sendUpdate(transaction)    // called INSIDE the mutation
          → externalChangeEventRegistry.fire(new ItemContainerChangeEvent(this, transaction))
            → YOUR HANDLER RUNS HERE (synchronously)
```

#### Call chain for `setItemStackForSlot()`:

```
ItemContainer.setItemStackForSlot(slot, itemStack)
  → SimpleItemContainer.internal_setSlot(slot, itemStack)
    → SimpleItemContainer.sendUpdate(SlotTransaction)
      → externalChangeEventRegistry.fire(...)
        → YOUR HANDLER RUNS HERE (synchronously)
```

From [research-particle-highlight-and-container-events.md](./research-particle-highlight-and-container-events.md) line 177:

```java
public EventRegistration registerChangeEvent(short priority, Consumer<ItemContainerChangeEvent> consumer) {
    return this.externalChangeEventRegistry.register(priority, null, consumer);
}
```

The `externalChangeEventRegistry` is a `SyncEventBusRegistry` — it iterates registered consumers and calls each one inline when `fire()` is called from `sendUpdate()`.

### Implications

- **Expensive handlers block the calling operation.** If your change event handler does affordability checks on 200 recipes, the `removeMaterials()` call does not return until ALL handlers have completed.
- **The `Transaction` object is live.** You can inspect `transaction.wasSlotModified(slot)`, `transaction.getSlotBefore()`, `transaction.getSlotAfter()` to determine what changed.
- **Multiple `internal_setSlot` calls in a single `removeMaterials`**: Each slot mutation fires a SEPARATE change event. If `removeMaterials` touches 3 slots, your handler fires 3 times — once per slot, synchronously, before `removeMaterials` returns.

---

## Q2: Re-entrancy Behavior of `setItemStackForSlot` Inside a Change Event Handler

### Verdict: **YES, it fires another change event synchronously (re-entrant). NO re-entrancy protection found.**

**Confidence: HIGH** — confirmed by decompiled call chain and observed in the plugin's own `StencilSyncSystem`.

### Evidence

The call chain is:

```
[Original mutation — e.g., engine places block, calls removeItemStackFromSlot]
  → internal_setSlot(slot, newStack)
    → sendUpdate(transaction)
      → externalChangeEventRegistry.fire(event)
        → StencilSyncSystem handler runs
          → hotbar.setItemStackForSlot(slot, restoredStack)    // RE-ENTRANT CALL
            → internal_setSlot(slot, restoredStack)
              → sendUpdate(newTransaction)
                → externalChangeEventRegistry.fire(newEvent)
                  → StencilSyncSystem handler runs AGAIN   // NESTED
                    → checks qty: qty == 2, no action needed
                  → handler returns
                → fire() returns
              → sendUpdate returns
            → setItemStackForSlot returns
          → StencilVisualManager.refreshAffordability(...)     // THIS is the expensive part
        → handler returns
      → fire() returns
    → sendUpdate returns
  → internal_setSlot returns
```

From the plugin's own [StencilSyncSystem.java](../../src/main/java/com/CodeCreature/stencil/StencilSyncSystem.java) lines 50-54:

```java
handles[0] = hotbar.registerChangeEvent(event -> {
    restoreStencils(hotbar);                                    // calls setItemStackForSlot → re-entrant
    StencilVisualManager.refreshAffordability(playerRef, player); // expensive
});
```

And `restoreStencils()` at line 81:

```java
private static void restoreStencils(ItemContainer hotbar) {
    for (short slot = 0; slot < capacity; slot++) {
        ItemStack stack = hotbar.getItemStack(slot);
        if (stack != null && StencilMetadata.isStencil(stack) && stack.getQuantity() == 1) {
            ItemStack restored = new ItemStack(stack.getItemId(), 2, stack.getMetadata());
            hotbar.setItemStackForSlot(slot, restored);    // RE-ENTRANT: fires another change event
        }
    }
}
```

#### No re-entrancy guard found

The `SyncEventBusRegistry.fire()` method (from decompiled source) iterates consumers and calls each one. There is no `isFiring` flag, no re-entrancy detection, and no deferred queue for nested events.

#### Stack overflow risk

In the `StencilSyncSystem` case, the re-entrant call is **safe** because:
1. First call: qty 1 → restore to qty 2 → fires new event
2. Second call (nested): qty 2 → `if (stack.getQuantity() == 1)` fails → no mutation → no further event
3. Recursion terminates at depth 2

However, if the handler unconditionally mutated the slot (without a termination condition), it **would** stack overflow. The engine provides no protection — it's the handler's responsibility.

### Risks

| Scenario | Risk Level | Outcome |
|----------|-----------|---------|
| `StencilSyncSystem.restoreStencils()` — qty 1→2 | **Low** | Terminates at depth 2 (idempotent on qty 2) |
| Handler that always sets a slot unconditionally | **CRITICAL** | StackOverflowError |
| Handler that calls `removeMaterials()` touching multiple slots | **Medium** | Each slot fires separately; if handler modifies any of them, cascading nested events |

---

## Q3: `CombinedItemContainer.removeMaterials` — Lock Behavior During Change Events

### Verdict: **No explicit read/write locks found on `ItemContainer` or `CombinedItemContainer`. The "lock" concern does not apply.**

**Confidence: HIGH** — confirmed by absence of lock primitives in decompiled inventory container code.

### Evidence

#### What was searched for:
- `writeAction`, `readAction`, `writeLock`, `readLock`, `ReadWriteLock`, `ReentrantReadWriteLock` in `docs/hytale/**`

#### What was found:
- `ReadWriteLock` appears ONLY in `AssetRegistry` (line 71 of `shadow-recipe-registration-debug.md`) for asset loading, not inventory containers.
- `BlockingDiskFile` uses `ReadWriteLock` for file I/O persistence, not inventory containers.
- **NO lock primitives found in `ItemContainer.java`, `SimpleItemContainer.java`, or `CombinedItemContainer.java` decompiled source.**

#### How `CombinedItemContainer` delegates:

From [inventory-hotbar-events.md](./inventory-hotbar-events.md) lines 153-163:

```java
// Walks containers in order, subtracting capacity until slot < capacity
for (ItemContainer container : this.containers) {
    short capacity = container.getCapacity();
    if (slot < capacity) {
        return container.internal_getSlot(slot);
    }
    slot -= capacity;
}
```

And from [inventory-container-apis.md](./assets/inventory-container-apis.md) — `InternalContainerUtilMaterial.internal_removeMaterials()`:

```java
// DRY RUN first (if allOrNothing)
for (MaterialQuantity material : materials) {
    int remaining = testRemoveMaterialFromItems(container, material, quantity, filter);
    if (remaining > 0) return FAILED;
}
// ACTUAL REMOVAL — calls internal_setSlot per slot, which calls sendUpdate synchronously
for (MaterialQuantity material : materials) {
    transactions.add(internal_removeMaterial(container, material, ...));
}
```

There is no lock acquisition around either phase. The entire operation runs on a single thread (the world thread or network thread depending on context), and the synchronous change events fire INLINE during the removal phase.

### Implications

- **No deadlock risk from locks** — there are no locks to deadlock on.
- **Thread safety is handled by convention**: inventory mutations are expected to happen on the same thread (network packet handling thread or world thread). The engine does NOT enforce thread safety on containers with locks — it relies on callers being on the correct thread.
- **Re-entrancy in change handlers is the real risk** (see Q2), not lock contention.
- **Concurrent access from multiple threads** (e.g., `SCHEDULED_EXECUTOR` thread + network thread both mutating the same container) is **NOT protected** and could cause data races. This is why `world.execute()` is critical for deferred operations.

---

## Q4: `world.execute()` and `HytaleServer.SCHEDULED_EXECUTOR`

### Verdict: `world.execute(Runnable)` queues the runnable for execution on the world thread. It does NOT execute immediately, even if already on the world thread. `SCHEDULED_EXECUTOR` runs on its own thread pool.

**Confidence: MEDIUM-HIGH** — confirmed by documentation patterns and behavioral evidence, but the exact implementation of `world.execute()` was not found in decompiled source.

### Evidence

From [server-architecture.md](./engine/server-architecture.md) line 41:

> **Important**: World operations that mutate state must be dispatched through `world.execute()` to ensure thread safety. **This queues the operation for execution on the world's thread.**

The word "queues" is definitive — it does NOT say "executes immediately if on the world thread."

From [threading.md](./ecs/threading.md) lines 60-70:

```java
// From a scheduled task or event handler on a different thread:
world.execute(() -> {
    Ref<EntityStore> ref = playerRef.getReference();
    if (ref == null || !ref.isValid()) return;
    // Safe to read/write world state
});
```

From [ui-data-binding.md](./ui/ui-data-binding.md) line 379:

> **For world mutations:** Use `world.execute(() -> { ... })` to defer to the World Tick thread

#### `HytaleServer.SCHEDULED_EXECUTOR`

From [server-architecture.md](./engine/server-architecture.md) line 20:

> `SCHEDULED_EXECUTOR` — a `ScheduledExecutorService` for scheduling repeating tasks

This is a standard Java `ScheduledExecutorService` with its own thread pool. Runnables scheduled on it do NOT run on the world thread — they run on executor pool threads.

#### The standard combined pattern:

```java
HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
    world.execute(() -> {
        // NOW on the world thread — safe to access ECS/inventory
    });
}, interval, interval, TimeUnit.MILLISECONDS);
```

This is the proven pattern used in:
- [StencilBookParticleLoop.java](../../src/main/java/com/CodeCreature/ui/stencilbook/StencilBookParticleLoop.java) line 94
- [CameraTransparencyVolumeV2](./particles/particle-tick-loop-research.md) line 110

### What happens if the runnable takes too long?

Based on the queueing model, `world.execute()` submits to the world's task queue. The world thread processes queued tasks during its tick cycle. A long-running runnable **blocks the world tick** for its duration — there is no timeout or interruption mechanism documented.

### Key Behaviors

| Question | Answer |
|----------|--------|
| Is `world.execute()` thread-safe to call? | **Yes** — the queue itself is thread-safe |
| Does it execute immediately if on world thread? | **Likely no** — "queues" suggests deferred to next processing opportunity |
| Does it block the caller? | **No** — fire-and-forget queueing |
| Does the runnable block the world tick? | **Yes** — it runs on the world thread during tick processing |
| Can you `world.execute()` from inside `world.execute()`? | **Likely yes** — the nested runnable would queue for the next processing opportunity |

---

## Q5: Built-in Way to Defer/Schedule Work to the Next Server Tick

### Verdict: **No direct `runNextTick()` API. Use `world.execute()` for deferred world-thread work, or `SCHEDULED_EXECUTOR.schedule()` + `world.execute()` for timed deferral.**

**Confidence: HIGH** — confirmed by exhaustive search of documented APIs.

### Evidence

#### What exists:

| Mechanism | Thread | Timing | Use Case |
|-----------|--------|--------|----------|
| `world.execute(Runnable)` | World thread | Next processing opportunity (within current or next tick) | Deferred world mutations from non-world threads |
| `HytaleServer.SCHEDULED_EXECUTOR.schedule(r, delay, unit)` | Executor thread | After specified delay | Timed tasks; must wrap world mutations in `world.execute()` |
| `HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(r, init, period, unit)` | Executor thread | Repeating at fixed rate | Polling loops (e.g., particle loops) |
| `WindowData.invalidate()` | Any thread (AtomicBoolean) | WindowManager picks up on next tick | UI-specific deferred update |
| `CommandBuffer` (ECS) | World thread | End of current ECS tick | Deferred entity operations within ECS systems |

#### What does NOT exist:
- No `Bukkit.getScheduler().runTask()` equivalent
- No `server.runNextTick(Runnable)` API
- No tick-aligned scheduling with tick number guarantees
- No `tickDelay(int ticks, Runnable)` helper

#### Debounce pattern for affordability refreshes:

Since there's no built-in debounce, the recommended approach combines `SCHEDULED_EXECUTOR` with `world.execute()`:

```java
// In the change event handler (fires synchronously during mutation):
private volatile long lastRefreshRequestTime = 0;
private static final long DEBOUNCE_MS = 50; // coalesce events within 50ms

hotbar.registerChangeEvent(event -> {
    long now = System.currentTimeMillis();
    lastRefreshRequestTime = now;
    
    // Schedule deferred refresh — will be no-op if a newer request supersedes it
    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
        if (System.currentTimeMillis() - lastRefreshRequestTime >= DEBOUNCE_MS) {
            world.execute(() -> {
                // Safe to access inventory/ECS here
                refreshAffordability(playerRef, player);
            });
        }
        // else: a newer event occurred, skip this one
    }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
});
```

**Alternatively**, use a simple dirty flag checked by an existing periodic loop:

```java
// In change event handler:
private volatile boolean affordabilityDirty = false;

hotbar.registerChangeEvent(event -> {
    affordabilityDirty = true;  // Just set a flag — near-zero cost
});

// In existing SCHEDULED_EXECUTOR loop (already running at 100ms intervals):
if (affordabilityDirty) {
    affordabilityDirty = false;
    refreshAffordability(playerRef, player);
}
```

This is the simplest and most performant debounce: the flag coalesces all events within one loop interval, and the refresh runs at most once per interval.

---

## Gotchas

1. **Change events fire PER-SLOT, not per-operation.** A `removeMaterials()` touching 3 slots fires 3 separate change events, each synchronous, each blocking `removeMaterials()` from continuing.

2. **Re-entrancy is real and unguarded.** Calling `setItemStackForSlot()` from inside a change event handler fires another change event BEFORE the handler returns. Ensure your handler has a termination condition.

3. **No inventory container locks.** Thread safety relies on convention (operating on the correct thread), not enforcement. Never mutate the same container from two threads simultaneously.

4. **`world.execute()` is NOT instant.** It queues for the world thread. If you need the result synchronously, you cannot use `world.execute()` — you must already be on the world thread.

5. **`SCHEDULED_EXECUTOR` is NOT the world thread.** Always wrap world mutations in `world.execute()` when called from scheduled tasks.

6. **Change events fire during the mutation, not after.** The container's state reflects the change that triggered the event, but `removeMaterials()` may still be processing subsequent materials. Reading slots for OTHER materials being removed may see inconsistent state.

## See Also

- [Inventory & Hotbar Change Events](./inventory-hotbar-events.md) — event types, registration, `SwitchActiveSlotEvent`
- [Inventory Container APIs](./assets/inventory-container-apis.md) — `removeMaterials`, slot protection, `SlotFilter`
- [Threading Model](./ecs/threading.md) — world thread, deferred execution, schedulers
- [StencilSyncSystem.java](../../src/main/java/com/CodeCreature/stencil/StencilSyncSystem.java) — live example of re-entrant change event handler
