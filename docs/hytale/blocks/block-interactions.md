---
topic: "Block Interactions"
category: "Blocks / Interactions"
updated: 2026-04-20
sources: ["BlockType.java", "SimpleBlockInteraction.java", "SimpleInteraction.java", "UseBlockInteraction.java", "OpenBenchPageInteraction.java", "Bench.java", "BenchState.java", "CraftingPlugin.java", "Interaction.java", "RootInteraction.java", "Bench_Furniture.json", "PortableBenchInteraction.java"]
---

# Block Interactions — Complete Reference

## Summary

Block interactions in Hytale are dispatched through the same `Interaction` / `RootInteraction` system as item interactions. When a player presses the "Use" key while targeting a block, the engine resolves the block's `RootInteraction` ID from `BlockType.getInteractions()`, then executes the interaction chain. Custom blocks can open windows, spawn entities, trigger portals, or run arbitrary server logic through this system.

---

## 1. SimpleBlockInteraction — Class Hierarchy

```
Interaction (abstract)
│   Package: ...interaction.config
│
└── SimpleInteraction
    │   WaitForDataFrom = None
    │   Adds: "Next" and "Failed" chaining fields
    │
    └── SimpleBlockInteraction (abstract)
        │   Package: ...interaction.config.client
        │   WaitForDataFrom = Client  (needs block target from client)
        │   Adds: "UseLatestTarget" field
        │   Abstract: interactWithBlock(), simulateInteractWithBlock()
        │
        ├── OpenBenchPageInteraction      — Type: "OpenBenchPage"
        ├── UseBlockInteraction           — Type: "UseBlock" (THE DISPATCHER)
        ├── BedInteraction                — Type: "Bed"
        ├── SeatingInteraction            — Type: "Seating"
        ├── SpawnNPCInteraction           — Type: "SpawnNPC"
        ├── EnterPortalInteraction        — Type: "EnterPortal"
        ├── TeleportConfigInstanceInteraction — Type: "TeleportConfigInstance"
        └── ... more
```

### Source: [SimpleBlockInteraction.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/client/SimpleBlockInteraction.java)

```java
public abstract class SimpleBlockInteraction extends SimpleInteraction {

    public static final BuilderCodec<SimpleBlockInteraction> CODEC =
        BuilderCodec.abstractBuilder(SimpleBlockInteraction.class, SimpleInteraction.CODEC)
            .appendInherited(
                new KeyedCodec<>("UseLatestTarget", Codec.BOOLEAN),
                (interaction, s) -> interaction.useLatestTarget = s,
                interaction -> interaction.useLatestTarget,
                (interaction, parent) -> interaction.useLatestTarget = parent.useLatestTarget
            )
            .build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;  // CRITICAL: needs client-sent block position
    }

    // tick0() validates the target block, resolves chunk, then calls:
    protected abstract void interactWithBlock(
        World world,
        CommandBuffer<EntityStore> commandBuffer,
        InteractionType type,
        InteractionContext context,
        @Nullable ItemStack itemInHand,
        Vector3i targetBlock,
        CooldownHandler cooldownHandler
    );
}
```

### Key Difference: SimpleInteraction vs SimpleBlockInteraction

| Aspect | `SimpleInteraction` | `SimpleBlockInteraction` |
|--------|--------------------|-----------------------|
| `WaitForDataFrom` | `None` | `Client` |
| Target block | Not provided | Validated and passed to `interactWithBlock()` |
| Use case | Item-only actions (e.g., portable bench) | Block-targeted actions (e.g., open bench, enter portal) |
| Extend for | Custom item interaction | Custom block interaction |

---

## 2. How Block Interaction Dispatch Works

### The Two-Step Dispatch Model

Block interactions use a **two-hop** system:

1. The **item** in hand defines a `UseBlock` interaction
2. The `UseBlockInteraction` reads the **block type's** `Interactions` map and chains into the block's own `RootInteraction`

### Source: [UseBlockInteraction.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/client/UseBlockInteraction.java)

