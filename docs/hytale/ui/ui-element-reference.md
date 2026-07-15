---
topic: "UI Element Reference"
category: "Plugin API / Custom UI"
updated: 2026-04-28
sources:
  - "https://hytale-docs.com/docs/api/server-internals/ui-reference"
  - "https://hytale-docs.com/docs/api/server-internals/custom-ui"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation"
  - "docs/hytale/plugins/ui-file-system.md"
  - "docs/hytale/plugins/ui-element-selector-reference.md"
  - "docs/hytale/plugins/custom-ui-item-display.md"
  - "docs/hytale/plugins/ui-grid-layout-research.md"
---

# UI Element Reference

## Summary

Complete reference for all UI element types available in Hytale's `.ui` DSL, their properties, and usage patterns. Elements are rendered by the client and controlled by the server via `UICommandBuilder` selectors.

---

## Core Layout & Display Elements

### Group (Container)

The primary container element. Used for layout, spacing, backgrounds, and nesting.

```
Group {
    Anchor: (Width: 400, Height: 300);
    Background: #141c26;
    LayoutMode: Top;
    Padding: (Full: 20);
    FlexWeight: 1;
    Visible: true;

    // Children go here
}
```

| Property | Type | Description |
|----------|------|-------------|
| `Anchor` | Anchor | Size and position constraints |
| `Background` | Color / PatchStyle | Background color or 9-slice image |
| `LayoutMode` | Enum | How children are arranged |
| `Padding` | Padding | Internal spacing |
| `FlexWeight` | Number | Flexible sizing weight in flex layouts |
| `Visible` | Boolean | Visibility (hidden elements take no layout space) |
| `Enabled` | Boolean | Interaction enabled state |
| `Opacity` | Number (0–1) | Transparency |
| `ScrollbarStyle` | ScrollbarStyle | Style when using scrolling LayoutMode |

### Label (Text Display)

```
Label #StatusText {
    Text: "Status: Ready";
    Anchor: (Height: 24);
    Style: (FontSize: 16, TextColor: #ffffff, HorizontalAlignment: Center);
}
```

| Property | Type | Description |
|----------|------|-------------|
| `Text` | String | Display text (must be quoted) |
| `Style` | LabelStyle | Text styling |
| `Anchor` | Anchor | Size constraints |
| `Visible` | Boolean | Visibility |

### Panel

Styled container with built-in border/background. Similar to Group but with visual styling.

### Sprite

Image display with animation frame support.

| Property | Type | Description |
|----------|------|-------------|
| `TexturePath` | String | Path to texture file |
| `Anchor` | Anchor | Size constraints |
| `Tint` | Color | Color tint overlay |

### AssetImage

Image from an asset path.

### SceneBlur

Applies a background blur effect behind UI content.

---

## Button Elements

### TextButton

```
TextButton #MyButton {
    Text: "Click Me";
    Anchor: (Width: 120, Height: 44);
    Style: @MyButtonStyle;
}
```

| Property | Type | Description |
|----------|------|-------------|
| `Text` | String | Button label text |
| `Style` | TextButtonStyle | Button styling (states: Default, Hovered, Pressed, Disabled) |
| `Anchor` | Anchor | Size constraints |
| `Enabled` | Boolean | Can be clicked |
| `Visible` | Boolean | Visibility |

### Button

Basic clickable button without text. Used for icon-only buttons.

### ActionButton

Button with icon and alignment options.

### ToggleButton

On/off toggle button with checked/unchecked states.

### TabButton

Tab button for use within `TabNavigation` containers.

```
TabButton {
    Icon: "../../Common/RecipesIcon.png";
    TooltipText: "All";
    Id: "All";
}
```

### BackButton

Pre-positioned navigation back button.

### MenuItem

Menu item element for context menus.

---

## Input Elements

### TextField (Text Input)

```
TextField #NameInput {
    PlaceholderText: "Enter name...";
    Anchor: (Height: 38);
    Style: @DefaultInputFieldStyle;
    FlexWeight: 1;
}
```

| Property | Type | Description |
|----------|------|-------------|
| `Value` | String | Current text value |
| `PlaceholderText` | String | Placeholder when empty |
| `Style` | InputFieldStyle | Input styling |
| `FlexWeight` | Number | Flexible width |

### CompactTextField

Compact variant of TextField.

### MultilineTextField

Multi-line text input.

### NumberField

