# Design: Recipe Filter Pipeline

## 1. Overview

`RecipeFilterPipeline` is a standalone, sequential filter pipeline that replaces the interleaved filtering logic currently split across `StencilSelectionPage.applyFilter()` and `buildRecipeList()`. Each stage has a single responsibility: take a list, produce a list. Affordability is computed **once** in a dedicated tagging stage, and both the sidebar set list (`currentSets`) and per-recipe dimming (`affordable`) derive from the same intermediate result.

The `CraftableFilter` enum (NONE / PARTIAL / FULL) is removed. The pipeline always computes affordability — unaffordable recipes are dimmed, never hidden.

## 2. Design Priorities

1. **Single responsibility per stage** — each stage does one thing: filter, tag, extract, or sort
2. **No redundant affordability checks** — `isAffordable()` runs once per recipe per pipeline execution
3. **Correctness** — `currentSets` and recipe filtering derive from the same tagged data
4. **Simplicity** — remove the CraftableFilter 3-mode dropdown; always tag, always dim
5. **Testability** — pipeline is a pure function (no UI state, no side effects); stages are package-private for unit testing

## 3. Component Diagram

```mermaid
classDiagram
    class RecipeFilterPipeline {
        +execute(allRecipes, activeTab, setFilters, query, checker) PipelineResult
        ~filterByTab(recipes, activeTab) List~InputRecipe~
        ~filterBySearch(recipes, query) List~InputRecipe~
        ~tagAffordability(recipes, checker) List~TaggedRecipe~
        ~extractSets(recipes) List~String~
        ~filterBySets(recipes, setFilters) List~TaggedRecipe~
        ~sort(recipes) List~TaggedRecipe~
    }

    class InputRecipe {
        &lt;&lt;record&gt;&gt;
        +String recipeId
        +String outputItemId
        +String blockTypeId
        +String benchId
        +String set
    }

    class TaggedRecipe {
        &lt;&lt;record&gt;&gt;
        +String recipeId
        +String outputItemId
        +String blockTypeId
        +String benchId
        +String effectiveSet
        +boolean affordable
    }

    class PipelineResult {
        &lt;&lt;record&gt;&gt;
        +List~TaggedRecipe~ displayedRecipes
        +List~String~ currentSets
    }

    class AffordabilityChecker {
        &lt;&lt;interface&gt;&gt;
        +isAffordable(InputRecipe recipe) boolean
    }

    class StencilSelectionPage {
        -RecipeFilterPipeline pipeline
        -List~InputRecipe~ allRecipes
        -isAffordable(recipe, container) boolean
        -refreshFilter() void
    }

    RecipeFilterPipeline --> InputRecipe : filters
    RecipeFilterPipeline --> TaggedRecipe : produces
    RecipeFilterPipeline --> PipelineResult : returns
    RecipeFilterPipeline --> AffordabilityChecker : uses
    StencilSelectionPage --> RecipeFilterPipeline : delegates to
    StencilSelectionPage ..|> AffordabilityChecker : provides impl
```

## 4. Responsibility Map

```mermaid
graph TB
    INPUT["allRecipes\nList&lt;InputRecipe&gt;"] --> S1["Stage 1: filterByTab\n(activeTab)"]
    S1 -->|"tab-matched"| S2["Stage 2: filterBySearch\n(searchQuery)"]
    S2 -->|"search-matched"| S3["Stage 3: tagAffordability\n(AffordabilityChecker)"]
    S3 -->|"TaggedRecipe list"| FORK{" "}
    FORK -->|"same list"| S4["Stage 4: extractSets\n→ currentSets"]
    FORK -->|"same list"| S5["Stage 5: filterBySets\n(activeSetFilters)"]
    S5 -->|"set-filtered"| S6["Stage 6: sort"]
    S4 -->|"currentSets"| RESULT["PipelineResult"]
    S6 -->|"displayedRecipes"| RESULT
```

### Stage details

| Stage | Method | Input | Output | Responsibility |
|-------|--------|-------|--------|----------------|
| 1 | `filterByTab` | `List<InputRecipe>`, `activeTab` | `List<InputRecipe>` | Retain recipes matching bench tab; pass all if `"All"` |
| 2 | `filterBySearch` | `List<InputRecipe>`, `searchQuery` | `List<InputRecipe>` | Retain recipes where recipeId, blockTypeId, or set contains query (case-insensitive); pass all if empty |
| 3 | `tagAffordability` | `List<InputRecipe>`, `AffordabilityChecker` | `List<TaggedRecipe>` | Convert to TaggedRecipe; normalize null `set` → `UNCATEGORIZED_SET`; compute `affordable` via checker (true if checker is null) |
| 4 | `extractSets` | `List<TaggedRecipe>` | `List<String>` | Collect unique `effectiveSet` values, sorted case-insensitive |
| 5 | `filterBySets` | `List<TaggedRecipe>`, `Set<String>` | `List<TaggedRecipe>` | Retain recipes whose `effectiveSet` is in `activeSetFilters`; pass all if filters is empty |
| 6 | `sort` | `List<TaggedRecipe>` | `List<TaggedRecipe>` | Sort by `effectiveSet` (alpha) → `affordable` first → `recipeId` (alpha) |

