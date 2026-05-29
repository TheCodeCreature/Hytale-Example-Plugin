---
topic: "World Thread Task Queue & Entity Lifecycle Safety"
category: "ECS / Threading"
updated: 2026-05-29
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/World.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/storage/EntityStore.java"
  - ".tmp_hytale_src/com/hypixel/hytale/component/Store.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/tracker/EntityTrackerSystems.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/tracker/NetworkId.java"
  - ".tmp_hytale_src/com/hypixel/hytale/component/spatial/SpatialSystem.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/HytaleServer.java"
---

# World Thread Task Queue & Entity Lifecycle Safety

## 1. Task Queue — Unbounded, No Backpressure

### Queue Type & Declaration

```java
// World.java:142
private final Deque<Runnable> taskQueue = new LinkedBlockingDeque<>();
```

The queue is a `java.util.concurrent.LinkedBlockingDeque` constructed with the **default (no-arg) constructor**, which means **unbounded capacity** (`Integer.MAX_VALUE`).

### Enqueue Path

```java
// World.java:691-696
public void execute(@Nonnull Runnable command) {
    if (!this.acceptingTasks.get()) {
        throw new SkipSentryException(
            new IllegalThreadStateException("World thread is not accepting tasks: " + this.name));
    } else {
        this.taskQueue.offer(command);  // never returns false on unbounded deque
    }
}
```

- The only guard is `acceptingTasks` — an `AtomicBoolean` set to `false` during `onShutdown()` (World.java:369).
- There is **no size check**, **no capacity limit**, and **no backpressure** mechanism.
- `offer()` on an unbounded `LinkedBlockingDeque` always succeeds.

### Drain Path

```java
// World.java:700-715
public void consumeTaskQueue() {
    this.debugAssertInTickingThread();
    int tickStepNanos = this.getTickStepNanos();

    Runnable runnable;
    while ((runnable = this.taskQueue.poll()) != null) {
        try {
            long before = System.nanoTime();
            runnable.run();
            long after = System.nanoTime();
            long diff = after - before;
            if (diff > tickStepNanos) {
                this.logger.at(Level.WARNING).log(
                    "Task took %s ns: %s", FormatUtil.nanosToString(diff), runnable);
            }
        } catch (Exception var9) {
            this.logger.at(Level.SEVERE).withCause(var9).log("Failed to run task!");
        }
    }
}
```

Key observations:
- The queue is drained **completely** each time — `while (poll() != null)`.
- Individual slow tasks generate a WARNING log if they exceed `tickStepNanos`, but execution continues regardless.
- `consumeTaskQueue()` is called **multiple times per tick** — at minimum twice during `tick(float dt)` (World.java:297, 310), plus during shutdown, chunk loading waits, and other blocking operations.

### When Is It Called During a Tick?

```java
// World.java:290-311 (tick method, simplified)
protected void tick(float dt) {
    AssetRegistry.ASSET_LOCK.readLock().lock();
    try {
        this.consumeTaskQueue();          // ← DRAIN 1: before entity tick
        if (!this.isPaused) {
            this.entityStore.getStore().tick(dt);
        }
        // chunk store tick...
        this.consumeTaskQueue();          // ← DRAIN 2: after chunk tick
    } finally {
        AssetRegistry.ASSET_LOCK.readLock().unlock();
    }
}
```

Additionally, `consumeTaskQueue()` is called from within `ChunkStore` during chunk loading operations (ChunkStore.java:196, 216).

### Answer: What Happens If the World Can't Keep Up?

**Yes, the queue grows unbounded.** If a plugin calls `HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(100ms)` and each callback does `world.execute(lambda)`:

1. **`SCHEDULED_EXECUTOR` is a single-thread executor** (HytaleServer.java:71):
   ```java
   public static final ScheduledExecutorService SCHEDULED_EXECUTOR = 
       Executors.newSingleThreadScheduledExecutor(ThreadUtil.daemon("Scheduler"));
   ```

