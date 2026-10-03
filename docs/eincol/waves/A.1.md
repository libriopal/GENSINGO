# Wave A.1 — the one camera (exe.md §13)

Candidates claimed: **A1, A2, A3, A4**, plus counter-candidate **I2**, mutants **I3** and **I4**,
and the pre-existing lint error **I17**. **A5 and A6 are re-queued to A.2** (reason in phase 4).
Branch `eincol/A.1`, off the session branch at `b519d1f` (the default branch plus the bootstrap
record this wave updates).

## 1 — Load (measured from the tree)

| What | Measured |
|---|---|
| Live camera holders | **Three.** (1) MapLibre's own camera in `ui/map/FieldMap.kt`: the initial position is written inline (L336); the first-fix jump, recentre and focus all animate it directly. (2) `ui/Terrain3DView.kt` L141, `var cam by remember { mutableStateOf(camera) }`. (3) `FieldViewModel._camera: ViewCamera?`, written only when the 2D camera goes idle and when a 3D gesture ends |
| Hand-across plumbing | `MainScreen`: `jumpCam`, `jumpTick`, `recenter` (tick), `vm.camera.value` read during composition (the I17 lint error, L120). `FieldMap`: `onCameraIdle`, `jumpTo`, `jumpTick`, `recenterTick`, `focus`/`onFocusHandled`, `st.centred`/`st.jumpTick`/`st.recenterTick`. `Terrain3DView`: `camera`, `onCamera`, `recenterTick`, `focus`/`onFocusHandled`, `moveTo`, `seenRecenter` |
| The camera seed | `ui/map/CameraStart.kt` (46 lines, tested by `CameraStartTest`) has **no caller in the app**. `FieldMap` repeats its literals (`35.55, -82.95`, zoom 9/14, tilt 50) inline |
| Web Mercator | **Five copies:** `MapCamera.mercatorX/Y` + inverses; `DemTileStore.lonToTileX/latToTileY/tileXToLon/tileYToLat`; `WaterLines.lngOfCell/latOfCell`; `Terrain3D.latOfEdge/lngOfEdge` (+ `Scene.elevationAt` through `MapCamera`); `RadiusScan.latOfRow/lngOfCol/colOf/rowOf` (private). Two formula families (`ln(tan(π/4+φ/2))` and `asinh(tan φ)`) |
| A live linear-latitude defect | `DemTileStore.Mosaic.latAtRow(row) = north + (south − north)·row/h`, used by `SuitabilityRasterizer` for the heat-load index. Rows are linear in Mercator y, not latitude, and it reads the row's top edge rather than its centre. This is the class of defect mutant O2 pinned in iteration 1 |
| Mesh vs projection witness | None. `MapCameraTest` checks `project` against invariants and the local-origin matrix against the absolute one. Nothing places mesh vertices through `mvpForMeshBuiltAt` (origin, scale, ground plane) and compares them with `project` at varied camera states |
| GL host | `GLSurfaceView` (`Terrain3DView` L327). Phase 5 measured that a `GLSurfaceView` over the map's `TextureView` blacks the screen |

## 2 — Distribution (tail first)

| p | Reading of "A.1: the one camera" |
|---|---|
| 0.30 | Mostly done already: `ViewCamera` exists; A1 is a rename, a non-null seed, and routing recentre and focus through it |
| 0.25 | The real work is demoting the two live cameras to **mirrors** of one app-owned state. MapLibre must keep a camera to render, so "no backend stores its own" can only mean "no backend's camera is authoritative" |
| 0.15 | A2 is the load-bearing part: five Mercator copies and a live latitude defect matter more for correctness than the camera plumbing |
| 0.12 | A5/A6 cannot land in A.1: removing the switch before a hand-off exists (A7/A8) removes the 3D capability (§9) or leaves a seam, and §13 says "no visual change in this wave" |
| **0.10** | **(tail) The merge is a rewrite in disguise (I2):** two surfaces (a `TextureView` map and a `GLSurfaceView` mesh) and two render threads; a cross-fade and "one frame choreographer" are impossible without changing the GL host or going native |
| **0.08** | **(tail) A.1 is unnecessary:** one camera value already crosses the switch, and more plumbing has no field value |

