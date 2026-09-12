# EINCOL execution record — GENSINGO Android build

Protocol: `EINCOL.md`, run against the goal "build a Google-Play-ready native Android APK
of GENSINGO per PRD_CC.md".

This file is the deliverable the protocol asks for in §5.5: what was found, what broke,
what the evaluator caught that the author did not, and **what is still open**.

---

## Step 1 — Cue, and what was actually measured

**Cue (one specific question):**
> In this build, which claim does the PRD or the repository treat as established while
> nothing has ever checked it?

Everything below is a measurement, not an impression. Nothing here is quoted from the PRD's
description of the material.

| Measured | Value |
|---|---|
| Files in `GENSINGO-gap-closure-bundle.zip` | **12** |
| Files in `GENSINGO-main.zip` | **112** |
| `diff -rq` main.zip vs repo HEAD | **empty — byte-identical** |
| React / JSX / TS / `package.json` / `vite*` in either zip | **0** |
| `PRD.md` in either zip | **0** |
| `.geojson` files in either zip | **0** |
| `state_regulations.json` / `companion_plants.json` | **0** |
| DEM grid in either zip | **0** |
| ONNX model | 1, exactly **235 bytes** |
| MBTiles | 1, 102 400 bytes, SQLite 3 |
| Kotlin files in repo / LOC | **96 / 4 210** |
| `app/src/main/res/` | **absent** |
| `app/src/main/assets/` | **absent** |
| `gradlew`, `gradle/wrapper/` | **absent** |
| `APK/INSTALL.V1.0.APK` (which `APK/MANIFEST.md` says the directory contains) | **absent from the working tree AND from all of git history** |
| ONNX weights non-zero | **3 of 7** (altitude, canopyCover, moistureLevel) |
| ONNX weights exactly zero | **4 of 7** (latitude, longitude, slopeAngle, aspect) |

### Finding 0 — the PRD's source contract is counterfactual

PRD §2 states that `GENSINGO-main.zip` contains "Current React/MapLibre web app + PRD.md",
and that the gap bundle contains "MBTiles/GeoJSON boundary data … state-regulation and
companion-plant reference data".

None of that is in the zips. `GENSINGO-main.zip` is a byte-identical snapshot of this
repository — 112 Kotlin/Android files, no JavaScript at all. The gap bundle contains the
ONNX graph, a demo MBTiles, a Python FastAPI backend, and four markdown files.

Consequence: every asset PRD §2.2 instructs the implementer to "extract" — except the ONNX
model and the MBTiles — had to be **authored**, and §8.4/§8.5 already sanction exactly that
for photos and the DEM. The authoring scripts are committed under `tools/`.

---

## Step 2 — Distribution, not an answer

| p | Candidate |
|---|---|
| 0.30 | The repo doesn't compile — no `res/`, no wrapper, no assets. |
| 0.20 | The PRD's §2 source contract is counterfactual (Finding 0). |
| 0.15 | Identity mismatch: PRD wants greenfield `com.ginsengo.steward`; repo is `com.discomplemented.ginseng`, a *different product* (tracking + backend sync + landowner permissions — the RadarNav lineage §12 excludes). |
| 0.12 | The "APK" is a document, not a binary: `APK/MANIFEST.md` asserts a file that never existed. |
| 0.10 | Provenance labels are vocabulary — "VERIFIED" strings with nothing computing them. |
| **0.08** | **The graph weights slopeAngle and aspect at exactly 0.0, so the DEM slope/aspect pipeline PRD §8.1 mandates cannot move the score by one bit.** |
| **0.05** | **The model's two dominant inputs are themselves defined by §8.1 as derivations of the user's own checklist, so the "research-grade estimate" is largely a re-encoding of what the digger just typed.** |

Row one is true and closes nothing: "add a `res/` folder" is a chore, not a finding.

---

## Step 3 — Working the tail

Rows 0.08 and 0.05 compose into one finding. Decoding the 235-byte graph's `raw_data`:

```
score = sigmoid( -0.0005·altitude + 1.2·canopyCover + 1.0·moistureLevel − 0.5 )
```

Measured sensitivity across each feature's full plausible field range:

| Feature | Range swept | Δ score |
|---|---|---|
| latitude | 34 → 42 | **0.000000** |
| longitude | −88 → −75 | **0.000000** |
| **slopeAngle** | 0° → 45° | **0.000000** |
| **aspect** | 0° → 359° | **0.000000** |
| altitude | 150 → 1800 m | −0.202759 |
| canopyCover | 0 → 1 | +0.285392 |
| moistureLevel | 0 → 1 | +0.239808 |

PRD Workflow A is built around slope orientation ("north/east = ideal") and position on
slope. PRD §8.1 mandates computing slope and aspect from a bundled DEM by finite
differences, and §8.5 mandates a 2–3 MB asset to support it. **The model is provably blind
to both.**

Meanwhile §8.1 defines the two dominant inputs as:
- canopy cover ← slope/aspect heuristic **+ companion-plant density** (user-confirmed sightings)
- moisture ← **slope position** (user-selected) **+ soil check** (user toggle)

So the digger answers a checklist, and the model hands back a number mostly computed from
those same answers — presented as a separate corroborating reading.

By the §3 sovereignty classifier this is a two-✗ row: the habitat score is pinned neither
**in time** (it did not exist before the input it constrains) nor **against a witness** (no
second independent thing computes it). It is the **self-witness** failure mode from §4.

---

## Step 4 — Falsifying my own claim, in writing

**First formulation:** *"The DEM grid PRD §8.5 mandates is dead weight — the model ignores
everything derived from it."*

**This is FALSE, and my own sensitivity table refutes it.** Altitude carries a non-zero
weight (−0.0005) and moves the score by 0.203 across the Appalachian elevation range. The
DEM is load-bearing; it just isn't load-bearing for the reason §8.1 gives.

