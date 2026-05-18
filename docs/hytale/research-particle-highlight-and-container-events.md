---
topic: "Particle-Only Highlights & ItemContainer Event Lifecycle"
category: "Plugin API Research"
updated: 2025-05-18
sources: ["decompiled ParticleUtil.java", "decompiled EffectControllerComponent.java", "decompiled ItemContainer.java", "decompiled Inventory.java", "decompiled SyncEventBusRegistry.java", "decompiled Registration.java", "decompiled Universe.java", "decompiled LivingEntity.java", "decompiled SpawnParticleSystem.java"]
---

# Research: Particle-Only Block Highlight & ItemContainer Event Lifecycle

---

## Question 1: Particle-Only Block Highlight Alternative

### Summary

The Hytale server **does** support spawning particle effects at arbitrary world positions **without** creating entities. The `ParticleUtil` class sends a `SpawnParticleSystem` packet directly to nearby players — no entity creation, no ECS registration, no tracker overhead.

---

### 1. Does the API support spawning particles WITHOUT a full entity?

**YES.** `ParticleUtil.spawnParticleEffect()` constructs a `SpawnParticleSystem` packet and sends it directly to player connections. No entity is involved.

**Evidence** — `ParticleUtil.java` line ~248:
```java
SpawnParticleSystem packet = new SpawnParticleSystem(name, new Position(x, y, z), rotation, scale, color);
// ...iterates playerRefs and sends via:
playerRefComponent.getPacketHandler().writeNoCache(packet);
```

The simplest overload:
```java
ParticleUtil.spawnParticleEffect(String name, Vector3d position, ComponentAccessor<EntityStore> componentAccessor)
```
This auto-collects nearby players within 75 blocks via spatial query, then sends the packet.

There are also overloads accepting explicit `List<Ref<EntityStore>> playerRefs` for targeted sends (single-player only).

---

### 2. Can `EntityEffect` be applied to a position rather than an entity ref?

**NO.** `EffectControllerComponent.addEffect()` always requires a `Ref<EntityStore> ownerRef` — the entity that "owns" the effect. Effects are entity-bound by design (they modify stat modifiers, models, etc.).

However, `EntityEffect` and "particle effects" are different concepts:
- **`EntityEffect`** = a status effect applied to an entity (e.g., Burn, Poison) — requires entity
- **Particle system** = a visual-only effect at a position — uses `ParticleUtil` / `SpawnParticleSystem` packet

For visual highlights, you want **particle systems**, not entity effects.

---

### 3. Is there a packet-only visual effect approach?

**YES.** The `SpawnParticleSystem` packet (ID 152) is exactly this:

```java
public class SpawnParticleSystem implements Packet {
    public String particleSystemId;   // e.g., "Drop_Uncommon", "BlockPlaceFail"
    public Position position;          // world coordinates
    public Direction rotation;         // optional rotation
    public float scale;                // size multiplier
    public Color color;                // optional tint
}
```

To send to a single player:
```java
PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
SpawnParticleSystem packet = new SpawnParticleSystem("Drop_Uncommon", new Position(x, y, z), null, 1.0f, null);
playerRefComponent.getPacketHandler().writeNoCache(packet);
```

**No entity, no ECS, no tracker.** Client renders the particle system at the position and auto-expires.

---

### 4. Can we use `addEffect()` with longer duration + only respawn on target change?

This is the approach currently used. The code already does this optimization:
```java
if (target.equals(lastTargetBlock) && activeEntity != null && activeEntity.isValid()) {
    // Entity already exists at this target — nothing to do
    return;
}
```

But the current effect duration is `UPDATE_INTERVAL_MILLIS` (100ms), which means on a stable target the effect visually expires between ticks if there's any delay. Extending to e.g., 2000ms is safe with `OverlapBehavior.OVERWRITE` — if the player moves to a new target, the old entity is removed anyway.

However, this still carries the full entity overhead. The `ParticleUtil` approach eliminates it entirely.

---

### 5. What is the overhead of `newHolder()` + `addEntity()` + `removeEntity()`?

**High for repeated use.** Based on the decompiled code:

| Operation | What happens |
|-----------|-------------|
| `EntityStore.REGISTRY.newHolder()` | Allocates a new `Holder` object, initializes component storage array |
| `holder.addComponent(...)` × 7 | Each call adds to the holder's internal map |
| `store.addEntity(holder, AddReason.SPAWN)` | Assigns archetype, inserts into chunk, registers with entity tracker, triggers spatial indexing, sends spawn packet to nearby clients |
| `store.removeEntity(ref, RemoveReason.REMOVE)` | Removes from archetype chunk, unregisters from tracker, triggers remove packet to clients |

The entity tracker registration and network sync are the expensive parts. Every `addEntity` causes a network entity spawn packet to all tracking players. Every `removeEntity` causes a despawn packet.

