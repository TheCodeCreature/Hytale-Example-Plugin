---
id: F2605051400
type: feature
title: "Blueprint Bench Visual States"
status: backlog
priority: high
epic: E2604221030
created: 2026-05-05
---

# Blueprint Bench Visual States

## Description
Enhance the Blueprint Bench UI with interconnected visual state feedback across three areas: selected cell highlighting in the recipe grid, per-ingredient affordability in the cost grid, and output item detail panel states (no-recipe, affordable, unaffordable).

## Acceptance Criteria

### Checklist
- [ ] Selected recipe cell in the icon grid shows a visually distinct highlighted state using PatchStyle backgrounds
- [ ] Cost grid ingredients show red quantity text and a dim overlay when the player doesn't have enough of that ingredient
- [ ] Output icon frame uses PatchStyle backgrounds for state: empty/no-recipe, affordable, unaffordable
- [ ] Output name color changes based on affordability (white = affordable, muted = unaffordable)
- [ ] All states update correctly on recipe selection and filter changes
- [ ] Unaffordable+selected grid cell shows both selected highlight and dim overlay simultaneously

### Scenarios
**Player selects an affordable recipe**
- **Given** a player has all required ingredients in inventory
- **When** they click a recipe cell in the grid
- **Then** the cell shows a highlighted background, the output icon displays with a normal frame, cost items show white quantity text, and the output name is white

**Player selects an unaffordable recipe**
- **Given** a player is missing one or more ingredients
- **When** they click a recipe cell in the grid
- **Then** the cell shows both highlighted and dimmed states, the output icon shows a muted frame, missing cost ingredients show red text + dim, and the output name is muted gray

**No recipe is selected**
- **Given** no recipe has been selected yet (or selection was cleared)
- **When** the detail panel is displayed
- **Then** the output icon frame shows a disabled/empty state background, and the name reads "No recipe selected"

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2605051405 | Selected Cell Highlight in Recipe Grid | backlog |
| S2605051410 | Per-Ingredient Cost Cell Affordability | backlog |
| S2605051415 | Output Detail Panel State Management | backlog |

## Notes
- Must use PatchStyle/TextButtonStyle image backgrounds for state management (no raw color overlays) to support proper 9-slice stretching
- Available background images: Primary_Square (selected), Tertiary (default), Tertiary_Active (active), Disabled (empty), Destructive (unaffordable/error)
- States update on recipe selection and filter change only — not live inventory tracking
