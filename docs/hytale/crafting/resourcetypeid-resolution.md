---
topic: "ResourceTypeId Resolution: Builders Bench vs Furniture Bench"
category: "Crafting"
updated: 2026-04-16
sources: ["decompiled CraftingManager.matches()", "decompiled ItemContainer.getMatchingResourceType()", "asset JSON files (Wood_Hardwood_Fence, Wood_Hardwood_Planks, Wood_Oak_Trunk, Furniture_Kweebec_Bed, Furniture_Tavern_Bed, Bench_Builders, Bench_Furniture)"]
---

# ResourceTypeId Resolution: Builders Bench vs Furniture Bench

## Summary

Both the Builders Bench (`StructuralCrafting`) and the Furniture Bench (`Crafting`) resolve `ResourceTypeId` recipe inputs using **the exact same engine mechanism**: direct string equality against each item's `ResourceTypes` array. There is no bench-specific resolution logic. The difference between benches lies in **UI behavior** (BlockGroup cycling) and **which recipes are assigned** to each bench, not in how `ResourceTypeId` matching works.

---

## 1. Builders Bench (StructuralCrafting) — ResourceTypeId Resolution

### Example: `Wood_Hardwood_Fence`

```json
"Recipe": {
  "Input": [{ "ResourceTypeId": "Wood_Hardwood", "Quantity": 1 }],
  "BenchRequirement": [{ "Id": "Builders", "Type": "StructuralCrafting", "Categories": ["Wall"] }]
}
```

### How the engine resolves `ResourceTypeId: "Wood_Hardwood"`

The engine's `CraftingManager.matches()` checks whether the **player's actual inventory item** has a `ResourceTypes` entry where `resourceType.id` equals `"Wood_Hardwood"` (exact string equality). It does **not** consult BlockGroups for matching.

Items that satisfy `ResourceTypeId: "Wood_Hardwood"`:

| Item | ResourceTypes (includes `"Wood_Hardwood"`) | Natural? |
|------|---------------------------------------------|----------|
| `Wood_Oak_Trunk` | `["Wood_Oak", "Wood_Hardwood", "Wood_Hardwood_Trunk", "Wood_Trunk", "Wood_All", "Fuel", "Charcoal"]` | Yes |
| `Wood_Hardwood_Planks` | `["Wood_Planks", "Fuel", "Charcoal", "Wood_Hardwood"]` | No (crafted) |
| Other hardwood items (Decorative, Ornate, etc.) | Would also include `"Wood_Hardwood"` | No (crafted) |

**Conclusion**: The Builders Bench accepts **any item** whose `ResourceTypes` includes `"Wood_Hardwood"` — both trunks (natural) and planks/derivatives (crafted). The player can use either `Wood_Oak_Trunk` or `Wood_Hardwood_Planks` to craft the fence.

---

## 2. Furniture Bench (Crafting) — ResourceTypeId Resolution

### Example: `Furniture_Kweebec_Bed`

```json
"Recipe": {
  "Input": [
    { "Quantity": 3, "ResourceTypeId": "Wood_All" },
    { "ItemId": "Ingredient_Fibre", "Quantity": 4 }
  ],
  "BenchRequirement": [{ "Type": "Crafting", "Id": "Furniture_Bench", "Categories": ["Furniture_Beds"] }]
}
```

### How the engine resolves `ResourceTypeId: "Wood_All"`

Identical mechanism — `CraftingManager.matches()` checks `item.getResourceTypes()` for exact equality with `"Wood_All"`.

Items that satisfy `ResourceTypeId: "Wood_All"`:

| Item | ResourceTypes (includes `"Wood_All"`) | Natural? |
|------|----------------------------------------|----------|
| `Wood_Oak_Trunk` | `["Wood_Oak", "Wood_Hardwood", "Wood_Hardwood_Trunk", "Wood_Trunk", "Wood_All", "Fuel", "Charcoal"]` | Yes |
| Other trunk variants (Birch, Elm, etc.) | Would also include `"Wood_All"` | Yes |
| Planks variants (if they declare `"Wood_All"`) | Depends on whether they include it | Crafted |

