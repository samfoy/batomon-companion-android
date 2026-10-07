#!/usr/bin/env python3
"""Generate non-reversible sprite signatures from the public text catalog.

The source PNGs are downloaded into a temporary directory and deleted. Only a
64-bit luminance hash, coarse average color, and edge density are written.
"""
import argparse, hashlib, json, tempfile, urllib.request
from pathlib import Path
from PIL import Image, ImageStat

BASE = "https://batomon.com"

def signature(url):
    request = urllib.request.Request(url, headers={"User-Agent": "BatomonCompanionSignatures/0.4 (+https://github.com/samfoy/batomon-companion-android)"})
    with urllib.request.urlopen(request, timeout=20) as response:
        data = response.read()
    digest = hashlib.sha256(data).hexdigest()
    image = Image.open(__import__('io').BytesIO(data)).convert("RGBA")
    background = Image.new("RGBA", image.size, (0, 0, 0, 255)); background.alpha_composite(image)
    rgb = background.convert("RGB")
    small = rgb.resize((8, 8), Image.Resampling.BILINEAR)
    luma = [round(.299 * r + .587 * g + .114 * b) for r, g, b in small.getdata()]
    avg = sum(luma) / len(luma); bits = sum((1 << i) for i, value in enumerate(luma) if value >= avg)
    color = ImageStat.Stat(rgb.resize((16, 16), Image.Resampling.BILINEAR)).mean
    gray = rgb.convert("L").resize((16, 16), Image.Resampling.BILINEAR); pixels = list(gray.getdata())
    edges = [abs(pixels[y * 16 + x] - pixels[y * 16 + x + 1]) for y in range(16) for x in range(15)]
    edges += [abs(pixels[y * 16 + x] - pixels[(y + 1) * 16 + x]) for y in range(15) for x in range(16)]
    return {"hash64": f"{bits:016x}", "averageRgb": [round(value) for value in color], "edgeDensity": round(sum(1 for edge in edges if edge >= 24) / len(edges), 4), "sourceSha256": digest}

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--catalog", default="app/src/main/assets/reference/batomons_balance24.json"); parser.add_argument("--output", default="app/src/main/assets/reference/batomon_sprite_signatures_balance24.json"); args = parser.parse_args()
    catalog = json.loads(Path(args.catalog).read_text()); entries = []
    for entry in catalog["entries"]:
        url = BASE + entry["spritePath"]
        try: derived = signature(url)
        except Exception as error: raise SystemExit(f"failed {entry['id']}: {error}")
        entries.append({"id": entry["id"], "name": entry["name"], "spritePath": entry["spritePath"], **derived})
    output = {"schema": 1, "reference": "Derived Batomon sprite signatures", "balance": catalog["balance"], "gameBuild": catalog["gameBuild"], "source": catalog["source"], "signatureMethod": "8x8 average-luminance hash + 16x16 average RGB + edge density; source PNG bytes are not retained", "entries": entries}
    Path(args.output).write_text(json.dumps(output, indent=2) + "\n")
    print(f"generated {len(entries)} signatures")

if __name__ == "__main__": main()
