---
topic: "Portable Bench Feasibility Analysis"
category: "Crafting"
updated: 2026-04-16
sources: ["FieldCraftingWindow.java", "BenchWindow.java", "CraftingWindow.java", "SimpleCraftingWindow.java", "DiagramCraftingWindow.java", "StructuralCraftingWindow.java", "Window.java", "BlockWindow.java", "WindowManager.java", "PageManager.java", "WindowType.java", "BenchType.java", "InteractionType.java", "InteractionManager.java", "GamePacketHandler.java", "OpenBenchPageInteraction.java", "CraftingPlugin.java", "Player.java", "PlayerRef.java", "OpenWindow.java"]
---

# Portable Bench Feasibility Analysis — Deep Dive

## Summary

Can a held item open a Builder's Bench (StructuralCrafting) UI when the player presses Q, without a physical bench block in the world? **Not via pure JSON configuration.** The bench UI system fundamentally requires a `BenchState` from a physical block. However, a **server plugin can work around this** using either a ghost-bench approach or (more promisingly) by mimicking the `FieldCraftingWindow` pattern with a custom `Window`.

This document now contains the complete decompiled-source-backed analysis.

---

## 1. FieldCraftingWindow — Full Analysis

**Source:** `com.hypixel.hytale.builtin.crafting.window.FieldCraftingWindow`

### Class Declaration & Inheritance

```java
public class FieldCraftingWindow extends Window {
```

**Key:** Extends `Window` directly. NOT `BlockWindow`, NOT `BenchWindow`. This means it has **zero block dependency** — no coordinates, no block type, no `ValidatedWindow` interface, no distance checks.

### Constructor — Exact Signature

```java
public FieldCraftingWindow() {
    super(WindowType.PocketCrafting);
    this.windowData.addProperty("type", BenchType.Crafting.ordinal());   // = 0
    this.windowData.addProperty("id", "Fieldcraft");
    this.windowData.addProperty("name", "server.ui.inventory.fieldcraft.title");
    JsonArray categories = new JsonArray();

    for (FieldcraftCategory fieldcraftCategory : FieldcraftCategory.getAssetMap().getAssetMap().values()) {
        JsonObject category = new JsonObject();
        category.addProperty("id", fieldcraftCategory.getId());
        category.addProperty("icon", fieldcraftCategory.getIcon());
        category.addProperty("name", fieldcraftCategory.getName());
        Set<String> recipes = CraftingPlugin.getAvailableRecipesForCategory("Fieldcraft", fieldcraftCategory.getId());
        if (recipes != null) {
            JsonArray itemsArray = new JsonArray();
            for (String recipeId : recipes) {
                itemsArray.add(recipeId);
            }
            category.add("craftableRecipes", itemsArray);
        }
    }

    this.windowData.add("categories", categories);
}
```

### How It Builds JSON Window Data

The `windowData` JSON contains:
| Field | Value | Purpose |
|-------|-------|---------|
| `type` | `BenchType.Crafting.ordinal()` = `0` | Tells client which bench type renderer to use |
| `id` | `"Fieldcraft"` | Bench ID for recipe lookup |
| `name` | `"server.ui.inventory.fieldcraft.title"` | i18n key for window title |
| `categories` | JSON array of `{id, icon, name, craftableRecipes}` | Category tabs with recipe lists |
| `worldMemoriesLevel` | integer (set in `onOpen0`) | Memories system integration |

### Action Handling

```java
@Override
public void handleAction(Ref<EntityStore> ref, Store<EntityStore> store, WindowAction action) {
    if (action instanceof CraftRecipeAction craftAction) {
        CraftingManager craftingManager = store.getComponent(ref, CraftingManager.getComponentType());
        if (CraftingWindow.craftSimpleItem(store, ref, craftingManager, craftAction)) {
            SoundUtil.playSoundEvent2d(ref, TempAssetIdUtil.getSoundEventIndex("SFX_Player_Craft_Item_Inventory"), SoundCategory.UI, store);
        }
    }
}
```

**Critical detail:** Delegates to `CraftingWindow.craftSimpleItem()` — a static method that takes materials from the player's combined backpack/storage/hotbar inventory. No bench-side container, no external resource section.

### Registration as Client-Requestable

In `CraftingPlugin.setup()`:
```java
Window.CLIENT_REQUESTABLE_WINDOW_TYPES.put(WindowType.PocketCrafting, FieldCraftingWindow::new);
```

