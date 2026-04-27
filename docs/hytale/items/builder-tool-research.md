---
topic: "BuilderTool System Research — Feasibility for Custom Block-Placement Tool"
category: "Items / Interactions / Plugin API"
updated: 2026-04-26
sources:
  - ".tmp_hytale_src/.../BuilderToolInteraction.java (server-side, interaction config)"
  - ".tmp_hytale_src/.../BuilderToolInteraction.java (protocol)"
  - ".tmp_hytale_src/.../BuilderToolsPlugin.java"
  - ".tmp_hytale_src/.../BuilderToolsPacketHandler.java"
  - ".tmp_hytale_src/.../BuilderToolOnUseInteraction.java (protocol packet)"
  - ".tmp_hytale_src/.../BuilderTool.java (asset type)"
  - ".tmp_hytale_src/.../BuilderToolItemReferenceAsset.java"
  - ".tmp_hytale_src/.../ItemBuilderToolData.java"
  - ".tmp_hytale_src/.../InteractionModule.java"
  - ".tmp_hytale_src/.../Interaction.java (protocol)"
  - ".tmp_hytale_src/.../SimpleInteraction.java (server-side)"
  - ".tmp_hytale_src/.../SimpleBlockInteraction.java (server-side)"
  - ".tmp_hytale_src/.../SimpleInstantInteraction.java (server-side)"
  - ".tmp_hytale_src/.../InteractionConfiguration.java"
  - ".tmp_hytale_src/.../ChangeBlockInteraction.java (server-side)"
  - ".tmp_hytale_src/.../HytalePermissions.java"
  - "docs/Resources/items/EditorTool/EditorTool_Paste.json"
  - "docs/Resources/items/Tool/BuilderTool/EditorTool_Selection.json"
  - "docs/hytale/plugins/interaction-codec-registration-api.md"
  - "docs/hytale/items/bucket-pattern-feasibility-for-placeblock.md"
---

# BuilderTool System Research — Feasibility for Custom Block-Placement Tool

## Executive Summary

**The BuilderTool system is NOT suitable as a basis for a custom block-placement tool in Adventure mode.** It is deeply coupled to the creative-mode editor infrastructure, gated behind editor permissions, and does not fire any interceptable ECS events.

**The recommended approach is to register a custom interaction type** extending `SimpleBlockInteraction`, which provides:
- Target block position from client raycast
- No item consumption
- `Next`/`Failed` chaining
- No game-mode restriction
- Full server-side control via the `tick0()` override

---

## Q1: Does `Builder_Tool` Interaction Work in Adventure Mode?

### Answer: **Partially YES for the interaction, but NO for the server-side handler.**

The interaction system itself has **no game mode restriction**. The `BuilderToolInteraction.tick0()` method copies client state to server state without checking game mode:

```java
// BuilderToolInteraction.java (server-side config)
@Override
protected void tick0(boolean firstRun, float time, InteractionType type,
                     InteractionContext context, CooldownHandler cooldownHandler) {
    context.getState().state = context.getClientState().state;
    super.tick0(firstRun, time, type, context, cooldownHandler);
}
```

The `InteractionConfig.UseDistance` field supports `Adventure` as a key. The `EditorTool_Paste.json` sets `"Adventure": 128`, which means the **client** will send the interaction at 128-block range in Adventure mode. The client does NOT block the interaction based on game mode.

**However**, the actual server-side work happens via a **separate packet** (`BuilderToolOnUseInteraction`, packet ID 413). This packet is handled by `BuilderToolsPacketHandler.handle(BuilderToolOnUseInteraction)`, which checks permissions:

```java
// BuilderToolsPacketHandler.java line 712
if (hasPermission(playerComponent, "hytale.editor.brush.use")) {
    BuilderToolsPlugin.addToQueue(playerComponent, playerRef,
        (r, s, componentAccessor) -> s.edit(ref, packet, componentAccessor));
}
```

The `hasPermission` check requires either `hytale.editor.brush.use` OR `hytale.editor.builderTools`. These are editor permissions — not granted to Adventure mode players by default.

