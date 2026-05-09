---
topic: "Particle Spawning API"
category: "Particles"
updated: 2025-05-08
sources: ["ParticleUtil.java (decompiled)", "SpawnParticleSystem.java (protocol)", "InteractionConfiguration.java (decompiled)", "BlockHarvestUtils.java (decompiled)", "ActionSpawnParticles.java (decompiled)", "ParticleSpawnCommand.java (decompiled)", "VoidSpawnerSystems.java (decompiled)", "Reference Assets/Server/Particles/ (asset files)"]
---

# Particle Spawning API

## Summary

Hytale has a fully functional server-side particle API via `ParticleUtil`. The server sends a `SpawnParticleSystem` packet (ID 152) to nearby clients, which renders the particle system client-side. Particle systems are defined in `.particlesystem` and `.particlespawner` JSON asset files. Server plugins can spawn any registered particle system at arbitrary world positions.

## 1. Confirmed: `ParticleUtil` — The Primary API

**Source:** [ParticleUtil.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/ParticleUtil.java) (decompiled)

`ParticleUtil` is a static utility class in `com.hypixel.hytale.server.core.universe.world`. It has ~15 overloads of `spawnParticleEffect()`.

### Simplest Overload (Auto-collects nearby players)

```java
ParticleUtil.spawnParticleEffect(
    @Nonnull String name,           // Particle system ID (e.g. "Example_Simple", "Splash")
    @Nonnull Vector3d position,     // World position to spawn at
    @Nonnull ComponentAccessor<EntityStore> componentAccessor  // Store or CommandBuffer
);
```

This overload **automatically collects all players within 75 blocks** of the position using the player spatial resource, then sends each of them a `SpawnParticleSystem` packet. `ComponentAccessor<EntityStore>` is the supertype of both `Store<EntityStore>` and `CommandBuffer<EntityStore>`.

### Other Useful Overloads

```java
// With explicit player list (skips spatial lookup)
ParticleUtil.spawnParticleEffect(String name, Vector3d position,
    List<Ref<EntityStore>> playerRefs, ComponentAccessor<EntityStore> componentAccessor);

// With rotation
ParticleUtil.spawnParticleEffect(String name, Vector3d position,
    Vector3f rotation, List<Ref<EntityStore>> playerRefs, ComponentAccessor<EntityStore> componentAccessor);

// With scale and color
ParticleUtil.spawnParticleEffect(String name, Vector3d position,
    float yaw, float pitch, float roll, float scale, Color color,
    List<Ref<EntityStore>> playerRefs, ComponentAccessor<EntityStore> componentAccessor);

// With source entity (excluded from receiving the particle)
ParticleUtil.spawnParticleEffect(String name, Vector3d position,
    Ref<EntityStore> sourceRef, List<Ref<EntityStore>> playerRefs, ComponentAccessor<EntityStore> componentAccessor);
```

### Underlying Mechanism

All overloads ultimately construct a `SpawnParticleSystem` packet and send it via `playerRefComponent.getPacketHandler().writeNoCache(packet)` to each nearby player:

```java
SpawnParticleSystem packet = new SpawnParticleSystem(name, new Position(x, y, z), rotation, scale, color);
// ... sent to each player ref in range
```

**Source:** [SpawnParticleSystem.java](../../../.tmp_hytale_src/com/hypixel/hytale/protocol/packets/world/SpawnParticleSystem.java) (protocol, packet ID 152)

## 2. Confirmed: Usage from `SimpleInstantInteraction.firstRun()`

The `firstRun()` method receives an `InteractionContext` which provides:
- `context.getEntity()` → `Ref<EntityStore>` (the player)
- `context.getCommandBuffer()` → `CommandBuffer<EntityStore>` (implements `ComponentAccessor<EntityStore>`)

**Recommended pattern for spawning particles at the aimed block:**

