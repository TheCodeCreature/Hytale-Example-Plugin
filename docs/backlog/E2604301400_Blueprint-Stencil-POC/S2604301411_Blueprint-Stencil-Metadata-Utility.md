---
id: S2604301411
type: story
title: "Blueprint Stencil Metadata Utility"
status: backlog
priority: high
feature: F2604301410
epic: E2604301400
created: 2026-04-30
---

# Blueprint Stencil Metadata Utility

## User Story
As a **developer**, I want a utility class for reading and writing blueprint stencil BSON metadata on ItemStacks so that stencil items can be created and identified consistently.

## Acceptance Criteria

### Checklist
- [ ] `StencilMetadata.isStencil(ItemStack)` returns true if the item has the blueprint BSON tag
- [ ] `StencilMetadata.getRecipeId(ItemStack)` returns the stored recipe ID string, or null
- [ ] `StencilMetadata.createStencil(String itemTypeKey, String recipeId)` creates an ItemStack of stack size 1 with blueprint BSON metadata
- [ ] BSON metadata includes at minimum: `blueprint: true`, `recipeId: "<recipe_id>"`

### Scenarios
**Create a stencil and verify metadata**
- **Given** the developer calls `StencilMetadata.createStencil("Wall_Cobble", "cobble_wall_recipe")`
- **When** the returned ItemStack is inspected
- **Then** `isStencil()` returns true, `getRecipeId()` returns `"cobble_wall_recipe"`, and stack count is 1

**Check a normal item**
- **Given** a regular `Wall_Cobble` ItemStack with no BSON metadata
- **When** `StencilMetadata.isStencil()` is called
- **Then** it returns false

## Notes
- Follow the same patterns as the existing `PlaceBlockMetadata` utility class for BSON read/write.
- The metadata must use `BsonDocument` fields that prevent stack merging with untagged items of the same type (confirmed: `isStackableWith()` checks metadata equality).
