---
topic: "ItemStack State Storage & Block Preview Per-Instance"
category: "Items / Block Preview / Architecture"
updated: 2026-04-26
sources:
  - ".tmp_hytale_src/.../ItemStack.java"
  - ".tmp_hytale_src/.../Item.java"
  - ".tmp_hytale_src/.../ItemBase.java (protocol)"
  - ".tmp_hytale_src/.../UpdateBlockTypes.java"
  - ".tmp_hytale_src/.../BlockTypePacketGenerator.java"
  - ".tmp_hytale_src/.../BlockPlacementSettings.java"
  - ".tmp_hytale_src/.../BlockPreviewVisibility.java"
  - "docs/hytale/items/item-state-system.md"
  - "docs/hytale/items/bucket-pattern.md"
---

# ItemStack State Storage & Block Preview Per-Instance

## Summary

**State is per-ItemStack via itemId swapping.** Hytale has NO separate "state" field on ItemStack. When a bucket fills with water, the `itemId` changes from `Container_Bucket` to `*Container_Bucket_State_Filled_Water`. Two buckets in different states are two ItemStacks with different itemIds — completely independent.

**Block preview is per-block-type-ID, NOT per-ItemStack.** The ghost block preview shown when holding a block item is derived from `ItemBase.blockId` — a numeric block type index sent to the client in the `UpdateItems` packet. `UpdateBlockTypes` reskins that index globally for the player. There is NO mechanism to have one item type produce different ghost previews for different ItemStack instances.

---

## Q1: How Does the ItemStack Store Its State?

### Answer: The `itemId` field IS the state. There is no separate state field.

