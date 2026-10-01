# GENSINGO

A minimal, offline-first field app for wild American ginseng stewards: one map, flat or in
real 3D, four buttons, and a research model that can only talk about places the phone itself
computed.

Native Android — Kotlin + Jetpack Compose, package `com.ginsengo.steward`.

**Your honey holes stay yours.** Tracks, finds and suggestions live in app-private storage;
cloud backup is off; there is no account or server. The research model is **off by
default**; when you turn it on with your own API key, a request carries the ~11 km grid cell
you are in, your state's rules and anonymised terrain numbers — never your GPS fix, your
track, or where any find is (`docs/PLAY_DATA_SAFETY.md`).

---

## The one screen

| Button | What it does |
|---|---|
| **Track** | Records where you walk, screen off, until you stop it (foreground service; no background-location permission). Battery policy: interval, batching and accuracy from measured battery, charging, screen and movement. |
| **Find** | Hold still while fixes average, add plants, prongs and a note, save. **Your finds are treated as true and accurate:** every one is drawn at full weight and learned from, whatever the GPS reported. The measured accuracy is kept with it as information, never as a filter. |
| **Suggest** | Places worth walking inside 10 miles. Always computed on the phone; annotated by Claude or Gemini (web-searched, sources kept only if retrieved in that call) when you allow it. Refreshes itself after you move 3 km. |
| **Layers** | Heatmaps, basemap, the learner's verdict, "Save 10 miles around me" for offline use, and research-model settings. |

Plus **one 2D/3D switch** (the same map, its camera, layers and basemap, on real terrain
about 3 km square in high definition) and **recentre**, which works in both.

### Heatmaps, in real time

