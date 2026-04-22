---
id: F2604221035
type: feature
title: "Block Placeholder Asset & Bench Integration"
status: backlog
priority: high
epic: E2604221030
created: 2026-04-22
---

# Block Placeholder Asset & Bench Integration

## Description
Create a `Block_Placeholder` item asset that can be placed into the Builders Bench crafting input slot. When placed, the bench should recognize this as a PlaceBlock tool item and switch to a recipe-browsing mode rather than standard crafting. The placeholder starts in an "unarmed" state with no recipe selected.

## Acceptance Criteria

### Checklist
- [ ] `Block_Placeholder` item asset exists with appropriate icon, categories, and quality
- [ ] Placeholder can be placed in the Builders Bench input slot
- [ ] Bench recognizes the placeholder and enters recipe-browsing mode
- [ ] Placeholder is categorized as a Tool for right-click interaction handling
- [ ] Placeholder defaults to "Tool" quality (blue highlight) when unarmed
- [ ] Placeholder is not consumed by standard crafting — bench does not treat it as a recipe input

### Scenarios
**Player places placeholder in bench**
- **Given** a player has a `Block_Placeholder` in their inventory
- **When** they place it in the Builders Bench input slot
- **Then** the bench enters recipe-browsing mode showing available recipes

**Player removes placeholder from bench**
- **Given** a `Block_Placeholder` is in the Builders Bench input slot
- **When** the player removes it
- **Then** the bench returns to normal crafting mode

**Placeholder is not consumed**
- **Given** a `Block_Placeholder` is in the input slot
- **When** the player selects a recipe
- **Then** the placeholder transforms visually but is NOT consumed

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604221100 | Create Block_Placeholder Item Asset | backlog |
| S2604221105 | Register PlaceBlock Bench Interaction | backlog |

## Notes
- Existing placeholder assets in `_Debug/Placeholders/` are reference material but serve a different purpose (debug visualization). The `Block_Placeholder` is a player-facing tool item.
- The `Assign_Bench` interaction codec is already registered but unimplemented — this may be the entry point.
- Risk: The engine's bench may enforce that input items must match a recipe. Need to determine if we can intercept/override this behavior.