```java
@Override
protected void firstRun(@Nonnull InteractionType type,
                        @Nonnull InteractionContext context,
                        @Nonnull CooldownHandler cooldownHandler) {
    Ref<EntityStore> ref = context.getEntity();
    CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
    Store<EntityStore> store = commandBuffer.getStore();

    // Raycast to get target block (same as BlueprintBookPickStencilInteraction)
    Vector3i target = TargetUtil.getTargetBlock(ref, 8.0, store);
    if (target == null) return;

    // Convert block coords to world position (center of block)
    Vector3d particlePos = new Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5);

    // Spawn the particle effect — auto-collects nearby players within 75 blocks
    ParticleUtil.spawnParticleEffect("Example_Simple", particlePos, commandBuffer);
}
```

### Real Engine Examples of This Pattern

| Caller | Particle System ID | Context |
|--------|--------------------|---------|
| `BlockHarvestUtils` | from `GatheringEffectsConfig.getParticleSystemId()` | Unbreakable block hit / wrong tool |
| `LaunchPadInteraction` | `"Splash"` | Launch pad activation |
| `VoidSpawnerSystems` | from `InvasionPortalConfig.getOnSpawnParticles()` | Portal entity spawn |
| `EntitySnapshotHistoryCommand` | `"Example_Simple"` | Debug visualization |
| `KnockbackPredictionSystems` | `"Example_Simple"` | Debug knockback visualization |
| `ProjectileComponent` | various (`bounceParticles`, `hitParticles`, `missParticles`, `deathParticles`) | Projectile lifecycle |
| `ActionSpawnParticles` (NPC) | from builder config | NPC behavior tree action |
| `DamageEffects` | from `WorldParticle[]` config | Combat hit effects |

## 3. Confirmed NOT Possible: Particle Fields in InteractionConfig

**Source:** [InteractionConfiguration.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/InteractionConfiguration.java) (decompiled)

`InteractionConfiguration` has only these fields:
- `displayOutlines` (boolean) — block selection wireframe
- `debugOutlines` (boolean) — debug wireframe
- `useDistance` (Map<GameMode, Float>) — interaction range
- `allEntities` (boolean) — target all entities vs specific
- `priorities` (Map<InteractionType, InteractionPriority>) — priority when multiple items equipped

**No particle-related fields exist in InteractionConfig.** Particles must be spawned programmatically.

## 4. Confirmed: Block Breaking Particles (Without Breaking)

**Source:** [BlockHarvestUtils.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/BlockHarvestUtils.java) (decompiled, lines 270-276)

When a player hits an unbreakable block with the wrong tool, the engine spawns particles from `GatheringEffectsConfig`:

```java
GatheringEffectsConfig unbreakableBlockConfig = gameplayConfig.getGatheringConfig().getUnbreakableBlockConfig();
String particleSystemId = unbreakableBlockConfig.getParticleSystemId();
if (particleSystemId != null) {
    ParticleUtil.spawnParticleEffect(particleSystemId, targetBlockCenterPos, results, entityStore);
}
```

This means the engine triggers block feedback particles **without** actually breaking the block. A plugin can replicate this by calling `ParticleUtil.spawnParticleEffect()` with any particle system ID.

However, the **block-material-specific** break particles (dust, wood chips, etc.) are controlled by `BlockParticleSetId` on the `BlockType` config (e.g. `"Stone"`, `"Wood"`, `"Water"`). These particles are triggered **client-side** during block breaking animations and are NOT directly invocable via `ParticleUtil`. You would need to use a generic particle system that visually approximates the effect.

## 5. Confirmed: JSON-Driven Particle System Definitions

Particle effects are defined in two layers of JSON asset files:

### `.particlesystem` — System Definition (Container)

References one or more spawners by ID:

```json
// Example: Dust_Sparkles_Fine.particlesystem
{
  "Spawners": [
    { "SpawnerId": "Dust_Sparkles_Fine", "FixedRotation": true, "WaveDelay": { "Min": 4, "Max": 36 } },
    { "SpawnerId": "Dust_Sparkles_Fine" },
    { "SpawnerId": "Dust_Sparkles_Fine" }
  ],
  "CullDistance": 30
}
```

