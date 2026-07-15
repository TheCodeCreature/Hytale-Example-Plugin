---
topic: "UI DSL Component Reuse & Composition"
category: "Plugin API / Custom UI"
updated: 2026-05-07
sources:
  - "docs/Reference Assets/Assets/Common/UI/Custom/Common.ui (full reference)"
  - "src/main/resources/Common/UI/Custom/Pages/StencilBook/*.ui"
  - "src/main/resources/Common/UI/Custom/Pages/StencilRadial/*.ui"
  - "src/main/resources/Common/UI/Custom/Styles/*.ui"
  - "docs/Reference Assets/Assets/Common/UI/Custom/Pages/UIGallery/*.ui"
  - "src/main/java/.../StencilSelectionPage.java"
  - "src/main/java/.../StencilRadialMenuPage.java"
---

# UI DSL Component Reuse & Composition

## Summary

Hytale's `.ui` DSL supports **two distinct categories of `@`-tokens**: **style tokens** (scalar values, property bags, typed style objects) and **element-tree tokens** (full component definitions with children). Both are defined identically using `@Name = ...` syntax. The DSL also supports **server-side `cmd.append()` stamping** of standalone `.ui` files as the primary mechanism for dynamic component instantiation. There is **no `Content:` property**, **no `Template`/`Prototype` keyword**, and **no client-side component instantiation** — all dynamic composition happens server-side via Java.

---

## 1. Token Categories in Common.ui

### 1a. Scalar Tokens (Constants)

Simple values: colors, numbers, strings. Used for design system consistency.

```
@ColorDefault = #ffffff;
@ButtonBorder = 12;
@PrimaryButtonHeight = 44;
@TitleHeight = 38;
@TextHighlightGradientMask = "Common/TextGradient.png";
```

**Usage:** `Anchor: (Height: $C.@PrimaryButtonHeight);` or `MaskTexturePath: $C.@TextHighlightGradientMask;`

### 1b. Property Bag Tokens (Typed Styles)

Composite objects that represent a single style property. These are the most common token type.

```
@DefaultLabelStyle = (FontSize: 16, TextColor: #96a9be);

@DefaultButtonDefaultBackground = PatchStyle(
    TexturePath: "Common/Buttons/Primary.png",
    VerticalBorder: @ButtonBorder,
    HorizontalBorder: 80
);

@DefaultTextButtonStyle = TextButtonStyle(
    Default: (Background: @DefaultButtonDefaultBackground, LabelStyle: @DefaultButtonLabelStyle),
    Hovered: (Background: @DefaultButtonHoveredBackground, LabelStyle: @DefaultButtonLabelStyle),
    ...
);

@DefaultScrollbarStyle = ScrollbarStyle(
    Spacing: 6, Size: 6,
    Background: (TexturePath: "Common/Scrollbar.png", Border: 3),
    Handle: (TexturePath: "Common/ScrollbarHandle.png", Border: 3),
    ...
);
```

**Usage:** `Style: $C.@DefaultTextButtonStyle;` or `ScrollbarStyle: $C.@DefaultScrollbarStyle;`

### 1c. Element-Tree Tokens (Full Components)

**YES — tokens CAN define full element trees with children.** This is confirmed by multiple examples in Common.ui:

#### `@Container` — Multi-child element tree with parameterization

```
@Container = Group {
    @ContentPadding = Padding(Full: @FullPaddingValue);
    @CloseButton = false;

    Group #Title {
        Anchor: (Height: @TitleHeight, Top: 0);
        Padding: (Top: 7);
        Background: (TexturePath: "Common/ContainerHeaderNoRunes.png", ...);
    }

    Group #Content {
        LayoutMode: Top;
        Padding: @ContentPadding;
        Anchor: (Top: @TitleHeight);
        Background: (TexturePath: "Common/ContainerPatch.png", Border: 23);
    }

    Button #CloseButton {
        Anchor: (Width: 32, Height: 32, Top: -8, Right: -8);
        Style: (...);
        Visible: @CloseButton;
    }
};
```

