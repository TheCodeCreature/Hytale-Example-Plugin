---
topic: "ECS Overview"
category: "ECS"
updated: 2026-04-16
sources: ["codebase analysis", "hytalemodding.dev"]
---

# Entity Component System (ECS) Overview

## Summary

Hytale uses an archetype-based Entity Component System as its core game object architecture. All dynamic objects — players, NPCs, projectiles, dropped items — are entities composed of components, processed by systems.

## Core Concepts

### Entities

Entities are lightweight identifiers — not objects themselves. An entity is an index into an `ArchetypeChunk`, which holds the actual component data. Entities are created and destroyed through `CommandBuffer` operations.

### Components

Components are data classes attached to entities. Each component has a `ComponentType` that uniquely identifies it. Examples from the Hytale API:

| Component | Purpose |
|-----------|---------|
| `PlayerRef` | Links an entity to a connected player |
| `Player` | Player-specific data (game mode, inventory) |
| `Velocity` | Physics velocity vector |
| `MovementManager` | Movement settings and state |
| `Transform` | Position and rotation in world space |

Components are accessed via their `ComponentType`:

```java
PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
```

### Systems

Systems contain the logic that processes entities. Two main types:

1. **`EntityEventSystem<S, E>`** — responds to specific ECS events (e.g., block placed, damage dealt)
2. **`EntityTickingSystem<S>`** — runs every tick for all matching entities

### Queries

A `Query<EntityStore>` defines which entities a system processes. Queries filter entities by their component composition:

```java
// Match entities that have PlayerRef, MovementManager, AND Velocity
Query<EntityStore> query = Query.and(
    PlayerRef.getComponentType(),
    MovementManager.getComponentType(),
    Velocity.getComponentType()
);
```

Use `Archetype.empty()` to match all entities (for event systems that only fire on relevant entities anyway).

## Architecture Diagram

```
┌─────────────────────────────────────────────────┐
│                  EntityStore                     │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │          Archetype Registry                │  │
│  │                                            │  │
│  │  Archetype A: [PlayerRef, Player, Velocity]│  │
│  │    ┌─────────────────────────────────┐     │  │
│  │    │ ArchetypeChunk (64 entities)    │     │  │
│  │    │ PlayerRef[] | Player[] | Vel[]  │     │  │
│  │    └─────────────────────────────────┘     │  │
│  │                                            │  │
│  │  Archetype B: [NPC, Transform, Velocity]   │  │
│  │    ┌─────────────────────────────────┐     │  │
│  │    │ ArchetypeChunk (64 entities)    │     │  │
│  │    │ NPC[] | Transform[] | Vel[]     │     │  │
│  │    └─────────────────────────────────┘     │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │          System Pipeline                   │  │
│  │                                            │  │
│  │  EntityTickingSystem → tick() per entity   │  │
│  │  EntityEventSystem  → handle() on event    │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │          CommandBuffer                     │  │
│  │  Deferred entity creation/destruction      │  │
│  │  Applied at end of tick                    │  │
│  └────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

## System Registration

Plugins register systems through the `EntityStoreRegistry`:

```java
@Override
protected void setup() {
    this.getEntityStoreRegistry().registerSystem(new MyCustomSystem());
}
```

Systems are instantiated once and process all matching entities in the world's entity store.

## Key Types

| Type | Package | Purpose |
|------|---------|---------|
| `EntityStore` | `server.core.universe.world.storage` | Per-world entity storage |
| `Store<EntityStore>` | `component` | Generic store accessor |
| `Ref<EntityStore>` | `component` | Reference to a specific entity |
| `ArchetypeChunk<EntityStore>` | `component` | Chunk of entities with same component layout |
| `CommandBuffer<EntityStore>` | `component` | Deferred mutation commands |
| `Query<EntityStore>` | `component.query` | Entity filter for systems |
| `ComponentType<EntityStore, T>` | `component` | Type-safe component identifier |
| `Archetype` | `component` | Defines a set of component types |
| `EntityEventSystem<S, E>` | `component.system` | Event-driven system |
| `EntityTickingSystem<S>` | `component.system.tick` | Per-tick system |

## Gotchas

- **Thread safety**: Systems run on the entity store's thread. Don't access world state from other threads without `world.execute()`.
- **CommandBuffer**: Entity creation/destruction is deferred via the command buffer. Changes are applied at the end of the current tick phase.
- **Component access**: Always null-check component access — entities may not have the expected components.
- **Parallel ticking**: `EntityTickingSystem` supports parallel execution via `isParallel()`. Use `EntityTickingSystem.maybeUseParallel()` for the default heuristic.

## See Also

- [Entity Store](./entity-store.md)
- [Event Systems](./event-systems.md)
- [Ticking Systems](./ticking-systems.md)
- [Queries & Archetypes](./queries-and-archetypes.md)
- [Threading Model](./threading.md)
