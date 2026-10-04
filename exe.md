# exe.md — GENSINGO execution directive

**Status:** standing directive and execution file. Supersedes `ORCHESTRATION_DIRECTIVE.md` and the
earlier `GENSINGO_DIRECTIVE.md` / `GENSINGO_CANDIDATES.md` pair.
**Self-contained:** the protocol, the rules and all 160 candidates are in this one file. Nothing else
must be read to start.
**Target:** the GENSINGO Android repository — Kotlin · Jetpack Compose · OpenGL ES · Room · a
foreground track service.
**Executor:** Claude Code, in-repo, with shell, Gradle, adb and git.
**Author of record:** Base44 lead-architect run, 2026-10-03. The owner is the sole approver of scope.

All paths in this file are **relative to the GENSINGO repository root**.

---

## 0 — Paste this prompt

> Read `exe.md` in full. Then run **§1 Bootstrap** if §4 is still unresolved. Then take **one wave**
> from the cursor in §12, running the EINCOL phases in §3 in order — including Load and Falsify, and
> working the tail row of the distribution before you design anything. Make all independent file
> changes in one batch. Verify once for the whole wave (§7) with raw output, reject every mutant, and
> take device evidence for anything visual. Then archive the ledger row, rewrite §12, and end with the
> three-line stop report. **Do not start a second wave.** Stop and ask if you hit any condition in §9.

That is the whole instruction. Everything below is the reasoning and the work.

---

## 1 — Bootstrap (run once, before the first wave)

Its output is a resolved §4. **Do not start a wave until §4 is resolved.** This step exists because
the orientation table in §4 was written from a prior run's inventory, and an execution file that
asserts file paths it has not verified is the exact failure it is written to prevent.

1. **Find the build entry point.**
   `ls -la`, `test -f gradlew && echo wrapper-present`, `cat settings.gradle.kts`
   Record the wrapper path and the application module's real name. This file assumes `:app` — if the
   module is named differently, rewrite every command in §7 accordingly.
2. **Confirm the toolchain.**
   `./gradlew --version`, `java -version`, `echo $ANDROID_HOME`
   If the build cannot run at all, **stop and ask.** A directive that cannot build is not executable.
3. **Record the pre-existing state of the tree, before any change.**
   `./gradlew :app:test` and `./gradlew :app:lint` on the untouched tree.
   Pre-existing failures and findings are recorded by name in the run log and are exempt from §7 for
   the rest of the program. Without this baseline, every later wave inherits blame it did not earn.
4. **Locate every file named in §4.**
   `find . -name "MainScreen.kt" -o -name "FieldMap.kt" -o -name "Terrain3DView.kt" -o -name "TerrainGlRenderer.kt" -o -name "TerrainShaders.kt" -o -name "MapLayers.kt" -o -name "CameraStart.kt" -o -name "Theme.kt" -o -name "OfflineArea.kt" -o -name "Prospects.kt" -o -name "RadiusScan.kt" -o -name "OnDeviceRationale.kt" -o -name "KeyVault.kt" -o -name "FindLearner.kt" | sort`
   Then: `grep -ril "terrarium" --include=*.kt .` and `ls -la $(find . -type d -name terrain3d)`
5. **Re-measure the corpus.** `find . -name "*.kt" | wc -l` — the §4 counts are a prior run's numbers,
   not truth.
6. **Check for a device.** `adb devices -l`. No device is not fatal: W1 is non-visual. Record
   `device: none` in the ledger and note which waves are therefore blocked.
7. **Write back.** Correct §4 in place with the resolved paths and counts. Create
   `docs/eincol/run-log.md` and `docs/eincol/evidence/` with the bootstrap row.
8. **Report three lines:** resolved paths · toolchain status · device status. Then stop. The first
   wave starts in the next session, not this one.

**Definition of ready for a wave** — all true before IMPLEMENT: the cursor is known; the candidate
IDs are claimed in the ledger; the files they touch have been read this session; every candidate has
its observable and its negative control written down; and the toolchain is known green.

**Definition of done for a wave** — all true before ADVANCE: the build is green; the wave's mutants
are rejected; the evidence row exists with raw output; §12 is rewritten; the cursor has moved.

---

## 2 — Mission

> **One map that a digger can trust in the field: a single coherent 2D/3D surface with the
> comprehension of a globe viewer, a rendering pipeline that is reliable before it is fast, and an
> optional AI layer that reads the app's own computed ground and says where to walk next — while
> never inventing a coordinate, a score or a plant.**

Three programs, one sentence each:

1. **The merge.** Today GENSINGO ships *two maps* and a switch. It must ship one map with a
   continuous camera, so the user never crosses a seam.
2. **The renderer.** Today the 3D surface is a hand-written GL terrain renderer bolted to a Compose
   view. It must become reliable — no cracks, no black frames, no lost context — then measurable,
   then fast, in that order. Speed claims on an unreliable renderer are worthless.
3. **The answer.** Today the app shows a surface and a radius scan. It must answer the owner's actual
   question — *give me the N best places to walk, in order, and tell me why each one* — with the
   honesty that the ground is a heuristic and the AI is an assistant, not an oracle.

The north star, unchanged and still binding:

> One minimal, offline-capable tool that maps the best ginseng ground inside a chosen radius, states
> how confident it is and why, and never pretends to know something it does not.

---

## 3 — The EINCOL protocol, made binding

EINCOL is the house protocol: **Load → Verbalize distribution → Falsify → Evaluate → Design →
Implement → Verify → Re-evaluate.** It is not a suggestion. Each phase produces an artifact, and the
wave may not advance past a gate it has not met.

| # | Phase | The artifact it must produce | Gate — the wave stops here if |
|---|---|---|---|
| 1 | **Load** | a *measured* inventory: file list, line counts, symbol names, call sites, read from the tree | the survey quotes memory instead of the repository |
| 2 | **Verbalize distribution** | a probability table over the plausible readings of the ask, with **the tail row worked first** | the least likely reading has not been tried or explicitly dismissed |
| 3 | **Falsify** | the reuse map, plus the counter-witness that would break each load-bearing claim | a claim has no named thing that would disprove it |
| 4 | **Evaluate** | the chosen design *with its rejected alternatives and the reason for each rejection* | fewer than two alternatives were rejected with reasons |
| 5 | **Design** | files, contracts, acceptance checks, and one negative control per check | an acceptance check has no observable, or would pass with the feature removed |
| 6 | **Implement** | the smallest coherent change set for the wave | the tree is red |
| 7 | **Verify** | raw output: Gradle build, lint, tests, every mutant **rejected**, device or screenshot evidence where §7 demands it | a claim is stated without its raw output |
| 8 | **Re-evaluate** | the ledger row, plus the sovereignty classifier over the wave's load-bearing claims | a claim is neither in-time nor against-a-witness and is not marked `open` |

**The sovereignty classifier** — run it over every claim the wave makes, and publish the result rather
than the conclusion:

| Claim | In time (true when written?) | Against a witness (re-checkable from the repo?) | Verdict |
|---|---|---|---|
| … | ✅ / ❌ | ✅ / ❌ | `sound` / `re-based, not ported` / `open` |

Two ❌ is `open`. Open claims are permitted; **hidden** open claims are not.

**The tail rule is the most violated phase.** Before designing anything, write down the reading of the
ask you believe is least likely — *the feature is unnecessary*, *the bug is in the data, not the code*,
*the merge is a rewrite in disguise* — and work it first. In the prior run this is what found the two
real defects and the one hidden cost. It is not optional ceremony.

---

## 4 — Repo orientation — resolved by §1 Bootstrap, 2026-10-03, at HEAD `c95815f`

Measured from the tree, not remembered. Module `:app` (the only module in `settings.gradle.kts`);
wrapper `./gradlew` (Gradle 8.13), JDK 21.0.10, Android SDK at `/opt/android-sdk` (from
`local.properties`; `$ANDROID_HOME` is unset). Corpus: **113 Kotlin files** (70 main, 42 unit test, 1
instrumented); 12,120 main lines, 7,278 test lines. The prior run's "66" was a stale count. A raw
`find` showed 428 because three merged contractor worktrees (127 MB) sat in `.claude/worktrees/`;
they were removed at bootstrap. Raw output: `docs/eincol/evidence/BOOT-*.txt`.

All paths are under `app/src/main/java/com/ginsengo/steward/` unless they start with `app/`.

