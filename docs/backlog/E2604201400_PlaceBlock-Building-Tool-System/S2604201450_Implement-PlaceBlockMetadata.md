---
id: S2604201450
type: story
title: "Implement PlaceBlockMetadata"
status: backlog
priority: high
feature: F2604201405
epic: E2604201400
created: 2026-04-20
---

# Implement PlaceBlockMetadata

## User Story
As a **developer**, I want **a utility class that reads and writes recipe data on PlaceBlock ItemStacks** so that **recipe assignment is stored per-item via BSON metadata**.

## Acceptance Criteria

### Checklist
- [ ] isPlaceBlock(ItemStack) returns true for all three PlaceBlock variants
- [ ] hasRecipe(ItemStack) returns true when META_RECIPE_ID is present and non-empty
- [ ] getRecipeId(ItemStack) reads the recipe ID from BSON metadata
- [ ] setRecipeId(ItemStack, String) returns a new ItemStack with metadata written
- [ ] getRecipeName(ItemStack) reads the display name from BSON metadata
- [ ] setRecipeName(ItemStack, String) returns a new ItemStack with metadata written
- [ ] clearRecipe(ItemStack) removes both recipe metadata keys
- [ ] All methods are pure — they return new ItemStack instances, never mutate the input

### Scenarios
**Round-trip metadata**
- **Given** a PlaceBlock_Default ItemStack
- **When** setRecipeId and setRecipeName are called, then getRecipeId and getRecipeName
- **Then** the returned values match what was written

**Clear metadata**
- **Given** a PlaceBlock_Armed with recipe metadata
- **When** clearRecipe is called
- **Then** hasRecipe returns false, getRecipeId returns null

## Notes
- Skeleton code exists with TODOs — needs ItemStack.withMetadata() API usage
- Runtime test needed: verify BSON metadata survives chest storage and reconnection (Contract #10)
