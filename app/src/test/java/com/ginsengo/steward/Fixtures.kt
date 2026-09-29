package com.ginsengo.steward

import com.ginsengo.steward.terrain.DemTileStore
import com.ginsengo.steward.terrain.TerrainMath
import java.io.DataInputStream
import kotlin.math.cos

/** Loads a multi-tile elevation mosaic written by tools/build_scan_fixture.py. */
object Fixtures {
    fun mosaic(name: String): DemTileStore.Mosaic {
        val stream = requireNotNull(Fixtures::class.java.getResourceAsStream("/$name")) { "missing fixture $name" }
        DataInputStream(stream.buffered()).use { d ->
            val magic = ByteArray(8).also { d.readFully(it) }
            require(String(magic, Charsets.US_ASCII) == "GSDEMMOS")
            val z = d.readInt(); val x0 = d.readInt(); val y0 = d.readInt()
            val nx = d.readInt(); val ny = d.readInt()
            val w = nx * DemTileStore.TILE; val h = ny * DemTileStore.TILE
            val elev = FloatArray(w * h) { d.readShort().toFloat() }
            val centreLat = (DemTileStore.tileYToLat(y0, z) + DemTileStore.tileYToLat(y0 + ny, z)) / 2
            val mpp = 40_075_016.686 * cos(Math.toRadians(centreLat)) / (DemTileStore.TILE * (1 shl z))
            return DemTileStore.Mosaic(TerrainMath.Grid(w, h, elev, mpp), z, x0, y0, nx, ny, 0, nx * ny, nx * ny)
        }
    }
}
