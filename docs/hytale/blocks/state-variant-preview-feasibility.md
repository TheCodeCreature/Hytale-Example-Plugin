---
topic: "Block_Placeholder State Variant Preview Feasibility"
category: "Blocks / Asset Config"
updated: 2026-04-26
sources: [
  "decompiled BlockType.java (config)",
  "decompiled BlockType.java (protocol)",
  "decompiled StateData.java",
  "decompiled Item.java",
  "decompiled ItemBase.java",
  "decompiled BlockTypePacketGenerator.java",
  "decompiled BlockTypeTextures.java",
  "Wood_Oak_Trunk.json",
  "Wood_Hardwood_Fence.json",
  "Bench_Stencil.json",
  "Block_Placeholder_Green.json"
]
---

# State Variant Preview Feasibility Research

## Executive Summary

**Verdict: Technically possible but impractical for the PlaceBlock use case.**

The engine's `State` system creates **separate BlockType assets** for each state definition. Each state definition inherits from the parent BlockType and can override any visual field (textures, model, draw type, tint, etc.). However, the approach has fundamental scaling and structural problems that make it unsuitable for per-recipe placeholder preview.

---

## Q1: Can a state variant reference another block type's visuals?

**Answer: No reference mechanism exists. Each state must inline all visual fields.**

### How State Definitions Work

From [StateData.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/StateData.java):

```java
// StateData.generateBlockKey():
private static String generateBlockKey(@Nonnull AssetExtraInfo<String> extraInfo) {
    String key = extraInfo.getKey();
    return "*" + key + "_" + extraInfo.peekKey('_');
}
```

The `"State" → "Definitions"` block uses `ContainedAssetCodec<>(BlockType.class, BlockType.CODEC, ContainedAssetCodec.Mode.INJECT_PARENT, StateData::generateBlockKey)`:

- Each definition key (e.g. `"Stripped"`) creates a **new BlockType asset** registered in the BlockTypeAssetMap
- The generated key follows the pattern `*{ParentBlockId}_State_Definitions_{StateName}` (based on `peekKey('_')`)
- The state definition **inherits all fields from the parent** via `ContainedAssetCodec.Mode.INJECT_PARENT`, then applies overrides

### JSON Example (Wood_Oak_Trunk.json)

```json
"State": {
  "Definitions": {
    "Stripped": {
      "Textures": [
        {
          "Sides": "BlockTextures/Stripped_Log_Side.png",
          "UpDown": "BlockTextures/Stripped_Log_Side.png",
          "Weight": 1
        }
      ],
      "Gathering": { ... }
    }
  }
}
```

This creates a BlockType with ID `*Wood_Oak_Trunk_State_Definitions_Stripped` that inherits everything from `Wood_Oak_Trunk`'s BlockType but overrides Textures.

### Key Finding

There is **no JSON field to say "use BlockType X's visuals"**. A state definition IS a BlockType definition — it must inline all fields it wants to override. Fields not specified are inherited from the parent.

**To mimic `Block_Cobble_Wall`'s appearance, you must copy every visual field from `Block_Cobble_Wall`'s JSON into the state definition.**

---

## Q2: What fields must a state variant's BlockType match for correct preview?

**The block preview uses the full protocol BlockType packet.** All visual fields matter.

### Visual Fields in protocol.BlockType

