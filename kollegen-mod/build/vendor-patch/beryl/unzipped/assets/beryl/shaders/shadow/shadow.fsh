#version 450

layout(binding = 3) uniform sampler2D Sampler0;

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    vec4 FogColor;
    float FogStart;
    float FogEnd;
    float ShadowDistortion;
};

layout(location = 1) in vec4 vertexColor;
//layout(location = 2) in vec4 normal;
layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }

    fragColor = color;
}