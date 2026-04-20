# Pipeline Flow per Resource Item

Traces each example resource through the Deco-aware two-tier pipeline to verify correct behavior.

## Legend

| Symbol | Meaning |
|--------|---------|
| **NRR** | NaturalResourceRegistry |
| **BRR** | BenchRecipeRegistry |
| **BBC** | BenchBlockClassifier |
| **RTR** | ResourceTypeResolver |

---

## 1. `Rock_Stone` — Natural block, drops different item

| Property | Value |
|----------|-------|
| Category | `Blocks.Rocks` |
| Recipe | None |
| Breaking | `ItemId: Rock_Stone_Cobble`, `GatherType: Rocks` |
| ResourceTypes | `Rock`, `Rock_Stone` |

**Flow:**
- **NRR.init()**: No recipe → natural. Not Deco → `Rock_Stone` added to `naturalBlockTypes`. Breaking drops `Rock_Stone_Cobble` → added to both `coreNaturalItemIds` and `allNaturalItemIds`. Fallback item `Rock_Stone` also added to both.
- **Phase 4b**: Natural, no recipe, not Deco → **processed**. Breaking quantity (default 1) × 12 = **12x `Rock_Stone_Cobble`**.
- **Phase 6**: `Rock_Stone_Cobble` in `allNaturalItemIds` → stack size × 12.

**Result: Correct** — natural block drops 12x cobble.

---

## 2. `Rock_Stone_Cobble` — Natural block, drops self

| Property | Value |
|----------|-------|
| Category | `Blocks.Rocks` |
| Recipe | None |
| Breaking | `GatherType: Rocks` (no explicit ItemId — fallback to self) |
| ResourceTypes | `Rock`, `Rock_Stone` |
| Set | `Rock_Stone` |

**Flow:**
- **NRR.init()**: No recipe → natural. Not Deco → item `Rock_Stone_Cobble` → both core and all sets.
- **Phase 4b**: Natural, no recipe, not Deco → **processed**. Breaking quantity (default 1) × 12 = **12x `Rock_Stone_Cobble`**.
- **Phase 6**: Stack size × 12.

**Result: Correct** — natural block drops 12x of self.

---

## 3. `Wood_Oak_Trunk` — Natural trunk, drops self

| Property | Value |
|----------|-------|
| Category | `Blocks.Wood` |
| Recipe | None |
| Breaking | `ItemId: Wood_Oak_Trunk`, `GatherType: Woods` |
| ResourceTypes | (inherited from parent, but includes `Wood_All`, `Wood_Trunk`, etc.) |

**Flow:**
- **NRR.init()**: No recipe → natural. Not Deco → `Wood_Oak_Trunk` → both core and all sets.
- **Phase 4b**: Natural, no recipe, not Deco → **processed**. Breaking 1 × 12 = **12x `Wood_Oak_Trunk`**.
- **Phase 6**: Stack size × 12.

**Result: Correct** — natural trunk drops 12x of self.

---

## 4. `Wood_Ash_Trunk` — Natural trunk (parent: `Wood_Oak_Trunk`)

| Property | Value |
|----------|-------|
| Category | `Blocks.Wood` |
| Recipe | None |
| Breaking | `ItemId: Wood_Ash_Trunk`, `GatherType: Woods` |
| ResourceTypes | `Wood_Ash`, `Wood_Hardwood`, `Wood_Hardwood_Trunk`, `Wood_Trunk`, `Wood_All`, `Fuel`, `Charcoal` |

**Flow:** Same as Oak Trunk — natural, not Deco, drops 12x self.

**Result: Correct.**

---

## 5. `Wood_Hardwood_Planks` — Base block (Builders bench)

| Property | Value |
|----------|-------|
| Recipe | Input: `1x ResourceTypeId:Wood_Hardwood_Trunk`, Bench: `Builders` |
| Breaking | Inherited from parent (no explicit gathering — likely `GatherType: Woods`) |
| ResourceTypes | `Wood_Planks`, `Fuel`, `Charcoal`, `Wood_Hardwood` |
| Set | `Wood_Hardwood_Planks` (is set root — id matches set) |

