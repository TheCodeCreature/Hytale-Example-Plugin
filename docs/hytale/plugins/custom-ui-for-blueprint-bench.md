---
topic: "Custom UI for Blueprint Bench"
category: "Plugin API / Custom UI"
updated: 2026-04-24
sources: ["decompiled OpenBenchPageInteraction.java", "decompiled Bench.java", "decompiled BlockType.java", "decompiled InteractiveCustomUIPage.java", "decompiled OpenCustomUIInteraction.java", "decompiled UICommandBuilder.java", "decompiled UIEventBuilder.java", "decompiled PortalDeviceSummonPage.java", "decompiled CommandListPage.java", "decompiled EntitySpawnPage.java"]
---

# Custom UI for Blueprint Bench — Engine Research

## Question 1: Can we prevent StructuralCraftingWindow from auto-opening?

### Answer: YES — two viable approaches

#### How the engine opens StructuralCraftingWindow

The chain is:

1. **BlockType asset decode** (`BlockType.java:1853-1860`): When a block has a `Bench` config but no explicit `Interactions.Use`, the engine auto-injects the bench's root interaction:
   ```java
   if (this.bench != null && !this.interactions.containsKey(InteractionType.Use)) {
       RootInteraction rootInteraction = this.bench.getRootInteraction();
       if (rootInteraction != null) {
           interactions.put(InteractionType.Use, rootInteraction.getId());
       }
   }
   ```

2. **Bench.getRootInteraction()** (`Bench.java:203`): Looks up from the static `BENCH_INTERACTIONS` map keyed by `BenchType`.

3. **CraftingPlugin.setup()** (`CraftingPlugin.java:128-130`): Registers the root interactions:
   ```java
   Bench.registerRootInteraction(BenchType.Crafting, OpenBenchPageInteraction.SIMPLE_CRAFTING_ROOT);
   Bench.registerRootInteraction(BenchType.DiagramCrafting, OpenBenchPageInteraction.DIAGRAM_CRAFTING_ROOT);
   Bench.registerRootInteraction(BenchType.StructuralCrafting, OpenBenchPageInteraction.STRUCTURAL_CRAFTING_ROOT);
   ```

4. **OpenBenchPageInteraction.interactWithBlock()** (`OpenBenchPageInteraction.java:88-93`): Switches on `PageType` and creates `new StructuralCraftingWindow(benchState)` for `STRUCTURAL_CRAFTING`.

#### Approach A: Override the block's `Interactions.Use` in the asset JSON

The critical code in `BlockType.java:1853` shows:
```java
if (this.bench != null && !this.interactions.containsKey(InteractionType.Use)) {
```

This means **if the block JSON already defines an `Interactions.Use` entry, the bench's root interaction is NOT injected**. The engine only auto-injects the bench interaction as a fallback.

**Solution:** In the Blueprint Bench's block JSON, explicitly set:
```json
{
  "Interactions": {
    "Use": "MyPlugin_BlueprintBenchUI"
  }
}
```
Where `MyPlugin_BlueprintBenchUI` is a `RootInteraction` you register that opens an `OpenCustomUIInteraction` with your custom page supplier.

This completely prevents StructuralCraftingWindow from ever opening.

#### Approach B: Remove the Bench config entirely, use a plain interactive block

Since you don't need the engine crafting system at all, you could remove the `Bench` config from the block type entirely and just define the `Interactions.Use` to open your custom UI. This is the cleanest approach if no crafting system features are needed.

#### Approach C (fragile): Re-register the BenchType interaction at runtime

`Bench.registerRootInteraction()` is a simple `put()` into an `EnumMap`:
```java
public static void registerRootInteraction(BenchType benchType, RootInteraction interaction) {
    BENCH_INTERACTIONS.put(benchType, interaction);
}
```

**Warning:** This is `@Deprecated(forRemoval = true)` and would affect ALL StructuralCrafting benches globally, not just the Blueprint Bench.

### Recommendation

**Approach A** is the best. Keep the `Bench` config (for category metadata, tier levels, etc. if needed later) but override `Interactions.Use` to point to your custom interaction. This is exactly how the engine's interaction system is designed to work — the bench interaction is just a default fallback.

