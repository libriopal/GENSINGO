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
