---
topic: "Grid Layout & Item Icon Grid Rendering in Custom UI"
category: "Plugin API / Custom UI"
updated: 2026-04-27
sources:
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/layout (LayoutMode section)"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemicon"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemgrid"
  - "docs/hytale/plugins/ui-file-system.md"
  - "docs/hytale/plugins/custom-ui-item-display.md"
  - "decompiled CommandListPage.java (appendInline examples)"
---

# Grid Layout & Item Icon Grid Rendering in Custom UI

## TL;DR — Definitive Answers

| Question | Answer |
|----------|--------|
| **Is there a `LeftWrapping` LayoutMode?** | **NO** — the wrapping mode is called `LeftCenterWrap` |
| **Is there a `Grid` LayoutMode?** | **NO** — grids are achieved via `LeftCenterWrap` (wrapping) or `ItemGrid` (dedicated element) |
| **What LayoutMode wraps children into a grid?** | **`LeftCenterWrap`** — flows left-to-right, wraps when row is full, each row horizontally centered |
| **Can `append()` add icon cells that wrap?** | **YES** — if the parent container uses `LayoutMode: LeftCenterWrap`, appended children wrap into a grid |
| **How does the creative menu show items?** | The creative menu is **client-side UI (not moddable)**. It uses the built-in `ItemGrid` element with `SlotsPerRow` |
| **Best approach for a custom icon grid?** | **Option A:** `ItemGrid` element with `SlotsPerRow` + `ItemGridSlot[]` from server. **Option B:** `LeftCenterWrap` container + appended `ItemSlotButton` templates |

---

## 1. Complete LayoutMode Reference

Per the [official Hytale docs](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/layout), these are **all** available LayoutMode values:

| LayoutMode | Behavior |
|------------|----------|
| `Top` | Vertical stack, top-to-bottom |
| `Bottom` | Vertical stack, bottom-aligned |
| `Left` | Horizontal stack, left-to-right |
| `Right` | Horizontal stack, right-aligned |
| `Center` | Center children horizontally |
| `Middle` | Center children vertically |
| `CenterMiddle` | Horizontal stack, centered both axes |
| `MiddleCenter` | Vertical stack, centered both axes |
| `Full` | Absolute positioning via Anchor |
| `TopScrolling` | Vertical stack with scrollbar |
| `BottomScrolling` | Bottom-aligned with scrollbar |
| `LeftScrolling` | Horizontal with scrollbar |
| `RightScrolling` | Right-aligned horizontal with scrollbar |
| **`LeftCenterWrap`** | **Wrapping horizontal, centered rows** |

### What does NOT exist
- ~~`LeftWrapping`~~ — not a valid mode
- ~~`TopWrapping`~~ — not a valid mode
- ~~`Grid`~~ — not a valid mode
- ~~`LeftWrap`~~ — not a valid mode
- ~~`Wrap`~~ — not a valid mode

### LeftCenterWrap behavior (per official docs)

Children flow left to right. When there's no more horizontal space, they wrap to the next row. Each row is horizontally centered within the parent:

```
Group {
    LayoutMode: LeftCenterWrap;
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
    Button { Anchor: (Width: 80, Height: 30); }
}
```

Visual result:
```
┌────────────────────────────────────────────┐
│                                            │
│       ┌──────┐ ┌──────┐ ┌──────┐          │
│       │  B1  │ │  B2  │ │  B3  │          │
│       └──────┘ └──────┘ └──────┘          │
│            ┌──────┐ ┌──────┐              │
│            │  B4  │ │  B5  │              │
│            └──────┘ └──────┘              │
│                                            │
└────────────────────────────────────────────┘
```

**Key point:** Each row is centered. There is no `LeftWrap` (left-aligned wrapping) — only `LeftCenterWrap` (center-aligned wrapping). If you need left-aligned wrapping, `LeftCenterWrap` is the closest available option.

---

## 2. Two Approaches for Item Icon Grids

### Approach A: `ItemGrid` Element (Built-in Grid)

The `ItemGrid` element is a dedicated grid layout for item slots. It handles grid layout internally via `SlotsPerRow`:

```
ItemGrid #RecipeGrid {
    Anchor: (Width: 300, Height: 200);
    SlotsPerRow: 5;
    DisplayItemQuantity: true;
    RenderItemQualityBackground: false;
    Style: (SlotSize: 48, SlotSpacing: 4);
}
```

Set from server:
```java
ItemGridSlot[] slots = new ItemGridSlot[] {
    new ItemGridSlot(new ItemStack("Item_Wood_Planks", 4)).setName("Oak Planks"),
    new ItemGridSlot(new ItemStack("Item_Stone_Brick", 2)).setName("Stone Bricks"),
    new ItemGridSlot(new ItemStack("Food_Bread", 1)).setName("Bread"),
};
cmd.set("#RecipeGrid.Slots", slots);
```

**Pros:**
- Native grid layout — `SlotsPerRow` handles wrapping automatically
- Built-in slot styling (quality backgrounds, quantity, durability)
- Supports drag-and-drop via `AreItemsDraggable`
- Can link to server inventory sections via `InventorySectionId`
- Events: `SlotClicking`, `SlotDoubleClicking`, `SlotMouseEntered`, `SlotMouseExited`

**Cons:**
- Grid content is set as an `ItemGridSlot[]` array (bulk update, not append-one-at-a-time)
- Styling is via `ItemGridStyle`, not individual element styling
- May not support custom sub-elements per cell (label below icon, etc.)

### Approach B: `LeftCenterWrap` Container + Appended Templates

