---
topic: "Item Drop Light Beam / Pillar Effects"
category: "Particles"
updated: 2026-05-08
sources: ["decompiled ItemEntityConfig.java", "decompiled ParticleUtil.java", "Reference Assets/Server/Particles/Drop/", "Reference Assets/Server/Item/Qualities/", "Reference Assets/Server/Entity/Effects/Drop/"]
---

# Item Drop Light Beam / Pillar Effects

## Summary

The green (and other colored) light beams on dropped items in Hytale are **particle systems** — not built-in entity visual components or beacon effects. The beam is the **`Drop_Common_Ray`** (or rarity-variant) particle spawner, which renders an upward-moving vertical ray using `Particles/Textures/Basic/Ray.png` with additive blending.

The visual has **three independent layers**:
1. **Particle system** (`Drop_Common.particlesystem`) — the ray beam + ground glow
2. **Entity effect** (`EntityBottomTint`) — a colored tint on the entity base (only for Uncommon+)
3. **Model VFX** (`ModelVFXId`) — a shader effect on the item model itself (only for Uncommon+)

## How It Works

### The Chain: Quality → ItemEntityConfig → Client

```
Item Quality JSON (e.g. Common.json)
  └─ "ItemEntityConfig": { "ParticleSystemId": "Drop_Common" }
       └─ Serialized into ItemEntityConfig protocol object
            └─ Sent to client when item entity is spawned
                 └─ Client loads Drop_Common.particlesystem
                      └─ Spawns Drop_Common_Ray + Drop_Common_Ground spawners
```

### Particle System Structure (Drop_Common)

| Spawner | Description | Offset Y | Delay |
|---------|-------------|----------|-------|
| `Drop_Common_Ground` | Flat ground glow (Erosion blend, Glow.png, rotated 90° on X) | 0.1 | 0.5s |
| `Drop_Common_Ray` | Vertical beam pillar (BlendAdd, Ray.png, BillboardY, moves upward) | 0.6 | 0.2s |

### Color by Rarity

| Rarity | Particle System | Ray Color (start → end) | Ground Tint | Has EntityEffect |
|--------|----------------|------------------------|-------------|-------------------|
| Common | `Drop_Common` | `#ffffff` → `#fffef9` (white) | `#e3faff` (pale blue) | No |
| Uncommon | `Drop_Uncommon` | `#51c534` → `#a1ff30` (green) | `#5a8826` (green) | Yes |
| Rare | `Drop_Rare` | (blue) | `#5875de` (blue) | Yes |
| Epic | `Drop_Epic` | (purple) | `#4c4ab2` (purple) | Yes |
| Legendary | `Drop_Legendary` | (gold) | (gold) | Yes |

### Key Observation: "Green beam" = Uncommon quality

The **green** light beam the user is seeing corresponds to **`Drop_Uncommon`**, not `Drop_Common`. Common items have a subtle white/pale beam. Uncommon items show the distinctive green `#51c534` → `#a1ff30` ray.

## Can We Spawn This Beam at Arbitrary Positions?

### Yes — via `ParticleUtil.spawnParticleEffect()`

The particle system `Drop_Common` / `Drop_Uncommon` etc. can be spawned at any world position:

```java
// Spawn the green (Uncommon) beam at a block position
ParticleUtil.spawnParticleEffect("Drop_Uncommon", blockCenterPos, store);

// Spawn the white (Common) beam
ParticleUtil.spawnParticleEffect("Drop_Common", blockCenterPos, store);
```

**However**, there is a critical difference: when used on dropped item entities, the particle system is **attached to the entity** and **loops continuously** (the entity effect config has `"Infinite": true`). When spawned via `ParticleUtil`, it is a **one-shot effect** — it plays once and disappears.

### One-shot vs Continuous

| Method | Behavior | Duration |
|--------|----------|----------|
| `ParticleUtil.spawnParticleEffect()` | Fires once | ~1.5 seconds (particle lifespan) |
| `ItemEntityConfig.particleSystemId` | Attached to entity, loops while entity exists | Infinite |
| Entity Effect (`Drop_Uncommon.json`) | Applied via EntityEffect system, loops | Infinite (config says `"Infinite": true`) |

