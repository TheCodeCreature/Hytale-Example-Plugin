---
id: S2605071521
type: story
title: "Create StencilRadialStyles.ui"
status: backlog
priority: high
feature: F2605071520
epic: E2605071500
created: 2026-05-07
---

# Create StencilRadialStyles.ui

## User Story
As a **plugin developer**, I want **a StencilRadialStyles.ui file** so that **radial-specific style tokens are defined in one place and can be referenced by all radial .ui files**.

## Acceptance Criteria

### Checklist
- [ ] StencilRadialStyles.ui created at `Common/UI/Custom/Pages/StencilRadial/StencilRadialStyles.ui`
- [ ] Imports SharedStyles.ui for universal tokens
- [ ] Defines @RadialSegmentButtonStyle (default #1a2030(0.7), hovered #3a7bd5(0.8), pressed #2a5ba0(0.9))
- [ ] Defines any radial-specific overlay label variants not already in SharedStyles.ui
- [ ] File parses without errors on server startup

## Notes
- This file imports SharedStyles.ui for universal tokens and adds only radial-specific tokens
- Tokens that could be promoted to SharedStyles.ui later are tagged with comments
