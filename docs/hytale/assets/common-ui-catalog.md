---
topic: "Common.ui Shared Asset Catalog"
category: "Plugin API / Custom UI"
updated: 2026-04-28
sources:
  - "decompiled EntitySpawnPage.java — Value.ref() calls"
  - "decompiled ChangeModelPage.java, PlaySoundPage.java, ParticleSpawnPage.java"
  - "decompiled CommandListPage.java, PluginListPage.java, InstanceListPage.java"
  - "decompiled SelectOverrideRespawnPointPage.java, ServerFileBrowser.java"
  - "decompiled PrefabPage.java, PrefabEditorSaveSettingsPage.java, PrefabEditorLoadSettingsPage.java"
  - "docs/Resources/Common/Pages/EntitySpawnPage.ui — full .ui markup"
  - "src/main/resources/Common/UI/Custom/Pages/StencilBook/*.ui — plugin .ui files"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/markup"
  - "hytalemodding.dev/en/docs/guides/plugin/ui (community guide)"
  - "docs/hytale/plugins/ui-file-system.md (local)"
  - "docs/Resources/Common/Pages/UIGallery/*.ui — engine UI gallery showcase page"
---

# Common.ui Shared Asset Catalog

## How to Import

### In `.ui` files

```
// From Pages/ subdirectory (one level deep):
$C = "../Common.ui";

// From Pages/MyPlugin/ subdirectory (two levels deep):
$C = "../../Common.ui";

// From the same directory as Common.ui:
$C = "Common.ui";
```

### In Java (for Value.ref style references)

```java
// Reference a named style from Common.ui
Value.ref("Common.ui", "DefaultTextButtonStyle")

// Reference a named style from Common/TextButton.ui
Value.ref("Common/TextButton.ui", "LabelStyle")
```

---

## 1. TEMPLATES (from Common.ui)

Templates are instantiated via `$C.@TemplateName { ... }` syntax. They produce full element subtrees. Override template variables with `@VarName = value;` inside the block.

### @Container

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Main page container with dark semi-transparent background |
| **Accepts** | `Anchor:` override, child elements |
| **Source** | `EntitySpawnPage.ui` line 6 |

```
// .ui usage — wraps entire page content
$C.@Container {
  Anchor: (Left: 50, Top: 170, Width: 450, Bottom: 120);

  // Page content goes here as children
  Label { Text: "My Page"; }
}
```

---

### @Title

| Property | Value |
|----------|-------|
| **Type** | Template (Group/Label) |
| **Produces** | Styled page header/title label |
| **Accepts** | `@Text` — the title text (string or translation key) |
| **Source** | `EntitySpawnPage.ui` line 12 |

```
// .ui usage
$C.@Title {
  @Text = %server.customUI.myPage.title;
}

// Or with a literal string
$C.@Title {
  @Text = "My Custom Page";
}
```

---

### @HeaderSearch

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Search input bar; creates element `#SearchInput` |
| **Accepts** | (none documented — use standard overrides) |
| **Source** | `EntitySpawnPage.ui` line 17 |

```
// .ui usage — typically placed in a header row
$C.@HeaderSearch {}

// Java — bind search value changes
eventBuilder.addEventBinding(
    CustomUIEventBindingType.ValueChanged, "#SearchInput",
    EventData.of("@SearchQuery", "#SearchInput.Value"), false);
```

**Note:** The `#SearchInput` selector is created by the template and can be targeted for event bindings and value reads.

---

### @TextButton

| Property | Value |
|----------|-------|
| **Type** | Template (TextButton) |
| **Produces** | Primary styled text button (blue/accent theme) |
| **Accepts** | `Text:`, `@Text`, standard TextButton properties |
| **Source** | `EntitySpawnPage.ui` line 219, hytalemodding.dev common-styling |

```
// .ui usage
$C.@TextButton #Spawn {
  Text: %server.customUI.entitySpawnPage.spawn;
}

$C.@TextButton #MyAction {
  Text: "Do Something";
  Anchor: (Height: 40);
}
```

---

### @SecondaryTextButton

| Property | Value |
|----------|-------|
| **Type** | Template (TextButton) |
| **Produces** | Secondary/muted styled text button (for tabs, secondary actions) |
| **Accepts** | `Text:`, `FlexWeight:`, standard TextButton properties |
| **Source** | `EntitySpawnPage.ui` lines 25, 34, 43, 105 |

```
// .ui usage — tabs
$C.@SecondaryTextButton #TabNPC {
  Text: %server.customUI.entitySpawnPage.tab.npc;
  FlexWeight: 1;
}

// .ui usage — secondary action
$C.@SecondaryTextButton #ClearMaterial {
  Text: %server.customUI.entitySpawnPage.clear;
  Anchor: (Top: 10);
  Visible: false;
}
```

**Java — toggle tab styles dynamically:**
```java
// Active tab gets primary style, inactive gets secondary
commandBuilder.set("#TabNPC.Style",
    activeTab.equals("NPC") ? TAB_STYLE_ACTIVE : TAB_STYLE_INACTIVE);

// Where:
private static final Value<String> TAB_STYLE_ACTIVE =
    Value.ref("Common.ui", "DefaultTextButtonStyle");
private static final Value<String> TAB_STYLE_INACTIVE =
    Value.ref("Common.ui", "SecondaryTextButtonStyle");
```

---

### @BackButton

