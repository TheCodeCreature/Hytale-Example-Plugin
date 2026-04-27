---
id: S2604261650
type: story
title: "Affordability State Transitions (Green ↔ Red)"
status: backlog
priority: high
feature: F2604261630
epic: E2604221030
created: 2026-04-26
---

# Affordability State Transitions (Green ↔ Red)

## User Story
As a **player**, I want the placeholder's color to change from green to red when I can no longer afford the recipe so that I get real-time visual feedback on resource availability.

## Acceptance Criteria

### Checklist
- [ ] On inventory change, PlaceholderSyncSystem checks each armed placeholder against `canRemoveMaterials()`
- [ ] Affordable armed placeholder stays in (or transitions to) `Armed_Green_{slot}` state
- [ ] Unaffordable armed placeholder transitions to `Armed_Red` state, preserving recipe metadata
- [ ] When transitioning Red → Green, the correct slot-specific Green state is restored (e.g., slot 3 → `Armed_Green_3`)
- [ ] The slot index is stored in metadata so it can be recovered when transitioning from Red back to Green
- [ ] State transition only fires `setItemStackForSlot` when the state actually changes (avoid unnecessary inventory change events)

### Scenarios

**Resources depleted after placement**
- **Given** player has Armed_Green_3 with Cobble Wall recipe, barely enough stone
- **When** player places one block, consuming the remaining stone
- **Then** placeholder transitions to Armed_Red (recipe metadata preserved)
- **Then** rarity indicator changes from Green to Red

**Resources acquired via pickup**
- **Given** player has Armed_Red with Cobble Wall recipe
- **When** player picks up stone, making the recipe affordable again
- **Then** placeholder transitions back to Armed_Green_3 (slot 3 restored from metadata)
- **Then** rarity indicator changes from Red to Green

**Multiple armed placeholders with mixed affordability**
- **Given** slot 0 armed with Cobble Wall (affordable), slot 5 armed with Gold Brick (unaffordable)
- **When** inventory is checked
- **Then** slot 0 remains Armed_Green_0, slot 5 shows Armed_Red
- **Then** each slot's preview reflects its individual affordability

## Notes
- The slot index must be persisted in BSON metadata (e.g., `"SlotIndex": 3`) so that Red → Green transitions know which Green state to restore.
- Risk: circular event loop — `setItemStackForSlot` triggers a change event, which triggers another affordability check. Need idempotency guard (only write if state actually changed).
- This story absorbs the responsibilities of S2604221145 (Quality State Machine) and S2604221150 (Inventory Change Event Listener).
