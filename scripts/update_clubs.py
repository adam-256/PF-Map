#!/usr/bin/env python3
"""Build data/clubs.json: every Planet Fitness club in the U.S.

planetfitness.com sits behind a Cloudflare bot wall, so instead of scraping it
we use open datasets that already track the official club pages:

1. All the Places (CC0) - its `planet_fitness_us` spider crawls
   planetfitness.com/clubs weekly and publishes GeoJSON. That crawl is often
   partial (Cloudflare), so we union the last ~10 weeks of runs by club id,
   newest record winning, salvaging truncated files line by line.
2. OpenStreetMap (ODbL) via Overpass - every feature tagged with the Planet
   Fitness brand (Wikidata Q7201095) - adds clubs with no ATP club within 300 m.

A run that would shrink the published list by more than 15% is refused.

Output (stable, sorted, so diffs are meaningful):
  data/clubs.json       {version, generated, source, count, clubs:[...]}
  data/clubs-meta.json  {version, generated, source, count}   (tiny; the app polls this)

`version` is a content hash of the club list, so it changes only when clubs change.
Standard library only.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request

ATP_LATEST = "https://data.alltheplaces.xyz/runs/latest.json"
ATP_HISTORY = "https://data.alltheplaces.xyz/runs/history.json"
ATP_SPIDER = "planet_fitness_us"
OVERPASS_MIRRORS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
OVERPASS_QUERY = """[out:json][timeout:300];
area["ISO3166-1"="US"][admin_level=2]->.us;
nwr["brand:wikidata"="Q7201095"](area.us);
out center tags;"""

# There are ~2,700 U.S. clubs. Anything far below that means the sources are broken.
MIN_CLUBS = 1500
UA = "pf-map-data-updater/1.0 (+https://github.com/adam-256/pf-map)"

US_STATES = {
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN", "IA",
    "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM",
    "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA",
    "WV", "WI", "WY", "PR", "GU", "VI", "AS", "MP",
}


def fetch(url: str, data: bytes | None = None, timeout: int = 180) -> bytes:
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def clean(s) -> str:
    if s is None:
        return ""
    return re.sub(r"\s+", " ", str(s)).strip()


def slug_from_url(url: str) -> str:
    m = re.search(r"/gyms/([-\w]+)", url or "")
    return m.group(1) if m else ""


def normalize_phone(p: str) -> str:
    digits = re.sub(r"\D", "", p or "")
    if len(digits) == 11 and digits.startswith("1"):
        digits = digits[1:]
    if len(digits) == 10:
        return f"({digits[:3]}) {digits[3:6]}-{digits[6:]}"
    return clean(p)


def make_club(props: dict, lat: float, lng: float) -> dict | None:
    state = clean(props.get("addr:state")).upper()
    country = clean(props.get("addr:country")).upper()
    if country and country not in ("US", "USA", "PR"):
        return None
    if state and state not in US_STATES:
        return None
    if not (-180 <= lng <= -60 and 13 <= lat <= 72):  # U.S. incl. AK/HI/PR/GU
        return None
    street = clean(props.get("addr:street_address"))
    if not street:
        street = " ".join(x for x in (clean(props.get("addr:housenumber")), clean(props.get("addr:street"))) if x)
    website = clean(props.get("website") or props.get("contact:website"))
    name = clean(props.get("branch") or props.get("name") or "Planet Fitness")
    if not name.lower().startswith("planet fitness"):
        name = f"Planet Fitness {name}".strip()
    club_id = clean(props.get("ref")) or slug_from_url(website) or f"{lat:.5f},{lng:.5f}"
    return {
        "id": club_id,
        "name": name,
        "street": street,
        "city": clean(props.get("addr:city")),
        "state": state,
        "zip": clean(props.get("addr:postcode"))[:10],
        "lat": round(lat, 6),
        "lng": round(lng, 6),
        "phone": normalize_phone(clean(props.get("phone") or props.get("contact:phone"))),
        "url": website,
        "hours": clean(props.get("opening_hours")),
    }


def parse_atp(geojson: dict) -> list[dict]:
    clubs = []
    for f in geojson.get("features", []):
        g = f.get("geometry") or {}
        if g.get("type") != "Point":
            continue
        lng, lat = g["coordinates"][:2]
        c = make_club(f.get("properties") or {}, float(lat), float(lng))
        if c:
            clubs.append(c)
    return clubs


def parse_overpass(data: dict) -> list[dict]:
    clubs = []
    for e in data.get("elements", []):
        if "lat" in e:
            lat, lng = e["lat"], e["lon"]
        elif "center" in e:
            lat, lng = e["center"]["lat"], e["center"]["lon"]
        else:
            continue
        tags = dict(e.get("tags") or {})
        tags.setdefault("ref", f"osm-{e['type'][0]}{e['id']}")
        c = make_club(tags, float(lat), float(lng))
        if c:
            clubs.append(c)
    return clubs


def load_geojson(raw: bytes) -> dict:
    """ATP writes one feature per line; crawls killed mid-write leave truncated files.
    Fall back to salvaging every complete feature line."""
    try:
        return json.loads(raw)
    except ValueError:
        feats = []
        for line in raw.decode("utf-8", "replace").splitlines():
            line = line.strip().rstrip(",")
            if line.startswith("{") and '"Feature"' in line:
                try:
                    feats.append(json.loads(line))
                except ValueError:
                    pass
        return {"features": feats}


def from_atp(max_runs: int = 12, max_age_days: int = 70) -> list[tuple[str, list[dict]]]:
    """Every readable recent ATP run, newest first. The PF crawl is often partial, so callers merge them."""
    runs = []
    try:
        runs.append(json.loads(fetch(ATP_LATEST)))
    except Exception as e:  # noqa: BLE001
        print(f"ATP latest.json failed: {e}", file=sys.stderr)
    try:
        hist = json.loads(fetch(ATP_HISTORY))
        hist = sorted(hist, key=lambda r: r.get("run_id", ""), reverse=True)
        seen = {x.get("run_id") for x in runs}
        runs += [r for r in hist if r.get("run_id") not in seen]
    except Exception as e:  # noqa: BLE001
        print(f"ATP history.json failed: {e}", file=sys.stderr)

    cutoff = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=max_age_days)).strftime("%Y-%m-%d")
    results = []
    for run in runs[:max_runs]:
        run_id = run.get("run_id", "")
        if run_id[:10] < cutoff:
            break
        out = run.get("output_url", "")
        base = out.rsplit("/", 1)[0] if out else f"https://alltheplaces-data.openaddresses.io/runs/{run_id}"
        try:
            clubs = parse_atp(load_geojson(fetch(f"{base}/output/{ATP_SPIDER}.geojson")))
        except Exception as e:  # noqa: BLE001
            print(f"ATP run {run_id}: {e}", file=sys.stderr)
            continue
        print(f"ATP run {run_id}: {len(clubs)} clubs", file=sys.stderr)
        if clubs:
            results.append((run_id, clubs))
    return results


def from_osm() -> list[dict]:
    body = urllib.parse.urlencode({"data": OVERPASS_QUERY}).encode()
    for attempt in range(2):
        for url in OVERPASS_MIRRORS:
            try:
                clubs = parse_overpass(json.loads(fetch(url, data=body, timeout=360)))
                print(f"OpenStreetMap ({url}): {len(clubs)} clubs", file=sys.stderr)
                return clubs
            except Exception as e:  # noqa: BLE001
                print(f"Overpass {url} attempt {attempt + 1}: {e}", file=sys.stderr)
        time.sleep(20)
    return []


def merge_runs(runs: list[tuple[str, list[dict]]]) -> list[dict]:
    """Union of clubs across runs by id; the newest run's record wins."""
    merged: dict[str, dict] = {}
    for _, clubs in runs:  # newest first
        for c in clubs:
            merged.setdefault(c["id"], c)
    return list(merged.values())


