---
topic: "Item State System — Deep Research"
category: "Items / States"
updated: 2026-04-26
sources:
  - "run/mods/CodeCreature.Development/Server/Item/Items/Container/Container_Bucket.json"
  - ".tmp_hytale_src/.../Item.java"
  - ".tmp_hytale_src/.../ItemStack.java"
  - ".tmp_hytale_src/.../ContainedAssetCodec.java"
  - ".tmp_hytale_src/.../AssetExtraInfo.java"
  - ".tmp_hytale_src/.../RefillContainerInteraction.java"
  - ".tmp_hytale_src/.../ExtraInfo.java"
  - "docs/Resources/items/Blocks/Wood/Wood_Hardwood_Fence.json"
  - "docs/Resources/items/Furniture/Benches/Bench_Builders.json"
---

# Item State System — Deep Research

## Summary

Hytale's Item State system allows a **single item JSON file** to define multiple **state variants** that each become **separate registered Item assets** in the asset map. Each state variant inherits from the parent item but can override any field (Icon, BlockType, Interactions, etc.). State switching at runtime is done by **swapping the itemId** to the variant's registered ID.

This document answers 10 specific questions about whether the state system can replace the 9 separate Green placeholder items with a single stateful item.

---

## Q1: Full Anatomy of the Bucket's State System

**Source**: [Container_Bucket.json](../../../run/mods/CodeCreature.Development/Server/Item/Items/Container/Container_Bucket.json)

### Complete JSON Structure

```json
{
  "Icon": "Icons/ItemsGenerated/Container_Bucket.png",
  "MaxStack": 1,
  "Interactions": {
    "Secondary": { /* RefillContainer chain — try water, then milk */ }
  },
  "State": {
    "Filled_Water": {
      "Variant": true,
      "Icon": "Icons/ItemsGenerated/Container_Bucket_Water.png",
      "MaxDurability": 1,
      "Consumable": true,
      "Interactions": {
        "Secondary": {
          "Interactions": [{
            "Type": "PlaceFluid",
            "RemoveItemInHand": false,
            "FluidToPlace": "Water_Source",
            "Next": {
              "Type": "ModifyInventory",
              "AdjustHeldItemDurability": -1,
              "BrokenItem": "Container_Bucket"
            }
          }]
        }
      },
      "BlockType": {
        "Material": "Empty",
        "DrawType": "Model",
        "CustomModel": "Blocks/Decorative_Sets/Village/Bucket_Full.blockymodel",
        "CustomModelTexture": [{ "Texture": "...Bucket_Texture_Water.png" }],
        "Gathering": { "Soft": { "IsWeaponBreakable": true } },
        ...
      }
    },
    "Filled_Milk": {
      "Variant": true,
      "Icon": "Icons/ItemsGenerated/Container_Bucket_Milk.png",
      "ResourceTypes": [{ "Id": "Milk_Bucket", "Quantity": 1 }],
      "Interactions": { "Secondary": "Root_Secondary_Consume_Drink" },
      "InteractionVars": { /* drink effects, broken item → Container_Bucket */ },
      "BlockType": { /* same model, milk texture */ },
      ...
    },
    "Filled_Mosshorn_Milk": {
      "Variant": true,
      "Icon": "Icons/ItemsGenerated/Container_Bucket_Milk_Mosshorn.png",
      ...
    }
  },
  "BlockType": { /* empty bucket model */ }
}
```

The bucket defines **3 state variants** (Filled_Water, Filled_Milk, Filled_Mosshorn_Milk), each with:
- Its own **Icon** (overrides parent)
- Its own **BlockType** (separate model/texture inline definitions)
- Its own **Interactions** (completely replaces parent interactions)
- Its own **MaxDurability**, **Consumable** flags
- The **`Variant: true`** flag

### What `Variant: true` Does

From [Item.java line 357-360](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L357-L360):
```java
new KeyedCodec<>("Variant", Codec.BOOLEAN)
// Documentation:
"Whether this item is a variant of another. Typically this is only the case for connected 
blocks. If this item is marked as a variant, then we filter it out of the item library menu 
by default, unless the player chooses to display variants."
```

**`Variant: true` only affects UI filtering** — it hides the item from the creative library/item browser. It does NOT affect gameplay, state transitions, or registration. It's cosmetic only.

### How States Reference the Parent vs Override

Fields use two codec patterns (from [Item.java](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java)):

| Pattern | Behavior | Example Fields |
|---------|----------|---------------|
| `appendInherited()` | If not specified in state JSON → inherits parent value | Icon, MaxStack, Interactions, BlockType, MaxDurability, Consumable, Variant, FuelQuality, PlayerAnimationsId, ItemLevel, Categories |
| `append()` | If not specified in state JSON → uses class default (NOT parent) | **Quality** |

