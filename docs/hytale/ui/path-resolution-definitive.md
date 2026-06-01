---
topic: "UI Path Resolution — Definitive Reference"
category: "Plugin API / Custom UI"
updated: 2026-05-02
sources:
  - "https://hytalemodding.dev/en/docs/official-documentation/custom-ui/markup — UIPath type (OFFICIAL Hypixel Studios docs)"
  - "https://hytale-docs.com/docs/api/server-internals/custom-ui — cmd.append() root context"
  - "CommonAssetValidator.java — icon directory validation"
  - "AssetModule.java, AssetRegistryLoader.java — plugin asset pack loading"
  - "UICommandBuilder.java — append(), set() decompiled source"
  - "docs/Resources/Common/Pages/ — base game .ui files"
  - "src/main/resources/Common/UI/Custom/Pages/BlueprintBook/ — plugin .ui files (verified working)"
---

# UI Path Resolution — Definitive Reference

## Summary

Hytale has **two distinct path resolution contexts**: one for in-file asset references (textures, icons, imports), and one for server-side Java commands (`cmd.append()`, `cmd.set()`). Conflating these is the root cause of most icon-path bugs.

---

## 1. The Path Resolution Algorithm

### Context A: Inside `.ui` Files (UIPath type)

**Official rule** (from Hypixel Studios, `hytalemodding.dev/en/docs/official-documentation/custom-ui/markup`):

> Paths are always **relative to the file where they have been declared in**.
> Looking up `MyButton.png` in `Menu/MyAwesomeMenu.ui` will result in `Menu/MyButton.png`.
> If you want to reference a file that is in a parent folder you can go back one directory by writing two dots (`../MyButton.png`).

**Resolution algorithm (step by step):**

1. Take the **directory** of the current `.ui` file
2. Resolve the path string relative to that directory
3. The resolved path must land within the `Common/UI/Custom/` namespace

**Examples from official docs:**

| Path in `.ui` file | `.ui` file location | Resolved path |
|---|---|---|
| `MyButton.png` | `Menu/MyAwesomeMenu.ui` | `Menu/MyButton.png` |
| `../MyButton.png` | `Menu/MyAwesomeMenu.ui` | `MyButton.png` |
| `../../MyButton.png` | `Menu/Popup/Templates/MyAwesomeMenu.ui` | `Menu/MyButton.png` |

### Context B: Java `cmd.append()` and `cmd.append(selector, path)`

**Root**: `Common/UI/Custom/`

The path passed to `cmd.append()` is **always relative to `Common/UI/Custom/`**, regardless of which `.ui` file is currently loaded.

```java
// File at: src/main/resources/Common/UI/Custom/Pages/BlueprintBook/BlueprintBookPage.ui
cmd.append("Pages/BlueprintBook/BlueprintBookPage.ui");

// File at: src/main/resources/Common/UI/Custom/YourPlugin/MyPage.ui
cmd.append("YourPlugin/MyPage.ui");

// Append a sub-template into a container
cmd.append("#Container", "Pages/BlueprintBook/SetFilterButton.ui");
```

Source: hytale-docs.com official tutorial + decompiled UICommandBuilder.

### Context C: Java `cmd.set()` with texture paths

When setting a `Background` or other texture path via `cmd.set()`, the path is treated as a **UIPath relative to the `.ui` file that defines the target element**.

```java
// This sets the Background on an element defined in BlueprintBookPage.ui
// The path resolves relative to Pages/BlueprintBook/ directory
cmd.set("#GroupFrame.Background", "#2a4a6a");  // color literal — no path resolution
cmd.set("#GroupFrame.Background", "../../Common/RecipesIcon.png");  // relative to the .ui file
```

**Key distinction**: Color literals (`#2a4a6a`, `#141c26(0.0)`) are NOT paths — they're parsed as colors. String paths to `.png` files are resolved using the UIPath algorithm relative to the `.ui` file that *contains the target element*.

---

## 2. The Plugin Asset Pack File System

### Directory Structure

