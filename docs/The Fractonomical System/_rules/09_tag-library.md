---
topic: tag-library
type: reference
---

# Tag Library Rules

## Purpose
Defines how tags are declared on tickets, how the tag catalog is maintained, and the naming conventions that keep tags consistent and queryable.

---

## How Tags Work

Tags are declared in the `tags` frontmatter array on any ticket file:

```yaml
tags:
  - domain:collaboration
  - layer:backend
  - signal:breaking-change
```

Tags serve three purposes:
1. **Filtering** — find all tickets in a domain or layer without traversing the full tree.
2. **Signaling** — surface cross-cutting concerns (tech debt, breaking changes, spikes) that are not visible from status alone.
3. **Graph enrichment** — tools and agents can group tickets by tag across epic boundaries.

---

## Tag Namespace Convention

All tags use a `namespace:value` format. The colon separates the category from the specific label.

| Namespace | Purpose | Examples |
|---|---|---|
| `domain:` | Business or product domain | `domain:collaboration`, `domain:documents`, `domain:billing` |
| `layer:` | Technical layer or system area | `layer:backend`, `layer:frontend`, `layer:data`, `layer:infra` |
| `type:` | Work classification | `type:feature`, `type:bug`, `type:spike`, `type:migration` |
| `kind:` | Knowledge artifact class | `kind:reference`, `kind:topic`, `kind:glossary`, `kind:source` |
| `signal:` | Cross-cutting concerns needing attention | `signal:breaking-change`, `signal:tech-debt`, `signal:needs-spec`, `signal:blocked-external` |
| `team:` | Owning team or sub-group | `team:backend`, `team:platform`, `team:design` |

Freeform tags without a namespace are allowed but discouraged. If a tag is used more than once without a namespace, add it to the catalog with a namespace.

---

## Tag Catalog

The living registry of all defined tags lives at:

[Tags.md](../_meta/Tags.md)

Before adding a new tag to a ticket, check the catalog first. If the tag does not exist, add it to the catalog in the correct namespace section before using it.

---

## Adding a New Tag

1. Open [Tags.md](../_meta/Tags.md).
2. Find the correct namespace section.
3. Add a row to the table: `tag`, `description`, `when to use`.
4. Update the `updated` date at the top of `Tags.md`.
5. Add the tag to the ticket's `tags` array.

Agents must follow this sequence. Do not add undocumented tags to ticket files.

---

## Tag Naming Rules

1. Lowercase only: `domain:access-control` not `domain:AccessControl`.
2. Hyphens for multi-word values: `signal:breaking-change` not `signal:breakingChange`.
3. Be specific enough to be useful but generic enough to apply to more than one ticket.
4. Do not create tags that duplicate status values (for example, do not create `signal:in-progress`).
