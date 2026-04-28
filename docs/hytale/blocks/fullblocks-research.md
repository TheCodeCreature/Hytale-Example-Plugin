---
topic: "FullBlocks BlockGroups & Base Transition Identification"
category: "Blocks"
updated: 2026-04-27
sources: ["decompiled BlockGroup.java (server.core.asset.type.item.config)", "decompiled AssetRegistryLoader.java (line 979-988)", "docs/Resources/resourcetypes/FullBlocks_Hardwood.json", "item asset JSONs (Rock_Stone, Rock_Stone_Cobble, Wood_Hardwood_Planks, Wood_Hardwood_Decorative, Wood_Oak_Trunk, Wood_Ash_Trunk)"]
---

# FullBlocks BlockGroups & Base Transition Identification

## 1. What Are FullBlocks Files?

### Files Found

Only **one** FullBlocks file exists in the workspace:

| File | Location | Contents |
|------|----------|----------|
| [FullBlocks_Hardwood.json](../../Resources/resourcetypes/FullBlocks_Hardwood.json) | `docs/Resources/resourcetypes/` | `{"Blocks": ["Wood_Hardwood_Planks", "Wood_Hardwood_Decorative", "Wood_Hardwood_Ornate"]}` |

No other `FullBlocks_*.json` files were found anywhere in the workspace. The test data in [TestDataSet.java](../../../src/test/java/com/UnobstructedThirdPerson/resourcecollection/TestDataSet.java) creates two synthetic BlockGroups (`FullBlocks_Blackwood`, `FullBlocks_Hardwood`), but these are fabricated test fixtures.

### FullBlocks Are BlockGroup Assets — NOT ResourceType Assets

Despite being filed under `docs/Resources/resourcetypes/`, this is a **BlockGroup** asset. The file format (`{"Blocks": [...]}`) matches the `BlockGroup.CODEC` which defines:

```java
// BlockGroup.java
.addField(new KeyedCodec<>("Blocks", Codec.STRING_ARRAY),
    (blockSet, strings) -> blockSet.blocks = strings,
    blockSet -> blockSet.blocks)
```

### Engine Registration

From [AssetRegistryLoader.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/AssetRegistryLoader.java) (line 979–988):

```java
AssetRegistry.register(
    HytaleAssetStore.builder(BlockGroup.class, new DefaultAssetMap())
        .setPath("Item/Groups")      // ← disk path: Server/Item/Groups/<Name>.json
        .setCodec(BlockGroup.CODEC)
        .setKeyFunction(BlockGroup::getId)
        .loadsAfter(BlockType.class, Item.class)
        .setPacketGenerator(new BlockGroupPacketGenerator())
        .build()
);
```

**Key facts:**
- **Asset path**: `Item/Groups` (NOT `Item/ResourceTypes`)
- **Full disk path**: `Server/Item/Groups/FullBlocks_Hardwood.json`
- **Load order**: After `BlockType` and `Item` (so all referenced blocks/items exist)
- **Client sync**: Via `UpdateBlockGroups` packet (packet ID 78)
- **Asset map**: `DefaultAssetMap<String, BlockGroup>` — string-keyed by filename

### Purpose: UI Variant Cycling

BlockGroups enable the "cycle block variant" feature in the Builders Bench UI. When `AllowBlockGroupCycling: true` on a `StructuralCraftingBench`, players can cycle through visual variants (Planks → Decorative → Ornate) using the `CycleBlockGroup` interaction. All variants in a FullBlocks group share the same recipe structure.

---

## 2. Can FullBlocks Identify "1:1 Base Transitions"?

### The Hypothesis

> "FullBlocks items (planks, cobble) are the direct-processing results of natural resources — simple 1:1 transformations."

### Analysis: FullBlocks ≠ Base Transitions

**FullBlocks groups contain MULTIPLE items that are visual variants of the same material, NOT a mapping of natural→processed transitions.**

