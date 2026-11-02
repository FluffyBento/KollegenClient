#version 450

#include "materials.glsl"

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

layout (binding = 2) uniform UBO2 {
    ivec4 SectionOffsets[128];
    vec4 SectionFadeFactors[128];
};

layout(push_constant) uniform pushConstant {
    vec3 ChunkOffset;
};

layout(binding = 4) uniform sampler2D Sampler2;

#define COMPRESSED_VERTEX

#ifdef COMPRESSED_VERTEX
layout (location = 0) in ivec4 Position;
layout (location = 1) in uvec2 UV0;
layout (location = 2) in uint PackedColor;
layout (location = 3) in vec3 Normal;
layout (location = 4) in int BlockId;
#else
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
layout(location = 4) in vec3 Normal;
layout(location = 5) in int BlockId;
#endif

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
const vec3 POSITION_OFFSET = vec3(4.0);

vec3 getVertexPosition() {
    const int encOffset = SectionOffsets[gl_InstanceIndex >> 2][gl_InstanceIndex & 3];
    const vec3 baseOffset = bitfieldExtract(ivec3(encOffset) >> ivec3(0, 16, 8), 0, 8);

    #ifdef COMPRESSED_VERTEX
        return fma(Position.xyz, POSITION_INV, ChunkOffset + baseOffset);
    #else
        return Position.xyz + ChunkOffset + baseOffset;
    #endif
}

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec3 normal;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out vec4 posLightSpace;
layout(location = 5) out vec3 fragPos;
layout(location = 6) out vec3 light;
layout(location = 7) out flat Material material;

const float LIGHT_CONV = 1.0 / 256.0;

#include "light.glsl"
#include "shadow.glsl"

void main() {
    // Compressed vertex
    const vec4 Color = unpackUnorm4x8(PackedColor);
//    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
    const uint uv = Position.a;
    const ivec2 UV2 = ivec2(bitfieldExtract(uv, 0, 8), bitfieldExtract(uv, 8, 8));
    texCoord0 = UV0 * UV_INV;

    vec4 position = vec4(getVertexPosition(), 1.0);
    gl_Position = MVP * position;

    vec4 lightSpacePos = position;
    vec3 lightOffset = LightSpaceOffset * max(Normal.y, 0.0);
    lightSpacePos.xyz -= lightOffset;

    posLightSpace = LightSpaceMat * lightSpacePos;
    posLightSpace.z -= ShadowBias;
    posLightSpace.xyz = distortShadowClipPos(posLightSpace.xyz);

    fragPos = (ModelViewMat * position).xyz;

    vertexDistance = length(fragPos);

    vertexColor.rgb = pow(Color.rgb, vec3(2.2));
    vertexColor.a = Color.a;
//    vertexColor = Color * sample_lightmap(Sampler2, ivec2(0, UV2.y));

    float lightY = UV2.y * LIGHT_CONV;
    lightY = lightY * lightY;

    float lightZ = max((lightY - 0.5) / (1.0 - 0.5), 0.0);
    float lightYM = lightY * AmbientLightFactor;
    lightYM = max(lightYM - NightFactor, MinAmbientLight);

    float lightX = UV2.x * LIGHT_CONV;
    lightX = lightX * lightX * (1 - lightYM);
//    lightX = lightX * lightX;

    light = vec3(lightX * 2, lightYM, lightZ);

//    normal = vec4(Normal, 0.0);
    normal = (ModelViewMat * vec4(Normal, 0.0)).xyz;

    material = getMaterial(BlockId);
}