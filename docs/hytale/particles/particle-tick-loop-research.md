---
topic: "Particle Tick Loop — Despawning, Tick Systems & Held Item Detection"
category: "Particles / ECS / Inventory"
updated: 2026-05-08
sources: ["ParticleUtil.java (decompiled)", "SpawnParticleSystem.java (protocol)", "CameraTransparencyVolumeV2.java (plugin)", "TargetUtil.java (decompiled)", "StencilRadialMenuPage.java (plugin)", "SwitchActiveSlotEvent.java (decompiled)", "InventoryPacketHandler.java (decompiled)", "Example_Simple.particlespawner (asset)", "inventory-hotbar-events.md (local docs)"]
---

# Particle Tick Loop Research: Despawning, Tick Systems & Held Item Detection

## Executive Summary

**Particles CANNOT be despawned.** There is no `DespawnParticleSystem` packet, no handle/ID returned from `spawnParticleEffect()`, and no server-side API to cancel a running particle. The recommended approach is **short-lived particles re-spawned every tick** — a "heartbeat" pattern where the effect auto-expires when the server stops sending it.

---

## 1. Despawning Particle Effects — CONFIRMED NOT POSSIBLE

### Evidence: No Return Value

All 15+ overloads of `ParticleUtil.spawnParticleEffect()` return `void`. No handle, ID, or token is returned:

```java
public static void spawnParticleEffect(@Nonnull String name, @Nonnull Vector3d position,
    @Nonnull ComponentAccessor<EntityStore> componentAccessor)
```

**Source:** [ParticleUtil.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/ParticleUtil.java)

### Evidence: No Despawn Packet

The full list of particle-related protocol packets:

| Packet | ID | Purpose |
|--------|----|---------|
| `SpawnParticleSystem` | 152 | Spawn a world particle effect |
| `SpawnBlockParticleSystem` | — | Spawn block-material particles |
| `SpawnModelParticles` | — | Spawn particles attached to entity models |
| `UpdateParticleSystems` | — | Asset update (add/modify system definitions) |
| `UpdateParticleSpawners` | — | Asset update (add/modify spawner definitions) |
| `UpdateBlockParticleSets` | — | Asset update (block-material particle mappings) |

**There is NO `DespawnParticleSystem`, `StopParticleSystem`, or `RemoveParticleSystem` packet.** The protocol has no mechanism to cancel a spawned particle.

### Evidence: No Despawn Method

Grep of entire codebase for `despawn.*particle`, `stop.*particle`, `remove.*particle` patterns: the only hits are `DespawnParticles` in `DeployableConfig` — which is a config field listing particles to play **when** an entity despawns (e.g., totem disappear effects). It does NOT despawn/cancel existing particles.

### Conclusion

Once `SpawnParticleSystem` packet is sent, the client plays the particle system to completion. The server has zero control over it after dispatch.

---

## 2. Recommended Approach: Short-Lived Particle Heartbeat

Since particles can't be cancelled, the solution is:
1. Define or use a **very short-lived particle** (~0.15-0.3 seconds)
2. **Re-spawn it every tick** at the aimed block position
3. When the player looks away or unequips the book, **stop re-spawning** → the last particle fades naturally

### Why This Works

Particle lifetime is controlled by the `.particlespawner` asset's `ParticleLifeSpan` field:

```json
// Example_Simple.particlespawner — 1 second lifetime
{
  "ParticleLifeSpan": { "Min": 1, "Max": 1 },
  "MaxConcurrentParticles": 1,
  "SpawnRate": { "Min": 6, "Max": 6 }
}
```

A custom particle spawner with `"ParticleLifeSpan": { "Min": 0.15, "Max": 0.15 }` and `"SpawnBurst": true` / `"TotalParticles": { "Min": 1, "Max": 1 }` would:
- Emit all particles instantly on spawn
- All particles die after ~150ms
- If re-spawned every 100ms, there's always exactly one generation alive → continuous visual
- When server stops spawning, the last generation dies in 150ms → clean disappearance

### Tick Rate Consideration

The `CameraTransparencyVolumeV2` scheduler uses `100ms` intervals. At 100ms tick + 150ms particle life:
- Particle spawned at t=0, alive until t=150
- Particle spawned at t=100, alive until t=250
- 50ms overlap ensures no visual gap
- Max 2 generations alive simultaneously
- When stopped: last particle dies within 150ms