| FullBlocks Group | Members | Recipe Input | Transition? |
|---|---|---|---|
| `FullBlocks_Hardwood` | `Wood_Hardwood_Planks` | 1× `ResourceTypeId: Wood_Hardwood_Trunk` | Yes: Trunk → Planks |
| `FullBlocks_Hardwood` | `Wood_Hardwood_Decorative` | 1× `ResourceTypeId: Wood_Hardwood_Trunk` | Yes: Trunk → Decorative |
| `FullBlocks_Hardwood` | `Wood_Hardwood_Ornate` | 1× `ResourceTypeId: Wood_Hardwood_Trunk` | Yes: Trunk → Ornate |

**Findings:**
1. **ALL items in `FullBlocks_Hardwood` ARE direct-processing results of natural resources** — each takes 1 trunk and produces 1 variant. So the hypothesis holds for this group.
2. **But FullBlocks groups contain MULTIPLE output variants**, not just "the" base processed form. Planks, Decorative, and Ornate are all "base transitions" from the same trunk.
3. **Rock→Cobble is NOT in any FullBlocks group** that we can observe. `Rock_Stone_Cobble` has no recipe at all — it's a **natural drop** from `Rock_Stone` (via `BlockGathering.Breaking.ItemId`), not a crafting product. There is no `FullBlocks_Stone` or `FullBlocks_Rock` file.

### Critical Distinction: Rock→Cobble Is NOT a Recipe

```json
// Rock_Stone.json - Breaking config
"Gathering": {
    "Breaking": {
        "GatherType": "Rocks",
        "ItemId": "Rock_Stone_Cobble"  // ← GATHERING DROP, not a recipe
    }
}
```

`Rock_Stone_Cobble` has **no `Recipe` field** in its item JSON. It is a natural block that drops itself when broken. The "Rock → Cobble" transition is a **block gathering mechanic** (break Rock_Stone, receive Rock_Stone_Cobble), NOT a crafting recipe.

### Conclusion

**FullBlocks groups DO identify items that are 1:1 crafting transformations of natural resources**, but:
- They are **visual variant groups**, not 1:1 transition mappings (one group = multiple outputs from same input)
- They only cover **wood-type materials** (planks/decorative/ornate patterns). Rock→Cobble is a gathering drop, not a recipe output.
- We only have data for one FullBlocks group. The full game likely has `FullBlocks_Blackwood`, `FullBlocks_Softwood`, etc. — but we cannot confirm `FullBlocks_Stone`.

---

## 3. How to Identify FullBlocks Items at Runtime

### Option A: Read BlockGroup Assets via AssetRegistry

**Yes — BlockGroup is a registered asset type with a public API.**

```java
// Access the BlockGroup asset store
AssetStore<String, BlockGroup, DefaultAssetMap<String, BlockGroup>> store =
    AssetRegistry.getAssetStore(BlockGroup.class);

// Get a specific group
DefaultAssetMap<String, BlockGroup> assetMap = store.getAssetMap();
BlockGroup hardwood = assetMap.getAsset("FullBlocks_Hardwood");

// Iterate all groups
for (BlockGroup group : assetMap.getAssetMap().values()) {
    String groupId = group.getId();
    for (int i = 0; i < group.size(); i++) {
        String blockId = group.get(i);
        // blockId is the item/blocktype ID (e.g. "Wood_Hardwood_Planks")
    }
}
```

From [BlockGroup.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/BlockGroup.java):

```java
// Find which BlockGroup an item belongs to
@Nullable
public static BlockGroup findItemGroup(@Nonnull Item item) {
    String blockId = item.getBlockId();
    if (blockId == null) return null;
    for (BlockGroup group : AssetRegistry.getAssetStore(BlockGroup.class)
            .getAssetMap().getAssetMap().values()) {
        if (ArrayUtil.contains(group.blocks, blockId)) {
            return group;
        }
    }
    return null;
}
```

**To filter for FullBlocks groups specifically**, check if the group ID starts with `"FullBlocks_"`:

