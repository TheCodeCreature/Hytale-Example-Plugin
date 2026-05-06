---
id: S2605051520
type: story
title: "Resource Type Recipe Filtering in Pipeline"
status: backlog
priority: high
feature: F2605051500
epic: E2604221030
created: 2026-05-05
---

# Resource Type Recipe Filtering in Pipeline

## User Story
As a **player**, I want the recipe grid to update based on my selected resource types so that I only see recipes whose input ingredients match my available resources.

## Acceptance Criteria

### Checklist
- [ ] When "Resource Driven" mode is active and resource types are selected, the recipe grid shows only recipes whose input ingredients include at least one of the selected resource types
- [ ] Resource type matching uses ResourceTypeId from MaterialQuantity inputs (not resolved ItemId)
- [ ] Generic resource types (e.g., Any_Trunk) match recipes that use that generic ResourceTypeId
- [ ] Specific resource types (e.g., Hardwood) match recipes that use that specific ResourceTypeId
- [ ] The filter works as AND with other active filters (tab, set, category, search)
- [ ] When no resource types are selected in Resource Driven mode, all recipes are shown (no filtering)
- [ ] When "Inventory Driven" or "All" mode is active, resource type selections are ignored

### Scenarios
**Recipe uses generic Any_Rock resource type**
- **Given** a recipe requires "Any_Rock" as an input
- **When** the player selects the "Any_Rock" resource type filter
- **Then** that recipe appears in the filtered grid

**Recipe uses specific Hardwood resource type**
- **Given** a recipe requires "Wood_Hardwood" as an input ResourceTypeId
- **When** the player selects the "Hardwood" resource type filter
- **Then** that recipe appears in the filtered grid

**No resource types selected**
- **Given** the bench is in Resource Driven mode
- **When** no resource type icons are toggled on
- **Then** all recipes are shown (unfiltered by resource type)

## Notes
- The mapping between icon filenames and ResourceTypeId values needs to be defined
- ResourceTypeResolver.itemsWithResourceType() resolves items for a given type — but this filter operates at the ResourceTypeId level on recipe inputs, not at the resolved item level
