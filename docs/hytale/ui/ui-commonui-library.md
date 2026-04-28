---
topic: "CommonUI Library Reference"
category: "Plugin API / Custom UI"
updated: 2026-04-28
sources:
  - "https://hytale-docs.com/docs/api/server-internals/custom-ui (Complete Common.ui Reference section)"
  - "https://hytale-docs.com/docs/api/server-internals/ui-reference (Common.ui Components + Styles)"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling"
  - "docs/hytale/plugins/ui-file-system.md"
  - "src/main/resources/Common/UI/Custom/Pages/BlueprintBench/BlueprintBenchPage.ui"
---

# CommonUI Library Reference (`Common.ui`)

## Summary

Hytale provides a shared UI component library at `Common/UI/Custom/Common.ui` that contains pre-built, styled components (buttons, inputs, containers, separators, spinners) and reusable styles. This library provides consistent visual styling matching the game's native UI. All custom plugin pages should import and use these components as the foundation.

## How to Import

```
$C = "../Common.ui";
```

> The path is relative to your `.ui` file's location. If your file is at `Common/UI/Custom/Pages/MyPlugin/MyPage.ui`, use `$C = "../../Common.ui";`.

Then reference components as `$C.@ComponentName` and styles as `$C.@StyleName`.

---

## Style Constants

These numeric constants define standard dimensions used throughout the UI:

```
@PrimaryButtonHeight = 44;
@SmallButtonHeight = 32;
@BigButtonHeight = 48;
@ButtonPadding = 24;
@DefaultButtonMinWidth = 172;
@ButtonBorder = 12;
@DropdownBoxHeight = 32;
@TitleHeight = 38;
@InnerPaddingValue = 8;
@FullPaddingValue = 17;         // @InnerPaddingValue + 9
@DisabledColor = #797b7c;
```

---

## Button Components

| Component | Parameters | Description |
|-----------|------------|-------------|
| `@TextButton` | `@Text`, `@Anchor`, `@Sounds` | Primary button (blue) with text |
| `@SecondaryTextButton` | `@Text`, `@Anchor`, `@Sounds` | Secondary button (gray) with text |
| `@TertiaryTextButton` | `@Text`, `@Anchor`, `@Sounds` | Tertiary button with text |
| `@CancelTextButton` | `@Text`, `@Anchor`, `@Sounds` | Destructive/cancel button (red) |
| `@SmallSecondaryTextButton` | `@Text`, `@Anchor`, `@Sounds` | Small secondary button |
| `@SmallTertiaryTextButton` | `@Text`, `@Anchor`, `@Sounds` | Small tertiary button |
| `@Button` | `@Anchor`, `@Sounds` | Square icon button (no text) |
| `@SecondaryButton` | `@Anchor`, `@Sounds`, `@Width` | Secondary square button |
| `@TertiaryButton` | `@Anchor`, `@Sounds`, `@Width` | Tertiary square button |
| `@CancelButton` | `@Anchor`, `@Sounds`, `@Width` | Cancel square button |
| `@CloseButton` | — | Pre-positioned close button (32×32) |
| `@BackButton` | — | Pre-positioned back navigation button |

### Usage Examples

```
$C = "../../Common.ui";

Group {
    LayoutMode: Left;
    Anchor: (Height: 50);

    $C.@TextButton #SaveBtn {
        @Text = "Save";
        Anchor: (Width: 100, Height: 44);
    }

    Group { Anchor: (Width: 10); }    // Spacer

    $C.@SecondaryTextButton #CancelBtn {
        @Text = "Cancel";
        Anchor: (Width: 100, Height: 44);
    }

    Group { Anchor: (Width: 10); }

    $C.@CancelTextButton #DeleteBtn {
        @Text = "Delete";
        Anchor: (Width: 100, Height: 44);
    }
}
```

---

## Input Components

| Component | Parameters | Description |
|-----------|------------|-------------|
| `@TextField` | `@Anchor` | Text input field (default height: 38) |
| `@NumberField` | `@Anchor` | Numeric-only input field (default height: 38) |
| `@DropdownBox` | `@Anchor` | Dropdown selector (default: 330×32) |
| `@CheckBox` | — | Checkbox only (22×22) |
| `@CheckBoxWithLabel` | `@Text`, `@Checked` | Checkbox with label text |

### TextField Properties

- `PlaceholderText` — Placeholder text when empty
- `FlexWeight` — Flexible width in flex layouts
- `Value` — Current text value

### NumberField Properties

- `Value` — Current numeric value
- `PlaceholderText` — Placeholder text

### CheckBoxWithLabel Properties

- `@Text` — Label text (template parameter)
- `@Checked` — Initial checked state: `true`/`false` (template parameter)

### Usage Examples