```
src/main/resources/
├── manifest.json                    ← "IncludesAssetPack": true (REQUIRED)
├── Common/
│   └── UI/
│       └── Custom/                  ← CLIENT-SIDE UI ROOT
│           ├── Common/              ← Shared textures/assets for your plugin
│           │   ├── RecipesIcon.png
│           │   ├── BlockSelectorSlotBackground.png
│           │   └── SearchIcon.png
│           └── Pages/
│               └── BlueprintBook/
│                   ├── BlueprintBookPage.ui
│                   ├── SetFilterButton.ui
│                   ├── CostCell.ui
│                   └── ...
└── Server/                          ← SERVER-SIDE ASSETS (items, blocks, etc.)
    └── Item/
        └── ...
```

### How the Plugin Overlays the Game's Filesystem

When `"IncludesAssetPack": true` is set in `manifest.json`:

1. The plugin JAR is opened as a `ZipFileSystem`
2. The `Common/UI/Custom/` subtree is **merged** with the game's `Common/UI/Custom/` namespace
3. Plugin files are accessible at the SAME path roots as base game files
4. **Plugin `Common/UI/Custom/Common/` IS the same namespace as the game's `Common/UI/Custom/Common/`**

This means:
- A plugin can place textures at `Common/UI/Custom/Common/MyIcon.png`
- These are accessible from ANY `.ui` file using relative path traversal to `Common/MyIcon.png`
- The game's own `Common/UI/Custom/Common/RecipesIcon.png` is accessible from plugin `.ui` files via the same relative path

### Is Plugin `Common/` the SAME as Game `Common/`?

**Yes, within the `Common/UI/Custom/` namespace.** The client merges all asset packs into a single virtual filesystem rooted at `Common/UI/Custom/`. A plugin's `Common/UI/Custom/Common/` folder IS in the same directory as the game's `Common/UI/Custom/Common/` folder. Files from either source are resolved identically.

---

## 3. Complete Path Reference Table

### All Working Texture Paths in `.ui` Files

| `.ui` file location (under `Custom/`) | Target asset | Path used | Resolved to (under `Custom/`) |
|---|---|---|---|
| `Pages/BlueprintBook/BlueprintBookPage.ui` | `Common/RecipesIcon.png` | `"../../Common/RecipesIcon.png"` | `Common/RecipesIcon.png` |
| `Pages/BlueprintBook/BlueprintBookPage.ui` | `Common/BlockSelectorSlotBackground.png` | `"../../Common/BlockSelectorSlotBackground.png"` | `Common/BlockSelectorSlotBackground.png` |
| `Pages/BlueprintBook/CostCell.ui` | `Common/BlockSelectorSlotBackground.png` | `"../../Common/BlockSelectorSlotBackground.png"` | `Common/BlockSelectorSlotBackground.png` |
| `Pages/BlueprintBook/RecipeIconCell.ui` | `Common/BlockSelectorSlotBackground.png` | `"../../Common/BlockSelectorSlotBackground.png"` | `Common/BlockSelectorSlotBackground.png` |
| `Pages/EntitySpawnPage.ui` | `Common/BlockSelectorSlotBackground.png` | `"../Common/BlockSelectorSlotBackground.png"` | `Common/BlockSelectorSlotBackground.png` |
| `Pages/EntitySpawnPage.ui` | `Common/BlockSelectorSlotDropIcon.png` | `"../Common/BlockSelectorSlotDropIcon.png"` | `Common/BlockSelectorSlotDropIcon.png` |
| `Pages/EntitySpawnPage.ui` | `Common/ContainerPanelPatch.png` | `"../Common/ContainerPanelPatch.png"` | `Common/ContainerPanelPatch.png` |
| `Pages/UIGallery/Categories/ButtonsContent.ui` | `Common/RecipesIcon.png` | `"../../../Common/RecipesIcon.png"` | `Common/RecipesIcon.png` |
| `Pages/UIGallery/Categories/ContainersContent.ui` | `Common/RecipesIcon.png` | `"../../../Common/RecipesIcon.png"` | `Common/RecipesIcon.png` |
| `Pages/UIGallery/Categories/InputContent.ui` | `Common/SearchIcon.png` | `"../../../Common/SearchIcon.png"` | `Common/SearchIcon.png` |

