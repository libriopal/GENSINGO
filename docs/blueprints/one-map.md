# Blueprint: one map (2D and 3D combined)

Run under [ARCHITECT.md](../../ARCHITECT.md), iteration 1. Owner's direction (verbatim
intent): combine the 2D map with the 3D map; reuse open-source code instead of writing it; a
minimal tool for a couple of ginseng diggers and land prospectors.

## A. Question and measurements

**Question:** *How do the 2D map and the 3D terrain become one map — same place, same layers,
same controls — reusing what the linked libraries and the existing code already do?*

Measured on 2026-10-01 (commit `ac82b1d`):

| What | Measured |
|---|---|
| Map library | MapLibre Android **13.6.1**. `classes.jar` has **no terrain API** (no `setTerrain`, no terrain class; `javap` on `Style`) but has `org.maplibre.android.snapshotter.MapSnapshotter` and `style.light.Light` |
| 2D map | `ui/map/FieldMap.kt` (599 lines), TextureView MapLibre map; layers: basemap, hillshade, habitat raster, creeks, visited heat, track, finds, 10-mile ring, suggestions, you; camera free; max pitch 60° |
| 3D view | `ui/Terrain3DView.kt` (328 lines) + `terrain3d/*`: own GLES3 renderer; 3 × 3 zoom-15 tiles (~3 km), 385² mesh, 1536² baked texture (habitat or elevation, hillshade, contours, creeks); markers: suggestions, finds, you |
| How they connect | They don't. `view3d` flag swaps screens; the 3D camera is locked on the user, cannot pan, ignores the 2D camera and every layer toggle except its own **H**; no basemap (roads, names) in 3D; no track in 3D |
| Camera maths | `terrain3d/MapCamera.kt` mirrors MapLibre's transform (512-px tiles, same zoom, bearing, pitch semantics); checked against MapLibre's projection in Phase 5 |

## B. Distribution

| p | Candidate |
|---|---|
| 0.35 | **One screen state, two projections.** Shared camera and shared layer state; 3D drapes a `MapSnapshotter` render of the same basemap, plus the app's own layers, on the existing HD terrain; switching keeps centre, zoom and bearing; 3D can pan like the map |
| 0.20 | Upgrade MapLibre to a version with native 3D terrain and drop the custom renderer: every layer drapes for free (depends on research Q1) |
| 0.12 | Draw the 3D mesh inside MapLibre with `CustomLayer` (needs an NDK C++ host object; Phase 5 found it unreachable from Kotlin) |
| 0.10 | Replace MapLibre with the custom renderer for both modes (2D = top-down 3D) |
| 0.08 | A transparent GL surface over the MapLibre view (measured in Phase 5: blacks the screen) |
| 0.08 | Terrain inside MapLibre as `fill-extrusion` contour bands ("wedding cake"), all in one map |
| 0.07 | Fetch raster tiles ourselves and texture the mesh with them (the default basemap is vector, so it would need its own renderer: that is what the snapshotter is) |

Working the tail: the fill-extrusion terrace keeps one map object, but draped layers (heat, creeks,
markers) sit at z = 0 under the extrusions and are hidden: rejected. Own renderer for 2D loses the
vector basemap, labels and offline regions MapLibre already provides: rejected. `CustomLayer`:
rejected (NDK). Overlay: rejected (measured). Native terrain: chosen **if** research finds a
released, offline-capable version; otherwise row one.

## C. Reuse (researcher's report)

Researcher sub-agent, 2026-10-01 (sources linked in its report; unverified items marked):

