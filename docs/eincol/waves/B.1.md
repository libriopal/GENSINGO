# Wave B.1 — a mesh that can be believed (exe.md §11 B1–B7)

Candidates claimed: **B1–B7**, with the counter-candidates §11 assigns to this phase: **I10**
(induced crack) and **I11** (skirt removal). Seven candidates, within §8's limit. Branch
`eincol/B.1`, off the session branch at `8c6f5bf` (A.3's archive).

**Owner decision recorded at CLAIM (2026-10-06):** *"I approve of j20, 21, 23 and 24."* J20, J21,
J23 and J24 move from `proposed` to `queued` and are scheduled as wave **J.2**, placed right after
this wave (none needs anything from B, C, D or F; all four are on-device with no network and no
new dependency). J22 stays `proposed`: it was not approved.

## 1 — Load (measured from the tree)

| What | Measured |
|---|---|
| Mesh shape (B1, B3) | `TerrainMesh.build`: **one regular grid** over the whole 3D square (3×3 DEM tiles stitched into one `TerrainMath.Grid` by `DemTileStore.grid`, then sampled every two cells, ≤ 385 vertices a side), plus one skirt ring. No per-tile meshes, no LOD, no second mesh on screen at any time (`MeshSession` holds one square; panning out of it builds a replacement). Inside the square a tile boundary is just another row of the same lattice |
| Skirt winding (B1) | The four skirt strips are emitted with the same vertex order. Worked by hand against the surface's order (and the MVP's y-flip, `scale(1, -1, 1)`): the **south and west walls face outward, the north and east walls face inward**. With `GL_CULL_FACE` on, a camera north of the square (bearing ≈ 180°) or east of it (≈ 270°) culls the near wall: the edge of the model is a paper sheet with the sky under it. Seen from the south, the far (north) wall's inner face is drawn behind the terrain and shares its top edge with the surface: the "flat base and the terrain fight" that `DepthFallbackChooser`'s comment blames on 16-bit depth |
| No-data (B4, B5) | `DemTileStore.grid` fills a tile it could not load with the elevation of the nearest loaded edge (`fillMissing`): a flat plateau. Every consumer then treats it as ground: the mesh draws it, `TerrainTextures` paints habitat, contours and hillshade on it, `Hydrology` traces channels across it, `SuitabilityRasterizer` scores it (flat, mid-slope ground scores a visible heat on the 2D map), and `RadiusScan.candidates` can rank a place on it. The only trace is the 3D status suffix " · N tile(s) missing, flattened". `decodeTerrarium` has no no-data rule: a transparent or black pixel decodes to −32,768 m |
| Low score vs unknown (B5) | `SuitabilityRasterizer.colourFor` makes every score under 0.35 fully transparent. A gap drawn as transparent would read as weak ground |
| Normals (B6) | Analysis: Horn (1981) 3×3 on the DEM grid (`TerrainMath.slopeAspect`). Mesh: central differences over the **vertex lattice** (two cells apart, bilinear-sampled). Texture hillshade: central differences over **texels** (B15's business, B.3). Three stencils for one slope |
| Depth (B7) | 24-bit depth where offered, else 16 (`DepthFallbackChooser`). Near plane `viewportHeight / 50` = 48 px on a 2400-px screen, whatever the camera. 3D pitch is clamped to 15–80° (`Terrain3DView`). Draped overlays are baked into the one texture: there is no second coplanar surface to fight. Arithmetic (raw in this record, §3): at the fitted square the depth step at the far corner is 0.06 m with 24 bits and **15–16 m with 16 bits** |
| Tests | 388 per variant (A.3). Fixtures: `terrain_boone_z14.bin`, `terrain_boone_z15.bin` (real Boone NC terrain), `scan_boone_z12.bin` |
| Device | `aosp` AVD cold-booted again (container restarted) |

## 2 — Distribution (tail first)

| p | Reading of "B.1" |
|---|---|
| **0.24** | **(tail) B1–B3 are unnecessary: the mesh is one grid, so tile boundaries cannot crack and there are no T-junctions** |
| 0.22 | B4/B5: missing elevation is drawn, scored and ranked as ground; that is invariant 2, and it reaches the ranking |
| 0.16 | B1 as a real defect somewhere other than tile boundaries |
| 0.14 | B6: the lighting and the score disagree about which way a slope faces |
| **0.12** | **(tail) B7's z-fighting is not a precision problem at all but a geometry one** |
| 0.12 | B2: a detector is worth having even if no crack exists today, because B3 or a future LOD would bring them |

### Working the tail

**"B1–B3 are unnecessary": right about the boundaries, wrong about the cracks.** No tile boundary
exists in the geometry, so B3 (quadtree LOD with stitching) has nothing to stitch: its witness, a
wireframe at a depth boundary, cannot be taken because there is no depth boundary. B3 is **struck**,
to re-open if the 3D view ever draws more than one mesh or any LOD. But working B1's witness
("no background showing through at grazing tilt") at every bearing instead of only the default one
found the real crack: the two inward-facing walls. It is not between tiles; it is between the model
and the sky. B1 is re-based onto it and B2's detector is aimed at the mesh that exists.

**"B7 is a geometry problem": mostly right.** The z-fight the code comment remembers is the north
wall's inner face sharing an edge with the surface, which B1's fix removes (an outward far wall is
back-facing and culled). What remains is real but small: on a 16-bit device the far corner resolves
depth only to ~16 m, coarse enough for a ridge to bleed into the slope behind it. That part is a
precision problem with a precision fix (§4).

## 3 — Falsify (what would break each claim)

| Claim | Counter-witness that would break it |
|---|---|
| The mesh has no tile boundaries | A second mesh drawn at once, or a per-tile index range in `TerrainMesh`. Read: neither exists (`grep -n "skirtStrip\|for (j in 0 until n - 1)"` is the whole index builder) |
| Two walls face inward | A topology check in which every interior edge is traversed once in each direction. The current mesh fails it on the north and east boundary edges (§7, first red run) |
| No-data is drawn as ground | A mosaic with an unloaded tile whose mesh has no triangle over it. Today `fillMissing` guarantees triangles there |
| No-data is ranked | `RadiusScan.candidates` returning a cell inside a missing tile. Nothing stops it today |
| The mesh and the score use different slopes | Horn's gradient and the lattice central difference agreeing within 0.01° on real terrain. They agree on a plane (both are exact there), so a plane cannot decide this; the Boone fixture can |
| 16 bits resolve only ~16 m at the far corner | The raw arithmetic: depth step ≈ d² (f − n) / (f · n · 2^b). At pitch 80°, d = 4,089 px, n = 48 px, f = 116,871 px: 5.3 px of depth = 16.0 m at 3.0 m/px. At 24 bits: 0.06 m |
| Overlays cannot z-fight | A second draw call over the terrain at the same depth. `TerrainGlRenderer.onDrawFrame` issues exactly one `glDrawElements`; markers, track and ring are a Compose canvas with no depth |

## 4 — Evaluate (chosen designs, rejected alternatives)

**B1 — walls.** *Chosen:* walk the north and east edges in the opposite order, so every wall
traverses its boundary edge opposite to the surface triangle that owns it (one rule, checked by B2).
*Rejected:* (a) disable face culling: every hidden back face then costs fill rate, and the far
wall's inner face would be drawn again; (b) a separate wall draw with culling off: a second draw
call and state change for four quads.

**B2 — crack detector.** *Chosen:* an edge-use oracle over the index buffer: every undirected edge
is used by exactly two triangles in opposite directions, except the skirt's bottom ring (open by
design: the camera never goes below ground) and the rim of a no-data hole. It finds open edges
(cracks, T-junctions, a dropped triangle, a missing skirt) and flipped walls with one pass.
*Rejected:* (a) a screenshot-diff crack finder: device-only and blind to cracks off screen; (b) a
geometric "no background pixel inside the silhouette" ray test: needs a rasteriser and still misses
cracks that a camera does not happen to look through. The oracle lives in test sources; at runtime
it would cost ~450k map entries per build for nothing a user sees.