def _dist_m(a: dict, b: dict) -> float:
    k = 111_320.0
    dx = (a["lng"] - b["lng"]) * k * math.cos(math.radians((a["lat"] + b["lat"]) / 2))
    dy = (a["lat"] - b["lat"]) * k
    return math.hypot(dx, dy)


def add_missing(base: list[dict], extra: list[dict], radius_m: float = 300) -> list[dict]:
    """Add clubs from `extra` that have no club in `base` within radius_m."""
    grid: dict[tuple[int, int], list[dict]] = {}
    cell = lambda c: (int(math.floor(c["lat"] * 100)), int(math.floor(c["lng"] * 100)))  # noqa: E731  ~1 km cells
    for c in base:
        grid.setdefault(cell(c), []).append(c)
    added = []
    for c in extra:
        cy, cx = cell(c)
        near = any(_dist_m(c, o) < radius_m for dy in (-1, 0, 1) for dx in (-1, 0, 1) for o in grid.get((cy + dy, cx + dx), []))
        if not near:
            added.append(c)
            grid.setdefault((cy, cx), []).append(c)
    return base + added


def dedupe(clubs: list[dict]) -> list[dict]:
    seen: dict[str, dict] = {}
    for c in clubs:
        key = c["id"]
        # merge duplicate ids, preferring the record with more filled fields
        if key in seen and sum(bool(v) for v in seen[key].values()) >= sum(bool(v) for v in c.values()):
            continue
        seen[key] = c
    return sorted(seen.values(), key=lambda c: (c["state"], c["city"], c["name"], c["id"]))