### Working the tail

**"A.1 is unnecessary": refuted, but partly right.** It is wrong because three measured defects
exist only because there are three cameras:
- the I17 lint error;
- recentre and focus implemented twice (once per view, with different zoom and tilt rules);
- `CameraStart`, the tested seed, is dead while `FieldMap` repeats its constants.

It is right that A.1 produces nothing visible, and §13 says so. Its value is fewer places to be
wrong and the first witness that the mesh agrees with the projection (A4).

**"The merge is a rewrite" (I2): measured, and partly right.**

| Later candidates | What they need | Rewrite? |
|---|---|---|
| A5, A6 continuous tilt, no switch | MapLibre cannot tilt past 60° (`MapLibreConstants.MAXIMUM_TILT`, measured in iteration 1), so above it only the mesh can draw. A hand-off at or below 60° is mandatory | No: a camera rule plus A7/A8 |
| A7, A8 measured hand-off, cross-fade | Both surfaces on screen at once with alpha. Phase 5 measured that `GLSurfaceView` over the map `TextureView` blacks the screen. **The fix is to host GL in a `TextureView`** (the `GLTextureView` pattern: AOSP-derived, permissively licensed implementations exist), keeping `TerrainGlRenderer`'s `Renderer` interface | No, **but it is the one structural change**: the GL host is replaced; the renderer survives |
| A9–A11 scene description, depth policy, drape | The mesh already drapes habitat, contours and water as a texture; MapLibre draws the same layers as style layers | Moderate |
| A12, A13 one tile store and ceiling | DEM tiles already come from one `DemTileStore` for both views; the basemap snapshot already uses MapLibre's own cache | **Mostly satisfied today**; reduces to verification |
| **A14 one frame choreographer** | MapLibre renders on its own thread with its own loop. Driving both from one clock needs `CustomLayer`, an NDK C++ host that Phase 5 found unreachable from Kotlin | **Infeasible as written.** Proposed replacement (owner decision): "the hidden backend's loop is paused, and both run only inside the hand-off window", which is measurable |
| A15–A18 | Depend on A5–A8 | — |

