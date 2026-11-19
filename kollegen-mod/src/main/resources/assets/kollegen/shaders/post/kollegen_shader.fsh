#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

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
    float GodRays;
    float SSAO;

    float DepthFog;
    float WaterShimmer;
    float GameTime;
    float SunVisibility;

    float SunScreenX;
    float SunScreenY;
    float FarPlane;
    float Pad;
};

layout(location = 0) in vec2 texCoord;
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
    float rawDepth = texture(DepthSampler, texCoord).r;
    bool isSky = rawDepth >= 0.9999;

    vec2 px = 1.0 / max(InSize, vec2(1.0, 1.0));

    if (!isSky && SSAO > 0.01) {
        float occ = 0.0;
        vec2 off[6] = vec2[](
            vec2(1.5, 0.5), vec2(-1.5, -0.5),
            vec2(0.5, 1.5), vec2(-0.5, -1.5),
            vec2(1.2, -1.2), vec2(-1.2, 1.2)
        );
        for (int i = 0; i < 6; i++) {
            float sd = texture(DepthSampler, texCoord + off[i] * px * 2.2).r;
            float diff = rawDepth - sd;
            if (diff > 0.00008 && diff < 0.008) {
                occ += 1.0;
            }
        }
        float ao = 1.0 - (occ / 6.0) * SSAO * 0.65;
        c *= clamp(ao, 0.28, 1.0);
    }

    if (!isSky && DepthFog > 0.01) {
        float zNear = 0.05;
        float zFar = max(FarPlane, 64.0);
        float linearDist = (zNear * zFar) / (zFar - rawDepth * (zFar - zNear));
        float fogDensity = DepthFog * 0.006;
        float fogFactor = 1.0 - exp(-pow(linearDist * fogDensity, 1.35));
        fogFactor = clamp(fogFactor, 0.0, 0.90);

        vec2 sunPos = vec2(SunScreenX, SunScreenY);
        float distToSun = length(texCoord - sunPos);
        float sunScattering = pow(max(1.0 - distToSun * 1.4, 0.0), 3.0) * SunVisibility;

        vec3 skyHaze = mix(vec3(0.55, 0.68, 0.88), vec3(1.15, 0.98, 0.76), sunScattering * 0.65);
        c = mix(c, skyHaze, fogFactor);
    }

    if (GodRays > 0.01 && SunVisibility > 0.05) {
        vec2 sunPos = vec2(SunScreenX, SunScreenY);
        vec2 delta = (sunPos - texCoord) / 8.0;
        vec2 cur = texCoord;
        float godRay = 0.0;
        float decay = 1.0;
        for (int i = 0; i < 8; i++) {
            cur += delta;
            float d = texture(DepthSampler, clamp(cur, 0.0, 1.0)).r;
            if (d >= 0.9998) {
                godRay += decay;
            }
            decay *= 0.85;
        }
        float toSunDist = length(sunPos - texCoord);
        float sunCone = smoothstep(1.1, 0.0, toSunDist);
        float intensity = (godRay / 8.0) * GodRays * SunVisibility * sunCone * 0.42;
        vec3 beamColor = vec3(1.18, 1.02, 0.78);
        c += beamColor * intensity;
    }

    if (!isSky && WaterShimmer > 0.01) {
        vec2 sunPos = vec2(SunScreenX, SunScreenY);
        float toSun = length(sunPos - texCoord);
        vec2 waveCoord = texCoord * 32.0 + vec2(GameTime * 0.9, GameTime * 0.6);
        float wave = sin(waveCoord.x) * cos(waveCoord.y);
        float spec = pow(max(1.0 - toSun * 1.8 + wave * 0.18, 0.0), 10.0) * SunVisibility;
        c += vec3(0.85, 0.95, 1.1) * spec * WaterShimmer * 0.28;
    }

    if (Bloom > 0.01) {
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