def build(clubs: list[dict], source: str) -> tuple[dict, dict]:
    clubs = dedupe(clubs)
    canonical = json.dumps(clubs, sort_keys=True, separators=(",", ":")).encode()
    version = hashlib.sha256(canonical).hexdigest()[:16]
    generated = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    meta = {"version": version, "generated": generated, "source": source, "count": len(clubs)}
    return {**meta, "clubs": clubs}, meta


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default="data")
    ap.add_argument("--source", choices=["auto", "atp", "osm"], default="auto")
    ap.add_argument("--force", action="store_true", help="publish even if the list shrank by >15%%")
    args = ap.parse_args()

    atp_runs = from_atp() if args.source in ("auto", "atp") else []
    clubs = merge_runs(atp_runs)
    parts = []
    if atp_runs:
        ids = [r for r, _ in atp_runs]
        parts.append(f"All the Places (CC0) crawls of planetfitness.com, {ids[-1][:10]} to {ids[0][:10]}: {len(clubs)}")
    if args.source in ("auto", "osm"):
        osm = from_osm()
        before = len(clubs)
        clubs = add_missing(clubs, osm)
        if osm:
            parts.append(f"OpenStreetMap (ODbL): +{len(clubs) - before}")
    source = "; ".join(parts)
    print(f"Merged: {len(clubs)} clubs ({source})", file=sys.stderr)

    previous = 0
    try:
        with open(f"{args.out_dir}/clubs-meta.json") as fh:
            previous = int(json.load(fh).get("count", 0))
    except (OSError, ValueError):
        pass
    if len(clubs) < MIN_CLUBS or (not args.force and len(clubs) < previous * 0.85):
        print(f"Refusing to publish: {len(clubs)} clubs (previous {previous}, minimum {MIN_CLUBS})", file=sys.stderr)
        return 1

    full, meta = build(clubs, source)
    os.makedirs(args.out_dir, exist_ok=True)
    # Keep `generated` stable when the club list didn't change, so commits only happen on real changes.
    try:
        with open(f"{args.out_dir}/clubs-meta.json") as fh:
            old = json.load(fh)
        if old.get("version") == meta["version"]:
            full["generated"] = meta["generated"] = old["generated"]
    except (OSError, ValueError):
        pass

    with open(f"{args.out_dir}/clubs.json", "w") as fh:
        json.dump(full, fh, separators=(",", ":"), ensure_ascii=False)
    with open(f"{args.out_dir}/clubs-meta.json", "w") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")
    print(json.dumps(meta), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
