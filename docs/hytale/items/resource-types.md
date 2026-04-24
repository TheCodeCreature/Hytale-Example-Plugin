---
topic: "ResourceType Assets"
category: "Items"
updated: 2026-04-23
sources: ["decompiled ResourceType.java (server.core.asset.type.item.config)", "decompiled AssetRegistryLoader.java", "decompiled AssetStore.validate()", "decompiled Item.java CODEC", "decompiled BenchRecipeRegistry.java", "decompiled CraftingManager.java", "docs/Resources/resourcetypes/FullBlocks_Hardwood.json"]
---

# ResourceType Assets

## Summary

A **ResourceType** is a named asset that defines a material category (e.g., "Rock", "Wood_Hardwood", "Fuel"). Items declare which ResourceTypes they belong to via a `ResourceTypes` array. Crafting recipes reference ResourceType IDs in their inputs to accept any item of that material category. ResourceType assets are **distinct from the ECS `ResourceType<ECS_TYPE, T>`** — they are JSON-configured item classification assets.

## How It Works

### Asset Registration

ResourceType assets are registered in `AssetRegistryLoader` with:

```java
AssetRegistry.register(
    HytaleAssetStore.builder(ResourceType.class, new DefaultAssetMap())
        .setPath("Item/ResourceTypes")
        .setCodec(ResourceType.CODEC)
        .setKeyFunction(ResourceType::getId)
        .setPacketGenerator(new ResourceTypePacketGenerator())
        .build()
);
```

**Key facts:**
- **Asset path**: `Item/ResourceTypes` (relative to the Server asset root)
- **Full disk path**: `Server/Item/ResourceTypes/<Name>.json`
- **Key type**: `String` (the `Id` field in the JSON, which also doubles as the filename)
- **Asset map**: `DefaultAssetMap<String, ResourceType>` — a simple string-keyed map
- **Packet sync**: ResourceTypes are synced to clients via `UpdateResourceTypes` packet (packet ID 59)

### JSON Asset Format

The `ResourceType.CODEC` defines these fields:

```json
{
  "Name": "Human-readable name (optional)",
  "Description": "Description text (optional)",
  "Icon": "Icons/Path/To/Icon.png (optional, validated against icon resources)"
}
```

The asset **key** (ID) comes from the **filename** (e.g., `Rock.json` → ID `"Rock"`), not from a field inside the JSON. This is standard Hytale asset behavior — the `AssetBuilderCodec` uses `Codec.STRING` as the key codec and the key is derived from the file path.

### Real Example

The only ResourceType-related file found locally is `docs/Resources/resourcetypes/FullBlocks_Hardwood.json`:

```json
{
  "Blocks": [
    "Wood_Hardwood_Planks",
    "Wood_Hardwood_Decorative",
    "Wood_Hardwood_Ornate"
  ]
}
```

> **Note**: This file has a `"Blocks"` array, not the `Name`/`Description`/`Icon` format expected by `ResourceType.CODEC`. This is likely a **BlockGroup** definition misfiled in the resourcetypes folder, or a custom documentation artifact — NOT a real ResourceType asset. The actual base-game ResourceType JSONs are in the game's `Server/Item/ResourceTypes/` directory (not included in the plugin workspace).

### What a Real ResourceType JSON Looks Like

Based on the `ResourceType.CODEC`, a minimal ResourceType asset is:

```json
{}
```

Yes — **all fields are optional**. The ID comes from the filename. A ResourceType with just a name:

```json
{
  "Name": "Place Block"
}
```

A fully specified one:

```json
{
  "Name": "Rock",
  "Description": "Stone-based crafting material",
  "Icon": "Icons/ResourceTypes/Rock.png"
}
```

## How Items Reference ResourceTypes

Items declare their ResourceTypes in a `"ResourceTypes"` array:

```json
{
  "ResourceTypes": [
    { "Id": "Rock" },
    { "Id": "Fuel", "Quantity": 2 }
  ]
}
```

Each entry has:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `Id` | `String` | Yes (non-null validated) | — | Must match a registered ResourceType asset key |
| `Quantity` | `Integer` | No | `1` | How many units of this resource type the item provides (must be > 0) |

### Validation at Load Time

The `Id` field is validated by `ResourceType.VALIDATOR_CACHE.getValidator()`, which is an `AssetKeyValidator<String>`. This validator calls:

```java
// AssetStore.validate()
public void validate(K key, ValidationResults results, ExtraInfo extraInfo) {
    if (key != null) {
        if (this.assetMap.getAsset(key) == null) {
            // Check if the asset is in the current loading context (e.g., same file batch)
            if (extraInfo instanceof AssetExtraInfo) {
                for (AssetExtraInfo.Data data = ...; data != null; data = data.getContainerData()) {
                    if (data.containsAsset(this.tClass, key)) {
                        return; // Found in loading context, valid
                    }
                }
            }
            results.fail("Asset '" + key + "' of type " + ResourceType.class.getName() + " doesn't exist!");
        }
    }
}
```

**What happens when an Item references a non-existent ResourceType ID (like `"PlaceBlock"`):**

1. The `AssetKeyValidator` looks up `"PlaceBlock"` in the ResourceType `AssetStore`
2. If not found, it calls `results.fail(...)` with a validation error message
3. The validation result is logged via `logOrThrowValidatorExceptions()`
4. Depending on configuration, this either:
   - **Logs a SEVERE warning** and continues loading the item (the item loads but with an invalid/unresolvable ResourceType reference)
   - **Throws a `CodecValidationException`**, causing the item asset to fail to load entirely

