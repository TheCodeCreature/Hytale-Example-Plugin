---
topic: "ItemGrid — Inventory-Style Display with Drag-and-Drop"
category: "Plugin API / Custom UI"
updated: 2026-04-28
sources:
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemgrid"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/itemgridslot"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/itemgridstyle"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemslot"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemslotbutton"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/clientitemstack"
  - "decompiled EntitySpawnPage.java — Dropped event on ItemGrid, ItemGridSlot usage"
  - "decompiled BarterPage.java — append-based ItemIcon rows (not ItemGrid)"
  - "decompiled ItemGridSlot.java — full codec and fields"
  - "decompiled UICommandBuilder.java — set/setObject for ItemGridSlot[]"
  - "decompiled CustomUIEventBindingType.java — all slot/drag event types"
  - "docs/hytale/plugins/custom-ui-item-display.md"
  - "docs/hytale/plugins/api-reference-interactive-custom-ui.md"
  - "docs/Resources/Common/Pages/EntitySpawnPage.ui"
---

# ItemGrid — Inventory-Style Display with Drag-and-Drop

## TL;DR — Definitive Answers to All Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | **Can `ItemGrid` with `Slots` show items with drag-and-drop?** | **YES, but the drag is FROM the player's inventory INTO the grid.** The `Dropped` event fires when an item is dragged from inventory onto the grid. You receive `itemStackId` in the event data. **Icons DO render** when `ItemGridSlot` has an `ItemStack` with a valid item ID. `InventorySectionId` is NOT required for icon rendering. |
| 2 | **`Slots` vs `InventorySectionId` — what's the difference?** | **`Slots`** = server-controlled display data (read-only visual). **`InventorySectionId`** = links to a live server inventory section for real drag-and-drop between grids. `Slots` mode DOES render icons and DOES fire `Dropped` events (drag-to), but does NOT support dragging items OUT of the grid. |
| 3 | **How does the built-in inventory render its grid?** | The built-in inventory is a **hardcoded client-side page**, not a Custom UI page. It uses `InventorySectionId` linked to the player's actual inventory sections. Custom UI pages that use ItemGrid (like EntitySpawnPage) use the `Slots` property instead. |
| 4 | **Does `DisplayItemQuantity: true` work with `Slots`?** | **YES** — confirmed by your own `#CostGrid` in `BlueprintBenchPage.ui` which uses `DisplayItemQuantity: true` + `Slots` set from server. The quantity is taken from the `ItemStack.quantity` field in each `ItemGridSlot`. |
| 5 | **What events fire when dragging FROM an ItemGrid slot?** | See detailed event table below. Key finding: `Dropped` fires when an item is dropped ONTO the grid (drag-to), NOT when dragging FROM it. Dragging FROM a `Slots`-backed grid is likely **not supported**. |
| 6 | **Can slots be clickable via `setActivatable(true)`?** | **YES** — `ItemGridSlot.IsActivatable` exists in both the decompiled codec and the official docs. However, **no decompiled engine page uses `setActivatable(true)` with `SlotClicking` events**, so the exact behavior is unverified. |

---

## 1. ItemGrid Properties — Complete Reference

Per [hytalemodding.dev ItemGrid docs](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemgrid):

### Core Properties

| Property | Type | Purpose |
|----------|------|---------|
| `SlotsPerRow` | Integer | Number of columns in the grid |
| `Slots` | ItemGridSlot[] | Server-set array of slot display data |
| `ItemStacks` | ClientItemStack[] | Alternative: set items directly without full slot config |
| `InventorySectionId` | Integer | Links to a server-side InventorySection for live sync |
| `AreItemsDraggable` | Boolean | Enable drag-and-drop (see section 4 for details) |
| `AllowMaxStackDraggableItems` | Boolean | Allow dragging max stack sizes |
| `DisplayItemQuantity` | Boolean | Show stack quantity numbers |
| `RenderItemQualityBackground` | Boolean | Show quality-tier backgrounds |
| `InfoDisplay` | ItemGridInfoDisplayMode | `Tooltip` / `Adjacent` / `None` |
| `AdjacentInfoPaneGridWidth` | Integer | Width of adjacent info pane |
| `Style` | ItemGridStyle | Visual styling |

### Styling (ItemGridStyle)

