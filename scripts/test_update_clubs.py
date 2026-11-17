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


if __name__ == "__main__":
    unittest.main()
