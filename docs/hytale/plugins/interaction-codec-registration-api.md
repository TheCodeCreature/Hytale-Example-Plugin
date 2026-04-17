---
topic: "Interaction Codec Registration API"
category: "Plugin API"
updated: 2026-04-16
sources: ["Interaction.java", "RootInteraction.java", "PluginBase.java", "CodecMapRegistry.java", "AssetCodecMapCodec.java", "InteractionModule.java", "SimpleBlockInteraction.java", "SimpleInteraction.java", "CraftingPlugin.java", "PortalsPlugin.java", "Item.java", "BlockType.java", "ContainedAssetCodec.java"]
---

# Interaction Codec Registration API — Exact Reference

## 1. `Interaction` Class

**Package:** `com.hypixel.hytale.server.core.modules.interaction.interaction.config`

```java
public abstract class Interaction
    implements Operation,
    JsonAssetWithMap<String, IndexedLookupTableAssetMap<String, Interaction>>,
    NetworkSerializable<com.hypixel.hytale.protocol.Interaction>
```

### CODEC Field

```java
@Nonnull
public static final AssetCodecMapCodec<String, Interaction> CODEC = new AssetCodecMapCodec<>(
    Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, data) -> t.data = data, t -> t.data
);
```

**Type:** `AssetCodecMapCodec<String, Interaction>` which extends `StringCodecMapCodec<Interaction, AssetBuilderCodec<String, Interaction>>`.

The discriminator key defaults to `"Type"` — i.e., in JSON:
```json
{ "Type": "OpenBenchPage", "Page": "STRUCTURAL_CRAFTING", ... }
```

### Related Static Codecs

```java
public static final Codec<String> CHILD_ASSET_CODEC = new ContainedAssetCodec<>(Interaction.class, CODEC);
public static final Codec<String[]> CHILD_ASSET_CODEC_ARRAY = new ArrayCodec<>(CHILD_ASSET_CODEC, String[]::new);
```

### ABSTRACT_CODEC (Base Fields)

```java
public static final BuilderCodec<Interaction> ABSTRACT_CODEC = BuilderCodec.abstractBuilder(Interaction.class)
    // Fields: ViewDistance (double), Effects (InteractionEffects), HorizontalSpeedMultiplier (float),
    //         RunTime (float), CancelOnItemChange (boolean), Rules (InteractionRules),
    //         Settings (Map<GameMode, InteractionSettings>), Camera (InteractionCameraSettings)
    .build();
```

### Abstract Methods a Subclass Must Implement

```java
protected abstract void tick0(boolean firstRun, float time, InteractionType type, InteractionContext context, CooldownHandler cooldownHandler);
protected abstract void simulateTick0(boolean firstRun, float time, InteractionType type, InteractionContext context, CooldownHandler cooldownHandler);
public abstract boolean walk(Collector collector, InteractionContext context);
protected abstract com.hypixel.hytale.protocol.Interaction generatePacket();
```

### Static Helpers

```java
public static AssetStore<String, Interaction, IndexedLookupTableAssetMap<String, Interaction>> getAssetStore()
public static IndexedLookupTableAssetMap<String, Interaction> getAssetMap()
```

---

## 2. `getCodecRegistry()` — PluginBase Methods

**Package:** `com.hypixel.hytale.server.core.plugin`

`PluginBase` (superclass of `JavaPlugin`) has three overloads:

### Overload 1 — For StringCodecMapCodec (general polymorphic types)

```java
@Nonnull
public <T, C extends Codec<? extends T>> CodecMapRegistry<T, C> getCodecRegistry(
    @Nonnull StringCodecMapCodec<T, C> mapCodec
)
```

### Overload 2 — For AssetCodecMapCodec (asset types like Interaction)

```java
@Nonnull
public <K, T extends JsonAsset<K>> CodecMapRegistry.Assets<T, ?> getCodecRegistry(
    @Nonnull AssetCodecMapCodec<K, T> mapCodec
)
```

### Overload 3 — For MapKeyMapCodec

```java
@Nonnull
public <V> MapKeyMapRegistry<V> getCodecRegistry(@Nonnull MapKeyMapCodec<V> mapCodec)
```

### `CodecMapRegistry.register()` — The Method You Call

**Package:** `com.hypixel.hytale.server.core.plugin.registry`

