# Review: Resource Type Filter — Bug Root Cause Analysis

## 1. Executive Summary

The Resource Type Input Filter has **three interconnected bugs** stemming from two root causes: (1) `recipeMatchesResourceTypes()` implements an incomplete matching algorithm that ignores ItemId-based recipe inputs and uses wrong ResourceTypeId values from a misbuilt registry, and (2) `updateDetailPanel()` unconditionally applies inventory-based affordability styling regardless of the active affordability mode. The highest-impact fix is rewriting `recipeMatchesResourceTypes()` to use `ResourceTypeResolver`'s existing item-resolution logic, which already handles both `ItemId` and `ResourceTypeId` inputs correctly.

---

## 2. Current Architecture Diagram

```mermaid
graph TB
    subgraph "User selects 'Any_Bone' in Resource Type Grid"
        A["ResourceTypeRegistry<br/>resourceTypeId = 'Any_Bone'"]
        B["activeResourceTypes.add('Any_Bone')"]
        C["applyFilter() → RESOURCE_DRIVEN branch"]
        D["recipeMatchesResourceTypes(recipe, {'Any_Bone'})"]
        E["CraftingRecipe.getInput()"]
        F{"input.getResourceTypeId()"}
        G["Recipe uses ItemId:<br/>'Ingredient_Bone_Fragment'"]
        H["Recipe uses ResourceTypeId:<br/>'Bone'"]
        I["resTypeId == null → SKIP"]
        J["'Bone' ∈ {'Any_Bone'}? → NO"]
        K["return false ❌"]
    end

    A --> B --> C --> D --> E --> F
    F -->|"ItemId-based input"| G --> I --> K
    F -->|"ResourceTypeId-based input"| H --> J --> K

    style A fill:#f66,stroke:#900
    style D fill:#f66,stroke:#900
    style G fill:#f96,stroke:#960
    style H fill:#f96,stroke:#960
    style K fill:#f66,stroke:#900
```

---

## 3. Findings Table