```java
public class UseBlockInteraction extends SimpleBlockInteraction {

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBuffer,
            InteractionType type, InteractionContext context,
            ItemStack itemInHand, Vector3i targetBlock, CooldownHandler cooldownHandler) {
        doInteraction(type, context, world, targetBlock, true);
    }

    private static void doInteraction(InteractionType type, InteractionContext context,
            World world, Vector3i targetBlock, boolean fireEvent) {
        BlockType blockType = world.getBlockType(targetBlock);

        // Step 1: Get the block's RootInteraction ID for this interaction type
        String blockTypeInteraction = blockType.getInteractions().get(type);
        if (blockTypeInteraction == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Step 2: Fire UseBlockEvent.Pre (cancellable)
        if (fireEvent) {
            UseBlockEvent.Pre event = new UseBlockEvent.Pre(type, context, targetBlock, blockType);
            commandBuffer.invoke(ref, event);
            if (event.isCancelled()) {
                context.getState().state = InteractionState.Failed;
                return;
            }
        }

        // Step 3: Execute the block's own RootInteraction chain
        context.getState().state = InteractionState.Finished;
        context.execute(RootInteraction.getRootInteractionOrUnknown(blockTypeInteraction));

        // Step 4: Fire UseBlockEvent.Post
        if (fireEvent) { /* ... */ }
    }
}
```

### Full Dispatch Flow (Mermaid)

```
Player presses Q on a block
        │
        ▼
Item's "Use" RootInteraction → resolves to item's Interaction chain
        │
        ▼
If chain contains "UseBlock" type interaction:
    UseBlockInteraction.interactWithBlock()
        │
        ▼
    BlockType blockType = world.getBlockType(targetBlock)
    String rootId = blockType.getInteractions().get(InteractionType.Use)
        │
        ▼
    context.execute(RootInteraction.get(rootId))
        │
        ▼
    Block's RootInteraction → resolves to block's Interaction chain
        │
        ▼
    e.g. OpenBenchPageInteraction.interactWithBlock() → opens bench window
```

**Key insight:** The item's interaction chain must contain a `UseBlock`-type interaction for the block's own interactions to fire. This is typically configured on the default player hand/tool items.

---

## 3. Block JSON Asset Format — Interactions Field

### `BlockType.getInteractions()` returns `Map<InteractionType, String>`

The `Interactions` field on a `BlockType` maps `InteractionType` enum keys to `RootInteraction` ID strings, identically to the Item's `Interactions` field:

```java
// BlockType codec (same pattern as Item)
new KeyedCodec<>("Interactions",
    new EnumMapCodec<>(InteractionType.class, RootInteraction.CHILD_ASSET_CODEC))
```

### JSON Format

```json
{
    "BlockType": {
        "Interactions": {
            "Use": "My_Custom_Root_Interaction"
        }
    }
}
```

### InteractionType Enum Values Used for Blocks

| Key | Trigger | Common Usage |
|-----|---------|--------------|
| `"Use"` | Q key on block | Open bench, open door, enter portal |
| `"Collision"` | Entity touching block | Lava damage, water swim |
| `"CollisionEnter"` | Entity enters block | Trigger zone |
| `"CollisionLeave"` | Entity leaves block | Exit trigger zone |

---

## 4. Bench Blocks Auto-Wire Their `Use` Interaction

### Source: [BlockType.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType.java) (line ~1853)

```java
// During BlockType.build() / afterDecode:
if (this.bench != null && !this.interactions.containsKey(InteractionType.Use)) {
    Map<InteractionType, String> interactions = this.interactions.isEmpty()
        ? new EnumMap<>(InteractionType.class)
        : new EnumMap<>(this.interactions);

    RootInteraction rootInteraction = this.bench.getRootInteraction();
    if (rootInteraction != null) {
        interactions.put(InteractionType.Use, rootInteraction.getId());
    }
    this.interactions = Collections.unmodifiableMap(interactions);
}
```

**This means:** If a `BlockType` has a `Bench` config and does NOT explicitly define `"Interactions": { "Use": "..." }`, the engine **automatically** sets the `Use` interaction to the RootInteraction registered for that bench type.

### How the Auto-Wiring is Set Up

In `CraftingPlugin.setup()`:
```java
Bench.registerRootInteraction(BenchType.Crafting,            OpenBenchPageInteraction.SIMPLE_CRAFTING_ROOT);
Bench.registerRootInteraction(BenchType.DiagramCrafting,     OpenBenchPageInteraction.DIAGRAM_CRAFTING_ROOT);
Bench.registerRootInteraction(BenchType.StructuralCrafting,  OpenBenchPageInteraction.STRUCTURAL_CRAFTING_ROOT);
```

