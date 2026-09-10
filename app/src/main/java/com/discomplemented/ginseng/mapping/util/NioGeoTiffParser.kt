package com.discomplemented.ginseng.mapping.util

import com.discomplemented.ginseng.domain.model.LatLng
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * A high-performance, NIO-based implementation of [GeoTiffParser].
 *
 * This parser assumes a standard, uncompressed GeoTIFF format where
 * elevation data is stored as 32-bit floating point values.
 * It uses memory-mapping for extremely fast random access to large files.
 */
class NioGeoTiffParser(private val file: File) : GeoTiffParser {

    override var width: Int = 0
        private set
    override var height: Int = 0
        private set
    override var modelTiepoint: DoubleArray = doubleArrayOf()
        private set
    override var modelPixelScale: DoubleArray = doubleArrayOf()
        private set

    private lateinit var buffer: MappedByteBuffer
    private var isLittleEndian = true

    private var stripOffsets: LongArray = longArrayOf()
    private var stripByteCounts: IntArray = intArrayOf()
    private var rasterDataOffset: Long = 0
    private var sampleFormat: Int = 3 // Default to IEEE Float
    private var bitsPerSample: Int = 32

    // TIFF Tags
    private val TAG_IMAGEWIDTH = 256
    private val TAG_IMAGELENGTH = 257
    private val TAG_BITSPERSAMPLE = 258
    private val TAG_MODELPIXELSCALE = 33550
    private val TAG_MODELTIEPOINT = 33922
    private val TAG_STRIPOFFSETS = 273
    private val TAG_STRIPBYTECOUNTS = 279
    private val TAG_SAMPLEFORMAT = 339

    init {
        parseHeader()
    }

    private fun parseHeader() {
        val raf = RandomAccessFile(file, "r")
        val channel = raf.channel
        buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        raf.close()

        // 1. Check Byte Order
        val byteOrder = buffer.get().toInt().toChar()
        if (byteOrder == 'I') {
            isLittleEndian = true
            buffer.order(ByteOrder.LITTLE_ENDIAN)
        } else if (byteOrder == 'M') {
            isLittleEndian = false
            buffer.order(ByteOrder.BIG_ENDIAN)
        } else {
            throw IllegalArgumentException("Invalid TIFF byte order: $byteOrder")
        }

        // 2. Check Magic Number (42)
        if (buffer.short != 42.toShort()) {
            throw IllegalArgumentException("Invalid TIFF magic number")
        }

        // 3. Get first IFD offset
        val ifdOffset = buffer.int.toLong() and 0xFFFFFFFFL
        parseIFD(ifdOffset)
    }

    private fun parseIFD(offset: Long) {
        buffer.position(offset.toInt())
        val numEntries = buffer.short.toInt() and 0xFFFF

        val tags = mutableMapOf<Int, TagValue>()

        for (i in 0 until numEntries) {
            val tag = buffer.short.toInt() and 0xFFFF
            val type = buffer.short.toInt() and 0xFFFF
            val count = buffer.int.toInt() and 0xFFFFFFFF

            val value: Any = when {
                count * getTypeSize(type) <= 4 -> {
                    val tempBuffer = buffer.duplicate()
                    readValue(tempBuffer, type, count)
                }
                else -> {
                    val offsetVal = buffer.int.toLong() and 0xFFFFFFFFL
                    val tempBuffer = buffer.duplicate()
                    tempBuffer.position(offsetVal.toInt())
                    readValue(tempBuffer, type, count)
                }
            }
            tags[tag] = TagValue(type, count, value)
        }

        // Assign properties
        width = (tags[TAG_IMAGEWIDTH]?.value as? Int) ?: 0
        height = (tags[TAG_IMAGELENGTH]?.value as? Int) ?: 0

        modelTiepoint = (tags[TAG_MODELTIEPOINT]?.value as? DoubleArray) ?: doubleArrayOf()
        modelPixelScale = (tags[TAG_MODELPIXELSCALE]?.value as? DoubleArray) ?: doubleArrayOf()

        // Handle strips
        stripOffsets = (tags[TAG_STRIPOFFSETS]?.value as? LongArray) ?: longArrayOf()
        stripByteCounts = (tags[TAG_STRIPBYTECOUNTS]?.value as? IntArray) ?: intArrayOf()

        if (stripOffsets.isNotEmpty()) {
            rasterDataOffset = stripOffsets[0]
        }

        // Parse sample format and bits per sample
        sampleFormat = (tags[TAG_SAMPLEFORMAT]?.value as? Int) ?: 3
        bitsPerSample = (tags[TAG_BITSPERSAMPLE]?.value as? Int) ?: 32
    }

