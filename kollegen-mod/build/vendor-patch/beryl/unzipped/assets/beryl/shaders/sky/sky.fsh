#version 450

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    vec3 LightDir;
    vec3 LightColor;
    vec4 SkyColor;
    vec4 FogColor;
    vec3 UpVector;
    float FogStart;
    float FogEnd;
    float NightFactor;
    float LightVisibility;
};

layout(location = 0) in float vertexDistance;
layout(location = 1) in vec3 fragPos;

layout(location = 0) out vec4 fragColor;

#include "lighting.glsl"

vec3 getSunColor(const float RdotL) {
    float f = 0.1 / (5000 * (0.9994 - RdotL) + 0.0001) - 0.005;
    f = clamp(f, 0.0, 1.0);
    float m = RdotL > 0.9994 ? 1.0 : 0.0;
    m += f;
    m *= LightVisibility;

    vec3 color = mix(vec3(0.0), LightColor * 5.0, m);
    return color;
}

vec3 getMoonColor(const float RdotL) {
    float f = 0.05 / (5000 * (0.9986 - RdotL) + 0.0001) - 0.005;
    f = clamp(f, 0.0, 1.0);
    float m = RdotL > 0.9986 ? 1.0 : 0.0;
    m += f;
    m *= LightVisibility;

    vec3 color = mix(vec3(0.0), LightColor * 3.0, m);
    return color;

    return color;
}

void main() {
    //TODO precompute and optimize
    vec3 fragDir = normalize(fragPos);
    float RoUp = dot(fragDir, UpVector);
    RoUp = max(RoUp, 0.0);

    float SoUp = max(dot(UpVector, LightDir), 0.0);

    float RdotL = max(dot(fragDir, LightDir), 0.0);

    fragColor = getSkyColor(RdotL, RoUp, SoUp);

    #ifndef TEXTURED_SUN
//        fragColor.rgb += NightFactor == 1.0  ? getMoonColor(RdotL) : getSunColor(RdotL);
        fragColor.rgb += NightFactor == 1.0  ? vec3(0.0) : getSunColor(RdotL);
    #endif
}
