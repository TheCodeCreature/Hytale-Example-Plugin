---
topic: "Collapsible / Expandable Section Pattern"
category: "Plugin API / Custom UI"
updated: 2026-05-04
sources:
  - "docs/hytale/ui/ui-element-reference.md — Visible property, LayoutMode reference"
  - "docs/hytale/ui/ui-data-binding.md — UICommandBuilder.set(), Activating events"
  - "docs/hytale/ui/custom-ui-overview.md — .ui DSL structure"
  - "docs/hytale/community/vex-ui-library-research.md — Vex core node type list"
  - "src/main/java/.../BlueprintSelectionPage.java — Visible toggling in practice"
---

# Collapsible / Expandable Section Pattern

## Summary

Hytale's `.ui` DSL has **no built-in collapsible, expandable, accordion, or tree-view element**. However, the pattern can be reliably simulated using `Visible` toggling on a `Group` container, driven by an `Activating` event on a `TextButton` header. When `Visible` is set to `false`, the element **takes no layout space**, so parent containers with stack-based `LayoutMode` (e.g., `Top`, `TopScrolling`) automatically reflow remaining children — producing a natural collapse/expand effect.

---

## Q1: Built-in Collapsible Element?

**No.** The complete list of confirmed engine node types (from the Vex UI Grimoire's `core-rules.md` and our own element reference) is:

```
Group, Label, Button, ProgressBar, TextButton, TextField,
ActionButton, CheckBox, DropdownBox, ItemGrid, TabNavigation,
TabButton, SliderNumberField, NumberField, ToggleButton,
DynamicPane, DynamicPaneContainer, ItemPreviewComponent,
SceneBlur, Panel, BackgroundImage, BackButton, MenuItem,
ScrollView, ReorderableList, ReorderableListGrip,
CharacterPreviewComponent, BlockSelector, CompactTextField,
MultilineTextField, FloatSlider, Slider, ColorPicker,
ColorPickerDropdownBox, ColorOptionGrid, CodeEditor,
TimerLabel, HotkeyLabel, CircularProgressBar, ItemIcon,
ItemSlot, ItemSlotButton, Sprite, AssetImage
```

There is no `Expander`, `Accordion`, `CollapsibleGroup`, `TreeView`, `Foldout`, or similar element.

---

## Q2: Simulating Collapsible Sections via Visible Toggling

**Yes — this is the recommended approach.** The key engine behavior that makes this work:

> `Visible: false` → **hidden elements take no layout space**
>
> — [ui-element-reference.md](./ui-element-reference.md), Group property table

### Pattern: Collapsible Section

#### .ui Template

```
// Section header — acts as the toggle button
TextButton #CategoriesHeader {
    Text: "▼ Categories";
    Anchor: (Height: 24, Left: 0, Right: 0);
    Padding: (Left: 6, Right: 6);
    Style: @SectionHeaderStyle;
}

// Collapsible body — toggled via server
Group #CategoriesBody {
    LayoutMode: LeftCenterWrap;
    Visible: true;   // default expanded

    // Children appended by server (GroupFilterButton.ui instances)
}
```

#### Java Server-Side

```java
// Track collapsed state per section
private boolean categoriesCollapsed = false;

// In event handler:
if ("toggleCategories".equals(action)) {
    categoriesCollapsed = !categoriesCollapsed;
    cmd.set("#CategoriesBody.Visible", !categoriesCollapsed);
    cmd.set("#CategoriesHeader.Text", 
        categoriesCollapsed ? "▶ Categories" : "▼ Categories");
}
```

#### Event Binding

```java
evt.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#CategoriesHeader",
    EventData.of("Action", "toggleCategories"),
    false
);
```

### Why This Works

1. **`Visible: false` removes layout space** — the Group's height collapses to zero in `Top`/`TopScrolling` layouts
2. **Parent reflowing is automatic** — sibling elements shift up to fill the gap
3. **Server round-trip is minimal** — a single `cmd.set()` call toggles visibility
4. **Children are preserved** — hiding a Group doesn't destroy its children; they remain in the DOM and reappear when `Visible` is toggled back

---

## Q3: Existing Examples

### In Our Codebase

`BlueprintSelectionPage.java` already uses `Visible` toggling extensively:

```java
// Toggling set filter visibility
cmd.set("#SetFilters[0].Visible", !currentSets.isEmpty());

// Toggling individual recipe cells
cmd.set("#RecipeGrid[" + i + "].Visible", false);

// Toggling cost grid items
cmd.set("#CostGrid[" + i + "].Visible", false);

// Toggling material group overlay
cmd.set("#MaterialGroups[0] #ActiveOverlay.Visible", active);
```

This confirms the pattern works at scale — we toggle visibility on dozens of elements per update cycle without issues.

### In Vex UI Library

The Vex project's macro library includes `Modal.ui` and `Toast.ui` templates that use visibility toggling for show/hide behavior (per dev log descriptions). No specific accordion pattern is documented in their public Grimoire pages, but their `Tabs.ui` macro handles content switching — a related pattern where only one content panel is visible at a time.

### In Base Game UI

The base game uses `TabNavigation` + `DynamicPane`/`DynamicPaneContainer` for section switching (one section visible at a time, like tabs). This is conceptually similar to an accordion where only one section is expanded at a time — but it uses a dedicated element pair rather than `Visible` toggling.

---

## Q4: Does LayoutMode: Top Reflow When Visible Is Toggled?

**Yes.** This is explicitly documented:

> | `Visible` | Boolean | Visibility (**hidden elements take no layout space**) |

All stack-based `LayoutMode` values (`Top`, `TopScrolling`, `Left`, `LeftScrolling`, `Bottom`, `Right`, etc.) participate in the standard flow layout. When a child's `Visible` becomes `false`:

1. The child is removed from layout calculations (zero width, zero height)
2. Subsequent siblings shift to fill the vacated space
3. The parent may shrink if it uses `FlexWeight` sizing rather than fixed `Anchor` dimensions

**Verified in practice:** Our `BlueprintSelectionPage.java` toggles `Visible` on recipe grid children inside a `LeftCenterWrap` container, and the remaining visible items reflow correctly into a tighter grid.

### Caveat: ScrollView / TopScrolling

When using `TopScrolling`, the scrollable content area adjusts its total scrollable height when children are hidden. The scrollbar thumb updates accordingly. This is desirable for collapsible sections in a scrollable sidebar.

---

## Recommended Implementation for BlueprintBookPage

### Option A: Simple Toggle (Independent Sections)

Each section collapses/expands independently. Best for your sidebar where both "Categories" and "Sets" might need to be visible simultaneously or independently.

```
// Inside the TopScrolling sidebar Group:

// ── Categories Section ──
TextButton #CategoriesToggle {
    Text: "▼ Categories";
    Anchor: (Height: 20, Left: 0, Right: 0);
    Padding: (Left: 6);
    // Style with left-aligned text, subtle background
}

Group #CategoriesBody {
    LayoutMode: LeftCenterWrap;
    Visible: true;
    // Server appends GroupFilterButton.ui instances here
}

Group { Anchor: (Height: 6); }   // Spacer

// ── Sets Section ──
TextButton #SetsToggle {
    Text: "▼ Sets";
    Anchor: (Height: 20, Left: 0, Right: 0);
    Padding: (Left: 6);
}

Group #SetsBody {
    LayoutMode: Top;
    Visible: true;
    // Server appends SetFilterButton.ui instances here
}
```

### Option B: Accordion (Mutual Exclusion)

Only one section open at a time. Saves vertical space but may frustrate users who want to see both.

```java
// Server-side: when one opens, close the other
if ("toggleCategories".equals(action)) {
    categoriesOpen = !categoriesOpen;
    if (categoriesOpen) setsOpen = false;
    cmd.set("#CategoriesBody.Visible", categoriesOpen);
    cmd.set("#SetsBody.Visible", setsOpen);
    // Update header arrows
}
```

### Option C: Hybrid — Use Existing Labels as Toggle Targets

Convert your existing `Label` ("Categories", "Sets") into `TextButton` elements styled to look like labels. This avoids adding new elements:

```
// Before (current):
Label { Text: "Categories"; ... }
Group #MaterialGroups { ... }

// After (collapsible):
TextButton #CategoriesToggle {
    Text: "▼ Categories";
    Anchor: (Height: 16, Left: 6);
    Style: @CollapsibleHeaderStyle;  // Styled to look like a label
}
Group #MaterialGroups {
    LayoutMode: LeftCenterWrap;
    // Visible toggled by server
}
```

Where `@CollapsibleHeaderStyle` mimics the label appearance:

```
@CollapsibleHeaderStyle = TextButtonStyle(
    Default: (Background: #141c26(0.0), LabelStyle: (FontSize: 11, TextColor: #6e7da1,
              RenderBold: true, HorizontalAlignment: Start, VerticalAlignment: Center)),
    Hovered: (Background: #141c26(0.0), LabelStyle: (FontSize: 11, TextColor: #96a9be,
              RenderBold: true, HorizontalAlignment: Start, VerticalAlignment: Center)),
    Pressed: (Background: #141c26(0.0), LabelStyle: (FontSize: 11, TextColor: #6e7da1,
              RenderBold: true, HorizontalAlignment: Start, VerticalAlignment: Center))
);
```

---

## Gotchas

- **No CSS transitions/animations** — the collapse is instant (no smooth height animation). Hytale's UI has no transition or animation properties for layout changes.
- **Arrow indicators via icon images, not Unicode text** — the `.ui` parser does not support `\u` escape sequences (crashes), and the game fonts may lack glyphs for Unicode geometric shapes (▶/▼). Use a small `.png` arrow icon displayed via `Group { Background: "ArrowDown.png"; }` next to the `TextButton`. The server can swap between `ArrowDown.png` and `ArrowRight.png` via `cmd.set()` on the Group's `Background` property. See [Unicode, Fonts & Icon Indicators](./unicode-fonts-and-icons.md) for full details.
- **Server round-trip latency** — toggling requires a client→server→client round trip. On a local server this is imperceptible; on a remote server there may be a brief delay.
- **Initial state matters** — set `Visible: true` or `Visible: false` in the `.ui` file to define the default expand/collapse state. The server can override on `build()` if needed.

## See Also

- [UI Element Reference](./ui-element-reference.md) — Full element and property reference
- [UI Data Binding](./ui-data-binding.md) — Event binding and `cmd.set()` patterns
- [Custom UI Overview](./custom-ui-overview.md) — Architecture and lifecycle