In practice, the engine logs validation warnings but typically still loads the item. The `ResourceTypes` array on the item will contain the `ItemResourceType` with `id = "PlaceBlock"`, but no matching `ResourceType` asset exists — so any recipe matching against `"PlaceBlock"` as a `ResourceTypeId` will work fine (it's just a string comparison), but the client may show missing icon/name info.

## How Crafting Uses ResourceTypes

The matching is **pure string equality** — no asset lookup is involved at craft time:

```java
// BenchRecipeRegistry.isValidCraftingMaterial()
ItemResourceType[] resourceTypeId = itemStack.getItem().getResourceTypes();
if (resourceTypeId != null) {
    for (ItemResourceType resTypeId : resourceTypeId) {
        if (this.allMaterialResourceType.contains(resTypeId.id)) {
            return true;
        }
    }
}

// CraftingManager.matches() — same pattern
for (ItemResourceType itemResourceType : itemStack.getItem().getResourceTypes()) {
    if (resTypeId.id.equals(upgradeMaterial.getResourceTypeId())) {
        return true;
    }
}
```

**Key insight**: The crafting system compares `ItemResourceType.id` strings against `MaterialQuantity.resourceTypeId` strings. It does NOT look up the ResourceType asset. This means:
- If both the item's ResourceTypes and a recipe's ResourceTypeId use `"PlaceBlock"`, the matching **will work** even if no `"PlaceBlock"` ResourceType asset exists
- The ResourceType asset is primarily for **client display** (icon, name) and **validation** (load-time warnings)

## Can Plugins Define New ResourceTypes?

### Yes — Via Asset Files

Plugins can provide ResourceType JSON files at:

```
src/main/resources/Server/Item/ResourceTypes/PlaceBlock.json
```

The `HytaleAssetStore` loads assets from the `"Item/ResourceTypes"` path, and the plugin's `src/main/resources/Server/` directory is merged into the server's asset root. Since ResourceType uses `DefaultAssetMap` (not an indexed/ordered map), new entries can be added without conflict.

### Example: Creating a "PlaceBlock" ResourceType

Create file `src/main/resources/Server/Item/ResourceTypes/PlaceBlock.json`:

```json
{
  "Name": "Place Block"
}
```

That's it. The filename `PlaceBlock.json` becomes the ResourceType ID `"PlaceBlock"`.

### Alternative: Programmatic Registration

While not directly exposed in the plugin API, ResourceTypes could theoretically be added via:

1. **`LoadAssetEvent`** — After assets load, you could use reflection to access `ResourceType.getAssetMap()` and insert entries
2. **Direct `AssetStore` manipulation** — `ResourceType.getAssetStore()` returns the store, but `DefaultAssetMap.put()` may not be public

The JSON file approach is strongly recommended as it uses the standard asset loading pipeline and handles client sync automatically via `ResourceTypePacketGenerator`.

## Server-Side Class Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `ResourceType` | `server.core.asset.type.item.config` | **The asset class** — ID, Name, Description, Icon |
| `ItemResourceType` | `protocol` | **Protocol DTO** — `id` (String) + `quantity` (int), used on items and in packets |
| `ResourceType` | `protocol` | **Protocol DTO** — `id` + `icon`, sent to clients in `UpdateResourceTypes` |
| `ResourceType` | `component` | **ECS ResourceType** — completely unrelated, part of the ECS component system |
| `ResourceTypePacketGenerator` | `server.core.asset.type.item` | Generates `UpdateResourceTypes` packets for client sync |
| `UpdateResourceTypes` | `protocol.packets.assets` | Network packet (ID 59) — carries ResourceType data to clients |
| `AssetKeyValidator` | `assetstore` | Validates that a referenced ResourceType ID exists in the asset store |

## Gotchas

- **Three classes named `ResourceType`**: The asset config class, the protocol DTO, and the ECS component type are all named `ResourceType` in different packages. The **item/asset one** is `server.core.asset.type.item.config.ResourceType`.
- **Validation is load-time only**: The `AssetKeyValidator` checks at asset load time. At craft time, it's pure string matching.
- **Missing ResourceType assets don't break crafting**: If `"PlaceBlock"` isn't registered as a ResourceType asset but both items and recipes reference it by string, crafting still works. The asset is mainly for client UI display.
- **Client sync**: ResourceTypes are sent to clients. Without a registered asset, the client may show a blank icon/name for items tagged with that ResourceType.
- **The `FullBlocks_Hardwood.json` in `docs/Resources/resourcetypes/`** has a `"Blocks"` array — this is a BlockGroup file, NOT a ResourceType file. Don't use it as a template.

## Action Required for "PlaceBlock"

To properly register a `"PlaceBlock"` ResourceType, create:

**File**: `src/main/resources/Server/Item/ResourceTypes/PlaceBlock.json`
```json
{
  "Name": "Place Block"
}
```

Then the existing items (`Block_Placeholder_Blue.json`, etc.) with `"ResourceTypes": [{"Id": "PlaceBlock"}]` will pass validation.

## See Also

- [ResourceTypeId Resolution](../crafting/resourcetypeid-resolution.md) — How crafting matches ResourceTypeIds
- [Items](./items.md) — Item asset format
