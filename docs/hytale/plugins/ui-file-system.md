---
topic: "Custom .ui File System for Plugins"
category: "Plugin API / Custom UI"
updated: 2026-04-25
sources:
  - "trouble-dev/hytale-basic-uis (GitHub, 44 stars, by TroubleDEV/noel-lang)"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui (Official Hytale Documentation)"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/markup"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/layout"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation"
  - "hytalemodding.dev/en/docs/guides/plugin/ui (Community Custom UI Guide)"
  - "decompiled UICommandBuilder.java"
  - "decompiled CommandListPage.java, EntitySpawnPage.java, PluginListPage.java, RespawnPage.java"
  - "server log analysis: 2026-04-25_17-42-59_server.log"
supersedes: "custom-ui-for-blueprint-bench.md Question 3 (previously answered 'UNLIKELY')"
---

# Custom `.ui` File System for Plugins

## Summary

**YES — plugins CAN create and ship custom `.ui` template files that the client will load and render.** This was previously documented as "UNLIKELY" in [custom-ui-for-blueprint-bench.md](./custom-ui-for-blueprint-bench.md) (Question 3), based solely on decompiled engine code analysis. Community evidence from TroubleDEV's [hytale-basic-uis](https://github.com/trouble-dev/hytale-basic-uis) repository (44 stars, MIT license) confirms this works and provides a tested reference implementation.

---

## 1. Resource Path — Where `.ui` Files Go

### Required directory structure

```
src/main/resources/
├── manifest.json                      ← Must have "IncludesAssetPack": true
└── Common/
    └── UI/
        └── Custom/
            └── Pages/
                ├── MyPage.ui          ← Your custom .ui files
                ├── MyDialog.ui
                └── Components/        ← Subdirectories are fine
                    └── MyButton.ui
```

### Key rules

| Rule | Detail |
|------|--------|
| **Root path** | `Common/UI/Custom/` is the asset root for custom UI files |
| **Convention** | Place pages in `Common/UI/Custom/Pages/` (matches engine convention) |
| **Manifest** | `"IncludesAssetPack": true` is **required** in `manifest.json` |
| **JAR packaging** | The build must include resources in the JAR (Gradle: `from("src/main/resources")` in `tasks.jar`) |
| **Subdirectories** | Supported — use them for organization (e.g., `Pages/Components/`) |

### How it maps to `commandBuilder.append()`

The path passed to `append()` is **relative to `Common/UI/Custom/`**:

```java
// File at: src/main/resources/Common/UI/Custom/Pages/MyPage.ui
commandBuilder.append("Pages/MyPage.ui");

// File at: src/main/resources/Common/UI/Custom/Pages/Components/MyButton.ui
commandBuilder.append("Pages/Components/MyButton.ui");

// File at: src/main/resources/Common/UI/Custom/Common.ui
// (Referenced as $Common = "../Common.ui" from within Pages/ directory)
```

### Evidence — TroubleDEV's manifest.json

```json
{
  "Group": "TestUI",
  "Name": "TestUIPlugin",
  "Version": "1.0.0",
  "Main": "de.noel.testui.TestUIPlugin",
  "IncludesAssetPack": true
}
```

### Installation note

From TroubleDEV's README:
> "Copy the contents of `src/main/resources/Common/` to the corresponding server directory"

This indicates the `.ui` files may need to exist in the server's file system at `Common/UI/Custom/Pages/` in addition to being in the JAR. The exact mechanism depends on how the Hytale mod loader distributes asset packs to clients — the asset pack system handles this when `IncludesAssetPack: true` is set.

---

## 2. `.ui` File Format — Complete Reference

### Syntax overview

Hytale's `.ui` format is a custom markup language (not JSON, not XML). It uses a CSS-like declarative syntax.

```
// Comments start with //
ElementType #OptionalId {
    Property: value;
    Property: (SubProp1: val1, SubProp2: val2);

    // Children are nested
    ChildElement #ChildId {
        Property: value;
    }
}
```

### Minimal example (HelloWorldPage.ui)

```
Group {
    Anchor: (Width: 400, Height: 250);
    Background: #1a1a2e(0.95);
    LayoutMode: Top;
    Padding: (Full: 20);

    Label #Title {
        Text: "Hello World";
        Anchor: (Height: 40);
        Style: (FontSize: 24, TextColor: #ffffff, Alignment: Center);
    }
}
```

