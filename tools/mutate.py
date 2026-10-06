#!/usr/bin/env python3
"""
EINCOL rung 1: break each property the app claims and check that a test notices.

Every mutation is a literal (old -> new) edit to one source file, plus the test classes
expected to kill it. The harness:
  1. refuses a mutation whose `old` text is not found exactly once (a vacuous mutation
     that edits nothing is the "vacuous control" failure mode, not a pass),
  2. applies it, runs the named test classes, records KILLED (tests failed) or SURVIVED,
  3. restores the file byte-for-byte, and verifies the restore,
  4. refuses to start when two mutations share an id (exe.md I20: A.2's R1-R10 ran the old
     hydrology R1-R3 instead, and B.1's first V1-V5 and W1-W2 collided again), and restores the
     mutated file when it is stopped by SIGTERM or SIGINT, not only when a test run ends (a
     killed A.2 run left RadiusScan.kt mutated),
  5. with --check, lists every mutation that no longer applies and exits 1 (wave M.1).

A SURVIVED row is a candidate defect, not a verdict: it may be an equivalent mutant.
Survivors are triaged by hand in EINCOL_REPORT.md Phase 7.

Usage: tools/mutate.py [ID ...]      (no IDs = all)
       tools/mutate.py --check        (exit 1 if any mutation no longer applies)
"""
import collections
import signal
import subprocess
import sys
import time

M = "app/src/main/java/com/ginsengo/steward/"
T = "com.ginsengo.steward."

