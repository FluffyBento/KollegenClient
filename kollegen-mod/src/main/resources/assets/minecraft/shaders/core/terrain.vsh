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
layout(location = 5) out vec3 worldPos;
layout(location = 6) out vec2 lightCoords;

void main() {
    vec3 basePos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    worldPos = basePos;
    lightCoords = vec2(UV2) / 240.0;

    float skyLight = clamp(lightCoords.y, 0.0, 1.0);
    float t = GameTime * 2200.0;
    vec3 animatedPos = basePos;

    if (skyLight > 0.35) {
        float waveStrength = (skyLight - 0.35) * 1.538;
        vec3 seed = basePos + vec3(CameraBlockPos);
        float w1 = sin(t * 1.8 + seed.x * 0.75 + seed.z * 0.5) * cos(t * 1.2 + seed.z * 0.8);
        float w2 = sin(t * 2.4 + seed.z * 1.2) * 0.5;
        animatedPos.x += (w1 + w2) * 0.035 * waveStrength;
        animatedPos.y += sin(t * 1.5 + seed.x * 0.9) * 0.02 * waveStrength;
        animatedPos.z += cos(t * 1.7 + seed.y * 0.8) * 0.025 * waveStrength;
    }

    gl_Position = ProjMat * ModelViewMat * vec4(animatedPos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(animatedPos);
    cylindricalVertexDistance = fog_cylindrical_distance(animatedPos);
    #ifndef OIT_ALPHA_ONLY
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    #else
    vertexColor = Color;
    #endif
    texCoord0 = UV0;

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(animatedPos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}