### Where key outputs are computed

- **`currentSets`** — Stage 4 (`extractSets`), derived from the Stage 3 output (post-affordability tagging, pre-set filtering). This means the sidebar always shows all sets present in the tab+search results.
- **`affordable` boolean** — Stage 3 (`tagAffordability`), computed once per recipe. The checker implementation in `StencilSelectionPage` calls `isAffordable()` which handles both raw material checks and BlockGroup interchangeability.

## 5. Sequence Diagram

```mermaid
sequenceDiagram
    participant UI as StencilSelectionPage
    participant P as RecipeFilterPipeline
    participant AC as AffordabilityChecker

    UI->>P: execute(allRecipes, tab, sets, query, checker)
    P->>P: filterByTab(allRecipes, tab)
    P->>P: filterBySearch(tabFiltered, query)
    loop Each search-filtered recipe
        P->>AC: isAffordable(recipe)
        AC-->>P: boolean
    end
    Note over P: tagAffordability produces TaggedRecipe list
    P->>P: extractSets(tagged) → currentSets
    P->>P: filterBySets(tagged, activeSetFilters)
    P->>P: sort(setFiltered)
    P-->>UI: PipelineResult(displayedRecipes, currentSets)
    UI->>UI: buildSetFilters(currentSets)
    UI->>UI: buildRecipeGrid(displayedRecipes)
```

## 6. Package Structure

```
src/main/java/com/UnobstructedThirdPerson/placeblock/ui/
├── RecipeFilterPipeline.java          # Pipeline class with nested records + interface
└── StencilSelectionPage.java        # Modified: delegates to pipeline, owns AffordabilityChecker impl
```

All new types (`InputRecipe`, `TaggedRecipe`, `PipelineResult`, `AffordabilityChecker`) are nested inside `RecipeFilterPipeline` to keep the public API surface small.

## 7. Integration Changes Required

### `StencilSelectionPage.java`

| Change | Description |
|--------|-------------|
| **Remove** `CraftableFilter` enum | No longer needed — affordability is always computed |
| **Remove** `craftableFilter` field | No longer needed |
| **Remove** `CraftableDropdown` binding in `build()` | Dropdown removed from UI |
| **Remove** `CraftableFilter` handling in `handleDataEvent()` | No dropdown events to handle |
| **Replace** `RecipeEntry` record | Use `RecipeFilterPipeline.InputRecipe` for `allRecipes`, `RecipeFilterPipeline.TaggedRecipe` for `displayedRecipes` |
| **Replace** `applyFilter()` body | Single call: `pipeline.execute(allRecipes, activeTab, activeSetFilters, searchQuery, checker)` → store `currentSets` and `displayedRecipes` from result |
| **Simplify** `buildRecipeList()` | Remove affordability computation loop — iterate `displayedRecipes` directly, read `affordable` from `TaggedRecipe` |
| **Adapt** `loadRecipes()` | Produce `List<InputRecipe>` instead of `List<RecipeEntry>` |
| **Keep** `isAffordable()` method | Wrap as `AffordabilityChecker` lambda: `recipe -> isAffordable(recipe, container)` |
| **Remove** `filteredRecipes` field | No longer needed — pipeline produces the final list |
| **Add** `RecipeFilterPipeline pipeline` field | Instantiate once, reuse across filter calls |

### `StencilBookPage.ui` (UI template)

| Change | Description |
|--------|-------------|
| **Remove** `#CraftableDropdown` | No longer needed |

### Set-less recipe handling

Recipes where `Item.set` is null are assigned `effectiveSet = "Uncategorized"` in Stage 3. This means:
- They appear in the sidebar under "Uncategorized"
- They are filterable like any other set
- They are never silently dropped

## 8. Open Questions

1. **"Uncategorized" label** — Is "Uncategorized" the right display name for set-less recipes, or should it be something else (e.g., "Other", "Misc")?
2. **Sidebar ordering** — Should "Uncategorized" appear at the end of the set list, or alphabetically? Current design sorts alphabetically.
3. **Empty pipeline result** — If all recipes are filtered out (e.g., search matches nothing), should the sidebar show no sets or retain the full set list? Current design: no sets (derived from same data).

## 9. Handoff Checklist

- [x] Component diagram included
- [x] Responsibility map included
- [x] All interfaces have Javadoc contracts
- [x] All skeleton files created with TODO markers
- [x] Integration Changes Required section populated
- [x] Open Questions section populated
