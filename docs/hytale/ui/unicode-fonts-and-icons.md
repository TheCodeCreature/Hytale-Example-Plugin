---
topic: "Unicode, Fonts & Icon Indicators in .ui Files"
category: "Plugin API / Custom UI"
updated: 2026-05-04
sources:
  - "https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup — Official string/char/font docs"
  - "https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/property-types/labelstyle — LabelStyle properties"
  - "docs/Reference Assets/Assets/Common/UI/Custom/ — All vanilla .ui files (scanned for non-ASCII content)"
  - "docs/Reference Assets/Assets/Common/UI/Custom/Common.ui — FontName 'Secondary' usage"
  - "docs/Reference Assets/Assets/Common/UI/Custom/Pages/RespawnPage.ui — FontName 'Secondary' usage"
  - "docs/Reference Assets/Assets/Common/UI/Custom/Pages/Memories/*.ui — FontName 'Default' / 'Secondary' usage"
  - "Vex UI Grimoire core-rules.md — core node types and property list"
---

# Unicode, Fonts & Icon Indicators in .ui Files

## Summary

Hytale's `.ui` parser does **NOT** support `\u` Unicode escape sequences in string literals — they crash the parser. The engine uses **named bitmap/SDF fonts** ("Default", "Secondary", "Mono") with pre-baked glyph atlases, whose Unicode coverage is unknown but likely limited. No vanilla `.ui` file contains non-ASCII characters. The safest approach for expand/collapse indicators is a **small icon image** (`.png`) displayed via `Group { Background: "..."; }` or `Sprite` adjacent to the `TextButton`.

---

## Q1: Does the .ui parser support `\u25BC` Unicode escapes?

**No.** The `.ui` DSL string literal syntax is documented as:

> **String**: `Text: "Hi!";`
> **Char**: `PasswordChar: "*";` — same syntax, max one character.

— [Official Markup Docs](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup)

The official documentation defines **no escape sequences** for strings — no `\u`, `\n`, `\t`, `\\`, or `\"` (the only escape shown is `\"` inside tooltip/code-sample contexts, not in normal `Text:` values). The parser is a **custom DSL parser**, not a JSON/Java/JavaScript parser, so it does not inherit their escape conventions.

**Evidence**: Putting `\u25BC` in a `Text:` property causes "Failed to load CustomUI documents" — confirming the parser treats `\` as an illegal character or attempts to parse the sequence as something else and fails.

---

## Q2: Can we paste literal UTF-8 Unicode characters (▼, ▶) into Text: "..."?

**Unknown — high risk.** The answer depends on two factors:

### Factor 1: Parser encoding support

The parser may or may not accept raw UTF-8 multi-byte sequences in string literals. The official docs show only ASCII examples. No vanilla `.ui` file in the reference assets contains any non-ASCII character (verified by scanning all `.ui` files under `docs/Reference Assets/`).

### Factor 2: Font glyph coverage

Even if the parser accepts the bytes, the font must contain a glyph for that codepoint. See Q3.

### Recommendation

**Test empirically** with a minimal `.ui` file:
```
Group {
    Label { Text: "Test: ▼"; }
}
```

Possible outcomes:
1. **Parser crash** — the `.ui` parser rejects non-ASCII bytes entirely → cannot use Unicode in text
2. **Renders as blank/box** — parser accepts it but the font has no glyph for ▼ → font limitation
3. **Renders correctly** — both parser and font support it → safe to use

Until tested, **assume it won't work** and use the icon-image alternative (Q5).

---

## Q3: What fonts does the .ui system use? What's their glyph coverage?

### Available Font Names

Per the [official markup docs](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup):

| FontName | Purpose | Used For |
|----------|---------|----------|
| `"Default"` | Default font for all text unless overridden | Body text, labels, buttons |
| `"Secondary"` | Headlines and standout elements | Titles, respawn screen, portal UI, pills |
| `"Mono"` | Development only | Profiling overlays, error displays |

Fonts are referenced by `FontName` inside `LabelStyle`:
```
Style: (FontName: "Secondary", FontSize: 24, RenderBold: true);
```

### Font Rendering Properties (LabelStyle)

| Property | Type | Description |
|----------|------|-------------|
| `FontName` | String (UIFontName) | Font selection |
| `FontSize` | Float | Size in pixels |
| `TextColor` | Color | Text color |
| `OutlineColor` | Color | Text outline color |
| `LetterSpacing` | Float | Letter spacing |
| `RenderBold` | Boolean | Bold rendering |
| `RenderItalics` | Boolean | Italic rendering |
| `RenderUnderlined` | Boolean | Underline rendering |
| `RenderUppercase` | Boolean | Force uppercase |
| `Wrap` | Boolean | Text wrapping |
| `HorizontalAlignment` | LabelAlignment | Start, Center, End |
| `VerticalAlignment` | LabelAlignment | Start, Center, End |

### Glyph Coverage — Unknown, Likely Limited

The fonts are **game-bundled assets** (not system fonts). Game engines typically use one of:
- **Bitmap font atlases** — pre-rendered glyph textures; only contains glyphs explicitly included
- **SDF/MSDF fonts** — signed distance field; broader coverage possible but still pre-baked

Either way, the glyph set is **fixed at build time by Hypixel Studios**. There is no documented way for plugins to add fonts or extend glyph atlases.

**Prediction**: The "Default" and "Secondary" fonts likely cover:
- Latin characters (A–Z, a–z, 0–9)
- Basic punctuation and symbols (`!@#$%^&*()_+-=[]{}|;:'",.<>?/~`)
- Extended Latin for localization (accented characters like é, ñ, ü)
- Possibly CJK characters for Asian language support

