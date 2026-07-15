---
id: E2605071530
type: epic
title: "Shared UI Component Library"
status: in-progress
priority: high
created: 2026-05-07
---

# Shared UI Component Library

## Goal
Create a reusable component library in `Common/Components/` that eliminates duplicate UI element definitions across StencilBook and StencilRadial. Consumers stamp shared components like `$Comp.@ItemIconCell` instead of re-defining icon cells, filter buttons, and cost displays from scratch per page.

## Success Criteria
- [ ] Common element patterns (icon cells, filter buttons, cost displays, segments) are defined once in a shared location
- [ ] Both StencilBook and StencilRadial pages consume shared components instead of page-specific duplicates
- [ ] Page-specific .ui files for migrated components are deleted
- [ ] All Java selectors are updated to match shared component IDs
- [ ] No visual regressions — pages look identical after migration

## Features
| ID | Title | Status |
|----|-------|--------|
| F2605071532 | Component Token Definitions | backlog |
| F2605071534 | Standalone Component Files | backlog |
| F2605071536 | Migration to Shared Components | backlog |

## Context
The StencilBook and StencilRadial UIs share many visual patterns — icon cells with dim overlays, filter toggle buttons, cost ingredient displays — but each page defines its own version with slightly different IDs and sizing. This leads to:
- Duplicate definitions that drift apart over time
- Developers needing to remember per-component affordances (dim states, hover, click targets)
- New features re-inventing the same elements

The Hytale engine supports full element tree tokens (like `$C.@Container`) with parse-time `@Param` overrides, making a shared component library feasible.
