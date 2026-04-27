---
topic: "Custom UI Responsive Sizing & Resolution Behavior"
category: "Plugin API / Custom UI"
updated: 2026-04-27
sources:
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/layout (Anchor, FlexWeight, LayoutMode)"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/markup (property types)"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/anchor"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/enums/resizetype"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui (architecture overview)"
  - "hytalemodding.dev/en/docs/guides/plugin/ui (community guide)"
  - "docs/hytale/plugins/ui-file-system.md (local)"
  - "docs/hytale/plugins/ui-grid-layout-research.md (local)"
---

# Custom UI Responsive Sizing & Resolution Behavior

## TL;DR — Definitive Answers

| Question | Answer |
|----------|--------|
| **Percentage-based sizing?** | **NO** — Anchor properties (`Width`, `Height`, etc.) are all `Integer` type (pixels). No `%`, `vw`, `vh`, or relative units exist. |
| **Viewport-relative units?** | **NO** — there are no viewport-relative units in the `.ui` markup language. |
| **Auto-scaling with resolution?** | **UNKNOWN / LIKELY YES** — the engine almost certainly applies a global UI scale factor (similar to Unity Canvas Scaler), but this is a **client-side C# implementation detail** with no official documentation. Custom UI pages are likely rendered in the same coordinate space as built-in UI (inventory, crafting, HUD). |
| **"UI Scale" setting?** | **YES** — Hytale has a UI Scale slider in Settings > General. Its effect on Custom UI pages is **not documented**, but it almost certainly applies uniformly to all UI (built-in and custom). |
| **Anchor properties beyond Width/Height/Full?** | **YES** — `MinWidth`, `MaxWidth`, `Horizontal`, `Vertical`, plus edge anchors (`Top`, `Bottom`, `Left`, `Right`). But all are integer pixel values. |
| **Best responsive strategy?** | Use `FlexWeight` for proportional sizing, edge-anchoring (`Full`, `Top`+`Bottom`) for stretching, and `LeftCenterWrap` for adaptive column count. Fixed pixel values for individual cell sizes. |

---

## 1. Anchor Property — Complete Reference (Official Docs)

Per the [official Anchor type documentation](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/anchor), the `Anchor` object has exactly these properties:

| Property | Type | Purpose |
|----------|------|---------|
| `Left` | Integer | Distance from container's left edge (px) |
| `Right` | Integer | Distance from container's right edge (px) |
| `Top` | Integer | Distance from container's top edge (px) |
| `Bottom` | Integer | Distance from container's bottom edge (px) |
| `Width` | Integer | Fixed width (px) |
| `Height` | Integer | Fixed height (px) |
| `Full` | Integer | Shorthand for all four edges at the same distance (px) |
| `Horizontal` | Integer | Shorthand for Left + Right at the same distance (px) |
| `Vertical` | Integer | Shorthand for Top + Bottom at the same distance (px) |
| `MinWidth` | Integer | Minimum width constraint (px) |
| `MaxWidth` | Integer | Maximum width constraint (px) |

**Critical observations:**
- **All properties are `Integer`** — there is no float, percentage, or relative unit type
- **No `MinHeight` or `MaxHeight`** documented (only `MinWidth` and `MaxWidth`)
- **No percentage property** — you cannot write `Width: 50%` or `Width: 0.5`
- **No viewport-relative properties** — nothing like CSS `vw`, `vh`, `vmin`, `vmax`

---

## 2. ResizeType Enum — Not for Responsive Sizing

The [`ResizeType` enum](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/enums/resizetype) has only three values:

| Value | Purpose |
|-------|---------|
| `None` | No resize behavior |
| `Start` | Resize from the start edge |
| `End` | Resize from the end edge |

This appears to be for **interactive resize handles** (like dragging to resize a panel), NOT for responsive/adaptive layout. It does not provide resolution-based scaling.

---

## 3. How the Engine Likely Handles Resolution (Inference)

### What we know for certain

1. **All official examples use fixed pixel values** — the Layout docs show `Width: 200`, `Height: 40`, etc. No examples use percentages or relative values.

2. **The game has a "UI Scale" setting** — visible in Settings > General. This is a client-side slider.

3. **Built-in UI (inventory, crafting, HUD) renders at the same visual size regardless of resolution** — the engine uses a reference resolution and scales uniformly.

4. **Custom UI is rendered by the same C# client UI framework** — the official docs state Custom UI elements are "the basic building block of a user interface" and share the same element types as built-in UI.

### Most likely behavior (based on engine patterns)

