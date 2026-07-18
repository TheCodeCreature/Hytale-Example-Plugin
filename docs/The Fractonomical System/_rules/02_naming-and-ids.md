---
topic: naming-and-ids
type: reference
---

# Naming and ID Rules

## Purpose
Defines the canonical format for folder names, file names, and ticket IDs so they remain unique, sortable, and predictable for humans and agents.

## Folder and File Naming

| Element | Format |
|---|---|
| Ticket folder | `<HierarchicalHexId>_<Title-Slug>` |
| Task file | `<HierarchicalHexId>_<Title-Slug>.md` |
| Container file | `Overview.md` (required) |
| Intent file | `Directive.md` (optional) |

Slug style: use `Kebab-Case-Words` consistently within a branch. Do not mix styles.

`Overview.md` replaces the legacy `Details.md` name for all new tickets.

## HierarchicalHexId Format

8-character uppercase hexadecimal value: `EEFFSSTT`

Field semantics:
1. `EE` = epic slot.
2. `FF` = feature slot under epic.
3. `SS` = story slot under feature.
4. `TT` = task slot under story.

Examples:
1. `01000000` = epic.
2. `01010000` = feature under epic `01`.
3. `01010100` = story under feature `01`.
4. `01010101` = task under story `01`.

Because IDs are fixed-width hex strings, alphanumeric sorting preserves hierarchy even when flattened.

## Allocation Strategy

| Slot | Level | Guidance |
|---|---|---|
| `EE` | Epic | Allocate one hex byte per epic (`01`, `02`, ... `0A`, `0B`, ... `FF`) |
| `FF` | Feature | Allocate within an epic; set `SS` and `TT` to `00` |
| `SS` | Story | Allocate within a feature; set `TT` to `00` |
| `TT` | Task | Allocate within a story |

Rules:
1. Use uppercase hex (`0-9`, `A-F`).
2. Use `00` for lower levels not yet instantiated.
3. Preserve parent prefix when creating descendants.
4. Never reuse IDs.
