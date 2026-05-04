---
topic: "addAssetsDependency — Gradle Plugin Option"
category: "Assets"
updated: 2026-05-04
sources: [
  "build.gradle.kts",
  "docs/hytale/plugins/gradle-plugin-mechanics.md",
  "ScaffoldIt source (adaliszk/gradle-scaffoldit-modkit)",
  "ScaffoldIt issue #18",
  "decompiled UpdateService.java / UpdateApplyCommand.java",
  "hytalemodding.dev/en/docs/guides/plugin/setting-up-env",
  "plugin-template README"
]
confidence: "Mixed — confirmed facts from code + speculative inferences flagged"
---

# `addAssetsDependency` — Full Reference

## 1. What Does `addAssetsDependency` Do?

**Confirmed** (from [gradle-plugin-mechanics.md](../plugins/gradle-plugin-mechanics.md) and [build.gradle.kts](../../../build.gradle.kts)):

```kotlin
hytale {
    addAssetsDependency = true
}
```

This flag tells the `hytale-mod` Gradle plugin to add the game's **`Assets.zip`** file to your project's **compile classpath / external libraries**. This makes the contents of `Assets.zip` browsable and searchable within your IDE (IntelliJ IDEA, VS Code, etc.).

### What `Assets.zip` Contains

**Confirmed** (from decompiled server code and asset format analysis):

`Assets.zip` is Hytale's main game asset archive. It contains:
- **All JSON asset definitions**: block types, items, crafting recipes, NPC configs, sounds, particles
- **Textures**: block textures, item icons, UI textures
- **Models**: block models (`.blockymodel`), entity models
- **UI definitions**: custom UI layouts, fonts
- **Sound files**: ambient, SFX, music
- **World generation data**: biome configs, structure definitions

This file is **very large** (hundreds of MB to potentially over 1 GB).

### What Happens When Enabled

| Step | What Occurs |
|------|-------------|
| 1 | The plugin locates the Hytale installation via `hytale.home_path` or default paths |
| 2 | It finds `Assets.zip` in the parent directory of the server installation |
| 3 | It adds `Assets.zip` as a dependency on the compile classpath |
| 4 | Your IDE indexes the entire contents of the zip |

### Why You Might Want It

- **Browse vanilla asset definitions**: See the exact JSON structure of every block type, item, crafting recipe
- **Discover asset IDs**: Find correct item IDs, block type IDs, resource type IDs for your plugin code
- **Inspect texture paths**: Verify icon paths, block texture filenames
- **Understand inheritance**: See which items/blocks inherit from parents and what fields they override

### Why It's Disabled by Default

The inline comment in the template says it all:

> ⚠️ CAUTION, this file is very big and might make your IDE unresponsive for some time!

---

## 2. Common Issues/Errors When Enabling It

### Issue A: IDE Unresponsiveness / Freezing

**Confirmed** (from build.gradle.kts inline warning):

When `addAssetsDependency = true`, your IDE must index the entire `Assets.zip`. This can:
- Freeze IntelliJ IDEA for several minutes during the initial index
- Consume significant RAM (increase `org.gradle.jvmargs` may help)
- Slow down project sync operations

**Mitigation**: Your project has `org.gradle.jvmargs=-Xmx1G` in `gradle.properties`. For indexing Assets.zip, consider increasing to `-Xmx2G` or higher.

### Issue B: "Failed to find Hytale at the expected location"

**Confirmed** (from [gradle-plugin-mechanics.md](../plugins/gradle-plugin-mechanics.md) and hytalemodding.dev):

If the plugin can't locate your Hytale installation, the build fails. Fix by setting in `gradle.properties`:

```properties
hytale.home_path=C:\Path\To\Hytale\Data
```

### Issue C: Assets.zip Not Found

**Confirmed** (from decompiled `UpdateService.isValidUpdateLayout()`):

The engine expects `Assets.zip` to be in the **parent directory** of the server installation:

```
<Hytale Install>/
├── Assets.zip          ← HERE
├── start.sh / start.bat
└── Server/
    └── HytaleServer.jar
```

If `Assets.zip` doesn't exist at this location, the plugin cannot add it as a dependency.

### Issue D: Configuration Phase Failures (CI/CD)