| Property | Value |
|----------|-------|
| **Type** | Template (Button/Group) |
| **Produces** | Navigation "back" button, typically at page bottom |
| **Accepts** | (none documented) |
| **Source** | `EntitySpawnPage.ui` line 226 |

```
// .ui usage — typically last element in the page
$C.@BackButton {}
```

---

### @TextField

| Property | Value |
|----------|-------|
| **Type** | Template (TextField) |
| **Produces** | Styled single-line text input |
| **Accepts** | `PlaceholderText:`, `Anchor:`, `FlexWeight:`, standard TextField props |
| **Source** | `ui-file-system.md` examples, hytalemodding.dev common-styling |

```
// .ui usage
$C.@TextField #NameInput {
  FlexWeight: 1;
  PlaceholderText: "Enter name...";
}

$C.@TextField #SearchInput {
  Anchor: (Height: 36);
  PlaceholderText: "Search recipes...";
}
```

---

### @NumberField

| Property | Value |
|----------|-------|
| **Type** | Template (Group with numeric input) |
| **Produces** | Numeric input field with increment/decrement |
| **Accepts** | `@Anchor`, `Format:`, `Value:` |
| **Source** | `EntitySpawnPage.ui` line 202 |

```
// .ui usage
$C.@NumberField #Count {
  @Anchor = (Left: 25, Width: 80, Right: 0);
  Format: (
    MaxDecimalPlaces: 0,
    Step: 1,
    MinValue: 1,
    MaxValue: 100
  );
  Value: 1;
}
```

---

### @CheckBoxWithLabel

| Property | Value |
|----------|-------|
| **Type** | Template (Group with checkbox + label) |
| **Produces** | Checkbox with accompanying text label |
| **Accepts** | `@Text` — label text, `@Checked` — initial state (bool) |
| **Source** | `ui-file-system.md` examples, hytalemodding.dev common-styling |

```
// .ui usage
$C.@CheckBoxWithLabel #NotifyOption {
  @Text = "Enable notifications";
  @Checked = true;
  Anchor: (Height: 28);
}

$C.@CheckBoxWithLabel #CoordsOption {
  @Text = "Show coordinates";
  @Checked = false;
  Anchor: (Height: 28);
}
```

---

### @PanelSeparatorFancy

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Decorative horizontal divider/separator line |
| **Accepts** | (none documented) |
| **Source** | `EntitySpawnPage.ui` line 142, `StencilBookPage.ui` lines 300, 326 |

```
// .ui usage — insert between content sections
$C.@PanelSeparatorFancy {}
```

---

### @DecoratedContainer

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Container with decorative frame, includes title bar |
| **Accepts** | `Anchor:`, `#Title` named slot, `#Content` named slot |
| **Source** | `UIGalleryPage.ui` line 5 |

```
// .ui usage — decorated container with title and content areas
$C.@DecoratedContainer {
  Anchor: (Width: 400, Height: 170);
  #Title { $C.@Title { @Text = "Title"; } }
  #Content { ... }
}
```

---

### @PageOverlay

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Full-screen overlay/backdrop for modal pages |
| **Accepts** | Child elements |
| **Source** | `UIGalleryPage.ui` line 3 |

```
// .ui usage — wrap page content in a modal overlay
$C.@PageOverlay {}
```

---

### @Subtitle

| Property | Value |
|----------|-------|
| **Type** | Template (Label) |
| **Produces** | Styled section subtitle label |
| **Accepts** | `@Text` — subtitle text |
| **Source** | `ButtonsContent.ui` line 13 |

```
// .ui usage
$C.@Subtitle { @Text = "Section Name"; }
```

---

### @TertiaryTextButton

| Property | Value |
|----------|-------|
| **Type** | Template (TextButton) |
| **Produces** | Tertiary styled text button |
| **Accepts** | `@Anchor`, `@Text`, standard TextButton properties |
| **Source** | `ButtonsContent.ui` line 91 |

```
// .ui usage
$C.@TertiaryTextButton {
  @Anchor = (Width: 180);
  @Text = "Tertiary";
}
```

---

### @CancelTextButton

| Property | Value |
|----------|-------|
| **Type** | Template (TextButton) |
| **Produces** | Cancel/destructive styled text button |
| **Accepts** | `@Anchor`, `@Text`, standard TextButton properties |
| **Source** | `ButtonsContent.ui` line 126 |

```
// .ui usage
$C.@CancelTextButton {
  @Anchor = (Width: 180);
  @Text = "Cancel";
}
```

---

### @SmallSecondaryTextButton

| Property | Value |
|----------|-------|
| **Type** | Template (TextButton) |
| **Produces** | Small secondary button variant |
| **Accepts** | `@Text`, standard TextButton properties |
| **Source** | `ButtonsContent.ui` line 161 |

```
// .ui usage
$C.@SmallSecondaryTextButton { @Text = "Small Secondary"; }
```

---

### @SmallTertiaryTextButton

| Property | Value |
|----------|-------|
| **Type** | Template (TextButton) |
| **Produces** | Small tertiary button variant |
| **Accepts** | `@Text`, standard TextButton properties |
| **Source** | `ButtonsContent.ui` line 166, `CodeViewer.ui` line 12 |

```
// .ui usage
$C.@SmallTertiaryTextButton { @Text = "Small Tertiary"; }
```

---

### @Button