```
NumberField #AmountInput {
    Value: 100;
    Anchor: (Width: 80, Height: 38);
}
```

| Property | Type | Description |
|----------|------|-------------|
| `Value` | Number | Current numeric value |
| `PlaceholderText` | String | Placeholder text |

### CheckBox

```
CheckBox #MyCheckbox {
    Value: true;
    Anchor: (Width: 22, Height: 22);
    Style: @DefaultCheckBoxStyle;
}
```

### Slider

```
Slider #VolumeSlider {
    Value: 0.75;
    MinValue: 0;
    MaxValue: 1;
    Anchor: (Height: 20);
    Style: @DefaultSliderStyle;
}
```

### FloatSlider

Float-precision slider variant.

### SliderNumberField / FloatSliderNumberField

Slider combined with a numeric display.

### DropdownBox

```
DropdownBox #MyDropdown {
    Anchor: (Width: 200, Height: 32);
    Style: @DefaultDropdownBoxStyle;

    DropdownEntry {
        Value: "option1";
        Text: "Option One";
    }
}
```

### ColorPicker / ColorPickerDropdownBox / ColorOptionGrid

Color selection elements.

### CodeEditor

Code editor component (primarily for dev tools).

---

## Item & Inventory Elements

### ItemIcon (Simple Icon Display)

Displays an item's icon by asset ID. No quantity, quality, or durability.

```
ItemIcon #OutputIcon {
    ItemId: "Item_Wood_Planks";
    Anchor: (Width: 32, Height: 32);
    ShowItemTooltip: true;
}
```

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | String | Item asset ID (e.g., `"Food_Bread"`) |
| `ShowItemTooltip` | Boolean | Show full item tooltip on hover |

**Set from server:** `cmd.set("#OutputIcon.ItemId", "Item_Wood_Planks");`

### ItemSlot (Full Slot Display)

Self-contained display-only slot: icon + quantity + quality background + durability bar.

```
ItemSlot #OutputSlot {
    ItemId: "Food_Bread";
    Quantity: 5;
    ShowQuantity: true;
    ShowQualityBackground: true;
    Anchor: (Width: 48, Height: 48);
}
```

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | String | Item asset ID |
| `Quantity` | Integer | Item count |
| `ShowQuantity` | Boolean | Show quantity number |
| `ShowQualityBackground` | Boolean | Show quality color background |
| `ShowDurabilityBar` | Boolean | Show durability indicator |

### ItemSlotButton (Clickable Item Slot)

Wraps an `ItemSlot` to make it interactive. Fires `Activating`, `RightClicking`, etc.

```
ItemSlotButton #TradeButton {
    ItemSlot #OutputSlot {
        Anchor: (Width: 48, Height: 48);
    }
}
```

### ItemGrid (Inventory Grid)

Dedicated grid layout for item slots. Handles grid arrangement internally via `SlotsPerRow`.

```
ItemGrid #CostGrid {
    FlexWeight: 1;
    SlotsPerRow: 4;
    DisplayItemQuantity: true;
    RenderItemQualityBackground: false;
    Style: (SlotSize: 48, SlotSpacing: 4);
}
```

| Property | Type | Description |
|----------|------|-------------|
| `SlotsPerRow` | Integer | Columns in the grid |
| `DisplayItemQuantity` | Boolean | Show quantity on slots |
| `RenderItemQualityBackground` | Boolean | Show quality backgrounds |
| `InventorySectionId` | String | Link to server inventory section |
| `Style` | ItemGridStyle | Grid styling (SlotSize, SlotSpacing) |

**Populate from server:** `cmd.setObject("#CostGrid.Slots", itemGridSlotArray);`

### ItemPreviewComponent

3D preview of an item. Use for detailed item inspection.

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | String | Item to preview |
| `ItemScale` | Float | Scale factor |

### BlockSelector

Block-type selector with built-in grid. Used by building tools.

| Property | Type | Description |
|----------|------|-------------|
| `Capacity` | Integer | Number of slots |
| `Value` | String | Current selected block ID |
| `Style` | BlockSelectorStyle | Wraps an `ItemGridStyle` |

**Fires:** `ValueChanged` when selection changes.

> **Note:** `BlockSelector` does NOT accept child elements. It renders an `ItemGrid` internally.

---

## Navigation & Progress Elements

### TabNavigation

Tab navigation bar. Contains `TabButton` children.

