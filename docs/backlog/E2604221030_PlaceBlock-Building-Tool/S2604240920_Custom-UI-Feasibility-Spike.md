---
id: S2604240920
type: story
title: "Custom UI Feasibility Spike"
status: backlog
priority: high
feature: F2604221040
epic: E2604221030
created: 2026-04-24
---

# Custom UI Feasibility Spike

## User Story
As a **developer**, I want **to test the InteractiveCustomUIPage system's capabilities** so that **I know whether to use inline UI, .ui template files, or a hybrid approach for the Stencil Crafting**.

## Acceptance Criteria

### Checklist
- [ ] Test `appendInline()` — can it render item icons and scrollable lists?
- [ ] Test `.ui` template file shipping — can plugins provide custom UI templates?
- [ ] Test bench interaction override — can we prevent StructuralCraftingWindow from auto-opening?
- [ ] Document findings with code samples

### Scenarios
**Inline UI test**
- **Given** a simple InteractiveCustomUIPage with a button and label
- **When** the player triggers it
- **Then** the UI renders correctly with working button events

## Notes
- This spike answers the three risks identified by the Architect (R1, R2, R3)
- Results determine the implementation approach for S2604240910
- Time-boxed investigation — not a production implementation
