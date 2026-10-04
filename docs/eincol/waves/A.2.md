# Wave A.2 — one surface (exe.md §11 A5–A12)

Candidates claimed: **A5, A6, A7, A8, A9, A10, A11, A12** (the cursor's slice). Implemented:
**A7, A8, A9, A10**. Verified by device evidence: **A11** (no code: true by construction). Blocked
on an owner decision: **A5 + A6** (one decision) and **A12** (one decision). Branch `eincol/A.2`, off
the session branch at `14d0183` (A.1's archive).

## 1 — Load (measured from the tree and the device)

| What | Measured |
|---|---|
| The switch | `MainScreen`: `if (view3d) Terrain3DView(...) else FieldMap(...)`. A hard cut: the map is disposed on the way in (its style is reloaded on the way back), and the 3D view is a dark screen reading "Tracing creeks and colouring the ground…" until its square is built. On the emulator (aosp, swiftshader, no acceleration) that screen lasted **12 min** (23:14 → 23:26, `A.2-device-04-3d-built.png`); on a phone it is seconds, but always a screen with no map on it |
| GL host | `GLSurfaceView` (`Terrain3DView` L297). Phase 5 measured that it blacks the screen over the map's `TextureView`, so the two can never be on screen together, so no cross-fade is possible without changing the host (I2's finding in A.1) |
| Layer controls | The sheet has 8 switches, a basemap choice and a Heat-opacity slider. In 3D: **Hillshade did nothing** (its label even said "(flat map)"; the bake always shaded), **Heat opacity did nothing** (the bake composited the habitat at full alpha, the 2D raster at the slider's 0.7), and **the scan ring was not drawn**. Three controls/layers that exist in one projection and not the other: an invariant violation ("no dead controls") A9 exists to end |
| Layer description | None. `FieldMap.applyVisibility` lists style-layer ids by hand; `Terrain3DView` reads `layers.habitat/water/contours/finds/suggestions/trackLine/visited` by hand; the drawing order exists only as the order of `addLayer` calls |
| Depth policy | Implicit. 2D: whatever `install` added last is on top. 3D: four layers baked into the texture, the rest on a Compose canvas over the GL surface (never occluded) |
| DEM stores | Two. MapLibre's `RasterDemSource` fetches Terrarium tiles itself (its own HTTP cache) for the hillshade; `DemTileStore` fetches the same tiles with `HttpURLConnection` into `cacheDir/dem_tiles` for habitat, water, contours and 3D. OkHttp is **not on the compile classpath** (MapLibre's dependency is `implementation`; `MapDrape.installRequestCounter` builds its client by reflection, debug builds only) |
| Device | aosp AVD, Android 14 (API 34) x86_64, 1080×2400 @ 2.625, swiftshader, `-accel off`, rooted. 2D habitat raster: 538 s (`GensingoMap: habitat: rasterised 768x768 in 538849 ms`). The emulator cannot fetch tiles itself (the container's TLS proxy is not in its trust store: `DemTileStore: … Trust anchor for certification path not found`); it runs on the tiles cached earlier |
| A privacy finding | `DemTileStore` logs `DEM tile 15/8831/12914 unavailable`: a z15 tile id in logcat locates the user to ~1 km. Pre-existing; registered as **I19**, not fixed inside this wave |

## 2 — Distribution (tail first)

| p | Reading of "A.2: one surface" |
|---|---|
| 0.25 | The cross-fade (A8) over a measured hand-off (A7). Needs the GL host change first: the structural change I2 named |
| 0.20 | The scene description (A9) and its depth policy (A10). Its field value is the three dead 3D controls it ends |
| 0.15 | Continuous tilt (A5) and deleting the switch (A6). Conflicts with the 2D map's default 50° tilt (below) |
| 0.12 | One tile store (A12). Needs either a dependency or a capability loss (below) |
| 0.10 | A11 is already true: the 3D view bakes habitat, contours and water into the mesh's texture. It needs evidence, not code |
| **0.10** | **(tail) The cross-fade is cosmetic; a ginseng hunter gains nothing from a transition** |
| **0.08** | **(tail) A hand-off by tilt does not exist in steep country, so A7's acceptance check ("change the tolerance → the hand-off tilt moves") is ill-posed** |

### Working the tail

**"The cross-fade is cosmetic": refuted on field value, right about the animation.** The fade
itself is polish. What it forces is not: to fade, both views must be alive at once, so (1) the map
stays on screen and usable while the terrain builds, instead of a dark screen for seconds (12 min
on the emulator); (2) the return to the map is instant, with no style reload and the habitat raster
still drawn; (3) a 3D build that fails ("No elevation tiles here yet", "Could not build") now leaves
the user on a working map with that message, not on a dark screen with it. That is reliability.

**"A tilt hand-off does not exist": measured, true.** `HandoffTest.noTiltMakesTheFlatMapAgreeWithRealRelief`
on the Boone z15 tile (real Terrarium, decoded by Python), camera fitted as the 3D view fits:

    Boone z15 full-relief disagreement by pitch: 0°=22.1px, 15°=50.6px, 30°=73.4px, 45°=90.1px, 60°=101.1px

The flat map draws no relief at any tilt, so in steep country no tilt agrees within 2 dp (5.25 px):
even straight down, perspective puts the full-relief ridges 22 px from where the map draws them.
The hand-off variable is therefore the **relief scale**, not the tilt: at relief 0 the mesh is the
map's plane (agreement to the A4 conformance tolerance); the hand-off is the largest relief at which
no control point on screen has moved more than the tolerance. The acceptance check survives with
"tilt" read as "relief": change the tolerance and the hand-off moves (measured below).

## 3 — Falsify (what would break each claim; the reuse map)

| Claim | Test that could break it | Result |
|---|---|---|
| Some tilt hands off cleanly | full-relief disagreement at 0–60° on real terrain | **Broken** (above). Design changed to a relief hand-off |
| The hand-off is "measured", not a constant | a mutant returning a constant (R2), and the pinhole witness: straight down, one point x px off-centre at height h under an eye D above the plane is drawn x·D/(D−h) out, so the hand-off is r = t·D / (h·(x+t)) | Pinned by `HandoffTest`, R2 must die |
| The fade never shows the backends disagreeing | `blend` invariant: whenever alpha < 1, relief ≤ hand-off | Pinned by `HandoffTest`; R1 (relief rises with alpha) must die |
| The published `GLTextureView` is safe to copy as is | reading it | **Broken**: `onSurfaceTextureUpdated` calls `requestRender()`. A `TextureView` calls that after every frame it shows, so in WHEN_DIRTY mode each frame requests the next: a render loop for as long as the view is on screen (a battery defect, Program B). Removed in the copy, recorded in its header |
| Compose `zIndex` reorders two `AndroidView` TextureViews without recreating them | device: warming screenshot shows the map over the building 3D view; then the fade | device evidence (§7) |
| A layer at alpha 0 is still drawn (so its TextureView gets a surface) | not relied on: at reveal 0 the 3D view is drawn opaque *under* the map | — |
| Every sheet switch means something in 3D | flip each switch, compare the 3D bake / canvas | **Broken before this wave** (Hillshade, Heat opacity); pinned by `SceneLayersTest` |

**Reuse (owner directive: copy proven open-source code rather than write it).**
- `GLTextureView` from GPUImage for Android (cats-oss/android-gpuimage, Apache-2.0, © 2018
  Wasabeef): GLSurfaceView's GL thread (pause/resume, context preservation, EGL config choice)
  hosted in a `TextureView`. Copied as `terrain3d/gl/GLTextureView.java` with two recorded changes
  (package; the render loop). Source, not a build dependency: no new artifact, no binary.
- Compose `Animatable` + `tween` for the fade's clock; `zIndex` and `graphicsLayer` for stacking.
- Already ported in A.1: MapLibre GL JS `recalculateZoomAndCenter` (the re-anchor) — reused for the
  ground plane during the fade.

## 4 — Evaluate (the chosen design, and the rejected ones)

**Chosen.** The 3D view moves to a `TextureView` host and joins the map in one stack. MainScreen
owns one number, `reveal` (0 → `COVERED` → `RISEN`): the 3D view warms *under* the map (drawn,
opaque, hidden by the map, gestures on the map) until it is built, fitted and has drawn a frame;
then it fades in with its relief held under the measured hand-off, then the relief rises the rest
of the way and gestures move to it. Back: the relief sinks to the hand-off (easing the pitch into
the map's 60° range), the camera lands flat *under the cover* (`landFlat`, which also brings the
map, which ignored the 3D gestures, to where the user left the 3D), and the 3D view fades out and is
disposed. The map is told when it is covered (`active = false`): it then neither follows moves nor
recomputes its habitat raster under a picture no one sees.

Layers: one `SceneLayer` enum is the scene description; `FlatLayers.ids` and `MeshLayers.draw` are
the two backends' renderers, each an exhaustive `when` (a layer without a renderer does not
compile); every layer declares a `Depth`; the sheet's rows, the map's visibility and the 3D bake
all read the registry.

**Rejected, with reasons:**
1. **Hand-off by tilt.** Measured impossible in steep country (phase 2).
2. **Keep the `GLSurfaceView` and float it (`setZOrderOnTop`, a translucent surface).** A
   `SurfaceView` cannot be faded with view alpha (its surface is composited by SurfaceFlinger, not
   drawn in the view tree), and Phase 5 measured the blackout. A fade needs a `TextureView`.
3. **Write an EGL `TextureView` host from scratch** (~250 lines). GLSurfaceView's thread semantics
   (pause/resume ordering, context loss, surface resize races) are where such hosts break; the
   proven copy is 1,800 lines because of them. The owner's directive is to copy proven code.
4. **Always pre-build the 3D square under the 2D map** (an instant fade on tap). Costs a full
   hydrology and scoring pass (1,280² cells at z15) on every map start, concurrent with the 2D
   habitat raster, plus ~50 MB of textures held for a view the user may never open: a battery and
   memory cost every session pays for a convenience some sessions use.
5. **A12 by pointing MapLibre's DEM source at `file://…/dem_tiles/{z}_{x}_{y}.png`.** Where
   `DemTileStore` holds no tile (every zoom below its range, every place it never fetched) the
   hillshade disappears: removing a capability the owner relies on (§9).
6. **A12 by a reflection-built OkHttp interceptor in release.** R8 renames OkHttp in the field
   build; `MapDrape` keeps its reflection client to debug builds for exactly this reason.
7. **The visited heatmap baked into the 3D texture.** A 2048² re-bake per recorded track point
   while walking. `VISITED` is declared with a line renderer in 3D (the track line), honestly.

**A5 + A6 → blocked(owner).** Continuous tilt as the only way into 3D means tilting the flat map
past the hand-off enters 3D. The flat map opens at 50° (`CameraStart.START_TILT`) and MapLibre stops
at 60°; the 3D view lives at 45–80°. Either the hand-off sits at 60° (tilting the map fully enters
3D, and the 3D view loses its 45–60° range, where it opens today), or below 50° (the map opens in 3D
in steep country). Each removes something the owner uses. **Decision needed:** *"Should tilting the
flat map to its 60° limit enter 3D (and the 3D view stop at 60° on the way back), so the 2D/3D
button can be removed; or does the button stay as the way into 3D?"* The fade built here works with
either answer.

**A12 → blocked(owner).** One store needs MapLibre's DEM requests to be served from the app's tile
cache, which needs an OkHttp interceptor at compile time. **Decision needed:** *"Approve declaring
`com.squareup.okhttp3:okhttp` (already inside the APK via MapLibre, pinned to MapLibre's version: no
new bytes shipped) as a direct dependency, so one interceptor serves MapLibre's hillshade tiles from
the app's DEM cache and saves every tile once?"* Also blocked(device) for its acceptance check: the
emulator cannot reach the network through this container's TLS proxy, so no network log can be taken.

## 5 — Design (files, contracts, acceptance checks, negative controls)

| Id | Change | Observable | Negative control (must be rejected) |
|---|---|---|---|
| A7 | `terrain3d/Handoff.kt`: `disagreementPx`, `relief` (bisection, monotone because each point slides along the image of its vertical and only points the map draws on screen count), `samples` (n×n cell centres, Mercator-linear) | `HandoffTest`: pinhole witness (straight down, analytic r); tolerance 2 → 5.25 → 32 px moves the hand-off up; tight (≤ t at r, > t at r + 0.01); no tilt agrees on real terrain. Device: `Terrain3D: ready: hand-off relief …` | R2 constant hand-off; R3 disagreement ignores relief |
| A8 | `Handoff.blend`; `MapCamera.mvpForMeshBuiltAt(relief)`; `terrain3d/gl/GLTextureView.java` (copied); `TerrainGlRenderer` on its interface, `onTerrainDrawn`; `Terrain3DView` (`reveal`, `onReady`, alpha, relief in frames and markers, rebuild/re-ground under the camera while not interactive); `MainScreen` (one stack, `zIndex`, `Animatable`, sink + `landFlat` + fade-out); `FieldViewModel.landFlat`; `FieldMap(active)` | `HandoffTest.theFadeKeepsTheReliefUnderTheHandOffWhileTheMapShows`; `MapCameraTest.reliefScalesHeightsAboveTheTargetPlane` (witness: `MapCamera.project`). Device: warming screenshot (map stays), a screen recording across the fade | R1 relief rises with alpha; R4 mesh ignores relief |
| A9 | `ui/map/SceneLayers.kt` (`SceneLayer`, `FlatLayers`, `MeshLayers`); sheet rows, `FieldMap.applyVisibility` and the 3D bake/canvas read it; `TerrainTextures.Layers(hillshade, habitatOpacity)`; 3D scan ring | `SceneLayersTest.everySwitchChangesWhatBothViewsDraw`; `Terrain3DTest` hillshade-off and opacity (witness: straight-alpha compositing written out) | R5 a draped layer on the canvas; R6 registry drops Hillshade; R7 bake ignores Hillshade; R8 bake ignores opacity; R10 one switch moves two layers |
| A10 | `Depth` per layer; `SceneLayersTest` validation; `FieldMap.checkSceneOrder` against the loaded style | `everyLayersDepthPolicyIsHonouredByBothBackends`, `theFlatMapStacksEveryDrapedLayerUnderEveryLayerOnTop`; device log `layers: style order matches the scene` | R9 a layer declares a depth its 3D renderer does not honour |
| A11 | none (baked by construction) | device screenshot of the risen 3D view at 60° tilt, overlays following a ridge | — |

## 6 — Implement (as built)

- **A7** `terrain3d/Handoff.kt` (114 lines): `disagreementPx`, `relief`, `samples`, plus `blend`
  for A8. `HandoffTest` (5 tests) with the Boone z15 fixture loader.
- **A8** `terrain3d/gl/GLTextureView.java` (copied, 1,817 lines, two changes in its header;
  notice and the Apache-2.0 text in `THIRD_PARTY_NOTICES.md`, which the APK ships as its in-app
  notices). `TerrainGlRenderer` implements its `Renderer` and calls `onTerrainDrawn` after a frame
  with terrain. `MapCamera.mvpForMeshBuiltAt(…, relief)`. `Terrain3DView`: `reveal`, `onReady`
  (built, fitted, grounded, drawn, centre inside the square), alpha (opaque while warming under the
  map: a layer at alpha 0 may never be drawn, and a TextureView never drawn never gets a surface),
  relief in GL frames and canvas markers, the square rebuilt and the ground plane kept under the
  centre while the 2D map is the one being moved; logs `ready: hand-off relief …`. `MainScreen`:
  one stack (`zIndex` −1 warming, +1 showing), `Animatable` reveal (1.2 s in; 0.6 s sink with the
  pitch eased into 60°; `landFlat`; 0.6 s out), separate map and 3D status lines.
  `FieldViewModel.landFlat`; `setView3d` glides into 3D (animated) and no longer moves the camera on
  the way back. `FieldMap(active)`: covered, it follows no move and recomputes no raster.
- **A9/A10** `ui/map/SceneLayers.kt` (122 lines): `Depth`, `SceneLayer` (declaration order =
  drawing order; `shown`, `set`, `SHEET`, `label`), `FlatLayers` (`ids`, `ORDER`), `MeshDraw`,
  `MeshLayers` (`draw`, `baked`, `onCanvas`). Readers: the sheet's rows (generated; the Hillshade
  label loses "(flat map)"), `FieldMap.applyVisibility` and `checkSceneOrder`, the 3D bake and
  canvas. `TerrainTextures.Layers(hillshade, habitatOpacity)`, `fade`. The 3D canvas now draws the
  scan ring (densified to ~50 m so it bends with the ground) and takes its order from the registry.
