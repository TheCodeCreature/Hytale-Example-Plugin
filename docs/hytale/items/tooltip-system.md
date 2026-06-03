---
topic: "Item Tooltip Rendering System"
category: "Items / UI"
updated: 2026-06-03
sources:
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/ItemBase.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/ItemTranslationProperties.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/ItemQuality.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/packets/assets/UpdateItems.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemQuality.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemTranslationProperties.java"
  - ".tmp_hytale_src/com/hypixel/hytale/server/core/modules/item/ItemPacketGenerator.java"
  - ".tmp_hytale_src/com/hypixel/hytale/protocol/ItemGridInfoDisplayMode.java"
---

# Item Tooltip Rendering System

## Summary

Tooltips are **rendered client-side**. The server's responsibility is sending the correct data via the protocol; the client decides layout and what to display. There are no server-side classes for tooltip rendering — no `ItemTooltipWidget`, `TooltipRenderer`, or similar exist in the server codebase.

The client constructs the tooltip from data in the `ItemBase` protocol object and the resolved `ItemQuality`. There are **no tooltip-specific flags** on `ItemBase` like `TooltipType`, `ShowDescription`, or `TooltipLayout`.

## Data Flow

```
JSON item config → server Item class → Item.toPacket() → ItemBase (protocol) → UpdateItems packet → client
                                                            ↓
                                              ItemQuality.toPacket() → ItemQuality (protocol) → UpdateItemQualities packet → client
```

## Fields That Control Tooltip Content

### On `ItemBase` (protocol)

| Field | Type | Tooltip Role |
|-------|------|--------------|
| `translationProperties.name` | `@Nullable String` | Item name displayed in the tooltip header. Localization key with raw-string fallback. |
| `translationProperties.description` | `@Nullable String` | Item description text. Localization key — vanilla items always use keys like `"server.items.{Id}.description"`. |
| `qualityIndex` | `int` | Resolves to `ItemQuality` which controls tooltip background texture, text color, and quality label. |
| `itemLevel` | `int` | Item level number displayed in the tooltip. |
| `tool` | `@Nullable ItemTool` | If present, client renders tool spec lines (gather types, power levels). |
| `weapon` | `@Nullable ItemWeapon` | If present, client renders weapon stat modifier lines. |
| `armor` | `@Nullable ItemArmor` | If present, client renders armor resistance/enhancement lines. |
| `durability` | `double` | If > 0, client renders a durability bar. |
| `blockId` | `int` | If > 0, item is a block item. May influence tooltip layout on the client. |

### On `ItemQuality` (protocol)

| Field | Type | Tooltip Role |
|-------|------|--------------|
| `itemTooltipTexture` | `String` | Path to the tooltip background 9-slice texture (e.g., `"UI/ItemQualities/Tooltips/ItemTooltipCommon.png"`). |
| `itemTooltipArrowTexture` | `String` | Path to the tooltip arrow texture. |
| `textColor` | `Color` | Color used for the item name text in the tooltip. |
| `visibleQualityLabel` | `boolean` | Whether the quality name label (e.g., "Common", "Tool") is rendered in the tooltip. |
| `localizationKey` | `String` | The localization key for the quality name (e.g., `"server.general.qualities.Common"`). |

### On `ItemCategory` (NOT directly tooltip-related)

| Field | Type | Purpose |
|-------|------|---------|
| `infoDisplayMode` | `ItemGridInfoDisplayMode` | Controls info display in the **creative library grid** only, NOT inventory tooltips. Values: `Tooltip` (popup), `Adjacent` (side panel), `None`. |

## TranslationProperties: Name vs Description Behavior

Both fields are `@Nullable String` in the protocol. The server-side documentation calls them "translation keys":

