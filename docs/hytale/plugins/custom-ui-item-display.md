---
topic: "Item/Block Icon Display in Custom UI Pages"
category: "Plugin API / Custom UI"
updated: 2026-04-25
sources:
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemicon"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemslot"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemslotbutton"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemgrid"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itempreviewcomponent"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/sprite"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/assetimage"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/itemgridslot"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/clientitemstack"
  - "decompiled UICommandBuilder.java"
  - "decompiled ItemGridSlot.java"
  - "decompiled InteractiveCustomUIPage.java"
  - "decompiled BarterPage.java"
  - "decompiled RespawnPage.java (Pages/DroppedItemSlot.ui, #ItemIcon usage)"
---

# Item/Block Icon Display in Custom UI Pages

## TL;DR — Definitive Answers

| Question | Answer |
|----------|--------|
| **Can you display item icons in custom UI?** | **YES** — use `ItemIcon`, `ItemSlot`, or `ItemGrid` elements |
| **What element type to use?** | `ItemIcon` for icon-only; `ItemSlot` for full slot (icon + qty + quality + durability); `ItemGrid` for grid of slots |
| **How to set the item from server?** | `cmd.set("#MyIcon.ItemId", "Food_Bread")` or `cmd.setObject("#MyGrid.Slots", itemGridSlotArray)` |
| **Can you render 3D item previews?** | **YES** — use `ItemPreviewComponent` with `ItemId` and `ItemScale` |
| **Are interactive inventory slots possible?** | **YES** — via `ItemGrid` with `InventorySectionId` + `openCustomPageWithWindows()` |
| **Can you set item icons via `cmd.set()`?** | **YES** — `cmd.set("#SlotId.ItemId", "Item_Wood_Planks")` |

---

## 1. Element Types for Item Display

### ItemIcon — Simple Icon Display

**Purpose:** Displays an item's icon by asset ID. No quantity, no quality background, no durability bar.

**Official description** (hytalemodding.dev):
> "Displays an item icon based on ItemId. Can optionally show tooltips on hover. For Custom UI with clickable item slots, prefer using ItemSlot which includes quality backgrounds, quantity, and durability bar."

**Key properties:**

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | String | The item asset ID (e.g., `"Food_Bread"`, `"Item_Wood_Planks"`) |
| `ShowItemTooltip` | Boolean | When true, shows full item tooltip on hover. Leave false inside `ItemSlotButton`. |
| `Anchor` | Anchor | Size/position |

**Usage in `.ui` file:**
```
ItemIcon #OutputIcon {
    ItemId: "Item_Wood_Planks";
    Anchor: (Width: 32, Height: 32);
}
```

**Set from server:**
```java
cmd.set("#OutputIcon.ItemId", "Item_Wood_Planks");
```

**Engine usage:** `Pages/DroppedItemSlot.ui` uses `#ItemIcon` — the `RespawnPage` sets it via `ItemGridSlot`.

---

### ItemSlot — Full Slot Display (Display-Only)

**Purpose:** Self-contained item slot that renders like native inventory slots. Displays icon, quantity, quality background, and durability bar.

**Official description** (hytalemodding.dev):
> "A self-contained item slot element for Custom UI that renders like native inventory slots. This is a display-only element - use inside an ItemSlotButton for click handling."

**Key properties:**

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | String | Item asset ID |
| `Quantity` | Integer | Item count to display |
| `ShowQuantity` | Boolean | Whether to show quantity number |
| `ShowQualityBackground` | Boolean | Whether to show quality background color |
| `ShowDurabilityBar` | Boolean | Whether to show durability bar |

**Two usage modes (per official docs):**
1. **Config-based:** Set `ItemId` and `Quantity` directly (for shops, previews)
2. **Instance-based:** Call `SetItemStack()` with a `ClientItemStack` (for actual inventory items)

**Usage in `.ui` file:**
```
ItemSlotButton #TradeButton {
    ItemSlot #OutputSlot {
        Anchor: (Width: 48, Height: 48);
    }
}
```

**Set from server:**
```java
cmd.set("#OutputSlot.ItemId", "Food_Bread");
cmd.set("#OutputSlot.Quantity", 5);
```

---

### ItemSlotButton — Clickable Slot with Tooltip

**Purpose:** A button that automatically shows item tooltips when hovering over `ItemIcon` or `ItemSlot` children.

