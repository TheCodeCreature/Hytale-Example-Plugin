---
topic: "Entity Spawning with Particle Attachment — Server Plugin Research"
category: "Entities / Particles / Plugin API"
updated: 2026-05-08
sources: ["World.java (decompiled)", "Entity.java (decompiled)", "NPCPlugin.java (decompiled)", "EntityModule.java (decompiled)", "EntityRegistry.java (decompiled)", "Store.java (decompiled)", "CommandBuffer.java (decompiled)", "SpawnModelParticles.java (protocol)", "ModelParticle.java (protocol)", "ApplicationEffects.java (protocol)", "EntityEffect.java (decompiled config)", "EffectControllerComponent.java (decompiled)", "DeployablesSystem.java (decompiled)", "DamageSystems.java (decompiled)", "particle-tick-loop-research.md (local docs)", "block-selection-highlight-research.md (local docs)"]
---

# Entity Spawning with Particle Attachment — Server Plugin Research

## Executive Summary

**Spawning a custom entity with an attached particle system that disappears on despawn is NOT feasible via the current server plugin API.** The approach fails on two fundamental fronts:

1. **Entity spawning is tightly coupled to pre-registered entity types** — you cannot create a lightweight "marker" entity without a registered `Entity` subclass with a role/model pipeline
2. **Particles cannot be attached to entities at spawn** — `ModelParticle` attachment requires an entity with a model, and particles play to completion once spawned (no cancel on entity removal)

The existing **particle heartbeat pattern** (already implemented in `StencilBookParticleLoop`) is the correct approach for this use case.

---

## 1. Entity Spawning from Server Plugins

### 1.1 `World.spawnEntity()` — DEPRECATED

```java
@Deprecated
public <T extends Entity> T spawnEntity(T entity, @Nonnull Vector3d position, Vector3f rotation)
```

