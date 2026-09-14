package com.ginsengo.steward.geo

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Reader for the bundled coarse elevation grid (PRD §8.5).
 *
 * The grid is a committed build-time asset. The app performs NO network elevation lookup,
 * ever - the authoring script lives at tools/build_dem.py and is run once on a developer
 * machine.
 *
 * Layout (big-endian, written by tools/build_dem.py):
 *   magic   8 bytes ASCII "GSDEM001"
 *   latMin  float64   lonMin float64   latStep float64   lonStep float64
 *   rows    int32     cols   int32
 *   noData  int16
 *   data    rows*cols int16, row-major, row 0 = latMin, col 0 = lonMin
 */
class DemGrid(
    private val latMin: Double,
    private val lonMin: Double,
    private val latStep: Double,
    private val lonStep: Double,
    private val rows: Int,
    private val cols: Int,
    private val noData: Short,
    private val data: ShortArray,
) {
    companion object {
        private const val MAGIC = "GSDEM001"

        fun read(input: InputStream): DemGrid? {
            DataInputStream(input.buffered()).use { d ->
                val magic = ByteArray(8)
                d.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != MAGIC) return null
                val latMin = d.readDouble()
                val lonMin = d.readDouble()
                val latStep = d.readDouble()
                val lonStep = d.readDouble()
                val rows = d.readInt()
                val cols = d.readInt()
                val noData = d.readShort()
                if (rows <= 0 || cols <= 0 || rows.toLong() * cols > 40_000_000L) return null
                val data = ShortArray(rows * cols)
                for (i in data.indices) data[i] = d.readShort()
                return DemGrid(latMin, lonMin, latStep, lonStep, rows, cols, noData, data)
            }
        }
    }

    /** Metres per degree of latitude; good enough at this grid's resolution. */
    private val metresPerDegLat = 111_320.0

    private fun at(r: Int, c: Int): Short? {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return null
        val v = data[r * cols + c]
        return if (v == noData) null else v
    }

    private fun rowOf(lat: Double) = ((lat - latMin) / latStep).roundToInt()
    private fun colOf(lng: Double) = ((lng - lonMin) / lonStep).roundToInt()

    /** Nearest-cell elevation in metres, or null outside coverage. */
    fun elevation(lat: Double, lng: Double): Double? =
        at(rowOf(lat), colOf(lng))?.toDouble()

    /**
     * Slope (degrees) and aspect (degrees clockwise from north) by central finite
     * differences over the eight neighbours, as PRD §8.1 specifies.
     *
     * IMPORTANT, and stated here because it is easy to assume otherwise: the shipped
     * habitat model weights slopeAngle and aspect at exactly 0.0, so nothing this function
     * returns can move the model's score. It exists to drive the *field checklist* and the
     * slope guidance in the UI, which are what actually use slope and aspect. See
     * HabitatModel.kt and EINCOL_REPORT.md.
     */
    fun slopeAspect(lat: Double, lng: Double): SlopeAspect? {
        val r = rowOf(lat); val c = colOf(lng)
        val z = at(r, c)?.toDouble() ?: return null
        val zN = at(r + 1, c)?.toDouble() ?: z
        val zS = at(r - 1, c)?.toDouble() ?: z
        val zE = at(r, c + 1)?.toDouble() ?: z
        val zW = at(r, c - 1)?.toDouble() ?: z

        val dyM = latStep * metresPerDegLat
        val dxM = lonStep * metresPerDegLat * cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
        if (dyM <= 0 || dxM <= 0) return null

        // dz/dx positive eastward, dz/dy positive northward
        val dzdx = (zE - zW) / (2 * dxM)
        val dzdy = (zN - zS) / (2 * dyM)

        val slopeDeg = Math.toDegrees(atan(kotlin.math.sqrt(dzdx * dzdx + dzdy * dzdy)))
        // Aspect = direction the slope FACES = downhill direction, clockwise from north.
        var aspect = Math.toDegrees(atan2(-dzdx, -dzdy))
        if (aspect < 0) aspect += 360.0
        return SlopeAspect(elevationM = z, slopeDegrees = slopeDeg, aspectDegrees = aspect)
    }

    fun coverage(): String =
        "%.1f..%.1f lat, %.1f..%.1f lng @ %.2f deg".format(
            latMin, latMin + rows * latStep, lonMin, lonMin + cols * lonStep, latStep
        )
}

data class SlopeAspect(
    val elevationM: Double,
    val slopeDegrees: Double,
    val aspectDegrees: Double,
)
