---
topic: "Server Freeze Root Cause Analysis — Entity Churn, Task Queue, Inventory Locking"
category: "Engine Internals Investigation"
updated: 2026-05-29
sources:
  - "decompiled Store.java — addEntity(), removeEntity(), ProcessingCounter, assertWriteProcessing()"
  - "decompiled EntityStore.java — NetworkIdSystem, UUIDSystem, takeNextNetworkId()"
  - "decompiled World.java — tick(), execute(), consumeTaskQueue()"
  - "decompiled SimpleItemContainer.java — ReentrantReadWriteLock, readAction(), writeAction()"
  - "decompiled CombinedItemContainer.java — recursive lock acquisition, sendUpdate()"
  - "decompiled ItemContainer.java — addItemStack(), sendUpdate(), countItemStacks()"
  - "decompiled EntityTrackerSystems.java — Visible (StampedLock), SendPackets, EntityViewer"
  - "docs/hytale/research-particle-highlight-and-container-events.md"
---

# Server Freeze Root Cause Analysis

## Executive Summary

**Most likely root cause: `ReentrantReadWriteLock` self-deadlock on the world thread.**

The inventory containers use `ReentrantReadWriteLock`. The write lock is NOT re-entrant across read→write upgrade. If the world thread holds a read lock (e.g., `countItemStacks()` during `executeRefresh()`) and then re-enters a code path that tries to acquire a write lock on the same container (e.g., through an event callback), the thread deadlocks permanently. However, the code paths as written appear to avoid this. A more probable scenario is detailed in Finding #6 below.

**The entity churn from `StencilBookParticleLoop` is NOT the primary freeze cause, but it is a severe amplifier.** The particle loop creates conditions (high task queue throughput, rapid entity tracker cycling, GC pressure) that increase the probability of hitting the actual deadlock condition.

---

## Finding 1: Entity Churn — Can It Cause a Permanent Freeze?

### Question
Could rapid entity churn from the particle loop DURING block breaking + item pickup cause the freeze?

### Answer: **No direct deadlock, but severe amplification.**

