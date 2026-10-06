# Wave M.1 — one map, in 3D: travel memory, battery, and J.2

**The owner's directive (2026-10-06), verbatim:** *"Continue and add a persistent gps travel memory
layer that shows everywhere you have been and remove the 2d map make 3d the main map with full
battery optimisation."*

This is the owner exercising §9 directly: deleting the flat-map path is a "delete or rewrite a
working subsystem" decision, and the owner has made it. It also answers two pending decisions:
**A5 + A6** (tilt into 3D, or keep the button): neither, there is no flat map to tilt from; **A12**
(OkHttp so MapLibre's hillshade reads the app's DEM cache): moot, the flat map's hillshade goes.
"Continue" runs the cursor, **J.2** (J20, J21, J23, J24), in the same wave, because two of its four
items are this directive's parts (J20 needs the travel memory; J24 is battery work).

Candidates claimed, all owner-approved:

| ID | Candidate | Source |
|---|---|---|
| J30 | **3D is the only map**: the flat map, its switch and the cross-fade are removed; the 3D view opens first, reaches the whole 10-mile radius through zoom levels, and shows ground within a second or two of a fix (relief first, habitat after) | this directive |
| J31 | **Persistent travel memory**: every fix the app receives is remembered as ~10 m cells, kept across sessions, drawn on the 3D ground as "where I've been"; the existing recorded tracks are folded in at upgrade | this directive |
| J32 | **Battery**: the on-screen location request follows battery, charging and stillness (it asked for a high-accuracy fix every 3 s for as long as the map was open); frames are paced; travel-memory writes batched | this directive |
| J20 | Strong ground you have not walked | J.2 |
| J21 | The way back to the car (to the start of the track) | J.2 |
| J23 | Honour "remove animations" | J.2 |
| J24 | A battery field mode | J.2 |

Seven, within §8's limit. Branch `eincol/M.1`, off the session branch at `9ee7dfd` (B.1's archive).

## 1 — Load (measured from the tree)

| What | Measured |
|---|---|
| The flat map | `ui/map/FieldMap.kt` 709 lines (MapLibre `MapView` + style layers for habitat raster, water, contours, hillshade, visited heatmap, track, finds, ring, suggestions, your position), hosted by `MainScreen` under the 3D view with a cross-fade (`Handoff`, 127 lines, `HandoffTest`), a 2D/3D switch (`FieldViewModel.view3d`, `setView3d`, `landFlat`, `CameraMath.forView/to2d`), a 2D layer backend (`FlatLayers` in `SceneLayers.kt`) and a 2D↔3D projection check (`AlignmentCheck`, 89 lines, `AlignmentCheckTest`). Used outside it: `DARK_STYLE` (MapDrape, OfflineArea) and `ring()` (the 3D ring) |
| MapLibre after the flat map | Still needed, headless: `MapDrape` (a `MapSnapshotter` of the dark style draped on the 3D ground) and `OfflineArea.downloadBasemap` (the offline region that snapshot reads) |
| The 3D view as a main map | One square of 3×3 DEM tiles at zoom 15 (~3 km) around the camera; zoom clamped to that square's fit −1.5…+3.5; markers drawn only where the square has ground. The 10-mile radius (32 km) cannot be seen. A build is `Terrain3D.build` (scores, hydrology, texture) before anything shows: 12–15 min on this emulator (A.3, B.1), unmeasured on a phone |
| Where you've been | `track_points` holds only fixes recorded with Track on. The live position (every 3 s on screen) is never stored. The VISITED layer is a 2D heatmap of track points; in 3D it is the track line |
| Live location battery | `FieldViewModel.onVisible`: `PowerPolicy.plan(tracking=false, screenOn=true, batteryPct=null, charging=false, speedMps=null, stillForMs=0)`: always `VIEWING`, HIGH accuracy, 3 s, whatever the battery or however long the user stands still. `TrackService` already re-plans from real battery, screen and stillness |
| Frames | `RENDERMODE_WHEN_DIRTY`; every pointer event during a gesture requests a frame (up to the touch rate) |
| Database | Room schema 5, migrations 1→5 and 4→5, exported schema `schemas/…/5.json`; `MigrationTest` (Robolectric, real SQLite files) and `MigrationSqlTest` pin it |
| Offline | "Save 10 miles" fetches DEM zooms 11–13 for the radius and 14–15 around the user (`OfflineArea.demTiles`): every zoom level the 3D map needs is saved |
| Animations (J23) | The 3D view never animates an app move (`SharedCamera.Snapshot.animate` is read by nobody but the flat map); the only animation in the app is the 2D↔3D cross-fade |

