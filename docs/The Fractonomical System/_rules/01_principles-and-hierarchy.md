---
topic: principles-and-hierarchy
type: explanation
---

# Principles and Canonical Hierarchy

## Purpose
Defines the philosophical intent of this system and the canonical split between the read-only Fractonomical framework and a writable project backlog.

## Design Principles

1. Every container is a ticket.
2. Parent tickets only summarize and link; they should not duplicate child detail.
3. Every ticket has structured YAML frontmatter for machine readability.
4. Every ticket has skimmable markdown sections for human readability.
5. Cross references are first-class (markdown links and wiki links are both allowed).
6. IDs are hierarchical and sortable so flattened views preserve containment order.

## Canonical Hierarchy

```text
Fractonomical-System/             ← framework (read-only in normal project work)
  _rules/
  _knowledge/
    _templates/                   ← source templates only

<Project-Backlog>/                ← writable execution tree (external to framework)
  Index.md
  README.md
  Tracking.md                     ← compatibility shim only
  <epic-hex-id>_<epic-slug>/
    Overview.md
    Directive.md                  ← optional: strategic intent and constraints
    <feature-hex-id>_<feature-slug>/
      Overview.md
      <story-hex-id>_<story-slug>/
        Overview.md
        <task-hex-id>_<task-slug>.md      ← leaf node, no children
```

Default recommendation:
1. Keep this repository untouched except framework updates.
2. Create project tickets in a sibling folder (for example, `../Project-Backlog`).
3. Copy starter structure from `_knowledge/_templates/` into the external backlog folder.

## Containment Rule

Each level only links its direct children. Traversal to deeper descendants is inferred through parent containers. Never flatten the hierarchy into a single file or index.

## Boundary Rule

Framework files (`_rules/`, `_knowledge/`, and root docs in this repository) are governance assets, not delivery artifacts. Project execution tickets must be authored in the external backlog root.
