---
topic: "BoundingBox Removal, Zeroing, and the Intangible Component"
category: "entities"
updated: 2026-05-22
sources:
  - "Decompiled BoundingBox.java (component)"
  - "Decompiled BlockEntitySystems.java (BlockEntitySetupSystem)"
  - "Decompiled Store.java (removeComponent, removeComponentIfExists)"
  - "Decompiled Holder.java (removeComponent)"
  - "Decompiled EntityModule.java (component registration, spatial systems)"
  - "Decompiled Intangible.java (component)"
  - "Decompiled IntangibleSystems.java (EntityTrackerAddAndRemove, EntityTrackerUpdate)"
  - "Decompiled TangiableEntitySpatialSystem.java (Query.not(Intangible))"
  - "Decompiled EntitySpatialSystem.java (Query.not(Intangible))"
  - "Decompiled TargetUtil.java (isHitByRay, getTargetEntity)"
  - "Decompiled Box.java (min/max fields, CODEC validator)"
  - "Decompiled RaycastSelector.java (selectNearbyEntities → entitySpatialResourceType)"
  - "Decompiled Selector.java (selectNearbyEntities implementation)"
---

# BoundingBox Removal, Zeroing, and the Intangible Component

## Summary

A `BlockEntity`'s `BoundingBox` cannot be effectively removed or zeroed to prevent client raycast interception — because **BoundingBox is not network-synced**. The client derives its own bounding box from the block type/model. The correct solution is the **`Intangible` component**, which is a built-in engine flag that removes entities from all spatial indices on the server AND is network-synced to the client via `ComponentUpdateType.Intangible`.

## TL;DR — Use `Intangible`

```java
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;

// After spawning the entity:
store.ensureComponent(entityRef, Intangible.getComponentType());
```

This makes the entity invisible to raycasts on both server and client while preserving all visual rendering.

---

## Q1: Can We Remove BoundingBox After Entity Creation?

### Answer: Yes, the API exists — but it won't help.

**The API exists.** `Store<EntityStore>` provides three methods:

```java
// Throws if component doesn't exist
store.removeComponent(ref, BoundingBox.getComponentType());

// Safe version — returns false if absent
store.removeComponentIfExists(ref, BoundingBox.getComponentType());

// Alias for removeComponentIfExists
store.tryRemoveComponent(ref, BoundingBox.getComponentType());
```

**How it works internally:**
- `removeComponent()` removes the entity from its current `ArchetypeChunk`
- Creates a new `Holder` without the BoundingBox component
- Moves the entity to a new (or existing) `ArchetypeChunk` matching the reduced archetype
- Fires `RefChangeSystem.onComponentRemoved()` for any registered systems
- Processes any queued commands via `CommandBuffer`

**Why it doesn't solve the problem:**

BoundingBox is registered **without a name or codec**:

```java
// EntityModule.java line 306
this.boundingBoxComponentType = entityStoreRegistry.registerComponent(BoundingBox.class, BoundingBox::new);
```

Compare with network-synced components:
```java
// These have names + codecs → synced to client
entityStoreRegistry.registerComponent(Velocity.class, "Velocity", Velocity.CODEC);
entityStoreRegistry.registerComponent(TransformComponent.class, "Transform", TransformComponent.CODEC);
entityStoreRegistry.registerComponent(Intangible.class, "Intangible", Intangible.CODEC);
```

**No name + no codec = not serialized over the network.** The client never receives BoundingBox data from the server. Removing it server-side has **zero effect** on client raycasts.

### Side effects of removal on the server

Removing BoundingBox would remove the entity from `TangiableEntitySpatialSystem`'s spatial index (since its query requires `BoundingBox`), which would prevent server-side collision detection. But this is moot because:
1. The client raycast problem remains
2. `EntitySpatialSystem` (used by `TargetUtil.getTargetEntity()`) does NOT require BoundingBox in its query — it only requires `Transform` and `NOT(Intangible)` and `NOT(Player)`

---

## Q2: Can We Set BoundingBox to Zero Size?

### Answer: Technically yes, but it won't affect the client.

**The API:**
```java
BoundingBox bb = store.getComponent(ref, BoundingBox.getComponentType());
bb.setBoundingBox(new Box(0, 0, 0, 0, 0, 0));  // zero-size box
```

`setBoundingBox()` calls `this.boundingBox.assign(boundingBox)` — no runtime validation. The `Box.CODEC` validator that rejects width/height/depth ≤ 0 only runs during asset deserialization, not runtime mutation.

**Box fields:**
```java
public class Box implements Shape {
    public final Vector3d min = new Vector3d();  // mutable
    public final Vector3d max = new Vector3d();  // mutable
}
```

