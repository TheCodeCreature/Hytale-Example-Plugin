---
topic: "Entity Tracker & Client Visibility"
category: "Entities / Networking"
updated: 2026-05-08
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/tracker/EntityTrackerSystems.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/tracker/NetworkId.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/system/NetworkSendableSpatialSystem.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/storage/EntityStore.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/EntityModule.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/npc/NPCPlugin.java (spawnEntity)"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/entity/system/ModelSystems.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/entity/Entity.java"
---

# Entity Tracker & Client Visibility

## Summary

For an entity to be visible to clients, it must enter the **entity tracker pipeline**. The gatekeeper is the `NetworkSendableSpatialSystem`, which indexes entities into a spatial structure that players query for nearby entities. **Without a `NetworkId` component, an entity is completely invisible to the tracker and will never be sent to clients.**

## Root Cause: Why Custom Entities Are Invisible

Your custom entity has:
- `TransformComponent` ✓
- `NetworkId` ✓
- `UUIDComponent` ✓
- `EffectControllerComponent` ✓
- `ModelComponent` ✓ (but at 0.001 scale — triggers LOD culling at >5.3 blocks)
- `NonSerialized` ✓
- **`Visible` ✗ — MISSING (fatal)**
- **`PersistentModel` ✗ — MISSING (recommended)**

### Two interacting failures:

1. **Without pre-added `Visible`**, QUEUE_UPDATE_GROUP systems (`LegacyEntityModel`, `TransformSystems.EntityTrackerUpdate`, `EffectControllerSystem`) never iterate the entity. The auto-management via `EnsureVisibleComponent` adds `Visible` via CommandBuffer, but the `systemIndexToArchetypeChunkIndexes` mapping for subsequent systems isn't updated within the same tick — the entity permanently misses its "newly visible" window.

2. **`Model.createScaledModel(asset, 0.001f)`** scales the bounding box to 0.001 thickness. `LegacyLODCull` removes the entity from `viewer.visible` at `distanceSq > 28.57` (~5.3 blocks), preventing `EnsureVisibleComponent` from ever adding `Visible` at range — even if the timing issue were fixed.

## The Entity Tracker Pipeline

```
1. NetworkSendableSpatialSystem (spatial indexing)
   Query: Archetype.of(TransformComponent, NetworkId)
   → Indexes entity position into SpatialStructure
   
2. CollectVisible (per-viewer, each tick)
   Query: Archetype.of(EntityViewer, TransformComponent)
   → For each viewer, queries SpatialStructure within viewRadiusBlocks
   → Populates viewer.visible set
   
3. EnsureVisibleComponent
   → For each entity in viewer.visible, ensures a Visible component exists
   
4. AddToVisible
   → Populates each entity's Visible.visibleTo map with viewer refs
   
5. QUEUE_UPDATE_GROUP (multiple systems)
   → LegacyEntityModel: queues Model updates for visible entities
   → EffectControllerSystem: queues EntityEffect updates
   → LegacyEntitySkin: queues skin updates
   → (etc.)
   
6. SendPackets
   → Builds EntityUpdates packet from viewer.updates map
   → Assigns NetworkId to sent map for new entities
   → Sends via viewer.packetReceiver.writeNoCache(packet)
```

### Critical Gate: NetworkSendableSpatialSystem

```java
// NetworkSendableSpatialSystem.java
public class NetworkSendableSpatialSystem extends SpatialSystem<EntityStore> {
    private static final Query<EntityStore> QUERY = Archetype.of(
        TransformComponent.getComponentType(), 
        NetworkId.getComponentType()          // <-- THIS IS THE GATE
    );
}
```

Only entities matching BOTH `TransformComponent` AND `NetworkId` are indexed. No `NetworkId` = not indexed = invisible.

### CollectVisible: How Players Find Entities

```java
// EntityTrackerSystems.CollectVisible.tick()
SpatialStructure<Ref<EntityStore>> spatialStructure = store
    .getResource(EntityModule.get().getNetworkSendableSpatialResourceType())
    .getSpatialStructure();
spatialStructure.collect(position, entityViewer.viewRadiusBlocks, results);
entityViewer.visible.addAll(results);
```

Players query the spatial structure within their view radius. Only spatially-indexed entities are returned.

## NetworkId Component

### What It Is

```java
public final class NetworkId implements Component<EntityStore> {
    private final int id;
    
    public NetworkId(int id) { this.id = id; }
    public int getId() { return this.id; }
}
```