2. The scheduler thread enqueues lambdas into the world's `taskQueue` every 100ms.

3. The world thread drains the queue during each tick. If the world tick rate is 20 TPS (50ms), and each lambda takes <1ms, this is fine — ~2 lambdas per tick.

4. **But if the world thread slows down** (heavy chunk loading, complex entity ticks), the drain rate drops below the enqueue rate. Since there's no capacity limit and no backpressure:
   - The `LinkedBlockingDeque` grows without bound
   - Memory usage increases linearly with time
   - Eventually: `OutOfMemoryError`

5. There is **no mechanism** to:
   - Reject tasks when the queue is too large
   - Notify the producer that the consumer is behind
   - Drop old tasks
   - Log queue depth warnings

### Risk Mitigation for Plugins

Plugins should implement their own backpressure. Options:
- Use `scheduleWithFixedDelay` instead of `scheduleAtFixedRate` (prevents pile-up if world.execute blocks)
- Track a pending flag: skip enqueue if the previous task hasn't been consumed yet
- Use an `AtomicBoolean` or bounded queue on the plugin side

---

## 2. Entity Add/Remove Lifecycle — Internal State Cleanup

### 2a. Network ID Lifecycle — Monotonic Counter, IDs Are NOT Recycled

```java
// EntityStore.java:44
private final AtomicInteger networkIdCounter = new AtomicInteger(1);

// EntityStore.java:79
public int takeNextNetworkId() {
    return this.networkIdCounter.getAndIncrement();
}
```

**Network IDs are never recycled.** The counter is a monotonically increasing `AtomicInteger`. At 10 entities/second/player × 100 players × 3600 seconds/hour = 3.6M IDs/hour. With `int` max at ~2.1 billion, this gives ~583 hours before overflow. In practice this is likely fine for a single world session, but for very long-running servers it's a theoretical risk.

### 2b. NetworkId Map Cleanup — Clean ✓

The `EntityStore.NetworkIdSystem` (a `RefSystem`) correctly cleans up:

```java
// EntityStore.java:117-127 — onEntityRemove
public void onEntityRemove(...) {
    EntityStore entityStore = store.getExternalData();
    NetworkId networkIdComponent = commandBuffer.getComponent(ref, NetworkId.getComponentType());
    entityStore.networkIdToRef.remove(networkIdComponent.getId(), ref);
}
```

The `networkIdToRef` (`Int2ObjectOpenHashMap`) entry is removed on entity removal. **No leak.**

### 2c. UUID Map Cleanup — Clean ✓

```java
// EntityStore.java:157-162 — UUIDSystem.onEntityRemove
public void onEntityRemove(...) {
    UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
    store.getExternalData().entitiesByUuid.remove(uuidComponent.getUuid(), ref);
}
```

The `entitiesByUuid` (`ConcurrentHashMap`) entry is removed. **No leak.**

### 2d. Entity Tracker (Visibility) Cleanup — Clean ✓

When an entity is removed, the `RemoveVisibleComponent` (`HolderSystem`) fires:

```java
// EntityTrackerSystems.java — RemoveVisibleComponent.onEntityRemoved
public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, ...) {
    holder.removeComponent(this.componentType);
}
```

The `Visible` component is stripped from the holder.

Additionally, in `SendPackets.tick()`, the system detects removed entities via `ref.isValid()`:

```java
// EntityTrackerSystems.java — SendPackets.tick (simplified)
ObjectIterator<...> iterator = viewer.sent.object2IntEntrySet().iterator();
while (iterator.hasNext()) {
    Entry entry = iterator.next();
    Ref ref = entry.getKey();
    if (!ref.isValid() || !viewer.visible.contains(ref)) {
        removedEntities.add(entry.getIntValue());
        iterator.remove();
    }
}
```