| Candidate | Finding | Decision |
|---|---|---|
| MapLibre native 3D terrain | **Not released** in any MapLibre Native Android version. Draft, unmerged [PR #4190](https://github.com/maplibre/maplibre-native/pull/4190) (`style.terrain.Terrain`, `setTerrain`) with known gaps (symbols and lines at the wrong altitude). MapTiler's mobile SDK has terrain but needs a key and is online-only; the `maplibreplus-native` fork has no artifact | **Not used.** Distribution row 2 is closed; row 1 stands |
| `MapSnapshotter` (MapLibre 13.6.1, BSD-2) | `@UiThread`, render on a background thread, callbacks on the calling thread; same `FileSource` as the map's cache and offline regions (offline on a device UNVERIFIED); `withRegion`, `withPixelRatio(1)`, `withLogo(false)`, `withAttribution(false)` (then the app owes attribution); `start()` once per instance; `cancel()` in `onStop`; open bugs: sprite decode [#2962](https://github.com/maplibre/maplibre-native/issues/2962), offline raster tiles [#633](https://github.com/maplibre/maplibre-native/issues/633) | **Used** for the 3D basemap (WP-A), at ≤ 2048², pixel ratio 1; attribution shown in the 3D legend; failure falls back to the neutral relief |
| maplibre-contour `isolines.ts` (BSD-3, 321 lines TS, from d3-contour, ISC; v0.1.1 2026-09-17, maintained) | Marching-squares isolines from a DEM grid, already used with Terrarium tiles | **Ported to Kotlin** (WP-C), with both notices in `THIRD_PARTY_NOTICES.md`, so the 2D map shows the same contours as the 3D texture |
| Martini RTIN (ISC, 164 lines JS, inactive since 2020; no Java/Kotlin port) | Adaptive terrain mesh, fewer triangles for the same error | **Not now**: no measured need (the 298k-triangle mesh's speed on a phone is unmeasured). Open list |
| Snapshot draped on a GLES mesh, Android example | None found | Built here (WP-A), on the existing renderer |
| udel marching squares | GPL | **Avoided** (licence) |

## D. Design

**One map, two projections.**

1. **Shared camera** (`ViewCamera`: lat, lng, zoom, bearing, pitch) in the view model. The 2D map
   writes it when its camera settles; the 3D view starts from it and writes it back as it moves;
   switching to 2D jumps the map to it. Same zoom semantics on both sides (both 512-px tiles).
2. **Same layers in both.** The Layers sheet drives both views. In 3D: habitat heat (texture),
   creeks (texture), contours (texture), suggestions, finds, track and you (projected markers).
3. **The basemap in 3D** comes from MapLibre itself: `MapSnapshotter` renders the same style for
   the 3D area's bounds at the texture size, offline from its cache; it becomes the texture's base
   under the habitat colour, creeks, contours and hillshade. If the snapshot fails (no cached
   tiles), the neutral relief is used, as now.
4. **3D moves like the map**: one finger pans (the ground under the finger stays under it), two
   fingers rotate, pinch zooms, vertical two-finger drag tilts. Recentre on me works in both.
   The 3D area is rebuilt around the camera centre when it leaves the built square.
5. **One control** switches 2D ↔ 3D. **H** goes: colouring follows the layer toggles.

Rejected, and why: see §B. Kept out of scope (open list): visited-track heatmap in 3D (a GPU
heatmap per frame on the mesh; the track line is shown instead); adaptive mesh (RTIN) unless the
device shows the 298k-triangle mesh is slow.

## E. Work packages

### WP-A — Map drape (contractor A)

```
Purpose: put the real basemap (and the app's layers, by toggle) on the 3D terrain.
Files you may create or change:
  app/src/main/java/com/ginsengo/steward/terrain3d/TerrainTextures.kt
  app/src/main/java/com/ginsengo/steward/ui/map/MapDrape.kt            (new)
  app/src/test/java/com/ginsengo/steward/terrain3d/MapDrapeCompositeTest.kt (new)
Files you must not touch: everything else.
Deliverable:
  - TerrainTextures: add Mode.MAP and a Layers(habitat=true, water=true, contours=true) parameter to
    bake(ground, mode, size, layers = Layers()). Ground gains `basemap: IntArray? = null` (ARGB,
    size x size, north-up, covering exactly the interior). MAP: base = basemap pixel where present
    and opaque, else the neutral relief; then the habitat colour over it if layers.habitat; hillshade;
    contours if layers.contours; creeks if layers.water. Existing modes keep their exact output when
    called with default Layers (existing Terrain3DTest must pass unchanged).
  - MapDrape.render(context, styleUri: String, north, west, south, east, sizePx, timeoutMs): IntArray?
    using org.maplibre.android.snapshotter.MapSnapshotter with the SCREEN's pixel ratio
    (context.resources.displayMetrics.density) and logical size sizePx / density, logo and
    attribution off, created and started on the main thread as the API requires (@UiThread),
    cancelled on timeout; returns exactly sizePx x sizePx ARGB pixels (scale if the bitmap differs
    by rounding) or null on failure or timeout. start() once per instance. No coordinates in logs.
  - Pure, JVM-testable: MapDrape.logicalSize(sizePx, density, widthM, lat, maxZoom = 14.0): Int and
    MapDrape.snapshotZoom(logicalPx, widthM, lat): Double — the logical size is reduced, if needed,
    so the snapshot's zoom never exceeds maxZoom (the saved offline region's top zoom).
  - Debug builds only (BuildConfig.DEBUG): MapDrape.installRequestCounter() sets an OkHttp client on
    org.maplibre.android.module.http.HttpRequestUtil whose interceptor counts requests by kind
    (style, sprite, glyphs, tile, other) and logs only the counts after each snapshot.
Reuse: MapLibre MapSnapshotter (already linked; BSD-2-Clause MapLibre licence).
Acceptance tests (write first, in MapDrapeCompositeTest):
  1. MAP mode, flat ground, layers all off, random opaque basemap -> output equals basemap (opaque).
  2. MAP mode, flat ground, habitat on -> each texel = over(basemap, colourFor(score, MIN_SCORE)).
  3. Valley ground: water on draws blue along the floor; water off draws none.
  4. basemap = null in MAP mode -> identical to HABITAT mode output for the same ground and layers.
  5. Default Layers reproduce the previous HABITAT and ELEVATION outputs exactly (regression).
  6. logicalSize/snapshotZoom: a 3 km square at 1536 px and density 2.625 gives a zoom <= 14; a
     density of 1 (logical 1536) would exceed 14, and logicalSize reduces it to keep zoom <= 14.
Negative control: make MAP ignore the basemap (use the neutral ramp) -> test 1 must fail; revert.
Budget: bake of 1536^2 stays under 1 s on the JVM.
Report back: files changed, test results, the negative control's failing output, open issues.
```

### WP-B — Shared camera and 3D navigation maths (contractor B)

```
Purpose: one camera for both views, and panning in 3D that keeps the ground under the finger.
Files you may create or change:
  app/src/main/java/com/ginsengo/steward/terrain3d/CameraMath.kt        (new)
  app/src/test/java/com/ginsengo/steward/terrain3d/CameraMathTest.kt   (new)
  app/src/main/java/com/ginsengo/steward/ui/FieldViewModel.kt
  app/src/main/java/com/ginsengo/steward/ui/map/FieldMap.kt
  app/src/main/java/com/ginsengo/steward/terrain3d/MapCamera.kt          (add unproject only)
Files you must not touch: everything else (MainScreen and Terrain3DView are integrated by the architect).
Deliverable:
  - data class ViewCamera(lat, lng, zoom, bearing, pitch) in CameraMath.kt.
  - MapCamera.unproject(screenX, screenY, heightM): DoubleArray? (lat, lng) — the inverse of
    project() onto the horizontal plane at heightM (same height convention as project's elevationM);
    null when the ray misses the plane (above the horizon).
  - CameraMath.pan(cam, fromX, fromY, toX, toY, viewportW, viewportH, heightAt: (lat, lng) -> Double):
    ViewCamera — finds the TERRAIN point under (fromX, fromY) by iterating unproject at the height
    heightAt returns (start at heightAt(centre), 4 iterations), then returns the camera (same zoom,
    bearing, pitch; new centre) that projects that point, at its height, to (toX, toY). heightAt uses
    the same convention as MapCamera.project's elevationM (the 3D view passes (e - ground) * exaggeration).
  - CameraMath.clampCentre(cam, north, west, south, east): ViewCamera.
  - CameraMath.to3d(cam2d, minZoom, maxZoom) / to2d(cam3d): keep centre and bearing; zoom clamped;
    pitch max(cam.pitch, 45) into 3D, min(cam.pitch, 60) back to 2D.
  - FieldViewModel: StateFlow<ViewCamera?> camera + setCamera(ViewCamera).
  - FieldMap: new parameters onCameraIdle: (ViewCamera) -> Unit (called from the existing idle
    listener) and jumpTo: ViewCamera? with a tick, so the map can be placed at a camera when
    returning from 3D (moveCamera, never an interrupted animation).
Acceptance tests (CameraMathTest), using MapCamera.project as the independent witness:
  1. Flat ground (heightAt = 0), pitch 0, bearings 0/90/200: after pan(from -> to), projecting the
     ground point that was under `from` with the NEW camera lands at `to` within 1 px.
  2. Flat ground, pitch 55: same, within 2 px.
  3. Sloped terrain (heightAt rising 0.3 m per m east, up to ~600 m over the area), pitch 55, zoom 15:
     the TERRAIN point under `from` (found by the test with its own fine search along the ray, not by
     calling the code under test) lands at `to` within 3 px; the camera-only version (heightAt ignored)
     misses by more than 10 px in the same case (the auditor's runner-up, measured).
  4. unproject(project(p, h), h) returns p within 1e-7 degrees.
  5. clampCentre keeps the centre inside the bounds and leaves an inside centre unchanged.
  6. to3d/to2d keep centre and bearing exactly; zoom and pitch clamped as specified.
Negative controls: (a) ignore bearing in pan -> test 1 at bearing 90 fails; (b) skip the height
  iteration -> test 3 fails. Show both failing, then revert.
Report back: files changed, test results, the negative control's failing output, open issues.
```

### WP-C — Contour lines for the 2D map, ported from maplibre-contour (contractor C)

```
Purpose: the 2D map shows the same contour lines the 3D texture bakes, by porting proven code.
Files you may create or change:
  app/src/main/java/com/ginsengo/steward/terrain/Isolines.kt             (new; the port)
  app/src/test/java/com/ginsengo/steward/terrain/IsolinesTest.kt        (new)
  THIRD_PARTY_NOTICES.md                                                (new)
Files you must not touch: everything else.
Deliverable:
  - A Kotlin port of maplibre-contour's src/isolines.ts (BSD-3-Clause, onthegomap; adapted from
    d3-contour, ISC): fun isolines(z: FloatArray, w: Int, h: Int, intervalM: Double): Map<Int, List<FloatArray>>
    keyed by elevation level, each line a flat [x0, y0, x1, y1, ...] in grid-cell coordinates, lines
    stitched into continuous polylines as the original does. Keep the original's algorithm and its
    structure recognisable; put the source URL, version/commit and both licence notices in the file
    header and in THIRD_PARTY_NOTICES.md.
Reuse: https://github.com/onthegomap/maplibre-contour/blob/main/src/isolines.ts (BSD-3-Clause) and its
  d3-contour (ISC) notice. Fetch the source from the web; do not use GitHub MCP tools on other repos.
Acceptance tests (IsolinesTest, written first):
  1. A cone z = 1000 - r: the 900 m isoline is one closed ring whose points are all at r = 100 +/- 1 cell.
  2. A plane rising east (z = x): each level L is one line at x = L +/- 0.5, spanning the full height.
  3. Every vertex of every level-L line, bilinearly sampled, equals L within 0.05 m (interpolation is right).
  4. A flat grid yields no lines.
Negative control: drop the linear interpolation (place vertices at cell centres) -> test 3 must fail; revert.
Report back: files changed, test results, the negative control's failing output, any deviation from
  the original algorithm and why.
```

### Integration (architect)

`ui/Terrain3DView.kt`, `ui/MainScreen.kt`: shared camera, gestures via `CameraMath`, recentre in
3D, rebuild when the centre leaves the square, snapshot drape via `MapDrape`, layer toggles via
`TerrainTextures.Layers`, track line projected, **H** removed, one 2D/3D control; a contour layer on
the 2D map from WP-C, at the same interval the 3D texture uses; attribution for the draped basemap.

**As built** (architect, after the three merges):

| Piece | Built as | Witness |
|---|---|---|
| One camera | `FieldViewModel.camera`; the 2D map writes it on camera idle, the 3D view on gesture end, recentre, focus and compass; the 2D/3D switch hands it back with `CameraMath.to2d` and `FieldMap(jumpTo, jumpTick)` | `CameraMathTest.switchingViews…` (WP-B) |
| 3D gestures | One finger: `CameraMath.pan` with the scene's elevation and its height range; two fingers: pinch zoom, twist bearing, vertical slide pitch | `CameraMathTest` (WP-B + the architect's ray-march fix) |
| **Found in integration:** the target plane must follow the ground | The 3D camera looks at the plane through the ground under its centre. After a pan the centre is over other ground; re-basing the plane alone jumps the picture by the height difference (×1.5). Reuse search: MapLibre GL JS solves exactly this (`TransformHelper.recalculateZoomAndCenter`, BSD-3): keep the eye, move the target along the view ray to the terrain, recompute zoom. Ported as `CameraMath.reanchor`, run when a gesture ends | `ReanchorTest`: every terrain point stays on the same pixel (< 0.5 px) at 4 pitches × 3 bearings; mutant O1 |
| Rebuild | When a gesture, recentre or "Show on map" leaves the centre outside the built square, the terrain is built around the new centre (3 × 3 zoom-15 tiles) | on device (G6) |
| Exact square | `Scene.north/south/west/east` from exact Mercator row edges (the contractor-era linear guess was ~1e-5° off; the snapshot is matched texel for texel), `Scene.elevationAt` bilinear | `SceneGeometryTest`; mutants O2, O4 |
| Drape | `MapDrape.render` for the exact square at the texture size once the terrain is built; MAP mode when it returns, else HABITAT, else ELEVATION with the habitat layer off. Only when the Dark style actually loaded on the 2D map (`FieldMap(onBasemap)`); never for Topo (built in code, not a style URL) | `MapDrapeCompositeTest` (WP-A); mutants A1, A2 |
| Layers in 3D | `TerrainTextures.Layers(habitat, water, contours)` from the Layers sheet; finds and suggestions obey their toggles; the track line is drawn when "Track line" **or** "Where I've been" is on (the GPU heat is not in 3D), decimated to ≤ 3,000 points, projected with one matrix per frame (`MapCamera.projector`) | `ReanchorTest.theProjectorIsProject`; device |
| Contours in 2D | `ContourLines.of` (the WP-C port over the mosaic interior, every 2nd vertex kept, index flag every 5th level) at `TerrainTextures.contourInterval(interior relief)`, a `LineLayer` under the creeks, brown on Topo and pale on Dark, interval in the status line; `MapLayerState.contours` + a Layers toggle | `ContourLinesTest`; mutant O3 |
| Notices in the app | `copyThirdPartyNotices` (Gradle `Copy` before `preBuild`) puts `THIRD_PARTY_NOTICES.md` in the APK's assets; Layers → Open-source notices shows it; the MapLibre GL JS notice was added for the port | the release check (G7) reads it from the APK |
| **H** | Removed; colouring follows the layer toggles | — |

**Inspection of the integration** (inspector sub-agent, fresh context, given this contract and
the diff; the architect wrote the integration, so it could not certify it). Verdict
*fix-then-merge*; every finding is either fixed or answered here:

| # | Finding | Response |
|---|---|---|
| 1 | Switching to Topo while in 3D left the Dark drape (and its credit) on the ground | Fixed: no style ⇒ the drape is cleared |
| 2 | 2D and 3D contours use the same **rule** on different footprints, so different **intervals**; the contract said "the same interval" | **Contract amended**, both versions kept. First: "the same interval the 3D texture uses". Corrected: "the same interval rule (`contourInterval` over what each view shows), with the interval printed on each view" (2D status line, 3D legend). Reason: one interval for a 10-mile view and a 3 km square is unreadable on one of them; a labelled interval is what a topo reader expects |
| 3 | The zoom fit wrote camera state during composition | Fixed before the inspection returned (now an effect); re-checked |
| 4 | A rebuild reset the ground plane and jumped the picture (re-anchoring cannot run while the centre is off the old square) | Fixed: after a rebuild the camera is settled on the new ground with the eye kept (`Terrain3D.settle`) |
| 5 | New terrain was drawn with the old square's texture until its bake finished | Fixed: bake first, then mesh and texture together |
| 6 | The notices folder relied on task ordering | Fixed: registered with `variant.sources.assets.addGeneratedSourceDirectory`; G7 reads the file from the APK |
| 7 | Status line could stick on "Tracing creeks…"; a GL frame on every GPS fix; contours share the habitat key | First two fixed (explicit publish; a frame only when camera, ground, square or viewport change). The third is open: toggling contours recomputes the habitat raster |
| 8 | The gesture-end step had no pure, tested function | Fixed: `Terrain3D.settle`, witnessed by `SceneGeometryTest.settlingPuts…` (mutant O5); its vacuous screen-centre asserts were removed from `ReanchorTest` |
| — | Not caught by tests: dropping the cos(lat) term in `reanchor` | Triaged: about 0.1 px over a 1 km move, below the test's 0.5 px tolerance; kept for exactness (MapLibre GL JS has it) |

## F. Load-bearing claims (for the auditor)

1. A `MapSnapshotter` render of the active style for the 3D square's bounds aligns with the
   terrain texture texel for texel, because both are north-up Web Mercator over the same bounds.
2. The snapshot works offline whenever the 2D map shows tiles there (same ambient cache).
3. Using the same zoom, bearing and pitch numbers in MapLibre and in `MapCamera` gives the same
   view of the same place (both use 512-px world tiles).
4. Panning in 3D can be computed on the CPU from the camera alone (no depth read-back) and keep
   the ground under the finger closely enough at pitch ≤ 60°.
5. Baking the basemap into the texture is acceptable for labels: they are drawn flat on the
   ground and may read stretched on steep slopes, which is better than none.

## G. Audit

Independent auditor sub-agent (fresh context, given only §D's design and §F's claims), 2026-10-01.

**Strongest objection — claim 2 is probably false.** The snapshot does not request what the 2D map
fetched: a different tile zoom (3 km in 1536 px is ~z14-15, the map may have only seen z11-12), a
different pixel ratio (the snapshotter defaults to 1; the map uses the screen's ~2.6, so its `@2x`
sprites are cached and the `@1x` ones are not), and different label glyphs. In still mode MapLibre
fails the whole render on the first resource error (`Map::Impl::onResourceError`), so one missing
sprite loses the entire basemap, exactly in the field conditions where the 2D map still looks fine.
**Cheaper instrument:** an OkHttp hook via `HttpRequestUtil.setOkHttpClient` that records every request
the snapshot makes after viewing the area in 2D; any request that reaches the network is a cache miss
that will fail offline. **Runner-up — claim 4:** panning from the camera alone has an error that grows
as relief over camera height, large when zoomed in over steep ground.

**Investigated, and the response** (both versions kept, per EINCOL step 4):

| Claim | First version | Corrected version | What changed |
|---|---|---|---|
| 2 | The snapshot works offline whenever the 2D map shows tiles there | It works offline **where the area was saved** ("Save 10 miles": region zoom 8-14 at the screen's pixel ratio, measured in `OfflineArea.downloadBasemap`), **if** it requests that pixel ratio and zoom ≤ 14; elsewhere it may fail whole | WP-A renders at the screen's pixel ratio with logical size = texture / density, checks its zoom is ≤ 14 (pure, tested), skips the snapshot when the 2D map is on its offline fallback style, times out, and falls back to terrain-only with a status that says so. A debug-build request counter (the auditor's instrument, counts by kind only, never URLs: tile URLs encode location) is added for the device gate |
| 4 | CPU panning from the camera alone keeps the ground under the finger closely enough | Keep the **terrain** point under the finger: unproject the touch onto the plane at the target's ground height, sample the elevation there, unproject again at that height, iterate, then solve for the camera that puts that 3D point under the finger's new position | WP-B adds `MapCamera.unproject` and a terrain-aware `CameraMath.pan(..., heightAt)`; its witness test uses a sloped synthetic terrain |
| 1, 3, 5 | — | Not attacked; kept, and checked at the device gate | — |

## H. Log

- Iteration 1: A-B done; C done (researcher); WP-C added from the reuse findings; E done (auditor): claims 2 and 4 corrected, WP-A and WP-B revised. G3 passes; contractors start.
- Contracts: WP-A, WP-B, WP-C delivered with their negative controls shown failing (G4). All three
  started from `main`, not the working branch, and fast-forwarded themselves: the next brief says
  "fast-forward to the working branch first". Contractor B's reported limit (no convergence for
  upper-screen touches along steep slopes) fixed by the architect test-first (ray march).
- Integration: built as tabled above; one design problem found while wiring (the ground plane
  after a pan) and solved by porting MapLibre GL JS's method instead of inventing one.
- Inspection: fix-then-merge, 8 findings, 7 fixed, 1 answered by a recorded contract amendment.
- G5: 339 unit tests pass (1 skipped: the full-scan timing fixture); 11 new mutants (A1, A2,
  B1-B3, I1, O1-O5), 11 killed; shaders compile. G6: below. G7: below.