**Cost estimate (I2's deliverable).** About 12 files and roughly 1,000 changed lines for A2–A8:
- a new projection module and its 6 callers;
- `FieldMap`, `Terrain3DView`, `MainScreen`, `FieldViewModel`, `CameraMath`;
- one vendored `GLTextureView`.

The renderers, the mesh, the textures and the analysis are untouched. **Verdict: not a rewrite.**
A14 must be re-scoped, and the GL host swap is the single structural risk (A.2).

## 3 — Falsify (the reuse map, and what would break each claim)

| Claim | Counter-witness that would break it |
|---|---|
| After A1 there is one authoritative camera | Any code path that moves a backend's camera without going through the shared state: a recentre that animates MapLibre directly, a 3D `cam` variable, or a first-fix jump inside `FieldMap` |
| Backends cannot fight (no feedback loop) | A backend that re-applies its own reported gesture: the camera would stutter or never settle. The epoch rule must hold: gestures do not bump the epoch, app moves do, and backends apply only epoch changes |
| After A2 the Web Mercator maths exists once | A second copy of the forward or inverse formula anywhere in `main/` outside the module; or a caller whose outputs differ from the module's |
| `latAtRow` is exact after A2 | A difference between `latAtRow(r)` and the cell-centre latitude computed by the module |
| The mesh agrees with the projection (A4) | A sampled camera state at which a mesh vertex lands more than the tolerance away from `project` of the same point; **and** the oracle must be seen to fail when the mesh path is fed a stale camera (otherwise it cannot fail) |

**Reuse.**
- `CameraStart` becomes the live seed; it is not rewritten.
- `CameraMath.to2d`/`to3d` are reused for the switch.
- `MapCamera` keeps its API, delegating to the module.
- No open-source code is needed in A.1. A.2's `GLTextureView` is the first copy.

## 4 — Evaluate (the chosen design, and the rejected ones)

**Chosen: one app-owned `CameraState`, with backends as mirrors under an epoch rule.**
- `SharedCamera` holds `(camera, epoch)`.
- A backend under the user's finger calls `report(camera)`: the state follows and the epoch does
  not change.
- App actions call `move(camera, animate)`: the first fix (`CameraStart`), recentre, "Show on map",
  the 2D/3D switch (`to3d`/`to2d`), and the 3D fit or compass reset. These bump the epoch, and
  every backend applies a new epoch exactly once.
- MapLibre keeps the camera it needs for drawing, but it is seeded from the shared state and
  reports every move.

**Rejected, with reasons:**
1. **Make MapLibre's camera the source of truth and have 3D read it.** The 2D map is disposed
   while 3D is on screen (one view at a time today), so the truth would vanish with it. It also
   inverts the direction A.2 needs, where 3D draws above 60° and MapLibre cannot.
2. **Keep three cameras and synchronise them on every frame.** This is the feedback-loop design
   the epoch rule exists to avoid; two writers with no precedence stutter under a gesture.
3. **Write MapLibre's camera from the shared state on every frame (no epoch).** That calls
   `moveCamera` per frame while the user drags, fighting MapLibre's own gesture handling.
4. **A5/A6 in this wave.** Removing the switch before A7/A8 either drops the 3D view (a §9 stop:
   it removes a capability the owner relies on) or ships a jump at a mode change. §13 also says
   "no visual change". **Re-queued to A.2.**

## 5 — Design (files, contracts, acceptance checks, negative controls)

| Id | Change | Files | Acceptance check (observable) | Negative control |
|---|---|---|---|---|
| A1 | `CameraState` (renamed from `ViewCamera`), plus `SharedCamera` (state and epoch); the view model owns one `SharedCamera` seeded from `CameraStart`, with the first fix, recentre, focus and switch as `move`s | `terrain3d/CameraState.kt` (new), `terrain3d/CameraMath.kt`, `ui/FieldViewModel.kt`, `ui/MainScreen.kt`, `ui/Terrain3DView.kt`, and tests renamed | `SharedCameraTest`: `report` keeps the epoch, `move` bumps it; a backend applying only epoch changes applies each `move` once and never its own `report`; the switch gives `to3d`/`to2d`. Structural check (the directive's grep witness, not behaviour): no `mutableStateOf(camera)`, `jumpTick`, `recenterTick` or `onCameraIdle` camera plumbing remains | Mutant Q5: `report` bumps the epoch, which makes a backend re-apply its own gesture → the test fails |
| A2 | `geo/Projection.kt`, the one Web Mercator module; `MapCamera`, `DemTileStore`, `WaterLines`, `Terrain3D` and `RadiusScan` delegate to it; `Mosaic.latAtRow` becomes exact (cell centre) | those six files | `ProjectionTest`: analytic values (equator → 0.5; ±85.0511287798° → 0 and 1; lng −180 → 0); round trip under 1e-12 over a grid; **consumer agreement**: every caller equals the module for random inputs; `latAtRow` equals the cell-centre latitude | Mutant Q3: north/south reversal in the module. Mutant Q4: `latAtRow` linear again |
| A3 | `FieldMap` becomes a mirror: initial camera from the shared state; `addOnCameraMoveListener` → `report`; applies `move`s by epoch (jump, or animate when asked); the first-fix jump, recentre and focus code are removed from it | `ui/map/FieldMap.kt`, `ui/MainScreen.kt` | No `jumpTo`/`jumpTick`/`recenterTick`/`focus` parameters; the same `SharedCameraTest` protocol covers the logic; the device check of the hand-across is **blocked(device)** | Covered by Q5 |
| A4 | `CameraConformanceTest`: 10 sampled camera states over a synthetic hilly scene; 25 mesh vertices each placed through `mvpForMeshBuiltAt` (exactly what the vertex shader computes) vs `MapCamera.project` of the same geographic point and drawn height | test only | Maximum disagreement under the tolerance at all 10 states. **The oracle proves it can fail inside the test:** feeding the mesh path the previous state's camera must exceed the tolerance by a wide margin | Mutant Q1 (= I3): bearing sign flipped in `mvpForMeshBuiltAt` → fails |
| I4 | DEM offset mutant (A2 touches `DemTileStore`) | — | `RealTerrainTest` kills a dropped Terrarium −32768 offset | Mutant Q2 |
| I17 | The `vm.camera.value` read in composition disappears with A1 | — | `:app:lint` reports 0 errors | — |

## 6 — Implement (as built)

| Id | Built | Files |
|---|---|---|
| A2 | `geo/Projection.kt`: `x`, `y`, `lng`, `lat`, `worldPx`, `metresPerPixel`. The five copies delegate to it; `Mosaic.latAtRow` is exact (cell centre) | `geo/Projection.kt` (new), `terrain/DemTileStore.kt`, `terrain/WaterLines.kt`, `terrain3d/Terrain3D.kt`, `terrain3d/MapCamera.kt`, `research/RadiusScan.kt` |
| A1 | `terrain3d/CameraState.kt`: `CameraState` (renamed from `ViewCamera`), `SharedCamera` (`report`, `move`, `Snapshot`, `Follower`). `FieldViewModel` owns the one `SharedCamera`, seeded from `CameraStart`, which now has a caller. The first fix (a jump), `recenter()`, `focusOn()` (animated) and `setView3d()` (via `CameraMath.forView`) are app `move`s. The 3D view mirrors it: gestures `report`; the fit and compass `move`; an app move re-grounds the camera or rebuilds the square there; re-anchoring after a rebuild only when the user panned out | `terrain3d/CameraState.kt` (new), `terrain3d/CameraMath.kt`, `ui/FieldViewModel.kt`, `ui/map/CameraStart.kt`, `ui/Terrain3DView.kt`, `ui/MainScreen.kt`; tests renamed |
| A3 | `FieldMap` opens at the shared camera, reports every camera move (`addOnCameraMoveListener`, not only idle), and follows app moves once (`Follower`), jumping unless animation is asked. The first-fix jump, recentre, focus, `jumpTo`/`jumpTick`/`onCameraIdle` and its `centred`/`recenterTick`/`jumpTick` state are gone | `ui/map/FieldMap.kt`, `ui/MainScreen.kt` |
| A4 | `CameraConformanceTest` (10 states × 25 vertices, a stale-camera control inside the test) | test only |
| I4 | **Found by verification:** the app's Terrarium decoder had no test. The pixel formula became `DemTileStore.terrariumMetres`, pinned by `TerrariumDecodeTest` with spec vectors (sea level, Mount Mitchell 2,037 m, Badwater −86 m, a fractional metre) | `terrain/DemTileStore.kt`, `TerrariumDecodeTest` (new) |
| I17 | Gone: nothing reads `vm.camera.value` during composition | — |

**A5 and A6** were re-queued to A.2 (phase 4, rejection 4).

## 7 — Verify (raw output in `docs/eincol/evidence/A.1-*`)

| Check | Result | Raw output |
|---|---|---|
| `./gradlew :app:assembleDebug` | exit 0 (and again after the I4 fix) | `A.1-01-build.txt`, `A.1-06-rerun-build.txt` |
| `./gradlew :app:lint` | **exit 0**, from exit 1 at bootstrap. 0 errors (I17's `StateFlowValueCalledInComposition` gone), 78 warnings, against 80 at bootstrap. No new finding by (severity, id, file); two `LogNotTimber` warnings left with the removed first-fix log lines | `A.1-02-lint.txt`, `A.1-07-rerun-lint.txt` |
| `./gradlew :app:test` | exit 0. **350 tests per variant** in the first pass (339 + `SharedCameraTest` 4, `ProjectionTest` 5, `CameraConformanceTest` 2), and **355** after the I4 fix (+ `TerrariumDecodeTest` 5); 0 failures, 1 skipped per variant | `A.1-03-test.txt`, `A.1-08-rerun-test.txt` |
| Conformance (A4), measured | Worst disagreement **0.0003–0.0089 px** across the 10 states (243 points; tolerance 0.5 px). The stale-camera control gave **534–2,755 px**, so the oracle can fail | the `CameraConformanceTest` system-out, quoted in the run log |
| Mutants | **Q1 killed** (bearing reversed in the mesh matrix: I3). **Q2 survived, then killed** after the decoder got a test (I4). **Q3 killed** (north/south reversal in `Projection`). **Q4 killed** (`latAtRow` linear again). **Q5 killed** (a report bumps the epoch). Each restore was verified by the harness, and no mutant fingerprint remains in the tree | `A.1-04-mutants.txt`, `A.1-09-rerun-mutants.txt` |
| Structural witnesses (the directive's grep checks for A1/A2) | No second camera store and no hand-across plumbing in `ui/` or `terrain3d/`; the one `map.cameraPosition =` is the open-at-shared-camera line. No Web Mercator formula outside `geo/Projection.kt`; five callers import it | `A.1-05-structural.txt` |
| Device | **Not required for A.1's candidates after the re-queue.** §13: "no visual change expected"; A5 moved to A.2. The MapLibre side of A3 (move listener, follow by epoch) is covered by the protocol test, not by a screen. Its device check joins A.2's device run (A15, A18), which first retries the `aosp` AVD (bootstrap L6) | — |

**A reporting trap caught in verification:** after the rerun, the debug variant's result
directory showed "5 tests, 1 failed". That was the mutation harness overwriting `:app:test`'s XML
with its own Q4 run, where the failure *is* the kill. The suite's verdict is `:app:test`'s exit code,
plus the field and release variants' untouched results (355 each, 0 failures). This is the same
family of failure as Phase 8's falsy report.

## 8 — Re-evaluate

**What the evaluators found that the design did not:**
1. **Q2 survived.** The app's Terrarium decoder had no test, because the terrain fixtures were
   decoded by Python. Bootstrap §4 had asserted the opposite. Fixed, and §4 corrected.
2. **`CameraStart` was dead.** The tested seed had no caller; the map repeated its constants
   (found in Load). It is now the live seed.
3. **`latAtRow` was linear.** A sixth, wrong copy of the projection (found in Load).
4. **A14 is infeasible as written** (found in Load). It needs MapLibre `CustomLayer` (NDK).
   A replacement is proposed for the owner.

**Sovereignty classifier over this wave's claims:**

| Claim | In time | Against a witness | Verdict |
|---|---|---|---|
| One camera: views only mirror `SharedCamera` | ✅ the protocol test was written in phase 5, before the views were rewired | ✅ `SharedCameraTest` (Q5 killed) + the structural witness | sound |
| Web Mercator exists once and every caller agrees | ✅ | ✅ `ProjectionTest`, analytic values not from the code (Q3, Q4 killed) | sound |
| The mesh and the projection agree within 0.01 px | ✅ | ✅ `CameraConformanceTest`, with the stale control failing by ≥ 534 px (Q1 killed) | sound |
| The app's DEM decoder is right | ✅ the spec vectors predate the code | ✅ `TerrariumDecodeTest` (Q2 killed) | sound (it was **open** until this wave, and was claimed sound at bootstrap) |
| The 2D map follows app moves on a real device | ❌ | ❌ no device | **open**: blocked(device), joins A.2 |
| A14 needs NDK | ✅ Phase 5 measurement | ❌ not re-measured this wave | **open**: a proposal for the owner, not a verdict |

**Counter-candidate I2 (the merge is a rewrite):** answered in phase 2, with a cost estimate.
Not a rewrite. The structural risk is the GL host swap (`GLSurfaceView` → `TextureView`-hosted
GL) in A.2, and A14 must be re-scoped.
