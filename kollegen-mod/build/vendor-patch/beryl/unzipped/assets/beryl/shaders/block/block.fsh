#version 450

//#include "fog.glsl"
#include "materials.glsl"

layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 4) uniform sampler2DShadow ShadowMap;

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    mat4 ModelViewMat;
    mat4 LightSpaceMat;
    vec3 LightSpaceOffset;
};

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    vec4 SkyColor;
    vec4 FogColor;
    vec3 UpVector;
    float FogStart;
    float FogEnd;
    vec3 LightDir;
    vec3 LightColor;
    vec3 ViewPos;
    float LightIntensity;
    float LightVisibility;
    float NightFactor;
    float AmbientLightFactor;
    float MinAmbientLight;
    float FogFactor;
    float ShadowTexelSize;
    float ShadowBias;
    float ShadowDistortion;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 posLightSpace;
layout(location = 4) in vec3 fragPos;
layout(location = 5) in vec3 light;
layout(location = 6) in float vertexDistance;
layout(location = 7) in flat Material material;

layout(location = 0) out vec4 fragColor;

#include "fog2.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "shadowSampling.glsl"

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.1) {
        discard;
    }
    texColor *= vertexColor * ColorModulator;

    vec4 color;

    vec3 viewDir = normalize(-fragPos);
    vec3 N = normal;
    vec3 V = viewDir;

    float bias = (1.0 - max(dot(N, LightDir), 0.0)) * 0.001;
    float shadow = ShadowCalculation(ShadowMap, posLightSpace, ShadowTexelSize, bias);

    vec3 F0 = material.F0;
    float roughness = material.roughness;
    float metallic = material.metallic;

    vec3 albedo = texColor.rgb;

    vec3 radiance = LightColor;

    vec3 color1 = LightingSphereGGX(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic);

    color.rgb = (color1 * shadow * light.z) + albedo * (0.3 * light.y + light.x * vec3(1.0, 0.7, 0.5));
    color.a = texColor.a;

    vec3 fragDir = normalize(fragPos);
    float RoUp = dot(fragDir, UpVector);
    RoUp = max(RoUp, 0.0);

    float SoUp = max(dot(UpVector, LightDir), 0.0);
    float RdotL = max(dot(fragDir, LightDir), 0.0);
    vec4 fogColor = vec4(getSkyColor(RdotL, RoUp, SoUp).rgb, 1.0);

    atmospheric_fog(color, fogColor.rgb, fragDir, vertexDistance, FogFactor);

    // Render distance Fog
    if (vertexDistance > 0.8 * FogEnd) {
        color = fog(color, vertexDistance, FogEnd, fogColor);
    }

    fragColor = color;
}