- **Found in review, fixed before verification** (the in-flight run was stopped, the device run
  restarted on the fixed build): while covered, the map skipped app moves but replayed the latest
  one when it became active again, at the start of the sink. Its camera reports would then have
  overwritten the camera the user left the 3D view at (e.g. "Centre on me" in 3D, a pan, back to
  the map: the map returned to the recentred view). The map now spends each skipped move
  (`take`, no `follow`), and `landFlat` is the move it follows. The hand-off is also measured only
  while a fade can use it, not on every gesture frame of the risen view.
- **A harness defect met on the way (I20):** the first mutant run reused ids R1–R10, which ran the
  old hydrology R1–R3 instead; stopping that run with SIGTERM left `RadiusScan.kt` mutated. Caught
  by `git status`, the file restored from git, the mutants renamed S1–S10. **And one mis-addressed
  oracle:** S4 first SURVIVED because the relief test landed in `GroundedCameraTest` (a second
  class in `MapCameraTest.kt`), which neither the harness entry nor my earlier test run selected —
  so that test had not yet run at all. Retargeted, it passes clean and kills S4.

## 7 — Verify (raw output in `docs/eincol/evidence/A.2-*`)

| Check | Result |
|---|---|
| Build | `assembleDebug assembleRelease` **exit 0** (`A.2-01-build.txt`); `assembleField -Pgensingo.abis=x86_64` exit 0 (the device APK, SHA-256 `d6aa4ea7…`) |
| Tests | `:app:test` **exit 0**: 367 per variant (debug, release, field), 0 failures, 1 skipped (`A.2-03-test.txt`). A.1: 355; +12 = HandoffTest 5, SceneLayersTest 4, Terrain3DTest 2, GroundedCameraTest 1 |
| Lint | `:app:lint` **exit 0**: 0 errors, 80 warnings, none above the bootstrap baseline by (id, file) (`A.2-02-lint.txt`). Exempt by name: `GLTextureView.java` (LogNotTimber, WrongCommentType: copied code kept as published, `app/lint.xml`) and the one new `Log.i` in `Terrain3DView` (`//noinspection`). Before the exemptions: 121 warnings, 40 of them in the copied file and 1 the new log line |
| Numerical witnesses | Pinhole formula (A7, straight down, analytic r); `MapCamera.project` (A8 relief in the MVP); straight-alpha compositing written out (A9 opacity); the real Boone z15 Terrarium tile, decoded by Python (A7 measurement, the falsification) |
| Negative controls | **S1–S10: 10/10 killed** (`A.2-07-mutants.txt`, `A.2-08-rerun-S4.txt`): S4 first survived because its oracle sat in a class the harness entry did not name (phase 6) |
| No-regression | every pre-existing test passes in all three variants |
| Device (aosp AVD, Android 14 / API 34, x86_64, 1080×2400 @ 2.625, swiftshader, no acceleration) | **A10:** `GensingoMap: layers: style order matches the scene (11 layers)` on the loaded style. **A8, warming:** after "Show in 3D" the map stays on top and usable for the whole build (`-06`, `-13` frames, `A.2-device-warming.mp4`: 12 min of the 2D map with habitat, creeks and contours and the status "Tracing creeks and colouring the ground…"), against the old dark screen (`-04`). **A7 on device:** `Terrain3D: ready: hand-off relief 0.056 at pitch 50 (tolerance 5.3 px)`, twice (00:22:33, 01:09:18), matching the JVM measurement on the Boone tile (0.0567 at 5.25 px). **A8, risen:** `-07`, terrain drawn through the TextureView host over the map. **A8, return:** `-09` → `-10`, `-11` frames and `A.2-device-fade-out.mp4`: risen terrain → terrain sunk flat to the hand-off (#19) → the map (#20); the map landed on a tile range (25/35) other than the recentred view's (20/24), so the "Centre on me" made in 3D was not replayed (the review fix). The emulator draws 2–4 frames a second, so no partial-alpha frame of that 1.2 s fade-out was caught. **A8, the fade in, recorded** (a continuous recorder, `A.2-device-fade-in.mp4`, frames `-14`): map (#9) → the 3D view arriving at low alpha (#10) → half faded, lying flat on the map with the map visible through it (#11) → covered at the hand-off relief (#12) → the relief rising (#13, #14) → risen (#15), all within 2 s. Zoomed (`-15`): mid-fade, the creek and the contours run straight across the edge of the 3D square onto the map with no break: the two backends agree while both show. What does differ during the fade is coverage: outside the 3 km square the map fades to the 3D view's dark background (predicted in phase 4, not a position disagreement) **A11:** `-07`/`-04` show habitat, contours and creeks following ridges at 50°; the injected two-finger tilt to 60° did not register (`-08` = `-07`), so the 60° screenshot is **not taken** |

## 8 — Re-evaluate

**What the evaluators found that the design did not:**
1. **The register's A7 is ill-posed in steep country** (phase 2, measured): the hand-off variable
   became the relief scale. The acceptance check survives with "tilt" read as "relief".
2. **The copied `GLTextureView` had a render loop** (phase 3): every displayed frame requested the
   next. Removed and stated in the file's header.
3. **A stale-move replay on the way back** (my own review, phase 6): fixed before verification;
   the device return shows the fix's effect indirectly (tile ranges), not directly (I21 hides the
   position marker).