**Corrected claim (the one that shipped):**
> The DEM is load-bearing for **altitude only**. The finite-difference slope/aspect
> derivation in §8.1 is dead code with respect to model output. Separately, the model's two
> largest-weight inputs are derived from the user's own checklist, so the habitat score and
> the field reading are **not independent signals** and must not be presented as though they
> corroborate each other.

Both versions are kept here because the correction is the evidence that the process ran.

A second consequence followed: since altitude is the *only* non-user-derived input with a
non-zero weight, it is the one place a genuine measurement is worth having. GPS altitude
(`Location.getAltitude()`) is a real reading at the digger's actual position; the bundled
grid is an ~11 km cell average. So the app prefers **GPS altitude**, with the DEM as
fallback — inverting what the PRD assumed.

---

## Step 5 — Evaluators (highest rung available)

### Rung 2 — execution against reality, on the prior state

Installed the Android SDK and ran the real build against the repository as inherited.

```
> Task :app:processDebugResources FAILED
ERROR: AndroidManifest.xml:23: AAPT: error: resource mipmap/ic_launcher not found.
ERROR: AndroidManifest.xml:23: AAPT: error: resource string/app_name not found.
ERROR: AndroidManifest.xml:23: AAPT: error: resource style/Theme.GinsengScout not found.
BUILD FAILED in 2m 43s
```
(full log: `docs/baseline_build_failure.log`)

**What the evaluator found that I did not.** I predicted duplicate class collisions from
the pairs of same-named files (`GinsengPatchEntity.kt` in two places, `AppDatabase.kt`
alongside `GinsengDatabase.kt`). Wrong — they sit in different packages and are legal. And
the Kotlin **compiled clean**: KSP ran, Room generated, dex merged, native libs merged. The
failure is narrow and late. The prior tree was *unbuilt* code, not *fake* code — a
materially fairer description than the one I started with, and I only got it by running the
build instead of reading the tree.

It also settles Finding 0's sharpest corollary: a repository shipping `APK/MANIFEST.md`
headed **"Status: Production Ready"**, describing installation of `INSTALL.V1.0.APK`, has
never once produced an APK. The claim was pinned by neither time nor witness. Nobody had
run it.

### Rung 1 — a program that breaks the thing and checks whether anyone notices

`HabitatModelTest.negativeControl_inertFeaturesCannotMoveTheScore` sweeps slope 0–60° and
aspect around the full compass (13 × 24 = 312 combinations) against the **real shipped
graph** and asserts the score never moves, at tolerance `0.0`.

Per EINCOL §2, a check that has only ever been seen to pass is decoration, and §4 warns
against the **vacuous control** — a perturbation of something the system correctly ignores.
So the negative control is paired with
`positiveControl_activeFeaturesDoMoveTheScore`, which proves the same harness *can* detect a
score change (canopy, moisture and altitude all move it). Without the positive control,
"nothing moved" would be evidence about the harness, not about the model.

**Seen to fail before being believed.** The graph's `raw_data` was patched to move the
slopeAngle weight from `0.0` to `1.0` — a perturbation of a value the model demonstrably
*reads* (it is in the input vector and multiplied by that weight), so it is not a vacuous
control. The mutant was dropped into the test resource and the suite re-run:

```
MUTANT (slopeAngle weight 0.0 -> 1.0)     7 tests, 3 failed
  [FAIL] negativeControl_inertFeaturesCannotMoveTheScore
         slope=0 aspect=0 changed a score the graph weights at zero
         expected:<0.9999999984730599> but was:<0.5744425191566552>
  [FAIL] parsesTheShippedWeights            expected:<0.0> but was:<1.0>
  [FAIL] mostOfTheScoreComesFromTheUsersOwnAnswers
         expected the checklist-derived share to dominate, got 0.0514
  [pass] positiveControl_activeFeaturesDoMoveTheScore
  [pass] scoreStaysInRange, rejectsAGraphWithNoUsableWeights, contributionsSumToTheLogit

REAL GRAPH (test resource restored, cmp-verified byte-identical to the shipped asset)
  25 tests, 0 failures
```

Note which tests did **not** fail on the mutant. The positive control still passed, because
canopy and moisture still move the score — so "nothing moved" in the negative control is
evidence about the model, not a globally dead harness. That is the difference between an
instrument and a decoration.

### Rung 2 — execution against reality, on the delivered state

```
./gradlew :app:assembleDebug :app:testDebugUnitTest   BUILD SUCCESSFUL   25 tests, 0 failures
./gradlew :app:bundleRelease :app:assembleRelease     BUILD SUCCESSFUL
apksigner verify app-release.apk                      Verifies (v2 scheme, 1 signer)
aapt dump badging                                     com.ginsengo.steward, sdk 26, target 34
                                                      native-code: arm64-v8a armeabi-v7a x86 x86_64
```

Artifacts: `app-release.aab` 57.6 MB (Play splits this per-device), `app-release.apk`
133 MB universal, `app-debug.apk` 140 MB. The bulk is the ONNX Runtime and MapLibre native
libraries across four ABIs; a per-device split is roughly a quarter of that. The AAB is the
Play deliverable for exactly this reason.

**What this evaluator caught that I did not, second time.** I wrote a data-safety document
asserting the app requests exactly five permissions. The merged release manifest has
**seven**: MapLibre contributes `ACCESS_WIFI_STATE` and AndroidX Core auto-generates a
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. I had checked my own source manifest — the
subject as its own witness. Reading the *merged* manifest is the second implementation that
disagreed. `docs/PLAY_DATA_SAFETY.md` now documents all seven and enumerates every component
in the shipped APK. The related "no tracking SDKs" claim was then tested rather than
asserted: grepping the release APK for `telemetry|analytics` returns **0 matches**, and the
only manifest components are five AndroidX framework ones.

