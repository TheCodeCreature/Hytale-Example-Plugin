## Overview

Introduce a shared `IconPathResolver` as the single owner of UI icon path normalization and resource-type icon resolution rules. The design principle is simple: callers should stop hand-building UI asset paths, and instead ask one shared component for either a normalized family-specific path or a resolved resource-type icon.

## Design Priorities

- Simplicity
- Extensibility
- Testability
- Framework-native patterns
- Performance

## Decision Summary

### A. Package placement

Decision: place `IconPathResolver` in `com.CodeCreature.ui.common`.

Reasoning:

- The class is fundamentally about UI asset addressing, not registry ownership. `Common/Icons/...` and `Common/GroupIcons/...` are presentation concerns.
- Placing it in `com.CodeCreature.ui.bench` would force `com.CodeCreature.registry.BenchTabGrouper` to depend on `ui.bench`, while `ui.bench` already depends on `registry`. That creates a bidirectional package relationship for no benefit.
- Placing it in `com.CodeCreature.registry` avoids that cycle, but it makes the registry package the owner of UI-specific path rules that are also used by `ui.ingredienttree` and `ui.bench`.
- `com.CodeCreature.ui.common` gives the cleanest dependency direction: `registry`, `ui.bench`, and `ui.ingredienttree` can all depend inward on a shared UI utility without introducing package churn.

Tiebreaker rule: if a class's primary invariant is "valid UI asset path under `Common/UI/Custom/`", it belongs under `ui`, not `registry`.

### B. Static utility vs instance

Decision: make it a `final` class with all-static methods and a private constructor.

Reasoning:

- There is no runtime configuration, no mutable state, and no external dependency to inject.
- Current call sites are either static builders or effectively singleton server-lifetime objects.
- An injectable instance would add ceremony without improving substitution or lifecycle control.
- Testability is not meaningfully worse here because the behavior is pure string transformation plus deterministic engine lookup.

Tiebreaker rule: keep it static until path rules become configurable or engine lookup must be abstracted for isolated tests.

### C. Method surface

Decision: use one public method per family.

Recommended public surface:

- `@Nullable String normalizeItemIcon(String raw)`
- `@Nullable String normalizeResourceTypeIcon(String raw)`
- `@Nullable String normalizeCategoryIcon(String raw)`
- `@Nullable String resolveResourceTypeIcon(String resourceTypeId)`

Reasoning:

- Family-specific methods are self-documenting at the call site. `normalizeCategoryIcon(raw)` is clearer than `normalize(raw, CATEGORY)` when reading UI code.
- A single autodetecting `normalize(String raw)` is the wrong abstraction. Bare filenames are ambiguous across families, and category icons intentionally apply different rules than item/resource icons.
- A public enum-driven API is acceptable internally, but it is not the best outward contract. Adding a fourth family later is straightforward with another method and avoids pushing family-selection decisions onto every caller.

Tiebreaker rule: prefer the API that makes the correct family obvious at the call site and impossible to guess incorrectly from the raw string.

### D. `resolveResourceTypeIcon` contract: null vs non-null

Decision: return `null` when resolution fails.

Reasoning:

- A missing icon is safer than a broken icon path. The current failure mode produces a path that looks valid but points nowhere.
- In the Hytale UI runtime, not setting `#GroupHeaderIcon.Background` is safer than setting it to a non-existent asset. The current UI code already treats `null` group icons as a supported state.
- A hardcoded placeholder would hide data quality issues and still requires confidence that the placeholder asset always exists.

Important caveat:

- `IngredientTreeBuilder` currently overloads `iconPath == null` to mean "direct item leaf" in the ingredient tree. That invariant is acceptable for group-header icon resolution, but it is not acceptable for resource-type leaf classification.
- Therefore, `null` is the correct resolver contract, but ingredient-tree code must stop inferring node kind from `iconPath == null` before unresolved resource-type leaf icons are allowed to propagate into tree nodes.

Tiebreaker rule: `null` is the contract for resolution failure; callers decide whether to suppress rendering, choose an engine-native fallback, or surface telemetry.

### E. Fallback elimination

Decision: do not write a fallback entry into `iconMap` when resolution fails.

Reasoning:

- Absence is cleaner than `null` values for this map. The map's job should be "resolved icon cache", not "tri-state lookup ledger".
- The current `getOrDefault(...)` pattern is part of the bug. The clean fix is to stop using default-string fabrication, not to encode explicit `null` into the same map.
- Distinguishing "checked and missing" from "never checked" has little value here because the tree is built once per data load, not via repeated hot-path lookups.

Required downstream change:

- Replace `getOrDefault(...)` in ingredient-tree construction with explicit `get(...)` handling.
- If the build still needs to distinguish direct items from unresolved resource types, that distinction must move to an explicit field or node kind, not to `iconMap` semantics.

Tiebreaker rule: use absent keys for unresolved icons; if checked-vs-unchecked matters later, add a dedicated result type instead of storing `null` values in a string map.

