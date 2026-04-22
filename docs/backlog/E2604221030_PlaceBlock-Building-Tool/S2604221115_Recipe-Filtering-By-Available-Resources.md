---
id: S2604221115
type: story
title: "Recipe Filtering by Available Resources"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-04-22
---

# Recipe Filtering by Available Resources

## User Story
As a **player**, I want to **see only recipes I can afford** so that **I don't waste time selecting recipes I can't build**.

## Acceptance Criteria

### Checklist
- [ ] Bench displays only recipes whose inputs are fully available (inventory + chests)
- [ ] Filtering uses the already-scaled recipe costs (12× economy values)
- [ ] Recipes are grouped by existing bench categories (Structural, etc.)
- [ ] Filter updates when the bench window opens
- [ ] Multi-input recipes require ALL inputs to be available

### Scenarios
**Player can afford some recipes**
- **Given** the player has 48 cobblestone but no planks
- **When** the recipe browser opens
- **Then** cobblestone-based recipes appear, plank-based recipes do not

**Player cannot afford any recipes**
- **Given** the player's inventory and nearby chests are empty
- **When** the recipe browser opens
- **Then** no recipes are shown (or an "insufficient resources" message appears)

## Notes
- The portable bench's "Craftable" tab already implements this pattern for inventory-only filtering.
- This story depends on S2604221110 (Inventory & Chest Resource Scanner) for the availability data.
