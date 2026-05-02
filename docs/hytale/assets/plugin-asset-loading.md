---
topic: "Plugin Asset Pack Loading"
category: "Assets"
updated: 2026-05-01
sources: ["AssetModule.java", "JavaPlugin.java", "PluginManifest.java", "AssetRegistryLoader.java", "HytaleServer.java", "PluginManager.java"]
---

# Plugin Asset Pack Loading

## Summary

Plugin JARs can include custom game assets (blocks, items, qualities, etc.) by setting `"IncludesAssetPack": true` in `manifest.json`. The engine opens the JAR as a virtual filesystem and loads assets from its `Server/` directory using the same `AssetStore` pipeline as the base game. **Plugin asset packs load AFTER the initial `LoadAssetEvent`**, via a separate `AssetPackRegisterEvent` pathway.

## How It Works

### Boot Sequence (Critical Ordering)

```
1. pluginManager.setup()
   ├── AssetModule.setup() runs (core plugin, first)
   │   ├── Loads directory/zip packs from run/mods/ (SKIPS .jar files!)
   │   ├── Registers LoadAssetEvent handler (priority -16)
   │   └── Registers AssetPackRegisterEvent handler (priority -16)
   ├── All other plugins' setup() runs
   │   └── Plugin registers its own event handlers
   │
2. LoadAssetEvent dispatched ← BASE GAME ASSETS LOADED HERE
   │   AssetModule handler fires:
   │   ├── Sets hasLoaded = true
   │   └── For each registered AssetPack: AssetRegistryLoader.loadAssets()
   │       └── Only directory/zip packs are registered at this point
   │
3. pluginManager.start()
   ├── Each plugin's start0() runs:
   │   ├── PluginBase.start0() → calls this.start() → state = ENABLED
   │   └── JavaPlugin.start0() continues:
   │       └── If includesAssetPack():
   │           └── AssetModule.registerPack(pluginId, jarPath, manifest)
   │               ├── Opens JAR as FileSystem (ZipFileSystem)
   │               ├── Root = JAR filesystem root (/)
   │               ├── Since hasLoaded = true:
   │               │   └── Dispatches AssetPackRegisterEvent
   │               │       └── Handler calls AssetRegistryLoader.loadAssets(null, pack)
   │               │           └── Iterates ALL AssetStores
   │               │               └── For each store with a path:
   │               │                   resolves <JAR_ROOT>/Server/<store_path>/
   │               │                   if directory exists → loadAssetsFromDirectory()
   │               └── Pack added to assetPacks list
   │
4. Universe.get().getUniverseReady().join()
5. BootEvent dispatched ← "ItemQuality: 12" log printed here
```

### Key Source: JavaPlugin.start0()

```java
// JavaPlugin.java line 31-43
@Override
protected void start0() {
    super.start0();  // runs start(), sets state = ENABLED
    if (this.getManifest().includesAssetPack()) {
        AssetModule assetModule = AssetModule.get();
        String id = new PluginIdentifier(this.getManifest()).toString();
        AssetPack existing = assetModule.getAssetPack(id);
        if (existing != null) {
            // Already registered — skip
            return;
        }
        assetModule.registerPack(id, this.file, this.getManifest());
    }
}
```

### Key Source: AssetModule.registerPack()

```java
// AssetModule.java
public void registerPack(String name, Path path, PluginManifest manifest) {
    Path absolutePath = path.toAbsolutePath().normalize();
    // For .jar/.zip files: opens as ZipFileSystem
    if (lowerFileName.endsWith(".zip") || lowerFileName.endsWith(".jar")) {
        fileSystem = FileSystems.newFileSystem(absolutePath, (ClassLoader)null);
        absolutePath = fileSystem.getPath("").toAbsolutePath().normalize();
        isImmutable = true;
    }
    AssetPack pack = new AssetPack(packLocation, name, absolutePath, fileSystem, isImmutable, manifest);
    this.assetPacks.add(pack);
    if (this.hasLoaded) {
        // POST-INIT: dispatches AssetPackRegisterEvent → loadAssets()
        dispatch(new AssetPackRegisterEvent(pack));
        return;
    }
    // PRE-INIT: just adds to list, will be loaded during LoadAssetEvent
}
```

### Key Source: AssetRegistryLoader.loadAssets0()

```java
// AssetRegistryLoader.java
private static void loadAssets0(LoadAssetEvent event, AssetPack assetPack) {
    Path serverAssetDirectory = assetPack.getRoot().resolve("Server");
    for (AssetStore store : AssetRegistry.getStoreMap().values()) {
        String path = store.getPath(); // e.g., "Item/Qualities"
        if (path != null) {
            Path assetsPath = serverAssetDirectory.resolve(path);
            if (Files.isDirectory(assetsPath)) {
                store.loadAssetsFromDirectory(packName, assetsPath);
            }
        }
    }
}
```

