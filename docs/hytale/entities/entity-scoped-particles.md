---
topic: "Entity-Scoped Particle Systems — How Particles Are Tied to Entity Lifecycle"
category: "Entities / Particles"
updated: 2026-05-08
sources:
  - "EffectControllerComponent.java (entity effect management, network sync)"
  - "EntityTrackerSystems.EffectControllerSystem (effect → client sync pipeline)"
  - "EntityEffect.java (asset config with ApplicationEffects)"
  - "ApplicationEffects.java (particles, tints, animations, VFX on entity effects)"
  - "ModelParticle.java (server config — systemId, targetEntityPart, detachedFromModel)"
  - "protocol/ModelParticle.java (wire format for model-attached particles)"
  - "SpawnModelParticles.java (packet 165 — one-shot entity-attached particles)"
  - "SpawnParticleSystem.java (packet 152 — fire-and-forget world particles)"
  - "ItemEntityConfig.java (particleSystemId on dropped items)"
  - "protocol/ComponentUpdate.java (entity sync — entityEffectUpdates field)"
  - "protocol/ComponentUpdateType.java (EntityEffects = 11)"
  - "protocol/EntityEffectUpdate.java (Add/Remove effect by index)"
  - "EntityEffectCommand.java (built-in /effect command — shows API usage)"
  - "NPCSystems.AddSpawnEntityEffectSystem (auto-apply effects on NPC spawn)"
  - "ProjectileComponent.java (projectile particles — WorldParticle, fire-and-forget)"
---

# Entity-Scoped Particle Systems

## Summary

Hytale has **three distinct mechanisms** for particles on entities. Only one — the **EntityEffect system** — provides true entity-scoped lifecycle management where the server controls particle creation/removal and particles are automatically cleaned up when the entity is removed. The other two (ItemEntityConfig and SpawnModelParticles) work differently.

---

## The Three Mechanisms

### Mechanism 1: EntityEffect System (Server-Driven, Entity-Scoped) ✅

**This is the primary mechanism for entity-scoped particles.** It provides full server control over adding/removing persistent visual effects on entities, with automatic cleanup when the entity is destroyed.

#### How It Works

```
Server-Side:                           Network:                  Client-Side:
┌──────────────────────┐               ┌───────────────┐         ┌──────────────────────┐
│ EffectControllerComp │───addEffect──▶│ EntityUpdates │────────▶│ Look up EntityEffect │
│ on entity            │               │ packet (161)  │         │ by index              │
│                      │               │ ComponentUpdate│         │                      │
│ tracks active effects│               │ type=EntityEffects│      │ Read ApplicationEffects│
│ queues EntityEffect  │               │ entityEffectUpdates│     │ .particles[]          │
│ Updates (Add/Remove) │               │ = [{Add, idx}]│         │                      │
└──────────────────────┘               └───────────────┘         │ Spawn ModelParticle   │
                                                                  │ systems on entity model│
                                                                  │                      │
                                                                  │ When entity removed:  │
                                                                  │ → destroy all visuals │
                                                                  │   including particles │
                                                                  └──────────────────────┘
```

#### Data Flow

1. **EntityEffect** is a JSON asset registered at `Entity/EntityEffect/`. Each effect has:
   - `ApplicationEffects.Particles[]` — array of `ModelParticle` configs (systemId, targetEntityPart, color, scale, position/rotation offsets, detachedFromModel)
   - `Duration`, `Infinite`, `OverlapBehavior`, `RemovalBehavior`
   - Plus: stat modifiers, tints, animations, screen effects, sounds, ModelVFX

2. **EffectControllerComponent** is an ECS component on entities that tracks active effects:
   - `addEffect(ref, entityEffect, componentAccessor)` — adds effect, queues network update
   - `removeEffect(ref, effectIndex, componentAccessor)` — removes effect, queues network update
   - `clearEffects(ref, componentAccessor)` — removes all effects
   - Changes are batched into `EntityEffectUpdate[]` arrays

3. **EntityTrackerSystems.EffectControllerSystem** syncs effects to clients:
   - On each tick, checks `consumeNetworkOutdated()` for changed effects
   - For newly visible viewers: sends `createInitUpdates()` (full state)
   - For existing viewers: sends `consumeChanges()` (delta updates)
   - Wraps in `ComponentUpdate` with `type = ComponentUpdateType.EntityEffects` (11)
   - Sent via `EntityUpdates` packet (161)