```java
// server-side ItemTranslationProperties.java
.appendInherited(new KeyedCodec<>("Name", Codec.STRING), ...)
    .documentation("The translation key for the name of this item.")
    .metadata(new UIEditor(new UIEditor.LocalizationKeyField("server.items.{assetId}.name", true)))  // generateDefaultKey=true

.appendInherited(new KeyedCodec<>("Description", Codec.STRING), ...)
    .documentation("The translation key for the description of this item.")
    .metadata(new UIEditor(new UIEditor.LocalizationKeyField("server.items.{assetId}.description"))) // generateDefaultKey=false (default)
```

The `generateDefaultKey` parameter is an **editor-only** hint — it tells the asset editor whether to auto-populate a default localization key when creating a new item. It does not affect runtime behavior.

### Client Localization Behavior (inferred)

The client receives the raw strings and performs localization lookup:

1. If the string matches a known localization key → display the localized text
2. If no match is found → **for Name**, fallback to displaying the raw string; **for Description**, behavior is uncertain (see Gotchas)

**Evidence for Name raw fallback**: Plugin stencil items display `"[Stencil] Oak Planks"` correctly — these are raw strings, not localization keys.

**Evidence for Description**: ALL vanilla items that display descriptions use localization keys (e.g., `"server.items.Weapon_Sword_Crude.description"`). No vanilla item uses a raw string for Description.

## Vanilla Item Patterns

### Items WITH descriptions in TranslationProperties

| Item | Quality | Has Tool/Weapon/Armor? | Description Value |
|------|---------|----------------------|-------------------|
| `Weapon_Sword_Crude` | Common | Weapon ✓ | `"server.items.Weapon_Sword_Crude.description"` |
| `Tool_Pickaxe_Crude` | Common | Tool ✓ | `"server.items.Tool_Pickaxe_Crude.description"` |
| `Tool_Pickaxe_Iron` | Uncommon | Tool ✓ | `"server.items.Tool_Pickaxe_Crude.description"` (reuses parent) |
| `Wood_Oak_Trunk` | (Default) | Block ✓ (blockId > 0) | `"server.items.Wood_Oak_Trunk.description"` |
| `Weapon_Spellbook_Rekindle_Embers` | — | Weapon ✓ | `"server.items.Weapon_Spellbook_Rekindle_Embers.description"` |

### Items WITHOUT descriptions

| Item | Quality | Has Tool/Weapon/Armor? |
|------|---------|----------------------|
| `Weapon_Sword_Wood` | Common | Weapon ✓ — shows stat lines, NO description |
| `Armor_Wood_Chest` | (inherited) | Armor ✓ — shows stat lines, NO description |
| `Template_Weapon_Sword` | Template | Weapon ✓ — template, not player-facing |

**Key insight**: What appears as "descriptions" on armor/weapon tooltips are actually **auto-generated stat lines** rendered by the client from `ItemArmor.damageResistance`, `ItemWeapon.statModifiers`, `ItemTool.specs` — NOT from `TranslationProperties.Description`.

## Quality Definitions and Tooltip Textures

Every quality defines its own tooltip background:

| Quality | VisibleQualityLabel | Tooltip Texture |
|---------|-------------------|-----------------|
| Default | `false` | `ItemTooltipDefault.png` |
| Junk | `false` | `ItemTooltipJunk.png` |
| Common | `true` | `ItemTooltipCommon.png` |
| Uncommon | `true` | `ItemTooltipUncommon.png` |
| Rare | `true` | `ItemTooltipRare.png` |
| Tool | `true` | `ItemTooltipDefault.png` |
| Template | `true` | `ItemTooltipTechnical.png` |
| Stencil_Affordable | `false` | `ItemTooltipUncommon.png` |
| Stencil_Unaffordable | `false` | `ItemTooltipCommon.png` |

`VisibleQualityLabel` controls the quality name line in the tooltip (e.g., "Common", "Uncommon"). It does **NOT** control whether the description section renders.

## UpdateItems Packet Behavior

`UpdateItems` with `UpdateType.AddOrUpdate` performs a **full replacement** of the `ItemBase` for each listed item ID. It does NOT merge fields — the entire `ItemBase` is replaced on the client.