This means the **client can initiate** a PocketCrafting window (e.g., when opening inventory crafting tab). The `GamePacketHandler` processes this:

```java
// GamePacketHandler.handle(ClientOpenWindow)
Supplier<? extends Window> supplier = Window.CLIENT_REQUESTABLE_WINDOW_TYPES.get(packet.type);
// ... creates window via supplier.get(), calls windowManager.clientOpenWindow()
```

Server-opened windows do NOT need to be in `CLIENT_REQUESTABLE_WINDOW_TYPES`.

---

## 2. WindowType Enum — Complete List

**Source:** `com.hypixel.hytale.protocol.packets.window.WindowType`

```java
public enum WindowType {
    Container(0),
    PocketCrafting(1),
    BasicCrafting(2),
    DiagramCrafting(3),
    StructuralCrafting(4),
    Processing(5),
    Memories(6);
}
```

### Mapping to Window Classes

| WindowType | Used By | BenchType in JSON |
|------------|---------|-------------------|
| `Container(0)` | Generic inventory containers | N/A |
| `PocketCrafting(1)` | `FieldCraftingWindow` | `Crafting(0)` |
| `BasicCrafting(2)` | `SimpleCraftingWindow` | `Crafting(0)` |
| `DiagramCrafting(3)` | `DiagramCraftingWindow` | `DiagramCrafting(2)` |
| `StructuralCrafting(4)` | `StructuralCraftingWindow` | `StructuralCrafting(3)` |
| `Processing(5)` | Processing bench windows | `Processing(1)` |
| `Memories(6)` | Memories system | N/A |

### BenchType Enum

```java
public enum BenchType {
    Crafting(0),
    Processing(1),
    DiagramCrafting(2),
    StructuralCrafting(3);
}
```

**There IS a `WindowType.StructuralCrafting(4)`** — the client has dedicated rendering for it. But there is **NO `WindowType` that means "portable structural crafting"** — `PocketCrafting` is the only portable type and is hardwired to `BenchType.Crafting`.

---

## 3. Window Inheritance Hierarchy — Complete Chain

```
Window (abstract)
│   Fields: windowType, isDirty, needRebuild, id, manager, playerRef
│   Key: closeEventRegistry, CLIENT_REQUESTABLE_WINDOW_TYPES static map
│   Constructor: Window(WindowType)
│
├── FieldCraftingWindow  ←── WindowType.PocketCrafting
│       NO block dependency, NO inventory containers
│       Uses CraftingWindow.craftSimpleItem() for simple recipe execution
│
└── BlockWindow (abstract, implements ValidatedWindow)
    │   Fields: x, y, z, blockType, rotationIndex, maxDistance
    │   Constructor: BlockWindow(WindowType, x, y, z, rotationIndex, BlockType)
    │   validate(): checks block exists, player within 7.0 distance
    │
    └── BenchWindow (abstract, implements MaterialContainerWindow)
        │   Fields: bench (Bench), benchState (BenchState), windowData (JsonObject)
        │   Fields: extraResourcesSection (MaterialExtraResourcesSection)
        │   Constructor: BenchWindow(WindowType, BenchState)
        │   onOpen0: sets CraftingManager.setBench(), fetches nearby chests
        │   onClose0: calls CraftingManager.clearBench()
        │
        └── CraftingWindow (abstract)
            │   Constructor: CraftingWindow(WindowType, BenchState)
            │   Builds categories JSON from bench.getCategories()
            │   Static: craftSimpleItem() — crafts from player inventory
            │
            ├── SimpleCraftingWindow
            │       WindowType.BasicCrafting
            │       Handles CraftRecipeAction, TierUpgradeAction
            │       Uses CraftingManager.queueCraft() for timed recipes
            │
            ├── DiagramCraftingWindow
            │       WindowType.DiagramCrafting
            │       (not fully analyzed here)
            │
            └── StructuralCraftingWindow (implements ItemContainerWindow)
                    WindowType.StructuralCrafting
                    Fields: inputContainer (SimpleItemContainer, 1 slot)
                    Fields: optionsContainer (SimpleItemContainer, 64 slots)
                    Fields: combinedItemContainer, selectedSlot
                    Handles: SelectSlotAction, CraftRecipeAction, ChangeBlockAction
                    Block group cycling support
```

### Critical Difference: FieldCraftingWindow vs StructuralCraftingWindow

