# Initiative Backlog System — Governance Hub

This folder is a file-system-native product initiative tracker organized by the **hub-and-spoke** documentation pattern with **topic-based authoring**. Each governance concern lives in its own focused file under `_rules/`. This file is the hub; the spokes are linked below.

This repository is intended to be the **Fractonomical framework root** (governance + templates). For future projects, keep this folder read-only and author delivery tickets in a separate writable backlog folder.

Start at [Index.md](./Index.md) to navigate active initiatives.

All starter templates live under [./_knowledge/_templates](./_knowledge/_templates/).

Recommended split for future projects:

1. `FRAMEWORK_ROOT` = this repository.
2. `BACKLOG_ROOT` = sibling folder such as `../Project-Backlog`.
3. Copy templates from `_knowledge/_templates/` into `BACKLOG_ROOT` and perform all ticket edits there.

Reference knowledge is managed under [./_knowledge](./_knowledge/README.md), using a graph-first structure for books, papers, wiki packs, and study sessions. Instantiated backlog tickets remain outside `_knowledge/` as execution artifacts.

---

## Rules and Guides

| # | File | Diátaxis Type | What It Covers |
|---|------|---------------|----------------|
| 01 | [Principles and Hierarchy](./_rules/01_principles-and-hierarchy.md) | Explanation | System philosophy, containment model, canonical folder tree |
| 02 | [Naming and IDs](./_rules/02_naming-and-ids.md) | Reference | Folder/file naming format and hierarchical hex ID conventions |
| 03 | [Frontmatter Standard](./_rules/03_frontmatter.md) | Reference | Required and recommended YAML keys for every ticket |
| 04 | [Status and Progress](./_rules/04_status-and-progress.md) | Reference | Status model, execution discipline, epic score formula (A/B) |
| 05 | [Body Structure and Navigation](./_rules/05_body-and-navigation.md) | Reference | Required section order, navigation link contract per level |
| 06 | [Knowledge Graph](./_rules/06_knowledge-graph.md) | Explanation | Link styles, anti-singularity rules, dependency table contract |
| 07 | [Agent Execution Rules](./_rules/07_agent-rules.md) | How-to | Behavioral contract for AI agents, prompts, hooks, and skills |
| 08 | [Migration Guide](./_rules/08_migration.md) | How-to | Step-by-step checklist for aligning legacy backlog files |
| 09 | [Tag Library Rules](./_rules/09_tag-library.md) | Reference | Namespace convention, catalog contract, agent rules for tags |
| 10 | [Knowledge System Rules](./_rules/10_knowledge-system.md) | Reference | Folder contracts for books/papers/wiki knowledge, topic maps, and study-session graphing without project progress fields |

**Tag Catalog:** [Tags.md](./_meta/Tags.md) — living registry of all defined tags, grouped by namespace.

## Knowledge Sources

Primary source documents live under `_knowledge/_sources/`.

| Source | Location | Purpose |
|---|---|---|
| Fractonomical System Definition | [./_knowledge/_sources/Fractonomical System/Fractonomical System Definition.md](./_knowledge/_sources/Fractonomical%20System/Fractonomical%20System%20Definition.md) | Conceptual source for system philosophy and decomposition model |

---

## About This Approach

**Topic-based authoring** breaks a monolithic document into discrete, single-concern files. Each file owns one topic and can be read, updated, and linked independently. This prevents any one file from becoming a maintenance bottleneck.

**Hub-and-spoke navigation** means this `README.md` is the hub: it provides orientation and links but contains no authoritative content itself. The spokes (the `_rules/` files) are the sources of truth.

**Diátaxis** is the classification framework used for the `type` frontmatter in each rules file. It distinguishes four kinds of documentation:
- *Explanation* — conceptual understanding (why and how it works)
- *Reference* — precise, scannable facts (what the rules are)
- *How-to* — goal-oriented steps (how to perform a specific task)
- *Tutorial* — learning-oriented walkthroughs (not yet used here)

## Design Principles

1. Every container is a ticket.
2. Parent tickets only summarize and link; they should not duplicate child detail.
3. Every ticket has structured YAML frontmatter for machine readability.
4. Every ticket has skimmable markdown sections for human readability.
5. Cross references are first-class (markdown links and wiki links are both allowed).
6. IDs are hierarchical and sortable so flattened lists preserve containment order.

For full detail on each principle, see [Principles and Hierarchy](./_rules/01_principles-and-hierarchy.md).
