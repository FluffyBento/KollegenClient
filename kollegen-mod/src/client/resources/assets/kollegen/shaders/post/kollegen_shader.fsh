#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;

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
    float RainWet;
    float DayFactor;
    float EmissiveHint;
    float TanHalfFov;
    float Aspect;
    float SunViewX;
    float SunViewY;
    float SunViewZ;
    float Raytracing;
    float WorldUpViewX;
    float WorldUpViewY;
    float WorldUpViewZ;
    float ScreenYFlip;
    float Realism;
    float DimType;
    float SunWorldX;
    float SunWorldY;
    float SunWorldZ;
    float CamVoxX;
    float CamVoxY;
    float CamVoxZ;
    float CamRightX;
    float CamRightY;
    float CamRightZ;
    float CamUpX;
    float CamUpY;
    float CamUpZ;
    float CamBackX;
    float CamBackY;
    float CamBackZ;
    float VoxReady;
    float PadA;
    float PadB;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const vec3 Gray = vec3(0.2126, 0.7152, 0.0722);

float luminance(vec3 v) {
    return dot(v, Gray);
}

vec3 aces(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 reinhard_jodie(vec3 v) {
    float l = luminance(v);
    vec3 tv = v / (1.0 + v);
    return mix(v / (1.0 + l), tv, tv);
}

vec3 uncharted2Tonemap(vec3 x) {
    float A = 0.22;
    float B = 0.30;
    float C = 0.10;
    float D = 0.20;
    float E = 0.01;
    float F = 0.30;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 c = pow(max(inTexel.rgb, vec3(0.0)), vec3(2.2));
    float realFac = clamp(Realism, 0.0, 1.5);

    if (Bloom > 0.01) {
        vec3 b = texture(BloomSampler, texCoord).rgb;
        c += b * (Bloom * (0.72 + 0.16 * realFac));
    }

    c *= max(Exposure, 0.1);

    if (abs(Warmth - 1.0) > 0.005) {
        float t = Warmth - 1.0;
        c.r *= (1.0 + t * 0.55);
        c.g *= (1.0 + t * 0.14);
        c.b *= (1.0 - t * 0.52);
        c = max(c, vec3(0.0));
    }

    if (ToneMap > 0.5) {
        if (ToneMap < 1.5) {
            c = aces(c * 1.18);
        } else if (ToneMap < 2.5) {
            c = reinhard_jodie(c * 1.38);
        } else {
            vec3 curr = uncharted2Tonemap(c * 1.75);
            vec3 whiteScale = 1.0 / uncharted2Tonemap(vec3(11.2));
            c = curr * whiteScale;
        }
    }

    c = pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));

    if (realFac > 0.1 && ToneMap > 0.5) {
        float lumaGrade = clamp(luminance(c), 0.0, 1.0);
        vec3 splitTone = mix(vec3(-0.022, 0.004, 0.032), vec3(0.036, 0.012, -0.022) * DayFactor, smoothstep(0.16, 0.80, lumaGrade));
        c += splitTone * (4.0 * lumaGrade * (1.0 - lumaGrade) * smoothstep(0.1, 0.8, realFac) * 0.65);
    }

    if (abs(Vibrance - 1.0) > 0.005) {
        float luma = luminance(c);
        float maxCol = max(c.r, max(c.g, c.b));
        float minCol = min(c.r, min(c.g, c.b));
        float sat = maxCol - minCol;
        float vibAmt = clamp(1.0 + (Vibrance - 1.0) * (1.0 - sat), 0.1, 3.0);
        c = mix(vec3(luma), c, vibAmt);
    }

    if (abs(Saturation - 1.0) > 0.005) {
        float luma = luminance(c);
        c = mix(vec3(luma), c, Saturation);
    }

    if (abs(Contrast - 1.0) > 0.005) {
        c = (c - 0.5) * Contrast + 0.5;
    }

    if (abs(Brightness - 1.0) > 0.005) {
        c *= Brightness;
    }

    if (abs(Gamma - 1.0) > 0.005) {
        c = pow(max(c, vec3(0.0)), vec3(1.0 / max(1.0 + (Gamma - 1.0) * 0.55, 0.35)));
    }

    if (Vignette > 0.005) {
        vec2 center = texCoord - 0.5;
        float dist = dot(center, center);
        float vig = clamp(1.0 - dist * (Vignette * 2.2), 0.0, 1.0);
        c *= mix(1.0, vig, 0.92);
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), inTexel.a);
}