| Property | Value |
|----------|-------|
| **Type** | Template (Button) |
| **Produces** | Primary icon button (no text) |
| **Accepts** | `@Anchor`, `Background:`, standard Button properties |
| **Source** | `ButtonsContent.ui` line 198 |

```
// .ui usage
$C.@Button {
  @Anchor = (Right: 10);
  Background: "icon.png";
}
```

---

### @SecondaryButton

| Property | Value |
|----------|-------|
| **Type** | Template (Button) |
| **Produces** | Secondary icon button |
| **Accepts** | `Background:`, standard Button properties |
| **Source** | `ButtonsContent.ui` line 207 |

```
// .ui usage
$C.@SecondaryButton { Background: "icon.png"; }
```

---

### @TertiaryButton

| Property | Value |
|----------|-------|
| **Type** | Template (Button) |
| **Produces** | Tertiary icon button |
| **Accepts** | `Background:`, standard Button properties |
| **Source** | `ButtonsContent.ui` line 216 |

```
// .ui usage
$C.@TertiaryButton { Background: "icon.png"; }
```

---

### @CancelButton

| Property | Value |
|----------|-------|
| **Type** | Template (Button) |
| **Produces** | Cancel/destructive icon button |
| **Accepts** | `Background:`, standard Button properties |
| **Source** | `ButtonsContent.ui` line 225 |

```
// .ui usage
$C.@CancelButton { Background: "icon.png"; }
```

---

### @Panel

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Simple panel container with dark background |
| **Accepts** | `Anchor:`, `Padding:`, child elements |
| **Source** | `ContainersContent.ui` line 17 |

```
// .ui usage
$C.@Panel {
  Anchor: (Width: 350, Height: 80);
  Padding: (Full: 15);
}
```

---

### @PanelTitle

| Property | Value |
|----------|-------|
| **Type** | Template (Group/Label) |
| **Produces** | Title bar for panels |
| **Accepts** | `@Text` — panel title text |
| **Source** | `ContainersContent.ui` line 52 |

```
// .ui usage
$C.@PanelTitle { @Text = "Panel Title"; }
```

---

### @SimpleContainer

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Minimal container without title bar |
| **Accepts** | `Anchor:`, child elements |
| **Source** | `ContainersContent.ui` line 284, `ScrollbarsContent.ui` line 17 |

```
// .ui usage
$C.@SimpleContainer {
  Anchor: (Width: 400, Height: 200);
}
```

---

### @ContentSeparator

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Content area separator line |
| **Accepts** | (none documented) |
| **Source** | `ContainersContent.ui` line 323 |

```
// .ui usage — insert between content sections
$C.@ContentSeparator {}
```

---

### @CheckBox

| Property | Value |
|----------|-------|
| **Type** | Template (CheckBox) |
| **Produces** | Styled checkbox (standalone, no label) |
| **Accepts** | `Value:`, standard CheckBox properties |
| **Source** | `SelectionContent.ui` line 21 |

```
// .ui usage
$C.@CheckBox { Value: true; }
```

---

### @MultilineTextField

| Property | Value |
|----------|-------|
| **Type** | Template (TextField) |
| **Produces** | Multi-line text input area |
| **Accepts** | `@Anchor`, `PlaceholderText:`, standard TextField properties |
| **Source** | `InputContent.ui` line 140 |

```
// .ui usage
$C.@MultilineTextField {
  @Anchor = (Width: 400, Height: 120);
  PlaceholderText: "Enter text...";
}
```

---

### @Slider

| Property | Value |
|----------|-------|
| **Type** | Template (Slider) |
| **Produces** | Range slider control |
| **Accepts** | `@Anchor`, `Value:`, `MinValue:`, `MaxValue:`, standard Slider properties |
| **Source** | `SlidersContent.ui` line 21 |

```
// .ui usage
$C.@Slider {
  @Anchor = (Width: 250);
  Value: 50;
  MinValue: 0;
  MaxValue: 100;
}
```

---

### @FloatSlider

| Property | Value |
|----------|-------|
| **Type** | Template (FloatSlider) |
| **Produces** | Float-precision slider control |
| **Accepts** | `@Anchor`, `Value:`, `MinValue:`, `MaxValue:`, `StepSize:` |
| **Source** | `SlidersContent.ui` line 76 |

```
// .ui usage
$C.@FloatSlider {
  @Anchor = (Width: 250);
  Value: 0.5;
  MinValue: 0.0;
  MaxValue: 1.0;
  StepSize: 0.01;
}
```

---

### @SliderNumberField

| Property | Value |
|----------|-------|
| **Type** | Template (Group with Slider + NumberField) |
| **Produces** | Slider with integrated numeric input display |
| **Accepts** | `@Anchor`, `Value:`, `MinValue:`, `MaxValue:`, `NumberFieldStyle:` |
| **Source** | `SlidersContent.ui` line 117 |

```
// .ui usage
$C.@SliderNumberField {
  @Anchor = (Width: 300);
  Value: 50;
  MinValue: 0;
  MaxValue: 100;
  NumberFieldStyle: $C.@DefaultInputFieldStyle;
}
```

---

### @DefaultSpinner

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Loading/activity spinner animation |
| **Accepts** | (none documented) |
| **Source** | `ProgressContent.ui` line 22 |

```
// .ui usage
$C.@DefaultSpinner {}
```

---

### @ProgressBar

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Horizontal progress bar |
| **Accepts** | `@Anchor`, `@Progress` — float 0.0–1.0 |
| **Source** | `ProgressContent.ui` line 60 |

