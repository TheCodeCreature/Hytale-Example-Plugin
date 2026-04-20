---
id: E2604201200
type: epic
title: "Resource Economy Scaling System"
status: done
priority: critical
created: 2026-04-20
---

# Resource Economy Scaling System

## Goal
Implement a 12× resource scaling economy where natural blocks drop 12× their vanilla yield, bench recipe costs scale to match, and breaking crafted blocks returns recipe ingredients — creating a fair, symmetric gather→craft→place→break loop.

## Success Criteria
- [x] Natural blocks drop 12× vanilla quantity
- [x] Stack sizes scale to accommodate 12× yields
- [x] All bench recipe costs scale by 12× (block and non-block outputs)
- [x] Breaking crafted blocks drops recipe ingredients at proportional quantities
- [x] Placing natural blocks consumes 12× items
- [x] Base block recipes excluded from cost scaling
- [x] Processed ingredients correctly classified as non-natural
- [x] Multiple bench types supported (Builders, Furniture) with independent processors

## Features
| ID | Title | Status |
|----|-------|--------|
| F2604201205 | Natural Resource Drop Scaling | done |
| F2604201210 | Recipe Cost Scaling (12×) | done |
| F2604201215 | Bench Category Processors | done |
| F2604201220 | Portable Bench Tool | done |
| F2604201225 | Placement Cost Scaling | done |

## Context
Fully implemented on branch `BlockBreakSystem/Split-3`. All design, review, and fix documents are in `docs/Plans/` and `docs/` (review-*.md files).

### Completed Plans (archived)
| Document | Type | Status |
|----------|------|--------|
| `Plans/design-bench-category-processors.md` | Design | Done |
| `Plans/design-deco-aware-natural-registry.md` | Design | Done |
| `Plans/design-portable-bench-tool.md` | Design | Done |
| `Plans/fix-base-block-misclassification.md` | Fix | Done |
| `Plans/fix-bench-specific-resolution.md` | Fix | Done |
| `Plans/fix-non-block-recipe-scaling.md` | Fix | Done |
| `Plans/pipeline-flow-per-resource.md` | Trace | Done |
| `Plans/refactor-resourcetype-resolution.md` | Refactor | Done |
| `review-non-block-recipe-gap.md` | Review | Done |
| `review-parallel-bench-processors.md` | Review | Done |
| `review-resourcetypeid-resolution.md` | Review | Done |
