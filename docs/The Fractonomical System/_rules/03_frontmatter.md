---
topic: frontmatter
type: reference
---

# YAML Frontmatter Standard

## Purpose
Defines the metadata schema every ticket file must carry so that agents and tooling can parse, validate, and cross-reference tickets without reading prose.

## Full Example

```yaml
---
id: "01010100"
type: story
title: "Intake Validation And Authoritative Grant Persistence"
status: backlog
parent: "01010000"
epic: "01000000"
created: 2026-07-16
updated: 2026-07-16
owners:
  - team-backend
tags:
  - collaboration
  - access-control
progress: 0
depends_on: []
blocked_by: []
---
```

## Required Keys

| Key | Description |
|---|---|
| `id` | Ticket ID matching the folder/file name prefix |
| `type` | `epic`, `feature`, `story`, or `task` |
| `title` | Human-readable title matching the slug |
| `status` | See [Status and Progress](./04_status-and-progress.md) |
| `created` | ISO date of first creation |
| `updated` | ISO date of last meaningful edit |
| `progress` | Integer 0–100 |

## Recommended Keys

| Key | Description |
|---|---|
| `parent` | Direct parent ticket ID |
| `epic` | Root epic ID (all levels) |
| `owners` | Team or individual responsible |
| `tags` | Domain labels for filtering |
| `depends_on` | IDs this ticket depends on |
| `blocked_by` | IDs actively blocking this ticket |

## Priority

Do not use a `priority` field. Request order determines active execution priority. If order needs to be captured, use `tags` or a delivery-notes comment.
