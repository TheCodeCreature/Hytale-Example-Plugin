---
topic: "ItemGrid Drag Behavior — Making Items Non-Draggable"
category: "Plugin API / Custom UI"
updated: 2026-05-01
sources:
  - "decompiled ItemGridSlot.java — no drag/lock/interactive fields exist"
  - "decompiled CustomUIEventBindingType.java — drag events are reactive, not preventive"
  - "decompiled MoveItemStack.java — drag packets reference InventorySectionId"
  - "decompiled RespawnPage.java — engine uses ItemIcon for display-only items"
  - "decompiled EntitySpawnPage.java — engine uses ItemGrid.Slots for interactive slot"
  - "docs/hytale/ui/ui-element-reference.md — ItemSlot, ItemSlotButton, ItemIcon docs"
---

# ItemGrid Drag Behavior — Making Items Non-Draggable

## Short Answer

**`ItemGrid` has NO property to disable dragging.** There is no `setDraggable`, `setLocked`, `setInteractive`, `setStatic`, `setReadOnly`, or `Enabled` property that suppresses the client-side drag visual. Drag behavior is baked into the `ItemGrid` element type on the client.

To display items in a grid without drag, **don't use `ItemGrid`** — use `ItemSlot` or `ItemSlotButton` elements inside a wrapping layout container.

---

## Why ItemGrid Always Shows Drag

### Server-side: No drag control exists

`ItemGridSlot` (the Java class behind each slot) exposes these fields only:

| Field | Purpose |
|-------|---------|
| `itemStack` | The displayed item |
| `background` | Per-slot background texture |
| `overlay` | Per-slot overlay texture |
| `icon` | Per-slot custom icon |
| `isItemIncompatible` | Visual "incompatible" state |
| `name` | Custom tooltip name |
| `description` | Custom tooltip description |
| `skipItemQualityBackground` | Skip rarity background |
| `isActivatable` | Whether slot fires click events |
| `isItemUncraftable` | Visual "uncraftable" dimming |

**None of these control drag behavior.** `isActivatable` controls click events, not drag.

### Client-side: Drag is inherent to the element type

The `ItemGrid` element type renders an internal grid of slots. The client-side implementation always allows picking up items visually — this is part of the element's built-in behavior, not a configurable property.

Even when:
- No `InventorySectionId` is set (no backing inventory)
- No drag event bindings are registered on the server
- The grid is populated purely via `cmd.set("#Grid.Slots", itemGridSlotArray)`

...the client still shows the drag visual when the player clicks and holds on a slot.

### What about `InventorySectionId`?

| Configuration | Drag visual? | Drag actually moves items? |
|---------------|-------------|---------------------------|
| `InventorySectionId` set → linked to real inventory | Yes | Yes — sends `MoveItemStack` packet |
| No `InventorySectionId` → populated via `.Slots` | **Yes (visual only)** | No — no inventory backing, drag cancels |

The drag visual still appears in both cases. Without `InventorySectionId`, the drag is cosmetically annoying but functionally harmless — the item snaps back.

### What about drag event bindings?

`CustomUIEventBindingType` includes these drag-related events:

```java
DragCancelled(17),
Dropped(18),
SlotMouseDragCompleted(19),
SlotMouseDragExited(20),
SlotClickReleaseWhileDragging(21),
SlotClickPressWhileDragging(22),
```

These are **reactive** — they let you respond to drags that already happened. They do NOT prevent the drag from starting. Not binding them doesn't suppress the visual.

---

## Solution: Use Display-Only Item Elements

Hytale provides three element types for displaying items without drag:

### Option 1: `ItemSlot` (display-only, richest display)

Self-contained slot with icon + quantity + quality background + durability bar. No drag, no click events.

```
ItemSlot #OutputSlot {
    ItemId: "Food_Bread";
    Quantity: 5;
    ShowQuantity: true;
    ShowQualityBackground: true;
    Anchor: (Width: 48, Height: 48);
}
```

Server-side:
```java
cmd.set("#OutputSlot.ItemId", "Food_Bread");
cmd.set("#OutputSlot.Quantity", 5);
```

### Option 2: `ItemSlotButton` wrapping `ItemIcon` (clickable, no drag)

Fires `Activating` events on click, shows item tooltip, but has NO drag behavior. This is the pattern the engine uses for clickable item displays.