**Flow:**
- **NRR.init()**: Has Builders recipe → `Wood_Hardwood_Planks` added to `craftableBlockIds` → NOT natural.
- **BRR.init()**: Recipe input `Wood_Hardwood_Trunk` → RTR resolves to a trunk item (e.g. `Wood_Ash_Trunk`). Is trunk in `coreNaturalItemIds`? **Yes** → `allInputsNatural` = true → **base block recipe**.
- **BBC.classify()**: Same check → **base block**.
- **Phase 1**: Base recipe → **SKIP** (cost stays at 1x `Wood_Hardwood_Trunk`).
- **Phase 4a**: Base block excluded from `getNonBaseBlocksByCategory()` → **SKIP**. Drops self when broken (vanilla behavior).

**Result: Correct** — base block keeps 1x cost, drops self. Player mines trunk at 12x, crafts planks 1:1, places planks, breaks planks → gets planks back.

---

## 6. `Wood_Hardwood_Decorative` — Base block (Builders bench)

| Property | Value |
|----------|-------|
| Recipe | Input: `1x ResourceTypeId:Wood_Hardwood_Trunk`, Bench: `Builders` |
| Set | `Wood_Hardwood_Planks` (derivative — set ≠ id) |
| Parent | `Wood_Hardwood_Planks` |

**Flow:**
- **NRR.init()**: Has Builders recipe → NOT natural.
- **BRR.init()**: Input `Wood_Hardwood_Trunk` resolves to trunk item → in `coreNaturalItemIds` → **base block recipe**.
- **Phase 1**: Base → **SKIP**.
- **Phase 4a**: Base → **SKIP**. Drops self.

Both Decorative and Planks use `Wood_Hardwood_Trunk` as input — both are base blocks with identical classification.

**Result: Correct** — Decorative is also a 1:1 recipe from trunk. Keeps 1x cost, drops self. Player mines trunk (gets 12x), crafts 1 trunk → 1 decorative, breaks decorative → gets decorative back.

---

## 7. `Wood_Hardwood_Fence` — Non-base recipe block (Builders bench, OutputQuantity: 2)

| Property | Value |
|----------|-------|
| Recipe | Input: `1x ResourceTypeId:Wood_Hardwood`, Bench: `Builders`, Output: **2** |
| Breaking | `ItemId: Wood_Hardwood_Fence`, `GatherType: Woods` |
| Set | `Wood_Hardwood_Planks` (derivative) |

**Flow:**
- **NRR.init()**: Has Builders recipe → NOT natural.
- **BRR.init()**: Input `Wood_Hardwood` (ResourceTypeId). RTR resolves for `BUILDERS_ONLY` (preferNatural=false). Pass 1 filters for non-natural items with `Wood_Hardwood` resource type. `Wood_Hardwood_Planks` has `Wood_Hardwood` in ResourceTypes, is non-natural (has recipe), and is set root → **resolves to `Wood_Hardwood_Planks`**. Is Planks in `coreNaturalItemIds`? **No** (it has a recipe → not natural). → `allInputsNatural` = false → **non-base recipe**.
- **Phase 1**: Non-base → input scaled to **12x** `Wood_Hardwood`. Recipe becomes `12x Wood_Hardwood → 2x Fence`.
- **BBC.classify()**: Same check → non-base → included in `getNonBaseBlocksByCategory(BUILDERS_ONLY)`.
- **Phase 4a**: RTR resolves `Wood_Hardwood` for `BUILDERS_ONLY` → `Wood_Hardwood_Planks`. Input qty = 12, output qty = 2 → drop qty = 12/2 = **6**. Breaking config set to drop **6x `Wood_Hardwood_Planks`**.

**Result: Correct** — Fence costs 12x planks-equivalent to craft (making 2), and drops 6x planks when broken.

---

## 8. `Plant_Leaves_Oak` — Natural block with drop lists

| Property | Value |
|----------|-------|
| Category | `Blocks.Plants` |
| Recipe | None |
| Breaking | None (soft/physics via DropLists `Tree_Leaves`, `Tree_Leaves_Physics`) |
| UseDefaultDropWhenPlaced | true |