| Property | Type | Purpose |
|----------|------|---------|
| `SlotSize` | Integer | Pixel size of each slot |
| `SlotIconSize` | Integer | Pixel size of the item icon within the slot |
| `SlotSpacing` | Integer | Pixel gap between slots |
| `SlotBackground` | PatchStyle / String | Background image/color for each slot |
| `DefaultItemIcon` | PatchStyle / String | Icon shown for empty slots |
| `QuantityPopupSlotOverlay` | PatchStyle / String | Overlay for quantity display |
| `DurabilityBar` | UI Path (String) | Durability bar texture |
| `DurabilityBarBackground` | PatchStyle / String | Durability bar BG |
| `DurabilityBarAnchor` | Anchor | Durability bar positioning |
| `DurabilityBarColorStart` | Color | Durability bar start color |
| `DurabilityBarColorEnd` | Color | Durability bar end color |
| `CursedIconPatch` | PatchStyle / String | Overlay for cursed items |
| `CursedIconAnchor` | Anchor | Cursed icon positioning |
| `BrokenSlotBackgroundOverlay` | PatchStyle / String | Overlay for broken items |
| `BrokenSlotIconOverlay` | PatchStyle / String | Icon overlay for broken items |
| `ItemStackActivateSound` | SoundStyle | Sound on slot activation |
| `ItemStackHoveredSound` | SoundStyle | Sound on slot hover |

### Event Callbacks (Official Docs)

| Event | Fires When |
|-------|------------|
| `SlotDoubleClicking` | Double-click on a slot |
| `DragCancelled` | A drag operation is cancelled |
| `SlotMouseEntered` | Mouse enters a slot |
| `SlotMouseExited` | Mouse exits a slot |

**Notable absence:** `SlotClicking` is NOT listed in the official ItemGrid event callbacks. It IS in the `CustomUIEventBindingType` enum but may only work with `InventorySectionId`-backed grids.

---

## 2. ItemGridSlot Properties — Complete Reference

Per [hytalemodding.dev ItemGridSlot docs](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/itemgridslot) and decompiled `ItemGridSlot.java`:

| Property | Type | Purpose | In Decompiled Codec |
|----------|------|---------|---------------------|
| `ItemStack` | ClientItemStack | The item to display (Id + Quantity + Durability + Metadata) | ✅ `ItemStack` |
| `Background` | PatchStyle / String | Custom slot background | ✅ `Background` (Value\<PatchStyle>) |
| `Overlay` | PatchStyle / String | Custom overlay | ✅ `Overlay` (Value\<PatchStyle>) |
| `Icon` | PatchStyle / String | Custom icon override (replaces item icon) | ✅ `Icon` (Value\<PatchStyle>) |
| `ExtraOverlays` | List\<PatchStyle> | Additional overlays | ❌ Not in decompiled server codec |
| `IsItemIncompatible` | Boolean | Gray out as incompatible | ✅ |
| `Name` | String | Display name override | ✅ |
| `Description` | String | Description override | ✅ |
| `InventorySlotIndex` | Integer | Links to a real inventory slot | ❌ Not in decompiled server codec |
| `SkipItemQualityBackground` | Boolean | Don't show quality background | ✅ |
| `IsActivatable` | Boolean | Whether the slot can be clicked | ✅ |
| `IsItemUncraftable` | Boolean | Show uncraftable indicator | ✅ |

### Server-side construction

```java
// Basic item slot
new ItemGridSlot(new ItemStack("Item_Wood_Planks", 4))

// With metadata
new ItemGridSlot(new ItemStack("Item_Wood_Planks", 4))
    .setName("Oak Planks")
    .setDescription("Used in construction")
    .setActivatable(true)

// Empty slot
new ItemGridSlot()

// Set on grid
cmd.set("#MyGrid.Slots", new ItemGridSlot[]{ slot1, slot2, slot3 });
```

---

## 3. The EntitySpawnPage Pattern — The Definitive Example

The `EntitySpawnPage` is the **only decompiled engine page** that uses `ItemGrid` with both `Slots` and `Dropped` event handling. Here's the complete pattern:

### .ui file (`Pages/EntitySpawnPage.ui` lines 86-100)

```
ItemGrid #ItemMaterialSlot {
    Anchor: (Width: @MaterialSlotSize, Height: @MaterialSlotSize, Horizontal: 0, Vertical: 0);
    SlotsPerRow: 1;
    Style: (
        SlotSize: @MaterialSlotSize,
        SlotIconSize: @MaterialSlotSize,
        SlotSpacing: 0,
        SlotBackground: "../Common/BlockSelectorSlotBackground.png"
    );
}
```

**Key observations:**
- **NO `AreItemsDraggable`** property set — defaults to false
- **NO `InventorySectionId`** property — this is a `Slots`-backed grid
- **NO `DisplayItemQuantity`** — single-item display, quantity irrelevant
- Styling uses `SlotBackground` for the dark slot appearance

### Java: build() — Event binding

```java
// EntitySpawnPage.java line 130
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Dropped, 
    "#ItemMaterialSlot", 
    new EventData().append("Type", "SetItemMaterial"), 
    false
);
```