You could also use `Box.setEmpty()` which sets min to `Double.MAX_VALUE` and max to `-Double.MAX_VALUE`.

**However:** For the same reason as Q1 — BoundingBox is not network-synced. The client never receives this change. Zeroing the server's BoundingBox would prevent server-side `CollisionMath.intersectRayAABB()` from matching, but the client would still raycast against its own bounding box derived from the block type.

### What about `CollisionMath.intersectRayAABB()` with a zero box?

A zero-size box where min == max would likely return `false` for any ray intersection because the slab method would produce degenerate t-values. So on the **server** it would effectively make the entity unhittable. But again, irrelevant for client raycasts.

---

## Q3: Can We Pre-empt BlockEntitySetupSystem?

### Answer: No. It unconditionally overwrites.

```java
// BlockEntitySetupSystem.onEntityAdd()
BoundingBox boundingBoxComponent = blockEntityComponent.createBoundingBoxComponent();
if (boundingBoxComponent == null) {
    boundingBoxComponent = new BoundingBox(Box.horizontallyCentered(1.0, 1.0, 1.0));
}
holder.putComponent(BoundingBox.getComponentType(), boundingBoxComponent);  // always writes
```

`holder.putComponent()` replaces any existing component. There is no "if BoundingBox already exists, skip" check. Even if you add a zero-size BoundingBox to the holder before `addEntity()`, the setup system will overwrite it with the block type's collision hitbox.

---

## Q4: Does the Client Use the Server's BoundingBox?

### Answer: No. The client derives its own.

**Evidence:**

1. **BoundingBox is not network-synced** (no name, no codec in `registerComponent()`)
2. **No `ComponentUpdateType.BoundingBox` exists** — there is no network update path for bounding box changes
3. **The entity tracker (`BlockEntityTrackerSystem`) never queues BoundingBox updates** — it only sends `ComponentUpdate` for `BlockEntity` changes (block type, state), not BoundingBox
4. **`BlockEntity` IS network-synced** (`"BlockEntity"`, `BlockEntity.CODEC`) — the client receives the block type key, and derives the visual model AND its own bounding box from the block type's hitbox definition

**Conclusion:** The client performs its own bounding box computation from the `BlockType`'s hitbox asset, just as `BlockEntity.createBoundingBoxComponent()` does on the server. Server-side BoundingBox modifications are invisible to the client.

---

## Q5: The `Intangible` Component — The Correct Solution

### What is Intangible?

A singleton marker component that makes an entity pass-through for targeting and collision on both server and client.

```java
// Intangible.java
public class Intangible implements Component<EntityStore> {
    public static final Intangible INSTANCE = new Intangible();
    public static final BuilderCodec<Intangible> CODEC = BuilderCodec.builder(
        Intangible.class, () -> INSTANCE).build();

    public static ComponentType<EntityStore, Intangible> getComponentType() {
        return EntityModule.get().getIntangibleComponentType();
    }
}
```

### Why it works

#### Server-side: Excluded from ALL spatial indices

**`EntitySpatialSystem`** — used by `TargetUtil.getTargetEntity()`, `RaycastSelector`, `Selector.selectNearbyEntities()`:
```java
// EntitySpatialSystem.java
public static final Query<EntityStore> QUERY = Query.and(
    TransformComponent.getComponentType(),
    Query.not(Intangible.getComponentType()),  // ← EXCLUDED
    Query.not(Player.getComponentType())
);
```

**`TangiableEntitySpatialSystem`** — used by `EntityRefCollisionProvider`, `StandardPhysicsTickSystem`:
```java
// TangiableEntitySpatialSystem.java
private static final Query<EntityStore> QUERY = Query.and(
    TransformComponent.getComponentType(),
    BoundingBox.getComponentType(),
    Query.not(Intangible.getComponentType())  // ← EXCLUDED
);
```

With `Intangible` added, the entity is not indexed in either spatial tree. `TargetUtil.getTargetEntity()` will never find it. `RaycastSelector` will never hit it. Physical collisions will ignore it.

#### Client-side: Synced via ComponentUpdateType.Intangible

`Intangible` is registered with a name and codec:
```java
// EntityModule.java
this.intangibleComponentType = entityStoreRegistry.registerComponent(
    Intangible.class, "Intangible", Intangible.CODEC);
```

`IntangibleSystems.EntityTrackerAddAndRemove` sends updates to all viewers:
```java
// When Intangible is added:
public void onComponentAdded(...) {
    commandBuffer.getResource(QueueResource.getResourceType()).queue.add(ref);
}

// EntityTrackerUpdate then sends:
ComponentUpdate update = new ComponentUpdate();
update.type = ComponentUpdateType.Intangible;
viewer.queueUpdate(ref, update);
```

