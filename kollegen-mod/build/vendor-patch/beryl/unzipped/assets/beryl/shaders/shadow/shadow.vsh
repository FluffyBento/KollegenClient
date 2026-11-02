#version 450

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    mat4 ModelViewMat;
    mat4 LightSpaceMat;
};

layout(binding = 1) uniform UBO{
    vec4 ColorModulator;
    vec4 FogColor;
    float FogStart;
    float FogEnd;
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

#include "light.glsl"
#include "shadow.glsl"

vec3 getVertexPosition() {
    const int encOffset = SectionOffsets[gl_InstanceIndex >> 2][gl_InstanceIndex & 3];
    const vec3 baseOffset = bitfieldExtract(ivec3(encOffset) >> ivec3(0, 16, 8), 0, 8);

    #ifdef COMPRESSED_VERTEX
        return fma(Position.xyz, POSITION_INV, ChunkOffset + baseOffset);
    #else
        return Position.xyz + ChunkOffset + baseOffset;
    #endif
}

layout(location = 1) out vec4 vertexColor;
layout(location = 0) out vec2 texCoord0;

void main() {
    // Compressed vertex
    const vec4 Color = unpackUnorm4x8(PackedColor);
    //    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
    const uint uv = Position.a;
    const ivec2 UV2 = ivec2(bitfieldExtract(uv, 0, 8), bitfieldExtract(uv, 8, 8));
    texCoord0 = UV0 * UV_INV;

    const vec4 position = vec4(getVertexPosition(), 1.0);

    gl_Position = LightSpaceMat * position;
    gl_Position.xyz = distortShadowClipPos(gl_Position.xyz);
    gl_Position.z = clamp(gl_Position.z, 0.0, 1.0);

    vertexColor = Color * ColorModulator;
}