**Critical finding:** The `Dropped` event fires when the player **drags an item from their inventory onto the `#ItemMaterialSlot`**. The event data includes `Type: "SetItemMaterial"`.

### Java: How the Dropped event provides item info

```java
// EntitySpawnPage.java line 175
private void handleSetItemMaterial(Ref<EntityStore> ref, Store<EntityStore> store, EntitySpawnPageEventData data) {
    if (data.itemStackId != null) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        this.selectItem(ref, store, data.itemStackId, commandBuilder);
        this.sendUpdate(commandBuilder, null, false);
    }
}
```

The `data.itemStackId` field receives the item ID of the dropped item. This is decoded from the event data codec:

```java
// EntitySpawnPageEventData codec
.append(new KeyedCodec<>("ItemStackId", Codec.STRING), (e, s) -> e.itemStackId = s, e -> e.itemStackId).add()
```

**Key insight:** The `Dropped` event automatically populates `ItemStackId` with the ID of the item that was dragged from the player's inventory. The server code does NOT set `@ItemStackId` in the `EventData` — the client automatically includes it. This means the `Dropped` event on an `ItemGrid` provides the item ID of whatever was dragged onto it.

### Java: Setting the slot display

```java
// EntitySpawnPage.java line 539
commandBuilder.set("#ItemMaterialSlot.Slots", new ItemGridSlot[]{
    new ItemGridSlot(new ItemStack(this.selectedItemId, 1))
});
```

After receiving the drop, the server updates the grid's `Slots` to display the dropped item's icon.

### Java: Clearing the slot

```java
// EntitySpawnPage.java line 305
commandBuilder.set("#ItemMaterialSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
```

Set an empty `ItemGridSlot()` to clear the display.

---

## 4. Drag-and-Drop — Two Distinct Modes

### Mode A: Drag FROM inventory ONTO an ItemGrid (`Dropped` event)

This is what EntitySpawnPage uses. The player opens their inventory alongside the custom UI page and drags an item onto the `ItemGrid`. The `Dropped` event fires on the grid, providing the item's ID.

**Requirements:**
- `ItemGrid` element in `.ui` file (NO `InventorySectionId` needed)
- `addEventBinding(CustomUIEventBindingType.Dropped, "#Grid", ...)` in `build()`
- The player's inventory must be visible (this happens automatically when a custom page is open — the inventory is accessible via Tab or is shown alongside)

**What you get in the event:**
- `ItemStackId` — the item asset ID of the dropped item (automatically populated by client)

