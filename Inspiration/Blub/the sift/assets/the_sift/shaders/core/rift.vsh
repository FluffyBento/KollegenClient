#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 coord;
out vec4 material;
out vec3 viewPosition;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPosition = view.xyz;
    coord = UV0;
    material = Color;
}