**Flow:**
- **NRR.init()**: No recipe → natural. Category is `Blocks.Plants` (NOT `Blocks.Deco`) → not Deco → drops go to both core and all sets. Drops resolved from `Tree_Leaves` drop list.
- **Phase 4b**: Natural, no recipe, not Deco → **processed**. No breaking config → soft/physics drop lists scaled for ingredient items.

**Result: Correct** — natural plant block, drops scaled at 12x.

---

## 9. `Deco_Moving_Box` — Deco block, no recipe (THE FIX TARGET)

| Property | Value |
|----------|-------|
| Category | **`Blocks.Deco`** |
| Recipe | None |
| Breaking | `GatherType: SoftWoods` (no ItemId — drops self) |
| ResourceTypes | `Wood_Planks`, `Fuel`, `Charcoal`, `Wood_All` |

**Flow:**
- **NRR.init()**: No recipe → natural. **Is Deco** → `Deco_Moving_Box` item added to `allNaturalItemIds` **only** (NOT `coreNaturalItemIds`).
- **Phase 4b**: Natural, no recipe → enters loop. `bt.getItem()` → `Deco_Moving_Box` → `ResourceTypeResolver.isDeco()` checks categories → `Blocks.Deco` → **true** → `naturalSkipped++; continue` → **SKIP**.
- **RTR.itemsWithResourceType()**: When resolving `Wood_All` or `Wood_Planks`, the `isDeco()` filter excludes `Deco_Moving_Box` → it never resolves as a recipe ingredient.
- **Phase 6**: `Deco_Moving_Box` is in `allNaturalItemIds` → stack size × 12 = 1200.

**Result: Correct** — Deco block stays at vanilla 1x drops. Not used as a recipe resolution for generic resource types. Stack size still scaled (harmless, allows stacking more of them).

---

## 10. `Furniture_Kweebec_Bed` — Furniture bench, multi-ingredient with ResourceTypeId

| Property | Value |
|----------|-------|
| Category | `Furniture.Beds` |
| Recipe | Input: `3x ResourceTypeId:Wood_All` + `4x ItemId:Ingredient_Fibre`, Bench: `Furniture_Bench` |
| Breaking | `GatherType: Woods` |

**Flow:**
- **NRR.init()**: Has Furniture_Bench recipe → `Furniture_Kweebec_Bed` added to `craftableBlockIds` → NOT natural.
- **BRR.init()**: Input 1: `Wood_All` (ResourceTypeId). RTR's `isResourceTypeExclusivelyNatural("Wood_All", coreNaturalItems)` → `itemsWithResourceType("Wood_All")` filters out Deco items → checks if ALL remaining are in `coreNaturalItemIds`. Trunks are core-natural, but Planks (non-natural, has recipe) also have `Wood_All` → **not exclusively natural**. Input 2: `Ingredient_Fibre` (direct ItemId) → is `Ingredient_Fibre` in `coreNaturalItemIds`? **No** (only in `allNaturalItemIds` via Deco plants). → `allInputsNatural` = false → **non-base recipe**.
- **Phase 1**: Non-base → inputs scaled to `36x Wood_All` + `48x Ingredient_Fibre`.
- **BBC.classify()**: Same → non-base → in `getNonBaseBlocksByCategory(FURNITURE_ONLY)`.
- **Phase 4a**: `FurnitureProcessor` processes. RTR resolves `Wood_All` for `FURNITURE_ONLY` (preferNatural=true) → resolves to a natural trunk item (e.g. `Wood_Oak_Trunk`). Drop quantities: `36/1 = 36` trunk + `48/1 = 48` Ingredient_Fibre. Uses multi-drop list.

**Result: Correct** — Kweebec bed costs 36 wood + 48 fibre to craft, drops those ingredients when broken.

---

## 11. `Furniture_Tavern_Bed` — Furniture bench, multi-ingredient with all direct ItemIds

