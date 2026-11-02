#version 450

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
};
layout(binding = 1) uniform sampler2D srcTexture;

layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec2 srcTexelSize;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);
    srcTexelSize = 1.0 / textureSize(srcTexture, 0);

    texCoord = UV0;
}