```
TabNavigation #BenchTabs {
    Style: $C.@TopTabsStyle;
    SelectedTab: "All";

    TabButton { Id: "All"; TooltipText: "All"; }
    TabButton { Id: "Builders"; TooltipText: "Builders Bench"; }
}
```

**Fires:** `SelectedTabChanged` when the active tab changes (user interaction only — server `cmd.set` does NOT fire this event).

| Property | Type | Description |
|----------|------|-------------|
| `SelectedTab` | String | Id of the currently selected `TabButton` |

**`SelectedTab` resolution:** The `SelectedTab` property is resolved by **`TabButton.Id` string**, not by position index. `cmd.set("#BenchTabs.SelectedTab", "Workbench")` finds and selects the child whose `Id` is `"Workbench"`.

**Dynamic slot pattern gotchas:**

When slots in a `.ui` template are pre-seeded with specific values (icons, IDs) and then overridden at runtime via `cmd.set("#BenchTabs[N].Id", ...)`:
- Override commands must come **before** `cmd.set("#BenchTabs.SelectedTab", ...)` in the command batch — so the Ids are in place when `SelectedTab` resolves.
- The template's pre-seeded `Icon` values for each slot index are **meaningless** once Ids are remapped dynamically. If the server omits an icon override (e.g., `resolveMappedTabIcon` returns null), the slot will display the pre-seeded icon from the template — which is the icon for a different bench at that position. **Always override the icon unconditionally with a fallback** rather than conditionally skipping the set:

```java
// WRONG — leaves pre-seeded template icon if resolver returns null:
if (resolvedIcon != null) {
    cmd.set("#BenchTabs[" + tabIndex + "].Icon", resolvedIcon);
}

// CORRECT — always override:
cmd.set("#BenchTabs[" + tabIndex + "].Icon",
    resolvedIcon != null ? resolvedIcon : DEFAULT_BENCH_ICON);
```

### ScrollView

Scrollable container.

```
ScrollView {
    Anchor: (Width: 300, Height: 200);
    Style: @DefaultScrollbarStyle;

    Group { LayoutMode: Top; /* scrollable content */ }
}
```

### ProgressBar / CircularProgressBar

Progress indicators.

### TimerLabel

Countdown/timer display.

### HotkeyLabel

Keyboard shortcut label display.

---

## Layout Helpers

### DynamicPane / DynamicPaneContainer

Dynamic content switching panes.

### ReorderableList / ReorderableListGrip

Drag-reorderable list with grip handles.

### CharacterPreviewComponent

3D character preview.

---

## Property Types

### Anchor (Size & Position)

```
Anchor: (Width: 200, Height: 50);                    // Fixed size
Anchor: (Full: 0);                                    // Fill parent
Anchor: (Top: 10, Bottom: 10, Left: 20, Right: 20);  // Edge anchoring
Anchor: (Height: 40, Left: 0, Right: 0);              // Full width, fixed height
```

| Property | Type | Description |
|----------|------|-------------|
| `Width` | Integer | Fixed width (px) |
| `Height` | Integer | Fixed height (px) |
| `Top`, `Bottom`, `Left`, `Right` | Integer | Edge distances (px) |
| `Full` | Integer | All edges at same distance |
| `Horizontal` | Integer | Left + Right shorthand |
| `Vertical` | Integer | Top + Bottom shorthand |
| `MinWidth`, `MaxWidth` | Integer | Width constraints |

> All values are in **reference pixels** (the engine scales uniformly for resolution). No percentage or viewport-relative units exist.

### Padding

```
Padding: (Full: 20);                                   // Uniform
Padding: (Horizontal: 10, Vertical: 5);                // H/V shorthand
Padding: (Top: 10, Bottom: 20, Left: 15, Right: 15);   // Per-edge
```

> **Important:** Use padding on the parent, NOT margin on children. Hytale's UI has no margin property.

### LayoutMode

| Mode | Behavior |
|------|----------|
| `Top` | Vertical stack, top-to-bottom |
| `Bottom` | Vertical stack, bottom-aligned |
| `Left` | Horizontal stack, left-to-right |
| `Right` | Horizontal stack, right-aligned |
| `Center` | Center children horizontally |
| `Middle` | Center children vertically |
| `CenterMiddle` | Horizontal stack, centered both axes |
| `MiddleCenter` | Vertical stack, centered both axes |
| `Full` | Absolute positioning via Anchor |
| `Overlay` | Stack children on top of each other |
| `TopScrolling` | Vertical stack with scrollbar |
| `BottomScrolling` | Bottom-aligned with scrollbar |
| `LeftScrolling` | Horizontal with scrollbar |
| `RightScrolling` | Right-aligned horizontal with scrollbar |
| **`LeftCenterWrap`** | **Wrapping horizontal, each row centered** |