```
// .ui usage
$C.@ProgressBar {
  @Anchor = (Width: 300);
  @Progress = 0.75;
}
```

---

### @CircularProgressBar

| Property | Value |
|----------|-------|
| **Type** | Template (Group) |
| **Produces** | Circular progress indicator |
| **Accepts** | `@Anchor`, `@Progress` — float 0.0–1.0 |
| **Source** | `ProgressContent.ui` line 125 |

```
// .ui usage
$C.@CircularProgressBar {
  @Anchor = (Width: 60, Height: 60);
  @Progress = 0.5;
}
```

---

## 2. STYLES (from Common.ui)

Styles are named expressions that define appearance properties. Use the spread operator `...` to inherit and override.

### @DefaultLabelStyle

| Property | Value |
|----------|-------|
| **Type** | LabelStyle |
| **Produces** | Base label appearance (default font, size, color) |
| **Usage** | As `Style:` value or spread into custom styles |
| **Source** | `EntitySpawnPage.ui` lines 74, 152, 172, 187, 198; `StencilBookPage.ui` lines 10–34 |

```
// .ui — direct use
Label {
  Text: "Hello";
  Style: $C.@DefaultLabelStyle;
}

// .ui — spread with overrides
Label {
  Text: "Centered Label";
  Style: (...$C.@DefaultLabelStyle, HorizontalAlignment: Center);
}

// .ui — inline tuple override
Label {
  Style: (...$C.@DefaultLabelStyle, VerticalAlignment: Center, Wrap: true);
}

// .ui — define a derived style
@HeaderStyle = LabelStyle(
    ...$C.@DefaultLabelStyle,
    FontSize: 22, RenderBold: true,
    HorizontalAlignment: Start, VerticalAlignment: Center
);
```

---

### @DefaultTextButtonStyle

| Property | Value |
|----------|-------|
| **Type** | TextButtonStyle |
| **Produces** | Primary text button appearance (Default/Hovered/Pressed states) |
| **Source** | `EntitySpawnPage.java` line 73 — `Value.ref("Common.ui", "DefaultTextButtonStyle")` |

```
// .ui — direct use on TextButton
TextButton #MyBtn {
  Text: "Click Me";
  Style: $C.@DefaultTextButtonStyle;
}
```

```java
// Java — reference for dynamic style switching
private static final Value<String> TAB_STYLE_ACTIVE =
    Value.ref("Common.ui", "DefaultTextButtonStyle");

commandBuilder.set("#TabNPC.Style", TAB_STYLE_ACTIVE);
```

---

### @SecondaryTextButtonStyle

| Property | Value |
|----------|-------|
| **Type** | TextButtonStyle |
| **Produces** | Secondary/muted text button appearance |
| **Source** | `EntitySpawnPage.java` line 74 — `Value.ref("Common.ui", "SecondaryTextButtonStyle")` |

```
// .ui — direct use
TextButton #InactiveTab {
  Text: "Items";
  Style: $C.@SecondaryTextButtonStyle;
}
```

```java
// Java
private static final Value<String> TAB_STYLE_INACTIVE =
    Value.ref("Common.ui", "SecondaryTextButtonStyle");
```

---

### @DefaultScrollbarStyle

| Property | Value |
|----------|-------|
| **Type** | ScrollbarStyle |
| **Produces** | Standard scrollbar for `TopScrolling` / `LeftScrolling` layout groups |
| **Source** | `EntitySpawnPage.ui` lines 56, 120; `StencilBookPage.ui` lines 173, 215, 291 |

```
// .ui — set on a scrollable group
Group #MyList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@DefaultScrollbarStyle;
  FlexWeight: 1;
}
```

---

### @DefaultSliderStyle

| Property | Value |
|----------|-------|
| **Type** | SliderStyle |
| **Produces** | Standard slider track + handle appearance |
| **Source** | `EntitySpawnPage.ui` lines 157, 177 |

```
// .ui — use on Slider or FloatSlider
Slider #RotationOffset {
  Anchor: (Left: 25, Width: 180, Height: 5, Right: 0);
  Style: $C.@DefaultSliderStyle;
  Min: -180;
  Max: 180;
  Step: 1;
}

FloatSlider #ScaleSlider {
  Anchor: (Left: 10, Width: 150, Height: 5);
  Style: $C.@DefaultSliderStyle;
  Min: 0.1;
  Max: 5.0;
  Step: 0.1;
  Value: 1.0;
}
```

---

### @DefaultButtonStyle

| Property | Value |
|----------|-------|
| **Type** | TextButtonStyle (likely alias or variant) |
| **Produces** | Default button styling |
| **Source** | hytalemodding.dev markup page — `Style: $Common.@DefaultButtonStyle;` |

```
// .ui — from official markup docs
TextButton {
  Style: $Common.@DefaultButtonStyle;
}
```

**Note:** May be the same as `@DefaultTextButtonStyle` — the official docs use `@DefaultButtonStyle` while the decompiled code uses `@DefaultTextButtonStyle`. Both likely resolve to the same style. Use `/ui-gallery` in-game to verify.

---

### @DefaultInputFieldStyle

| Property | Value |
|----------|-------|
| **Type** | TextFieldStyle (or similar) |
| **Produces** | Styled text input field appearance |
| **Source** | hytalemodding.dev community guide — `Style: $Common.@DefaultInputFieldStyle;` |

