---
id: S2604201500
type: story
title: "Implement PlaceBlockQualitySwapper"
status: backlog
priority: high
feature: F2604201425
epic: E2604201400
created: 2026-04-20
---

# Implement PlaceBlockQualitySwapper

## User Story
As a **player**, I want **my PlaceBlock tool to automatically change its highlight color** so that **I always know at a glance whether I can place blocks**.

## Acceptance Criteria

### Checklist
- [ ] evaluateAndSwap() checks recipe assignment and resource availability
- [ ] swapToArmed() creates new ItemStack with ITEM_ARMED ID preserving metadata
- [ ] swapToNoResources() creates new ItemStack with ITEM_NO_RESOURCES ID preserving metadata
- [ ] swapToDefault() creates new ItemStack with ITEM_DEFAULT ID clearing metadata
- [ ] Variant swap only occurs when the current variant doesn't match the evaluated state
- [ ] hasRequiredResources() uses CraftingManager.getInputMaterials() and canRemoveMaterials()

### Scenarios
**Swap from Default to Armed**
- **Given** a PlaceBlock_Default with recipe metadata and sufficient resources
- **When** evaluateAndSwap is called
- **Then** the hotbar slot is updated to PlaceBlock_Armed with metadata preserved

## Notes
- Skeleton code exists with detailed TODOs
- Must handle creating new ItemStack with different item ID while preserving BSON metadata
