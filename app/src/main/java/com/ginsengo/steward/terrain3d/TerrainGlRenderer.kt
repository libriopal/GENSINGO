package com.ginsengo.steward.terrain3d

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the terrain mesh into a transparent GL surface layered over the map.
 *
 * This is the part that cannot be tested without a device, so it is kept deliberately
 * thin: it compiles two shaders, uploads one interleaved buffer, and issues one draw call.
 * Every decision with arithmetic in it — the projection, the mesh, the normals, the
 * suitability — lives in [MapCamera], [TerrainMesh] and the terrain package, all of which
 * are pure Kotlin and covered by tests.
 *
 * Shader compilation and link status are checked and logged rather than assumed. A GLES
 * program that fails to link silently renders nothing, which is indistinguishable from
 * "the overlay is off" unless somebody asks.
 */
class TerrainGlRenderer : GLSurfaceView.Renderer {

    /** Set from any thread; consumed on the GL thread at the next frame. */
    private val pendingMesh = AtomicReference<TerrainMesh.Mesh?>(null)
    private val cameraState = AtomicReference<Frame?>(null)

    /** Reported back so the UI can say why nothing is showing. */
    @Volatile var lastError: String? = null
        private set
    @Volatile var programReady: Boolean = false
        private set
    @Volatile var trianglesDrawn: Int = 0
        private set

    data class Frame(
        val mvp: FloatArray,
        val opacity: Float,
        val suitabilityMix: Float,
        val minScore: Float,
        val elevMin: Float,
        val elevMax: Float,
    ) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    private var program = 0
    private var vbo = 0
    private var ebo = 0
    private var vao = 0
    private var indexCount = 0

    private var uMvp = -1
    private var uOpacity = -1
    private var uSuitabilityMix = -1
    private var uLightDir = -1
    private var uMinScore = -1
    private var uElevRange = -1

    fun submitMesh(mesh: TerrainMesh.Mesh?) = pendingMesh.set(mesh)
    fun submitFrame(frame: Frame?) = cameraState.set(frame)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        // Terrain is a height field viewed from above; back faces are never wanted, and
        // culling them halves the fragment work on a 190k-triangle mesh.
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        program = buildProgram() ?: run {
            programReady = false
            return
        }
        uMvp = GLES30.glGetUniformLocation(program, "u_mvp")
        uOpacity = GLES30.glGetUniformLocation(program, "u_opacity")
        uSuitabilityMix = GLES30.glGetUniformLocation(program, "u_suitabilityMix")
        uLightDir = GLES30.glGetUniformLocation(program, "u_lightDir")
        uMinScore = GLES30.glGetUniformLocation(program, "u_minScore")
        uElevRange = GLES30.glGetUniformLocation(program, "u_elevationRange")

        val buf = IntArray(1)
        GLES30.glGenVertexArrays(1, buf, 0); vao = buf[0]
        GLES30.glGenBuffers(1, buf, 0); vbo = buf[0]
        GLES30.glGenBuffers(1, buf, 0); ebo = buf[0]
        programReady = true
        lastError = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (!programReady) return

        pendingMesh.getAndSet(null)?.let { upload(it) }

        val frame = cameraState.get() ?: return
        if (indexCount == 0) return

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, frame.mvp, 0)
        GLES30.glUniform1f(uOpacity, frame.opacity)
        GLES30.glUniform1f(uSuitabilityMix, frame.suitabilityMix)
        GLES30.glUniform1f(uMinScore, frame.minScore)
        GLES30.glUniform2f(uElevRange, frame.elevMin, frame.elevMax)
        // Light from the north-west and well above, the convention topographic maps use;
        // relief read under any other lighting inverts for most people.
        GLES30.glUniform3f(uLightDir, -0.5f, -0.5f, 0.7f)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        trianglesDrawn = indexCount / 3
    }

    private fun upload(mesh: TerrainMesh.Mesh) {
        val vb = ByteBuffer.allocateDirect(mesh.vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(mesh.vertices).position(0)
        val ib = ByteBuffer.allocateDirect(mesh.indices.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(mesh.indices).position(0)

        GLES30.glBindVertexArray(vao)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, mesh.vertices.size * 4, vb, GLES30.GL_DYNAMIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, ib, GLES30.GL_DYNAMIC_DRAW
        )

        val stride = TerrainMesh.STRIDE_BYTES
        fun attrib(loc: Int, size: Int, offsetFloats: Int) {
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(
                loc, size, GLES30.GL_FLOAT, false, stride, offsetFloats * 4
            )
        }
        attrib(TerrainShaders.LOC_POSITION, 3, TerrainMesh.OFF_POSITION)
        attrib(TerrainShaders.LOC_NORMAL, 3, TerrainMesh.OFF_NORMAL)
        attrib(TerrainShaders.LOC_ELEVATION, 1, TerrainMesh.OFF_ELEVATION)
        attrib(TerrainShaders.LOC_SUITABILITY, 1, TerrainMesh.OFF_SUITABILITY)

        GLES30.glBindVertexArray(0)
        indexCount = mesh.indices.size
    }

    private fun buildProgram(): Int? {
        val vs = compile(GLES30.GL_VERTEX_SHADER, TerrainShaders.VERTEX) ?: return null
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, TerrainShaders.FRAGMENT) ?: return null
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            lastError = "link: " + GLES30.glGetProgramInfoLog(p)
            Log.e(TAG, lastError!!)
            GLES30.glDeleteProgram(p)
            return null
        }
        return p
    }

    private fun compile(type: Int, source: String): Int? {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, source)
        GLES30.glCompileShader(s)
        val status = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            lastError = "compile: " + GLES30.glGetShaderInfoLog(s)
            Log.e(TAG, lastError!!)
            GLES30.glDeleteShader(s)
            return null
        }
        return s
    }

    private companion object {
        const val TAG = "TerrainGlRenderer"
    }
}