```
// .ui — from community guide
TextField #MyInput {
  Style: $Common.@DefaultInputFieldStyle;
  Background: $Common.@InputBoxBackground;
  Anchor: (Top: 10, Width: 200, Height: 50);
}
```

---

### @InputBoxBackground

| Property | Value |
|----------|-------|
| **Type** | PatchStyle (9-patch texture) |
| **Produces** | Styled background for input fields |
| **Source** | hytalemodding.dev community guide — `Background: $Common.@InputBoxBackground;` |

```
// .ui — from community guide
TextField #MyInput {
  Style: $Common.@DefaultInputFieldStyle;
  Background: $Common.@InputBoxBackground;
  Anchor: (Top: 10, Width: 200, Height: 50);
}
```

---

### @TopTabsStyle

| Property | Value |
|----------|-------|
| **Type** | TabNavigationStyle |
| **Produces** | Tab navigation styling positioned above a container (Height: 66) |
| **Source** | `ContainersContent.ui` line 174 |

```
// .ui usage
TabNavigation {
  Style: $C.@TopTabsStyle;
  SelectedTab: "Main";
  Anchor: (Height: 66, Left: 2, Right: 0);
}
```

---

### @HeaderTabsStyle

| Property | Value |
|----------|-------|
| **Type** | TabNavigationStyle |
| **Produces** | Tab navigation styling inside a title bar (Height: 34) |
| **Source** | `ContainersContent.ui` line 207 |

```
// .ui usage
TabNavigation {
  Style: $C.@HeaderTabsStyle;
  SelectedTab: "List";
  Anchor: (Height: 34, Left: 4);
}
```

---

### @DefaultColorPickerDropdownBoxStyle

| Property | Value |
|----------|-------|
| **Type** | ColorPickerDropdownBoxStyle |
| **Produces** | Styled color picker dropdown |
| **Source** | `SelectionContent.ui` line 155 |

```
// .ui usage
ColorPickerDropdownBox {
  Style: $C.@DefaultColorPickerDropdownBoxStyle;
  Color: #ff5555;
}
```

---

### @DefaultTextTooltipStyle

| Property | Value |
|----------|-------|
| **Type** | TextTooltipStyle |
| **Produces** | Default text tooltip appearance |
| **Source** | `TooltipsContent.ui` line 31 |

```
// .ui usage — set on any element
Group #MyElement {
  TextTooltipStyle: $C.@DefaultTextTooltipStyle;
}
```

---

### @DefaultExtraSpacingScrollbarStyle

| Property | Value |
|----------|-------|
| **Type** | ScrollbarStyle |
| **Produces** | Scrollbar with extra spacing/padding |
| **Source** | `ScrollbarsContent.ui` line 51 |

```
// .ui usage
Group #MyList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@DefaultExtraSpacingScrollbarStyle;
}
```

---

### @TranslucentScrollbarStyle

| Property | Value |
|----------|-------|
| **Type** | ScrollbarStyle |
| **Produces** | Translucent/transparent scrollbar variant |
| **Source** | `ScrollbarsContent.ui` line 79 |

```
// .ui usage
Group #MyList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $C.@TranslucentScrollbarStyle;
}
```

---

### @ClearButtonStyle

| Property | Value |
|----------|-------|
| **Type** | ButtonStyle |
| **Produces** | Style for clear/X button in search/input fields |
| **Source** | `InputContent.ui` line 103 |

```
// .ui usage — on a TextField with clear button
ClearButtonStyle: $C.@ClearButtonStyle;
```

---

## 3. STYLES (from Common/TextButton.ui)

These are in a **separate** .ui file at `Common/UI/Custom/Common/TextButton.ui`. Import differently:

```
// In .ui — import the TextButton file separately
$TB = "../Common/TextButton.ui";    // From Pages/ subdirectory
```

### LabelStyle (Common/TextButton.ui)

| Property | Value |
|----------|-------|
| **Type** | LabelStyle |
| **Produces** | Default label style for text button entries |
| **Source** | `EntitySpawnPage.java` line 71, `ChangeModelPage.java` line 48, `PlaySoundPage.java` line 34, `ParticleSpawnPage.java` line 48 |

```java
// Java — used for list items (default state)
private static final Value<String> BUTTON_LABEL_STYLE =
    Value.ref("Common/TextButton.ui", "LabelStyle");

commandBuilder.set("#NPCList[0] #Button.Style", BUTTON_LABEL_STYLE);
```

---

### SelectedLabelStyle (Common/TextButton.ui)

| Property | Value |
|----------|-------|
| **Type** | LabelStyle |
| **Produces** | Highlighted/selected label style for text button entries |
| **Source** | `EntitySpawnPage.java` line 72, `ChangeModelPage.java` line 49, `PlaySoundPage.java` line 35, `ParticleSpawnPage.java` line 49 |

```java
// Java — used for list items (selected state)
private static final Value<String> BUTTON_LABEL_STYLE_SELECTED =
    Value.ref("Common/TextButton.ui", "SelectedLabelStyle");

commandBuilder.set("#NPCList[0] #Button.Style", BUTTON_LABEL_STYLE_SELECTED);
```

---

## 4. STYLES (from Pages/BasicTextButton.ui)

These are in a **separate** .ui file at `Common/UI/Custom/Pages/BasicTextButton.ui`.

