---
topic: "Plugin Events"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis", "hytalemodding.dev"]
---

# Plugin Events

## Summary

Hytale has two event systems: global `IEvent`s dispatched by the server, and `EcsEvent`s dispatched within the Entity Component System. Plugins can listen to both.

## Event Types

### IEvent (Global Events)

Global events are dispatched on the server's main thread and registered via `getEventRegistry()`:

```java
this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, MyPlugin::onPlayerReady);
this.getEventRegistry().register(LoadAssetEvent.class, MyPlugin::onAssetsLoaded);
```

#### Key Global Events

| Event | Trigger |
|-------|---------|
| `LoadAssetEvent` | All assets loaded, ready for modification |
| `PlayerReadyEvent` | Player fully connected and loaded |
| `PlayerDisconnectEvent` | Player disconnecting |
| `PlayerConnectEvent` | Player connection initiated |
| `PlayerSetupConnectEvent` | Player setup phase |
| `ShutdownEvent` | Server shutting down |
| `BootEvent` | Server booting up |
| `AddWorldEvent` | New world created |
| `StartWorldEvent` | World starting |
| `RemoveWorldEvent` | World being removed |
| `PlayerChatEvent` | Player sends chat message (IAsyncEvent) |
| `ChunkPreLoadProcessEvent` | Chunk about to load |
| `GenerateSchemaEvent` | Asset editor schema generation |

#### registerGlobal vs register

- `registerGlobal(EventClass, handler)` — for player-scoped events (`PlayerReadyEvent`, `PlayerDisconnectEvent`)
- `register(EventClass, handler)` — for system-wide events (`LoadAssetEvent`, `ShutdownEvent`)

### EcsEvent (Entity Component System Events)

ECS events are handled by `EntityEventSystem` classes registered via `getEntityStoreRegistry()`:

```java
this.getEntityStoreRegistry().registerSystem(new MyEventSystem());
```

See [ECS Event Systems](../ecs/event-systems.md) for full details.

#### Key ECS Events

| Event | Trigger | Cancellable |
|-------|---------|-------------|
| `PlaceBlockEvent` | Player places a block | Yes |
| `BreakBlockEvent` | Player breaks a block | Yes |
| `DamageBlockEvent` | Block takes damage | Yes |
| `Damage` | Entity takes damage | Yes |
| `CraftRecipeEvent.Pre` | Before crafting starts | Yes |
| `CraftRecipeEvent.Post` | After crafting completes | No |
| `DropItemEvent.Drop` | Item being dropped | Yes |
| `DropItemEvent.PlayerRequest` | Player requests item drop | Yes |
| `UseBlockEvent.Pre` | Before block interaction | Yes |
| `UseBlockEvent.Post` | After block interaction | No |
| `InteractivelyPickupItemEvent` | Player picks up item | Yes |
| `SwitchActiveSlotEvent` | Player switches hotbar slot | Yes |
| `MoonPhaseChangeEvent` | Moon phase changes | No |
| `PrefabPasteEvent` | Prefab being pasted | Yes |

### IAsyncEvent

Events processed asynchronously:

| Event | Trigger |
|-------|---------|
| `PlayerChatEvent` | Player sends a chat message |
| `SendCommonAssetsEvent` | Common assets being sent to client |

## Event Handler Patterns

### Global Event Handler (Static Method)

```java
private static void onPlayerReady(PlayerReadyEvent event) {
    Ref<EntityStore> ref = event.getPlayerRef();
    Store<EntityStore> store = ref.getStore();
    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
    
    if (playerRef == null) return;
    
    playerRef.sendMessage(Message.raw("§aWelcome!"));
}
```

### Accessing World from PlayerReadyEvent

```java
private static void onPlayerReady(PlayerReadyEvent event) {
    Ref<EntityStore> ref = event.getPlayerRef();
    Store<EntityStore> store = ref.getStore();
    EntityStore entityStore = store.getExternalData();
    World world = entityStore.getWorld();
    
    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
    // Now have playerRef + world
}
```

## See Also

- [ECS Event Systems](../ecs/event-systems.md)
- [Plugin Lifecycle](./lifecycle.md)
