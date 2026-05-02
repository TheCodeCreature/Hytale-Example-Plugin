---
topic: "Stencil Visual Identity & Affordability Indicator — Engine Research"
category: "Items / Visual / Quality"
updated: 2026-05-01
sources:
  - ".tmp_hytale_src/.../ItemBase.java (protocol)"
  - ".tmp_hytale_src/.../Item.java"
  - ".tmp_hytale_src/.../ItemStack.java"
  - ".tmp_hytale_src/.../ItemQuality.java (server config)"
  - ".tmp_hytale_src/.../ItemQuality.java (protocol)"
  - ".tmp_hytale_src/.../ItemQualityPacketGenerator.java"
  - ".tmp_hytale_src/.../UpdateItems.java"
  - ".tmp_hytale_src/.../UpdateItemQualities.java"
  - ".tmp_hytale_src/.../ItemGridSlot.java"
  - ".tmp_hytale_src/.../ItemTranslationProperties.java"
  - ".tmp_hytale_src/.../ItemWithAllMetadata.java"
  - ".tmp_hytale_src/.../SoundUtil.java"
  - ".tmp_hytale_src/.../SpawnParticleSystem.java"
  - "docs/hytale/items/item-icon-rendering.md"
  - "docs/hytale/items/item-state-system.md"
  - "docs/hytale/inventory-hotbar-events.md"
---

# Stencil Visual Identity & Affordability Indicator — Engine Research

## Executive Summary

| Goal | Feasibility | Mechanism | Per-Stack? | Real-Time? |
|------|-------------|-----------|------------|------------|
| Quality/rarity glow | **YES** | `UpdateItems` with modified `qualityIndex` | No — per item TYPE | YES |
| Custom display name | **YES** | `UpdateItems` with modified `translationProperties.name` | No — per item TYPE | YES |
| Tooltip text | **YES** | `UpdateItems` with modified `translationProperties.description` | No — per item TYPE | YES |
| Affordability indicator via quality change | **YES** | `UpdateItems` per-player when inventory changes | Per item TYPE | YES |
| Sound feedback | **YES** | `PlaySoundEvent2D` / `PlaySoundEvent3D` packets | Per player | YES |
| Particle feedback | **YES** | `SpawnParticleSystem` packet | Per player | YES |
| Per-slot visual overlay | **NO** | No API exists | N/A | N/A |

**Critical constraint**: Quality is a property of the **Item asset definition** (via `qualityIndex`), NOT of the ItemStack. It cannot vary between two stacks of the same item ID. However, since each stencil variant already has its own item ID (e.g., `*Block_Placeholder_State_Armed_Green_0`), we can override quality per-variant per-player via `UpdateItems`.

---

## 1. Item Quality/Rarity System — Deep Dive

### Architecture

```
ItemQuality JSON asset    ──→   ItemQuality (server config)    ──→   qualityIndex (int)
(e.g., "Developer")            registered in IndexedLookupTable       stored on Item.qualityIndex

Item JSON asset            ──→   Item (server config)           ──→   ItemBase.qualityIndex
"Quality": "Developer"          resolved at init time                 sent to client via UpdateItems

Client receives:
  - UpdateItemQualities → defines what each qualityIndex looks like (textures, colors)
  - UpdateItems → each item's qualityIndex references the quality definition
  - Client renders slot background texture + text color based on the quality
```

### ItemQuality Asset Definition

**Server class**: `com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality`

| Field | Type | Purpose |
|-------|------|---------|
| `id` | `String` | e.g. `"Common"`, `"Developer"`, `"Rare"` |
| `qualityValue` | `int` | Ordering value (0 = lowest quality) |
| `itemTooltipTexture` | `String` | Tooltip background texture path |
| `itemTooltipArrowTexture` | `String` | Tooltip arrow texture path |
| `slotTexture` | `String` | **Inventory slot background** for non-block items |
| `blockSlotTexture` | `String` | **Inventory slot background** for block-type items |
| `specialSlotTexture` | `String` | Slot texture for consumable/usable items |
| `textColor` | `Color` | **Text color** for item name in inventory |
| `localizationKey` | `String` | Quality label localization key |
| `visibleQualityLabel` | `boolean` | Whether quality label shows in tooltip |
| `renderSpecialSlot` | `boolean` | Whether to use specialSlotTexture |
| `hideFromSearch` | `boolean` | Hidden from creative library |
| `itemEntityConfig` | `ItemEntityConfig` | Dropped item visuals |