### LabelStyle (Pages/BasicTextButton.ui)

| Property | Value |
|----------|-------|
| **Type** | LabelStyle |
| **Produces** | Default label style for basic text buttons |
| **Used by** | CommandListPage, InstanceListPage, ServerFileBrowser, PrefabPage |
| **Source** | `CommandListPage.java` line 45, `InstanceListPage.java` line 37 |

```java
private static final Value<String> BUTTON_LABEL_STYLE =
    Value.ref("Pages/BasicTextButton.ui", "LabelStyle");
```

### SelectedLabelStyle (Pages/BasicTextButton.ui)

| Property | Value |
|----------|-------|
| **Type** | LabelStyle |
| **Produces** | Selected/highlighted label style for basic text buttons |
| **Used by** | CommandListPage, InstanceListPage, ServerFileBrowser, PrefabPage, PrefabEditorSaveSettingsPage, PrefabEditorLoadSettingsPage, PrefabTeleportPage |
| **Source** | `CommandListPage.java` line 46, `ServerFileBrowser.java` line 39 |

```java
private static final Value<String> BUTTON_LABEL_STYLE_SELECTED =
    Value.ref("Pages/BasicTextButton.ui", "SelectedLabelStyle");
```

---

## 5. STYLES (from other .ui template files)

### DefaultRespawnButtonStyle / SelectedRespawnButtonStyle (Pages/OverrideRespawnPointButton.ui)

```java
private static final Value<String> DEFAULT_RESPAWN_BUTTON_STYLE =
    Value.ref("Pages/OverrideRespawnPointButton.ui", "DefaultRespawnButtonStyle");
private static final Value<String> SELECTED_RESPAWN_BUTTON_STYLE =
    Value.ref("Pages/OverrideRespawnPointButton.ui", "SelectedRespawnButtonStyle");
```

### LabelStyle / SelectedLabelStyle (Pages/PluginListButton.ui)

```java
private static final Value<String> BUTTON_LABEL_STYLE =
    Value.ref("Pages/PluginListButton.ui", "LabelStyle");
private static final Value<String> BUTTON_LABEL_STYLE_SELECTED =
    Value.ref("Pages/PluginListButton.ui", "SelectedLabelStyle");
```

---

## 6. COLORS (from Common.ui)

Named color expressions used as `$C.@ColorName` in inline style overrides or property values.

| Name | Purpose | Source |
|------|---------|--------|
| `@ColorDefault` | Default/primary text color | `TextContent.ui` line 69 |
| `@ColorDefaultLabel` | Default label text color | `TextContent.ui` line 75 |
| `@ColorBlueAccent` | Blue accent color | `TextContent.ui` line 81 |
| `@ColorBlueAccentHovered` | Blue accent hover state | `TextContent.ui` line 87 |
| `@ColorBlueAccentPressed` | Blue accent pressed state | `TextContent.ui` line 93 |
| `@ColorGoldHighlight` | Gold highlight color | `TextContent.ui` line 99 |
| `@ColorGrayCaption` | Gray caption text color | `TextContent.ui` line 105 |
| `@ColorButtonText` | Button text color | `TextContent.ui` line 111 |
| `@ColorCaptionLight` | Light caption color | `TextContent.ui` line 30 |
| `@ColorSimpleButtonBackground` | Simple button background color | `CategoryButton.ui` line 9 |
| `@ColorPlaceholder` | Placeholder text color | `InputContent.ui` line 98 |
| `@ColorBackgroundCode` | Code block background color | `CodeViewer.ui` line 21 |
| `@ColorCodeText` | Code text color | `CodeViewer.ui` line 25 |

### Usage

```
// .ui — use in inline style override
Label {
  Style: (...$C.@DefaultLabelStyle, Color: $C.@ColorBlueAccent);
  Text: "Highlighted text";
}

// .ui — use as a property value
Group {
  BackgroundColor: $C.@ColorBackgroundCode;
}
```

---

## 7. TEXTURE EXPRESSIONS (from Common.ui)

Named texture/mask expressions referenced as `$C.@TextureName`.

### @TextHighlightGradientMask

| Property | Value |
|----------|-------|
| **Type** | Texture path expression |
| **Produces** | Gradient mask for highlighted/shimmering text effects |
| **Source** | `TextContent.ui` line 138, `CategoryButton.ui` line 16 |

```
// .ui usage — apply as a mask texture
Label {
  MaskTexturePath: $C.@TextHighlightGradientMask;
}
```

---

## 8. TEXTURE ASSETS (Common/ directory PNGs)

These are 9-patch or standard textures at `Common/UI/Custom/Common/`. Reference via relative path from your `.ui` file.

### ContainerPanelPatch.png

| Property | Value |
|----------|-------|
| **Type** | 9-patch texture |
| **Produces** | Rounded panel background (scales with Border slicing) |
| **Source** | `EntitySpawnPage.ui` line 128 |

```
// .ui — as Group background with 9-patch border
Group {
  Background: (TexturePath: "../Common/ContainerPanelPatch.png", Border: 4);
}

// .ui — PatchStyle variable
@PanelBg = PatchStyle(TexturePath: "../Common/ContainerPanelPatch.png", Border: 4);
Group { Background: @PanelBg; }
```

---

### BlockSelectorSlotBackground.png