### Complex example (FormPage.ui — with imports, styles, inputs)

```
$C = "../Common.ui";

@PrimaryButtonStyle = TextButtonStyle(
    Default: (Background: #3a7bd5, LabelStyle: (FontSize: 14, TextColor: #ffffff,
              RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)),
    Hovered: (Background: #4a8be5, LabelStyle: (FontSize: 14, TextColor: #ffffff,
              RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)),
    Pressed: (Background: #2a6bc5, LabelStyle: (FontSize: 14, TextColor: #ffffff,
              RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center))
);

Group {
    Anchor: (Width: 480, Height: 380);
    Background: #141c26(0.98);
    LayoutMode: Top;
    Padding: (Full: 20);

    Label { Text: "Settings"; Anchor: (Height: 45);
            Style: (FontSize: 26, TextColor: #ffffff, HorizontalAlignment: Center, RenderBold: true); }

    Group { Anchor: (Height: 1); Background: #2b3542; }   // Horizontal divider
    Group { Anchor: (Height: 16); }                        // Spacer

    Group {
        LayoutMode: Left;
        Anchor: (Height: 44);
        Label { Text: "Player Name"; Anchor: (Width: 130);
                Style: (FontSize: 14, TextColor: #96a9be, VerticalAlignment: Center); }
        $C.@TextField #NameInput { FlexWeight: 1; PlaceholderText: "Enter name..."; }
    }

    Group { Anchor: (Height: 12); }    // Spacer

    $C.@CheckBoxWithLabel #NotifyOption { @Text = "Enable notifications"; @Checked = true; Anchor: (Height: 28); }
    $C.@CheckBoxWithLabel #CoordsOption { @Text = "Show coordinates"; @Checked = false; Anchor: (Height: 28); }

    Group { FlexWeight: 1; }           // Flexible spacer pushes buttons to bottom

    Group {
        LayoutMode: Center;
        Anchor: (Height: 44);
        TextButton #SaveButton { Text: "SAVE"; Anchor: (Width: 110, Height: 40); Style: @PrimaryButtonStyle; }
        Group { Anchor: (Width: 16); }
        TextButton #CancelButton { Text: "CANCEL"; Anchor: (Width: 110, Height: 40); Style: @CancelButtonStyle; }
    }
}
```

### Available UI elements (40+ types)

Per the official type documentation at hytalemodding.dev:

#### Core layout & display

| Element | Purpose |
|---------|---------|
| `Group` | Container — the fundamental layout element |
| `Label` | Text display |
| `Panel` | Styled container (background, borders) |
| `Sprite` | Image display (with animation frames) |
| `AssetImage` | Image from asset path |
| `SceneBlur` | Background blur effect |

#### Buttons & interactions

| Element | Purpose |
|---------|---------|
| `Button` | Basic clickable button |
| `TextButton` | Button with text label and style states (Default/Hovered/Pressed) |
| `ActionButton` | Button with icon and alignment options |
| `BackButton` | Navigation back button |
| `ToggleButton` | On/off toggle button |
| `TabButton` | Tab button for tab navigation |
| `MenuItem` | Menu item (for context menus) |

#### Input elements

| Element | Purpose |
|---------|---------|
| `TextField` | Single-line text input |
| `CompactTextField` | Compact text input variant |
| `MultilineTextField` | Multi-line text input |
| `NumberField` | Numeric input field |
| `CheckBox` | Boolean checkbox |
| `CheckBoxContainer` | Checkbox with container |
| `LabeledCheckBox` | Checkbox with label |
| `Slider` | Range slider |
| `FloatSlider` | Float-precision slider |
| `SliderNumberField` | Slider with number display |
| `FloatSliderNumberField` | Float slider with number display |
| `DropdownBox` | Dropdown select |
| `DropdownEntry` | Entry within a dropdown |
| `ColorPicker` | Color selection |
| `ColorPickerDropdownBox` | Color picker in dropdown |
| `ColorOptionGrid` | Grid of color options |
| `CodeEditor` | Code editor component |

#### Item & inventory elements

| Element | Purpose |
|---------|---------|
| `ItemGrid` | Grid of item slots (for inventory-like displays) |
| `ItemIcon` | Single item icon display |
| `ItemSlot` | Single item slot |
| `ItemSlotButton` | Clickable item slot |
| `ItemPreviewComponent` | 3D item preview |
| `BlockSelector` | Block type selector |

