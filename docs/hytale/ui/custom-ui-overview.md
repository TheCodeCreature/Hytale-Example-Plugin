---
topic: "Custom UI System — Overview"
category: "Plugin API / Custom UI"
updated: 2026-04-28
sources:
  - "https://hytale-docs.com/docs/api/server-internals/custom-ui"
  - "https://hytale-docs.com/docs/api/server-internals/ui-reference"
  - "hytalemodding.dev/en/docs/official-documentation/custom-ui"
  - "docs/hytale/plugins/custom-ui-for-Stencil-Book.md (archived)"
  - "docs/hytale/plugins/custom-ui-options.md (archived)"
  - "docs/hytale/plugins/ui-file-system.md (archived)"
  - "docs/hytale/plugins/api-reference-interactive-custom-ui.md (archived)"
---

# Custom UI System — Overview

## Summary

Hytale provides a fully server-driven custom UI framework that allows plugins to define visual layouts in `.ui` template files and control them at runtime via Java. The server sends commands to manipulate UI elements; the client renders the UI and sends events back when the player interacts. This is the primary mechanism for building plugin UIs like crafting benches, settings pages, item browsers, and interactive dialogs.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        CLIENT                            │
│  resources/Common/UI/Custom/YourPlugin/                  │
│  └── YourPage.ui  (loaded when player connects)          │
│                                                          │
│  Renders .ui files, displays UI, captures user input     │
└───────────────────────┬─────────────────┬────────────────┘
                        │ Commands        │ Events
                        │ (set values,    │ (button clicks,
                        │  append, clear) │  input changes)
                        ▼                 │
┌───────────────────────────────────────────────────────────┐
│                        SERVER                             │
│  InteractiveCustomUIPage<T>                               │
│  - build(): load layout, set values, bind events          │
│  - handleDataEvent(): respond to user interactions        │
│  - sendUpdate(): push incremental UI changes              │
└───────────────────────────────────────────────────────────┘
```

### Key Points

- `.ui` files are downloaded to the client when the player connects (via asset pack)
- The server **cannot** create `.ui` files dynamically — they must exist at build time
- Any syntax error in a `.ui` file **crashes the player's connection**
- The server sends **commands** to manipulate UI elements (`set`, `append`, `clear`, `remove`)
- The client sends **events** back when the player interacts (`Activating`, `ValueChanged`, etc.)
- Communication is via packets: `CustomPage` (server→client) and `CustomPageEvent` (client→server)

## .ui File Format

Hytale uses a **custom DSL** (Domain Specific Language) — NOT XML, XAML, JSON, or HTML. It uses a CSS/SCSS-like declarative syntax.

### Basic Structure

```
// Comments start with //

// Import Common.ui for reusable components
$C = "../Common.ui";

// Define custom styles (optional)
@MyStyle = LabelStyle(FontSize: 16, TextColor: #ffffff);

// Root element (only ONE root element per file)
Group {
    Anchor: (Width: 400, Height: 300);
    Background: #141c26(0.95);
    LayoutMode: Top;
    Padding: (Full: 20);

    Label {
        Text: "Hello World!";
        Style: @MyStyle;
    }

    $C.@TextButton #MyButton {
        @Text = "Click Me";
        Anchor: (Width: 150, Height: 44);
    }
}
```

### Critical Syntax Rules

| Rule | Correct | Wrong |
|------|---------|-------|
| Text values must be quoted | `Text: "Hello";` | `Text: Hello;` |
| Properties end with semicolon | `Anchor: (Width: 100);` | `Anchor: (Width: 100)` |
| Colors use hex format | `#ffffff` or `#fff` | `white` or `rgb(255,255,255)` |
| Alpha in colors | `#141c26(0.95)` | `#141c26cc` |
| Element IDs start with `#` | `Label #Title { }` | `Label Title { }` |
| One root element per file | Single `Group { }` | Multiple roots |
| Import syntax | `$C = "../Common.ui";` | `import Common.ui` |

### Import and Reference Syntax

```
// Import a UI file and assign to variable
$C = "../Common.ui";

// Use imported components with @
$C.@TextButton #MyButton {
    @Text = "Click";       // Template parameter (initial value)
    Anchor: (Width: 120);  // Regular property
}

// Reference a style from imported file
Style: $C.@DefaultLabelStyle;
```

### Template Parameters vs Properties

| Context | Syntax | Example |
|---------|--------|---------|
| In `.ui` file (template param) | `@Parameter` | `@Text = "Default";` |
| In Java (runtime property) | `.Property` | `cmd.set("#Button.Text", "New");` |

**You cannot use `@Text` in Java selectors.** Always use `.Text`.

### Named Expressions and Spread Operator

```
// Variables
@Title = "Hytale";
@ExtraSpacing = 5;

// Spread operator for style inheritance
@MyBaseStyle = LabelStyle(FontSize: 24, LetterSpacing: 2);
Label { Style: (...@MyBaseStyle, FontSize: 36); }  // Override FontSize only

// Templates (reusable component definitions)
@Row = Group {
    Anchor: (Height: 50);
    Label #Label { Text: @LabelText; }
};

@Row #FirstRow { @LabelText = "Row 1"; }
@Row #SecondRow { @LabelText = "Row 2"; }
```

