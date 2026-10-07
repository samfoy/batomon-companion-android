#!/usr/bin/env python3
"""Regenerate the text-only item catalog from the public versioned reference.

Usage: python3 tools/fetch_items_catalog.py > app/src/main/assets/reference/items_balance24.json
The generated file intentionally keeps image paths as attribution metadata and does
not download artwork; the reference site notes that remote sprite licensing is pending.
"""
import datetime, json, re, urllib.request

URL = "https://batomon.com/items"
request = urllib.request.Request(URL, headers={"User-Agent": "BatomonCompanionCatalog/0.2 (+https://github.com/samfoy/batomon-companion-android)"})
raw = urllib.request.urlopen(request, timeout=20).read().decode("utf-8")
pattern = re.compile(r'\\"id\\":\\"([^\"]+)\\",\\"kind\\":\\"item\\",\\"slug\\":\\"([^\"]+)\\",\\"name\\":\\"([^\"]+)\\",\\"description\\":\\"([^\"]*)\\",\\"spritePath\\":\\"([^\"]+)\\"')
items = [{"id": a, "name": c, "effect": d, "spritePath": e} for a, _, c, d, e in pattern.findall(raw)]
if len(items) < 40:
    raise SystemExit(f"expected at least 40 item records, found {len(items)}")
print(json.dumps({"schema": 1, "reference": "Batomon item reference", "balance": "24", "gameBuild": "1.2.0 / 25600878", "source": URL, "sourceAccessed": datetime.date.today().isoformat(), "assetPolicy": "Text metadata only. Artwork remains remote because the public reference notes licensing is pending.", "items": items}, indent=2, ensure_ascii=False))