When Intangible is removed, it sends a removal update:
```java
public void onComponentRemoved(...) {
    viewer.queueRemove(ref, ComponentUpdateType.Intangible);
}
```

The client receives `ComponentUpdateType.Intangible` and (based on the engine's design pattern) skips the entity during its own raycasting. This is the same client that performs the raycast that populates `WorldInteraction.entityId` — so with Intangible, the entity will be skipped and the block behind it will be targeted instead.

#### Visuals: Unaffected

`Intangible` is a targeting/collision flag. It does NOT affect:
- Entity tracker visibility (`Visible` component)
- Rendering (model, effects, particles)
- Entity existence or lifecycle

The entity remains visible to players — it just can't be targeted or collided with.

### Usage in engine

The engine uses `Intangible` in exactly these scenarios:

1. **Spawn markers** — `SpawnMarkerSystems` adds `Intangible` to spawn marker entities so they don't interfere with gameplay:
   ```java
   holder.ensureComponent(Intangible.getComponentType());
   ```

2. **Projectiles** — `LaunchProjectileInteraction` adds `Intangible` to newly spawned projectiles (likely to prevent immediate self-collision):
   ```java
   holder.ensureComponent(Intangible.getComponentType());
   ```

3. **Debug command** — `/entity intangible` toggles Intangible on any entity:
   ```java
   // EntityIntangibleCommand
   store.ensureComponent(entity, Intangible.getComponentType());  // add
   store.tryRemoveComponent(entity, Intangible.getComponentType()); // remove
   ```

### How to apply

```java
// In your entity spawn code, after addEntity():
world.execute(store -> {
    store.ensureComponent(entityRef, Intangible.getComponentType());
});
```

Or in the holder before `addEntity()`:
```java
holder.ensureComponent(Intangible.getComponentType());
store.addEntity(holder);
```

Since `BlockEntitySetupSystem.onEntityAdd()` does NOT touch `Intangible`, adding it to the holder before spawn is safe — the setup system will still add BoundingBox (which is fine for server-side spatial indexing purposes), but the `Intangible` flag will prevent the entity from being indexed in the spatial trees regardless.

---

## Summary Table

| Approach | Server Effect | Client Effect | Verdict |
|----------|--------------|---------------|---------|
| Remove BoundingBox via `store.removeComponent()` | Removes from tangible spatial index | None (not synced) | **Won't work** |
| Set BoundingBox to zero/empty | Server raycast won't match | None (not synced) | **Won't work** |
| Pre-empt with zero BoundingBox before addEntity | Overwritten by BlockEntitySetupSystem | None (not synced) | **Won't work** |
| Add `Intangible` component | Excluded from all spatial indices | Client skips in raycast | **Works** |
| Remove BlockEntity (use effect-only entity) | No BoundingBox auto-added | No block model rendered | **Works but loses visual** |

## Diagram

```
Player looks at block with highlight entity (Intangible applied)
         │
         ├─── CLIENT RAYCAST ──────────────────────────────┐
         │    1. Entity has ComponentUpdateType.Intangible  │
         │    2. Client skips entity in its raycast         │
         │    3. Ray continues to hit the underlying block  │
         │    4. WorldInteraction.entityId = -1 (no entity) │
         │    5. WorldInteraction.blockPosition = target    │
         │                                                  │
         │    Result: Block interactions WORK normally      │
         └──────────────────────────────────────────────────┘
         │
         ├─── SERVER SPATIAL INDEX ────────────────────────┐
         │    EntitySpatialSystem query:                    │
         │      Transform AND NOT(Intangible) AND NOT(Player)
         │    → Entity excluded from spatial tree           │
         │    → TargetUtil.getTargetEntity() can't find it  │
         │    → RaycastSelector can't hit it                │
         │                                                  │
         │    TangiableEntitySpatialSystem query:           │
         │      Transform AND BoundingBox AND NOT(Intangible)
         │    → Entity excluded from collision tree         │
         └──────────────────────────────────────────────────┘
         │
         ├─── VISUAL RENDERING ────────────────────────────┐
         │    EntityTracker still tracks entity (Visible)   │
         │    BlockEntity still renders block model         │
         │    EffectController still plays particles        │
         │    EntityScaleComponent still applies scale      │
         │                                                  │
         │    Result: Visual highlight UNCHANGED            │
         └──────────────────────────────────────────────────┘
```

## See Also

- [BlockEntity Interaction Interception](./block-entity-interaction-interception.md) — original problem analysis
- [Entity Tracker Visibility](./entity-tracker-visibility.md)
- [Server/Client Boundary](../server-client-boundary.md)