### Translation Keys

```
Label { Text: %ui.general.cancel; }   // % prefix references a translation key
```

## Project Structure

```
your-plugin/
├── build.gradle
├── src/main/
│   ├── java/com/yourname/plugin/
│   │   ├── YourPlugin.java
│   │   └── ui/
│   │       └── MyPage.java
│   └── resources/
│       ├── manifest.json              ← Must have "IncludesAssetPack": true
│       └── Common/
│           └── UI/
│               └── Custom/
│                   └── Pages/
│                       └── MyPage.ui
```

### Manifest Requirement

```json
{
    "Identifier": "your-plugin",
    "Name": "Your Plugin",
    "Version": "1.0.0",
    "EntryPoint": "com.yourname.plugin.YourPlugin",
    "IncludesAssetPack": true
}
```

> **Critical:** `"IncludesAssetPack": true` is mandatory. Without it, clients see "Red X" for custom images and `.ui` files won't load.

### Path Resolution

The path passed to `cmd.append()` is **relative to `Common/UI/Custom/`**:

```java
// File at: src/main/resources/Common/UI/Custom/Pages/MyPage.ui
cmd.append("Pages/MyPage.ui");

// File at: src/main/resources/Common/UI/Custom/Pages/StencilBook/StencilBookPage.ui
cmd.append("Pages/StencilBook/StencilBookPage.ui");
```

## Server-Side Lifecycle

### Page Class Hierarchy

```
CustomUIPage (abstract base)
  └── BasicCustomUIPage (non-interactive, display-only)
  └── InteractiveCustomUIPage<T> (adds typed event data handling)
        └── YourPage extends InteractiveCustomUIPage<YourEventData>
```

### Creating a Page

```java
public class MyPage extends InteractiveCustomUIPage<MyPage.EventData> {

    public static final String LAYOUT = "Pages/MyPage.ui";
    private final PlayerRef playerRef;

    public MyPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder evt, Store<EntityStore> store) {
        cmd.append(LAYOUT);
        // Set initial values, bind events...
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref,
                                Store<EntityStore> store, EventData data) {
        // Respond to user interactions...
    }
}
```

### Opening a Page

```java
Player player = store.getComponent(ref, Player.getComponentType());
player.getPageManager().openCustomPage(ref, store, new MyPage(playerRef));
```

### Page Lifetime Options

| Value | Behavior |
|-------|----------|
| `CantClose` | Cannot be closed by user (e.g., death screen) |
| `CanDismiss` | Player can press ESC to close |
| `CanDismissOrCloseThroughInteraction` | ESC or interaction closes it |

### Opening via Block Interaction

Register a custom page supplier and reference it in the block's JSON:

```java
// In plugin setup():
OpenCustomUIInteraction.registerSimple(
    this, MyPage.class, "MyPageId",
    playerRef -> new MyPage(playerRef)
);
```

```json
// In block type JSON:
{
    "Interactions": {
        "Use": {
            "Interaction": "OpenCustomUI",
            "Page": "MyPageId"
        }
    }
}
```

## Update Flow

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│  build()  │ ──────▶ │ Player   │ ──────▶ │ handle   │
│ cmd+evt   │         │ interacts│         │ DataEvent│
└──────────┘         └──────────┘         └──────────┘
                                                 │
                                                 ▼
                                          ┌──────────┐
                                          │sendUpdate │
                                          │(cmd, evt) │
                                          └──────────┘
```

1. **`build()`** — Called once when page opens. Load layout, set initial values, bind events.
2. **Player interacts** — Client sends event to server.
3. **`handleDataEvent()`** — Server processes event, then sends incremental update via `sendUpdate()`.
4. **`rebuild()`** — Full rebuild (avoid if possible; use targeted `set()` instead).

### Sending Updates

```java
@Override
public void handleDataEvent(...) {
    UICommandBuilder cmd = new UICommandBuilder();
    cmd.set("#StatusLabel.Text", "Updated!");
    this.sendUpdate(cmd, false);   // false = don't clear existing bindings
}
```

### Closing the Page

```java
this.close();
```

## Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| "Failed to load CustomUI documents" | Syntax error in `.ui` file | Check quotes, semicolons, color format, Common.ui import path |
| "Failed to apply CustomUI event bindings" | Element ID mismatch | Verify `#MyButton` in Java matches `#MyButton` in `.ui` |
| "Selected element in CustomUI command was not found" | Wrong selector for appended templates | Appended templates ARE the element at index: use `#Container[0].Text`, NOT `#Container[0] #Button.Text` |
| Player disconnects when opening page | `.ui` file parse error or file doesn't exist | Verify file path, start minimal, add complexity gradually |
| UI opens but buttons don't work | Event bindings not configured | Ensure `evt.addEventBinding()` called in `build()`, IDs match |

## See Also

- [UI Element Reference](./ui-element-reference.md) — All UI element types and their properties
- [CommonUI Library Reference](./ui-commonui-library.md) — Reusable components from `Common.ui`
- [UI Data Binding](./ui-data-binding.md) — Event binding, value capture, and codec patterns
