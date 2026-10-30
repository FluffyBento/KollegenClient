#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ShaderConfig {
    float Saturation;
    float Contrast;
    float Brightness;
    float Vignette;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 c = inTexel.rgb;

    // Saturation (Farbsättigung / Vibrance)
    float luma = dot(c, Gray);
    c = mix(vec3(luma), c, Saturation);

    // Contrast
    c = (c - 0.5) * Contrast + 0.5;

    // Brightness
    c = c * Brightness;

    // Vignette
    if (Vignette > 0.001) {
        vec2 uv = texCoord - 0.5;
        float dist = length(uv);
        float vig = clamp(1.0 - dist * Vignette * 1.4, 0.0, 1.0);
        c = c * vig;
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), inTexel.a);
}