A simple integer wrapper. The `id` is used in `EntityUpdates` packets to identify entities on the wire. Each entity gets a unique id per-world via `EntityStore.takeNextNetworkId()`.

### How to Get the Next Network ID

```java
// From a Store<EntityStore>:
int networkId = store.getExternalData().takeNextNetworkId();

// From a CommandBuffer<EntityStore>:
int networkId = commandBuffer.getExternalData().takeNextNetworkId();
```

`EntityStore.takeNextNetworkId()` uses an `AtomicInteger` counter, so it's thread-safe.

### NetworkIdSystem: Auto-Registration

When an entity with `NetworkId` is added to the store, `EntityStore.NetworkIdSystem` (a `RefSystem`) fires and registers the mapping:

```java
// EntityStore.NetworkIdSystem.onEntityAdded()
EntityStore entityStore = store.getExternalData();
NetworkId networkIdComponent = commandBuffer.getComponent(ref, NetworkId.getComponentType());
int networkId = networkIdComponent.getId();
if (entityStore.networkIdToRef.putIfAbsent(networkId, ref) != null) {
    // Collision — auto-assign a new one
    networkId = entityStore.takeNextNetworkId();
    commandBuffer.putComponent(ref, NetworkId.getComponentType(), new NetworkId(networkId));
    entityStore.networkIdToRef.put(networkId, ref);
}
```

So even if you pass a stale/invalid ID, the system will auto-correct it. But you MUST add the component.

### How NPCs Get NetworkId (You Don't Have This Path)

NPCs extend the legacy `Entity` class (`NPCEntity → LivingEntity → Entity`). The `EntityModule.LegacyEntityHolderSystem<T extends Entity>` fires `onEntityAdd` for any holder containing a legacy entity component:

```java
// EntityModule.LegacyEntityHolderSystem.onEntityAdd()
entityComponent.loadIntoWorld(store.getExternalData().getWorld());
// Inside loadIntoWorld():  this.networkId = world.getEntityStore().takeNextNetworkId();
holder.putComponent(NetworkId.getComponentType(), new NetworkId(entityComponent.getNetworkId()));
```

**Your custom entity has no legacy Entity component, so this path never fires.**

### How Props Get NetworkId (You Don't Have This Either)

Props (placed models via EntitySpawnPage) use `ModelSystems.AssignNetworkIdToProps`:

```java
// Query: PropComponent AND NOT NetworkId
public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store) {
    holder.addComponent(networkIdComponentType, 
        new NetworkId(store.getExternalData().takeNextNetworkId()));
}
```

**Your entity doesn't have `PropComponent`, so this path doesn't fire either.**

## Visible Component