### F. Migration sequencing

Decision: migrate in four steps, with the active ingredient-tree bug first after the shared utility exists.

Recommended order:

1. Introduce `ui.common.IconPathResolver` with family-specific normalization methods and `resolveResourceTypeIcon(String resourceTypeId)`.
2. Migrate `IngredientTreeBuilder` first.
3. Migrate `StencilSelectionPage` second.
4. Migrate `BenchTabGrouper` last.

Reasoning:

- Step 1 creates one source of truth without changing behavior yet.
- Step 2 removes the active production bug and centralizes the most complete existing resolution chain first. This is the highest-value slice and the best place to validate the resolver contract.
- Step 3 removes storage/render-time divergence for category icons by normalizing at `buildCategoryInfoMap()` time and deleting the inline `lastIndexOf('/')` rewrite.
- Step 4 moves `BenchTabGrouper` last because it has stable behavior today and its fallback-to-default semantics are slightly different from the nullable resolver contract.

Migration note:

- Do not allow unresolved resource-type leaf nodes to carry `null` icon paths until the ingredient tree has an explicit way to distinguish direct-item leaves from resource-type leaves.

### G. Naming

Decision: keep the name `IconPathResolver`.

Reasoning:

- `IconPathNormalizer` under-describes the class if it also performs the registry/engine lookup chain for resource types.
- `IconPaths` reads like a constants holder, not behavior.
- `UiIconPaths` is acceptable but still sounds more like a namespace of path constants than a rule-bearing component.
- `HytaleIconPaths` is broader than the actual responsibility and unnecessarily couples the name to the engine instead of the plugin's UI asset policy.
- `IconPathResolver` correctly signals that callers may hand it partially specified icon data and receive either a normalized concrete path or no result.

Tiebreaker rule: choose the name that still reads correctly if the class contains both normalization helpers and one or two higher-level resolution methods.

## Recommended Contracts

### Normalization rules by family

- Item icons normalize toward `Common/Icons/ItemsGenerated/`.
- Resource-type icons normalize toward `Common/Icons/ResourceTypes/`.
- Category icons normalize toward `Common/GroupIcons/` and should treat engine-provided values as file-oriented, not directory-oriented: take the last segment, reject blank or path traversal, prepend the group-icon base path.

### Storage policy

- Normalize category icon paths when constructing `RecipeFilterPipeline.CategoryInfo`, not at render time.
- Keep `ResourceTypeRegistry.getIconPath(id)` as a filename-returning API for now, but document clearly that it returns a filename, not a full UI path.
- Do not store fabricated fallback paths such as `"Icons/ResourceTypes/" + id + ".png"`.

## Integration Changes Required

- `src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTreeBuilder.java`
  Replace both private normalization helpers with `IconPathResolver` calls; route group-icon resolution through `resolveResourceTypeIcon`; remove raw string fallback writes; stop using `getOrDefault(...)` for icon fabrication.
- `src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTree.java`
  Stop treating `iconPath == null` as the sole discriminator between direct-item leaves and resource-type leaves if unresolved resource-type icons may be null.
- `src/main/java/com/CodeCreature/ui/ingredienttree/IngredientTreeGridController.java`
  Guard leaf icon rendering if unresolved resource-type nodes can carry null, or rely on an explicit node-kind discriminator.
- `src/main/java/com/CodeCreature/ui/bench/StencilSelectionPage.java`
  Normalize category icons at `buildCategoryInfoMap()` time and delete the inline `lastIndexOf('/')` rewrite before `#FilterIcon.Background` assignment.
- `src/main/java/com/CodeCreature/registry/BenchTabGrouper.java`
  Replace `normalizeTabIconPath(...)` with `IconPathResolver.normalizeItemIcon(...)` plus local default fallback behavior.
- `src/main/java/com/CodeCreature/ui/bench/ResourceTypeRegistry.java`
  Clarify in Javadoc that `getIconPath(...)` returns a filename token, not a UI-ready path.

## Open Questions

- None blocking for the architectural decision.
- Implementation must decide whether unresolved resource-type leaf nodes are rendered blank, hidden, or represented by an explicit node variant once `iconPath == null` is no longer reserved exclusively for direct items.

## Recommended Rollout Checkpoints

1. Validate that ingredient-group header icons disappear cleanly when a resource type has no resolvable icon.
2. Validate that category filter icons remain stable for both top-level categories and dot-notation child categories after normalization moves to storage time.
3. Validate that bench-tab mappings still preserve `Bench_*` filename behavior and still fall back to `DEFAULT_TAB_ICON` for invalid bare filenames.

## Final Recommendation

Adopt `com.CodeCreature.ui.common.IconPathResolver` as a static shared utility with family-specific normalization methods and a nullable `resolveResourceTypeIcon(...)` helper. Remove fabricated fallback paths entirely, normalize category icons at ingestion time, and treat the ingredient-tree `iconPath == null` overload as the one architectural constraint that must be addressed during migration.