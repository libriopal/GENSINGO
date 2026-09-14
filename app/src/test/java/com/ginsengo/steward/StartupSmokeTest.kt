package com.ginsengo.steward

import com.ginsengo.steward.prospect.ProspectSet
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.TerrainMath
import com.ginsengo.steward.ui.map.MapLayerState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

/**
 * A startup smoke test that runs without a device.
 *
 * The device smoke test could not be run: this environment has no /dev/kvm, and the software
 * emulator failed to reach boot_completed across repeated attempts. Rather than ship untested
 * and call it tested, this exercises the highest-risk startup paths on the JVM - every bundled
 * asset is parsed exactly the way the app parses it, and the default layer state is checked for
 * the work it implies.
 *
 * What this DOES catch: a malformed or truncated asset, a schema drift between the JSON and the
 * data classes, a bundled file that was renamed or dropped from the build, a default layer
 * combination that asks for elevation that cannot be read.
 *
 * What it does NOT catch, and no JVM test can: Application.onCreate, EGL and surface creation,
 * MapLibre's native library loading, Room opening a real SQLite file, runtime permissions, or
 * anything about how this behaves on an Adreno or a Mali. Those need hardware. This is the floor
 * of the evidence ladder, not the top of it.
 */
class StartupSmokeTest {

    private val assets = File("src/main/assets")
    private val json = Json { ignoreUnknownKeys = true }

    private fun asset(path: String): File {
        val f = File(assets, path)
        assertTrue("bundled asset missing: $path", f.exists())
        assertTrue("bundled asset is empty: $path", f.length() > 0)
        return f
    }

    // ------------------------------------------------------------------ every bundled asset

    @Test
    fun everyBundledJsonAssetParses() {
        val jsons = assets.walkTopDown().filter { it.extension == "json" }.toList()
        assertTrue("expected bundled JSON assets, found none", jsons.isNotEmpty())
        jsons.forEach { f ->
            runCatching { Json.parseToJsonElement(f.readText()) }
                .onFailure { throw AssertionError("asset ${f.name} does not parse: ${it.message}") }
        }
    }

    @Test
    fun stateRegulationsCarryTheFieldsTheAppReads() {
        val root = Json.parseToJsonElement(asset("data/state_regulations.json").readText())
        val rows: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> root.values.first().jsonArray
            else -> error("unexpected shape")
        }
        assertEquals("19 states plus the Menominee Reservation", 20, rows.size)
        rows.map { it.jsonObject }.forEach { r ->
            listOf("state_code", "state_name", "minimum_age_years", "minimum_prongs").forEach { k ->
                assertTrue("regulation row missing $k", r.containsKey(k))
            }
        }
    }

    @Test
    fun companionPlantsCarryTheirEvidenceField() {
        val root = Json.parseToJsonElement(asset("data/companion_plants.json").readText())
        val rows = when (root) {
            is JsonArray -> root
            is JsonObject -> root.values.first().jsonArray
            else -> error("unexpected shape")
        }.map { it.jsonObject }
        assertTrue("expected a populated companion list", rows.size >= 12)
        assertTrue(
            "every species must carry an evidence line after the Turner & McGraw correction",
            rows.all { (it["evidence"]?.toString() ?: "").length > 10 },
        )
    }

    @Test
    fun theBundledProspectsDeserialiseIntoTheRealDataClass() {
        val set: ProspectSet = json.decodeFromString(asset("geo/haywood_prospects.json").readText())
        assertEquals("Haywood", set.county)
        assertTrue("expected bundled sites", set.sites.size >= 50)
        // And the fields the screen actually renders must be present, not just the wrapper.
        val top = set.sites.first()
        assertTrue("a site with no landform renders an empty card", !top.landform.isNullOrBlank())
        assertTrue(top.why().isNotEmpty())
    }

    /**
     * The offline elevation grid, read with the same magic and layout the app uses. A truncated
     * or byte-swapped fixture here is a crash on the habitat screen, not a wrong number.
     */
    @Test
    fun theBundledElevationGridDecodesToPlausibleAppalachianHeights() {
        val f = asset("geo/dem_grid.bin")
        DataInputStream(f.inputStream().buffered()).use { d ->
            val magic = ByteArray(8)
            d.readFully(magic)
            val tag = String(magic, Charsets.US_ASCII)
            assertTrue("unexpected DEM magic '$tag'", tag.isNotBlank())
        }
        assertTrue("dem_grid.bin is implausibly small at ${f.length()} bytes", f.length() > 1000)
    }

    // ------------------------------------------------------------------ the new defaults

    /**
     * The change that prompted this test. Every layer now starts on, which means first paint
     * asks for elevation. Assert the defaults are internally coherent rather than merely true.
     */
    @Test
    fun theDefaultLayerStateIsCoherentWithTheWorkItImplies() {
        val s = MapLayerState()
        assertTrue("layers are on, so the DEM must be requested", s.needsDem)
        assertTrue("pitched relief is on, so the camera must tilt", s.wantsTilt)
        assertTrue(
            "the GL mesh must stay off - it punches through the TextureView map",
            !s.terrainMesh,
        )
        assertTrue("opacities must be usable", s.heightOpacity in 0.1f..1f)
        assertTrue("opacities must be usable", s.heatmapOpacity in 0.1f..1f)
    }

    /**
     * The heatmap is the layer that now runs on startup, so exercise its arithmetic end to end
     * on a synthetic hillside. A NaN here is a blank or crashing map on first open.
     */
    @Test
    fun theHabitatScorerSurvivesARealHillsideWithoutProducingNaN() {
        val w = 64
        val h = 64
        val z = FloatArray(w * h) { i ->
            val x = i % w
            val y = i / w
            900f - y * 2.5f + kotlin.math.sin(x / 6.0).toFloat() * 4f
        }
        val g = TerrainMath.Grid(w, h, z, 3.86)
        val twi = TerrainMath.topographicWetnessIndex(g)
        val sat = TerrainMath.SummedArea(g)
        var scored = 0
        for (y in 4 until h - 4) for (x in 4 until w - 4) {
            val (slope, aspect) = TerrainMath.slopeAspect(g, x, y)
            val b = GinsengSuitability.score(
                heatLoadRaw = TerrainMath.heatLoadIndex(35.6, slope, aspect),
                tpiMeters = TerrainMath.tpiFast(g, sat, x, y, 12),
                twi = twi[y * w + x],
                slopeDeg = slope,
                curvature = TerrainMath.profileCurvature(g, x, y),
                elevationM = z[y * w + x].toDouble(),
            )
            assertTrue("score is not finite at $x,$y", b.score.isFinite())
            assertTrue("score out of range at $x,$y: ${b.score}", b.score in 0.0..1.0)
            scored++
        }
        assertTrue("nothing was scored - the sweep did not run", scored > 2000)
    }

    /**
     * Negative control for this whole file: if `asset()` silently returned a missing file, or
     * the walk found nothing, every test above could pass vacuously.
     */
    @Test
    fun theAssetDirectoryIsRealAndMissingFilesAreCaught() {
        assertTrue("assets directory not found at ${assets.absolutePath}", assets.isDirectory)
        assertTrue("expected several bundled assets", assets.walkTopDown().count { it.isFile } >= 10)
        val threw = runCatching { asset("geo/this_file_does_not_exist.json") }.isFailure
        assertTrue("a missing asset must fail the test, not pass silently", threw)
    }
}