> **No `Grid` mode exists.** For grid layouts use `LeftCenterWrap` (wrapping) or `ItemGrid` (dedicated element).

### Color

```
Background: #ffffff;          // Opaque white
Background: #fff;             // Short form
Background: #141c26(0.95);   // 95% opacity
Background: #000000(0.5);    // 50% black overlay
```

### LabelStyle

```
LabelStyle(
    FontSize: 16,
    TextColor: #ffffff,
    RenderBold: true,
    RenderItalic: false,
    RenderUppercase: false,
    HorizontalAlignment: Center,    // Start, Center, End
    VerticalAlignment: Center,      // Top, Center, Bottom
    FontName: "Default",            // "Default", "Secondary", "Mono"
    LetterSpacing: 0,
    LineSpacing: 1.0,
    Wrap: true,
    Overflow: Ellipsis              // Ellipsis, Clip, Visible
)
```

### TextButtonStyle

```
@MyButtonStyle = TextButtonStyle(
    Default: (
        Background: #3a7bd5,
        LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true,
                     HorizontalAlignment: Center, VerticalAlignment: Center)
    ),
    Hovered: (Background: #4a8be5, LabelStyle: (...)),
    Pressed: (Background: #2a6bc5, LabelStyle: (...)),
    Disabled: (Background: #555555, LabelStyle: (...)),
    Sounds: @ButtonSounds
);
```

States: `Default`, `Hovered`, `Pressed`, `Disabled`. Each has `Background` and `LabelStyle`.

### PatchStyle (9-Slice Backgrounds)

For scalable backgrounds that avoid pixel stretching.

```
PatchStyle(
    TexturePath: "Common/Button.png",
    Border: 12                          // All sides
)

PatchStyle(
    TexturePath: "Common/Panel.png",
    HorizontalBorder: 80,              // Left/Right
    VerticalBorder: 12                  // Top/Bottom
)
```

> **Best Practice:** Always use `PatchStyle` for UI backgrounds (buttons, panels) to prevent pixel stretching artifacts. A border of 10–12 is standard for Hytale's 32x textures.

---

## Selector Syntax (Java API)

### Basic Selectors

```java
"#ElementId"                    // By ID
"#ElementId.PropertyName"       // Property access
"#Parent #Child"                // Nested element
"#Parent #Child.PropertyName"   // Nested property
```

### Dynamic Selectors (Indexed)

When appending templates to containers, they become indexed elements:

```java
cmd.append("#Container", "template.ui");   // Creates #Container[0]
cmd.append("#Container", "template.ui");   // Creates #Container[1]

cmd.set("#Container[0].Text", "Hello");    // Access first element
cmd.set("#Container[1].Text", "World");    // Access second element
```

> **Critical:** The appended template IS the element at that index. Do NOT try to navigate inside:
> - **Correct:** `cmd.set("#Container[0].Text", "Hello");`
> - **Wrong:** `cmd.set("#Container[0] #Button.Text", "Hello");` (unless the template has nested IDs)

### Selector Property Names

| In `.ui` file (template param) | In Java (runtime property) |
|--------------------------------|----------------------------|
| `@Text` | `.Text` |
| `@Checked` | `.Value` |
| `Visible:` | `.Visible` |
| `Text:` | `.Text` |
| `Value:` | `.Value` |

---

## Image Display

> **Warning:** The direct `Image` element node is widely deprecated due to parser instability. Use a `Group` with a `Background` property instead.

```
// Recommended: Group with Background
Group {
    Background: PatchStyle(TexturePath: "Common/MyImage.png", Border: 0);
    Anchor: (Width: 64, Height: 64);
}

// Legacy (avoid — may cause crashes):
Image {
    TexturePath: "Common/MyImage.png";
    Anchor: (Width: 64, Height: 64);
}
```

---

## See Also

- [Custom UI Overview](./custom-ui-overview.md) — Architecture, lifecycle, project structure
- [CommonUI Library Reference](./ui-commonui-library.md) — Reusable components from `Common.ui`
- [UI Data Binding](./ui-data-binding.md) — Event binding, value capture, and codec patterns