**Source:** [World.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/World.java#L636)

This method is marked `@Deprecated` and delegates to `addEntity()`. It requires:
- An `Entity` instance that is **already known to `EntityModule`** (`EntityModule.get().isKnown(entity)`)
- The entity must already have its world set (`entity.getWorld() == this`)
- The entity must have a valid network ID (`entity.getNetworkId() != -1`)
- Entity cannot be a `Player`

**These prerequisites mean you CANNOT just instantiate a new `Entity` subclass and spawn it.** The entity must be constructed through the `EntityModule` registration pipeline.

### 1.2 `NPCPlugin.spawnEntity()` — The Primary Spawn Path

This is how the engine actually spawns entities (NPCs):

```java
public Pair<Ref<EntityStore>, NPCEntity> spawnEntity(
    @Nonnull Store<EntityStore> store,
    int roleIndex,           // The NPC role ID (defines behavior, model, etc.)
    @Nonnull Vector3d position,
    @Nullable Vector3f rotation,
    @Nullable Model spawnModel,
    @Nullable TriConsumer<NPCEntity, Holder<EntityStore>, Store<EntityStore>> preAddToWorld,
    @Nullable TriConsumer<NPCEntity, Ref<EntityStore>, Store<EntityStore>> postSpawn
)
```

**Source:** [NPCPlugin.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/npc/NPCPlugin.java#L1100)

**What it does internally:**
1. Creates an `NPCEntity` component
2. Sets spawn time, role name, role index
3. Creates a `Holder<EntityStore>` and adds components:
   - `NPCEntity` — the NPC data
   - `TransformComponent` — position/rotation
   - `HeadRotation` — head rotation
   - `DisplayNameComponent` — name
   - `UUIDComponent` — unique ID
   - `ModelComponent` + `PersistentModel` — if a model is provided
4. Calls `store.addEntity(holder, AddReason.SPAWN)` → returns a `Ref<EntityStore>`
5. Returns `Pair<Ref<EntityStore>, NPCEntity>`

**Key insight:** `roleIndex` is mandatory. It resolves to a role name via `NPCPlugin.getName(roleIndex)`. Without a valid role index, spawn fails and returns null.

### 1.3 Entity Registration Pipeline

Entity types MUST be registered before they can be spawned:

```java
// From SpawningPlugin.java
this.getEntityRegistry().registerEntity(
    "LegacySpawnBeacon",          // string ID
    LegacySpawnBeaconEntity.class, // Java class
    LegacySpawnBeaconEntity::new,  // constructor
    LegacySpawnBeaconEntity.CODEC  // serialization codec
);

// From NPCPlugin.java
this.getEntityRegistry().registerEntity("NPC", NPCEntity.class, NPCEntity::new, NPCEntity.CODEC);
```

**Source:** [EntityRegistry.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/EntityRegistry.java#L20)

The registration:
1. Maps `String ID ↔ Class ↔ Constructor ↔ Codec`
2. Registers a `ComponentType<EntityStore, T>` in the ECS
3. Registers legacy `HolderSystem` and `RefSystem` for lifecycle management

**A plugin CAN register custom entity types** via `this.getEntityRegistry().registerEntity(...)` in its `setup()` method. However, creating a custom Entity subclass requires:
- Extending `Entity` (abstract class)
- Implementing serialization codec
- Being registered before any world loads

### 1.4 Existing Entity Types

| Entity Type | Class | Purpose |
|-------------|-------|---------|
| `NPC` | `NPCEntity extends LivingEntity` | All NPCs, mobs, creatures |
| `SpawnBeacon` | `SpawnBeacon extends Entity` | Spawn system markers |
| `LegacySpawnBeacon` | `LegacySpawnBeaconEntity extends Entity` | Legacy spawn markers |
| `PatrolPathMarker` | `PatrolPathMarkerEntity extends Entity` | NPC patrol paths |
| `Player` | `Player extends LivingEntity` | Cannot be spawned |

There is **no existing "marker" or "invisible" entity type** suitable for attaching particles.

---

## 2. Entity with Particle Attachment

### 2.1 `SpawnModelParticles` Packet (ID 165)

This packet spawns particles **attached to an entity's model**:

```java
public class SpawnModelParticles implements Packet {
    public int entityId;                    // network ID of the target entity
    public ModelParticle[] modelParticles;  // array of particle definitions
}
```

**Source:** [SpawnModelParticles.java](../../../.tmp_hytale_src/com/hypixel/hytale/protocol/packets/entities/SpawnModelParticles.java)

**`ModelParticle` protocol fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `systemId` | `String` | Particle system asset ID (e.g., `"Example_Simple"`) |
| `scale` | `float` | Particle scale multiplier |
| `color` | `Color?` | Tint color |
| `targetEntityPart` | `EntityPart` | Which part of the model to attach to (`Self`, etc.) |
| `targetNodeName` | `String?` | Specific model node to attach to |
| `positionOffset` | `Vector3f?` | Offset from attachment point |
| `rotationOffset` | `Direction?` | Rotation offset |
| `detachedFromModel` | `boolean` | Whether particles follow the model or are world-space |

**Source:** [ModelParticle.java (protocol)](../../../.tmp_hytale_src/com/hypixel/hytale/protocol/ModelParticle.java)

### 2.2 How `SpawnModelParticles` Is Used

**DamageSystems** sends this packet when an entity takes damage:

```java
SpawnModelParticles packet = new SpawnModelParticles(targetNetworkId, modelParticlesProtocol);
// ... broadcasts to nearby players ...
playerRefComponent.getPacketHandler().write(packet);
```

**Source:** [DamageSystems.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/damage/DamageSystems.java#L252)

**Critical requirement:** The entity MUST have:
- A valid `NetworkId` component (assigned during entity registration)
- A model on the client side for particles to attach to

### 2.3 `ApplicationEffects` — Entity Effect Visual System

`EntityEffect` assets (JSON) can define `ApplicationEffects` which include model particles:

```java
public class ApplicationEffects {
    public ModelParticle[] particles;           // particles attached to entity model
    public ModelParticle[] firstPersonParticles; // particles visible in first person
    public String modelVFXId;                   // model VFX reference
    public String entityAnimationId;            // animation override
    public Color entityBottomTint;              // bottom tint
    public Color entityTopTint;                 // top tint
    // ... sound effects, movement effects, etc.
}
```

**Source:** [ApplicationEffects.java](../../../.tmp_hytale_src/com/hypixel/hytale/protocol/ApplicationEffects.java)

These effects are applied via the `EffectControllerComponent` system. The entity must:
1. Have an `EffectControllerComponent` registered on its archetype
2. Have a model for model-attached particles to bind to
3. The `EntityEffect` must be registered in the asset system

### 2.4 `DeployablesSystem` Particle Pattern

Deployables (turrets, traps) use `ModelParticle` for spawn/despawn effects:

```java
// On entity spawn
ModelParticle[] particles = deployableConfig.getSpawnParticles();
for (ModelParticle particle : particles) {
    DeployablesSystem.spawnParticleEffect(ref, commandBuffer, position, particle);
}
```

But `spawnParticleEffect` in `DeployablesSystem` actually calls `ParticleUtil.spawnParticleEffect()` — it spawns a **world particle**, not a model-attached particle:

```java
ParticleUtil.spawnParticleEffect(
    particle.getSystemId(),
    particlePosition.x, particlePosition.y, particlePosition.z,
    particleRotation.x, particleRotation.y, particleRotation.z,
    sourceRef, results, commandBuffer
);
```

**Source:** [DeployablesSystem.java](../../../.tmp_hytale_src/com/hypixel/hytale/builtin/deployables/system/DeployablesSystem.java#L31)

This means even deployables don't actually attach particles to entities — they spawn world particles at the entity's position.

---

## 3. Entity Removal / Despawn

### 3.1 `entity.remove()` — Direct Removal

```java
public boolean remove() {
    this.world.debugAssertInTickingThread();
    // ... fires EntityRemoveEvent ...
    this.world.getEntityStore().getStore().removeEntity(this.reference, RemoveReason.REMOVE);
    return true;
}
```

**Source:** [Entity.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/entity/Entity.java#L104)

Must be called from the world thread.

### 3.2 `Store.removeEntity(Ref, RemoveReason)` — ECS-Level Removal

```java
public Holder<ECS_TYPE> removeEntity(@Nonnull Ref<ECS_TYPE> ref, @Nonnull RemoveReason reason)
```

**Source:** [Store.java](../../../.tmp_hytale_src/com/hypixel/hytale/component/Store.java#L651)

Removes the entity from the store, fires lifecycle callbacks, and broadcasts despawn to clients.

### 3.3 `CommandBuffer.removeEntity(Ref, RemoveReason)` — Deferred Removal

```java
commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
```

**Source:** [CommandBuffer.java](../../../.tmp_hytale_src/com/hypixel/hytale/component/CommandBuffer.java#L148)

Used inside ECS systems where direct mutation isn't safe. Queues removal for end of tick.

### 3.4 `DespawnComponent` — Time-Based Auto-Despawn

Adds automatic despawn after a duration:

```java
holder.addComponent(
    DespawnComponent.getComponentType(),
    DespawnComponent.despawnInSeconds(timeResource, 300L)  // 5 minutes
);
```

Used by item drops and projectiles. Not useful for our case (we want instant programmatic removal).

### 3.5 Getting the Ref Back After Spawning

The `NPCPlugin.spawnEntity()` returns `Pair<Ref<EntityStore>, NPCEntity>`. The `Ref` is the handle you need for removal:

```java
Pair<Ref<EntityStore>, NPCEntity> result = npcPlugin.spawnEntity(store, roleIndex, position, rotation, null, null);
Ref<EntityStore> entityRef = result.getFirst();
// ... later ...
commandBuffer.removeEntity(entityRef, RemoveReason.REMOVE);
```

---

## 4. Why the "Dummy Entity + Particle" Approach FAILS

### Problem 1: No Lightweight Entity Type

There is no existing invisible/marker entity type. To create one, you'd need to:
- Write a custom `Entity` subclass
- Create a serialization codec
- Register it via `getEntityRegistry().registerEntity()`
- Create an NPC role definition (for NPCPlugin-based spawning) or use the deprecated `World.spawnEntity()` path

This is massive overengineering for a particle highlight.

### Problem 2: Particles Don't Track Entities Automatically

Even if you could spawn an invisible entity at the block position:
- `SpawnModelParticles` requires an entity with a visible model — particles attach to model nodes
- An invisible entity (no model) would have nothing for particles to attach to
- World particles (`ParticleUtil.spawnParticleEffect`) are position-based, not entity-tracked

### Problem 3: Particle Lifetime Is Independent of Entity Lifetime

**Particles CANNOT be cancelled** (see [particle-tick-loop-research.md](../particles/particle-tick-loop-research.md)). Even if you:
1. Spawn entity at block position
2. Send `SpawnModelParticles` packet targeting that entity
3. Remove the entity

The particle would **continue playing to completion** on the client. There is no mechanism to kill it early. The client has no "stop particle because entity despawned" behavior for world particles.

### Problem 4: The Original Goal Is Already Solved

The question "when the entity is despawned, the particle disappears instantly" implies wanting particle lifecycle tied to entity lifecycle. But:
- The heartbeat particle pattern **already achieves instant disappearance** — stop spawning particles, and the last generation dies within 150ms
- This is simpler, lighter, and more reliable than entity spawning + particle attachment

---

## 5. Alternative: Dummy NPC Approach — ANALYSIS

**Can you spawn a dummy NPC with no AI, no collision, no model, just particles?**

### Spawning an NPC

```java
NPCPlugin npcPlugin = NPCPlugin.get();
Store<EntityStore> store = world.getEntityStore().getStore();
int roleIndex = ...; // must be a valid, pre-defined NPC role
Pair<Ref<EntityStore>, NPCEntity> result = npcPlugin.spawnEntity(
    store, roleIndex, position, null, null, null
);
```

### Requirements That Block This

1. **Valid `roleIndex` required** — must resolve to a registered NPC role name. There are no built-in "empty" or "marker" roles.
2. **Role defines behavior** — roles include AI, sensors, combat, movement. Even an empty role would initialize NPC subsystems.
3. **No AI suppression API** — you can't spawn an NPC and tell it "do nothing." The NPC system will tick it every frame.
4. **Model requirement for visual effects** — without a `ModelComponent`, the client has no geometry to render or attach particles to.
5. **Heavyweight** — each NPC allocates sensors, decision makers, motion controllers. Spawning one per aimed-at-block per player is expensive.

### Could You Pre-Register a Custom Empty Role?

In theory, you could create a `.role` asset file with:
- No sensors, no decision makers, no abilities
- An invisible model (zero-size or transparent)
- No collision

But this still:
- Initializes the full NPC pipeline
- Requires a custom role asset to be loaded
- Doesn't solve the particle-doesn't-die-on-despawn problem

**Verdict: Not recommended.** The NPC system is designed for creatures and characters, not invisible particle anchors.

---

## 6. Recommended Approach: Continue Using Particle Heartbeat

The `StencilBookParticleLoop` already implements the correct pattern:

```
┌──────────────────────────────────────────────────────┐
│                Particle Heartbeat                     │
│                                                       │
│  Every 100ms:                                         │
│  1. Check if player holds Stencil Book              │
│  2. Raycast to get aimed block                        │
│  3. Check if block has a recipe                       │
│  4. Spawn short-lived particle at block center        │
│                                                       │
│  When stopped (unequip/look away):                    │
│  → Last particle dies within particle lifespan        │
│  → Effect: near-instant disappearance (~150ms)        │
└──────────────────────────────────────────────────────┘
```

### Advantages Over Entity Approach

| Aspect | Particle Heartbeat | Entity + Particle |
|--------|-------------------|-------------------|
| Spawn complexity | `ParticleUtil.spawnParticleEffect()` — one line | Entity registration + spawn + component setup |
| Disappearance | ~150ms after stop (with short-lived particle) | Particle continues playing after entity removed |
| Performance | 1 small packet per tick per player | Entity creation/destruction + NPC pipeline overhead |
| Position tracking | Follows aim naturally (respawn at new position) | Must move entity every tick (transform update) |
| Cleanup | Stop scheduling → particles die naturally | Must track + remove entity + handle edge cases |
| Already implemented | ✅ `StencilBookParticleLoop.java` | ❌ Would need new code |

### If "Instant" Disappearance is Critical

The 150ms fade with the heartbeat pattern is near-instant. If truly zero-frame disappearance is needed:
- Reduce particle lifespan to 100ms (matches tick interval — at most 1 generation alive)
- Or accept that server-side approaches inherently have 1-tick latency

---

## 7. Protocol Packets Reference

| Packet | ID | Fields | Purpose |
|--------|----|--------|---------|
| `SpawnParticleSystem` | 152 | position, rotation, particle system ID | World-space particle |
| `SpawnBlockParticleSystem` | — | position, block type | Block-material particle |
| `SpawnModelParticles` | 165 | entityId, ModelParticle[] | Entity-model-attached particle |
| `UpdateParticleSystems` | — | asset data | Hot-reload particle definitions |
| `UpdateParticleSpawners` | — | asset data | Hot-reload spawner definitions |

**No despawn/cancel packets exist for any of these.**

---

## Gotchas

1. **`World.spawnEntity()` is deprecated** — the engine is moving toward holder/store-based entity creation via `store.addEntity(holder, reason)`
2. **`EntityModule.isKnown()` gate** — you cannot spawn entities that aren't registered through the entity registry
3. **NPC roles are asset-defined** — creating a "dummy" role requires a role asset file, not just Java code
4. **Model particles require a model** — `SpawnModelParticles` targets entity model nodes; no model = no attachment point
5. **World particles are fire-and-forget** — `ParticleUtil.spawnParticleEffect()` returns `void`, no handle, no cancel
6. **Entity removal broadcasts to nearby players** — despawning an entity sends a network packet, but this does NOT cancel any previously-spawned particles on the client

## See Also

- [Particle Tick Loop Research](../particles/particle-tick-loop-research.md) — particle despawning impossibility, heartbeat pattern
- [Particle Spawning API](../particles/particle-spawning-api.md) — `ParticleUtil` API reference
- [Block Selection Highlight Research](../blocks/block-selection-highlight-research.md) — alternative visual approaches
- [StencilBookParticleLoop.java](../../../src/main/java/com/UnobstructedThirdPerson/stencil/StencilBookParticleLoop.java) — current implementation