4. **S4's oracle was mis-addressed**, so a test I believed green had never run (phase 6).
5. **Your position is not drawn on the 2D map** (I21, pre-existing since at least A.1, found only
   because this wave compared 2D and 3D screenshots).
6. **The device gate itself had two defects**: `adb logcat -d` cannot dump a full buffer inside 15 s
   on this emulator (the `ready:` line was missed and the segment holding the fade deleted), and
   segments stopped while detection ran on. Both rewritten (`--pid -t`, a continuous recorder),
   after which the fade in was recorded on the third attempt. Two orphaned gate scripts from
   stopped runs were also found still running (killed; they took no taps after the final run's).

**Sovereignty classifier over this wave's claims:**

| Claim | In time | Against a witness | Verdict |
|---|---|---|---|
| The hand-off is measured from agreement, not a constant | ✅ the pinhole witness was written before the bisection | ✅ analytic formula; tolerance moves it; S2, S3 killed; device 0.056 = JVM 0.0567 | sound |
| No tilt hands off cleanly on real terrain | ✅ phase 3 | ✅ real Terrarium tile, decoded outside the app | sound (one tile, one camera fit: a measurement, not a law) |
| While the map shows through, the mesh agrees with it within 2 dp | ✅ | ✅ `blend` invariant (S1 killed) + relief MVP witness (S4 killed) + the recorded mid-fade frame (`-15`: creeks and contours continuous across the square's edge) | sound (on screen: by eye at the recording's resolution, not measured in pixels) |
| The map stays usable while the 3D view builds | — | ✅ device frames across a 12-minute build | sound |
| Every sheet switch means the same in 2D and 3D | ✅ | ✅ `SceneLayersTest` + Terrain3DTest witnesses (S5–S8, S10 killed) | sound |
| Every layer's declared depth is honoured by both backends | ✅ | ✅ validation test (S9 killed) + the device order check | sound for DRAPED and ON_TOP; OCCLUDED is declared by no layer (no backend can draw it) |
| Overlays follow the terrain at 60° (A11) | — | ❌ the 60° screenshot was not taken | **open**: blocked(device) until gesture injection works (J18); at 50° they do (`-07`) |
| The covered map never replays a stale move | ✅ reviewed before verification | ⚠️ indirect device evidence only | **open** until I21 lets the marker witness it |

**Counter-candidate (one per wave): "the cross-fade is cosmetic"** — answered in phase 2. The
animation is polish; what it forced (the map alive under the 3D view) is the field value: a usable
map during a build that took 12 minutes on the emulator, an instant return with the raster kept,
and a failed build that leaves a working map rather than a dark screen with a message.
