# Design: Semantic Background Tokens

## 1. Overview

Introduces a centralized `Backgrounds.ui` token file to eliminate all hardcoded color and asset-path literals from production `.ui` layout files. Every `Background:` property currently using an inline color or image path will reference a named semantic token instead, following the same pattern established by `Buttons.ui`, `Labels.ui`, etc.

## 2. Design Priorities

1. **Single source of truth** — one place to change a color/asset, all consumers update
2. **Readability** — semantic names (`@DimOverlay`) instantly communicate intent vs. `#000000(0.5)`
3. **Consistency with existing system** — same `$ALIAS.@Token` import/reference pattern
4. **Minimal disruption** — mechanical find-and-replace, no structural changes to layout files

## 3. Token Inventory

### New File: `Styles/Backgrounds.ui`

| Token | Value | Semantic Meaning | Occurrences |
|-------|-------|-----------------|-------------|
| `@DividerColor` | `#2b3542` | Vertical/horizontal column divider | 2 |
| `@DimOverlay` | `#000000(0.5)` | Dim overlay (unaffordable cell / modal backdrop) | 5 |
| `@IconContainerDark` | `#0d1520(0.85)` | Dark background behind radial cost icon | 1 |
| `@SegmentDefaultBg` | `#1a2030(0.7)` | Radial segment button resting state | 1 |
| `@CategoryCellBg` | `#141c26` | Material category icon cell background | 1 |
| `@FilterActiveHighlight` | `#2a4a6a(0.6)` | Active/selected filter overlay highlight | 2 |
| `@ItemSlotFrame` | `"../Common/BlockSelectorSlotBackground.png"` | Item icon slot frame background | 5 |
| `@TrashIcon` | `"../Common/Icons/AssetNotifications/Trash.png"` | Delete/clear action icon | 4 |

> **Path resolution note:** Asset paths in token definitions resolve relative to the file where the token is defined (`Styles/Backgrounds.ui`). From `Styles/`, `../Common/` resolves to `Custom/Common/` — confirmed by how `Common.ui`'s PatchStyle paths work when consumed from `Styles/Buttons.ui`.

## 4. Import Convention

From any `.ui` file inside `Pages/<Subsystem>/`:

```ui
$BG = "../../Styles/Backgrounds.ui";
```

## 5. Replacement Plan

### 5.1 BlueprintBenchPage.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 163 | `Background: #2b3542;` | `Background: $BG.@DividerColor;` |
| 184 | `Background: #2b3542;` | `Background: $BG.@DividerColor;` |
| 220 | `Background: #000000(0.5);` | `Background: $BG.@DimOverlay;` |
| 111 | `Background: "../../Common/Icons/AssetNotifications/Trash.png";` | `Background: $BG.@TrashIcon;` |
| 145 | `Background: "../../Common/Icons/AssetNotifications/Trash.png";` | `Background: $BG.@TrashIcon;` |
| 262 | `Background: "../../Common/Icons/AssetNotifications/Trash.png";` | `Background: $BG.@TrashIcon;` |

### 5.2 CostCell.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 13 | `Background: "../../Common/BlockSelectorSlotBackground.png";` | `Background: $BG.@ItemSlotFrame;` |
| 19 | `Background: #000000(0.5);` | `Background: $BG.@DimOverlay;` |

### 5.3 RecipeIconCell.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 12 | `Background: "../../Common/BlockSelectorSlotBackground.png";` | `Background: $BG.@ItemSlotFrame;` |
| 21 | `Background: #000000(0.5);` | `Background: $BG.@DimOverlay;` |

### 5.4 GroupFilterButton.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 16 | `Background: "../../Common/BlockSelectorSlotBackground.png";` | `Background: $BG.@ItemSlotFrame;` |
| 26 | `Background: #2a4a6a(0.6);` | `Background: $BG.@FilterActiveHighlight;` |

### 5.5 ExactItemFilterButton.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 14 | `Background: "../../Common/BlockSelectorSlotBackground.png";` | `Background: $BG.@ItemSlotFrame;` |
| 22 | `Background: #2a4a6a(0.6);` | `Background: $BG.@FilterActiveHighlight;` |

### 5.6 StencilRadialMenu.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 12 | `Background: #000000(0.5);` | `Background: $BG.@DimOverlay;` |
| 82 | `Background: "../../Common/Icons/AssetNotifications/Trash.png";` | `Background: $BG.@TrashIcon;` |