### Witness pin built into the product

`HabitatEngine` runs **both** onnxruntime-android and the pure-Kotlin Gemm+Sigmoid on every
analysis and reports the agreement delta in Settings. PRD §8.1 offers the Kotlin path only
as a *fallback*; running it alongside converts a number nobody checked into a number a
second independent implementation agrees with — the witness pin the §3 classifier asks for.

The Kotlin path reads its weights **out of the same `.onnx` bytes** via a minimal protobuf
reader (`OnnxGraphWeights`), rather than hardcoding the seven floats. Hardcoding would
create two sources of truth that diverge silently the moment the graph is replaced with a
trained model — and the divergence would surface only as a slightly different habitat score.

---

## What was built to close the finding

1. **`ModelBreakdown`** in the habitat screen. Every analysis shows each feature's signed
   contribution, tagged `measured` or `from your answers`, plus the line: *"N% of this score
   comes from answers you just gave. The model is mostly restating your own checklist, not
   checking it."*
2. **Inert features are named in the UI**, not hidden: *"Ignored entirely by this model:
   latitude, longitude, slope angle, slope aspect."* Settings prints the full weight vector
   with zeros marked `0 · ignored`.
3. **The field checklist is kept structurally separate from the model** (`HabitatChecklist`
   is plain inspectable rules). If the checklist verdict were derived from the model, the
   two things a digger is standing there looking at — which way the hill faces, and where on
   it they are — would silently stop affecting the answer.
4. **GPS altitude preferred over the DEM** (see Step 4).

### A second honesty fix the protocol forced, in the regulation data

PRD §6.2's example record carries `"season_end": "Nov 30"` with `"source": "FWS 2025"`.
Checking the source: **FWS does not publish per-state end dates.** Its ginseng page states
only that *"in all 19 States, ginseng harvest season starts in September."*

Filling `season_end` for all 19 states from "FWS 2025" would be a fabricated measurement
wearing an authoritative label — and here it is also a legal hazard: a digger shown a
confident wrong closing date can harvest out of season. It would also have been *wrong*:
NC, VA, TN and KY run to **Dec 31**, not Nov 30.

So the schema carries per-field verification flags. 5 of 20 jurisdictions have a sourced end
date; the other 15 render as *"confirm with \<agency\>"* and never as a number.
`SeasonTest.presentButUnverifiedEndDateIsStillWithheld` pins that the flag governs, not the
presence of a string.

**Sourced and shipped as VERIFIED:** the 19 states + Menominee Reservation; season starts in
September in all 19; 18 states require 5 years / 3 prongs, **Illinois requires 10 years / 4
prongs**; stem scars read 4 ≈ 5 years and 9 ≈ 10 years.

---

## Open — unfixed, and stated as open

1. **Protected-area boundaries are approximate bounding boxes**, not real unit geometry. The
   NPS boundary service returned 404 during the build. They over-cover, so they can warn
   outside the real unit. Mitigated, not fixed: every such feature carries
   `provenance=approximate`, the UI says so, and PRD §8.6's warn-never-block rule means an
   over-covering box cannot forbid a legal harvest. Replacing them with real NPS/USFS
   geometry is outstanding.
2. **14 of 19 state season end dates are unsourced** and render as "confirm with \<agency\>".
   This is honest, not complete. Closing it means sourcing 14 state agencies individually.
3. **Fonts.** PRD §5.2 specifies Plus Jakarta Sans and Inter. Neither is bundled — shipping
   them means committing licensed binaries, and downloadable-fonts needs Play Services at
   runtime, which breaks offline-first exactly where the app is used. The type *scale,
   weights and hierarchy* are honoured against the platform grotesk. One-file change in
   `Theme.kt` to swap the real faces in.
4. **R8 minification is off for release.** Room, kotlinx-serialization and the ONNX JNI
   bridge all resolve reflectively, and a shrink misconfiguration fails at *runtime*, not
   build time. A verifiable APK outranks a smaller one for v1.
5. **No on-device run.** The APK builds, is signed, and the JVM unit tests pass, but this
   environment has no emulator or physical device, so **no workflow has been exercised on
   real hardware**. MapLibre tile rendering, CameraX capture, the compass feed and the
   onnxruntime JNI path in particular are unverified at runtime — the Kotlin inference path
   is tested, the native one is not. This is the single largest gap in the deliverable.
8. **Universal APK is 133 MB** because ONNX Runtime and MapLibre ship native libraries for
   four ABIs. The AAB (57.6 MB, split per-device by Play) is the intended delivery vehicle.
   If a universal APK is ever needed, add ABI splits or drop the x86 variants.
9. **The release was signed with a throwaway keystore** generated during verification and
   discarded. It exists only to prove the signing configuration works. A real release must
   use the publisher's own keystore — see README.
6. **Phase 3 not built** (offline tile pre-download, opt-in Firebase sync). Settings states
   the app's actual posture rather than implying the capability.
7. **The bundled MBTiles is a 100 KB demo fixture**, not real topographic coverage — the gap
   bundle's own `PROVENANCE.md` says so. The map uses OpenFreeMap online tiles.

---

## Named failure modes — self-check (EINCOL §4)

- **Vocabulary proxy** — avoided: the model finding is a measured Δ-score table, not a grep
  for "aspect". `Provenance` is an enum derived from where a value came from, never a string
  typed next to the value it describes.
- **Self-witness** — this *was* the finding, and the fix (two engines + contribution
  breakdown) is aimed squarely at it.
- **Vacuous control** — explicitly guarded by the paired positive control; the perturbed
  feature is asserted to be one the model reads.
