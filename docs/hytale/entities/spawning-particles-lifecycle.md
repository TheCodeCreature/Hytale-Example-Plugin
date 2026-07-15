---
topic: "Entity Spawning, Particle Attachment, and Lifecycle Management"
category: "Entities"
updated: 2026-05-08
sources:
  - "DeployablesUtils.java (full entity spawn example)"
  - "ProjectileModule.java (projectile spawn with holder)"
  - "ItemComponent.java (world item drop generation)"
  - "ItemUtils.java (dropItem/throwItem flow)"
  - "ParticleUtil.java (one-shot particle packets)"
  - "ItemEntityConfig.java (persistent particle on world items)"
  - "ItemQuality.java (quality→ItemEntityConfig→particleSystemId chain)"
  - "SpawnMarkerSystems.java (entity removal via commandBuffer)"
  - "DespawnComponent.java (timed auto-despawn)"
  - "SpawnParticleSystem.java (network packet)"
---

# Entity Spawning, Particle Attachment, and Lifecycle Management

## Summary

Hytale's ECS provides a `Holder<EntityStore>` pattern for constructing entities with components, then adding them to the world via `commandBuffer.addEntity()` or `store.addEntity()`. Particle effects can be either **one-shot** (via `ParticleUtil.spawnParticleEffect()` which sends a `SpawnParticleSystem` packet to nearby players) or **persistent** (via `ItemEntityConfig.particleSystemId` on world item entities, which the client renders continuously while the entity exists). Entities are removed via `commandBuffer.removeEntity(ref, RemoveReason.REMOVE)` or `store.removeEntity(ref, RemoveReason.REMOVE)`.

---

## 1. Spawning Entities from Server Plugins

### The Holder Pattern

All entity spawning follows the same pattern:

```java
// 1. Create a new empty holder from the EntityStore registry
Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

// 2. Add components to define the entity
holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
holder.addComponent(SomeComponent.getComponentType(), new SomeComponent(...));
holder.ensureComponent(SomeMarkerComponent.getComponentType()); // for marker/tag components

// 3. Add the entity to the world - returns a Ref<EntityStore> for tracking
Ref<EntityStore> entityRef = commandBuffer.addEntity(holder, AddReason.SPAWN);
// OR from a Store context:
Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
```

### Key APIs

| Class | Method | Returns | Purpose |
|-------|--------|---------|---------|
| `EntityStore.REGISTRY` | `newHolder()` | `Holder<EntityStore>` | Creates a new empty entity holder |
| `Holder<EntityStore>` | `addComponent(type, instance)` | void | Adds a component to the holder |
| `Holder<EntityStore>` | `ensureComponent(type)` | void | Ensures a component exists (marker/tag) |
| `Holder<EntityStore>` | `ensureAndGetComponent(type)` | `T` | Ensures + returns the component instance |
| `CommandBuffer<EntityStore>` | `addEntity(holder, AddReason)` | `Ref<EntityStore>` | Spawns entity, returns tracked reference |
| `Store<EntityStore>` | `addEntity(holder, AddReason)` | `Ref<EntityStore>` | Same but from Store context |
| `ComponentAccessor<EntityStore>` | `addEntity(holder, AddReason)` | void | Adds entity (no ref return in this overload) |
| `ComponentAccessor<EntityStore>` | `addEntities(holders[], AddReason)` | void | Batch add multiple entities |

### AddReason Enum

```java
public enum AddReason {
    SPAWN,  // New entity being created
    LOAD;   // Entity being loaded from persistence
}
```

### Minimal Invisible Entity (Marker Entity)

The minimal set of components for a positioned entity in the world:

```java
Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, Vector3f.ZERO));
holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType()); // Prevents persistence to disk
// Optionally: UUIDComponent, NetworkId for client visibility
```

**Important**: Without `NetworkId` and the visible component, the entity may not be sent to clients at all. For an entity that needs client-side effects (like particles), you likely need:

```java
holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
holder.ensureComponent(EntityModule.get().getVisibleComponentType());
```

### Full Example: DeployablesUtils.spawnDeployable()

The `DeployablesUtils.spawnDeployable()` method is the most complete reference for spawning a positioned, visible entity. It adds:
- `TransformComponent` (position/rotation)
- `HeadRotation`
- `UUIDComponent` (UUID.randomUUID())
- `ModelComponent` (visual model)
- `BoundingBox`
- `NetworkId` (for client sync)
- `EntityModule.get().getVisibleComponentType()` (visibility marker)
- `EntityStore.REGISTRY.getNonSerializedComponentType()` (non-persistent)
- `DespawnComponent` (timed auto-removal)

---

## 2. Particle Systems — Two Distinct Mechanisms

### Mechanism A: One-Shot Particles via `ParticleUtil` (Fire-and-Forget)

`ParticleUtil.spawnParticleEffect()` sends a `SpawnParticleSystem` network packet to nearby players. The particle plays once at the specified position. **It is not attached to any entity.** Once the packet is sent, the server has no control over the particle — it plays and finishes on the client.

```java
// Simplest overload — spawns particle at position, notifies players within 75 blocks
ParticleUtil.spawnParticleEffect("Drop_Uncommon", position, componentAccessor);

// With explicit player list
ParticleUtil.spawnParticleEffect("Drop_Uncommon", position, playerRefs, componentAccessor);

// With rotation, scale, color
ParticleUtil.spawnParticleEffect("Drop_Uncommon", x, y, z, yaw, pitch, roll, scale, color, sourceRef, playerRefs, componentAccessor);
```

The underlying packet:

```java
SpawnParticleSystem packet = new SpawnParticleSystem(
    "Drop_Uncommon",           // particleSystemId (String)
    new Position(x, y, z),     // position
    rotation,                  // Direction (nullable)
    scale,                     // float (default 1.0)
    color                      // Color (nullable)
);
// Sent to each nearby player via playerRefComponent.getPacketHandler().writeNoCache(packet)
```

**Key insight**: This is a one-shot effect. For a *continuous* particle loop visible while looking at a block, you would need to re-send this packet periodically (e.g., every tick or every few ticks) — which is feasible but network-heavy.

### Mechanism B: Persistent Particles via `ItemEntityConfig.particleSystemId` (Entity-Bound)

World item entities (dropped items) have a persistent particle system defined by `ItemEntityConfig`:

```
ItemQuality → ItemEntityConfig → particleSystemId
```

The chain:
1. Each `ItemQuality` asset (e.g., "Uncommon") has an `ItemEntityConfig` field
2. `ItemEntityConfig` has a `particleSystemId` field (defaults to `"Item"`)
3. The quality-specific config overrides this — e.g., `"Drop_Uncommon"` for uncommon items
4. This config is sent to the client as part of the item entity's network data
5. The **client renders the particle system continuously** while the entity exists
6. When the entity is removed, the client stops the particle

**This is the mechanism behind `Drop_Common`, `Drop_Uncommon`, etc.** — they are continuous particle loops rendered by the client on world item entities.

### There Is No Generic `ParticleSystemComponent`

There is **no** general-purpose ECS component like `ParticleSystemComponent` that can be added to arbitrary entities to give them a continuous particle effect. The persistent particle system is specific to the world item entity rendering path via `ItemEntityConfig`.

NPC entities use a different mechanism: `ActionSpawnParticles` in their behavior tree, which also uses `ParticleUtil` (one-shot).

---

## 3. Deleting/Despawning Entities

### Manual Removal

```java
// From CommandBuffer (within ECS systems):
commandBuffer.removeEntity(ref, RemoveReason.REMOVE);

// From Store (from commands, event handlers, etc.):
store.removeEntity(ref, RemoveReason.REMOVE);

// Returns the Holder if you need to re-add it later:
Holder<EntityStore> holder = store.removeEntity(ref, RemoveReason.UNLOAD);
```

