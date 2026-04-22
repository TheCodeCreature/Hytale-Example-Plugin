---
id: F2604221040
type: feature
title: "Recipe Selection & Placeholder Transformation"
status: backlog
priority: high
epic: E2604221030
created: 2026-04-22
---

# Recipe Selection & Placeholder Transformation

## Description
When a `Block_Placeholder` is in the Builders Bench input slot, the bench displays available recipes filtered by what the player can afford (checking inventory and nearby chests within the configurable bench radius). Selecting a recipe transforms the placeholder into a new placeholder that displays the selected recipe's output icon. No resources are consumed during selection (Contract #10).

## Acceptance Criteria

### Checklist
- [ ] Available recipes are filtered based on player inventory and nearby chest contents
- [ ] "Nearby chests" use the configurable bench radius setting
- [ ] Selecting a recipe transforms the placeholder to show the selected block's icon
- [ ] Recipe selection does NOT consume any resources (Contract #10)
- [ ] Player can change the selected recipe freely without penalty
- [ ] Player can clear the selection, returning to the unarmed placeholder state
- [ ] The armed placeholder retains its recipe reference when moved to the player's hotbar

### Scenarios
**Player selects a recipe they can afford**
- **Given** a `Block_Placeholder` is in the bench input slot and the player has 48 cobblestone
- **When** the player selects the "Cobble Wall" recipe
- **Then** the placeholder transforms to show the Cobble Wall icon

**Player cannot afford a recipe**
- **Given** a `Block_Placeholder` is in the bench input slot and the player has 0 cobblestone
- **When** the player views available recipes
- **Then** the "Cobble Wall" recipe is not shown (filtered out)

**Player changes recipe selection**
- **Given** the placeholder is armed with "Cobble Wall"
- **When** the player selects "Stone Fence" instead
- **Then** the placeholder updates to show Stone Fence icon, no resources consumed

**Placeholder survives bench close**
- **Given** the player has armed the placeholder with a recipe
- **When** they close the bench and hold the placeholder
- **Then** the placeholder retains the selected recipe and icon

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221110 | Inventory & Chest Resource Scanner | backlog |
| S2604221115 | Recipe Filtering by Available Resources | backlog |
| S2604221120 | Placeholder Icon Transformation on Selection | backlog |

## Notes
- The portable bench system already has inventory scanning and "Craftable" tab filtering — similar patterns should be reused.
- The `PortableStructuralWindow` has `chestHorizontalRadius` and `chestVerticalRadius` fields — these need to be made configurable and shared with this system.
- Open question: Should recipes from ALL registered benches appear, or only Builders Bench recipes? Product Owner should clarify if needed.
- Risk: Persisting the armed recipe reference on the item may require custom NBT-like metadata on the placeholder item instance.
