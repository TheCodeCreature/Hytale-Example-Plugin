---
id: S2605191107
type: story
title: "Implement Supporting Records"
status: backlog
priority: high
feature: F2605191105
epic: E2605191100
created: 2026-05-19
---

# Implement Supporting Records

## User Story
As a **developer**, I want **immutable record types for raw material requirements and consumption entries** so that **auto-craft plans are type-safe and self-documenting**.

## Acceptance Criteria

### Checklist
- [ ] `RawMaterialRequirement` record with `itemId` and `quantity` fields
- [ ] `ConsumptionEntry` record with `itemId` and `quantity` fields
- [ ] `AutoCraftPlan` record with `consumptions`, `affordable`, `requiresAutoCraft`, `directCostView`, `rawCostView`
- [ ] `AutoCraftPlan` factory methods: `direct(...)`, `autoCraft(...)`, `unaffordable(...)`
- [ ] All records are immutable with unmodifiable list fields

### Scenarios
**AutoCraftPlan.direct creates fast-path plan**
- **Given** a list of direct consumption entries
- **When** `AutoCraftPlan.direct(consumptions, directView, rawView)` is called
- **Then** returns a plan with `affordable=true`, `requiresAutoCraft=false`

## Notes
Skeleton files already exist in `src/main/java/com/CodeCreature/crafting/`.
Design reference: [design-auto-craft-stencil.md](../../design-auto-craft-stencil.md) §4.3