These map to static instances:
```java
// OpenBenchPageInteraction.java
public static final OpenBenchPageInteraction STRUCTURAL_CRAFTING =
    new OpenBenchPageInteraction("*Structural_Crafting_Default", PageType.STRUCTURAL_CRAFTING);
public static final RootInteraction STRUCTURAL_CRAFTING_ROOT =
    new RootInteraction(STRUCTURAL_CRAFTING.getId(), STRUCTURAL_CRAFTING.getId());
```

### So for a bench block JSON like `Bench_Furniture.json`:

```json
{
    "BlockType": {
        "Bench": {
            "Type": "Crafting",
            "Id": "Furniture_Bench",
            "Categories": [ ... ]
        }
        // NO "Interactions" field needed — auto-wired by engine
    }
}
```

The engine auto-sets `Interactions.Use = "*Simple_Crafting_Default"`, which chains through `OpenBenchPageInteraction` to open the bench window.

---

## 5. Block Entities and State — Do You Need One?

### For Bench Blocks: Yes — `BenchState` is Required

`OpenBenchPageInteraction.interactWithBlock()` does this:

```java
if (world.getState(targetBlock.x, targetBlock.y, targetBlock.z, true) instanceof BenchState benchState) {
    BenchWindow benchWindow = switch (this.pageType) {
        case SIMPLE_CRAFTING      -> new SimpleCraftingWindow(benchState);
        case STRUCTURAL_CRAFTING  -> new StructuralCraftingWindow(benchState);
        case DIAGRAM_CRAFTING     -> new DiagramCraftingWindow(ref, commandBuffer, benchState);
    };
    playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, benchWindow);
}
```

If `world.getState()` does not return a `BenchState`, the interaction silently fails. The `BenchState` is created from the `BlockEntity` + block state registry:

```json
{
    "BlockType": {
        "BlockEntity": {
            "Components": {
                "BenchBlock": {}
            }
        }
    }
}
```

And registered in `CraftingPlugin.setup()`:
```java
blockStateRegistry.registerBlockState(BenchState.class, "crafting", BenchState.CODEC);
```

### For Custom (Non-Bench) Windows: No Block Entity Required

If you write a custom `SimpleBlockInteraction` that opens a stateless window, you do NOT need a block entity. Example — a custom interaction that opens a window fresh each time:

```java
@Override
protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBuffer,
        InteractionType type, InteractionContext context,
        ItemStack itemInHand, Vector3i targetBlock, CooldownHandler cooldownHandler) {

    Ref<EntityStore> ref = context.getEntity();
    Store<EntityStore> store = ref.getStore();
    Player player = commandBuffer.getComponent(ref, Player.getComponentType());

    MyCustomWindow window = new MyCustomWindow(/* stateless data */);
    player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
    context.getState().state = InteractionState.Finished;
}
```

The block entity is only needed when you need **persistent per-block state** (tier level, upgrade items, active crafting queues, etc.).

---

## 6. Interaction Codec Registration — Block vs Item

### There Is ONE Unified Codec Registry

Both items and blocks use the same `Interaction.CODEC` registry. There is no separate "block interaction codec." All interaction types — whether triggered from items or blocks — are registered into:

```java
Interaction.CODEC  // AssetCodecMapCodec<String, Interaction>
```

### Registration Pattern

```java
// In your plugin's setup():
this.getCodecRegistry(Interaction.CODEC)
    .register("MyBlockInteraction", MyBlockInteraction.class, MyBlockInteraction.CODEC);
```

This is identical to registering item interactions. The `"Type"` discriminator in JSON determines which class is instantiated:

```json
// Item/Interactions/MyBlockInteraction.json
{
    "Id": "My_Block_Interaction",
    "Type": "MyBlockInteraction",
    "RunTime": 0.1
}
```

---

## 7. Can You Open a StructuralCrafting Window from a Custom Block Interaction?

### Yes — Two Approaches

#### Approach A: Leverage the Auto-Wiring (No Custom Code)

Define your block with `"Bench": { "Type": "StructuralCrafting", ... }` and the engine auto-wires `OpenBenchPageInteraction.STRUCTURAL_CRAFTING` as the `Use` interaction. This requires:
- A `BlockEntity` with `BenchBlock` component
- A registered `BenchState` block state (already done by `CraftingPlugin`)

#### Approach B: Custom Interaction That Opens a Window Directly

Like `PortableBenchInteraction` does — extend `SimpleInteraction` (for item-triggered) or `SimpleBlockInteraction` (for block-targeted) and open the window yourself:

```java
// From PortableBenchInteraction.java (item-triggered, no block needed):
PortableBenchWindow window = new PortableBenchWindow(config);
playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
```