```
// Text input with label
Group {
    LayoutMode: Left;
    Anchor: (Height: 44);

    Label {
        Text: "Username";
        Anchor: (Width: 100);
        Style: (FontSize: 14, TextColor: #96a9be, VerticalAlignment: Center);
    }

    $C.@TextField #UsernameInput {
        FlexWeight: 1;
        PlaceholderText: "Enter username...";
    }
}

// Number input
$C.@NumberField #AmountInput {
    Anchor: (Width: 80);
    Value: 100;
}

// Checkboxes
$C.@CheckBoxWithLabel #EnableOption {
    @Text = "Enable this feature";
    @Checked = true;
    Anchor: (Height: 28);
}

// Dropdown with entries
$C.@DropdownBox #MyDropdown {
    Anchor: (Width: 200, Height: 36);

    DropdownEntry { Value: "option1"; Text: "First Option"; }
    DropdownEntry { Value: "option2"; Text: "Second Option"; }
}
```

---

## Container Components

| Component | Parameters | Description |
|-----------|------------|-------------|
| `@Container` | `@ContentPadding`, `@CloseButton` | Styled container with title area and content area |
| `@DecoratedContainer` | `@ContentPadding`, `@CloseButton` | Container with decorative border |
| `@Panel` | — | Simple panel with border |
| `@PageOverlay` | — | Semi-transparent background overlay |

### Container Structure

The `@Container` component provides built-in structural sections:

- **`#Title`** — Title area (height: 38px) at the top
- **`#Content`** — Content area with padding below the title
- **`#CloseButton`** — Optional close button (when `@CloseButton = true`)

### Usage

```
$C.@Container {
    @CloseButton = true;
    Anchor: (Width: 400, Height: 300);

    // Elements placed here land inside #Content
    // Use #Title area for header content
}
```

### How Our BlueprintBenchPage Uses It

Our [BlueprintBenchPage.ui](../../../src/main/resources/Common/UI/Custom/Pages/BlueprintBench/BlueprintBenchPage.ui) uses `$C.@Container` as the main wrapper, placing content inside `#Title` and `#Content`:

```
$C.@Container {
    #Title {
        Group { LayoutMode: Left;
            $C.@Title { @Text = "Blueprint Bench:"; }
            $C.@Title #ActiveBenchLabel { Text: "All"; }
        }
        $C.@HeaderSearch {}
    }

    #Content {
        // Two-column layout: recipe browser | recipe details
    }
}
```

---

## Text Components

| Component | Parameters | Description |
|-----------|------------|-------------|
| `@Title` | `@Text`, `@Alignment` | Styled title label |
| `@Subtitle` | `@Text` | Styled subtitle label |
| `@TitleLabel` | — | Large centered title (FontSize: 40) |
| `@PanelTitle` | `@Text`, `@Alignment` | Panel section title |

### Usage

```
$C.@Title {
    @Text = "My Page Title";
    @Alignment = Center;
    Anchor: (Height: 38);
}

$C.@Subtitle {
    @Text = "Section subtitle";
}
```

---

## Layout / Separator Components

| Component | Parameters | Description |
|-----------|------------|-------------|
| `@ContentSeparator` | `@Anchor` | Horizontal line separator (height: 1) |
| `@VerticalSeparator` | — | Vertical separator line (width: 6) |
| `@HeaderSeparator` | — | Header section separator (5×34) |
| `@PanelSeparatorFancy` | `@Anchor` | Decorative panel separator |
| `@ActionButtonContainer` | — | Container for action buttons |
| `@ActionButtonSeparator` | — | Space between action buttons (width: 35) |

### Usage

```
$C.@ContentSeparator { Anchor: (Height: 1); }

Group { Anchor: (Height: 16); }    // Vertical spacer (manual)

$C.@VerticalSeparator {}

$C.@PanelSeparatorFancy {}
```

### Manual Spacers (Common Pattern)

```
// Vertical spacer
Group { Anchor: (Height: 20); }

// Horizontal spacer (in LayoutMode: Left)
Group { Anchor: (Width: 20); }

// Separator line
Group { Anchor: (Height: 1); Background: #333333; }
```

---

## Utility Components

| Component | Parameters | Description |
|-----------|------------|-------------|
| `@DefaultSpinner` | `@Anchor` | Loading spinner animation (32×32) |
| `@HeaderSearch` | `@MarginRight` | Search input field with magnifying glass icon |

### Usage

```
$C.@DefaultSpinner {
    Anchor: (Width: 32, Height: 32);
}

$C.@HeaderSearch {}    // Used in Container #Title for search functionality
```

---

## Available Styles

### Button Styles

| Style | Description |
|-------|-------------|
| `@DefaultTextButtonStyle` | Primary button (blue) |
| `@SecondaryTextButtonStyle` | Secondary button (gray) |
| `@TertiaryTextButtonStyle` | Tertiary button |
| `@CancelTextButtonStyle` | Destructive/cancel button (red) |
| `@SmallDefaultTextButtonStyle` | Small primary button |
| `@SmallSecondaryTextButtonStyle` | Small secondary button |
| `@DefaultButtonStyle` | Icon button (no text) |
| `@SecondaryButtonStyle` | Secondary icon button |
| `@TertiaryButtonStyle` | Tertiary icon button |
| `@CancelButtonStyle` | Cancel icon button |