- **Mode answer** — row one ("it doesn't compile") was fixed as a chore, not reported as a
  finding.
- **Dropped connection vs refusal** — distinguished: the NPS 404 is recorded as a service
  failure, and the resulting approximate polygons are labelled, not passed off as real.
- **Widening the scope** — the greenfield rewrite is what PRD §12/§13.1 asks for, not a
  redesign prompted by the finding.

---

# Phase 2 — satellite, terrain, and the habitat forecast heatmap

Second pass of the protocol, against the goal "add satellite view, 3D height map, free
movement, and a terrain-based ginseng forecast heatmap accurate to ~3 m, rendered on the
GPU with adaptive antialiasing and adaptive scope by zoom."

## Step 1 — What the material actually is

**The "4D engine" exists and does not render.** The phrase points at the deleted
`mapping/layers/*` tree (`MicroTerrainRendererImpl`, `LODTransitionManager`,
`CoordinateTransformer`, `NioGeoTiffParser`) plus `DESIGN_LOD_HYBRID.md`, which specifies a
"Managed Overlay Injection" pattern: a high-resolution mesh synchronised to MapLibre's
projection matrix. Recovered from git history and measured:

| File | Lines | Contains GL? |
|---|---|---|
| `MicroTerrainRendererImpl.kt` | 182 | **no** |
| `NioGeoTiffParser.kt` | 236 | no (a real TIFF/IFD parser) |
| `LODTransitionManagerImpl.kt` | 49 | no |
| `CoordinateTransformer.kt` | 32 | no |

`MicroTerrainRendererImpl.onDraw()` builds a 128×128 vertex grid, runs a fade timer, and
then ends at the comment *"In a real implementation, this would use OpenGL ES to draw the
mesh."* There is no shader, no GL program, no `glDrawElements` — **zero GL calls in the
entire tree.** So "rewire the 4D engine into a heatmap renderer" has no renderer to rewire.
What is genuinely reusable is its *architecture* (overlay synchronised to the map's own
projection, LOD by camera state) and that is the pattern the heatmap now follows.

**Resolution, measured rather than assumed.** AWS Terrain Tiles (Terrarium, public domain,
no key) serve to z15 and 404 at z16. At 36°N, z15 = **3.86 m/px** — the "3 metre or close to
it" target. But grid spacing is not information content, so I checked whether it is real:
along a z15 scanline over Boone NC, **98% of samples carry non-zero second differences with
gap=1**, which is not what bilinear upsampling looks like (upsampling leaves long runs of
near-zero second difference). The 3.86 m detail is real there. Coverage is not uniform —
away from lidar-mapped ground the source is coarser and z15 is genuinely smoother.

**Two constraints found by reading the artifacts, not the docs:**

- *No 3D terrain, at any version.* Unpacked `android-sdk` 11.5.2, 11.8.1, 11.11.0, 11.12.1,
  11.13.0 and 13.6.1 (534 classes). **No Terrain class, no `setTerrain`, nothing.**
  `raster-dem` is wired only into hillshade. MapLibre GL JS has `setTerrain`; MapLibre
  Android does not. 13.6.1 does add `ColorReliefLayer` — a GPU colour ramp over a DEM —
  which is a real height-map overlay, just not a mesh.
- *Esri imagery cannot ship.* Esri World Imagery requires an ArcGIS licence and restricts
  commercial/mobile redistribution. USGS `USGSImageryOnly` is public domain, keyless,
  unrestricted, and covers the whole contiguous US — every one of the 19 states. Verified
  serving to z16.

## Step 2 — Distribution

| p | Candidate for "what makes this heatmap wrong rather than merely rough" |
|---|---|
| 0.30 | Resolution: 3 m is unobtainable, so the forecast is coarser than promised. |
| 0.20 | The 4D engine can't be rewired because it never rendered. |
| 0.15 | MapLibre Android can't do 3D terrain, so one requested feature is impossible as specified. |
| 0.12 | Wrong DEM: mixing Mapbox-RGB and Terrarium encodings silently yields absurd elevations. |
| 0.10 | Neighbourhood operators at tile edges produce a seam of wrong values all round the viewport. |
| **0.08** | **The heatmap is monotonic in wetness and slope, so its brightest pixels land in creek bottoms — exactly where ginseng does not grow.** |
| 0.05 | Fixing the TPI radius in *cells* silently redefines "position on slope" at every zoom. |

Rows 1–3 are real and were handled, but they are constraints, not defects: they make the
feature smaller, not wrong.

## Step 3 — The tail

Row 0.08 is the finding. Every intuitive ginseng heatmap ramps monotonically: lower on the
slope is wetter is better. Built that way, the brightest ground on the map is the creek
bottom, because wetness and slope position both maximise there.

The literature says that is the one place not to send a digger. Virginia Cooperative
Extension, *Growing American Ginseng in Forestlands*: ginseng **"will not grow in
waterlogged soil, compacted areas (such as old roadbeds), leaf-filled depressions, rocky
outcrops, water flows, or heavy clay soils"**, and **"flat sites with poor drainage or a
history of flooding will not support ginseng growth."** Meanwhile the good ground is *"north
or east facing, not too steep, and near the bottom of slopes"*, with hollows productive.

Both ends are constrained. Wetness, slope angle and slope position are therefore **optimum
bands, not ramps** — `TerrainMath.band()` — and the surface turns over at the wet end.
Row 0.05 composes: the band for "position on slope" is meaningless unless its radius is
fixed in metres, so `tpiRadiusMetresFor(zoom)` returns metres and converts to cells per
mosaic.

## Step 4 — Falsifying my own claim

**First formulation:** *"Finer DEM resolution makes the forecast better, so push to 3 m."*