```java
public class CodecMapRegistry<T, C extends Codec<? extends T>> implements IRegistry {

    @Nonnull
    public CodecMapRegistry<T, C> register(String id, Class<? extends T> aClass, C codec)

    @Nonnull
    public CodecMapRegistry<T, C> register(Priority priority, String id, Class<? extends T> aClass, C codec)
}
```

### `CodecMapRegistry.Assets.register()` — For Asset Types

```java
public static class Assets<T extends JsonAsset<?>, C extends Codec<? extends T>> extends CodecMapRegistry<T, C> {

    @Nonnull
    public Assets<T, C> register(String id, Class<? extends T> aClass, BuilderCodec<? extends T> codec)

    @Nonnull
    public Assets<T, C> register(Priority priority, String id, Class<? extends T> aClass, BuilderCodec<? extends T> codec)
}
```

Since `Interaction.CODEC` is an `AssetCodecMapCodec`, calling `getCodecRegistry(Interaction.CODEC)` returns `CodecMapRegistry.Assets<Interaction, ?>`. The `register()` method then accepts a `BuilderCodec<? extends Interaction>`.

**Important:** `register()` returns `this` for chaining. Unregistration happens automatically on plugin shutdown via the `unregister` list.

---

## 3. Registration Pattern — Exact Examples from Engine

### CraftingPlugin (registers into Interaction.CODEC via plugin API)

```java
// com.hypixel.hytale.builtin.crafting.CraftingPlugin.setup()
this.getCodecRegistry(Interaction.CODEC)
    .register("OpenBenchPage", OpenBenchPageInteraction.class, OpenBenchPageInteraction.CODEC)
    .register("OpenProcessingBench", OpenProcessingBenchInteraction.class, OpenProcessingBenchInteraction.CODEC);
```

### PortalsPlugin

```java
// com.hypixel.hytale.builtin.portals.PortalsPlugin.setup()
this.getCodecRegistry(Interaction.CODEC)
    .register("EnterPortal", EnterPortalInteraction.class, EnterPortalInteraction.CODEC)
    .register("ReturnPortal", ReturnPortalInteraction.class, ReturnPortalInteraction.CODEC);
```

### CreativeHubPlugin

```java
this.getCodecRegistry(Interaction.CODEC)
    .register("HubPortal", HubPortalInteraction.class, HubPortalInteraction.CODEC);
```

### TeleporterPlugin

```java
this.getCodecRegistry(Interaction.CODEC)
    .register("Teleporter", TeleporterInteraction.class, TeleporterInteraction.CODEC);
```

### InteractionModule (registers directly on CODEC, not via getCodecRegistry)

The core module registers built-in types directly:
```java
Interaction.CODEC.register("Simple", SimpleInteraction.class, SimpleInteraction.CODEC);
Interaction.CODEC.register("PlaceBlock", PlaceBlockInteraction.class, PlaceBlockInteraction.CODEC);
Interaction.CODEC.register("BreakBlock", BreakBlockInteraction.class, BreakBlockInteraction.CODEC);
// ... 40+ more built-in types
```

**Key difference:** Core modules call `Interaction.CODEC.register()` directly. Plugins call `this.getCodecRegistry(Interaction.CODEC).register()` — this wraps the registration with automatic cleanup on shutdown.

---

## 4. `RootInteraction` Class

**Package:** `com.hypixel.hytale.server.core.modules.interaction.interaction.config`

```java
public class RootInteraction
    implements JsonAssetWithMap<String, IndexedLookupTableAssetMap<String, RootInteraction>>,
    NetworkSerializable<com.hypixel.hytale.protocol.RootInteraction>
```

**NOT abstract.** It's a concrete class — you can instantiate it directly. It serves as the **entry point** for interaction chains. Each RootInteraction references one or more `Interaction` IDs by string.

### CODEC Field

```java
@Nonnull
public static final AssetBuilderCodec<String, RootInteraction> CODEC = AssetBuilderCodec.builder(
    RootInteraction.class, RootInteraction::new, Codec.STRING,
    (o, i) -> o.id = i, o -> o.id, (o, i) -> o.data = i, o -> o.data
)
// Fields: Interactions (String[]), Cooldown (InteractionCooldown), Rules (InteractionRules),
//         Settings (Map<GameMode, RootInteractionSettings>), ClickQueuingTimeout (float),
//         RequireNewClick (boolean)
.build();
```

