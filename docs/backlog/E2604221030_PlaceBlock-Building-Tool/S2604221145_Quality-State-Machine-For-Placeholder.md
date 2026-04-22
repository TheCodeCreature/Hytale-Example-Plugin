---
id: S2604221145
type: story
title: "Quality State Machine for Placeholder"
status: backlog
priority: high
feature: F2604221050
epic: E2604221030
created: 2026-04-22
---

# Quality State Machine for Placeholder

## User Story
As a **player**, I want the **placeholder's highlight color to change based on my resource availability** so that **I can see at a glance whether I can place the selected block**.

## Acceptance Criteria

### Checklist
- [ ] Three states: Unarmed (blue/Tool), Armed-Available (green/Uncommon), Armed-Unavailable (red/Developer)
- [ ] State transitions occur when: recipe is selected/cleared, resources change, placement occurs
- [ ] State machine is deterministic — same inputs always produce the same state
- [ ] Quality change is reflected visually in the player's inventory/hotbar
- [ ] Indicator must never show green when resources are actually insufficient (Contract #13)

### Scenarios
**State transitions**
- **Given** transitions:
  - Unarmed → Armed-Available (recipe selected, resources sufficient)
  - Unarmed → Armed-Unavailable (recipe selected, resources insufficient)
  - Armed-Available → Armed-Unavailable (resources drop below threshold)
  - Armed-Unavailable → Armed-Available (resources increase above threshold)
  - Armed-* → Unarmed (recipe cleared)

## Notes
- The engine's `Quality` field controls highlight color. Changing it at runtime may require item swapping (creating a new item instance with different quality).
- The state machine should be a standalone component that other stories (S2604221150) can trigger.
- The Architect needs to determine the runtime quality change mechanism.