## 2 — Distribution (tail first)

| p | Reading |
|---|---|
| **0.20** | **(tail) Removing the flat map takes a field capability with it** |
| **0.15** | **(tail) A travel memory that shows "everywhere" needs background location** |
| 0.20 | The owner wants one map that does everything the two did, on the terrain |
| 0.15 | "Full battery optimisation" means the GPS first: it is the largest cost the app controls |
| 0.15 | The travel memory is what makes "where have I not looked yet" answerable (J20) |
| 0.15 | Startup: a main map that shows nothing for a long build is a regression |

### Working the tail

**"Removing the flat map loses capability": right in four places, each answered or stated.**
(1) *The whole 10-mile radius*: the 3D square is 3 km. Answered: zoom levels; the square is built
from DEM zoom 15, 14, 13, 12 or 11 by the camera's zoom (3, 6, 12, 24, 48 km), so zooming out
shows the radius, its ring and every suggestion on terrain. (2) *Instant panning*: tiles stream
in a flat map; a 3D square must be built. Answered in part: relief first (mesh + elevation colour,
no habitat or creeks) then the full colour, and the old square stays on screen until the new one is
ready; stated: a new square costs CPU a flat map did not. (3) *The Topo basemap*: it was never
draped in 3D. It goes; the 3D ground draws contours and hillshade from elevation itself, and the
dark map's roads and names stay draped. (4) *Speed of the radius scan*: unchanged (it never used
the map). The battery trade is stated, not claimed: one GL surface instead of two and no vector
tile decoding, against a square's build per new area; only a real phone can measure which wins
(C18).

**"Everywhere needs background location": rejected.** Recording where the user goes while the app
is closed would need `ACCESS_BACKGROUND_LOCATION` or an always-running location service: a battery
cost the same message asks to cut, and a privacy line the app has never crossed. The memory fills
from every fix the app already receives: while the map is open, and while Track records in the
pocket (the foreground service it already has). The Track button is how a walk with the phone in a
pocket gets remembered; the layer says so.

## 3 — Falsify

| Claim | Counter-witness |
|---|---|
| Nothing outside `FieldMap.kt` needs the flat map except `DARK_STYLE` and `ring()` | `grep` for every top-level symbol of `FieldMap.kt` across main: done (§1) |
| Every zoom level's tiles are saved offline | `OfflineArea.demTiles`: z11–13 over the radius plus TPI margin, z14–15 over `DETAIL_M` |
| The old on-screen request never adapted | `onVisible`'s call site passes constants (§1) |
| A degree grid can be computed identically in SQL and Kotlin | SQLite `CAST(x AS INTEGER)` truncates toward zero; with `lat + 90 ≥ 0` and `lng + 180 ≥ 0` that is `floor`. A test runs the migration's SQL on real SQLite and compares every key with the Kotlin function |
| The 3D view never animates | `grep -n animate` in `Terrain3DView`: only `CameraMath.to3d(…, animate = !interactive)`, for the fade being removed |

## 4 — Evaluate

