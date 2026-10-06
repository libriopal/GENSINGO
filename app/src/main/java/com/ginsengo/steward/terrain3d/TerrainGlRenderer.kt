package com.ginsengo.steward.terrain3d

import android.opengl.GLES30
import android.util.Log
import com.ginsengo.steward.terrain3d.gl.GLTextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the terrain mesh with its baked colour texture.
 *
 * This is the part that cannot be tested without a device, so it is kept deliberately
 * thin: it compiles two shaders, uploads one interleaved buffer and one texture, and issues
 * one draw call. Every decision with arithmetic in it (projection, mesh, colour) lives in
 * [MapCamera], [TerrainMesh] and [TerrainTextures], which are pure Kotlin and tested.
 *
 * CONTEXT LOSS (Phase 8). Android can destroy the GL context when the app is backgrounded;
 * [onSurfaceCreated] then runs again with empty buffers. The first version only uploaded a
 * mesh once, from a hand-off slot it had already emptied, so the view came back blank. The
 * current mesh and texture are now kept and re-uploaded whenever a context is created.
 */
class TerrainGlRenderer : GLTextureView.Renderer {

    /** Latest mesh / texture, kept for re-upload after a context loss. */
    private val mesh = AtomicReference<TerrainMesh.Mesh?>(null)
    private val texture = AtomicReference<Texture?>(null)
    private val memory = AtomicReference<Memory?>(null)
    @Volatile private var memoryDirty = false
    private val cameraState = AtomicReference<Frame?>(null)
    @Volatile private var meshDirty = false
    @Volatile private var textureDirty = false

    @Volatile var lastError: String? = null
        private set
    @Volatile var programReady: Boolean = false
        private set
    @Volatile var trianglesDrawn: Int = 0
        private set

    /**
     * Called on the GL thread after every frame that drew the terrain. The 3D view waits for the
     * first one before it fades in over the map: fading in a surface with nothing on it yet would
     * flash the haze colour over the map.
     */
    @Volatile var onTerrainDrawn: (() -> Unit)? = null

    /** F.1: called on the GL thread when drawing fails, so the screen can say so instead of staying blank. */
    @Volatile var onError: ((String) -> Unit)? = null

    private fun fail(msg: String) {
        lastError = msg
        com.ginsengo.steward.perf.FieldDiagnostics.glError = msg
        Log.e(TAG, msg)
        onError?.invoke(msg)
    }

    class Texture(val argb: IntArray, val size: Int)

    /** The travel memory mask (TravelMask.rg): two bytes per texel, [size] square. */
    class Memory(val rg: ByteArray, val size: Int)

    data class Frame(
        val mvp: FloatArray,
        /** Eye depth where haze begins and where it is full. */
        val hazeStart: Float,
        val hazeEnd: Float,
        /** Draw where you've been (J31). */
        val visitedOn: Boolean = false,
        /** Grey out ground you have walked, leaving colour on unwalked ground (J20). */
        val unwalkedOn: Boolean = false,
    ) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    private var program = 0
    private var vbo = 0
    private var ebo = 0
    private var vao = 0
    private var tex = 0
    private var memTex = 0
    private var indexCount = 0
    private var hasTexture = false

    private var uMvp = -1
    private var uColour = -1
    private var uLightDir = -1
    private var uHazeColour = -1
    private var uHaze = -1
    private var uMemory = -1
    private var uMemoryOn = -1

    fun submitMesh(m: TerrainMesh.Mesh?) { mesh.set(m); meshDirty = true }
    fun hasMesh(m: TerrainMesh.Mesh): Boolean = mesh.get() === m
    fun submitTexture(t: Texture?) { texture.set(t); textureDirty = true }
    fun submitFrame(frame: Frame?) = cameraState.set(frame)
    fun submitMemory(m: Memory?) { memory.set(m); memoryDirty = true }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val h = TerrainShaders.HAZE_RGB
        GLES30.glClearColor(h[0], h[1], h[2], 1f)
        com.ginsengo.steward.perf.FieldDiagnostics.gpu =
            "${GLES30.glGetString(GLES30.GL_RENDERER)} (${GLES30.glGetString(GLES30.GL_VERSION)})"
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        // Terrain is a height field viewed from above; back faces are never wanted.
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        program = buildProgram() ?: run {
            programReady = false
            return
        }
        uMvp = GLES30.glGetUniformLocation(program, "u_mvp")
        uColour = GLES30.glGetUniformLocation(program, "u_colour")
        uLightDir = GLES30.glGetUniformLocation(program, "u_lightDir")
        uHazeColour = GLES30.glGetUniformLocation(program, "u_hazeColour")
        uHaze = GLES30.glGetUniformLocation(program, "u_haze")
        uMemory = GLES30.glGetUniformLocation(program, "u_memory")
        uMemoryOn = GLES30.glGetUniformLocation(program, "u_memoryOn")

