---
id: S2604261640
type: story
title: "State-Based Arming and Disarming API"
status: backlog
priority: critical
feature: F2604261630
epic: E2604221030
created: 2026-04-26
---

# State-Based Arming and Disarming API

## User Story
As a **developer**, I want PlaceBlockMetadata to use `withState()` for arming and disarming so that state transitions use the engine's native mechanism instead of constructing new ItemStack IDs manually.

## Acceptance Criteria

### Checklist
- [ ] `PlaceBlockMetadata.arm(stack, recipeId, blockTypeId, hotbarSlot)` returns a new ItemStack with `Armed_Green_{slot}` state and recipe metadata
- [ ] `PlaceBlockMetadata.disarm(stack)` returns a new base Blue ItemStack (no state) with metadata cleared
- [ ] `PlaceBlockMetadata.isPlaceBlock(stack)` recognizes the base item and all 11 state variants
- [ ] `PlaceBlockMetadata.isArmed(stack)` returns true for any Green or Red state variant
- [ ] `PlaceBlockMetadata.isGreenVariant(stack)` returns true for Armed_Green_0 through Armed_Green_8
- [ ] `PlaceBlockMetadata.getSlotIndex(stack)` extracts the hotbar slot from the Green state name
- [ ] All callers updated: AssignSubCommand, ClearSubCommand, BlueprintSelectionPage
- [ ] Old variant methods removed: `getVariantItemId()`, `toVariant()`, `toBaseGreen()`
- [ ] Old constants removed: `GREEN_VARIANT_PREFIX`, `PLACEHOLDER_BLUE`, `PLACEHOLDER_GREEN`, `PLACEHOLDER_RED`

### Scenarios

**Arming sets correct state**
- **Given** an unarmed Block_Placeholder in hotbar slot 5
- **When** `arm(stack, "Recipe_Cobble_Wall", "Block_Cobble_Wall", 5)` is called
- **Then** returned stack has item ID `*Block_Placeholder_State_Armed_Green_5`
- **Then** returned stack metadata contains RecipeId and TargetBlockId

**Disarming returns to base**
- **Given** an armed placeholder in state Armed_Green_5
- **When** `disarm(stack)` is called
- **Then** returned stack has item ID `Block_Placeholder` (base, no state)
- **Then** returned stack metadata has RecipeId and TargetBlockId removed

## Notes
- `withState(stateName)` creates a new ItemStack with the state variant's item ID while preserving BSON metadata. Verified by bucket pattern analysis.
- Need to verify: does `withState()` on the base item work, or do we need to construct via `new ItemStack("*Block_Placeholder_State_Armed_Green_5", qty, metadata)`?
- The `toArmedGreen(stack, slot)` and `toArmedRed(stack)` methods handle affordability transitions — they swap state while preserving metadata.