**Bottom line:** The interaction fires, but the server-side handler gates all behavior behind editor permissions. Even if you granted the permission, the handler routes into `BuilderToolsPlugin`'s internal brush/edit queue — not into anything useful for single-block placement.

---

## Q2: What Is the `Builder_Tool` Interaction Type?

### Architecture

The BuilderTool system has a **split architecture**:

1. **Interaction layer** (`BuilderToolInteraction`, Type ID 7):
   - Registered as `"BuilderTool"` in `InteractionModule`
   - Extends `SimpleInteraction` (not `SimpleBlockInteraction` — no block targeting)
   - `tick0()` simply copies client state → server state
   - `WaitForDataFrom.Client` — waits for client sync data
   - `needsRemoteSync() = true` — syncs state to other players
   - Does NOT fire any ECS events
   - Does NOT place, break, or modify any blocks

2. **Packet layer** (`BuilderToolOnUseInteraction`, packet 413):
   - Sent by client when the tool is actually used (click)
   - Contains: target position (x, y, z), interaction type (Primary/Secondary), raycast origin/direction, paint mode offsets, brush modifiers
   - Handled by `BuilderToolsPacketHandler` → `BuilderToolsPlugin.addToQueue()` → `BuilderState.edit()`

3. **Plugin layer** (`BuilderToolsPlugin`):
   - Maintains per-player `BuilderState` (selection, clipboard, history)
   - Routes tool operations to scripted brushes, paste operations, etc.
   - Deeply coupled to editor infrastructure

The `"Primary": "Builder_Tool"` in the JSON is a **RootInteraction ID**, not a type name. It references a pre-defined `RootInteraction` asset that wraps a `BuilderToolInteraction` instance.

### How Builder Tool IDs Are Resolved

```java
// BuilderTool.java — asset type with its own registry
public class BuilderTool implements JsonAssetWithMap<String, DefaultAssetMap<String, BuilderTool>> {
    public static final AssetBuilderCodec<String, BuilderTool> CODEC = ...
        .addField("Id", Codec.STRING, ...)
        .addField("IsBrush", Codec.BOOLEAN, ...)
        .addField("Args", new MapCodec<>(ToolArg.CODEC, ...), ...)
        .addField("BrushData", BrushData.CODEC, ...)
}
```

The `BuilderTool` config on an item (the `"BuilderTool"` JSON block) is parsed as a `BuilderTool` asset. The `Id` field (e.g., `"Paste"`, `"Selection"`) identifies the tool type. Tool arguments define configurable parameters shown in the editor UI.

---

## Q3: Can We Register a Custom `BuilderTool.Id`?

### Answer: **Technically possible, practically useless.**

`BuilderTool` has its own `AssetStore` and `CODEC`, and items can embed `BuilderTool` data. A plugin could potentially register a new builder tool asset via asset injection.

However, the `BuilderToolsPlugin.BuilderState.edit()` method routes operations based on the tool's `IsBrush` flag and scripted brush definitions. A custom tool ID would need:
- A matching `ScriptedBrushAsset` (or custom routing logic)
- Integration with the brush config system
- The entire BuilderToolsPlugin infrastructure

The system is designed for mass-editing (selections, fills, brushes), not single-block placement.

---

## Q4: Is There a `BuilderToolEvent` or Similar?

### Answer: **NO. No ECS events fire during builder tool operations.**

The flow is:
1. Client sends `BuilderToolOnUseInteraction` packet
2. `BuilderToolsPacketHandler` checks permissions
3. `BuilderToolsPlugin.addToQueue()` adds to a per-player operation queue
4. `BuilderState.edit()` executes the tool operation (brush paint, selection, paste, etc.)
5. Block changes are applied directly via `WorldChunk.setBlock()` (with history tracking)

At no point does this fire a `PlaceBlockEvent`, `BreakBlockEvent`, or any other ECS event. The builder tools bypass the normal ECS event pipeline entirely.

---

## Q5: Custom Item with `Builder_Tool` Interaction but No `BuilderTool` Config?

### Answer: **The interaction would fire but the tool operation would fail.**