**B3 — struck** (see §2). *Rejected alternatives:* building a quadtree LOD now (a rewrite of a
working mesh path for a square that never exceeds 385² vertices: §9, and no need measured); keeping
B3 queued with a witness that cannot be taken (§11 forbids it).

**B4 — no-data.** *Chosen:* keep the edge-extended elevation for the neighbourhood operators (TPI,
wetness and channels need finite values everywhere, and §9 forbids rewriting the analysis inside a
rendering wave), and carry a **no-data mask** on the `Mosaic`, set for unloaded tiles and for pixels
that decode to no real elevation (alpha 0, or below −11,000 m: deeper than any ocean). Every
consumer honours it: the mesh drops triangles and walls over it (a hole), `elevationAt` returns null
(markers do not stand on invented ground), the texture and the 2D raster draw "unknown" there,
channels are not drawn or measured over it, and cells whose Horn stencil touches it are neither
scored nor ranked nor used as learner background. *Rejected:* (a) NaN in the grid itself: every
operator in `TerrainMath` and `Hydrology` would have to become NaN-aware, a rewrite of the analysis;
(b) refusing to build any view with a gap: the field case is "half the square cached", and a square
with an honest hole beats no square.

**B5 — unknown looks unknown.** *Chosen:* a diagonal hatch in an existing colour (the 3D legend's
dim text colour, `Gen.TextDim`, at partial alpha) on the 2D raster and in the 3D texture, with a
legend row "No elevation data" shown only when the area has a gap (no dead legend). In 3D the gap is
a hole, so the legend says so. *Rejected:* (a) transparent: identical to "weak ground" (§1); (b) a
new solid "unknown" colour: a palette change (§9), and a solid fill looks like data.