**What you DON'T get:**
- Slot index of the target slot (only one slot in EntitySpawnPage's case)
- Quantity of the dropped stack
- The source inventory slot

### Mode B: Full bidirectional drag via `InventorySectionId` + `openCustomPageWithWindows()`

This links the `ItemGrid` to a real server-side `InventorySection`, enabling:
- Drag items FROM the grid (the grid represents a real container)
- Drag items INTO the grid from player inventory
- Server-synced slot contents
- Full `SlotClicking`, `SlotDoubleClicking` events

**Requirements:**
- `InventorySectionId: 0` (or appropriate section ID) on the `ItemGrid`
- `AreItemsDraggable: true` on the `ItemGrid`
- Page opened via `player.getPageManager().openCustomPageWithWindows(ref, store, page, window)` where `window` implements `ItemContainerWindow`
- Window provides an `ItemContainer` that maps to the `InventorySectionId`

**No decompiled engine pages use this combination with Custom UI pages.** The engine crafting benches (StructuralCraftingWindow, etc.) use the Window system directly, not Custom UI pages. However, the `openCustomPageWithWindows()` API exists and is documented.

---

## 5. Event Reference — All Slot/Drag Events

From `CustomUIEventBindingType` enum (decompiled):

| Event | Ordinal | What Fires It | Provides Slot Info? |
|-------|---------|---------------|---------------------|
| `SlotClicking` | 13 | Click on a slot | Unknown — likely `InventorySectionId` grids only |
| `SlotDoubleClicking` | 14 | Double-click on a slot | Unknown |
| `SlotMouseEntered` | 15 | Mouse enters a slot | Unknown |
| `SlotMouseExited` | 16 | Mouse exits a slot | Unknown |
| `DragCancelled` | 17 | Drag operation cancelled | Unknown |
| `Dropped` | 18 | Item dropped onto element | **YES** — provides `ItemStackId` automatically |
| `SlotMouseDragCompleted` | 19 | Drag completed on a slot | Unknown |
| `SlotMouseDragExited` | 20 | Drag exits a slot | Unknown |
| `SlotClickReleaseWhileDragging` | 21 | Click release during drag | Unknown |
| `SlotClickPressWhileDragging` | 22 | Click press during drag | Unknown |

**Only `Dropped` has confirmed behavior** from decompiled code (EntitySpawnPage).

The official hytalemodding.dev docs list ItemGrid event callbacks as:
- `SlotDoubleClicking`
- `DragCancelled`
- `SlotMouseEntered`
- `SlotMouseExited`

**Notably missing from official docs:** `SlotClicking`, `Dropped`, `SlotMouseDragCompleted`, `SlotMouseDragExited`. However, `Dropped` works in practice (EntitySpawnPage uses it). The official docs may be incomplete.

---

## 6. IsActivatable — What We Know

`ItemGridSlot.IsActivatable` is a boolean field in both:
- The decompiled `ItemGridSlot.java` codec
- The official hytalemodding.dev ItemGridSlot property list

**However, no decompiled engine page sets `setActivatable(true)`.** The field exists and is transmitted to the client, but its exact effect is unverified. Likely behavior:
- When `true`, the slot may fire `SlotClicking` events when clicked
- When `false`, clicking the slot does nothing (display-only)

**This needs testing.**

---

## 7. BarterPage Comparison — NOT an ItemGrid Pattern

The BarterPage uses **`append()` + individual `ItemIcon` elements**, NOT `ItemGrid`:

```java
// BarterPage uses a template-per-row approach
commandBuilder.append("#TradeGrid", "Pages/BarterTradeRow.ui");
commandBuilder.set(selector + " #OutputSlot.ItemId", itemId);
commandBuilder.set(selector + " #OutputQuantity.Text", qty > 1 ? String.valueOf(qty) : "");
```

Each `BarterTradeRow.ui` template has individual `ItemIcon` elements (`#OutputSlot`, `#InputSlot`) with `.ItemId` properties. This is NOT drag-and-drop — clicking the `#TradeButton` fires an `Activating` event.

---

## 8. Practical Recommendations for Blueprint Bench

### For a display-only recipe grid (current approach: Approach B — `LeftCenterWrap` + `ItemSlotButton`)

Your current approach using `LeftCenterWrap` + appended `RecipeIconCell.ui` templates with `ItemSlotButton` + `ItemIcon` is **correct for a clickable icon grid**. Each cell fires `Activating` events with the recipe ID.

### For adding drag FROM the grid in the future

**Option A: Fake drag with click events (recommended)**
- Keep `ItemSlotButton` cells with `Activating` events
- On click, enter an "assigning" mode where the next click target receives the item
- No actual drag visual, but functionally equivalent

**Option B: ItemGrid with Slots + custom drag handling**
- Replace the `LeftCenterWrap` container with an `ItemGrid` element
- Set `Slots` with `ItemGridSlot[]` where each slot has `setActivatable(true)`
- Bind `SlotClicking` events (if it works with `Slots` mode)
- Quantity display via `DisplayItemQuantity: true`
- **Risk:** `SlotClicking` may not work without `InventorySectionId`

**Option C: ItemGrid with InventorySectionId (full drag-and-drop)**
- Create a `Window implements ItemContainerWindow` with a `SimpleItemContainer` sized to your recipe count
- Pre-populate the container with `ItemStack` objects for each recipe output
- Open with `openCustomPageWithWindows()`
- Set `AreItemsDraggable: true` on the `ItemGrid`
- Bind `SlotClicking`, `Dropped`, `DragCancelled` events
- **Risk:** Most complex; items in the container are "real" — the player could remove them; requires careful container management

### For quantity display

Your existing `#CostGrid` already proves this works:

```
ItemGrid #CostGrid {
    FlexWeight: 1;
    SlotsPerRow: 4;
    DisplayItemQuantity: true;
    RenderItemQualityBackground: false;
    Style: (SlotSize: 48, SlotSpacing: 4);
}
```

```java
costSlots[idx++] = new ItemGridSlot(new ItemStack(e.getKey(), e.getValue()))
        .setName(e.getKey().replace('_', ' '));
cmd.set("#CostGrid.Slots", costSlots);
```

The quantity from `ItemStack` is displayed in the bottom-right of each slot when `DisplayItemQuantity: true`.

---

## 9. Icon Rendering — Why It Should Work

Item icons render based on the `ItemStack.Id` field in the `ItemGridSlot`. The client resolves the icon texture from the item's asset definition. This works independently of `InventorySectionId`.

**If icons weren't rendering previously**, possible causes:
1. Invalid `ItemId` — the item asset doesn't exist on the client
2. `ItemStack` quantity was 0 — some display logic may skip qty=0 items
3. The `ItemGridSlot` was constructed without an `ItemStack` (empty constructor)
4. The `.ui` file didn't have correct sizing (e.g., `SlotSize: 0` or missing `Anchor`)

**Verified working pattern** (EntitySpawnPage):
```java
new ItemGridSlot(new ItemStack(itemId, 1))  // qty >= 1, valid itemId
```