**Official description** (hytalemodding.dev):
> "A button that automatically shows item tooltips when hovering over ItemIcon or ItemSlot children. Designed for Custom UI scenarios where you need clickable item slots with tooltips."

**Usage:** Replace `Button` with `ItemSlotButton` in your `.ui` file. Any `ItemIcon` or `ItemSlot` children will automatically show tooltips on hover, and clicks anywhere on the button work normally.

**Event callbacks:** `Activating`, `DoubleClicking`, `RightClicking`, `MouseEntered`, `MouseExited`

**Pattern:**
```
ItemSlotButton #RecipeOutput {
    Anchor: (Width: 56, Height: 56);
    Style: @SlotButtonStyle;

    ItemSlot #Slot {
        Anchor: (Width: 48, Height: 48);
        ShowQuantity: true;
        ShowQualityBackground: true;
    }
}
```

---

### ItemGrid — Grid of Slots

**Purpose:** Grid of item slots for inventory-like displays. Supports drag-and-drop, scrolling, and server-synced inventory sections.

**Key properties:**

| Property | Type | Description |
|----------|------|-------------|
| `Slots` | ItemGridSlot[] | Array of slot data |
| `ItemStacks` | ClientItemStack[] | Array of item stacks |
| `SlotsPerRow` | Integer | Columns in the grid |
| `InventorySectionId` | Integer | Links to a server-side inventory section for live drag/drop |
| `AreItemsDraggable` | Boolean | Enable drag-and-drop |
| `RenderItemQualityBackground` | Boolean | Show quality backgrounds |
| `InfoDisplay` | ItemGridInfoDisplayMode | How to display item info |
| `DisplayItemQuantity` | Boolean | Show stack quantities |
| `Style` | ItemGridStyle | Visual styling (SlotSize, SlotSpacing, etc.) |

**Event callbacks:** `SlotDoubleClicking`, `DragCancelled`, `SlotMouseEntered`, `SlotMouseExited`

**Set slots from server (via `ItemGridSlot` array):**
```java
ItemGridSlot[] slots = new ItemGridSlot[] {
    new ItemGridSlot(new ItemStack("Item_Wood_Planks", 4)).setName("Oak Planks"),
    new ItemGridSlot(new ItemStack("Item_Stone_Brick", 2)).setName("Stone Bricks"),
};
cmd.set("#MyGrid.Slots", slots);
```

---

### ItemPreviewComponent — 3D Item Preview

**Purpose:** Renders a 3D preview of an item (model rendering, not just 2D icon).

**Key properties:**

| Property | Type | Description |
|----------|------|-------------|
| `ItemId` | String | Item asset ID |
| `ItemScale` | Float | Scale of the 3D model |

**Usage in `.ui` file:**
```
ItemPreviewComponent #Preview3D {
    ItemId: "Item_Iron_Sword";
    ItemScale: 1.5;
    Anchor: (Width: 128, Height: 128);
}
```

**Set from server:**
```java
cmd.set("#Preview3D.ItemId", "Item_Iron_Sword");
cmd.set("#Preview3D.ItemScale", 2.0f);
```

---

### Sprite — Spritesheet/Image Display

**Purpose:** Displays a spritesheet-based animation or static image. NOT for item icons — uses `TexturePath` to load a UI image file.

**Key properties:** `TexturePath` (UI Path String), `Frame` (SpriteFrame), `FramesPerSecond`, `Angle`, `RepeatCount`

**Cannot render item icons by asset path.** This is for custom PNG/spritesheet images only.

---

### AssetImage — Asset-Based Image

**Purpose:** Displays an image from an asset path.

**Key property:** `AssetPath` (String)

**Unclear whether it can reference item icon textures.** The official docs don't specify what asset paths are valid. It likely references image assets in the game's asset system, not dynamically resolved item icons. For item icons, use `ItemIcon` or `ItemSlot` instead.

---

## 2. Item Icon Paths — What ItemId Values to Use

The `ItemId` property on `ItemIcon`, `ItemSlot`, and `ItemPreviewComponent` uses the **item asset ID** — the same string used in `ItemStack` constructors and recipe definitions.

### Format