**This means states inherit almost everything from the parent** except Quality, which resets to null.

---

## Q2: Item.getItemForState() and Item.getStateForItem()

### Implementation

From [Item.java lines 789-811](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L789-L811):

```java
// Returns the item ID string for a given state name
@Nullable
public String getItemIdForState(String state) {
    return this.stateToBlock != null ? this.stateToBlock.get(state) : null;
}

// Returns the full Item asset for a given state name
@Nullable
public Item getItemForState(String state) {
    String id = this.getItemIdForState(state);
    return id == null ? null : getAssetMap().getAsset(id);
}

// Returns true if THIS item is a state variant of some parent
public boolean isState() {
    return this.getStateForItem(this.id) != null;
}

// Reverse lookup: item → state name  
@Nullable
public String getStateForItem(@Nonnull Item item) {
    return this.getStateForItem(item.getId());
}

@Nullable
public String getStateForItem(String key) {
    return this.blockToState != null ? this.blockToState.get(key) : null;
}
```

### How State Variants Are Registered

State variants are registered as **separate Item entries** in the global asset map during JSON loading. The registration flow:

1. The `State` codec is a `MapCodec` wrapping a `ContainedAssetCodec<Item>` with **`INJECT_PARENT`** mode
2. For each entry in the `State` map (e.g., `"Filled_Water": {...}`):
   - `ContainedAssetCodec` generates a unique ID via `AssetExtraInfo.generateKey()`
   - The generated ID format: `*{parentId}_{keyStack}` (see below)
   - The child item JSON is parsed using the parent's codec, inheriting parent values
   - A new `RawAsset` is added to the asset store's contained assets
   - During asset store finalization, this becomes a fully registered Item in the asset map
3. The parent's `stateToBlock` map is populated: `{"Filled_Water" → generated_id, ...}`
4. In `postLoad()`, `blockToState` is built as the reverse map

### Generated ID Format