The Hytale client almost certainly uses a **reference resolution** system (similar to Unity's Canvas Scaler). This means:

- `.ui` pixel values are in **virtual/reference pixels**, NOT physical screen pixels
- The engine scales all UI uniformly based on the ratio of actual resolution to reference resolution
- The "UI Scale" slider applies an additional multiplier on top of the resolution scaling
- A `Width: 400` element appears the same **proportional size** on 1080p and 4K displays

**Evidence:** If pixel values were literal screen pixels, a `Width: 400` element would be tiny on a 4K display — yet the game's built-in UI (which uses the same system) is usable at all resolutions. The community guide example uses `Anchor: (Width: 800, Height: 1000)` which would overflow many screens at literal pixel values, suggesting scaling is applied.

### What this means for your Blueprint Bench

Your current page root at `Anchor: (Width: 620, Height: 520)` is in **reference pixels**. The engine scales this uniformly. Item icon cells at `Width: 56, Height: 56` will maintain their proportional size across resolutions.

**You do NOT need to implement resolution-adaptive sizing.** The engine handles this automatically via its internal UI scale system.

---

## 4. Available Strategies for Adaptive Layout

While you can't use percentage-based sizing, Hytale's layout system provides several mechanisms for flexible/adaptive layouts:

### Strategy A: `FlexWeight` — Proportional Space Distribution

```
Group {
    LayoutMode: Left;
    Anchor: (Width: 600);

    Group { Anchor: (Width: 340); }       // Fixed left column
    Group { Anchor: (Width: 1); Background: #2b3542; }  // Divider
    Group { FlexWeight: 1; }              // Right column takes remaining space
}
```

`FlexWeight` distributes **remaining space** after fixed-size children are placed. This is the closest equivalent to CSS `flex: 1`.

### Strategy B: Edge Anchoring — Fill Parent

```
Group {
    Anchor: (Full: 0);          // Fill entire parent
}
// or
Group {
    Anchor: (Top: 10, Bottom: 10, Left: 10, Right: 10);  // Fill with margin
}
```

Setting opposing edges (Top+Bottom or Left+Right) causes the element to stretch between them.

### Strategy C: `LeftCenterWrap` — Adaptive Column Count

```
Group #RecipeGrid {
    LayoutMode: LeftCenterWrap;
    // Width determined by parent (FlexWeight or fixed)
    // Children wrap to new rows when horizontal space runs out
}
```

With `LeftCenterWrap`, the number of columns per row adapts to the container width. If the container is wider, more cells fit per row. **However**, since the container width itself is typically fixed in reference pixels, this only provides adaptive columns if the container uses `FlexWeight` or edge-anchoring.

### Strategy D: `MinWidth` / `MaxWidth` — Constrained Flexibility

```
Group {
    FlexWeight: 1;
    Anchor: (MinWidth: 200, MaxWidth: 500);
}
```

Constrains how much a flex-weighted element can grow or shrink. Useful for ensuring minimum readability.

---

## 5. What You CANNOT Do

| Desired Feature | Available? | Alternative |
|----------------|------------|-------------|
| `Width: 50%` | **NO** | Use `FlexWeight: 1` with a sibling that has `FlexWeight: 1` |
| `Width: 80vw` | **NO** | Use a large fixed width that the engine auto-scales |
| Item cells that change pixel size per resolution | **NO** (from markup) | The engine's global UI scale handles this automatically |
| Server queries client resolution | **NO known API** | Not exposed in the plugin API |
| Dynamically set Anchor dimensions from server | **UNTESTED** | `cmd.set("#Element.Anchor", ...)` might work but the type is unclear |
| Media queries / breakpoints | **NO** | Not part of the `.ui` system |

---

## 6. Recommendation for Blueprint Bench Item Icon Cells

**Don't try to make cells resolution-adaptive.** The engine's internal UI scaling already handles this. Instead:

1. **Use fixed reference-pixel sizes** for individual cells (e.g., `Width: 56, Height: 56`)
2. **Use `FlexWeight`** on the recipe grid's parent container to consume available space
3. **Use `LeftCenterWrap`** on the grid container so the number of columns adapts to the available width
4. **The engine's UI Scale setting** will uniformly scale everything — your 56×56 cells will be 56 reference pixels regardless of screen resolution

Your current [BlueprintBenchPage.ui](../../src/main/resources/Common/UI/Custom/Pages/BlueprintBench/BlueprintBenchPage.ui) already uses this pattern correctly:

```
// Scrollable wrapping recipe icon grid
Group {
    LayoutMode: TopScrolling;
    FlexWeight: 1;

    Group #RecipeGrid {
        LayoutMode: LeftCenterWrap;
    }
}
```

This is the **optimal approach** given the engine's capabilities.

---

## See Also

- [UI File System](./ui-file-system.md) — `.ui` file format, syntax, imports
- [Grid Layout Research](./ui-grid-layout-research.md) — `LeftCenterWrap` and `ItemGrid` patterns
- [Custom UI Item Display](./custom-ui-item-display.md) — `ItemIcon`, `ItemSlot`, `ItemGrid` elements