**B6 — one slope.** *Chosen:* one Horn gradient (`TerrainMath.horn`, inline, no allocation) used by
`slopeAspect` and by the mesh, which samples it bilinearly at each vertex exactly as it samples
elevation. *Rejected:* (a) compute the analysis from the mesh lattice instead: changes every score
(a model change wearing a rendering label); (b) normals in the vertex shader from a height texture:
moves tested arithmetic into GLSL, which nothing here can test.

**B7 — depth.** *Chosen:* a terrain-aware near plane: the eye's height above the highest ground in
the square, times the cosine of the frustum's half-diagonal, times 0.9, and never below the old
`viewportHeight / 50`. Precision is ~1/near, so this buys 10–35× where it matters (16-bit devices,
high pitch) and nothing can be clipped: no visible point is nearer than that bound. *Rejected:*
(a) require 24-bit depth: low-end GPUs (B20) would lose the 3D view; (b) a tight far plane from the
mesh bounds: precision is dominated by the near plane, so it buys under 1 %; (c) reversed-Z: needs
a float depth buffer and `glClipControl`, not in ES 3.0.

## 5 — Design (files, contracts, witnesses, negative controls)

| ID | Change | Observable (witness) | Negative control (mutant, `tools/mutate.py`) |
|---|---|---|---|
| B1 | `TerrainMesh`: north and east skirt strips walked in reverse | `MeshTopologyTest`: no edge used twice in one direction; device: bearing 180° at 60°+ pitch shows the north wall, before and after | X3: north strip back in forward order → same-direction edges reported |
| B2 | `MeshTopology` (test oracle): open, same-direction and over-shared edges | Full-data mesh: open edges are exactly the 4·(n − 1) bottom-ring edges, nothing else | X1 (= I10): one surface triangle dropped → two open surface edges; X2 (= I11): skirt strips not emitted → 4·(n − 1) open surface edges |
| B3 | struck (one grid, no LOD); re-opens with any multi-mesh or LOD view | — | — |
| B4 | `DemTileStore`: `Mosaic.noData`, `isNoData(argb)`; `TerrainMesh`, `Terrain3D`, `TerrainTextures`, `SuitabilityRasterizer`, `RadiusScan` honour it | A mosaic with one unloaded interior tile: no triangle has a vertex over it, `elevationAt` is null there, no ranked candidate or background sample falls in it, its texels and raster pixels are "unknown". Device: a cached tile removed under the square → a hole in 3D, hatch in 2D | X6: mesh treats every vertex as valid → triangles over the gap; X7: `RadiusScan` ignores the mask → a candidate inside the gap; X10: `isNoData` always false → a transparent pixel decodes as ground |
| B5 | `SuitabilityRasterizer.unknownAt` hatch; legend rows (2D sheet, 3D legend) only when there is a gap | Unknown pixels differ from the transparent low-score pixel and from every ramp colour; the hatch is periodic (half its pixels clear, so the basemap shows); screenshot of both legends | X8: unknown drawn as `colourFor(0.0)` → equal to a low score |
| B6 | `TerrainMath.horn`; `TerrainMesh` normals from it | Analytic plane: exact normal at every vertex (N/S sign included); Boone z15: every mesh normal equals the bilinear Horn normal within 0.01°, while the old lattice stencil is off by over 1° somewhere | X4: south gradient sign flipped → plane test fails; X5: mesh samples the nearest cell instead of bilinear → agreement test fails |
| B7 | `DepthRange.near`; `MapCamera.mvpForMeshBuiltAt(nearPx)`; the 3D view passes the square's highest ground | (a) No vertex inside the frustum is nearer than the near plane, over pitches 15–80°, eight bearings, fit zoom to fit + 3; (b) 16-bit depth step at the far corner under one z15 cell (3.9 m) at every pitch; it is 16 m with the old near. Device: 80° screenshot, no shimmer | X9: near ignores terrain height → a vertex is clipped at fit + 3 |

