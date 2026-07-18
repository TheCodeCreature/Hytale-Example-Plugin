---
topic: body-and-navigation
type: reference
---

# Body Structure and Navigation Contract

## Purpose
Defines the required section order inside every ticket file and the navigation links each container must carry to support both human skimming and automated graph traversal.

---

## Body Structure Standard

Sections must appear in this order. Optional sections may be omitted but must not be reordered if present.

| # | Section | Required | Notes |
|---|---|---|---|
| 1 | `# <Title>` | Yes | Matches `title` in frontmatter |
| 2 | Status + progress line | Yes | `Status: … \| Progress: […] …%` |
| 3 | `## Navigation` | Yes | See navigation contract below |
| 4 | `## Outcome` or `## User Story` | Yes | `Outcome` for epic/feature; `User Story` for story/task |
| 5 | `## Acceptance Criteria` | Yes | |
| 6 | `## Child Tickets` | Containers only | Table of direct children |
| 7 | `## Dependencies` | When applicable | See [Knowledge Graph](./06_knowledge-graph.md) |
| 8 | `## Delivery Notes` | Optional | |
| 9 | `## Change Log` | Optional | |

---

## Navigation Contract

Every container `Overview.md` must contain these links in `## Navigation`:

1. Link to `Index.md` at the backlog root.
2. Link to the direct parent `Overview.md` (omit for epic root).
3. Links to each direct child `Overview.md` or task file.

**Rule:** link only direct children. Never deep-link to grandchildren or lower from a parent ticket.

### Link Target Resolution

| Level | Index link | Parent link |
|---|---|---|
| Epic | `../../Index.md` | — |
| Feature | `../../../Index.md` | `../Overview.md` |
| Story | `../../../../Index.md` | `../Overview.md` |
| Task | `../../../../Index.md` | `../Overview.md` (story) |