        val buf = IntArray(1)
        GLES30.glGenVertexArrays(1, buf, 0); vao = buf[0]
        GLES30.glGenBuffers(1, buf, 0); vbo = buf[0]
        GLES30.glGenBuffers(1, buf, 0); ebo = buf[0]
        GLES30.glGenTextures(1, buf, 0); tex = buf[0]
        GLES30.glGenTextures(1, buf, 0); memTex = buf[0]
        // Until a mask arrives the shader samples an empty one: nothing remembered, nothing greyed.
        uploadMemory(Memory(ByteArray(2), 1))
        indexCount = 0
        hasTexture = false
        // A new context has nothing in it: whatever was last submitted goes up again.
        meshDirty = mesh.get() != null
        textureDirty = texture.get() != null
        memoryDirty = memory.get() != null
        programReady = true
        lastError = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (!programReady) return

        if (meshDirty) { meshDirty = false; mesh.get()?.let { uploadMesh(it) } }
        if (textureDirty) { textureDirty = false; texture.get()?.let { uploadTexture(it) } }
        if (memoryDirty) { memoryDirty = false; memory.get()?.let { uploadMemory(it) } }

        val frame = cameraState.get() ?: return
        if (indexCount == 0 || !hasTexture) return

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, frame.mvp, 0)
        // Light from the north-west and well above, the convention relief maps use; the
        // baked hillshade uses the same direction.
        GLES30.glUniform3f(uLightDir, -0.5f, -0.5f, 0.7f)
        val h = TerrainShaders.HAZE_RGB
        GLES30.glUniform3f(uHazeColour, h[0], h[1], h[2])
        GLES30.glUniform2f(uHaze, frame.hazeStart, frame.hazeEnd)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glUniform1i(uColour, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, memTex)
        GLES30.glUniform1i(uMemory, 1)
        GLES30.glUniform2f(uMemoryOn, if (frame.visitedOn) 1f else 0f, if (frame.unwalkedOn) 1f else 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        trianglesDrawn = indexCount / 3
        com.ginsengo.steward.perf.FieldDiagnostics.framesDrawn++
        onTerrainDrawn?.invoke()
    }

    private fun uploadMesh(mesh: TerrainMesh.Mesh) {
        val vb = ByteBuffer.allocateDirect(mesh.vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(mesh.vertices).position(0)
        val ib = ByteBuffer.allocateDirect(mesh.indices.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(mesh.indices).position(0)

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, ib, GLES30.GL_STATIC_DRAW)

        val stride = TerrainMesh.STRIDE_BYTES
        fun attrib(loc: Int, size: Int, offsetFloats: Int) {
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, stride, offsetFloats * 4)
        }
        attrib(TerrainShaders.LOC_POSITION, 3, TerrainMesh.OFF_POSITION)
        attrib(TerrainShaders.LOC_NORMAL, 3, TerrainMesh.OFF_NORMAL)
        attrib(TerrainShaders.LOC_ELEVATION, 1, TerrainMesh.OFF_ELEVATION)
        attrib(TerrainShaders.LOC_UV, 2, TerrainMesh.OFF_UV)
        attrib(TerrainShaders.LOC_WALL, 1, TerrainMesh.OFF_WALL)

        GLES30.glBindVertexArray(0)
        indexCount = mesh.indices.size
    }

    /**
     * Uploads with a full mip chain, trilinear filtering, and anisotropic filtering where the
     * GPU offers it: the terrain is seen at a glancing angle, which is exactly where plain
     * mipmapping blurs contours and creeks into mush.
     */
    private fun uploadTexture(t: Texture) {
        val buf = ByteBuffer.allocateDirect(t.size * t.size * 4).order(ByteOrder.nativeOrder())
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            // RGBA bytes in memory are the int 0xAABBGGRR: swap R and B of each ARGB pixel.
            val ib = buf.asIntBuffer()
            for (p in t.argb) ib.put((p and 0xFF00FF00.toInt()) or ((p shr 16) and 0xFF) or ((p and 0xFF) shl 16))
        } else {
            for (p in t.argb) {
                buf.put((p shr 16).toByte()); buf.put((p shr 8).toByte()); buf.put(p.toByte()); buf.put((p ushr 24).toByte())
            }
        }
        buf.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, t.size, t.size, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        if (ext.contains("GL_EXT_texture_filter_anisotropic")) {
            val max = FloatArray(1)
            GLES30.glGetFloatv(MAX_ANISOTROPY_EXT, max, 0)
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, TEXTURE_MAX_ANISOTROPY_EXT, minOf(8f, max[0]).coerceAtLeast(1f))
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) {
            fail("texture upload: GL error 0x%x".format(err))
        }
        hasTexture = err == GLES30.GL_NO_ERROR
    }

    /** Two bytes a texel (R, G), linear filtering so the memory's edges are soft, not blocky. */
    private fun uploadMemory(m: Memory) {
        val buf = ByteBuffer.allocateDirect(m.rg.size).order(ByteOrder.nativeOrder())
        buf.put(m.rg).position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, memTex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG8, m.size, m.size, 0,
            GLES30.GL_RG, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
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
            fail("link: " + GLES30.glGetProgramInfoLog(p))
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
            fail("compile: " + GLES30.glGetShaderInfoLog(s))
            GLES30.glDeleteShader(s)
            return null
        }
        return s
    }

    private companion object {
        const val TAG = "TerrainGlRenderer"
        const val TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        const val MAX_ANISOTROPY_EXT = 0x84FF
    }
}