### `.particlespawner` — Spawner Definition (Behavior)

Defines the actual particle behavior: texture, lifetime, velocity, animation, collision, etc.

Key fields:
- `RenderMode`: e.g. `"Erosion"`, `"Billboard"`
- `ParticleRotationInfluence`: `"Billboard"`, etc.
- `MaxConcurrentParticles`: int
- `ParticleLifeSpan`: `{ "Min": float, "Max": float }`
- `SpawnRate`: `{ "Min": float, "Max": float }`
- `InitialVelocity`: speed, yaw, pitch ranges
- `Particle.Texture`: path to texture
- `Particle.Animation`: keyframed scale/opacity/rotation
- `SpawnBurst`: boolean — all particles at once vs continuous
- `TotalParticles`: `{ "Min": int, "Max": int }`
- `SoftParticles`: `"Disable"` / `"Enable"`

### Asset Location

Reference assets found in: `docs/Reference Assets/Assets/Server/Particles/`

Structure:
```
Particles/
├── Block/          — block interaction particles
├── Combat/         — hit/damage particles
├── Deployables/    — placeable item particles
├── Drop/           — item drop particles
├── Explosion/      — explosion effects
├── NPC/            — NPC spawn/death particles
├── Projectile/     — projectile trails/impacts
├── Spell/          — spell effects
├── Weather/        — rain, snow, etc.
├── _Example/       — example/test systems (Example_Simple, Example_Hit, etc.)
└── _Test/          — R&D particle systems
```

### Known Particle System IDs (Confirmed Usable)

From decompiled code references:
- `"Example_Simple"` — used in debug commands (EntitySnapshotHistoryCommand, KnockbackPredictionSystems)
- `"Splash"` — used by LaunchPadInteraction
- Any ID matching a `.particlesystem` file in the Particles asset directory

## 6. Speculative But Likely

### Custom Particle Systems via Plugin Assets

The particle system is loaded from the asset registry. If the plugin can register custom `.particlesystem` and `.particlespawner` files via `LoadAssetEvent`, custom particle effects could be defined. This follows the same pattern as custom block types and items. **Not yet tested for particles specifically.**

### `BlockParticleSetId` Trigger Without Block Break

`BlockParticleSetId` particles (the material-specific dust) are likely triggered client-side during the block breaking animation. There may be a packet or method that triggers this effect at a position without an actual block break, but no evidence of such an API has been found in decompiled code.

## 7. Not Possible / Unknown

| Question | Status |
|----------|--------|
| Particle fields in InteractionConfig JSON | **Not possible** — no such fields exist |
| Spawning `BlockParticleSetId` particles without breaking the block | **Unknown** — these appear to be client-driven |
| Custom particle textures from plugin resources | **Unknown** — texture paths reference `Particles/Textures/...` in the asset bundle |
| Controlling particle color per-spawn for built-in systems | **Possible** — the `spawnParticleEffect(name, pos, yaw, pitch, roll, scale, color, ...)` overload accepts a `Color` parameter |

## Recommended Approach

For spawning particles at the aimed block from `BlueprintBookPickStencilInteraction.firstRun()`:

1. **Use `ParticleUtil.spawnParticleEffect(String, Vector3d, ComponentAccessor)`** — the simplest overload
2. **Reuse the `TargetUtil.getTargetBlock()` call** you already have for the block position
3. **Start with `"Example_Simple"`** as the particle system ID — it's used in multiple debug/test contexts and is guaranteed to exist
4. **Center the position** at `(blockX + 0.5, blockY + 0.5, blockZ + 0.5)` for block-center particles, or `(blockX + 0.5, blockY + 1.0, blockZ + 0.5)` for top-of-block

### Required Imports

```java
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
```

## See Also

- [InteractionConfiguration](../items/builder-tool-research.md) — InteractionConfig fields reference
- [Block Selection Highlight](../blocks/block-selection-highlight-research.md) — DisplayOutlines behavior
- [Reference particle assets](../../Reference%20Assets/Assets/Server/Particles/) — example particle system files