From [AssetExtraInfo.java line 41-42](../../../.tmp_hytale_src/com/hypixel/hytale/assetstore/AssetExtraInfo.java#L41-L42):
```java
public String generateKey() {
    return "*" + this.getKey() + "_" + this.peekKey('_');
}
```

From [ExtraInfo.java lines 217+](../../../.tmp_hytale_src/com/hypixel/hytale/codec/ExtraInfo.java#L217):
`peekKey('_')` joins the codec key stack with `_` separator.

**Evidence from fence JSON** ([Wood_Hardwood_Fence.json](../../../docs/Resources/items/Blocks/Wood/Wood_Hardwood_Fence.json)):
```json
"Corner": "*Wood_Hardwood_Fence_State_Definitions_Corner"
```

The key stack when processing a State map entry `"Filled_Water"` inside `"State"` is `["State", "Filled_Water"]`, giving `peekKey('_')` = `"State_Filled_Water"`.

**Bucket state variant IDs:**
- `*Container_Bucket_State_Filled_Water`
- `*Container_Bucket_State_Filled_Milk`
- `*Container_Bucket_State_Filled_Mosshorn_Milk`

**Yes, you can look up a state variant by its generated ID:**
```java
Item filledWater = Item.getAssetMap().getAsset("*Container_Bucket_State_Filled_Water");
```

Or via the parent:
```java
Item bucket = Item.getAssetMap().getAsset("Container_Bucket");
Item filledWater = bucket.getItemForState("Filled_Water");
```

### State Inheritance of `stateToBlock`

From [Item.java line 1130](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L1130):
```java
(item, parent) -> item.stateToBlock = parent.stateToBlock
```

**Children inherit the parent's `stateToBlock` map**. This means a child state variant can also resolve sibling states (e.g., `filledWater.getItemForState("Filled_Milk")` works).

---

## Q3: Can States Be Created/Registered at Runtime?

### Short Answer: **No native API exists. Possible via reflection but extremely fragile.**

### Evidence

1. **No `addState`/`registerState` methods** exist on `Item`. Searched `.tmp_hytale_src` — no results for Item-level state mutation.

2. The `stateToBlock` map is `protected` and populated exclusively during JSON codec parsing:
   ```java
   // Item.java line 1128
   (item, m) -> item.stateToBlock = m,
   ```

3. The state variant Items are registered in the asset map during `AssetStore` finalization (the `addContainedAsset` → `RawAsset` → asset store load pipeline). There is no runtime `addAsset` API.

4. **`blockToState` is made unmodifiable** in `postLoad()`:
   ```java
   this.blockToState = Collections.unmodifiableMap(map);  // line 1106
   ```

### Could We Pre-Define States and Activate Dynamically?

**Yes — this is viable.** You could:
1. Define all needed states in the JSON at build time
2. At runtime, use `ItemStack.withState(stateName)` to switch between them
3. Use `UpdateItems` packets to dynamically change a state variant's icon per-player

**But**: With 200+ recipes, defining 200+ states in a single JSON would be impractical and wasteful (200+ registered Items + 200+ registered BlockTypes on every client).

### Could We Add States via Reflection?

Theoretically:
1. Create a new Item via the codec programmatically
2. Register it in `Item.getAssetMap()` via reflection
3. Update the parent's `stateToBlock` map
4. Send `UpdateItems` to clients

This is extremely fragile, untested, and likely to break across game updates.

---

## Q4: Can a State Variant Reference Another Item's Icon/BlockType?

### Icon: Yes (by path reuse)

Each state variant defines its own `Icon` string:
```json
"Icon": "Icons/ItemsGenerated/Container_Bucket_Water.png"
```

You can set this to **any valid icon path**, including one used by another item:
```json
"Icon": "Icons/ItemsGenerated/Block_Cobble_Wall.png"  // reuse another item's icon
```

The Icon is just a texture path — there's no ownership restriction.

### BlockType: Inline definition only (no references)

Each state variant's `BlockType` is a **ContainedAsset** (mode `INHERIT_ID_AND_PARENT`) that creates a separate BlockType entry. From [Item.java line 385](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L385):
```java
new KeyedCodec<>("BlockType", new ContainedAssetCodec<>(BlockType.class, BlockType.CODEC, 
    ContainedAssetCodec.Mode.INHERIT_ID_AND_PARENT))
```

**You cannot reference another item's BlockType by ID.** Each state needs its own inline BlockType definition. However:
- You can use the same model/texture paths as another block
- At runtime, you can use `UpdateBlockTypes` to reskin the state's BlockType to match any other block's appearance

### Practical Impact for PlaceBlock

If we define states, each state needs a placeholder BlockType definition in JSON. Then at runtime, `BlockPreviewReskinManager` can use `UpdateBlockTypes` to reskin each state's block type to show the correct recipe output — **exactly what we do today with the 9 Green variants**.

---

## Q5: ItemStack State Switching at Runtime

### The API

From [ItemStack.java lines 190-198](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/ItemStack.java#L190-L198):

```java
@Nonnull
public ItemStack withState(@Nonnull String state) {
    String newItemId = this.getItem().getItemIdForState(state);
    if (newItemId == null) {
        throw new IllegalArgumentException("Invalid state: " + state);
    } else {
        return new ItemStack(newItemId, this.quantity, this.durability, this.maxDurability, this.metadata);
    }
}
```

### Key Observations

1. **State switching = itemId swap.** The new ItemStack has the state variant's registered ID as its `itemId`.
2. **Metadata is preserved.** Quantity, durability, maxDurability, and metadata all carry over.
3. **It's immutable.** Returns a NEW ItemStack (ItemStack is immutable).
4. **Throws on invalid state.** If the state name doesn't exist in `stateToBlock`, it throws `IllegalArgumentException`.

### How RefillContainer Uses It

From [RefillContainerInteraction.java line 179](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/modules/interaction/interaction/config/server/RefillContainerInteraction.java#L179):

```java
Item newItemAsset = current.getItem().getItemForState(newState);
// Then creates a new ItemStack with newItemAsset.getId() and sets it in inventory
```

The interaction doesn't use `withState()` directly — it manually constructs the new ItemStack after resolving the state Item. But the effect is the same: swap the itemId to the state variant's ID.

### For PlaceBlock: Switching Armed State

To switch a placeholder from "unarmed Blue" to "armed Cobble_Wall":
```java
// If "Cobble_Wall" is a defined state on the parent placeholder item:
ItemStack armed = unarmedStack.withState("Cobble_Wall");
// armed.getItemId() → "*Block_Placeholder_State_Cobble_Wall"
// armed.getMetadata() → preserved (RecipeId, TargetBlockId still there)
```

---

## Q6: `RemoveItemInHand: false` with States

### Each State Variant Has Its Own Interactions

**Yes.** The bucket proves this conclusively:

- **Empty bucket** (`Container_Bucket`): `Secondary` → `RefillContainer` interaction
- **Filled_Water state**: `Secondary` → `PlaceFluid` with `RemoveItemInHand: false`
- **Filled_Milk state**: `Secondary` → `Root_Secondary_Consume_Drink`

Each state completely **replaces** the parent's Interactions map (because `Interactions` uses `appendInherited` with `MapUtil.combineUnmodifiable` — entries in the child override entries in the parent for the same InteractionType key).

### Engine Uses the Variant's Interaction Config

When a state variant's interaction fires, the engine looks up the interaction from the **variant's** registered Item, not the parent. This is because the variant IS a separate registered Item in the asset map. The engine resolves interactions from `itemInHand.getItem().getInteractions()`, where `getItem()` returns the variant Item.

---

## Q7: State Variants and UpdateBlockTypes/UpdateItems

### Each State Variant IS a Separate Item Entry

- State variant `Filled_Water` is registered as Item `*Container_Bucket_State_Filled_Water`
- It has its own `blockId` field pointing to a registered BlockType

### Each State Variant with BlockType Gets Its Own Block Type ID

When a state variant defines a `BlockType`, the `ContainedAssetCodec` with `INHERIT_ID_AND_PARENT` mode creates a separate BlockType entry. The ID is based on the Item's ID (since mode is `INHERIT_ID_AND_PARENT`, it inherits the container asset's key — the state variant's item ID).

### Can We Use UpdateBlockTypes to Reskin a State Variant's Block Type?

**Yes.** Each state variant's BlockType is a standard entry in `BlockType.getAssetMap()`. You can:
1. Look up the block type index: `BlockType.getAssetMap().getIndex(stateVariantBlockTypeId)`
2. Build an `UpdateBlockTypes` packet with reskinned textures/model
3. Send it to the specific player

This is the same mechanism `BlockPreviewReskinManager` currently uses for the 9 Green variants.

### Can We Use UpdateItems to Reskin a State Variant's Icon?

**Yes.** `UpdateItems` operates on item IDs. State variants are just regular items with IDs like `*Block_Placeholder_State_Cobble_Wall`. You can send `UpdateItems` with a modified `ItemBase` for that ID, changing its icon per-player.

---

## Q8: Quality/Rarity on State Variants

### Quality Does NOT Inherit from Parent

From [Item.java line 145](../../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L145):
```java
.<String>append(new KeyedCodec<>("Quality", Codec.STRING), 
    (item, s) -> item.qualityId = s, 
    item -> item.qualityId)  // ← .append(), NOT .appendInherited()
```

**`Quality` uses `.append()` — NOT `.appendInherited()`.** This means:
- If a state variant specifies `"Quality": "Rare"` → it gets Rare
- If a state variant does NOT specify Quality → it gets `null` (the class default), NOT the parent's Quality

### Each State Can Have Different Quality

```json
{
  "Quality": "Common",  // Parent placeholder — Blue/unarmed
  "State": {
    "Armed_Affordable": {
      "Quality": "Uncommon"  // Green tint
    },
    "Armed_Unaffordable": {
      "Quality": "Rare"  // Red tint (or whatever maps to red)
    }
  }
}
```

### Practical Implication

We could use 3 states for color tiers:
- **Base item** (no state) = Blue/unarmed → default Quality
- **State "Armed_Affordable"** = Green → Quality that renders green
- **State "Armed_Unaffordable"** = Red → Quality that renders red

But we'd still need per-recipe icon/block type differentiation via `UpdateItems`/`UpdateBlockTypes` at runtime.

---

## Q9: How Many States Can an Item Have?

### No Hardcoded Limit

The `stateToBlock` map is a `HashMap<String, String>`. There is no size validation, no max-states constant, and no guard in the codec. The engine supports as many states as you define.

### Cost Per State

Each state creates:
1. **One registered Item** in `Item.getAssetMap()` — sent to every client on connection via `UpdateItems`
2. **One registered BlockType** (if the state defines one) in `BlockType.getAssetMap()` — sent via `UpdateBlockTypes`
3. Each Item registration adds to the `UpdateItems` init packet size (serialized as `ItemBase`)
4. Each BlockType registration adds to the `UpdateBlockTypes` init packet size

### 200+ States: Viable But Heavyweight

With 200+ recipes, 200+ states would mean:
- **200+ additional Item assets** loaded on every client connection
- **200+ additional BlockType assets** (if each state has a BlockType)
- Significant increase in the init packet sizes
- All state variants' BlockTypes would need reskinning per-player via `UpdateBlockTypes` on connection

### Recommendation

**Keep state count small (3-5).** Use states for the 3 color tiers (Blue, Green, Red), not for per-recipe variants. Continue using `UpdateBlockTypes`/`UpdateItems` for recipe-specific icon/appearance — this is already per-player and doesn't pollute the global asset map.

**Optimal architecture:**
```
Base Item: Block_Placeholder
├── State "Armed_Green"   → Quality for green tint, generic placeholder BlockType
├── State "Armed_Red"     → Quality for red tint, generic placeholder BlockType  
└── (base = unarmed Blue) → Quality for blue tint, generic placeholder BlockType

Runtime per-player:
  UpdateBlockTypes → reskin Armed_Green's BlockType to show recipe output
  UpdateItems → reskin Armed_Green's icon to show recipe output icon
```

This gives us **1 item JSON → 3 registered Items** instead of the current **10 separate JSON files → 10 registered Items**.

---

## Q10: PlaceBlockEvent with State Variants

### What getItemInHand() Returns

When a state variant with a BlockType is right-clicked, `PlaceBlockEvent.getItemInHand()` returns the ItemStack **with the state variant's item ID**.

If the placeholder is in state "Armed_Green", the ItemStack's `itemId` would be `*Block_Placeholder_State_Armed_Green` (or whatever the generated ID is).

### Impact on PlaceBlockMetadata.isPlaceBlock()

Current implementation ([PlaceBlockMetadata.java line 24-30](../../../src/main/java/com/UnobstructedThirdPerson/placeblock/PlaceBlockMetadata.java#L24-L30)):
```java
public static boolean isPlaceBlock(@Nullable ItemStack stack) {
    if (stack == null) return false;
    String id = stack.getItemId();
    return PLACEHOLDER_BLUE.equals(id) || PLACEHOLDER_GREEN.equals(id) || PLACEHOLDER_RED.equals(id)
            || id.startsWith(GREEN_VARIANT_PREFIX);
}
```

**This would need updating** if we switch to states. The state variant IDs would be like `*Block_Placeholder_State_Armed_Green`, which doesn't match the current checks.

### Updated Detection Strategy

Option A — Check by parent item:
```java
public static boolean isPlaceBlock(@Nullable ItemStack stack) {
    if (stack == null) return false;
    Item item = stack.getItem();
    String id = item.getId();
    // Check if this is the base placeholder or any of its states
    if (PLACEHOLDER_BASE.equals(id)) return true;
    // Check if this item is a state variant of the placeholder
    Item parentPlaceholder = Item.getAssetMap().getAsset(PLACEHOLDER_BASE);
    return parentPlaceholder != null && parentPlaceholder.getStateForItem(id) != null;
}
```

Option B — Check by ID prefix (simpler, relies on naming convention):
```java
public static boolean isPlaceBlock(@Nullable ItemStack stack) {
    if (stack == null) return false;
    String id = stack.getItemId();
    return id.equals(PLACEHOLDER_BASE) || id.startsWith("*" + PLACEHOLDER_BASE + "_State_");
}
```

### Does PlaceBlockEvent Fire for State Variants?

Yes. The engine dispatches `PlaceBlockEvent` based on the item's interaction chain (`PlaceBlockInteraction`). Since each state variant inherits or overrides interactions independently, if the state has a `PlaceBlock` interaction (or the parent's interaction fires for a block-type-having item), the event fires as normal.

---

## Key Architectural Decision Matrix

| Approach | Items in Asset Map | BlockTypes in Map | Per-Player Packets | JSON Files | Complexity |
|----------|-------------------|-------------------|-------------------|------------|------------|
| **Current: 10 separate items** | 10 | 10 | UpdateBlockTypes × 9 per player | 10 | Simple |
| **3 states (Blue/Green/Red)** | 3 | 3 | UpdateBlockTypes + UpdateItems per player | 1 | Medium |
| **200+ states (per recipe)** | 200+ | 200+ | None (pre-defined) | 1 | Complex, wasteful |
| **1 item + metadata + UpdateItems** | 1 | 1 | UpdateBlockTypes + UpdateItems per player | 1 | Medium |

### Verdict

**3-state approach is optimal.** It reduces 10 JSON files to 1, keeps asset map pollution minimal (3 items/blocktypes), and our existing `UpdateBlockTypes` reskinning pattern handles per-recipe differentiation at runtime. The state system provides the Blue→Green→Red color tiers natively via Quality, while metadata continues to carry the recipe ID.

---

## See Also
- [Bucket Pattern](./bucket-pattern.md) — detailed bucket lifecycle analysis
- [PlaceBlockMetadata.java](../../../src/main/java/com/UnobstructedThirdPerson/placeblock/PlaceBlockMetadata.java) — current placeholder detection
- [PlaceBlockPlacementSystem.java](../../../src/main/java/com/UnobstructedThirdPerson/placeblock/PlaceBlockPlacementSystem.java) — current placement handling