    private fun getTypeSize(type: Int): Int {
        return when (type) {
            1 -> 1 // BYTE
            2 -> 1 // ASCII
            3 -> 2 // SHORT
            4 -> 4 // LONG
            5 -> 8 // RATIONAL
            12 -> 8 // DOUBLE
            else -> 4 // Default to 4 for simplicity (e.g. FLOAT)
        }
    }

    private fun readValue(buf: ByteBuffer, type: Int, count: Int): Any {
        return when (type) {
            1 -> { // BYTE
                val arr = IntArray(count)
                for (i in 0 until count) arr[i] = buf.get().toInt() and 0xFF
                arr
            }
            2 -> { // ASCII
                val arr = ByteArray(count)
                buf.get(arr)
                arr
            }
            3 -> { // SHORT
                val arr = IntArray(count)
                for (i in 0 until count) arr[i] = buf.short.toInt() and 0xFFFF
                arr
            }
            4 -> { // LONG
                val arr = LongArray(count)
                for (i in 0 until count) arr[i] = buf.int.toLong() and 0xFFFFFFFFL
                arr
            }
            12 -> { // DOUBLE
                val arr = DoubleArray(count)
                for (i in 0 until count) arr[i] = buf.double
                arr
            }
            else -> {
                // Handling Float (often type 3 or 4 depending on implementation, but let's assume we need to read 4 bytes)
                if (type == 3 || type == 4) { // If it's a 4-byte type like FLOAT
                    val arr = FloatArray(count)
                    for (i in 0 until count) arr[i] = buf.float
                    arr
                } else {
                    // Fallback for unknown types - just return the raw bytes as Ints for now
                    val arr = IntArray(count)
                    for (i in 0 until count) arr[i] = buf.int
                    arr
                }
            }
        }
    }

    private data class TagValue(val type: Int, val count: Int, val value: Any)

    override fun getElevationAt(x: Int, y: Int): Float {
        if (x !in 0 until width || y !in 0 until height) return 0f

        val bytesPerPixel = bitsPerSample / 8
        val pixelOffset = (y.toLong() * width + x) * bytesPerPixel
        val totalOffset = rasterDataOffset + pixelOffset

        if (totalOffset + bytesPerPixel > buffer.limit()) return 0f

        val readBuf = buffer.duplicate()
        readBuf.position(totalOffset.toInt())

        return when (sampleFormat) {
            3 -> { // IEEE Float
                readBuf.float
            }
            2 -> { // Signed Integer (e.g., Int16)
                if (bitsPerSample == 16) {
                    readBuf.short.toFloat()
                } else {
                    readBuf.int.toFloat()
                }
            }
            1 -> { // Unsigned Integer
                if (bitsPerSample == 16) {
                    readBuf.short.toInt().and(0xFFFF).toFloat()
                } else {
                    readBuf.int.toLong().and(0xFFFFFFFFL).toFloat()
                }
            }
            else -> {
                // Fallback to float
                readBuf.float
            }
        }
    }

    override fun getElevationAt(lat: Double, lon: Double): Float {
        if (modelTiepoint.size < 6 || modelPixelScale.size < 3) return 0f

        // ModelTiepoint: [0, 0, 0, lat, lon, alt]
        // ModelPixelScale: [scaleX, scaleY, scaleZ]

        val tieLat = modelTiepoint[3]
        val tieLon = modelTiepoint[4]
        val scaleX = modelPixelScale[0]
        val scaleY = modelPixelScale[1]

        val x = ((lon - tieLon) / scaleX).toInt()
        val y = ((tieLat - lat) / scaleY).toInt()

        return getElevationAt(x, y)
    }
}
