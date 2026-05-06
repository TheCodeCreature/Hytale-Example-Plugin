---
id: F2605051500
type: feature
title: "Resource Type Input Filter"
status: backlog
priority: high
epic: E2604221030
created: 2026-05-05
---

# Resource Type Input Filter

## Description
Replace the deprecated placeholder list section in the Blueprint Bench right column with a searchable resource type icon grid. This acts as a third affordability/filter mode: players toggle resource type icons ON/OFF to discover recipes that use those resource types as inputs. The filter uses a loose OR — selecting multiple resource types shows recipes that use ANY of the selected types.

## Acceptance Criteria

### Checklist
- [ ] The placeholder section (#PlaceholderList, #NoPlaceholdersLabel, #InputSlotLabel) is replaced with a resource type icon grid
- [ ] All ~78 resource type icons from `Icons/ResourceTypes/` are displayed as toggleable 36x36 icon buttons
- [ ] Generic resource types (Any_Bone, Any_Book, etc.) are included in the grid
- [ ] The affordability toggle cycles through three states: All → Inventory Driven → Resource Type Driven
- [ ] In "Resource Type Driven" mode, only recipes whose input ingredients match ANY selected resource type are shown
- [ ] In "Inventory Driven" mode (default on open), existing inventory-based filtering applies
- [ ] In "All" mode, no affordability filtering is applied
- [ ] Resource type filter works alongside existing tab, set, category, and search filters (AND)
- [ ] A clear button resets all resource type selections

### Scenarios
**Player opens bench**
- **Given** the Blueprint Bench opens
- **When** the page loads
- **Then** the affordability mode is "Inventory Driven" (default)

**Player cycles to Resource Type mode**
- **Given** the bench is open in Inventory Driven mode
- **When** the player clicks the affordability toggle
- **Then** the mode switches to "Resource Type Driven" and the resource type icon grid becomes active

**Player selects wood-related resource types**
- **Given** the bench is in Resource Type Driven mode
- **When** the player toggles ON "Wood", "Wood_Planks", and "Hardwood"
- **Then** the recipe grid shows recipes that use ANY of Wood, Wood_Planks, or Hardwood as inputs

**Player also has a category filter active**
- **Given** the player has "Wood" resource type selected AND the "Builders" tab active
- **When** the recipe grid updates
- **Then** only Builders-bench recipes using Wood as an input are shown (AND logic with other filters)

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605051505 | Replace Placeholder Section with Resource Type Grid UI | backlog |
| S2605051510 | Three-State Affordability Toggle | backlog |
| S2605051515 | Resource Type Filter Build/Bind/Update Logic | backlog |
| S2605051520 | Resource Type Recipe Filtering in Pipeline | backlog |

## Notes
- Icons MUST come from `Common/UI/Custom/Common/Icons/ResourceTypes/`
- Reuses the GroupFilterButton.ui icon-button pattern (36x36 with active overlay)
- The ResourceTypeResolver already has `itemsWithResourceType(String)` for resolving which items match a resource type — this can inform the filtering logic
- Resource type list is static (derived from icon folder contents at build time), not dynamically discovered at runtime
