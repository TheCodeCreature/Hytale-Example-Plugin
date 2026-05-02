---
topic: "Custom ItemQuality JSON Asset — Load Failure Investigation"
category: "Items / Quality / Asset Loading"
updated: 2026-05-01
sources:
  - ".tmp_hytale_src/.../ItemQuality.java (server config)"
  - ".tmp_hytale_src/.../ItemQuality.java (protocol)"
  - ".tmp_hytale_src/.../ItemQualityPacketGenerator.java"
  - ".tmp_hytale_src/.../AssetBuilderCodec.java"
  - ".tmp_hytale_src/.../AssetStore.java"
  - ".tmp_hytale_src/.../AssetModule.java"
  - ".tmp_hytale_src/.../AssetRegistryLoader.java"
  - ".tmp_hytale_src/.../ColorCodec.java"
  - ".tmp_hytale_src/.../ColorParseUtil.java"
  - ".tmp_hytale_src/.../CommonAssetValidator.java"
  - ".tmp_hytale_src/.../ValidationResults.java"
---

# Custom ItemQuality JSON — Load Failure Investigation

## Problem Statement

Custom `ItemQuality` JSON assets at `Server/Item/Qualities/Stencil_Affordable.json` and `Stencil_Unaffordable.json` produce no visible quality glow on stencil items. Built-in qualities like `"Uncommon"` and `"Developer"` work correctly.

---

## Root Cause: Two Independent Issues

### Issue #1 — Texture Validation Failure (LIKELY BLOCKING)

Every texture field in `ItemQuality.CODEC` is validated by `CommonAssetValidator.TEXTURE_ITEM_QUALITY`:

```java
// CommonAssetValidator.java
public static final CommonAssetValidator TEXTURE_ITEM_QUALITY =
    new CommonAssetValidator("png", true, "UI/ItemQualities");
```

The validator runs three checks:
1. Path starts with `"UI/ItemQualities/"` — **PASS** (our paths do)
2. File extension is `.png` — **PASS**
3. `CommonAssetRegistry.hasCommonAsset(asset)` — **UNKNOWN / LIKELY FAIL**

If check #3 fails, the validator calls `results.fail()`, which results in:

```java
// ValidationResults.logOrThrowValidatorExceptions()
if (failed) {
    throw new CodecValidationException(sb.toString());  // ← HARD FAILURE
}
```

This `CodecValidationException` **completely prevents the ItemQuality asset from loading**. The asset never enters the `IndexedLookupTableAssetMap`, so `getIndexOrDefault("Stencil_Affordable", 0)` returns `0` (Default), and the item renders with no quality glow.

**Why the DEFAULT_ITEM_QUALITY is exempt**: It's pre-loaded programmatically via `.preLoadAssets()` — it never passes through JSON codec validation. Its texture paths (`"UI/ItemQualities/Tooltips/ItemTooltipDefault.png"`, etc.) may be placeholder paths that don't correspond to actual files in `CommonAssetRegistry`.

**Why built-in qualities work**: They are loaded from the base game's asset pack, and their texture paths reference files that DO exist in the base game's `Common/` directory (e.g., `UI/ItemQualities/Tooltips/ItemTooltipUncommon.png`).

### Issue #2 — Default Textures Produce No Visible Glow

Even if the JSONs loaded successfully, our custom qualities reference the **Default** quality textures:

```json
"SlotTexture": "UI/ItemQualities/Slots/SlotDefault.png"
```

The "Default" quality is intentionally the plain/invisible base tier. It has **no glow, no colored border, no visual indicator**. To get a visible glow, the quality must reference textures from an actual quality tier (Uncommon, Rare, etc.) or custom textures.

---

## Detailed Field-by-Field Analysis

### JSON Field Names — ALL CORRECT