---

## Question 2: Can plugins use InteractiveCustomUIPage to render UI?

### Answer: YES — full server-driven UI system available

#### Architecture

```
CustomUIPage (abstract base)
  └── InteractiveCustomUIPage<T> (adds typed event data handling)
        └── YourPage extends InteractiveCustomUIPage<YourEventData>
```

- **`CustomUIPage`** (`CustomUIPage.java`): Abstract base. Has `build()`, `rebuild()`, `sendUpdate()`, `close()`, `onDismiss()`. The `build()` method receives `UICommandBuilder` and `UIEventBuilder`.
- **`InteractiveCustomUIPage<T>`** (`InteractiveCustomUIPage.java`): Adds typed event handling via a `BuilderCodec<T>`. Client events are deserialized into your typed `T` data class. Also adds `sendUpdate(commandBuilder, eventBuilder, clear)`.

#### Available UI Commands (UICommandBuilder)

| Method | Purpose |
|--------|---------|
| `append(documentPath)` | Load a `.ui` template file as page root |
| `append(selector, documentPath)` | Append a `.ui` template as child of selector |
| `appendInline(selector, document)` | Append inline UI markup string |
| `insertBefore(selector, documentPath)` | Insert before a selector |
| `insertBeforeInline(selector, document)` | Insert inline markup before selector |
| `set(selector, value)` | Set property — supports `String`, `boolean`, `float`, `int`, `double`, `Message`, `Value<T>` |
| `setObject(selector, data)` | Set complex object — supports `Area`, `ItemGridSlot`, `ItemStack`, `LocalizableString`, `PatchStyle`, `DropdownEntryInfo`, `Anchor` |
| `set(selector, T[])` / `set(selector, List<T>)` | Set arrays of supported types |
| `setNull(selector)` | Set null value |
| `clear(selector)` | Clear children of selector |
| `remove(selector)` | Remove element |

#### Available Event Types (CustomUIEventBindingType)

| Event | Description |
|-------|-------------|
| `Activating` | Button click / activation |
| `RightClicking` | Right-click |
| `DoubleClicking` | Double-click |
| `MouseEntered` / `MouseExited` | Hover events |
| `ValueChanged` | Input field value change (text, slider, dropdown) |
| `ElementReordered` | Drag reorder |
| `Validating` | Input validation |
| `Dismissing` | Page dismiss |
| `FocusGained` / `FocusLost` | Focus events |
| `KeyDown` | Keyboard input |
| `MouseButtonReleased` | Mouse release |
| `SlotClicking` / `SlotDoubleClicking` | Item grid slot interactions |
| `SlotMouseEntered` / `SlotMouseExited` | Item grid slot hover |
| `DragCancelled` / `Dropped` | Drag and drop |
| `SlotMouseDragCompleted` / `SlotMouseDragExited` | Slot drag operations |
| `SlotClickReleaseWhileDragging` / `SlotClickPressWhileDragging` | Click during drag |
| `SelectedTabChanged` | Tab selection |

#### Page Lifetimes (CustomPageLifetime)

| Value | Behavior |
|-------|----------|
| `CantClose` | Player cannot close the page |
| `CanDismiss` | Player can press ESC/dismiss |
| `CanDismissOrCloseThroughInteraction` | Can dismiss or auto-closes on new interaction |

#### Item Display

`ItemGridSlot` supports:
- `setItemStack(ItemStack)` — display an item with its icon
- `setBackground(Value<PatchStyle>)` — custom background
- `setOverlay(Value<PatchStyle>)` — custom overlay
- `setIcon(Value<PatchStyle>)` — custom icon
- `name` / `description` — custom tooltip text
- `isActivatable` — whether the slot can be clicked
- `isItemUncraftable` — visual indicator
- `isItemIncompatible` — visual indicator
- `skipItemQualityBackground` — skip quality-based background

#### Concrete Engine Examples

