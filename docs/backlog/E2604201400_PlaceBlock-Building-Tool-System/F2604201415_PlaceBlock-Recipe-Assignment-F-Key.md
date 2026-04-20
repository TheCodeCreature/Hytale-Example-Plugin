---
id: F2604201415
type: feature
title: "PlaceBlock Recipe Assignment (F-Key Menu)"
status: backlog
priority: high
epic: E2604201400
created: 2026-04-20
---

# PlaceBlock Recipe Assignment (F-Key Menu)

## Description
When the player presses F while holding a PlaceBlock item, a PocketCrafting window opens showing all block-output recipes from configured benches. Clicking a recipe assigns it to the PlaceBlock (writes metadata, swaps quality variant). A "Clear Recipe" tab resets the PlaceBlock to default state. No materials are consumed during assignment.

## Acceptance Criteria

### Checklist
- [ ] Pressing F while holding any PlaceBlock variant opens the selector window
- [ ] Window shows recipes from both Builders and Furniture bench categories
- [ ] Only block-output recipes appear (non-block recipes like Rope are excluded)
- [ ] Clicking a recipe writes recipe ID and name to PlaceBlock metadata
- [ ] After assignment, PlaceBlock swaps to Armed (green) or NoResources (red)
- [ ] "Clear Recipe" tab resets PlaceBlock to Default (blue)
- [ ] No materials are consumed during recipe assignment
- [ ] Player can re-open the menu at any time to change or clear the recipe

### Scenarios
**Assign a recipe via F-key**
- **Given** a player holds PlaceBlock_Default
- **When** they press F, select "Oak Planks", and the player has sufficient resources
- **Then** the PlaceBlock swaps to PlaceBlock_Armed (green) with recipe "Oak_Planks" in metadata

**Clear an assigned recipe**
- **Given** a player holds PlaceBlock_Armed with recipe "Oak_Planks"
- **When** they press F and select "Clear Recipe"
- **Then** the PlaceBlock swaps to PlaceBlock_Default (blue) with no recipe in metadata

## Stories
| ID | Title | Status |
|----|-------|--------|
| S2604201465 | Implement PlaceBlockMenuInteraction | backlog |
| S2604201470 | Implement PlaceBlockSelectorWindow | backlog |
| S2604201475 | Create PlaceBlock Interaction JSON Assets | backlog |

## Notes
- Interaction type "PlaceBlock_Menu" registered via codec, same pattern as PortableBenchInteraction
- The selector window reuses PortableBenchConfig for category definitions
- Skeleton code exists at `PlaceBlockMenuInteraction.java` and `PlaceBlockSelectorWindow.java`