### Using Existing Particles (Without Custom Assets)

If custom particle assets aren't loaded yet, `"Example_Simple"` has a 1-second lifespan. This is usable but:
- 1 second overlap means multiple generations stack up (6 particles/sec × 1 sec = ~6 concurrent)
- When stopped, the effect lingers for 1 full second
- Acceptable for prototyping; replace with a short-lived custom particle for production

---

## 3. Tick-Loop Pattern: `ScheduledFuture` + `world.execute()`

### Proven Pattern from `CameraTransparencyVolumeV2`

The codebase already uses this exact pattern — a per-player scheduled task that runs on the world thread:

```java
private ScheduledFuture<?> updateTask = null;

private void startUpdateLoop() {
    updateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
        try {
            world.execute(() -> {
                // Inside world thread — safe to access ECS components
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) return;

                Store<EntityStore> store = ref.getStore();
                // ... per-tick logic here ...
            });
        } catch (Exception e) {
            LOGGER.warning("Error in update loop: " + e.getMessage());
        }
    }, UPDATE_INTERVAL_MILLIS, UPDATE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
}

private void stopUpdateLoop() {
    if (updateTask != null) {
        updateTask.cancel(false);
        updateTask = null;
    }
}
```

**Key elements:**
- `HytaleServer.SCHEDULED_EXECUTOR` — the server's shared scheduled thread pool
- `world.execute(Runnable)` — defers execution to the world thread (required for ECS access)
- `playerRef.getReference()` — gets the player's ECS entity ref (null if disconnected)
- `ScheduledFuture.cancel(false)` — clean shutdown without interrupting running tasks

**Source:** [CameraTransparencyVolumeV2.java](../../../src/main/java/com/UnobstructedThirdPerson/camera/v2/CameraTransparencyVolumeV2.java) lines 107-135

---

## 4. Held Item Detection

### Getting the Active Hotbar Slot

```java
byte activeSlot = player.getInventory().getActiveHotbarSlot();
```

**Source:** [StencilRadialMenuPage.java](../../../src/main/java/com/UnobstructedThirdPerson/stencil/StencilRadialMenuPage.java) line 231

### Getting the ItemStack in the Active Slot

```java
ItemContainer hotbar = player.getInventory().getHotbar();
byte activeSlot = player.getInventory().getActiveHotbarSlot();
ItemStack heldItem = (activeSlot >= 0) ? hotbar.getItemStack(activeSlot) : null;
```

### Checking Item Type

```java
// By item ID (numeric)
if (heldItem != null && heldItem.getItemId() == STENCIL_BOOK_ITEM_ID) { ... }

// By item metadata (for stencil detection)
if (heldItem != null && StencilMetadata.isStencil(heldItem)) { ... }

// By item asset string ID — resolve via Item asset map
Item item = Item.getAssetMap().getAsset(heldItem.getItemId());
if (item != null && "Stencil_Book".equals(item.getId())) { ... }
```

### Getting the Player Entity in the Tick Loop

From the `world.execute()` context with a `PlayerRef`:

```java
world.execute(() -> {
    Ref<EntityStore> ref = playerRef.getReference();
    if (ref == null || !ref.isValid()) return;

    Store<EntityStore> store = ref.getStore();
    Player player = store.getComponent(ref, Player.getComponentType());
    if (player == null) return;

    byte activeSlot = player.getInventory().getActiveHotbarSlot();
    ItemStack held = (activeSlot >= 0)
        ? player.getInventory().getHotbar().getItemStack(activeSlot)
        : null;
    // ... check held item type ...
});
```

---

## 5. Aimed Block Detection Per Tick

### `TargetUtil.getTargetBlock(Ref, double, ComponentAccessor)`

```java
// From inside world.execute():
Vector3i target = TargetUtil.getTargetBlock(ref, 8.0, store);
// Returns null if not looking at any block within range
// Returns block coordinates (integer) of the aimed block
```

This performs a server-side raycast from the player's eye position along their look direction, iterating blocks until a non-air block is hit.

**Source:** [TargetUtil.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/util/TargetUtil.java) line 160