**This is NOT designed for high-frequency use.** It's designed for spawning persistent game entities (NPCs, items, blocks). Using it at 10Hz per player creates significant GC pressure and unnecessary network traffic.

---

### Recommendation

**Replace the BlockEntity approach with direct `SpawnParticleSystem` packets.**

```java
// Instead of spawning a full entity with 7 components:
world.execute(() -> {
    Ref<EntityStore> playerEntityRef = playerRef.getReference();
    Store<EntityStore> store = playerEntityRef.getStore();
    
    // Send particle directly to this player only
    SpawnParticleSystem packet = new SpawnParticleSystem(
        affordable ? "Drop_Uncommon" : "BlockPlaceFail",
        new Position(target.x + 0.5, target.y + 0.5, target.z + 0.5),
        null, 2.1f, null
    );
    playerRef.getPacketHandler().writeNoCache(packet);
});
```

**Advantages:**
- Zero entity allocation
- Zero ECS overhead
- Zero tracker registration
- Zero archetype management
- Single UDP packet per update
- Client-side rendering only — auto-expires

**Limitation:** Particle systems are fire-and-forget. The client plays the particle animation once and it's done — there's no "persistent highlight" unless you re-send periodically. But sending a packet every 500ms–1000ms is vastly cheaper than spawning/removing entities every 100ms.

