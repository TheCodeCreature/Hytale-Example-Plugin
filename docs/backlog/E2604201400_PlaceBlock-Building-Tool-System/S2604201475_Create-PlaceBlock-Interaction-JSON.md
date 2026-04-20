---
id: S2604201475
type: story
title: "Create PlaceBlock Interaction JSON Assets"
status: backlog
priority: high
feature: F2604201415
epic: E2604201400
created: 2026-04-20
---

# Create PlaceBlock Interaction JSON Assets

## User Story
As a **developer**, I want **the PlaceBlock_Menu interaction and root interaction JSON files to be correctly configured** so that **the engine dispatches F-key presses to PlaceBlockMenuInteraction**.

## Acceptance Criteria

### Checklist
- [ ] PlaceBlock_Menu.json root interaction references PlaceBlock_Menu_Interaction
- [ ] PlaceBlock_Menu_Interaction.json has Type "PlaceBlock_Menu" and RunTime 0.1
- [ ] All three PlaceBlock item JSONs reference "Use": "PlaceBlock_Menu"
- [ ] Engine resolves the interaction chain without errors

## Notes
- JSON files already scaffolded — verify they match the codec registration in setup()
