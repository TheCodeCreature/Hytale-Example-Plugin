---
id: F2604201405
type: feature
title: "PlaceBlock Item Variants & Metadata"
status: backlog
priority: high
epic: E2604201400
created: 2026-04-20
---

# PlaceBlock Item Variants & Metadata

## Description
Three item JSON assets (Default/Armed/NoResources) with different Quality values to produce blue/green/red slot highlights. A metadata utility class reads and writes recipe assignment data on ItemStack BSON, and a constants class centralizes item IDs and metadata keys.

## Acceptance Criteria

### Checklist
- [ ] Three PlaceBlock item assets exist with Quality values Tool, Uncommon, and Developer
- [ ] All three variants share the same icon and have MaxStack 1
- [ ] PlaceBlockConstants defines all item IDs and metadata keys
- [ ] PlaceBlockMetadata can read/write recipe ID and recipe name to ItemStack BSON
- [ ] PlaceBlockMetadata.isPlaceBlock() correctly identifies all three variants
- [ ] Metadata survives ItemStack serialization (chest storage, disconnect)

### Scenarios
**Identify a PlaceBlock item**
- **Given** a player holds a PlaceBlock_Armed item
- **When** PlaceBlockMetadata.isPlaceBlock() is called
- **Then** it returns true

**Write and read recipe metadata**
- **Given** a PlaceBlock_Default ItemStack
- **When** setRecipeId() and setRecipeName() are called
- **Then** getRecipeId() and getRecipeName() return the written values

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604201440 | Create PlaceBlock Item JSON Assets | backlog |
| S2604201445 | Implement PlaceBlockConstants | backlog |
| S2604201450 | Implement PlaceBlockMetadata | backlog |

## Notes
- The three item JSONs are already scaffolded at `src/main/resources/Server/Item/Items/Tool/PlaceBlock_*.json`
- Skeleton code exists at `src/main/java/.../placeblock/PlaceBlockConstants.java` and `PlaceBlockMetadata.java`
- Open question Q2 (metadata tooltip sync) needs runtime testing