```java
boolean isFullBlocksItem = false;
BlockGroup group = BlockGroup.findItemGroup(item);
if (group != null && group.getId().startsWith("FullBlocks_")) {
    isFullBlocksItem = true;
}
```

### Option B: Check Item Properties

Items in FullBlocks groups share these observable properties:

| Property | Planks | Decorative | Ornate | Natural Trunk |
|----------|--------|------------|--------|---------------|
| `Set` | `"Wood_Hardwood_Planks"` | `"Wood_Hardwood_Planks"` | (likely same) | `"Wood_Oak"` |
| `Categories` | (inherited) | (inherited) | (inherited) | `["Blocks.Wood"]` |
| Has Recipe | Yes | Yes | Yes | No |
| Recipe bench | `StructuralCrafting` / Builders | Same | Same | N/A |
| `ResourceTypes` | `Wood_Planks, Fuel, Charcoal, Wood_Hardwood` | (inherited from Planks parent) | (inherited) | `Wood_Oak, Wood_Hardwood, ...` |

**The `Set` field clusters FullBlocks members together.** `Wood_Hardwood_Decorative` has `Set: "Wood_Hardwood_Planks"` (pointing to Planks as the set root). This means all visual variants in a FullBlocks group share the same `Set` value.

### Option C: Use the `Set` Field as a Proxy