### All Working `$C` Import Paths

| `.ui` file location (under `Custom/`) | Import statement | Resolved to |
|---|---|---|
| `Pages/BlueprintBook/*.ui` | `$C = "../../Common.ui";` | `Common.ui` |
| `Pages/EntitySpawnPage.ui` | `$C = "../Common.ui";` | `Common.ui` |
| `Pages/UIGallery/Categories/*.ui` | `$C = "../../../Common.ui";` | `Common.ui` |

**Pattern**: Count the directory depth from `Custom/` to your `.ui` file, then use that many `../` to reach back to the `Custom/` root, then reference `Common.ui`.

### All `cmd.append()` Paths in Java

| Java code | Resolved to (under `Custom/`) |
|---|---|
| `cmd.append("Pages/BlueprintBook/BlueprintBookPage.ui")` | `Pages/BlueprintBook/BlueprintBookPage.ui` |
| `cmd.append("#SetFilters", "Pages/BlueprintBook/SetFilterButton.ui")` | `Pages/BlueprintBook/SetFilterButton.ui` |
| `cmd.append("#MaterialGroups", "Pages/BlueprintBook/MaterialGroupButton.ui")` | `Pages/BlueprintBook/MaterialGroupButton.ui` |
| `cmd.append("#CostGrid", "Pages/BlueprintBook/CostCell.ui")` | `Pages/BlueprintBook/CostCell.ui` |

### All `cmd.set()` Calls with Path-Like Values in Java

| Java code | Value type | Resolution |
|---|---|---|
| `cmd.set("#GroupFrame.Background", "#2a4a6a")` | **Color literal** | No path resolution — parsed as hex color |
| `cmd.set("#GroupFrame.Background", "#141c26(0.0)")` | **Color literal** | No path resolution — parsed as hex color with alpha |
| `cmd.set("#OutputIcon.ItemId", "Rock_Stone")` | **Item ID** | Not a path — looked up in asset registry |
| `cmd.set("#CostIcon.ItemId", itemId)` | **Item ID** | Not a path — looked up in asset registry |
| `cmd.set("#StatusLabel.Text", "some text")` | **String** | Not a path — plain text |

**Note**: In our codebase, no `cmd.set()` call currently sets a texture path dynamically. All texture paths are defined statically in `.ui` files. This is the recommended pattern.

---

## 4. The `Common/` Folder — What's Actually There

### Game's `Common/UI/Custom/Common/` Directory

These shared UI textures are shipped with the base game client and available to all `.ui` files:

| File | Purpose | Used by |
|------|---------|---------|
| `RecipesIcon.png` | Generic crafting/recipe tab icon | TabButtons, icon buttons |
| `BlockSelectorSlotBackground.png` | Background for item grid slots | ItemGrid SlotBackground, Group Background |
| `BlockSelectorSlotDropIcon.png` | Drop zone indicator icon | ItemGrid drop targets |
| `ContainerPanelPatch.png` | 9-slice container background (PatchStyle) | Container backgrounds |
| `SearchIcon.png` | Search input magnifying glass | CompactTextField Decoration.Icon |
| `Spinner.png` | Loading spinner animation | @DefaultSpinner |

### Game's `Common/Icons/` Directories (NOT under `UI/Custom/`)

These directories are under the **game root `Common/`** — NOT under `Common/UI/Custom/`:

| Directory | Contents | Accessible from `.ui` files? |
|---|---|---|
| `Icons/ItemCategories/` | Wood.png, Rock.png, etc. | **NO** — outside `UI/Custom/` namespace |
| `Icons/CraftingCategories/` | Furniture/Storage.png, etc. | **NO** — outside `UI/Custom/` namespace |
| `Icons/ResourceTypes/` | Resource type icons | **NO** — outside `UI/Custom/` namespace |
| `Icons/ItemsGenerated/` | 3D-rendered item icons | **NO** — outside `UI/Custom/` namespace |
| `Icons/EntityStats/` | Health, hunger, etc. | **NO** — outside `UI/Custom/` namespace |