MUTATIONS = [
    # --- research: the witness layer
    ("V1", "validator accepts unknown candidate IDs", M + "research/ResearchValidator.kt",
     "if (id !in candidateIds) { idsRejected++; continue }", "if (false) { idsRejected++; continue }",
     [T + "research.ResearchValidatorTest", T + "research.ResearchRepositoryTest"]),
    ("V2", "validator accepts citations that were never retrieved", M + "research/ResearchValidator.kt",
     "Witness.EXACT -> exact[n]", "Witness.EXACT -> exact[n] ?: SourceRef(raw, raw)",
     [T + "research.ResearchValidatorTest", T + "research.ResearchRepositoryTest"]),
    ("V3", "coordinates in model prose are kept", M + "research/ResearchValidator.kt",
     'var t = COORDS.replace(text) { n++; "[location removed]" }', "var t = text",
     [T + "research.ResearchValidatorTest"]),
    ("V4", "'legal to dig' sentences are kept", M + "research/ResearchValidator.kt",
     't = LEGALITY.replace(t) { n++; "" }', "t = t",
     [T + "research.ResearchValidatorTest"]),
    ("V5", "duplicates are kept", M + "research/ResearchValidator.kt",
     "if (!seen.add(id)) { dupes++; continue }", "seen.add(id)",
     [T + "research.ResearchValidatorTest"]),
    # --- research: positions and privacy
    ("P1", "a model item is joined to the wrong candidate", M + "research/SuggestionAssembler.kt",
     "val c = byId[item.candidateId] ?: return@forEach", "val c = candidates.firstOrNull() ?: return@forEach",
     [T + "research.SuggestionAssemblyTest", T + "research.ResearchRepositoryTest"]),
    ("P2", "the prompt carries the exact fix", M + "research/SuggestionAssembler.kt",
     "fun cell(v: Double) = floor(v * 10.0) / 10.0 + 0.05", "fun cell(v: Double) = v",
     [T + "research.PromptPrivacyTest", T + "research.ResearchRepositoryTest"]),
    ("P3", "consent is not checked before sending", M + "research/ResearchRepository.kt",
     "!settings.researchConsent -> \"Research model is off. These are computed on your phone.\"",
     "false -> \"Research model is off. These are computed on your phone.\"",
     [T + "research.ResearchRepositoryTest"]),
    ("P4", "offline is not checked before sending", M + "research/ResearchRepository.kt",
     '!isOnline() -> "Offline.', 'false -> "Offline.',
     [T + "research.ResearchRepositoryTest"]),
    ("P8", "an automatic refresh calls the model without approval", M + "research/ResearchRepository.kt",
     '!allowModel -> "Updated on this phone', 'false -> "Updated on this phone',
     [T + "research.ResearchRepositoryTest"]),
    ("P9", "a stale fix starts research", M + "research/ResearchTrigger.kt",
     "if (now - fixTime > FRESH_MS) return false", "", [T + "research.ResearchTriggerTest"]),
    ("P5", "protected land is suggested", M + "research/RadiusScan.kt",
     "if (exclude(lat, lng)) continue", "",
     [T + "research.RadiusScanRealTerrainTest"]),
    ("P6", "candidates outside the radius", M + "research/RadiusScan.kt",
     "if (!inRadius(lat, lngOfCol(x.toDouble())) || !scorable(x, y)) continue", "if (!scorable(x, y)) continue",
     [T + "research.RadiusScanRealTerrainTest"]),
    ("P7", "candidates are not separated", M + "research/RadiusScan.kt",
     "if (picked.any { Prospects.distanceMetres(it.lat, it.lng, lat, lng) < minSeparationM }) continue", "",
     [T + "research.RadiusScanRealTerrainTest"]),
    # --- providers
    ("C1", "pause_turn is not resumed", M + "research/ClaudeResearchClient.kt",
     "StopReason.PAUSE_TURN -> history += msg.toParam()", "StopReason.PAUSE_TURN -> throw ResearchFailure(ResearchFailure.Status.NO_SUBMISSION, \"x\")",
     [T + "research.ClaudeWireTest"]),
    ("C2", "the submit tool is not strict", M + "research/ClaudeResearchClient.kt",
     ".strict(true)", ".strict(false)", [T + "research.ClaudeWireTest"]),
    ("C3", "a refusal reads as no submission", M + "research/ClaudeResearchClient.kt",
     "ResearchFailure.Status.REFUSED,", "ResearchFailure.Status.NO_SUBMISSION,", [T + "research.ClaudeWireTest"]),
    ("G1", "the Gemini key goes back into the URL", M + "research/GeminiResearchClient.kt",
     'post("$baseUrl/v1beta/models/$model:generateContent", body)', 'post("$baseUrl/v1beta/models/$model:generateContent?key=$apiKey", body)',
     [T + "research.GeminiWireTest"]),
    # --- learning
    ("L1", "adopt without a significance test", M + "learn/FindLearner.kt",
     "return (clusterGains.average() >= MARGIN && p < ALPHA) to p", "return (clusterGains.average() >= MARGIN) to p",
     [T + "learn.FindLearnerTest", T + "learn.LearnerGateTest"]),
    ("L2", "judge the learner on its own training data", M + "learn/FindLearner.kt",
     "val w = fitter(train, bgTrain, prior)", "val w = fitter(finds.map { it.factors }, bgTrain, prior)",
     [T + "learn.FindLearnerTest", T + "learn.LearnerGateTest"]),
    ("L3", "every find is its own cluster", M + "learn/FindLearner.kt",
     "const val CLUSTER_M = 200.0", "const val CLUSTER_M = 0.0", [T + "learn.FindLearnerTest"]),
    ("L4", "learning starts from one find", M + "learn/FindLearner.kt",
     "const val MIN_FINDS = 5", "const val MIN_FINDS = 1", [T + "learn.FindLearnerTest"]),
    ("L5", "held-out blocking falls back to a fixed 200 m (the critic's leak)", M + "research/ResearchRepository.kt",
     "linkM = s.correlationRangeM(),", "linkM = 200.0,", [T + "research.ResearchRepositoryTest"]),
    ("L6", "leakage test: range blocking replaced by 200 m", M + "learn/FindLearner.kt",
     "val cl = clusters(finds, linkM.coerceAtLeast(CLUSTER_M))", "val cl = clusters(finds, CLUSTER_M)",
     [T + "learn.LearnerLeakageTest"]),
    # The user's field data is ground truth (learn/UserFinds.kt): each of these quietly
    # demotes some of it again, and a test must notice.
    ("F1", "a find with a poor GPS fix is saved as less than confirmed", M + "memory/FieldMemoryRepository.kt",
     "verification = UserFinds.CONFIRMED,",
     "verification = if (fix.accuracyM <= 20f) UserFinds.CONFIRMED else \"UNVERIFIED\",",
     [T + "research.ResearchRepositoryTest"]),
    ("F2", "imprecise finds are kept off the learner", M + "research/ResearchRepository.kt",
     "samples += FindLearner.Sample(factors, f.lat, f.lng)",
     "if ((f.accuracyM ?: 99f) <= 20f) samples += FindLearner.Sample(factors, f.lat, f.lng)",
     [T + "research.ResearchRepositoryTest"]),
    ("F3", "patches imported from the older app are kept off the learner", M + "research/ResearchRepository.kt",
     "samples += FindLearner.Sample(factors, f.lat, f.lng)",
     "if (f.sourcePatchId == null) samples += FindLearner.Sample(factors, f.lat, f.lng)",
     [T + "research.ResearchRepositoryTest"]),
    ("F4", "a track 500 m away 'visits' a suggestion", M + "memory/FieldMemoryRepository.kt",
     "const val VISIT_M = 40.0", "const val VISIT_M = 4_000.0", [T + "research.ResearchRepositoryTest"]),
    # --- tracking and power
    ("T1", "the original accuracy/2 noise floor", M + "field/TrackFilter.kt",
     "val noise = fix.accuracyM.toDouble() * if (still) 3.0 else 1.0", "val noise = fix.accuracyM.toDouble() / 2.0",
     [T + "field.TrackFilterTest"]),
    ("T2", "no centroid when speed is missing", M + "field/TrackFilter.kt",
     "if (window.size < MIN_WINDOW) return null", "if (window.size < 1) return null",
     [T + "field.TrackFilterTest"]),
    ("T3", "spikes are stored", M + "field/TrackFilter.kt",
     "> maxSpeedMps) return null", "> 1e12) return null", [T + "field.TrackFilterTest"]),
    ("W1", "screen-off does not batch", M + "field/PowerPolicy.kt",
     "maxDelayMs = if (batch) 60_000L else 0L,", "maxDelayMs = 0L,", [T + "field.PowerPolicyTest"]),
    ("W2", "low battery still auto-researches", M + "field/PowerPolicy.kt",
     "                minDistanceM = 5f,\n                allowAutoResearch = false,\n            )\n        }\n        if (!tracking)",
     "                minDistanceM = 5f,\n                allowAutoResearch = true,\n            )\n        }\n        if (!tracking)",
     [T + "field.PowerPolicyTest"]),
    # --- heatmap and memory
    ("H1", "the 7a3656d regression: TPI and TWI dropped from the drawn surface", "app/src/main/java/com/ginsengo/steward/terrain/SuitabilityRasterizer.kt",
     "                tpiMeters = tpi,\n                twi = wet,", "                tpiMeters = -4.0,\n                twi = 8.0,",
     [T + "terrain.DrawnSurfaceTest"]),
    # --- 3D view and water (Phase 8)
    ("H2", "the 3D habitat colour is not the 2D heatmap colour", M + "terrain3d/TerrainTextures.kt",
     "fade(SuitabilityRasterizer.colourFor(score, MIN_SCORE), layers.habitatOpacity)",
     "fade(SuitabilityRasterizer.colourFor(score * 0.8, MIN_SCORE), layers.habitatOpacity)",
     [T + "terrain3d.Terrain3DTest"]),
    ("H3", "the 3D view scores the ground its own way again", M + "terrain3d/Terrain3D.kt",
     "SuitabilityRasterizer.scoreGrid(mosaic, interior, tpiRadiusM, weights)", "SuitabilityRasterizer.scoreGrid(mosaic, interior / 4, tpiRadiusM, weights)",
     [T + "terrain3d.Terrain3DTest"]),
    ("H4", "weak ground fades to black instead of bare relief", M + "terrain3d/TerrainTextures.kt",
     "else ramp(NEUTRAL_RAMP, t)", "else 0x000000",
     [T + "terrain3d.Terrain3DTest"]),
    ("H5", "contours only ever darken (vanish on dark ground)", M + "terrain3d/TerrainTextures.kt",
     "if (luminance(px[i]) >= 80.0)", "if (true)",
     [T + "terrain3d.Terrain3DTest"]),
    ("H6", "texture coordinates do not span the model", M + "terrain3d/TerrainMesh.kt",
     "verts[base + OFF_UV] = fx.toFloat()", "verts[base + OFF_UV] = (fx * 0.5).toFloat()",
     [T + "terrain3d.TerrainMeshTest"]),
    ("R1", "pits are not filled (every pit ends a stream)", M + "terrain/Hydrology.kt",
     "if (filled[i] <= zc) filled[i] = Math.nextUp(zc)", "if (false) filled[i] = Math.nextUp(zc)",
     [T + "terrain.HydrologyTest"]),
    ("R2", "channels are drawn without convergence (a plane gets creeks)", M + "terrain/Hydrology.kt",
     "DRAINAGE(20_000.0, \"small drainage\")", "DRAINAGE(200.0, \"small drainage\")",
     [T + "terrain.HydrologyTest"]),
    ("R3", "a missing tile is left as a 0 m cliff", M + "terrain/DemTileStore.kt",
     "                java.util.Arrays.fill(z, r * w + tx * TILE, r * w + (tx + 1) * TILE, v)\n", "",
     [T + "terrain.MissingTileFillTest"]),
    ("D1", "the separator becomes a space again", M + "data/db/Entities.kt",
     'const val SEPARATOR = "\\u001F"', 'const val SEPARATOR = " "', [T + "terrain.ConvertersTest", T + "data.MigrationTest"]),
    ("D2", "no migration from the field-tested build", M + "data/db/AppDatabase.kt",
     "val MIGRATIONS = arrayOf(MIGRATION_1_5, MIGRATION_4_5, MIGRATION_5_6)", "val MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6)",
     [T + "data.MigrationTest"]),
    ("H9", "the heatmap memo reuses the wrong cell's score", M + "terrain/SuitabilityRasterizer.kt",
     "(gx.toInt().coerceIn(1, g.w - 2) - ix0)", "(gx.toInt().coerceIn(1, g.w - 2) - ix0) / 2 * 2",
     [T + "terrain.DrawnSurfaceTest"]),
    ("R4", "a suggestion's 'nearest creek' is any small drainage", M + "research/RadiusScan.kt",
     "val minClass = (Hydrology.Kind.CREEK.ordinal + 1).toByte()", "val minClass = 1.toByte()",
     [T + "research.WaterContextTest"]),
    ("R5", "the model is told the creek's exact distance", M + "research/ResearchPrompt.kt",
     "val dist = ((w.distanceM / 50).roundToInt() * 50).coerceAtLeast(50)", "val dist = w.distanceM.roundToInt()",
     [T + "research.WaterContextTest"]),
    ("D3", "imported patches are demoted to LEGACY", M + "data/db/AppDatabase.kt",
     "0, 'VERIFIED', `id` ", "0, 'LEGACY', `id` ", [T + "data.MigrationTest"]),

    # --- one map (ARCHITECT iteration 1, docs/blueprints/one-map.md): contracts and integration
    ("A1", "the map drape is ignored (MAP mode bakes the relief)", M + "terrain3d/TerrainTextures.kt",
     "val map = ground.basemap?.takeIf { mode == Mode.MAP && it.size == size * size }",
     "val map: IntArray? = null", [T + "terrain3d.MapDrapeCompositeTest"]),
    ("A2", "the snapshot asks for zooms the saved region lacks", M + "ui/map/MapDrape.kt",
     "return minOf(wanted, ceiling).coerceAtLeast(1)", "return wanted",
     [T + "terrain3d.MapDrapeCompositeTest"]),
    ("B1", "3D pan ignores the terrain height", M + "terrain3d/CameraMath.kt",
     "if (!settled && heightRange != null) marchRay(mc, sx, sy, heightAt, heightRange)?.let { h = it }\n        return h",
     "if (!settled && heightRange != null) marchRay(mc, sx, sy, heightAt, heightRange)?.let { h = it }\n        return 0.0",
     [T + "terrain3d.CameraMathTest"]),
    ("B2", "3D pan ignores the bearing", M + "terrain3d/CameraMath.kt",
     "MapCamera(cam.lat, cam.lng, cam.zoom, cam.bearing, cam.pitch, viewportW, viewportH)",
     "MapCamera(cam.lat, cam.lng, cam.zoom, 0.0, cam.pitch, viewportW, viewportH)",
     [T + "terrain3d.CameraMathTest"]),
    ("B3", "no ray march when the height iteration does not settle", M + "terrain3d/CameraMath.kt",
     "marchRay(mc, sx, sy, heightAt, heightRange)?.let { h = it }", "marchRay(mc, sx, sy, heightAt, heightRange)?.let { }",
     [T + "terrain3d.CameraMathTest"]),
    ("I1", "isolines placed at cell corners (no interpolation)", M + "terrain/Isolines.kt",
     "accept((c - 1).toDouble(), r - ratio(bld, threshold, tld))", "accept((c - 1).toDouble(), r - 0.5)",
     [T + "terrain.IsolinesTest"]),
    ("O1", "re-anchoring moves the target but not the zoom (the picture jumps)", M + "terrain3d/CameraMath.kt",
     "val zoom = cam.zoom + ln2(d / dNew) +", "val zoom = cam.zoom + 0.0 * ln2(d / dNew) +",
     [T + "terrain3d.ReanchorTest"]),
    ("O2", "the 3D square's edges are interpolated linearly in latitude", M + "terrain3d/Terrain3D.kt",
     "val north = latOfEdge(mosaic, mosaic.haloPx.toDouble())",
     "val north = mosaic.northLat + (mosaic.southLat - mosaic.northLat) * (mosaic.haloPx.toDouble() / mosaic.grid.h)",
     [T + "terrain3d.SceneGeometryTest"]),
    ("O3", "2D contour lines lose the halo offset", M + "terrain/ContourLines.kt",
     "WaterLines.lngOfCell(m, xy[2 * i] + halo.toDouble())", "WaterLines.lngOfCell(m, xy[2 * i] + 0.0)",
     [T + "terrain.ContourLinesTest"]),
    ("O5", "after a gesture the ground plane moves by the exaggerated height", M + "terrain3d/Terrain3D.kt",
     "return next to anchorM + dh / EXAGGERATION", "return next to anchorM + dh",
     [T + "terrain3d.SceneGeometryTest"]),
    # --- exe.md wave A.1 (one camera): Q1 = exe.md I3, Q2 = exe.md I4
    ("Q1", "camera sign flip: bearing reversed in the mesh matrix only (exe.md I3)", M + "terrain3d/MapCamera.kt",
     "m = m * rotateZ(angle)\n        m = m * translate(originX * k - centerX",
     "m = m * rotateZ(-angle)\n        m = m * translate(originX * k - centerX",
     [T + "terrain3d.CameraConformanceTest"]),
    ("Q2", "Terrarium offset dropped (exe.md I4)", M + "terrain/DemTileStore.kt",
     "(r * 256f + g + b / 256f) - 32768f", "(r * 256f + g + b / 256f)",
     [T + "terrain.TerrariumDecodeTest", T + "terrain.RealTerrainTest"]),
    ("Q3", "north/south reversal in the one projection", M + "geo/Projection.kt",
     "atan(sinh(PI * (1.0 - 2.0 * y)))", "atan(sinh(PI * (2.0 * y - 1.0)))",
     [T + "geo.ProjectionTest"]),
    ("Q4", "latAtRow back to linear latitude (the defect A2 removed)", M + "terrain/DemTileStore.kt",
     "Projection.lat((tileY0 * TILE + row + 0.5) / Projection.worldPx(zoom, TILE))",
     "northLat + (southLat - northLat) * (row.toDouble() / grid.h)",
     [T + "geo.ProjectionTest"]),
    ("Q5", "a gesture report bumps the epoch (views chase their own gestures)", M + "terrain3d/CameraState.kt",
     "fun report(c: CameraState) = _state.update { it.copy(camera = c) }",
     "fun report(c: CameraState) = _state.update { it.copy(camera = c, epoch = it.epoch + 1) }",
     [T + "terrain3d.SharedCameraTest"]),
    ("O4", "3D elevation read half a cell off", M + "terrain3d/Terrain3D.kt",
     "mosaic.tileX0 * DemTileStore.TILE - 0.5", "mosaic.tileX0 * DemTileStore.TILE - 0.0",
     [T + "terrain3d.SceneGeometryTest"]),

    # --- wave A.2: one surface (measured hand-off, cross-fade, scene description)
    # S1-S3 (Handoff: fade, measured hand-off, relief scale) were removed in wave M.1 with
    # Handoff.kt: the flat map and the hand-off between the two maps no longer exist (J30).
    # S4 (the mesh ignores the relief scale) was removed in wave M.1 with the parameter: the
    # relief scale existed only for the cross-fade, which went with the flat map (J30).
    ("S5", "a draped layer drawn over the mesh instead of on it", M + "ui/map/SceneLayers.kt",
     "SceneLayer.CONTOURS, SceneLayer.WATER -> MeshDraw.BAKED",
     "SceneLayer.CONTOURS -> MeshDraw.BAKED\n        SceneLayer.WATER -> MeshDraw.CANVAS",
     [T + "ui.map.SceneLayersTest"]),
    ("S6", "the 3D ignores the Hillshade switch (registry)", M + "ui/map/SceneLayers.kt",
     "hillshade = SceneLayer.HILLSHADE.shown(s),", "hillshade = true,",
     [T + "ui.map.SceneLayersTest"]),
    ("S7", "the 3D bake shades whatever the switch says", M + "terrain3d/TerrainTextures.kt",
     "if (layers.hillshade) {", "if (true) {",
     [T + "terrain3d.Terrain3DTest"]),
    ("S8", "the 3D habitat ignores Heat opacity", M + "terrain3d/TerrainTextures.kt",
     "((c ushr 24) and 255) * opacity.coerceIn(0f, 1f)", "((c ushr 24) and 255) * 1f",
     [T + "terrain3d.Terrain3DTest"]),
    ("S9", "a layer declares a depth its 3D renderer does not honour", M + "ui/map/SceneLayers.kt",
     "TRACK(Depth.ON_TOP,", "TRACK(Depth.DRAPED,",
     [T + "ui.map.SceneLayersTest"]),
    ("S10", "one switch moves two layers", M + "ui/map/SceneLayers.kt",
     "{ s, on -> s.copy(trackLine = on) }", "{ s, on -> s.copy(trackLine = on, visited = on) }",
     [T + "ui.map.SceneLayersTest"]),

    # --- wave A.3: one map, finished (memory, continuity, gestures, occlusion) + field defects
    ("U1", "tile ids pass through the log redactor", M + "geo/LogRedaction.kt",
     '.replace(SLASHED) { "${it.groupValues[1]}/x/y" }', ".replace(SLASHED) { it.value }",
     [T + "geo.LogRedactionTest"]),
    ("U2", "a stale fix uses up the landing", M + "ui/map/CameraStart.kt",
     "fixAgeMs <= FRESH_FIX_MS && accuracyM", "fixAgeMs <= Long.MAX_VALUE && accuracyM",
     [T + "ui.map.CameraStartTest"]),
    ("U3", "the 3D view tilts at its old 0.15 deg/px", M + "terrain3d/GestureMath.kt",
     "const val TILT_DEG_PER_PX = 0.1f", "const val TILT_DEG_PER_PX = 0.15f",
     [T + "terrain3d.GestureMathTest"]),
    # U4 (Handoff's pitch easing) was removed in wave M.1 with Handoff.kt (J30).
    ("U5", "the budget evicts the most recently used", M + "perf/MemoryBudget.kt",
     ".sortedBy { it.lastUse }", ".sortedByDescending { it.lastUse }",
     [T + "perf.MemoryBudgetTest"]),
    ("U6", "the budget evicts what is on screen", M + "perf/MemoryBudget.kt",
     "entries.values.filter { !it.pinned }.sortedBy", "entries.values.filter { true }.sortedBy",
     [T + "perf.MemoryBudgetTest", T + "terrain3d.SceneGeometryTest"]),
    ("U7", "nothing is ever occluded", M + "terrain3d/Occlusion.kt",
     "if (g > z + CLEARANCE_M * ppm) return true", "if (g > z + CLEARANCE_M * ppm && false) return true",
     [T + "terrain3d.OcclusionTest"]),
    ("U8", "the markers drawn on top, ignoring their occlusion", M + "ui/map/SceneLayers.kt",
     "SceneLayer.FINDS, SceneLayer.SUGGESTIONS, SceneLayer.ME -> MeshDraw.CANVAS_OCCLUDED",
     "SceneLayer.FINDS, SceneLayer.SUGGESTIONS, SceneLayer.ME -> MeshDraw.CANVAS",
     [T + "ui.map.SceneLayersTest"]),
    ("U9", "the eye on the wrong side of the target", M + "terrain3d/MapCamera.kt",
     "centerY + cos(angle) * eyeY1", "centerY - cos(angle) * eyeY1",
     [T + "terrain3d.OcclusionTest"]),
    ("U10", "a trim ignores how hard the system asked", M + "perf/MemoryBudget.kt",
     "ceilingBytes = minOf(ceilingBytes, (baseCeilingBytes * share).toLong())",
     "ceilingBytes = minOf(ceilingBytes, baseCeilingBytes)",
     [T + "perf.MemoryBudgetTest"]),

    # --- wave B.1: a mesh that can be believed (walls, cracks, no-data, one slope, depth)
    ("X1", "an induced crack: one surface triangle dropped (I10)", M + "terrain3d/TerrainMesh.kt",
     "if (valid[b] && valid[c] && valid[d]) {",
     "if (valid[b] && valid[c] && valid[d] && !(i == n / 2 && j == n / 2)) {",
     [T + "terrain3d.MeshTopologyTest"]),
    ("X2", "the skirt removed (I11)", M + "terrain3d/TerrainMesh.kt",
     "if (!valid[a] || !valid[b]) continue", "continue",
     [T + "terrain3d.MeshTopologyTest"]),
    ("X3", "the north wall walked the old way (faces inward)", M + "terrain3d/TerrainMesh.kt",
     "skirtStrip((n - 1 downTo 0).map { it })", "skirtStrip((0 until n).map { it })",
     [T + "terrain3d.MeshTopologyTest"]),
    ("X4", "mesh normals with north and south swapped", M + "terrain3d/TerrainMesh.kt",
     "val nx = -dx * exaggeration; val ny = -dy * exaggeration",
     "val nx = -dx * exaggeration; val ny = dy * exaggeration",
     [T + "terrain3d.MeshNormalsTest"]),
    ("X5", "mesh normals from the nearest cell, not the analysis's blend", M + "terrain3d/TerrainMesh.kt",
     "val wgt = (if (c and 1 == 0) 1 - tx else tx) * (if (c shr 1 == 0) 1 - ty else ty)",
     "val wgt = if (c == 0) 1.0 else 0.0",
     [T + "terrain3d.MeshNormalsTest"]),
    ("X6", "the mesh draws missing elevation as ground", M + "terrain3d/TerrainMesh.kt",
     "valid[j * n + i] = mosaic.displayable(x0, y0)",
     "valid[j * n + i] = true",
     [T + "terrain3d.NoDataTest"]),
    ("X7", "the ranking scores missing elevation", M + "research/RadiusScan.kt",
     "if (!inRadius(lat, lngOfCol(x.toDouble())) || !scorable(x, y)) continue",
     "if (!inRadius(lat, lngOfCol(x.toDouble()))) continue",
     [T + "terrain3d.NoDataTest"]),
    ("X8", "unknown ground drawn as weak ground", M + "terrain/SuitabilityRasterizer.kt",
     "if (s.isNaN()) unknownAt(it % outSize, it / outSize) else colourFor(s, minScore)",
     "colourFor(s, minScore)",
     [T + "terrain3d.NoDataTest"]),
    ("X9", "the near plane ignores the ground's height", M + "terrain3d/DepthRange.kt",
     "val bound = (eyeHeight - highestAbovePlanePx) * cos(halfDiagonal) * SAFETY",
     "val bound = eyeHeight * cos(halfDiagonal) * SAFETY",
     [T + "terrain3d.DepthRangeTest"]),
    ("X10", "a transparent or black pixel decodes as ground", M + "terrain/DemTileStore.kt",
     "fun isNoData(argb: Int): Boolean = (argb ushr 24) == 0 || terrariumMetres(argb) < LOWEST_REAL_M",
     "fun isNoData(argb: Int): Boolean = false",
     [T + "terrain3d.NoDataTest"]),
    ("X11", "a tile with no real pixel counts as loaded", M + "terrain/DemTileStore.kt",
     "if (!loaded[ty * nx + tx] || holes == TILE * TILE) {", "if (!loaded[ty * nx + tx]) {",
     [T + "terrain3d.NoDataTest"]),
    ("X12", "an edge vertex judged on the halo (a missing halo tile takes the wall)", M + "terrain/DemTileStore.kt",
     "val ax = x0.coerceIn(haloPx, hiX); val bx = (x0 + 1).coerceIn(haloPx, hiX)",
     "val ax = x0; val bx = x0 + 1",
     [T + "terrain3d.NoDataTest"]),

    # --- wave M.1: the one 3D map (J30), travel memory (J31), battery (J32, J24), J20, J21, J23
    ("Y1", "the square ignores the camera's zoom (always the 3 km one)", M + "terrain3d/SquareLevel.kt",
     "val ideal = (FINEST + (zoom - fitFinest)).roundToInt().coerceIn(COARSEST, FINEST)",
     "val ideal = FINEST + 0 * (zoom - fitFinest).roundToInt()",
     [T + "terrain3d.SquareLevelTest"]),
    ("Y2", "no hysteresis: a camera at a boundary rebuilds back and forth", M + "terrain3d/SquareLevel.kt",
     "return if (abs(zoom - fitCurrent) > 0.5 + HYSTERESIS) ideal else current",
     "return if (abs(zoom - fitCurrent) >= 0.0) ideal else current",
     [T + "terrain3d.SquareLevelTest"]),
    ("Y3", "fixes are dots: no cells filled between them", M + "field/TravelMemory.kt",
     "val n = ceil(d / STEP_M).toInt().coerceAtLeast(1)", "val n = 1",
     [T + "field.TravelMemoryTest"]),
    ("Y4", "the upgrade's backfill rounds where the app floors (another grid)", M + "field/TravelMemory.kt",
     "CAST((`lat` + 90) * 10000 AS INTEGER) * 4000000", "CAST(ROUND((`lat` + 90) * 10000) AS INTEGER) * 4000000",
     [T + "data.TravelMigrationTest"]),
    ("Y5", "the memory mask upside down (rows run north)", M + "terrain3d/TravelMask.kt",
     "private fun row(lat: Double) = (Projection.y(lat) - y0) / (y1 - y0) * size",
     "private fun row(lat: Double) = (y1 - Projection.y(lat)) / (y1 - y0) * size",
     [T + "terrain3d.TravelMaskTest"]),
    ("Y6", "the map open on a still phone keeps a fix every 3 s", M + "field/PowerPolicy.kt",
     "            if (!moving) {\n                // Looking at the map, standing still (J32).",
     "            if (false) {\n                // Looking at the map, standing still (J32).",
     [T + "field.PowerPolicyTest"]),
    ("Y7", "the pacer drops the last frame of a gesture", M + "field/FieldPower.kt",
     "        trailing = true\n        return wait", "        return -1",
     [T + "field.FieldPowerTest"]),
    ("Y8", "the J20 buffer is zero (walked means only the exact cell)", M + "terrain3d/TravelMask.kt",
     "val bufferTexels: Int = (UNWALKED_BUFFER_M / (widthM / size)).roundToInt()",
     "val bufferTexels: Int = 0 * (UNWALKED_BUFFER_M / (widthM / size)).roundToInt()",
     [T + "terrain3d.TravelMaskTest"]),
    ("Y9", "the way back leads to the last point, not the start", M + "field/FieldPower.kt",
     ".minByOrNull { it.time }", ".maxByOrNull { it.time }",
     [T + "field.FieldPowerTest"]),
    ("Y10", "an animation re-added to the UI (J23)", M + "ui/map/Ring.kt",
     "        Math.toDegrees(lat2) to Math.toDegrees(lng2)\n    }\n}\n",
     "        Math.toDegrees(lat2) to Math.toDegrees(lng2)\n    }\n}\n\nprivate fun fade() = androidx.compose.animation.core.Animatable(0f)\n",
     [T + "ui.NoAnimationTest"]),
    ("Y11", "the battery mode ignores the charger", M + "field/FieldPower.kt",
     "systemSaver || (!charging && batteryPct != null && batteryPct <= thresholdPct)",
     "systemSaver || (batteryPct != null && batteryPct <= thresholdPct)",
     [T + "field.FieldPowerTest"]),
    ("Y12", "the backfill keeps fixes the recorder would refuse", M + "data/db/AppDatabase.kt",
     "\"FROM `track_points` WHERE `accuracyM` <= 30 GROUP BY 1\"", "\"FROM `track_points` WHERE 1 GROUP BY 1\"",
     [T + "data.TravelMigrationTest"]),
    ("Y13", "the J20 buffer is a square, not a disk", M + "terrain3d/TravelMask.kt",
     "if (xx < 0 || xx >= size || dx * dx + dy * dy > r2) continue", "if (xx < 0 || xx >= size) continue",
     [T + "terrain3d.TravelMaskTest"]),
    ("Y14", "a vague fix becomes the start of the next line", M + "field/TravelMemory.kt",
     "if (fix.accuracyM <= TravelMemory.MAX_ACCURACY_M) prev = fix", "prev = fix",
     [T + "field.TravelMemoryTest"]),
    ("Y15", "the recorder writes on every fix", M + "field/TravelMemory.kt",
     "flushNow = pending.size >= FLUSH_CELLS || clock() - lastFlush >= FLUSH_MS", "flushNow = true",
     [T + "field.TravelMemoryTest"]),
    ("Y16", "a 60 m canopy fix paints ground never stood on", M + "field/TravelMemory.kt",
     "if (fix.accuracyM > MAX_ACCURACY_M) return LongArray(0)", "",
     [T + "field.TravelMemoryTest"]),
    ("Y17", "a late batch moves a visit back in time", M + "data/db/Daos.kt",
     "WHERE cell IN (:cells) AND lastAt < :time", "WHERE cell IN (:cells)",
     [T + "data.TravelMigrationTest"]),
    ("Y18", "the still-viewing plan drops to cell-tower accuracy", M + "field/PowerPolicy.kt",
     "mode = Mode.VIEWING_STILL,\n                    accuracy = Accuracy.HIGH,",
     "mode = Mode.VIEWING_STILL,\n                    accuracy = Accuracy.BALANCED,",
     [T + "field.PowerPolicyTest"]),
    ("Y19", "the travel memory re-bakes the colour texture", M + "ui/map/SceneLayers.kt",
     "SceneLayer.VISITED, SceneLayer.UNWALKED -> MeshDraw.MEMORY", "SceneLayer.VISITED, SceneLayer.UNWALKED -> MeshDraw.BAKED",
     [T + "ui.map.SceneLayersTest"]),
    ("Y20", "the schema-6 table differs from Room's", M + "data/db/AppDatabase.kt",
     "`lastAt` INTEGER NOT NULL, PRIMARY KEY(`cell`))", "`lastAt` INTEGER, PRIMARY KEY(`cell`))",
     [T + "data.TravelSchemaTest"]),
    ("Y22", "where you've been drawn in the creeks' blue again", M + "terrain3d/TerrainShaders.kt",
     "base = mix(base, vec3(0.902, 0.957, 0.925), mem.r", "base = mix(base, vec3(0.435, 0.718, 1.0), mem.r",
     [T + "terrain3d.ShaderSourceTest"]),
    ("Y23", "every water line drawn on the 48 km square, however thin", M + "terrain3d/TerrainTextures.kt",
     "if (style.widthM / metresPerTexel < MIN_WATER_TEXELS) continue", "",
     [T + "terrain3d.WaterLevelOfDetailTest"]),
    ("Y21", "a cell-tower first fix lands the one map for good", M + "ui/map/CameraStart.kt",
     "fixAgeMs <= FRESH_FIX_MS && accuracyM <= FINAL_ACCURACY_M -> Landing.FINAL",
     "fixAgeMs <= FRESH_FIX_MS -> Landing.FINAL",
     [T + "ui.map.CameraStartTest"]),
]


