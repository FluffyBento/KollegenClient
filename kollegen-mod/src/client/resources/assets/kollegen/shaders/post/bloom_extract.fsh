#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

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

vec3 extractSample(vec3 srgbCol) {
    vec3 col = pow(max(srgbCol, vec3(0.0)), vec3(2.2));
    float realFac = clamp(Realism, 0.0, 1.5);
    float maxC = max(col.r, max(col.g, col.b));
    float minC = min(col.r, min(col.g, col.b));
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float sat = maxC > 0.001 ? (maxC - minC) / maxC : 0.0;
    float warmFire = smoothstep(0.08, 0.50, col.r - col.b) * smoothstep(0.32, 0.82, col.r) * ((0.38 + 0.16 * realFac) * max(0.4, EmissiveHint));
    float coolLantern = smoothstep(0.06, 0.45, max(col.b, col.g) - col.r * 0.85) * smoothstep(0.30, 0.80, max(col.b, col.g)) * ((0.42 + 0.16 * realFac) * max(0.4, EmissiveHint));
    float nightLampBias = (1.0 - DayFactor) * smoothstep(0.28, 0.80, maxC) * (0.28 * max(0.4, EmissiveHint));
    float signal = max(luma, maxC * (0.78 + 0.22 * sat)) + max(warmFire, coolLantern) + nightLampBias;
    float thresh = mix(0.68, max(0.26, 0.34 - 0.04 * realFac), clamp(Bloom, 0.0, 1.0));
    float knee = 0.24;
    float rq = clamp(signal - (thresh - knee), 0.0, 2.0 * knee);
    rq = (rq * rq) / (4.0 * knee + 0.0001);
    float weight = max(rq, signal - thresh) / max(signal, 0.0001);
    return col * clamp(weight, 0.0, 1.8);
}

void main() {
    if (Bloom <= 0.01) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec2 px = 1.6 / max(InSize, vec2(1.0));
    vec3 s0 = extractSample(texture(InSampler, texCoord + vec2(-px.x, -px.y)).rgb);
    vec3 s1 = extractSample(texture(InSampler, texCoord + vec2( px.x, -px.y)).rgb);
    vec3 s2 = extractSample(texture(InSampler, texCoord + vec2(-px.x,  px.y)).rgb);
    vec3 s3 = extractSample(texture(InSampler, texCoord + vec2( px.x,  px.y)).rgb);
    vec3 avg = (s0 + s1 + s2 + s3) * 0.25;
    fragColor = vec4(avg, 1.0);
}