**Note:** `RootInteraction.CODEC` is `AssetBuilderCodec`, NOT `AssetCodecMapCodec`. RootInteraction is NOT polymorphic — there's no `"Type"` discriminator. All RootInteractions are the same class.

### Constructors

```java
public RootInteraction()
public RootInteraction(@Nonnull String id, @Nonnull String... interactionIds)
public RootInteraction(@Nonnull String id, @Nullable InteractionCooldown cooldown, @Nonnull String... interactionIds)
```

### Key Methods

```java
public String getId()
public String[] getInteractionIds()
public void build()  // Resolves interactionIds → Operation[] via Interaction.getAssetMap()
public Operation getOperation(int index)
public int getOperationMax()
public InteractionCooldown getCooldown()
public InteractionRules getRules()
```

### Static Asset Helpers

```java
public static AssetStore<String, RootInteraction, ...> getAssetStore()
public static IndexedLookupTableAssetMap<String, RootInteraction> getAssetMap()
public static RootInteraction getRootInteractionOrUnknown(String id)  // @Deprecated
```

### Child Asset Codecs (for referencing from other assets)

```java
public static final ContainedAssetCodec<String, RootInteraction, ?> CHILD_ASSET_CODEC = ...;
public static final Codec<String[]> CHILD_ASSET_CODEC_ARRAY = ...;
public static final MapCodec<String, HashMap<String, String>> CHILD_ASSET_CODEC_MAP = ...;
```

### Loading Into Asset Store

```java
// Programmatic loading (used by CraftingPlugin):
AssetRegistry.getAssetStore(RootInteraction.class)
    .loadAssets("Hytale:Hytale", List.of(
        new RootInteraction("*MyRoot", "*MyInteraction")
    ));
```

---

## 5. Interaction Class Hierarchy

```
Interaction (abstract)
│   Package: ...interaction.config
│   CODEC: AssetCodecMapCodec (polymorphic, discriminator = "Type")
│   ABSTRACT_CODEC: BuilderCodec (shared base fields)
│
├── SimpleInteraction
│   │   Package: ...interaction.config
│   │   Type ID: "Simple"
│   │   CODEC extends ABSTRACT_CODEC, adds "Next" and "Failed" fields
│   │   Provides: chaining (next/failed interaction branching)
│   │
│   ├── SimpleBlockInteraction (abstract)
│   │   │   Package: ...interaction.config.client
│   │   │   CODEC extends SimpleInteraction.CODEC, adds "UseLatestTarget"
│   │   │   WaitForDataFrom = Client (needs block target from client)
│   │   │   Abstract: interactWithBlock(), simulateInteractWithBlock()
│   │   │
│   │   ├── OpenBenchPageInteraction    — Type: "OpenBenchPage"
│   │   ├── BedInteraction              — Type: "Bed"
│   │   ├── SeatingInteraction          — Type: "Seating"
│   │   ├── SpawnNPCInteraction         — Type: "SpawnNPC"
│   │   ├── SpawnMinecartInteraction    — Type: "SpawnMinecart"
│   │   ├── EnterPortalInteraction      — Type: "EnterPortal"
│   │   ├── ReturnPortalInteraction     — Type: "ReturnPortal"
│   │   └── TeleportConfigInstanceInteraction  — Type: "TeleportConfigInstance"
│   │
│   └── [many more SimpleInteraction subclasses]
│
├── PlaceBlockInteraction               — Type: "PlaceBlock"
├── BreakBlockInteraction               — Type: "BreakBlock"
├── DamageEntityInteraction             — Type: "DamageEntity"
├── ChargingInteraction                 — Type: "Charging"
├── OpenCustomUIInteraction             — Type: "OpenCustomUI"
├── OpenPageInteraction                 — Type: "OpenPage"
└── ... (40+ built-in types)
```

### No `SimpleItemInteraction`

There is **no** `SimpleItemInteraction` class. For interactions that don't need a block target, extend `SimpleInteraction` directly (which has `WaitForDataFrom.None`).

---

## 6. How `"Interactions": { "Use": "My_Custom_Id" }` Resolves

### In Item JSON

The `Item` codec field:
```java
// com.hypixel.hytale.server.core.asset.type.item.config.Item
new KeyedCodec<>("Interactions", new EnumMapCodec<>(InteractionType.class, RootInteraction.CHILD_ASSET_CODEC))
```

