#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 worldPos;
layout(location = 6) in vec2 lightCoords;

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
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    vec3 dX = dFdx(worldPos);
    vec3 dY = dFdy(worldPos);
    vec3 N = normalize(cross(dX, dY));
    if (!gl_FrontFacing) {
        N = -N;
    }

    vec3 sunDir = normalize(vec3(0.48, 0.78, 0.38));
    vec3 V = normalize(-worldPos);
    float NdotL = max(dot(N, sunDir), 0.0);
    float upFactor = max(N.y, 0.0);

    vec3 sunLight = vec3(1.10, 1.02, 0.92) * (NdotL * 0.45 + 0.55);
    vec3 skyLight = vec3(0.85, 0.92, 1.05) * (upFactor * 0.3 + 0.7);
    vec3 naturalLighting = mix(skyLight, sunLight, lightCoords.y);

    float isWater = (color.a < 0.95 && color.a > 0.05) ? 1.0 : 0.0;
    if (isWater > 0.5) {
        float wT = GameTime * 1800.0;
        vec2 p = worldPos.xz * 0.7;
        float w1 = sin(p.x * 2.2 + wT * 1.6) * cos(p.y * 1.9 + wT * 1.3);
        float w2 = sin(p.x * 4.1 - wT * 2.2 + p.y * 2.7) * 0.5;
        vec3 waveN = normalize(N + vec3(w1 * 0.20, 0.0, w2 * 0.20));

        vec3 H = normalize(sunDir + V);
        float spec = pow(max(dot(waveN, H), 0.0), 64.0) * 2.0 * lightCoords.y;
        float fresnel = 0.04 + 0.96 * pow(1.0 - max(dot(V, waveN), 0.0), 4.5);

        vec3 deepWater = vec3(0.06, 0.22, 0.42);
        vec3 shallowWater = vec3(0.15, 0.60, 0.72);
        vec3 waterColor = mix(deepWater, shallowWater, clamp(1.0 - sphericalVertexDistance / 36.0, 0.0, 1.0));

        color.rgb = mix(color.rgb * 0.45 + waterColor * 0.55, vec3(1.0, 0.98, 0.92), fresnel * 0.55);
        color.rgb += vec3(1.08, 1.02, 0.94) * spec;
        color.a = clamp(color.a * 0.85 + 0.15, 0.35, 0.90);
    } else {
        color.rgb *= naturalLighting;
        if (lightCoords.y > 0.5) {
            vec3 H = normalize(sunDir + V);
            float spec = pow(max(dot(N, H), 0.0), 32.0) * 0.18 * lightCoords.y;
            color.rgb += vec3(1.0, 0.96, 0.88) * spec;
        }
    }

    float torch = pow(lightCoords.x, 2.2);
    vec3 torchGlow = vec3(1.0, 0.65, 0.32) * (torch * 0.65);
    color.rgb += color.rgb * torchGlow;

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