def run_tests(classes):
    args = ["./gradlew", "--no-daemon", "--max-workers=2", "-q", ":app:testDebugUnitTest"]
    for c in classes:
        args += ["--tests", c]
    t0 = time.time()
    p = subprocess.run(args, capture_output=True, text=True)
    out = p.stdout + p.stderr
    compile_error = "Compilation error" in out or "e: file://" in out
    return p.returncode, compile_error, time.time() - t0


def stale():
    """Mutations that can no longer run: the file is gone, the old text is not found exactly once,
    or a named test class does not exist. Wave M.1 found P6, H2 and H4 stale since earlier waves:
    each run reported them INVALID in its table and nothing failed, so three properties went
    unguarded. `--check` fails on any of these before a run starts."""
    import glob
    import os
    import re
    classes = set()
    for f in glob.glob("app/src/test/java/**/*.kt", recursive=True):
        src = open(f, encoding="utf-8").read()
        pkg = re.search(r"^package (\S+)", src, re.M).group(1)
        classes.update(pkg + "." + c for c in re.findall(r"^class (\w+)", src, re.M))
    out = []
    for mid, _, path, old, _, tests in MUTATIONS:
        if not os.path.exists(path):
            out.append(f"{mid}: {path} does not exist")
            continue
        n = open(path, encoding="utf-8").read().count(old)
        if n != 1:
            out.append(f"{mid}: old text found {n}x in {path}")
        out += [f"{mid}: no test class {t}" for t in tests if t not in classes]
    return out


