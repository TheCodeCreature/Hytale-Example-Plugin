---
topic: "ItemGrid Slot Styling & Background Customization"
category: "Plugin API / Custom UI"
updated: 2026-04-30
sources:
  - "decompiled ItemGridSlot.java — full field/setter list"
  - "decompiled PatchStyle.java — full property list"
  - "decompiled Area.java — sub-area definition"
  - "decompiled Value.java — Value.of() and Value.ref() factory methods"
  - "docs/Resources/Common/Pages/EntitySpawnPage.ui — SlotBackground usage"
  - "docs/hytale/assets/common-ui-catalog.md — texture asset catalog"
  - "docs/hytale/ui/ui-element-reference.md — ItemGrid element properties"
---

# ItemGrid Slot Styling & Background Customization

## 1. ItemGridStyle (`.ui` Inline Style)

**No `ItemGridStyle` Java class exists.** The `Style:` property on `ItemGrid` elements is an anonymous inline tuple in `.ui` markup — not a named Java type. It is NOT settable from the server-side Java API.

### Known Properties

| Property | Type | Description | Settable from Java? |
|----------|------|-------------|---------------------|
| `SlotSize` | Integer | Pixel size of each slot cell | No (.ui only) |
| `SlotIconSize` | Integer | Pixel size of the item icon within the slot | No (.ui only) |
| `SlotSpacing` | Integer | Pixel gap between slots | No (.ui only) |
| `SlotBackground` | String (texture path) | **Grid-wide** background image for every slot | No (.ui only) |

### What's NOT in ItemGridStyle

There are **no** `HoveredSlotBackground`, `SelectedSlotBackground`, `UncraftableSlotBackground`, or `SlotHighlight` properties. The engine handles hover/selection highlighting internally on the client side.

### .ui Usage Example

```
ItemGrid #RecipeGrid {
    SlotsPerRow: 4;
    DisplayItemQuantity: true;
    RenderItemQualityBackground: false;
    Style: (
        SlotSize: 46,
        SlotIconSize: 46,
        SlotSpacing: 0,
        SlotBackground: "../Common/BlockSelectorSlotBackground.png"
    );
}
```

### ItemGrid Element Properties (set in .ui)

| Property | Type | Description |
|----------|------|-------------|
| `SlotsPerRow` | Integer | Columns in the grid |
| `DisplayItemQuantity` | Boolean | Show quantity text on slots |
| `RenderItemQualityBackground` | Boolean | Show quality-colored backgrounds (rarity colors) |
| `InventorySectionId` | String | Link to a server inventory section |
| `Style` | ItemGridStyle tuple | Grid styling (see above) |

---

## 2. ItemGridSlot (Server-Side Java API)

Source: [ItemGridSlot.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/ui/ItemGridSlot.java)

### All Fields (Codec)

| Codec Key | Java Field | Type |
|-----------|------------|------|
| `"ItemStack"` | `itemStack` | `ItemStack` |
| `"Background"` | `background` | `Value<PatchStyle>` |
| `"Overlay"` | `overlay` | `Value<PatchStyle>` |
| `"Icon"` | `icon` | `Value<PatchStyle>` |
| `"IsItemIncompatible"` | `isItemIncompatible` | `boolean` |
| `"Name"` | `name` | `String` |
| `"Description"` | `description` | `String` |
| `"SkipItemQualityBackground"` | `skipItemQualityBackground` | `boolean` |
| `"IsActivatable"` | `isActivatable` | `boolean` |
| `"IsItemUncraftable"` | `isItemUncraftable` | `boolean` |

### All Setters (fluent builder pattern)

| Method | Parameter Type | Returns | Purpose |
|--------|---------------|---------|---------|
| `setItemStack(ItemStack)` | `ItemStack` | `ItemGridSlot` | Set the displayed item |
| **`setBackground(Value<PatchStyle>)`** | `Value<PatchStyle>` | `ItemGridSlot` | **Per-slot custom background texture** |
| **`setOverlay(Value<PatchStyle>)`** | `Value<PatchStyle>` | `ItemGridSlot` | **Per-slot overlay texture (drawn on top)** |
| **`setIcon(Value<PatchStyle>)`** | `Value<PatchStyle>` | `ItemGridSlot` | **Per-slot custom icon (replaces item icon?)** |
| `setItemIncompatible(boolean)` | `boolean` | `ItemGridSlot` | Visual "incompatible" indicator |
| `setName(String)` | `String` | `ItemGridSlot` | Custom tooltip name |
| `setDescription(String)` | `String` | `ItemGridSlot` | Custom tooltip description |
| `setItemUncraftable(boolean)` | `boolean` | `void` | Visual "uncraftable" dimming effect |
| `setActivatable(boolean)` | `boolean` | `void` | Whether slot fires click/activation events |
| `setSkipItemQualityBackground(boolean)` | `boolean` | `void` | Skip quality-based rarity background color |

