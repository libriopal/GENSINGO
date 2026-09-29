package com.ginsengo.steward.research

import com.ginsengo.steward.Fixtures
import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.TerrainMath
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import kotlin.math.cos

/**
 * Times the full 10-mile scan on the real 6x6-tile z12 Haywood mosaic (the app's worst case).
 *
 * Not a pass/fail performance gate: desktop JVM time is not phone time. It exists so the
 * report quotes a measured number. The 4.7 MB fixture is not committed; build it with
 * tools/build_scan_fixture.py and pass its path in GENSINGO_SCAN_FIXTURE. Skipped otherwise.
 */
class RadiusScanTimingTest {

    @Test
    fun timeTheFullRadiusScan() {
        val path = System.getenv("GENSINGO_SCAN_FIXTURE")
        assumeTrue("set GENSINGO_SCAN_FIXTURE to time the full scan", path != null && File(path).exists())
        val mosaic = DataInputStream(File(path!!).inputStream().buffered()).use { d ->
            ByteArray(8).also { d.readFully(it) }
            val z = d.readInt(); val x0 = d.readInt(); val y0 = d.readInt(); val nx = d.readInt(); val ny = d.readInt()
            val w = nx * 256; val h = ny * 256
            val e = FloatArray(w * h) { d.readShort().toFloat() }
            val lat = (DemTileStore.tileYToLat(y0, z) + DemTileStore.tileYToLat(y0 + ny, z)) / 2
            val mpp = 40_075_016.686 * cos(Math.toRadians(lat)) / (256.0 * (1 shl z))
            DemTileStore.Mosaic(TerrainMath.Grid(w, h, e, mpp), z, x0, y0, nx, ny, 0, nx * ny, nx * ny)
        }
        val rt = Runtime.getRuntime()
        rt.gc()
        val before = rt.totalMemory() - rt.freeMemory()
        val t0 = System.nanoTime()
        val scan = RadiusScan.of(mosaic, 35.55, -82.95)
        val t1 = System.nanoTime()
        val cands = scan.candidates(GinsengSuitability.PRIOR_WEIGHTS)
        val t2 = System.nanoTime()
        val bg = scan.background()
        val t3 = System.nanoTime()
        val used = (rt.totalMemory() - rt.freeMemory() - before) / 1_048_576
        println("MEASURED scan grid=${mosaic.grid.w}x${mosaic.grid.h} cell=%.1fm build(TWI+SAT)=%d ms candidates=%d ms background=%d ms heapDelta~%d MB picks=%d"
            .format(mosaic.grid.cellSizeM, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, used, cands.size))
        cands.forEachIndexed { i, c ->
            println("MEASURED C${i + 1} %.4f,%.4f d=%.0fm score=%.3f elev=%.0f slope=%.1f aspect=%.0f"
                .format(c.lat, c.lng, c.distanceM, c.score, c.elevationM, c.slopeDeg, c.aspectDeg))
        }
        println("MEASURED background=${bg.size}")
    }
}
