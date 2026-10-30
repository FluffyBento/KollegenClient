#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec4 c = texture(InSampler, texCoord) * 0.44;
    c += texture(InSampler, texCoord + oneTexel * vec2( 1.5,  0.75)) * 0.14;
    c += texture(InSampler, texCoord - oneTexel * vec2( 1.5,  0.75)) * 0.14;
    c += texture(InSampler, texCoord + oneTexel * vec2( 3.0,  1.5)) * 0.14;
    c += texture(InSampler, texCoord - oneTexel * vec2( 3.0,  1.5)) * 0.14;
    fragColor = c;
}