Template file (e.g., `RecipeCell.ui`):
```
ItemSlotButton {
    Anchor: (Width: 64, Height: 64);

    ItemIcon #CellIcon {
        Anchor: (Full: 4);
        ShowItemTooltip: true;
    }
}
```

### Option 3: `ItemIcon` (simplest, icon only)

Just the item icon, no quantity/quality/durability display.

```
ItemIcon #Icon {
    ItemId: "Item_Wood_Planks";
    Anchor: (Width: 32, Height: 32);
    ShowItemTooltip: true;
}
```

---

## Migration Pattern: ItemGrid → Append-Based Grid

### Before (ItemGrid — has unwanted drag)

**.ui:**
```
ItemGrid #RecipeGrid {
    SlotsPerRow: 9;
    DisplayItemQuantity: false;
    Style: (SlotSize: 64, SlotSpacing: 8,
            SlotBackground: "../../Common/BlockSelectorSlotBackground.png");
}
```

**Java:**
```java
ItemGridSlot[] slots = new ItemGridSlot[recipes.size()];
for (int i = 0; i < recipes.size(); i++) {
    slots[i] = new ItemGridSlot(new ItemStack(recipes.get(i).outputItemId(), 1));
    slots[i].setActivatable(true);
}
cmd.set("#RecipeGrid.Slots", slots);
```

### After (Append-based grid — no drag)

**.ui (main page):**
```
Group #RecipeGrid {
    LayoutMode: LeftCenterWrap;
}
```

**RecipeCell.ui (template):**
```
Group {
    Anchor: (Width: 72, Height: 72);
    Background: "../../Common/BlockSelectorSlotBackground.png";

    ItemSlotButton {
        Anchor: (Full: 4);

        ItemIcon #CellIcon {
            Anchor: (Full: 0);
            ShowItemTooltip: true;
        }
    }
}
```

**Java:**
```java
cmd.clear("#RecipeGrid");
for (int i = 0; i < recipes.size(); i++) {
    cmd.append("#RecipeGrid", "Pages/BlueprintBench/RecipeCell.ui");
    String sel = "#RecipeGrid[" + i + "]";
    cmd.set(sel + " #CellIcon.ItemId", recipes.get(i).outputItemId());
}
```

**Event binding:**
```java
// Bind Activating on each cell instead of SlotClicking on the grid
evt.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeGrid",
        EventData.of("Action", "RecipeSelect"), false);
```

### Tradeoffs

| Aspect | `ItemGrid` + `.Slots` | Append-based grid |
|--------|----------------------|-------------------|
| Drag behavior | Always shows drag visual | No drag — display only |
| Population | Single `cmd.set()` call | `clear()` + loop of `append()` + `set()` |
| Performance | Excellent (one command) | More commands, but fine for <200 items |
| Grid layout | Built-in `SlotsPerRow` | Must use `LeftCenterWrap` or manual layout |
| Slot styling | Via `ItemGridSlot` builder | Via `.ui` template styling |
| Click events | `SlotClicking` / `SlotMouseEntered` | `Activating` / `MouseEntered` |
| Tooltip | Automatic from ItemStack | Via `ShowItemTooltip: true` on ItemIcon |

---

## Engine Examples

### RespawnPage — display-only items (uses ItemIcon)

```java
// Appends a template per dropped item — no ItemGrid, no drag
for (int i = 0; i < itemsLostOnDeath.length; i++) {
    ItemStack itemStack = itemsLostOnDeath[i];
    String sel = "#DroppedItemsContainer[" + i + "] ";
    commandBuilder.append("#DroppedItemsContainer", "Pages/DroppedItemSlot.ui");
    commandBuilder.set(sel + "#ItemIcon.ItemId", itemStack.getItemId());
    commandBuilder.set(sel + "#ItemIcon.Quantity", itemStack.getQuantity());
}
```

### EntitySpawnPage — interactive single slot (uses ItemGrid.Slots)

```java
// Single-slot ItemGrid intended for drag-and-drop interaction
commandBuilder.set("#ItemMaterialSlot.Slots",
    new ItemGridSlot[]{new ItemGridSlot(new ItemStack(itemId, 1))});
```

The engine uses `ItemGrid` only when drag interaction is desired.

---

## See Also

- [UI Element Reference](./ui-element-reference.md) — full property lists for ItemSlot, ItemSlotButton, ItemIcon
- [ItemGrid Slot Styling](./itemgrid-slot-styling.md) — per-slot background/overlay customization
- [Slot Event Auto-Fields](./slot-event-auto-fields.md) — how slot index is passed in events
