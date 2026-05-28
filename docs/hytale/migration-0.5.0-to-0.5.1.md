---
topic: "Hytale 0.5.0 → 0.5.1 API Migration Report"
category: "plugin-api/migration"
updated: 2026-05-27
sources: ["decompiled SemverRange.java (0.5.0)", "server logs (0.5.0 and 0.5.1)", "decompiled TargetUtil.java", "IDE compile diagnostics", "build.gradle.kts"]
---

# Hytale 0.5.0 → 0.5.1 API Migration Report

## Summary

The plugin fails to load on 0.5.1 due to a **single blocking change**: the `SemverRange` parser was tightened to reject bare semver versions with a non-zero patch component. The build.gradle.kts already has the correct `^` prefix logic but the build output is stale. Beyond the manifest fix, there are **deprecated Inventory APIs** that are marked for removal and will break in a future release, plus **Hytale-internal vector type drift** that may become a compile issue if the server JAR changes its exported types.

---

## 1. Confirmed 0.5.1 Breaking Changes

### 1.1 SemverRange Parsing Tightened (BLOCKING)

**Evidence**: 0.5.1 server log error at `PluginManager.loadPluginsInClasspath`:

```
Caused by: java.lang.IllegalArgumentException: Bare version '0.5.1' is not a valid range.
  Use '=0.5.1' for an exact match, or '^0.5.1' / '~0.5.1' for a range.
  Bare ranges only work when the patch is zero (e.g. '1.2.0' or '1.x').
    at com.hypixel.hytale.common.semver.SemverRange.fromString(SemverRange.java:170)
    at com.hypixel.hytale.common.semver.SemverRangeCodec.parse(SemverRangeCodec.java:57)
    at com.hypixel.hytale.common.plugin.PluginManifest$1.parse(PluginManifest.java:62)
```

**What changed**:
- 0.5.1 introduced a **new class `SemverRangeCodec`** (does not exist in decompiled 0.5.0 source) that wraps `SemverRange.CODEC` with stricter validation
- `PluginManifest` now uses `PluginManifest$1` (anonymous codec subclass) that delegates to `SemverRangeCodec.parse()` instead of raw `SemverRange.fromString()`
- Bare versions like `0.5.1` (non-zero patch) are now **rejected with a helpful error message** instead of the old cryptic `"Invalid X-Range!"` exception
- 0.5.1 also **added backward compatibility** for pre-semver `YYYY.MM.DD-<sha>` formats — these are treated as wildcards with a warning

**Valid `ServerVersion` formats in 0.5.1**:

| Format | Example | Meaning |
|--------|---------|---------|
| `^M.m.p` | `^0.5.1` | Compatible with (≥0.5.1, <0.6.0) |
| `~M.m.p` | `~0.5.1` | Tilde range (≥0.5.1, <0.6.0) |
| `=M.m.p` | `=0.5.1` | Exact match only |
| `>=M.m.p` | `>=0.5.0` | Greater or equal |
| `M.m.0` | `0.5.0` | Bare with patch=0 still works (≥0.5.0, <0.6.0) |
| `M.0.0` | `1.0.0` | Major range (≥1.0.0, <2.0.0) |
| `*` | `*` | Wildcard (any version) |
| Range | `>=0.5.0 \|\| <1.0.0` | OR-combined ranges |
| Hyphen | `0.5.0 - 1.0.0` | ≥0.5.0 AND ≤1.0.0 |

**Fix**: The `build.gradle.kts` already has the correct logic:
```kotlin
"server_version" to if (detectedServerVersion.matches(Regex("""\d+\.\d+\.\d+""")))
    "^$detectedServerVersion" else detectedServerVersion,
```
This produces `^0.5.1` when the detected version is `0.5.1`. **A clean rebuild (`gradlew clean processResources`) is required** to regenerate `build/resources/main/manifest.json` with the `^` prefix.

### 1.2 New `DocumentContainingCodec` Error on Config Load (NEW)

**Evidence**: 0.5.1 log line 3 (does NOT appear in 0.5.0 log):
```
[SEVERE] [SERR] decodeJson: class com.hypixel.hytale.codec.DocumentContainingCodec
```

**Impact**: Appears during `HytaleServer.Loading config...`, before plugin loading. This is a server-internal config parsing issue — likely a tightened codec validation that emits errors for config fields it previously silently ignored. Not plugin-blocking, but indicates the codec framework was updated.

