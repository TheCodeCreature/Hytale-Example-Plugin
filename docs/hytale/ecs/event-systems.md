---
topic: "Event Systems"
category: "ECS"
updated: 2026-04-16
sources: ["codebase analysis", "hytalemodding.dev"]
---

# ECS Event Systems

## Summary

`EntityEventSystem` is the ECS mechanism for responding to game events like block placement, damage, crafting, and item drops. Event systems are registered by plugins and automatically invoked when matching events fire.

## EntityEventSystem

```java
public class MyEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public MyEventSystem() {
        super(PlaceBlockEvent.class);  // Declare which event to handle
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> archetypeChunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       PlaceBlockEvent event) {
        // React to the event
        // 'index' is the entity index in the archetype chunk
        // 'event' contains event-specific data
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Return Archetype.empty() to match all entities
        return Archetype.empty();
    }
}
```

## Cancellable Events

Many ECS events extend `CancellableEcsEvent` and can be cancelled by the handler:

- `BreakBlockEvent`
- `PlaceBlockEvent`
- `DamageBlockEvent`
- `DropItemEvent`
- `CraftRecipeEvent` (Pre/Post)
- `ChangeGameModeEvent`
- `InteractivelyPickupItemEvent`
- `SwitchActiveSlotEvent`
- `Damage`

```java
if (shouldPrevent) {
    event.cancel();  // Prevents the action from completing
}
```

## Event Execution Order

For `PlaceBlockEvent` specifically:

1. Engine creates the event (item not yet consumed from inventory)
2. Plugin event handlers run — may cancel or pre-consume items
3. If not cancelled, engine consumes 1 item from active slot
4. Engine places the block in the world

## Full ECS Event List

| Event | Description |
|-------|-------------|
| `BreakBlockEvent` | A block is being broken |
| `PlaceBlockEvent` | A block is being placed |
| `DamageBlockEvent` | A block is taking damage |
| `Damage` | An entity is taking damage |
| `DropItemEvent` | An item is being dropped (Drop / PlayerRequest) |
| `CraftRecipeEvent` | A recipe is being crafted (Pre / Post) |
| `UseBlockEvent` | A block is being used/interacted with (Pre / Post) |
| `ChangeGameModeEvent` | Player's game mode is changing |
| `ChunkSaveEvent` | A chunk is being saved |
| `ChunkUnloadEvent` | A chunk is being unloaded |
| `InteractivelyPickupItemEvent` | Player picks up an item |
| `SwitchActiveSlotEvent` | Player switches hotbar slot |
| `PrefabPasteEvent` | A prefab is being pasted |
| `DiscoverInstanceEvent` | Player discovers a dungeon instance |
| `DiscoverZoneEvent` | Player discovers a zone |
| `MoonPhaseChangeEvent` | Moon phase changes |

## Registration

Event systems are registered in the plugin's `setup()` method:

```java
this.getEntityStoreRegistry().registerSystem(new MyEventSystem());
```

## See Also

- [ECS Overview](./overview.md)
- [Plugin Events (IEvent)](../plugins/events.md)
- [Ticking Systems](./ticking-systems.md)
