# Testing Item IDs

When manually testing with `ItemStack`, always use real item IDs from `docs/Resources/items/`:

- `Rock_Stone` — `docs/Resources/items/Blocks/Rocks/Rock_Stone.json`
- `Rock_Stone_Cobble` — `docs/Resources/items/Blocks/Rocks/Rock_Stone_Cobble.json`
- `Wood_Oak_Trunk` — `docs/Resources/items/Blocks/Wood/Wood_Oak_Trunk.json`
- `Wood_Hardwood_Planks` — `docs/Resources/items/Blocks/Wood/Wood_Hardwood_Planks.json`
- `Plant_Leaves_Oak` — `docs/Resources/items/Blocks/Plants/Plant_Leaves_Oak.json`
- `Bench_Builders` — `docs/Resources/items/Furniture/Benches/Bench_Builders.json`
- `Container_Bucket` — `docs/Resources/items/Container/Container_Bucket.json`

Do NOT guess item IDs like "Wood_Log_Oak" or "Unknown" — they crash or fail silently.