| Item Type | ItemId Example | Notes |
|-----------|---------------|-------|
| Block items | `"Item_Wood_Planks"` | The item form of a block |
| Block items (alt) | `"BlockType_Kweebec_Bed"` | Some blocks use BlockType prefix |
| Tool items | `"Item_Iron_Pickaxe"` | Standard item prefix |
| Food items | `"Food_Bread"` | Category prefix varies |
| Generic items | `"Item_Stick"` | Standard item prefix |

### How the client resolves the icon

The client has an internal mapping from `ItemId` → icon texture. This is part of the item asset definition (e.g., `Item/Items/Item_Wood_Planks.json` has an `Icon` field). The `ItemIcon` element uses this built-in resolution — you do NOT need to know the texture path, just the item ID.

### How to find valid ItemIds

- Check your mod's `Server/Item/Items/` directory for item JSON files
- The filename (without `.json`) is typically the `ItemId`
- Check `CraftingRecipe` definitions for `itemId` values in inputs/outputs
- Use the in-game `/give` command autocomplete to discover IDs

---

## 3. Inventory Slot Integration — Interactive Slots in Custom UI

### Can a custom UI page include interactive inventory slots?

**YES — via two mechanisms:**

### Mechanism A: `ItemGrid` with `InventorySectionId`

The `ItemGrid` element has an `InventorySectionId` property (Integer). When set, this links the grid to a server-side `InventorySection`, enabling:
- Real-time slot synchronization with the server
- Native drag-and-drop behavior
- Server receives `SlotClicking`/`SlotDoubleClicking` events

This requires opening the page with associated windows via `openCustomPageWithWindows()`:

```java
// Create page + window
BlueprintPage page = new BlueprintPage(playerRef);
BlueprintInputWindow window = new BlueprintInputWindow(); // extends Window implements ItemContainerWindow

// Open together — window provides the InventorySection
player.getPageManager().openCustomPageWithWindows(ref, store, page, window);
```

In the `.ui` file:
```
ItemGrid #InputSlots {
    Anchor: (Width: 64, Height: 64);
    SlotsPerRow: 1;
    InventorySectionId: 0;     // Links to window's inventory section 0
    AreItemsDraggable: true;
    Style: (SlotSize: 56, SlotSpacing: 4);
}
```

### Mechanism B: Display-only with event handling

Use `ItemSlotButton` + `ItemSlot` for display-only slots that fire click events back to the server. The server handles the logic:

```
ItemSlotButton #InputSlotBtn {
    Anchor: (Width: 56, Height: 56);
    ItemSlot #InputSlot {
        Anchor: (Width: 48, Height: 48);
    }
}
```

Server event binding:
```java
evt.addEventBinding(CustomUIEventBindingType.Activating, "#InputSlotBtn",
    EventData.of("Action", "InputSlotClicked"));
```

### How engine benches handle input/output slots

Engine crafting benches (StructuralCraftingWindow, CraftingWindow, etc.) use the **Window system**, NOT the Custom UI Page system. The window JSON data describes categories and recipes, and the client renders a hardcoded bench UI layout. The input/output slots are `InventorySection` objects managed by the `Window`'s `ItemContainerWindow` implementation.

**The Custom UI Page system is separate from the Window system**, but they can be combined via `openCustomPageWithWindows()`.

---

## 4. UICommandBuilder.set() for Item Display

### Setting ItemId on ItemIcon / ItemSlot

```java
// Set item on an ItemIcon element
cmd.set("#MyIcon.ItemId", "Item_Wood_Planks");

// Set item on an ItemSlot element
cmd.set("#MySlot.ItemId", "Item_Wood_Planks");
cmd.set("#MySlot.Quantity", 5);

// Set item on an ItemPreviewComponent element
cmd.set("#My3DPreview.ItemId", "Item_Iron_Sword");
cmd.set("#My3DPreview.ItemScale", 1.5f);
```

### Setting slots on ItemGrid via setObject/set array

```java
// Set ItemGridSlot array on an ItemGrid's Slots property
ItemGridSlot[] slots = new ItemGridSlot[]{
    new ItemGridSlot(new ItemStack("Item_Wood_Planks", 4))
        .setName("Oak Wood Planks")
        .setActivatable(true),
    new ItemGridSlot(new ItemStack("Item_Stone_Brick", 2))
        .setName("Stone Bricks")
};
cmd.set("#MyGrid.Slots", slots);
```

