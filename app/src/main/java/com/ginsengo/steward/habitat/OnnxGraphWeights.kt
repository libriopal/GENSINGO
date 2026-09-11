package com.ginsengo.steward.habitat

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extracts the Gemm weight and bias initialisers straight out of the bundled
 * habitat_model.onnx.
 *
 * Why parse the file instead of hardcoding the seven floats:
 * PRD §8.1 offers a "hand-port the weights to Kotlin" fallback if onnxruntime-android
 * misbehaves. Hardcoding creates two sources of truth that silently disagree the moment
 * the .onnx asset is replaced with a trained model - and the disagreement would surface as
 * a slightly different habitat score, which nobody would notice. Reading the same bytes
 * both paths run on makes divergence impossible by construction.
 *
 * This is a deliberately minimal protobuf reader: just enough wire-format handling to walk
 * ModelProto.graph(7) -> GraphProto.initializer(5) -> TensorProto{dims(1), data_type(2),
 * name(8), raw_data(9)}. It is not a general ONNX parser and does not pretend to be.
 */
object OnnxGraphWeights {

    private const val DT_FLOAT = 1

    data class Tensor(val name: String, val dims: List<Long>, val values: FloatArray) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = name.hashCode()
    }

    /** name -> tensor, for every float initialiser in the graph. */
    fun readInitializers(input: InputStream): Map<String, Tensor> {
        val bytes = input.readBytes()
        val model = Reader(bytes, 0, bytes.size)
        var graph: Reader? = null
        while (model.hasMore()) {
            val (field, wire) = model.tag()
            if (field == 7 && wire == 2) graph = model.lengthDelimited()
            else model.skip(wire)
        }
        val g = graph ?: return emptyMap()

        val out = LinkedHashMap<String, Tensor>()
        while (g.hasMore()) {
            val (field, wire) = g.tag()
            if (field == 5 && wire == 2) {
                readTensor(g.lengthDelimited())?.let { out[it.name] = it }
            } else {
                g.skip(wire)
            }
        }
        return out
    }

    private fun readTensor(t: Reader): Tensor? {
        val dims = ArrayList<Long>()
        var dataType = 0
        var name = ""
        var raw: ByteArray? = null
        while (t.hasMore()) {
            val (field, wire) = t.tag()
            when {
                field == 1 && wire == 0 -> dims.add(t.varint())
                // dims may also arrive packed
                field == 1 && wire == 2 -> {
                    val p = t.lengthDelimited()
                    while (p.hasMore()) dims.add(p.varint())
                }
                field == 2 && wire == 0 -> dataType = t.varint().toInt()
                field == 8 && wire == 2 -> name = t.lengthDelimited().utf8()
                field == 9 && wire == 2 -> raw = t.lengthDelimited().bytes()
                else -> t.skip(wire)
            }
        }
        if (dataType != DT_FLOAT || raw == null || name.isEmpty()) return null
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val vals = FloatArray(raw.size / 4) { bb.getFloat(it * 4) }
        return Tensor(name, dims, vals)
    }

    /** Cursor over a protobuf byte range. */
    private class Reader(val buf: ByteArray, var pos: Int, val end: Int) {
        fun hasMore() = pos < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (pos < end) {
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 63) break
            }
            return result
        }

        /** Returns (fieldNumber, wireType). */
        fun tag(): Pair<Int, Int> {
            val t = varint()
            return Pair((t ushr 3).toInt(), (t and 0x7L).toInt())
        }

        fun lengthDelimited(): Reader {
            val len = varint().toInt()
            val start = pos
            val stop = minOf(end, start + maxOf(0, len))
            pos = stop
            return Reader(buf, start, stop)
        }

        fun bytes() = buf.copyOfRange(pos, end)
        fun utf8() = String(buf, pos, end - pos, Charsets.UTF_8)

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> lengthDelimited()
                5 -> pos += 4
                else -> pos = end
            }
        }
    }
}