### 1.3 Asset Pack Manifest Validation Tightened (NEW)

**Evidence**: 0.5.1 log shows new warnings for mod directory packs:
```
[WARN] [AssetModule|P] Skipping pack at Camera_[CodeCreature] Blueprint System: missing or invalid manifest.json
[WARN] [AssetModule|P] Skipping pack at Camera_[CodeCreature] Unobstructed Third Person Camera: missing or invalid manifest.json
```

**Impact**: Asset packs in `mods/` now require valid `manifest.json` files. Packs without them are skipped. This means the plugin's asset pack at `Camera_[CodeCreature] Blueprint System` failed to load its assets. Once the classpath manifest is fixed, the classpath-loaded plugin will work, but any mod-directory asset packs need their own valid manifests.

---

## 2. Potential Risks (Once Manifest Is Fixed)

### 2.1 Deprecated Inventory APIs (HIGH RISK)

**Evidence**: IDE compile diagnostics show **deprecated-and-marked-for-removal** methods across 3 files:

| Method | Files Using It | Severity |
|--------|---------------|----------|
| `Inventory.getActiveHotbarItem()` | StencilInputListener (×2) | **Deprecated for removal** |
| `Inventory.getActiveHotbarSlot()` | StencilInputListener, BlueprintBookParticleLoop | **Deprecated for removal** |
| `Inventory.getHotbar()` | StencilInputListener, BlueprintBookParticleLoop | **Deprecated for removal** |
| `Inventory.getCombinedBackpackStorageHotbar()` | BlueprintBookParticleLoop, StencilPlacementSystem | **Deprecated for removal** |
| `Inventory` (class itself) | StencilPlacementSystem | **Deprecated for removal** |
| `ItemStack.getMetadata()` | StencilMetadataTest | Deprecated |

These APIs work in 0.5.1 but will **break in a future minor release** (likely 0.6.0 or sooner). The replacement APIs are not yet identified from the decompiled source — investigation of the 0.5.1 Inventory class is needed.

### 2.2 Permission Node Validation (EXISTING BUG, WILL RESURFACE)