```java
// ItemPacketGenerator.java — init packet
UpdateItems packet = new UpdateItems();
packet.type = UpdateType.Init;
packet.items = new Object2ObjectOpenHashMap<>();
for (Entry<String, Item> entry : assets.entrySet()) {
    packet.items.put(entry.getKey(), entry.getValue().toPacket());
}
packet.updateModels = true;
packet.updateIcons = true;
```

**Consequence**: If you send an `UpdateItems` with `translationProperties.description = null`, the client **loses** any previously-set description for that item.

### updateModels / updateIcons flags

| Flag | Purpose |
|------|---------|
| `updateModels` | Triggers client-side 3D model and texture cache rebuild |
| `updateIcons` | Triggers client-side icon atlas rebuild |

Setting both to `false` creates a lightweight packet that only updates metadata (quality, name, description, etc.) without expensive client rebuilds. This is correct for visual-only overrides.

## Gotchas

1. **Full replacement semantics**: `UpdateItems.AddOrUpdate` replaces the ENTIRE `ItemBase`. If your override packet only sets `name` on `translationProperties` and leaves `description` as null, the client loses the description. You must copy all fields you want to preserve.

2. **Localization key vs raw string for Description**: All vanilla items use localization keys for `TranslationProperties.Description`. While raw strings work for `Name` (confirmed by stencil display names), there is no vanilla evidence that raw strings work for `Description`. The client may silently drop non-resolvable description keys.

3. **Shared ItemBase instances**: `Item.toPacket()` caches the result via `SoftReference`. If you mutate the returned `ItemBase`, you mutate the cached copy for ALL future calls. Clone the packet fields before modifying, or accept that mutation persists until GC collects the soft reference.

4. **Stat lines ≠ Description**: Armor/weapon/tool tooltips show auto-generated stat lines from their component data (`ItemArmor.damageResistance`, `ItemWeapon.statModifiers`, `ItemTool.specs`). These are NOT from `TranslationProperties.Description`. An item needs these protocol components to get stat lines.

5. **Block items may have simplified tooltips**: Items with `blockId > 0` and no tool/weapon/armor data might render with a simplified tooltip layout on the client. The exact behavior is client-determined.

## Implications for Plugin Development

### Blueprint Book (Custom Tool Item)

The BlueprintBook.json uses a raw string for Description:
```json
"TranslationProperties": {
    "Name": "Blueprint Journal",
    "Description": "A journal containing various blueprints for crafting."
}
```

This may not render because:
- The client may require Description to be a valid localization key
- The item has no `Tool`, `Weapon`, or `Armor` data, so no stat lines render
- Without stat lines OR description, the tooltip is just: Name + Quality Label

**Fix options**:
1. Register a localization key for the description (if the plugin API supports it)
2. Accept that Description may not display for custom items without localization entries
3. Add a `Tool` component to the item to generate stat lines (workaround for visual effect)

### Stencil Items (Block Items with Visual Overrides)

The `StencilVisualManager.buildUpdatePacket()` creates a new `ItemTranslationProperties` with only `name` set:
```java
packet.translationProperties = new ItemTranslationProperties();
packet.translationProperties.name = resolveDisplayName(itemId);
// description is null — explicitly lost
```

**Fix**: Set description on the replacement `translationProperties`:
```java
packet.translationProperties = new ItemTranslationProperties();
packet.translationProperties.name = resolveDisplayName(itemId);
packet.translationProperties.description = "Materials needed: ..."; // or a localization key
```

**Caveat**: Even with this fix, whether the description actually renders depends on:
- Whether the client supports raw strings for Description
- Whether the client's tooltip layout for block items includes a description section
- Whether the Stencil quality's tooltip texture supports the description area

## See Also

- [server-client-boundary.md](../server-client-boundary.md)
- [ItemQuality source](.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/ItemQuality.java)
- [ItemBase protocol](.tmp_hytale_src/com/hypixel/hytale/protocol/ItemBase.java)