| Area | Resolved path | Lines | This directive's relationship to it |
|---|---|---|---|
| Main screen | `ui/MainScreen.kt` | 476 | **evolve**: hosts the one map; gets no new chrome |
| Flat map path | `ui/map/FieldMap.kt` | 694 | **absorb**: becomes a rasterisation backend, not a screen |
| Mesh renderer | `terrain3d/`: 9 files, 1,802 lines (`AlignmentCheck` 89, `CameraMath` 194, `MapCamera` 331, `MeshCoverage` 122, `Terrain3D` 203, `TerrainGlRenderer` 246, `TerrainMesh` 246, `TerrainShaders` 83, `TerrainTextures` 288) | 1,802 | **absorb**: becomes the mesh backend behind the same camera |
| 3D host / mode switch | `ui/Terrain3DView.kt` | 567 | **replace**: the switch is the thing being deleted |
| The "mode" (A6's target) | **No enum exists.** The mode is `ui/FieldViewModel.kt` `view3d: Boolean` (`_view3d`, `setView3d`) and the switch button in `ui/MainScreen.kt` | — | A6 deletes this flag and its switch, not an enum |
| Shared camera (already present) | `terrain3d/CameraMath.kt` `ViewCamera` + `FieldViewModel.camera`, from ARCHITECT iteration 1: one camera *value* handed across the switch, but each view still holds its own live camera | — | the seed of A1; A1 must remove the per-view live cameras |
| Layer registry | `ui/map/MapLayers.kt` (`MapLayerState`, `Basemap`) | 49 | **promote**: becomes the single scene description both backends read |
| Camera seed | `ui/map/CameraStart.kt` | 46 | **promote**: the seed of the one `CameraState` |
| Palette | `ui/Theme.kt`, `object Gen`, 9 colours | 41 | **freeze** |
| Contours, hydrology | `terrain/ContourLines.kt`, `terrain/Isolines.kt`, `terrain/Hydrology.kt`, `terrain/WaterLines.kt` | — | **revive**: contours already ship on both views (iteration 1); D1–D4 extend them |
| DEM decoding (Terrarium) | `terrain/DemTileStore.kt` (`terrariumMetres`, pure since A.1), `ui/map/FieldMap.kt` (MapLibre decodes its own copy for the hillshade). Witness: `TerrariumDecodeTest` (spec vectors, A.1). **Correction (A.1):** bootstrap named `RealTerrainTest` as the decoder's test. It is not: its fixtures were decoded offline by a Python script, so the app's decoder had no test, and mutant Q2 (= I4) survived until it got one | — | the I4 offset mutant targets `DemTileStore.terrariumMetres` |
| Offline area | `ui/OfflineArea.kt` | 98 | **complete**: program H |
| Prospecting spine | `prospect/Prospects.kt` 141, `research/RadiusScan.kt` 342, `research/OnDeviceRationale.kt` 66 | — | **extend**: the spine of program F |
| AI providers | `research/KeyVault.kt` 71, `research/ClaudeResearchClient.kt`, `research/GeminiResearchClient.kt`, `research/ResearchRepository.kt` | — | **harden and unify**: program E |
| Weight learning | `learn/FindLearner.kt` 299, `learn/UserFinds.kt` | — | **gate**: program G |
| Fix averaging | `field/FixAverager.kt` | — | **freeze** |
| Persistence and recording | Room (`data/db/`), the foreground track service (`field/`) | — | **extend**: every schema change needs a migration (H9) |
| **Unwired engines (found at bootstrap)** | `habitat/HabitatChecklist.kt` 213, `soil/SoilSuitability.kt` 245, `terrain/AstroEphemerisEngine.kt` 331 + `terrain/EsiMatrixModel.kt` 217 (they reference only each other), `habitat/HabitatEngine.kt` 77 + `HabitatModel.kt` 130 + `OnnxGraphWeights.kt` 125 (bundled `assets/models/habitat_model.onnx`), `verify/PlantVerification.kt` 182, `geo/DemGrid.kt` 116 | 1,636 | **no caller in the app.** Candidates J1, J6 and J7 decide, with evidence, whether to wire or strike each one |

**Do not touch:** the palette (`Gen`), the 20 s averaging window, the layer semantics, the season and
ownership wording, or any licence-bearing asset. These are frozen by prior owner instruction, and a
wave that changes one is struck regardless of its other merits.

**If a file in this table cannot be found, that is a finding, not a blocker** — record it in §4 and in
the run log, and continue with the files that do exist. The table is the directive's weakest
assumption and the first thing execution should test.

---

## 5 — Invariants (violating one fails the wave, however green the build)

1. **One map, one camera.** A single camera state is the only source of truth for position, zoom,
   bearing and tilt. Both backends derive from it. No second camera, no per-view stored camera.
2. **Real data or an honest gap.** No synthetic relief, no invented channels, no fabricated moisture,
   no placeholder coordinates. Missing elevation renders as *unknown*, never as sea level.
3. **One quantisation point.** Factors are computed once and stored once; the map, the read-out, the
   ranking and the AI layer all read that same stored cell. No consumer re-derives.
4. **Terrain, never legality.** A score is a ground-quality heuristic. Never a likelihood of finding a
   plant, never a harvest right, never a legal opinion.
5. **Provenance on every number.** Source, resolution, model version, date, and what the number does
   *not* mean — in the UI, not only in the docs.
6. **Privacy by construction.** No fix, find, track or precise coordinate leaves the device without
   explicit owner action. Any model call sees a coarse cell only, and the payload builder is tested to
   prove it.
7. **The AI assists; it never computes ground.** The model may explain, rank, summarise and draft. It
   may not produce a score, a coordinate, or a claim about a plant. A refusal is a valid output.
8. **No dead controls.** A toggle exists only if a renderer exists behind it. An absent feature is
   better than a switch that does nothing and a sheet that lies.
9. **Reliability before speed.** No performance claim is accepted until the renderer is proven correct
   under context loss, tile failure, rotation and no-data.
10. **Honest status.** `Implemented` requires its evidence attached. Otherwise `Deferred`,
    `Blocked — needs data`, `Blocked — needs licence`, `Blocked — needs device run`, or `Struck`.

---

## 6 — The three programs

### Program A — one map (the 2D/3D merge)

**Where it is.** Two renderers, two cameras, two layer implementations, and a switch in
`ui/Terrain3DView.kt` that swaps one for the other. The user pays for the seam: a jump on transition,
two sets of gestures, layers that exist in one projection and not the other.

**Where it goes.** One scene, two rasterisation backends. Tilt is a continuous value from 0° to 85°,
not a mode. Below the hand-off tilt the mesh backend is simply not asked to draw; above it the flat map
fades out. The user pans, zooms and tilts through the transition without seeing it happen.

**The eight hard problems — this is the work:**

| # | Problem | Resolution |
|---|---|---|
| A-i | Two cameras disagree after a transition | One `CameraState` (lat, lng, zoom, bearing, tilt). Both backends read it, neither stores its own. A conformance test asserts they agree within a tolerance at ten sampled states. |
| A-ii | Two coordinate pipelines | One `Projection` (WGS84 ↔ Web Mercator ↔ screen). The flat path stops using the map view's private projection for anything the mesh path also needs. |
| A-iii | Two layer implementations | `MapLayers` becomes a *scene description*. Each backend implements a renderer per layer type. A layer type with no backend is removed from the sheet in that same wave. |
| A-iv | A visible seam at the hand-off | Hand-off is driven by *measured agreement* — the two backends' screen positions for a set of control points — not by a magic tilt constant. |
| A-v | Depth policy is accidental | Every layer declares its depth behaviour: `draped`, `occluded`, or `always-on-top`. Declared per layer, honoured by both backends, testable. |
| A-vi | Two tile stores | One store. A DEM tile fetched for the mesh is available to the flat path and vice versa. One eviction policy, one memory ceiling. |
| A-vii | Two frame loops | One choreographer drives both backends. Two render loops cause double work and jank. |
| A-viii | Rotation loses state | One camera state restored across configuration change; both backends rehydrate from it. |

**The Google-Earth goal, stated testably.** The merge is complete when, for any camera state, the user
can: tilt from flat to oblique without a jump; see every overlay draped on the terrain in both
projections; read a label that is correctly occluded by a ridge; and have every control in the layer
sheet still mean something. Each is a candidate with its own acceptance check (A18).

### Program B — a rendering pipeline that can be believed

Order of operations is the point: **correct → measurable → fast.** A renderer that flickers cannot be
profiled, because its frame times are meaningless.

- **Correctness.** Mesh cracks at tile boundaries (skirts or stitched LOD), no-data holes rendered as
  holes, normals from the same stencil the analysis uses, depth precision adequate for draped overlays,
  winding correct past 90° of tilt, GL context loss and restore, shader compile failures surfaced
  rather than silently black, and a hard fallback to the flat map if the mesh backend draws nothing.
- **Measurability.** Frame time recorded and displayed; GPU timer queries where supported; peak memory;
  cold-start time to first frame. Without these, "optimized" is a word, not a claim.
- **Speed.** Bounded tile concurrency, prefetch along the camera direction, frustum and horizon
  culling, an LRU mesh cache under a hard ceiling, vertex-buffer pooling instead of per-frame
  allocation, decode off the GL thread, adaptive LOD bias from measured frame time, and a render loop
  that pauses when the surface is not visible.
- **Battery and thermals.** The field case is a phone in a pocket, a phone in sunlight, and a phone on
  a ridge for four hours. A render loop at 60 Hz while the screen is off is a defect, not an
  optimisation.

### Program C — the answer: top-N mandated hotspots, with an optional AI layer

Two halves that must not be confused with each other.

**C-1 · The engine — deterministic, on device, no network.** The owner sets a mandate: *N* places,
inside a region, under constraints. The engine returns exactly *N* ranked **places** — clusters, not
cells — or fewer with a stated reason. Each candidate carries its score, its factors, its distance and
bearing, its separation from the others, whether it has been searched before, whether it is reachable,
and whether it sits inside a protected or no-collection area. Same inputs produce the same list, every
time. This half works in airplane mode and always will.

**C-2 · The AI layer — optional, network, never load-bearing.** One provider interface, one gateway
owning consent, redaction, budget, cancellation, provenance and the structured-output contract. It
explains a cell from its stored factors, compares two candidates, orders a day's walk, drafts a field
note the user edits before saving, and answers *where should I go today* — strictly over the engine's
own output. It never computes a score, never invents a coordinate, and is never the reason a
recommendation exists. With the key removed, every screen still works.

**The gap between them is the honesty of the product.** C-1 is a measurement with a heuristic in it.
C-2 is commentary. The UI must make the difference visible on screen, and the provenance stamp must say
which half a sentence came from.

---

## 7 — Verification doctrine

A wave passes only if **all** of these hold, with raw output recorded in the evidence row. Commands
assume the module resolved in §1 is `:app`.

1. **Build** — `./gradlew :app:assembleDebug` exits 0; `./gradlew :app:assembleRelease` exits 0 for any
   wave touching R8 rules, permissions or the manifest.
2. **Lint** — `./gradlew :app:lint` reports no *new* findings on touched paths. Pre-existing findings
   are exempted **by name**, from the §1 baseline, never by category.
3. **Tests** — `./gradlew :app:test` green, including every pre-existing test. A wave that breaks a test
   it did not write has failed, not "moved a boundary".
4. **Numerical witnesses** — terrain and scoring candidates are verified against analytic surfaces (a
   plane of known gradient) and independently decoded fixture tiles, never against the app's own decoder
   output alone.
5. **Negative controls** — for every new oracle, a mutant the oracle **must** reject: north/south
   reversal, dropped Terrarium offset, inverted optimum band, no-data forced to zero, degrees/radians
   mixed, a duplicated hotspot, a coordinate smuggled into an AI payload, a coordinate invented in an
   AI reply, a shuffled-label learner, an induced mesh crack.
6. **No-regression** — previously passing checks still pass.
7. **Consumer agreement** — if the wave touches scoring, the map pixel, the read-out and the ranking
   must be shown reading the same stored cell.
8. **Device or visual evidence** — required for any UI, touch, colour, rendering or offline candidate.
   `adb exec-out screencap -p > docs/eincol/evidence/<wave-id>-<name>.png`, at a named device and
   Android version, plus an airplane-mode run for offline candidates. **A green build is never visual
   proof.** For a rendering candidate the screenshot must show the specific thing claimed — a stitched
   boundary with no crack, a label occluded by a ridge, a draped overlay at 60° of tilt.

**A test that cannot fail is not evidence.** If a check would pass with the feature removed, it is
deleted or replaced. This is the most expensive rule in the file and the one that catches the most
self-deception.

**Fail fast, report as-is.** The harness reports the first failure and stops. A failed run is recorded
with its raw output and never softened, re-run until green, or summarised away.

**Where the evidence lives.** `docs/eincol/run-log.md`, one row per wave: wave id, candidates, branch,
commit range, build/lint/test output, mutants rejected, device, screenshot paths. The ledger in §10
links to it; it does not duplicate it.

**When something is already broken.** If the build or a test is red before your change, stop, record it
as a pre-existing failure by name, and do not fix it inside a feature wave — it becomes its own
candidate. If a file in §4 does not exist, fix §4 and continue. If no device is attached, take the
screenshot-dependent candidates out of the wave and re-queue them as `blocked(device)`.

---

## 8 — The wave

A **wave** is the atomic unit: one bounded batch of candidates designed, implemented, verified and
archived in one session. Default **4–8 candidates**, never more than 10.

**Lifecycle.** CLAIM (ledger row, `in-flight`) → SURVEY (read only the files the candidates touch) →
DESIGN (per candidate: what changes, in which file, the observable that proves it) → IMPLEMENT (all
independent writes in one batch) → VERIFY (one pass for the whole wave) → ARCHIVE (evidence row,
status, counts) → ADVANCE (move the cursor). **A wave is not finished until ADVANCE.**

**Safe point** = between ADVANCE of one wave and IMPLEMENT of the next. Stopping mid-wave is not
permitted; if the budget runs out, finish the smallest coherent slice and re-queue the remainder.

**Branch and commit protocol.** Branch `eincol/<wave-id>` off the default branch. One commit per
candidate, `eincol(<id>): <name>`, body containing the wave's verification output. Never commit to the
default branch, never force-push, never rewrite history, never amend a pushed commit.

**Budget rules.** One verification pass per wave, never per candidate. Never re-read a file already in
context. Never re-run a check whose inputs are unchanged — cache the measurement in the run log. No
exploratory refactor inside a feature wave; a refactor is its own candidate with its own evidence. If
the remaining budget cannot cover IMPLEMENT through ADVANCE, do not start the wave: record the intent
and stop cleanly.

---

## 9 — Stop conditions requiring the owner

Stop and ask. Do not improvise, do not choose on the owner's behalf, and do not soften a blocker into a
partial implementation.

- adopting a dataset whose licence is unverified (imagery, hydrography, soil survey, land cover, DEM);
- adding any dependency, SDK or binary blob to the build;
- adding or changing a network destination, an API key, a provider, or any outbound call carrying
  location — including a change to the coarse-cell rule;
- claiming calibration, accuracy or a probability without held-out field observations;
- needing a real device run (airplane mode, low-memory device, thermal soak) that cannot be performed;
- changing the sovereign goal, an invariant, the phase order, or the palette;
- deleting or rewriting a working subsystem rather than extending it — including the flat map path;
- any Room schema change without a stated migration;
- anything that would remove a capability the owner currently relies on in the field.

Each is recorded as `blocked(*)` with **the exact decision the owner must make**, then the queue moves
on. Blocked items never stall the cursor.

---

## 10 — Ledger, resume, definition of done

The ledger is the only source of truth for position. Resume never re-derives state from memory.

**Ledger fields per wave:** `wave id · program · candidates · status · branch/commits · files touched ·
evidence row · verification output · cursor after`.

**Status vocabulary:** `queued · in-flight · partial · verified · archived · blocked(data) ·
blocked(licence) · blocked(device) · struck`.

**Stop report** — three lines, always, and machine-readable on the first token:

```
STOPPED AT:  <wave id> — safe point, cursor advanced to <next wave id>
DONE:        <candidate ids + status>
NEXT:        <candidate ids> — <one-line plan>
```

**Resume instruction from the owner:** `continue exe.md from <wave id>`. The executor then reads the
ledger, confirms the last wave is `archived` (if `partial`, finishes the re-queued remainder first),
re-reads only the files the next wave touches, and resumes at IMPLEMENT. No re-verification of archived
waves, no re-planning of closed phases.

**Program close — the definition of done.** All of:

1. Every candidate in §11 carries a terminal status with evidence attached. No item left "staged".
2. Every `blocked(*)` item is listed with its blocker and the owner's decision needed, and the app
   shows the honest label. No blocker silently converted into a claim.
3. One clean-tree build, debug and release, exit 0; the full test suite green; every mutant rejected.
4. Every rendering claim has device evidence at a named device, showing the specific thing claimed.
5. The four merge statements in Program A each pass on a real device.
6. The engine produces the same top-N from the same inputs on two different devices.
7. The AI layer, with its key removed, leaves every screen working.
8. An independent audit pass that may strike claims, with the strikes recorded.
9. The ledger's counts and §11's statuses agree exactly.
10. The stop report reads `COMPLETE`, and the open list is published as the honest remainder.

---

## 11 — Candidate register — 160 candidates, nine groups

Every candidate starts `queued`. Status is tracked in the ledger (§10) and in the wave-state block
(§12), not in this table — this is the stable inventory and **must not be re-numbered** once a wave has
run against it. A row whose witness cannot fail is not a candidate; it is deleted or replaced.

### A — One map: the 2D/3D merge · A1–A18 · phase W1

Purpose: replace two maps and a switch with one scene, one camera and two rasterisation backends.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| A1 | Single `CameraState` as the sole source of truth | Position, zoom, bearing and tilt live in exactly one object; no backend stores its own | Grep: no second camera field; both backends read the same instance |
| A2 | One `Projection` module | WGS84 ↔ Web Mercator ↔ screen exists once, used by both backends | Both call sites import the same module; no duplicated maths |
| A3 | Backends stop owning the camera | Flat path no longer treats the map view's camera as authoritative | Camera conformance test (A4) passes with the flat view's internal camera ignored |
| A4 | Camera conformance test | At 10 sampled states, both backends place control points within tolerance | Test fails if either backend is fed a stale camera |
| A5 | Continuous tilt 0°–85° | Tilt is a value, not a mode; no discrete switch in the UI | Device run: tilt through the range with no mode change in the state |
| A6 | Delete the mode enum | After A5, the 2D/3D mode enum and its switch are removed | Grep: enum absent; no dead branch in the main screen |
| A7 | Measured hand-off | The cross-over point is derived from backend agreement, not a hard-coded tilt | Change the tolerance → the hand-off tilt moves |
| A8 | Seamless cross-fade | No frame shows both backends disagreeing visibly | Screen recording across the hand-off at a fixed location |
| A9 | Layer registry becomes a scene description | One registry; each backend implements a renderer per layer type | Adding a layer type without a backend fails a compile-time or test-time check |
| A10 | Declared depth policy per layer | Every layer declares `draped` / `occluded` / `always-on-top` | A layer with no declared policy fails the scene validation test |
| A11 | Draped overlays conform to terrain | Heatmap, contours and mask follow the mesh, not a flat plane | Device screenshot at 60° tilt showing an overlay following a ridge |
| A12 | One shared tile store | A tile fetched for one backend is available to the other | Fetch on the flat path, tilt, confirm no re-download (network log) |
| A13 | One eviction policy | One memory ceiling governs both paths | Counters show a single ceiling respected under pressure |
| A14 | One frame choreographer | A single render clock drives both backends | Instrumentation: one frame callback per frame, not two |
| A15 | Camera continuity | No jump when crossing the hand-off | Automated: sample the projected centre before/after; delta below threshold |
| A16 | State survives configuration change | Camera and layers restored; both backends rehydrated | Rotate mid-tilt on a device; camera and layers identical after |
| A17 | One gesture handler | Pan/pinch/rotate/tilt drive the camera identically in both projections | Device run: the same gestures in both projections produce the same camera deltas |
| A18 | The four merge statements as device tests | Tilt without jump; overlays draped; labels occluded correctly; every layer control still means something | One device test per statement, all four green |

### B — Rendering reliability · B1–B20 · phase W2

Purpose: make the mesh backend correct before anyone claims it is fast.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| B1 | Skirts or stitched LOD | Tile boundaries cannot crack under any camera | Visual: stitched boundary at grazing tilt, no background showing through |
| B2 | Crack detector | An artificially induced crack is found by the detector | Mutant I10: remove skirts → detector fails the build |
| B3 | Quadtree LOD with stitching | Adjacent tiles at different depths share vertices; no T-junctions | Wireframe capture at a depth boundary |
| B4 | No-data renders as a hole | Missing elevation is never drawn as 0 m | Fixture tile with a no-data block: hole visible, no flat patch |
| B5 | No-data visual language | "Unknown" is visually distinct from "low score" | Legend and screenshot side by side |
| B6 | Normals from the analysis stencil | Mesh normals use the same stencil as the habitat analysis | Numeric: mesh normal vs analytic plane normal within tolerance |
| B7 | Adequate depth precision | Draped overlays do not z-fight at any tilt | Screenshot at 85° tilt; no shimmer on the overlay |
| B8 | Correct winding past 90° | No inverted faces at extreme tilt | Screenshot at maximum tilt |
| B9 | GL context loss handled | Loss → rebuild → resume without a crash or a black map | Device: background the app under memory pressure, return |
| B10 | Render-target resize | Rotation, multi-window and foldable changes resize cleanly | Device: rotate and split-screen; no stretched or clipped frame |
| B11 | Shader failure surfaced | A compile/link failure shows a message, never a silent black pane | Inject a bad shader in a test build; UI reports it |
| B12 | Black-frame guard | If the mesh backend draws nothing, the flat map is shown instead | Mutant: force an empty draw → fallback engages |
| B13 | Tile failure is visible | A failed terrain tile offers retry, never an empty void | Airplane mode mid-load; retry succeeds on reconnect |
| B14 | Exaggeration in one place | Mesh and analysis either agree or the difference is stated on screen | Change exaggeration → analysis unchanged, and the UI says so |
| B15 | Hillshade from the same normals | No double shading; hillshade and mesh agree | Screenshot: shading direction matches the sun indicator |
| B16 | Deterministic mesh rebuild | Changing exaggeration leaks no buffers and produces identical geometry | Buffer counter flat across 20 toggles |
| B17 | No allocation in the draw loop | Zero allocations per frame on the GL thread | Allocation profiler over a 60 s pan |
| B18 | Explicit GPU resource lifetime | Every texture and buffer is deleted on eviction | Counter returns to baseline after a cache clear |
| B19 | Capability negotiation | ES 3.1 / 3.0 / 2.0 paths selected explicitly; no silent downgrade | Device matrix log naming the selected path |
| B20 | Device matrix run | Minimum supported Android version and a low-end GPU both render | Two named devices, screenshots attached |

### C — Performance, memory, battery · C1–C18 · phase W3

Purpose: make "optimized" a measurement rather than a word.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| C1 | Bounded tile concurrency | A hard cap on simultaneous tile work | Counter never exceeds the cap under fast panning |
| C2 | Directional prefetch | Tiles ahead of the camera are fetched first | Hit-rate improvement measured over a scripted pan |
| C3 | Frustum culling | Off-screen tiles are not drawn | Draw-call count drops when looking away |
| C4 | Horizon culling | Terrain beyond the horizon is not drawn | Draw-call count drops at high tilt |
| C5 | LRU mesh cache with a ceiling | Memory stays under the cap; oldest evicted | Peak memory flat across a long pan |
| C6 | Vertex-buffer pooling | Buffers reused, not reallocated | Allocation count flat across tile churn |
| C7 | Decode off the GL thread | Tile decode never blocks the frame | Frame-time trace shows no decode stall |
| C8 | Frame time measured and shown | Frame time is recorded and visible in a debug view | Debug view screenshot |
| C9 | GPU timers where supported | GPU time measured where the device allows, CPU fallback elsewhere | Log shows which path was used |
| C10 | Peak memory ceiling | Measured peak RSS on a low-end device, under a stated budget | Profiler capture on the named baseline device |
| C11 | Cold-start budget | Time to first frame under a stated budget | Instrumented cold start, five runs |
| C12 | Adaptive LOD bias | Quality drops before frame time degrades | Frame time stays within budget under forced load |
| C13 | Render loop pauses when invisible | No drawing while the surface is hidden or the screen is off | Battery trace over 30 min backgrounded |
| C14 | Idle frame-rate reduction | Static camera → reduced rate | Frame counter over 60 s idle |
| C15 | Thermal response | Quality reduces before the device throttles | Thermal soak run: frame time stable as the device heats |
| C16 | Bounded disk cache | Tile cache has a size cap and evicts | Cache size after a long session stays under cap |
| C17 | Before/after evidence | Every performance claim carries before/after numbers on a named device | Run log entry per claim |
| C18 | Pinned baseline device | One device named for all performance claims | Every C-row cites the same device |

### D — Comprehension and legibility · D1–D18 · phase W4

Purpose: turn a correct renderer into one a digger can read in the field.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| D1 | Contours revived | Real isolines from the same DEM, on both backends | Screenshot; isolines match an independent contour of a fixture |
| D2 | Metre-based intervals | Interval stated in metres, major/minor distinguished | Legend states the interval |
| D3 | Contour labels | Sparse labels, readable on a phone | Screenshot at field zoom |
| D4 | Contour seam reconciliation | Isolines join across tile edges | Screenshot across a tile boundary |
| D5 | Line-of-sight elevation profile | Profile from the fix toward a target | Profile matches sampled DEM values |
| D6 | Route elevation profile | Profile along the planned walk | Profile matches the planned geometry |
| D7 | Distance depth cueing | Atmospheric fade with distance | Screenshot; far terrain visibly softer |
| D8 | Sun/shadow indicator | The hillshade direction is explained on screen | Indicator agrees with the shading in a screenshot |
| D9 | North indicator | Reflects camera bearing in both projections | Rotate; indicator tracks |
| D10 | Scale and altitude readout | Valid in both projections | Values agree across the hand-off |
| D11 | Labels legible at tilt | No squashed or overlapping text at pitch | Screenshot at 60° tilt |
| D12 | Exaggeration indicator | Vertical scale is never mistaken for truth | Indicator visible whenever exaggeration ≠ 1 |
| D13 | "You are here" clarity | The fix marker is unambiguous at any tilt | Screenshot at 0° and 75° |
| D14 | One legend for everything | Every colour in both projections is explained once | Legend audit against the scene description |
| D15 | Sunlight and colour-vision checks | Contrast holds in sunlight; ramp survives colour-vision simulation | Simulated screenshots, both checks |
| D16 | "Why is this red" | Reachable from the map, in the digger's terms | Tap → explanation naming the stored factors |
| D17 | Field-language landforms | Cove, hollow, bench, ridge — not jargon | Copy review against the field glossary |
| D18 | Source line on every visual claim | Each visual states where its data came from | Audit: no claim without a source |

### E — The AI layer · E1–E22 · phase W6

Purpose: an optional, bounded, honest assistant that reads the app's own computed ground.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| E1 | One provider interface | Claude and Gemini sit behind a single interface | Both providers swapped by configuration only |
| E2 | One gateway | Consent, budget, redaction, provenance and cancellation live in one place | Grep: no client call bypasses the gateway |
| E3 | Key vault hardened | Key in Android Keystore; absent from prefs, backups, logs, crash reports | Backup extraction and log scan show no key |
| E4 | Off by default | No key → the layer is absent, not broken | Fresh install: every screen works, no network call |
| E5 | Consent screen | States exactly what leaves the device, in plain words | Screenshot; copy lists the coarse cell and nothing else |
| E6 | Coarse-cell payload | Only the ~11 km cell leaves; the rule is preserved | Payload fixture shows one coarse cell, no precise coordinate |
| E7 | Payload privacy oracle | A smuggled coordinate is rejected before the call | Mutant I6: inject a fix → oracle fails the build |
| E8 | Structured output contract | JSON schema validated; invalid output refused, not parsed loosely | Malformed reply fixture → refusal, no partial parse |
| E9 | No invented coordinates | A reply containing a new coordinate is rejected | Mutant I7 → validator rejects |
| E10 | No invented scores | Scores come only from the engine; AI text never writes one | Reply containing a score is discarded, engine value used |
| E11 | Provenance stamp | Provider, model, date and prompt version on every AI statement | UI shows the stamp on each AI sentence |
| E12 | Prompt versioning | Prompts versioned with a golden-set regression suite | Changing a prompt without updating the golden set fails |
| E13 | Cost preview | Cost shown before a call | Screenshot of the preview |
| E14 | Hard budget | Session and monthly caps that stop the layer | Exceed the cap → calls refused with a clear message |
| E15 | Cancellation | Outstanding requests cancelled; stale replies suppressed | Cancel then reply → nothing appears |
| E16 | Timeouts and circuit breaker | Jittered retries, bounded, then the layer disables itself | Forced failures → breaker opens, UI says so |
| E17 | Offline degradation | No network → on-device rationale, never a spinner | Airplane mode: explanations still appear |
| E18 | Explain this cell | The AI reads the stored factor vector and says why | Output cites the actual stored factors |
| E19 | Compare and plan | Compare two candidates; order a day's walk | Output references engine candidates only |
| E20 | Drafted field notes | Notes the user edits before saving | Save is blocked until the user confirms |
| E21 | Provider swap by config | Model and provider change without a code change | Swap in settings; the same behaviour contract holds |
| E22 | Local telemetry and kill switch | Call log stored locally, exportable, no PII; one switch disables the layer | Export contains no coordinates; switch stops all calls |

### F — Top-N mandated hotspot engine · F1–F22 · phase W5

Purpose: answer the owner's real question — the N best places to walk, in order, with reasons.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| F1 | Mandate as first-class input | N, region and constraints are a saved, re-runnable object | Reopen a saved mandate and reproduce the result |
| F2 | Exactly N, or fewer with a reason | The count is honoured or the shortfall is stated | Ask for 10 in sparse ground → 6 returned with the reason shown |
| F3 | Places, not cells | Candidates are clusters with a boundary | Each result has an extent, not a single coordinate |
| F4 | Minimum patch size | Single-cell flukes rejected | Synthetic lone peak → rejected |
| F5 | Separation rule | Results read as separate walks | Minimum spacing enforced; test on a dense synthetic field |
| F6 | Determinism | Same inputs → identical list | Two devices, same mandate, identical output hash |
| F7 | Strongest first | Ranking order matches the copy | Test asserts descending score; UI says "strongest first" |
| F8 | Coverage report | Analysed area stated; unknown cells excluded, gaps not hidden | Report shows analysed area and excluded cells |
| F9 | Rank stability | Small weight changes do not reshuffle the list wildly | Perturb weights within the sensitivity bound; order stable |
| F10 | Confidence and reason per candidate | Each carries a confidence and a stated reason | Read-out cites stored factors, not prose invention |
| F11 | Already-searched suppression | A searched slope is not recommended again | Mark searched → it drops out of the next mandate |
| F12 | Reachability flag | Effort and access reported per candidate | Each candidate shows distance and an effort class |
| F13 | Legal flag | Protected land and no-collection zones excluded or flagged | Candidate inside a zone is flagged, never silently ranked |
| F14 | Seasonal fitness | Says whether the ground will be good when you go | Out-of-season mandate warns (mutant I15) |
| F15 | Constraint filters | Elevation band and slope limits as filters | Filter excludes candidates outside the band |
| F16 | Ordered walk plan | The top N in a walkable order | Plan respects the separation and the start point |
| F17 | "Why not the others" | The rejected neighbourhood is explainable | Show the runner-up and why it lost |
| F18 | Sensible refresh policy | Recomputes on weight, data or camera change, not every pan | Pan without recompute; change a weight → recompute |
| F19 | "No honest answer" state | Missing data produces that state, not a bad list | Empty-coverage mandate → explicit message |
| F20 | Reproducible and exportable | Mandate and result exportable and re-importable | Export → import → identical result |
| F21 | Never needs the network | Engine is fully on-device | Airplane mode: full mandate works |
| F22 | Consumer agreement | Ranked score and map pixel read the same stored cell | Sample a ranked candidate; pixel colour matches its score |

### G — Field truth, validation and learning · G1–G12 · phase W7

Purpose: make any future claim of accuracy earnable, and impossible to fake.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| G1 | Observations first-class | Found / not-found / searched recorded separately from owner finds | Schema and UI show three distinct records |
| G2 | Held-out protocol defined first | The validation split is specified before any learning | Written protocol committed before the learner changes |
| G3 | Spatial separation | Train and test cells are spatially separated | Split report shows the separation distance |
| G4 | Minimum-sample gate | Training refused below a stated sample count | Attempt to train below the gate → refused |
| G5 | Improvement gate | Learned weights ship only on held-out improvement | Report shows baseline vs learned on held-out data |
| G6 | Rollback | One action restores the published baseline | Roll back; scores match the baseline exactly |
| G7 | Learner negative control | Shuffled labels do not improve the held-out score | Shuffled labels → no improvement; the build fails if there is |
| G8 | No calibration without data | No accuracy or probability claim without held-out presence/absence | Copy audit finds no such claim |
| G9 | Learned-weight provenance | Sample size, date and method recorded | UI and docs show the provenance |
| G10 | No self-fulfilling loop | Recommendations never enter training | Pipeline audit: training reads observations only |
| G11 | Searched-nothing suppression | A searched, empty slope is not re-recommended | Record it; it drops out (same path as F11) |
| G12 | Correction without erasure | A field note can be corrected, the original kept | Edit a note; the original remains visible |

### H — Offline, device and release · H1–H14 · phase W8

Purpose: make the app survive a ridge with no signal, and a store submission.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| H1 | Bounded area download | The user chooses a bounded field area before prefetching | Download respects the drawn boundary |
| H2 | Size estimate | Storage expected is shown before committing | Estimate within a stated margin of the actual |
| H3 | Pause and resume | A manifest persists; an unfinished download resumes | Kill mid-download, reopen, it resumes |
| H4 | Offline coverage outline | The saved area's complete-data extent is drawn | Screenshot; the outline matches the manifest |
| H5 | Cache health check | Missing tiles detected before leaving | Health check flags a deliberately deleted tile |
| H6 | Area management | Rename and delete without touching finds | Delete an area; finds intact |
| H7 | Offline app shell | Startup assets available without a connection | Cold start in airplane mode |
| H8 | Storage warning | Warn before eviction removes a saved area | Force eviction; warning shown first |
| H9 | Room migrations | Every schema change ships a migration | Upgrade an old database in a test; no data loss |
| H10 | Airplane-mode acceptance | A real device run covering startup, map, inspection and recording | Device run log with screenshots |
| H11 | APK size budget | Release APK under a stated budget | `assembleRelease` size recorded |
| H12 | Release hardening | R8 rules for every SDK, no debug keys, no verbose logs | Release build: no key present, logs quiet |
| H13 | Permissions and foreground service | Minimum permissions; Android 14+ foreground service types declared | Manifest audit; service runs without a compliance warning |
| H14 | Crash reporting without location | No fix, find or track in any crash payload | Payload fixture inspected; no coordinates |

### I — Gaps, mutations and counter-candidates · I1–I22 · every wave

Purpose: the deliberate attempts to break the above, plus the gaps nobody has claimed yet. One
counter-candidate runs with **every** wave; the rest are assigned as their phase arrives.

| ID | Candidate | What must be true | Witness |
|---|---|---|---|
| I1 | **Counter-candidate: is 3D needed at all?** | Remove the mesh backend in a branch and ask whether the flat map answers the field questions | A written answer with the work it saves or costs — before A2 |
| I2 | **Counter-candidate: the merge is a rewrite** | Measure the true cost of the merge before committing to A2–A8 | A cost estimate with file counts, produced in W1 |
| I3 | Camera sign-flip mutant | A bearing or tilt sign flip is caught | Oracle rejects the mutant |
| I4 | DEM offset mutant | A dropped Terrarium offset is caught | Oracle rejects the mutant |
| I5 | Duplicated-hotspot mutant | Two identical results are caught | Separation oracle rejects |
| I6 | Smuggled-coordinate mutant | A precise fix in an AI payload is caught | Privacy oracle rejects |
| I7 | Invented-coordinate mutant | A new coordinate in an AI reply is caught | Contract validator rejects |
| I8 | Constant-score ranker mutant | A ranker that returns a constant fails | Ordering and determinism tests fail |
| I9 | Short-list-without-reason mutant | Returning fewer than N silently fails | Mandate oracle rejects |
| I10 | Induced mesh crack mutant | A crack is found | Crack detector fails the build |
| I11 | Skirt-removal mutant | A boundary discontinuity is detected | Continuity test fails |
| I12 | Label-behind-terrain mutant | An incorrectly un-occluded label is flagged | Occlusion oracle rejects |
| I13 | Texture-leak mutant | An eviction that leaks is caught | Counter reports non-zero |
| I14 | Truncated-manifest mutant | A partial offline manifest is detected | Manifest check rejects |
| I15 | Out-of-season mutant | An out-of-season date is rejected | Season oracle rejects |
| I16 | **Standing gaps with no owner yet** | Device matrix; licence register for every asset and dataset; accessibility audit; localisation; privacy policy; reproducible build; crash-free-session metric; behaviour when the owner's API key is revoked or the provider changes terms | Each gap is either claimed as a new candidate or published as an accepted open item — never left implicit |
| I17 | **Pre-existing lint error (found at bootstrap)** | `StateFlowValueCalledInComposition` at `ui/MainScreen.kt:120` is gone: the 3D view no longer reads `vm.camera.value` during composition | `./gradlew :app:lint` reports 0 errors. Expected to close inside A.1, whose `CameraState` replaces that line; if A.1 leaves it, I17 runs on its own |
| I18 | **A stale last-known fix captures the first-fix landing** | The first-fix jump (`FieldViewModel.onFix`, `CameraStart.landing`) waits for a fresh fix; a cached last-known location (Play services' `lastLocation`, possibly hours old and far away) lands only provisionally and does not consume it | A test feeding an old, distant fix and then a fresh one lands on the fresh one. *Re-diagnosed in A.3:* the emulator's "California" landing was its default GPS position delivered live (the AOSP image has no Play services, so `lastKnown` is empty), not this defect; the device half needs a Play-services phone |
| I19 | **A DEM tile id in logcat locates the user (found in A.2)** | `DemTileStore` no longer logs `z/x/y` (a z15 tile is ~1 km): failures are counted and logged without the tile id (invariant: privacy, coarse cell only) | Grep: no tile coordinates in any log call; a log-capture test of a failed fetch contains no digits of the tile id |
| I20 | **The mutation harness can leave a live mutant, or aim at the wrong class (found in A.2, A.3)** | `tools/mutate.py` refuses duplicate mutant ids (A.2 reused R1–R10, which ran the old hydrology R1–R3 instead), restores the file on SIGINT/SIGTERM (a killed run left `RadiusScan.kt` mutated; caught by `git status`), and checks that each named test class contains tests at all and fails on the mutant for a reason it names: twice (S4 in A.2, U2 in A.3) the killing test sat in a second class of the same file and never ran | Two mutants with one id fail before any edit; `kill -TERM` mid-run leaves `git status` clean; a mutant whose class holds no test that touches the mutated code is reported, not counted |
| I21 | **Your position is not drawn on the 2D map (found in A.2, pre-existing)** | The flat map shows the `g-me-layer` dot wherever the GPS chip shows a fix. On the emulator it was missing in every kept 2D screenshot of the A.1 and A.2 builds (`A.2-device-02`, `-03`, `-05`, `-10`, `-12`: after "Centre on me" the map centres on the fix and no dot is drawn), yet a screenshot of a superseded A.2 build (deleted with that run's evidence) showed it, so it is intermittent. Suspect: `pushData` records the position key before the source accepts the data, so a push lost to a style (re)load is never retried while the phone stands still (the GPS drops repeats under 2 m) | A test (or a device run) in which the style loads after the first fix still draws the dot; on device, after "Centre on me", the dot sits at the screen centre. A field defect: fix before F.1 |
| I22 | **MapLibre's renderer finalizer can outlast Android's 10 s watchdog (found in A.3)** | When a map is destroyed under load its renderer is released by a finalizer; on A.3's device run that took over 10 s and Android killed the app (`FinalizerWatchdogDaemon: MapRendererFactory$1.finalize() timed out`). Rotation no longer destroys the map (A16); finishing the activity still does: the map should be released deterministically before the view is dropped, not left to the finalizer | A device run that leaves and re-enters the app under load ten times without a watchdog kill; logcat shows the renderer destroyed on the main thread, not in `FinalizerDaemon` |

### J — Owner-directed goal candidates · J1–J24 · proposed (J1–J9 at bootstrap, J10–J14 in A.1, J15–J19 in A.2, J20–J24 in A.3)

Purpose, in the owner's words: put a digger in an almost unfair position to find big ginseng and,
hopefully, a honey hole. Drafted under the owner's standing rule that every iteration proposes at
least five ideas. Each is grounded in a bootstrap measurement or a cited study, and names the code it
reuses before anything new is written. **Status: `proposed`.** The owner is the sole approver of
scope (header), so none is scheduled until approved. Recommended placement: wave **J.1 = J1–J4**
right after F.1, because they feed the top-N engine; J5 and J6 with D.1; J7 with B.1; J8 and J9 are
§9 decisions.

| ID | Candidate | What must be true | Witness | Reuse / source |
|---|---|---|---|---|
| J1 | **"Check this spot": wire the field checklist** | Slope third, overstory trees and companion plants scored on the spot (`HabitatChecklist`), saved as an observation beside the cell's stored factors (feeds G1) | A fixture checklist gives the documented verdict; flipping one favourable tree to unfavourable lowers it (negative control) | Own unwired code: `habitat/HabitatChecklist.kt` (213 lines), `assets/data/companion_plants.json` (13 plants with images). Needs an observations table, so a Room migration (H9, a §9 decision stated in the design) |
| J2 | **Calcium indicators from the literature** | Rattlesnake fern, white ash, baneberry, spleenwort and wild ginger added to the checklist as a separate calcium sub-score, with the citation on screen | The calcium sub-score rises only when a calcium indicator is ticked; ticking a non-indicator leaves it unchanged | Burkhart 2013, *Agroforestry Systems* (Pennsylvania: white ash, jack-in-the-pulpit, rattlesnake fern mark ≥ 3,360 kg/ha calcium). Text only; new plant photos are blocked(licence) until sourced |
| J3 | **Big-plant ground** | The owner's finds weighted up by size (max prongs, plant count) in the finds heat and as learner sample weights. Never down: no find ever counts for less than it does today | A 4-prong find outweighs a 1-prong find at equal distance; a size-blind mutant fails | Data already recorded (`Find.maxProngs`, `plantCount`); `learn/FindLearner.kt`, the finds heat layer in `ui/map/FieldMap.kt`. Grounded in McGraw 2001 (*Biological Conservation*): plant size declined under harvest, so big plants mark where pressure was low |
| J4 | **Terrain twins of your honey hole** | Pick a find (default: the biggest); rank the radius's places by distance in stored-factor space to that find's signature, under F5's separation rule. Labelled "looks like your patch", never a prediction | On a synthetic landscape with a planted twin of the find's factor vector, the twin ranks first; with the factor columns shuffled it does not (negative control) | `research/RadiusScan.kt` grid, `terrain/GinsengSuitability.kt` factors. Deterministic and on-device; no AI (invariant 7) |
| J5 | **Harvest-pressure refuges** | Walking effort from the nearest road or trail by Tobler's hiking function over the DEM (grid Dijkstra), shown as a layer; refuges are strong habitat with high effort | On a synthetic slope, cost rises with distance and steepness exactly per Tobler's formula; a slope-blind mutant fails | Tobler 1993 formula (no code needed); priority-queue pattern from `terrain/Hydrology.kt`; roads from the already-saved OpenStreetMap vector tiles (ODbL, the licence the basemap already carries: confirm analytic use, §9) |
| J6 | **Sky-view factor and cove shade from the DEM** | Sky-view factor computed from the terrain (horizon angles in 16 directions), replacing the constant 0.85 in `AstroEphemerisEngine`; deep shaded coves read as such | Matches the closed-form sky-view factor of an analytic V-valley within tolerance; a flat-horizon mutant fails | Port the algorithm of RVT_py (`rvt.vis`, Apache-2.0, Kokalj et al.), with attribution in `THIRD_PARTY_NOTICES.md`. Grounded in the 70–90 % shade optimum (USDA Forest Service, 2011) |
| J7 | **Counter-candidate: strike or wire the dead engines** | Each unwired engine (§4 last row, 1,636 lines) is either wired with evidence or deleted. The "nocturnal moonlight" score in `AstroEphemerisEngine`/`EsiMatrixModel` has no cited evidence and must not ship (invariants 2 and 5). The bundled ONNX habitat model's training provenance must be found, or the model struck | For each engine, a written verdict citing its evidence or the lack of it; APK size before and after | Deleting code is a §9 decision; this candidate prepares that decision rather than taking it |
| J8 | **Soil survey for the saved area** | `soil/SoilSuitability.kt` (coded for the NC mountains) fed from USDA SSURGO for the saved 10 miles, offline after download | A fixture map unit gives the documented verdict | USDA Soil Data Access (public domain). **A new network destination: blocked(owner decision, §9)** |
| J9 | **Partner sync without a server** | The two diggers exchange finds and observations by QR code, phone to phone, encrypted, owner-initiated, with no network | A round trip reproduces the records exactly; a tampered code is rejected | Offline only. Moves finds between devices, so it needs the owner's explicit yes (invariant 6) |
| J10 | **Deer-browse refuges (a hypothesis layer)** | Interior depth from field and clearing edges (OpenStreetMap landuse in the saved tiles) plus steep or rocky ground, shown as a *hypothesis*. Never folded into the score until the owner's own browsed or not-found observations (G1) support it; the copy says so | On a synthetic forest with one clearing, interior depth rises monotonically away from the edge; a mutant that ignores the clearing fails | Grounded in McGraw & Furedi 2005, *Science* 307:920 (deer browse threatens ginseng more than harvest; none of 36 surveyed populations viable). **The edge-to-browse link is UNVERIFIED**, so it ships as a hypothesis or not at all (invariant 5, G8) |
| J11 | **Walk-the-band** | For a ranked place, the elevation band its strongest cells occupy, drawn as the two contours bounding that band across the cove: a walking line along the slope, the way diggers work a cove | On a synthetic cove whose best cells lie at 700–720 m, the route is exactly the 700 m and 720 m isolines clipped to the cove; a band computed from the wrong cells fails | Reuse `terrain/ContourLines.kt` (the maplibre-contour port) and the engine's stored cells (F3) |
| J12 | **Honey-hole detector over your own finds** | The owner's finds clustered on the device (a patch is finds within tens of metres); a patch with many plants and mostly 3-prong-or-better plants is flagged as a *honey hole* and ranked by mature-plant count. Never leaves the phone | A synthetic set with one dense mature patch and scattered 1-prong finds flags exactly that patch; a prong-blind mutant fails | Reuse the find clustering already in `learn/FindLearner.kt`; `Find.plantCount`/`maxProngs`. Grounded in the prong-age relation (3 prongs ≈ 4+ years, the usual legal threshold) |
| J13 | **Look-alike guard in the Find sheet** | A short identification check before saving (palmately compound leaves on one stalk, 3–5 serrated leaflets per prong, the berry cluster at the fork), contrasted with the common look-alikes. Text only; no model, no dependency | Saving a find shows the check; the check's wording is reviewed against a state identification guide, cited on screen | PA DCNR *American Ginseng Identification* (2018). Improves the record that every learner and heatmap trusts |
| J14 | **Seed your own honey hole** | A "planted seeds" record (date, count, place) with a maturity clock (~4–5 years to three prongs) and a reminder; suggested sites are the engine's top-ranked ground near the owner's finds. Planting on someone else's land needs their permission, said on screen | A planting record round-trips through the database; the clock reports the expected 3-prong year | New record kind, so a Room migration (H9, a §9 decision with the migration stated). Long-game: turns today's best ground into a future patch |
| J15 | **Canopy gate** | Cells under open or thin canopy are marked as poor habitat, from the USFS/NLCD Tree Canopy Cover raster (30 m, percent canopy) for the saved area, offline after download. The terrain score is untouched: the gate is a separate, visible layer and mask, and its threshold is the owner's to set from J6's cited shade optimum | A fixture patch of low canopy is masked and one of closed canopy is not; a mutant reading canopy inverted fails | NLCD Tree Canopy Cover, USDA Forest Service, published as public domain (MRLC; USFS raster gateway). **A new dataset and network destination: blocked(owner, §9)**; licence re-checked at adoption. Grounded in Canada's *Recovery Strategy for the American Ginseng* (2018, read in A.2): the species is "intolerant to larger openings in the canopy" and occupies mature, "closed-canopy" forest |
| J16 | **Slope band, cited and pinned** | The model's slope response is checked against an occurrence band read from a primary source, and the citation shown in the factor read-out. If the weights put the optimum outside that band, the discrepancy goes to the owner; it is not silently "fixed" | A test with the cited band as witness: a slope inside the band beats slopes below and above it; an inverted-band mutant fails | `terrain/GinsengSuitability.kt` factors. **The source is UNVERIFIED:** a "slopes 10–40 %" figure appeared in a search summary, but the Canadian recovery strategy it was attributed to does not contain it (checked in A.2). No number is used until a primary source is read (invariant 5) |
| J17 | **The last 3D square stays warm** | Leaving 3D keeps the built scene (one square, its mesh and one texture) in the ViewModel under a memory ceiling, so returning to the same place is instant instead of a full rebuild (measured in A.2: 12 min on the unaccelerated emulator; seconds on a phone) | Re-entering 3D at the same tile shows the terrain without a "Tracing creeks…" status; a memory counter shows one scene held, released on trim-memory | `Terrain3D.Scene` as built; feeds A13 (one eviction policy) |
| J18 | **Gesture injection for device gates** | A committed script drives two-finger tilt, pinch and rotate on the emulator's multitouch device (`sendevent`, protocol B), so the visual witnesses that need a gesture (A11 at 60°, A15, A17, A18) are reproducible by anyone | The script tilts the 3D view by a requested amount; the before/after screenshots differ in pitch as requested | Linux multitouch protocol B; the emulator's `virtio_input_multi_touch` device (found in A.2) |
| J19 | **A paper backup of the day's plan** | One button writes the day's top places (rank, bearing and distance from the car, elevation band, the cove's contour snippet) to a local PDF the owner can print or keep offline: a dead phone on a ridge still has the plan. Owner-initiated, on the device, no network | The PDF lists exactly the engine's places in rank order with their stored factors; a reorder mutant fails | Android `PdfDocument` (platform API, no dependency); the engine's output (F-group) and `terrain/ContourLines.kt` |
| J20 | **Strong ground you have not walked** | A layer: habitat cells above the owner's threshold minus the ground within ~15 m of every recorded track (the "visited" data already stored). The digger sees at a glance which strong slopes are still unsearched: the honey hole is more likely where nobody (including the owner) has looked | On a synthetic landscape with one strong patch half crossed by a track, exactly the unwalked half is shown; a mutant that ignores the track shows all of it | `terrain/SuitabilityRasterizer.kt`, the stored `TrackPoint`s, the VISITED layer's data. On device, no network; no AI |
| J21 | **The way back to the car** | A chip with the bearing and distance to where today's track began (or a pinned "car" point), always on screen while recording: in a hollow with no signal, the way out is the first safety feature a digger needs | Bearing and distance agree with the great-circle formula on fixtures (within 1° and 1 %); a mutant using the last point instead of the first fails | `android.location.Location.bearingTo`/`distanceTo` (platform), the recorder's session start. Local only |
| J22 | **Photos of a find, location stripped** | A photo attached to a find, stored in app-private storage with its EXIF GPS tags removed (the find already carries its place; a shared photo must not), shown in the find sheet and usable as field truth (G1) | A fixture JPEG with GPS tags comes out with none and with its pixels unchanged; a mutant that keeps the tags fails | Platform `android.media.ExifInterface` (no dependency); the camera through the system picker (no permission). A Room column for the photo path: a migration (H9, §9 decision stated in the design) |
| J23 | **Honour "remove animations"** | When the phone's animator scale is 0 (Accessibility → Remove animations), the 2D/3D switch cuts instead of fading and the relief appears at once; the map follows moves without animation | With the scale at 0 the transition completes in one frame; a mutant that ignores the setting animates | `Settings.Global.ANIMATOR_DURATION_SCALE` (platform); `MainScreen`'s reveal. Closes part of I16's accessibility gap |
| J24 | **A battery field mode** | Below a battery level the owner sets (default 25 %), the habitat raster drops to the next coarser size, the 3D view is offered but not pre-warmed, and the map is capped at 20 fps, with a chip saying so | With the level faked below the threshold, the raster size and frame cap change and the chip shows; above it nothing changes (negative control) | `BatteryManager` (platform); `DemTileStore.rasterSizeFor`, `MapView.setMaximumFps`. Program B's "a phone on a ridge for four hours"; the real saving needs a real phone (C18) |

### Register arithmetic

| Group | Range | Count | Phase |
|---|---|---|---|
| A — One map | A1–A18 | 18 | W1 |
| B — Reliability | B1–B20 | 20 | W2 |
| C — Performance | C1–C18 | 18 | W3 |
| D — Comprehension | D1–D18 | 18 | W4 |
| E — AI layer | E1–E22 | 22 | W6 |
| F — Top-N engine | F1–F22 | 22 | W5 |
| G — Truth and learning | G1–G12 | 12 | W7 |
| H — Offline and release | H1–H14 | 14 | W8 |
| I — Gaps and mutations | I1–I22 | 22 | every wave (I17 at bootstrap; I18–I21 in A.2; I22 in A.3) |
| **Total** | | **166** | |
| J — Owner-directed goal candidates | J1–J24 | 24 | proposed; not counted until the owner approves scope |

**Phase order and why.** A before B: everything draws through the one map, so building the renderer
before the merge means building it twice. B before C: a reliable renderer can be profiled; a fast
flickering one cannot be believed. D after C: comprehension is worth nothing on a pipeline that drops
frames. F before E: the AI reads the engine's output, so the output must exist and be deterministic
first. G after F: learning needs observations, and observations need a reason to be recorded. H last,
because release readiness on a moving app is wasted work. **I is not a phase** — one counter-candidate
per wave, always, recorded with its rejection.

---

## 12 — Wave state (rewritten at every wave close)

**Bootstrap:** `done` 2026-10-03 at `c95815f`. See `docs/eincol/run-log.md` (BOOT).
**Last wave:** `A.3` **archived** 2026-10-04 (`docs/eincol/waves/A.3.md`).
**Cursor:** `W2 / B.1`, B1–B7: rendering reliability. W1 (one map) is done but for what is blocked
on the owner (A5, A6, A12, A14) or on a device that can rotate (A16) and A18's fourth statement.
**Device:** the `aosp` AVD (API 34, x86_64, swiftshader, 4 GB / 4 cores, cold-booted after the
container restarted). Works: installs, taps, swipes, two-finger gestures through the emulator console
(`adb emu event send … EV_ABS:ABS_MT_* … EV_SYN:0:0`), screenshots, recording at 2–4 fps,
`am send-trim-memory`. Does not: the network (this container's TLS proxy); rotating this app (the
emulator host crashes, A.3); Play services (no `lastLocation`, so I18's device half). Performance
claims still need a real phone (C18).
**Blocked:**
- C1–C18 blocked(device: performance claims need a real phone, C18);
- A5 + A6 blocked(owner): decision 4 below;
- A12 blocked(owner): decision 5 below; and blocked(device) for its network-log check;
- A14 blocked(owner): decision 2 below;
- A16 blocked(device): the rotation check (the code is in: in-place rotation, the held square);
- J8, J15 blocked(owner: new network destination or dataset); J9 blocked(owner: finds leave the device).

**Partial:** A18, statement 4 (a device toggle of every Layers switch in 3D).

**Owner decisions pending:**
1. Approve the J candidates' scope (J1–J4 recommended as wave J.1 after F.1; J15–J24 new).
2. **A14 is infeasible as written** (one frame clock needs MapLibre `CustomLayer`, NDK). Proposed
   replacement: "the hidden backend's render loop is paused, and both run only inside the hand-off
   window". A.2 built most of it (the covered map follows nothing; the 3D view is disposed once out).
3. The branch rule for waves: `eincol/<wave-id>` is pushed and the session branch is
   fast-forwarded to it.
4. **A5 + A6:** *"Should tilting the flat map to its 60° limit enter 3D (and the 3D view stop at
   60° on the way back), so the 2D/3D button can be removed; or does the button stay?"* The
   gesture injection A.3 got working makes the device test of either answer possible.
5. **A12:** *"Approve declaring `com.squareup.okhttp3:okhttp` (already inside the APK via MapLibre,
   MapLibre's version: no new bytes) as a direct dependency, so one interceptor serves MapLibre's
   hillshade tiles from the app's DEM cache?"*

**Pre-existing, exempt by name:** the lint warnings remaining from `BOOT-lint-baseline.tsv` (79 now;
none above its per-file counts); the copied `GLTextureView.java` (`app/lint.xml`); `MapLogging` and
three log lines (`@SuppressLint`/`//noinspection`, named in the A.2/A.3 records).
**Counts:** 19 archived (A1, A2, A3, A4, A7, A8, A9, A10, A11, A13, A15, A17, I2, I3, I4, I17,
I18, I19, I21) · 23 blocked (C1–C18 device; A5, A6, A12, A14 owner; A16 device) · 1 partial (A18) ·
123 queued · 24 proposed (J1–J24, awaiting the owner's scope approval). Total 166 + 24 proposed.

| Wave | Program | Candidates | Status | Branch / commits | Evidence |
|---|---|---|---|---|---|
| BOOT | bootstrap | — | **archived** | `claude/minimal-3d-llm-location-app-yk0lid` | `docs/eincol/run-log.md#boot` |
| A.1 | One map | A1–A4 (+ I2, I3, I4, I17); A5, A6 → A.2 | **archived** | `eincol/A.1` | `docs/eincol/waves/A.1.md` |
| A.2 | One map | A7–A10 (+ A11 at 50°); A5, A6, A12 blocked(owner); A11's 60° shot blocked(device) | **archived** | `eincol/A.2` | `docs/eincol/waves/A.2.md` |
| A.3 | One map | A13, A15, A17 + A11 (from A.2); A18 partial; A16 blocked(device); A14 blocked(owner); I18, I19, I21 | **archived** | `eincol/A.3` | `docs/eincol/waves/A.3.md` |
| B.1 | Reliability | B1–B7 | queued (cursor) | — | — |
| B.2 | Reliability | B8–B14 | queued | — | — |
| B.3 | Reliability | B15–B20 | queued | — | — |
| C.1 | Performance | C1–C6 | blocked(device) | — | — |
| C.2 | Performance | C7–C12 | blocked(device) | — | — |
| C.3 | Performance | C13–C18 | blocked(device) | — | — |
| D.1 | Comprehension | D1–D6 | queued | — | — |
| D.2 | Comprehension | D7–D12 | queued | — | — |
| D.3 | Comprehension | D13–D18 | queued | — | — |
| F.1 | Engine | F1–F6 | queued | — | — |
| F.2 | Engine | F7–F12 | queued | — | — |
| F.3 | Engine | F13–F18 | queued | — | — |
| F.4 | Engine | F19–F22 | queued | — | — |
| E.1 | AI layer | E1–E6 | queued | — | — |
| E.2 | AI layer | E7–E12 | queued | — | — |
| E.3 | AI layer | E13–E18 | queued | — | — |
| E.4 | AI layer | E19–E22 | queued | — | — |
| G.1 | Truth | G1–G6 | queued | — | — |
| G.2 | Truth | G7–G12 | queued | — | — |
| H.1 | Release | H1–H7 | queued | — | — |
| H.2 | Release | H8–H14 | queued | — | — |
| J.1 | Goal (proposed) | J1–J4 | proposed: the owner approves scope | — | — |

---

## 13 — First action

**Wave A.1 — `A1–A6`: the one camera.**

Run §1 Bootstrap first if §4 is still unresolved. Then survey `Terrain3DView.kt`, `FieldMap.kt`,
`CameraStart.kt` and `terrain3d/` in full and count them — do not trust §4's numbers. Then introduce a
single `CameraState` as the sole source of truth, make both backends read it, and add the conformance
test (A4) that asserts the two backends agree on a set of control points at ten sampled states. **No
visual change is expected in this wave** — it is the spine, and the seam is removed in A.2.

Before implementing, work the tail: **the merge is a rewrite in disguise and should be refused.** Write
that reading down, work it, and record what it turned out to be (candidate I2). It was partly right
last time, and finding out which part is the first real deliverable.