### Converting Block Coords to World Position

```java
// Center of block (for particles)
Vector3d particlePos = new Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5);

// Top of block
Vector3d topPos = new Vector3d(target.x + 0.5, target.y + 1.0, target.z + 0.5);
```

---

## 6. Per-Player State Tracking Pattern

### `ConcurrentHashMap<UUID, Instance>` Pattern

Both `CameraTransparencyVolumeV2` and `StencilVisualManager` use the same pattern:

```java
private static final Map<UUID, MySystem> INSTANCES = new ConcurrentHashMap<>();

public static void start(PlayerRef playerRef, World world) {
    UUID playerId = playerRef.getUuid();
    MySystem existing = INSTANCES.get(playerId);
    if (existing != null) {
        existing.shutdown();
    }
    MySystem instance = new MySystem(playerRef, world);
    instance.startUpdateLoop();
    INSTANCES.put(playerId, instance);
}

public static void remove(UUID playerId) {
    MySystem instance = INSTANCES.remove(playerId);
    if (instance != null) {
        instance.shutdown();
    }
}
```

### Tracked State for This Feature

```java
private Vector3i lastTargetBlock = null;  // last block particles were spawned on
private boolean wasHoldingBook = false;   // whether player was holding book last tick
```

---

## 7. Code Skeleton: Stencil Book Aim Particle System

```java
public class StencilBookAimParticleSystem {

    private static final Logger LOGGER = Logger.getLogger("StencilBookAimParticles");
    private static final long TICK_INTERVAL_MILLIS = 100; // 10 ticks/sec
    private static final String PARTICLE_EFFECT = "StencilBook_Aim_Highlight";
    // Fallback: "Example_Simple" if custom particle not yet loaded
    private static final double AIM_RANGE = 8.0;

    private static final Map<UUID, StencilBookAimParticleSystem> INSTANCES = new ConcurrentHashMap<>();

    private final PlayerRef playerRef;
    private final World world;
    private ScheduledFuture<?> updateTask = null;

    // Per-tick state
    private Vector3i lastTargetBlock = null;

    public StencilBookAimParticleSystem(PlayerRef playerRef, World world) {
        this.playerRef = playerRef;
        this.world = world;
    }

    // --- Lifecycle ---

    public static void start(PlayerRef playerRef, World world) {
        UUID playerId = playerRef.getUuid();
        StencilBookAimParticleSystem existing = INSTANCES.get(playerId);
        if (existing != null) {
            existing.shutdown();
        }
        StencilBookAimParticleSystem instance = new StencilBookAimParticleSystem(playerRef, world);
        instance.startUpdateLoop();
        INSTANCES.put(playerId, instance);
    }

    public static void remove(UUID playerId) {
        StencilBookAimParticleSystem instance = INSTANCES.remove(playerId);
        if (instance != null) {
            instance.shutdown();
        }
    }

    private void startUpdateLoop() {
        updateTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                world.execute(this::tick);
            } catch (Exception e) {
                LOGGER.warning("[AimParticles] Error: " + e.getMessage());
            }
        }, TICK_INTERVAL_MILLIS, TICK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void shutdown() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
        lastTargetBlock = null;
    }

    // --- Per-Tick Logic ---

    private void tick() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // 1. Check held item
        if (!isHoldingStencilBook(player)) {
            lastTargetBlock = null; // clear state when not holding
            return;
        }

        // 2. Raycast to aimed block
        Vector3i target = TargetUtil.getTargetBlock(ref, AIM_RANGE, store);
        if (target == null) {
            lastTargetBlock = null; // looking at sky/no block
            return;
        }

        // 3. Spawn particle at aimed block (every tick — heartbeat pattern)
        Vector3d particlePos = new Vector3d(
            target.x + 0.5, target.y + 1.0, target.z + 0.5
        );
        ParticleUtil.spawnParticleEffect(PARTICLE_EFFECT, particlePos, store);

        lastTargetBlock = target;
    }

    private boolean isHoldingStencilBook(Player player) {
        byte activeSlot = player.getInventory().getActiveHotbarSlot();
        if (activeSlot < 0) return false;

        ItemStack held = player.getInventory().getHotbar().getItemStack(activeSlot);
        if (held == null) return false;

        // Option A: Check by item ID
        // return held.getItemId() == STENCIL_BOOK_ITEM_ID;

        // Option B: Check by asset string ID
        Item item = Item.getAssetMap().getAsset(held.getItemId());
        return item != null && "Stencil_Book".equals(item.getId());
    }

    // --- Integration Points ---
    // Call start() from onPlayerReady()
    // Call remove() from onPlayerDisconnect()
}
```