| Aspect | FieldCraftingWindow | StructuralCraftingWindow |
|--------|--------------------|-----------------------|
| Extends | `Window` | `CraftingWindow → BenchWindow → BlockWindow → Window` |
| Block required | **No** | **Yes** (BenchState) |
| WindowType | `PocketCrafting(1)` | `StructuralCrafting(4)` |
| BenchType in data | `Crafting(0)` | `StructuralCrafting(3)` |
| Has item containers | **No** | **Yes** (input + options slots) |
| Implements ItemContainerWindow | **No** | **Yes** |
| Implements MaterialContainerWindow | **No** | **Yes** (via BenchWindow) |
| Inventory in OpenWindow packet | `null` | `InventorySection` (input + options) |
| ExtraResources in OpenWindow packet | `null` | `ExtraResources` (nearby chests) |
| Block validation | **None** | `BlockWindow.validate()` (7m range, block must exist) |
| Crafting mechanism | `CraftingWindow.craftSimpleItem()` | `CraftingManager.queueCraft()` + slot selection |
| Block group cycling | **No** | **Yes** (`ChangeBlockAction`) |

**Conclusion: FieldCraftingWindow CANNOT handle StructuralCrafting.** The `PocketCrafting` WindowType tells the client to render a simple recipe list UI. `StructuralCraftingWindow` sends inventory sections (input slot + 64 option slots) and expects `SelectSlotAction`/`ChangeBlockAction` — the `PocketCrafting` client renderer doesn't support any of this.

---

## 4. PageManager.setPageWithWindows() — Full Call Chain

**Source:** `com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager`

### Method Signature

```java
public boolean setPageWithWindows(
    Ref<EntityStore> ref,
    Store<EntityStore> store,
    Page page,
    boolean canCloseThroughInteraction,
    Window... windows
)
```

### Full Call Chain: Server → Client

```
1. playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)
   │
   ├── 2. windowManager.openWindows(ref, store, windows)
   │       │
   │       └── For each window:
   │           3. windowManager.openWindow(ref, window, store)
   │               │
   │               ├── Assigns window ID (atomic incrementing int, starts at 1)
   │               ├── window.init(playerRef, this) — stores refs
   │               ├── window.onOpen(ref, store) — calls onOpen0()
   │               │       FieldCraftingWindow: sets worldMemoriesLevel, invalidates
   │               │       BenchWindow: sets CraftingManager.setBench(), fetches chests
   │               │
   │               └── Returns OpenWindow packet:
   │                   new OpenWindow(id, window.getType(), window.getData().toString(),
   │                                  inventorySection, extraResources)
   │
   ├── 4. this.setPage(ref, store, page, canCloseThroughInteraction)
   │       │
   │       └── playerRef.getPacketHandler().writeNoCache(new SetPage(page, canCloseThroughInteraction))
   │           [PACKET: SetPage → client changes UI page to Bench]
   │
   └── 5. For each OpenWindow packet:
           playerRef.getPacketHandler().write(packet)
           [PACKET: OpenWindow → client creates window UI]
```

### Page Enum Values

```java
public enum Page {
    None(0), Bench(1), Inventory(2), ToolsSettings(3),
    Map(4), MachinimaEditor(5), ContentCreation(6), Custom(7);
}
```

### Alternative: openCustomPageWithWindows()

```java
public boolean openCustomPageWithWindows(
    Ref<EntityStore> ref, Store<EntityStore> store,
    CustomUIPage page, Window... windows
)
```
Same pattern but uses Custom UI system instead of a built-in Page.

---

## 5. Q Key / Use Interaction Chain

### InteractionType Enum (Complete)

**Source:** `com.hypixel.hytale.protocol.InteractionType`

```java
Primary(0), Secondary(1), Ability1(2), Ability2(3), Ability3(4),
Use(5), Pick(6), Pickup(7), CollisionEnter(8), CollisionLeave(9),
Collision(10), EntityStatEffect(11), SwapTo(12), SwapFrom(13),
Death(14), Wielding(15), ProjectileSpawn(16), ProjectileHit(17),
ProjectileMiss(18), ProjectileBounce(19), Held(20), HeldOffhand(21),
Equipped(22), Dodge(23), GameModeSwap(24)
```

`Use(5)` = the Q key.

### How the Use Key Works — Full Chain

