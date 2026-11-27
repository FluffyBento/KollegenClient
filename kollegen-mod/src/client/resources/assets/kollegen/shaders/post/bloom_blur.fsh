#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurParams {
    vec4 DirAndStride;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 px = 1.0 / max(InSize, vec2(1.0));
    vec2 stepUv = DirAndStride.xy * DirAndStride.z * px;
    vec3 acc = texture(InSampler, texCoord).rgb * 0.227027;
    acc += (texture(InSampler, texCoord + stepUv * 1.0).rgb + texture(InSampler, texCoord - stepUv * 1.0).rgb) * 0.194595;
    acc += (texture(InSampler, texCoord + stepUv * 2.0).rgb + texture(InSampler, texCoord - stepUv * 2.0).rgb) * 0.121622;
    acc += (texture(InSampler, texCoord + stepUv * 3.0).rgb + texture(InSampler, texCoord - stepUv * 3.0).rgb) * 0.054054;
    acc += (texture(InSampler, texCoord + stepUv * 4.0).rgb + texture(InSampler, texCoord - stepUv * 4.0).rgb) * 0.016216;
    fragColor = vec4(acc, 1.0);
}