Without a `BuilderTool` config, the `BuilderToolsPlugin` would not find any tool data for the held item. The `ToolOperation` constructor reads the held item's builder tool configuration:

```java
// ToolOperation.java constructor
public ToolOperation(Ref<EntityStore> ref, BuilderToolOnUseInteraction packet,
                     ComponentAccessor<EntityStore> componentAccessor) {
    // ... reads item's BuilderTool data via Item.getBuilderToolData() or similar
}
```

Without builder tool data, the operation would either throw an exception or silently no-op.

---

## Q6: Is There a Simpler "UseItem" Interaction Type?

### Answer: **YES — several base classes exist, and plugins can register custom types.**

### Interaction Hierarchy (Relevant Subset)

```
Interaction (abstract)
│   ABSTRACT_CODEC: base fields (ViewDistance, Effects, RunTime, Settings, Rules, Camera)
│   NO Next/Failed chaining
│
├── SimpleInteraction                         ← Has Next/Failed. WaitForDataFrom=None
│   │   CODEC adds "Next" and "Failed" fields
│   │
│   ├── SimpleBlockInteraction (abstract)     ← Has target block from client raycast
│   │   │   WaitForDataFrom=Client
│   │   │
│   │   ├── ChangeBlockInteraction            ← Changes one block to another, no item consumption
│   │   ├── OpenBenchPageInteraction          ← Opens a crafting bench page
│   │   ├── EnterPortalInteraction            ← Teleports player
│   │   ├── SpawnNPCInteraction               ← Spawns an NPC at target
│   │   ├── SeatingInteraction                ← Seats player on target block
│   │   └── [many more]
│   │
│   ├── SimpleInstantInteraction (abstract)   ← Runs once on first tick
│   │   ├── UseNPCInteraction
│   │   ├── TeleporterInteraction
│   │   └── ProjectileInteraction
│   │
│   └── BuilderToolInteraction               ← Copies client state (no block targeting)
│
├── PlaceBlockInteraction                     ← NO Next/Failed. Extends Interaction directly.
├── BreakBlockInteraction                     ← NO Next/Failed. Extends Interaction directly.
├── DamageEntityInteraction                   ← NO Next/Failed. Extends Interaction directly.
└── [many more that extend Interaction directly]
```

### Key Insight

`SimpleBlockInteraction` is the sweet spot for a custom interaction:
- Gets target block position from client raycast (`WaitForDataFrom.Client`)
- Has `Next`/`Failed` chaining via `SimpleInteraction`
- The abstract method `interactWithBlock()` provides: `World`, `CommandBuffer`, `InteractionType`, `InteractionContext`, `ItemStack itemInHand`, `Vector3i targetBlock`, `CooldownHandler`
- Does NOT consume items
- Does NOT place blocks
- No game mode restriction

---

## Q7: What About `InteractionConfig`?

### InteractionConfiguration Fields

From `InteractionConfiguration.java`:

| JSON Field | Type | Default | Purpose |
|-----------|------|---------|---------|
| `DisplayOutlines` | boolean | `true` | Show block selection outlines |
| `DebugOutlines` | boolean | `false` | Show debug outlines |
| `UseDistance` | Map<GameMode, Float> | Adventure:5, Creative:6 | Max interaction range per game mode |
| `AllEntities` | boolean | `false` | Target all entities (not just interactable) |
| `Priorities` | Map<InteractionType, InteractionPriority> | null | Priority when dual-wielding items |

### UseDistance

```java
private static final Object2FloatMap<GameMode> DEFAULT_USE_DISTANCE = new Object2FloatOpenHashMap<>() {{
    this.putIfAbsent(GameMode.Adventure, 5.0F);
    this.putIfAbsent(GameMode.Creative, 6.0F);
}};
```

Setting `"UseDistance": {"Adventure": 10}` would allow 10-block range in Adventure mode. The EditorTool_Paste sets 128 for both modes. This field is used by `SimpleBlockInteraction.tick0()` for range validation via `TargetUtil`.

**This is useful for our custom tool** — we can control placement range per game mode.

---

## Q8: Is There a Generic Interaction Type That Fires Events Without Placing?

