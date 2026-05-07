---
topic: "Radial Menu Research — Engine Capabilities & Implementation Strategy"
category: "Plugin API / Custom UI"
updated: 2026-05-07
sources:
  - "decompiled: HudComponent.java (com.hypixel.hytale.protocol.packets.interface_)"
  - "decompiled: HudManager.java (com.hypixel.hytale.server.core.entity.entities.player.hud)"
  - "decompiled: EmoteCommand.java (com.hypixel.hytale.server.core.cosmetics.commands)"
  - "decompiled: Emote.java (com.hypixel.hytale.server.core.cosmetics)"
  - "decompiled: CustomUIPage.java, InteractiveCustomUIPage.java, BasicCustomUIPage.java"
  - "decompiled: PageManager.java (com.hypixel.hytale.server.core.entity.entities.player.pages)"
  - "decompiled: CustomUIHud.java (com.hypixel.hytale.server.core.entity.entities.player.hud)"
  - "decompiled: CustomUIEventBindingType.java (com.hypixel.hytale.protocol.packets.interface_)"
  - "decompiled: CustomPageLifetime.java, Page.java"
  - "decompiled: Inventory.java — activeUtilitySlot, setActiveUtilitySlot()"
  - "https://hytalemodding.dev/en/docs/official-documentation/custom-ui/layout"
  - "https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation"
  - "https://hytale-docs.com/docs/api/server-internals/custom-ui"
  - "docs/hytale/ui/ui-element-reference.md"
  - "docs/hytale/ui/custom-ui-overview.md"
  - "docs/hytale/plugins/input-detection-research.md"
  - "docs/Reference Assets/Assets/Common/UI/Custom/Common.ui — CircularProgressBar definition"
  - "docs/Reference Assets/Assets/Common/UI/Custom/Pages/UIGallery/Categories/ProgressContent.ui"
  - "docs/Reference Assets/Assets/Cosmetics/CharacterCreator/EmotesInGame.json"
---

# Radial Menu Research — Engine Capabilities & Implementation Strategy

## 1. Executive Summary

