---
topic: "Ticking Systems"
category: "ECS"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# ECS Ticking Systems

## Summary

`EntityTickingSystem` runs every game tick for all entities matching its query. This is used for continuous per-entity logic like custom movement, gravity modification, or periodic status effects.

## EntityTickingSystem

```java
public class MyTickingSystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, Velocity> velocityType;
    private final Query<EntityStore> query;

    public MyTickingSystem() {
        this.playerRefType = PlayerRef.getComponentType();
        this.velocityType = Velocity.getComponentType();
        this.query = Query.and(playerRefType, velocityType);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void tick(float dt,
                     int index,
                     ArchetypeChunk<EntityStore> archetypeChunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef playerRef = archetypeChunk.getComponent(index, playerRefType);
        Velocity velocity = archetypeChunk.getComponent(index, velocityType);
        
        if (playerRef == null || velocity == null) return;
        
        // Per-tick logic here
        // 'dt' is the delta time since last tick
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Use the built-in heuristic for parallel execution
        return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
    }
}
```

## Key Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `dt` | `float` | Delta time in seconds since last tick |
| `index` | `int` | Entity index within the archetype chunk |
| `archetypeChunk` | `ArchetypeChunk<EntityStore>` | Contains component arrays for this group of entities |
| `store` | `Store<EntityStore>` | The entity store for component access |
| `commandBuffer` | `CommandBuffer<EntityStore>` | For deferred entity operations |

## Parallel Execution

Ticking systems can opt into parallel execution for better performance across many entities. The `isParallel()` method controls this:

```java
@Override
public boolean isParallel(int archetypeChunkSize, int taskCount) {
    return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
}
```

**Warning**: If your tick logic accesses shared mutable state (e.g., static maps), parallel execution can cause race conditions. Either:
- Return `false` from `isParallel()`, or
- Use `ConcurrentHashMap` and thread-safe data structures

## MovementManager Integration

For modifying player movement, access `MovementManager`:

```java
MovementManager movementManager = archetypeChunk.getComponent(index, 
    MovementManager.getComponentType());

// Read default movement settings
MovementSettings defaults = movementManager.getDefaultSettings();

// Modify active settings
MovementSettings active = movementManager.getSettings();
active.jumpForce = defaults.jumpForce * 0.165f;  // Moon gravity

// Push changes to client
movementManager.update(playerRef.getPacketHandler());
```

## See Also

- [ECS Overview](./overview.md)
- [Event Systems](./event-systems.md)
- [Threading Model](./threading.md)