**Falsified by the literature I was citing.** Besnard et al. (2013, *Diversity and
Distributions* 19:955-963) found a **250 m DEM produced better-fitting species models than
a 50 m DEM** for TWI-based prediction; Kopecký & Čížková (2010) likewise warn that TWI is
resolution-sensitive in ways that do not reward fineness. Finer is not uniformly better —
groundwater does not follow 3 m microtopography.

**Corrected claim, which is what shipped:** resolution should differ *by purpose*. The
**visual** layers (hillshade, height overlay) use the finest DEM available, because
microtopography is exactly what a digger reads off a hillside. The **analytical**
neighbourhood radius is specified in metres and *widens* as you zoom out, so each index is
computed at the scale it is meaningful at rather than at whatever the pixel grid happens to
be. Both versions are kept here because the correction is the finding.

One methodological choice came directly from this reading: Kopecký & Čížková compared 11
flow-routing algorithms against Ellenberg soil-moisture values over 521 forest plots and
found correlation **doubled** with multiple-flow routing, with D8 among the worst. TWI here
uses Freeman multiple-flow accumulation, not the far simpler D8.

## Step 5 — Evaluators

### Rung 1 — mutation: build the naive heatmap and watch the controls catch it

The wetness, slope and slope-position bands were replaced with monotonic ramps — i.e. the
obvious heatmap the literature says is wrong — and the suite re-run:

```
MONOTONIC MUTANT                          55 tests, 4 failed
  [FAIL] negativeControl_wetnessIsNotMonotonic
  [FAIL] negativeControl_slopeIsNotMonotonic
  [FAIL] textbookSiteScoresWellAndBakedRidgeDoesNot
  [FAIL] suitabilityDiscriminatesAcrossARealHillside   <- on REAL Boone NC terrain
  [pass] positiveControl_theModelActuallyRespondsToItsInputs

RESTORED                                  55 tests, 0 failures
```

The positive control passing under the mutant is what makes this an instrument: "the
surface turned over" is a statement about the model, not about a dead harness.

### Rung 2 — execution against real terrain

`RealTerrainTest` runs the whole index stack over committed Terrarium tiles of Boone, North
Carolina (real Appalachian ginseng country, elevations 941–1124 m). Synthetic ramps prove
the formulas compute what they claim; they cannot catch a model that is internally
consistent and still useless on a hillside. So the real-terrain tests assert the surface
**discriminates** (spread > 0.25, mean neither saturated high nor low, top band actually
reachable) and that **the wettest 5% of real cells do not outscore the best 5%**.

### What the evaluator caught that I did not

`TerrainMath.profileCurvature` **had the sign inverted.** Its KDoc said "NEGATIVE = concave
= hollows" and it returned `-(d2x+d2y)/2`, while `GinsengSuitability` consumed it as
"positive = concave = cove". Every hollow in the country would have scored as a ridge nose
and every ridge as a hollow. Nothing would have revealed this in use: the heatmap would
still have looked entirely plausible, just inverted. A synthetic V-valley test caught it in
one line. The sign convention is now stated loudly in the function, and both signs plus the
flat case are pinned.

Second catch, in the reference data rather than the code: the Commons plate-classifier
labelled `AMP-011-0071-Cimicifuca racemosa.png` a **photograph**. "AMP" is *American
Medicinal Plants* (1892) — an engraving. The app would have printed "Photograph · Wikimedia
Commons" under a 19th-century plate: a false provenance claim of exactly the kind this
project exists to avoid. The classifier now treats any catalogue-code filename
(`[A-Z]{2,4}-\d{2,4}`) as a scan, and the species was re-fetched as a real CC0 photograph.

## What shipped

| Requested | Delivered | Honest status |
|---|---|---|
| Satellite view | USGS `USGSImageryOnly`, public domain, to z16 | **done** |
| Free movement | pan/zoom/**rotate**/**tilt**/quick-zoom all enabled | **done** |
| Height map overlay | `ColorReliefLayer` over Terrarium `RasterDemSource`, GPU, opacity slider, Appalachian-tuned ramp | **done** |
| Habitat heatmap toggle, lower-third-slope aware | terrain forecast from HLI + TPI + MFD-TWI + slope + curvature | **done** |
| ~3 m detail | z15 Terrarium = **3.86 m/px**, verified as real detail not upsampling | **done** |
| Adaptive scope by zoom | DEM zoom from camera zoom; TPI radius in **metres**, 120 m → 1500 m | **done** |
| Adaptive antialiasing | supersample factor 1–4 chosen from the DEM-cell : output-pixel ratio, plus GPU linear resampling | **done** |
| GPU rendering | compositing, draping, filtering, hillshade and colour relief all on MapLibre's GPU path; the neighbourhood analysis (MFD accumulation, multi-scale TPI) runs on the CPU per viewport | **partial, stated** |
| **3D height map** | **not possible as specified** — MapLibre Android exposes no terrain API at any version. Shipped as "pitched relief": 55° camera tilt + exaggerated hillshade + colour relief | **substituted, labelled in-app** |

## Open after Phase 2

10. **True 3D terrain is blocked.** Options, in ascending cost: (a) keep pitched relief;
    (b) render a mesh into a `GLSurfaceView` overlay synchronised to MapLibre's projection
    matrix — the pattern `DESIGN_LOD_HYBRID.md` specified and the old tree never
    implemented; (c) move the map to a renderer that has terrain. (b) is the faithful
    answer to the original request, and I deliberately did **not** ship it blind: there is
    no device or emulator here, so a hand-written GL renderer would go out having never
    executed once — which is precisely how this repository arrived with 96 Kotlin files
    that had never been compiled.
