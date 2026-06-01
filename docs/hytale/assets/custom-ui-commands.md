---
topic: "CustomUI Command System — Selectors, AppendInline, and Dynamic Lists"
category: "UI / Custom Pages"
updated: 2026-04-27
sources: ["decompiled UICommandBuilder.java", "decompiled CustomUICommandType.java", "decompiled CustomUICommand.java", "decompiled CommandListPage.java", "decompiled WarpListPage.java", "decompiled PluginListPage.java", "decompiled RespawnPage.java", "decompiled BarterPage.java", "decompiled EntitySpawnPage.java", "decompiled ItemGridSlot.java"]
---

# CustomUI Command System — Selectors, AppendInline, and Dynamic Lists

## Summary

The CustomUI command system uses 7 command types to manipulate UI elements: `Append`, `AppendInline`, `InsertBefore`, `InsertBeforeInline`, `Remove`, `Set`, and `Clear`. The `[index]` selector syntax works with `Set`, `Append`, event bindings, and other commands that resolve existing elements — but **NOT with `AppendInline`**. `appendInline` only accepts a simple `#Id` selector pointing to an existing named container, never an indexed child.

## Command Types (CustomUICommandType enum)

```java
// From: CustomUICommandType.java
Append(0),        // Load a .ui file and append its root element to a container
AppendInline(1),  // Parse inline markup and append the result to a container
InsertBefore(2),  // Load a .ui file and insert before a named element
InsertBeforeInline(3), // Parse inline markup and insert before a named element
Remove(4),        // Remove an element by selector
Set(5),           // Set a property value on an element
Clear(6);         // Remove all children of a container
```

## UICommandBuilder API

```java
// From: UICommandBuilder.java (lines 37-75)
public UICommandBuilder clear(String selector)                          // Clear all children
public UICommandBuilder remove(String selector)                         // Remove specific element
public UICommandBuilder append(String documentPath)                     // Append .ui file to root
public UICommandBuilder append(String selector, String documentPath)    // Append .ui file to container
public UICommandBuilder appendInline(String selector, String document)  // Append inline markup to container
public UICommandBuilder insertBefore(String selector, String documentPath)
public UICommandBuilder insertBeforeInline(String selector, String document)
public UICommandBuilder set(String selector, ...)                       // Set property (many overloads)
```

## Selector Syntax

### Simple `#Id` selector
Targets an element by its declared Id in the .ui file.
```
#RecipeGrid
#SubcommandCards
#WarpList
#PluginList
```

### Indexed `#Id[n]` selector
Targets the nth child of a container. **Zero-based.**
```
#SubcommandCards[0]     — first child of #SubcommandCards
#DroppedItemsContainer[2] — third child of #DroppedItemsContainer
```

### Nested index `#Id[n][m]` selector
Targets the mth child of the nth child.
```
#SubcommandCards[0][2]  — third child of first child of #SubcommandCards
```

### Descendant `#Parent[n] #ChildId.Property` selector
Targets a named element inside an indexed child, then accesses its property.
```
#DroppedItemsContainer[0] #ItemIcon.ItemId
#PluginList[3] #Button.Text
#SubcommandCards[0][1] #SubcommandName.TextSpans
#TradeGrid[0] #OutputSlot.ItemId
```

### Property access `.Property`
```
#RespawnButton.Disabled
#SearchInput.Value
#SelectedName.Text
```

## CRITICAL: Which commands accept which selectors

| Selector Form          | `set` | `append` | `appendInline` | `clear` | `remove` | Event bindings |
|------------------------|-------|----------|----------------|---------|----------|----------------|
| `#Id`                  | ✅    | ✅       | ✅             | ✅      | ✅       | ✅             |
| `#Id.Property`         | ✅    | N/A      | N/A            | N/A     | N/A      | N/A            |
| `#Id[n]`               | ✅    | ✅       | ❌ CRASH       | ?       | ?        | ✅             |
| `#Id[n].Property`      | ✅    | N/A      | N/A            | N/A     | N/A      | N/A            |
| `#Id[n] #Child.Prop`   | ✅    | N/A      | N/A            | N/A     | N/A      | N/A            |
| `#Id[n][m]`            | ✅    | N/A      | N/A            | N/A     | N/A      | ✅             |