| JSON Field | KeyedCodec Name | Java Type | Our Value | Status |
|---|---|---|---|---|
| `"QualityValue"` | `"QualityValue"` | `int` | `100` | ✅ Correct |
| `"ItemTooltipTexture"` | `"ItemTooltipTexture"` | `String` | `"UI/ItemQualities/Tooltips/ItemTooltipDefault.png"` | ✅ Format correct, but see Issue #1/#2 |
| `"ItemTooltipArrowTexture"` | `"ItemTooltipArrowTexture"` | `String` | `"UI/ItemQualities/Tooltips/ItemTooltipDefaultArrow.png"` | ✅ Format correct, but see Issue #1/#2 |
| `"SlotTexture"` | `"SlotTexture"` | `String` | `"UI/ItemQualities/Slots/SlotDefault.png"` | ✅ Format correct, but see Issue #1/#2 |
| `"BlockSlotTexture"` | `"BlockSlotTexture"` | `String` | `"UI/ItemQualities/Slots/SlotDefault.png"` | ✅ Format correct, but see Issue #1/#2 |
| `"SpecialSlotTexture"` | `"SpecialSlotTexture"` | `String` | `"UI/ItemQualities/Slots/SpecialSlotDefault.png"` | ✅ Format correct, but see Issue #1/#2 |
| `"TextColor"` | `"TextColor"` | `Color` via `ProtocolCodecs.COLOR` | `"#55FF55"` | ✅ Valid — `ColorCodec` accepts `#RGB`, `#RRGGBB`, `rgb(R,G,B)` |
| `"LocalizationKey"` | `"LocalizationKey"` | `String` | `"server.general.qualities.Stencil_Affordable"` | ✅ Correct |
| `"VisibleQualityLabel"` | `"VisibleQualityLabel"` | `boolean` | `false` | ✅ Correct |
| `"RenderSpecialSlot"` | `"RenderSpecialSlot"` | `boolean` | `true` | ✅ Correct |
| `"HideFromSearch"` | `"HideFromSearch"` | `boolean` | `true` | ✅ Correct |

**No field name discrepancies.** All JSON keys match the `KeyedCodec` names exactly.

### Optional Fields We're Not Providing

| Field | Required? | Effect of Omission |
|---|---|---|
| `"ItemEntityConfig"` | Optional | No custom dropped-item particles/model. Uses default. |

This is fine — `ItemEntityConfig` is optional.

### Asset ID Derivation — CORRECT

The engine derives the asset key from the filename:

```java
// AssetStore.decodeFilePathKey()
String fileName = path.getFileName().toString();
return fileName.substring(0, fileName.length() - this.extension.length());
// "Stencil_Affordable.json" → "Stencil_Affordable"
```

Then the `AssetBuilderCodec` assigns this as the ID:

```java
(itemQuality, s) -> itemQuality.id = s  // sets id from extraInfo.getKey()
```

No `"Id"` field is needed inside the JSON.

### Color Format — CORRECT

`ProtocolCodecs.COLOR` is a `ColorCodec` instance that:

1. Calls `ColorParseUtil.parseColor(stringValue)`
2. Which checks `HEX_COLOR_PATTERN = "^\\s*#([0-9a-fA-F]{3}){1,2}\\s*$"`
3. `"#55FF55"` matches → `hexStringToColor()` → `Color(0x55, 0xFF, 0x55)`

### Protocol Packet — CORRECT

The `toPacket()` method sends these fields to the client:

```java
packet.id = this.id;
packet.itemTooltipTexture = this.itemTooltipTexture;
packet.itemTooltipArrowTexture = this.itemTooltipArrowTexture;
packet.slotTexture = this.slotTexture;
packet.blockSlotTexture = this.blockSlotTexture;
packet.specialSlotTexture = this.specialSlotTexture;
packet.textColor = this.textColor;
packet.localizationKey = this.localizationKey;
packet.visibleQualityLabel = this.visibleQualityLabel;
packet.renderSpecialSlot = this.renderSpecialSlot;
packet.hideFromSearch = this.hideFromSearch;
```

Note: `qualityValue` is NOT sent to the client (server-only for sorting/comparison).

---

## How to Verify: Check Server Logs

When a `CodecValidationException` is thrown during asset loading, the engine catches it and logs the failure. Look for these log patterns at startup:

```
FAIL: Common Asset 'UI/ItemQualities/Tooltips/ItemTooltipDefault.png' doesn't exist!
```

Or the more general asset loading failure:

```
Failed to validate asset!
```

The `AssetStore.loadAssetsFromPaths()` method tracks failures in `failedToLoadKeys` and `failedToLoadPaths`. The `HytaleAssetStore.sendReloadedNotification()` also sends in-game notifications about failed loads.

---

## Fix: Corrected Stencil_Affordable.json

### Option A: Reference Built-in Uncommon Textures (Quick Fix)

Use texture paths from a built-in quality that actually exists and has visible glow:

```json
{
  "QualityValue": 100,
  "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipUncommon.png",
  "ItemTooltipArrowTexture": "UI/ItemQualities/Tooltips/ItemTooltipUncommonArrow.png",
  "SlotTexture": "UI/ItemQualities/Slots/SlotUncommon.png",
  "BlockSlotTexture": "UI/ItemQualities/Slots/SlotUncommon.png",
  "SpecialSlotTexture": "UI/ItemQualities/Slots/SpecialSlotUncommon.png",
  "TextColor": "#55FF55",
  "LocalizationKey": "server.general.qualities.Stencil_Affordable",
  "VisibleQualityLabel": false,
  "RenderSpecialSlot": true,
  "HideFromSearch": true
}
```