### Key Insight: Per-Slot Backgrounds ARE Possible

The three `Value<PatchStyle>` setters (`setBackground`, `setOverlay`, `setIcon`) allow **per-slot** visual customization from server-side Java code. This is separate from the grid-wide `SlotBackground` in `.ui` markup.

### Java Usage Examples

```java
// Inline PatchStyle — custom background texture per slot
ItemGridSlot slot = new ItemGridSlot(new ItemStack("Rock_Stone", 4));
slot.setBackground(Value.of(
    new PatchStyle(Value.of("Common/BlockSelectorSlotBackground.png"))
));

// Reference a named style from a .ui file
slot.setBackground(Value.ref("Common.ui", "SlotBackground"));

// With colored tint
PatchStyle bgStyle = new PatchStyle(Value.of("Common/BlockSelectorSlotBackground.png"))
    .setColor(Value.of("#88ff88"));
slot.setBackground(Value.of(bgStyle));

// Overlay on top of the slot
slot.setOverlay(Value.of(
    new PatchStyle(Value.of("Common/BlockSelectorSlotDropIcon.png"))
));
```

---

## 3. PatchStyle (9-Slice Background System)

Source: [PatchStyle.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/ui/PatchStyle.java)

### All Properties

| Property | Type | Description |
|----------|------|-------------|
| `TexturePath` | `Value<String>` | Path to the texture image (relative to `Common/UI/Custom/`) |
| `Border` | `Value<Integer>` | Uniform 9-slice border (all sides) |
| `HorizonzalBorder` | `Value<Integer>` | Left/Right 9-slice border (note: typo in engine — "Horizonzal") |
| `VerticalBorder` | `Value<Integer>` | Top/Bottom 9-slice border |
| `Color` | `Value<String>` | Color tint (hex string e.g. `"#ff5555"`) |
| `Area` | `Value<Area>` | Sub-area of the texture to use (X, Y, Width, Height) |

### Constructors

```java
new PatchStyle()                                          // Empty
new PatchStyle(Value<String> texturePath)                 // Texture only
new PatchStyle(Value<String> texturePath, Value<Integer> border)  // Texture + border
```

### All Setters (fluent)

| Method | Parameter | Returns |
|--------|-----------|---------|
| `setTexturePath(Value<String>)` | Texture path | `PatchStyle` |
| `setBorder(Value<Integer>)` | Uniform border | `PatchStyle` |
| `setHorizontalBorder(Value<Integer>)` | H border | `PatchStyle` |
| `setVerticalBorder(Value<Integer>)` | V border | `PatchStyle` |
| `setColor(Value<String>)` | Color tint | `PatchStyle` |
| `setArea(Value<Area>)` | Source area | `PatchStyle` |

### Area Sub-Type

```java
new Area()
    .setX(0).setY(0)
    .setWidth(32).setHeight(32)
```

Use `Area` with `PatchStyle.setArea()` to select a sub-region of a texture atlas.

### .ui Syntax

```
// Simple — stretch entire texture
Background: PatchStyle(TexturePath: "Common/Button.png", Border: 0);

// 9-slice — border pixels are not stretched
Background: PatchStyle(TexturePath: "Common/ContainerPanelPatch.png", Border: 4);

// Separate horizontal/vertical borders
Background: PatchStyle(
    TexturePath: "Common/Panel.png",
    HorizontalBorder: 80,
    VerticalBorder: 12
);
```

---

## 4. Value<T> (Reference System)

Source: [Value.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/ui/Value.java)

Two factory methods for creating values:

| Method | Purpose | Example |
|--------|---------|---------|
| `Value.of(T value)` | Inline literal value | `Value.of("Common/Slot.png")` |
| `Value.ref(String document, String valueName)` | Reference a named `@variable` from a `.ui` file | `Value.ref("Common.ui", "SlotBackground")` |