### What cmd.set() supports for item display

| Selector | Value Type | Effect |
|----------|-----------|--------|
| `#Icon.ItemId` | String | Sets item ID on `ItemIcon` element |
| `#Slot.ItemId` | String | Sets item ID on `ItemSlot` element |
| `#Slot.Quantity` | int | Sets quantity on `ItemSlot` |
| `#Preview.ItemId` | String | Sets item ID on `ItemPreviewComponent` |
| `#Preview.ItemScale` | float | Sets 3D preview scale |
| `#Grid.Slots` | ItemGridSlot[] | Sets all slots on `ItemGrid` |

### Sprite and AssetImage — NOT for item icons

`Sprite.TexturePath` and `AssetImage.AssetPath` reference image file paths, NOT item IDs. You cannot set an item icon on these elements.

---

## 5. Server-Side Item Manipulation Without UI Slots

### Can we programmatically find items in the player's inventory?

**YES.** The `Player` component provides full inventory access:

```java
// Get player component
Player player = store.getComponent(ref, Player.getComponentType());

// Get combined inventory (hotbar + backpack)
ItemContainer inventory = player.getInventory().getCombinedHotbarFirst();

// Get item in hand
ItemStack inHand = player.getInventory().getItemInHand();

// Iterate all slots
for (int i = 0; i < inventory.getSize(); i++) {
    ItemStack stack = inventory.getItemStack(i);
    if (stack != null && isPlaceBlockItem(stack)) {
        // Found a placeholder item — arm it
        armPlaceholder(stack, selectedRecipe);
    }
}
```

### Can we arm an item without a UI slot?

**YES.** You can find items in the player's inventory, modify their metadata, and invalidate the container to push updates to the client. No UI slot interaction required.

Pattern:
```java
// In handleDataEvent() when player selects a recipe:
Ref<EntityStore> ref = playerRef.getReference();
Player player = ref.getStore().getComponent(ref, Player.getComponentType());
ItemStack inHand = player.getInventory().getItemInHand();

if (inHand != null && isPlaceBlockItem(inHand)) {
    // Arm the placeholder with the selected recipe
    PlaceBlockMetadata.setArmedRecipeId(inHand, selectedRecipeId);
    // Invalidate to push changes to client
    player.getInventory().invalidate();
}
```

---

## 6. Recommended Approach for Blueprint Bench

For the Blueprint Bench recipe selection UI, the recommended approach is:

### In the `.ui` file — add item icons to recipe entries

```
// RecipeEntryWithIcon.ui — recipe list entry with item icon
ItemSlotButton {
    Anchor: (Height: 40);
    LayoutMode: Left;
    Padding: (Left: 8, Right: 12);

    ItemIcon #Icon {
        Anchor: (Width: 32, Height: 32);
    }

    Group { Anchor: (Width: 8); }   // spacer

    Label #Name {
        Text: "";
        FlexWeight: 1;
        Style: @DefaultStyle;
    }
}
```

### From the server — set item IDs dynamically

```java
// For each recipe in the list:
cmd.append("#RecipeList", "Pages/BlueprintBench/RecipeEntryWithIcon.ui");
int idx = recipeIndex;
cmd.set("#RecipeList[" + idx + "] #Icon.ItemId", recipe.getPrimaryOutput().getItemId());
cmd.set("#RecipeList[" + idx + "] #Name.Text", recipe.getDisplayName());

evt.addEventBinding(CustomUIEventBindingType.Activating,
    "#RecipeList[" + idx + "]",
    EventData.of("RecipeId", recipe.getId()).append("Action", "Select"));
```

### For the detail panel — show 3D preview of selected recipe output

```java
// When a recipe is selected, update the detail panel:
cmd.set("#Preview3D.ItemId", selectedRecipe.getPrimaryOutput().getItemId());
cmd.set("#OutputName.Text", selectedRecipe.getDisplayName());
cmd.set("#CostSummary.Text", formatCostSummary(selectedRecipe));
```

---

## See Also

- [UI File System](./ui-file-system.md) — Complete .ui file format reference
- [Custom UI Options](./custom-ui-options.md) — Comparison of UI approaches
- [API Reference: InteractiveCustomUIPage](./api-reference-interactive-custom-ui.md) — Server-side API
- [Official Type Documentation](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation) — All element types
