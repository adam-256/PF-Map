"""Offline tests for update_clubs.py (python3 -m unittest discover scripts)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import update_clubs as u  # noqa: E402


class UpdateClubsTest(unittest.TestCase):
    def test_parse_atp(self):
        clubs = u.parse_atp({"features": [
            {"geometry": {"type": "Point", "coordinates": [-73.98, 40.74]},
             "properties": {"ref": "1", "name": "Planet Fitness", "branch": "Midtown", "addr:state": "NY",
                            "addr:city": "New York", "phone": "12125550100", "addr:country": "US"}},
            {"geometry": {"type": "Point", "coordinates": [-79.38, 43.65]},
             "properties": {"ref": "2", "addr:state": "ON", "addr:country": "CA"}},
            {"geometry": None, "properties": {"ref": "3"}},
        ]})
        self.assertEqual(len(clubs), 1)
        self.assertEqual(clubs[0]["name"], "Planet Fitness Midtown")
        self.assertEqual(clubs[0]["phone"], "(212) 555-0100")

    def test_parse_overpass(self):
        clubs = u.parse_overpass({"elements": [
            {"type": "node", "id": 5, "lat": 41.88, "lon": -87.62, "tags": {"name": "Planet Fitness", "addr:state": "IL"}},
            {"type": "way", "id": 9, "center": {"lat": 33.75, "lon": -84.39}, "tags": {"name": "Planet Fitness"}},
            {"type": "relation", "id": 1, "tags": {}},
        ]})
        self.assertEqual([c["id"] for c in clubs], ["osm-n5", "osm-w9"])

    def test_build_is_stable_and_dedupes(self):
        a = {"id": "x", "name": "Planet Fitness A", "street": "", "city": "B", "state": "NY", "zip": "", "lat": 1, "lng": 2,
             "phone": "", "url": "", "hours": ""}
        richer = dict(a, street="1 Main St")
        full1, meta1 = u.build([a, richer], "s")
        full2, meta2 = u.build([richer], "s")
        self.assertEqual(full1["count"], 1)
        self.assertEqual(full1["clubs"][0]["street"], "1 Main St")
        self.assertEqual(meta1["version"], meta2["version"])

    def test_truncated_geojson_is_salvaged(self):
        raw = (b'{"type":"FeatureCollection","features":[\n'
               b'{"type":"Feature","geometry":{"type":"Point","coordinates":[-73.9,40.7]},"properties":{"ref":"a","addr:state":"NY"}},\n'
               b'{"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.8]},"properties":{"ref":"b","addr:state":"NY"}},\n'
               b'{"type":"Feature","geometry":{"type":"Po')
        self.assertEqual([c["id"] for c in u.parse_atp(u.load_geojson(raw))], ["a", "b"])

    def test_merge_runs_newest_wins(self):
        new = [{"id": "a", "v": 2}]
        old = [{"id": "a", "v": 1}, {"id": "b", "v": 1}]
        merged = {c["id"]: c["v"] for c in u.merge_runs([("2026-11", new), ("2026-10", old)])}
        self.assertEqual(merged, {"a": 2, "b": 1})

    def test_add_missing_skips_nearby(self):
        base = [{"id": "atp", "lat": 40.0, "lng": -75.0}]
        osm = [{"id": "near", "lat": 40.001, "lng": -75.001},  # ~140 m away -> duplicate
               {"id": "far", "lat": 40.05, "lng": -75.0}]      # ~5.5 km away -> new club
        self.assertEqual([c["id"] for c in u.add_missing(base, osm)], ["atp", "far"])


if __name__ == "__main__":
    unittest.main()
