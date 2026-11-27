#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec3 cloudNormal;
layout(location = 3) in float cloudHeightFrac;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    return color;
}

void main() {
    if (ShaderFlags.x > 0.5 && ShaderWorldB.w > 0.01 && ShaderExtra.x > 0.35) {
        discard;
    }
    vec4 color = vertexColor;
    if (ShaderFlags.x > 0.5 && ShaderWorldB.w > 0.01) {
        vec3 n = length(cloudNormal) > 0.001 ? normalize(cloudNormal) : vec3(0.0, 1.0, 0.0);
        vec3 sunDir = normalize(SunDirData.xyz);
        float dayFac = SunDirData.w;
        float sunFace = dot(n, sunDir) * 0.5 + 0.5;
        float sunsetAmt = (1.0 - abs(dayFac * 2.0 - 1.0)) * smoothstep(0.0, 0.4, dayFac + 0.1);
        vec3 shadowCol = mix(vec3(0.76, 0.82, 0.96), vec3(0.72, 0.78, 0.94), dayFac);
        vec3 litCol = mix(vec3(0.86, 0.92, 1.05), mix(vec3(1.16, 0.84, 0.66), vec3(1.12, 1.09, 1.03), dayFac), smoothstep(0.05, 0.45, dayFac));
        vec3 scatter = mix(shadowCol, litCol, smoothstep(0.15, 0.88, sunFace * 0.65 + cloudHeightFrac * 0.35));
        scatter = mix(scatter, litCol * vec3(1.06, 0.86, 0.74), sunsetAmt * 0.45 * cloudHeightFrac);
        color.rgb *= mix(vec3(1.0), scatter, clamp(ShaderWorldB.w, 0.0, 1.2));
    }
    #ifndef OIT_DEPTH_BOUNDS
    float fade = 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    if (ShaderFlags.x > 0.5) {
        fade = smoothstep(0.0, 0.88, fade);
        float realFade = mix(1.0, 0.0, smoothstep(0.35, 0.85, clamp(ShaderExtra.x, 0.0, 1.5)));
        fade *= realFade;
    }
    color.a *= fade;
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