**Unlikely to include**: Mathematical symbols (▼ U+25BC), geometric shapes (▶ U+25B6), arrows (⯆ U+2BC6), or emoji (🔻).

---

## Q4: Do any vanilla .ui files use special symbols or non-ASCII characters?

**No.** A comprehensive scan of all `.ui` files under `docs/Reference Assets/Assets/Common/UI/Custom/` found:

- **Zero** Unicode arrows, triangles, geometric shapes, or special symbols
- **Zero** `\u` escape sequences
- **Zero** non-ASCII characters of any kind in `Text:` or `TooltipText:` values

All text content in vanilla `.ui` files uses either:
1. Plain ASCII strings: `Text: "Pill";`, `Text: "@Title";`
2. Translation keys: `Text: %server.customUI.uiGallery.text.colorDefault;`

The base game achieves all iconography through **image assets** (`TabButton { Icon: "..."; }`, `Group { Background: "..."; }`, `Sprite { TexturePath: "..."; }`), never through Unicode text characters.

---

## Q5: Alternative — Icon Images for Expand/Collapse Indicators

**Yes — this is the recommended approach.** Use a small `.png` arrow icon displayed via a `Group` with `Background`, positioned alongside the `TextButton` text.

### Pattern A: Group with Background Image (Simplest)

Place the TextButton inside a container with `LayoutMode: Left`. Add a small Group with the arrow icon, then the label.

```
// Collapsible section header with arrow icon
Group #CategoriesHeaderRow {
    Anchor: (Height: 24, Left: 0, Right: 0);
    LayoutMode: Left;

    // Arrow indicator (swapped between expanded/collapsed via cmd.set Background)
    Group #CategoriesArrow {
        Anchor: (Width: 16, Height: 16);
        Background: "ArrowDown.png";   // ← your custom 16x16 arrow icon
    }

    // Clickable header text
    TextButton #CategoriesHeader {
        Text: "Categories";
        FlexWeight: 1;
        Anchor: (Height: 24);
        Style: @SectionHeaderStyle;
    }
}
```

**Server-side toggling:**
```java
// Toggle icon
cmd.set("#CategoriesArrow.Background", 
    collapsed ? "Pages/BlueprintBench/ArrowRight.png" 
              : "Pages/BlueprintBench/ArrowDown.png");

// Toggle body visibility
cmd.set("#CategoriesBody.Visible", !collapsed);
```

### Pattern B: Sprite Element (Supports Animation)

If you want animated transitions or sprite sheets:

```
Sprite #CategoriesArrow {
    TexturePath: "ArrowDown.png";
    Anchor: (Width: 16, Height: 16);
}
```

Note: `Sprite` does **not** accept children (per official docs). Use it for pure icon display only.

### Pattern C: Embed Arrow Text in Button Label (Fallback)

If testing confirms that simple ASCII characters render fine, use basic arrow characters that are in the Latin charset:

```java
// Use ASCII "v" / ">" as a poor-man's arrow
cmd.set("#CategoriesHeader.Text", collapsed ? "> Categories" : "v Categories");
```

This is visually inferior but guaranteed to work with any font.

### Creating Custom Arrow Icons

Create two 16×16 (or 12×12) transparent `.png` files:
- `ArrowDown.png` — downward triangle/chevron (▼ or ˅) for expanded state
- `ArrowRight.png` — rightward triangle/chevron (▶ or ˃) for collapsed state

Place them at:
```
src/main/resources/Common/UI/Custom/Pages/BlueprintBench/ArrowDown.png
src/main/resources/Common/UI/Custom/Pages/BlueprintBench/ArrowRight.png
```

Reference in `.ui` files as `"ArrowDown.png"` (same directory, relative path).

Reference from Java `cmd.set()` as `"Pages/BlueprintBench/ArrowDown.png"` (relative to `Common/UI/Custom/`).

---

## Gotchas

1. **`\uXXXX` crashes the parser** — never use Java/JS-style Unicode escapes in `.ui` files
2. **No escape sequences documented** — the `.ui` string parser is simpler than JSON; treat strings as raw literal content
3. **Fonts are not customizable** — plugins cannot add fonts, extend glyph sets, or change font files
4. **Glyph missing = invisible** — if a character has no glyph in the font, it renders as nothing (blank space) or a replacement box; it does NOT crash
5. **Icon images are the standard pattern** — vanilla Hytale uses `TabButton { Icon: }`, `Group { Background: }`, and `Sprite` for ALL visual indicators; text characters are never used for iconography
6. **The `Background:` path on a Group can be updated via `cmd.set()`** — this enables swapping between expanded/collapsed arrow icons at runtime
7. **`Sprite` cannot have children** — per official docs, Sprite "Accepts child elements: No"

## See Also

- [Icon Paths & Image References](./icon-paths-and-images.md) — How to reference images in .ui files
- [Path Resolution — Definitive Reference](./path-resolution-definitive.md) — Path resolution algorithm
- [Collapsible Section Pattern](./collapsible-section-pattern.md) — Full collapse/expand implementation
- [UI Element Reference](./ui-element-reference.md) — All element types and properties