**Key observation**: `Wood_Hardwood_Planks` declares `["Wood_Planks", "Fuel", "Charcoal", "Wood_Hardwood"]` — it does **NOT** declare `"Wood_All"`. So planks may not satisfy `Wood_All` unless they explicitly include it in their `ResourceTypes` array. Each item's eligibility depends entirely on whether it lists `"Wood_All"` in its `ResourceTypes`.

### Contrast: `Furniture_Tavern_Bed`

```json
"Recipe": {
  "Input": [
    { "ItemId": "Wood_Darkwood_Planks", "Quantity": 3 },
    { "ItemId": "Ingredient_Fibre", "Quantity": 4 },
    { "ItemId": "Cloth_Block_Wool_Red", "Quantity": 2 },
    { "ItemId": "Cloth_Block_Wool_White" }
  ],
  "BenchRequirement": [{ "Type": "Crafting", "Id": "Furniture_Bench", "Categories": ["Furniture_Beds"] }]
}
```

The Tavern Bed uses **direct `ItemId`** references — no `ResourceTypeId` flexibility. Only `Wood_Darkwood_Planks` works.

---

## 3. BlockGroups — UI Cycling, NOT Resolution

### What BlockGroups are

`BlockGroup` is a **client-side UI feature** for the Builders Bench. When `AllowBlockGroupCycling: true`, the bench UI lets the player cycle through block variants within a group.

```json
"Bench": {
  "Type": "StructuralCrafting",
  "Id": "Builders",
  "AllowBlockGroupCycling": true,
  "HeaderCategories": ["WoodPlanks", "OrnatePlanks", "DecorativePlanks"]
}
```

### What BlockGroups contain

Each `BlockGroup` (e.g., `FullBlocks_Hardwood`) defines an ordered list of block type IDs that represent the same structural shape in different material variants:

```
FullBlocks_Hardwood → ["Wood_Hardwood_Planks", "Wood_Hardwood_Decorative", "Wood_Hardwood_Ornate"]
FullBlocks_Blackwood → ["Wood_Blackwood_Planks", "Wood_Blackwood_Decorative", "Wood_Blackwood_Ornate"]
```

### What BlockGroups do NOT do

- BlockGroups are **NOT** used by `CraftingManager.matches()` to resolve `ResourceTypeId` inputs
- BlockGroups are **NOT** the mechanism that determines which items satisfy a recipe's `ResourceTypeId`
- BlockGroups have **NO** relationship to the `ResourceTypes` array on items
- BlockGroups are **NOT** consulted during server-side crafting validation

### What BlockGroups DO

1. **UI variant cycling**: In the Builder's Bench, pressing the cycle button changes which visual block variant you'll craft (e.g., switching between Hardwood Planks → Hardwood Decorative → Hardwood Ornate)
2. **Recipe reuse**: All blocks in a BlockGroup typically share the same underlying recipe structure — one recipe input specification produces different visual outputs based on which BlockGroup member is selected
3. **Category organization**: `HeaderCategories` on the bench definition group recipes by material type for display

### Relationship summary

```
ResourceTypeId ("Wood_Hardwood")  ─── resolved by ───→  Item.getResourceTypes()
                                                          (engine matching)

BlockGroup ("FullBlocks_Hardwood") ─── used by ────→  Bench UI cycling
                                                          (client-side display)
```

These are **independent systems**. A BlockGroup might happen to contain items that also share a ResourceType, but that's a content design convention, not an engine constraint.

---

## 4. `_All` ResourceTypes — Literal Declarations, NOT Wildcards

### How `_All` works

`Wood_All` is a **literal resource type ID**. There is no engine-level wildcard or suffix-matching behavior. Items explicitly declare membership:

```json
// Wood_Oak_Trunk.json
"ResourceTypes": [
  { "Id": "Wood_Oak" },
  { "Id": "Wood_Hardwood" },
  { "Id": "Wood_Hardwood_Trunk" },
  { "Id": "Wood_Trunk" },
  { "Id": "Wood_All" },      // ← explicit declaration
  { "Id": "Fuel" },
  { "Id": "Charcoal" }
]
```

The engine performs `"Wood_All".equals(resourceType.id)` — no prefix stripping, no suffix processing, no pattern matching. If an item doesn't declare `{ "Id": "Wood_All" }` in its `ResourceTypes`, it does **not** match a recipe input with `ResourceTypeId: "Wood_All"`.

