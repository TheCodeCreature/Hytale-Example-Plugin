---
topic: "Vex Lich Dungeon — Community UI Library & Grid Pattern Research"
category: "Community / Custom UI"
updated: 2026-04-28
sources:
  - "https://vex.boop.ninja/dev/ui/ — UI Findings hub"
  - "https://vex.boop.ninja/dev/ — Dev log home"
  - "https://vex.boop.ninja/feed.xml — RSS feed"
  - "https://vex.boop.ninja/md/dev/ui/cheat-sheet.md — UI Cheat Sheet"
  - "https://vex.boop.ninja/md/dev/ui/core-rules.md — Core UI Rules"
  - "https://vex.boop.ninja/md/dev/ui/patterns.md — Common Patterns"
  - "https://github.com/mbround18/hytale-modding-template — Modding template"
  - "docs/hytale/plugins/ui-grid-layout-research.md — Our ItemGrid research"
  - "docs/hytale/plugins/itemgrid-inventorysectionid-deep-research.md — Deep InventorySectionId research"
  - "docs/hytale/plugins/custom-ui-item-display.md — Item display element reference"
  - "docs/hytale/assets/common-ui-catalog.md — Common.ui template catalog"
---

# Vex Lich Dungeon — Community UI Library & Grid Pattern Research

## 1. What Is the Vex UI Library?

