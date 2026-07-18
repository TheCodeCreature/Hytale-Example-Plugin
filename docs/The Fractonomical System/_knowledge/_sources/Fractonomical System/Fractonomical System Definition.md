---
topic: fractonomical-system-definition
type: explanation
source_id: "FS-0001"
source_container: "_knowledge/_sources/Fractonomical System"
---

# Fractonomical System Definition

## Navigation
- [Knowledge Catalog](../../Catalog.md)
- [Knowledge Hub](../../README.md)
- [Source Overview](./Overview.md)

## Purpose
Define the Fractonomical System as the canonical operating model for this backlog: a recursive, containment-first documentation architecture where each planning node is a ticket, each ticket is machine-parseable, and each edge is constrained to preserve graph signal.

## Source Placement Contract
This document is canonical source knowledge and must live under `_knowledge/_sources/`.

Rules for placement:
1. Keep this file in `_knowledge/_sources/Fractonomical System/`.
2. Do not place this file in `_rules/`; `_rules/` contains governance contracts, not long-form source artifacts.
3. If governance rules evolve, update the rule files in `_rules/` and then sync this source artifact.

## Core Definition
The Fractonomical System is a hierarchical decomposition model that treats product delivery as a nested system of bounded containers:

1. Each container is a ticket with a stable identity.
2. Each parent container describes intent and exposes direct children only.
3. Each child container inherits context from ancestors through structure, not copy-pasted prose.
4. Lifecycle state is explicit and continuously updated.
5. Cross-container dependencies are declared as graph edges, not inferred from narrative text.

In this model, decomposition is fractal in behavior: the same contracts repeat at every level (identity, metadata, status, navigation, dependencies), while scope narrows from strategic outcomes to executable work.

## Structural Grammar
The system uses a constrained grammar for both folders and files.

```text
backlog/
  Index.md
  README.md
  _rules/
  _knowledge/
    _templates/
    _sources/
      Fractonomical System/
        Overview.md
        Fractonomical System Definition.md
  <epic-hex-id>_<epic-slug>/
    Overview.md
    Directive.md                  (optional)
    <feature-hex-id>_<feature-slug>/
      Overview.md
      <story-hex-id>_<story-slug>/
        Overview.md
        <task-hex-id>_<task-slug>.md
```

Interpretation:
1. `Index.md` is a top-level registry, not a full graph dump.
2. `README.md` is the governance hub linking rule spokes.
3. `Overview.md` is the required container contract for epic, feature, and story levels.
4. `<task-hex-id>_<task-slug>.md` is a leaf contract with no children.
5. `Directive.md` is optional and only present when strategic constraints must be explicit.

## Identity and Ordering Model
Ticket identity is deterministic and sortable using:

- HierarchicalHexId: `EEFFSSTT`
- Slug: semantic descriptor

Properties:
1. IDs are globally stable once issued.
2. Hierarchical ordering is preserved by lexical sorting.
3. Descendants inherit the parent prefix (`EE` -> `EEFF` -> `EEFFSS` -> `EEFFSSTT`).
4. Folder and frontmatter IDs must match.

This yields a monotonic namespace where history and intent remain legible to humans and agents.

## Metadata Contract (Machine Layer)
Every ticket carries YAML frontmatter as an executable contract.

Required semantics:
1. `id`, `type`, `title` establish identity and class.
2. `status`, `progress` encode lifecycle state.
3. `created`, `updated` provide temporal integrity.

Recommended graph semantics:
1. `parent` binds structural lineage.
2. `epic` binds root initiative context.
3. `depends_on` and `blocked_by` express non-tree constraints.
4. `owners` and `tags` support ownership and queryability.

The Fractonomical principle is that prose explains intent, while frontmatter defines computable truth.

## Behavioral Contract (Human Layer)
Each ticket body follows a fixed rendering order so all nodes are skimmable and tool-compatible:

1. Title
2. Status/progress line
3. Navigation
4. Outcome or User Story
5. Acceptance Criteria
6. Child Tickets (containers only)
7. Dependencies (when applicable)
8. Delivery Notes (optional)
9. Change Log (optional)

