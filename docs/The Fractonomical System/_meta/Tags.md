---
type: tag-catalog
updated: 2026-07-16
---

# Tag Catalog

Living registry of all defined tags in this backlog system. Read the usage rules at [Tag Library Rules](../_rules/09_tag-library.md) before adding entries.

Tags use `namespace:value` format. Add a row to the correct section before using a new tag on any ticket.

---

## `domain:` — Business or Product Domain

Identifies the product or business area a ticket belongs to. Use when the ticket's concern maps to a named capability or user-facing domain.

| Tag | Description | When to Use |
|---|---|---|
| `domain:collaboration` | Sharing, invites, and access grants between users | Tickets involving collaborators, invite flows, or shared document access |
| `domain:documents` | Document lifecycle, content, and metadata | Tickets involving document creation, editing, conversion, or storage |
| `domain:projects` | Project containers and organization | Tickets scoped to the project management layer |
| `domain:templates` | Document and custom templates | Tickets involving template authoring, uploading, or applying |
| `domain:search` | Search indexing and query | Tickets involving full-text or structured search |
| `domain:notifications` | System and user notifications | Tickets involving email, in-app, or push notification flows |
| `domain:billing` | Subscriptions, upgrades, and payments | Tickets involving subscription plans or payment processing |
| `domain:auth` | Authentication and identity | Tickets involving login, tokens, identity providers, or session management |
| `domain:admin` | Administration and organization management | Tickets scoped to admin-level controls or org settings |

---

## `layer:` — Technical Layer or System Area

Identifies which technical boundary owns the work. Use alongside `domain:` to narrow scope.

| Tag | Description | When to Use |
|---|---|---|
| `layer:backend` | API, domain services, and infrastructure | Tickets in Catina.API, Catina.Domain, or Catina.Infrastructure |
| `layer:frontend` | Client-side UI and UX | Tickets that touch React, Angular, or other client code |
| `layer:data` | Data model, persistence, and migrations | Tickets changing MongoDB schemas, indexes, or migration scripts |
| `layer:infra` | Cloud infrastructure and DevOps | Tickets touching Azure resources, pipelines, or deployment config |
| `layer:functions` | Azure Functions and background jobs | Tickets in Catina.FunctionApps or Catina.YjsFunctionApp |
| `layer:realtime` | SignalR hubs and YJS collaboration | Tickets involving real-time collaboration or hub communication |

---

## `type:` — Work Classification

Describes the nature of the work independent of domain or layer.

| Tag | Description | When to Use |
|---|---|---|
| `type:feature` | New user-facing capability | Standard feature development |
| `type:bug` | Defect or incorrect behavior | Fixes to existing behavior |
| `type:spike` | Time-boxed investigation or proof of concept | Research tasks with uncertain output |
| `type:migration` | Data or code migration | Schema changes, data moves, or API version upgrades |
| `type:refactor` | Internal restructuring without behavior change | Code quality or architecture improvements |
| `type:test` | Test coverage additions or improvements | Tasks solely focused on test authoring |
| `type:docs` | Documentation updates | Tickets that only change documentation |
| `type:config` | Configuration or environment changes | Feature flags, settings, or infrastructure config |

---

## `kind:` — Knowledge Artifact Class

Identifies the knowledge role of a node or source-level file in the reference graph.

| Tag | Description | When to Use |
|---|---|---|
| `kind:reference` | Distilled source-level reference guidance | Source overviews and quick-reference material |
| `kind:topic` | Concept node in the topic graph | Topic or subtopic overview nodes |
| `kind:glossary` | Terminology definition artifact | Dictionary, glossary, and term-definition notes |
| `kind:source` | Canonical source artifact marker | Source-container files and source metadata notes |

---

## `signal:` — Cross-Cutting Concerns

Surfaces conditions that need attention regardless of status. Agents and reviewers should scan for these.

| Tag | Description | When to Use |
|---|---|---|
| `signal:breaking-change` | Public contract or API change that requires consumer updates | Any endpoint, schema, or event change that breaks existing callers |
| `signal:tech-debt` | Known shortcut or suboptimal design accepted intentionally | When a decision is made to defer a better solution |
| `signal:needs-spec` | Acceptance criteria or design is incomplete | Ticket cannot start until more detail is added |
| `signal:blocked-external` | Blocked by a team, system, or dependency outside this backlog | When the blocker is not a ticket in this tree |
| `signal:security` | Security-sensitive change requiring review | Auth changes, permission checks, data exposure risks |
| `signal:performance` | Performance-critical path or known bottleneck | Tickets where latency or throughput is the primary concern |
| `signal:experimental` | Approach is uncertain and may be reverted | Spikes promoted to stories that still carry risk |

---

## `team:` — Owning Team or Sub-Group

Identifies which team or sub-group is responsible. Use when ownership differs from the default.

| Tag | Description | When to Use |
|---|---|---|
| `team:backend` | Backend engineering team | Default for Catina.Domain / Catina.Infrastructure work |
| `team:platform` | Platform or DevOps team | Infrastructure, pipelines, and shared tooling |
| `team:design` | Design and UX team | UI/UX tickets with design deliverables |
| `team:qa` | Quality assurance team | Test strategy, test authoring, or QA-owned verification |
