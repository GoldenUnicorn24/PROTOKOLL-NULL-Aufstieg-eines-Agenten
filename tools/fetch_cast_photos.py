#!/usr/bin/env python3
"""Download only CC-licensed actor portraits from Wikimedia Commons.

Usage: python3 tools/fetch_cast_photos.py
Uses the Python standard library. Stores credited JPEGs for offline Android use.
"""
from __future__ import annotations
import hashlib
import html
import json
import re
import sys
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "assets" / "CAST_IMAGES.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "cast"
CREDITS = ROOT / "assets" / "CAST_IMAGE_CREDITS.json"
USER_AGENT = "ProtokollNullFanGame/0.1 (+https://github.com/GoldenUnicorn24/PROTOKOLL-NULL-Aufstieg-eines-Agenten)"
ALLOWED = ("CC-BY-", "CC-BY-SA-")

def get(url: str, limit: int = 3_000_000) -> bytes:
    request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
    for attempt in range(3):
        try:
            with urlopen(request, timeout=30) as response:
                data = response.read(limit + 1)
                if len(data) > limit:
                    raise ValueError("Image too large")
                return data
        except HTTPError as exc:
            if exc.code not in (429, 500, 502, 503) or attempt == 2:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError("Network retries exhausted")

def plain(value: str) -> str:
    return html.unescape(re.sub(r"<[^>]+>", "", value or "")).strip()

def metadata_field(metadata: dict, key: str) -> str:
    return metadata.get(key, {}).get("value", "")

def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    imported, failures = [], []
    for row in json.loads(SOURCES.read_text(encoding="utf-8")):
        try:
            params = urlencode({
                "action": "query", "format": "json", "formatversion": 2,
                "titles": "File:" + row["fileTitle"], "prop": "imageinfo",
                "iiprop": "url|extmetadata", "iiurlwidth": 640
            })
            response = json.loads(get("https://commons.wikimedia.org/w/api.php?" + params))
            page = response["query"]["pages"][0]
            if page.get("missing") or not page.get("imageinfo"):
                raise ValueError("Commons file missing")
            info = page["imageinfo"][0]
            meta = info.get("extmetadata", {})
            license_name = plain(metadata_field(meta, "LicenseShortName"))
            if not license_name.upper().replace(" ", "-").startswith(ALLOWED):
                raise ValueError("License not allowed: " + license_name)
            data = get(info.get("thumburl") or info["url"])
            if not data.startswith(bytes.fromhex("ffd8ff")):
                raise ValueError("Unexpected image format; JPEG required")
            path = OUT / (row["id"] + ".jpg")
            path.write_bytes(data)
            imported.append({
                "id": row["id"],
                "fileTitle": row["fileTitle"],
                "path": str(path.relative_to(ROOT)),
                "sourcePage": row["page"],
                "author": plain(metadata_field(meta, "Artist")),
                "attribution": plain(metadata_field(meta, "Attribution")) or plain(metadata_field(meta, "Artist")),
                "license": license_name,
                "licenseUrl": metadata_field(meta, "LicenseUrl"),
                "changes": "Wikimedia thumbnail, 640 px",
                "sha256": hashlib.sha256(data).hexdigest()
            })
            print("OK", row["id"], license_name, str(len(data)) + " bytes")
        except Exception as exc:
            print("SKIP", row["id"], str(exc), file=sys.stderr)
            failures.append({"id": row["id"], "reason": str(exc)})
        time.sleep(0.2)
    CREDITS.write_text(json.dumps({"images": imported, "failed": failures}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Imported", len(imported), "out of", len(imported) + len(failures))
    return 0 if imported else 1

if __name__ == "__main__":
    sys.exit(main())
