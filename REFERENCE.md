# Public reference data

v0.3 bundles text-only catalogs generated from public [Batomon](https://batomon.com/batomon), [trinket](https://batomon.com/trinkets), [trainer](https://batomon.com/trainers), and [item](https://batomon.com/items) references. They identify the snapshot as Balance 24 / game build 1.2.0 (Steam build 25600878). The current snapshot contains 136 Batomon, 93 trinkets, 24 trainers, and 40 items. Each record includes descriptive text plus the source sprite path for attribution/search. It does not redistribute image files: the public reference currently states that remote game sprites have licensing pending.

Regenerate it with:

```bash
python3 tools/fetch_items_catalog.py > app/src/main/assets/reference/items_balance24.json
python3 tools/fetch_reference_catalogs.py --out-dir app/src/main/assets/reference
python3 tools/generate_sprite_signatures.py --output app/src/main/assets/reference/batomon_sprite_signatures_balance24.json
```

The sprite-signature file is a versioned, non-reversible calibration aid generated from the same public sprite URLs. The generator downloads each PNG only to a temporary directory, emits perceptual/color/edge summaries and source hashes, and removes the artwork; no sprite pixels are committed. Its matching scores are experimental and must not be treated as identification without device fixtures. Public provenance used for the v0.4 scene cues is the [official Steam listing](https://store.steampowered.com/app/4557380/Batomon_Showdown/) and the community [how-to-play guide](https://batomon.com/how-to-play). Those sources establish broad visual context only; the arena/result cues are inferences and require calibration on real Thor captures.

The app has no Internet permission and never fetches these catalogs at runtime. Comps remain a live external link because rankings are time-sensitive and artwork/data licensing is not established for redistribution. The source is community-maintained and versioned; values may become stale when the game changes. This project does not imply endorsement by the game developer or batomon.com.
