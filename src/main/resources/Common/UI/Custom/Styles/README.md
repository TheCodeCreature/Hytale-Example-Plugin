# UI Styles Guide

## Folder Structure

```
Styles/
├── Backgrounds.ui — Background property tokens (colors + asset paths)
├── Buttons.ui     — All TextButtonStyle and ButtonStyle tokens
├── Labels.ui      — All LabelStyle tokens (headings, body, cost quantities)
├── Overlays.ui    — LabelStyle tokens with bold + outline (for use over images)
├── Entries.ui     — List/grid entry button styles (default, selected, unaffordable)
└── README.md      — This file
```

## Import Convention

From any `.ui` file inside `Pages/<Subsystem>/`, use:

```ui
$C = "../../Common.ui";
$BG = "../../Styles/Backgrounds.ui";
$B = "../../Styles/Buttons.ui";
$L = "../../Styles/Labels.ui";
$O = "../../Styles/Overlays.ui";
$E = "../../Styles/Entries.ui";
```

Only import what the file actually uses. Never import all four if you only need one.

## Usage in .ui Files

Reference tokens with the alias prefix:

```ui
Style: $B.@FilterActiveStyle;
Style: $L.@HeaderStyle;
Style: $O.@OverlayLabelStyle;
Style: $E.@EntryStyle;
Background: $BG.@DimOverlay;
Background: $BG.@ItemSlotFrame;
```

## Usage in Java (Value.ref)

```java
Value.ref("Styles/Buttons.ui", "FilterActiveStyle");
Value.ref("Styles/Labels.ui", "CostQuantityStyle");
Value.ref("Styles/Overlays.ui", "CostQuantityOverlayStyle");
Value.ref("Styles/Entries.ui", "EntryStyle");
```

The first argument is relative to `Common/UI/Custom/`.

Note: `Backgrounds.ui` tokens are only used in `.ui` layout files, not from Java.

## Rules

1. **No inline styles in production .ui files.** Every `Style:` property must reference a named token from this folder. Inline styles are only acceptable in throwaway test/mock files.

2. **One token, one definition.** If two subsystems need the same style, it lives here. Never duplicate a style definition across files.

3. **Reuse before creating.** Before adding a new token, check if an existing one fits. Prefer adjusting your layout to match an existing style over creating a near-duplicate.

4. **File placement by type, not by subsystem.** A button style goes in `Buttons.ui` regardless of which page uses it. This prevents style sprawl and makes tokens discoverable.

5. **No per-page style files.** All named styles live in `Styles/`. Subsystem folders (`Pages/StencilBook/`, `Pages/StencilRadial/`) contain only layout `.ui` files — no `*Styles.ui` files.

6. **Leverage $C.@ for backgrounds.** Button styles should reference `$C.@DefaultSquareButtonDefaultBackground`, `$C.@TertiaryDefaultButtonBackground`, etc. from `Common.ui` rather than hardcoding texture paths or colors for backgrounds. This ensures consistency with the engine's built-in theme.

7. **Naming conventions:**
   - Label styles: `@{Purpose}Style` — e.g. `@HeaderStyle`, `@SubtextStyle`, `@CostQuantityStyle`
   - Button styles: `@{Purpose}ButtonStyle` or `@{Purpose}Style` — e.g. `@NavButtonStyle`, `@FilterActiveStyle`
   - Entry styles: `@{State}EntryStyle` — e.g. `@SelectedEntryStyle`, `@UnaffordableEntryStyle`
   - Overlay variants: `@{Purpose}OverlayStyle` or place in `Overlays.ui`

8. **Explicit approval required to deviate.** Any inline style or new per-page style file requires explicit approval. This is not a suggestion — it is a hard constraint for production code.

## Adding a New Style

1. Determine which file it belongs in (Buttons, Labels, Overlays, or Entries)
2. Check that no existing token already covers the use case
3. Add the token with a comment noting its section
4. Use `$C.@` references for backgrounds/colors where applicable
5. Reference it immediately from the consuming `.ui` file — never leave orphaned tokens

## When to Create a New Style File

Only if a genuinely new **category** of UI element emerges that doesn't fit Buttons, Labels, Overlays, or Entries. Examples that would warrant a new file:
- `Tooltips.ui` — if we build a custom tooltip system
- `Sliders.ui` — if we add slider/range inputs

Do **not** create a new file for a single token or a subsystem-specific variant.