**These icon directories are used by the game's native UI** (inventory, crafting screens, HUD) and by JSON asset definitions (e.g., `"Icon": "Icons/ItemCategories/Wood.png"` in ItemCategory JSONs). They are **NOT accessible from `.ui` file path references** because `.ui` path resolution is confined to the `Common/UI/Custom/` subtree.

---

## 5. How `cmd.set()` and `cmd.append()` Differ

| Aspect | `cmd.append(path)` | `cmd.set(selector, value)` |
|--------|---|---|
| **Path root** | `Common/UI/Custom/` (always) | Depends on value type |
| **What it does** | Loads a `.ui` template file | Sets a property on an existing element |
| **Path resolution** | Path is resolved from `Custom/` root | If the value is a UIPath (e.g., texture), it resolves **relative to the `.ui` file that defines the target element** |
| **Color vs path** | N/A — always a file path | `#hex` = color, `"file.png"` = texture path |
| **When used** | `build()` — one-time layout construction | `build()` and `handleDataEvent()` — setting values |

### Critical difference for texture paths in `cmd.set()`

When you do:
```java
cmd.set("#SomeElement.Background", "../../Common/RecipesIcon.png");
```

The path `../../Common/RecipesIcon.png` is resolved **relative to the `.ui` file where `#SomeElement` was originally defined**, NOT relative to `Common/UI/Custom/`.

If `#SomeElement` is defined in `Pages/BlueprintBook/BlueprintBookPage.ui`, then the resolution is:
```
Pages/BlueprintBook/ + ../../Common/RecipesIcon.png → Common/RecipesIcon.png ✓
```

If you instead used a `Custom/`-root-relative path:
```java
cmd.set("#SomeElement.Background", "Common/RecipesIcon.png");  // WRONG for element in Pages/BlueprintBook/
```
This would resolve to `Pages/BlueprintBook/Common/RecipesIcon.png` — which doesn't exist.

---

## 6. Key Questions — Answered

### Q: When `Pages/BlueprintBook/Foo.ui` references `"../../Common/RecipesIcon.png"`, what is the resolved path?

**A:** `Common/RecipesIcon.png` (under `Common/UI/Custom/`).

Step by step:
1. File is at `Pages/BlueprintBook/Foo.ui`
2. Directory is `Pages/BlueprintBook/`
3. `../../` goes up two levels → back to `Custom/` root
4. `Common/RecipesIcon.png` resolves to `Custom/Common/RecipesIcon.png`

### Q: Can a plugin `.ui` file reference textures from the game's `Common/Icons/` directory?

**A:** **No.** The `Common/Icons/` directory is under the **game's root `Common/`** folder, not under `Common/UI/Custom/`. The `.ui` path resolution is confined to the `Common/UI/Custom/` subtree. You cannot navigate above it with `../`. To use those icons, you would need to **copy them** into your plugin's `Common/UI/Custom/Common/` directory (or a similar location within the `Custom/` tree).

### Q: Can a plugin `.ui` file reference textures from another plugin's asset pack?

**A:** **Yes**, as long as both plugins place their textures within `Common/UI/Custom/` and you know the path. All plugin asset packs are merged into a single virtual filesystem. However, this creates a fragile dependency — if the other plugin changes its file structure, your paths break.

### Q: When `cmd.set("#foo.Background", "somePath.png")` is called, is the path resolved relative to the `.ui` file or some other root?

**A:** **Relative to the `.ui` file that defines the element `#foo`.** This is because the client applies the `set` command by finding the element in its DOM tree, and the UIPath resolution uses the document context of that element. The Java server code does NOT influence the resolution root.

### Q: Is there a way to use absolute paths (from `Custom/` root) in `.ui` files?

**A:** **No.** Per the official Hytale documentation, all UIPath values are resolved **relative to the declaring file**. There is no absolute path syntax, no `/`-rooted paths, and no `Custom://` scheme. You must always use relative `../` navigation.

