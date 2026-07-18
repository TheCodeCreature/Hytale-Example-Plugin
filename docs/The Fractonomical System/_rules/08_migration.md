---
topic: migration
type: how-to
---

# Migration Guide for Existing Backlog Files

## Purpose
Step-by-step instructions for aligning an existing backlog that predates this governance system with the current standard, without breaking existing links or losing content.

This includes migrating execution artifacts out of the framework repository into an external backlog root.

---

## Migration Checklist

### Phase 0 — Root Separation

- [ ] Define `FRAMEWORK_ROOT` (this repository) and `BACKLOG_ROOT` (external writable folder).
- [ ] Create `BACKLOG_ROOT` (recommended: `../Project-Backlog`).
- [ ] Copy the required baseline files and folders into `BACKLOG_ROOT`: `Index.md`, `README.md`, and ticket containers.
- [ ] Keep `_rules/` and `_knowledge/` in `FRAMEWORK_ROOT` as governance/template source.

### Phase 1 — Index and Root

- [ ] Create `Index.md` in `BACKLOG_ROOT` using the canonical `Index.md` structure defined by this governance system.
- [ ] Convert `Tracking.md` into a compatibility shim that redirects to `Index.md`.
- [ ] Ensure `README.md` in `BACKLOG_ROOT` points to framework governance docs in `FRAMEWORK_ROOT/_rules/`.

### Phase 2 — Container Files

For each epic, feature, and story folder:

- [ ] Create `Overview.md` by copying and refining content from `Details.md`.
- [ ] Add required frontmatter fields: `updated`, `progress`.
- [ ] Remove `priority` from frontmatter.
- [ ] Update all navigation links to use `Index.md` and relative `../Overview.md` paths.
- [ ] Keep `Details.md` temporarily as a compatibility shim (add a one-line redirect to `Overview.md` at the top).

### Phase 3 — Task Files

- [ ] Rename task files from `Task-01.md` style to `<HierarchicalHexId>_<slug>.md`.
- [ ] Add full frontmatter to each task file.
- [ ] Add navigation links pointing to the parent story `Overview.md` and `Index.md`.

### Phase 4 — Knowledge Graph Cleanup

- [ ] Remove any descendant lists from epic or feature containers (anti-singularity).
- [ ] Add `## Dependencies` tables where cross-ticket dependencies exist.
- [ ] Verify all `parent` and `epic` frontmatter fields are correctly populated.

### Phase 5 — Scoring

- [ ] Populate `Score (A/B)` in `Index.md` by computing the epic score formula for each epic.
- [ ] Validate that no per-feature score falls in the 1–2 exclusive invalid band.

---

## Compatibility Shim Pattern

When keeping a legacy file temporarily, replace its content with:

```markdown
# <FileName> (Compatibility Shim)

This file is retained for backward compatibility.

Use [[./Overview|Overview.md]] as the canonical source.
```