**J30 — levels, not tiles.** *Chosen:* one square at a time, its DEM zoom picked from the camera's
zoom (the level whose 3×3-tile square fits the screen), with hysteresis, rebuilt only after a
gesture ends; relief-first builds. *Rejected:* (a) a quadtree of tiles in 3D (B3's LOD): a rewrite
of the mesh path and of B2's guarantees, for a view that shows one area at a time; (b) keep the
flat map for zoomed-out views: the owner said remove it.

**J31 — cells, not points.** *Chosen:* a `visited_cells` table keyed by a 1/10,000° grid (≈11 m ×
9 m here), first and last time per cell; every fix with accuracy ≤ 30 m marks its cell and the
cells along the line from the previous fix (gaps under 150 m and 5 min), buffered and written in
batches; the upgrade folds in every stored track point with one SQL statement. Drawn as a GPU mask
(a small R8 texture over the square, sampled by the fragment shader), so a new cell updates a few
bytes instead of re-baking a 16 MB texture. *Rejected:* (a) store every fix as a track point: grows
without bound (a fix every 3 s is 1,200 rows an hour on screen) and redraws the line per frame;
(b) bake the memory into the colour texture: a full re-bake for every new cell; (c) background
location (§2).

**J32 — measure-driven GPS, paced frames.** *Chosen:* the on-screen request is planned from the
real battery level, charging state and stillness, and re-planned when they change; `PowerPolicy`
gains a still-while-viewing step (15 s, balanced accuracy after 90 s without moving); frames are
paced to 30 per second (20 in battery mode) with the last frame of a gesture always drawn.
*Rejected:* (a) a fixed slower interval: the live dot would lag a walking user; (b) stop location
when still: the travel memory and the find averaging need it.

**J20** — in the shader: the visited mask dilated by 15 m; where the "only unwalked ground" switch
is on, walked ground is greyed out so the habitat colour shows only where the owner has not
been. *Rejected:* a separate texture bake (cost as J31b).
**J21** — a chip while recording: distance and bearing from you to the first point of the current
session (the great-circle functions already in `Prospects`). *Rejected:* a pinned car point (a new
control and a new record kind; the start of the track is where the car is in practice).
**J23** — held by construction: with the cross-fade gone, nothing in the app animates (the 3D view
applies app moves at once). A source check keeps it so. *Rejected:* reading
`ANIMATOR_DURATION_SCALE` to gate animations that no longer exist (a dead control).
**J24** — battery mode when the level is at or below the owner's threshold (default 25 %, set in the
Layers sheet) and not charging, or when the system's battery saver is on: half the mesh density,
half the texture size, no map snapshot, frames at 20 per second, a chip saying so.
*Rejected:* automatic thresholds without an owner setting (J24 says the owner sets it).

## 5 — Design (files, contracts, witnesses, negative controls)

| ID | Change | Witness | Negative control |
|---|---|---|---|
| J30 | `MainScreen` hosts only `Terrain3DView`; `FieldMap.kt`, `Handoff.kt`, `AlignmentCheck.kt`, `FlatLayers`, `view3d`, `forView/to2d` deleted; `DARK_STYLE`, `ring()` moved; `SquareLevel` (pure): level from zoom with hysteresis, the TPI radius per level; `Terrain3D.buildQuick` | `SquareLevelTest`: each level's square fits the screen at the zoom that picks it; no flapping across a boundary; the radius is visible at the coarsest level. Device: the app opens in 3D; pinching out builds a coarser square that shows the ring | Y1: level ignores zoom (always 15) → the radius test fails; Y2: no hysteresis → the flapping test fails |
| J31 | `VisitedCell` entity, `VisitedDao`, schema 6, `MIGRATION_5_6` (create + backfill); `TravelMemory` (pure keys, lines between fixes, buffered recorder); `TravelMask` (pure: square → R8 mask); renderer's second texture; VISITED becomes a ground layer | `TravelMemoryTest`: keys equal SQLite's for the same points (Robolectric), a 100 m walk sampled every 30 s leaves no gap, a jump of 2 km leaves one; `MigrationTest`: a schema-5 file with track points opens at 6 with exactly their cells; `TravelMaskTest`: the mask marks exactly the cells inside the square | Y3: no line filling → gap test fails; Y4: the backfill uses a different grid → migration test fails; Y5: mask rows flipped north/south → mask test fails |
| J32 | `FieldViewModel` plans the live request from real battery and stillness and re-plans on change; `PowerPolicy` VIEWING_STILL; `FramePacer` | `PowerPolicyTest`: still on screen for 90 s → 15 s balanced; `FramePacerTest`: at most one frame per 33 ms, the trailing frame always scheduled | Y6: still-while-viewing ignored → test fails; Y7: the pacer drops the trailing frame → test fails |
| J20 | `TravelMask.dilate`, `unwalked`; shader mode | Synthetic strong patch half crossed by a track: exactly the unwalked half kept | Y8: dilation radius 0 → the 15 m buffer test fails |
| J21 | `WayBack` (pure); chip | Bearing and distance equal the great-circle values within 1° and 1 %; uses the session's first point | Y9: last point instead of first → fails |
| J23 | none (removal) | `NoAnimationTest`: no `Animatable`/`animate*AsState`/`AnimatedVisibility` in `ui/` sources | Y10: an `Animatable` re-added → fails |
| J24 | `BatteryMode` (pure); setting; chip; lighter build | On at or below the threshold unplugged, or with saver on; off when charging; the lighter grid and texture sizes | Y11: charging ignored → fails |