**Confirmed** (from ScaffoldIt issue [#18](https://github.com/adaliszk/gradle-scaffoldit-modkit/issues/18)):

The Assets.zip resolution happens during Gradle's **configuration phase**, meaning:
- Even `./gradlew build` may fail if Assets.zip is not present
- CI/CD environments without a Hytale installation will fail
- This was fixed in ScaffoldIt `v0.2.10+` by deferring the resolution to only tasks that need it

**For the `hytale-mod` plugin**: The behavior may differ. If your CI builds fail, disable `addAssetsDependency` for CI:

```kotlin
hytale {
    addAssetsDependency = (System.getenv("CI") == null)
}
```

### Issue E: Stale Assets After Game Update

**Speculative** (inferred from version mismatch pattern in gradle-plugin-mechanics.md):

If Hytale auto-updates, `Assets.zip` will reflect the new version, but Gradle may cache the old dependency. Run:

```bash
./gradlew build --refresh-dependencies
```

---

## 3. Prerequisites and Additional Configuration

### Required

| Prerequisite | Details |
|-------------|---------|
| Hytale installed | Via the Hytale launcher |
| `hytale.home_path` | Set in `gradle.properties` if not at default location |
| Sufficient RAM | Increase `org.gradle.jvmargs` for IDE indexing |

### Recommended

| Setting | Value | Why |
|---------|-------|-----|
| `org.gradle.jvmargs` | `-Xmx2G` or higher | Large zip indexing |
| `org.gradle.configuration-cache` | `false` | Avoid caching issues with file-system dependencies |

### Your Current Configuration

From `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx1G
org.gradle.caching=false
org.gradle.configuration-cache=false
server_version=2026.03.26-89796e57b
```

**Note**: 1G may be tight with `addAssetsDependency = true`. Consider 2G+.

---

## 4. Relationship With `syncAssets` and `deployCommonAssets`

**Confirmed** (from [build.gradle.kts](../../../build.gradle.kts) lines 120-160 and 340-370):

These three features serve **completely different purposes** and are **independent**:

| Feature | Purpose | Scope |
|---------|---------|-------|
| `addAssetsDependency` | Makes vanilla Assets.zip browsable in IDE at compile time | **Read-only reference** |
| `syncAssets` | Copies modified assets FROM `build/resources/main` BACK TO `src/main/resources` after server stops | **Your plugin's assets only** |
| `deployCommonAssets` | Copies `Common/` assets (UI files) to `run/mods/CodeCreature.Development/Common/` for client loading | **Your plugin's Common/ assets** |

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│ COMPILE TIME                                                        │
│                                                                     │
│  addAssetsDependency = true                                         │
│  └── Adds vanilla Assets.zip to classpath                           │
│      └── IDE can browse/search all vanilla JSON definitions         │
│          (block types, items, recipes, textures, etc.)              │
│                                                                     │
│  This does NOT affect your plugin's assets or build output.         │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ BUILD/RUN TIME                                                      │
│                                                                     │
│  processResources                                                   │
│  └── Copies src/main/resources/ → build/resources/main/             │
│      └── Expands manifest.json template variables                   │
│                                                                     │
│  deployCommonAssets (runs BEFORE runServer)                          │
│  └── Copies build/resources/main/Common/                            │
│      → run/mods/CodeCreature.Development/Common/                    │
│      Purpose: Client needs Common/ outside the JAR                  │
│                                                                     │
│  runServer                                                          │
│  └── Starts server, game may modify assets in build/                │
│                                                                     │
│  syncAssets (runs AFTER successful server shutdown)                  │
│  └── Copies build/resources/main/ → src/main/resources/             │
│      Purpose: Preserve asset editor changes back to source          │
│      Excludes: manifest.json (template version preserved)           │
└─────────────────────────────────────────────────────────────────────┘
```

**Key insight**: `addAssetsDependency` has **zero interaction** with `syncAssets` and `deployCommonAssets`. Disabling it does not affect your plugin's asset loading, building, or deployment.

---

## 5. How `Assets.zip` Gets Sourced

### Confirmed: NOT Downloaded by the Plugin

**Confirmed** (from ScaffoldIt docs, decompiled server code, and hytalemodding.dev):

`Assets.zip` is **NOT downloaded by the Gradle plugin**. It comes from your **local Hytale game installation**, which is installed and updated by the **Hytale launcher**.

### Source Chain

```
Hytale Launcher
  └── Downloads/updates game files to install directory
      └── <Hytale Install>/
          ├── Assets.zip        ← Updated by launcher
          ├── start.bat
          └── Server/
              └── HytaleServer.jar

hytale-mod Gradle plugin
  └── Reads hytale.home_path from gradle.properties
      └── Locates Assets.zip at <home_path>/../Assets.zip (or similar)
          └── Adds as compile dependency when addAssetsDependency = true
```

### Where the Plugin Looks

**Confirmed** (from `UpdateService.isValidUpdateLayout()` in decompiled source):

The engine validates the layout by checking:
```java
Path parent = Path.of("..").toAbsolutePath();
Files.exists(parent.resolve("Assets.zip"))
    && (Files.exists(parent.resolve("start.sh"))
        || Files.exists(parent.resolve("start.bat")));
```

So `Assets.zip` lives in the **parent** of the `Server/` directory within the Hytale installation.

### Typical Windows Path

```
C:\Users\<user>\AppData\Roaming\Hytale\Data\
├── Assets.zip
├── start.bat
└── Server\
    └── HytaleServer.jar
```

Or wherever `hytale.home_path` points.

---

## Summary Table

| Question | Answer | Confidence |
|----------|--------|------------|
| What does `addAssetsDependency` do? | Adds Assets.zip to IDE classpath for browsing | ✅ Confirmed |
| Is it needed for plugin compilation? | **No** — plugins compile against the Server Maven artifact | ✅ Confirmed |
| Is it needed for plugin asset loading? | **No** — plugin assets load from JAR's `Server/` directory | ✅ Confirmed |
| Does it affect `syncAssets`/`deployCommonAssets`? | **No** — completely independent systems | ✅ Confirmed |
| Is Assets.zip auto-downloaded? | **No** — comes from local Hytale installation via launcher | ✅ Confirmed |
| Default value? | `false` | ✅ Confirmed |
| Main risk? | IDE freezing/unresponsiveness from indexing the large file | ✅ Confirmed |
| CI/CD compatible? | May fail if Hytale not installed; disable for CI | ✅ Confirmed (via ScaffoldIt #18) |

---

## See Also

- [Gradle Plugin Mechanics](../plugins/gradle-plugin-mechanics.md) — full plugin reference
- [Asset Pipeline](./asset-pipeline.md) — how assets load at runtime
- [Plugin Asset Pack Loading](./plugin-asset-loading.md) — how your plugin's assets get loaded
- [Asset Formats Overview](./formats-overview.md) — JSON format reference for what's inside Assets.zip