### Answer: **No built-in interaction type fires generic server events.** But a custom interaction can do exactly this.

**The plugin API allows registering custom interaction types** (see `interaction-codec-registration-api.md`):

```java
this.getCodecRegistry(Interaction.CODEC)
    .register("PlaceBlock_Custom", PlaceBlockCustomInteraction.class, PlaceBlockCustomInteraction.CODEC);
```

A custom interaction extending `SimpleBlockInteraction` can:
1. Receive the target block position from client raycast
2. Fire a custom ECS event in `interactWithBlock()`
3. Place a block via `WorldChunk.setBlock()` or `BlockPlaceUtils.placeBlock()`
4. NOT consume the held item
5. Work in Adventure mode

**This is the canonical pattern.** It's exactly what `OpenBenchPageInteraction`, `EnterPortalInteraction`, `TeleporterInteraction`, etc. do — custom server-side logic triggered by item right-click with target position.

---

## Q9: Can We Use `Condition` Chains to Create a No-Op?

### Answer: **Yes, but it doesn't help — no ECS events fire from interaction chain nodes.**

`ConditionInteraction` checks item metadata or other conditions and routes to `Next`/`Failed`. You could create a chain like:

```json
{
  "Type": "Condition",
  "Conditions": [{ "Type": "Always", "Result": false }],
  "Failed": { "Type": "Simple" }
}
```

This would always route to `Failed`, running a `Simple` interaction that does nothing. But **none of these interactions fire ECS events** — they just manage interaction state internally. There's no "event bridge" from the interaction chain to the ECS event system unless the interaction subclass explicitly invokes one (like `PlaceBlockInteraction` fires `PlaceBlockEvent`).

---

## Q10: What About `ChangeBlock` Interaction?

### Answer: **Promising concept, but wrong tool for the job.**

`ChangeBlockInteraction` extends `SimpleBlockInteraction` and:
- Gets target block position from client
- Changes one block type to another (via a `Changes` map)
- Does NOT consume the held item
- Has `Next`/`Failed` chaining
- Works in Adventure mode

However:
- It requires a static `Changes` map defined at load time (not per-item-instance)
- It replaces existing blocks, not places blocks on empty faces
- It has no `PlaceBlockEvent` integration
- It can't determine the target block type from item metadata

It confirms that `SimpleBlockInteraction` subclasses work for non-consuming block interactions, but `ChangeBlock` itself is too specialized.

---

## Recommendation: The Simplest Path

### Register a Custom `SimpleBlockInteraction` Subclass

```java
public class PlaceBuildingBlockInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<PlaceBuildingBlockInteraction> CODEC =
        BuilderCodec.builder(PlaceBuildingBlockInteraction.class,
            PlaceBuildingBlockInteraction::new, SimpleBlockInteraction.CODEC)
        .build();

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBuffer,
            InteractionType type, InteractionContext context,
            ItemStack itemInHand, Vector3i targetBlock, CooldownHandler cooldownHandler) {
        // 1. Read target block type from item metadata
        // 2. Validate placement (range, space, resources)
        // 3. Place block via world.setBlock() or BlockPlaceUtils
        // 4. Consume resources from inventory programmatically
        // 5. Do NOT consume the held item
    }
}
```

Register in plugin setup:
```java
this.getCodecRegistry(Interaction.CODEC)
    .register("PlaceBuildingBlock", PlaceBuildingBlockInteraction.class,
        PlaceBuildingBlockInteraction.CODEC);
```

Item JSON:
```json
{
  "Interactions": {
    "Secondary": {
      "Type": "PlaceBuildingBlock"
    }
  },
  "InteractionConfig": {
    "UseDistance": { "Adventure": 6, "Creative": 8 },
    "DisplayOutlines": true
  },
  "Quality": "Tool",
  "MaxStack": 1
}
```

### What This Gives You