### Q: What happens with `PatchStyle(TexturePath: "...")`?

**A:** The `TexturePath` inside a `PatchStyle` follows the same UIPath rules — relative to the `.ui` file:
```
Background: (TexturePath: "../Common/ContainerPanelPatch.png", Border: 4);
```
This is used in `EntitySpawnPage.ui` (at `Pages/EntitySpawnPage.ui` depth) and resolves correctly.

### Q: What happens with `Decoration: (Default: (Icon: (Texture: "...")))`?

**A:** The `Texture` inside a `CompactTextField` Decoration follows the same UIPath rules:
```
Icon: (Texture: "../../../Common/SearchIcon.png", Width: 16, Height: 16, Offset: 9)
```
Used in `InputContent.ui` (at `Pages/UIGallery/Categories/` depth — 3 levels deep).

---

## 7. Recommendations

### For referencing icons/textures from plugin `.ui` files:

1. **Place shared textures in `Common/UI/Custom/Common/`** — this mirrors the game's convention and makes relative paths predictable
2. **Count your directory depth** — from `Custom/` to your `.ui` file, that's how many `../` you need to reach `Common/`
3. **Never use absolute paths** — they don't exist in the UIPath system
4. **Don't try to reference `Icons/ItemCategories/` etc.** — these are outside the `UI/Custom/` tree
5. **Use `ItemIcon` elements for item icons** — set `.ItemId` via `cmd.set()` and let the engine resolve the icon from its asset registry. This is how the game displays item icons without needing texture paths
6. **Keep texture paths in `.ui` files, not Java** — define `Background:` and `Icon:` paths statically in `.ui` markup. Use `cmd.set()` for data (item IDs, text, visibility, styles), not for texture paths
7. **For sub-templates** loaded via `cmd.append()`, the paths in those templates resolve relative to the template file's own location, not the parent page

### Quick Reference: `../` Count by Depth

| `.ui` file location | `../` needed to reach `Custom/Common/` |
|---|---|
| `Custom/MyPlugin/Page.ui` | `../Common/` |
| `Custom/Pages/Page.ui` | `../Common/` |
| `Custom/Pages/MyPlugin/Page.ui` | `../../Common/` |
| `Custom/Pages/MyPlugin/Sub/Page.ui` | `../../../Common/` |

---

## 8. Confirmed Limitations

| What | Works? | Why |
|------|--------|-----|
| `.ui` file referencing textures in `Common/UI/Custom/Common/` | ✅ Yes | Same virtual filesystem namespace |
| `.ui` file referencing textures in game's `Common/Icons/` | ❌ No | Outside `UI/Custom/` tree; UIPath can't navigate above `Custom/` |
| `.ui` file referencing textures in `Server/` | ❌ No | `Server/` is server-side only, not sent to client |
| `cmd.set()` with texture path | ✅ Yes (with caveats) | Path resolves relative to the `.ui` file, not the Java code |
| `cmd.set()` with color literal | ✅ Yes | Parsed as color, no path resolution |
| `cmd.append()` with absolute path | ❌ No | Must be relative to `Custom/` |
| `cmd.append()` with `../` prefix | ❌ No | `cmd.append()` paths don't support parent traversal; always from `Custom/` root |
| Plugin textures visible to other plugins' `.ui` files | ✅ Yes | Merged virtual filesystem |
| Navigating above `Custom/` with `../../..` | ❌ Unknown/Unsafe | Likely silently fails or produces "Red X" |

---

## See Also

- [Icon Paths & Image References](./icon-paths-and-images.md) — icon categories, ItemCategory assets
- [Custom UI Overview](./custom-ui-overview.md) — architecture, project structure
- [CommonUI Library](./ui-commonui-library.md) — reusable components
- [Common.ui Catalog](../assets/common-ui-catalog.md) — all Common.ui components and styles
- [Plugin Asset Loading](../assets/plugin-asset-loading.md) — how JAR asset packs are loaded
- [Custom UI Commands](../assets/custom-ui-commands.md) — cmd.set(), cmd.append() details
