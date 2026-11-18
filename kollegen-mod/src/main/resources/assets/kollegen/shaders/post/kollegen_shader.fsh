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
    float Bloom;
    float Exposure;
    float Warmth;
    float Vibrance;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 c = inTexel.rgb;

    if (Bloom > 0.001) {
        vec2 px = 1.0 / InSize;
        vec3 bloomAcc = vec3(0.0);
        float wSum = 0.0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                if (x == 0 && y == 0) continue;
                vec2 offset = vec2(float(x), float(y)) * px * 2.0;
                vec3 s = texture(InSampler, texCoord + offset).rgb;
                float l = dot(s, Gray);
                if (l > 0.65) {
                    float weight = 1.0 / (1.0 + float(x * x + y * y));
                    bloomAcc += (s - 0.65) * weight;
                    wSum += weight;
                }
            }
        }
        if (wSum > 0.0) {
            c += (bloomAcc / wSum) * Bloom * 1.5;
        }
    }

    if (abs(Exposure - 1.0) > 0.001) {
        c = c * Exposure;
    }

    if (abs(Warmth - 1.0) > 0.001) {
        if (Warmth > 1.0) {
            float t = Warmth - 1.0;
            c.r += c.r * t * 0.18;
            c.g += c.g * t * 0.08;
            c.b -= c.b * t * 0.12;
        } else {
            float t = 1.0 - Warmth;
            c.r -= c.r * t * 0.12;
            c.b += c.b * t * 0.18;
        }
    }

    float luma = dot(c, Gray);

    if (abs(Vibrance - 1.0) > 0.001) {
        float maxC = max(c.r, max(c.g, c.b));
        float minC = min(c.r, min(c.g, c.b));
        float sat = maxC - minC;
        float factor = 1.0 + (Vibrance - 1.0) * (1.0 - sat);
        c = mix(vec3(luma), c, max(0.0, factor));
        luma = dot(c, Gray);
    }

    if (abs(Saturation - 1.0) > 0.001) {
        c = mix(vec3(luma), c, Saturation);
    }

    if (abs(Contrast - 1.0) > 0.001) {
        c = (c - 0.5) * Contrast + 0.5;
    }

    if (abs(Brightness - 1.0) > 0.001) {
        c = c * Brightness;
    }

    if (Vignette > 0.001) {
        vec2 uv = texCoord - 0.5;
        float dist = length(uv);
        float vig = clamp(1.0 - dist * Vignette * 1.4, 0.0, 1.0);
        c = c * vig;
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), inTexel.a);
}