| Property | Value |
|----------|-------|
| Recipe | Input: `3x Wood_Darkwood_Planks` + `4x Ingredient_Fibre` + `2x Cloth_Block_Wool_Red` + `1x Cloth_Block_Wool_White`, Bench: `Furniture_Bench` |

**Flow:**
- **NRR.init()**: Has recipe → NOT natural.
- **BRR.init()**: All inputs are direct ItemIds. `Wood_Darkwood_Planks` → has recipe → not in `coreNaturalItemIds`. `Ingredient_Fibre` → not in `coreNaturalItemIds`. `Cloth_Block_Wool_Red`, `Cloth_Block_Wool_White` → likely not natural. → **non-base recipe**.
- **Phase 1**: Inputs scaled to `36x Darkwood_Planks` + `48x Ingredient_Fibre` + `24x Wool_Red` + `12x Wool_White`.
- **Phase 4a**: `FurnitureProcessor` resolves all direct ItemIds. 4-ingredient drop list created.

**Result: Correct** — expensive furniture drops all scaled ingredients.

---

## 12. `Bench_Builders` — Bench itself (Fieldcraft/Workbench recipe)

| Property | Value |
|----------|-------|
| Category | `Furniture.Benches` |
| Recipe | Input: `6x Wood_Trunk` + `3x Rock`, Bench: `Fieldcraft` / `Workbench` |

**Flow:**
- **NRR.init()**: `isCraftingBench()` checks recipe bench IDs (`Fieldcraft`, `Workbench`) against `CRAFTING_BENCH_IDS` = `{Builders, Furniture_Bench, Workbench, Fieldcraft}`. **Matches** → `Bench_Builders` added to `craftableBlockIds` → NOT natural.
- **BRR.init()**: Bench IDs are `Fieldcraft`/`Workbench` — NOT `Builders` or `Furniture_Bench` → **not registered** in any BenchRecipeRegistry. Pipeline ignores it.
- **Phase 4a**: No category → not processed.
- **Phase 4b**: Not natural → not processed.

**Result: Correct** — the bench itself is excluded from the economy pipeline. It's crafted at the Workbench/Fieldcraft, which we don't manage. Vanilla behavior.

---

## 13. `Bench_Furniture` — Same pattern as Builders bench

Recipe bench is `Workbench` → same flow as above. **Not processed.**

**Result: Correct.**

---

## Summary Table

| Item | Type | Deco? | Recipe? | Pipeline Path | Cost | Drop | Stack |
|------|------|-------|---------|---------------|------|------|-------|
| Rock_Stone | Natural | No | No | Phase 4b | — | 12x Rock_Stone_Cobble | 12x |
| Rock_Stone_Cobble | Natural | No | No | Phase 4b | — | 12x self | 12x |
| Wood_Oak_Trunk | Natural | No | No | Phase 4b | — | 12x self | 12x |
| Wood_Ash_Trunk | Natural | No | No | Phase 4b | — | 12x self | 12x |
| Plant_Leaves_Oak | Natural | No | No | Phase 4b | — | Drop list scaled | 12x |
| Wood_Hardwood_Planks | Base block | No | Builders | Skip Phase 1+4a | 1x trunk | self | — |
| Wood_Hardwood_Decorative | Base block | No | Builders | Skip Phase 1+4a | 1x trunk | self | — |
| Wood_Hardwood_Fence | Recipe block | No | Builders | Phase 1 + 4a | 12x hardwood | 6x planks | — |
| Deco_Moving_Box | **Deco natural** | **Yes** | No | **Phase 4b SKIP** | — | **1x self (vanilla)** | 12x |
| Furniture_Kweebec_Bed | Recipe block | No | Furniture | Phase 1 + 4a | 36 wood + 48 fibre | Multi-drop list | — |
| Furniture_Tavern_Bed | Recipe block | No | Furniture | Phase 1 + 4a | 36+48+24+12 | Multi-drop list | — |
| Bench_Builders | Craftable | No | Workbench | Not managed | vanilla | vanilla | — |
| Bench_Furniture | Craftable | No | Workbench | Not managed | vanilla | vanilla | — |