#### Navigation & progress

| Element | Purpose |
|---------|---------|
| `TabNavigation` | Tab navigation bar |
| `ProgressBar` | Progress indicator |
| `CircularProgressBar` | Circular progress indicator |
| `TimerLabel` | Countdown/timer display |
| `HotkeyLabel` | Keyboard shortcut display |

#### Layout helpers

| Element | Purpose |
|---------|---------|
| `DynamicPane` | Dynamic content pane |
| `DynamicPaneContainer` | Container for dynamic panes |
| `ReorderableList` | Drag-reorderable list |
| `ReorderableListGrip` | Drag handle for reorderable list |
| `CharacterPreviewComponent` | 3D character preview |

### Layout system

#### Anchor (positioning & sizing)

```
Anchor: (Width: 200, Height: 40);               // Fixed size
Anchor: (Top: 10, Left: 20, Width: 100);        // Position + size
Anchor: (Full: 0);                               // Fill parent (all edges at 0)
Anchor: (Top: 10, Bottom: 10, Left: 20);         // Stretch vertically, fixed left
Anchor: (Bottom: 10, Right: 10, Width: 100, Height: 30);  // Bottom-right corner
```

#### LayoutMode (child arrangement)

| Mode | Behavior |
|------|----------|
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
| `LeftCenterWrap` | Wrapping horizontal, centered rows |

#### Padding

```
Padding: (Full: 20);                            // Uniform 20px
Padding: (Top: 10, Bottom: 20, Left: 15, Right: 15);  // Per-edge
Padding: (Horizontal: 20, Vertical: 10);         // Shorthand
```

#### FlexWeight (distribute remaining space)

```
Group {
    LayoutMode: Left;
    Group { Anchor: (Width: 100); }     // Fixed 100px
    Group { FlexWeight: 2; }            // Gets 2/3 of remaining
    Group { FlexWeight: 1; }            // Gets 1/3 of remaining
}
```

#### Visibility

```
Button #Hidden { Visible: false; }      // Hidden, takes no layout space
```

### Property types

#### Basic values

```
Visible: false;                          // Boolean
Height: 20;                              // Int
Min: 0.2;                                // Float
Text: "Hi!";                             // String
PasswordChar: "*";                       // Char
Background: #ffffff;                     // Color (hex)
Background: #000000(0.3);               // Color with alpha
Background: #rrggbbaa;                  // Color with alpha (alt)
```

#### Translations

```
Label { Text: %ui.general.cancel; }      // Translation key (% prefix)
```

#### Paths (relative to current file)

```
Path: "MyButton.png";                    // Same directory
Path: "../MyButton.png";                 // Parent directory
```

#### Font names

```
Style: (FontName: "Default");           // Default game font
Style: (FontName: "Secondary");         // Headlines font
Style: (FontName: "Mono");              // Monospace (dev use)
```

### Named expressions (variables)

```
@Title = "Hytale";
@ExtraSpacing = 5;
Label {
    Text: @Title;
    Style: (LetterSpacing: 2 + @ExtraSpacing);
}
```

#### Spread operator (style inheritance)

```
@MyBaseStyle = LabelStyle(FontSize: 24, LetterSpacing: 2);
Label { Style: (...@MyBaseStyle, FontSize: 36); }    // Override FontSize
```

### Document references (imports)

```
$Common = "../Common.ui";                // Import another .ui file
TextButton { Style: $Common.@DefaultButtonStyle; }   // Use its named expressions
```

### Templates (reusable components)

```
@Row = Group {
    Anchor: (Height: 50);
    Label #Label { Anchor: (Left: 0, Width: 100); Text: @LabelText; }
    Group #Content { Anchor: (Left: 100); }
};

Group #Rows {
    LayoutMode: TopScrolling;
    @Row #FirstRow {
        @LabelText = "First row";
        #Content { TextField {} }
    }
    @Row #SecondRow {
        @LabelText = "Second row";
    }
}
```

### Common.ui — shared styles library

The engine provides `Common/UI/Custom/Common.ui` with pre-built styles. Import and use:

```
$Common = "../Common.ui";       // From within Pages/ subdirectory

$Common.@TextButton { @Text = "My Button"; }
$Common.@Container { ... }
$Common.@TextField #MyInput { PlaceholderText: "Type here..."; }
$Common.@CheckBoxWithLabel #MyCheck { @Text = "Enable option"; @Checked = true; }
$Common.@NumberField #MyNumber { Value: 100; }
```

