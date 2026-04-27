# Product Board

> Last updated: 2026-04-27 13:00

## Status Overview

```mermaid
pie title Backlog Distribution
    "Backlog" : 17
    "In Progress" : 1
    "Done" : 13
    "Cancelled" : 8
```

## Board

### In Progress
| ID | Type | Title | Epic | Priority |
|----|------|-------|------|----------|
| F2604262300 | Feature | Custom Interaction Placement | E2604221030 | critical |

### Backlog
| ID | Type | Title | Epic | Priority |
|----|------|-------|------|----------|
| E2604221030 | Epic | PlaceBlock Building Tool | — | high |
| S2604262305 | Story | Register Custom PlaceBlock Interaction Type | E2604221030 | critical |
| S2604262310 | Story | Implement Placement Logic in Custom Interaction | E2604221030 | critical |
| S2604262315 | Story | Update Block_Placeholder JSON to Custom Interaction | E2604221030 | critical |
| S2604262320 | Story | Delete PlaceBlockPlacementSystem and Suppress/Resume | E2604221030 | high |
| F2604261630 | Feature | Placeholder State Consolidation | E2604221030 | critical |
| S2604261635 | Story | Unified Block_Placeholder JSON with 11 States | E2604221030 | critical |
| S2604261640 | Story | State-Based Arming and Disarming API | E2604221030 | critical |
| S2604261645 | Story | State-Based Block Preview Reskinning | E2604221030 | critical |
| S2604261650 | Story | Affordability State Transitions (Green ↔ Red) | E2604221030 | high |
| S2604261655 | Story | Remove SlotFilter.DENY Workaround | E2604221030 | high |
| S2604261660 | Story | Delete PlaceBlockIndicatorListener Stub | E2604221030 | medium |
| F2604221050 | Feature | Rarity-Based Availability Indicators | E2604221030 | high |
| F2604221055 | Feature | Resource Consumption (Inventory Only) | E2604221030 | high |
| S2604221135 | Story | Atomic Resource Consumption from Inventory | E2604221030 | high |
| S2604221140 | Story | PlacementCostScaler Mutual Exclusion | E2604221030 | high |
| S2604221200 | Story | Inventory Resource Availability Check | E2604221030 | high |

### Done
| ID | Type | Title | Epic | Priority |
|----|------|-------|------|----------|
| F2604221035 | Feature | Blueprint Bench Block Asset | E2604221030 | high |
| F2604221040 | Feature | Recipe Selection & Placeholder Transformation | E2604221030 | high |
| F2604221045 | Feature | Block Preview & Placement | E2604221030 | high |
| S2604221100 | Story | Create Block_Placeholder Item Assets | E2604221030 | high |
| S2604221105 | Story | Register PlaceBlock Bench Interaction | E2604221030 | high |
| S2604221125 | Story | Block Preview Integration with Armed Placeholder | E2604221030 | high |
| S2604221130 | Story | Right-Click Placement Handler | E2604221030 | high |
| S2604221145 | Story | Quality State Machine for Placeholder | E2604221030 | high |
| S2604221150 | Story | Inventory Change Event Listener for Rarity Updates | E2604221030 | high |
| S2604221210 | Story | Runtime BenchRequirement Mutation (Shadow Recipes) | E2604221030 | high |
| S2604230900 | Story | Expand Recipe Aggregation to All Benches | E2604221030 | critical |
| S2604240900 | Story | PlaceBlock Command for Testing | E2604221030 | critical |
| S2604240920 | Story | Custom UI Feasibility Spike | E2604221030 | high |
| S2604240910 | Story | Custom Blueprint Selection UI | E2604221030 | high |
| S2604271300 | Story | Placeholder Acquisition Button (Life Essence) | E2604221030 | high |

### Cancelled
| ID | Type | Title | Epic | Reason |
|----|------|-------|------|--------|
| S2604221155 | Story | Nearby Chest Discovery by Radius | E2604221030 | Scope reduced — inventory only |
| S2604221205 | Story | Atomic Multi-Source Resource Consumption | E2604221030 | Scope reduced — inventory only |
| S2604221215 | Story | CraftRecipeEvent Interceptor for Placeholder | E2604221030 | StructuralCraftingWindow dimming unsolvable |
| S2604230905 | Story | Non-Block Recipe Feedback Message | E2604221030 | Replaced by command feedback |
| S2604221115 | Story | Recipe Filtering By Available Resources | E2604221030 | Deferred to custom UI |
| S2604221110 | Story | Inventory & Chest Resource Scanner | E2604221030 | Scope reduced — inventory only |
| S2604221120 | Story | Placeholder Icon Transformation on Selection | E2604221030 | Arming via command/UI now |

## Epics

### E2604221030 — PlaceBlock Building Tool
**Status:** in-progress | **Priority:** high

Select-then-build workflow at the **Blueprint Bench**. Player arms a placeholder tool with a recipe, sees a ghost block preview, and right-clicks to place — consuming resources at placement time from inventory.

**UI PIVOT (2026-04-24):** StructuralCraftingWindow approach abandoned — client dims recipes because the placeholder doesn't match expected materials. Moving to:
- **Phase 2a:** Command-based arming (`/placeblock assign|clear|list|info`) for pipeline testing
- **Phase 2b:** Custom `InteractiveCustomUIPage` for production UI with "Select" button

**Three Benches:**
| Bench | Intent | Output |
|-------|--------|--------|
| Builders Bench | "I need items" | Crafted item → inventory |
| Portable Bench | "I need items, away from bench" | Same, but mobile |
| **Blueprint Bench** | "I want to build in the world" | Armed placeholder → deferred placement |

**Phases:**
1. ~~Bench Asset~~ — **Done.** Blueprint Bench JSON, shadow recipes, PlaceBlock ResourceType
2. **Recipe Selection** — In progress. Phase 2a (command testing) active. Phase 2b (custom UI) backlog.
3. **Placement** — Backlog. Block preview, right-click handler, atomic resource consumption
4. **Feedback** — Backlog. Rarity indicators, inventory change events

**Contracts:** #10–#16 (unchanged by pivot)

**Key Docs:**
- [design-blueprint-bench-custom-ui.md](docs/Plans/design-blueprint-bench-custom-ui.md) — Custom UI architecture
- [custom-ui-options.md](docs/hytale/plugins/custom-ui-options.md) — Hytale Expert UI research
- [structural-crafting-dimming.md](docs/hytale/crafting/structural-crafting-dimming.md) — Dimming root cause analysis

### E2604201200 — Resource Economy Scaling System
**Status:** backlog (empty) | **Priority:** —

### E2604201400 — PlaceBlock Building Tool System
**Status:** backlog (empty, superseded by E2604221030) | **Priority:** —