**Default quality**: Index 0, ID `"Default"`, grey text (`#c9d2dd`), no visible quality label.

### How Quality Reaches the Client

1. **At load time**: `Item.java` resolves `qualityId` string → `qualityIndex` integer via `ItemQuality.getAssetMap().getIndexOrDefault(this.qualityId, 0)`
2. **In `toPacket()`**: `packet.qualityIndex = this.qualityIndex`
3. **In `UpdateItems`**: Sent as `ItemBase.qualityIndex` (int, fixed at byte offset 46)
4. **Client side**: Uses `qualityIndex` to look up the `ItemQuality` data (received via `UpdateItemQualities` packet), then renders the slot background texture and text color accordingly

### The Blue Glow on Tools (slots 1-3)

Those tools have a non-default `Quality` field in their item JSON (e.g., `"Quality": "Uncommon"` or similar). The client renders:
- The `slotTexture` (or `blockSlotTexture` for blocks) as the slot background
- The `textColor` for the item name
- The `itemTooltipTexture` for the tooltip background

### Can Quality Be Set Per-Stack?

**NO.** Quality is on `Item` (the asset definition), NOT on `ItemStack`. The `ItemStack` only stores: `itemId`, `quantity`, `durability`, `maxDurability`, `metadata` (BSON), `overrideDroppedItemAnimation`. There is no `qualityIndex` on ItemStack.

**However**, since each stencil variant has its own unique item ID, we can override the quality at the item type level per-player via `UpdateItems`. Two stacks of the same stencil variant will always show the same quality — but different variants can show different qualities.

### Can We Create Custom Quality Tiers?

**YES.** `ItemQuality` is an asset type with its own `AssetStore`. We can:
1. Define custom quality JSON files (e.g., `Stencil_Affordable.json`, `Stencil_Unaffordable.json`)
2. They'll be registered with their own `qualityIndex` values
3. We can reference them in `UpdateItems` packets

Alternatively, we can reuse existing quality tiers by index. The `UpdateItemQualities` packet can also be sent per-player to define entirely new quality visuals.

---

## 2. Custom Display Name

### How Item Names Work

**Server**: `ItemTranslationProperties` stores `name` and `description` as localization key strings.

```java
public class ItemTranslationProperties {
    String name;         // e.g., "server.items.Rock_Stone.name"
    String description;  // e.g., "server.items.Rock_Stone.description"
}
```

**Protocol**: `ItemBase.translationProperties` is an `ItemTranslationProperties` with `name` and `description` strings.

**Client**: The client uses the `name` field as a **localization key** to look up the display string. If the key isn't found in the localization table, the raw string is displayed as-is.

### Can We Override the Display Name Per-Player?

**YES**, via `UpdateItems`. By sending a modified `ItemBase` with a custom `translationProperties.name`, we can change what the client displays:

```java
ItemBase modifiedItem = originalItem.toPacket(); // clone from original
modifiedItem.translationProperties = new ItemTranslationProperties();
modifiedItem.translationProperties.name = "[Stencil] Cobble Wall";  // raw string, not a loc key
modifiedItem.translationProperties.description = "Requires: 3× Rock_Stone";

UpdateItems update = new UpdateItems();
update.type = UpdateType.AddOrUpdate;
update.items = Map.of(stencilItemId, modifiedItem);
update.updateModels = false;
update.updateIcons = false;
playerRef.getPacketHandler().writeNoCache(update);
```

**Note**: If the string isn't a valid localization key, the client will show the raw string. This is the expected behavior for custom/dynamic names.

### Color Codes in Names

The `§` (section sign) color code system is **client-dependent**. Whether `§b[Stencil] Cobble Wall` renders with aqua color depends on whether the Hytale client supports Minecraft-style formatting codes. **This has NOT been confirmed** in the decompiled source. The safer approach is to use the `textColor` from the `ItemQuality` system to colorize the name.

---

## 3. UpdateItems Packet — Full API

### Packet Structure

```java
public class UpdateItems implements Packet {
    public static final int PACKET_ID = 54;
    public static final boolean IS_COMPRESSED = true;

    public UpdateType type;                  // Init, AddOrUpdate, Remove
    public Map<String, ItemBase> items;      // itemId → full item data
    public String[] removedItems;            // items to remove
    public boolean updateModels;             // rebuild 3D models
    public boolean updateIcons;              // regenerate icon cache
}
```

### What Can Be Overridden Per-Player

By sending `UpdateItems` with `type = AddOrUpdate` to a specific player's packet handler:

