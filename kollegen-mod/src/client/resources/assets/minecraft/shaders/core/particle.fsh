#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:globals.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;
layout(location = 4) in vec2 lightCoord;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 texCol = texture(Sampler0, texCoord0);
    vec4 color = texCol * vertexColor * ColorModulator;
    if (color.a < 0.06) {
        discard;
    }

    if (ShaderFlags.x > 0.5 && ShaderWorldB.y > 0.01) {
        float blueRain = smoothstep(0.05, 0.20, texCol.b - max(texCol.r, texCol.g * 0.90)) * step(0.35, texCol.b);
        if (blueRain > 0.05) {
            float distFade = smoothstep(38.0, 6.0, sphericalVertexDistance) * 0.45 + 0.55;
            float nearFade = smoothstep(0.8, 3.2, sphericalVertexDistance);
            float coreSoft = smoothstep(0.15, 0.85, texCol.a);
            color.a *= mix(1.0, 0.28 * distFade * nearFade * (0.45 + 0.55 * coreSoft), blueRain);
            #ifndef OIT_ALPHA_ONLY
            float luma = dot(color.rgb, vec3(0.28, 0.58, 0.14));
            float dayFac = SunDirData.w;
            vec3 skyRainTint = mix(vec3(0.46, 0.56, 0.76), vec3(0.88, 0.93, 0.99), dayFac);
            vec3 torchGlow = vec3(1.18, 0.86, 0.54) * smoothstep(0.28, 0.95, lightCoord.x) * 0.65;
            vec3 shadedRain = vec3(luma * 1.15) * skyRainTint + torchGlow * luma;
            color.rgb = mix(color.rgb, shadedRain, blueRain * 0.96);
            #endif
        }
    }

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
