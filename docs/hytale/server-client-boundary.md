---
topic: "Server vs Client Boundary"
category: "Architecture"
updated: 2026-04-16
sources: ["codebase analysis", "hytale.com blog posts"]
---

# Server vs Client Boundary

## Summary

Hytale uses a server-authoritative architecture. Plugins run exclusively on the server and can only affect client behavior indirectly through packets and server-controlled settings.

## What Runs Where

### Server-Side (Plugin Territory)

| System | Examples |
|--------|----------|
| **Game state** | Block data, entity positions, inventories |
| **ECS** | All entity systems, component queries |
| **Events** | PlaceBlockEvent, BreakBlockEvent, Damage |
| **Commands** | Chat commands, arguments, validation |
| **Assets** | Block types, items, recipes, drop lists |
| **World** | Chunk management, block physics, support |
| **Crafting** | Recipe evaluation, bench requirements |
| **NPCs** | Behavior trees, combat evaluator, roles |
| **Networking** | Packet sending, state synchronization |

### Client-Side (Not Directly Modifiable by Plugins)

| System | Notes |
|--------|-------|
| **Rendering** | Block textures, models, shaders, lighting |
| **UI** | HUD, inventory screen, crafting UI, menus |
| **Audio** | Sound effects, music, ambient sounds |
| **Animations** | Player animations, NPC animations, particles |
| **Input** | Keyboard, mouse, controller handling |
| **Camera** | Core camera logic (but settings are server-controlled) |
| **Client prediction** | Movement prediction, block placement prediction |

### Shared / Hybrid

| System | Server Role | Client Role |
|--------|-------------|-------------|
| **Camera settings** | Server sends `ServerCameraSettings` | Client applies them |
| **Block visibility** | Server sends `ServerSetBlock` packets | Client renders the fake block |
| **Movement** | Server validates via `MovementManager` | Client predicts locally |
| **Interactions** | Server processes the action | Client sends intent + renders feedback |

## Server → Client Communication

Plugins influence client behavior through:

1. **Packets** — `ServerSetBlock`, camera settings, block type updates
2. **Messages** — Chat messages with color codes
3. **Asset packs** — JSON assets bundled with the plugin
4. **ECS state** — Modified entity components synced to clients

## Client → Server Communication

The client sends:

1. **Input events** — movement, interaction, chat
2. **Interaction chains** — block/entity targeting data
3. **Placement requests** — which block to place where

Plugins can intercept these through event handlers and ECS systems.

## Custom UI

Hytale supports custom UI via an HTML/CSS/JS-based system. As of early 2026, this is an evolving feature. Documentation available at the [official Custom UI docs](https://hytalemodding.dev/en/docs/official-documentation/custom-ui).

## See Also

- [Plugin Capabilities](./plugins/capabilities.md)
- [Networking & Packets](./plugins/networking.md)
