---
id: S2605051415
type: story
title: "Output Detail Panel State Management"
status: backlog
priority: high
feature: F2605051400
epic: E2604221030
created: 2026-05-05
---

# Output Detail Panel State Management

## User Story
As a **player**, I want **the output item display to visually reflect whether I can afford the recipe** so that **I get immediate feedback on my crafting ability**.

## Acceptance Criteria

### Checklist
- [ ] Output icon background frame uses PatchStyle backgrounds for three states: empty, affordable, unaffordable
- [ ] "No recipe selected" state: disabled/empty background (Disabled.png), default gray name text
- [ ] Affordable state: normal slot background, white bold name text
- [ ] Unaffordable state: destructive/muted background, dimmed name text color
- [ ] A `#OutputDim` overlay is added to the output icon area for unaffordable dimming
- [ ] The output name label style changes between affordable and unaffordable states
- [ ] States update on recipe selection and filter changes

### Scenarios
**No recipe selected**
- **Given** the bench UI opens with no previous selection
- **When** the detail panel renders
- **Then** the output frame shows a disabled background and the name reads "No recipe selected" in muted style

**Affordable recipe selected**
- **Given** the player has all ingredients for "Cloth Block Wool Red"
- **When** they select that recipe
- **Then** the output frame shows the normal slot background, name shows "Cloth Block Wool Red" in white bold

**Unaffordable recipe selected**
- **Given** the player lacks ingredients for "Cloth Block Wool Red"
- **When** they select that recipe
- **Then** the output frame shows a muted/destructive background, name shows in dimmed text, and a dim overlay appears on the icon

## Notes
- Three background states for the output frame: empty (Disabled.png PatchStyle), normal (BlockSelectorSlotBackground.png), unaffordable (Destructive.png PatchStyle)
- Name color: muted gray for no-recipe, white for affordable, dimmed for unaffordable
