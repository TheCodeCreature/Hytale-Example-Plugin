---
id: S2604201465
type: story
title: "Implement PlaceBlockMenuInteraction"
status: backlog
priority: high
feature: F2604201415
epic: E2604201400
created: 2026-04-20
---

# Implement PlaceBlockMenuInteraction

## User Story
As a **player**, I want **pressing F while holding a PlaceBlock to open a recipe selection menu** so that **I can choose which block to arm my tool with**.

## Acceptance Criteria

### Checklist
- [ ] Pressing F while holding any PlaceBlock variant opens a PocketCrafting window
- [ ] Interaction type "PlaceBlock_Menu" is registered in the codec registry
- [ ] Interaction fails gracefully if held item is not a PlaceBlock
- [ ] Interaction loads config from PortableBenchRegistry using "Bench_Assignment" key

### Scenarios
**Open menu on F press**
- **Given** a player holds PlaceBlock_Default
- **When** they press F
- **Then** a PocketCrafting window opens showing available recipes

## Notes
- Follows same pattern as PortableBenchInteraction
- Skeleton code exists at PlaceBlockMenuInteraction.java
- JSON assets for interaction and root interaction already scaffolded
