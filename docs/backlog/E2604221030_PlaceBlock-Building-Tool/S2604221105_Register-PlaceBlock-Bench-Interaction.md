---
id: S2604221105
type: story
title: "Register PlaceBlock Bench Interaction"
status: backlog
priority: high
feature: F2604221035
epic: E2604221030
created: 2026-04-22
---

# Register PlaceBlock Bench Interaction

## User Story
As a **player**, I want the **Builders Bench to recognize my Block_Placeholder** so that **it enters recipe-browsing mode instead of standard crafting when the placeholder is in the input slot**.

## Acceptance Criteria

### Checklist
- [ ] The Builders Bench detects when a `Block_Placeholder` is placed in the input slot
- [ ] Standard crafting behavior is suppressed for the placeholder item
- [ ] The bench opens or switches to a recipe-browsing UI instead of attempting to craft
- [ ] The `PlaceBlock_Menu` or `Assign_Bench` interaction codec is wired up
- [ ] Removing the placeholder reverts the bench to normal behavior

### Scenarios
**Bench recognizes placeholder**
- **Given** a `Block_Placeholder` is placed in the Builders Bench input slot
- **When** the bench processes the input
- **Then** it enters recipe-browsing mode, not standard crafting

**Normal items still craft normally**
- **Given** a normal crafting ingredient is in the input slot
- **When** the bench processes the input
- **Then** standard crafting behavior occurs as before

## Notes
- The `PlaceBlock_Menu` interaction codec is already registered in the plugin setup but unimplemented.
- The `Assign_Bench` interaction codec is also registered — determine which is appropriate here.
- Risk: Intercepting the bench's input slot detection may require hooking into `CraftRecipeEvent` or the bench's window logic.
- The Architect needs to determine the best hook point — window-level interception vs. event-level interception.
