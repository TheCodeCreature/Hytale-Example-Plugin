---
topic: "Threading Model"
category: "ECS"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Threading Model

## Summary

Hytale uses multiple threads with strict rules about which operations are safe on which threads. Understanding the threading model is critical for writing correct server plugins.

## Thread Types

### Main / Server Thread

- Handles server startup, shutdown
- Processes global events (`IEvent`)
- Manages player connections

### World Thread

- Each world has its own execution context
- ECS systems (`EntityTickingSystem`, `EntityEventSystem`) run on this thread
- Block operations (set, break, physics cascade) run on this thread
- **World state mutations must happen on this thread**

### Scheduled Executor

`HytaleServer.SCHEDULED_EXECUTOR` is a `ScheduledExecutorService` for timer-based tasks:

```java
HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
    // This runs on the executor thread, NOT the world thread
    // Must defer world mutations:
    world.execute(() -> {
        // NOW safe to access world state
    });
}, 100, 100, TimeUnit.MILLISECONDS);
```

### Network Threads

Handle packet I/O. Never mutate game state directly from network threads.

## Thread Safety Rules

| Operation | Thread Requirement | How to Ensure |
|-----------|-------------------|---------------|
| Read/write blocks | World thread | `world.execute(...)` |
| Access EntityStore | World thread | From within system handlers |
| Send packets | Any thread | `playerRef.getPacketHandler()` is thread-safe for sending |
| Read asset maps | Any thread (after loading) | Assets are immutable after `LoadAssetEvent` |
| Modify static maps | Any thread with sync | Use `ConcurrentHashMap` |
| Schedule timers | Any thread | `HytaleServer.SCHEDULED_EXECUTOR` |

## Deferred World Execution

The `world.execute()` pattern:

```java
// From a scheduled task or event handler on a different thread:
world.execute(() -> {
    Ref<EntityStore> ref = playerRef.getReference();
    if (ref == null || !ref.isValid()) return;
    
    // Safe to read/write world state
    Store<EntityStore> store = ref.getStore();
    Transform look = TargetUtil.getLook(ref, store);
    // ... modify blocks, entities, etc.
});
```

## Common Patterns

### Periodic World Updates from Timer

```java
ScheduledFuture<?> task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
    try {
        world.execute(() -> {
            // World-safe operations
        });
    } catch (Exception e) {
        // Handle errors — don't let exceptions kill the scheduler
    }
}, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

// Cancel when done:
task.cancel(false);
```

### Per-Player State Tracking

Use `ConcurrentHashMap` for state accessed from multiple threads:

```java
private static final Map<UUID, MyState> INSTANCES = new ConcurrentHashMap<>();

// Safe to put/get from any thread
INSTANCES.put(playerRef.getUuid(), new MyState());
MyState state = INSTANCES.get(playerId);
INSTANCES.remove(playerId);
```

## See Also

- [Server Architecture](../engine/server-architecture.md)
- [ECS Overview](./overview.md)