| Requirement | How It's Met |
|------------|-------------|
| Player holds in hotbar (Adventure) | Normal item, no game mode restriction |
| Right-click places specific block | `interactWithBlock()` reads metadata, places block |
| Does NOT consume itself | `SimpleBlockInteraction` never consumes items |
| Plugin consumes materials | Your code calls `itemContainer.removeItemStackFromSlot()` |
| Each slot targets different block | Different metadata per ItemStack in each slot |
| Target position available | `Vector3i targetBlock` parameter in `interactWithBlock()` |
| Range control | `InteractionConfig.UseDistance` |

### What You Lose vs PlaceBlock

| Feature | PlaceBlock | Custom Interaction |
|---------|-----------|-------------------|
| Client-side block preview (ghost) | Built-in | Must be done server-side via fake packets |
| Client-side placement prediction | Built-in | Server-authoritative only (slight latency) |
| Drag-placement | Built-in | Must implement manually |
| Block rotation UI | Built-in | Must implement manually |

### Block Preview Workaround

The existing `PreviewBlockManager` in the plugin already handles server-side block previews via `ServerSetBlock` packets. This approach works but adds complexity. The alternative is to set a `BlockType` on the placeholder item that matches the target block — but this only works for a single target type per item asset (not per-instance).

---

## Complete Interaction Type Reference

All 45 interaction types registered in `InteractionModule`:

| Type Name | Class | Extends | Game Mode Restricted | Fires ECS Events |
|-----------|-------|---------|---------------------|-------------------|
| Simple | SimpleInteraction | Interaction → Simple | No | No |
| PlaceBlock | PlaceBlockInteraction | Interaction | No* | Yes (PlaceBlockEvent) |
| PlaceFluid | PlaceFluidInteraction | SimpleInteraction | No | No |
| BreakBlock | BreakBlockInteraction | Interaction | No | Yes (BreakBlockEvent, DamageBlockEvent) |
| PickBlock | PickBlockInteraction | SimpleBlockInteraction | Creative only | No |
| UseBlock | UseBlockInteraction | SimpleBlockInteraction | No | Yes (UseBlockEvent) |
| BlockCondition | BlockConditionInteraction | SimpleBlockInteraction | No | No |
| ChangeBlock | ChangeBlockInteraction | SimpleBlockInteraction | No | No |
| ChangeState | ChangeStateInteraction | SimpleBlockInteraction | No | No |
| UseEntity | UseEntityInteraction | SimpleInteraction | No | No |
| **BuilderTool** | **BuilderToolInteraction** | **SimpleInteraction** | **Permission-gated** | **No** |
| ModifyInventory | ModifyInventoryInteraction | SimpleInteraction | No | No |
| Charging | ChargingInteraction | SimpleInteraction | No | No |
| Chaining | ChainingInteraction | SimpleInteraction | No | No |
| Condition | ConditionInteraction | SimpleInteraction | No | No |
| FirstClick | FirstClickInteraction | SimpleInteraction | No | No |
| Repeat | RepeatInteraction | SimpleInteraction | No | No |
| Parallel | ParallelInteraction | SimpleInteraction | No | No |
| Serial | SerialInteraction | SimpleInteraction | No | No |
| Selector | SelectInteraction | SimpleInteraction | No | No |
| DamageEntity | DamageEntityInteraction | Interaction | No | Yes (Damage) |
| Replace | ReplaceInteraction | SimpleInteraction | No | No |
| Wielding | WieldingInteraction | SimpleInteraction | No | No |
| OpenCustomUI | OpenCustomUIInteraction | SimpleInstantInteraction | No | No |
| OpenPage | OpenPageInteraction | SimpleInstantInteraction | No | No |
| RefillContainer | RefillContainerInteraction | SimpleBlockInteraction | No | No |
| Door | DoorInteraction | SimpleBlockInteraction | No | No |

*PlaceBlock consumes items in Adventure mode when `RemoveItemInHand=true` (default).

---

## See Also

- [Interaction Codec Registration API](../../hytale/plugins/interaction-codec-registration-api.md)
- [PlaceBlock Building Tool — API Research](./placeblock-building-tool-api-research.md)
- [Bucket Pattern Feasibility](./bucket-pattern-feasibility-for-placeblock.md)
- [State Item PlaceBlock Interaction Research](./state-placeblock-interaction-research.md)