- `InteractionType.class` → the enum key (e.g., `"Use"` → `InteractionType.Use`)
- `RootInteraction.CHILD_ASSET_CODEC` → `ContainedAssetCodec<String, RootInteraction, ?>`

### Resolution Chain

```
Item JSON: { "Interactions": { "Use": "My_Custom_Root" } }
                                        │
                                        ▼
ContainedAssetCodec.decode(BsonValue) 
    → bsonValue.isString() → returns the string key "My_Custom_Root"
    → Item stores: Map<InteractionType, String> interactions
                                        │
                                        ▼
At runtime, InteractionManager resolves:
    String rootId = item.getInteractions().get(InteractionType.Use);  // "My_Custom_Root"
    RootInteraction root = RootInteraction.getAssetMap().getAsset(rootId);
    root.build();  // resolves interactionIds → Interaction instances
    // Creates InteractionChain, executes operations
```

### Same Pattern on BlockType

```java
// com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
new KeyedCodec<>("Interactions", new EnumMapCodec<>(InteractionType.class, RootInteraction.CHILD_ASSET_CODEC))
```

### Full Resolution: String → Instance

1. **JSON string** `"My_Custom_Root"` is decoded by `ContainedAssetCodec` as a raw `String` key
2. **At load time**, the string must correspond to a `RootInteraction` loaded into `AssetRegistry.getAssetStore(RootInteraction.class)`
3. **At runtime**, `RootInteraction.getAssetMap().getAsset("My_Custom_Root")` does the lookup
4. The `RootInteraction` contains `interactionIds = ["My_Custom_Interaction"]`
5. `RootInteraction.build()` resolves each ID via `Interaction.getAssetMap().getAsset(id)`
6. Each `Interaction` is a subclass identified by its `"Type"` field in JSON (e.g., `"OpenBenchPage"`)

---

## 7. `FieldCraftingWindow` — Exact Constructor & JSON Data

**Package:** `com.hypixel.hytale.builtin.crafting.window`

```java
public class FieldCraftingWindow extends Window {
    private final JsonObject windowData = new JsonObject();

    public FieldCraftingWindow() {
        super(WindowType.PocketCrafting);
        this.windowData.addProperty("type", BenchType.Crafting.ordinal());    // 0
        this.windowData.addProperty("id", "Fieldcraft");
        this.windowData.addProperty("name", "server.ui.inventory.fieldcraft.title");
        JsonArray categories = new JsonArray();
        for (FieldcraftCategory fc : FieldcraftCategory.getAssetMap().getAssetMap().values()) {
            JsonObject category = new JsonObject();
            category.addProperty("id", fc.getId());
            category.addProperty("icon", fc.getIcon());
            category.addProperty("name", fc.getName());
            Set<String> recipes = CraftingPlugin.getAvailableRecipesForCategory("Fieldcraft", fc.getId());
            if (recipes != null) {
                JsonArray itemsArray = new JsonArray();
                for (String recipeId : recipes) { itemsArray.add(recipeId); }
                category.add("craftableRecipes", itemsArray);
            }
        }
        this.windowData.add("categories", categories);
    }
}
```

### JSON Data Fields Written

| Field | Type | Value | Source |
|-------|------|-------|--------|
| `type` | int | `BenchType.Crafting.ordinal()` = 0 | Constructor |
| `id` | string | `"Fieldcraft"` | Constructor |
| `name` | string | `"server.ui.inventory.fieldcraft.title"` | Constructor |
| `categories` | JsonArray | `[{id, icon, name, craftableRecipes}, ...]` | Constructor |
| `worldMemoriesLevel` | int | From `MemoriesPlugin.get().getMemoriesLevel()` | `onOpen0()` |

### StructuralCrafting Equivalent JSON Data

For `StructuralCraftingWindow`, the JSON data (via `BenchWindow` + `StructuralCraftingWindow`) contains:

| Field | Type | Source |
|-------|------|--------|
| `type` | int | `BenchType.StructuralCrafting.ordinal()` = 3 |
| `id` | string | `bench.getId()` |
| `name` | string | `item.getTranslationKey()` |
| `blockItemId` | string | `item.getId()` |
| `tierLevel` | int | `benchState.getTierLevel()` |
| `selected` | int | `0` (initial slot selection) |
| `allowBlockGroupCycling` | boolean | `structuralBench.shouldAllowBlockGroupCycling()` |
| `alwaysShowInventoryHints` | boolean | `structuralBench.shouldAlwaysShowInventoryHints()` |
| `worldMemoriesLevel` | int | From MemoriesPlugin |
| `nearbyChestCount` | int | From `CraftingManager.feedExtraResourcesSection()` |
| `maxChestCount` | int | From `CraftingConfig.getBenchMaterialChestLimit()` |
| `chestHorizontalRadius` | int | From `CraftingConfig` |
| `chestVerticalRadius` | int | From `CraftingConfig` |

**Note:** StructuralCraftingWindow does NOT write `categories` to JSON — it gets recipes dynamically based on the input item slot.

---

## 8. `Page` Enum

**Package:** `com.hypixel.hytale.protocol.packets.interface_`

```java
public enum Page {
    None(0),
    Bench(1),
    Inventory(2),
    ToolsSettings(3),
    Map(4),
    MachinimaEditor(5),
    ContentCreation(6),
    Custom(7);
}
```

**Yes, `Page.Bench` (value 1) is the correct page** for opening crafting bench windows. Used by `OpenBenchPageInteraction`:

```java
playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, benchWindow);
```

---

## 9. How to Register a Custom Interaction Type from a Plugin

### Minimal Template

```java
public class MyPlugin extends JavaPlugin {
    public MyPlugin(JavaPluginInit init) { super(init); }

    @Override
    protected void setup() {
        // 1. Register the Interaction codec type
        this.getCodecRegistry(Interaction.CODEC)
            .register("MyCustomInteraction", MyCustomInteraction.class, MyCustomInteraction.CODEC);

        // 2. Optionally load programmatic RootInteractions + Interactions
        AssetRegistry.getAssetStore(Interaction.class)
            .loadAssets("MyMod:MyMod", List.of(new MyCustomInteraction("*My_Default")));
        AssetRegistry.getAssetStore(RootInteraction.class)
            .loadAssets("MyMod:MyMod", List.of(new RootInteraction("*My_Default_Root", "*My_Default")));
    }
}
```

### The Custom Interaction Class

Extend `SimpleInteraction` for non-block interactions, `SimpleBlockInteraction` for block-targeted ones:

```java
public class MyCustomInteraction extends SimpleInteraction {

    // CODEC: extends SimpleInteraction.CODEC, add custom fields
    public static final BuilderCodec<MyCustomInteraction> CODEC =
        BuilderCodec.builder(MyCustomInteraction.class, MyCustomInteraction::new, SimpleInteraction.CODEC)
            // .appendInherited(...) for custom JSON fields
            .build();

    protected MyCustomInteraction() {}

    public MyCustomInteraction(String id) {
        super(id);
    }

    @Override
    protected void tick0(boolean firstRun, float time, InteractionType type,
                         InteractionContext context, CooldownHandler cooldownHandler) {
        if (firstRun) {
            // Your logic here
            context.getState().state = InteractionState.Finished;
        }
    }

    @Override
    protected void simulateTick0(boolean firstRun, float time, InteractionType type,
                                 InteractionContext context, CooldownHandler cooldownHandler) {
        // Client-side simulation (can be empty for server-only)
    }

    @Override
    public boolean walk(Collector collector, InteractionContext context) {
        return false; // Return true if this interaction produces data for the collector
    }

    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.SimpleInteraction();
    }
}
```

### Binding to an Item via JSON

```json
{
    "Id": "My_Cool_Item",
    "Parent": "Some_Base_Item",
    "Interactions": {
        "Use": "*My_Default_Root"
    }
}
```

The `"Use"` key maps to `InteractionType.Use` (Q key). The value `"*My_Default_Root"` must be a loaded `RootInteraction` ID, which references your `Interaction` IDs.

### Asset Path for JSON-Defined Interactions

- Interactions: `Item/Interactions/*.json` (loaded by `InteractionModule`)
- RootInteractions: `Item/RootInteractions/*.json` (loaded by `InteractionModule`)

JSON format:
```json
// Item/Interactions/MyCustom.json
{
    "Id": "My_Custom_Interaction",
    "Type": "MyCustomInteraction",
    "RunTime": 0.5
}
```

```json
// Item/RootInteractions/MyCustomRoot.json
{
    "Id": "My_Custom_Root",
    "Interactions": ["My_Custom_Interaction"]
}
```