| Property | Field Path | Effect |
|----------|-----------|--------|
| Quality/glow | `ItemBase.qualityIndex` | Changes slot background, text color, tooltip style |
| Display name | `ItemBase.translationProperties.name` | Changes item name shown in UI |
| Description | `ItemBase.translationProperties.description` | Changes tooltip description |
| Icon | `ItemBase.icon` | Changes icon path (requires `updateIcons=true`) |
| Model | `ItemBase.model`, `.texture` | Changes hand-held model (requires `updateModels=true`) |
| Categories | `ItemBase.categories` | Changes creative menu categorization |
| Max stack | `ItemBase.maxStack` | Changes max stack size |
| Block ID | `ItemBase.blockId` | Changes which block type the item places |
| All interactions | `ItemBase.interactions` | Changes what happens on use |

**Scope**: Per-player. Other players see the original item definition.

### Sending Pattern

```java
// Get the original Item asset
Item item = Item.getAssetMap().getAsset(stencilItemId);
ItemBase packet = item.toPacket();

// Modify quality
packet.qualityIndex = customQualityIndex;

// Modify name
packet.translationProperties = new ItemTranslationProperties();
packet.translationProperties.name = "[Stencil] Cobble Wall";

// Send to specific player
UpdateItems update = new UpdateItems();
update.type = UpdateType.AddOrUpdate;
update.items = new HashMap<>();
update.items.put(stencilItemId, packet);
update.updateModels = false;
update.updateIcons = false;  // true only if icon/blockId changed
playerRef.getPacketHandler().writeNoCache(update);
```

---

## 4. ItemGridSlot — UI Grid Properties

`ItemGridSlot` is used for UI panels (crafting grids, entity spawn pages), NOT for the main hotbar. It has:

| Field | Type | Purpose |
|-------|------|---------|
| `name` | `String` | Override display name in the UI grid |
| `description` | `String` | Override description in the UI grid |
| `skipItemQualityBackground` | `boolean` | Suppress quality slot texture |
| `isItemIncompatible` | `boolean` | Visual flag for incompatible items |
| `isItemUncraftable` | `boolean` | Visual flag for uncraftable items |
| `isActivatable` | `boolean` | Whether the slot can be activated |
| `background` | `Value<PatchStyle>` | Custom background texture |
| `overlay` | `Value<PatchStyle>` | **Custom overlay texture** |
| `icon` | `Value<PatchStyle>` | Custom icon override |

**Relevance**: `ItemGridSlot` is for custom UI pages (like the Blueprint Bench), not the native hotbar. The hotbar rendering uses the `ItemBase` data directly. However, if we build a custom UI panel for stencil management, `ItemGridSlot` gives us `overlay`, `background`, and `name` overrides per-slot.

---

## 5. BSON Metadata — What It Can and Cannot Do

### ItemStack Metadata

`ItemStack.metadata` is a `BsonDocument` — arbitrary key-value pairs attached to a specific stack instance. It is:
- ✅ Sent to the client in `ItemWithAllMetadata.metadata` (as JSON string)
- ✅ Preserved across state changes via `withState()`
- ✅ Preserved when moving items between containers
- ✅ Used for stacking identity (`isStackableWith` checks metadata equality)
- ❌ **NOT used for visual rendering** — the client ignores unknown metadata keys
- ❌ **NOT connected to quality/name/icon** — those come from `ItemBase` (the item type definition)

### What Metadata IS Used For

From decompiled code, metadata is read by specific interaction handlers (e.g., `RefillContainerInteraction` checks metadata keys). The client receives the BSON as a JSON string but only uses it for interaction-specific logic, not visual rendering.

### Verdict

**BSON metadata cannot control visual appearance.** Quality, name, icon, etc. are properties of the `Item` asset definition, not the ItemStack. The only way to change visuals is to:
1. Change the item's type definition per-player via `UpdateItems`
2. Change the item's quality definition per-player via `UpdateItemQualities`
3. Swap the itemId to a different variant with different visual properties

---

## 6. Affordability Indicator — Approach

### The Problem

When inventory changes, we need to signal whether the player can afford to place each stencil block. This must be:
- Per-player (each player has different inventory)
- Real-time (updates when resources are gained/spent)
- Visual (player sees it without opening a UI)

### Recommended: Quality Index Swapping via UpdateItems

**Mechanism**: Define two custom `ItemQuality` assets:

1. **`Stencil_Affordable`**: Distinctive slot texture (blue/green glow), colored text
2. **`Stencil_Unaffordable`**: Warning slot texture (red/grey), dimmed text

When inventory changes:
1. Listen to `LivingEntityInventoryChangeEvent`
2. For each stencil item in the player's hotbar, check affordability
3. If affordability state changed, send `UpdateItems` with the new `qualityIndex`

### Event Listening

```java
// Register globally
this.getEventRegistry().registerGlobal(
    LivingEntityInventoryChangeEvent.class,
    this::onInventoryChange
);

private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
    LivingEntity entity = event.getEntity();
    if (!(entity instanceof Player player)) return;

    PlayerRef ref = player.getPlayerRef();
    Inventory inventory = player.getInventory();
    ItemContainer hotbar = inventory.getHotbar();

    // Check each hotbar slot for stencil items
    for (int slot = 0; slot < hotbar.getSize(); slot++) {
        ItemStack stack = hotbar.getItemStack(slot);
        if (stack == null || !isStencilItem(stack)) continue;

        boolean canAfford = checkAffordability(inventory, stack);
        updateStencilQuality(ref, stack.getItemId(), canAfford);
    }
}
```

### Cost of UpdateItems

`UpdateItems` is a **compressed** packet (IS_COMPRESSED = true). Sending it per inventory change for a handful of stencil items is lightweight:
- The packet only includes the items that changed
- The client only processes the items in the map
- No model/icon rebuild needed (just `qualityIndex` change)
- The slot texture swap is instant client-side

### Batching

Multiple `qualityIndex` changes can be batched in a single `UpdateItems` packet:

```java
UpdateItems update = new UpdateItems();
update.type = UpdateType.AddOrUpdate;
update.items = new HashMap<>();
for (String stencilId : changedStencils) {
    ItemBase packet = getModifiedItemBase(stencilId, canAfford);
    update.items.put(stencilId, packet);
}
update.updateModels = false;
update.updateIcons = false;
playerRef.getPacketHandler().writeNoCache(update);
```

### Throttling Consideration

`LivingEntityInventoryChangeEvent` fires per-mutation, which can be frequent during bulk operations (crafting, pickup). Consider debouncing:
- Track dirty stencil items
- Send batched `UpdateItems` on next tick or after a short delay
- Avoid sending if affordability state didn't actually change

---

## 7. Sound & Particle Feedback

### Playing Sounds to a Player

```java
// 2D sound (UI-like, no spatial positioning)
SoundUtil.playSoundEvent2dToPlayer(
    playerRef,
    soundEventIndex,        // int — index from SoundEvent asset map
    SoundCategory.UI,       // or SFX
    volumeModifier,         // float, default 1.0
    pitchModifier           // float, default 1.0
);

// 3D sound at a position
SoundUtil.playSoundEvent3d(
    soundEventIndex,
    SoundCategory.SFX,
    x, y, z,                // world coordinates
    componentAccessor       // broadcasts to nearby players
);

// 3D sound to specific player at position
SoundUtil.playSoundEvent3dToPlayer(
    playerRef,
    soundEventIndex,
    SoundCategory.SFX,
    position,               // Vector3d
    componentAccessor
);
```

**Sound index resolution**: Use `TempAssetIdUtil.getSoundEventIndex("SFX_Name")` to resolve a sound event name to its index.

### Spawning Particles

```java
SpawnParticleSystem particlePacket = new SpawnParticleSystem(
    "ParticleSystem/Effects/MyEffect.particlesystem",  // particle system asset path
    new Position(x, y, z),     // world position
    null,                      // rotation (nullable)
    1.0f,                      // scale
    new Color(255, 0, 0)       // color tint (nullable)
);
playerRef.getPacketHandler().writeNoCache(particlePacket);
```

### "Can't Afford" Feedback

When the player tries to place a stencil they can't afford:
1. Play a UI error sound: `SoundUtil.playSoundEvent2dToPlayer(ref, errorSoundIndex, SoundCategory.UI)`
2. Optionally spawn a red particle at the target block position
3. The quality-based slot dimming provides persistent visual feedback

---

## 8. Hotbar Slot Visual States

### Direct Slot Override API

**Does NOT exist.** There is no packet or API to change the visual appearance (border, highlight, overlay) of a specific hotbar slot independently of the item it contains.

The hotbar renders each slot based on:
1. The `ItemBase` data for the item in that slot (from `UpdateItems`)
2. The `ItemQuality` data for that item's `qualityIndex` (from `UpdateItemQualities`)
3. Standard UI rendering of quantity, durability bar, etc.