---

## 8. Custom Short-Lived Particle Asset (Recommended)

Create a custom `.particlespawner` for the aim highlight:

```json
// StencilBook_Aim_Highlight.particlespawner
{
  "ParticleRotationInfluence": "Billboard",
  "MaxConcurrentParticles": 4,
  "RenderMode": "Erosion",
  "SpawnBurst": true,
  "TotalParticles": { "Min": 4, "Max": 4 },
  "ParticleLifeSpan": { "Min": 0.15, "Max": 0.2 },
  "Particle": {
    "Texture": "Particles/Textures/Fire/Ember.png",
    "FrameSize": { "Width": 32, "Height": 32 },
    "ScaleRatioConstraint": "OneToOne",
    "UVOption": "None",
    "InitialAnimationFrame": {
      "Opacity": 0.6,
      "Scale": 0.3,
      "FrameIndex": { "Min": 0, "Max": 0 }
    },
    "Animation": {
      "0": { "Opacity": 0.6, "Scale": 0.3 },
      "1": { "Opacity": 0, "Scale": 0.5 }
    }
  }
}
```

```json
// StencilBook_Aim_Highlight.particlesystem
{
  "Spawners": [
    { "SpawnerId": "StencilBook_Aim_Highlight", "FixedRotation": true }
  ],
  "CullDistance": 30
}
```

**Key properties:**
- `SpawnBurst: true` + `TotalParticles: 4` → all particles emit instantly
- `ParticleLifeSpan: 0.15-0.2s` → dies before next tick's spawn (100ms interval)
- Minimal overlap, clean visual

---

## 9. Decision Matrix

| Approach | Despawnable? | Visual Quality | Complexity | Network Cost |
|----------|-------------|----------------|------------|--------------|
| **Short-lived particle heartbeat** | N/A — auto-expires | Good (tunable) | Low | ~10 packets/sec/player |
| Long-lived particle + manual cancel | NOT POSSIBLE | — | — | — |
| ECS entity with particle component | Possible but heavy | Good | High | Entity spawn/despawn overhead |
| Block selection outline (`displayOutlines`) | No server control | Limited (wireframe only) | N/A | N/A |

**Recommendation: Short-lived particle heartbeat** is the only viable approach for server-controlled aim particles.

---

## 10. Gotchas

1. **Network cost**: At 10 ticks/sec, each active player generates 10 `SpawnParticleSystem` packets/sec sent to all nearby players (within 75 blocks). For single-player or small servers this is fine. For large servers, consider:
   - Using the explicit player list overload to send only to the holding player
   - Reducing tick rate to 5/sec (200ms interval) with a slightly longer particle life

2. **`world.execute()` is required**: The `ScheduledFuture` callback runs on the scheduler thread, NOT the world thread. All ECS access (`getComponent`, `TargetUtil`, `ParticleUtil`) must be inside `world.execute()`.

3. **Stale `PlayerRef`**: Always check `ref.isValid()` before accessing components. The player may have disconnected between scheduling and execution.

4. **Particle stacking**: If the tick interval is shorter than particle life, multiple generations overlap. With `Example_Simple` (1s life) at 100ms ticks, up to 10 generations stack. Use a custom short-lived particle to avoid this.

5. **Raycast accuracy**: `TargetUtil.getTargetBlock()` performs a server-side raycast using the player's head rotation. There may be slight desync with the client's visual crosshair, especially during fast mouse movement. This is inherent to server-side raycasting and acceptable for a visual indicator.

## See Also

- [Particle Spawning API](./particle-spawning-api.md) — ParticleUtil reference
- [Inventory & Hotbar Events](../inventory-hotbar-events.md) — SwitchActiveSlotEvent, ItemContainerChangeEvent
- [ECS Threading](../ecs/threading.md) — world.execute() and thread safety
