---
id: S2605191005
type: story
title: "Build RecipeTierClassifier"
status: in-progress
priority: critical
feature: F2605191000
epic: E2604201200
created: 2026-05-19
---

# Build RecipeTierClassifier

## User Story
As a **developer**, I want **a classifier that identifies which items are crafted intermediates** so that **Phase 1 can skip scaling their quantities in recipes**.

## Acceptance Criteria

### Checklist
- [ ] New class `RecipeTierClassifier` in `com.CodeCreature.scaling` package
- [ ] `init()` scans all `CraftingRecipe` assets and builds a `Set<String>` of crafted item IDs
- [ ] An item is classified as crafted if it appears as the `primaryOutput` of any non-Salvage recipe
- [ ] Salvage recipes (ID starts with "Salvage") are excluded from the scan
- [ ] `isCraftedItem(String itemId)` returns true if the item is in the crafted set
- [ ] `isCraftedItem()` returns false for items that are only natural drops
- [ ] Items that are both natural drops AND crafting outputs return false (treated as raw)
- [ ] `ResourceTypeId` handling: `isRawInput(MaterialQuantity mq)` checks if any item matching the resource type is a natural drop — if yes, classify as raw
- [ ] Thread-safe: built once at init, immutable after
- [ ] Fail-fast: logs warning if no crafted items found (indicates broken asset loading)

### Scenarios
**Pure Crafted Item**
- **Given** `Ingredient_Fibre` is the output of recipe `Ingredient_Fibre_Recipe`
- **When** `isCraftedItem("Ingredient_Fibre")` is called
- **Then** Returns true

**Pure Natural Item**
- **Given** `Rock_Stone_Cobble` is NOT the output of any non-Salvage recipe
- **When** `isCraftedItem("Rock_Stone_Cobble")` is called
- **Then** Returns false

**Dual-Identity Item**
- **Given** An item appears as both a natural block drop AND a recipe output
- **When** `isCraftedItem()` is called
- **Then** Returns false (natural identity takes precedence)

**Salvage Recipe Exclusion**
- **Given** Item X appears only as the output of a Salvage recipe
- **When** `isCraftedItem("ItemX")` is called
- **Then** Returns false (salvage outputs don't count)

## Notes
- Must be initialized BEFORE `DropScaler.scaleCraftingCosts()` in Phase 1
- Uses `NaturalResourceRegistry.isNaturalItem()` for dual-identity check
- Init order: `NaturalResourceRegistry.init()` → `RecipeTierClassifier.init()` → Phase 1