| Property | Value |
|----------|-------|
| **Type** | Texture |
| **Produces** | Item slot background (inventory/selector style) |
| **Source** | `EntitySpawnPage.ui` line 93 |

```
// .ui — as ItemGrid slot background
ItemGrid #ItemSlot {
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

---

### BlockSelectorSlotDropIcon.png

| Property | Value |
|----------|-------|
| **Type** | Texture |
| **Produces** | Drop indicator overlay icon (drag-and-drop target indicator) |
| **Source** | `EntitySpawnPage.ui` line 99 |

```
// .ui — as drag indicator overlay
Group #DropIndicator {
  Anchor: (Width: 24, Height: 24, Horizontal: 0, Vertical: 0);
  Background: "../Common/BlockSelectorSlotDropIcon.png";
  HitTestVisible: false;
}
```

---

## 9. REUSABLE .ui TEMPLATE FILES (appendable from Java)

These ship with the base game and can be appended into any custom page via `commandBuilder.append(selector, path)`.

| Path | Purpose | Key Selectors | Used By |
|------|---------|---------------|---------|
| `Common/TextButton.ui` | Styled text button with label | `#Label`, `#Button` | EntitySpawnPage, ChangeModelPage, PlaySoundPage, ParticleSpawnPage |
| `Pages/BasicTextButton.ui` | Basic text button | `#Label` | CommandListPage, InstanceListPage, PrefabPage, ServerFileBrowser |
| `Pages/PluginListButton.ui` | Plugin list item with checkbox | `#Label`, `#CheckBox` | PluginListPage |
| `Pages/DroppedItemSlot.ui` | Item slot for dropped items | `#ItemIcon` | RespawnPage |
| `Pages/WarpEntryButton.ui` | Warp point entry button | — | WarpListPage |
| `Pages/SubcommandCard.ui` | Command subcommand card | — | CommandListPage |
| `Pages/VariantCard.ui` | Command variant display card | — | CommandListPage |
| `Pages/ParameterItem.ui` | Command parameter item | — | CommandListPage |
| `Pages/OverrideRespawnPointButton.ui` | Respawn point button | — | SelectOverrideRespawnPointPage |
| `Pages/ItemRepairElement.ui` | Item repair list item | `#ElementList` | ItemRepairPage |

### Usage pattern

```java
// Append a template as a child of an element
commandBuilder.append("#NPCList", "Common/TextButton.ui");

// Set properties on the appended instance (index-based addressing)
commandBuilder.set("#NPCList[0] #Label.Text", "Villager");

// Toggle selection via style swap
commandBuilder.set("#NPCList[0] #Button.Style",
    Value.ref("Common/TextButton.ui", "SelectedLabelStyle"));
```

---

## 10. TEMPLATE VARIABLE OVERRIDES

When instantiating Common.ui templates, these `@` variables can be overridden:

| Template | Variable | Type | Purpose | Default |
|----------|----------|------|---------|---------|
| `@Title` | `@Text` | String / Translation | Title text content | — |
| `@NumberField` | `@Anchor` | Anchor | Size/position override | — |
| `@CheckBoxWithLabel` | `@Text` | String / Translation | Checkbox label text | — |
| `@CheckBoxWithLabel` | `@Checked` | Boolean | Initial checked state | `false` |
| `@DecoratedContainer` | `#Title` | Named slot | Title area slot | — |
| `@DecoratedContainer` | `#Content` | Named slot | Content area slot | — |
| `@Subtitle` | `@Text` | String | Subtitle text | — |
| `@TertiaryTextButton` | `@Anchor` | Anchor | Size/position override | — |
| `@TertiaryTextButton` | `@Text` | String | Button text | — |
| `@CancelTextButton` | `@Anchor` | Anchor | Size/position override | — |
| `@CancelTextButton` | `@Text` | String | Button text | — |
| `@SmallSecondaryTextButton` | `@Text` | String | Button text | — |
| `@SmallTertiaryTextButton` | `@Text` | String | Button text | — |
| `@PanelTitle` | `@Text` | String | Panel title text | — |
| `@CheckBoxWithLabel` (gallery) | `@Text` | String | Checkbox label text | — |
| `@CheckBoxWithLabel` (gallery) | `@Checked` | Boolean | Initial checked state | `false` |
| `@MultilineTextField` | `@Anchor` | Anchor | Size/position override | — |
| `@Slider` | `@Anchor` | Anchor | Size/position override | — |
| `@FloatSlider` | `@Anchor` | Anchor | Size/position override | — |
| `@SliderNumberField` | `@Anchor` | Anchor | Size/position override | — |
| `@ProgressBar` | `@Anchor` | Anchor | Size/position override | — |
| `@ProgressBar` | `@Progress` | Float (0.0–1.0) | Progress value | — |
| `@CircularProgressBar` | `@Anchor` | Anchor | Size/position override | — |
| `@CircularProgressBar` | `@Progress` | Float (0.0–1.0) | Progress value | — |

### Override syntax

```
$C.@Title {
  @Text = "My Page Title";           // Override the @Text variable
}

$C.@NumberField #Count {
  @Anchor = (Left: 25, Width: 80);   // Override the @Anchor variable
  Format: (...);                       // Set standard properties
  Value: 1;
}

$C.@CheckBoxWithLabel #Option {
  @Text = "Enable feature";           // Override @Text
  @Checked = true;                     // Override @Checked
  Anchor: (Height: 28);               // Set standard properties
}
```

---