**Evidence**: The 0.5.0 log shows this error (does NOT appear in 0.5.1 only because the plugin didn't load):
```
[SEVERE] Failed to register command: placeblock -
  Invalid permission node: camera.[codecreature] blueprint system.command.placeblock
```

The plugin's group is `Camera` and name is `[CodeCreature] Blueprint System`, producing permission nodes with brackets and spaces. The `PermissionsModule.registerPermission()` rejects these. Once the manifest is fixed, this error will re-appear in 0.5.1.

**Fix**: Change the plugin's `Group` in `manifest.json` (via `gradle.properties`) to avoid special characters, or sanitize the permission node before registration.

### 2.3 Vector Type Drift (MODERATE RISK)

**Evidence from decompiled source**:

| API | Decompiled Type | Plugin Import |
|-----|----------------|---------------|
| `TargetUtil.getTargetBlock()` return | `com.hypixel.hytale.math.vector.Vector3i` | `org.joml.Vector3i` |
| `TargetUtil` internal | `com.hypixel.hytale.math.vector.Vector3d` | `org.joml.Vector3d` |
| `BlueprintBookParticleLoop` | Uses `Rotation3f` | `com.hypixel.hytale.math.vector.Rotation3f` |
| `BoundingBoxRayCast` | Uses `Transform` | `com.hypixel.hytale.math.vector.Transform` |

The decompiled `com.hypixel.hytale.math.vector.Vector3i` is a standalone class (NOT extending `org.joml.Vector3i`). Since the plugin compiles and runs with `org.joml.Vector3i`, one of these must be true:
1. The server JAR bytecode still exports `org.joml` types (decompiler chose wrong FQN)
2. Hytale's `Vector3i` was recently introduced and will replace `org.joml` types in a future release

The plugin currently compiles without vector errors, so the JOML types are still valid in the 0.5.1 JAR. But this is a known drift vector — the design doc at `docs/design-s2605261205-compile-restoration-vector-types.md` already identified this.

### 2.4 Nullability Contract Tightening

**Evidence**: 371 compile diagnostics, many are null-type-safety warnings:
- `@Nonnull` parameters receiving `@Nullable` values (e.g., `Ref<EntityStore>`, `CraftingRecipe`, `CombinedItemContainer`)
- These are currently warnings, but if the server adds runtime null checks in a future release, they would become runtime crashes

Key locations:
- `BlueprintBookParticleLoop`: 18 null safety warnings (entity refs, effect lookups, component types)
- `StencilPlacementSystem`: container nullability
- `RecipeFilterRegistry`: collection type safety

---

## 3. No Change Detected

### 3.1 Interaction System — STABLE
- `SimpleInstantInteraction` structure unchanged (same `CODEC`, same `firstRun()` abstract method)
- `BuilderCodec` API pattern unchanged
- `Interaction.CODEC` registry pattern unchanged
- The plugin's `BlueprintBenchOpenUIInteraction` and `BlueprintBookPickStencilInteraction` codecs should work as-is

### 3.2 Core Plugin Module List — STABLE
- Both 0.5.0 and 0.5.1 load the same 37 core plugins in the same order
- Same classpath plugins list (except `Camera:[CodeCreature] Blueprint System` is missing in 0.5.1 due to manifest failure)

### 3.3 ItemModule/AssetStore — NO NEW VALIDATION DETECTED
- `ItemModule|P` reports `Computed damage data for 0 of 0 weapon items` in both versions
- Asset store warnings for unused keys are the same pattern in both versions
- No new item validation that would affect custom items

---

## 4. Recommendations

### Immediate (Required to Load on 0.5.1)

1. **Rebuild the plugin**: `gradlew clean processResources build`  
   The `build.gradle.kts` already prepends `^` to semver-format versions. A clean build will produce `"ServerVersion": "^0.5.1"` in the manifest.

2. **Verify the built manifest**: Check `build/resources/main/manifest.json` contains `"ServerVersion": "^0.5.1"` (not bare `0.5.1`)

### Short-Term (Should Fix Before Next Server Update)

3. **Fix permission node bug**: Sanitize the group/name to remove `[`, `]`, and spaces from the permission node path (or change plugin group in `gradle.properties`)

4. **Replace deprecated Inventory APIs**: Investigate the 0.5.1 `Inventory` class for replacement methods for `getActiveHotbarItem()`, `getActiveHotbarSlot()`, `getHotbar()`, and `getCombinedBackpackStorageHotbar()`

### Medium-Term

5. **Address null safety warnings**: Add null guards at the 18+ call sites flagged by the compiler, especially in `BlueprintBookParticleLoop` and `StencilPlacementSystem`

6. **Monitor vector type drift**: If a future server version moves from `org.joml` to `com.hypixel.hytale.math.vector` types, the 4 files identified in the design doc will need import/type updates

---

## 5. Open Questions

| # | Question | Why It Matters |
|---|----------|---------------|
| 1 | What are the replacement APIs for the deprecated `Inventory` methods? | These are marked for removal — need to find the new API before it breaks |
| 2 | Is `com.hypixel.hytale.math.vector.Rotation3f` still a valid type in 0.5.1? | Class file not found in decompiled source; may have been renamed/moved |
| 3 | Did the `BuilderCodec.readField` error handling change in 0.5.1? | In 0.5.0, codec field parse errors may have been swallowed; in 0.5.1 they propagate |
| 4 | What is the `DocumentContainingCodec` error about? | New SEVERE error in 0.5.1 config loading — may indicate a config format change |
| 5 | Does the `SemverRangeCodec` have any other new validation beyond bare-version rejection? | Only saw one error path, but there could be more |

---

## 6. Log Diff Summary

| Feature | 0.5.0 Log | 0.5.1 Log |
|---------|-----------|-----------|
| Server Version | `0.5.0, Revision: c68d7d0d` | `0.5.1, Revision: 8a0fc430` |
| Plugin Loaded? | Yes (`Camera:[CodeCreature] Blueprint System`) | **No** (manifest parse failure) |
| `DocumentContainingCodec` error | Not present | **New** SEVERE on line 3 |
| Asset pack skip warnings | Not present | **New** (3 packs skipped) |
| Pre-semver compat warning | Not present | **New** (wildcard fallback for `YYYY.MM.DD-sha`) |
| Permission node error | Present (2 commands) | Not present (plugin didn't load) |
| Console type | `windows-vtp` | `dumb-color` |
| Boot time | 2.942s | 3.305s |
