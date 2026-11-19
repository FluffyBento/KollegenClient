#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec3 worldPos;
layout(location = 5) in vec2 lightCoords;

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
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
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
    color.rgb *= naturalLighting;

    float torch = pow(lightCoords.x, 2.2);
    vec3 torchGlow = vec3(1.0, 0.65, 0.32) * (torch * 0.65);
    color.rgb += color.rgb * torchGlow;

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
