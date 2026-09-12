# GENSINGO

A private, offline-capable field companion for wild American ginseng stewards.

Native Android — Kotlin + Jetpack Compose (Material 3), package `com.ginsengo.steward`.

It is built around the digger's actual workflow: read habitat, identify plants, verify legal
maturity, harvest ethically, protect patches. It is **not** a navigation app, not a plant-ID
(computer vision) app, not a marketplace, not a law-enforcement tool, and not a scientific
instrument.

**Your honey holes stay yours.** Patch locations, photos and readings live in this app's
private storage on your phone. There is no account, no server, no analytics, and Android's
cloud backup is switched off for this app. The only network request GENSINGO makes is for
map tiles, and it carries no patch data.

---

## Workflows

| | |
|---|---|
| **Read Habitat** | Compass slope aspect, position on slope, tree association, companion-plant photo grid, soil check → a field verdict. Optional model analysis on top. |
| **Verify Maturity** | Prongs → berries → stem scars → verdict, against your state's actual minimum. Stewardship reminders either way. |
| **Log Patch** | GPS (with manual override), in-app camera, plant count, notes, harvest record. Saved to Room. |
| **My Patches** | Proximity-grouped, filterable, coordinates blurred until you tap them. |
| **Stewardship Guide** | Per-state rules, land status, stewardship practices, companion gallery, plant aging, look-alikes. |

## Map layers

Free movement throughout — pan, zoom, rotate and tilt.

| Layer | What it is |
|---|---|
| **Dark / Satellite / Topo** | OpenFreeMap vector dark, or USGS imagery and topo (public domain, to z16) |
| **Height map** | GPU colour relief over AWS Terrarium elevation tiles, ramp tuned to the Appalachian band, opacity slider |
| **Hillshade** | GPU relief shading from the same elevation source |
| **Pitched relief** | 55° camera tilt with deepened shading. **Not a 3D mesh** — MapLibre Android exposes no terrain API; the app says so where you toggle it |
| **Habitat heatmap** | The ginseng forecast, below |

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

**What it cannot see:** soil calcium — among the strongest published predictors of ginseng
ground (Burkhart: ~3,360 kg/ha marks promising sites) and not derivable from elevation. The
app says so on the panel and names the indicator species that reveal it instead.

## Build

Requires JDK 17+ and an Android SDK with platform 34 and build-tools 34.0.0.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:bundleRelease        # Play AAB (needs signing, below)
```

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

The bundled graph is a 235-byte linear baseline — `sigmoid(W·x + b)` — not a trained or
validated ecological model. **It weights slope angle and slope aspect at exactly zero**, so
the analysis cannot respond to them at all. The app says so on screen, prints the full
weight vector in Settings, and shows what share of each score came from your own checklist
answers rather than from a measurement. On a hillside, the field reading is the one to
trust. See [`EINCOL_REPORT.md`](EINCOL_REPORT.md).

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
