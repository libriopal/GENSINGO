# GENSINGO

A minimal, offline-first field app for wild American ginseng stewards: one map, four
buttons, a real 3D terrain view, and a research model that can only talk about places the
phone itself computed.

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
| **Find** | Hold still while fixes average, confirm three identification checks, save. A find is **verified** only if the averaged fix is ≤ 20 m, fresh, and all three checks hold; otherwise it is saved unverified and never learned from. |
| **Suggest** | Places worth walking inside 10 miles. Always computed on the phone; annotated by Claude or Gemini (web-searched, sources kept only if retrieved in that call) when you allow it. Refreshes itself after you move 3 km. |
| **Layers** | Heatmaps, basemap, the learner's verdict, "Save 10 miles around me" for offline use, and research-model settings. |

Plus a **3D** toggle (a real terrain mesh around you, coloured by the habitat surface) and
recentre.

### Heatmaps, in real time

| Layer | What it is |
|---|---|
| **Habitat** (green) | The published six-factor terrain model below, computed per viewport on the phone. Switches to learned weights only after they beat the published ones on held-out finds. |
| **Where I've been** (blue) | GPU heatmap of your recorded track. |
| **My finds** (amber) | GPU heatmap of your finds; verified ones weigh most. |

### Suggestions that cannot invent places

1. **Compute** — the radius scan runs the habitat model over 10 miles of cached elevation
   (~31 m cells) and picks separated high-scoring areas. Protected land is left out.
2. **Annotate** — the model sees candidates by ID with their numbers; its submission schema
   only admits those IDs. It can rank and explain; it cannot add or move a place.
3. **Witness** — citations survive only if the model's own search retrieved them in this
   call; coordinates and "legal to dig" sentences are stripped from its prose.
4. **Earn** — your verified finds refit the model's weights, adopted only if they rank
   held-out finds better than the published weights, holding out whole blocks at the
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

### The 3D view

A triangle mesh built from the elevation tiles around you, drawn in its own GL surface and
coloured by the habitat surface (or by elevation), with your position, finds and suggestions
projected onto it. It is a separate screen rather than a layer over the map: a GL surface
over the map's TextureView blacked out the screen (Phase 5). It renders only when a gesture
moves the camera.

**What it cannot see:** soil calcium — among the strongest published predictors of ginseng
ground (Burkhart: ~3,360 kg/ha marks promising sites) and not derivable from elevation. The
app says so on the panel and names the indicator species that reveal it instead.

## Build

Requires JDK 17+ and an Android SDK with platform 36 and build-tools 35.0.0.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # debug APK, installable (debug-signed)
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
| Habitat suitability score | RESEARCH-GRADE ESTIMATE | 235-byte linear baseline graph |
| Your patches and readings | PROTOTYPE | Entered by you, unverified |

**State season closing dates.** FWS publishes only that harvest season *starts* in September
in all 19 approved states; it does not publish per-state end dates. GENSINGO therefore shows
a closing date only where one was actually sourced (5 of 20 jurisdictions) and otherwise
says *"confirm with \<agency\>"*. It will not show you a closing date it cannot stand behind.

### About the habitat model

The only habitat score the app shows is the six-factor terrain model above: expert weights,
not a fitted species distribution model, labelled as a research-grade estimate. Your
verified finds can replace those weights, but only after held-out finds prove the learned
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