### For Blueprint Book Use Case

Since `BlueprintBookParticleLoop` already runs a 100ms polling loop and re-fires particles each tick, using `ParticleUtil.spawnParticleEffect("Drop_Uncommon", pos, store)` would create a **continuously refreshing beam effect** — effectively mimicking the persistent beam on dropped items.

**Recommended approach:**
```java
// In BlueprintBookParticleLoop, replace current particle effect:
String effect = "Drop_Uncommon";  // or "Drop_Common" for white
Vector3d particlePos = new Vector3d(target.x + 0.5, target.y + 0.1, target.z + 0.5);
ParticleUtil.spawnParticleEffect(effect, particlePos, store);
```

Note the Y offset: the `Drop_Common.particlesystem` already has internal Y offsets (+0.1 for ground, +0.6 for ray), so place the spawn position at the **bottom of the block** (block Y + 0.1) rather than the center.

## Limitations

1. **No `EntityBottomTint` via particles** — The colored tint under the entity model is a separate visual applied by the EntityEffect system, not part of the particle system. `ParticleUtil` cannot replicate this.
2. **No `ModelVFXId` via particles** — The shader glow on the item model is also separate. Not applicable to blocks anyway.
3. **Particle overdraw** — Re-firing `Drop_Uncommon` every 100ms means ~15 overlapping ray particles at any time (1.5s lifespan / 0.1s interval). The ray spawner has `MaxConcurrentParticles: 2`, but that's per-spawner-instance. Each `spawnParticleEffect` call creates a new independent instance. Visual accumulation may cause excessive brightness.

## Alternative Beam Particle Systems

### From Reference Assets

| System | Location | Description |
|--------|----------|-------------|
| `NatureBeam` | `Particles/_Test/NatureRnD/` | Multi-spawner beam with core, overlay, sparks, glow |
| `Test_Beam_*` | `Particles/_Test/MagicRnD/Beam/` | Various test beam effects (static, lightning, etc.) |
| `Totem_Slow_BeamStart*` | `Particles/_Test/SlowTotem/` | Totem beam effects |
| `Beam_Heal_*` | `Particles/_Test/HealBeams/` | Green/red healing beams |
| `Stick_Slam_*_Beam` | `Particles/_Test/Sticks/` | Combat beam effects |

> **Warning**: Systems under `_Test/` may not be shipped in production builds and could be removed at any time.

### The Drop systems are the safest choice since they're production assets.

## Spawner Detail: Drop_Common_Ray

```json
{
  "RenderMode": "BlendAdd",
  "ParticleRotationInfluence": "BillboardY",
  "MaxConcurrentParticles": 2,
  "ParticleLifeSpan": { "Min": 1.5, "Max": 1.5 },
  "SpawnRate": { "Min": 1.0, "Max": 1.0 },
  "InitialVelocity": {
    "Pitch": { "Min": 90, "Max": 90 },
    "Speed": { "Min": 0.2, "Max": 0.4 }
  },
  "Particle": {
    "Texture": "Particles/Textures/Basic/Ray.png",
    "FrameSize": { "Width": 32, "Height": 96 },
    "Animation": {
      "0":   { "Opacity": 0.1, "Color": "#ffffff" },
      "40":  { "Opacity": 1.0 },
      "75":  { "Opacity": 0.8 },
      "100": { "Color": "#fffef9", "Opacity": 0.1 }
    }
  }
}
```

## Protocol: ItemEntityConfig

The `ItemEntityConfig` protocol class carries:
- `particleSystemId` (String) — which particle system to play on the item entity
- `particleColor` (Color) — optional color override
- `showItemParticles` (boolean) — whether to render item-specific particles

This is serialized into the entity spawn packet and handled entirely client-side. There is no server API to attach an `ItemEntityConfig` to a non-item entity or arbitrary position.

## See Also
- [ParticleUtil source](.tmp_hytale_src/com/hypixel/hytale/server/core/universe/world/ParticleUtil.java)
- [ItemEntityConfig source](.tmp_hytale_src/com/hypixel/hytale/protocol/ItemEntityConfig.java)
- [ApplicationEffects source](.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/entityeffect/config/ApplicationEffects.java)
