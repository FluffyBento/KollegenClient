#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.299, 0.587, 0.114);

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 c = inTexel.rgb;

    // Saturation / Farbsättigung (lebendigere Farben)
    float luma = dot(c, Gray);
    c = mix(vec3(luma), c, 1.35);

    // Kontrast
    c = (c - 0.5) * 1.08 + 0.5;

    // Kino-Vignette
    vec2 uv = texCoord * 2.0 - 1.0;
    float dist = length(uv);
    float vig = smoothstep(1.3, 0.65, dist);
    c *= mix(1.0, vig, 0.20);

    fragColor = vec4(clamp(c, 0.0, 1.0), inTexel.a);
}
