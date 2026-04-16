---
topic: "Server Architecture"
category: "Engine"
updated: 2026-04-16
sources: ["codebase analysis", "hytalemodding.dev"]
---

# Server Architecture

## Summary

The Hytale server manages game state across one or more worlds within a universe. It processes player connections, dispatches events, runs ECS systems, handles asset loading, and manages the network protocol. Plugins hook into the server at well-defined extension points.

## Core Components

### HytaleServer

The central server class that provides:

- `SCHEDULED_EXECUTOR` — a `ScheduledExecutorService` for scheduling repeating tasks (e.g., transparency volume updates every 100ms)
- Server lifecycle management (boot, shutdown)
- Player connection handling

### Universe

The top-level container for all game data:

- Contains one or more `World` instances
- Manages player data persistence (`universe/players/`)
- Stores world data (`universe/worlds/`)

### World

Each world is an independent game space with its own:

- `ChunkStore` — manages loaded chunk data
- `EntityStore` — manages entities via the ECS
- Block state and physics
- Thread-safe execution via `world.execute(Runnable)`

**Important**: World operations that mutate state must be dispatched through `world.execute()` to ensure thread safety. This queues the operation for execution on the world's thread.

```java
world.execute(() -> {
    // Safe to modify world state here
    chunkStore.setBlock(x, y, z, blockTypeId);
});
```

### ChunkStore

Manages the voxel data for a world:

- Provides block read/write access
- Organizes data into `ChunkColumn` objects
- Handles chunk loading/unloading events
- `ChunkPreLoadProcessEvent` fires before a chunk is fully loaded

### PlayerRef

Represents a connected player on the server:

- `getUuid()` — unique player identifier
- `getUsername()` — display name
- `getTransform()` — position and rotation in world space
- `getPacketHandler()` — send packets to the client
- `sendMessage(Message)` — send chat messages
- `getReference()` — get the ECS entity reference (`Ref<EntityStore>`)

## Server Lifecycle

```
Boot
  ├── Load server config (config.json)
  ├── Initialize modules
  ├── Load asset packs (vanilla + plugins with IncludesAssetPack)
  ├── Fire LoadAssetEvent ← plugins modify assets here
  ├── Start worlds
  └── Accept player connections
       ├── PlayerConnectEvent
       ├── PlayerSetupConnectEvent
       ├── PlayerReadyEvent ← player is fully loaded
       └── ... gameplay ...
            ├── PlayerDisconnectEvent
            └── ShutdownEvent
```

## Threading Model

| Thread | Purpose |
|--------|---------|
| Main thread | Server startup, shutdown, event dispatch |
| World thread(s) | Per-world ECS ticking, block operations |
| Scheduled executor | Timer-based tasks (e.g., `HytaleServer.SCHEDULED_EXECUTOR`) |
| Network threads | Packet I/O |

**Rule**: Never mutate world state from a non-world thread. Use `world.execute()` to defer mutations.

## Server Configuration

The server reads `config.json` from the run directory:

```json
{
  "ServerName": "Hytale Server",
  "MaxPlayers": 100,
  "MaxViewRadius": 32,
  "Defaults": {
    "World": "default",
    "GameMode": "Adventure"
  },
  "DefaultModsEnabled": true
}
```

See [Server Config](../server/config.md) for full details.

## See Also

- [Threading Model](../ecs/threading.md)
- [World & Chunks](../blocks/world-and-chunks.md)
- [Plugin Lifecycle](../plugins/lifecycle.md)