> **Caveat**: The exact texture filenames for built-in qualities (e.g., `ItemTooltipUncommon.png`, `SlotUncommon.png`) need to be confirmed by checking the base game's `Common/UI/ItemQualities/` directory contents. The naming pattern follows `{TextureType}{QualityName}.png` based on convention.

### Option B: Provide Custom Textures (Proper Fix)

1. Create custom texture files in the mod's `Common/` directory:
   ```
   Common/UI/ItemQualities/Tooltips/ItemTooltipStencilAffordable.png
   Common/UI/ItemQualities/Tooltips/ItemTooltipStencilAffordableArrow.png
   Common/UI/ItemQualities/Slots/SlotStencilAffordable.png
   Common/UI/ItemQualities/Slots/SpecialSlotStencilAffordable.png
   ```

2. Reference them in the quality JSON:
   ```json
   {
     "QualityValue": 100,
     "ItemTooltipTexture": "UI/ItemQualities/Tooltips/ItemTooltipStencilAffordable.png",
     "ItemTooltipArrowTexture": "UI/ItemQualities/Tooltips/ItemTooltipStencilAffordableArrow.png",
     "SlotTexture": "UI/ItemQualities/Slots/SlotStencilAffordable.png",
     "BlockSlotTexture": "UI/ItemQualities/Slots/SlotStencilAffordable.png",
     "SpecialSlotTexture": "UI/ItemQualities/Slots/SpecialSlotStencilAffordable.png",
     "TextColor": "#55FF55",
     "LocalizationKey": "server.general.qualities.Stencil_Affordable",
     "VisibleQualityLabel": false,
     "RenderSpecialSlot": true,
     "HideFromSearch": true
   }
   ```

### Option C: Test with Minimal Validation Risk

To confirm whether texture validation is the blocker, temporarily reference the SAME texture paths used by a known-working built-in quality. This isolates the texture validation issue from any other problem.

---

## Additional Issue: Misplaced Item JSONs

Files at `Server/Item/Items/_Debug/Stencil/Stencil_Affordable.json` and `Stencil_Unaffordable.json` contain ItemQuality-format JSON but are in the **Item** asset path. The Item codec expects completely different fields (`"StackSize"`, `"Icon"`, `"Quality"`, etc.). These files would fail to load as Items and should be removed.

---

## Verification Checklist

After applying the fix:

1. [ ] Check server startup logs for any `FAIL` messages referencing `Stencil_Affordable` or `Stencil_Unaffordable`
2. [ ] Verify the quality loaded: At runtime, confirm `ItemQuality.getAssetMap().getIndexOrDefault("Stencil_Affordable", 0)` returns a value > 0
3. [ ] Update `StencilVisualManager.QUALITY_AFFORDABLE` to `"Stencil_Affordable"` and `QUALITY_UNAFFORDABLE` to `"Stencil_Unaffordable"`
4. [ ] Remove misplaced files from `Server/Item/Items/_Debug/Stencil/`
5. [ ] Test in-game that stencil items show the expected quality glow

---

## Engine Reference: Asset Loading Flow for ItemQuality

```
Plugin JAR loaded
  ↓
AssetModule.registerPack() opens JAR as FileSystem
  ↓
AssetStore.loadAssetsFromDirectory()
  walks: pack.getRoot() / "Server" / "Item/Qualities" / *.json
  ↓
For each .json file:
  decodeFilePathKey() → strip .json → asset key (e.g., "Stencil_Affordable")
  ↓
  ItemQuality.CODEC.decodeJsonAsset(reader, extraInfo)
    ↓
    For each field: KeyedCodec reads JSON key → deserializes value
    ↓
    Validators run (including CommonAssetValidator.TEXTURE_ITEM_QUALITY)
      ↓
      CommonAssetValidator.accept():
        1. Check path prefix "UI/ItemQualities/"
        2. Check ".png" extension
        3. Check CommonAssetRegistry.hasCommonAsset(path)
           → if missing: results.fail() → CodecValidationException → ASSET REJECTED
    ↓
    extraInfo.getKey() → idSetter sets itemQuality.id
    ↓
  AssetMap.put(key, asset) → assigns integer index
  ↓
  ItemQualityPacketGenerator creates UpdateItemQualities packet
  ↓
  Packet sent to connected clients
```