**Alternative: Keep entity, but only spawn once per target block.** If you need the persistent BlockEntity visual (renders as the actual block model with textures, which particles can't do), keep the entity approach but:
1. Set effect duration to infinite (`addInfiniteEffect()`) or very long (10000ms)
2. Only spawn when target changes (already done)
3. Reduce update loop to 200-500ms (just for stale target detection)

---

### Risk Assessment

| Approach | Confidence | Risk |
|----------|-----------|------|
| `ParticleUtil` / direct packet | **High** | Well-documented, heavily used throughout engine (20+ call sites in decompiled code) |
| Single-player packet send | **High** | `playerRef.getPacketHandler().writeNoCache(packet)` is the standard pattern |
| Keep entity + infinite effect | **Medium** | Works but still carries entity overhead; visual may differ from particles |

---

## Question 2: ItemContainer.registerChangeEvent() Lifecycle

### Summary

`registerChangeEvent()` **does** return an `EventRegistration` handle that supports `unregister()`. The Hytale engine itself uses this pattern (see `Inventory.unregister()`). Additionally, containers are garbage-collected with the player entity, so even without explicit unregistration, listeners don't leak permanently — but they do remain active during the session.

---

### 1. Does `registerChangeEvent()` return a handle for unregistration?

**YES.** It returns `EventRegistration` (extends `Registration`).

**Evidence** — `ItemContainer.java` line ~106:
```java
public EventRegistration registerChangeEvent(@Nonnull Consumer<ItemContainer.ItemContainerChangeEvent> consumer) {
    return this.registerChangeEvent((short)0, consumer);
}

public EventRegistration registerChangeEvent(short priority, @Nonnull Consumer<ItemContainer.ItemContainerChangeEvent> consumer) {
    return this.externalChangeEventRegistry.register(priority, null, consumer);
}
```

The `Registration` base class:
```java
public class Registration {
    protected final BooleanSupplier isEnabled;
    protected final Runnable unregister;
    private boolean registered = true;

    public void unregister() {
        if (this.registered && this.isEnabled.getAsBoolean()) {
            this.unregister.run();  // Calls SyncEventBusRegistry.unregister() 
        }
        this.registered = false;
    }
}
```

The `SyncEventBusRegistry.unregister()` method removes the consumer from the internal map:
```java
private void unregister(@Nullable KeyType key, @Nonnull SyncEventConsumer<EventType> consumer) {
    KeyType k = (KeyType)(key != null ? key : NULL);
    SyncEventConsumerMap<EventType> eventMap = this.map.get(k);
    if (eventMap != null && !eventMap.remove(consumer)) {
        throw new IllegalArgumentException(String.valueOf(consumer));
    }
}
```

---

### 2. What is the lifecycle of `ItemContainer` instances?

**Containers live as long as the `Inventory`, which lives as long as the `LivingEntity`/`Player`.**

Lifecycle chain:
1. `Player` component is created → creates `Inventory` → creates `SimpleItemContainer` instances
2. `Player.remove()` is called on disconnect → entity is removed from `EntityStore`
3. `LivingEntity.setInventory()` calls `this.inventory.unregister()` when replacing (but not on final removal)
4. Once the `Player` component and `Inventory` are unreferenced, GC collects them along with all `ItemContainer` instances

**Evidence** — `LivingEntity.java` line ~116:
```java
if (this.inventory != null) {
    this.inventory.unregister();
}
```

`Inventory.unregister()` nulls out the entity reference and calls `unregister()` on each stored `EventRegistration`:
```java
public void unregister() {
    this.entity = null;
    if (this.storageChange != null) { this.storageChange.unregister(); this.storageChange = null; }
    if (this.armorChange != null) { this.armorChange.unregister(); this.armorChange = null; }
    if (this.hotbarChange != null) { this.hotbarChange.unregister(); this.hotbarChange = null; }
    if (this.utilityChange != null) { this.utilityChange.unregister(); this.utilityChange = null; }
    if (this.toolChange != null) { this.toolChange.unregister(); this.toolChange = null; }
    this.unregisterBackpackChange();
}
```

---

### 3. Is there a `removeChangeEvent()` method?

**No dedicated method by that name.** The pattern is:
1. Store the `EventRegistration` returned by `registerChangeEvent()`
2. Call `eventRegistration.unregister()` when done

This is the canonical Hytale pattern used in `Inventory.registerChangeEvents()` / `Inventory.unregister()`.

---

### 4. If containers are GC'd with the player, is this a real leak?

**Partially correct — it's not a permanent memory leak, but it IS a behavioral issue during the session.**

- **After disconnect:** The `Player` entity is removed from the `EntityStore`. The `Inventory` and its containers become eligible for GC. Your lambda listeners are collected along with the containers. **No permanent leak.**
- **During the session:** Your lambdas capture `playerRef` and `player` references. They remain active for the entire session even if the player stops using stencils. Every hotbar/backpack/storage change triggers your listeners unnecessarily.
- **If the player reconnects** (same server, no restart): A fresh `Player`/`Inventory` is created. Your old registrations died with the old containers. `StencilSyncSystem.register()` runs again on the new player instance. **No accumulation across sessions.**

**Verdict:** Not a memory leak, but an unnecessary CPU cost (executing stencil-restoration logic on every inventory change for the entire session, even after the player puts away stencils).

---

### 5. Engine examples of change event registration/deregistration patterns

The canonical pattern from `Inventory.java`:

```java
// Registration — store the handle
@Nullable private EventRegistration storageChange;
@Nullable private EventRegistration hotbarChange;

protected void registerChangeEvents() {
    this.storageChange = this.storage.registerChangeEvent(e -> { ... });
    this.hotbarChange = this.hotbar.registerChangeEvent(e -> { ... });
}

// Deregistration — call unregister() on the handle
public void unregister() {
    if (this.storageChange != null) {
        this.storageChange.unregister();
        this.storageChange = null;
    }
    if (this.hotbarChange != null) {
        this.hotbarChange.unregister();
        this.hotbarChange = null;
    }
}
```

Also, `Inventory.registerBackpackListener()` shows re-registration after backpack swap:
```java
private void registerBackpackListener() {
    this.unregisterBackpackChange();  // Remove old listener
    this.backpackChange = this.backpack.registerChangeEvent(e -> { ... });  // Register new
}
```

---

### Recommendation

**Store the `EventRegistration` handles and call `unregister()` on player disconnect.**

```java
public final class StencilSyncSystem {
    // Change from Map<UUID, Boolean> to Map<UUID, List<EventRegistration>>
    private static final ConcurrentHashMap<UUID, List<EventRegistration>> registeredPlayers = new ConcurrentHashMap<>();

    public static void register(PlayerRef playerRef, Player player) {
        UUID uuid = playerRef.getUuid();
        if (registeredPlayers.containsKey(uuid)) return;

        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        
        List<EventRegistration> registrations = new ArrayList<>(3);
        registrations.add(hotbar.registerChangeEvent(event -> {
            restoreStencils(hotbar);
            StencilVisualManager.refreshAffordability(playerRef, player);
        }));
        registrations.add(inventory.getBackpack().registerChangeEvent(event ->
                StencilVisualManager.refreshAffordability(playerRef, player)));
        registrations.add(inventory.getStorage().registerChangeEvent(event ->
                StencilVisualManager.refreshAffordability(playerRef, player)));

        registeredPlayers.put(uuid, registrations);
    }

    public static void unregister(UUID uuid) {
        List<EventRegistration> registrations = registeredPlayers.remove(uuid);
        if (registrations != null) {
            registrations.forEach(EventRegistration::unregister);
        }
    }
}
```

---

### Risk Assessment

| Approach | Confidence | Risk |
|----------|-----------|------|
| Store `EventRegistration` + call `unregister()` | **High** | Exact pattern used by the engine's own `Inventory` class |
| Current approach (no unregister) | **Low risk** | Not a memory leak since containers are GC'd with player, but wastes CPU |
| "Do nothing" is acceptable if stencils are always active | **Medium** | Depends on whether stencil restoration should always run |

---

## Cross-Reference Summary

| Finding | Status | Recommended Action |
|---------|--------|-------------------|
| #5 — Full BlockEntity per 100ms | **Confirmed expensive** | Replace with `SpawnParticleSystem` packet OR keep entity with long-duration/infinite effect |
| #9 — Listeners never unregister | **Confirmed fixable** | Store `EventRegistration` handles, call `unregister()` in disconnect handler |
