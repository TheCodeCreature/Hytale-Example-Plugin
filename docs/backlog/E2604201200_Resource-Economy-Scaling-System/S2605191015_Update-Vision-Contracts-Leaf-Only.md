---
id: S2605191015
type: story
title: "Update Vision Contracts for Leaf-Only Scaling"
status: not started
priority: critical
feature: F2605191000
epic: E2604201200
created: 2026-05-19
---

# Update Vision Contracts for Leaf-Only Scaling

## User Story
As a **developer**, I want **vision and contract documents to reflect leaf-only scaling** so that **future development decisions are based on the correct scaling model**.

## Acceptance Criteria

### Checklist
- [ ] `docs/product/vision.md` Contract #3 updated: raw inputs scaled ×12, crafted inputs unchanged
- [ ] `docs/product/vision.md` Contract #7 updated: processed ingredients are NOT scaled
- [ ] `docs/product/vision.md` UX flow diagrams updated to show leaf-only quantities
- [ ] `docs/product/vision.md` Edge cases table updated for per-input classification
- [ ] `docs/product/vision.md` Anti-patterns updated
- [ ] `docs/product/contracts/crafting-costs.md` Contract #1 updated for per-input scaling
- [ ] `docs/product/contracts/crafting-costs.md` Edge cases updated
- [ ] `docs/product/contracts/block-breaking.md` Contract #2 updated for tier-aware drop quantities
- [ ] `docs/product/contracts/block-breaking.md` Deco_Rope edge cases updated
- [ ] `docs/product/contracts/economy-persistence.md` manifest examples updated
- [ ] `docs/product/contracts/economy-commands.md` inspect output examples updated
- [ ] New edge case rows added for salvage exclusion and dual-identity items

## Notes
- PO change report identifies 16 text changes across 5 files plus 3 new contract statements
- Must be done before or alongside implementation so contracts guide development