1. **CommandListPage** — Search input, scrollable list, button selection, breadcrumb navigation, send-to-chat action
2. **EntitySpawnPage** — Tabbed interface (NPC/Items/Model tabs), search input, scrollable lists, sliders, item preview with `ItemGridSlot`
3. **PortalDeviceSummonPage** — Artwork display, bullet lists, pill tags, hover effects, activation button
4. **WarpListPage** — Simple list with buttons
5. **LaunchPadSettingsPage** — Settings form
6. **PrefabEditorSaveSettingsPage** — File browser, text input, dropdowns

#### Event Data Pattern

Define a typed event data class with a `BuilderCodec`:
```java
// From CommandListPage:
public record CommandListPageEventData(
    String searchQuery, String command, String navigateUp,
    String subcommand, String variantIndex, String sendToChat
) {
    public static final BuilderCodec<CommandListPageEventData> CODEC = ...;
}
```

Event bindings map UI element events to data fields:
```java
// Search input → sends value as @SearchQuery
eventBuilder.addEventBinding(
    CustomUIEventBindingType.ValueChanged, "#SearchInput",
    EventData.of("@SearchQuery", "#SearchInput.Value"), false
);
// Button click → sends static data
eventBuilder.addEventBinding(
    CustomUIEventBindingType.Activating, "#SummonButton",
    EventData.of("Action", "SummonActivated"), false
);
```

The `@` prefix in `EventData` keys pulls the live value from the UI element (e.g., `#SearchInput.Value`).

---

## Question 3: Can plugins ship custom `.ui` template files?

### Answer: YES — confirmed by community example

> **CORRECTION (2026-04-25):** This answer was previously "UNLIKELY". Community evidence from TroubleDEV's [hytale-basic-uis](https://github.com/trouble-dev/hytale-basic-uis) repository confirms plugins CAN ship custom `.ui` files. See [ui-file-system.md](./ui-file-system.md) for the complete reference.

#### How `.ui` files are referenced

All engine code references `.ui` files by relative path:
- `"Pages/CommandListPage.ui"` — via `commandBuilder.append("Pages/CommandListPage.ui")`
- `"Pages/EntitySpawnPage.ui"` — via `commandBuilder.append("Pages/EntitySpawnPage.ui")`
- `"Common/TextButton.ui"` — reusable component
- `"Pages/Portals/Pill.ui"` — sub-component

These are resolved **on the client side** from asset bundles — both the base game AND mod/plugin asset packs.

#### Where plugin `.ui` files go

Place custom `.ui` files in `src/main/resources/Common/UI/Custom/Pages/` and set `"IncludesAssetPack": true` in `manifest.json`. The path passed to `commandBuilder.append()` is relative to `Common/UI/Custom/`.

#### `appendInline()` as the alternative

`appendInline(selector, document)` lets you write UI markup as inline strings:
```java
commandBuilder.appendInline("#ElementList",
    "Label { Text: %customUI.itemRepairPage.noItems; Style: (Alignment: Center); }");

commandBuilder.appendInline("#SubcommandCards",
    "Group { LayoutMode: Left; Anchor: (Bottom: 0); }");
```

This is Hytale's UI markup language (similar to their `.ui` file format) but embedded as a string. The client parses it on the fly.

#### Hybrid approach (recommended)

The engine examples show a clear pattern:
1. **Use existing `.ui` templates for structure** — templates like `"Common/TextButton.ui"`, `"Pages/BasicTextButton.ui"` already exist and provide styled buttons, lists, etc.
2. **Use `appendInline()` for custom elements** — add labels, groups, custom layout
3. **Use `set()` for dynamic data** — populate text, items, visibility, colors

For a recipe browser, you could:
- Use `"Common/TextButton.ui"` for recipe list items
- Use `ItemGridSlot` arrays for recipe output display
- Use `appendInline()` for search bars, category headers, custom layout
- Bind `ValueChanged` events for search, `Activating` for recipe selection

#### Value.ref() for cross-template style references

```java
Value.ref("Common/TextButton.ui", "LabelStyle")       // Reference a named style from a template
Value.ref("Common.ui", "DefaultTextButtonStyle")       // Reference from common styles
Value.ref("Pages/BasicTextButton.ui", "SelectedLabelStyle")
```

