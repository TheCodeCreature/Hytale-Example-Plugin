---
topic: "Plugin Capabilities & Limitations"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Plugin Capabilities & Limitations

## Summary

Hytale server plugins have extensive access to game systems but are limited to server-side operations. Understanding what's possible (and what's not) prevents wasted effort.

## What Plugins CAN Do

### Block & World Manipulation
- Read and write blocks in the world
- Create transparent block volumes (visual fakes via packets)
- Set blocks with or without physics updates
- Programmatically remove blocks with or without drops
- Register new block types via asset packs

### Entity & Player Systems
- Register ECS ticking systems (per-frame logic)
- Register ECS event systems (react to game events)
- Modify player movement settings (gravity, jump force, speed)
- Send messages to players
- Track per-player state
- Access player inventory, active item, game mode

### Asset Modification
- Modify block gathering configs (drop types, quantities)
- Scale crafting recipe inputs
- Modify item max stack sizes
- Create synthetic drop lists
- Override vanilla item/block definitions via asset packs

### Camera Control
- Set custom `ServerCameraSettings` (distance, offset, first/third person)
- Control camera lerp speeds
- Toggle reticle display
- Position offsets and eye-relative positioning

### Commands
- Register custom commands with arguments, validators, and aliases
- Create command groups with subcommands
- Restrict commands by game mode

### Events
- Listen to player connect/disconnect/ready events
- Intercept block placement and breaking
- Handle crafting events
- React to damage, item drops, slot switches
- Handle asset loading completion

### Networking
- Send fake block updates (`ServerSetBlock` packets)
- Send camera settings packets
- Access player packet handlers

### Scheduling
- Schedule repeating tasks via `HytaleServer.SCHEDULED_EXECUTOR`
- Defer world operations via `world.execute()`

## What Plugins CANNOT Do

### Client-Side Rendering
- ❌ Modify block textures at runtime (textures are client-side cached)
- ❌ Change rendering shaders or visual effects
- ❌ Add or modify particle systems on the fly
- ❌ Change UI elements or HUD layout
- ❌ Modify client-side animations
- ❌ Control client sound effects (beyond sound events)

### Client Behavior
- ❌ Execute code on the client
- ❌ Modify client input handling
- ❌ Change client-side prediction behavior
- ❌ Access client file system

### Engine Internals
- ❌ Add new component types to the ECS
- ❌ Modify the rendering pipeline
- ❌ Change network protocol
- ❌ Modify chunk loading/unloading logic directly

## Workarounds for Common Limitations

| Want to... | Approach |
|------------|----------|
| Make blocks transparent | Send fake `ServerSetBlock` packets with transparent block type |
| Change camera view | Use `ServerCameraSettings` with custom distance/offset |
| Show debug info | Send colored chat messages or use debug shapes |
| Custom movement | Modify `MovementSettings` on `MovementManager` |
| Change drop behavior | Modify `BlockGathering` via reflection during `LoadAssetEvent` |
| Add new blocks | Include asset pack with JSON definitions |
| Consume extra items on placement | Register `EntityEventSystem<EntityStore, PlaceBlockEvent>` |

## See Also

- [Server vs Client](../server-client-boundary.md)
- [Plugin Lifecycle](./lifecycle.md)