```java
// From OpenBenchPageInteraction.java (block-targeted, needs BenchState):
StructuralCraftingWindow window = new StructuralCraftingWindow(benchState);
playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
```

Both approaches use `Page.Bench` (enum value 1) to tell the client to display the bench UI. The `Window` subclass provides the JSON data that the client uses to render specific UI elements.

### Can You Open a Custom Window (Not a Bench)?

Yes. `Page.Custom` (enum value 7) exists for custom UI pages. The `OpenCustomUIInteraction` type uses this. However, the client must have the corresponding custom UI definition to render it.

---

## 8. Complete Example: Custom Block That Opens a Window

### Step 1: Create the Interaction Class

```java
public class MyBenchBlockInteraction extends SimpleBlockInteraction {

    public static final BuilderCodec<MyBenchBlockInteraction> CODEC =
        BuilderCodec.builder(MyBenchBlockInteraction.class, MyBenchBlockInteraction::new,
            SimpleBlockInteraction.CODEC)
        .build();

    protected MyBenchBlockInteraction() {}
    public MyBenchBlockInteraction(String id) { super(id); }

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBuffer,
            InteractionType type, InteractionContext context,
            ItemStack itemInHand, Vector3i targetBlock, CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Open your custom window
        MyWindow window = new MyWindow(/* pass data */);
        player.getPageManager().setPageWithWindows(ref, ref.getStore(), Page.Bench, true, window);
        context.getState().state = InteractionState.Finished;
    }

    @Override
    protected void simulateInteractWithBlock(InteractionType type, InteractionContext context,
            ItemStack itemInHand, World world, Vector3i targetBlock) {
        // No client simulation needed
    }

    @Override
    protected Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.SimpleBlockInteraction();
    }

    @Override
    public boolean walk(Collector collector, InteractionContext context) {
        return false;
    }
}
```

### Step 2: Register in Plugin setup()

```java
@Override
protected void setup() {
    // Register the codec type
    this.getCodecRegistry(Interaction.CODEC)
        .register("MyBenchBlock", MyBenchBlockInteraction.class, MyBenchBlockInteraction.CODEC);

    // Load the interaction + root interaction instances
    MyBenchBlockInteraction interaction = new MyBenchBlockInteraction("*My_Bench_Interaction");
    RootInteraction root = new RootInteraction("*My_Bench_Root", "*My_Bench_Interaction");

    AssetRegistry.getAssetStore(Interaction.class)
        .loadAssets("MyMod:MyMod", List.of(interaction));
    AssetRegistry.getAssetStore(RootInteraction.class)
        .loadAssets("MyMod:MyMod", List.of(root));
}
```

### Step 3: Bind to Block Type JSON

```json
{
    "BlockType": {
        "Interactions": {
            "Use": "*My_Bench_Root"
        }
    }
}
```

Or load programmatically by mutating the `BlockType` at `LoadedAssetsEvent` time.

---

## Gotchas

1. **UseBlockInteraction is the dispatcher** — Block interactions don't fire unless the player's held item has a `UseBlock` interaction in its chain. Standard tools/hands have this built-in.

2. **Bench blocks auto-wire but can be overridden** — If a bench block explicitly defines `"Interactions": { "Use": "..." }`, the auto-wiring is skipped (`!this.interactions.containsKey(InteractionType.Use)` check).

3. **BenchState is required for OpenBenchPageInteraction** — Without a `BlockEntity` with `BenchBlock` component and a registered `BenchState`, the `instanceof BenchState` check fails silently.

4. **No separate codec for block interactions** — Everything goes through `Interaction.CODEC`. The distinction between "block interaction" and "item interaction" is purely about whether you extend `SimpleBlockInteraction` (which sets `WaitForDataFrom.Client` and provides the target block) vs `SimpleInteraction`.

5. **`UseBlockEvent.Pre` is cancellable** — Plugins can cancel block interactions by listening to `UseBlockEvent.Pre`.

6. **`@Deprecated` on `Bench.registerRootInteraction`** — This API is marked for removal. Future versions may use a different mechanism for bench interaction wiring.

## See Also

- [Interaction Codec Registration API](../plugins/interaction-codec-registration-api.md)
- [Crafting Window Architecture](../crafting/crafting-window-architecture.md)
- [Block Types](./block-types.md)
- [Window Actions and Updates](../plugins/window-actions-and-updates.md)