## AssetStore Path Mapping

Each asset type has a registered path under `Server/`:

| Asset Type | Store Path | Plugin Resource Directory |
|-----------|-----------|--------------------------|
| `ItemQuality` | `Item/Qualities` | `resources/Server/Item/Qualities/` |
| `Item` | `Item/Items` | `resources/Server/Item/Items/` |
| `BlockType` | `Item/Block/Blocks` | `resources/Server/Item/Block/Blocks/` |
| `Fluid` | `Item/Block/Fluids` | `resources/Server/Item/Block/Fluids/` |
| `ResourceType` | `Item/ResourceTypes` | `resources/Server/Item/ResourceTypes/` |
| `CraftingRecipe` | (dynamic) | via `CraftingRecipe.CODEC` |
| `ItemDropList` | `Drops` | `resources/Server/Drops/` |

## IncludesAssetPack Manifest Flag

```json
{
  "IncludesAssetPack": true
}
```

- Declared in `PluginManifest.java` as `private boolean includesAssetPack = false`
- Read via `manifest.includesAssetPack()`
- When `true`, `JavaPlugin.start0()` registers the JAR as an asset pack
- When `false` (default), the JAR's `Server/` directory is ignored

## AssetPack Class

```java
public class AssetPack {
    private final String name;        // e.g., "MyGroup:MyPlugin"
    private final Path root;          // JAR filesystem root
    private final FileSystem fileSystem; // ZipFileSystem for JARs
    private final boolean isImmutable;   // true for JARs
    private final PluginManifest manifest;
    private final Path packLocation;  // original JAR path on disk
}
```

## IndexedLookupTableAssetMap (ItemQuality's Map Type)

- Uses `putAll0()` to add new assets with auto-incrementing indices
- Thread-safe via `StampedLock` (keyToIndex) and `ReentrantLock` (array)
- `getIndex(key)` returns the numeric index for a string key
- `getIndexOrDefault(key, default)` used by `StencilVisualManager.resolveQualityIndex()`
- Keys are **case-insensitive** (`CaseInsensitiveHashStrategy`)

## UpdateItemQualities Packet

When assets are added post-init, the `ItemQualityPacketGenerator` generates an `UpdateItemQualities` packet:

```java
// Packet fields:
public UpdateType type;                    // Init, AddOrUpdate, Remove
public int maxId;                          // highest index + 1
public Map<Integer, ItemQuality> itemQualities; // index → protocol quality
```

The protocol `ItemQuality` contains:
- `id`, `itemTooltipTexture`, `itemTooltipArrowTexture`
- `slotTexture`, `blockSlotTexture`, `specialSlotTexture`
- `textColor` (Color), `localizationKey`
- `visibleQualityLabel`, `renderSpecialSlot`, `hideFromSearch`

When a pack is loaded post-init, `HytaleAssetStore.handleRemoveOrUpdate()` broadcasts the update packet to connected players.

## Gotchas

1. **Timing**: Plugin asset packs load during `pluginManager.start()`, AFTER `LoadAssetEvent`. Any code in `LoadAssetEvent` handlers won't see plugin assets yet.
2. **JAR exclusion**: `AssetModule.loadPacksFromDirectory()` explicitly **skips .jar files** — only the `JavaPlugin.start0()` pathway loads JARs as asset packs.
3. **Client sync**: New qualities added post-init need the `UpdateItemQualities` packet sent to connected clients. This happens automatically via `HytaleAssetStore.handleRemoveOrUpdate()`.
4. **Shared filesystem**: Each JAR gets its own `ZipFileSystem`. The `AssetPack.root` is the JAR's filesystem root, not the disk path.
5. **Immutability**: JAR-based asset packs are marked `isImmutable = true`, so the asset monitor won't watch them for changes.

## Programmatic Asset Registration (Alternative)

If asset files aren't loading from the JAR, assets can be registered programmatically:

```java
// NOT directly supported via public API, but the AssetStore internally does:
// assetStore.loadAssets(packKey, List<T> assets)
// assetStore.loadAssetsFromPaths(packKey, List<Path> paths)
```

The `IndexedLookupTableAssetMap.putAll0()` method handles adding entries with auto-incrementing indices, so new qualities would get indices 12, 13, etc.

## See Also
- [Plugin Manifest](../plugins/manifest.md)
- [Plugin Lifecycle](../plugins/lifecycle.md)
- [Plugin Capabilities](../plugins/capabilities.md)
