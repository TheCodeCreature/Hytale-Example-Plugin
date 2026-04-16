---
topic: "Crafting Recipes"
category: "Crafting"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Crafting Recipes

## Summary

`CraftingRecipe` defines how items are crafted at workbenches. Recipes specify input materials, output items, bench requirements, and optional time costs.

## Recipe Structure

Recipes are defined inline in item JSON files:

```json
"Recipe": {
  "Input": [
    {
      "ResourceTypeId": "Wood_Hardwood_Trunk",
      "Quantity": 1
    }
  ],
  "BenchRequirement": [
    {
      "Id": "Builders",
      "Type": "StructuralCrafting",
      "Categories": ["WoodPlanks"]
    }
  ],
  "OutputQuantity": 1,
  "TimeSeconds": 0
}
```

## Input Types

Each input is a `MaterialQuantity` with one of these matching modes:

| Field | Description |
|-------|-------------|
| `ItemId` | Match a specific item by ID |
| `ResourceTypeId` | Match any item in a resource type group |
| `TagIndex` | Match items by tag |
| `Quantity` | How many of this material are needed |

### MaterialQuantity API

```java
MaterialQuantity mq = recipe.getInput()[0];
String itemId = mq.getItemId();               // Specific item (may be null)
String resourceTypeId = mq.getResourceTypeId(); // Resource group (may be null)
int tagIndex = mq.getTagIndex();               // Tag match (may be -1)
int quantity = mq.getQuantity();               // Required amount

// Create a clone with different quantity
MaterialQuantity scaled = mq.clone(newQuantity);
```

### Resource Type Matching

When `ResourceTypeId` is used, ANY item whose `ResourceTypes` list includes that ID satisfies the input. For example:

- Recipe input: `"ResourceTypeId": "Rock"` 
- Accepted items: any item with `"ResourceTypes": [{"Id": "Rock"}]` — includes `Rock_Stone`, `Rock_Shale`, etc.

## Output

```java
MaterialQuantity output = recipe.getPrimaryOutput();
String outputItemId = output.getItemId();
int outputQuantity = output.getQuantity();
```

The `OutputQuantity` field in JSON sets how many items are produced per craft.

## Bench Requirements

Recipes require specific workbenches:

```java
BenchRequirement[] benches = recipe.getBenchRequirement();
for (BenchRequirement bench : benches) {
    String id = bench.getId();        // e.g., "Builders", "Furniture_Bench"
    String type = bench.getType();    // e.g., "StructuralCrafting", "Crafting"
    // Categories determine which bench tab the recipe appears in
}
```

## Runtime Access

```java
// Iterate all recipes
for (var entry : CraftingRecipe.getAssetMap().getAssetMap().entrySet()) {
    CraftingRecipe recipe = entry.getValue();
    String recipeId = recipe.getId();
    
    // Check inputs
    MaterialQuantity[] inputs = recipe.getInput();
    
    // Check output
    MaterialQuantity output = recipe.getPrimaryOutput();
}
```

## Modifying Recipes at Runtime

Recipe inputs can be modified via reflection (no public setters):

```java
Field recipeInput = CraftingRecipe.class.getDeclaredField("input");
recipeInput.setAccessible(true);

MaterialQuantity[] scaledInputs = new MaterialQuantity[inputs.length];
for (int i = 0; i < inputs.length; i++) {
    scaledInputs[i] = inputs[i].clone(inputs[i].getQuantity() * multiplier);
}

recipeInput.set(recipe, scaledInputs);
```

## Special Recipe Types

| Prefix/Type | Description |
|-------------|-------------|
| `Salvage*` | Salvage recipes (breaking down items) — often filtered out |
| Fieldcraft bench | Basic recipes available without a workbench |
| Processing bench | Refinement recipes (e.g., smelting) |

## See Also

- [Bench Types](./bench-types.md)
- [Block Groups & Resource Types](./block-groups.md)
- [Crafting Recipe Format](../assets/formats/crafting-recipe.md)