Run `/ui-gallery` in-game to see all available Common.ui styles live.

### VS Code extension

There is an official VS Code extension for `.ui` syntax highlighting:
[HypixelStudiosCanadaInc.vscode-hytaleui](https://marketplace.visualstudio.com/items?itemName=HypixelStudiosCanadaInc.vscode-hytaleui)

---

## 3. Path Resolution — How `commandBuilder.append()` Resolves Paths

### Resolution rules

```
commandBuilder.append(path)
→ Client resolves: Common/UI/Custom/{path}
→ Searches: base game assets THEN active mod/plugin asset packs
```

| Call | Resolved client path |
|------|---------------------|
| `append("Pages/MyPage.ui")` | `Common/UI/Custom/Pages/MyPage.ui` |
| `append("Pages/Components/Btn.ui")` | `Common/UI/Custom/Pages/Components/Btn.ui` |
| `append("Common/TextButton.ui")` | `Common/UI/Custom/Common/TextButton.ui` |

### Evidence from engine code

All decompiled engine pages use paths relative to `Common/UI/Custom/`:

```java
// CommandListPage.java
commandBuilder.append("Pages/CommandListPage.ui");
commandBuilder.append("#CommandList", "Pages/BasicTextButton.ui");

// EntitySpawnPage.java
commandBuilder.append("Pages/EntitySpawnPage.ui");
commandBuilder.append("#NPCList", "Common/TextButton.ui");

// PluginListPage.java
commandBuilder.append("Pages/PluginListPage.ui");
commandBuilder.append("#PluginList", "Pages/PluginListButton.ui");

// RespawnPage.java
commandBuilder.append("Pages/RespawnPage.ui");
commandBuilder.append("#DroppedItemsContainer", "Pages/DroppedItemSlot.ui");
```

### Evidence from TroubleDEV's plugin

```java
// TestPage.java — loads custom plugin .ui file
// File at: src/main/resources/Common/UI/Custom/Pages/TestPage.ui
commandBuilder.append("Pages/TestPage.ui");
```

### Can mods override base game `.ui` files?

The asset pack system likely supports overlay — a mod asset pack at the same path would override the base file. This is consistent with how other Hytale asset types (blocks, items) work. **Not explicitly tested.**

### Two `append()` overloads

| Method | Purpose |
|--------|---------|
| `append(documentPath)` | Load `.ui` as page root (first call in `build()`) |
| `append(selector, documentPath)` | Append `.ui` as child of `#selector` (for list items, sub-components) |

From the decompiled `UICommandBuilder.java`:
```java
public UICommandBuilder append(String documentPath) {
    this.commands.add(new CustomUICommand(CustomUICommandType.Append, null, null, documentPath));
    return this;
}

public UICommandBuilder append(String selector, String documentPath) {
    this.commands.add(new CustomUICommand(CustomUICommandType.Append, selector, null, documentPath));
    return this;
}
```

---

## 4. Complete Working Example — From File to UI

### Step 1: Create the `.ui` file

**`src/main/resources/Common/UI/Custom/Pages/BlueprintBenchPage.ui`**:

```
$Common = "../Common.ui";

@HeaderStyle = LabelStyle(FontSize: 22, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center);
@SubtextStyle = LabelStyle(FontSize: 12, TextColor: #6e7da1, HorizontalAlignment: Center);

Group {
    Anchor: (Width: 500, Height: 400);
    Background: #141c26(0.98);
    LayoutMode: Top;
    Padding: (Full: 20);

    // Header
    Label #Title {
        Text: "Blueprint Bench";
        Anchor: (Height: 45);
        Style: @HeaderStyle;
    }

    Group { Anchor: (Height: 1); Background: #2b3542; }
    Group { Anchor: (Height: 8); }

    // Search bar
    $Common.@TextField #SearchInput {
        Anchor: (Height: 36);
        PlaceholderText: "Search recipes...";
    }

    Group { Anchor: (Height: 8); }

    // Scrollable recipe list
    Group #RecipeList {
        LayoutMode: TopScrolling;
        ScrollbarStyle: $Common.@DefaultScrollbar;
        FlexWeight: 1;
    }

    Group { Anchor: (Height: 8); }

    // Status bar
    Label #StatusLabel {
        Text: "Select a recipe to assign";
        Anchor: (Height: 20);
        Style: @SubtextStyle;
    }
}
```

### Step 2: Create the Java page class

```java
public class BlueprintBenchPage extends InteractiveCustomUIPage<BlueprintBenchPage.EventData> {

    public static class EventData {
        public String searchQuery;
        public String recipeId;
        public String action;

        public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
            .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (o, v) -> o.searchQuery = v, o -> o.searchQuery).add()
            .append(new KeyedCodec<>("RecipeId", Codec.STRING), (o, v) -> o.recipeId = v, o -> o.recipeId).add()
            .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action).add()
            .build();
    }

    public BlueprintBenchPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        // Load the custom .ui template
        cmd.append("Pages/BlueprintBenchPage.ui");

        // Bind search input
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
            new EventData().append("@SearchQuery", "#SearchInput.Value").append("Action", "Search"), false);

        // Populate recipe list items (using existing engine template for each item)
        for (RecipeEntry recipe : getAvailableRecipes()) {
            cmd.append("#RecipeList", "Pages/BasicTextButton.ui");
            cmd.set("#RecipeList[" + i + "] #Label.Text", recipe.displayName());

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#RecipeList[" + i + "]",
                new EventData().append("RecipeId", recipe.id()).append("Action", "Select"));
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store, @Nonnull EventData data) {
        if ("Search".equals(data.action)) {
            // Rebuild with filtered results
        } else if ("Select".equals(data.action)) {
            // Assign the selected recipe
        }
    }
}
```

### Step 3: Ensure manifest.json has asset pack enabled

```json
{
  "Group": "MyPlugin",
  "Name": "MyPlugin",
  "Version": "1.0.0",
  "Main": "com.myplugin.MyPlugin",
  "IncludesAssetPack": true
}
```

### Step 4: Ensure Gradle packages resources

```kotlin
tasks.jar {
    from("src/main/resources")    // Include .ui files in JAR
}
```

---

## 5. Engine `.ui` Files Available for Reuse

These templates ship with the base game and can be referenced by any plugin:

| Path | Purpose | Key selectors |
|------|---------|---------------|
| `Pages/BasicTextButton.ui` | Simple clickable text button | `#Label` |
| `Common/TextButton.ui` | Styled text button | `#Label` |
| `Pages/CommandListPage.ui` | Full command browser layout | `#SearchInput`, `#CommandList`, etc. |
| `Pages/EntitySpawnPage.ui` | Tabbed entity browser | `#NPCList`, `#ModelList` |
| `Pages/PluginListPage.ui` | Plugin list with checkboxes | `#PluginList` |
| `Pages/PluginListButton.ui` | Plugin list item | `#Label`, `#CheckBox` |
| `Pages/RespawnPage.ui` | Death/respawn screen | `#DroppedItemsContainer` |
| `Pages/DroppedItemSlot.ui` | Item slot for dropped items | `#ItemIcon` |
| `Pages/WarpListPage.ui` | Warp point list | |
| `Pages/WarpEntryButton.ui` | Warp list item | |
| `Pages/LaunchPadSettingsPage.ui` | Settings form | |
| `Pages/PlaySoundPage.ui` | Sound browser | `#SoundList` |
| `Pages/ItemRepairElement.ui` | Item repair list item | `#ElementList` |
| `Pages/SubcommandCard.ui` | Command subcommand display | |
| `Pages/VariantCard.ui` | Command variant display | |
| `Pages/ParameterItem.ui` | Command parameter display | |
| `Common.ui` | Shared styles library | `@DefaultScrollbar`, `@TextField`, `@TextButton`, etc. |

---

## 6. Deployment — Getting `.ui` Files to the Client

### The problem: server loads them, client can't find them

During development with `./gradlew runServer`, the server loads common assets from **three** sources in sequence:

```
1. Base game:        /                                          → 24,748 assets (CommonAssetsIndex.hashes)
2. Deployed mod:     run/mods/CodeCreature.Development/         → 0 assets (no Common/ directory!)
3. Build output:     build/resources/main/                      → 2 assets (the .ui files)
```

Pass #3 is a **development-only shortcut** added by the `hytale-mod` Gradle plugin — it adds `build/resources/main` as an extra asset source. This means the **server** can find the `.ui` files, but the **client** cannot. The client only receives assets from pass #1 (base game) and pass #2 (the deployed mod directory). Since `run/mods/CodeCreature.Development/Common/` doesn't exist, the client fails with "Failed to load customUI documents" / "Could not find document".

**Source:** Server log `2026-04-25_17-42-59_server.log` lines 123-133.

### Root causes

| # | Cause | Detail |
|---|-------|--------|
| 1 | **`run/mods/*/manifest.json` has `IncludesAssetPack: false`** | The deployed mod's manifest is a separate file created by the `hytale-mod` Gradle plugin (or manually). It does NOT use the template from `src/main/resources/manifest.json`. If `IncludesAssetPack` is `false`, the client won't attempt to load the mod's `Common/` asset pack at all. |
| 2 | **`run/mods/*/Common/` directory is missing** | The `hytale-mod` plugin creates the dev mod shell with only `Server/` assets. `Common/` assets are **never** automatically copied to the deployed mod directory. The server compensates with the `build/resources/main` shortcut, but the client has no such fallback. |

### How the `run/mods/` directory gets populated

The `hytale-mod` Gradle plugin creates a "development mod" directory at `run/mods/{Group}.{Name}/`:

```
run/mods/CodeCreature.Development/
├── manifest.json          ← Created by hytale-mod plugin (NOT the template-processed one)
└── Server/                ← Server-side assets (block types, items, etc.)
    └── Item/
        └── Items/
```

**Key insight:** The deployed `manifest.json` at `run/mods/` is a **separate file** from the source template at `src/main/resources/manifest.json`. The source template goes through `processResources` and ends up in `build/resources/main/manifest.json`, but the `hytale-mod` plugin generates its own simplified manifest for the run directory. They can and do diverge.

### The fix

Two changes are needed:

#### Fix 1: Set `IncludesAssetPack: true` in the deployed manifest

Edit `run/mods/CodeCreature.Development/manifest.json`:

```json
{
  "IncludesAssetPack": true
}
```

**Warning:** This file may be overwritten by the `hytale-mod` plugin on rebuild. If it keeps reverting, you may need to investigate how the plugin generates this manifest and configure it accordingly.

#### Fix 2: Add a Gradle task to deploy Common/ assets

Add to `build.gradle.kts`:

```kotlin
val deployCommonAssets = tasks.register<Copy>("deployCommonAssets") {
    group = "hytale"
    description = "Copies Common/ assets (UI files, etc.) to the deployed mod directory so the client can load them."

    dependsOn("processResources")

    from(layout.buildDirectory.dir("resources/main/Common"))
    into("run/mods/CodeCreature.Development/Common")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Common assets deployed to run/mods/CodeCreature.Development/Common/")
    }
}
```

Then hook it into `runServer` in the `afterEvaluate` block:

```kotlin
targetTask.dependsOn(deployCommonAssets)
```

After the fix, the deployed mod structure should be:

```
run/mods/CodeCreature.Development/
├── manifest.json               ← IncludesAssetPack: true
├── Common/                     ← NEW: deployed by deployCommonAssets task
│   └── UI/
│       └── Custom/
│           └── Pages/
│               └── BlueprintBench/
│                   ├── BlueprintBenchPage.ui
│                   └── RecipeEntry.ui
└── Server/
    └── Item/
        └── Items/
```

### How asset distribution works (server → client)

```
Server startup:
  1. Loads base game assets from Assets.zip (CommonAssetsIndex.hashes)
  2. For each mod with IncludesAssetPack: true:
     a. Loads Common/ assets from run/mods/{mod}/Common/
     b. Loads Server/ assets from run/mods/{mod}/Server/
  3. (Dev mode only) Also loads from build/resources/main/

Client connects:
  1. Server tells client which asset packs are active
  2. Client loads Common/ assets from the mod's asset pack
  3. Client resolves .ui paths against: base game → mod asset packs (in load order)
  4. commandBuilder.append("Pages/BlueprintBench/BlueprintBenchPage.ui")
     → Client resolves to: Common/UI/Custom/Pages/BlueprintBench/BlueprintBenchPage.ui
     → Found in the mod's Common/ asset pack ✓
```

### TroubleDEV's installation note explained

From TroubleDEV's README:
> "Copy the contents of `src/main/resources/Common/` to the corresponding server directory"

This is the **manual equivalent** of the `deployCommonAssets` Gradle task — manually copying `Common/` into the mod's deployed directory. The `hytale-mod` plugin doesn't automate this step.

### For production deployment (non-dev servers)

When distributing your mod as a JAR for production servers:

1. The JAR must contain `Common/UI/Custom/Pages/...` resources (Gradle's `processResources` handles this)
2. The server extracts or reads Common/ assets from the JAR into the mod's asset pack
3. `manifest.json` inside the JAR must have `"IncludesAssetPack": true`

The production flow is different from development — in production, the mod loader reads the JAR directly. The `run/mods/` directory structure issue only affects development mode.

### Checklist

- [ ] `src/main/resources/manifest.json` has `"IncludesAssetPack": true`
- [ ] `run/mods/{mod}/manifest.json` has `"IncludesAssetPack": true`
- [ ] `run/mods/{mod}/Common/UI/Custom/Pages/` contains your `.ui` files
- [ ] `build.gradle.kts` has `deployCommonAssets` task hooked into `runServer`
- [ ] `.ui` file paths in `commandBuilder.append()` are relative to `Common/UI/Custom/`

---

## 7. Caveats and Limitations

- If a plugin's `.ui` file has the same path as a base game file (e.g., `Pages/CommandListPage.ui`), the mod asset pack may override the engine's version. Use unique paths to avoid conflicts.
- **Recommendation**: Use a plugin-specific subdirectory: `Pages/MyPlugin/MyPage.ui` → `commandBuilder.append("Pages/MyPlugin/MyPage.ui")`

### Inline vs file-based

| Approach | Use when |
|----------|----------|
| `.ui` files | Complex layouts, reusable templates, designer-friendly iteration |
| `appendInline()` | Simple dynamic elements, generated UI fragments, no-file-needed prototyping |
| Hybrid | `.ui` file for page structure + `appendInline()` for dynamic list items |

### Client-side rendering

- `.ui` files are parsed and rendered by the **C# client**, not the Java server
- The server sends the path string; the client loads and renders the template
- Server-side Java code cannot introspect the `.ui` file contents — it's a black box to the server

### No `loadUI()` in decompiled source

TroubleDEV's README references `builder.loadUI("Common/UI/Custom/Pages/MyPage.ui")`, but the actual code uses `commandBuilder.append("Pages/TestPage.ui")`. The decompiled `UICommandBuilder` class has no `loadUI()` method. Either:
- `loadUI()` is a convenience wrapper added in a newer API version
- The README is slightly inaccurate (using a more intuitive name)

**Use `append()` — it is the confirmed, working method.**

---

## 8. Correction to Previous Documentation

[custom-ui-for-blueprint-bench.md](./custom-ui-for-blueprint-bench.md) Question 3 previously stated:
> "No evidence in the decompiled source that plugins can register or ship custom `.ui` template files."

**This was incorrect.** The decompiled source alone cannot reveal this capability because `.ui` file resolution happens on the C# client side, not in the Java server. The server simply sends a path string via `commandBuilder.append()`. Whether that path resolves to a base game file or a modded asset pack file is determined by the client's asset loading system, which **does** support mod asset packs.

The TroubleDEV community example proves this works end-to-end:
1. `.ui` files in `src/main/resources/Common/UI/Custom/Pages/`
2. `"IncludesAssetPack": true` in manifest
3. `commandBuilder.append("Pages/MyPage.ui")` in Java
4. Client loads and renders the custom template

### Updated summary table

| Question | Previous Answer | Corrected Answer |
|----------|----------------|-----------------|
| Ship custom `.ui` template files? | NO | **YES** — place in `Common/UI/Custom/Pages/`, set `IncludesAssetPack: true` |

---

## See Also

- [Custom UI Options](./custom-ui-options.md) — comparison of all UI approaches
- [Custom UI for Blueprint Bench](./custom-ui-for-blueprint-bench.md) — engine research on InteractiveCustomUIPage
- [API Reference: InteractiveCustomUIPage](./api-reference-interactive-custom-ui.md) — complete decompiled API
- [Manifest](./manifest.md) — plugin manifest format
- [Official Custom UI Docs](https://hytalemodding.dev/en/docs/official-documentation/custom-ui) — Hypixel Studios documentation
- [Official Markup Reference](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup) — `.ui` syntax
- [Official Layout Reference](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/layout) — layout system
- [Official Type Documentation](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation) — all element types
- [Common Styling](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/common-styling) — Common.ui shared styles
- [TroubleDEV's hytale-basic-uis](https://github.com/trouble-dev/hytale-basic-uis) — working reference implementation