4. **Client receives** the `EntityEffectUpdate[]`, looks up `EntityEffect` by asset index, and:
   - For `EffectOp.Add`: spawns `ApplicationEffects.particles[]` as `ModelParticle` systems on the entity
   - For `EffectOp.Remove`: stops those particle systems
   - When entity is removed entirely (via `EntityUpdates.removed[]`): destroys all visuals including particles

#### Server API Usage

```java
// Get effect asset
EntityEffect effect = EntityEffect.getAssetMap().getAsset("My_Custom_Effect");

// Get entity's effect controller
EffectControllerComponent effectCtrl = store.getComponent(entityRef, EffectControllerComponent.getComponentType());

// Add effect with default duration/behavior from asset config
effectCtrl.addEffect(entityRef, effect, store);

// Add effect with custom duration and overlap behavior
effectCtrl.addEffect(entityRef, effect, 30.0f, OverlapBehavior.OVERWRITE, store);

// Add infinite effect (persists until explicitly removed)
int effectIndex = EntityEffect.getAssetMap().getIndex("My_Custom_Effect");
effectCtrl.addInfiniteEffect(entityRef, effectIndex, effect, store);

// Remove specific effect
effectCtrl.removeEffect(entityRef, effectIndex, store);

// Remove all effects
effectCtrl.clearEffects(entityRef, store);
```

#### EntityEffect Asset Example (JSON)

```json
{
  "Id": "Blueprint_Particle_Loop",
  "Infinite": true,
  "OverlapBehavior": "Ignore",
  "RemovalBehavior": "Complete",
  "ApplicationEffects": {
    "Particles": [
      {
        "SystemId": "Drop_Uncommon",
        "TargetEntityPart": "Self",
        "Scale": 1.0,
        "DetachedFromModel": false
      }
    ]
  }
}
```

#### Requirements for an Entity to Use EntityEffect

The entity MUST have:
1. **`EffectControllerComponent`** — the component that tracks active effects
2. **`NetworkId`** — for client visibility/tracking
3. **Visible component** — so the entity tracker sends updates to players
4. **`TransformComponent`** — for spatial positioning
5. **`ModelComponent`** — because `ModelParticle` attaches to the entity's model (required for particles to render)

The `RoleBuilderSystem` automatically adds `EffectControllerComponent` to NPC entities. For custom entities, you must add it manually.

---

### Mechanism 2: ItemEntityConfig.particleSystemId (Client-Driven, Item-Specific) ⚠️

**This is how dropped item beams work (Drop_Common, Drop_Uncommon, etc.).**

#### How It Works

1. Each `Item` asset can have an `ItemEntityConfig` with fields:
   - `ParticleSystemId` (String) — e.g., `"Drop_Uncommon"` — defaults to `"Item"`
   - `ParticleColor` (Color) — optional tint
   - `ShowItemParticles` (boolean) — defaults to `true`

2. `ItemEntityConfig` is sent to the client as part of the Item asset data (via `UpdateItems` packet during asset sync), NOT per-entity

3. When the client renders a world item entity:
   - It reads the item type from the `ComponentUpdate.Item` field
   - Looks up the Item asset → `ItemEntityConfig`
   - If `showItemParticles` is true, spawns the `particleSystemId` particle system on the entity
   - The particle runs continuously while the entity is rendered

4. When the entity is removed (appears in `EntityUpdates.removed[]`):
   - Client destroys the entity's entire render representation
   - **Particle dies instantly** — not after a fade, not after lifespan. The client simply stops it.

#### Why This Doesn't Help Plugins (Directly)

- `particleSystemId` is defined on the **Item asset**, not per-entity
- You can't change it at runtime for a specific dropped item instance
- You'd need to create a custom Item type with the desired particle
- Dropping that item creates the particle effect, picking it up removes it
- But this only works for item entities — not arbitrary positions

#### The Quality Chain

```
ItemQuality asset → ItemEntityConfig → particleSystemId
                                     → particleColor
                                     → showItemParticles

Example:
  "Common" quality  →  particleSystemId: null (no beam)
  "Uncommon" quality → particleSystemId: "Drop_Uncommon" (green beam)
  "Rare" quality     → particleSystemId: "Drop_Rare" (blue beam)
```