### RemoveReason Enum

```java
public enum RemoveReason {
    REMOVE,  // Entity is permanently destroyed
    UNLOAD;  // Entity is being unloaded (can be re-added)
}
```

### Automatic Timed Despawn via DespawnComponent

```java
// Despawn in N seconds from now
holder.addComponent(
    DespawnComponent.getComponentType(),
    DespawnComponent.despawnInSeconds(timeResource, 10) // 10 seconds
);

// Despawn at specific instant
holder.addComponent(
    DespawnComponent.getComponentType(),
    new DespawnComponent(timeResource.getNow().plus(Duration.ofSeconds(30)))
);
```

The `DespawnComponent` is processed by an engine system that automatically removes the entity when the time expires.

### Does Removing the Entity Stop Client Particles?

**Yes.** When a world item entity is removed from the server:
1. The server sends an entity removal packet to all tracking clients
2. The client removes the entity from its local store
3. The client stops rendering any associated particle systems (including the `ItemEntityConfig.particleSystemId` particle)

For one-shot `ParticleUtil` particles: removal is irrelevant because they're not attached to any entity — they fire once and self-terminate on the client.

---

## 4. World Item Entity Approach (Recommended for Your Use Case)

### How World Items Are Spawned

`ItemComponent.generateItemDrop()` creates a complete world item entity holder:

```java
Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(itemStack));
holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
holder.ensureAndGetComponent(Velocity.getComponentType()).set(0, 0, 0);
holder.ensureComponent(PhysicsValues.getComponentType());
holder.ensureComponent(UUIDComponent.getComponentType());
holder.ensureComponent(Intangible.getComponentType());
holder.addComponent(DespawnComponent.getComponentType(), DespawnComponent.despawnInSeconds(timeResource, ttl));
// Then: store.addEntity(holder, AddReason.SPAWN) → returns Ref<EntityStore>
```

### How the Particle System Gets Applied

The particle system on a world item comes from the **item quality's `ItemEntityConfig`**, NOT from an ECS component:

1. `Item` → `getQualityIndex()` → `ItemQuality`
2. `ItemQuality` → `itemEntityConfig` → `particleSystemId` (e.g., `"Drop_Uncommon"`)
3. Alternatively, `Item` → `getItemEntityConfig()` → `particleSystemId` (per-item override)
4. The `ItemEntityConfig` is serialized to the client via network protocol
5. Client renders the particle continuously while the entity exists

### Can You Spawn a World Item Just for the Particle?

**Yes, with caveats:**

You could spawn an item entity with a real item (e.g., any uncommon-quality item) and the client would render the `Drop_Uncommon` particle. However:

- **The item is visible** — the player sees a floating item model
- **The item can be picked up** — unless you add `Intangible` and set a very long pickup delay
- **The item has physics** — it will bounce and settle on the ground
- **You can't set an arbitrary particle** — it's determined by the item's quality config, not freely configurable

This approach is **not ideal** for arbitrary particle placement because you can't make the item invisible or set a custom particle system independently.

### Preventing Pickup

```java
ItemComponent itemComponent = new ItemComponent(itemStack);
itemComponent.setPickupDelay(Float.MAX_VALUE); // Effectively unpickable
```

Or add the `Intangible` marker component (already used in item drops):
```java
holder.ensureComponent(Intangible.getComponentType());
```

---

## 5. Alternative: Custom NPC/Entity Approach

There is no evidence of a simple "invisible particle carrier" entity type in the engine. Options:

### Option A: Repeated One-Shot Particles (Simplest, Recommended)

Instead of spawning an entity, just call `ParticleUtil.spawnParticleEffect("Drop_Uncommon", position, ...)` periodically from a tick system:

```java
// In your ECS system's tick method:
ParticleUtil.spawnParticleEffect("Drop_Uncommon", blockCenterPosition, componentAccessor);
```