`Value.ref()` resolves at render time on the client, referencing a `@VarName` defined in the specified `.ui` document.

---

## 5. Available PNG Assets (Common/ directory)

These textures ship with the base game at `Common/UI/Custom/Common/`:

| File | Type | Purpose |
|------|------|---------|
| `BlockSelectorSlotBackground.png` | Texture | Item slot background (inventory/selector style) |
| `BlockSelectorSlotDropIcon.png` | Texture | Drop indicator overlay for drag-and-drop targets |
| `ContainerPanelPatch.png` | 9-patch | Rounded panel background (use with `Border: 4`) |
| `HintContainerPanelPatch@2x.png` | 9-patch | Hint container panel (HUD/Toolbar area) |
| `LegendContainerPanelPatch@2x.png` | 9-patch | Legend container panel (HUD/Toolbar area) |

### Reference Paths (from Pages/ subdirectory)

```
"../Common/BlockSelectorSlotBackground.png"     // Slot bg
"../Common/BlockSelectorSlotDropIcon.png"        // Drop icon
"../Common/ContainerPanelPatch.png"              // Panel bg
```

---

## 6. Built-In Usage Examples

### EntitySpawnPage.ui — Grid-Wide SlotBackground

The engine's built-in entity spawn page uses `SlotBackground` in the ItemGridStyle to set a **grid-wide** background for all slots:

```
ItemGrid #ItemMaterialSlot {
    Anchor: (Width: 46, Height: 46, Horizontal: 0, Vertical: 0);
    SlotsPerRow: 1;
    Style: (
        SlotSize: 46,
        SlotIconSize: 46,
        SlotSpacing: 0,
        SlotBackground: "../Common/BlockSelectorSlotBackground.png"
    );
}
```

### BlueprintBenchPage.ui — No SlotBackground (plain grid)

The plugin's blueprint bench page uses grids **without** `SlotBackground` and with `RenderItemQualityBackground: false`:

```
ItemGrid #RecipeGrid {
    FlexWeight: 1;
    SlotsPerRow: 4;
    DisplayItemQuantity: true;
    RenderItemQualityBackground: false;
    Style: (SlotSize: 64, SlotIconSize: 64, SlotSpacing: 8);
}
```

---

## 7. Styling Strategy Summary

### Two Layers of Background Control

| Layer | Where Defined | Scope | Mechanism |
|-------|---------------|-------|-----------|
| **Grid-wide** | `.ui` file `Style:` tuple | All slots in the grid | `SlotBackground: "path.png"` |
| **Per-slot** | Java `ItemGridSlot` setter | Individual slots | `slot.setBackground(Value.of(patchStyle))` |

### What You CAN Do

- Set a grid-wide `SlotBackground` texture in `.ui` markup
- Set per-slot `Background`, `Overlay`, and `Icon` via `Value<PatchStyle>` from Java
- Use `PatchStyle.setColor()` to tint backgrounds per-slot
- Use `PatchStyle.setArea()` to select sub-regions of a texture atlas
- Skip item quality (rarity) backgrounds per-slot via `setSkipItemQualityBackground(true)`
- Mark slots as uncraftable/incompatible for visual dimming

### What You CANNOT Do

- Change hover/selection highlight colors or textures (client-controlled)
- Set per-slot background from `.ui` markup (only grid-wide `SlotBackground`)
- Dynamically change the grid-wide `SlotBackground` from Java (it's a `.ui`-only property)
- Access `HoveredSlotBackground`, `SelectedSlotBackground`, etc. (these don't exist)

---

## 8. Gotchas

- **`HorizonzalBorder`** — There's a typo in the engine: it's "Horizonzal" not "Horizontal" in the codec key. The Java setter is correctly named `setHorizontalBorder()`.
- **`Value.ref()` document paths** — Use the document path as it would be referenced from `Common/UI/Custom/`, e.g. `"Common.ui"` or `"Common/TextButton.ui"`.
- **Per-slot Background vs Grid SlotBackground** — These are independent. Setting a per-slot `Background` via `ItemGridSlot.setBackground()` likely overrides the grid-wide `SlotBackground` for that specific slot.
- **`RenderItemQualityBackground: false`** — Set this on the ItemGrid in `.ui` to prevent the engine from drawing quality-colored backgrounds behind items. Use `skipItemQualityBackground` on `ItemGridSlot` for per-slot control.
