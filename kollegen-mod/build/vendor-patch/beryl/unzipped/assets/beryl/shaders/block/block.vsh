#version 450

#include "materials.glsl"

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
layout(location = 4) in vec3 Normal;

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

layout(binding = 2) uniform sampler2D Sampler2;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec3 normal;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec4 posLightSpace;
layout(location = 4) out vec3 fragPos;
layout(location = 5) out vec3 light;
layout(location = 6) out float vertexDistance;
layout(location = 7) out flat Material material;

const float LIGHT_CONV = 1.0 / 256.0;

#include "light.glsl"
#include "fog.glsl"
#include "shadow.glsl"

void main() {
    const vec4 position = vec4(Position, 1.0);
    gl_Position = MVP * vec4(Position, 1.0);

    posLightSpace = LightSpaceMat * position;
    posLightSpace.xyz = distortShadowClipPos(posLightSpace.xyz);

    fragPos = (ModelViewMat * position).xyz;

    vertexDistance = fog_distance(Position.xyz, 0);

//    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    vertexColor.rgb = pow(Color.rgb, vec3(2.2));
    vertexColor.a = Color.a;

    float lightY = UV2.y * LIGHT_CONV;
    lightY = lightY * lightY;

    float lightZ = max((lightY - 0.5) / (1.0 - 0.5), 0.0);
    lightY *= AmbientLightFactor;
    lightY = max(lightY - NightFactor, MinAmbientLight);

    float lightX = UV2.x * LIGHT_CONV;
    lightX = lightX * lightX * (1 - lightY);

    light = vec3(lightX * 2, lightY, lightZ);

    normal = (ModelViewMat * vec4(Normal, 0.0)).xyz;

    material = getMaterial(10); // Grass Block

    texCoord0 = UV0;
}