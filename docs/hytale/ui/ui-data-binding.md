---
topic: "UI Data Binding — Events, Value Capture, and Codec Patterns"
category: "Plugin API / Custom UI"
updated: 2026-04-28
sources:
  - "https://hytale-docs.com/docs/api/server-internals/custom-ui (Event Data Codec, UIEventBuilder)"
  - "https://hytale-docs.com/docs/api/server-internals/ui-reference (Java API Reference)"
  - "docs/hytale/plugins/api-reference-interactive-custom-ui.md"
  - "docs/hytale/plugins/custom-ui-for-Stencil-Book.md"
  - "decompiled CustomUIEventBindingType.java"
  - "decompiled EventData.java"
  - "decompiled UICommandBuilder.java"
  - "decompiled UIEventBuilder.java"
  - "decompiled InteractiveCustomUIPage.java"
  - "decompiled CommandListPage.java, EntitySpawnPage.java, BarterPage.java"
---

# UI Data Binding — Events, Value Capture, and Codec Patterns

## Summary

Hytale's Custom UI system uses a unidirectional data flow: the **server** pushes state to the client via `UICommandBuilder` commands, and the **client** sends interaction events back via `UIEventBuilder` bindings. There is no automatic two-way data binding — all state synchronization is explicit and server-driven.

```
Server                                Client
  │                                      │
  │──── cmd.set("#Label.Text", "Hi") ───▶│  Server pushes values
  │                                      │
  │◀── evt(Activating, "#Btn", data) ────│  Client sends events
  │                                      │
  │──── sendUpdate(cmd) ────────────────▶│  Server responds with updates
```

---

## UICommandBuilder — Pushing Data to Client

### Methods

| Method | Purpose | Example |
|--------|---------|---------|
| `append(path)` | Load a `.ui` document as root | `cmd.append("Pages/MyPage.ui")` |
| `append(selector, path)` | Append template to container | `cmd.append("#List", "Pages/ListItem.ui")` |
| `appendInline(selector, doc)` | Append inline UI markup | `cmd.appendInline("#List", "Label { Text: \"Hi\"; }")` |
| `insertBefore(selector, path)` | Insert template before element | `cmd.insertBefore("#Footer", "Pages/Ad.ui")` |
| `insertBeforeInline(selector, doc)` | Insert inline before element | — |
| `set(selector, String)` | Set text property | `cmd.set("#Title.Text", "Hello")` |
| `set(selector, boolean)` | Set boolean property | `cmd.set("#Panel.Visible", false)` |
| `set(selector, int)` | Set integer property | `cmd.set("#Count.Text", 42)` |
| `set(selector, float)` | Set float property | `cmd.set("#Slider.Value", 0.5f)` |
| `set(selector, double)` | Set double property | — |
| `set(selector, Message)` | Set localized message | `cmd.set("#Title.Text", Message.raw("Hi"))` |
| `set(selector, Value<T>)` | Set document reference | `cmd.set("#Btn.Style", Value.ref("Common.ui", "DefaultButtonStyle"))` |
| `setNull(selector)` | Set property to null | `cmd.setNull("#Icon.ItemId")` |
| `setObject(selector, Object)` | Set complex object | `cmd.setObject("#Grid.Slots", slots)` |
| `set(selector, T[])` | Set array | `cmd.set("#Grid.Slots", slotArray)` |
| `set(selector, List<T>)` | Set list | — |
| `clear(selector)` | Remove all children | `cmd.clear("#ItemList")` |
| `remove(selector)` | Remove specific element | `cmd.remove("#OldElement")` |

### Supported Object Types for `setObject()`

| Type | Description |
|------|-------------|
| `Area` | Rectangle area |
| `ItemGridSlot` | Inventory slot data (item, quantity, background, overlays) |
| `ItemStack` | Item with amount/quality |
| `LocalizableString` | Translated string |
| `PatchStyle` | 9-slice background |
| `DropdownEntryInfo` | Dropdown option |
| `Anchor` | Size/position constraints |

### Value References

