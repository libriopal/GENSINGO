package com.ginsengo.steward.terrain3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consistency between the GLSL text and the Kotlin constants that feed it.
 *
 * These two drift silently. `glVertexAttribPointer` takes an integer location, and the
 * shader declares its own with `layout(location = N)`. If they disagree, nothing errors:
 * the GPU reads normals as positions and draws a shape that is technically a mesh. Neither
 * `glslangValidator` (which sees only the GLSL) nor the Kotlin compiler (which sees only
 * the constants) can catch that, because the connection between them is a number typed
 * twice.
 *
 * Compilation itself is checked separately by `tools/validate_shaders.sh`, which runs the
 * real glslang compiler over these sources — the only evaluator available that actually
 * executes shader code, since this project has no device.
 */
class ShaderSourceTest {

    private fun declaredLocation(name: String): Int? =
        Regex("""layout\(location\s*=\s*(\d+)\)\s*in\s+\w+\s+$name\b""")
            .find(TerrainShaders.VERTEX)?.groupValues?.get(1)?.toInt()

    @Test
    fun attributeLocationsMatchTheShaderDeclarations() {
        assertEquals(
            "a_position location", TerrainShaders.LOC_POSITION, declaredLocation("a_position")
        )
        assertEquals(
            "a_normal location", TerrainShaders.LOC_NORMAL, declaredLocation("a_normal")
        )
        assertEquals(
            "a_elevation location", TerrainShaders.LOC_ELEVATION, declaredLocation("a_elevation")
        )
        assertEquals(
            "a_suitability location",
            TerrainShaders.LOC_SUITABILITY, declaredLocation("a_suitability"),
        )
    }

    @Test
    fun attributeLocationsAreDistinct() {
        val locs = listOf(
            TerrainShaders.LOC_POSITION,
            TerrainShaders.LOC_NORMAL,
            TerrainShaders.LOC_ELEVATION,
            TerrainShaders.LOC_SUITABILITY,
        )
        assertEquals("attribute locations must be unique", locs.size, locs.toSet().size)
    }

    /**
     * The interleaved layout the renderer binds must match what the mesh writes. Position
     * and normal are vec3, elevation and suitability are scalars: eight floats in that
     * order.
     */
    @Test
    fun vertexLayoutOffsetsAreContiguousAndCorrectlySized() {
        assertEquals(0, TerrainMesh.OFF_POSITION)
        assertEquals(3, TerrainMesh.OFF_NORMAL)
        assertEquals(6, TerrainMesh.OFF_ELEVATION)
        assertEquals(7, TerrainMesh.OFF_SUITABILITY)
        assertEquals(8, TerrainMesh.FLOATS_PER_VERTEX)
        assertEquals(32, TerrainMesh.STRIDE_BYTES)
    }

    @Test
    fun bothStagesDeclareTheSameGlslVersion() {
        assertTrue("vertex needs #version", TerrainShaders.VERTEX.startsWith("#version 300 es"))
        assertTrue("fragment needs #version", TerrainShaders.FRAGMENT.startsWith("#version 300 es"))
    }

    /** Every varying written by the vertex stage must be read by the fragment stage. */
    @Test
    fun varyingsLineUpAcrossStages() {
        val outs = Regex("""^out\s+\w+\s+(\w+);""", RegexOption.MULTILINE)
            .findAll(TerrainShaders.VERTEX).map { it.groupValues[1] }.toSet()
        val ins = Regex("""^in\s+\w+\s+(\w+);""", RegexOption.MULTILINE)
            .findAll(TerrainShaders.FRAGMENT).map { it.groupValues[1] }.toSet()
        assertTrue("vertex stage should emit varyings", outs.isNotEmpty())
        assertEquals("vertex outputs must match fragment inputs", outs, ins)
    }

    /** Every uniform the renderer looks up must actually exist in a shader. */
    @Test
    fun rendererUniformsExistInTheShaders() {
        val src = TerrainShaders.VERTEX + TerrainShaders.FRAGMENT
        listOf(
            "u_mvp", "u_opacity", "u_suitabilityMix",
            "u_lightDir", "u_minScore", "u_elevationRange",
        ).forEach {
            assertTrue("uniform $it is queried by the renderer but not declared", src.contains(it))
        }
    }
}
