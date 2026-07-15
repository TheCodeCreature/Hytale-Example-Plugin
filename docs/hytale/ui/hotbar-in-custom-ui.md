---
topic: "Hotbar in Custom UI Pages"
category: "Plugin API / Custom UI / Inventory"
updated: 2026-04-30
sources:
  - "decompiled: HudComponent.java (com.hypixel.hytale.protocol.packets.interface_)"
  - "decompiled: HudManager.java (com.hypixel.hytale.server.core.entity.entities.player.hud)"
  - "decompiled: HotbarManager.java (com.hypixel.hytale.server.core.entity.entities.player)"
  - "decompiled: Inventory.java (com.hypixel.hytale.server.core.inventory)"
  - "decompiled: ItemGridSlot.java (com.hypixel.hytale.server.core.ui)"
  - "decompiled: InteractiveCustomUIPage.java"
  - "decompiled: EntitySpawnPage.java (ItemGrid usage example)"
  - "docs/hytale/ui/ui-element-reference.md"
  - "docs/hytale/community/vex-ui-library-research.md"
---

# Hotbar in Custom UI Pages

## Summary

**There is NO `Hotbar` UI widget type in the .ui DSL.** The hotbar is exclusively a built-in HUD component (`HudComponent.Hotbar`, enum value `0`) rendered by the client. It cannot be embedded inside a `CustomUIPage`. To display hotbar contents within a custom page, you must use `ItemGrid` populated from the server, or individual `ItemSlot`/`ItemIcon` elements.

---

## 1. The Hotbar is a HUD Component, Not a UI Widget

### `HudComponent` enum

```java
// com.hypixel.hytale.protocol.packets.interface_.HudComponent
public enum HudComponent {
    Hotbar(0),           // ← The hotbar HUD element
    StatusIcons(1),
    Reticle(2),
    Chat(3),
    // ...24 total values
}
```

The hotbar is toggled on/off via `HudManager`:

```java
// Show or hide the hotbar HUD
hudManager.showHudComponents(playerRef, HudComponent.Hotbar);
hudManager.hideHudComponents(playerRef, HudComponent.Hotbar);
```

It is **always visible by default** (included in `DEFAULT_HUD_COMPONENTS`). But it lives in the client-side HUD rendering layer, completely separate from the custom UI page system.

### Key distinction

| System | Controls | Can embed in CustomUIPage? |
|--------|----------|--------------------------|
| **HUD** (`HudComponent`) | Hotbar, health, stamina, compass, chat, etc. | **NO** — client-rendered, fixed layout |
| **Custom UI** (`.ui` files) | Groups, Labels, ItemGrids, Buttons, etc. | **YES** — composable, server-controlled |

---

## 2. No `Hotbar` Widget Exists in .ui DSL

The complete set of item/inventory-related UI elements is:

| Element | Purpose | Can display hotbar items? |
|---------|---------|--------------------------|
| `ItemIcon` | Display a single item icon by ID | Yes (one at a time, display-only) |
| `ItemSlot` | Full slot: icon + quantity + quality + durability | Yes (one at a time, display-only) |
| `ItemSlotButton` | Clickable `ItemSlot` with events | Yes (one at a time, interactive) |
| `ItemGrid` | Grid of item slots, server-populated via `.Slots` | **Yes — best option for hotbar display** |
| `BlockSelector` | Block variant selector with internal grid | No — for block type selection only |
| `ItemPreviewComponent` | 3D item preview | No — single item preview |

**None of these are "the hotbar."** They are generic item display widgets that must be manually populated from the server.

---

## 3. How to Display Hotbar Contents in a Custom Page

### Option A: `ItemGrid` with server-populated `.Slots` (Recommended)

Use a 9-slot `ItemGrid` and populate it from the server by reading `inventory.getHotbar()`.

**.ui markup:**
```
ItemGrid #HotbarDisplay {
    Anchor: (Width: 468, Height: 48);
    SlotsPerRow: 9;
    DisplayItemQuantity: true;
    RenderItemQualityBackground: true;
    Style: (
        SlotSize: 48,
        SlotIconSize: 48,
        SlotSpacing: 4,
        SlotBackground: "../../Common/BlockSelectorSlotBackground.png"
    );
}
```

**Server-side Java (in `build()` or `handleDataEvent()`):**
```java
ItemContainer hotbar = player.getInventory().getHotbar();
ItemGridSlot[] slots = new ItemGridSlot[hotbar.getCapacity()];
for (int i = 0; i < hotbar.getCapacity(); i++) {
    ItemStack stack = hotbar.getItemStack((short) i);
    slots[i] = stack != null ? new ItemGridSlot(stack) : new ItemGridSlot();
}
commandBuilder.set("#HotbarDisplay.Slots", slots);
```

**`ItemGridSlot` properties available** (from decompiled `ItemGridSlot.java`):