| Layer | What it is |
|---|---|
| **Habitat** (green) | The published six-factor terrain model below, computed on the phone for the elevation tiles in view and redrawn when those tiles change. Switches to learned weights only after they beat the published ones on held-out finds. |
| **Where I've been** (blue) | GPU heatmap of your recorded track. |
| **My finds** (amber) | GPU heatmap of your finds, including patches logged in the older app, all at full weight. |
| **Contour lines** | From the same elevation tiles (a Kotlin port of maplibre-contour's marching squares), every 5-100 m chosen from the relief, every fifth stronger; the interval is shown in the status line. The same rule draws them in 3D. |
| **Creeks & streams** (blue lines) | Traced from the same elevation: pits filled, water routed downhill, and a line drawn where enough ground drains through (2 ha: small drainage; 20 ha: creek; 200 ha: stream). Works offline. Not surveyed: small ones may be dry, and a river flowing in from outside the loaded tiles is drawn smaller than it is. |

### Suggestions that cannot invent places

1. **Compute** — the radius scan runs the habitat model over 10 miles of cached elevation
   (~31 m cells) and picks separated high-scoring areas. Protected land is left out.
2. **Annotate** — the model sees candidates by ID with their numbers; its submission schema
   only admits those IDs. It can rank and explain; it cannot add or move a place.
3. **Witness** — citations survive only if the model's own search retrieved them in this
   call; coordinates and "legal to dig" sentences are stripped from its prose.
4. **Earn** — your finds (all of them, imported patches included) refit the model's weights,
   adopted only if they rank held-out finds better than the published weights, holding out whole blocks at the
   distance this terrain stops resembling itself (measured by the scan), with an exact
   sign-flip test. It needs finds in 5 separate spots before it can learn anything.

How this was built and tested — mutation testing, an independent critic, and runs on
Android — is in [`EINCOL_REPORT.md`](EINCOL_REPORT.md) Phase 7; the 314 candidate ideas it
started from are in [`docs/IDEA_CANDIDATES.md`](docs/IDEA_CANDIDATES.md).

### The habitat forecast

A terrain-derived suitability surface computed from elevation tiles at up to **3.86 m per
cell** (zoom 15 — measured as real detail, not upsampling). It combines five published
terrain indices:

- **Heat load** — McCune & Keon (2002) Eq. 3, folded about the NE–SW axis so north-east
  slopes read coolest. This is what encodes ginseng's preference for north and east faces.
- **Topographic position** — Weiss (2001), radius specified in **metres** and widened as you
  zoom out, so "position on the slope" means the same physical thing at every zoom.
- **Wetness** — Beven & Kirkby (1979) TWI over **multiple-flow** accumulation (Freeman),
  not D8: Kopecký & Čížková (2010) found multiple-flow routing roughly doubles TWI's
  correlation with actual soil moisture.
- **Slope angle** and **profile curvature** — for the "not too steep" band and for coves.

**It deliberately is not monotonic.** The obvious version ramps "lower = wetter = better"
and paints the creek bottoms as the best ground on the map. Extension guidance is explicit
that ginseng "will not grow in waterlogged soil … leaf-filled depressions … water flows",
and that flat, poorly drained sites will not support it. Wetness, slope and slope position
are therefore optimum **bands**, and the surface turns over at the wet end. That behaviour
is pinned by tests that fail if anyone makes it monotonic again.

Antialiasing adapts to the DEM-cell to output-pixel ratio: supersample and integrate when
one pixel covers many cells, interpolate when one cell covers many pixels.

### The map in 3D

**One map, two projections** ([`docs/blueprints/one-map.md`](docs/blueprints/one-map.md)).
The 3D switch shows the map you were looking at, from the same place, zoom and direction,
and switching back lands the flat map on wherever 3D left off. In 3D:

- **one finger drags the ground** (the point you touched stays under your finger, on the
  terrain, not on a flat plane); **two fingers** pinch to zoom, twist to rotate, slide up or
  down to tilt. When you let go the camera settles back onto the ground without the picture
  moving (the method MapLibre GL JS uses); drag past the edge and the terrain is rebuilt there;
- **the same layers**: the Layers sheet's habitat, creeks, contours, finds, suggestions and
  your track (drawn as a line) apply to both views;
- **the flat map's own basemap on the ground**: MapLibre renders the Dark map's roads and
  names for the square and they are drawn on the terrain, under your layers. It works
  offline where you used "Save 10 miles"; elsewhere the status line says so and the terrain
  is coloured by habitat (or by elevation with the habitat layer off).

About 3 km square at the best elevation the tiles offer: zoom 15, **3.9 m cells**, falling
back to zoom 14 or 13 where those are not cached. The whole budget goes on that one square:

- a 385 × 385 vertex mesh (about 300,000 triangles) standing on a solid base;
- a draped colour texture of up to 2048² texels (two per elevation cell), mipmapped with
  anisotropic filtering, so contours and creeks stay sharp at a glancing angle;
- **the habitat heatmap**: the exact scoring function and colour ramp of the 2D map, over a
  neutral relief (the first 3D view tinted each vertex every ~60 m and faded weak ground to
  black, which is why its heatmap did not show);
- relief shading lit from the north-west, contour lines (every 5-100 m, chosen from the
  relief; dark on light ground, light on dark), and creeks traced from the elevation;
- numbered suggestion markers, your finds, "You", a compass that faces north and refits the
  view when tapped, and a legend (with the basemap's attribution when it is draped).

It is a separate screen rather than a layer over the map: a GL surface over the map's
TextureView blacked out the screen (Phase 5). It renders only when the camera moves or new
colour arrives, builds terrain once per square, and survives the app going to the background.

Code ported from other projects (maplibre-contour's isolines, MapLibre GL JS's camera
re-anchoring) is listed with its licences in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md),
which also ships inside the app (Layers → Open-source notices).

**What it cannot see:** soil calcium — among the strongest published predictors of ginseng
ground (Burkhart: ~3,360 kg/ha marks promising sites) and not derivable from elevation. The
app says so on the panel and names the indicator species that reveal it instead.

## Build

Requires JDK 17+ and an Android SDK with platform 36 and build-tools 35.0.0.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # debug APK, installable (debug-signed), ~80 MB universal
./gradlew :app:assembleField        # shrunk sideload APK: R8, debug-signed, arm64, ~21 MB
                                    #   (-Pgensingo.abis=x86_64 for an emulator)
./gradlew :app:testDebugUnitTest    # JVM unit tests (incl. Robolectric migration tests)
./gradlew :app:connectedDebugAndroidTest   # on a device: SDK-on-ART and Keystore tests
python3 tools/mutate.py             # mutation harness: every claimed property, broken on purpose
./gradlew :app:bundleRelease        # Play AAB (needs signing, below)
```

The full-radius scan timing test is skipped unless you build its 4.7 MB fixture:
`python3 tools/build_scan_fixture.py /tmp/scan.bin 35.55 -82.95 12 6` then
`GENSINGO_SCAN_FIXTURE=/tmp/scan.bin ./gradlew :app:testDebugUnitTest --tests '*Timing*'`.

The research model needs your own key, entered in Layers → Research model. Nothing is
compiled into the APK.

### Release signing

The keystore never enters version control. Provide `keystore.properties` at the repo root:

```properties
storeFile=../gensingo-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

or the equivalent environment variables `GENSINGO_STORE_FILE`, `GENSINGO_STORE_PASSWORD`,
`GENSINGO_KEY_ALIAS`, `GENSINGO_KEY_PASSWORD`. Without either, the release build still
produces an unsigned artifact rather than failing.

## Data provenance

Every data surface in the app carries a tag, derived from where the value actually came
from rather than typed in beside it.

| Data | Tag | Source |
|---|---|---|
| Approved states, maturity minimums, season start | VERIFIED | U.S. Fish & Wildlife Service, American Ginseng export program |
| Companion plant photographs | VERIFIED | Wikimedia Commons, public domain / CC, attributed per photo |
| State outlines | VERIFIED | Public-domain generalised US state boundaries |
| Elevation grid | VERIFIED | NASA SRTM 90 m, sampled once at build time to a 0.1° grid |
| Protected-area boundaries | **APPROXIMATE** | Bounding polygons. They over-cover and may warn outside the real unit. |
| Habitat suitability score | RESEARCH-GRADE ESTIMATE | Six-factor terrain model, computed on the phone |
| Creeks and streams | RESEARCH-GRADE ESTIMATE | Traced on the phone from the elevation tiles; not surveyed hydrography |
| Your finds, patches and readings | VERIFIED (by you) | Entered by you and treated as true and accurate: learned from and drawn at full weight |

**State season closing dates.** FWS publishes only that harvest season *starts* in September
in all 19 approved states; it does not publish per-state end dates. GENSINGO therefore shows
a closing date only where one was actually sourced (5 of 20 jurisdictions) and otherwise
says *"confirm with \<agency\>"*. It will not show you a closing date it cannot stand behind.

### About the habitat model

The only habitat score the app shows is the six-factor terrain model above: expert weights,
not a fitted species distribution model, labelled as a research-grade estimate. Your
finds can replace those weights, but only after held-out finds prove the learned
ones rank better. The bundled 235-byte ONNX graph from the original PRD (which weights slope
and aspect at exactly zero; see `EINCOL_REPORT.md` Phase 1) is still in the assets and is not
used by any screen.

## Assets

Bundled under `app/src/main/assets/`, all authored once at build time by the committed
scripts in `tools/`. The app itself never fetches reference data at runtime.

```
models/habitat_model.onnx           235 B    linear baseline graph
geo/dem_grid.bin                    107 KB   SRTM 90 m sampled to 0.1°, 195×280
geo/state_boundaries.geojson         26 KB   19 approved states
geo/protected_areas.geojson         8.5 KB   21 areas, approximate
data/state_regulations.json          14 KB   19 states + Menominee Reservation
data/companion_plants.json          4.7 KB   7 species with per-photo attribution
images/companion_plants/*.jpg       1.6 MB   7 CC/PD photographs
tiles/gensingo_demo.mbtiles         100 KB   demo fixture only
```

## Disclaimers

American ginseng is protected under CITES Appendix II. Harvest is legal in 19 states and on
the Menominee Reservation, in season, on ground you have the right to dig.

This app assists identification and record-keeping. **It does not decide.** Verify maturity
and land status yourself, and check your state's current rules before you dig.