**Vex: The Lich Dungeon** is a community Hytale server mod by [mbround18](https://github.com/mbround18) — a procedural dungeon crawler with scoring, party HUDs, leaderboards, and custom UI pages. The project's dev logs at [vex.boop.ninja/dev](https://vex.boop.ninja/dev) document extensive Hytale UI work including:

- A **UI Grimoire** — syntax reference, patterns, and validation rules for Hytale `.ui` files
- A **VS Code extension** — intellisense, validation, and highlighting for Hytale UI
- A **UI mangle generator** — generates Java classes from `.ui` definitions to keep bindings in sync
- A **macro library** — reusable `.ui` templates (`Layout.ui`, `Grid.ui`, `Inputs.ui`, `Tabs.ui`, etc.)

### Key Caveat

The Vex macro library source code is **not publicly available**. The modding template at [github.com/mbround18/hytale-modding-template](https://github.com/mbround18/hytale-modding-template) is a basic HelloWorld starter — it does NOT contain the macro library. The UI library is part of the private Vex Lich Dungeon project. What we have are:
- Dev log descriptions of what they built
- The published UI Grimoire pages (cheat sheet, core rules, patterns)
- The snippet shown in the VS Code extension

---

## 2. The `$G.@TileGrid` Macro — Same Engine Syntax

The user-provided snippet:

```
$L = "../../Macros/Layout.ui";
$G = "../../Macros/Grid.ui";

$L.@PageFrame #Frame {
  @Width = 560;
  @Height = 360;
  $L.@HeaderBar #Header { @Text = "Demo: Grid"; }
  $G.@TileGrid #Grid { }
}
```

### Analysis: This Is the Same Macro System We Already Use

**YES — this is identical to the `$C = "../../Common.ui"` / `$C.@Container` pattern.** The syntax:

| Vex Pattern | Our Pattern | Meaning |
|-------------|-------------|---------|
| `$L = "../../Macros/Layout.ui"` | `$C = "../../Common.ui"` | Import a `.ui` file as alias |
| `$L.@PageFrame` | `$C.@Container` | Instantiate a named template from that file |
| `@Width = 560` | `@Text = "Title"` | Override a template variable |
| `$G.@TileGrid #Grid { }` | `$C.@HeaderSearch {}` | Instantiate template with element ID |

The Vex team simply organized their templates into **domain-specific files** rather than putting everything in `Common.ui`:

| Vex Macro File | Purpose | Our Equivalent |
|----------------|---------|----------------|
| `Macros/Layout.ui` | `@PageFrame`, `@HeaderBar` | `Common.ui` → `@Container`, `@Title` |
| `Macros/Grid.ui` | `@TileGrid` | No direct equivalent |
| `Macros/Inputs.ui` | Input components | `Common.ui` → `@TextField`, `@NumberField` |
| `Macros/Tabs.ui` | Tab patterns | `Common.ui` → `@TopTabsStyle`, `@HeaderTabsStyle` |
| `Macros/Toolbar.ui` | Toolbar layouts | — |
| `Macros/Stats.ui` | Stat displays | — |
| `Macros/Toast.ui` | Toast notifications | — |
| `Macros/Modal.ui` | Modal dialogs | `Common.ui` → `@PageOverlay`, `@DecoratedContainer` |
| `Macros/Pagination.ui` | Pagination controls | — |

**Conclusion:** There is no special engine feature here. The Vex library is a community-built set of reusable `.ui` templates using the exact same macro/template system documented in the Hytale engine.

---

## 3. What Is `@TileGrid`? — Probable Implementation

We don't have the `Grid.ui` source, but we can infer what `@TileGrid` likely is based on:

1. **The Vex UI Grimoire** documents `ItemGrid` as a core node type (confirmed in core-rules.md)
2. **Their demos** include a "Grid" page among other demos
3. **The engine** only has two approaches for grid layouts: `ItemGrid` element or `LeftCenterWrap` container

### Most Likely: A Styled `Group` Wrapper Around an `ItemGrid` or `LeftCenterWrap`

```
// Probable @TileGrid template definition (reconstructed):
@TileGrid = Group {
  @Columns = 6;
  @SlotSize = 48;
  @SlotSpacing = 4;

  LayoutMode: Top;
  FlexWeight: 1;

  // Option A: Built-in ItemGrid
  ItemGrid #Grid {
    SlotsPerRow: @Columns;
    Style: (SlotSize: @SlotSize, SlotSpacing: @SlotSpacing);
    DisplayItemQuantity: true;
  }

  // Option B: LeftCenterWrap container for custom cells
  // Group #Grid {
  //   LayoutMode: LeftCenterWrap;
  // }
};
```

### Why "TileGrid" and Not "ItemGrid"?

The name `TileGrid` suggests a **generic tile/icon grid** — not necessarily tied to `ItemGrid`'s `InventorySectionId` system. It likely uses `LeftCenterWrap` with appended cell templates, giving full control over each cell's content. This would support:
- Custom icons (not just items)
- Label text below/above icons
- Mixed content per cell
- Click events per cell (not just slot events)

This is exactly our **Approach B** from [ui-grid-layout-research.md](../plugins/ui-grid-layout-research.md).

---

## 4. Relevance to Our ItemGrid Problem

### What We Need

A Custom UI ItemGrid with drag-and-drop for a blueprint bench.

### What the Vex Library Tells Us

1. **The macro system is NOT a new grid approach** — it's organizational sugar around the same engine primitives we already know.

2. **`@TileGrid` is likely NOT using `InventorySectionId`** — the Vex project is a dungeon crawler (leaderboards, scoring, HUDs), not an inventory management system. Their grid is probably for displaying icons/tiles, not for drag-and-drop inventory sections.

3. **The Vex UI Grimoire confirms the same core node types** we documented:
   - `ItemGrid` — the only grid element with built-in slot management
   - `Group` with `LayoutMode: LeftCenterWrap` — the only wrapping layout
   - No secret `Grid` or `TileGrid` node type exists

4. **For drag-and-drop, we must use `ItemGrid` + `InventorySectionId`** — the Vex `@TileGrid` macro doesn't change this fundamental engine constraint.

### Our Existing Research Already Covers the Path Forward

| Approach | Document | Drag-and-Drop? |
|----------|----------|----------------|
| `ItemGrid` + `InventorySectionId` + `openCustomPageWithWindows()` | [itemgrid-inventorysectionid-deep-research.md](../plugins/itemgrid-inventorysectionid-deep-research.md) | **YES** — full bidirectional |
| `ItemGrid` + `ItemGridSlot[]` from server (no window) | [ui-grid-layout-research.md](../plugins/ui-grid-layout-research.md) | `SlotClicking` events only (no native drag) |
| `LeftCenterWrap` + appended templates | [ui-grid-layout-research.md](../plugins/ui-grid-layout-research.md) | `Activating` events only (click-to-act like BarterPage) |

---

## 5. Vex UI Grimoire — Documented Findings

The Vex team published three reference pages accessible at `vex.boop.ninja/md/dev/ui/`:

### Core UI Rules (`core-rules.md`)

Confirmed node types from scanning base game client files:

```
Group, Label, Button, ProgressBar, TextButton, TextField,
ActionButton, CheckBox, DropdownBox, ItemGrid, TabNavigation,
TabButton, SliderNumberField, NumberField, ToggleButton,
DynamicPane, ItemPreviewComponent, DynamicPaneContainer,
SceneBlur, Panel, BackgroundImage
```

**Notable:** No `TileGrid` in the core types — confirming it's a macro template, not an engine primitive.

### Cheat Sheet (`cheat-sheet.md`)

Key rules we should adopt:
- `Image` is NOT a node type — use `BackgroundImage { Image: "..." }` or `Group` with `Background`
- `Border` is NOT a `Group` property — use it inside `Background`/`PatchStyle` tuples
- No standalone spread statements (`...@Template;`) — spread only inside tuples
- Client UI paths: strip everything through `Custom/`
- Server translations in `shared/interfaces/src/main/resources/Server/Languages/en-US/server.lang`

### Patterns (`patterns.md`)

Common patterns documented:
- Container with Title (`$C.@Panel` + `$C.@Title`)
- Overlay + Centered Modal (`$C.@PageOverlay` + `LayoutMode: Middle`)
- Style Merge (`...@DefaultLabelStyle, FontSize: 18`)
- HUD Overlay Pattern

**No grid-specific patterns documented** in the public pages.

---

## 6. Vex Dev Log Timeline — UI-Relevant Entries

| Date | Entry | Relevance |
|------|-------|-----------|
| 2026-01-28 | UI Grimoire v1 | Published syntax reference, patterns, validation rules |
| 2026-01-31 | UI Pipeline Stabilized | Working UI pipeline for pages/HUDs, path normalization lessons |
| 2026-01-31 | TroubleDev Alignment Check | Custom UI path conventions, threading model verification |
| 2026-02-02 | Vex UI Components + UI Mangle | Leaderboard, portal countdown, dungeon summary HUDs; Java class generation from `.ui` |
| 2026-02-02 | UI Tooling + Friends Sweep | `PlayerPoller`, `UiThread` utilities, formatted logging |
| 2026-02-02 | Friends UI + Party HUD Refactors | Social UI stability, non-null model hardening |
| 2026-02-04 | Remove Friends Plugin | Dropped friends UI in favor of upcoming native feature |
| 2026-02-09 | Enemy Scoring & HUD Fixes | Thread-safe HUD updates, PlayerRef rebinding |
| 2026-02-10 | AGPL Commons Rider | New UI manager front-end, shared stream utilities |

### Key Vex Lessons Applicable to Us

1. **Path normalization is critical** — strip `Common/UI/Custom/` prefix for client paths
2. **UI mangle generation** — generating Java bindings from `.ui` files prevents ID drift
3. **Thread-safe HUD updates** — use `UiThread.runOnPlayerWorld()` or equivalent, not scheduler
4. **PlayerRef rebinding** — refresh `PlayerRef` binding from Universe when handling events
5. **Build tiny demos first** — single-purpose UI demos before scaling to complex layouts

---

## 7. What We Don't Have (and Can't Get)

| Missing Piece | Why It Matters | Workaround |
|---------------|----------------|------------|
| `Macros/Grid.ui` source | Would reveal exact `@TileGrid` implementation | Reconstruct from engine primitives (see §3) |
| `Macros/Layout.ui` source | Would show `@PageFrame`, `@HeaderBar` structure | Use our `$C.@Container`, `$C.@Title` equivalents |
| Vex demo page sources | Would show how grid demos work | Build our own test pages |
| UI mangle tool source | Would show Java generation approach | Manually maintain element ID refs |
| VS Code extension internals | Would show validation rules | Use the published Grimoire rules |

---

## 8. Actionable Takeaways for Custom UI ItemGrid with Drag-and-Drop

1. **The Vex `@TileGrid` does NOT solve our drag-and-drop problem.** It's a visual convenience macro, not a new engine capability.

2. **Our path forward remains `openCustomPageWithWindows()`** — per [itemgrid-inventorysectionid-deep-research.md](../plugins/itemgrid-inventorysectionid-deep-research.md):
   - Create a `Window` implementing `ItemContainerWindow`
   - Use `SimpleItemContainer` for virtual inventory sections
   - Open via `pageManager.openCustomPageWithWindows(ref, store, page, window)`
   - Set `#Grid.InventorySectionId` to `window.getId()` in `build()`

3. **Consider adopting the Vex organizational pattern** — split our macros into domain-specific `.ui` files:
   ```
   Common/UI/Custom/Macros/
   ├── Layout.ui     — @PageFrame, @HeaderBar, @SplitPanel
   ├── Grid.ui       — @TileGrid, @IconCell
   ├── Bench.ui      — @RecipeRow, @CostDisplay
   └── Inventory.ui  — @InventoryGrid, @SlotCell
   ```

4. **The Vex cheat sheet rules are validated** against the same engine we use — adopt them as project conventions (especially the `Image` is not a node type, and no standalone spread warnings).

---

## See Also

- [ui-grid-layout-research.md](../plugins/ui-grid-layout-research.md) — Grid layout approaches (ItemGrid vs LeftCenterWrap)
- [itemgrid-inventorysectionid-deep-research.md](../plugins/itemgrid-inventorysectionid-deep-research.md) — Deep dive on InventorySectionId + Windows for drag-and-drop
- [custom-ui-item-display.md](../plugins/custom-ui-item-display.md) — ItemIcon, ItemSlot, ItemGrid element reference
- [custom-ui-for-blueprint-bench.md](../plugins/custom-ui-for-blueprint-bench.md) — Blueprint bench Custom UI API
- [common-ui-catalog.md](../assets/common-ui-catalog.md) — Full Common.ui template & style catalog
- [resources.md](./resources.md) — General community resources
