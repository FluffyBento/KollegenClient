#version 450

layout(binding = 1) uniform sampler2D Sampler0;
layout(binding = 2) uniform sampler2D DepthBuffer;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord0);
    gl_FragDepth = texture(DepthBuffer, texCoord0).r;
}