#version 450

layout(location = 0) out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, -(gl_VertexIndex & 2) + 1);
    vec4 pos = vec4(uv * vec2(2, -2) + vec2(-1, +1), 0, 1);

    gl_Position = pos;
    texCoord = uv;
}