def main():
    dup = [k for k, v in collections.Counter(m[0] for m in MUTATIONS).items() if v > 1]
    if dup:
        sys.exit(f"duplicate mutation ids {sorted(dup)}: rename before running (exe.md I20)")
    if sys.argv[1:] == ["--check"]:
        bad = stale()
        print("\n".join(bad) or f"all {len(MUTATIONS)} mutations apply")
        sys.exit(1 if bad else 0)
    # SIGTERM unwinds like Ctrl-C, so the finally below restores the file a mutant is in.
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    want = set(sys.argv[1:])
    rows = []
    for mid, what, path, old, new, tests in MUTATIONS:
        if want and mid not in want:
            continue
        src = open(path, encoding="utf-8").read()
        count = src.count(old)
        if count != 1:
            rows.append((mid, what, f"INVALID (old text found {count}x)", 0))
            print(rows[-1], flush=True)
            continue
        try:
            open(path, "w", encoding="utf-8").write(src.replace(old, new, 1))
            code, cerr, secs = run_tests(tests)
        finally:
            open(path, "w", encoding="utf-8").write(src)
        assert open(path, encoding="utf-8").read() == src, f"restore failed for {path}"
        verdict = "COMPILE-ERROR" if cerr else ("KILLED" if code != 0 else "SURVIVED")
        rows.append((mid, what, verdict, round(secs)))
        print(rows[-1], flush=True)
    print("\n| ID | Mutation | Result |\n|---|---|---|")
    for mid, what, verdict, _ in rows:
        print(f"| {mid} | {what} | {verdict} |")
    killed = sum(1 for r in rows if r[2] == "KILLED")
    print(f"\nkilled {killed} / {len(rows)}")


if __name__ == "__main__":
    main()
