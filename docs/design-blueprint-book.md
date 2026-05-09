# Design Document: Blueprint Book System

## 1. Overview

The Blueprint Book is a new held item that provides a portable, streamlined interface for acquiring blueprint stencils. It offers two input paths: **middle-click** (Pick) resolves the aimed block to its crafting recipe and places a stencil in the hotbar, and **Q-key** (Use) opens a Quick Select UI showing only recipes the player has previously encountered. An encounter tracking system gates both flows — players can only quick-select or pick recipes they've seen at a bench or successfully resolved before.

## 2. Design Priorities

1. **Framework-native patterns** — follows the existing interaction chain, packet listener, and `InteractiveCustomUIPage` conventions exactly
2. **Simplicity** — minimal new abstractions; reuses `BenchRecipeRegistries`, `RecipeAffordabilityResolver`, `StencilMetadata`, and shared UI components
3. **Testability** — encounter tracker is a pure data structure with no UI coupling
4. **Extensibility** — encounter set can later be populated from additional sources (trade, discovery, etc.)

## 3. Responsibility Map

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Blueprint Book System                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  JSON Layer (wiring)                                                │
│  ├─ BlueprintBook.json          → item definition                  │
│  ├─ BlueprintBook_QuickSelect.json → root interaction              │
│  └─ BlueprintBook_QuickSelect_Interaction.json → interaction cfg   │
│                                                                     │
│  Java Layer                                                         │
│  ├─ BlueprintBookQuickSelectInteraction                            │
│  │     extends SimpleInstantInteraction                             │
│  │     Q-key → opens QuickSelectPage                               │
│  │                                                                  │
│  ├─ BlueprintBookPickHandler                                        │
│  │     static methods called from StencilRadialInputListener        │
│  │     middle-click → resolve block → create stencil               │
│  │                                                                  │
│  ├─ BlueprintBookQuickSelectPage                                    │
│  │     extends InteractiveCustomUIPage                              │
│  │     flat grid of encountered recipes with affordability          │
│  │                                                                  │
│  └─ RecipeEncounterTracker                                          │
│        per-player encountered recipe set                            │
│        persistence via BSON to data directory                       │
│                                                                     │
│  UI Layer                                                           │
│  └─ BlueprintBookQuickSelect.ui                                    │
│        grid + pagination using shared ClickableIconCell + CostCell  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## 4. File Inventory

| # | Path | Purpose |
|---|------|---------|
| 1 | `src/main/resources/Server/Item/Items/Tool/BlueprintBook.json` | Item definition |
| 2 | `src/main/resources/Server/Item/RootInteractions/BlueprintBook_QuickSelect.json` | Root interaction |
| 3 | `src/main/resources/Server/Item/Interactions/BlueprintBook_QuickSelect_Interaction.json` | Interaction config |
| 4 | `src/main/java/com/UnobstructedThirdPerson/blueprintbook/BlueprintBookQuickSelectInteraction.java` | Interaction class |
| 5 | `src/main/java/com/UnobstructedThirdPerson/blueprintbook/BlueprintBookPickHandler.java` | Block pick logic |
| 6 | `src/main/java/com/UnobstructedThirdPerson/blueprintbook/BlueprintBookQuickSelectPage.java` | Quick Select UI page |
| 7 | `src/main/java/com/UnobstructedThirdPerson/blueprintbook/RecipeEncounterTracker.java` | Encounter set management |
| 8 | `src/main/resources/Common/UI/Custom/Pages/BlueprintBook/BlueprintBookQuickSelect.ui` | UI template |

**Existing files requiring modification:**

| File | Change |
|------|--------|
| `StencilRadialInputListener.java` | Add `else if` branch for Blueprint Book item → delegate to `BlueprintBookPickHandler` |
| `UnobstructedThirdPersonPlugin.java` | Register `"BlueprintBook_QuickSelect"` interaction codec; initialize `RecipeEncounterTracker` |
| `BlueprintSelectionPage.java` | Call `RecipeEncounterTracker.markBenchEncountered()` on bench open |

## 5. Data Flow

### Middle-click (Pick) Flow