## 6 — Implement (as built)

| ID | Files | What changed |
|---|---|---|
| J30 | deleted `ui/map/FieldMap.kt` (709 lines), `terrain3d/Handoff.kt`, `terrain3d/AlignmentCheck.kt`, `test/…/HandoffTest.kt`; `ui/MainScreen.kt`, `ui/FieldViewModel.kt`, `ui/Terrain3DView.kt`, `terrain3d/CameraMath.kt`, `terrain3d/SquareLevel.kt` (new), `terrain3d/Terrain3D.kt`, `terrain3d/MeshSession.kt`, `ui/map/SceneLayers.kt`, `ui/map/MapLayers.kt`, `ui/map/Ring.kt` (new) | `MainScreen` hosts only `Terrain3DView`; the 2D/3D button, `view3d`, `landFlat`, the cross-fade, `CameraMath.forView/to2d` and the `FlatLayers` backend are gone. `SquareLevel.forZoom` picks the square's DEM zoom (15 … 11, 3 km … 48 km) from the camera's zoom, keeping the current level until the camera is more than half a level plus 0.25 from its fit; the zoom range runs from the coarsest square's fit − 1 to the finest's + 4. A build is two steps on one mesh: `Terrain3D.buildQuick` (mesh + relief, hillshade and contours: no scores, no creeks) is published at once, then `Terrain3D.build(…, mesh = quick.mesh)` colours it, with the level's own position-on-slope radius (`SquareLevel.tpiRadiusM`: 300 m at 15, 1,500 m at 11). `DARK_STYLE` moved to `MapLayers.kt`, `ring()` to `Ring.kt`. MapLibre stays, headless: `MapDrape`'s snapshotter and `OfflineArea` |
| J31 | `data/db/FieldMemory.kt` (`VisitedCell`), `Daos.kt` (`VisitedDao`), `AppDatabase.kt` (schema 6, `MIGRATION_5_6`), `schemas/…/6.json`, `field/TravelMemory.kt` (new), `terrain3d/TravelMask.kt` (new), `TerrainShaders.kt`, `TerrainGlRenderer.kt`, `AppContainer.kt`, `TrackService.kt`, `FieldViewModel.kt`, `Terrain3DView.kt` | A `visited_cells` table (cell, firstAt, lastAt). `TravelCells`: a 1/10,000° grid keyed `latIndex · 4,000,000 + lngIndex`, the same key in SQLite (`SQL_KEY`). `TravelMemory.cellsFor`: a fix vaguer than 30 m marks nothing; otherwise its cell and, within 150 m and 5 min of the previous accepted fix, every cell on the line between them (sampled every 3 m). `TravelRecorder` (one per process, fed by the map's live fixes and the track service's accepted points) writes in batches of 64 cells or every 30 s, and on leaving the screen; it keeps what this process added in memory so the map draws a new cell without reading the table. The upgrade creates the table and folds in every stored track point within 30 m with one `INSERT … SELECT … GROUP BY`. `TravelMask`: a 512² mask over the square's Web Mercator bounds (R = visited, G = the 15 m buffer), uploaded as an RG8 texture the fragment shader samples: a new cell re-uploads 0.5 MB instead of re-baking the 16 MB colour texture. VISITED is a ground layer (`MeshDraw.MEMORY`) |
| J32 | `field/PowerPolicy.kt`, `field/FieldPower.kt` (`FramePacer`), `ui/FieldViewModel.kt`, `ui/Terrain3DView.kt` | The on-screen request is planned from the real battery level, charging state and stillness (a still phone is re-checked every 30 s, since it gets no fixes past the request's minimum distance), and swapped only when the plan changes. `PowerPolicy` gains `VIEWING_STILL`: after 90 s within its own accuracy, a fix every 15 s instead of 3 s. **Changed from the design (§4 said "balanced accuracy"):** it keeps HIGH accuracy, because under canopy BALANCED falls back to cell towers whose 500 m fixes would never show the owner walking off again, and the plan would stay still while they walked. `FramePacer`: at most one frame per 33 ms during a gesture (50 ms in the battery mode), and the last request always drawn (a trailing frame) |
| J20 | `TravelMask.dilated`, `unwalked`; `TerrainShaders.kt`; `SceneLayers.kt` (`UNWALKED`) | The mask's G channel is the visited texels grown by a 15 m disk; with "Only show ground I haven't walked" on, the shader greys ground under the buffer so colour stays only where the owner has not been |
| J21 | `field/FieldPower.kt` (`WayBack`), `MainScreen.kt` | While Track records: a chip "Back to start: 1.2 km NE", the great-circle distance and bearing from the fix to the earliest point of the current session (`Prospects`' functions) |
| J23 | — | Nothing animates after the cross-fade's removal; `NoAnimationTest` scans every main source for the animation APIs |
| J24 | `field/FieldPower.kt` (`BatteryMode`), `AppContainer.kt` (`SettingsStore.batterySaverPct`), `FieldViewModel.kt`, `MainScreen.kt`, `Terrain3D.kt`, `TerrainTextures.kt`, `Terrain3DView.kt` | On at or below the owner's threshold (default 25 %, 10–50 % in the Layers sheet) unplugged, or with the phone's battery saver on: mesh cap 193 vertices per edge (385), texture cap 1,024² (2,048²), no map snapshot, 20 frames a second, and a chip saying so with the level. A change of mode rebuilds the square |
| I20 (more) | `tools/mutate.py` | `--check`: lists every mutant whose file is gone, whose text is not found exactly once, or whose test class does not exist, and exits 1. It found P6, H2 and H4 stale since earlier waves (fixed: retargeted to the current lines), S9 and D2 stale by this wave, and S1–S3 and U4 aimed at the deleted `Handoff.kt` (removed, with a note) |
| — | `tools/mutate.py` | Y1–Y20, the wave's negative controls (the design's Y1–Y11 plus Y12–Y20: the backfill's accuracy filter, the disk shape of the buffer, a vague fix as a line's start, writing on every fix, the canopy-fix filter, a late batch moving a visit back, cell-tower accuracy when still, the memory re-baking the texture, the schema-6 table) |
| J30 (from the device run) | `ui/map/CameraStart.kt`, `ui/FieldViewModel.kt` | A fresh first fix vaguer than 200 m (a phone's first fix is often a cell tower's) lands the camera only provisionally, so the GPS fix after it still lands. Found by the device run (phase 8, finding 1): with 3D the only map, a landing builds 3 km of ground, and a landing in the wrong place was never corrected. `CameraStartTest.aVagueFreshFixLandsOnlyProvisionally`, mutant Y21; U2 retargeted |
| J31 (from the device run) | `terrain3d/TerrainShaders.kt`, `ui/Terrain3DView.kt` (legend), `ui/MainScreen.kt` (sheet text), `test/…/ShaderSourceTest.kt` | The travel memory's wash was the app's blue, the creeks' colour: on the device a walk along a hollow read as a creek (phase 8, finding 2). It is now a pale wash of `Gen.Text` (#E6F4EC), a trodden trail, more than 0.3 (RGB distance) from every water colour, the track line's blue and the habitat green; `whereYouveBeenIsNotTheColourOfACreek`, mutant Y22. The palette itself is unchanged |
| J30 (from the device run) | `terrain3d/TerrainTextures.kt`, `test/…/WaterLevelOfDetailTest.kt` | A water line narrower than a quarter of a texel is not drawn: on the 48 km square a 2.5 m drain was painted at least 1.2 texels (~28 m) wide and the drains covered the ground (phase 8, finding 3). The 3 km square draws every kind as before; the 48 km one draws streams only; the reported lengths are unchanged. Mutant Y23 |
| J30 (cleanup) | `terrain3d/MapCamera.kt`, `test/…/MapCameraTest.kt`, `tools/mutate.py` | `mvpForMeshBuiltAt`'s `relief` scale existed only for the cross-fade and was passed by nothing: removed, with mutant S4; its test now pins that a vertex 400 m above the plane is drawn where `project` draws it (and not where the plane is). Comments citing the deleted on-device `AlignmentCheck` say it is gone and why nothing replaces it (nothing on screen is drawn by MapLibre's camera) |
| — | comments | Comments that still described the flat map as present (`MemoryBudget`, `CameraState`, `CameraMath`, `MapDrape`, `TerrainTextures`, `MeshSession`, the Layers sheet) say what is true now |

