---
topic: status-and-progress
type: reference
---

# Status, Progress, and Scoring

## Purpose
Defines how lifecycle state is tracked at the individual ticket level (status + progress) and how aggregate progress rolls up to the epic level (score formula).

---

## Status Model

| Value | Meaning |
|---|---|
| `idea` | Captured but not yet refined |
| `backlog` | Defined and ready to be prioritized |
| `ready` | Refined and ready to start |
| `in-progress` | Actively being worked |
| `blocked` | Cannot advance; reason must be noted |
| `review` | Work complete, awaiting validation |
| `done` | Accepted and closed |
| `archived` | No longer relevant; retained for history |

## Execution Discipline

Status and progress are mandatory lifecycle fields and must be maintained **before and after every meaningful action**.

| When | Required action |
|---|---|
| Before starting | Set `status: in-progress`, update `progress` baseline |
| During work | Update `progress` at meaningful checkpoints |
| On completion | Set `status: review` or `done`, set `progress: 100` |
| On blocker | Set `status: blocked`, add a concise blocker note in Delivery Notes |

Tooling, prompts, skills, and hooks must treat stale `status`/`progress` as a contract violation.

## Progress Representation in Body

Include this summary line directly after the `# Title` heading:

```text
Status: in-progress | Progress: [####......] 40%
```

Progress bar uses 10 slots: `#` = completed, `.` = remaining.

---

## Epic Score Formula (A/B)

The Index table shows each epic as `A/B`:

- `B` = total number of features in the epic.
- `A` = sum of per-feature scores, rounded to 2 decimal places.

### Per-Feature Score: F + U

| Variable | Value |
|---|---|
| `F` | `1` if feature `status == done`, else `0` |
| `U` | `completed_stories ÷ total_stories` (0–1 inclusive) |

### Valid Score Bands

| Band | Meaning |
|---|---|
| 0 to <1 | Feature not complete; may have partial story progress |
| Exactly 2 | Feature complete and all stories complete |
| **1 to 2 exclusive** | **Invalid — consistency error** (stories done but feature not marked done, or vice-versa) |

### Worked Example (4 features, 3 stories each)

| State | F | U | Feature Score | A | A/B |
|---|---|---|---|---|---|
| No progress | 0 | 0 | 0 | 0 | 0/4 |
| 1 story done in F2 | 0 | 0.33 | 0.33 | 0.33 | 0.33/4 |
| F1 done, 1 story in F2 | 1 + 0 | 0 + 0.33 | 1.33 total | 1.33 | 1.33/4 |
| F1 done, all F2 stories done | 1 + 0 | 0 + 1 | 2 total | 2 | 2/4 |
| F1 and F2 fully done | 2 + 2 | 0 + 0 | 4 total | 4 | 4/4 |

### Validation Rules for Agents

1. `A` must be ≥ 0 and ≤ `B × 2`. Any value outside this range is a bug.
2. Any per-feature score strictly between 1 and 2 (exclusive) is a consistency error; flag it and do not silently update the index.
3. Recalculate and update `A` in `Index.md` whenever any feature or story changes status to `done`.
