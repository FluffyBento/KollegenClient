#version 450

layout(location = 0) in vec3 Position;

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    mat4 ModelViewMat;
};

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec3 fragPos;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);
    fragPos = (ModelViewMat * vec4(Position, 1.0)).xyz;

    vertexDistance = length(fragPos.xyz);
}