`EntityTrackerSystems.Visible` is managed by `EnsureVisibleComponent` (auto-adds when entity appears in a viewer's spatial query) and `RemoveEmptyVisibleComponent` (auto-removes when no viewers see it).

**HOWEVER: You MUST pre-add `Visible` directly to the holder for custom plugin entities.**

The auto-management relies on archetype chunk index mappings being updated between system ticks. When `EnsureVisibleComponent` adds `Visible` via CommandBuffer.consume(), the entity moves to a new archetype. But the pre-computed `systemIndexToArchetypeChunkIndexes` for QUEUE_UPDATE_GROUP systems (like `LegacyEntityModel`) may not include the new archetype chunk in the same tick — causing the entity to miss its "newly visible" window permanently.

The proven engine pattern from `DeployablesUtils.spawnDeployable()`:
```java
holder.ensureComponent(EntityModule.get().getVisibleComponentType());
```

Pre-adding `Visible` ensures the entity is in the correct archetype from tick #1, so QUEUE_UPDATE_GROUP systems immediately find and process it.

### LOD Culling Warning

`LegacyLODCull` removes entities from `viewer.visible` based on:
```
maximumThickness < 3.5E-5 * distanceSq
```
A model at 0.001f scale has maximumThickness ≈ 0.001, which gets culled at >5.3 blocks. **Use scale 1.0 for particle-carrier entities.**

## The Fix: Add NetworkId to Your Custom Entity

### Minimum Required Components for Client Visibility

| Component | Purpose | Required? |
|-----------|---------|-----------|
| `TransformComponent` | Position for spatial indexing | **YES** |
| `NetworkId` | Wire identity + spatial index gate | **YES** |
| `Visible` | Ensures QUEUE_UPDATE systems process entity on tick 1 | **YES (pre-add)** |
| `ModelComponent` | Visual model sent to client | For model visibility |
| `PersistentModel` | Model reference for persistence/change tracking | With ModelComponent |
| `EffectControllerComponent` | Entity effects sent to client | For effects visibility |

### Code: Full Corrected Entity Creation (Proven Pattern)

Based on `DeployablesUtils.spawnDeployable()`:

```java
Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

// Position (required for spatial indexing)
holder.addComponent(TransformComponent.getComponentType(), 
    new TransformComponent(position, Vector3f.ZERO));

// Network identity (REQUIRED for client visibility)
holder.addComponent(NetworkId.getComponentType(), 
    new NetworkId(store.getExternalData().takeNextNetworkId()));

// *** CRITICAL: Pre-add Visible for immediate tracker processing ***
holder.ensureComponent(EntityModule.get().getVisibleComponentType());

// UUID  
holder.ensureComponent(UUIDComponent.getComponentType());

// Visual model (use scale 1.0 to avoid LOD culling)
ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset("NPC_Spawn_Marker");
Model model = Model.createUnitScaleModel(modelAsset);
holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));

// Effects
holder.ensureComponent(EffectControllerComponent.getComponentType());

// Non-persisted
holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
```

## Comparison: DeployablesUtils vs Current Entity Components

| Component | DeployablesUtils | Your Entity (Broken) | Required for Visibility? |
|-----------|-----------------|---------------------|--------------------------|
| TransformComponent | ✓ | ✓ | **YES** |
| NetworkId | ✓ | ✓ | **YES** |
| **Visible** | **✓ (pre-added)** | **✗ MISSING** | **YES (pre-add)** |
| ModelComponent | ✓ (1.0 scale) | ✓ (0.001 scale — LOD issue) | For model |
| PersistentModel | ✓ | ✗ | Recommended |
| EffectControllerComponent | ✓ | ✓ | For effects |
| HeadRotation | ✓ | ✗ | Optional (defaults to ZERO) |
| BoundingBox | ✓ (explicit) | ✗ (auto from tiny model) | Optional (affects LOD) |
| UUIDComponent | ✓ | ✓ | No |
| NonSerialized | ✓ | ✓ | No |

## Fallback: Manual Packet Sending

If for some reason you cannot use the entity tracker (e.g., you want to send effects without a tracked entity), you can send packets directly:

```java
// Get the player's packet handler
Player player = store.getComponent(playerRef, Player.getComponentType());
IPacketReceiver packetReceiver = player.getPacketReceiver();

// Build and send EntityUpdates manually
EntityUpdates packet = new EntityUpdates();
// ... populate packet ...
packetReceiver.writeNoCache(packet);
```

However, this is fragile — you'd need to manage entity lifecycle, network IDs, and component updates yourself. **Adding `NetworkId` to the holder is the correct fix.**

## Gotchas

- **Pre-add `Visible` to the holder.** Despite the auto-management system existing, custom plugin entities spawned via `store.addEntity()` during `world.execute()` will NOT reliably receive tracker updates without it. The proven pattern from `DeployablesUtils` always pre-adds it.
- **Do NOT use model scale < 1.0 for particle-carrier entities.** `LegacyLODCull` uses `maximumThickness < 3.5E-5 * distanceSq` — a 0.001 scale model gets culled at >5.3 blocks.
- `NetworkId` has NO default factory — calling `ensureComponent()` on it will throw `UnsupportedOperationException("Not implemented")`. You MUST use `addComponent()` with an explicit `new NetworkId(id)`.
- `EntityStore.takeNextNetworkId()` is `AtomicInteger`-based and thread-safe.
- If you accidentally use a duplicate network ID, `NetworkIdSystem` auto-reassigns it — but always use `takeNextNetworkId()` to avoid the collision path.
- The entity tracker only sends updates for entities within the viewer's `viewRadiusBlocks`. If you spawn an entity far from all players, it won't be visible until a player comes within range.
- Always add `PersistentModel` alongside `ModelComponent` — the `ModelSystems.ModelChange` system only tracks model changes on entities with `PersistentModel`.

## See Also

- [Entity Spawning & Particles Lifecycle](./spawning-particles-lifecycle.md)
- [Entity-Scoped Particles](./entity-scoped-particles.md)
