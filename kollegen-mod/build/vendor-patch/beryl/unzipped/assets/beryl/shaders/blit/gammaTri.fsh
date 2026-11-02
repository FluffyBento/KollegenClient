#version 450

layout(binding = 0) uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

#define Contrast 2.0
#define Brightness 0.8

float saturate(float f) {
    return clamp(f, 0.0, 1.0);
}

vec3 saturate(vec3 vec) {
    return vec3(saturate(vec.x), saturate(vec.y), saturate(vec.z));
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    color.rgb = pow(color.rgb, vec3(1.0 / 2.2));

//    color.a = 1.0f;
    fragColor = color;
}