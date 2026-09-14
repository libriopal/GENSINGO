package com.ginsengo.steward.terrain3d

/**
 * GLSL ES 3.0 sources for the terrain overlay.
 *
 * These are kept as plain constants in one file so they can be extracted and compiled by
 * `glslangValidator` outside the app — see `tools/validate_shaders.sh` and
 * `ShaderSourceTest`. A shader that fails to compile on a device is a black screen with a
 * message in logcat that nobody reads; catching it at build time is the difference between
 * a typo and a silent blank overlay.
 *
 * GLES 3.0 is safe at minSdk 26 (it has been guaranteed since API 18 on capable hardware
 * and is universal on Android 8+), and it buys `uint` index buffers, which a 192x192 mesh
 * plus skirts needs.
 */
object TerrainShaders {

    const val VERTEX = """#version 300 es
precision highp float;

layout(location = 0) in vec3 a_position;    // world pixels, relative to the mesh origin
layout(location = 1) in vec3 a_normal;
layout(location = 2) in float a_elevation;  // metres
layout(location = 3) in float a_suitability;// 0..1

uniform mat4 u_mvp;
uniform vec2 u_elevationRange;              // (min, max) metres, for the hypsometric tint

out vec3 v_normal;
out float v_elevNorm;
out float v_suitability;
out float v_elevation;

void main() {
    v_normal = normalize(a_normal);
    v_suitability = a_suitability;
    v_elevation = a_elevation;
    float span = max(u_elevationRange.y - u_elevationRange.x, 1.0);
    v_elevNorm = clamp((a_elevation - u_elevationRange.x) / span, 0.0, 1.0);
    gl_Position = u_mvp * vec4(a_position, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;

in vec3 v_normal;
in float v_elevNorm;
in float v_suitability;
in float v_elevation;

uniform float u_opacity;
uniform float u_suitabilityMix;   // 0 = hypsometric tint, 1 = habitat forecast
uniform vec3  u_lightDir;         // normalised, pointing towards the light
uniform float u_minScore;         // below this the forecast contributes nothing

out vec4 fragColor;

// Hypsometric ramp over the Appalachian band, matching the 2D colour-relief layer so the
// two views of the same ground do not disagree about what colour a ridge is.
vec3 elevationTint(float t) {
    vec3 c0 = vec3(0.024, 0.133, 0.180);  // #06222E
    vec3 c1 = vec3(0.071, 0.353, 0.227);  // #125A3A
    vec3 c2 = vec3(0.180, 0.490, 0.275);  // #2E7D46
    vec3 c3 = vec3(0.549, 0.659, 0.306);  // #8CA84E
    vec3 c4 = vec3(1.000, 0.784, 0.341);  // #FFC857
    if (t < 0.25) return mix(c0, c1, t / 0.25);
    if (t < 0.50) return mix(c1, c2, (t - 0.25) / 0.25);
    if (t < 0.75) return mix(c2, c3, (t - 0.50) / 0.25);
    return mix(c3, c4, (t - 0.75) / 0.25);
}

// Habitat forecast ramp, matching SuitabilityRasterizer.colourFor so the 3D mesh and the
// flat heatmap read as the same surface.
vec3 suitabilityTint(float t) {
    vec3 lo = vec3(0.055, 0.290, 0.353);  // #0E4A5A
    vec3 mid = vec3(0.000, 1.000, 0.533); // #00FF88
    vec3 hi = vec3(1.000, 0.784, 0.341);  // #FFC857
    if (t < 0.5) return mix(lo, mid, t / 0.5);
    return mix(mid, hi, (t - 0.5) / 0.5);
}

void main() {
    vec3 n = normalize(v_normal);
    float lambert = max(dot(n, normalize(u_lightDir)), 0.0);
    float shade = 0.35 + 0.65 * lambert;

    float score = clamp((v_suitability - u_minScore) / max(1.0 - u_minScore, 0.001), 0.0, 1.0);
    vec3 base = elevationTint(v_elevNorm);
    vec3 forecast = suitabilityTint(score);
    vec3 rgb = mix(base, forecast, u_suitabilityMix * step(u_minScore, v_suitability));

    // In forecast mode, weak ground fades out rather than tinting the whole hillside, so
    // the map underneath stays readable everywhere the model has nothing to say.
    float alpha = mix(u_opacity, u_opacity * (0.25 + 0.75 * score), u_suitabilityMix);

    fragColor = vec4(rgb * shade, alpha);
}
"""

    /** Attribute locations, matching the `layout(location = ...)` qualifiers above. */
    const val LOC_POSITION = 0
    const val LOC_NORMAL = 1
    const val LOC_ELEVATION = 2
    const val LOC_SUITABILITY = 3
}