## 7 — Verify (raw output in `docs/eincol/evidence/M.1-*`)

| Check | Result |
|---|---|
| Build | `assembleDebug` **exit 0** on the final tree (`M.1-02-build.txt`); the x86_64 field build for the device runs and the arm64 field build delivered |
| Tests | `:app:test` **exit 0**: 437 per variant (debug, release, field), 0 failures, 1 skipped (`M.1-04-test.txt`; B.1: 403, +34) |
| Lint | `:app:lint` **exit 0**: 0 errors, 54 warnings (79 at B.1: the flat map's went with it), none above the bootstrap baseline by (check, file). Run 1 found `UseKtx` in `AppContainer.kt` one above (the new battery setting): the file's six settings writes now use core-ktx's `edit { }` (`M.1-03-lint.txt`) |
| Witnesses | SQLite's key equals the Kotlin key on 3,201 points, 201 of them on cell edges (`TravelMigrationTest`); a real schema-5 file with track points opens at 6 holding exactly their cells, first and last times, and none from a fix vaguer than 30 m; the DAO never moves a visit back; a walk leaves no gap between cells, a jump or a five-minute pause is not joined, a vague fix neither marks nor starts a line, the recorder writes in batches (`TravelMemoryTest`); the mask lines up with an independently written Web Mercator, north is row 0, the 15 m buffer is a disk, and a walk down a strong patch greys exactly the ground within 15 m (`TravelMaskTest`, J20's witness); each level is chosen at the zoom where it fits, no flip at a boundary, the 48 km square holds the 10-mile radius at 35.55° and 47° (`SquareLevelTest`); the way back against an independent haversine and bearing (`FieldPowerTest`); schema 6 equals Room's own and leaves every schema-5 table as it was (`TravelSchemaTest`); no animation API in any main source, with a negative control on the scan (`NoAnimationTest`) |
| Negative controls | **55/55 killed** (`M.1-05-mutants.txt`): Y1–Y23 and every earlier mutant on a file this wave changed, P6, H2, H4 among them (stale since earlier waves, `M.1-01`), plus the retargeted D2, S9, U2. `--check` and its negative control (`M.1-06`). The one-map grep (`M.1-07`) |
| Device: upgrade (aosp AVD, API 34, x86_64, swiftshader; the B.1 field build's data in place) | The new build installed over the B.1 one; the file went from schema 5 to 6 on first open. Its database held no track points (`M.1-08`), so the fold-in was tried again on the device's own file put back to schema 5 with 310 track points (300 written for the test, 10 from the gate's recorded walk; two of them vaguer than 30 m): opened by the M.1 build it holds **144 cells, the count SQLite gives for the migration's own key over the 308 good points; 0 missing; 0 from a vague point alone; first and last times right for 144/144**; the 300 written points alone give 136 cells on the device and 136 by the app's floor rule computed independently in Python (`M.1-11`) |
| Device: one map | Opens in 3D: no flat map, no switch, zero log lines from a map view (`M.1-device-01`). Relief first: **relief at 51.8 s and 88.1 s** in two cold builds, the habitat colour at **470.7 s** (the 3 km square; this unaccelerated emulator, the radius scan running alongside); the square with B.1's removed tile as a hole; ready at pitch 50 with 24-bit depth. Pinching out built **level 11, the 47.8 km square: relief 26.4 s, habitat 138.6 s, the dashed 10-mile ring and all ten suggestions on the terrain** (`M.1-device-07`) |
| Device: travel memory, J20, J21 | A 500 m walk as 50 fixes (10 m apart, every 3 s) left **56 cells** in the table and a wash from the start to the fix (`M.1-device-02`; finding 2: it was blue). Track on and 250 m north: **"● REC 0.20 km" and "Back to start: 199 m S"** (the track's first point is where the service stored it, ~50 m into the walk; `M.1-device-03`). "Only show ground I haven't walked" greys the walked strip (`M.1-device-05`); the Layers sheet with the new switches (`M.1-device-04`) |
| Device: battery mode (J24) | At 15 %, unplugged and discharging: **the square rebuilt 32 s after the level dropped, "Battery saver: lighter 3D (15 %)", level 11 relief 18.8 s and habitat 91.4 s** (26.4 s and 138.6 s at full weight; one run each, a build time, not a battery measurement); back on the charger the chip went and the square rebuilt at full weight (`M.1-10`, `M.1-device-06`). The first try only unplugged the emulator, whose status stayed "charging": the mode rightly stayed off (finding 4) |
| Device: privacy | **0 lines with a z/x/y shape and 0 with a coordinate shape** in the app's log over the run (`M.1-09`) |
| Device: after the device-found fixes | **Deferred by the owner** ("Differ emulator run, provide download for apk I can install and test now"): the device run used the build before the three device-found fixes (the vague-fix landing, the pale wash, the coarse-square water). Each fix is held by a unit test and a killed mutant (Y21, Y22, Y23), and the delivered APK contains them (the new wash colour found in its dex, absent from the gate's); their on-screen check is **open**, for the next device run or the owner's phone |

## 8 — Re-evaluate

**What the device found that the design did not:**
1. **A wrong first landing is never corrected, and with one 3D map that costs a square.** On this
   emulator the first live fix is its own default position (A.3's finding), the camera jumps only
   once, and run 1 sat on "No elevation tiles here yet" until "Centre on me" (B.1's gate tapped it;
   this one had not). The emulator case stays (an accurate fix in the wrong place is
   indistinguishable from a real one), but the field case behind it is fixed: a phone's first fix
   is often a cell tower's, fresh and kilometres off, and it used to use up the landing; now a
   fresh fix vaguer than 200 m lands only provisionally (`CameraStart`, Y21).
2. **"Where I've been" was the creeks' colour.** On the device a walk along a hollow read as a
   creek. The wash is now pale (`Gen.Text`), held more than 0.3 from every water colour by a test
   (Y22).
3. **The 48 km square was covered in drains.** Drawn at least 1.2 texels wide, a 2.5 m drain at
   62 m cells is painted ~28 m wide; at that level they hid the habitat colour. Lines under a
   quarter of a texel are no longer drawn (Y23). No square that coarse existed before this wave.
4. **The gate, not the app, had the battery wrong**: `dumpsys battery unplug` leaves the status at
   "charging". The app reads charging from the status, as the track service already did; a phone
   on a charger that is not charging it (status "not charging") would count as unplugged. Stated,
   not changed.
5. **Relief first works as designed:** ground in 52–88 s instead of 471 s here (5–9× sooner). On a
   phone neither number is measured (C18).
6. The "ready" log line prints when the view first becomes ready, not on a rebuild in place, so
   the battery gate's wait for "battery mode" in it never matched; the chip and the relief log are
   the evidence instead.

**What the review of my own diff found** (before the device run): `SquareLevel.forZoom` had a
"pinned" branch that could never decide anything (the clamp already returns the current level past
either end): removed. The mutation harness's `--check` then found P6, H2 and H4 stale since earlier
waves (every run since had listed them as INVALID in its table, and nothing failed), and U2 stale
the moment `CameraStart` changed.

**Sovereignty classifier over this wave's claims:**

| Claim | In time | Against a witness | Verdict |
|---|---|---|---|
| The flat map, its switch and the cross-fade are gone; the app opens in 3D (J30, A6) | ✅ | ✅ grep (no `view3d`, `FieldMap`, `Handoff`, `FlatLayers`); device run 2 | sound |
| The one map reaches the whole 10-mile radius (J30) | ✅ | ✅ `SquareLevelTest` (half-width > 16.09 km at 35.55° and 47°) + device level 11 with the ring and every suggestion | sound |
| Relief shows before the habitat colour (J30) | ✅ | ✅ device logs: relief 52–88 s, habitat 471 s | sound on the emulator; phone timings **open** (C18) |
| Every fix the app receives is remembered, across sessions (J31) | ✅ | ✅ `TravelMemoryTest` + device (56 cells from 50 fixes; the table survives restarts) | sound for fixes the app receives; **not** while the app is closed, by design (§2) |
| Recorded tracks are folded in at upgrade, on the same grid (J31) | ✅ | ✅ `TravelMigrationTest` (3,201 keys) + device 144/144 | sound |
| The on-screen GPS request follows battery, charging and stillness (J32) | ✅ | ✅ `PowerPolicyTest` + Y6, Y18; the replanning wiring read in full | sound for the plan; the saving is **unmeasured** (C18) |
| Frames are paced, the last one never lost (J32) | ✅ | ✅ `FieldPowerTest` + Y7 | sound |
| Walked ground (15 m) greys out under the switch (J20) | ✅ | ✅ the strong-patch test + Y8, Y13 + device | sound |
| The way back leads to the start of today's track (J21) | ✅ | ✅ independent haversine + Y9 + device chip | sound |
| Nothing animates (J23) | ✅ | ✅ `NoAnimationTest` + Y10 | sound |
| The battery mode turns on at the owner's threshold unplugged, or with the saver, and lightens the build (J24) | ✅ | ✅ `FieldPowerTest` + Y11 + device (chip, rebuild, shorter build) | sound; what it saves in battery is **unmeasured** (C18) |
| The travel memory never leaves the phone | ✅ | ✅ it is a Room table; nothing but the recorder and the map's source reads it (grep), and the database is out of every backup (`allowBackup="false"`; the backup and data-extraction rules exclude the database domain) | sound |

**Drafted this iteration (owner directive, five ideas):** J33 when you last walked it (the memory by
recency: a slope searched seasons ago is worth another look); J34 how much of this cove you have
walked (a share per ranked place, feeding F11); J35 ground on screen at once after a restart (the
last square kept in storage); J36 walk the gaps (a loop through unwalked strong ground and back to
the car); J37 battery left at today's real rate (measured drain, never an assumed wattage).
