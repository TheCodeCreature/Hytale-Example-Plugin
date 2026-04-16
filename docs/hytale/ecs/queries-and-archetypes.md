---
topic: "Queries & Archetypes"
category: "ECS"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Queries & Archetypes

## Summary

Queries and Archetypes define which entities a system processes. An Archetype is a specific combination of component types, while a Query filters entities by their archetype composition.

## Query

A `Query<EntityStore>` filters entities for system processing.

### Query.and()

Match entities that have **all** specified components:

```java
Query<EntityStore> query = Query.and(
    PlayerRef.getComponentType(),
    MovementManager.getComponentType(),
    Velocity.getComponentType()
);
```

### Archetype.empty()

Match **all** entities (no component filter). Used by event systems where the event itself already targets the correct entity:

```java
@Override
public Query<EntityStore> getQuery() {
    return Archetype.empty();
}
```

## ArchetypeChunk

`ArchetypeChunk<EntityStore>` is a contiguous block of entities that share the same component composition. Systems iterate over entities within chunks:

- Entities in the same chunk have identical component types
- Component data is stored in arrays within the chunk for cache efficiency
- Access components by entity index within the chunk:

```java
PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
```

## ComponentType

`ComponentType<EntityStore, T>` is a type-safe identifier for a component class:

```java
// Get the component type (static method on the component class)
ComponentType<EntityStore, PlayerRef> playerRefType = PlayerRef.getComponentType();
ComponentType<EntityStore, Player> playerType = Player.getComponentType();
ComponentType<EntityStore, Velocity> velocityType = Velocity.getComponentType();
```

Store component types as fields in your system class to avoid repeated lookups:

```java
public class MySystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    
    public MySystem() {
        this.playerRefType = PlayerRef.getComponentType();
    }
}
```

## See Also

- [ECS Overview](./overview.md)
- [Entity Store](./entity-store.md)