Use a `LeftCenterWrap` parent container and append individual icon-cell templates:

**Template: `Pages/BlueprintBench/RecipeIconCell.ui`**
```
ItemSlotButton {
    Anchor: (Width: 56, Height: 56);
    Padding: (Full: 4);

    ItemIcon #Icon {
        Anchor: (Width: 48, Height: 48);
    }
}
```

**Parent container in page `.ui`:**
```
Group #RecipeGrid {
    LayoutMode: LeftCenterWrap;
    FlexWeight: 1;
    // Children (appended templates) will wrap into rows
}
```

**Server-side population:**
```java
// In build() or handleDataEvent():
for (int i = 0; i < recipes.size(); i++) {
    CraftingRecipe recipe = recipes.get(i);
    cmd.append("#RecipeGrid", "Pages/BlueprintBench/RecipeIconCell.ui");
    cmd.set("#RecipeGrid[" + i + "] #Icon.ItemId", recipe.getPrimaryOutput().getItemId());
    evt.addEventBinding(CustomUIEventBindingType.Activating,
        "#RecipeGrid[" + i + "]",
        EventData.of("RecipeId", recipe.getId()).append("Action", "Select"));
}
```

**Pros:**
- Full control over each cell's layout (icon + label, icon + quantity text, etc.)
- Cells added incrementally via `append()` — supports dynamic filtering
- Each cell is a separate element with its own event bindings
- `ItemSlotButton` provides automatic tooltip on hover

**Cons:**
- Rows are center-aligned (no left-aligned option)
- Must manage sizing carefully — container width ÷ cell width determines columns
- No built-in scrolling (would need to wrap in a `TopScrolling` parent, or use a fixed-height container)

---

## 3. Creative Menu — How It Works

The creative menu (item browser) is **client-side UI** — it's part of the built-in C# client and is NOT moddable via Custom UI. Per the official docs:

> "Built-in interfaces controlled by the C# game client: Main menu and settings, Character creation, Built-in HUD (health, hotbar, chat), **Inventory and crafting screens**, Development tools. **You cannot modify these.**"

The creative menu uses the `ItemLibrary` protocol (server sends the full item catalog to the client) and renders via the client's internal grid. The server-side `AssetRegistryLoader` loads creative library categories from `"Item/Category/CreativeLibrary"`, and each item has `categories` in its JSON definition.

**The creative menu is NOT a Custom UI page.** It's hardcoded in the client. We cannot see its `.ui` files or modify its layout.

For a **plugin-built grid that looks like the creative menu**, use the `ItemGrid` element or the `LeftCenterWrap` pattern described above.

---

## 4. Typical Icon Cell Sizes

From engine patterns and `.ui` file analysis:

| Context | Icon Size | Cell Size | Notes |
|---------|-----------|-----------|-------|
| DroppedItemSlot.ui | 32×32 | ~40×40 | `#ItemIcon` in RespawnPage |
| ItemGrid default | ~48×48 | 48-56px | `SlotSize` in `ItemGridStyle` |
| ItemSlotButton pattern | 48×48 | 56×56 | 4px padding around icon |
| Custom recipe entry | 32×32 | 40×40 | Icon + label in a row |
| Large preview | 64×64+ | 72×72+ | For featured/selected item |

Standard inventory slot size in Hytale appears to be **48×48** for the icon, with **56×56** total cell including spacing.

---

## 5. LeftCenterWrap + Scrolling Combo

`LeftCenterWrap` does NOT have a scrolling variant (there is no `LeftCenterWrapScrolling`). To get a scrollable wrapping grid:

**Option A: Nest inside TopScrolling**
```
Group {
    LayoutMode: TopScrolling;
    ScrollbarStyle: $Common.@DefaultScrollbar;
    FlexWeight: 1;

    Group #RecipeGrid {
        LayoutMode: LeftCenterWrap;
        // Children wrap here, parent scrolls vertically
    }
}
```

**Option B: Use `ItemGrid` with enough slots** — `ItemGrid` has built-in scrolling behavior when content exceeds the grid area.

---

## 6. Can `appendInline()` Create Icon Cells?

**YES** — `appendInline()` accepts the same markup syntax as `.ui` files. You can inline-create an `ItemSlotButton` with an `ItemIcon`:

```java
cmd.appendInline("#RecipeGrid",
    "ItemSlotButton { Anchor: (Width: 56, Height: 56); Padding: (Full: 4); " +
    "ItemIcon #Icon { Anchor: (Width: 48, Height: 48); } }");
cmd.set("#RecipeGrid[0] #Icon.ItemId", "Item_Wood_Planks");
```

However, `.ui` template files are recommended over `appendInline()` for anything more than trivial elements, because:
- Templates are easier to maintain and debug
- Templates support named expressions (`@variables`), imports (`$Common`), and spread operator
- `appendInline()` strings become unreadable for complex layouts

---

## 7. Summary: Recommended Approach for Icon Grid

| Requirement | Best Approach |
|-------------|---------------|
| Simple item grid, bulk-set from server | `ItemGrid` with `SlotsPerRow` + `ItemGridSlot[]` |
| Custom cell layout (icon + label + custom styling) | `LeftCenterWrap` container + appended `.ui` templates |
| Scrollable icon grid | `ItemGrid` (built-in) or `TopScrolling` wrapper around `LeftCenterWrap` |
| Click events per cell | `ItemSlotButton` wrapper (both approaches) or `ItemGrid` slot events |
| Tooltip on hover | `ItemSlotButton` (auto-tooltip) or `ItemIcon` with `ShowItemTooltip: true` |
