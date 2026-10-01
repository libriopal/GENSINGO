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

*Pending: the researcher sub-agent is running (iteration 1, step C).*

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
    using org.maplibre.android.snapshotter.MapSnapshotter (pixel ratio 1, logo off, attribution off),
    on the main thread as the API requires, returning size x size ARGB pixels or null on failure or
    timeout. No coordinates in logs.
Reuse: MapLibre MapSnapshotter (already linked; BSD-2-Clause MapLibre licence).
Acceptance tests (write first, in MapDrapeCompositeTest):
  1. MAP mode, flat ground, layers all off, random opaque basemap -> output equals basemap (opaque).
  2. MAP mode, flat ground, habitat on -> each texel = over(basemap, colourFor(score, MIN_SCORE)).
  3. Valley ground: water on draws blue along the floor; water off draws none.
  4. basemap = null in MAP mode -> identical to HABITAT mode output for the same ground and layers.
  5. Default Layers reproduce the previous HABITAT and ELEVATION outputs exactly (regression).
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
Files you must not touch: everything else (MainScreen and Terrain3DView are integrated by the architect).
Deliverable:
  - data class ViewCamera(lat, lng, zoom, bearing, pitch) in CameraMath.kt.
  - CameraMath.pan(cam, dxPx, dyPx, viewportW, viewportH): ViewCamera — the ground point under the
    finger's start ends under the finger's end (content moves with the finger), for any bearing,
    and for pitch up to 60 within the tolerance below.
  - CameraMath.clampCentre(cam, north, west, south, east): ViewCamera.
  - CameraMath.to3d(cam2d, minZoom, maxZoom) / to2d(cam3d): keep centre and bearing; zoom clamped;
    pitch max(cam.pitch, 45) into 3D, min(cam.pitch, 60) back to 2D.
  - FieldViewModel: StateFlow<ViewCamera?> camera + setCamera(ViewCamera).
  - FieldMap: new parameters onCameraIdle: (ViewCamera) -> Unit (called from the existing idle
    listener) and jumpTo: ViewCamera? with a tick, so the map can be placed at a camera when
    returning from 3D (moveCamera, never an interrupted animation).
Acceptance tests (CameraMathTest), using MapCamera.project as the independent witness:
  1. Pitch 0, bearings 0/90/200: after pan(dx, dy), projecting the OLD centre with the NEW camera
     lands at viewport centre + (dx, dy) within 1 px.
  2. Pitch 55: same, within 3% of the drag length.
  3. clampCentre keeps the centre inside the bounds and leaves an inside centre unchanged.
  4. to3d/to2d keep centre and bearing exactly; zoom and pitch clamped as specified.
Negative control: ignore bearing in pan -> test 1 at bearing 90 must fail; revert.
Report back: files changed, test results, the negative control's failing output, open issues.
```

### Integration (architect)

`ui/Terrain3DView.kt`, `ui/MainScreen.kt`: shared camera, gestures via `CameraMath`, recentre in
3D, rebuild when the centre leaves the square, snapshot drape via `MapDrape`, layer toggles via
`TerrainTextures.Layers`, track line projected, **H** removed, one 2D/3D control.

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

*Pending: the independent auditor sub-agent is running (iteration 1, step E).*

## H. Log

- Iteration 1, step A–B done; research and audit pending.