Cross-template style/value references without embedding the actual data:

```java
Value.ref("Common/TextButton.ui", "LabelStyle")         // Named style from template
Value.ref("Common.ui", "DefaultTextButtonStyle")          // Style from Common.ui
Value.ref("Pages/BasicTextButton.ui", "SelectedLabelStyle")
Value.of("Hello")                                          // Direct value
```

---

## UIEventBuilder — Receiving Data from Client

### Event Binding

```java
evt.addEventBinding(
    CustomUIEventBindingType.Activating,  // Event type
    "#MyButton",                          // UI element selector
    new EventData()                        // Data to send with event
        .append("Action", "click")
        .append("@Value", "#Input.Value"), // @ prefix captures live value
    false                                  // locksInterface (blocks further input)
);
```

### The `@` Prefix — Value Capture

The `@` prefix in `EventData` keys is the core mechanism for reading UI state. When an event fires, `@`-prefixed fields pull the current value from the referenced UI element:

```java
// Static data — always sends "click"
new EventData().append("Action", "click")

// Dynamic capture — pulls live value from #SearchInput.Value at event time
new EventData().append("@SearchQuery", "#SearchInput.Value")

// Combined — static action + captured value
new EventData()
    .append("Action", "submit")
    .append("@Username", "#UsernameInput.Value")
    .append("@Amount", "#AmountField.Value")
```

**How it works:**
1. Server binds event with `@FieldName` → `#Element.Property`
2. Player interacts (clicks button, changes value)
3. Client reads the live value from the referenced element
4. Client sends the captured value in the event data
5. Server deserializes into the typed event data class

### All 24 Event Types

| Type | Ordinal | Fires When |
|------|---------|------------|
| `Activating` | 0 | Click or Enter key |
| `RightClicking` | 1 | Right mouse click |
| `DoubleClicking` | 2 | Double click |
| `MouseEntered` | 3 | Mouse enters element bounds |
| `MouseExited` | 4 | Mouse leaves element bounds |
| `ValueChanged` | 5 | Input/slider/dropdown value changes |
| `ElementReordered` | 6 | Element was reordered (drag) |
| `Validating` | 7 | Form validation trigger |
| `Dismissing` | 8 | Page being dismissed |
| `FocusGained` | 9 | Element gained keyboard focus |
| `FocusLost` | 10 | Element lost keyboard focus |
| `KeyDown` | 11 | Key pressed while focused |
| `MouseButtonReleased` | 12 | Mouse button released |
| `SlotClicking` | 13 | Inventory slot clicked |
| `SlotDoubleClicking` | 14 | Inventory slot double-clicked |
| `SlotMouseEntered` | 15 | Mouse entered inventory slot |
| `SlotMouseExited` | 16 | Mouse exited inventory slot |
| `DragCancelled` | 17 | Drag operation cancelled |
| `Dropped` | 18 | Item dropped |
| `SlotMouseDragCompleted` | 19 | Slot drag completed |
| `SlotMouseDragExited` | 20 | Slot drag exited bounds |
| `SlotClickReleaseWhileDragging` | 21 | Click released during drag |
| `SlotClickPressWhileDragging` | 22 | Click pressed during drag |
| `SelectedTabChanged` | 23 | Tab selection changed |

### Common Event Patterns

```java
// Button click
evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBtn",
    EventData.of("Action", "save"), false);

// Search input change (captures value on every keystroke)
evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
    EventData.of("@SearchQuery", "#SearchInput.Value"), false);

// Tab change
evt.addEventBinding(CustomUIEventBindingType.SelectedTabChanged, "#BenchTabs",
    EventData.of("@Tab", "#BenchTabs.SelectedTab"), false);

// Dropdown change
evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#FilterDropdown",
    EventData.of("@Filter", "#FilterDropdown.Value"), false);

// Dynamic list item click (indexed selector)
for (int i = 0; i < items.size(); i++) {
    evt.addEventBinding(CustomUIEventBindingType.Activating,
        "#RecipeList[" + i + "]",
        new EventData()
            .append("Action", "select")
            .append("RecipeId", items.get(i).getId()),
        false);
}
```

