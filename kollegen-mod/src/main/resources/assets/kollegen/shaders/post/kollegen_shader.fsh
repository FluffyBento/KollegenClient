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
    float SunRays;
    float Atmosphere;
    float ShadowTint;
};

layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.2126, 0.7152, 0.0722);

vec3 applyAces(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 applyFilmic(vec3 x) {
    vec3 v = max(vec3(0.0), x - 0.004);
    return (v * (6.2 * v + 0.5)) / (v * (6.2 * v + 1.7) + 0.06);
}

vec3 applyDreamy(vec3 x) {
    vec3 v = x / (1.0 + x * 0.65);
    return pow(clamp(v, 0.0, 1.0), vec3(0.92));
}

vec3 applyVibrant(vec3 x) {
    vec3 v = pow(clamp(x, 0.0, 2.0), vec3(1.12));
    return (v / (1.0 + v * 0.45)) * 1.45;
}

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 c = inTexel.rgb;

    if (Bloom > 0.001) {
        vec2 px = 1.0 / max(InSize, vec2(1.0, 1.0));
        vec3 bloomAcc = vec3(0.0);
        float wSum = 0.0;
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                if (x == 0 && y == 0) continue;
                vec2 offset = vec2(float(x), float(y)) * px * 2.2;
                vec3 s = texture(InSampler, texCoord + offset).rgb;
                float l = dot(s, Gray);
                if (l > 0.60) {
                    float distSq = float(x * x + y * y);
                    float weight = exp(-distSq * 0.12);
                    bloomAcc += (s - 0.60) * weight;
                    wSum += weight;
                }
            }
        }
        if (wSum > 0.0) {
            c += (bloomAcc / wSum) * Bloom * 1.8;
        }
    }

    if (SunRays > 0.001) {
        vec2 sunPos = vec2(0.5, 0.15);
        vec2 delta = (sunPos - texCoord) / 8.0;
        vec2 curCoord = texCoord;
        vec3 rayColor = vec3(0.0);
        float decay = 1.0;
        for (int i = 0; i < 8; i++) {
            curCoord += delta;
            vec3 s = texture(InSampler, clamp(curCoord, 0.0, 1.0)).rgb;
            float lum = dot(s, Gray);
            if (lum > 0.70) {
                rayColor += s * decay;
            }
            decay *= 0.82;
        }
        c += rayColor * SunRays * 0.18 * vec3(1.15, 0.98, 0.75);
    }

    if (Atmosphere > 0.001) {
        float horizon = smoothstep(0.7, 0.25, texCoord.y);
        vec3 atmoColor = vec3(0.95, 0.85, 0.72) * Atmosphere * 0.22;
        c = mix(c, c + atmoColor, horizon);
    }

    if (abs(Exposure - 1.0) > 0.001) {
        c = c * Exposure;
    }

    if (abs(Warmth - 1.0) > 0.001) {
        if (Warmth > 1.0) {
            float t = Warmth - 1.0;
            c.r += c.r * t * 0.22;
            c.g += c.g * t * 0.10;
            c.b -= c.b * t * 0.16;
        } else {
            float t = 1.0 - Warmth;
            c.r -= c.r * t * 0.15;
            c.b += c.b * t * 0.22;
        }
    }

    float luma = dot(c, Gray);

    if (ShadowTint > 0.001) {
        float shadowFactor = clamp(1.0 - luma * 1.4, 0.0, 1.0) * ShadowTint;
        vec3 coolShadow = vec3(0.82, 0.90, 1.12);
        c = mix(c, c * coolShadow, shadowFactor * 0.45);
        luma = dot(c, Gray);
    }

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

    if (ToneMap > 0.5) {
        if (ToneMap < 1.5) {
            c = applyFilmic(c);
        } else if (ToneMap < 2.5) {
            c = applyAces(c);
        } else if (ToneMap < 3.5) {
            c = applyDreamy(c);
        } else {
            c = applyVibrant(c);
        }
    }

    if (Vignette > 0.001) {
        vec2 uv = texCoord - 0.5;
        float dist = length(uv);
        float vig = clamp(1.0 - dist * Vignette * 1.4, 0.0, 1.0);
        c = c * vig;
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}