## 11. SPREAD SYNTAX FOR STYLE INHERITANCE

Use `...` to spread a Common.ui style and override specific properties:

```
// Spread into inline style tuple
Label {
  Style: (...$C.@DefaultLabelStyle, FontSize: 16);
}

// Spread into named expression
@HeaderStyle = LabelStyle(
    ...$C.@DefaultLabelStyle,
    FontSize: 22,
    RenderBold: true,
    HorizontalAlignment: Start,
    VerticalAlignment: Center
);

// Layer multiple spreads
@CustomStyle = LabelStyle(
    ...$C.@DefaultLabelStyle,
    ...@MyExtraStyle,
    FontSize: 18
);
```

---

## 12. DISCOVERY — How to Find More

The official Common.ui source code is not published. To discover additional styles:

1. **`/ui-gallery` command** — Run in-game to see all Common.ui styles rendered live with names
2. **Decompiled Java** — Search for `Value.ref("Common.ui", ...)` patterns
3. **Decompiled .ui files** — Search for `$C.@` or `$Common.@` patterns
4. **Community exploration** — The hytalemodding.dev wiki and Discord are active sources

### Quick Reference — All Known Common.ui Named Expressions

| Name | Type | Category |
|------|------|----------|
| `@Container` | Template | Layout |
| `@Title` | Template | Layout |
| `@HeaderSearch` | Template | Input |
| `@TextButton` | Template | Button |
| `@SecondaryTextButton` | Template | Button |
| `@BackButton` | Template | Navigation |
| `@TextField` | Template | Input |
| `@NumberField` | Template | Input |
| `@CheckBoxWithLabel` | Template | Input |
| `@PanelSeparatorFancy` | Template | Decoration |
| `@DecoratedContainer` | Template | Layout |
| `@PageOverlay` | Template | Layout |
| `@Subtitle` | Template | Label |
| `@TertiaryTextButton` | Template | Button |
| `@CancelTextButton` | Template | Button |
| `@SmallSecondaryTextButton` | Template | Button |
| `@SmallTertiaryTextButton` | Template | Button |
| `@Button` | Template | Button |
| `@SecondaryButton` | Template | Button |
| `@TertiaryButton` | Template | Button |
| `@CancelButton` | Template | Button |
| `@Panel` | Template | Layout |
| `@PanelTitle` | Template | Layout |
| `@SimpleContainer` | Template | Layout |
| `@ContentSeparator` | Template | Decoration |
| `@CheckBox` | Template | Input |
| `@MultilineTextField` | Template | Input |
| `@Slider` | Template | Input |
| `@FloatSlider` | Template | Input |
| `@SliderNumberField` | Template | Input |
| `@DefaultSpinner` | Template | Progress |
| `@ProgressBar` | Template | Progress |
| `@CircularProgressBar` | Template | Progress |
| `@DefaultLabelStyle` | LabelStyle | Style |
| `@DefaultTextButtonStyle` | TextButtonStyle | Style |
| `@SecondaryTextButtonStyle` | TextButtonStyle | Style |
| `@DefaultScrollbarStyle` | ScrollbarStyle | Style |
| `@DefaultSliderStyle` | SliderStyle | Style |
| `@DefaultButtonStyle` | TextButtonStyle | Style |
| `@DefaultInputFieldStyle` | TextFieldStyle | Style |
| `@InputBoxBackground` | PatchStyle | Style |
| `@TopTabsStyle` | TabNavigationStyle | Style |
| `@HeaderTabsStyle` | TabNavigationStyle | Style |
| `@DefaultColorPickerDropdownBoxStyle` | ColorPickerDropdownBoxStyle | Style |
| `@DefaultTextTooltipStyle` | TextTooltipStyle | Style |
| `@DefaultExtraSpacingScrollbarStyle` | ScrollbarStyle | Style |
| `@TranslucentScrollbarStyle` | ScrollbarStyle | Style |
| `@ClearButtonStyle` | ButtonStyle | Style |
| `@ColorDefault` | Color | Color |
| `@ColorDefaultLabel` | Color | Color |
| `@ColorBlueAccent` | Color | Color |
| `@ColorBlueAccentHovered` | Color | Color |
| `@ColorBlueAccentPressed` | Color | Color |
| `@ColorGoldHighlight` | Color | Color |
| `@ColorGrayCaption` | Color | Color |
| `@ColorButtonText` | Color | Color |
| `@ColorCaptionLight` | Color | Color |
| `@ColorSimpleButtonBackground` | Color | Color |
| `@ColorPlaceholder` | Color | Color |
| `@ColorBackgroundCode` | Color | Color |
| `@ColorCodeText` | Color | Color |
| `@TextHighlightGradientMask` | Texture | Texture |

---

## See Also

- [UI File System](../plugins/ui-file-system.md) — how `.ui` files work, path resolution, deployment
- [Custom UI Options](../plugins/custom-ui-options.md) — comparison of all UI approaches
- [Custom UI Responsive Sizing](../plugins/custom-ui-responsive-sizing.md) — layout strategies
- [Item Display in Custom UI](../plugins/custom-ui-item-display.md) — ItemIcon, ItemSlot, ItemGrid
- [Official Common Styling](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling) — Hypixel Studios docs
- [Official Markup Reference](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup) — `.ui` syntax
- [Community UI Guide](https://hytalemodding.dev/en/docs/guides/plugin/ui) — underscore95's guide
