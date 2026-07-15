---
topic: "Custom UI Documentation Index"
category: "Plugin API / Custom UI"
updated: 2026-05-02
---

# Custom UI Documentation

Reference documentation for Hytale's server-driven Custom UI system. These docs cover the `.ui` DSL, available elements, the CommonUI component library, Java-side data binding, and asset path resolution.

## Quick Navigation

### Start Here
| Document | When to read |
|----------|-------------|
| [Custom UI Overview](./custom-ui-overview.md) | First time building a custom UI page |
| [Path Resolution — Definitive Reference](./path-resolution-definitive.md) | **Icon/texture not showing?** Start here |

### Core Reference
| Document | Description |
|----------|-------------|
| [UI Element Reference](./ui-element-reference.md) | All element types (Group, Label, TextButton, Slider, ItemGrid, TabNavigation), property types (Anchor, Padding, LayoutMode, Color), selector syntax |
| [CommonUI Library](./ui-commonui-library.md) | Reusable `Common.ui` components — buttons, inputs, containers, separators, styles, spread operator |
| [UI Data Binding](./ui-data-binding.md) | `UICommandBuilder`, `UIEventBuilder`, `@` value capture, all 24 event types, `BuilderCodec`, `ItemGridSlot`, thread safety |

### Assets & Paths
| Document | Description |
|----------|-------------|
| [Path Resolution — Definitive Reference](./path-resolution-definitive.md) | **THE** reference for how `.ui` file paths, `cmd.append()`, and `cmd.set()` resolve paths. Includes the complete algorithm, all working examples, and confirmed limitations |
| [Icon Paths & Image References](./icon-paths-and-images.md) | Icon asset directories (`ItemCategories/`, `CraftingCategories/`), how to display images in `.ui` elements, known icon files, gotchas |

### Specialized Topics
| Document | Description |
|----------|-------------|
| [ItemGrid Drag Behavior](./itemgrid-drag-behavior.md) | Drag-and-drop mechanics for ItemGrid elements |
| [ItemGrid Slot Styling](./itemgrid-slot-styling.md) | Slot appearance, quality backgrounds, uncraftable/incompatible states |
| [Hotbar in Custom UI](./hotbar-in-custom-ui.md) | Accessing and manipulating hotbar items from custom UI pages |
| [Slot Event Auto-Fields](./slot-event-auto-fields.md) | Automatic event fields populated by ItemGrid slot interactions |

## Key Rules (Cheat Sheet)

### Path Resolution at a Glance

| Context | Root | Example |
|---------|------|---------|
| `.ui` file texture/icon path | Relative to the `.ui` file | `"../../Common/RecipesIcon.png"` |
| `cmd.append(path)` | `Common/UI/Custom/` | `"Pages/StencilBook/SetFilterButton.ui"` |
| `cmd.set(sel, texturePath)` | Relative to `.ui` file defining the element | Same as in-file paths |
| `cmd.set(sel, "#hex")` | N/A — color literal | `"#2a4a6a"`, `"#141c26(0.0)"` |

### `../` Count Quick Reference

| `.ui` file depth (from `Custom/`) | `../` to reach `Custom/Common/` |
|---|---|
| `Custom/Pages/Foo.ui` | `../Common/` |
| `Custom/Pages/MyPlugin/Foo.ui` | `../../Common/` |
| `Custom/Pages/MyPlugin/Sub/Foo.ui` | `../../../Common/` |

### What Works / What Doesn't

| Want to... | Approach | Works? |
|------------|----------|--------|
| Show a shared UI texture (RecipesIcon, etc.) | `"../../Common/RecipesIcon.png"` in `.ui` | ✅ |
| Show an item's 3D icon | `ItemIcon` element + `cmd.set(".ItemId", id)` | ✅ |
| Show a game-native category icon (Wood.png) | Reference `Icons/ItemCategories/Wood.png` | ❌ Outside `UI/Custom/` |
| Ship custom PNG textures | Place in `Common/UI/Custom/Common/` | ✅ |
| Set a texture path dynamically from Java | `cmd.set(".Background", relativePath)` | ⚠️ Path relative to `.ui` file |

## Our Usage

Our [StencilBookPage.ui](../../../src/main/resources/Common/UI/Custom/Pages/StencilBook/StencilBookPage.ui) is the primary custom UI in this plugin. It demonstrates:
- `$C.@Container` with `#Title` / `#Content` structure
- `$C.@TopTabsStyle` for tab navigation
- `$C.@HeaderSearch` for search input
- Style inheritance via spread operator (`...$C.@DefaultLabelStyle`)
- `TopScrolling` LayoutMode with `$C.@DefaultScrollbarStyle`
- Dynamic list population via `cmd.append()` + indexed selectors
- `ItemIcon` for displaying item icons without texture paths
- `ItemGrid` with `ItemGridSlot` for recipe grid display
