---
id: S2604221150
type: story
title: "Inventory Change Event Listener for Rarity Updates"
status: backlog
priority: high
feature: F2604221050
epic: E2604221030
created: 2026-04-22
---

# Inventory Change Event Listener for Rarity Updates

## User Story
As a **player**, I want the **placeholder's color to update automatically when my inventory changes** so that **I always know whether I can afford to place the selected block**.

## Acceptance Criteria

### Checklist
- [ ] Listener subscribes to player inventory change events
- [ ] On inventory change, re-evaluates resource availability for the armed recipe
- [ ] Triggers the quality state machine (S2604221145) to update the highlight
- [ ] Updates occur when items are: picked up, dropped, crafted, consumed, moved
- [ ] Updates occur after each PlaceBlock placement (resources just consumed)
- [ ] Does not trigger for unarmed placeholders (optimization)
- [ ] Performance: does not cause lag on rapid inventory changes (debounce if needed)

### Scenarios
**Pick up resources**
- **Given** the placeholder is armed and red (insufficient)
- **When** the player picks up enough cobblestone
- **Then** the highlight changes to green

**Resources consumed by other means**
- **Given** the placeholder is armed and green
- **When** the player crafts something else that uses cobblestone
- **Then** the highlight changes to red if cobblestone is now insufficient

## Notes
- `ItemContainer.registerChangeEvent()` is used by `PortableBenchWindow` — same pattern applies.
- The listener should only run when the player is holding an armed placeholder (performance optimization).
- Chest content changes are harder to observe — may be limited to check-on-demand rather than real-time events.