11. **The heatmap's neighbourhood analysis is CPU, not shader.** Moving MFD accumulation
    and multi-scale TPI into a fragment or compute shader is feasible and would remove the
    recompute-on-idle pause; same verification problem as above.
12. **The forecast is expert-weighted, not fitted.** No ginseng occurrence dataset was used
    and it has never been validated against known patches. Closing this means occurrence
    data, which is exactly the data diggers are right to refuse to share — a real tension,
    not an oversight.
13. **Soil calcium is invisible to it.** One of the strongest published predictors
    (Burkhart: ~3,360 kg/ha) cannot be derived from elevation. Surfaced in the UI as a
    field check with its indicator species rather than silently omitted.
14. **Terrain tiles stream.** The heatmap and relief need network on first visit to new
    ground, then cache. The habitat *model* remains fully offline on the bundled grid; the
    two elevation sources are deliberately separate.

---

# Phase 3 — the real 3D terrain mesh

Third pass, against "build the 3D mesh with the GLSurfaceView overlay" — the option Phase 2
listed as open item 10 and deliberately did not ship blind.

## Step 1 — Measuring what the platform actually allows

Two facts decided the whole design, and both came from unpacking artifacts rather than
reading documentation:

- **`CustomLayer` is unreachable from Kotlin.** Its only usable constructor is
  `CustomLayer(String, long)` — a pointer to a C++ host object. Rendering *inside*
  MapLibre's own GL context, which would have handed over the correct matrix for free, needs
  NDK code. Ruled out.
- **`Projection` exposes no matrix of any kind** — only `toScreenLocation`,
  `fromScreenLocation` and `getMetersPerPixelAtLatitude`.

`DESIGN_LOD_HYBRID.md` assumed the opposite: *"Accept the ModelViewProjection (MVP) matrix
directly from the MapLibre camera."* That assumption is false, and it is why the original
engine could never have worked as specified even if someone had written its GL calls.

So the overlay has to **reconstruct** MapLibre's camera from the public `CameraPosition`.

## Step 2 — Distribution

| p | Candidate for "what makes this overlay wrong rather than merely rough" |
|---|---|
| 0.30 | The shaders don't compile on device and the overlay is silently blank. |
| 0.22 | The reconstructed camera is subtly wrong, so terrain renders convincingly in the wrong place. |
| 0.16 | float32 can't hold Web Mercator world coordinates at high zoom; vertices jitter by metres. |
| 0.12 | Tile-edge cracks where adjacent mesh loads disagree. |
| 0.10 | A transparent GLSurfaceView over a MapView z-orders wrongly and hides the map. |
| **0.07** | **The verification itself is wrong — the alignment check reports its own rounding error as a map misalignment, or passes because it only ever tests the one point that cannot fail.** |
| 0.03 | The mesh is built on the main thread and the map stutters. |

## Step 3 — The tail

Row 0.07 is the finding, and it is the same shape as every other finding in this project:
**the instrument is part of the system under test.**

Two concrete ways an alignment check silently lies:

1. **It only probes the centre.** The camera target is the fixed point of the projection —
   it lands at the viewport centre under *any* scale error, any field-of-view error, any
   tile-size error. A check that probes the centre passes for a projection that is wrong
   everywhere else. `AlignmentCheck.probesFor` therefore samples a 3×3 grid at 15%/50%/85%
   across the visible region, and `detectsAScaleErrorThatPreservesTheCentre` pins that a
   5% scale error — which leaves the centre exactly right — is caught.
2. **It spends its own budget on rounding.** See the defect below.

## Step 4 — Falsifying my own claim

**First formulation:** *"Verify the camera offline by writing a reference implementation of
MapLibre's transform and asserting the Kotlin matches it."*

**Rejected before writing it**, on EINCOL §2's rule: *never let the subject be its own
witness.* A reference implementation written by the same author from the same reading of the
same algorithm agrees precisely when that reading is wrong. It would have produced a
confident green suite and a mesh floating off the ground.

**Corrected approach, which is what shipped** — two witnesses, neither of them a copy of my
arithmetic:

- **Offline: geometric invariants.** Properties that must hold for *any* correct
  implementation — the target lands at the viewport centre; an unpitched camera maps world
  pixels to screen pixels exactly 1:1; a bearing change preserves distance from centre;
  ground resolution matches the Web Mercator definition computed independently; zooming one
  level doubles displacement; pitch compresses distant ground without inverting its order;
  the local-origin matrix agrees with the absolute one.
- **On device: MapLibre's own projection.** `AlignmentCheck` runs every time the camera
  settles, comparing against `Projection.toScreenLocation` — a genuinely independent
  implementation written by other people. **If the mean residual exceeds 2 px the mesh is
  not drawn** and the layer panel says why. Failing visibly beats floating a hillside.

## Step 5 — Evaluators, and what they caught

### Rung 2 — a real compiler, executed on the shader code

`glslangValidator` (ESSL) compiles both stages via `tools/validate_shaders.sh`. This is the
only evaluator available that *executes* shader code, since there is no device here. Proven
capable of failing: injecting `dot(vec3, float)` produced
`ERROR: 0:42: 'dot' : no matching overloaded function found`, and the real sources compile
clean.

### Rung 1 — invariants and mutation

91 tests, 0 failures. Four failed on the first run, and the split is the point:

**Three were my tests being wrong, not the code.** I had asserted that at bearing 90 north
moves right (it moves *left* — bearing is the direction the camera faces, so facing east
puts north on the left); that pitch raises distant ground (it *lowers* nearby ground as the
horizon opens above); and that ground far to the north passes behind a pitched camera (it is
ground to the *south* that does — `clip.w = d + dy·sin(pitch)` only grows northward). In
each case I checked the geometry analytically before touching anything. **"The test failed
so change the code" is exactly how a correct projection gets broken**, and all three
corrected tests now record what the geometry actually does.

