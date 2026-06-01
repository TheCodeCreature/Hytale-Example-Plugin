---
topic: "Icon Paths & Image References in .ui Files"
category: "Plugin API / Custom UI"
updated: 2026-05-02
sources:
  - "CommonAssetValidator.java — ICON_ITEM_CATEGORIES, ICON_CRAFTING, ICON_RESOURCE, ICON_ITEM"
  - "ItemCategory.java — icon field + CODEC"
  - "docs/Resources/items/Furniture/Benches/Bench_Furniture.json — CraftingCategories icon paths"
  - "docs/Resources/items/Furniture/Benches/Bench_Builders.json — StructuralCrafting bench"
  - "docs/Resources/Common/Pages/UIGallery/Categories/ButtonsContent.ui — icon button patterns"
  - "docs/Resources/Common/Pages/UIGallery/Categories/ContainersContent.ui — TabButton Icon usage"
  - "docs/Resources/Common/Pages/UIGallery/Categories/InputContent.ui — CompactTextField Decoration Icon"
  - "src/main/resources/Common/UI/Custom/Pages/BlueprintBook/BlueprintBookPage.ui"
---

# Icon Paths & Image References in .ui Files

## Summary

Hytale has multiple icon asset directories, each validated by `CommonAssetValidator`. Icons are referenced in `.ui` files via relative paths from the `.ui` file's location. There is **no `Image` or `ImageButton` element** — icon-only buttons use `Button` (or `$C.@Button`) wrapping a `Group` with a `Background:` image path.

---

## Icon Asset Directories (from CommonAssetValidator.java)

The engine validates icon paths against these root directories under `Common/`:

| Validator Constant | Extension | Root Directory | Purpose |
|---|---|---|---|
| `ICON_ITEM_CATEGORIES` | `.png` | `Icons/ItemCategories/` | Item category tab icons (Wood, Rock, Furniture, etc.) |
| `ICON_CRAFTING` | `.png` | `Icons/CraftingCategories/` | Crafting bench category icons (per-bench subcategories) |
| `ICON_RESOURCE` | `.png` | `Icons/ResourceTypes/` | Resource type icons |
| `ICON_ITEM` | `.png` | `Icons/ItemsGenerated/` or `Icons/Items/` | Individual item icons (3D-rendered or manual) |
| `ICON_ENTITY_STAT` | `.png` | `Icons/EntityStats/` | Entity stat icons (health, hunger, etc.) |
| `ICON_MODEL` | `.png` | `Icons/ModelsGenerated/` or `Icons/Models/` | Model preview icons |

Source: [CommonAssetValidator.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/common/CommonAssetValidator.java)

---

## ItemCategory Icons

### How They're Defined

`ItemCategory` assets have an `"Icon"` field (String) validated by `ICON_ITEM_CATEGORIES`:

```java
// ItemCategory.java
.<String>append(new KeyedCodec<>("Icon", Codec.STRING),
    (itemCategory, s) -> itemCategory.icon = s,
    itemCategory -> itemCategory.icon)
.addValidator(CommonAssetValidator.ICON_ITEM_CATEGORIES)
```

This means the `Icon` value must be a `.png` file under `Icons/ItemCategories/`.

### Expected Path Format in JSON Assets

```json
{
  "Id": "Wood",
  "Name": "server.itemCategories.wood",
  "Icon": "Icons/ItemCategories/Wood.png",
  "Order": 1
}
```

### Important: These icons are **base game assets**

ItemCategory icon files (e.g., `Icons/ItemCategories/Wood.png`) are shipped with the base game client. They are **not** in the plugin's resource directory. The plugin's `ItemCategory` JSON only references them by path — the client resolves them from the game's `Common/` asset root.

---

## CraftingCategories Icons (Known Files)

From `Bench_Furniture.json`, these crafting category icons exist in the base game:

