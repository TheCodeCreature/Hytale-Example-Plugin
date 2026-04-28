---
topic: "Custom UI Documentation Index"
category: "Plugin API / Custom UI"
updated: 2026-04-28
---

# Custom UI Documentation

Reference documentation for Hytale's server-driven Custom UI system. These docs cover the `.ui` DSL, available elements, the CommonUI component library, and Java-side data binding.

## Documents

| Document | Description |
|----------|-------------|
| [Custom UI Overview](./custom-ui-overview.md) | Architecture, `.ui` file format, project structure, `InteractiveCustomUIPage` lifecycle, page opening patterns, troubleshooting |
| [UI Element Reference](./ui-element-reference.md) | All element types (Group, Label, TextButton, Slider, ItemGrid, TabNavigation, etc.), property types (Anchor, Padding, LayoutMode, Color), selector syntax |
| [CommonUI Library](./ui-commonui-library.md) | Reusable components from `Common.ui` — buttons, inputs, containers, separators, styles, and the spread operator for style inheritance |
| [UI Data Binding](./ui-data-binding.md) | `UICommandBuilder` methods, `UIEventBuilder` bindings, `@` value capture, all 24 event types, `BuilderCodec` setup, `ItemGridSlot`, thread safety |

## Quick Start

1. Read the **Overview** to understand architecture and `.ui` syntax
2. Use the **CommonUI Library** reference to find reusable components (don't build from scratch)
3. Consult the **Element Reference** for property details on specific elements
4. Use **Data Binding** when wiring up Java-side event handling

## Our Usage

Our [BlueprintBenchPage.ui](../../../src/main/resources/Common/UI/Custom/Pages/BlueprintBench/BlueprintBenchPage.ui) is the primary custom UI in this plugin. It demonstrates:
- `$C.@Container` with `#Title` / `#Content` structure
- `$C.@TopTabsStyle` for tab navigation
- `$C.@HeaderSearch` for search input
- `$C.@DropdownBox` with inline `DropdownEntry` children
- Style inheritance via spread operator (`...$C.@DefaultLabelStyle`)
- `TopScrolling` LayoutMode with `$C.@DefaultScrollbarStyle`
- Dynamic list population via `cmd.append()` + indexed selectors