This defines a **Group with three children** (`#Title`, `#Content`, `#CloseButton`). When you write `$C.@Container { ... }`, the engine stamps out the entire subtree.

#### `@DecoratedContainer` — Variant with additional decoration elements

Similar to `@Container` but adds `#ContainerDecorationTop` and `#ContainerDecorationBottom` children.

#### `@CheckBoxWithLabel` — Group with CheckBox + Label children

```
@CheckBoxWithLabel = Group {
    @Checked = false;
    @LabelStyle = ();

    LayoutMode: Left;

    @CheckBox #CheckBox {
        Value: @Checked;
    }

    Label {
        Text: @Text;
        Style: (...@DefaultLabelStyle, ...@LabelStyle, VerticalAlignment: Center);
    }
};
```

#### `@HeaderSearch` — Group with CompactTextField child

```
@HeaderSearch = Group {
    @MarginRight = 10;
    Anchor: (Width: 200, Right: 0);

    CompactTextField #SearchInput {
        Anchor: (Height: 30, Right: @MarginRight);
        CollapsedWidth: 34;
        ExpandedWidth: 200;
        ...
    }
};
```

#### `@PanelSeparatorFancy` — Group with three children (decorative)

```
@PanelSeparatorFancy = Group {
    @Anchor = ();
    LayoutMode: Left;
    Anchor: (...@Anchor, Height: 8);

    Group { FlexWeight: 1; Background: "Common/ContainerPanelSeparatorFancyLine.png"; }
    Group { Anchor: (Width: 11); Background: "Common/ContainerPanelSeparatorFancyDecoration.png"; }
    Group { FlexWeight: 1; Background: "Common/ContainerPanelSeparatorFancyLine.png"; }
};
```

#### `@PanelTitle` — Group with Label + divider

```
@PanelTitle = Group {
    @Alignment = Start;
    @Text = "";
    LayoutMode: Top;

    Label #PanelTitle {
        Style: (...);
        Anchor: (Height: 35, Horizontal: 8);
        Text: @Text;
    }

    Group {
        Background: #393426(0.5);
        Anchor: (Height: 1);
    }
};
```

#### Single-element tokens (element, no children)

```
@TextButton = TextButton {
    @Anchor = Anchor();
    @Sounds = ();
    Style: (...@DefaultTextButtonStyle, Sounds: (...));
    Anchor: (...@Anchor, Height: @DefaultButtonHeight);
    Padding: (Horizontal: @DefaultButtonPadding);
    Text: @Text;
};

@CheckBox = CheckBox {
    Anchor: (Width: 22, Height: 22);
    Background: (TexturePath: "Common/CheckBoxFrame.png", Border: 7);
    Padding: (Full: 4);
    Style: @DefaultCheckBoxStyle;
};
```

#### Cross-file element-tree tokens

Tokens defined in separate `.ui` files also work as full components. Example from `CodeViewer.ui`:

```
@CodeBlock = Group {
    @Code = "";
    LayoutMode: Top;

    $C.@SmallTertiaryTextButton #ToggleCode { ... }

    Group #CodeContainer {
        Visible: false;
        CodeEditor { Value: @Code; ... }
    }
};
```

Referenced in `ButtonsContent.ui` as:
```
$CV = "../CodeViewer.ui";

Group #Code0 {
    $CV.@CodeBlock {
        @Code = "...";
    }
}
```

---

## 2. Parameterization Mechanism

### 2a. `@Parameter` Defaults and Overrides

Element-tree tokens define **`@Parameter = DefaultValue`** declarations inside the element body. The consumer overrides these at instantiation:

```
// Definition (in Common.ui)
@TextButton = TextButton {
    @Anchor = Anchor();     // default: empty anchor
    @Sounds = ();           // default: no extra sounds
    @Text = "";             // implicit: text parameter
    ...
    Text: @Text;
};

// Usage (in a page .ui file)
$C.@TextButton #SaveBtn {
    @Text = "Save";                          // override @Text
    @Anchor = (Width: 180, Right: 15);       // override @Anchor
}
```

### 2b. `@ContentPadding` Override Pattern

The `@Container` token demonstrates overriding structural parameters:

```
$C.@Container {
    @ContentPadding = (Left: 0, Right: 0, Top: 10, Bottom: 0);   // override padding
    Anchor: (Width: 1180, Height: 644);                           // regular property

    #Title { ... }     // inject content into the #Title child
    #Content { ... }   // inject content into the #Content child
}
```

### 2c. Named Child Injection (`#Title`, `#Content` slots)

When a token defines children with IDs like `#Title` and `#Content`, the consumer can **inject content into those named children** by referencing them:

```
$C.@Container {
    #Title {
        $C.@Title { @Text = "My Page"; }
    }

    #Content {
        Label { Text: "Hello World"; }
    }
}
```

This is **NOT parent-to-child parameter passing** — it's the consumer adding children to named slots in the stamped tree. The engine merges the consumer's content into the matching child by ID.

### 2d. Limitations of Parameterization

- **No conditional logic** — you cannot show/hide children based on a parameter
- **No loops** — you cannot stamp N copies of a child from a parameter
- **No computed values** — parameters are static at parse time
- **No cross-file parameter passing** — `cmd.append()` has no parameter API
- **Runtime changes only via Java** — `cmd.set("#ElementId.Property", value)`

---

## 3. Composition Patterns

### 3a. Token Reference (Inline Stamping)

The primary composition mechanism. A token is referenced inline and the entire subtree is stamped at that position in the tree:

```
// In StencilBookPage.ui
$C.@Container {
    #Title {
        Group {
            LayoutMode: Left;
            $C.@Title { @Text = "Stencil Crafting:"; }     // stamp Title token
            $C.@HeaderSearch {}                             // stamp HeaderSearch token
        }
    }
}
```

**Characteristics:**
- Resolved at parse time (static)
- Full element tree is inlined
- Parameterizable via `@` overrides
- Can assign an ID: `$C.@TextButton #MyBtn { ... }`

### 3b. `cmd.append()` (Server-Side Dynamic Stamping)

The server stamps standalone `.ui` files into container elements at runtime:

```java
// In StencilSelectionPage.java build()
cmd.append("Pages/StencilBook/StencilBookPage.ui");

for (int i = 0; i < totalSetCount; i++) {
    cmd.append("#SetFilters", "Pages/StencilBook/SetFilterButton.ui");
}

for (int i = 0; i < MAX_COST_CELLS; i++) {
    cmd.append("#CostGrid", "Pages/StencilBook/CostCell.ui");
}
```

**Characteristics:**
- Resolved at runtime by the server
- Each `.ui` file defines a single root element (the component)
- Elements are appended as children of a target container
- Indexed addressing: `#Container[0]`, `#Container[1]`, etc.
- **No parameterization at append time** — all customization via subsequent `cmd.set()` calls
- Files must exist on disk (cannot be generated dynamically)

### 3c. Style Token Reference

Style properties reference tokens for consistent theming:

```
Style: $B.@FilterActiveStyle;
ScrollbarStyle: $C.@DefaultScrollbarStyle;
Background: $BG.@ItemSlotFrame;
```

### 3d. Spread Operator Composition

Styles can be composed via the spread operator:

```
@SmallButtonLabelStyle = LabelStyle(
    ...@DefaultButtonLabelStyle,      // inherit all properties
    FontSize: 14                       // override FontSize
);
```

---

## 4. What Does NOT Exist

### No `Content:` Property

