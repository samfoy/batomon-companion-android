# Public reference data

v0.2 bundles a text-only item catalog generated from the public [Batomon item reference](https://batomon.com/items). The page identifies the snapshot as Balance 24 / game build 1.2.0 (Steam build 25600878). The catalog is stored at `app/src/main/assets/reference/items_balance24.json` and includes effect text plus the source sprite path for attribution/search. It does not redistribute image files: the public reference currently states that remote game sprites have licensing pending.

Regenerate it with:

```bash
python3 tools/fetch_items_catalog.py > app/src/main/assets/reference/items_balance24.json
```

The app has no Internet permission and never fetches this catalog at runtime. The source is community-maintained and versioned; values may become stale when the game changes. This project does not imply endorsement by the game developer or batomon.com.