This lets you reuse existing client-side styles in your dynamically-built UI.

---

## Bonus: What if we remove the Bench config entirely?

### Answer: SAFE — since we don't need the engine crafting system

If you change the Blueprint Bench from a `StructuralCrafting` bench to a plain interactive block:

**What you lose:**
- Engine-managed `BenchState` (tier levels, upgrade requirements, the auto-generated `BenchWindow`)
- Engine recipe resolution via `CraftingPlugin.getBenchRecipes(benchType, benchId)`
- The `BenchBlock` component for automatic chest scanning
- Client-side crafting UI (StructuralCraftingWindow with category tabs, recipe grid, etc.)

**What you keep:**
- The block exists in the world and can be interacted with
- You can open a custom UI page via `OpenCustomUIInteraction`
- Full control over the interaction flow

**Since your design doc states:**
> "We only need the bench as an interaction trigger — we don't need the engine's crafting system at all since we arm the placeholder via command/custom UI"

**Removing the `Bench` config is safe and recommended.** The block becomes a plain interactive block with a custom `Interactions.Use` that opens your `InteractiveCustomUIPage`. No StructuralCraftingWindow, no crafting system overhead.

### Recommended block JSON structure

```json
{
  "Parent": "Bench_Builders",
  "Bench": null,
  "Interactions": {
    "Use": {
      "Interaction": "OpenCustomUI",
      "Page": "BlueprintBench"
    }
  }
}
```

And in your plugin's `setup()`:
```java
OpenCustomUIInteraction.registerSimple(
    this,
    BlueprintBenchPage.class,
    "BlueprintBench",
    playerRef -> new BlueprintBenchPage(playerRef)
);
```

Or for block-state-aware pages:
```java
OpenCustomUIInteraction.registerBlockCustomPage(
    this,
    BlueprintBenchPage.class,
    "BlueprintBench",
    YourBlockState.class,
    (playerRef, state) -> new BlueprintBenchPage(playerRef, state)
);
```

---

## Summary Table

| Question | Answer | Confidence |
|----------|--------|------------|
| Prevent StructuralCraftingWindow auto-open? | **YES** — override `Interactions.Use` in block JSON | High — code path is clear and well-understood |
| Use InteractiveCustomUIPage for custom UI? | **YES** — rich server-driven UI with buttons, items, search, events | High — many engine examples exist |
| Ship custom `.ui` template files? | **YES** — place in `Common/UI/Custom/Pages/`, set `IncludesAssetPack: true`. See [ui-file-system.md](./ui-file-system.md) | High — confirmed by TroubleDEV's community example |
| Remove Bench config entirely? | **YES** — safe if no engine crafting system needed | High — block interaction system is independent of bench system |

## Key Code Citations

| Class | Method/Field | Line | Purpose |
|-------|-------------|------|---------|
| `BlockType` | `afterDecode` | 1853-1860 | Auto-injects bench interaction only if `Interactions.Use` is absent |
| `Bench` | `BENCH_INTERACTIONS` | 101 | Static map of BenchType → RootInteraction |
| `Bench` | `getRootInteraction()` | 203 | Looks up interaction from static map |
| `CraftingPlugin` | `setup()` | 128-130 | Registers StructuralCrafting → OpenBenchPageInteraction |
| `OpenBenchPageInteraction` | `interactWithBlock()` | 88-93 | Creates StructuralCraftingWindow |
| `OpenCustomUIInteraction` | `registerSimple()` | 88-89 | Plugin API to register custom page suppliers |
| `OpenCustomUIInteraction` | `registerBlockCustomPage()` | 93-141 | Plugin API for block-state-aware pages |
| `InteractiveCustomUIPage` | `sendUpdate()` | 36-55 | Push UI updates to client |
| `UICommandBuilder` | all methods | 1-200 | Full UI command API |
| `UIEventBuilder` | `addEventBinding()` | 22-48 | Event binding API |
| `CustomUIEventBindingType` | enum | 1-24 | All available UI event types |
| `ItemGridSlot` | constructor/setters | 1-60 | Item display in UI grids |
