---
id: E2605071500
type: epic
title: "Unified Style & Affordability System"
status: backlog
priority: high
created: 2026-05-07
---

# Unified Style & Affordability System

## Goal
Establish a consistent visual language across all UI subsystems (BlueprintBook, StencilRadial) with shared style tokens, unified affordability feedback, and a single source of truth for ingredient resolution and inventory checking.

## Success Criteria
- [ ] Plugin-wide SharedStyles.ui exists with universal tokens (button bases, label foundations, affordability colors)
- [ ] BlueprintBookStyles.ui imports SharedStyles.ui and retains bench-specific styles
- [ ] StencilRadialStyles.ui imports SharedStyles.ui and contains radial-specific overlay tokens
- [ ] All inline styles in StencilRadial .ui files are replaced with $S references
- [ ] Stencil radial cost arc shows per-ingredient affordability (dim/tint for insufficient)
- [ ] Affordability checking logic is extracted into a shared utility used by all three consumers
- [ ] Ingredient resolution chain (getPerUnitCost → resolveInputItemId → resolveToGatherableForm) is deduplicated
- [ ] Existing BlueprintBook behavior is unchanged (no visual regression)

## Features
| ID | Title | Status |
|----|-------|--------|
| F2605071510 | Plugin-Wide Shared Style Tokens | backlog |
| F2605071520 | StencilRadial Style Extraction | backlog |
| F2605071530 | Unified Affordability Resolver | backlog |
| F2605071540 | Radial Cost Arc Affordability Feedback | backlog |

## Context
Code Reviewer audit (docs/review-ui-styling-system.md) found:
- StencilRadial has zero shared styles — all inline across 3 .ui files
- Two independent affordability code paths with no shared abstraction
- Ingredient resolution chain duplicated in BlueprintSelectionPage and StencilRadialMenuPage
- Radial cost arc has no affordability visual feedback (display-only)

Product Owner validated as ALIGNED with the vision. Affordability feedback in the radial closes a gap in the Contract #13 promise chain.

## File Deletion Plan
| # | File | Action | Prerequisite | Confidence |
|---|------|--------|-------------|------------|
| 1 | BlueprintBookStyles.ui | TRIM — extract ~10 universal styles to SharedStyles.ui, keep 7 bench-specific | SharedStyles.ui created and tested | HIGH |
| 2 | ItemGridTestPage.ui | DELETE candidate — test page with local @StatusStyle, not production | Verify no test harness loads it | MEDIUM |
| 3 | StencilRadial *.ui (3 files) | MODIFY in-place — replace inline styles with $S references | SharedStyles.ui + StencilRadialStyles.ui created | HIGH |
| 4 | Duplicated ingredient resolution in StencilRadialMenuPage + BlueprintSelectionPage | REPLACE — both call sites replaced by shared utility | RecipeAffordabilityResolver implemented | HIGH |

## Risk: Silent Value.ref() Breakage
8 Value.ref() paths in BlueprintSelectionPage.java hardcode "Pages/BlueprintBook/BlueprintBookStyles.ui". If styles are moved without updating these paths, the UI engine silently falls back to default appearance. Mitigation: BlueprintBookStyles.ui imports SharedStyles.ui and re-exports shared tokens, keeping existing Value.ref() paths valid.

## Roadmap
Phase 1: SharedStyles.ui + StencilRadialStyles.ui creation (F2605071510 + F2605071520)
Phase 2: RecipeAffordabilityResolver extraction (F2605071530)
Phase 3: Radial cost arc affordability feedback (F2605071540)