| Path | Used By |
|------|---------|
| `Icons/CraftingCategories/Furniture/Storage.png` | Furniture Bench — Storage tab |
| `Icons/CraftingCategories/Furniture/Beds.png` | Furniture Bench — Beds tab |
| `Icons/CraftingCategories/Furniture/Lighting.png` | Furniture Bench — Lighting tab |
| `Icons/CraftingCategories/Furniture/Pottery.png` | Furniture Bench — Pottery tab |
| `Icons/CraftingCategories/Furniture/Textiles.png` | Furniture Bench — Textiles tab |
| `Icons/CraftingCategories/Furniture/Village_Walls.png` | Furniture Bench — Village Walls tab |
| `Icons/CraftingCategories/Furniture/Misc.png` | Furniture Bench — Misc tab |
| `Icons/CraftingCategories/Furniture/Seasonal.png` | Furniture Bench — Seasonal tab |
| `Icons/CraftingCategories/Builders/Blocks.png` | Builders Bench (referenced in design docs) |

**Note**: The Builders bench (`Bench_Builders.json`) uses `StructuralCrafting` type and lists categories by ID only (e.g., `"WoodPlanks"`, `"Bricks"`, `"Stairs"`) — it does **not** include icon paths in the JSON. The native crafting UI resolves icons via the `ItemCategory` asset system.

---

## How to Reference Images in .ui Files

### Path Resolution Rules

1. **All image paths in `.ui` files are relative to the `.ui` file's own location** (official: `UIPath` type, per Hypixel Studios docs)
2. Use `../` to navigate up the directory tree
3. The resolved path must land within the `Common/UI/Custom/` namespace — you **cannot** navigate above it to reach the game's top-level `Common/Icons/` directories
4. For the definitive algorithm and full reference, see [Path Resolution — Definitive Reference](./path-resolution-definitive.md)

### From `Pages/BlueprintBook/` (your current location)

Your `.ui` file is at:
```
Common/UI/Custom/Pages/BlueprintBook/BlueprintBookPage.ui
```

To reference assets in `Common/`:
```
../../Common/RecipesIcon.png      ← goes up to Custom/, then Common/ sibling
```

To reference assets in an `Icons/` directory, the path depends on where those icon files are actually served from. The `Icons/` directories are under the **game's `Common/` root**, not under `Common/UI/Custom/`.

### Known Working .ui Image Paths

These patterns are confirmed working in existing `.ui` files:

| From Location | Target | Path in .ui |
|---|---|---|
| `Pages/BlueprintBook/*.ui` | `Common/RecipesIcon.png` | `"../../Common/RecipesIcon.png"` |
| `Pages/BlueprintBook/*.ui` | `Common/BlockSelectorSlotBackground.png` | `"../../Common/BlockSelectorSlotBackground.png"` |
| `Pages/UIGallery/Categories/*.ui` | `Common/RecipesIcon.png` | `"../../../Common/RecipesIcon.png"` |
| `Pages/UIGallery/Categories/*.ui` | `Common/SearchIcon.png` | `"../../../Common/SearchIcon.png"` |
| `Pages/*.ui` (one deep) | `Common/ContainerPanelPatch.png` | `"../Common/ContainerPanelPatch.png"` |

### Known Common/ UI Assets

| File | Purpose |
|------|---------|
| `Common/RecipesIcon.png` | Generic recipe/crafting icon (used for tab buttons) |
| `Common/BlockSelectorSlotBackground.png` | Background for item grid slots |
| `Common/BlockSelectorSlotDropIcon.png` | Drop zone icon for item slots |
| `Common/ContainerPanelPatch.png` | 9-slice container background |
| `Common/SearchIcon.png` | Search input icon |
| `Common/Spinner.png` | Loading spinner |

---

## How to Display Icons in .ui Elements

### TabButton — Icon-only tab (built-in element)

`TabButton` has a native `Icon:` property. Used inside `TabNavigation`.

```
TabNavigation #MyTabs {
    Style: $C.@TopTabsStyle;
    SelectedTab: "All";
    Anchor: (Height: 66, Left: 2, Right: 0);

    TabButton {
        Icon: "../../Common/RecipesIcon.png";
        TooltipText: "All Items";
        Id: "All";
    }
}
```

### Icon-only Button (no text)

There is **no `ImageButton` element** in Hytale's UI system. Instead, use `Button` (or `$C.@Button`) and place a child `Group` with a `Background:` image:

```
$C.@Button {
    @Anchor = (Right: 10);

    Group {
        Anchor: (Width: 32, Height: 32);
        Background: "../../Common/RecipesIcon.png";
    }
}
```

