# PF Map

An Android app that shows every Planet Fitness club in the U.S. on one interactive map.

**Download:** [`pf-map.apk`](https://github.com/adam-256/pf-map/releases/download/latest/pf-map.apk). Open it on your phone and allow installs from your browser when Android asks.

## Features

- **The map.** Every U.S. club is on a vector map from OpenFreeMap (OpenStreetMap data). Pins are grouped into clusters, and tapping a cluster zooms in on it.
- **Club details.** Tapping a pin opens a sheet with the club's address, phone number, hours, distance from you, a link to the club page and a share button.
- **Google rating and reviews.** The sheet shows the club's star rating, review count and up to 5 recent reviews, fetched from Google Places API (New). A button opens the full list in Google Maps.
- **Navigation.**
  - **Navigate with Google Maps** starts turn-by-turn directions right away.
  - **Navigate with another app…** opens Android's app picker, which lists Waze, OsmAnd, Organic Maps, HERE and any other installed navigation app.
- **Search.** You can search by city, state (code or full name), ZIP or street. Results are sorted nearest-first once your location is known.
- **My location and nearest club.** One button centers the map on you, and a second jumps straight to the closest club.
- **Club list updates on every startup.** The app shows its saved list right away, then downloads a small manifest of about 200 bytes. It fetches the full list only when the content hash has changed, and then tells you how many clubs were added or removed. Offline, it keeps using the saved list.

## Where the club data comes from

planetfitness.com/clubs is behind a Cloudflare bot wall, so the app doesn't scrape it. Instead, [`scripts/update_clubs.py`](scripts/update_clubs.py) builds `data/clubs.json` from open datasets that already track the official club pages:

1. **[All the Places](https://www.alltheplaces.xyz/)** (CC0) is the main source. Its `planet_fitness_us` spider crawls planetfitness.com/clubs every week. The script uses the newest complete run and goes back through older runs if the latest one is short.
2. **OpenStreetMap** (ODbL) is used only if All the Places fails. The script takes every U.S. feature tagged `brand:wikidata=Q7201095`.

The script refuses to publish a list that is suspiciously small (fewer than 1,800 clubs).

| Workflow | When it runs | What it does |
|---|---|---|
| **Update club list** (`update-clubs.yml`) | Daily, and on demand | Rebuilds `data/clubs.json`, commits it if it changed, and publishes it to the `clubs-data` release. The app polls that release. |
| **Build APK** (`build-apk.yml`) | Every push, and after each club-list update | Runs the unit tests, builds a signed release APK with the current snapshot bundled in, and publishes it to the `latest` release. |

On every startup the app checks these sources in order:

1. The `clubs-data` release asset.
2. `data/clubs.json` on the default branch.
3. All the Places directly, but only if the saved data is missing or more than 45 days old.

## Google reviews setup (optional)

Google only provides reviews through the Places API, and that needs an API key:

1. In Google Cloud Console, enable **Places API (New)** and create an API key. Restrict the key to that API, and ideally to the app's package `com.pfmap.app` and its signing certificate.
2. Add the key in one of these places:
   - **On the phone:** paste it under ⚙ Settings → *Places API key*. The key is stored only on that device.
   - **At build time:** add a repository secret named `PLACES_API_KEY`. The key is then built into the APK. The APK is public, so restrict the key.

Without a key, the **Open reviews in Google Maps** button still takes you to the club's reviews.

## Building locally

You need JDK 17 or newer and the Android SDK (platform 36):

```bash
./gradlew testDebugUnitTest assembleRelease      # → app/build/outputs/apk/release/app-release.apk
python3 scripts/update_clubs.py --out-dir data   # refresh the bundled club list
python3 -m unittest discover -s scripts          # data-script tests
```

### Signing

APKs are signed with the committed key `app/pfmap-shared.keystore`, so every CI build installs over the previous one without having to uninstall first. To sign with a private key instead, add these repository secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Switching keys means you have to uninstall the app once.

## Tech

Kotlin, Jetpack Compose with Material 3, and MapLibre Native Android 11 (GeoJSON clustering). It uses kotlinx.serialization and has no other dependencies. Minimum Android version is 7.0 (API 24).

Not affiliated with or endorsed by Planet Fitness. Map © OpenFreeMap © OpenStreetMap contributors.