**Pros**: No entity management, no cleanup, trivial implementation
**Cons**: Network overhead (one packet per tick per effect per player), particle may flicker between sends

### Option B: Spawn a Deployable-Style Entity with DespawnComponent

Create a minimal invisible entity using the DeployablesUtils pattern but without a model:

```java
Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, Vector3f.ZERO));
holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
holder.ensureComponent(EntityModule.get().getVisibleComponentType());
holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
// No ModelComponent → no visual model
// But: unclear if client renders anything without a model or item
```

**Problem**: Without a recognized entity rendering path (item entity, NPC, projectile, etc.), the client likely won't render any particle even if you could attach one. The particle rendering is specific to item entities via `ItemEntityConfig`.

### Option C: World Item with Invisible/Dummy Item

If you can create a custom item with no visual model and the desired `ItemEntityConfig.particleSystemId`:

```json
{
    "ItemEntityConfig": {
        "ParticleSystemId": "Drop_Uncommon",
        "ShowItemParticles": true
    }
}
```

Then spawn a world item entity with that item. The client would render the particle but no visible item model. **This requires creating a custom item asset.**

---

## 6. Entity Reference Tracking

### Spawning Returns `Ref<EntityStore>`

```java
Ref<EntityStore> entityRef = commandBuffer.addEntity(holder, AddReason.SPAWN);
// OR
Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
```

### Ref Validity

```java
entityRef.isValid()  // Returns false after the entity is removed
```

### Using the Ref to Remove Later

```java
// Store the ref somewhere (e.g., in a Map<UUID, Ref<EntityStore>>)
// Later, when the player looks away:
if (entityRef.isValid()) {
    commandBuffer.removeEntity(entityRef, RemoveReason.REMOVE);
    // OR
    store.removeEntity(entityRef, RemoveReason.REMOVE);
}
```

### Thread Safety: `world.execute()`

Entity store operations must happen on the world thread. From outside the ECS tick:

```java
world.execute(() -> {
    store.removeEntity(entityRef, RemoveReason.REMOVE);
});
```

---

## Recommendation for Stencil Book Particle Loop

For showing a `Drop_Uncommon` particle at a block position while the player is looking at it:

### Best Approach: Repeated One-Shot Particles

1. **Track what block the player is looking at** (already in your raycasting system)
2. **When the player starts looking**: begin calling `ParticleUtil.spawnParticleEffect("Drop_Uncommon", blockCenter, playerRefs, store)` each tick
3. **When the player looks away**: simply stop calling it — the particle naturally ends on the client

**Why this works**: No entity spawning/cleanup complexity, no custom invisible items, trivially correct lifecycle management. The `Drop_Uncommon` particle system is likely designed as a short-duration looping effect, so calling it every 5-10 ticks should produce a continuous visual.

### If Continuous Looping Particles Flicker

If the one-shot approach produces visible gaps between particle emissions, the entity-based approach becomes necessary:

1. Create a custom invisible item asset with `"ParticleSystemId": "Drop_Uncommon"`
2. Spawn a world item entity with that item at the block position
3. Set `pickupDelay` to `Float.MAX_VALUE` to prevent pickup
4. Track the `Ref<EntityStore>` 
5. When the player looks away, call `store.removeEntity(ref, RemoveReason.REMOVE)`
6. Client automatically stops the particle

---

## See Also

- [DespawnComponent.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/DespawnComponent.java) — Timed auto-despawn
- [DeployablesUtils.java](../../../.tmp_hytale_src/com/hypixel/hytale/builtin/deployables/DeployablesUtils.java) — Complete entity spawn reference
- [ParticleUtil.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/ParticleUtil.java) — One-shot particle API
- [ItemEntityConfig.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemEntityConfig.java) — Persistent item particle config
- [ItemQuality.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemQuality.java) — Quality→particle chain