**One was a real defect, and it was in the instrument.** `project()` — the function
`AlignmentCheck` depends on — projected through the **float32** matrix. The translation
column carries the camera centre in world pixels: ~33.5 million at zoom 17, where float32's
step is 4 world pixels. Because world pixels shrink as zoom grows while the centre
coordinate grows to match, this works out at a **constant ~1.9 m of ground error at every
zoom level** — 0.59 px off centre at z17. The alignment witness would have been spending a
fifth of its 2 px budget reporting its own rounding, and could have tripped on it. Fixed by
keeping `project()` in double precision throughout; `viewProjectionMatrix()` stays float and
is now documented as safe only for local-origin geometry.

Pinned by `projectionAccuracyDoesNotDecayWithZoom` and a 0.05 px tolerance on the centre
test. Mutation-checked: reverting `project()` to the float matrix fails exactly those two
tests and nothing else.

### The precision design, separately verified

`vertexPositionsStaySmallEnoughForFloat32` asserts three things at once: stored coordinates
stay small, the **origin carries the magnitude** (proving it was actually subtracted rather
than the test passing vacuously on a mesh that happens to sit near the origin), and float32's
step at the largest stored coordinate is still sub-5-cm.

## What shipped

- `MapCamera` — MapLibre's transform reconstructed in double precision, with a
  local-origin MVP so float32 never sees a world coordinate.
- `TerrainMesh` — adaptive 96–192 vertex grid, central-difference normals, per-vertex
  suitability, and a skirt ring so adjacent mesh loads show no see-through crack.
- `TerrainShaders` — GLSL ES 3.00, compile-verified offline, with the hypsometric and
  forecast ramps matching the 2D layers so the same ground is the same colour in both views.
- `TerrainGlRenderer` — one program, one interleaved VBO, one draw call. Deliberately thin:
  everything with arithmetic in it lives in tested pure-Kotlin classes.
- `Terrain3DOverlay` — transparent `GLSurfaceView`, `RENDERMODE_WHEN_DIRTY`, mesh rebuilt
  off the main thread on camera settle, matrix resynced on every camera move.
- Layer panel: 3D toggle, opacity, 1–4× exaggeration, "tint 3D by forecast", and a live
  status line reporting triangles, grid, DEM zoom and the measured alignment residual.

This is the "Managed Overlay Injection" pattern `DESIGN_LOD_HYBRID.md` specified — now with
the GL calls its `onDraw()` never had.

## Open after Phase 3

15. **Still no on-device run.** The shaders compile under glslang, the maths is covered by
    91 tests, and the APK builds and signs — but no frame has ever been rendered. What
    offline work cannot check: whether `setZOrderMediaOverlay(true)` composites correctly
    above MapLibre's own surface on real hardware, whether the reconstructed camera matches
    MapLibre's to within 2 px in practice, and frame pacing. The alignment check is built to
    answer the second of those on first run, and to hide the mesh rather than mislead if the
    answer is no.
16. **`RENDERMODE_WHEN_DIRTY` may lag during fling.** The matrix is resynced from
    `OnCameraMoveListener`, which fires per frame, but if it lags the mesh will swim
    slightly against the map. Continuous render mode fixes it at a battery cost that matters
    for an app carried all day; the right choice needs a device to judge.
17. **Mesh rebuilds only on camera idle**, so a long pan shows terrain from the previous
    viewport until it settles. Tile-level LOD streaming would fix it and is a larger piece
    of work.
18. **The overlay draws above all map layers**, including patch pins. Depth-sorting the
    pins against terrain would need them rendered in the same GL pass.

---

# Phase 4 — fling lag and rebuilding during camera movement

Open items 16 and 17 from Phase 3. They look like two small fixes and are not: the obvious
version of each makes the other worse.

## Step 1 — Measure before designing

"Rebuild while the camera moves" is only safe if a rebuild is cheap. That was never
measured, so it was measured, on the real Boone fixture:

| Work | Cost (desktop JVM) |
|---|---|
| mesh build, gridN=192, no forecast tint | 27 ms |
| mesh build, gridN=192, with forecast tint | 320 ms |
| **mesh build, gridN=128, wide TPI radius** | **568 ms** |
| TPI sweep, radius 5 cells | 8 ms |
| TPI sweep, radius 15 cells | 69 ms |
| TPI sweep, radius 40 cells | 520 ms |
| **TPI sweep, radius 60 cells** | **1142 ms** |

A JVM figure is a floor, not a prediction — a handset is several times slower. Rebuilding on
camera move as the code stood would have frozen the map on every pan.

**The hot spot is the topographic position index**, and it is quadratic: 142x from radius 5
to radius 60. The radius is chosen from camera zoom, so the worst case is simply "zoom out".

## Step 2 — Distribution

| p | Candidate for "why the obvious fix makes this worse" |
|---|---|
| 0.28 | Rebuilding per move event queues builds faster than they finish; the mesh falls further behind the longer the gesture lasts. |
| 0.20 | Continuous rendering fixes the swim and drains the battery of an app carried all day. |
| 0.16 | Cancelling an in-flight build on every move means no build ever completes during a fling. |
| **0.14** | **The per-frame path does far more than matrix maths — it runs the alignment check and publishes Compose state every frame, so "fling lag" is partly self-inflicted.** |
| 0.12 | TPI is quadratic in a radius chosen from zoom, so a zoomed-out rebuild is seconds. |
| 0.06 | Optimising TPI by changing the window shape silently changes the model. |
| 0.04 | Caching mosaic analysis unboundedly trades a stutter for an OOM kill. |

## Step 3 — The tail

