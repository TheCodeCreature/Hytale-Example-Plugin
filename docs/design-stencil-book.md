# Design Document: Stencil Book System

## 1. Overview

The Stencil Book is a new held item that provides a portable, streamlined interface for acquiring stencil stencils. It offers two input paths: **middle-click** (Pick) resolves the aimed block to its crafting recipe and places a stencil in the hotbar, and **Q-key** (Use) opens a Quick Select UI showing only recipes the player has previously encountered. An encounter tracking system gates both flows — players can only quick-select or pick recipes they've seen at a bench or successfully resolved before.

## 2. Design Priorities

1. **Framework-native patterns** — follows the existing interaction chain, packet listener, and `InteractiveCustomUIPage` conventions exactly
2. **Simplicity** — minimal new abstractions; reuses `BenchRecipeRegistries`, `RecipeAffordabilityResolver`, `StencilMetadata`, and shared UI components
3. **Testability** — encounter tracker is a pure data structure with no UI coupling
4. **Extensibility** — encounter set can later be populated from additional sources (trade, discovery, etc.)

## 3. Responsibility Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Stencil Book System                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  JSON Layer (wiring)                                                │
│  ├─ StencilBook.json          → item definition                  │
│  ├─ StencilBook_QuickSelect.json → root interaction              │
│  └─ StencilBook_QuickSelect_Interaction.json → interaction cfg   │
│                                                                     │
│  Java Layer                                                         │
│  ├─ StencilBookQuickSelectInteraction                            │
│  │     extends SimpleInstantInteraction                             │
│  │     Q-key → opens QuickSelectPage                               │
│  │                                                                  │
│  ├─ StencilBookPickHandler                                        │
│  │     static methods called from StencilRadialInputListener        │
│  │     middle-click → resolve block → create stencil               │
│  │                                                                  │
│  ├─ StencilBookQuickSelectPage                                    │
│  │     extends InteractiveCustomUIPage                              │
│  │     flat grid of encountered recipes with affordability          │
│  │                                                                  │
│  └─ RecipeEncounterTracker                                          │
│        per-player encountered recipe set                            │
│        persistence via BSON to data directory                       │
│                                                                     │
│  UI Layer                                                           │
│  └─ StencilBookQuickSelect.ui                                    │
│        grid + pagination using shared ClickableIconCell + CostCell  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## 4. File Inventory

| # | Path | Purpose |
|---|------|---------|
| 1 | `src/main/resources/Server/Item/Items/Tool/StencilBook.json` | Item definition |
| 2 | `src/main/resources/Server/Item/RootInteractions/StencilBook_QuickSelect.json` | Root interaction |
| 3 | `src/main/resources/Server/Item/Interactions/StencilBook_QuickSelect_Interaction.json` | Interaction config |
| 4 | `src/main/java/com/UnobstructedThirdPerson/stencilbook/StencilBookQuickSelectInteraction.java` | Interaction class |
| 5 | `src/main/java/com/UnobstructedThirdPerson/stencilbook/StencilBookPickHandler.java` | Block pick logic |
| 6 | `src/main/java/com/UnobstructedThirdPerson/stencilbook/StencilBookQuickSelectPage.java` | Quick Select UI page |
| 7 | `src/main/java/com/UnobstructedThirdPerson/stencilbook/RecipeEncounterTracker.java` | Encounter set management |
| 8 | `src/main/resources/Common/UI/Custom/Pages/StencilBook/StencilBookQuickSelect.ui` | UI template |

**Existing files requiring modification:**

| File | Change |
|------|--------|
| `StencilRadialInputListener.java` | Add `else if` branch for Stencil Book item → delegate to `StencilBookPickHandler` |
| `UnobstructedThirdPersonPlugin.java` | Register `"StencilBook_QuickSelect"` interaction codec; initialize `RecipeEncounterTracker` |
| `StencilSelectionPage.java` | Call `RecipeEncounterTracker.markBenchEncountered()` on bench open |

## 5. Data Flow

### Middle-click (Pick) Flow

```
Player middle-clicks while holding Stencil Book
    │
    ▼
StencilRadialInputListener.onOutboundPacket()
    │ detects InteractionType.Pick
    │ checks held item → isStencilBook(itemInHand)
    ▼
StencilBookPickHandler.handlePick(playerRef, ref, store, player)
    │
    ├─ TargetUtil.getTargetBlock(ref, 8, store) → Vector3i
    │
    ├─ world.getBlockType(pos) → blockTypeId
    │
    ├─ BenchRecipeRegistries.getRecipeForBlock(blockTypeId) → CraftingRecipe?
    │   └─ if null → return (fail silently)
    │
    ├─ RecipeEncounterTracker.isEncountered(playerUuid, recipeId)?
    │   └─ if not encountered → return (fail silently)
    │
    ├─ StencilMetadata.createStencil(outputItemId, recipeId) → ItemStack
    │
    ├─ player.getInventory().getHotbar().setItemStackForSlot(activeSlot, stencil)
    │
    └─ RecipeEncounterTracker.markEncountered(playerUuid, recipeId)
```

### Q-key (Use) Flow