From [protocol/BlockType.java](../../.tmp_hytale_src/com/hypixel/hytale/protocol/BlockType.java#L28-L100):

| Field | Type | Purpose |
|-------|------|---------|
| `drawType` | `DrawType` (Cube, Model, Empty) | Rendering mode |
| `cubeTextures` | `BlockTextures[]` | Per-face textures for Cube draw type |
| `cubeSideMaskTexture` | `String` | Side mask overlay texture |
| `cubeShadingMode` | `ShadingMode` | Shading behavior |
| `model` | `String` | Model path for Model draw type |
| `modelTexture` | `ModelTexture[]` | Textures for custom model |
| `modelScale` | `float` | Model scale factor |
| `modelAnimation` | `String` | Animation path |
| `looping` | `boolean` | Animation loop flag |
| `tint` | `Tint` (6 face colors) | Per-face color tint |
| `biomeTint` | `Tint` | Biome-dependent tint |
| `opacity` | `Opacity` | Solid/Transparent |
| `requiresAlphaBlending` | `boolean` | Alpha blend flag |
| `shaderEffect` | `ShaderType[]` | Shader effects |
| `light` | `ColorLight` | Emitted light |
| `randomRotation` | `RandomRotation` | Random rotation mode |
| `variantRotation` | `VariantRotation` | Variant rotation mode |
| `rotationYawPlacementOffset` | `Rotation` | Placement yaw offset |
| `placementSettings` | `BlockPlacementSettings` | Preview visibility, etc. |
| `connectedBlockRuleSet` | `ConnectedBlockRuleSet` | Connected block rules |

### Minimum Fields for Visual Match

For a Cube block: `DrawType`, `Textures` (All/Sides/UpDown/per-face), `Tint`, `Opacity`, `CubeShadingMode`

For a Model block: `DrawType`, `CustomModel`, `CustomModelTexture`, `CustomModelScale`, `Tint`, `Opacity`

Plus: `PlacementSettings.BlockPreviewVisibility` must be `"Default"` or match the source.

---

## Q3: Can we programmatically read a BlockType's config at runtime?

**Yes — all fields are accessible.**

### Available Methods

From [config/BlockType.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType.java):

```java
// Lookup any BlockType by string ID:
BlockType bt = BlockType.getAssetMap().getAsset("Block_Cobble_Wall");

// Access fields (all are `protected`, accessible via reflection):
bt.drawType          // DrawType enum
bt.textures          // BlockTypeTextures[] (contains per-face paths)
bt.customModel       // String model path
bt.customModelTexture // CustomModelTexture[] 
bt.customModelScale  // float
bt.tintUp/Down/...   // Color[] per face
bt.opacity           // Opacity enum
bt.cubeShadingMode   // ShadingMode enum
bt.placementSettings // BlockPlacementSettings
bt.connectedBlockRuleSet // ConnectedBlockRuleSet

// Full protocol conversion:
protocol.BlockType packet = bt.toPacket();
// packet contains ALL visual fields in serializable form
```

### BlockTypeTextures field access

From [BlockTypeTextures.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockTypeTextures.java):

```java
// Fields (all protected):
protected String up, down, north, south, east, west;  // texture paths
protected int weight;
```

### toPacket() serialization (line 1048-1283)

The `toPacket()` method in config.BlockType reads every field and populates a `protocol.BlockType`. This is exactly what `UpdateBlockTypes` sends to the client. Key mappings:

- `this.textures[i].toPacket(totalWeight)` → `packet.cubeTextures`
- `this.customModelTexture[i].toPacket(totalWeight)` → `packet.modelTexture`  
- `this.customModel` → `packet.model`
- `this.drawType` → `packet.drawType`
- `ColorParseUtil.colorToARGBInt(this.tintUp[0])` → `packet.tint.top` (etc.)
- `this.opacity` → `packet.opacity`

**All fields can be read at runtime via reflection on the config.BlockType.**

---

## Q4: Can we read an Item's icon path at runtime?

**Yes.**

From [Item.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L874):

```java
public String getIcon() {
    return this.icon;  // e.g. "Icons/ItemsGenerated/Wood_Oak_Trunk.png"
}

public AssetIconProperties getIconProperties() {
    return this.iconProperties;  // Scale, Translation, Rotation for 3D icon rendering
}
```

Usage:
```java
Item item = Item.getAssetMap().getAsset("Block_Cobble_Wall");
String iconPath = item.getIcon();  // returns the icon texture path string
```

The `ItemBase` protocol packet (sent to client) contains:
```java
packet.icon = this.icon;              // String texture path
packet.iconProperties = ...;          // AssetIconProperties (scale, translation, rotation)
```

**Note:** The Item's `"Icon"` field in JSON is a simple string path. This can be written into the placeholder's JSON.

---

## Q5: JSON file writing from a server command

**A plugin CAN write files, but there are significant constraints.**

### Where assets load from

The mod's assets load from:
- Compiled: `build/resources/main/Server/Item/Items/...`
- Development: `src/main/resources/Server/Item/Items/...`
- Runtime mod dir: `run/mods/CodeCreature.Development/Server/Item/Items/...`

### Can a plugin write files at runtime?

Java `File` I/O works — plugins run in a standard JVM. You can write to any writable path. However:

1. **Source files (`src/main/resources/`)**: Writable but has no effect on the running server — assets are loaded from the compiled build output
2. **Build output (`build/resources/main/`)**: Writable, but these are generated by Gradle and would be overwritten on next build
3. **Mod directory (`run/mods/...`)**: This is the best candidate — it's the runtime asset directory the server reads from

### Do existing systems write JSON files?

**No.** Neither `StencilBookRecipeMutator` nor `DropScaler` write JSON files. They mutate assets **in memory** during `LoadAssetEvent`:
- `StencilBookRecipeMutator` creates shadow `CraftingRecipe` objects and registers them via `CraftingRecipe.getAssetStore().loadAssets()`
- `DropScaler` mutates `BlockGathering` objects by cloning and modifying them in memory

The idea would be to generate the JSON as a **build-time or offline tool**, not at runtime.

---

## Q6: How many placeable recipes exist?

### Enumeration logic

From [StencilBookRecipeMutator.java](../../src/main/java/com/UnobstructedThirdPerson/placeblock/StencilBookRecipeMutator.java#L59-L65):

```java
for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
    if (recipe == null) continue;
    BenchRequirement[] reqs = recipe.getBenchRequirement();
    if (reqs == null || reqs.length == 0) continue;
    if (!hasPlaceableOutput(recipe)) continue;
    // ... creates shadow
}
```

`hasPlaceableOutput` checks: `recipe.getPrimaryOutput().getItemId()` → `Item.getAssetMap().getAsset(itemId).getBlockId() != null`.

From [StencilSelectionPage.java](../../src/main/java/com/UnobstructedThirdPerson/placeblock/ui/StencilSelectionPage.java#L63-L75), the similar enumeration:
```java
for (CraftingRecipe recipe : CraftingRecipe.getAssetMap().getAssetMap().values()) {
    if (recipe == null) continue;
    // Filters: has output, output item has blockId
    allRecipes.add(new RecipeEntry(id, outputItemId, outputItem.getBlockId(), true));
}
```

### Estimated count

The log output `"Registered N shadow Stencil recipes"` gives the exact count at runtime. Based on typical Hytale content:
- **Builders bench**: ~100-200 block recipes (walls, stairs, fences, pillars, etc.)
- **Furniture bench**: ~50-100 furniture recipes (benches, chairs, etc.)
- **Other benches**: ~20-50 miscellaneous

**Estimated total: 200-400 placeable recipes.**

Each state definition creates a full BlockType in the asset registry. At 200-400 state definitions, each with a full BlockType, this would:
- Add 200-400 entries to the BlockTypeAssetMap
- Increase the `UpdateBlockTypes` init packet by 200-400 × ~1KB = 200-400KB per player connection
- All sent regardless of whether the player uses the PlaceBlock system

---

## Q7: Asset reload requirements

**Server restart is required. No hot-reload for JSON files.**

### How assets load

Assets are loaded during server startup via the `AssetRegistry` → `AssetStore` pipeline. The `LoadAssetEvent` fires after all assets are loaded. There is no file-watcher or hot-reload mechanism for Item/BlockType JSON files.

### The asset editor (development tool)

The in-game asset editor (`assetEditor/`) can modify and reload individual assets, but this uses `AssetUpdateQuery` and `UpdateBlockTypes` packets internally — it's a development tool, not a production mechanism.

### Implications

If you write JSON files, the server must be restarted for them to take effect. This makes the approach a **build-time code generation** strategy, not a runtime one.

---

## Q8: Protocol BlockType fields (complete list)

From [protocol/BlockType.java](../../.tmp_hytale_src/com/hypixel/hytale/protocol/BlockType.java#L16-L100):

```java
public class BlockType {
    // Identity
    @Nullable public String item;           // Associated item ID
    @Nullable public String name;           // Block type name/ID
    public boolean unknown;                  // Unknown block flag
    
    // Rendering
    @Nonnull public DrawType drawType;       // Cube, Model, Empty
    @Nonnull public Opacity opacity;         // Solid, Transparent
    @Nullable public ShaderType[] shaderEffect;
    public boolean requiresAlphaBlending;
    
    // Cube textures
    @Nullable public BlockTextures[] cubeTextures;
    @Nullable public String cubeSideMaskTexture;
    @Nonnull public ShadingMode cubeShadingMode;
    
    // Model
    @Nullable public String model;           // Model path
    @Nullable public ModelTexture[] modelTexture;
    public float modelScale;
    @Nullable public String modelAnimation;
    public boolean looping;
    
    // Color
    @Nullable public Tint tint;              // 6-face tint (top/bottom/front/back/left/right)
    @Nullable public Tint biomeTint;
    @Nullable public Color particleColor;
    @Nullable public ColorLight light;
    
    // Rotation
    @Nonnull public RandomRotation randomRotation;
    @Nonnull public VariantRotation variantRotation;
    @Nonnull public Rotation rotationYawPlacementOffset;
    
    // Physics / Material
    @Nonnull public BlockMaterial material;
    public int hitbox;
    public int interactionHitbox;
    public int maxSupportDistance;
    @Nonnull public BlockSupportsRequiredForType blockSupportsRequiredFor;
    @Nullable public Map<BlockNeighbor, RequiredBlockFaceSupport[]> support;
    @Nullable public Map<BlockNeighbor, BlockFaceSupport[]> supporting;
    public boolean ignoreSupportWhenPlaced;
    
    // Sound / Particles
    public int blockSoundSetIndex;
    public int ambientSoundEventIndex;
    @Nullable public ModelParticle[] particles;
    @Nullable public String blockParticleSetId;
    @Nullable public String blockBreakingDecalId;
    
    // Transition
    public int group;
    @Nullable public String transitionTexture;
    @Nullable public int[] transitionToGroups;
    public int transitionToTag;
    
    // Interaction
    @Nullable public BlockMovementSettings movementSettings;
    @Nullable public BlockFlags flags;
    @Nullable public String interactionHint;
    @Nullable public Map<InteractionType, Integer> interactions;
    
    // Metadata
    @Nullable public BlockGathering gathering;
    @Nullable public BlockPlacementSettings placementSettings;
    @Nullable public ModelDisplay display;
    @Nullable public RailConfig rail;
    @Nullable public int[] tagIndexes;
    @Nullable public Bench bench;
    @Nullable public ConnectedBlockRuleSet connectedBlockRuleSet;
    
    // States
    @Nullable public Map<String, Integer> states;  // state name → BlockType index
}
```

---

## Feasibility Analysis

### What the approach would require

For each placeable recipe, add a state definition to `Block_Placeholder_Green.json`:

```json
"State": {
  "Definitions": {
    "Recipe_Cobble_Wall": {
      "DrawType": "Cube",
      "Textures": [{ "All": "BlockTextures/Cobble_Wall.png", "Weight": 1 }],
      "Tint": ["#ffffff"],
      "Opacity": "Solid",
      "Material": "Solid"
    },
    "Recipe_Wood_Hardwood_Fence": {
      "DrawType": "Model",
      "CustomModel": "Blocks/Structures/Fences/Fence_Hardwood.blockymodel",
      "CustomModelTexture": [{ "Texture": "...", "Weight": 1 }],
      "Opacity": "Transparent"
    }
    // ... 200-400 more definitions
  }
}
```

### Problems

| Problem | Severity | Detail |
|---------|----------|--------|
| **JSON file size** | High | 200-400 state definitions × ~20 lines each = 4,000-8,000 line JSON file. Maintainability nightmare. |
| **BlockType registry bloat** | High | Each state creates a full BlockType in the global asset map. 200-400 extra BlockTypes sent to ALL clients on connect, not just PlaceBlock users. |
| **No reference mechanism** | High | Cannot say "use Block_Cobble_Wall's visuals" — must duplicate all visual fields. If the source block changes, the state definition is stale. |
| **Static, not dynamic** | High | States are baked at asset load time. Adding a new recipe requires regenerating the JSON and restarting the server. The current `UpdateBlockTypes` approach is dynamic. |
| **State switching** | Medium | States are per-BlockType, not per-ItemStack. All instances of the same block type share the same state. To have 9 hotbar slots showing different recipes simultaneously, you'd still need 9 separate placeholder items (which you already have with Green_0 through Green_8). |
| **Icon not in BlockType** | Medium | The `"Icon"` field is on the **Item**, not the BlockType. State definitions can only override BlockType fields. To change the inventory icon, you'd need Item-level state support — which doesn't exist for Items in the same way. |
| **Connected blocks** | Medium | Fences, walls etc. use `ConnectedBlockRuleSet` which references specific block type IDs. State definitions can't replicate connected block behavior without duplicating the full rule set and all referenced blocks. |

### What the current approach (UpdateBlockTypes) does better

The existing `BlockPreviewReskinManager` approach:
- ✅ Zero asset bloat — no extra BlockTypes in the registry
- ✅ Dynamic — works for any recipe without JSON changes
- ✅ Per-player — different players see different block previews
- ✅ Per-slot — each hotbar slot independently reskinned
- ✅ Always current — reads target BlockType's live config via `toPacket()`
- ❌ Requires per-player packet management
- ❌ Requires 9 variant items (Green_0 through Green_8)

### Conclusion

The state variant approach is **not recommended** for the PlaceBlock system because:

1. It trades runtime complexity (packets) for build-time complexity (JSON generation) without reducing overall complexity
2. It adds permanent registry bloat for all players
3. It can't handle the per-slot requirement without the same variant items needed today
4. It can't update the Item icon (only BlockType visuals)
5. It creates a maintenance burden: every time a block's visuals change, the placeholder states must be regenerated

The current `UpdateBlockTypes` packet approach remains the better architecture. If the goal is to reduce runtime packet overhead, a more promising direction would be to explore whether the 9 Green variant items can be reduced or whether the reskin packets can be batched more efficiently.

## See Also
- [server-client-boundary.md](../server-client-boundary.md)
- [design-block-preview-reskin.md](../../Plans/design-block-preview-reskin.md)
