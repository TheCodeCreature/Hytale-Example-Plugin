---
topic: "Block Type JSON Format"
category: "Asset Formats"
updated: 2026-04-16
sources: ["codebase analysis", "asset JSON files"]
---

# Block Type JSON Format

## Summary

Block types are defined as part of item JSON files. The `BlockType` field within an item definition makes that item placeable as a block.

## Full Schema

```json
{
  "TranslationProperties": {
    "Name": "server.items.MyBlock.name",
    "Description": "server.items.MyBlock.description"
  },
  "Parent": "ParentItemId",
  "ItemLevel": 10,
  "MaxStack": 100,
  "Icon": "Icons/ItemsGenerated/MyBlock.png",
  "Categories": ["Blocks.Rocks"],
  "SubCategory": "Trees",
  "Set": "Rock_Stone",
  "FuelQuality": 9.0,
  "PlayerAnimationsId": "Block",
  "Quality": "Common",
  "ItemSoundSetId": "ISS_Blocks_Stone",
  
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Cube",
    "Group": "Stone",
    "Opacity": "Solid",
    "HitboxType": "Full",
    "Flags": {},
    
    "Gathering": {
      "Breaking": {
        "GatherType": "Rocks",
        "Quality": 1,
        "Quantity": 1,
        "ItemId": "Rock_Stone_Cobble",
        "DropListId": null
      },
      "Soft": {
        "ItemId": "some_item",
        "DropList": "some_drop_list"
      },
      "Harvest": {
        "ItemId": "harvest_item",
        "Quantity": 3
      },
      "Physics": {
        "ItemId": "physics_drop_item",
        "DropList": "physics_drop_list"
      },
      "Tools": [
        {
          "Type": "Scraper",
          "State": "Stripped",
          "DropList": "Bark"
        },
        {
          "Type": "Shears"
        }
      ],
      "UseDefaultDropWhenPlaced": true
    },
    
    "Textures": [
      {
        "All": "BlockTextures/MyBlock.png",
        "Weight": 1
      },
      {
        "Sides": "BlockTextures/MyBlock_Side.png",
        "UpDown": "BlockTextures/MyBlock_Top.png",
        "Weight": 1
      }
    ],
    
    "CustomModel": "Blocks/Custom/MyModel.blockymodel",
    "CustomModelTexture": [
      {
        "Texture": "Blocks/Custom/MyModel_Texture.png",
        "Weight": 1
      }
    ],
    
    "VariantRotation": "NESW",
    "RandomRotation": "YawPitchRollStep1",
    "RequiresAlphaBlending": false,
    
    "BlockParticleSetId": "Stone",
    "ParticleColor": "#737055",
    "BlockSoundSetId": "Stone",
    "BlockBreakingDecalId": "Breaking_Decals_Rock",
    
    "Aliases": ["myblock", "custom_block"],
    "Effect": ["Wind"],
    
    "MaxSupportDistance": 5,
    "Support": {
      "Down": [
        {
          "AllowSupportPropagation": false,
          "FaceType": "Full",
          "Rotate": false
        }
      ],
      "Horizontal": [
        {
          "TagId": "Type=Trunk",
          "Rotate": false,
          "Support": "Ignored"
        }
      ]
    },
    
    "PlacementSettings": {
      "AllowBreakReplace": true
    },
    
    "State": {
      "Definitions": {
        "Stripped": {
          "Textures": [{"All": "BlockTextures/Stripped.png"}],
          "Gathering": {
            "Breaking": { "GatherType": "Woods", "ItemId": "Wood_Stripped" }
          }
        }
      }
    },
    
    "Bench": {
      "Type": "StructuralCrafting",
      "Id": "Builders",
      "AllowBlockGroupCycling": true,
      "Categories": ["WoodPlanks", "Stairs", "Wall"]
    },
    
    "Interactions": {
      "Use": {
        "Interactions": [{ "Type": "Bed" }]
      },
      "Primary": "Check_Can_Break_Respawn"
    },
    
    "Beds": [
      {
        "Offset": { "X": -0.1, "Y": 0.4, "Z": 0.7 },
        "Yaw": 0
      }
    ]
  },
  
  "Recipe": {
    "Input": [
      { "ResourceTypeId": "Wood_Trunk", "Quantity": 6 },
      { "ResourceTypeId": "Rock", "Quantity": 3 }
    ],
    "BenchRequirement": [
      {
        "Id": "Fieldcraft",
        "Type": "Crafting",
        "Categories": ["Tools"]
      }
    ],
    "OutputQuantity": 1,
    "TimeSeconds": 0
  },
  
  "ResourceTypes": [
    { "Id": "Rock" },
    { "Id": "Rock_Stone" }
  ],
  
  "Tags": {
    "Type": ["Rock"],
    "Family": ["Hardwood"]
  },
  
  "IconProperties": {
    "Scale": 0.58823,
    "Rotation": [22.5, 45, 22.5],
    "Translation": [0, -13.5]
  },
  
  "Interactions": {
    "Primary": "Block_Primary",
    "Secondary": "Block_Secondary"
  }
}
```

## Material Types

| Value | Description |
|-------|-------------|
| `Solid` | Standard solid block |
| `Liquid` | Water, lava |
| `Gas` | Air-like |

## Draw Types

| Value | Description |
|-------|-------------|
| `Cube` | Standard 6-face cube |
| `Model` | Custom 3D model (requires `CustomModel`) |
| `Cross` | X-shaped (plants, grass) |

## Opacity Types

| Value | Description |
|-------|-------------|
| `Solid` | Fully opaque |
| `Transparent` | Transparent with alpha |
| `Cutout` | Binary transparency (no partial alpha) |

## See Also

- [Block Types](../../blocks/block-types.md)
- [Item Format](./item.md)