```
Player middle-clicks while holding Blueprint Book
    │
    ▼
StencilRadialInputListener.onOutboundPacket()
    │ detects InteractionType.Pick
    │ checks held item → isBlueprintBook(itemInHand)
    ▼
BlueprintBookPickHandler.handlePick(playerRef, ref, store, player)
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
Player presses Q while holding Blueprint Book
    │
    ▼
BlueprintBookQuickSelectInteraction.firstRun()
    │ gets Player, PlayerRef, PageManager
    │ checks no page already open
    ▼
BlueprintBookQuickSelectPage(playerRef, player)
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

### `Server/Item/Items/Tool/BlueprintBook.json`
```json
{
  "TranslationProperties": {
    "Name": "server.items.BlueprintBook.name",
    "Description": "server.items.BlueprintBook.description"
  },
  "Icon": "Icons/ItemsGenerated/Spellbook.png",
  "Model": "Items/Weapons/Spellbook/Book.blockymodel",
  "Categories": ["Tool.BlueprintBook"],
  "MaxStack": 1,
  "Quality": "Tool",
  "PlayerAnimationsId": "Item",
  "Interactions": {
    "Use": "BlueprintBook_QuickSelect"
  },
  "Set": "BlueprintBook"
}
```

### `Server/Item/RootInteractions/BlueprintBook_QuickSelect.json`
```json
{
  "Id": "BlueprintBook_QuickSelect",
  "Interactions": ["BlueprintBook_QuickSelect_Interaction"]
}
```

### `Server/Item/Interactions/BlueprintBook_QuickSelect_Interaction.json`
```json
{
  "Id": "BlueprintBook_QuickSelect_Interaction",
  "Type": "BlueprintBook_QuickSelect",
  "RunTime": 0.1
}
```

## 7. Integration Points

### `StencilRadialInputListener.java` — Add Blueprint Book branch

```java
// After: var itemInHand = player.getInventory().getActiveHotbarItem();
// Add BEFORE the stencil check:
if (BlueprintBookPickHandler.isBlueprintBook(itemInHand)) {
    LOGGER.atInfo().log("[BlueprintBook] Pick for player %s", playerRef.getUuid());
    var world = store.getExternalData().getWorld();
    world.execute(() -> BlueprintBookPickHandler.handlePick(playerRef, ref, store, player));
    break;
}
// Existing: if (!StencilMetadata.isStencil(itemInHand)) continue;
```

### `UnobstructedThirdPersonPlugin.java` — Registration

```java
// In setup():
this.getCodecRegistry(Interaction.CODEC)
        .register("BlueprintBook_QuickSelect",
                BlueprintBookQuickSelectInteraction.class,
                BlueprintBookQuickSelectInteraction.CODEC);
RecipeEncounterTracker.initialize(this.getDataDirectory());

// In onPlayerDisconnect():
RecipeEncounterTracker.evict(event.getPlayerRef().getUuid());
```

### `BlueprintSelectionPage.java` — Encounter population

```java
// In build() after successful open:
RecipeEncounterTracker.markBenchEncountered(playerRef.getUuid(), activeBenchId);
```

## 8. Task Decomposition

### Wave 1 (parallel — no dependencies)

| Ticket | Agent | Scope |
|--------|-------|-------|
| T1 | Engineer | `RecipeEncounterTracker.java` — all methods |
| T2 | Engineer | `BlueprintBookPickHandler.java` — `isBlueprintBook()`, `handlePick()` |
| T3 | Engineer | JSON configs (3 files) |

### Wave 2 (depends on Wave 1)

| Ticket | Agent | Scope | Depends On |
|--------|-------|-------|------------|
| T4 | Engineer | `BlueprintBookQuickSelectInteraction.java` | T3 (codec registration) |
| T5 | Engineer | `BlueprintBookQuickSelectPage.java` | T1 (encounter tracker) |
| T6 | Engineer | `BlueprintBookQuickSelect.ui` | T5 (page class design) |

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

1. **Item type key detection** — How does `isBlueprintBook()` identify the item? By category string or item type key?
2. **Pick behavior when not encountered** — Fail silently or show chat message?
3. **Icon asset** — Confirm `Icons/ItemsGenerated/Spellbook.png` exists or determine correct path
4. **Encounter persistence timing** — Immediate persist vs. batched write for bench-open (50+ recipes)
