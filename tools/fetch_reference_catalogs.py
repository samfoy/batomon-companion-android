#!/usr/bin/env python3
"""Fetch text-only, versioned public reference catalogs.

Usage: python3 tools/fetch_reference_catalogs.py --out-dir app/src/main/assets/reference
Artwork is retained only as a source path; no image bytes are downloaded.
"""
import argparse, datetime, json, re, urllib.request
from pathlib import Path

BASE = "https://batomon.com"
ROUTES = {
    "batomon": ("batomon", 100),
    "trinket": ("trinkets", 50),
    "trainer": ("trainers", 20),
}
ENTITY = re.compile(
    r'\\"id\\":\\"([^\"]+)\\",\\"kind\\":\\"(batomon|trinket|trainer)\\",'
    r'\\"slug\\":\\"([^\"]+)\\",\\"name\\":\\"((?:\\\\.|[^\"])*)\\",'
    r'\\"description\\":\\"((?:\\\\.|[^\"])*)\\",\\"spritePath\\":\\"([^\"]+)\\"'
)

def decode(value):
    return json.loads('"' + value + '"')

def fetch(route):
    request = urllib.request.Request(f"{BASE}/{route}", headers={"User-Agent": "BatomonCompanionCatalog/0.3 (+https://github.com/samfoy/batomon-companion-android)"})
    return urllib.request.urlopen(request, timeout=20).read().decode("utf-8")

def parse(raw, kind):
    entries = []
    for match in ENTITY.finditer(raw):
        if match.group(2) != kind:
            continue
        entry = {"id": match.group(1), "slug": match.group(3), "name": decode(match.group(4)), "description": decode(match.group(5)), "spritePath": match.group(6)}
        entries.append(entry)
    seen = set()
    return [item for item in entries if not (item["id"] in seen or seen.add(item["id"]))]

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--out-dir", default="app/src/main/assets/reference"); args = parser.parse_args()
    out = Path(args.out_dir); out.mkdir(parents=True, exist_ok=True); today = datetime.date.today().isoformat()
    for kind, (route, minimum) in ROUTES.items():
        items = parse(fetch(route), kind)
        if len(items) < minimum: raise SystemExit(f"{kind}: expected at least {minimum}, found {len(items)}")
        payload = {"schema": 1, "reference": f"Batomon {kind} reference", "balance": "24", "gameBuild": "1.2.0 / 25600878", "source": f"{BASE}/{route}", "sourceAccessed": today, "assetPolicy": "Text metadata only; sprite paths are provenance and artwork is not redistributed while licensing is pending.", "entries": items}
        (out / f"{kind}s_balance24.json").write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n")
        print(f"{kind}: {len(items)} records")

if __name__ == "__main__": main()
