---
id: E2605071700
type: epic
title: "Blueprint Book — Field Building Tool"
status: backlog
priority: high
created: 2026-05-07
---

# Blueprint Book — Field Building Tool

## Goal
Give builders a portable tool that eliminates the walk-back-to-bench interruption when switching block types during active building. The Blueprint Book lets players middle-click blocks to get stencils and Q-key to quick-select from previously encountered recipes — keeping them in flow-state at their build site.

## Success Criteria
- [ ] Player can hold the Blueprint Book (book model visible in hand)
- [ ] Middle-click on a placed block gives the stencil for that block's recipe
- [ ] Q-key opens a compact Quick Select grid showing encountered recipes
- [ ] Only previously-encountered recipes are accessible via the Book
- [ ] Encounter set persists across sessions

## Features
| ID | Title | Status |
|----|-------|--------|
| F2605071705 | Blueprint Book Item Definition | backlog |
| F2605071710 | Block Pick → Stencil (middle-click) | backlog |
| F2605071715 | Quick Select UI (Q-key subset view) | backlog |
| F2605071720 | Recipe Encounter Tracking | backlog |

## Context
The existing Blueprint Bench (physical block) provides full recipe browsing, filtering, and stencil arming. The Blueprint Book is a field-convenience layer that operates on a strict subset of known recipes. It stays within the deferred-placement paradigm (stencils only, no resource consumption at acquisition time). The physical bench remains authoritative for discovery and full catalog access.

## Design Doc
See `docs/design-blueprint-book.md` for full system design.