From [ItemStack.java lines 190-198](../../.tmp_hytale_src/com/hypixel/hytale/server/core/inventory/ItemStack.java#L190-L198):
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

**Fields on ItemStack:**
| Field | Purpose |
|-------|---------|
| `String itemId` | The item's registered ID — changes when state changes |
| `int quantity` | Stack count |
| `double durability` | Current durability |
| `double maxDurability` | Max durability |
| `BsonDocument metadata` | Arbitrary key-value data (preserved across state changes) |
| `boolean overrideDroppedItemAnimation` | Animation flag |

**There is no `state` field.** State switching creates a NEW ItemStack with a different `itemId`.

### When a bucket fills with water:
- **Before:** `ItemStack.itemId = "Container_Bucket"`
- **After:** `ItemStack.itemId = "*Container_Bucket_State_Filled_Water"`

The `*` prefix indicates a generated/contained asset ID. The full generated ID format is:
```
*{parentId}_State_{stateName}
```

---

## Q2: Are State Variants Just Different Item IDs?

### Answer: YES. State variants are completely independent Item assets registered in the global asset map under generated IDs.

From [Item.java line 790](../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/item/config/Item.java#L790):
```java
public String getItemIdForState(String state) {
    return this.stateToBlock != null ? this.stateToBlock.get(state) : null;
}
```

The `stateToBlock` map is populated during JSON codec loading. Each entry in the `State` map creates:
1. A separate **Item** entry in `Item.getAssetMap()`
2. A separate **BlockType** entry in `BlockType.getAssetMap()` (if the state defines BlockType)
3. A mapping: state name → generated item ID (in parent's `stateToBlock`)
4. A reverse mapping: generated item ID → state name (in parent's `blockToState`)

**Two buckets in the same inventory (empty + water) are two different items:**

| Slot | ItemStack.itemId | Item Asset | BlockType Asset |
|------|-----------------|------------|-----------------|
| 0 | `Container_Bucket` | `Container_Bucket` | empty bucket model |
| 1 | `*Container_Bucket_State_Filled_Water` | `*Container_Bucket_State_Filled_Water` | full bucket with water texture |

They don't share any mutable state. They're as independent as a sword and a pickaxe.

---

## Q3: Does `UpdateBlockTypes` Reskin by Item ID or by Block Type ID?

### Answer: By numeric block type INDEX. It's global per-player — ALL items referencing that block type ID see the reskin.

From [UpdateBlockTypes.java](../../.tmp_hytale_src/com/hypixel/hytale/protocol/packets/assets/UpdateBlockTypes.java):
```java
public class UpdateBlockTypes implements Packet {
    @Nullable
    public Map<Integer, BlockType> blockTypes;  // ← key is numeric block type index
}
```

From [BlockTypePacketGenerator.java](../../.tmp_hytale_src/com/hypixel/hytale/server/core/asset/type/blocktype/BlockTypePacketGenerator.java):
```java
int index = assetMap.getIndex(key);  // String key → numeric index
blockTypes.put(index, entry.getValue().toPacket());
```

**The packet maps `int blockTypeIndex → BlockType protocolData`.** The client replaces its cached block type data for that index. This affects:
- The ghost block preview for ANY item whose `ItemBase.blockId == that index`
- Blocks already placed in the world with that block type index
- Any new placements of that block type

### The Critical Implication

If two armed placeholders in slots 3 and 7 both have `itemId = "*Block_Placeholder_State_Armed_Green"`, they both reference the SAME block type index. Reskinning that index to show "Cobble Wall" makes BOTH show Cobble Wall. **There is no per-slot override.**

**This is exactly why the 9-variant system exists** — `Block_Placeholder_Green_0` through `_8` each have their own block type index, so each can be independently reskinned.

### Is there ANY way to have per-slot block type overrides?

**No.** The `UpdateBlockTypes` packet has no concept of "this reskin applies only when this block type is referenced from inventory slot N." The protocol is:
```
blockTypeIndex → BlockType (model, texture, drawType, etc.)
```
There is no slot-scoping field.

---

## Q4: How Does the Bucket Handle Visual Differentiation?

### Answer: Each state variant has its own BlockType definition → its own block type index → its own independent appearance.

The bucket does NOT face the "same block type" problem because water and milk are **different states with different block type IDs**:

| State | Generated Item ID | Block Type Entry | Block Type Index |
|-------|------------------|------------------|-----------------|
| (base) | `Container_Bucket` | empty bucket model | N |
| Filled_Water | `*Container_Bucket_State_Filled_Water` | bucket_full + water texture | N+1 |
| Filled_Milk | `*Container_Bucket_State_Filled_Milk` | bucket_full + milk texture | N+2 |
| Filled_Mosshorn_Milk | `*Container_Bucket_State_Filled_Mosshorn_Milk` | bucket_full + mosshorn texture | N+3 |

Each state's BlockType is registered as a separate entry via `ContainedAssetCodec<BlockType>` with `INHERIT_ID_AND_PARENT` mode. Each gets a unique numeric index. The client can show different ghost previews because they ARE different block types.

**This is the same pattern the 9 Green variants use** — each `Block_Placeholder_Green_N` has its own block type index.

---

## Q5: Could We Use Dynamic States for Per-Recipe Differentiation?

### Proposed: 9 state variants `Armed_Green_0` through `Armed_Green_8` in a single JSON

### Answer: YES, this is viable. It is functionally equivalent to 9 separate JSON files.

Each state variant would:
1. Be registered as a separate Item: `*Block_Placeholder_State_Armed_Green_0`, etc.
2. Get its own BlockType entry with its own numeric index
3. Be independently reskinnable via `UpdateBlockTypes`

### Comparison: States vs Separate JSON Files

| Aspect | 9 JSON Files | 9 State Variants |
|--------|-------------|-----------------|
| Item registration | 9 separate Items | 9 separate Items (identical) |
| BlockType registration | 9 separate BlockTypes | 9 separate BlockTypes (identical) |
| Numeric block type indices | 9 unique indices | 9 unique indices (identical) |
| UpdateBlockTypes independence | ✅ Each independently reskinnable | ✅ Each independently reskinnable |
| Ghost preview independence | ✅ Per-slot previews work | ✅ Per-slot previews work |
| ID format | `Block_Placeholder_Green_0` | `*Block_Placeholder_State_Armed_Green_0` |
| JSON maintenance | 9 files, mostly copy-paste | 1 file, 9 state entries |
| Runtime state switching | Must swap itemId manually | Can use `ItemStack.withState("Armed_Green_3")` |
| Metadata preservation | Manual (must copy BSON) | Automatic (`withState()` preserves metadata) |
| `Variant: true` filtering | Must set per-file | Can set once per state (hidden from creative menu) |

### Differences That Matter

1. **ID format**: States use `*` prefix and `_State_` infix. Any code doing string matching on item IDs (like `PlaceBlockMetadata.isGreenVariant()`) must use the state-generated ID format.

2. **`withState()` convenience**: The state API provides `ItemStack.withState("Armed_Green_3")` which automatically:
   - Resolves the generated item ID from the parent's `stateToBlock` map
   - Creates a new ItemStack preserving quantity, durability, and metadata
   - No manual construction needed

3. **Sibling transitions**: State variants inherit `stateToBlock` from the parent, so you can switch directly between siblings: `stack.withState("Armed_Green_7")` from any state variant.

4. **Single source of truth**: One JSON file defines all variants. Changes to shared properties (Icon, Interactions, etc.) propagate to all states via inheritance, unless overridden.

### Verdict

States are **slightly cleaner** than separate files but **functionally identical** for the purpose of independent block preview reskinning. The choice is a maintenance preference, not an architectural one.

---

## Q6: Could We Use BSON Metadata to Differentiate Block Preview Per Slot?

### Answer: NO. ItemStack metadata has NO connection to block preview rendering.

The ghost block preview chain:

```
ItemStack (server) 
  → itemId 
    → Item asset 
      → Item.blockId (String, e.g. "Block_Placeholder_Green_0") 
        → BlockType asset 
          → toPacket() → protocol.BlockType 
            → ItemBase.blockId (int, numeric index)
              → Client receives in UpdateItems packet
                → Client uses blockId to render ghost preview
```

**Where metadata enters:** Never. The `ItemWithAllMetadata` packet (sent for hotbar items) includes:
```java
packet.itemId = this.itemId.toString();     // → determines blockId lookup
packet.quantity = this.quantity;
packet.durability = this.durability;
packet.maxDurability = this.maxDurability;
packet.metadata = this.metadata != null ? this.metadata.toJson() : null;  // → opaque to client preview
```

The client uses `itemId` to look up the `ItemBase` (from the `UpdateItems` init packet), which has `int blockId`. The `metadata` JSON is available for UI tooltips and custom display, but it does NOT influence which block type the client renders as a ghost preview.

### Could a future custom client mod read metadata for preview?

Theoretically yes — a client-side mod could read `metadata` and dynamically swap the ghost preview. But:
- This requires client-side modding, which is outside the server plugin boundary
- The server plugin API cannot control client rendering behavior through metadata
- There is no existing mechanism or hook for this

---

## Q7: One Item File, Many Independent Visual Previews?

### Answer: NO. There is no mechanism to have one item type produce different ghost block previews for different ItemStack instances.

The ghost preview is **always** derived from:
```
Item type → BlockType reference → numeric block type index → client-side block type data
```

This chain is:
1. **Per-Item-type**, not per-ItemStack: all ItemStacks with the same `itemId` reference the same Item asset, which has one `blockId`
2. **Reskinnable per-player** via `UpdateBlockTypes`, but the reskin applies to ALL items referencing that block type index
3. **Not scoped per-inventory-slot**: the protocol has no concept of "this block type looks like X in slot 3 but like Y in slot 7"

### The Only Way to Get Independent Previews

You MUST have **different block type IDs** for each independently-reskinnable preview. This means either:

| Approach | Mechanism | Trade-off |
|----------|-----------|-----------|
| **N separate JSON files** | `Block_Placeholder_Green_0.json` through `_8.json` | More files, simple IDs |
| **N state variants** | `State: { Armed_Green_0: {...}, ..., Armed_Green_8: {...} }` in one JSON | One file, generated `*` prefix IDs |
| **N+M pre-defined variants** | Define all possible target block types as states | Hundreds of block types registered, impractical |

The 9-variant approach (whether via files or states) is the correct architecture. Each variant gets its own block type index, enabling independent `UpdateBlockTypes` reskinning per hotbar slot.

### The `BlockPlacementSettings` Red Herring

`BlockPlacementSettings` has fields like `wallPlacementOverrideBlockId`, `floorPlacementOverrideBlockId`, `ceilingPlacementOverrideBlockId` — these override which block gets PLACED based on surface orientation, but they do NOT affect the ghost preview rendering. They're for connected blocks (stairs, fences) that change shape based on placement surface.

---

## Architectural Conclusion

```
┌─────────────────────────────────────────────────────────┐
│                   THE CHAIN OF TRUTH                     │
│                                                          │
│  ItemStack.itemId ─→ Item asset ─→ Item.blockId (str)   │
│       ↓                                                  │
│  Item.blockId ─→ BlockType.getAssetMap().getIndex()     │
│       ↓                                                  │
│  Numeric index ─→ sent to client in ItemBase.blockId    │
│       ↓                                                  │
│  Client renders ghost preview for that numeric index     │
│       ↓                                                  │
│  UpdateBlockTypes can change what that index looks like  │
│  BUT it changes it for ALL items referencing that index  │
│                                                          │
│  ∴ Independent previews REQUIRE different block type IDs │
│  ∴ 9 variants (files or states) is the correct approach  │
└─────────────────────────────────────────────────────────┘
```
