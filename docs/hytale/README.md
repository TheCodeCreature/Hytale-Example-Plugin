---
topic: "Hytale Documentation Index"
category: "Overview"
updated: 2026-04-16
sources: ["codebase analysis", "hytalemodding.dev", "hytale.com"]
---

# Hytale Engine & Modding Documentation

A comprehensive local reference for understanding how Hytale runs, its internal systems, and how to create server plugins.

## Documentation Map

### Getting Started
- [Engine Overview](./engine/overview.md) — Architecture, data-driven design, server/client split
- [Getting Started with Plugins](./plugins/getting-started.md) — Setting up a plugin project from scratch
- [Build & Run](./plugins/build-and-run.md) — Gradle, building, testing, deploying

### Engine Architecture
- [Server Architecture](./engine/server-architecture.md) — How the server processes ticks, worlds, and players
- [Entity Component System (ECS)](./ecs/overview.md) — Core ECS architecture
  - [Entity Store](./ecs/entity-store.md) — EntityStore, components, refs, stores
  - [Event Systems](./ecs/event-systems.md) — EntityEventSystem, event dispatch
  - [Ticking Systems](./ecs/ticking-systems.md) — EntityTickingSystem, per-frame logic
  - [Queries & Archetypes](./ecs/queries-and-archetypes.md) — Query, Archetype, ArchetypeChunk
  - [Threading Model](./ecs/threading.md) — World thread, deferred execution, schedulers

### Blocks & World
- [Block Types](./blocks/block-types.md) — BlockType, materials, draw types, textures
- [Block Gathering](./blocks/gathering.md) — Breaking, soft, harvest, physics drop configs
- [Block States & Variants](./blocks/states-and-variants.md) — State definitions, variant rotation
- [Block Support & Physics](./blocks/support-and-physics.md) — Support rules, physics cascade
- [Drop Resolution](./blocks/drop-resolution.md) — How the engine determines what drops
- [World & Chunks](./blocks/world-and-chunks.md) — World, ChunkStore, ChunkColumn, coordinates

### Items & Inventory
- [Items](./items/items.md) — Item definitions, properties, resource types
- [Item Stacks & Containers](./items/stacks-and-containers.md) — ItemStack, ItemContainer, inventory
- [Drop Lists](./items/drop-lists.md) — ItemDropList, ItemDrop, drop containers

### Crafting
- [Crafting Recipes](./crafting/recipes.md) — CraftingRecipe, MaterialQuantity, inputs/outputs
- [Bench Types](./crafting/bench-types.md) — Crafting, StructuralCrafting, Processing benches
- [Block Groups & Resource Types](./crafting/block-groups.md) — ResourceTypeId, tag-based matching

### Asset System
- [Asset Pipeline](./assets/asset-pipeline.md) — AssetRegistry, AssetStore, loading lifecycle
- [Asset Formats Overview](./assets/formats-overview.md) — JSON format conventions, inheritance
- [Runtime Asset Mutation](./assets/runtime-mutation.md) — Reflection-based modification, risks
- **Format References:**
  - [Block Type Format](./assets/formats/block-type.md) — Full JSON schema for block definitions
  - [Item Format](./assets/formats/item.md) — Full JSON schema for item definitions
  - [Crafting Recipe Format](./assets/formats/crafting-recipe.md) — Recipe JSON structure
  - [Drop List Format](./assets/formats/drop-list.md) — ItemDropList JSON structure

### Plugin API
- [Plugin Lifecycle](./plugins/lifecycle.md) — JavaPlugin, JavaPluginInit, setup(), events
- [Manifest](./plugins/manifest.md) — manifest.json structure, dependencies, asset packs
- [Commands](./plugins/commands.md) — Command registration, AbstractCommandCollection, arguments
- [Events](./plugins/events.md) — Event registry, global vs scoped, IEvent vs EcsEvent
- [Capabilities & Limitations](./plugins/capabilities.md) — What plugins can and cannot do
- [Networking & Packets](./plugins/networking.md) — Server packets, SetServerCamera, ServerSetBlock

### Server / Client Boundary
- [Server vs Client](./server-client-boundary.md) — What's server-side vs client-side

### NPC System
- [NPC Overview](./npcs/overview.md) — Roles, instruction lists, data-driven NPCs
- [Combat Action Evaluator](./npcs/combat-evaluator.md) — Smart combat decisions
- [NPC Debug Commands](./npcs/debug-commands.md) — Visualization flags for development

### Server Configuration
- [Server Config](./server/config.md) — config.json, permissions, whitelist, bans
- [Folder Structure](./server/folder-structure.md) — Run directory layout, mod installation

### Community Resources
- [Community Resources](./community/resources.md) — Documentation sites, tutorials, tools
