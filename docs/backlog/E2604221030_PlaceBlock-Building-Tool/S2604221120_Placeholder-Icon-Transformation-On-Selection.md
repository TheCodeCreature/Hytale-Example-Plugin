---
id: S2604221120
type: story
title: "Placeholder Icon Transformation on Selection"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-04-22
---

# Placeholder Icon Transformation on Selection

## User Story
As a **player**, I want the **placeholder to show the selected recipe's icon** so that **I can see what block I'm about to place when I hold the tool**.

## Acceptance Criteria

### Checklist
- [ ] Selecting a recipe transforms the placeholder's icon to match the recipe output
- [ ] The transformation does NOT consume the placeholder item
- [ ] The transformation does NOT consume any recipe resources (Contract #10)
- [ ] The armed placeholder retains its recipe reference when moved between inventory slots
- [ ] The armed placeholder retains its recipe reference when the bench is closed
- [ ] Clearing the selection reverts the icon to the default placeholder appearance
- [ ] The recipe reference includes the recipe ID and output block type

### Scenarios
**Select recipe**
- **Given** the placeholder is unarmed in the bench
- **When** the player selects "Cobble Wall"
- **Then** the placeholder icon changes to the Cobble Wall icon

**Change recipe**
- **Given** the placeholder is armed with "Cobble Wall"
- **When** the player selects "Stone Fence" instead
- **Then** the icon changes to Stone Fence

**Close bench with armed placeholder**
- **Given** the placeholder is armed
- **When** the player closes the bench and checks their hotbar
- **Then** the placeholder still shows the selected recipe icon

## Notes
- This may require creating a new item instance with modified icon metadata, or using the engine's item customization APIs.
- The Architect needs to determine the persistence mechanism — custom NBT, item replacement, or component data.
- Risk: If the engine doesn't support per-instance icon overrides, each armed state may need its own item type (one per recipe). This would be a significant design constraint.
