package com.ginsengo.steward.terrain3d

/**
 * GLSL ES 3.0 sources for the 3D terrain view.
 *
 * These are kept as plain constants in one file so they can be extracted and compiled by
 * `glslangValidator` outside the app (see `tools/validate_shaders.sh` and `ShaderSourceTest`).
 * A shader that fails to compile on a device is a black screen with a message in logcat that
 * nobody reads; catching it at build time is the difference between a typo and a blank view.
 *
 * All colour comes from one texture that [TerrainTextures] bakes on the CPU: the habitat
 * surface (the same function and ramp as the 2D heatmap), relief shading, contours and
 * water. The shader only drapes it, adds a little light from the mesh normal so the form
 * reads in perspective, and hazes distant ground so depth reads too. Keeping the arithmetic
 * out of GLSL keeps it testable.
 */
object TerrainShaders {

    const val VERTEX = """#version 300 es
precision highp float;

layout(location = 0) in vec3 a_position;    // world pixels, relative to the mesh origin
layout(location = 1) in vec3 a_normal;
layout(location = 2) in float a_elevation;  // metres (kept for completeness; colour is baked)
layout(location = 3) in vec2 a_uv;          // 0..1 over the interior, north-west origin

uniform mat4 u_mvp;

out vec3 v_normal;
out vec2 v_uv;
out float v_depth;

void main() {
    v_normal = normalize(a_normal);
    v_uv = a_uv;
    gl_Position = u_mvp * vec4(a_position, 1.0);
    v_depth = gl_Position.w;
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;

in vec3 v_normal;
in vec2 v_uv;
in float v_depth;

uniform sampler2D u_colour;
uniform vec3 u_lightDir;     // normalised, pointing towards the light
uniform vec3 u_hazeColour;   // the sky the far ground fades into
uniform vec2 u_haze;         // (start, end) in eye depth

out vec4 fragColor;

void main() {
    vec3 base = texture(u_colour, v_uv).rgb;
    // Relief is baked into the texture at elevation resolution; this only adds the
    // perspective form of the mesh, gently, so it does not double the shading.
    float lambert = max(dot(normalize(v_normal), normalize(u_lightDir)), 0.0);
    vec3 rgb = base * (0.80 + 0.22 * lambert);
    float haze = smoothstep(u_haze.x, u_haze.y, v_depth) * 0.6;
    fragColor = vec4(mix(rgb, u_hazeColour, haze), 1.0);
}
"""

    /** Attribute locations, matching the `layout(location = ...)` qualifiers above. */
    const val LOC_POSITION = 0
    const val LOC_NORMAL = 1
    const val LOC_ELEVATION = 2
    const val LOC_UV = 3

    /** Sky and haze: a dark slate that sits with the app's colours. */
    val HAZE_RGB = floatArrayOf(0.055f, 0.098f, 0.118f)
}
