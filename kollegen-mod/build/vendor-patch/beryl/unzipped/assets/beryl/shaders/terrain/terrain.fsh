#version 450

#include "materials.glsl"

layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 5) uniform sampler2DShadow ShadowMap;

#ifdef COLORED_SHADOWS
layout(binding = 6) uniform sampler2DShadow ShadowMap1;
layout(binding = 7) uniform sampler2D ShadowMapColor;
#endif

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
    float FogEnvStart;
    float FogEnvEnd;
    vec3 LightDir;
    vec3 LightColor;
    vec3 AmbientLight;
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

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) in vec3 normal;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec4 posLightSpace;
layout(location = 5) in vec3 fragPos;
layout(location = 6) in vec3 light;
layout(location = 7) in flat Material material;

layout(location = 0) out vec4 fragColor;

#include "fog2.glsl"
#include "lighting.glsl"
#include "shadow.glsl"
#include "shadowSampling.glsl"

// Returns a random number based on a vec3 and an int.
float random(vec3 seed, int i){
    vec4 seed4 = vec4(seed, i);
    float dot_product = dot(seed4, vec4(12.9898,78.233, 45.164, 94.673));
    return fract(sin(dot_product) * 43758.5453);
}

//float ShadowCalculation(sampler2DShadow shadowMap, vec4 fragPosLightSpace, float texelSize, float bias)
//{
//    //    fragPosLightSpace.xyz = distortShadowClipPos(fragPosLightSpace.xyz);
//
//    // perform perspective divide
//    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;
//
//    float distX = abs(projCoords.x);
//    float distY = abs(projCoords.y);
//    float dist = max(distX, distY);
//
//    // transform to [0,1] range
//    projCoords.y = -projCoords.y;
//    projCoords.xy = projCoords.xy * 0.5 + 0.5;
//
//    if (projCoords.x < 0.0 || projCoords.x > 1.0
//    || projCoords.y < 0.0 ||  projCoords.y > 1.0
//    || projCoords.z < 0.0 || projCoords.z > 1.0)
//    {
//        return 1.0;
//    }
//
//    // PCF
//    float currentDepth = projCoords.z;
//    float shadow = 0.0;
//    //TODO texture size uniform
//    float pcfDepth = 0.0;
//
//    bias += 0.002 * dist * dist;
//
//    // Poisson fixed disc 4 taps
////    vec2 texelSizeM = 1.3 * vec2(texelSize);
////    for (int i = 0 ; i < 4; i++){
//////        int index = int(4.0 * random(gl_FragCoord.xyy, i)) & 3;
////        int index = i;
////        vec3 pos = vec3(projCoords.xy + poissonDisk[index] * texelSizeM, currentDepth);
////        shadow += texture(shadowMap, pos) * 0.25;
////
//////        pcfDepth = texture(shadowMap, projCoords.xy + poissonDisk[i] * texelSizeM).r;
//////        shadow += currentDepth - bias > pcfDepth ? 0.0 : 0.2;
////        //        shadow += currentDepth - bias < pcfDepth ? 0.0 : 0.2;
////    }
//
//    currentDepth -= bias;
//
//    //    Poisson disc randomly rotated
//    float randomAngle = gl_FragCoord.x * (11.0 * PI) + gl_FragCoord.y * (2.0 * PI);
//    vec2 randomBase = vec2(cos(randomAngle), sin(randomAngle));
//    mat2 R = mat2(randomBase.x, randomBase.y, -randomBase.y, randomBase.x);
//
//    vec2 texelSizeM = 1.0 * fragPosLightSpace.w * vec2(texelSize);
//    for (int i = 0 ; i < 4; i++){
//        vec3 pos = vec3(projCoords.xy + R * poissonDisk[i] * texelSizeM, currentDepth);
//        shadow += texture(shadowMap, pos);
////        pos = vec3(projCoords.xy + R * -poissonDisk[i] * texelSizeM, currentDepth);
////        shadow += texture(shadowMap, pos);
//    }
//    shadow *= (1.0 / (4 * 1));
//
//
//    // fade on shadow map borders for smoother transition
//    float intensity = 1 - clamp((dist - 0.9) / (1.0 - 0.9), 0.0, 1.0);
//    shadow = 1 - shadow;
//    shadow *= intensity;
//    shadow = 1 - shadow;
//
//    return shadow;
//}

void main() {
    vec4 texColor = texture(Sampler0, texCoord0) * vertexColor;

    if (texColor.a < 0.5) {
        discard;
    }

    vec4 color;

    vec3 viewDir = normalize(-fragPos);
    vec3 N = normal;
    vec3 V = viewDir;

    float bias = (1.0 - max(dot(N, LightDir), 0.0)) * 0.001;

    #ifdef COLORED_SHADOWS
        vec3 shadow = computeShadowColor(ShadowMap, ShadowMap1, ShadowMapColor, posLightSpace, ShadowTexelSize, bias);
    #else
        float shadow = ShadowCalculation(ShadowMap, posLightSpace, ShadowTexelSize, bias);
    #endif


    vec3 F0 = material.F0;
    float roughness = material.roughness;
    float metallic = material.metallic;

    vec3 albedo = texColor.rgb;

    vec3 radiance = LightColor * 0.6;

    // Gradually reduce lighting when its low on the horizon
    radiance *= saturate((dot(UpVector, LightDir) - 0.04) * 50); // 1 / 0.02

//    vec3 color1 = LightingGGX(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic);
//    vec3 color1 = LightingSphereGGX(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic);
    vec3 color1 = LightingSphereGGX2(viewDir, normal, LightDir, albedo, radiance, F0, roughness, metallic, material.lightingType);

    color.rgb = (color1 * shadow * light.z) + albedo * (light.y * AmbientLight + light.x * vec3(1.0, 0.7, 0.5));
    float NdotU = max(dot(N, UpVector), 0.0);
    NdotU = material.lightingType == 0 ? NdotU : 0.8;
    color.rgb += radiance * 0.1 * NdotU * albedo * shadow * light.z;

    if (material.lightEmission > 0.0) {
        color.rgb += max((texColor.rgb - vec3(0.05)), vec3(0.0)) * material.lightEmission;
    }

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

//    color.rgb += volLighting(vec3(0.0), fragPos, normalize(fragPos));

    fragColor = color;
}