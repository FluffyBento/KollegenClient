#version 450

#include "fog.glsl"
#include "materials.glsl"

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
};

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
};

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator;
}