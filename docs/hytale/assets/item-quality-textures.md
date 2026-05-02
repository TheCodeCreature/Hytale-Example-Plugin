---
topic: "ItemQuality Texture Paths & Validation"
category: "Assets"
updated: 2026-05-01
sources: ["decompiled ItemQuality.java", "decompiled CommonAssetValidator.java", "decompiled CommonAssetRegistry.java", "server logs 2026-05-01"]
---

# ItemQuality Texture Paths & Validation

## Summary

Custom `ItemQuality` assets loaded via plugin asset packs are subject to **hard texture validation**. Every texture field must reference a PNG file that exists in the `CommonAssetRegistry` (loaded from `Common/` directories of asset packs). Validation failure causes the entire quality asset to be **rejected** — not warned, rejected.

## Exact Texture Paths from DEFAULT_ITEM_QUALITY

The hardcoded `DEFAULT_ITEM_QUALITY` (source: `ItemQuality.java:133-141`) uses:

```
ItemTooltipTexture:      UI/ItemQualities/Tooltips/ItemTooltipDefault.png
ItemTooltipArrowTexture: UI/ItemQualities/Tooltips/ItemTooltipDefaultArrow.png
SlotTexture:             UI/ItemQualities/Slots/SlotDefault.png
BlockSlotTexture:        UI/ItemQualities/Slots/SlotDefault.png
SpecialSlotTexture:      UI/ItemQualities/Slots/SpecialSlotDefault.png
```

## Base Game Texture Availability (Confirmed via Validation)

| Texture Path | Exists in Base Game? |
|---|---|
| `UI/ItemQualities/Tooltips/ItemTooltipDefault.png` | Yes (hardcoded in DEFAULT) |
| `UI/ItemQualities/Tooltips/ItemTooltipDefaultArrow.png` | Yes (hardcoded in DEFAULT) |
| `UI/ItemQualities/Slots/SlotDefault.png` | Yes (hardcoded in DEFAULT) |
| `UI/ItemQualities/Slots/SpecialSlotDefault.png` | Yes (hardcoded in DEFAULT) |
| `UI/ItemQualities/Tooltips/ItemTooltipUncommon.png` | **Yes** (passed validation) |
| `UI/ItemQualities/Tooltips/ItemTooltipUncommonArrow.png` | **Yes** (passed validation) |
| `UI/ItemQualities/Slots/SlotUncommon.png` | **Yes** (passed validation) |
| `UI/ItemQualities/Slots/SpecialSlotUncommon.png` | **NO** (failed validation) |

## Validation Chain

Defined in `ItemQuality.CODEC`:
```
.addValidator(Validators.nonNull())       // Field cannot be null
.addValidator(Validators.nonEmptyString()) // Field cannot be ""
.addValidator(CommonAssetValidator.TEXTURE_ITEM_QUALITY)  // Must exist in registry
```

`CommonAssetValidator.TEXTURE_ITEM_QUALITY` checks:
1. Path starts with `UI/ItemQualities/` (required root)
2. Path ends with `.png` (required extension)
3. `CommonAssetRegistry.hasCommonAsset(path)` returns true
4. If not found and `isUIAsset=true`, also tries `{name}@2x.png`
5. If still not found: `results.fail()` → throws `CodecValidationException` → asset rejected

## Validation is a HARD FAIL

From `ValidationResults.logOrThrowValidatorExceptions()`:
- `FAIL` results → throws `CodecValidationException`
- `WARNING` results → logs warning, clears exceptions, continues

The catch block in `AssetStore.decodeAsset0()` handles `CodecValidationException` by adding the asset key to `failedToLoadKeys`, meaning it's removed entirely.

## Loading Order

For `AssetPackRegisterEvent` (plugin asset packs):
1. **Priority -32**: `CommonAssetModule` loads `Common/` directory files into `CommonAssetRegistry`
2. **Priority -16**: `AssetModule` calls `AssetRegistryLoader.loadAssets()` which loads `Server/` assets

This means if your plugin includes textures in `Common/UI/ItemQualities/`, they will be registered BEFORE quality JSON validation runs.

## Pre-loaded Assets Bypass Validation

`DEFAULT_ITEM_QUALITY` is pre-loaded as a Java object via `preLoadAssets(Collections.singletonList(...))`. The `loadAssets(packKey, List<T>)` method puts these directly into the `loadedAssets` map without going through JSON decode or validation. Pre-loaded assets never hit `CommonAssetValidator`.

## Workarounds for Custom Qualities

### Option A: Reference existing textures (recommended)
Use texture paths known to exist in the base game. Safe choices:
- Use `Default` textures for fields you don't care about visually
- Use `Uncommon` textures for tooltip/slot (but NOT SpecialSlot)

### Option B: Include your own textures
Add actual PNG files to your plugin's `Common/UI/ItemQualities/` directory. They'll be loaded at priority -32 before quality validation runs.

### Option C: No way to skip validation
- All texture fields are **required** (`nonNull` + `nonEmptyString`)
- Cannot set to null or empty string
- Cannot disable validation
- `--validate-assets` flag only controls whether validation failures cause server EXIT, not whether validation runs