```
Player presses Q with item in hand
        │
        ▼
Client resolves item's Interactions.Use → RootInteraction ID
Client creates InteractionChainData (type=Use, target info)
Client sends SyncInteractionChain packet
        │
        ▼
GamePacketHandler receives packet
  → queues into interactionPacketQueue
        │
        ▼
InteractionManager.tick() processes queue
  → tryConsumePacketQueue()
  → Resolves RootInteraction from held item's interaction map
  → Creates InteractionChain with the RootInteraction
  → Executes interaction chain operations
        │
        ▼
If target is a block with bench:
  OpenBenchPageInteraction.interactWithBlock() runs
        │
        ▼
If target is not a block / custom interaction:
  Your custom Interaction subclass runs
```

### InteractionManager Key Details

**Source:** `com.hypixel.hytale.server.core.entity.InteractionManager`

- Component on player entities, processes all interaction chains
- Maintains `Int2ObjectMap<InteractionChain> chains` — active interaction chains
- Uses `CooldownHandler` for interaction cooldowns
- Synchronizes with client via `SyncInteractionChain` packets
- Each chain has a `RootInteraction` → resolves to `Interaction` implementation → executes `Operation`s

### Can a Plugin Intercept Use on a Held Item?

**Yes, via custom Interaction types.** The `CraftingPlugin` registers custom interaction codecs:
```java
this.getCodecRegistry(Interaction.CODEC)
    .register("OpenBenchPage", OpenBenchPageInteraction.class, OpenBenchPageInteraction.CODEC)
    .register("OpenProcessingBench", OpenProcessingBenchInteraction.class, OpenProcessingBenchInteraction.CODEC);
```

A plugin could register its own `Interaction` type (e.g., `"OpenPortableBench"`) and bind it to an item's `Use` interaction via JSON.

---

## 6. Window Creation from Plugin — Exact API Path

### Server-Initiated Window Opening

```java
// 1. Get Player component (must be on world thread)
Player playerComponent = store.getComponent(ref, Player.getComponentType());

// 2. Get PageManager
PageManager pageManager = playerComponent.getPageManager();

// 3. Create your window
Window myWindow = new MyCustomWindow();

// 4. Open with page change (sets UI to Bench page + opens window)
pageManager.setPageWithWindows(ref, store, Page.Bench, true, myWindow);

// OR: Open window without page change
WindowManager windowManager = playerComponent.getWindowManager();
OpenWindow packet = windowManager.openWindow(ref, myWindow, store);
if (packet != null) {
    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
    playerRef.getPacketHandler().write(packet);
}
```

### Thread Safety

All window/page operations MUST run on the world thread. From outside the world thread:
```java
world.execute(() -> {
    // Safe to access Player, PageManager, WindowManager here
    Player player = store.getComponent(ref, Player.getComponentType());
    player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
});
```

### PlayerRef Accessible APIs

`PlayerRef` provides:
- `getPacketHandler()` — for sending raw packets
- `getReference()` — to get the entity `Ref<EntityStore>`
- `getComponent(componentType)` — deprecated but works
- `sendMessage(Message)` — chat messages
- `referToServer(host, port)` — server transfers

`Player` (separate component) provides:
- `getPageManager()` — for opening pages/windows
- `getWindowManager()` — for direct window management
- `getInventory()` — player inventory access

---

## 7. OpenBenchPageInteraction — How Bench Block Interaction Works

**Source:** `com.hypixel.hytale.builtin.crafting.interaction.OpenBenchPageInteraction`

### Static Instances Registered at Startup

```java
public static final OpenBenchPageInteraction SIMPLE_CRAFTING =
    new OpenBenchPageInteraction("*Simple_Crafting_Default", PageType.SIMPLE_CRAFTING);
public static final OpenBenchPageInteraction DIAGRAM_CRAFTING =
    new OpenBenchPageInteraction("*Diagram_Crafting_Default", PageType.DIAGRAM_CRAFTING);
public static final OpenBenchPageInteraction STRUCTURAL_CRAFTING =
    new OpenBenchPageInteraction("*Structural_Crafting_Default", PageType.STRUCTURAL_CRAFTING);
```

Each has a corresponding `RootInteraction`:
```java
public static final RootInteraction SIMPLE_CRAFTING_ROOT = new RootInteraction(SIMPLE_CRAFTING.getId(), ...);
public static final RootInteraction DIAGRAM_CRAFTING_ROOT = ...;
public static final RootInteraction STRUCTURAL_CRAFTING_ROOT = ...;
```

### Bench Type → RootInteraction Registration

```java
Bench.registerRootInteraction(BenchType.Crafting, OpenBenchPageInteraction.SIMPLE_CRAFTING_ROOT);
Bench.registerRootInteraction(BenchType.DiagramCrafting, OpenBenchPageInteraction.DIAGRAM_CRAFTING_ROOT);
Bench.registerRootInteraction(BenchType.StructuralCrafting, OpenBenchPageInteraction.STRUCTURAL_CRAFTING_ROOT);
```