Despite initial assumptions, there is **no `Content: "path/to/file.ui"` property** in the DSL. Searching all `.ui` files (both in `src/main/resources` and `docs/Reference Assets`) finds zero matches. Component inclusion is achieved exclusively through:
1. Token references (`$C.@TokenName {}`)
2. Server-side `cmd.append("path.ui")`

### No `Template` or `Prototype` Keywords

The DSL has no dedicated template/prototype mechanism. The `@Token = Element { ... }` syntax IS the template mechanism. There is no separate instantiation keyword.

### No Client-Side Instantiation

The client cannot dynamically create UI elements. Only the server can add elements via `cmd.append()`. The `.ui` files are parsed and rendered, but the client has no API for cloning or instantiating components.

### No Dynamic Children in Tokens

You cannot write a token that takes a variable number of children as a parameter. The children are fixed at definition time. Dynamic child counts require `cmd.append()` from Java.

---

## 5. Component File Architecture (Observed Patterns)

### Pattern A: Page + Appendable Sub-components

The dominant pattern in the codebase. A page `.ui` defines the static structure with empty containers, and Java fills them:

```
StencilBookPage.ui     — static layout with empty #SetFilters, #RecipeGridArea, #CostGrid
  ├── SetFilterButton.ui  — appended N times into #SetFilters
  ├── GroupFilterButton.ui — appended N times into #MaterialGroups  
  ├── SetGroupContainer.ui — appended N times into #RecipeGridArea
  │   └── RecipeIconCell.ui — appended per-group into #GroupCells
  └── CostCell.ui         — appended N times into #CostGrid
```

**File structure:**
| File | Role | Root Element |
|------|------|-------------|
| `StencilBookPage.ui` | Page layout (loaded once) | `Group` with full layout tree |
| `CostCell.ui` | Reusable component (stamped N times) | Single `Group` with children |
| `RecipeIconCell.ui` | Reusable component (stamped N times) | Single `Group` with children |
| `SetFilterButton.ui` | Reusable component (stamped N times) | Single `Group` with children |

**Java orchestration:**
```java
// 1. Load the page layout
cmd.append("Pages/StencilBook/StencilBookPage.ui");

// 2. Stamp sub-components into containers
for (int i = 0; i < count; i++) {
    cmd.append("#ContainerId", "Pages/StencilBook/SubComponent.ui");
}

// 3. Set properties on stamped instances
cmd.set("#ContainerId[0] #ChildId.Property", value);
```

### Pattern B: Token Library (Common.ui)

Common.ui serves as a **shared token library** providing:
- Scalar constants (colors, sizes)
- Typed style objects (ButtonStyle, LabelStyle, ScrollbarStyle)
- Element-tree components (Container, TextButton, HeaderSearch, PanelSeparatorFancy)

### Pattern C: Domain-Specific Style Files

Separate `.ui` files for style tokens, organized by concern:
- `Styles/Buttons.ui` — Button styles
- `Styles/Labels.ui` — Label styles
- `Styles/Backgrounds.ui` — Background colors and asset paths
- `Styles/Overlays.ui` — Overlay label styles
- `Styles/Entries.ui` — List entry styles

### Pattern D: Token Nesting

Tokens can reference other tokens, including across files:

```
// In CodeViewer.ui
@CodeBlock = Group {
    $C.@SmallTertiaryTextButton #ToggleCode { ... }    // uses Common.ui token
    Group #CodeContainer { ... }
};

// In ButtonsContent.ui
$CV = "../CodeViewer.ui";
$CV.@CodeBlock { @Code = "..."; }                       // uses CodeViewer.ui token
```

---

## 6. Addressing Appended Components

Appended components are addressed by **index** on the container:

```java
// Element at index 3 inside #CostGrid:
cmd.set("#CostGrid[3].Visible", true);
cmd.set("#CostGrid[3] #CostIcon.ItemId", "hytale:oak_log");
cmd.set("#CostGrid[3] #CostQty.Text", "x4");
```

