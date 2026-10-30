#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D PreviousSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 prev = texture(PreviousSampler, texCoord);

    // Echte temporale Bewegungsunschärfe (Previous Frame Akkumulation)
    if (prev.a < 0.1) {
        fragColor = current;
    } else {
        fragColor = mix(current, prev, 0.40);
    }
}