## 6 — Implement (as built)

| ID | Files | What changed |
|---|---|---|
| B1 | `terrain3d/TerrainMesh.kt` | The north and east skirt strips are walked in reverse (`n - 1 downTo 0`), so every wall walks its boundary edge opposite to the surface triangle that owns it. One rule for all four, stated at the call site |
| B2 | `test/…/terrain3d/MeshTopology.kt`, `MeshTopologyTest.kt` | The edge-use oracle (open / same-direction / over-shared edges) and three tests: the full mesh is closed but for the 4·(n − 1) bottom-ring edges; the oracle finds an induced crack (I10) and a removed skirt (I11) on edited index buffers; from eight bearings at 60° each wall is front-facing on screen exactly when the camera is on its outward side, projected with the view's own matrix |
| B3 | — | Struck (§2): one grid, no LOD, no depth boundary; re-opens with any multi-mesh or LOD view |
| B4 | `terrain/DemTileStore.kt`, `terrain3d/TerrainMesh.kt`, `terrain3d/Terrain3D.kt`, `terrain3d/TerrainTextures.kt`, `terrain/SuitabilityRasterizer.kt`, `terrain/ContourLines.kt`, `terrain/WaterLines.kt`, `research/RadiusScan.kt` | `Mosaic.noData` (null when complete) with `hasData`, `scorable` (the 3×3 Horn stencil all real) and `interiorNoDataCells`. `isNoData(argb)`: alpha 0 or below −11,000 m; `decodeTerrarium` gives NaN there. `markNoData` (pure, tested) marks unloaded tiles and NaN pixels, demotes an all-NaN tile to missing, and fills in-tile gaps from the tile's own nearest real cells (`fillWithinTile`) before `fillMissing` edge-extends missing tiles as before. Consumers: the mesh emits no triangle or wall touching a vertex whose four source cells are not all real, and its relief ignores the stand-in; `elevationAt` is null over no-data; the texture paints unknown texels (no habitat, hillshade, contour or channel) and keeps the stand-in out of the tint range; `scoreGrid` is NaN where no sample is scorable; 2D contours and water lines are cut where they cross no-data; `RadiusScan` skips unscorable cells in `candidates`, `background`, `correlationRangeM`, `factorsAt` and its creek search; `interiorLengthM` counts no channel over no-data. After the device run (phase 8): `Mosaic.displayable` judges a displayed point (vertex, texel, marker height) on its cells clamped into the interior, so a missing halo tile no longer takes the square's edge |
| B5 | `terrain/SuitabilityRasterizer.kt` (`unknownAt`, `HATCH`, `pixels`), `ui/map/FieldMap.kt`, `ui/Terrain3DView.kt` | A diagonal hatch over a quarter of the pixels in `Gen.TextDim` (0x8FA89A) at alpha 0xB0. 2D: the status chip adds "hatched: no elevation data" only when the interior has a gap. 3D: a legend row "Hole: no elevation data" (sky-coloured swatch) only when the square has one; the build note now reads "N tile(s) without elevation, left as holes" (it said "flattened") |
| B6 | `terrain/TerrainMath.kt`, `terrain3d/TerrainMesh.kt` | `TerrainMath.horn` (inline, the Horn stencil `slopeAspect` already used, now shared; no allocation); mesh normals are the bilinear blend of the four source cells' Horn gradients, (−dz/dx·e, −dz/dy·e, 1) normalised. The lattice central-difference pass is gone |
| B7 | `terrain3d/DepthRange.kt`, `terrain3d/MapCamera.kt`, `terrain3d/Terrain3D.kt`, `ui/Terrain3DView.kt` | `DepthRange.near(cam, highestAbovePlanePx)` = max(MapLibre's `viewportHeight / 50`, 0.9 × (eye height − highest ground) × cos(frustum half-diagonal)); `mvpForMeshBuiltAt(nearPx)`; `Terrain3D.highestAbovePlanePx`; the ready log now names the depth size the EGL chooser got and whether the square has holes |
| I20 (partial) | `tools/mutate.py` | Refuses to start when two mutations share an id; SIGTERM unwinds like SIGINT so the `finally` restores the mutated file |
| — | `tools/mutate.py` | X1–X11, the wave's negative controls (drafted as V1–V10; V and W were already taken, which is how I20 was hit again), plus X11: a tile with no real pixel counted as loaded, and X12 (after the device run): an edge vertex judged on the halo |

## 7 — Verify (raw output in `docs/eincol/evidence/B.1-*`)

| Check | Result |
|---|---|
| Build | `assembleDebug` **exit 0** on the final code, 27fe090 (`B.1-02-build.txt`); the x86_64 field build for the device runs and the arm64 field build delivered |
| Tests | `:app:test` **exit 0**: 403 per variant (debug, release, field), 0 failures, 1 skipped (`B.1-04-test.txt`; A.3: 388, +15) |
| Lint | `:app:lint` **exit 0**: 0 errors, 79 warnings, none above the bootstrap baseline by (check, file) (`B.1-03-lint.txt`) |
| Witnesses | the edge-use oracle (B2); a projection of every wall triangle through the view's own matrix from eight bearings (B1); a plane of known gradient and the analysis's own slope and aspect on real terrain (B6); the depth step measured over 7 pitches × 8 bearings (`B.1-05-depth-measured.txt`: 0.11–1.70 m at 16 bits against 13.5–17.5 m with the fixed near plane) and no vertex nearer than the near plane over 6 zooms (B7) |
| Negative controls | **X1–X12: 12/12 killed** (`B.1-07-mutants.txt`; X6 retargeted and X12 added after the device run). The I20 guards witnessed (`B.1-08-harness-guards.txt`) |
| Device (aosp AVD, Android 14 / API 34, x86_64, swiftshader; one cached elevation tile east of the start removed as root, at zooms 15, 14 and 13) | **Before (the A.3 build, `B.1-09`, `B.1-device-before-02…05`):** the missing tile drawn as a striped flat slab with an invented creek along it, status "1 tile(s) missing, flattened", 42 km of creeks; near wall present at bearing 0 and after the 270° turn (south, west), **missing after the 90° and 180° turns (east, north)**: the sky under the edge. **After, run 1 (6866b6f, `B.1-10`, `B.1-device-after-01…07`):** the tile a hole with the sky through it, legend row "Hole: no elevation data", status "1 tile(s) without elevation, left as holes", 35 km of creeks, ready log `depth 24 bits · holes (no elevation)`; the east wall back; **the north and west walls missing** (phase 8, finding 1); 80° view clean along every edge (`-06`); the 2D map with the missing zoom-14 tile hatched, status "hatched: no elevation data", contours and creeks stopping at its edge (`-07`). **After, run 2 (27fe090, `B.1-11`, `B.1-device-after2-02…05`):** the near wall present at bearing 0 and after every quarter turn (south, east, north, west: `-02…05`), the hole and its legend row as in run 1, the same ready line (`depth 24 bits · holes (no elevation)`). Zero lines with a z/x/y shape in the app's log in every run |

## 8 — Re-evaluate

**What the device found that the design did not:**
1. **B4's rule took the edges of the square with it.** A vertex on the square's edge is
   interpolated half a cell into the halo; on this emulator the halo tiles north and west of the
   square were never cached, so B4 marked every vertex on those edges as having no data and the
   north and west walls (and the edge row of the surface) were dropped. No unit test had a missing
   halo. Fixed in 27fe090 (`Mosaic.displayable`: a displayed point is judged on its cells clamped
   into the interior, where the stand-in is a copy of the interior's own edge), with
   `aMissingHaloKeepsTheEdgesAndTheWalls` and X12. Scoring stays strict: a cell whose slope
   stencil reads the stand-in is unknown, so a missing halo leaves a one-cell hatched rim, which is
   honest and about one screen pixel.
2. **B1's defect is real and was visible** (before-run): from the east and the north the near wall
   was culled. It had not been seen because every earlier device run looked from the south.
3. **The 2D habitat raster takes 494 s on this emulator** (`habitat: rasterised 768x768 in 494172
   ms`), so a 2D screenshot taken before it lands shows a dark map. A.3's 2D shots were taken too
   early for the same reason; it is not a regression (A.2's `-03` shows the raster once it landed).
   The gate now takes its 2D shot on the way back from 3D.
4. **This emulator's system server falls behind after a cold boot** and stacks "Process system
   isn't responding" dialogs; hiding error dialogs (`hide_error_dialogs 1`) let the runs proceed.
   And **the host's memory cgroup killed the emulator** when Gradle's test and lint run overlapped
   it (`Memory cgroup out of memory: Killed process … (qemu-system-x86)`): device runs and Gradle
   must not overlap here. After the next cold boot the AVD's bluetooth stack crashed natively
   (`libbluetooth_jni.so … ShimModuleStartUp`) and took the system services down with it; the
   probable cause of the starved system server all session. Bluetooth is now disabled on the AVD
   (`pm disable com.android.bluetooth`; the app does not use it). One gate start that had hit the
   dead services kept running and tapped the same app as the next one for a few minutes (A.3's
   lesson, repeated): it was stopped, and the 3D build and every shot of run 2 are the second
   gate's (noted in `B.1-11`).
5. **The mutation harness's id trap fired twice more** (V1–V5, then W1–W2 were taken): fixed at the
   root this time (duplicates refused before any edit).

**Sovereignty classifier over this wave's claims:**

| Claim | In time | Against a witness | Verdict |
|---|---|---|---|
| Two of the four walls faced inward before B.1 (B1) | ✅ | ✅ device before-run + the topology check failing on the old order (X3) | sound |
| All four walls face outward now (B1) | ✅ | ✅ `MeshTopologyTest` (eight bearings, projected) + device run 2 | sound |
| The crack detector finds an induced crack and a missing skirt (B2) | ✅ | ✅ X1, X2 killed; edited index buffers in the test | sound |
| The mesh has no tile boundary, so B3 has nothing to stitch | ✅ | ✅ the index builder read in full (one lattice, one skirt) | sound → B3 **struck** |
| Missing elevation is never drawn, scored or ranked as ground (B4) | ✅ | ✅ `NoDataTest` (mesh, marker height, score, texture, ranking, learner background, missing halo) + X6, X7, X10, X11, X12 + device before/after | sound |
| Unknown does not look like weak ground (B5) | ✅ | ✅ `unknownGroundDoesNotLookLikeWeakGround` + X8; device: hatch and status in 2D, hole and legend row in 3D | sound |
| The mesh is lit by the slope the score uses (B6) | ✅ | ✅ plane (exact) + Boone (within 0.01° of the analysis's own slope and aspect; the old stencil off by over 1°) + X4, X5 | sound |
| 16-bit depth resolves under one cell at the far ground, and nothing is clipped (B7) | ✅ | ✅ `DepthRangeTest` + X9; device at 80° clean (24-bit there) | sound for the arithmetic; the 16-bit screen needs a device that falls back to 16 bits: **open** (B20) |
| Draped overlays cannot z-fight (B7) | ✅ | ✅ one draw call, overlays baked into its texture | sound |
| A duplicate mutant id is refused; SIGTERM restores the file (I20, partial) | ✅ | ✅ `B.1-08-harness-guards.txt` | sound; the class check is **open** |
| Scores near a hole are free of the stand-in | ❌ | ❌ | **open**: registered as I23 |

**Drafted this iteration (owner directive, five ideas):** J25 no holes on the ridge (a pre-trip
check of the saved area's tiles); J26 light left in this hollow (terrain sunset from the DEM); J27
the climb before you walk it (profile to a ranked place); J28 what your own patches have in common
(aspect and slope roses of your finds against the background); J29 buzz on arrival.