### The interactWithBlock() Method — Exact Code

```java
protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBuffer,
        InteractionType type, InteractionContext context,
        ItemStack itemInHand, Vector3i targetBlock, CooldownHandler cooldownHandler) {

    Ref<EntityStore> ref = context.getEntity();
    Store<EntityStore> store = ref.getStore();
    Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());

    if (playerComponent != null) {
        CraftingManager craftingManager = commandBuffer.getComponent(ref, CraftingManager.getComponentType());
        if (!craftingManager.hasBenchSet()) {
            if (world.getState(targetBlock.x, targetBlock.y, targetBlock.z, true) instanceof BenchState benchState) {
                BenchWindow benchWindow = switch (this.pageType) {
                    case SIMPLE_CRAFTING -> new SimpleCraftingWindow(benchState);
                    case DIAGRAM_CRAFTING -> new DiagramCraftingWindow(ref, commandBuffer, benchState);
                    case STRUCTURAL_CRAFTING -> new StructuralCraftingWindow(benchState);
                };

                UUID uuid = commandBuffer.getComponent(ref, UUIDComponent.getComponentType()).getUuid();
                if (benchState.getWindows().putIfAbsent(uuid, benchWindow) == null) {
                    benchWindow.registerCloseEvent(event -> benchState.getWindows().remove(uuid, benchWindow));
                }

                playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, benchWindow);
            }
        }
    }
}
```

**Blockers for portable use:**
1. Extends `SimpleBlockInteraction` — requires a block target
2. `world.getState()` must return `BenchState` — requires a placed bench block
3. Multi-user tracking via `benchState.getWindows().putIfAbsent(uuid, window)` — ties to block state

---

## 8. StructuralCraftingWindow — Why It Can't Be Portable As-Is

### Constructor Signature

```java
public StructuralCraftingWindow(BenchState benchState) {
    super(WindowType.StructuralCrafting, benchState);
    // Creates SimpleItemContainer(1) for input
    // Creates SimpleItemContainer(64) for options
    // Casts bench to StructuralCraftingBench for cycling/hints config
}
```

### What It Needs from BenchState

Through its parent chain:
1. `CraftingWindow(WindowType, BenchState)` → reads categories from `bench.getCategories()`
2. `BenchWindow(WindowType, BenchState)` → reads block position, block type, bench config, item translation key, tier level
3. `BlockWindow(WindowType, x, y, z, rotationIndex, BlockType)` → stores block coordinates for validation

### It Implements ItemContainerWindow

The `OpenWindow` packet includes an `InventorySection`:
```java
// WindowManager.openWindow()
if (window instanceof ItemContainerWindow itemContainerWindow) {
    section = itemContainerWindow.getItemContainer().toPacket();
}
```

The client expects inventory slots for the input and options containers. This is part of the `WindowType.StructuralCrafting` contract.

---

## 9. Approach Analysis — Updated with Full Source Knowledge

### Approach A: Ghost Bench Block

**Viability: Possible but with significant complications**

The `BlockWindow.validate()` method runs periodically:
```java
public boolean validate(Ref<EntityStore> ref, ComponentAccessor<EntityStore> store) {
    // Checks: player within maxDistanceSqr (7.0m default)
    // Checks: chunk loaded
    // Checks: block at (x,y,z) has same item as original blockType
    // Returns false if any check fails → window closes
}
```

The bench block must remain placed AND within 7m of the player for the entire session. Walking away closes the window.

### Approach B: Custom Window with WindowType.StructuralCrafting (NEW INSIGHT)

**This is more promising than previously thought.** A plugin could create a `Window` subclass (not `BlockWindow`) that:

1. Uses `WindowType.StructuralCrafting` — the client will render the structural crafting UI
2. Implements `ItemContainerWindow` — provides the input/options containers the client expects
3. Builds the same JSON data that `StructuralCraftingWindow` produces
4. Handles `SelectSlotAction`, `CraftRecipeAction`, `ChangeBlockAction`

**This avoids all block dependencies.** The `BlockWindow.validate()` distance check is NOT inherited. The window has no coordinates to validate.

**Remaining unknowns:**
- Does the client crash/error if `WindowType.StructuralCrafting` doesn't have block position data? The `OpenWindow` packet itself doesn't include block position — only `WindowType`, `windowData` JSON, `InventorySection`, and `ExtraResources`. Block position is in the JSON `windowData` only if the server puts it there.
- Does the client try to render a bench model at block coordinates from the window data?