This order converts every ticket into a predictable interface.

## Navigation Topology
Navigation follows strict parent-child locality:

1. A node links to root index and direct parent (if any).
2. A node links to direct children only.
3. A node never enumerates grandchildren or deeper descendants.

This prevents topological collapse and enforces bounded context per file.

## Graph Theory: Containment Plus Dependencies
The Fractonomical graph has two edge classes:

1. Containment edges (tree): `parent -> child`
2. Dependency edges (cross-links): `depends_on` and `blocked_by`

Containment provides deterministic traversal. Dependency edges encode execution reality across branches.

Anti-singularity constraint:
1. No universal mega-hub listing all descendants.
2. Graph discoverability is distributed across containers.
3. `Index.md` remains epic-only by design.

## Lifecycle Semantics
State and progress are first-class operational fields.

Rules:
1. Before work: set `status: in-progress` and initialize progress baseline.
2. During work: update progress at meaningful checkpoints.
3. Completion: set `status: review` or `done` and `progress: 100`.
4. Blockers: set `status: blocked` and capture the blocker in notes/dependencies.

The system rejects stale state as a governance violation, not a minor formatting issue.

## Aggregate Progress Semantics (Epic Score A/B)
Epic health is represented as `A/B`:

- `B`: total features in epic.
- `A`: sum of feature scores where each feature score is `F + U`.

Where:
- `F = 1` if feature status is `done`, else `0`.
- `U = completed_stories / total_stories`.

This creates a dual signal:
1. Structural completion (`F`)
2. Execution completion (`U`)

Consistency guardrail:
- Any feature score in `(1, 2)` is invalid and signals state mismatch.

## Agent Operating Semantics
Agents act as contract enforcers inside the Fractonomical System.

Minimum obligations:
1. Preserve and never reuse IDs.
2. Keep frontmatter valid YAML after each edit.
3. Update `updated`, `status`, and `progress` with each meaningful action.
4. Maintain parent `Child Tickets` linkage when adding children.
5. Restrict edits to local blast radius.
6. Recalculate epic score when feature/story completion changes.

Agent autonomy is permitted only inside these invariants.

## Tagging as Secondary Index
Tags provide an orthogonal query layer across the containment tree.

Design:
1. Use namespaced tags (`domain:`, `layer:`, `type:`, `signal:`, `team:`).
2. Keep values lowercase and hyphenated.
3. Register new tags in the catalog before use.

Containment answers where work belongs. Tags answer how work is grouped across structures.

## Why It Is Fractonomical
The system is fractonomical because each scale level repeats the same governance atoms while changing only scope:

1. Epic: strategic outcome container.
2. Feature: capability container.
3. Story: user-value container.
4. Task: executable action container.

Each level reuses the same interface (identity, lifecycle, navigation, criteria, dependencies), producing self-similar behavior from strategy to execution.

## Practical Outcomes
Adopting this definition yields:

1. Deterministic decomposition from initiative to implementation.
2. Lower navigation entropy through local links and anti-singularity constraints.
3. Improved automation reliability via strict frontmatter contracts.
4. Traceable execution through explicit lifecycle and dependency updates.
5. Scalable governance where additional work increases depth and breadth without collapsing readability.

## Non-Goals
The Fractonomical System does not attempt to:

1. Replace delivery tools or sprint mechanics.
2. Flatten all planning into one canonical file.
3. Encode priority as a fixed field.
4. Model every relationship as a bidirectional link.

It is a governance substrate for backlog intelligence, not a project management product.

## Canonical References
- [Principles and Hierarchy](../../../_rules/01_principles-and-hierarchy.md)
- [Naming and IDs](../../../_rules/02_naming-and-ids.md)
- [Frontmatter Standard](../../../_rules/03_frontmatter.md)
- [Status and Progress](../../../_rules/04_status-and-progress.md)
- [Body Structure and Navigation](../../../_rules/05_body-and-navigation.md)
- [Knowledge Graph](../../../_rules/06_knowledge-graph.md)
- [Agent Execution Rules](../../../_rules/07_agent-rules.md)
- [Tag Library Rules](../../../_rules/09_tag-library.md)