**Hytale has NO native radial/wheel menu element and NO circular LayoutMode.** The emote wheel and offhand/utility slot selector are **built-in Client UI (C#)** — they are not Custom UI pages, not `.ui` files, and not modifiable by server plugins. There is no decompiled Java class for `RadialMenu`, `EmoteMenu`, `WheelMenu`, or `OffhandMenu` on the server side.

However, a radial menu **can be simulated** using the Custom UI system with absolute positioning (`LayoutMode: Full`), pre-computed trigonometric positions for segments, and `MouseEntered`/`MouseExited` events for hover detection.

---

## 2. Built-in Radial/Wheel UIs — Client-Side Only

### 2.1 Emote Wheel

The emote wheel is a **client-side C# UI** that:
- Is triggered by a client keybind (not transmitted to the server)
- Displays emote options in a radial layout
- Sends a `/emote <id>` command to the server when an emote is selected

**Server-side evidence:**
- `EmoteCommand.java` — The ONLY server entry point. Calls `AnimationUtils.playAnimation(ref, AnimationSlot.Emote, null, emote.getId(), true, store)`
- `Emote.java` — Data class with `id`, `name`, `animation` fields
- `EmotesInGame.json` — Defines available emotes with `Id`, `Animation`, `Name`, `Icon`, `IsLooping`
- `GamePacketHandler.registerHandlers()` — Has NO emote request handler
- `InteractionType` enum — Has NO emote entry

**Conclusion:** The emote key press is handled entirely client-side. No packet is sent to the server when the player opens the emote wheel. The wheel UI itself is implemented in the C# client — no `.ui` file, no server-side Java class.

### 2.2 Utility Slot Selector (Offhand Menu)

The utility/offhand slot selector is controlled via `HudComponent.UtilitySlotSelector`:

```java
// HudComponent enum values (server can toggle visibility):
HudComponent.UtilitySlotSelector  // offhand item selector
HudComponent.BlockVariantSelector // block variant picker
```

The server can:
- **Show/hide** the utility slot selector: `hudManager.showHudComponents(playerRef, HudComponent.UtilitySlotSelector)`
- **Read/set** the active utility slot: `inventory.getActiveUtilitySlot()`, `inventory.setActiveUtilitySlot(byte)`
- **Read** the utility item: `inventory.getUtilityItem()`

The server **cannot**:
- Modify the visual layout of the utility selector
- Add custom items to it
- Change the number of slots
- Override the keybind that opens it

**Conclusion:** The utility slot selector is a built-in HUD component. The server can toggle its visibility and read/write slot data, but cannot modify its appearance or behavior.

---

## 3. Available Layout Modes — No Radial/Circular

The complete list of `LayoutMode` values (from official docs and decompiled code):

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
| `Overlay` | Stack children on top of each other |
| `TopScrolling` | Vertical stack with scrollbar |
| `BottomScrolling` | Bottom-aligned with scrollbar |
| `LeftScrolling` | Horizontal with scrollbar |
| `RightScrolling` | Right-aligned horizontal with scrollbar |
| `LeftCenterWrap` | Wrapping horizontal, each row centered |

**No `Radial`, `Circular`, `Wheel`, or `Arc` layout mode exists.**

---

## 4. Relevant UI Elements for Simulation

### 4.1 CircularProgressBar (NOT a Menu)

`CircularProgressBar` exists but is only a progress indicator — not interactive, not segmented, not a menu.

```
// From Common.ui:
@CircularProgressBar = CircularProgressBar {
  @Anchor = ();
  @Size = 48;
  Anchor: (...@Anchor, Width: @Size, Height: @Size);
  Value: 1.0;
  Background: #1a2030;
  Color: #aa7c4a;
  MaskTexturePath: "Common/CircularProgressBarMask.png";
};
```

Properties: `Value` (0-1 progress), `Background`, `Color`, `MaskTexturePath`. No segment/slice support. No click events. **Not usable as a radial menu.**

### 4.2 Usable Elements for a Simulated Radial

| Element | Role in Radial |
|---------|----------------|
| `Group` with `LayoutMode: Full` | Container with absolute positioning |
| `Button` / `TextButton` | Clickable segment buttons |
| `Sprite` | Custom segment/slice textures |
| `ItemIcon` | Item icon display in each segment |
| `Label` | Segment labels |
| `SceneBlur` | Background blur effect |

### 4.3 Available Events for Interaction

From `CustomUIEventBindingType`:

| Event | Value | Use in Radial |
|-------|-------|---------------|
| `Activating` | 0 | Segment click/select |
| `RightClicking` | 1 | Alternative action |
| `MouseEntered` | 3 | Segment hover highlight |
| `MouseExited` | 4 | Segment unhighlight |
| `MouseButtonReleased` | 12 | Selection on release |
| `Dismissing` | 8 | Menu close |

**Key limitation:** There is NO `MouseMove` or `MousePosition` event. The server cannot track the mouse cursor position in real-time. Hover detection is element-boundary-based only (enter/exit).

---

## 5. Custom UI Page Lifecycle — How to Open/Close

### 5.1 Opening a Custom Page

```java
Player player = store.getComponent(ref, Player.getComponentType());
PageManager pageManager = player.getPageManager();
MyRadialPage page = new MyRadialPage(playerRef);
pageManager.openCustomPage(ref, store, page);
```

### 5.2 Closing a Custom Page

From within the page:
```java
this.close();  // Sends Page.None to client
```

Or externally:
```java
pageManager.setPage(ref, store, Page.None);
```

### 5.3 Page Lifetime Options

| Lifetime | Behavior |
|----------|----------|
| `CantClose` | Player cannot close (no ESC) |
| `CanDismiss` | Player can press ESC to close |
| `CanDismissOrCloseThroughInteraction` | ESC or interaction closes |

### 5.4 Page Enum (Built-in Pages)

```java
public enum Page {
   None(0),    // No page (close)
   Bench(1),   // Crafting bench
   Inventory(2),
   ToolsSettings(3),
   Map(4),
   MachinimaEditor(5),
   ContentCreation(6),
   Custom(7)   // Custom UI page
}
```

### 5.5 Custom HUD (Always-Visible Overlay)

For a radial that overlays the game without stealing input, use `CustomUIHud`:

```java
public abstract class CustomUIHud {
    public void show();                                    // Build and send
    public void update(boolean clear, UICommandBuilder cmd); // Incremental update
    protected abstract void build(UICommandBuilder cmd);    // Define layout
}

// Usage:
HudManager hudManager = player.getHudManager();
hudManager.setCustomHud(playerRef, myHud);    // Show
hudManager.setCustomHud(playerRef, null);     // Hide
```

**Key difference from Page:** HUD overlays are display-only — NO event bindings, NO user interaction. If you need clickable segments, you MUST use `CustomUIPage`.

---

## 6. Simulation Strategy — Faking a Radial Menu

### 6.1 Architecture

Since no native radial element exists, a radial menu must be simulated:

```
┌─────────────────────────────────────────────────┐
│  Full-screen Group (LayoutMode: Full)           │
│                                                  │
│    ┌─────────────────────────────┐               │
│    │  Center circle background  │               │
│    │  (CircularProgressBar      │               │
│    │   or Sprite)               │               │
│    └─────────────────────────────┘               │
│                                                  │
│    ┌──────┐                    ┌──────┐          │
│    │ Seg1 │                    │ Seg2 │          │
│    │(Btn) │                    │(Btn) │          │
│    └──────┘                    └──────┘          │
│                                                  │
│    ┌──────┐                    ┌──────┐          │
│    │ Seg4 │                    │ Seg3 │          │
│    │(Btn) │                    │(Btn) │          │
│    └──────┘                    └──────┘          │
│                                                  │
└─────────────────────────────────────────────────┘
```

### 6.2 Positioning Segments in a Circle

Since all positions are in absolute pixels, compute positions at build time:

```java
int segments = 8;
int radius = 120;     // pixels from center
int centerX = 200;    // center of radial menu (half of container width)
int centerY = 150;    // center of radial menu (half of container height)
int segSize = 48;     // segment button size

for (int i = 0; i < segments; i++) {
    double angle = (2 * Math.PI * i / segments) - (Math.PI / 2); // start at top
    int x = centerX + (int)(radius * Math.cos(angle)) - segSize / 2;
    int y = centerY + (int)(radius * Math.sin(angle)) - segSize / 2;

    cmd.append("#RadialContainer", "YourPlugin/RadialSegment.ui");
    String sel = "#RadialContainer[" + i + "]";
    cmd.set(sel + ".Left", x);
    cmd.set(sel + ".Top", y);
    cmd.set(sel + " #Icon.ItemId", items[i]);
    cmd.set(sel + " #Label.Text", labels[i]);

    evt.addEventBinding(CustomUIEventBindingType.Activating, sel,
        new EventData().append("Action", "select").append("Index", String.valueOf(i)), false);
    evt.addEventBinding(CustomUIEventBindingType.MouseEntered, sel,
        new EventData().append("Action", "hover").append("Index", String.valueOf(i)), false);
    evt.addEventBinding(CustomUIEventBindingType.MouseExited, sel,
        new EventData().append("Action", "unhover").append("Index", String.valueOf(i)), false);
}
```

### 6.3 .ui File Structure for the Radial Menu

**RadialMenuPage.ui:**
```
$C = "../Common.ui";

Group {
    Anchor: (Full: 0);
    LayoutMode: Full;
    Background: #000000(0.4);   // Semi-transparent backdrop

    SceneBlur {
        Anchor: (Full: 0);
    }

    // Container for segments (absolute positioning)
    Group #RadialContainer {
        Anchor: (Width: 400, Height: 400);
        LayoutMode: Full;
        // Centered on screen via parent
    }

    // Center piece
    Group #CenterIcon {
        Anchor: (Width: 64, Height: 64, Left: 168, Top: 168);
        Background: #1a2030(0.9);
    }

    // Title label
    Label #HoverLabel {
        Anchor: (Width: 200, Height: 30, Left: 100, Top: 410);
        Style: (FontSize: 16, TextColor: #ffffff, HorizontalAlignment: Center);
        Text: "";
    }
}
```

**RadialSegment.ui (template for each segment):**
```
$C = "../Common.ui";

$C.@Button {
    @Anchor = (Width: 56, Height: 56);
    ItemIcon #Icon {
        ItemId: "";
        Anchor: (Full: 4);
        ShowItemTooltip: false;
    }
}
```

### 6.4 Hover Handling

Since there's no continuous mouse position tracking, hover is rectangle-based per element:

- `MouseEntered` fires when the cursor enters a segment's bounding box
- `MouseExited` fires when it leaves
- The server updates the visual state via `sendUpdate()`

```java
@Override
public void handleDataEvent(..., EventData data) {
    if ("hover".equals(data.action)) {
        UICommandBuilder cmd = new UICommandBuilder();
        // Highlight hovered segment
        cmd.set("#RadialContainer[" + data.index + "].Background", "#3a7bd5(0.8)");
        cmd.set("#HoverLabel.Text", getSegmentLabel(data.index));
        this.sendUpdate(cmd, false);
    } else if ("unhover".equals(data.action)) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#RadialContainer[" + data.index + "].Background", "#1a2030(0.6)");
        cmd.set("#HoverLabel.Text", "");
        this.sendUpdate(cmd, false);
    } else if ("select".equals(data.action)) {
        handleSelection(Integer.parseInt(data.index));
        this.close();
    }
}
```

---

## 7. Limitations & Gotchas

### 7.1 No Angular Mouse Detection

The engine provides NO way to detect mouse angle from center. Unlike a true radial menu where you'd compute `atan2(mouseY - centerY, mouseX - centerX)`, the Custom UI system only provides rectangle-based hover events. This means:

- Segments must be **rectangular buttons** arranged in a circle
- There will be **dead zones** between segments (gaps where no button is hovered)
- True **pie-slice segments** are not possible without client-side modifications

### 7.2 Server Round-Trip Latency

Every hover event requires a `client → server → client` round trip:
1. Client detects mouse enter/exit on element
2. Sends `CustomPageEvent` to server
3. Server processes and sends `sendUpdate()` back
4. Client updates visual state

On local servers this is imperceptible. On remote servers there will be noticeable delay on hover highlighting.

### 7.3 Input Capture

When a `CustomUIPage` is open:
- **All keyboard and mouse input is captured** by the UI
- The player cannot move, look around, or interact with the world
- The cursor is visible and free-moving
- ESC closes the page (if `CanDismiss` lifetime)

This is **different from the built-in emote wheel** which likely allows looking while open. There is no way to open a Custom UI page that only partially captures input.

### 7.4 No Animation Support in Custom UI

The `.ui` DSL has no CSS transitions, keyframe animations, or tweening. Elements cannot smoothly animate position, opacity, or scale. Changes are instantaneous. A radial menu will "pop" open rather than animate.

The only animated element is `Sprite` with frame-based animation (flipbook), which could be used for decorative effects but not layout animation.

### 7.5 Maximum Practical Segment Count

Given rectangular hover zones and the need for visual clarity:
- **4 segments**: Very comfortable, large buttons, minimal dead zones
- **6 segments**: Good balance of options and usability
- **8 segments**: Maximum practical — segments start overlapping if too small
- **12+**: Not recommended — dead zones become problematic

---

## 8. Alternative Approaches

### 8.1 Grid-Based "Radial" (Recommended for Simplicity)

Instead of computing circular positions, use a 3×3 grid with the center empty:

```
$C = "../Common.ui";

Group {
    Anchor: (Width: 200, Height: 200);
    LayoutMode: Full;
    Background: #000000(0.5);

    // Top row
    Button #Seg0 { Anchor: (Left: 0,   Top: 0,   Width: 56, Height: 56); }
    Button #Seg1 { Anchor: (Left: 72,  Top: 0,   Width: 56, Height: 56); }
    Button #Seg2 { Anchor: (Left: 144, Top: 0,   Width: 56, Height: 56); }

    // Middle row (no center)
    Button #Seg3 { Anchor: (Left: 0,   Top: 72,  Width: 56, Height: 56); }
    // center is empty
    Button #Seg4 { Anchor: (Left: 144, Top: 72,  Width: 56, Height: 56); }

    // Bottom row
    Button #Seg5 { Anchor: (Left: 0,   Top: 144, Width: 56, Height: 56); }
    Button #Seg6 { Anchor: (Left: 72,  Top: 144, Width: 56, Height: 56); }
    Button #Seg7 { Anchor: (Left: 144, Top: 144, Width: 56, Height: 56); }
}
```

**Pros:** Simpler positioning, no dead zones, no trigonometry.
**Cons:** Looks like a grid, not a radial — less visually polished.

### 8.2 Circular Layout with Sprite Backgrounds

Use `Sprite` elements with custom pie-slice textures to create the visual appearance of a radial menu, even though the hit regions are rectangular:

```
// Each segment is a Group with:
// - A Sprite with a pie-slice texture (visual)
// - An invisible Button overlay (hit region, slightly smaller)
```

This gives the visual polish of a radial with rectangular click regions.

### 8.3 Tab-Based Quick Select (No Radial)

Use `TabNavigation` for category selection + `ItemGrid` for items — a flat menu that achieves the same goal (quick item/action selection) without needing circular layout.

---

## 9. Triggering the Radial Menu from Gameplay

Based on [input-detection-research.md](../plugins/input-detection-research.md):

| Trigger | Server Detection | Method |
|---------|-----------------|--------|
| Crouch + Right-click + held item | YES | `PlayerMouseButtonEvent` + `MovementStatesComponent.crouching` |
| Middle click (Pick) + held item | YES | `SyncInteractionChains` packet with `InteractionType.Pick` |
| Chat command (`/stencil`) | YES | Command registration |
| Emote key press | NO | Client-only, no packet sent |
| Custom keybind | NO | No custom keybind API exists |

### Opening the Page on Trigger

```java
// In your event handler:
Player player = store.getComponent(ref, Player.getComponentType());
PageManager pageManager = player.getPageManager();
if (pageManager.getCustomPage() == null) {  // Don't override existing page
    RadialMenuPage radialPage = new RadialMenuPage(playerRef, availableOptions);
    pageManager.openCustomPage(ref, store, radialPage);
}
```

### CustomPageEvent for Selection Communication

When the player selects a segment, the client sends `CustomPageEvent` (packet ID 219):

```java
public class CustomPageEvent implements Packet {
    public CustomPageEventType type;  // Acknowledge, Data, Dismiss
    public String data;              // JSON payload (up to 4MB)
}
```

The server receives this in `PageManager.handleEvent()` which dispatches to your page's `handleDataEvent()`.

---

## 10. Files Searched — No Radial Menu Files Found

### Decompiled Java — No radial menu classes

| Searched Pattern | Result |
|-----------------|--------|
| `RadialMenu`, `WheelMenu`, `EmoteMenu`, `OffhandMenu` | NOT FOUND |
| `emote.*menu`, `emote.*wheel`, `offhand.*menu` | NOT FOUND |
| `radial`, `wheel` (in Hytale packages) | Only `HashedWheelTimer` (Netty), `MouseEvent.Wheel` (jline) |

### .ui Files — No radial/wheel layouts

| Searched Pattern | Result |
|-----------------|--------|
| `radial`, `wheel`, `circular`, `segment` in `.ui` files | Only `CircularProgressBar` (not a menu) |
| `emote*.ui`, `offhand*.ui`, `wheel*.ui` | NOT FOUND |

### Reference Assets — Emote data only

| File | Content |
|------|---------|
| `Cosmetics/CharacterCreator/EmotesInGame.json` | Emote definitions (Id, Animation, Name, Icon) |
| `Cosmetics/CharacterCreator/Emotes.json` | Character animations (Idle, Walk, Run, etc.) |
| `Common/Languages/*/emotes.lang` | Emote localization strings |

---

## 11. Key Classes Reference

| Class | Package | Relevance |
|-------|---------|-----------|
| `CustomUIPage` | `server.core.entity.entities.player.pages` | Base class for custom pages |
| `InteractiveCustomUIPage<T>` | Same | Interactive page with typed event data |
| `BasicCustomUIPage` | Same | Simplified page (build only) |
| `PageManager` | Same | Opens/closes pages, handles events |
| `CustomUIHud` | `server.core.entity.entities.player.hud` | Always-visible HUD overlay (no interaction) |
| `HudManager` | Same | Controls HUD component visibility |
| `HudComponent` | `protocol.packets.interface_` | Enum of built-in HUD elements |
| `Page` | Same | Enum of built-in page types |
| `CustomPageLifetime` | Same | How a page can be closed |
| `CustomUIEventBindingType` | Same | UI event types (Activating, MouseEntered, etc.) |
| `CustomUICommandType` | Same | UI command types (Append, Set, Clear, etc.) |
| `UICommandBuilder` | `server.core.ui.builder` | Builds UI manipulation commands |
| `UIEventBuilder` | Same | Builds event bindings |
| `EmoteCommand` | `server.core.cosmetics.commands` | Server-side emote execution |
| `Emote` | `server.core.cosmetics` | Emote data class |

---

## 12. Definitive Answer

**Can we create a custom radial menu via the plugin API?**

**YES, but with significant limitations:**

1. **Visual simulation only** — No native radial element. Must use absolute-positioned rectangular buttons arranged in a circle.
2. **No angular mouse tracking** — Cannot compute which "pie slice" the mouse is over. Only rectangular hit regions via `MouseEntered`/`MouseExited`.
3. **Full input capture** — The page steals all input; player cannot move while menu is open (unlike the built-in emote wheel).
4. **Server round-trip for hover** — Hover state changes require network round-trip, adding latency.
5. **No animations** — Menu appears/disappears instantly.

**Recommended approach for a practical "quick select" menu:**
- Use a `CustomUIPage` with `LayoutMode: Full`
- Position 4–8 `Button` elements in a circle using pre-computed coordinates
- Use `MouseEntered`/`MouseExited` for hover highlights
- Use `Activating` for selection
- Use custom `Sprite` textures with pie-slice graphics for visual polish
- Trigger via crouch + right-click detection on `PlayerMouseButtonEvent`

---

## See Also

- [Custom UI Overview](./custom-ui-overview.md) — Full Custom UI system documentation
- [UI Element Reference](./ui-element-reference.md) — All available elements and properties
- [Input Detection Research](../plugins/input-detection-research.md) — How to detect crouch + click triggers
- [Collapsible Section Pattern](./collapsible-section-pattern.md) — Visible toggling pattern (related technique)
- [Hotbar in Custom UI](./hotbar-in-custom-ui.md) — HudComponent visibility management