### Why `appendInline` crashes with `[index]`

The client-side parser for `AppendInline` commands requires the selector to resolve to a **named document element** — it uses a different code path than `Set` or `Append`. The `Set` command can resolve indexed children because it walks the DOM after construction. `AppendInline` needs to parse the inline markup AND find the parent container in a single pass, and the `[index]` resolution is not implemented in that parse path.

**Error message**: `"Failed to parse or resolve document from Custom UI Appendline Command. Selector: #RecipeGrid[0]"`

This confirms: the client's `AppendInline` handler does NOT support `[index]` selectors.

## Proven Patterns from Engine Code

### Pattern 1: `appendInline` to a simple `#Id` container (CommandListPage)

```java
// CommandListPage.java lines 388-408
// Step 1: appendInline creates a Group as child of #SubcommandCards (simple #Id)
commandBuilder.appendInline("#SubcommandCards", "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");

// Step 2: append a .ui file INTO that newly created child via [index]
commandBuilder.append("#SubcommandCards[" + rowIndex + "]", "Pages/SubcommandCard.ui");

// Step 3: set properties on children within the appended .ui via [row][col] #ChildId.Property
commandBuilder.set("#SubcommandCards[" + rowIndex + "][" + cardsInCurrentRow + "] #SubcommandName.TextSpans", ...);
```

**Key insight**: `appendInline` targets `#SubcommandCards` (simple Id). Then `append` (file-based) targets `#SubcommandCards[0]` (indexed child). `set` targets deep nested selectors.

### Pattern 2: `append` + `[index]` + `#ChildId.Property` (RespawnPage)

```java
// RespawnPage.java lines 105-115
for (int i = 0; i < this.itemsLostOnDeath.length; i++) {
    ItemStack itemStack = this.itemsLostOnDeath[i];
    String itemSelector = "#DroppedItemsContainer[" + i + "] ";
    
    commandBuilder.append("#DroppedItemsContainer", "Pages/DroppedItemSlot.ui");
    commandBuilder.set(itemSelector + "#ItemIcon.ItemId", itemStack.getItemId());
    commandBuilder.set(itemSelector + "#ItemIcon.Quantity", itemStack.getQuantity());
    if (itemStack.getQuantity() > 1) {
        commandBuilder.set(itemSelector + "#QuantityLabel.Text", String.valueOf(itemStack.getQuantity()));
    }
}
```

**Key insight**: The `DroppedItemSlot.ui` template contains an element with `#ItemIcon` that has `.ItemId` and `.Quantity` properties. The server addresses it via `#DroppedItemsContainer[i] #ItemIcon.ItemId`.

### Pattern 3: `append` + `[index]` simple properties (WarpListPage)

```java
// WarpListPage.java lines 49-58
for (int i = 0; i < warps.size(); i++) {
    String selector = "#WarpList[" + i + "]";
    String warp = warps.get(i);
    commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
    commandBuilder.set(selector + " #Name.Text", warp);
    commandBuilder.set(selector + " #World.Text", ...);
    eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, ...);
}
```

### Pattern 4: `append` + `[index]` for item grids (BarterPage)

```java
// BarterPage.java lines 85-109
for (int i = 0; i < trades.length; i++) {
    String selector = "#TradeGrid[" + i + "]";
    commandBuilder.append("#TradeGrid", "Pages/BarterTradeRow.ui");
    commandBuilder.set(selector + " #OutputSlot.ItemId", itemId);     // ItemIcon via #Id.ItemId
    commandBuilder.set(selector + " #OutputQuantity.Text", ...);
    commandBuilder.set(selector + " #InputSlot.ItemId", ...);
    commandBuilder.set(selector + " #InputSlotBorder.Background", ...);
}
```

**Key insight**: `#OutputSlot` and `#InputSlot` are elements in the `.ui` template that have an `.ItemId` property — likely `ItemIcon` elements declared in the `.ui` file with those Ids.