---

## Event Data Codec — Deserializing Events

### Defining the Codec

```java
public static class MyEventData {
    public static final BuilderCodec<MyEventData> CODEC = BuilderCodec.builder(
            MyEventData.class, MyEventData::new
    )
    .append(new KeyedCodec<>("Action", Codec.STRING),
        (e, v) -> e.action = v, e -> e.action)
    .add()
    .append(new KeyedCodec<>("RecipeId", Codec.STRING),
        (e, v) -> e.recipeId = v, e -> e.recipeId)
    .add()
    .append(new KeyedCodec<>("SearchQuery", Codec.STRING),
        (e, v) -> e.searchQuery = v, e -> e.searchQuery)
    .add()
    .build();

    private String action;
    private String recipeId;
    private String searchQuery;

    public MyEventData() {}  // Required no-arg constructor
}
```

### Key Rules

1. **Field names in the codec must match the keys in `EventData.append()`** — `"Action"` in codec = `"Action"` in event data.
2. **`@` prefix is stripped** — `EventData.append("@SearchQuery", ...)` maps to codec key `"SearchQuery"`.
3. **No-arg constructor required** — `BuilderCodec` uses reflection.
4. **Fields not present in an event remain `null`** — multiple event bindings can share one codec; handle missing fields gracefully.

### Available Codec Types

| Codec | Java Type |
|-------|-----------|
| `Codec.STRING` | `String` |
| `Codec.INT` | `Integer` |
| `Codec.LONG` | `Long` |
| `Codec.FLOAT` | `Float` |
| `Codec.DOUBLE` | `Double` |
| `Codec.BOOL` | `Boolean` |

### Using Records (Engine Pattern)

The engine uses Java records for conciseness:

```java
public record CommandListPageEventData(
    String searchQuery, String command, String navigateUp,
    String subcommand, String variantIndex, String sendToChat
) {
    public static final BuilderCodec<CommandListPageEventData> CODEC = ...;
}
```

---

## Handling Events

```java
@Override
public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull MyEventData data
) {
    if ("select".equals(data.action)) {
        handleRecipeSelect(ref, store, data.recipeId);
    } else if ("search".equals(data.action)) {
        handleSearch(data.searchQuery);
    } else if ("close".equals(data.action)) {
        this.close();
    }
}
```

### Sending Updates After Events

```java
private void handleRecipeSelect(Ref<EntityStore> ref, Store<EntityStore> store, String recipeId) {
    UICommandBuilder cmd = new UICommandBuilder();
    cmd.set("#OutputName.Text", getRecipeName(recipeId));
    cmd.set("#OutputIcon.ItemId", getOutputItemId(recipeId));

    // Option 1: Update only (no new event bindings)
    this.sendUpdate(cmd, false);

    // Option 2: Update with new event bindings
    UIEventBuilder evt = new UIEventBuilder();
    evt.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmBtn",
        EventData.of("Action", "confirm"), false);
    this.sendUpdate(cmd, evt, false);  // false = don't clear existing bindings
}
```

### `sendUpdate()` Parameters

| Signature | Description |
|-----------|-------------|
| `sendUpdate(cmd, clear)` | Send UI commands; `clear=true` removes existing event bindings |
| `sendUpdate(cmd, evt, clear)` | Send commands + new event bindings |

### Rebuild vs Update

| Method | When to Use |
|--------|-------------|
| `this.sendUpdate(cmd, false)` | Incremental updates — change specific properties |
| `this.rebuild()` | Full rebuild — calls `build()` again from scratch |

> **Performance:** Avoid `rebuild()` when possible. Use targeted `set()` calls for responsive UI. `rebuild()` forces a full document re-parse on the client.

---

## Dynamic List Pattern

### Complete Example

**Template file (`Pages/StencilBook/RecipeEntry.ui`):**
```
$C = "../../Common.ui";

$C.@SecondaryTextButton {
    @Text = "Recipe";
    Anchor: (Height: 40);
}
```