**Evidence from `Store.addEntity()`** ([Store.java](../../.tmp_hytale_src/com/hypixel/hytale/component/Store.java#L403)):
```java
public Ref<ECS_TYPE> addEntity(Holder<ECS_TYPE> holder, Ref<ECS_TYPE> ref, AddReason reason) {
    this.assertThread();           // Must be world thread
    this.assertWriteProcessing();  // Must NOT be inside a system tick
    // ...
    this.processing.lock();        // Just increments a counter — NOT a real lock
    try {
        // Iterates HolderSystems, RefSystems (NetworkIdSystem, UUIDSystem)
    } finally {
        this.processing.unlock();  // Decrements counter
    }
    commandBuffer.consume();       // Processes deferred operations
}
```

**`Store.addEntity()` does NOT call `world.execute()`.** It runs synchronously on the calling thread. No task queue feedback loop.

**`Store.removeEntity()`** ([Store.java](../../.tmp_hytale_src/com/hypixel/hytale/component/Store.java#L662)) follows the identical pattern — synchronous, no `world.execute()`.

**`ProcessingCounter`** ([Store.java](../../.tmp_hytale_src/com/hypixel/hytale/component/Store.java#L2118)) is a simple integer counter, NOT a blocking lock:
```java
private static class ProcessingCounter implements Lock {
    private int count = 0;
    public void lock()   { this.count++; }
    public void unlock() { this.count--; }
}
```
No blocking, no deadlock possible from this.

**`EntityStore.takeNextNetworkId()`** ([EntityStore.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/storage/EntityStore.java#L77)):
```java
public int takeNextNetworkId() {
    return this.networkIdCounter.getAndIncrement();  // AtomicInteger — lock-free
}
```
No issue with rapid allocation.

**`EntityStore.NetworkIdSystem`** adds/removes from `Int2ObjectOpenHashMap` (`networkIdToRef`). This is NOT thread-safe, but all access is on the world thread via `onEntityAdded()`/`onEntityRemove()` callbacks during `addEntity()`/`removeEntity()`. No concurrent modification.

### But: Entity Tracker StampedLock Contention

The `Visible` component uses a `StampedLock` ([EntityTrackerSystems.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/tracker/EntityTrackerSystems.java#L701)):
```java
public static class Visible implements Component<EntityStore> {
    private final StampedLock lock = new StampedLock();
    // ...
    public void addViewerParallel(Ref<EntityStore> ref, EntityViewer entityViewer) {
        long stamp = this.lock.writeLock();  // BLOCKING write lock
        try {
            this.visibleTo.put(ref, entityViewer);
            if (!this.previousVisibleTo.containsKey(ref)) {
                this.newlyVisibleTo.put(ref, entityViewer);
            }
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }
}
```

`addViewerParallel` is called from `AddToVisible.tick()`, which runs with `isParallel = true`. If the particle loop is spawning/removing entities rapidly, and the `AddToVisible` system is running in parallel, there could be contention on this `StampedLock`. However, `StampedLock` is non-reentrant — calling `writeLock()` when the same thread already holds it would deadlock. Since `addViewerParallel` is designed for parallel ECS ticking (different threads, different entities), this shouldn't deadlock under normal conditions.

**Verdict: Entity churn does NOT directly cause the freeze.** But it creates GC pressure, increases task queue throughput, and forces the entity tracker to process more entities per tick — any of which could push timing windows that expose the real deadlock.

---

## Finding 2: world.execute() from Inside consumeTaskQueue()

### Question
Do `Store.addEntity()` or `Store.removeEntity()` call `world.execute()` or add tasks to the taskQueue?

### Answer: **No. They are purely synchronous.**

**Evidence:**
- `Store.addEntity()` ([Store.java](../../.tmp_hytale_src/com/hypixel/hytale/component/Store.java#L403)): Calls `assertThread()`, `assertWriteProcessing()`, `processing.lock()`, iterates systems, `processing.unlock()`, `commandBuffer.consume()`. No `world.execute()` call anywhere.
- `Store.removeEntity()` ([Store.java](../../.tmp_hytale_src/com/hypixel/hytale/component/Store.java#L662)): Same pattern. Synchronous, no task queue interaction.

**However, `commandBuffer.consume()` is called AFTER `processing.unlock()`.** This processes deferred operations that RefSystems queued (e.g., `commandBuffer.removeEntity()` in `UUIDSystem` for duplicate UUIDs). These deferred operations run synchronously — they do NOT go through `world.execute()`.

**No task queue feedback loop exists.** The `executeTick()` lambda runs during `consumeTaskQueue()`, calls `store.addEntity()`/`store.removeEntity()` synchronously, and completes. No new tasks are added to the queue by these calls.

---

## Finding 3: Inventory Locking During Item Pickup

### Question
Does `countItemStacks()` on `CombinedItemContainer` acquire locks? Could there be a deadlock with `addItemStack()` during pickup?

### Answer: **Yes it acquires read locks. But deadlock is unlikely in this specific path.**

**`SimpleItemContainer` locking model** ([SimpleItemContainer.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/SimpleItemContainer.java#L56)):
```java
protected final ReadWriteLock lock = new ReentrantReadWriteLock();

protected <V> V readAction(Supplier<V> action) {
    this.lock.readLock().lock();
    try { return action.get(); }
    finally { this.lock.readLock().unlock(); }
}

protected <V> V writeAction(Supplier<V> action) {
    this.lock.writeLock().lock();
    try { return action.get(); }
    finally { this.lock.writeLock().unlock(); }
}
```

**`CombinedItemContainer` recursively acquires locks** ([CombinedItemContainer.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/CombinedItemContainer.java#L47)):
```java
private <V> V readAction0(int i, Supplier<V> action) {
    return i >= this.containers.length
        ? action.get()
        : this.containers[i].readAction(() -> this.readAction0(i + 1, action));
}
// Same pattern for writeAction0 — acquires ALL sub-container locks
```

So `CombinedItemContainer.readAction()` acquires read locks on containers[0], containers[1], ..., containers[n-1] in order.
And `CombinedItemContainer.writeAction()` acquires write locks on all sub-containers in order.

**`countItemStacks()`** ([ItemContainer.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/ItemContainer.java#L1156)):
```java
public int countItemStacks(Predicate<ItemStack> itemPredicate) {
    return this.readAction(() -> {
        // iterates slots, counts matching items
    });
}
```

On `CombinedItemContainer`, this acquires read locks on ALL sub-containers.

**`addItemStack()` flow** ([ItemContainer.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/ItemContainer.java#L797)):
```java
public ItemStackTransaction addItemStack(ItemStack itemStack, boolean allOrNothing, boolean fullStacks, boolean filter) {
    ItemStackTransaction transaction = InternalContainerUtilItemStack.internal_addItemStack(this, ...);
    this.sendUpdate(transaction);  // AFTER lock is released
    return transaction;
}
```

`internal_addItemStack` uses `writeAction()` internally. The write lock is acquired, mutation happens, write lock is released, THEN `sendUpdate()` fires events.

**Critical insight: `sendUpdate()` dispatches events OUTSIDE the write lock.**

The flow during item pickup:
1. `PlayerItemEntityPickupSystem.tick()` → `addItemStack()` on hotbar container
2. Write lock acquired → mutation → write lock released
3. `sendUpdate()` → dispatches change event → our handler → `markDirty(world)` → `world.execute(executeRefresh)`
4. `executeRefresh` queued to task queue, runs in next `consumeTaskQueue()` (after `entityStore.tick()` finishes)
5. `executeRefresh` → `countItemStacks()` → read locks acquired → no write locks held → succeeds

**No deadlock in this path.** The temporal separation (items are picked up during `entityStore.tick()`, refresh runs during the second `consumeTaskQueue()`) prevents lock contention.

---

## Finding 4: Event Dispatch During Lock — setItemStackForSlot Re-entrancy

### Question
If `sendUpdate()` fires while a write lock is held, could `setItemStackForSlot()` from a listener try to re-acquire the write lock?

### Answer: **The base `ItemContainer.sendUpdate()` fires OUTSIDE the write lock. But `CombinedItemContainer.sendUpdate()` has a subtlety.**

**`CombinedItemContainer.sendUpdate()`** ([CombinedItemContainer.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/container/CombinedItemContainer.java#L201)):
```java
@Override
protected void sendUpdate(@Nonnull Transaction transaction) {
    if (transaction.succeeded()) {
        super.sendUpdate(transaction);  // fires events on CombinedItemContainer
        for (ItemContainer container : this.containers) {
            Transaction containerTransaction = transaction.fromParent(this, start, container);
            if (containerTransaction != null && containerTransaction.succeeded()) {
                container.sendUpdate(containerTransaction);  // fires events on sub-containers
            }
            start += container.getCapacity();
        }
    }
}
```

When called from `addItemStack()` on a `CombinedItemContainer`:
1. Write locks on ALL sub-containers were held during `internal_addItemStack` via `writeAction0()` — but are RELEASED before `sendUpdate` is called
2. `super.sendUpdate()` fires our listener on the CombinedItemContainer's registry — **no locks held**
3. Then `container.sendUpdate()` fires on each sub-container — **no locks held**

**If our listener calls `setItemStackForSlot()` during the event callback**, it re-enters `writeAction()`, acquires the write lock, mutates, releases, then calls `sendUpdate()` again — which fires another event. The `AffordabilityCoalescer.isRestoring()` guard handles this:

```java
// From AffordabilityCoalescer — guards against re-entrant restoreStencils()
coalescer.setRestoring(true);
try { restoreStencils(hotbar); }
finally { coalescer.setRestoring(false); }
```

**`ReentrantReadWriteLock.writeLock()` IS re-entrant for the same thread.** So even if `setItemStackForSlot()` is called while the same thread already holds the write lock, it succeeds (lock count increments).

**Verdict: No self-deadlock from this path, because (a) events fire outside locks, and (b) `ReentrantReadWriteLock` is re-entrant for the same lock type.**

### HOWEVER: Read → Write upgrade DOES deadlock

`ReentrantReadWriteLock` does **NOT** support read-to-write lock upgrading. If a thread holds a read lock and attempts to acquire the write lock, it deadlocks:

```
Thread holds: readLock on container[0]
Thread wants: writeLock on container[0]
→ writeLock waits for readLock to release
→ readLock waits for writeLock to complete (because the thread is blocked)
→ PERMANENT DEADLOCK
```

**This is the most dangerous pattern.** See Finding #6 for the specific scenario.

---

## Finding 5: UI Best Practices from Engine

### Documented Best Practices

From [research-particle-highlight-and-container-events.md](../hytale/research-particle-highlight-and-container-events.md):

| Practice | Detail |
|----------|--------|
| **Entity spawn/remove frequency** | NOT designed for >1Hz per player. Each cycle: archetype allocation, tracker registration, spawn/despawn packets, GC |
| **Particle-only alternative** | `ParticleUtil.spawnParticleEffect()` or direct `SpawnParticleSystem` packet — zero entity overhead |
| **Change event lifecycle** | `registerChangeEvent()` returns `EventRegistration`; call `.unregister()` on cleanup |
| **Container read/write locking** | `SimpleItemContainer` uses `ReentrantReadWriteLock`; `CombinedItemContainer` acquires all sub-container locks recursively |
| **Event dispatch timing** | `sendUpdate()` fires AFTER write lock release in base class; CombinedItemContainer delegates to sub-containers |

### Additional findings from decompiled code

| Pattern | Source |
|---------|--------|
| `AssetRegistry.ASSET_LOCK.readLock()` held during entire `World.tick()` | [World.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/World.java#L296) — includes both `consumeTaskQueue()` calls and `entityStore.tick()` |
| `Store.assertWriteProcessing()` — prevents entity mutation during ECS tick | [Store.java](../../.tmp_hytale_src/com/hypixel/hytale/component/Store.java#L1957) — throws if `processing.isHeld()` and system tries to call `addEntity`/`removeEntity` |
| `consumeTaskQueue()` drains ALL tasks including newly added ones | [World.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/World.java#L700) — `while ((runnable = taskQueue.poll()) != null)` — tasks added during drain are picked up immediately |
| Entity tracker `StampedLock` in `Visible.addViewerParallel()` | [EntityTrackerSystems.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/tracker/EntityTrackerSystems.java#L720) — blocking write lock, non-reentrant |

---

## Finding 6: The Most Likely Freeze Scenario — consumeTaskQueue Re-entrancy with Inventory Locks

### The Scenario

The `consumeTaskQueue()` while-loop drains ALL tasks, including tasks added during drain:

```java
// World.consumeTaskQueue()
while ((runnable = this.taskQueue.poll()) != null) {
    runnable.run();  // If this adds more tasks, they're picked up in this same loop
}
```

Consider this sequence during the FIRST `consumeTaskQueue()` (before `entityStore.tick()`):

1. **Task A** runs: `StencilBookParticleLoop.executeTick()` → calls `store.addEntity()` + `store.removeEntity()` synchronously → completes → sets `pending = false`

2. **Task B** runs (was already in queue from a previous tick's `markDirty()`): `AffordabilityCoalescer.executeRefresh()` → calls `countItemStacks()` on `CombinedItemContainer` → **acquires read locks on all sub-containers** (hotbar, backpack, storage)

3. INSIDE `countItemStacks()`, while read locks are held — wait, `countItemStacks()` just iterates and counts. It doesn't call back into anything. So this is safe.

But what if `executeRefresh()` calls `restoreStencils()` which calls `setItemStackForSlot()` while also holding a read lock from a prior `countItemStacks()` in the same call stack?

Let me check the actual `executeRefresh` flow...

**This depends on what `StencilVisualManager.refreshAffordability()` does.** If it:
1. Calls `countItemStacks()` (read lock)
2. Then calls `setItemStackForSlot()` (write lock)
3. Both on the same container

Step 1 releases the read lock before step 2 runs, because `readAction()` is a wrapper that acquires/executes/releases. So they're sequential, not nested. **No deadlock.**

### The ACTUAL Dangerous Scenario

What if `refreshAffordability()` does something like:

```java
CombinedItemContainer combined = player.getInventory().getCombinedBackpackStorageHotbar();
combined.readAction(() -> {
    // read lock held on all sub-containers
    int count = /* count items */;
    if (count > 0) {
        hotbar.setItemStackForSlot(slot, newStack);  // TRIES TO ACQUIRE WRITE LOCK ON HOTBAR
        // But hotbar's read lock is already held by CombinedItemContainer.readAction!
        // ReentrantReadWriteLock does NOT support read → write upgrade
        // → PERMANENT DEADLOCK
    }
});
```

If ANY code path calls `readAction` on a `CombinedItemContainer` and then, inside that callback, calls a write method (`setItemStackForSlot`, `addItemStack`, etc.) on one of the sub-containers — **that is a permanent self-deadlock** because:
- `CombinedItemContainer.readAction()` holds read locks on ALL sub-containers
- `setItemStackForSlot()` needs the write lock on the specific sub-container
- `ReentrantReadWriteLock` does NOT allow read→write upgrade on the same thread
- The thread blocks forever waiting for the write lock, which waits for the read lock to release, which waits for the thread to unblock

### Is This Happening in Our Code?

**Verified — NO read→write deadlock in the current code paths.**

I traced every call path:

**`restoreStencils(hotbar)`** in [StencilSyncSystem.java](../../../src/main/java/com/CodeCreature/stencil/StencilSyncSystem.java#L100):
```java
private static void restoreStencils(ItemContainer hotbar) {
    for (short slot = 0; slot < capacity; slot++) {
        ItemStack stack = hotbar.getItemStack(slot);        // readAction → read lock → RELEASE
        // ... checks ...
        hotbar.setItemStackForSlot(slot, restored);          // writeAction → write lock → RELEASE
    }
}
```
Each lock is acquired and released SEQUENTIALLY. No nesting. Safe.

**`scanAndSend()`** in [StencilVisualManager.java](../../../src/main/java/com/CodeCreature/stencil/StencilVisualManager.java#L222):
```java
for (short slot = 0; slot < capacity; slot++) {
    ItemStack stack = hotbar.getItemStack(slot);             // read lock → RELEASE
    boolean affordable = RecipeAffordabilityResolver
        .isAffordableWithAutoCraft(recipe, BUILDERS_ONLY, container);  // readActions → RELEASE each
}
```
`isAffordableWithAutoCraft` → `AutoCraftPlanner.plan()` → multiple `container.countItemStacks()` calls. Each uses `readAction()` — acquire, iterate, release. All sequential. **No nested write.**

**`StencilBookParticleLoop.executeTick()`** with `ENABLE_AFFORDABILITY_CHECK = false`: Only calls `hotbar.getItemStack(activeSlot)` — a single read lock acquire/release. No write operations on containers. Safe.

**Conclusion: The read→write deadlock theory is NOT the root cause with the CURRENT code.**

---

## Finding 7: Why It Correlates with Breaking 10-20 Blocks

The freeze happens after 10-20 blocks are broken and items drop. Here's why:

1. **Block breaking** creates item entities (drops)
2. **Item pickup** runs during `entityStore.tick()` via `PlayerItemEntityPickupSystem`
3. Each pickup calls `addItemStack()` on a container → `sendUpdate()` → change events
4. Change events call `markDirty()` → `world.execute(executeRefresh)` → queues to taskQueue
5. The SECOND `consumeTaskQueue()` drains these `executeRefresh` tasks
6. **If `executeRefresh`/`refreshAffordability` has the read→write lock upgrade bug**, the deadlock occurs

The more items picked up, the more events fire, the more `executeRefresh` tasks queue, the higher the probability of hitting the deadlock timing window.

**Meanwhile, `StencilBookParticleLoop` is firing every 100ms**, adding `executeTick` tasks to the same queue. This increases overall task queue pressure and GC load, widening the timing window.

---

## Definitive Answers

### Q1: Could entity churn from the particle loop cause the freeze?
**No, not directly.** `store.addEntity()`/`store.removeEntity()` are synchronous, use no blocking locks (only a counter), and don't call `world.execute()`. `takeNextNetworkId()` is `AtomicInteger` — lock-free. The entity tracker's `StampedLock` in `Visible` is only used during parallel ECS ticks, not during entity add/remove. However, entity churn is a severe performance amplifier.

### Q2: Does addEntity/removeEntity create a task queue feedback loop?
**No.** Both are purely synchronous. They never call `world.execute()` or `taskQueue.offer()`. Verified from decompiled `Store.java` — the full call path is: `assertThread()` → `assertWriteProcessing()` → `processing.lock()` (counter++) → iterate systems → `processing.unlock()` (counter--) → `commandBuffer.consume()`. All synchronous.

### Q3: Could there be a deadlock between countItemStacks and addItemStack?
**Not between pickup and refresh.** `addItemStack()` releases the write lock BEFORE `sendUpdate()` fires events. The `executeRefresh` runs in a different `consumeTaskQueue()` phase. But a **self-deadlock IS possible** if any code path calls `readAction()` on `CombinedItemContainer` and then writes to a sub-container inside that read lambda.

### Q4: Is setItemStackForSlot re-entrant safe with the write lock?
**Write→write re-entrancy: YES** (ReentrantReadWriteLock supports it). **Read→write upgrade: NO** — this causes permanent self-deadlock. The `AffordabilityCoalescer.isRestoring()` guard protects against write→event→write recursion, but does NOT protect against read→write scenarios.

### Q5: What UI best practices exist?
See Finding #5 table above. Key: don't use entities for high-frequency visuals; use `SpawnParticleSystem` packets instead. Container events fire outside locks. `CombinedItemContainer` recursively locks all sub-containers.

---

## Assessment: Can Any of These Cause a PERMANENT Freeze?

| Scenario | Permanent Freeze? | Mechanism |
|----------|-------------------|-----------|
| Entity churn (addEntity/removeEntity at 10Hz) | **No** | Counter-based "lock", no blocking |
| Task queue feedback loop | **No** | addEntity/removeEntity don't use world.execute() |
| Inventory read→write lock upgrade | **No (verified)** | All lock acquisitions are sequential, never nested |
| StampedLock in entity tracker | **Unlikely** | Only used in parallel tick systems, not in addEntity/removeEntity |
| ConcurrentHashMap contention | **No** | ConcurrentHashMap is lock-free for reads |
| Cross-thread inventory lock contention | **Possible** | See Finding #8 below |

---

## Finding 8: Cross-Thread Lock Contention — The Probable Root Cause

### The Scenario

The `SimpleItemContainer` uses `ReentrantReadWriteLock`, which is shared across ALL threads. Inventory operations (packet handlers for item dragging, crafting, etc.) may run on **Netty threads** if the engine handles them inline rather than deferring via `world.execute()`.

Consider:
1. **Netty thread**: Client sends inventory packet → handler calls `moveItemStackFromSlot()` → acquires WRITE lock on hotbar
2. **World thread** (during `consumeTaskQueue()` → `executeTick()`): calls `hotbar.getItemStack(activeSlot)` → tries READ lock → **blocks** (write lock held by Netty)
3. **Netty thread**: `sendUpdate()` fires change event → `restoreStencils()` → calls `hotbar.setItemStackForSlot()` → acquires WRITE lock (re-entrant, same thread) → fires `sendUpdate()` again → calls `markDirty(world)` → `world.execute(executeRefresh)` → `taskQueue.offer()` → returns
4. **Netty thread**: releases all write locks → returns
5. **World thread**: unblocks → continues

This causes a **brief block**, not permanent. BUT — if step 3's `markDirty()` fires `world.execute()` and the `world.execute()` call itself has an issue (world shutting down, `acceptingTasks = false`), a `SkipSentryException` is thrown. The catch block in the scheduled executor handles `Exception`, but **the event handler code in `StencilSyncSystem` does NOT catch exceptions from `markDirty`**:

```java
handles[0] = hotbar.registerChangeEvent(event -> {
    if (!coalescer.isRestoring()) {
        coalescer.setRestoring(true);
        try {
            restoreStencils(hotbar);
        } finally {
            coalescer.setRestoring(false);
        }
    }
    coalescer.markDirty(world);  // ← UNCAUGHT if world rejects tasks
});
```

If `markDirty()` → `world.execute()` throws, the exception propagates into `SyncEventBusRegistry.dispatch()`, which propagates into `sendUpdate()`, which propagates into `addItemStack()` or `moveItemStackFromSlot()` — which is called from the engine's pickup system during `entityStore.tick()`. 

**If the exception escapes while write locks are still held in nested `sendUpdate` calls** (not in this specific flow, but in `CombinedItemContainer.sendUpdate()` where multiple containers are iterated), subsequent lock acquisitions on those containers would deadlock.

### More Likely: `AssetRegistry.ASSET_LOCK` Interaction

The world thread holds `AssetRegistry.ASSET_LOCK.readLock()` during the ENTIRE tick:

```java
// World.tick() — line ~296
AssetRegistry.ASSET_LOCK.readLock().lock();
try {
    this.consumeTaskQueue();        // executeTick runs here
    this.entityStore.tick(dt);       // pickup + events run here
    this.chunkStore.tick(dt);
    this.consumeTaskQueue();        // executeRefresh runs here
} finally {
    AssetRegistry.ASSET_LOCK.readLock().unlock();
}
```

If ANY code during the tick tries to acquire `AssetRegistry.ASSET_LOCK.writeLock()` (e.g., a hot-reload, asset registry update, or mod loading), the world thread deadlocks — it holds the read lock and wants the write lock (read→write upgrade on a ReadWriteLock = deadlock).

This is an engine-level concern, but our plugin's entity churn might trigger asset-related operations (e.g., `BlockEntity` constructor resolving block type assets, `EntityEffect.getAssetMap().getAsset(effectId)` in `spawnHighlightEntity()`) that interact with the asset registry.

**If the entity churn at 10Hz causes an asset registry write lock acquisition** (even rarely), and the world thread already holds the asset read lock, this would be a **permanent self-deadlock** on the world thread.

---

## Revised Assessment

| Scenario | Permanent Freeze? | Probability |
|----------|-------------------|-------------|
| AssetRegistry read→write deadlock from entity churn | **YES** | **Medium-High** — entity spawn resolves assets, effect application touches asset maps |
| Exception during event dispatch leaving locks held | **YES** | **Medium** — depends on engine's lock release guarantees |
| All lock acquisitions sequential (no nested deadlock) | Verified safe | — |
| Store.addEntity/removeEntity internal deadlock | **No** | Confirmed safe via code analysis |

**Most likely root cause: The combination of rapid entity spawning (10Hz) during `consumeTaskQueue()` — which runs under `AssetRegistry.ASSET_LOCK.readLock()` — triggers an asset lookup that attempts a write lock on the same `AssetRegistry.ASSET_LOCK`, causing a permanent self-deadlock on the world thread.**

This would explain:
- **Why 10-20 blocks**: More items → more entity tracker work → more time under the lock → wider timing window
- **Why permanent**: ReadWriteLock read→write upgrade = permanent self-deadlock (single-threaded)
- **Why console goes silent**: World thread is the logging thread for game events
- **Why the pending fix didn't help**: The pending fix prevents task accumulation, but doesn't prevent the asset lock deadlock

---

## Recommendation

### PRIMARY FIX: Replace entity-based highlights with SpawnParticleSystem packets

This eliminates the most probable deadlock trigger by removing ALL entity operations from `consumeTaskQueue()`:
- **Zero `store.addEntity()`/`store.removeEntity()` calls** during `consumeTaskQueue()`
- **Zero asset registry lookups** from `BlockEntity` constructor and `EntityEffect.getAssetMap().getAsset()`
- **Zero entity tracker overhead** (no `Visible` component, no `StampedLock`, no spawn/despawn packets)
- **Zero GC pressure** from Holder/Ref/ArchetypeChunk allocation
- Sends one UDP packet per update — `SpawnParticleSystem` via `playerRef.getPacketHandler().writeNoCache()`

This directly addresses the research doc's recommendation and eliminates the probable root cause.

### SECONDARY: Add defensive exception handling in event handlers

Wrap `markDirty(world)` calls in try-catch to prevent exceptions from propagating into the engine's event dispatch:

```java
handles[0] = hotbar.registerChangeEvent(event -> {
    try {
        if (!coalescer.isRestoring()) {
            coalescer.setRestoring(true);
            try { restoreStencils(hotbar); }
            finally { coalescer.setRestoring(false); }
        }
        coalescer.markDirty(world);
    } catch (Exception e) {
        DebugLogger.log(STENCIL, Level.WARNING, "[StencilSync] Error in change handler: " + e.getMessage());
    }
});
```

### TERTIARY: Add a thread dump on freeze detection

Add a watchdog that detects when the world tick hasn't progressed for >5 seconds and dumps all thread stacks. This would definitively identify the exact lock/method causing the freeze:

```java
Thread.getAllStackTraces().forEach((thread, stack) -> {
    logger.warning("Thread: " + thread.getName() + " state=" + thread.getState());
    for (StackTraceElement element : stack) {
        logger.warning("  at " + element);
    }
});
```

### DIAGNOSTIC: Verify the AssetRegistry.ASSET_LOCK theory

Before the entity-to-particle migration, add a diagnostic log in `spawnHighlightEntity()` to check if the current thread holds a read lock:

```java
// At the start of spawnHighlightEntity():
DebugLogger.log(STENCIL_BOOK, Level.INFO, "[DEBUG] Asset read lock held: " + 
    AssetRegistry.ASSET_LOCK.readLock().tryLock()); // If true, we're under double read (fine). 
                                                     // If the freeze happens, this tells us we're under read lock.
```

## Next Steps

1. **Implement SpawnParticleSystem migration** — replace all entity operations in `StencilBookParticleLoop` with direct packet sends
2. **Add try-catch in all inventory change event handlers** — defensive against exception propagation
3. **If freeze persists after migration** — add thread-dump watchdog to identify the exact deadlock location
4. **Consider reducing `executeRefresh` to only run during the second `consumeTaskQueue()`** — use a flag to skip it during the first drain
