---
area: "Economy Persistence"
updated: 2026-05-19
---

# Economy Persistence — Manifest Contract

## Player Experience Goal

Once an admin calculates the economy, it should survive server restarts without any visible delay or recomputation. When the server boots, it loads the saved manifest and applies the pre-computed modifications instantly. The admin should never wonder "did my economy changes stick?"

## Behavioral Contracts

1. **The manifest is the single source of truth.** If a manifest exists on disk, the server applies it on boot. If no manifest exists, the economy runs at vanilla values.
2. **The manifest is human-readable JSON.** Admins can open it, inspect it, and understand what was changed.
3. **The manifest includes a staleness check.** An asset content hash (or recipe count + block count fingerprint) is stored. If the loaded assets don't match, the server logs a warning but still applies the manifest — the admin must explicitly recalculate.
4. **The manifest stores the computed modifications, not the logic.** It records "Recipe X: input[0] quantity changed from 1 to 12" — not "run the pipeline." This makes it inspectable and debuggable.
5. **The manifest lives in the server's run directory** (e.g., `run/economy_manifest.json`). It is NOT inside the world save — it applies server-wide.

## Manifest Format

```json
{
  "version": 1,
  "pluginVersion": "1.0.0",
  "createdAt": "2026-04-18T20:33:42Z",
  "multiplier": 12,
  "assetFingerprint": "recipes:1063,blocks:4671,items:2520",
  "benchIds": ["Builders", "Furniture_Bench"],
  
  "recipeCostScaling": [
    {
      "recipeId": "Deco_Rope_Recipe_Generated_0",
      "inputs": [
        { "index": 0, "itemId": "Ingredient_Fibre", "classification": "crafted", "originalQty": 1, "scaledQty": 1 }
      ]
    }
  ],
  
  "blockDropOverrides": [
    {
      "blockTypeId": "Deco_Rope",
      "breaking": {
        "gatherType": "Plants",
        "quality": 0,
        "quantity": 1,
        "itemId": "Ingredient_Fibre",
        "dropListId": null
      }
    }
  ],
  
  "naturalBlockDropScaling": [
    {
      "blockTypeId": "Rock_Stone",
      "breaking": { "originalQty": 1, "scaledQty": 12 }
    }
  ],
  
  "syntheticDropLists": [
    {
      "id": "Plugin_NaturalIngredient_Ingredient_Fibre",
      "drops": [
        { "itemId": "Ingredient_Fibre", "quantityMin": 12, "quantityMax": 12 }
      ]
    }
  ],
  
  "stackSizeOverrides": [
    { "itemId": "Rock_Stone_Cobble", "originalMax": 100, "scaledMax": 1200 }
  ],
  
  "summary": {
    "recipesScaled": 577,
    "naturalBlocksModified": 4615,
    "recipeBlocksConfigured": 576,
    "syntheticDropListsCreated": 89,
    "stackSizesBoosted": 2438,
    "baseBlockRecipesSkipped": 486,
    "baseItemRecipesSkipped": 0,
    "errors": []
  }
}
```

## Edge Cases & Decisions

| Scenario | Decision | Rationale |
|----------|----------|-----------|
| Manifest file is corrupted or invalid JSON | Log error, run at vanilla values, do NOT auto-recalculate | Safety — don't silently apply a broken economy |
| Manifest was created with multiplier=12 but admin changes config to multiplier=8 | Staleness detected — log warning, apply old manifest until admin recalculates | Determinism — never silently change the economy |
| New plugin version changes pipeline logic | `pluginVersion` mismatch triggers warning in `/economy status` | Admin awareness |
| Manifest references a recipe/block/item that no longer exists in assets | Skip that entry, log warning, continue applying the rest | Graceful degradation after game updates |

## Anti-Patterns to Reject

- **Storing the manifest inside world data.** The economy applies server-wide, not per-world. Multiple worlds share the same recipe/block assets.
- **Binary or opaque formats.** Admins must be able to read and verify the manifest.
- **Auto-recalculation on staleness.** Stale data should warn, not silently recompute. The admin decides when to recalculate.
