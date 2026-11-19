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
    float ToneMap;
    float Gamma;
    float Pad1;
    float Pad2;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.2126, 0.7152, 0.0722);

float luminance(vec3 v) {
    return dot(v, Gray);
}

vec3 aces(vec3 x) {
    const float a = 2.1;
    const float b = 0.15;
    const float c = 2.03;
    const float d = 0.8;
    const float e = 0.25;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 reinhard_jodie(vec3 v) {
    float l = luminance(v);
    vec3 tv = v / (1.0 + v);
    return mix(v / (1.0 + l), tv, tv);
}

vec3 uncharted2Tonemap(vec3 x) {
    float A = 0.28;
    float B = 0.29;
    float C = 0.10;
    float D = 0.2;
    float E = 0.025;
    float F = 0.35;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 c = inTexel.rgb;

    if (Bloom > 0.01) {
        vec2 px = 1.0 / max(InSize, vec2(1.0, 1.0));
        vec3 bloomAcc = vec3(0.0);
        float wSum = 0.0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                if (x == 0 && y == 0) continue;
                vec2 offset = vec2(float(x), float(y)) * px * 1.5;
                vec3 s = texture(InSampler, texCoord + offset).rgb;
                float sum = s.r + s.g + s.b;
                if (sum > 2.5) {
                    float weight = 1.0 / (1.0 + float(x * x + y * y));
                    bloomAcc += (s - 0.83) * weight;
                    wSum += weight;
                }
            }
        }
        if (wSum > 0.0) {
            c += (bloomAcc / wSum) * Bloom * 0.35;
        }
    }

    if (abs(Exposure - 1.0) > 0.001) {
        c *= Exposure;
    }

    if (abs(Warmth - 1.0) > 0.001) {
        if (Warmth > 1.0) {
            float t = Warmth - 1.0;
            c.r += c.r * t * 0.12;
            c.g += c.g * t * 0.04;
            c.b -= c.b * t * 0.08;
        } else {
            float t = 1.0 - Warmth;
            c.r -= c.r * t * 0.08;
            c.b += c.b * t * 0.12;
        }
    }

    float luma = luminance(c);

    if (abs(Vibrance - 1.0) > 0.001) {
        float maxC = max(c.r, max(c.g, c.b));
        float minC = min(c.r, min(c.g, c.b));
        float sat = maxC - minC;
        float factor = 1.0 + (Vibrance - 1.0) * (1.0 - sat);
        c = mix(vec3(luma), c, max(0.0, factor));
        luma = luminance(c);
    }

    if (abs(Saturation - 1.0) > 0.001) {
        c = mix(vec3(luma), c, Saturation);
    }

    if (abs(Contrast - 1.0) > 0.001) {
        c = (c - 0.5) * Contrast + 0.5;
    }

    if (abs(Brightness - 1.0) > 0.001) {
        c *= Brightness;
    }

    c = max(vec3(0.0), c);

    if (ToneMap > 0.5) {
        if (ToneMap < 1.5) {
            c = aces(c);
        } else if (ToneMap < 2.5) {
            c = reinhard_jodie(c);
        } else {
            c = clamp(uncharted2Tonemap(c), 0.0, 1.0);
        }
    }

    if (abs(Gamma - 1.0) > 0.001 && Gamma > 0.1) {
        c = pow(clamp(c, 0.0, 1.0), vec3(1.0 / Gamma));
    }

    if (Vignette > 0.001) {
        vec2 uv = texCoord - 0.5;
        float dist = length(uv);
        float vig = clamp(1.0 - dist * dist * Vignette * 1.5, 0.0, 1.0);
        c *= vig;
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}