Invalid refs (removed entities) are automatically detected and their network IDs are sent to clients as `EntityUpdates.removed`. **No leak** — the tracker self-heals within one tick.

### 2e. Spatial Index — Fully Rebuilt Every Tick, No Leak Possible ✓

```java
// SpatialSystem.java:22-36
public void tick(float dt, int systemIndex, @Nonnull Store<ECS_TYPE> store) {
    SpatialData<Ref<ECS_TYPE>> spatialData = spatialResource.getSpatialData();
    spatialData.clear();                    // ← wipes everything
    store.forEachChunk(systemIndex, (archetypeChunk, commandBuffer) -> {
        // re-inserts only living entities
    });
    spatialResource.getSpatialStructure().rebuild(spatialData);  // ← full rebuild
}
```

The spatial index is **completely cleared and rebuilt from scratch every tick**. Removed entities simply won't be iterated. **No leak possible.**

### 2f. Store Internal Arrays — Clean ✓

```java
// Store.java:662-720 (removeEntity, simplified)
// swap-remove: move last entity into the vacated slot
this.refs[lastIndex] = null;
this.entityToArchetypeChunk[lastIndex] = Integer.MIN_VALUE;
this.entityChunkIndex[lastIndex] = Integer.MIN_VALUE;
this.entitiesSize = lastIndex;
archetypeChunk.removeEntity(chunkEntityRef, holder);
if (archetypeChunk.size() == 0) {
    this.removeArchetypeChunk(archetypeIndex);
}
ref.invalidate(proxyReason);
```

The Store uses **swap-remove** on its internal arrays (O(1)), nulls out the vacated last slot, and empty archetype chunks are cleaned up. The ref is invalidated so any stale references will fail `isValid()` checks. **No leak.**

---

## 3. Summary Table

| Concern | Status | Detail |
|---------|--------|--------|
| `taskQueue` capacity | **UNBOUNDED** | `LinkedBlockingDeque()` — no size limit |
| Backpressure mechanism | **NONE** | No rejection, no notification, no depth monitoring |
| Task slow warning | Exists | Logs WARNING if a single task exceeds `tickStepNanos` |
| Queue depth warning | **NONE** | No logging of queue size |
| NetworkId recycling | **NONE** | Monotonic `AtomicInteger`, wraps at `Integer.MAX_VALUE` |
| `networkIdToRef` cleanup | **Clean** | `NetworkIdSystem.onEntityRemove()` removes entry |
| `entitiesByUuid` cleanup | **Clean** | `UUIDSystem.onEntityRemove()` removes entry |
| Entity tracker (visibility) | **Clean** | `SendPackets` detects invalid refs, sends removal to clients |
| Spatial index | **Clean** | Full clear+rebuild every tick |
| Store internal arrays | **Clean** | Swap-remove, null out, archetype chunk compaction |
| Ref invalidation | **Clean** | `ref.invalidate()` called, stale refs detected everywhere |

## 4. Recommendations for Plugin Authors

1. **Never use `scheduleAtFixedRate` with `world.execute()`** without plugin-side backpressure. Prefer `scheduleWithFixedDelay` or guard with an `AtomicBoolean`:
   ```java
   AtomicBoolean pending = new AtomicBoolean(false);
   SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
       if (pending.compareAndSet(false, true)) {
           world.execute(() -> {
               try { /* work */ } 
               finally { pending.set(false); }
           });
       }
   }, 0, 100, TimeUnit.MILLISECONDS);
   ```

2. **Entity add/remove at ~10/sec/player is safe** — all internal state is properly cleaned up. The main costs are:
   - `NetworkId` counter increment (negligible)
   - `RefSystem` callbacks during remove (O(number of registered RefSystems))
   - `HolderSystem` callbacks after remove (O(number of registered HolderSystems))
   - Spatial index rebuild (happens anyway every tick regardless)

3. **Monitor for the long-term `networkIdCounter` overflow** if your server runs for >500 hours continuously with high entity churn.
