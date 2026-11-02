#version 450

#include "fog.glsl"

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    vec3 LightDir;
    vec3 LightColor;
    vec4 SkyColor;
    vec4 FogColor;
    vec3 UpVector;
    float FogStart;
    float FogEnd;
    float FogCloudsEnd;
    float LightVisibility;
    float NightFactor;
};

vec4 getMieScattering(const vec3 fragDir, float RoUp, const float SoUp) {
    vec4 color;

    float FdotL = max(dot(fragDir, LightDir), 0.0);

    // Mie scattering approx
    color.rgb = LightColor * min((1.0 / ((1 - FdotL) * 20.0 + 1.0)), 0.8);

    return color;
}

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 fragPos;
layout(location = 2) in vec3 normal;
layout(location = 3) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = vertexColor;

    vec3 fragDir = normalize(fragPos);
    float RdotUp = dot(fragDir, UpVector);
    RdotUp = max(RdotUp, 0.0);

    float LdotUp = max(dot(UpVector, LightDir), 0.0);
    float NdotDown = max(dot(-UpVector, normal), 0.0);
    float NdotL = max(dot(normal, LightDir), 0.0);
    vec3 H = normalize(-fragDir + LightDir);
    float NdotH = max(dot(normal, H), 0.0);

    color.a *= 1.0 - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    color.rgb = vec3(NdotH * 0.4 + 0.1) * LightColor;
    color.rgb += (0.1 + 0.2 * (1 - NdotDown)) * getMieScattering(fragDir, RdotUp, LdotUp).rgb;
    color.rgb *= ColorModulator.rgb;
    fragColor = color;
}
