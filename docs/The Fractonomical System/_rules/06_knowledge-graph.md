---
topic: knowledge-graph
type: explanation
---

# Knowledge Graph and Cross-Reference Rules

## Purpose
Defines how tickets link to each other, how dependencies are declared, and the anti-singularity constraints that keep the graph navigable rather than collapsed into a single hub node.

---

## Link Styles

Two styles are supported. Prefer wiki links for body cross-references because they generate automatic backlinks in graph-aware tools.

| Style | Format | Use |
|---|---|---|
| Standard markdown | `[01010100](../01010100_.../Overview.md)` | Navigation sections, child tables |
| Wiki link | `[[path/to/Overview\|01010100 Title]]` | Dependency tables, cross-references |

---

## Anti-Singularity Rule

A link singularity occurs when every ticket in the tree is directly linked from one file, making that file a universal hub and destroying the signal value of graph traversal.

1. `Index.md` links top-level epics **only**.
2. Epic `Overview.md` links direct features only.
3. Feature `Overview.md` links direct stories only.
4. Story `Overview.md` links direct tasks only.
5. Never enumerate all descendants from any ancestor file.

Parent-child containment implies traversal to all descendants. Explicit links are reserved for direct relationships and declared dependencies.

---

## Dependency Table Contract

When a ticket depends on or is blocked by another ticket, declare it in a `## Dependencies` section using a table with wiki links:

```markdown
## Dependencies

| Relationship | Ticket |
|---|---|
| depends_on | [[../01010100_Intake-Validation-And-Authoritative-Grant-Persistence/Overview\|01010100 Intake Validation And Authoritative Grant Persistence]] |
| blocked_by | [[./01010101_Validate-Grant-Idempotency\|01010101 Validate Grant Idempotency]] |
```

Rules:

1. Use one wiki link per row; link directly to the ticket file.
2. Add the dependency row on **one side only** — the ticket that depends or is blocked. Do not add reciprocal duplicate links in the target ticket unless the relationship is genuinely bidirectional.
3. The `depends_on` and `blocked_by` keys in frontmatter should mirror the IDs listed here.

---

## Frontmatter Graph Keys

| Key | Purpose |
|---|---|
| `parent` | Direct parent ID (structural, single value) |
| `epic` | Root epic ID (repeated at all levels for fast filtering) |
| `depends_on` | IDs this ticket must wait for before starting |
| `blocked_by` | IDs actively preventing progress |
