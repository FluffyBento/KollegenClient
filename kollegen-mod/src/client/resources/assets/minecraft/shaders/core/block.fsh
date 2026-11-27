#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec3 localPos;
layout(location = 5) in vec2 lightCoord;

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
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifndef OIT_ALPHA_ONLY
    if (ShaderFlags.x > 0.5 && color.a > 0.01) {
        vec3 dPosX = dFdx(localPos);
        vec3 dPosY = dFdy(localPos);
        vec3 cr = cross(dPosY, dPosX);
        float crLen = length(cr);
        vec3 N = crLen > 1e-6 ? (cr / crLen) : vec3(0.0, 1.0, 0.0);
        if (dot(N, -localPos) < 0.0) {
            N = -N;
        }
        float vLen = length(localPos);
        vec3 viewDir = vLen > 1e-5 ? (-localPos / vLen) : vec3(0.0, 1.0, 0.0);
        vec3 albedo = pow(max(texCol.rgb * ColorModulator.rgb, vec3(0.0)), vec3(2.2)) * pow(max(vertexColor.rgb, vec3(0.0)), vec3(1.35));

        float skyL = pow(clamp(lightCoord.y, 0.0, 1.0), 1.65);
        float blockL = clamp(lightCoord.x, 0.0, 1.0);
        vec3 sunDir = normalize(SunDirData.xyz);
        float dayFac = SunDirData.w;
        float rain = ShaderWorldB.y;
        float sunY = clamp(sunDir.y, 0.0, 1.0);
        float voxelSun = smoothstep(0.66, 0.94, lightCoord.y);
        float ndl = clamp(dot(N, sunDir), 0.0, 1.0);

        vec3 daySun = mix(vec3(1.60, 0.80, 0.34), vec3(1.42, 1.24, 0.94), smoothstep(0.15, 0.82, sunY));
        vec3 nightSun = vec3(0.07, 0.11, 0.24);
        vec3 directCol = mix(nightSun, daySun, dayFac) * mix(1.0, 0.35, rain);
        vec3 skyFill = mix(vec3(0.016, 0.024, 0.052), vec3(0.24, 0.36, 0.56), dayFac) * (0.72 + 0.28 * max(N.y, 0.0));
        float distD = max(15.0 * (1.0 - blockL) - 1.0, 0.0);
        float torchPool = (1.65 / (1.0 + distD * distD * 0.22) * smoothstep(0.04, 0.45, blockL) + 0.14 * blockL * blockL) * (0.78 + 0.22 * N.y);
        vec3 torchCol = mix(vec3(1.38, 0.68, 0.28), vec3(1.18, 0.94, 0.66), blockL * 0.65);

        vec3 lighting = vec3(0.022, 0.028, 0.048) * (0.85 + 0.15 * N.y) + skyL * (skyFill + directCol * (ndl * voxelSun * ShaderFlags.w)) + torchCol * torchPool;
        vec3 surface = albedo * lighting;

        float maxTex = max(texCol.r, max(texCol.g, texCol.b));
        if (ShaderWorldA.x > 0.01 && vLen < 42.0) {
            vec3 hVec = normalize(sunDir + viewDir);
            float nh = max(dot(N, hVec), 0.0);
            float rough = 0.44;
            float a = rough * rough;
            float a2 = a * a;
            float denom = nh * nh * (a2 - 1.0) + 1.0;
            float ggx = a2 / (3.14159265 * denom * denom);
            float spec = min(ggx * 0.08, 1.5) * ndl * voxelSun * skyL * dayFac * (1.0 - rain) * ShaderWorldA.x;
            surface += directCol * spec;
        }
        if (ShaderWorldA.y > 0.01 && blockL > 0.85 && maxTex > 0.72) {
            surface += albedo * (smoothstep(0.72, 0.98, maxTex) * 0.85 * ShaderWorldA.y);
        }
        float peak = max(surface.r, max(surface.g, surface.b));
        if (peak > 0.88) {
            surface *= (0.88 + (peak - 0.88) / (1.0 + (peak - 0.88) * 1.35)) / peak;
        }
        color.rgb = pow(max(surface, vec3(0.0)), vec3(1.0 / 2.2));
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