Row 0.14 is a defect I shipped in Phase 3 and had not noticed. `syncCamera` was wired to
`OnCameraMoveListener`, which fires per frame, and it did three things: recompute the matrix
(nanoseconds, correct), **run `AlignmentCheck`** (nine `project` calls plus nine
`toScreenLocation` calls, ~18 projections), and **publish status into Compose state**
(recomposition, every frame, during a fling). Two thirds of the per-frame work existed to
verify and report, not to draw.

So part of the reported "fling lag" was the verification machinery running at frame rate.
The three jobs are now on three rates: matrix per frame, rebuild throttled, alignment and
status on settle only.

Row 0.06 became the interesting one during implementation — see Step 4.

## Step 4 — Falsifying my own fix

**First formulation:** *"Use a summed-area table so TPI is O(1) regardless of radius."*

A summed-area table gives constant-time SQUARE windows. TPI's window is circular. I built the
square version, and it was fast — TPI fell from 1142 ms to 0 ms, mesh build from 568 ms to
14 ms. Then I checked what it did to the output rather than assuming a window is a window:

```
TPI square-vs-circular on real terrain:
  r=3  sign(|tpi|>1m) 1.000  corr 0.9822  meanAbs 0.23 m  worstScoreDelta 0.0835
  r=8  sign(|tpi|>1m) 0.997  corr 0.9879  meanAbs 0.53 m  worstScoreDelta 0.1254   <-
```

**0.125 of final ginseng score on some cells — more than half the width of a suitability
band.** That is not an optimisation, it is a different model wearing the old model's name,
and the user would never have known: the heatmap would have looked entirely plausible.

**Corrected fix, which shipped:** one summed-area rectangle query per ROW of the disk, with
the row half-width from the circle equation. **Exactly the circular mask**, in 2r+1 lookups
instead of (2r+1)^2 cell reads — 121 versus 11,300 at r=60.

```
TPI exact-circular fast path vs reference:
  r=3, 8, 20, 40   corr 1.0000   meanAbs 0.00 m   worstScoreDelta 0.0000
```

O(r) rather than O(1), and worth it. Both versions are recorded because the correction is
the finding: the first measurement (it is fast) was true and the wrong question.

A related detour worth naming: my first agreement test used a synthetic surface with a
hard-edged 40 m bump — the worst possible case for a window-shape change, and evidence about
nothing. Moved to the real fixture. And the first metric was sign agreement, which near
TPI ~ 0 flips on a 0.2 m difference and measures noise; it is now measured only where the
sign is a real claim, alongside the metric that actually matters, the **downstream
suitability delta**.

## Step 5 — Evaluators

### Rung 2 — measurement, before and after

| | before | after |
|---|---|---|
| TPI sweep, r=60 | 1142 ms | **42 ms** |
| TPI cost spread, r=5 to r=60 | 142x | **10x** |
| mesh build, wide radius | 568 ms | **40 ms** |
| mesh build, forecast tint | 320 ms | **66 ms** |
| mesh build, plain | 27 ms | **18 ms** |

Flow accumulation and the summed-area table are now cached per mosaic
(`TerrainAnalysis`), because a pan usually lands on the same elevation tiles and redoing
~224 ms of flow routing for the same data was the rest of the gap. Cache is capped at three
entries: these arrays are megabytes, and trading a stutter for an out-of-memory kill on a
cheap handset is not a trade.

### Rung 1 — mutation, against both failure modes

The rebuild policy is pure and lives in `MeshCoverage`, so both ways of getting it wrong are
testable:

```
MUTANT A — lagging (REBUILD_AT 0.6 -> 10.0, i.e. only rebuild once the screen has left)
  [FAIL] rebuildsBeforeTheViewportReachesTheEdge
  [FAIL] aBuildAlreadyCoveringTheScreenIsNotRestarted

MUTANT B — thrashing (MIN_REBUILD_INTERVAL_MS 250 -> 0)
  [FAIL] rebuildIsThrottledDuringAFling
  [FAIL] aFullSecondOfFlingProducesOnlyAFewBuilds

RESTORED: 11/11 pass, full suite 106 tests / 0 failures
```

Each mutant is caught by exactly the tests written for it and by no others, which is what
separates a suite that measures from a suite that merely passes.

## What shipped

- **Mesh decoupled from the viewport.** Built over 1.8x the visible region, rebuilt when the
  viewport has used 60% of that margin — while live terrain still covers the screen, not
  after a hole appears. Panning inside the margin costs nothing.
- **Rebuild throttled** to 250 ms, and an in-flight build whose mesh still covers the screen
  is left to finish rather than cancelled and restarted. A simulated one-second 60 fps fling
  produces at most 5 builds, not 60.
- **Render mode follows the camera**: continuous while moving, back to on-demand the moment
  it settles. The swim is a frame-latency problem, and this removes the frame; leaving it
  continuous would have been a battery cost on an app carried all day for nothing.
- **Per-frame path stripped to matrix arithmetic.** Alignment check and Compose status
  publishing moved to camera-settle. Status is also diffed before publishing, so an
  unchanged status recomposes nothing.
- **Exact-circular constant-lookup TPI** and a per-mosaic analysis cache.

## Open after Phase 4

19. **Still no device.** Every number above is a desktop JVM figure and a policy simulated in
    a unit test. Whether the mesh visually keeps up on real hardware is unmeasured — the
    frame-latency fix in particular is reasoned, not observed.
20. **Rebuild granularity is still whole-mesh.** Crossing a region boundary rebuilds
    everything rather than streaming the newly exposed strip. Tile-level LOD streaming would
    remove the remaining hitch and is a substantially larger piece of work.
21. **`MeshCoverage.MARGIN` and the throttle are tuned by reasoning, not profiling.** 1.8x
    and 250 ms are defensible from the measured build costs, but the right values depend on
    real fling velocities on real hardware.
