package com.ginsengo.steward.terrain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Fetches and decodes Terrarium-encoded elevation tiles, and assembles them into an
 * elevation grid the terrain indices can run over.
 *
 * SOURCE: AWS Open Data "Terrain Tiles" (s3://elevation-tiles-prod), Terrarium encoding,
 * public domain, no API key. In the contiguous US these are built from USGS 3DEP/NED.
 *
 * ENCODING (Terrarium, not Mapbox RGB — these differ and mixing them up yields silently
 * wrong elevations):  height_metres = (R * 256 + G + B / 256) - 32768
 *
 * MEASURED RESOLUTION. Zoom 15 is the deepest zoom the bucket serves (z16 returns 404).
 * At 36 degrees N that is 3.86 m per pixel, which is the practical floor for this app.
 * Checked rather than assumed: a z15 tile over Boone NC carries non-zero second
 * differences at 98% of samples along a scanline, which is not what bilinear upsampling of
 * coarser data looks like — upsampling leaves long runs of near-zero second difference.
 * So the 3.86 m grid carries real per-pixel variation there. Coverage is not uniform: away
 * from lidar-mapped areas the underlying source is coarser and z15 is genuinely smoother.
 *
 * NETWORK POSTURE. This is a VISUALISATION source and it streams like the basemap does.
 * It is deliberately NOT the elevation input to the habitat model — that stays on the
 * bundled offline grid (assets/geo/dem_grid.bin) so the model keeps working with no signal.
 * Tiles are cached to app-private storage, so ground already walked stays available offline.
 */
class DemTileStore(context: Context) {

    private val cacheDir = File(context.cacheDir, "dem_tiles").apply { mkdirs() }

    /** Decoded elevation tiles, keyed z/x/y. ~256 KB each as floats; cap at ~24 tiles. */
    private val memory = object : LruCache<String, FloatArray>(24) {
        override fun sizeOf(key: String, value: FloatArray) = 1
    }

    suspend fun tile(z: Int, x: Int, y: Int): FloatArray? = withContext(Dispatchers.IO) {
        val key = "$z/$x/$y"
        memory.get(key)?.let { return@withContext it }

        val f = File(cacheDir, "${z}_${x}_${y}.png")
        val bytes = if (f.exists() && f.length() > 0) {
            runCatching { f.readBytes() }.getOrNull()
        } else {
            download(z, x, y)?.also { runCatching { f.writeBytes(it) } }
        } ?: return@withContext null

        val bmp = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return@withContext null

        val out = decodeTerrarium(bmp)
        bmp.recycle()
        memory.put(key, out)
        out
    }

    private fun download(z: Int, x: Int, y: Int): ByteArray? = runCatching {
        val url = URL("$TERRARIUM/$z/$x/$y.png")
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "GENSINGO/1.0")
            if (responseCode != 200) { disconnect(); return null }
            inputStream.use { it.readBytes() }.also { disconnect() }
        }
    }.getOrElse {
        Log.w(TAG, "DEM tile $z/$x/$y unavailable", it)
        null
    }

    private fun decodeTerrarium(bmp: Bitmap): FloatArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return FloatArray(w * h) { i ->
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (r * 256f + g + b / 256f) - 32768f
        }
    }

    /**
     * Assembles a [TerrainMath.Grid] covering [bounds] at DEM zoom [z], padded by
     * [haloTiles] of extra tiles on every side.
     *
     * The halo is not decoration. TPI over a 40-cell radius and multiple-flow accumulation
     * both read well beyond the pixel they are computing, so a mosaic cut exactly to the
     * viewport produces a visible seam of wrong values all round the edge — the classic
     * neighbourhood-operator border artefact. The halo is cropped off before display.
     */
    suspend fun grid(
        north: Double, west: Double, south: Double, east: Double,
        z: Int, haloTiles: Int = 1,
    ): Mosaic? = coroutineScope {
        val x0 = lonToTileX(west, z) - haloTiles
        val x1 = lonToTileX(east, z) + haloTiles
        val y0 = latToTileY(north, z) - haloTiles
        val y1 = latToTileY(south, z) + haloTiles
        val nx = x1 - x0 + 1
        val ny = y1 - y0 + 1
        if (nx <= 0 || ny <= 0 || nx.toLong() * ny > MAX_TILES) return@coroutineScope null

        val max = 1 shl z
        val jobs = ArrayList<Pair<Pair<Int, Int>, kotlinx.coroutines.Deferred<FloatArray?>>>()
        for (ty in y0..y1) for (tx in x0..x1) {
            val wx = ((tx % max) + max) % max
            if (ty < 0 || ty >= max) continue
            jobs += (tx to ty) to async { tile(z, wx, ty) }
        }

        val w = nx * TILE
        val h = ny * TILE
        val z0 = FloatArray(w * h)
        var got = 0
        for ((pos, job) in jobs) {
            val data = job.await() ?: continue
            got++
            val (tx, ty) = pos
            val ox = (tx - x0) * TILE
            val oy = (ty - y0) * TILE
            for (row in 0 until TILE) {
                System.arraycopy(data, row * TILE, z0, (oy + row) * w + ox, TILE)
            }
        }
        if (got == 0) return@coroutineScope null

        // Ground resolution at the mosaic's centre latitude.
        val centreLat = (north + south) / 2.0
        val metresPerPixel = EQUATOR_M * cos(Math.toRadians(centreLat)) / (TILE * (1 shl z))

        Mosaic(
            grid = TerrainMath.Grid(w, h, z0, metresPerPixel),
            zoom = z,
            tileX0 = x0, tileY0 = y0,
            tilesX = nx, tilesY = ny,
            haloPx = haloTiles * TILE,
            tilesLoaded = got, tilesRequested = jobs.size,
        )
    }

    data class Mosaic(
        val grid: TerrainMath.Grid,
        val zoom: Int,
        val tileX0: Int, val tileY0: Int,
        val tilesX: Int, val tilesY: Int,
        val haloPx: Int,
        val tilesLoaded: Int, val tilesRequested: Int,
    ) {
        val northLat: Double get() = tileYToLat(tileY0, zoom)
        val southLat: Double get() = tileYToLat(tileY0 + tilesY, zoom)
        val westLon: Double get() = tileXToLon(tileX0, zoom)
        val eastLon: Double get() = tileXToLon(tileX0 + tilesX, zoom)

        /** Latitude of a pixel row, used to keep the heat-load index latitude-correct. */
        fun latAtRow(row: Int): Double {
            val f = row.toDouble() / grid.h
            return northLat + (southLat - northLat) * f
        }
    }

    companion object {
        private const val TAG = "DemTileStore"
        const val TILE = 256
        const val MAX_DEM_ZOOM = 15
        private const val MAX_TILES = 64
        private const val EQUATOR_M = 40_075_016.686
        const val TERRARIUM = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium"

        fun lonToTileX(lon: Double, z: Int): Int =
            floor((lon + 180.0) / 360.0 * (1 shl z)).toInt()

        fun latToTileY(lat: Double, z: Int): Int {
            val l = lat.coerceIn(-85.05112878, 85.05112878)
            val r = Math.toRadians(l)
            return floor((1.0 - asinh(tan(r)) / PI) / 2.0 * (1 shl z)).toInt()
        }

        fun tileXToLon(x: Int, z: Int): Double = x.toDouble() / (1 shl z) * 360.0 - 180.0

        fun tileYToLat(y: Int, z: Int): Double {
            val n = PI - 2.0 * PI * y / (1 shl z)
            return Math.toDegrees(kotlin.math.atan(sinh(n)))
        }

        /**
         * ADAPTIVE SCOPE (the "adaptive scopes depending on the area coverage from zoomed
         * in depth" requirement).
         *
         * Two things change together as the camera moves, and they pull in opposite
         * directions:
         *
         *  - DEM zoom rises with camera zoom so a digger examining one hillside gets the
         *    3.86 m detail, capped at 15 because the bucket serves nothing deeper.
         *  - The TPI neighbourhood radius is expressed in METRES and converted to cells, so
         *    "position on the slope" keeps meaning the same physical thing at every zoom.
         *    Fixing the radius in cells instead would silently redefine the index every
         *    time the user pinched.
         *
         * The metre radius itself shrinks as you zoom in: at landscape zoom the question is
         * "where does this ridge sit in the range", at field zoom it is "where does this
         * bench sit on this hillside".
         */
        fun demZoomFor(cameraZoom: Double): Int =
            floor(cameraZoom).toInt().coerceIn(8, MAX_DEM_ZOOM)

        fun tpiRadiusMetresFor(cameraZoom: Double): Double = when {
            cameraZoom >= 15.0 -> 120.0
            cameraZoom >= 13.0 -> 300.0
            cameraZoom >= 11.0 -> 700.0
            else -> 1500.0
        }

        /** Output raster size: enough to look sharp, small enough to compute on the fly. */
        fun rasterSizeFor(cameraZoom: Double): Int = if (cameraZoom >= 13.0) 768 else 512
    }
}