### Label Styles

| Style | Properties |
|-------|------------|
| `@DefaultLabelStyle` | FontSize: 16, TextColor: #96a9be |
| `@DefaultButtonLabelStyle` | FontSize: 17, TextColor: #bfcdd5, Bold, Uppercase, Center |
| `@TitleStyle` | FontSize: 15, Bold, Uppercase, TextColor: #b4c8c9, Secondary font |
| `@SubtitleStyle` | FontSize: 15, Uppercase, TextColor: #96a9be |
| `@PopupTitleStyle` | FontSize: 38, Bold, Uppercase, Center, LetterSpacing: 2 |

### Input Styles

| Style | Description |
|-------|-------------|
| `@DefaultInputFieldStyle` | Default text input styling |
| `@DefaultInputFieldPlaceholderStyle` | Placeholder text styling (TextColor: #6e7da1) |
| `@InputBoxBackground` | Input field background |
| `@InputBoxHoveredBackground` | Input field hover state |
| `@InputBoxSelectedBackground` | Input field selected/focused state |

### Other Styles

| Style | Description |
|-------|-------------|
| `@DefaultScrollbarStyle` | Scrollbar styling |
| `@DefaultCheckBoxStyle` | Checkbox styling |
| `@DefaultDropdownBoxStyle` | Dropdown selector styling |
| `@DefaultSliderStyle` | Slider styling |
| `@DefaultTextTooltipStyle` | Tooltip styling |
| `@DefaultColorPickerStyle` | Color picker styling |
| `@TopTabsStyle` | Tab navigation styling (used by BlueprintBenchPage) |

---

## Style Inheritance from Common.ui

Custom styles can extend Common.ui styles using the spread operator:

```
@HeaderStyle = LabelStyle(
    ...$C.@DefaultLabelStyle,
    FontSize: 22,
    RenderBold: true,
    HorizontalAlignment: Start,
    VerticalAlignment: Center
);
```

This pattern is used extensively in our BlueprintBenchPage to create custom styles that inherit base properties from the engine's Common.ui.

---

## Custom TextButtonStyle Pattern

When Common.ui button styles don't match your needs:

```
@MyButtonStyle = TextButtonStyle(
    Default: (
        Background: #3a7bd5,
        LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true,
                     HorizontalAlignment: Center, VerticalAlignment: Center)
    ),
    Hovered: (
        Background: #4a8be5,
        LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true,
                     HorizontalAlignment: Center, VerticalAlignment: Center)
    ),
    Pressed: (
        Background: #2a6bc5,
        LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true,
                     HorizontalAlignment: Center, VerticalAlignment: Center)
    )
);
```

Or using `PatchStyle` for 9-slice texture backgrounds:

```
@MyButtonStyle = TextButtonStyle(
    Default: (
        Background: PatchStyle(TexturePath: "Common/Button.png", Border: 12),
        LabelStyle: @SomeLabelStyle
    ),
    // ... Hovered, Pressed, Disabled states
    Sounds: @ButtonSounds
);
```

---

## Patterns from Our BlueprintBenchPage

Our [BlueprintBenchPage.ui](../../../src/main/resources/Common/UI/Custom/Pages/BlueprintBench/BlueprintBenchPage.ui) demonstrates several CommonUI patterns:

### 1. Style Inheritance

```
@HeaderStyle = LabelStyle(
    ...$C.@DefaultLabelStyle,
    FontSize: 22, RenderBold: true
);
```

### 2. Container with Title and Search

```
$C.@Container {
    #Title { ... }
    #Content { ... }
}
```

### 3. Tab Navigation with CommonUI Style

```
TabNavigation #BenchTabs {
    Style: $C.@TopTabsStyle;
    SelectedTab: "All";
    TabButton { Id: "All"; TooltipText: "All"; }
    TabButton { Id: "Builders"; TooltipText: "Builders Bench"; }
}
```

### 4. Dropdown with CommonUI Component

```
$C.@DropdownBox #CraftableDropdown {
    @Anchor = (Height: 24, Left: 0, Right: 0);
    Value: "FULL";
    DropdownEntry { Value: "NONE"; Text: "No Filter"; }
    DropdownEntry { Value: "FULL"; Text: "Can Craft"; }
}
```

### 5. Scrollable Container

```
Group {
    LayoutMode: TopScrolling;
    ScrollbarStyle: $C.@DefaultScrollbarStyle;
    // Scrollable content here
}
```

### 6. PanelSeparatorFancy for Visual Breaks

```
$C.@PanelSeparatorFancy {}
```

---

## See Also

- [Custom UI Overview](./custom-ui-overview.md) — Architecture and lifecycle
- [UI Element Reference](./ui-element-reference.md) — All element types and properties
- [UI Data Binding](./ui-data-binding.md) — Event binding and codec patterns
- [Community: Vex UI Library](../community/vex-ui-library-research.md) — Third-party component library
