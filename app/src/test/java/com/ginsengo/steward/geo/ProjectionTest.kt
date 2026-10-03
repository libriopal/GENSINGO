package com.ginsengo.steward.geo

import com.ginsengo.steward.research.RadiusScan
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import com.ginsengo.steward.terrain.WaterLines
import com.ginsengo.steward.terrain3d.MapCamera
import com.ginsengo.steward.terrain3d.Terrain3D
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * exe.md A2: Web Mercator exists once. Two witnesses:
 *  1. Analytic values of the projection, which do not come from any code in the app.
 *  2. Consumer agreement: every caller that used to carry its own copy now returns exactly what
 *     [Projection] returns, for random inputs. A caller that drifted (a copy reintroduced, an
 *     offset dropped) fails here.
 * Negative controls: mutants Q3 (north/south reversal in the module) and Q4 (`latAtRow` linear
 * again).
 */
class ProjectionTest {

    private val rnd = Random(20261003)

    @Test
    fun analyticValues() {
        assertEquals(0.5, Projection.y(0.0), 1e-15)
        assertEquals(0.0, Projection.y(Projection.MAX_LATITUDE), 1e-12)
        assertEquals(1.0, Projection.y(-Projection.MAX_LATITUDE), 1e-12)
        assertEquals("north is up: y shrinks going north", true, Projection.y(36.0) < Projection.y(35.0))
        assertEquals(0.0, Projection.x(-180.0), 0.0)
        assertEquals(0.5, Projection.x(0.0), 0.0)
        // Tile 0 of zoom 1 spans the northern hemisphere; its south edge is the equator.
        assertEquals(0.0, Projection.lat(0.5), 1e-12)
        // y(45°) = (1 − asinh(1)/π)/2 = (1 − ln(1+√2)/π)/2, written independently of the code.
        assertEquals((1 - kotlin.math.ln(1 + kotlin.math.sqrt(2.0)) / Math.PI) / 2, Projection.y(45.0), 1e-15)
        // One world pixel at zoom 0 (512-px tiles) at the equator is C / 512 metres.
        assertEquals(Projection.EARTH_CIRCUMFERENCE / 512, Projection.metresPerPixel(0.0, Projection.worldPx(0.0, 512.0)), 1e-6)
    }

    @Test
    fun roundTripsAreExact() {
        for (i in -80..80 step 5) {
            val lat = i + 0.123
            assertEquals(lat, Projection.lat(Projection.y(lat)), 1e-11)
        }
        for (i in -179..179 step 7) assertEquals(i + 0.5, Projection.lng(Projection.x(i + 0.5)), 1e-11)
    }

    @Test
    fun everyCallerAgreesWithTheModule() {
        val z = 14; val tx0 = 4475; val ty0 = 6422
        val n = 3 * DemTileStore.TILE
        val m = DemTileStore.Mosaic(TerrainMath.Grid(n, n, FloatArray(n * n), 9.5), z, tx0, ty0, 3, 3, DemTileStore.TILE, 9, 9)
        val world = Projection.worldPx(z, DemTileStore.TILE)
        repeat(200) {
            val lat = 25 + rnd.nextDouble() * 24; val lng = -125 + rnd.nextDouble() * 60
            // MapCamera (the 3D camera's own names for the maths).
            assertEquals(Projection.y(lat), MapCamera.mercatorY(lat), 1e-15)
            assertEquals(lat, MapCamera.latFromMercatorY(Projection.y(lat)), 1e-11)
            // DemTileStore's tile arithmetic.
            assertEquals(kotlin.math.floor(Projection.y(lat) * (1 shl z)).toInt(), DemTileStore.latToTileY(lat, z))
            assertEquals(kotlin.math.floor(Projection.x(lng) * (1 shl z)).toInt(), DemTileStore.lonToTileX(lng, z))
            val ty = DemTileStore.latToTileY(lat, z)
            assertEquals(Projection.lat(ty.toDouble() / (1 shl z)), DemTileStore.tileYToLat(ty, z), 1e-12)
            // Cell centres (WaterLines, ContourLines) and edges (Terrain3D).
            val row = rnd.nextDouble() * n; val col = rnd.nextDouble() * n
            assertEquals(Projection.lat((ty0 * 256 + row + 0.5) / world), WaterLines.latOfCell(m, row), 1e-12)
            assertEquals(Projection.lng((tx0 * 256 + col + 0.5) / world), WaterLines.lngOfCell(m, col), 1e-12)
            assertEquals(Projection.lat((ty0 * 256 + row) / world), Terrain3D.latOfEdge(m, row), 1e-12)
        }
    }

    /** The defect A2 found: rows are linear in Mercator y, so a linear latitude is wrong. */
    @Test
    fun latAtRowIsTheExactCellCentreLatitude() {
        val n = 5 * DemTileStore.TILE
        val m = DemTileStore.Mosaic(TerrainMath.Grid(n, n, FloatArray(n * n), 9.5), 14, 4475, 6422, 5, 5, DemTileStore.TILE, 25, 25)
        for (row in listOf(0, 1, 300, 640, 1279)) {
            assertEquals("row $row", WaterLines.latOfCell(m, row.toDouble()), m.latAtRow(row), 1e-10)
        }
    }

    /** The radius scan used its own private copy (forward formula ln(tan + sec)). */
    @Test
    fun theRadiusScanAgrees() {
        val n = 2 * DemTileStore.TILE
        val g = TerrainMath.Grid(n, n, FloatArray(n * n) { i -> (600 + (i % n) * 0.3).toFloat() }, 38.0)
        val m = DemTileStore.Mosaic(g, 12, 1117, 1605, 2, 2, 0, 4, 4)
        val scan = RadiusScan.of(m, (m.northLat + m.southLat) / 2, (m.westLon + m.eastLon) / 2, 5_000.0)
        val world = Projection.worldPx(12, DemTileStore.TILE)
        for (y in listOf(0.0, 17.25, 300.5, 777.0)) {
            assertEquals(Projection.lat((1605 * 256 + y + 0.5) / world), scan.latOfRow(y), 1e-12)
        }
        for (x in listOf(0.0, 5.5, 1000.0)) {
            assertEquals(Projection.lng((1117 * 256 + x + 0.5) / world), scan.lngOfCol(x), 1e-12)
        }
    }
}
