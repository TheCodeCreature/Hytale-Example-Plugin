---
topic: "Asset Pipeline"
category: "Assets"
updated: 2026-04-16
sources: ["codebase analysis"]
---

# Asset Pipeline

## Summary

Hytale's asset pipeline loads, validates, and registers all game content (blocks, items, recipes, NPCs, etc.) from JSON files before gameplay begins. Plugins can include their own asset packs and modify loaded assets at runtime.

## Loading Lifecycle

```
Server Boot
  │
  ├── 1. Initialize AssetRegistry
  │
  ├── 2. Discover asset packs
  │     ├── Vanilla assets (built-in)
  │     └── Plugin assets (IncludesAssetPack = true)
  │
  ├── 3. Load & parse JSON files
  │     ├── Resolve Parent inheritance chains
  │     ├── Validate fields
  │     └── Register assets into AssetMap/AssetStore
  │
  ├── 4. Fire LoadAssetEvent  ← PLUGIN HOOK POINT
  │     └── Plugins modify loaded assets here
  │
  ├── 5. Assets are now immutable (by convention)
  │
  └── 6. Gameplay begins
```

## Key Types

### AssetRegistry

Global registry that manages all asset stores:

```java
AssetRegistry registry = AssetRegistry.getInstance();
```

### AssetStore

Typed storage for a specific asset type. Supports loading additional assets at runtime:

```java
// Load synthetic drop lists at runtime
ItemDropList.getAssetStore().loadAssets("Plugin:Namespace", syntheticDropLists);
```

### AssetMap / DefaultAssetMap

Read-only map of all loaded assets of a given type:

```java
// Get all block types
DefaultAssetMap<BlockType> blockMap = BlockType.getAssetMap();
Map<String, BlockType> map = blockMap.getAssetMap();

// Get a specific asset by ID
BlockType stone = blockMap.getAsset("Rock_Stone");

// Get by numeric ID
BlockType bt = blockMap.getAsset(numericId);
```

### Static Access Pattern

All asset types provide static accessors:

```java
BlockType.getAssetMap()        // All block types
Item.getAssetMap()             // All items
CraftingRecipe.getAssetMap()   // All crafting recipes
ItemDropList.getAssetStore()   // Drop list store
```

## Asset Packs

### Plugin Asset Packs

When `IncludesAssetPack` is `true` in `manifest.json`, the plugin's resource files under `Server/` are loaded as an asset pack:

```
src/main/resources/
  ├── manifest.json
  └── Server/
      └── Item/
          ├── Block/
          │   └── Fluids/
          │       └── Water_Source.json
          └── Items/
              ├── Rock/
              │   └── Aqua/
              │       └── Rock_Stone_Aqua.json
              └── _Debug/
                  └── Placeholders/
                      └── Placeholder_Full.json
```

Files placed in the `Server/` directory follow the same structure as vanilla assets and can:
- **Add** new assets (new block types, items)
- **Override** existing assets (same ID replaces vanilla definition)

### Asset Pack Resolution Order

1. Vanilla assets load first
2. Plugin assets load in dependency order
3. Later packs override earlier packs for same-ID assets

## JSON Inheritance (Parent Field)

Assets can inherit from other assets using the `Parent` field:

```json
{
  "Parent": "Wood_Softwood_Planks",
  "BlockType": {
    "Textures": [{"All": "BlockTextures/Custom.png"}]
  }
}
```

- The child starts with all parent properties
- Only overridden fields are replaced
- Nested objects are merged (not replaced wholesale)

## LoadAssetEvent

The primary hook for plugins to modify assets after loading:

```java
// In plugin setup():
this.getEventRegistry().register(LoadAssetEvent.class, MyPlugin::onAssetsLoaded);

// Handler:
private static void onAssetsLoaded(LoadAssetEvent event) {
    // All assets are loaded — safe to iterate and modify
    for (var entry : BlockType.getAssetMap().getAssetMap().entrySet()) {
        // Modify block types, recipes, items...
    }
}
```

## See Also

- [Asset Formats Overview](./formats-overview.md)
- [Runtime Asset Mutation](./runtime-mutation.md)
- [Plugin Lifecycle](../plugins/lifecycle.md)