### Approach C: WindowType.PocketCrafting with StructuralCrafting Bench Type

**Not viable.** The `WindowType` determines the client-side renderer:
- `PocketCrafting(1)` → simple recipe list (no input slots, no options grid)
- `StructuralCrafting(4)` → slot-based UI with input, options, block cycling

Setting `BenchType.StructuralCrafting` in the JSON data of a `PocketCrafting` window would give the wrong UI renderer. The client would show a simple recipe list, not the structural crafting interface.

### Approach D: Custom UI Page

Via `PageManager.openCustomPageWithWindows()`:
```java
pageManager.openCustomPageWithWindows(ref, store, customUIPage, windows...);
```

Full control over UI via `UICommandBuilder` + `UIEventBuilder`. Could build a structural crafting interface from scratch with Custom UI HTML/CSS/JS.

---

## 10. Recommended Path: Custom StructuralCrafting Window

Based on the full source analysis, **Approach B is the strongest path**:

```java
public class PortableStructuralCraftingWindow extends Window implements ItemContainerWindow {
    private final SimpleItemContainer inputContainer;
    private final SimpleItemContainer optionsContainer;
    private final CombinedItemContainer combinedItemContainer;
    private final JsonObject windowData = new JsonObject();

    public PortableStructuralCraftingWindow() {
        super(WindowType.StructuralCrafting);  // Client renders structural UI

        this.inputContainer = new SimpleItemContainer((short)1);
        this.optionsContainer = new SimpleItemContainer((short)64);
        this.combinedItemContainer = new CombinedItemContainer(inputContainer, optionsContainer);

        // Populate windowData JSON matching what the client expects:
        this.windowData.addProperty("type", BenchType.StructuralCrafting.ordinal());
        this.windowData.addProperty("id", "Builders");
        this.windowData.addProperty("name", "translation.key.here");
        this.windowData.addProperty("tierLevel", 1);
        this.windowData.addProperty("selected", 0);
        this.windowData.addProperty("allowBlockGroupCycling", true);
        this.windowData.addProperty("alwaysShowInventoryHints", false);
        // ... add categories, recipes, etc.
    }

    // Implement handleAction for SelectSlotAction, CraftRecipeAction, ChangeBlockAction
    // Implement getItemContainer() → combinedItemContainer
    // No block validation needed
}
```

**Opening it:**
```java
playerComponent.getPageManager().setPageWithWindows(
    ref, store, Page.Bench, true, new PortableStructuralCraftingWindow()
);
```

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Client expects block position in window data | Medium | Test without it; may need dummy values |
| Client renders bench model at (0,0,0) | Low | If so, add far-away dummy coordinates |
| Missing `MaterialContainerWindow` (nearby chests) | Low | Skip extra resources; portable bench has no chests |
| `CraftingManager.setBench()` not called | Medium | May need to call manually or handle crafting differently |
| Tier upgrade not applicable | None | Skip tier upgrade support for portable version |

---

## Key Findings Summary

1. **`FieldCraftingWindow`** extends `Window` directly with a no-arg constructor, uses `WindowType.PocketCrafting`, hardcodes `BenchType.Crafting`. It is the only blockless crafting window in vanilla.

2. **`WindowType.StructuralCrafting(4)` EXISTS** as a separate client-side renderer — the client knows how to render structural crafting UIs.

3. **PocketCrafting CANNOT handle StructuralCrafting** — the `WindowType` determines the client renderer, and `PocketCrafting` renders a simple recipe list without input slots.

4. **The API path to open a window from server code:**
   `Player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)`

5. **A custom `Window extends Window implements ItemContainerWindow`** using `WindowType.StructuralCrafting` is the most promising approach — it bypasses all block dependencies while using the native client renderer.

6. **The interaction chain for Q key:** Client → `SyncInteractionChain` → `InteractionManager.tick()` → resolves `RootInteraction` from item's `Interactions.Use` → executes `Interaction` implementation.

7. **Plugins CAN register custom Interaction types** via `getCodecRegistry(Interaction.CODEC).register(...)` and bind them to item JSON `Use` interactions.

---

## See Also

- [Bench Types](./bench-types.md)
- [Recipes](./recipes.md)
- [Plugin Capabilities](../plugins/capabilities.md)
- [Server-Client Boundary](../server-client-boundary.md)
