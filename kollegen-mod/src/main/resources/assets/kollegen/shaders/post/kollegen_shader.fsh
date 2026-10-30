#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 c = inTexel.rgb;

    // Sanfte Farbsättigung (Digital Vibrance)
    float luma = dot(c, Gray);
    c = mix(vec3(luma), c, 1.25);

    // Feiner Kontrast
    c = (c - 0.5) * 1.04 + 0.5;

    fragColor = vec4(clamp(c, 0.0, 1.0), inTexel.a);
}