---

### Mechanism 3: SpawnModelParticles (One-Shot, Entity-Attached) ⚠️

**Packet 165. Fire-and-forget particle spawn on an entity's model.**

#### How It Works

```java
SpawnModelParticles packet = new SpawnModelParticles();
packet.entityId = networkId;  // entity's network ID
packet.modelParticles = new ModelParticle[] {
    new ModelParticle(
        "Drop_Uncommon",         // systemId
        1.0f,                    // scale
        null,                    // color
        EntityPart.Self,         // targetEntityPart
        null,                    // targetNodeName (bone name)
        null,                    // positionOffset
        null,                    // rotationOffset
        false                    // detachedFromModel
    )
};
```

#### ModelParticle Fields

| Field | Type | Purpose |
|-------|------|---------|
| `systemId` | String | Particle system asset ID |
| `scale` | float | Scale multiplier |
| `color` | Color | Optional color override |
| `targetEntityPart` | EntityPart enum | Which part of the entity (Self, LeftHand, RightHand, etc.) |
| `targetNodeName` | String | Specific bone/node on the model to attach to |
| `positionOffset` | Vector3f | Offset from attachment point |
| `rotationOffset` | Direction | Rotation offset |
| `detachedFromModel` | boolean | If true, spawns in world space at entity position rather than following the model |

#### Why This Doesn't Solve the Lifecycle Problem

- These are **one-shot** — the server sends the packet and forgets
- The particle's lifespan is governed by the particle system config on the client
- If the particle system is configured to loop indefinitely, it will keep running
- When the entity is removed, the client **should** destroy these with the entity render — but the server has no "stop" packet
- There is no server-side tracking or removal API for these particles

---

## Projectile Particles

Projectile particles use a **completely different mechanism**:

- `Projectile` asset config has `BounceParticles` and `HitParticles` — both are `WorldParticle` type
- These use `ParticleUtil.spawnParticleEffect()` → `SpawnParticleSystem` packet (152)
- They are **fire-and-forget world-positioned particles** at the impact/bounce point
- Trail particles on flying projectiles are defined in the **model asset** (client-side rendering), not sent via packets
- When a projectile entity is removed, the client stops rendering the model (and its built-in trail particles)

---

## Definitive Answer: Entity-Scoped Particles from a Plugin

### YES — via the EntityEffect System

The exact approach:

#### Step 1: Create an EntityEffect Asset

Create a JSON file in the `Entity/EntityEffect/` asset directory:

```json
{
  "Id": "Plugin_Particle_Loop",
  "Infinite": true,
  "OverlapBehavior": "Ignore", 
  "RemovalBehavior": "Complete",
  "ApplicationEffects": {
    "Particles": [
      {
        "SystemId": "Drop_Uncommon",
        "TargetEntityPart": "Self",
        "Scale": 1.0,
        "DetachedFromModel": true
      }
    ]
  }
}
```

Key: `DetachedFromModel: true` spawns the particle in world space at the entity position, so it doesn't need to track a model skeleton.

#### Step 2: Spawn a Lightweight Entity

```java
Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

// Position
holder.addComponent(TransformComponent.getComponentType(), 
    new TransformComponent(blockPosition.toVector3d(), Vector3f.ZERO));

// Model (required for entity to be visible — use smallest/invisible model available)
ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset("some_tiny_model");
holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(Model.createRandomScaleModel(modelAsset)));

// Network visibility
holder.addComponent(NetworkId.getComponentType(), 
    new NetworkId(store.getExternalData().takeNextNetworkId()));

// Effect controller (required for EntityEffect system)
holder.ensureComponent(EffectControllerComponent.getComponentType());

// Auto-cleanup
holder.addComponent(DespawnComponent.getComponentType(), 
    DespawnComponent.despawnInSeconds(timeResource, 30));

// Non-persistent (don't save to disk)
holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

// UUID for identification
holder.ensureComponent(UUIDComponent.getComponentType());

Ref<EntityStore> entityRef = commandBuffer.addEntity(holder, AddReason.SPAWN);
```

#### Step 3: Apply the EntityEffect

