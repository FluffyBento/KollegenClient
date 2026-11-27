#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
layout(location = 5) out vec3 localPos;
layout(location = 6) out vec4 terrainMeta;
layout(location = 7) out vec3 worldBlockPos;
layout(location = 8) flat out float matId;

void main() {
    vec3 wPos = Position + vec3(ChunkPosition);
    vec3 baseCamPos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    ivec2 cleanUV2 = ivec2(UV2.x & 255, UV2.y & 255);
    int isLeaves = (UV2.x >> 8) & 1;
    int isBush = (UV2.x >> 9) & 1;
    int isSubmerged = (UV2.x >> 10) & 1;
    int matClass = (UV2.x >> 11) & 15;
    vec2 lightUV = clamp(vec2(cleanUV2) / 240.0, vec2(0.0), vec2(1.0));
    float cMax = max(Color.r, max(Color.g, Color.b));
    float cMin = min(Color.r, min(Color.g, Color.b));
    float blueDominance = Color.b - max(Color.r, Color.g * 0.88);
    float foliageMeta = isSubmerged == 1
        ? -1.0
        : ((isLeaves == 1 || isBush == 1) ? 1.0 : (step(0.004, cMax - cMin) * (1.0 - step(0.08, blueDominance)) * 0.5));
    float waterMask = 0.0;
    #if defined(OIT_ACCUMULATE) || defined(OIT_ALPHA_ONLY) || defined(OIT_DEPTH_BOUNDS) || defined(OIT_TRANSMITTANCE)
    waterMask = clamp((blueDominance - 0.015) * 12.0, 0.0, 1.0) * step(0.14, Color.b);
    #elif defined(ALPHA_CUTOUT)
    if (ALPHA_CUTOUT < 0.25) {
        waterMask = clamp((blueDominance - 0.015) * 12.0, 0.0, 1.0) * step(0.14, Color.b);
    }
    #endif
    if (waterMask > 0.1) {
        isLeaves = 0;
        isBush = 0;
        isSubmerged = 0;
        matClass = 0;
        foliageMeta = 0.0;
    }

    vec3 animOffset = vec3(0.0);
    if (ShaderFlags.x > 0.5) {
        float t = ShaderWorldB.x;
        if (ShaderFlags.y > 0.001) {
            float fx = fract(Position.x + 0.0005);
            float fz = fract(Position.z + 0.0005);
            bool insideBlock = (fx > 0.015 && fx < 0.985) || (fz > 0.015 && fz < 0.985);
            bool allowSway = (isLeaves == 1) || (isBush == 1);
            #ifdef ALPHA_CUTOUT
            if (ALPHA_CUTOUT > 0.25 && foliageMeta > 0.25 && insideBlock) {
                allowSway = true;
            }
            #endif
            if (allowSway) {
                float skyScale = mix(0.45, 1.0, smoothstep(0.05, 0.78, lightUV.y));
                vec2 pMod = mod(wPos.xz, 1024.0) * (6.2831853 / 1024.0);
                float tW = t * (6.2831853 / 3600.0);
                float gA = sin(dot(pMod, vec2(9.0, 3.0)) + sin(dot(pMod, vec2(-1.0, 3.0))) * 1.4 - tW * 172.0);
                float gB = sin(dot(pMod, vec2(4.0, 1.0)) - tW * 61.0 + 1.3);
                float gRaw = clamp(0.45 + 0.35 * gA + 0.30 * gB, 0.0, 1.0);
                float gust = gRaw * gRaw * (3.0 - 2.0 * gRaw);
                float storm = 1.0 + 0.55 * ShaderWorldB.y;
                vec2 windDir = vec2(0.9487, 0.3162);
                vec3 p3 = mod(wPos, 1024.0);
                float ph1 = dot(p3, vec3(98.0, 31.0, 61.0) * (6.2831853 / 1024.0));
                float ph2 = dot(p3, vec3(57.0, 13.0, 89.0) * (6.2831853 / 1024.0));
                float lean = (0.30 + 0.70 * gust) * (0.65 + 0.35 * sin(tW * 974.0 + ph1));
                float side = (isLeaves == 1)
                    ? 0.45 * sin(tW * 860.0 + ph2) * (0.5 + 0.5 * gust)
                    : 0.30 * sin(tW * 1531.0 + ph2) * (0.5 + 0.5 * gust);
                vec2 xzDir = windDir * lean + vec2(-windDir.y, windDir.x) * side;
                float amount = (isLeaves == 1 && !insideBlock) ? 0.048 : 0.068;
                vec3 ofs = vec3(xzDir.x, 0.0, xzDir.y) * amount;
                if (isLeaves == 1) {
                    vec3 q = p3 * (6.2831853 / 1024.0);
                    vec3 fl = vec3(
                        sin(tW * 1900.0 + dot(q, vec3(205.0, 163.0, 131.0))),
                        sin(tW * 1600.0 + dot(q, vec3(149.0, 211.0, 97.0))) * 0.55,
                        sin(tW * 2200.0 + dot(q, vec3(113.0, 89.0, 227.0)))
                    );
                    ofs += fl * (0.022 * (0.45 + 0.55 * gust));
                }
                animOffset += ofs * (ShaderFlags.y * skyScale * storm);
            }
        }
        if (waterMask > 0.1 && ShaderFlags.z > 0.001) {
            float fy = fract(Position.y + 0.001);
            float topPin = smoothstep(0.45, 0.88, fy) * (1.0 - step(0.97, fy));
            float rippleWaveY = 0.0;
            for (int i = 0; i < 16; i++) {
                vec4 rp = WaterRipples[i];
                if (rp.w > 0.01) {
                    float rAge = mod(rp.w, 4.0);
                    float rAmp = (rp.w - rAge) * 0.0125;
                    vec2 diffXZ = baseCamPos.xz - rp.xz;
                    float distXZ = length(diffXZ);
                    float maxR = 3.2 + rAmp * 4.5;
                    if (distXZ < maxR && abs(baseCamPos.y - rp.y) < 1.6) {
                        float speed = 2.4 + rAmp * 1.15;
                        float radius = 0.14 + rAge * speed;
                        float deltaR = distXZ - radius;
                        float lifeFade = smoothstep(3.2, 0.25, rAge) * smoothstep(0.0, 0.06, rAge);
                        float width = 1.6 / (0.85 + rAmp * 0.45);
                        float ringEnv = exp(-deltaR * deltaR * width) * lifeFade;
                        float freq = max(6.8, 12.5 - rAmp * 2.6);
                        rippleWaveY += sin(deltaR * freq) * ringEnv * rAmp * 0.022;
                    }
                }
            }
            animOffset.y += rippleWaveY * min(1.4, ShaderFlags.z) * waterMask * topPin;
        }
    }

    vec3 pos = baseCamPos + animOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    #ifndef OIT_ALPHA_ONLY
    if (ShaderFlags.x > 0.5) {
        vertexColor = Color;
    } else {
        vertexColor = Color * sample_lightmap(Sampler2, cleanUV2);
    }
    #else
    vertexColor = Color;
    #endif
    texCoord0 = UV0;
    localPos = pos;
    terrainMeta = vec4(lightUV.x, lightUV.y, waterMask, foliageMeta);
    worldBlockPos = wPos + animOffset;
    matId = float(matClass);

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}