| Property | Type | Description |
|----------|------|-------------|
| `ItemStack` | ItemStack | The item to display |
| `Background` | PatchStyle | Custom slot background |
| `Overlay` | PatchStyle | Overlay on top of slot |
| `Icon` | PatchStyle | Custom icon override |
| `IsItemIncompatible` | boolean | Grey out / mark incompatible |
| `Name` | String | Custom name override |
| `Description` | String | Custom description override |
| `SkipItemQualityBackground` | boolean | Suppress quality background |
| `IsActivatable` | boolean | Whether slot fires Activating events |
| `IsItemUncraftable` | boolean | Mark as uncraftable |

### Option B: `ItemGrid` with `InventorySectionId` (Bidirectional, requires Window)

For **full bidirectional interaction** (drag-and-drop between hotbar and the grid), use `InventorySectionId` bound to the hotbar section. This requires:

1. Opening the page with `openCustomPageWithWindows()` instead of `openCustomPage()`
2. Binding the `ItemGrid`'s `InventorySectionId` to a Window that wraps the hotbar `ItemContainer`

```
ItemGrid #HotbarGrid {
    InventorySectionId: -1;  // Hotbar section ID constant
    SlotsPerRow: 9;
    // ...
}
```

**Caveat:** This approach is more complex and requires the Window API (`ItemContainerWindow`). See [window-actions-and-updates.md](../plugins/window-actions-and-updates.md) and [vex-ui-library-research.md](../community/vex-ui-library-research.md) for details.

### Option C: Individual `ItemSlot` / `ItemIcon` elements (Manual)

For fine-grained control over each slot's layout:

```
Group #HotbarRow {
    LayoutMode: Left;

    ItemSlot #Slot0 { Anchor: (Width: 48, Height: 48); }
    ItemSlot #Slot1 { Anchor: (Width: 48, Height: 48); }
    // ... up to #Slot8
}
```

Server populates each individually:
```java
cmd.set("#Slot0.ItemId", stack.getItem().getId());
cmd.set("#Slot0.Quantity", stack.getQuantity());
```

This is more verbose but allows per-slot custom styling (e.g., highlighting the active slot).

---

## 4. Related Server-Side Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `HotbarManager` | `server.core.entity.entities.player` | Manages saved hotbar presets (Creative mode, up to 10) |
| `Inventory` | `server.core.inventory` | Player inventory with hotbar section (`HOTBAR_SECTION_ID = -1`, capacity = 9) |
| `ItemContainer` | `server.core.inventory.container` | Low-level container that holds items; `getHotbar()` returns the hotbar container |
| `HudManager` | `server.core.entity.entities.player.hud` | Controls HUD component visibility |
| `HudComponent` | `protocol.packets.interface_` | Enum of all HUD elements (Hotbar = 0) |
| `ItemGridSlot` | `server.core.ui` | Data class for populating `ItemGrid` slots from the server |

---

## 5. Gotchas

- **Stale data**: An `ItemGrid` populated via `.Slots` is a **snapshot**. If the player's hotbar changes (swap, drop, pickup), the displayed grid does NOT auto-update. You must listen for `ItemContainerChangeEvent` or `LivingEntityInventoryChangeEvent` and send updated commands. See [inventory-hotbar-events.md](../inventory-hotbar-events.md).

- **No active slot indicator**: The `ItemGrid` widget has no concept of "selected slot." To highlight the active hotbar slot, you'd need to use `ItemGridSlot.setBackground()` on the active slot, or overlay a custom highlight element.

- **HUD hotbar vs custom grid coexistence**: If your custom page is open and the HUD hotbar is also visible, the player sees two representations of the hotbar. Consider hiding the HUD hotbar while your page is open:
  ```java
  hudManager.hideHudComponents(playerRef, HudComponent.Hotbar);
  // ... on page close:
  hudManager.showHudComponents(playerRef, HudComponent.Hotbar);
  ```

- **Drag-and-drop**: A display-only `ItemGrid` (populated via `.Slots`) does NOT support drag-and-drop. For that, you need the `InventorySectionId` + Window approach (Option B above).

---

## 6. Existing Usage in This Codebase

The `StencilSelectionPage.java` already reads the hotbar to display placeholder items:

```java
// StencilSelectionPage.java line ~549
ItemContainer hotbar = player.getInventory().getHotbar();
for (int i = 0; i < PlaceBlockMetadata.HOTBAR_SIZE; i++) {
    ItemStack stack = hotbar.getItemStack((short) i);
    if (stack != null && PlaceBlockMetadata.isPlaceBlock(stack)) {
        // Build a PlaceholderRow for this slot
    }
}
```

This demonstrates the server-side scan → UI build pattern. The hotbar items are read, filtered, and rendered as individual `PlaceholderRow` components in the custom page.

---

## See Also

- [ui-element-reference.md](./ui-element-reference.md) — Full UI element catalog
- [itemgrid-slot-styling.md](./itemgrid-slot-styling.md) — ItemGridSlot styling details
- [inventory-hotbar-events.md](../inventory-hotbar-events.md) — Inventory & hotbar change events
- [slot-event-auto-fields.md](./slot-event-auto-fields.md) — ItemGrid event data
- [vex-ui-library-research.md](../community/vex-ui-library-research.md) — InventorySectionId + Windows research
