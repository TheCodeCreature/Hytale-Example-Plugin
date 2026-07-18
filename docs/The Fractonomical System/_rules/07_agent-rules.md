---
topic: agent-rules
type: how-to
---

# Agent Execution Rules

## Purpose
Defines the behavioral contract for AI agents, prompts, skills, and hooks operating on backlog files. These rules are enforceable and violations should be flagged.

---

## Mandatory Behaviors

| Rule | Description |
|---|---|
| Preserve IDs | Never modify an existing ticket ID |
| No ID reuse | Never assign an ID that already exists in the tree |
| Update `updated` | Set the `updated` frontmatter field on every meaningful edit |
| Update status/progress | Set `status` and `progress` before starting and after completing any action on a ticket |
| Bidirectional links | When adding a child ticket, add its link in the parent's `## Child Tickets` table |
| Valid YAML | All frontmatter must parse as valid YAML after every edit |
| Minimal blast radius | Only edit files directly related to the current task; do not touch unrelated tickets |
| Framework is read-only by default | Do not create delivery tickets inside this framework repository unless explicitly requested |
| External backlog root | Create and update project tickets only under the configured backlog root (`BACKLOG_ROOT`) |
| Template provenance | New tickets must be instantiated from `_knowledge/_templates/` and then written into `BACKLOG_ROOT` |

---

## Backlog Root Contract

Agents must resolve two roots before ticket creation:

1. `FRAMEWORK_ROOT` = this Fractonomical repository.
2. `BACKLOG_ROOT` = writable external project backlog folder.

Default if user does not specify: `../Project-Backlog` (sibling of `FRAMEWORK_ROOT`).

Agent behavior:

- Read templates from `FRAMEWORK_ROOT/_knowledge/_templates/`.
- Create all execution artifacts in `BACKLOG_ROOT`.
- Keep framework files unchanged during normal project planning.
- Ask for explicit confirmation before editing framework docs.

---

## Status/Progress Gate

Agents must not mark work complete without updating both fields. Hooks and prompts should reject or warn when:

- `status` is `done` but `progress` < 100.
- `status` is `backlog` but `progress` > 0.
- `status` is `in-progress` but `updated` date is unchanged from `created`.

---

## Score Recalculation Trigger

Whenever a feature or story `status` changes to `done`, the agent must:

1. Recompute the epic's `A` score using the formula in [[./04_status-and-progress|Status and Progress]].
2. Update the `Score (A/B)` cell in `Index.md` for that epic.
3. Validate that no per-feature score falls in the invalid 1–2 exclusive band.

---

## File Creation Rules

When creating a new ticket container:

1. Generate the ID using the hierarchical hex format `EEFFSSTT` (for example, `01010000`).
2. Create the folder with the correct `<HierarchicalHexId>_<Slug>` name.
3. Create `Overview.md` by copying the appropriate template from `FRAMEWORK_ROOT/_knowledge/_templates/` into `BACKLOG_ROOT`.
4. Add the child link row in the parent `Overview.md`'s `## Child Tickets` table.
5. Do not create `Directive.md` unless explicitly requested.

---

## Project Manager Prompt Compliance

Any prompt or agent profile operating as "Project Manager" must enforce the Fractonomical planning/documentation contract.

Required behavior:

1. Read and apply `_rules/01` through `_rules/07` before creating or restructuring tickets.
2. Use hierarchical uppercase hex IDs (`EEFFSSTT`) and preserve parent prefixes.
3. Maintain direct-child-only navigation and child-ticket tables on parent containers.
4. Keep frontmatter and body status/progress synchronized on each meaningful update.
5. Check for duplicate intent and ID collisions before creating new nodes.
6. Delegate implementation, testing, review, and cleanup to specialist agents; Project Manager owns backlog structure and orchestration.
7. Resolve and state `BACKLOG_ROOT` before creating any ticket files.
8. Use framework templates as source only; write ticket instances to `BACKLOG_ROOT`.

Prompt-level guardrails:

- Must reject completion claims when `status/progress` gates are violated.
- Must not perform broad refactors outside the active branch without user confirmation.
- Must halt and request clarification whenever hierarchy, dependency, or ownership is ambiguous.