```java
// After entity is added to the world (in next tick or via world.execute()):
EffectControllerComponent effectCtrl = store.getComponent(entityRef, EffectControllerComponent.getComponentType());
EntityEffect effect = EntityEffect.getAssetMap().getAsset("Plugin_Particle_Loop");
effectCtrl.addEffect(entityRef, effect, store);
```

#### Step 4: Remove = Particle Dies

```java
// When you want the particle to stop:
commandBuffer.removeEntity(entityRef, RemoveReason.REMOVE);
// The client receives the entity removal and destroys all visuals including particles
```

### The Packet Sequence

```
1. EntityUpdates { updates: [{ networkId: X, updates: [
     { type: Transform, transform: {pos, rot, scale} },
     { type: Model, model: {...} },
     { type: EntityEffects, entityEffectUpdates: [{ type: Add, id: effectIdx }] }
   ]}]}
   → Client creates entity, looks up EntityEffect by idx, reads ApplicationEffects.particles,
     spawns "Drop_Uncommon" particle system at entity position

2. (Later) EntityUpdates { removed: [X] }
   → Client destroys entity render → particles die instantly
```

### Open Questions / Risks

1. **Model requirement**: Does an entity need a real model for `DetachedFromModel: true` particles to render? Or does any model (even invisible) work? Based on the code, `ModelParticle` is part of `ApplicationEffects` which is sent via `EntityEffectUpdate`, and the client interprets `DetachedFromModel` to mean "spawn at entity world position." The entity still needs a `ModelComponent` for the entity tracker to include the model update, but the actual model visual might not matter.

2. **EffectControllerComponent without RoleBuilderSystem**: NPC entities get `EffectControllerComponent` via `RoleBuilderSystem`. Custom entities need `holder.ensureComponent(EffectControllerComponent.getComponentType())`. This should work because `EffectControllerComponent` has a default constructor and its own `CODEC` for serialization.

3. **Visibility pipeline**: The `EntityTrackerSystems.EffectControllerSystem` requires both `Visible` and `EffectControllerComponent` in its query. The `Visible` component is auto-added by `EnsureVisibleComponent` when an `EntityViewer` (player) can see the entity. The entity needs `NetworkId` to be included in the spatial structure that viewers query.

4. **Alternative: Use SpawnModelParticles + Entity Removal**: If creating a custom `EntityEffect` asset isn't feasible, you could:
   - Spawn entity with just `TransformComponent`, `ModelComponent`, `NetworkId`
   - Send `SpawnModelParticles` packet to nearby players
   - Remove entity when done — client kills particles with entity
   - Risk: particles might render incorrectly without the full EntityEffect pipeline

5. **Alternative: Phantom Item Entity**: Create a custom Item type with the desired `ItemEntityConfig.ParticleSystemId`, and drop it invisibly at the target position. Removing the item entity kills the particle. But this pollutes the item registry and the entity has pickup behavior.

---

## Summary Table

| Mechanism | Packet | Entity-Bound? | Server Control? | Auto-Cleanup on Remove? | Loop Support? |
|-----------|--------|---------------|-----------------|-------------------------|---------------|
| EntityEffect + EffectControllerComponent | EntityUpdates (161) | ✅ Yes | ✅ Full (add/remove) | ✅ Yes | ✅ Yes (Infinite: true) |
| ItemEntityConfig.particleSystemId | UpdateItems (asset) | ✅ Yes (item entities) | ❌ Asset-level only | ✅ Yes | ✅ Yes (client renders while entity exists) |
| SpawnModelParticles | 165 | ⚠️ Partially (targets entity) | ❌ Fire-and-forget | ⚠️ Probable (client impl) | ❌ Depends on particle config |
| SpawnParticleSystem | 152 | ❌ No (world position) | ❌ Fire-and-forget | ❌ No | ❌ No (one-shot) |
| SpawnBlockParticleSystem | 153 | ❌ No (block position) | ❌ Fire-and-forget | ❌ No | ❌ No (one-shot) |

## See Also

- [spawning-particles-lifecycle.md](./spawning-particles-lifecycle.md) — Entity spawn patterns and basic particle overview
- EntityEffect JSON schema: `Entity/EntityEffect/*.json`
- ApplicationEffects fields: tints, animations, particles, screen effects, sounds, ModelVFX