### 5.7 StencilRadialCostSlot.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 14 | `Background: #0d1520(0.85);` | `Background: $BG.@IconContainerDark;` |
| 20 | `Background: #000000(0.5);` | `Background: $BG.@DimOverlay;` |

### 5.8 StencilRadialSegment.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 18 | `Background: #1a2030(0.7);` | `Background: $BG.@SegmentDefaultBg;` |

### 5.9 MaterialGroupButton.ui

**Add import:**
```ui
$BG = "../../Styles/Backgrounds.ui";
```

**Replacements:**

| Line | Old | New |
|------|-----|-----|
| 8 | `Background: #141c26;` | `Background: $BG.@CategoryCellBg;` |

## 6. Files NOT Changed

| File | Reason |
|------|--------|
| `ItemGridTestPage.ui` | Throwaway test file — `#333333` stays inline |
| `Buttons.ui` (`@DestructiveButtonStyle`, `@RadialSegmentButtonStyle`) | Intentional custom button-state colors — per constraints, left alone |

## 7. README Update

Add `Backgrounds.ui` to the folder structure and import convention sections of `Styles/README.md`:

```
Styles/
├── Backgrounds.ui — Background property tokens (colors + asset paths)
├── Buttons.ui     — All TextButtonStyle and ButtonStyle tokens
├── Labels.ui      — All LabelStyle tokens
├── Overlays.ui    — LabelStyle tokens with bold + outline
├── Entries.ui     — List/grid entry button styles
└── README.md      — This file
```

Import line: `$BG = "../../Styles/Backgrounds.ui";`

## 8. Open Questions

1. **Path token validation** — Asset path tokens (`@ItemSlotFrame`, `@TrashIcon`) have no existing precedent as raw string references in this engine. If the engine doesn't resolve string tokens as Background paths, fall back to keeping these as inline paths and only tokenize the color values. Test by loading a single page (e.g., CostCell.ui) after conversion.

## 9. Handoff Checklist

- [x] Token inventory with values and consumers
- [x] File-by-file replacement plan with exact old → new
- [x] Import changes specified per file
- [x] New file content defined
- [x] README update specified
- [x] Exclusions documented with rationale
- [x] Open Questions populated
- [x] Task Decomposition populated

## 10. Task Decomposition

### Wave 1 (no dependencies — can run in parallel)

#### Unit: Create `Styles/Backgrounds.ui`
- **Action**: Create new file with all 8 token definitions
- **Done when**: File exists with correct relative paths and color values

#### Unit: Update `Styles/README.md`
- **Action**: Add `Backgrounds.ui` to folder structure, import convention, and usage examples
- **Done when**: README reflects the new file

### Wave 2 (depends on Wave 1 — can run in parallel with each other)

#### Unit: BlueprintBenchPage.ui
- **Action**: Add `$BG` import, replace 6 inline values
- **Done when**: Zero hardcoded colors/paths remain (excluding test files)

#### Unit: CostCell.ui
- **Action**: Add `$BG` import, replace 2 inline values
- **Done when**: No inline Background literals

#### Unit: RecipeIconCell.ui
- **Action**: Add `$BG` import, replace 2 inline values
- **Done when**: No inline Background literals

#### Unit: GroupFilterButton.ui
- **Action**: Add `$BG` import, replace 2 inline values
- **Done when**: No inline Background literals

#### Unit: ExactItemFilterButton.ui
- **Action**: Add `$BG` import, replace 2 inline values
- **Done when**: No inline Background literals

#### Unit: StencilRadialMenu.ui
- **Action**: Add `$BG` import, replace 2 inline values
- **Done when**: No inline Background literals

#### Unit: StencilRadialCostSlot.ui
- **Action**: Add `$BG` import, replace 2 inline values
- **Done when**: No inline Background literals

#### Unit: StencilRadialSegment.ui
- **Action**: Add `$BG` import, replace 1 inline value
- **Done when**: No inline Background literals

#### Unit: MaterialGroupButton.ui
- **Action**: Add `$BG` import, replace 1 inline value
- **Done when**: No inline Background literals

### Wave 3 (integration — depends on Wave 2)

#### Unit: Validation
- **Action**: Load each page in-engine, verify backgrounds render correctly
- **Priority test**: CostCell.ui (tests both color token `@DimOverlay` and path token `@ItemSlotFrame`)
- **Done when**: All pages render identically to before the change