| # | Category | Severity | Location | Detail |
|---|----------|----------|----------|--------|
| 1a | Logic Error | 🔴 Blocked | [StencilSelectionPage.java](src/main/java/com/UnobstructedThirdPerson/placeblock/ui/StencilSelectionPage.java#L894-L912) | `recipeMatchesResourceTypes()` only checks `input.getResourceTypeId()`. Recipes whose inputs use `ItemId` (e.g., `Deco_Bone_Skulls_Wall` uses `ItemId: "Ingredient_Bone_Fragment"`) are silently skipped because `getResourceTypeId()` returns null for those inputs. The function never resolves the ItemId to its item definition to check `Item.getResourceTypes()`. |
| 1b | Contract Violation | 🔴 Blocked | [ResourceTypeRegistry.java](src/main/java/com/UnobstructedThirdPerson/placeblock/ui/ResourceTypeRegistry.java#L51) | The registry entry `"Any_Bone"` was derived from the **icon filename** (`Any_Bone.png`), not from the actual engine ResourceTypeId. The engine's resource type definition is [`Bone.json`](docs/Reference%20Assets/Assets/Server/Item/ResourceTypes/Bone.json) — the ResourceTypeId is `"Bone"`, and `Any_Bone.png` is merely its icon. Selecting "Any_Bone" in the UI adds `"Any_Bone"` to `activeResourceTypes`, but no recipe input or item declaration uses that string. Both `"Bone"` (index 9) and `"Any_Bone"` (index 0) exist as separate registry entries, making the generic entry permanently non-functional. |
| 2 | Architecture | 🟡 Should Fix | [StencilSelectionPage.java](src/main/java/com/UnobstructedThirdPerson/placeblock/ui/StencilSelectionPage.java#L772-L842) | `updateDetailPanel()` unconditionally performs inventory-based affordability checks (`countItemInInventory()`, red border, red cost quantities) regardless of `affordabilityMode`. In `RESOURCE_DRIVEN` mode, the recipe grid dims items based on resource type matching (via the pipeline's `tagResourceTypeMatch`), but the detail panel shows inventory-based red/green — creating a visual contradiction where a recipe appears highlighted in the grid but "unaffordable" in the detail panel. |
| 3a | Redundancy | 🟡 Should Fix | [StencilSelectionPage.java](src/main/java/com/UnobstructedThirdPerson/placeblock/ui/StencilSelectionPage.java#L894-L912) | `recipeMatchesResourceTypes()` reimplements a partial, incorrect version of resource-type resolution that `ResourceTypeResolver` already handles correctly. The resolver handles both `ItemId` and `ResourceTypeId` inputs, applies set-root preference, and filters decorative items — none of which `recipeMatchesResourceTypes()` does. |
| 3b | Redundancy | 🟠 QA | [StencilSelectionPage.java](src/main/java/com/UnobstructedThirdPerson/placeblock/ui/StencilSelectionPage.java#L772-L810) vs [StencilSelectionPage.java](src/main/java/com/UnobstructedThirdPerson/placeblock/ui/StencilSelectionPage.java#L1062-L1080) | `updateDetailPanel()` resolves recipe inputs using `ResourceTypeResolver.resolveInputItemId()` + `NaturalResourceRegistry.resolveToGatherableForm()` for display, then checks affordability via `countItemInInventory()`. Meanwhile, `isAffordable()` uses `canRemoveMaterials()` for the same purpose but with different semantics (it also checks BlockGroup interchangeability). Two different affordability checks coexist with different logic. |
| 3c | Scalability | 🔵 Review | [ResourceTypeRegistry.java](src/main/java/com/UnobstructedThirdPerson/placeblock/ui/ResourceTypeRegistry.java#L48-L130) | The registry is a hardcoded `List.of(...)` with 79 entries derived from icon filenames rather than from the engine's `ResourceTypes/*.json` asset definitions. Adding/removing resource types requires a code change. The `"Any_*"` entries (Any_Bone, Any_Book, Any_Meat, Any_Mushroom, Any_Recipe, Any_Rock, Any_Rubble, Any_Trunk) appear to be **icon names** that the engine shares across multiple ResourceType definitions, not actual ResourceTypeId values. |

---

## 4. Detailed Root Cause Analysis

### Bug 1: Generic resource types don't match any recipes

**Two independent failures conspire to produce zero matches:**

**Failure A — ItemId inputs ignored (Finding 1a):**

The recipe for `Deco_Bone_Skulls_Wall` specifies its input as:
```json
{ "ItemId": "Ingredient_Bone_Fragment", "Quantity": 4 }
```

The current matching function at line 894:
```java
String resTypeId = input.getResourceTypeId();
if (resTypeId != null && selectedTypes.contains(resTypeId)) {
    return true;
}
```

For this recipe, `input.getResourceTypeId()` returns `null` because the input uses `ItemId`, not `ResourceTypeId`. The function never resolves `Ingredient_Bone_Fragment` to its item definition to check whether it declares `ResourceTypes: [{Id: "Bone"}]`.

Meanwhile, `ResourceTypeResolver.resolveInputItemId()` at [ResourceTypeResolver.java](src/main/java/com/UnobstructedThirdPerson/resourcecollection/ResourceTypeResolver.java#L66-L79) already handles both paths correctly — it checks `ItemId` first, then falls back to `ResourceTypeId` resolution.

**Failure B — Registry ID mismatch (Finding 1b):**

Even for recipes that DO use `ResourceTypeId` in their inputs (e.g., `Furniture_Feran_Torch` uses `ResourceTypeId: "Bone"`), selecting the "Any_Bone" entry in the UI adds `"Any_Bone"` to `activeResourceTypes`. But the recipe input's `getResourceTypeId()` returns `"Bone"` — and `"Bone" ∈ {"Any_Bone"}` evaluates to **false**.

The root cause is that `ResourceTypeRegistry` was populated from icon filenames:
- Engine file: `ResourceTypes/Bone.json` → `ResourceTypeId = "Bone"`, `Icon = "Any_Bone.png"`
- Registry entry: `resourceTypeId = "Any_Bone"` (from icon filename, NOT from actual ID)

The `"Any_*"` prefix on icons appears to be the engine's convention for indicating a generic/shared icon — e.g., `Any_Trunk.png` is shared across `Wood_Hardwood_Trunk`, `Wood_Softwood_Trunk`, `Wood_Redwood_Trunk`, etc. These are icon names, not ResourceTypeId values.

**Impact:** Selecting ANY generic resource type (Any_Bone, Any_Book, Any_Meat, Any_Mushroom, Any_Recipe, Any_Rock, Any_Rubble, Any_Trunk) in the filter grid will dim ALL recipes. The feature is completely non-functional for these entries.

---

### Bug 2: Detail panel always shows inventory affordability

**Root cause:** `updateDetailPanel()` has no awareness of `affordabilityMode`. It unconditionally:

1. Resolves ingredients via `ResourceTypeResolver.resolveInputItemId()` (lines 799-804)
2. Counts each ingredient in the player's inventory via `countItemInInventory()` (line 812)
3. Applies red styling (`COST_QTY_INSUFFICIENT`, `OUTPUT_BG_UNAFFORDABLE`) if the player doesn't have enough (lines 813-822)

```mermaid
graph TB
    subgraph "Recipe Grid (Pipeline)"
        PG1["applyFilter()"]
        PG2["RESOURCE_DRIVEN → resourceTypeChecker"]
        PG3["tagResourceTypeMatch()"]
        PG4["affordable = matchesResourceType()"]
        PG5["Grid: dim if !affordable"]
    end

    subgraph "Detail Panel"
        DP1["updateDetailPanel()"]
        DP2["ALWAYS checks inventory"]
        DP3["countItemInInventory()"]
        DP4["Red border if !sufficient"]
    end

    PG1 --> PG2 --> PG3 --> PG4 --> PG5
    DP1 --> DP2 --> DP3 --> DP4

    PG5 -.->|"MISMATCH"| DP4

    style PG4 fill:#6c6,stroke:#060
    style PG5 fill:#6c6,stroke:#060
    style DP2 fill:#f66,stroke:#900
    style DP3 fill:#f66,stroke:#900
    style DP4 fill:#f66,stroke:#900
```

In `RESOURCE_DRIVEN` mode:
- **Grid says:** "This recipe matches your selected resource type" → highlighted
- **Detail panel says:** "You don't have enough Ingredient_Bone_Fragment" → red border, red quantities

The user sees conflicting signals: the recipe looks "selected" in the grid but "unaffordable" in the detail panel.

**Impact:** Confusing UX in RESOURCE_DRIVEN mode. The detail panel always communicates inventory state even when the user is browsing by resource type, not by what they can craft.

---

### Bug 3: Reusability Analysis

#### Resolution Logic Map

```mermaid
graph TB
    subgraph "Current: Duplicated Resolution Logic"
        A1["recipeMatchesResourceTypes()<br/>StencilSelectionPage:894"]
        A2["Only checks input.getResourceTypeId()"]

        B1["ResourceTypeResolver.resolveInputItemId()<br/>ResourceTypeResolver:66"]
        B2["Handles both ItemId + ResourceTypeId"]

        C1["ResourceTypeResolver.itemsWithResourceType()<br/>ResourceTypeResolver:124"]
        C2["Scans Item.getResourceTypes() array"]

        D1["updateDetailPanel()<br/>StencilSelectionPage:772"]
        D2["Uses resolveInputItemId() for display"]
        D3["Uses countItemInInventory() for styling"]

        E1["tagResourceTypeMatch()<br/>RecipeFilterPipeline:312"]
        E2["Delegates to ResourceTypeChecker"]

        F1["canRemoveMaterials()<br/>Engine ItemContainer"]
        F2["Native ResourceTypeId matching"]
    end

    A1 --> A2
    B1 --> B2
    C1 --> C2
    D1 --> D2
    D1 --> D3
    E1 --> E2
    F1 --> F2

    style A1 fill:#f66,stroke:#900
    style A2 fill:#f66,stroke:#900
    style D3 fill:#f96,stroke:#960
    style B1 fill:#6c6,stroke:#060
    style C1 fill:#6c6,stroke:#060
```

#### Duplication Analysis

| Location | What it does | Input handling | Uses ResourceTypeResolver? |
|----------|-------------|----------------|---------------------------|
| `recipeMatchesResourceTypes()` | Checks if recipe inputs match selected resource types | `ResourceTypeId` only — ignores `ItemId` | ❌ No |
| `ResourceTypeResolver.resolveInputItemId()` | Resolves a `MaterialQuantity` to a concrete item ID | Both `ItemId` and `ResourceTypeId` | ✅ Is the resolver |
| `ResourceTypeResolver.itemsWithResourceType()` | Finds all items declaring a ResourceTypeId | Scans `Item.getResourceTypes()` array | ✅ Is the resolver |
| `updateDetailPanel()` | Resolves inputs for display + checks inventory | Both (via `resolveInputItemId()`) | ✅ For display only |
| `tagResourceTypeMatch()` | Pipeline tagging stage | Delegates to `ResourceTypeChecker` lambda | Indirectly (via lambda) |
| Engine `canRemoveMaterials()` | Native engine affordability check | Both `ItemId` and `ResourceTypeId` internally | N/A (engine-level) |

**Core problem:** `recipeMatchesResourceTypes()` should NOT re-implement resolution logic. It should use `ResourceTypeResolver.itemsWithResourceType()` or a new method on `ResourceTypeResolver` that answers the question: "does this recipe's input resolve to an item that declares resource type X?"

---

## 5. Target Architecture Diagram

```mermaid
graph TB
    subgraph "Target: Unified Resource Type Matching"
        RT["ResourceTypeResolver"]
        M1["resolveInputItemId()"]
        M2["itemsWithResourceType()"]
        M3["recipeMatchesResourceTypes()<br/>(NEW: moved here)"]

        BSP["StencilSelectionPage"]
        RFP["RecipeFilterPipeline"]
        DP["updateDetailPanel()"]
    end

    BSP -->|"calls"| M3
    RFP -->|"calls via checker"| M3
    DP -->|"calls"| M1

    M3 -->|"uses internally"| M2
    M3 -->|"uses internally"| M1

    style RT fill:#6c6,stroke:#060
    style M1 fill:#6c6,stroke:#060
    style M2 fill:#6c6,stroke:#060
    style M3 fill:#6c6,stroke:#060
```

---

## 6. Migration Notes

### Bug 1 Fix — `recipeMatchesResourceTypes()` rewrite

- **Move** the matching logic to `ResourceTypeResolver` as a new static method (e.g., `recipeMatchesAnyResourceType(CraftingRecipe, Set<String>)`)
- **For each recipe input**, check BOTH paths:
  1. If `input.getResourceTypeId()` is non-null → check if `selectedTypes.contains(resTypeId)`
  2. If `input.getItemId()` is non-null → resolve to `Item`, check if any entry in `Item.getResourceTypes()` has an ID in `selectedTypes`
- **Delete** the local `recipeMatchesResourceTypes()` from `StencilSelectionPage`; replace the lambda at line 220 with a call to the new `ResourceTypeResolver` method

### Bug 1 Fix — `ResourceTypeRegistry` ID correction

- **Fix** the 8 `"Any_*"` entries whose `resourceTypeId` is derived from icon filenames instead of actual engine ResourceTypeId values:
  - `"Any_Bone"` → `"Bone"` (entry already exists at index 9 — **delete** the `Any_Bone` entry entirely, or repurpose it as a "group all bone subtypes" meta-filter if that concept is desired)
  - `"Any_Book"` → `"Books"` (entry already exists at index 10)
  - `"Any_Meat"` → determine actual ResourceTypeId from engine assets
  - `"Any_Mushroom"` → determine actual ResourceTypeId
  - `"Any_Recipe"` → determine actual ResourceTypeId
  - `"Any_Rock"` → `"Rock"` (entry already exists at index 31)
  - `"Any_Rubble"` → `"Rubble"` (entry already exists at index 67)
  - `"Any_Trunk"` → `"Wood_Trunk"` (entry already exists at index 77)
- **Consider** building the registry dynamically from `ResourceTypes/*.json` asset files at init time instead of hardcoding

### Bug 2 Fix — `updateDetailPanel()` mode awareness

- **Add** an `affordabilityMode` check to `updateDetailPanel()`
- In `RESOURCE_DRIVEN` mode: skip inventory quantity checks, show neutral styling (no red borders/quantities), or show resource-type-match state instead
- In `ALL` mode: skip affordability styling entirely (no dim/red)
- In `INVENTORY_DRIVEN` mode: keep current behavior (inventory-based red/green)

### Reusability consolidation

- **All** resource-type-to-recipe matching should flow through `ResourceTypeResolver`
- **The pipeline's** `ResourceTypeChecker` functional interface is correct as an abstraction — the lambda just needs to call the correct resolver method
- **`updateDetailPanel()`** already uses `ResourceTypeResolver.resolveInputItemId()` for display — no change needed for the resolution path, only for the affordability styling path

---

## 7. Severity Summary

| Bug | Severity | Blocking? | Effort |
|-----|----------|-----------|--------|
| Bug 1a (ItemId inputs ignored) | 🔴 Blocked | Yes — feature non-functional for majority of recipes | Small — rewrite one method |
| Bug 1b (Registry ID mismatch) | 🔴 Blocked | Yes — 8 of 79 entries permanently broken | Small — fix registry data |
| Bug 2 (Detail panel mismatch) | 🟡 Should Fix | No — cosmetic/UX only | Small — add mode branch |
| Bug 3 (Duplication) | 🟡 Should Fix | No — code health | Medium — extract to resolver |

---

→ @Engineer implement migration from docs/review-resource-type-matching-bugs.md
→ @Architect if the `"Any_*"` entries should become meta-filters that match multiple ResourceTypeIds (e.g., "Any_Bone" matches both "Bone" and all bone subtypes), that requires a new design decision before implementation