There is no server-side API for per-slot overlays, borders, or custom highlights on the native hotbar.

### What ItemGridSlot.overlay Does

`ItemGridSlot.overlay` is available for custom UI grids (`.ui` files), NOT the native hotbar. If the Blueprint Bench UI uses `ItemGridSlot`, it CAN have per-slot overlays. The native hotbar cannot.

---

## 9. UpdateBlockTypes vs UpdateItems

| Aspect | UpdateBlockTypes | UpdateItems |
|--------|-----------------|-------------|
| Scope | Block type definitions | Item definitions |
| Key type | `int` (block type index) | `String` (item ID) |
| What changes | In-world block appearance, ghost preview | Hotbar icon, slot quality, name, tooltip |
| Icon update | ❌ Does NOT update item icons | ✅ With `updateIcons=true` |
| Per-player | ✅ Via `writeNoCache` | ✅ Via `writeNoCache` |
| Packet ID | Varies | 54 |

**For stencil visual identity, only `UpdateItems` is needed** (no block type reskinning required for quality/name changes).

---

## 10. Existing Codebase Patterns

### UpdateBlockTypes Usage (in plugin)

- [PlaceholderTransparencyUtil.java](../../../src/main/java/com/UnobstructedThirdPerson/shape/v1/placeholder/PlaceholderTransparencyUtil.java) — sends `UpdateBlockTypes` to make placeholder blocks transparent
- [TransparentBlockUtils.java](../../../src/main/java/com/UnobstructedThirdPerson/shape/v1/placeholder/TransparentBlockUtils.java) — batched `UpdateBlockTypes` for texture modifications
- [PreviewBlockSubCommand.java](../../../src/main/java/com/UnobstructedThirdPerson/command/debug/SubCommands/PreviewBlockSubCommand.java) — debug command for block preview reskinning

### Quality Usage (in plugin)

- [AbstractBenchProcessor.java](../../../src/main/java/com/UnobstructedThirdPerson/resourcecollection/AbstractBenchProcessor.java#L85) — reads `BlockBreakingDropType.getQuality()` (this is block gathering quality, NOT item quality — different system)
- [BreakBlockDiagnostic.java](../../../src/main/java/com/UnobstructedThirdPerson/resourcecollection/BreakBlockDiagnostic.java#L198) — logs quality field from gathering configs

### No Existing UpdateItems Usage

The plugin does NOT currently use `UpdateItems` packets. This would be a new pattern.

---

## Recommended Approach

### Visual Identity (One-Time Setup + Per-Player Override)

1. **Create custom ItemQuality assets**: Define `Stencil_Affordable` and `Stencil_Unaffordable` quality tiers with distinct slot textures and text colors

2. **On stencil creation** (when player arms a stencil slot): Send `UpdateItems` per-player with:
   - Modified `qualityIndex` → `Stencil_Affordable` index
   - Modified `translationProperties.name` → `"[Stencil] Cobble Wall"` (raw string, not loc key)
   - Modified `translationProperties.description` → recipe cost summary

3. **Result**: The stencil item in the hotbar shows a distinctive glow/border (from quality slot texture) and a custom name with color (from quality text color)

### Affordability Indicator (Real-Time)

1. **Listen to `LivingEntityInventoryChangeEvent`** (global)
2. **On each change**: Check if any stencil items' affordability state changed
3. **If changed**: Send batched `UpdateItems` per-player swapping `qualityIndex` between `Stencil_Affordable` and `Stencil_Unaffordable`
4. **Throttle**: Debounce rapid inventory changes (e.g., 1-tick delay)

### Gotchas

- **Quality is per-item-TYPE, not per-stack**: Two stacks of `*Block_Placeholder_State_Armed_Green_0` in different slots will always show the same quality. Since each stencil variant has its own ID, this is fine — each slot uses a different variant ID.
- **UpdateItems is cached**: Use `writeNoCache()` on the packet handler, not `write()`, to ensure per-player overrides aren't cached and shared.
- **Item.toPacket() uses SoftReference cache**: Call `item.toPacket()` then clone/modify the result. Don't mutate the cached packet directly — it would affect all players.
- **No § color codes confirmed**: Don't rely on Minecraft-style formatting. Use the quality system's `textColor` instead.
- **Restore on disconnect**: If the player disconnects and reconnects, they'll receive the original `UpdateItems` init packet. Re-apply overrides on `PlayerReadyEvent`.
