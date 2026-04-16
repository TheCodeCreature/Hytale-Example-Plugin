---
topic: "Entity Store"
category: "ECS"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Entity Store

## Summary

`EntityStore` is the per-world container for all entities and their components. Each `World` has exactly one `EntityStore` that manages entity lifecycle, component storage, and system execution.

## Key Types

### `EntityStore`

The concrete external data for a `Store`. Obtained via:

```java
Store<EntityStore> store = ref.getStore();
EntityStore entityStore = store.getExternalData();
World world = entityStore.getWorld();
```

### `Store<EntityStore>`

The generic store interface providing component access:

```java
// Get a component from an entity reference
PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
```

### `Ref<EntityStore>`

A reference to a specific entity within the store. References can become invalid when entities are destroyed:

```java
Ref<EntityStore> ref = playerRef.getReference();
if (ref != null && ref.isValid()) {
    // Safe to use
}
```

### `Holder<T>`

A wrapper type used for holding references to assets and other registered objects.

## Accessing Components

From a `Ref`:
```java
Ref<EntityStore> ref = event.getPlayerRef();
Store<EntityStore> store = ref.getStore();
PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
```

From an `ArchetypeChunk` (inside a system):
```java
@Override
public void handle(int index, ArchetypeChunk<EntityStore> chunk, ...) {
    Player player = chunk.getComponent(index, Player.getComponentType());
}
```

## Entity Lifecycle

Entities are created and destroyed through `CommandBuffer`:

```java
// Inside a system's tick/handle method:
commandBuffer.createEntity(archetype, componentData);
commandBuffer.destroyEntity(ref);
```

All command buffer operations are deferred and applied at the end of the current tick phase.

## See Also

- [ECS Overview](./overview.md)
- [Queries & Archetypes](./queries-and-archetypes.md)