Nested addressing:
```java
// Cell 2 inside group 0's #GroupCells:
cmd.set("#RecipeGridArea[0] #GroupCells[2] #CellIcon.ItemId", "hytale:stone_bricks");
```

---

## 7. Recommendations for Building a Shared Component Library

### Tier 1: Style Token Libraries (Always Do This)

Create domain-specific style files following the existing pattern:

```
Styles/
├── Backgrounds.ui    — background colors and asset paths
├── Buttons.ui        — TextButtonStyle, ButtonStyle tokens
├── Labels.ui         — LabelStyle tokens
├── Overlays.ui       — overlay-specific label styles
└── Entries.ui        — list entry styles
```

Reference via `$B = "../../Styles/Buttons.ui";` → `Style: $B.@MyStyle;`

### Tier 2: Element-Tree Tokens for Fixed-Structure Components

Define reusable element trees in a shared `.ui` file when the component has:
- Fixed internal structure (no variable child count)
- Parameterizable via `@` overrides
- Used in multiple pages

Good candidates:
```
@StatusBadge = Group {
    @Color = #ffffff;
    @Text = "";
    Anchor: (Width: 80, Height: 24);
    Background: @Color;
    Label { Text: @Text; Style: @BadgeStyle; }
};
```

### Tier 3: Standalone `.ui` Files for Append-Stamped Components

Create individual `.ui` files for components that need:
- Dynamic instantiation (N copies stamped by server)
- Runtime property updates via `cmd.set()` on indexed selectors
- Self-contained sub-component trees

Each file has exactly one root element. Server stamps via `cmd.append()`.

### Anti-Patterns to Avoid

1. **Don't over-abstract** — The DSL has no conditional logic, so a token that "handles all cases" will be more complex than multiple simpler tokens
2. **Don't rely on `@` parameters for runtime behavior** — They're parse-time only. Use `cmd.set()` for runtime changes
3. **Don't nest token files deeply** — The import path system is relative, and deep nesting makes paths unwieldy
4. **Don't share `.ui` files across unrelated pages for `cmd.append()`** — Each page has its own container structure and indexing scheme; shared files couple unrelated pages

### Recommended Project Structure

```
Common/UI/Custom/
├── Common.ui                           ← engine-provided + custom tokens
├── Styles/
│   ├── Backgrounds.ui                  ← shared background tokens
│   ├── Buttons.ui                      ← shared button styles
│   ├── Labels.ui                       ← shared label styles
│   ├── Overlays.ui                     ← overlay styles
│   └── Entries.ui                      ← list entry styles
├── Components/                         ← NEW: shared element-tree tokens
│   └── SharedComponents.ui             ← @StatusBadge, @InfoRow, etc.
├── Pages/
│   └── MyPlugin/
│       ├── MyPage.ui                   ← page layout
│       ├── MyListItem.ui               ← appendable sub-component
│       └── MyCostCell.ui               ← appendable sub-component
```

---

## 8. Key Takeaway

The Hytale UI DSL has **two orthogonal composition axes**:

| Axis | Mechanism | When Resolved | Parameterization | Dynamic Count |
|------|-----------|---------------|------------------|---------------|
| **Token reference** | `$File.@Token { @Param = val; }` | Parse time (static) | Yes — `@` overrides | No — fixed |
| **Server append** | `cmd.append("#container", "file.ui")` | Build time (server) | No — use `cmd.set()` after | Yes — loop in Java |

All complex UIs in the codebase use **both**: token references for static structure and consistent styling, server appends for dynamic/repeated elements. The engine does not provide a mechanism that combines both (parameterized dynamic stamping).

## See Also

- [CommonUI Library Reference](./ui-commonui-library.md) — Full token reference for Common.ui
- [Custom UI Overview](./custom-ui-overview.md) — System architecture and lifecycle
- [UI Element Reference](./ui-element-reference.md) — All element types and properties