**Build method:**
```java
@Override
public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                  UIEventBuilder evt, Store<EntityStore> store) {
    cmd.append(LAYOUT);

    List<Recipe> recipes = getFilteredRecipes();
    for (int i = 0; i < recipes.size(); i++) {
        Recipe recipe = recipes.get(i);

        // Append template — creates #RecipeList[i]
        cmd.append("#RecipeList", "Pages/StencilBook/RecipeEntry.ui");

        // Set text on the appended element
        String selector = "#RecipeList[" + i + "]";
        cmd.set(selector + ".Text", recipe.getName());

        // Bind click event
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            selector,
            new EventData()
                .append("Action", "select")
                .append("RecipeId", recipe.getId()),
            false
        );
    }
}
```

### Refreshing a Dynamic List

```java
private void refreshList(UICommandBuilder cmd, UIEventBuilder evt) {
    cmd.clear("#RecipeList");  // Remove all children

    List<Recipe> filtered = applyFilters();
    for (int i = 0; i < filtered.size(); i++) {
        cmd.append("#RecipeList", "Pages/StencilBook/RecipeEntry.ui");
        cmd.set("#RecipeList[" + i + "].Text", filtered.get(i).getName());
        evt.addEventBinding(CustomUIEventBindingType.Activating,
            "#RecipeList[" + i + "]",
            new EventData().append("Action", "select")
                          .append("RecipeId", filtered.get(i).getId()),
            false);
    }

    this.sendUpdate(cmd, evt, true);  // true = clear old bindings
}
```

---

## Thread Safety

> **Warning:** `handleDataEvent()` often runs on **network threads**, NOT the World Tick thread.
>
> - **Safe:** Reading event data fields, sending notifications, calling `sendUpdate()`, `close()`, `rebuild()`
> - **Unsafe:** Accessing `store.getComponent()` unless you're certain you're on the World Tick thread
> - **For player stats:** Use `playerRef.getComponent(EntityStatMap.getComponentType())` — safe from any thread
> - **For world mutations:** Use `world.execute(() -> { ... })` to defer to the World Tick thread

---

## ItemGridSlot — Inventory Display Binding

For `ItemGrid` elements, use `ItemGridSlot` arrays:

```java
ItemGridSlot[] slots = new ItemGridSlot[ingredients.size()];
for (int i = 0; i < ingredients.size(); i++) {
    ItemGridSlot slot = new ItemGridSlot();
    slot.setItemStack(ingredients.get(i).toItemStack());
    // Optional: custom background, overlay, tooltip
    slot.setBackground(Value.ref("Common.ui", "SlotBackground"));
    slot.name = "Ingredient " + (i + 1);
    slot.isActivatable = false;
    slots[i] = slot;
}
cmd.setObject("#CostGrid.Slots", slots);
```

### ItemGridSlot Properties

| Property | Type | Description |
|----------|------|-------------|
| `setItemStack(ItemStack)` | — | Set the displayed item |
| `setBackground(Value<PatchStyle>)` | — | Custom slot background |
| `setOverlay(Value<PatchStyle>)` | — | Custom overlay |
| `setIcon(Value<PatchStyle>)` | — | Custom icon |
| `name` | String | Custom tooltip name |
| `description` | String | Custom tooltip description |
| `isActivatable` | boolean | Whether slot fires click events |
| `isItemUncraftable` | boolean | Visual "uncraftable" indicator |
| `isItemIncompatible` | boolean | Visual "incompatible" indicator |
| `skipItemQualityBackground` | boolean | Skip quality-based background color |

---

## Required Imports

```java
// Core UI
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;

// Codec
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

// ECS
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

// Player & Commands
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.Message;

// Notifications
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
```

---

## See Also

- [Custom UI Overview](./custom-ui-overview.md) — Architecture and lifecycle
- [UI Element Reference](./ui-element-reference.md) — All element types and properties
- [CommonUI Library Reference](./ui-commonui-library.md) — Reusable components