### Hierarchy convention (not engine-enforced)

The `_All` suffix is a **content design convention** for broad categories:

```
Wood_All         ← broadest: any wood item that declares it
  Wood_Trunk     ← all trunks
  Wood_Planks    ← all planks
  Wood_Hardwood  ← all hardwood (trunks + planks + derivatives)
  Wood_Softwood  ← all softwood variants
```

This hierarchy is enforced by which items declare which ResourceTypes — not by the engine.

---

## 5. The `Set` Field

### What `Set` does

The `Set` field groups items for UI/organizational purposes:

```json
// Wood_Hardwood_Fence.json
"Set": "Wood_Hardwood_Planks"

// Wood_Hardwood_Planks.json
"Set": "Wood_Hardwood_Planks"

// Wood_Oak_Trunk.json
"Set": "Wood_Oak"
```

### Relationship to BlockGroups and ResourceTypeId

| Feature | Purpose | Used for crafting resolution? |
|---------|---------|------------------------------|
| `Set` | UI grouping — items in the same set are visually grouped | **No** |
| `ResourceTypes` | Crafting input matching — determines what satisfies a `ResourceTypeId` | **Yes** |
| `BlockGroup` | Bench UI cycling — cycle through variant blocks | **No** |

The fence's `Set: "Wood_Hardwood_Planks"` means it's displayed alongside hardwood planks in inventory/UI. This has **no effect** on which items can be used to craft it — that's determined entirely by its recipe's `ResourceTypeId: "Wood_Hardwood"` and which items declare that ResourceType.

---

## 6. Specific Answers

### Q: What items does the Builders Bench consider valid for `ResourceTypeId: "Wood_Hardwood"`?

**Any item whose `ResourceTypes` array includes `{ "Id": "Wood_Hardwood" }`.**

Based on observed asset data:
- `Wood_Oak_Trunk` — natural trunk, declares `"Wood_Hardwood"`
- `Wood_Hardwood_Planks` — crafted planks, declares `"Wood_Hardwood"`
- Other hardwood derivatives (Decorative, Ornate) — if they declare `"Wood_Hardwood"`

This is **NOT** limited to `FullBlocks_Hardwood` BlockGroup members. BlockGroups are irrelevant to input matching.

### Q: What items does the Furniture Bench consider valid for `ResourceTypeId: "Wood_All"`?

**Any item whose `ResourceTypes` array includes `{ "Id": "Wood_All" }`.**

Based on observed asset data:
- `Wood_Oak_Trunk` — yes, declares `"Wood_All"`
- `Wood_Hardwood_Planks` — **NO**, its ResourceTypes are `["Wood_Planks", "Fuel", "Charcoal", "Wood_Hardwood"]` — does not include `"Wood_All"`

This means **for the Kweebec Bed specifically**, the `Wood_All` input would accept trunks but **not** the Hardwood Planks we have data for. Whether other planks variants declare `"Wood_All"` would need to be verified per-item.

> **Important caveat**: We only have asset data for a limited set of items. Other wood items may or may not declare `"Wood_All"`. The answer for any specific item depends on its actual `ResourceTypes` array.

---

## 7. Engine Resolution Flow (Definitive)

```
Recipe Input
    │
    ├── Has ItemId? ──→ Match exactly that item
    │
    └── Has ResourceTypeId? ──→ For each item in player's inventory:
                                    item.getResourceTypes()
                                        for each rt in resourceTypes:
                                            if resourceTypeId.equals(rt.id):
                                                ✓ This item satisfies the input
```

This is the same for ALL bench types. `StructuralCrafting`, `Crafting`, `Processing`, and `DiagramCrafting` all use `CraftingManager.matches()` with the same ResourceType equality check.

---

## See Also

- [Block Groups](./block-groups.md)
- [Recipes](./recipes.md)
- [Bench Types](./bench-types.md)
- [Items](../items/items.md)
- [Review: ResourceTypeId Resolution System](../../review-resourcetypeid-resolution.md) — documents the previous incorrect BlockGroup-based approach
- [Fix Plan: Base Block Misclassification](../../Plans/fix-base-block-misclassification.md) — the downstream bug caused by natural-item preference in resolution