The `Set` field groups items for UI/organizational purposes. Observed pattern:
- `Rock_Stone` → `Set: "Rock_Stone"`
- `Rock_Stone_Cobble` → `Set: "Rock_Stone"` (same set as Rock_Stone — they're in the same material family)
- `Wood_Hardwood_Planks` → `Set: "Wood_Hardwood_Planks"` (set root)
- `Wood_Hardwood_Decorative` → `Set: "Wood_Hardwood_Planks"` (points to Planks)
- `Wood_Oak_Trunk` → `Set: "Wood_Oak"`

**`Set` groups items by material family, not by "base transition" status.** `Rock_Stone` and `Rock_Stone_Cobble` share the SAME set despite being natural/processed respectively. This makes `Set` unreliable for distinguishing base transitions from natural blocks within the same family.

---

## 4. Item Categories Approach

### Do FullBlocks Items Share a Category?

| Item | Categories | Natural? |
|------|-----------|----------|
| `Rock_Stone` | `["Blocks.Rocks"]` | Yes |
| `Rock_Stone_Cobble` | `["Blocks.Rocks"]` | Yes (natural drop, no recipe) |
| `Wood_Oak_Trunk` | `["Blocks.Wood"]` | Yes |
| `Wood_Ash_Trunk` | `["Blocks.Wood"]` | Yes |
| `Wood_Hardwood_Planks` | (inherited from parent `Wood_Softwood_Planks`) | No (has recipe) |
| `Wood_Hardwood_Decorative` | (inherited from parent `Wood_Hardwood_Planks`) | No (has recipe) |

**Categories group by material type (`Blocks.Rocks`, `Blocks.Wood`), not by natural vs processed.** Both `Rock_Stone` (natural) and `Rock_Stone_Cobble` (natural drop) share `Blocks.Rocks`. Categories cannot distinguish base transitions.

---

## 5. The `Item.Set` Field and FullBlocks

### Observed Set Assignments

| Item | Set | Is Set Root? | FullBlocks Member? |
|------|-----|-------------|-------------------|
| `Rock_Stone` | `"Rock_Stone"` | Yes (id = set) | Unknown (no FullBlocks_Stone found) |
| `Rock_Stone_Cobble` | `"Rock_Stone"` | No (id ≠ set) | Unknown |
| `Wood_Oak_Trunk` | `"Wood_Oak"` | Yes (id = set) | No |
| `Wood_Ash_Trunk` | `"Wood_Ash"` | Yes (id = set) | No |
| `Wood_Hardwood_Planks` | `"Wood_Hardwood_Planks"` | Yes (id = set) | Yes |
| `Wood_Hardwood_Decorative` | `"Wood_Hardwood_Planks"` | No (id ≠ set) | Yes |

### Key Observation

`Rock_Stone_Cobble` has `Set: "Rock_Stone"` — it points to `Rock_Stone` as its set root. This confirms they're in the same material family. But this does NOT distinguish "cobble is a processed form of rock" from "cobble and rock are just variants."

The `Set` field's relationship to FullBlocks:
- **FullBlocks members share a Set value** — `Wood_Hardwood_Decorative` → `Set: "Wood_Hardwood_Planks"`
- **The set root of a FullBlocks group is the first member** (Planks), which is also the "primary" crafted variant
- **But non-FullBlocks items can also share sets** — `Rock_Stone_Cobble` shares `Set: "Rock_Stone"` without being in a FullBlocks group

---

## 6. How to Actually Identify "Base Transitions" Programmatically

FullBlocks groups alone are insufficient. Here are the reliable approaches:

### Approach 1: Recipe Structure Analysis (Recommended)

A "base transition" recipe has ALL of:
1. Exactly **one input** with a `ResourceTypeId` (not `ItemId`)
2. The `ResourceTypeId` matches items that are **natural** (no recipe)
3. The recipe belongs to the **Builders bench** (`StructuralCrafting`)
4. `OutputQuantity` is **1** (1:1 ratio)

```
Wood_Hardwood_Planks.Recipe:
  Input: [{ResourceTypeId: "Wood_Hardwood_Trunk", Quantity: 1}]
  BenchRequirement: [{Id: "Builders", Type: "StructuralCrafting"}]
  OutputQuantity: 1
→ Matches all criteria → IS a base transition
```

### Approach 2: Gathering Drop → No Recipe (for Rock→Cobble pattern)

`Rock_Stone_Cobble` is NOT a recipe output — it's a **gathering drop**. To identify this pattern:
1. Find blocks where `BlockGathering.Breaking.ItemId` ≠ the block's own item ID
2. The drop item has **no recipe** anywhere
3. Both the source block and the drop item share the same `Set`

```
Rock_Stone.Gathering.Breaking.ItemId = "Rock_Stone_Cobble"
Rock_Stone_Cobble has no Recipe
Both have Set: "Rock_Stone"
→ This is a gathering-based base transition, NOT a crafting transition
```

### Approach 3: FullBlocks + Recipe (for Wood patterns)

If a FullBlocks group exists for a material:
1. All members have 1:1 recipes from the same natural resource type
2. The first member (index 0) is conventionally the "plain" variant (Planks)
3. All members are visual variants of the same base transition

---

## 7. Summary Table

| Question | Answer |
|----------|--------|
| Are FullBlocks files the same as ResourceType files? | **No.** BlockGroup assets at `Server/Item/Groups/`, not ResourceTypes. |
| Can FullBlocks identify 1:1 base transitions? | **Partially.** They identify visual variant groups whose members ARE base transitions, but only for wood-type materials. Rock→Cobble is not a recipe. |
| Can we read BlockGroups at runtime? | **Yes.** `AssetRegistry.getAssetStore(BlockGroup.class)` + `BlockGroup.findItemGroup(item)`. |
| Do FullBlocks items share a common property? | **Yes — `Set` field.** All members point to the same set root (the Planks item). But `Set` also groups non-FullBlocks items. |
| Can Categories identify base transitions? | **No.** Categories group by material type, not by natural vs processed. |
| What is the `Set` field relationship? | Groups items in the same material family. FullBlocks members share a set. `Rock_Stone_Cobble` shares set `"Rock_Stone"` with `Rock_Stone`. |
| Best approach for identifying base transitions? | **Recipe structure analysis**: single ResourceTypeId input, all-natural, Builders bench, output qty 1. |

## See Also

- [ResourceTypeId Resolution](../crafting/resourcetypeid-resolution.md) — How crafting matches ResourceTypeIds
- [Resource Types](../items/resource-types.md) — ResourceType asset format
- [Pipeline Flow per Resource](../../Plans/pipeline-flow-per-resource.md) — Traces each resource through the economy pipeline