```
Player presses Q while holding Stencil Book
    │
    ▼
StencilBookQuickSelectInteraction.firstRun()
    │ gets Player, PlayerRef, PageManager
    │ checks no page already open
    ▼
StencilBookQuickSelectPage(playerRef, player)
    │
    ├─ build():
    │   ├─ RecipeEncounterTracker.getEncounteredRecipes(playerUuid) → Set<String>
    │   ├─ for each recipeId: RecipeFilterRegistry.getEntry(recipeId) → FilteredRecipeEntry
    │   ├─ RecipeAffordabilityResolver.isAffordable(recipe, container) → boolean
    │   ├─ render grid page (12 items, affordability overlays)
    │   └─ pagination controls
    │
    ├─ handleDataEvent("select:<index>"):
    │   ├─ resolve FilteredRecipeEntry from index
    │   ├─ StencilMetadata.createStencil(outputItemId, recipeId)
    │   ├─ player.getInventory().getHotbar().setItemStackForSlot(activeSlot, stencil)
    │   └─ close()
    │
    └─ handleDataEvent("nextPage" / "prevPage"):
        └─ update grid with next/prev 12 items
```

## 6. JSON Configs

### `Server/Item/Items/Tool/StencilBook.json`
```json
{
  "TranslationProperties": {
    "Name": "server.items.StencilBook.name",
    "Description": "server.items.StencilBook.description"
  },
  "Icon": "Icons/ItemsGenerated/Spellbook.png",
  "Model": "Items/Weapons/Spellbook/Book.blockymodel",
  "Categories": ["Tool.StencilBook"],
  "MaxStack": 1,
  "Quality": "Tool",
  "PlayerAnimationsId": "Item",
  "Interactions": {
    "Use": "StencilBook_QuickSelect"
  },
  "Set": "StencilBook"
}
```

### `Server/Item/RootInteractions/StencilBook_QuickSelect.json`
```json
{
  "Id": "StencilBook_QuickSelect",
  "Interactions": ["StencilBook_QuickSelect_Interaction"]
}
```

### `Server/Item/Interactions/StencilBook_QuickSelect_Interaction.json`
```json
{
  "Id": "StencilBook_QuickSelect_Interaction",
  "Type": "StencilBook_QuickSelect",
  "RunTime": 0.1
}
```

## 7. Integration Points

### `StencilRadialInputListener.java` — Add Stencil Book branch

```java
// After: var itemInHand = player.getInventory().getActiveHotbarItem();
// Add BEFORE the stencil check:
if (StencilBookPickHandler.isStencilBook(itemInHand)) {
    LOGGER.atInfo().log("[StencilBook] Pick for player %s", playerRef.getUuid());
    var world = store.getExternalData().getWorld();
    world.execute(() -> StencilBookPickHandler.handlePick(playerRef, ref, store, player));
    break;
}
// Existing: if (!StencilMetadata.isStencil(itemInHand)) continue;
```

### `UnobstructedThirdPersonPlugin.java` — Registration

```java
// In setup():
this.getCodecRegistry(Interaction.CODEC)
        .register("StencilBook_QuickSelect",
                StencilBookQuickSelectInteraction.class,
                StencilBookQuickSelectInteraction.CODEC);
RecipeEncounterTracker.initialize(this.getDataDirectory());

// In onPlayerDisconnect():
RecipeEncounterTracker.evict(event.getPlayerRef().getUuid());
```

### `StencilSelectionPage.java` — Encounter population

```java
// In build() after successful open:
RecipeEncounterTracker.markBenchEncountered(playerRef.getUuid(), activeBenchId);
```

## 8. Task Decomposition

### Wave 1 (parallel — no dependencies)

| Ticket | Agent | Scope |
|--------|-------|-------|
| T1 | Engineer | `RecipeEncounterTracker.java` — all methods |
| T2 | Engineer | `StencilBookPickHandler.java` — `isStencilBook()`, `handlePick()` |
| T3 | Engineer | JSON configs (3 files) |

### Wave 2 (depends on Wave 1)

| Ticket | Agent | Scope | Depends On |
|--------|-------|-------|------------|
| T4 | Engineer | `StencilBookQuickSelectInteraction.java` | T3 (codec registration) |
| T5 | Engineer | `StencilBookQuickSelectPage.java` | T1 (encounter tracker) |
| T6 | Engineer | `StencilBookQuickSelect.ui` | T5 (page class design) |

### Wave 3 (integration — depends on Wave 2)

| Ticket | Agent | Scope | Depends On |
|--------|-------|-------|------------|
| T7 | Engineer | `StencilRadialInputListener.java` modification | T2 |
| T8 | Engineer | Plugin registration + bench encounter wiring | T1, T4 |

## 9. Handoff Checklist

- [x] All interfaces have Javadoc contracts
- [x] All skeleton files defined with TODO markers
- [x] Integration points documented with exact code snippets
- [x] Task decomposition with dependency ordering
- [x] JSON config content finalized

## 10. Open Questions

1. **Item type key detection** — How does `isStencilBook()` identify the item? By category string or item type key?
2. **Pick behavior when not encountered** — Fail silently or show chat message?
3. **Icon asset** — Confirm `Icons/ItemsGenerated/Spellbook.png` exists or determine correct path
4. **Encounter persistence timing** — Immediate persist vs. batched write for bench-open (50+ recipes)