### Pattern 5: ItemGridSlot with `set` for ItemGrid elements (EntitySpawnPage)

```java
// EntitySpawnPage.java lines 273, 512, 518, 529
commandBuilder.set("#ItemMaterialSlot.Slots", new ItemGridSlot[]{new ItemGridSlot()});
commandBuilder.set("#ItemMaterialSlot.Slots", new ItemGridSlot[]{new ItemGridSlot(new ItemStack(itemId, 1))});
```

**Key insight**: `ItemGrid` elements have a `.Slots` property that accepts `ItemGridSlot[]`. This is the proper API for item grids — the server sends the full slot array and the client renders them.

## ItemGridSlot Properties

```java
// ItemGridSlot.java — all available properties
ItemStack itemStack          // The item to display
Value<PatchStyle> background // Background patch style
Value<PatchStyle> overlay    // Overlay patch style  
Value<PatchStyle> icon       // Icon override
boolean isItemIncompatible   // Gray out as incompatible
String name                  // Display name override
String description           // Description override
boolean skipItemQualityBackground  // Skip quality-based background
boolean isActivatable        // Can be clicked
boolean isItemUncraftable    // Show uncraftable indicator
```

## ItemIcon as UI Element

`ItemIcon` exists as a client-side UI element type with at least these properties:
- `.ItemId` (String) — the item to display an icon for
- `.Quantity` (int) — quantity to display

Evidence from RespawnPage:
```java
commandBuilder.set(itemSelector + "#ItemIcon.ItemId", itemStack.getItemId());
commandBuilder.set(itemSelector + "#ItemIcon.Quantity", itemStack.getQuantity());
```

Evidence from BarterPage:
```java
commandBuilder.set(selector + " #OutputSlot.ItemId", ...);
```

These are ItemIcon elements **defined in .ui template files** with specific `#Id` names. The server never creates ItemIcon elements via `appendInline` — they are always pre-defined in templates.

## The Correct Approach for Dynamic Item Grids

### Option A: Put ItemIcon in the .ui template (Recommended)

Create `RecipeIconCell.ui` with an ItemIcon already inside:
```
Group {
    Id: CellRoot;
    ItemIcon {
        Id: CellIcon;
        Anchor: (Full: 4);
    }
}
```

Then in Java:
```java
for (int i = 0; i < showing; i++) {
    String selector = "#RecipeGrid[" + i + "]";
    cmd.append("#RecipeGrid", "Pages/BlueprintBook/RecipeIconCell.ui");
    cmd.set(selector + " #CellIcon.ItemId", entry.outputItemId);
    cmd.set(selector + ".Background", "#2a4a6a");
}
```

### Option B: Use an ItemGrid element with ItemGridSlot[]

If the .ui template contains an `ItemGrid` element:
```java
ItemGridSlot[] slots = new ItemGridSlot[showing];
for (int i = 0; i < showing; i++) {
    slots[i] = new ItemGridSlot(new ItemStack(entry.outputItemId, 1))
        .setBackground(...)
        .setActivatable(true);
}
cmd.set("#RecipeGrid.Slots", slots);
```

### Option C: Use appendInline WITHOUT [index] selectors

If you must use `appendInline`, the inline document must contain all content:
```java
// This works — simple #Id selector
cmd.appendInline("#RecipeGrid", 
    "Group { ItemIcon { Anchor: (Full: 4); ItemId: " + itemId + "; } }");
```

But you CANNOT then use `#RecipeGrid[i]` to address it with `appendInline` again. You CAN use `#RecipeGrid[i]` with `set` afterward.

## Why Your Code Crashes

```java
// CRASH: appendInline does NOT support [index] selectors
cmd.appendInline("#RecipeGrid[0]", "ItemIcon { ... }");
```

The fix: put ItemIcon in the `.ui` template file, then use `set` to configure it via `#RecipeGrid[i] #CellIcon.ItemId`.

## See Also
- [server-client-boundary.md](../server-client-boundary.md)
