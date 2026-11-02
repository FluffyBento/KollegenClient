#version 450

#include "fog.glsl"
#include "materials.glsl"

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 vertexColor;

void main() {
    const vec4 position = vec4(Position, 1.0);
    gl_Position = MVP * position;

    vertexColor = Color;
}