Variants: `$C.@SecondaryButton`, `$C.@TertiaryButton`, `$C.@CancelButton`

Source: [ButtonsContent.ui](../../../docs/Resources/Common/Pages/UIGallery/Categories/ButtonsContent.ui) — "Icon Buttons Section"

### TextButton with inline icon — NOT directly supported

`TextButton` does not have an `Icon:` property. For text + icon, use a `Group` with `LayoutMode: Left` containing a Button (icon) and a Label (text) side by side.

### CompactTextField Decoration Icon

`CompactTextField` supports an icon via the `Decoration` property:

```
CompactTextField {
    Decoration: (
        Default: (
            Icon: (Texture: "../../Common/SearchIcon.png", Width: 16, Height: 16, Offset: 9),
            ClearButtonStyle: $C.@ClearButtonStyle
        )
    );
}
```

### Sprite Element — Image display (non-interactive)

```
Sprite {
    TexturePath: "Common/Spinner.png";
    Anchor: (Width: 32, Height: 32);
}
```

### Group Background — Simplest image display

```
Group {
    Anchor: (Width: 32, Height: 32);
    Background: "../../Common/RecipesIcon.png";
}
```

For 9-slice (stretchable) images:
```
Group {
    Background: (TexturePath: "../Common/ContainerPanelPatch.png", Border: 4);
}
```

---

## Gotchas

1. **No `Image` or `ImageButton` element exists** — use `Group { Background: "..."; }` or `$C.@Button { Group { Background: "..."; } }` for icon buttons

2. **Relative path from `.ui` file location** — all image paths resolve relative to the `.ui` file, not from some root. Count your `../` carefully.

3. **`Icons/ItemCategories/` vs `Icons/CraftingCategories/`** — these are DIFFERENT directories:
   - `ItemCategories/` = material groups (Wood, Rock, Stone, Metal) — used by `ItemCategory` assets
   - `CraftingCategories/` = bench subcategories (Furniture/Storage, Furniture/Beds) — used by `CraftingBench` category definitions

4. **ItemCategory icons are base game assets** — they exist in the game client's `Common/` root, not in your plugin. You can reference them if the path resolves, but you cannot inspect or list them from the plugin JAR.

5. **The `../../Common/` prefix** — from `Pages/BlueprintBook/*.ui`, this navigates up to `Common/UI/Custom/` and then into the sibling `Common/` directory that contains shared UI assets (RecipesIcon.png, etc.). This is NOT the same as the game-wide `Common/` root where `Icons/` lives.

6. **CraftingCategories icons are available** — since the native crafting UI uses them, they exist client-side. The confirmed paths from `Bench_Furniture.json` can be referenced if the path resolution supports reaching the game's `Common/Icons/` from a `.ui` file.

7. **Testing icon paths** — if an icon path fails to resolve, the UI will render without the icon (no crash). Test by deploying and checking if the tab/button shows the expected image.

---

## Answered Questions (See Definitive Reference)

- **Can `.ui` files reference `Icons/ItemCategories/*.png` directly?** **NO.** Per the official Hypixel Studios documentation, UIPath resolution is relative to the `.ui` file and confined to the `Common/UI/Custom/` tree. The `Icons/` directories live under the game's top-level `Common/`, which is outside the UI namespace. See [Path Resolution — Definitive Reference](./path-resolution-definitive.md).
- **Can plugins ship custom icon PNGs?** **YES.** Plugin `Common/UI/Custom/` contents are merged with the game's `Common/UI/Custom/` namespace when `IncludesAssetPack: true`. Place custom textures at e.g., `src/main/resources/Common/UI/Custom/Common/MyIcon.png`.
- **To display item icons without texture paths**, use `ItemIcon` elements and set `.ItemId` via `cmd.set()`. The engine resolves the icon from its internal asset registry — no path needed.

## See Also

- [Path Resolution — Definitive Reference](./path-resolution-definitive.md) — Complete algorithm, working examples, limitations
- [UI Element Reference](./ui-element-reference.md) — All element types and properties
- [CommonUI Library Reference](./ui-commonui-library.md) — Reusable components and styles
