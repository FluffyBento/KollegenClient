#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec3 skyDir;

layout(location = 0) out vec4 fragColor;

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbmClouds(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 5; i++) {
        v += a * valueNoise(p);
        p = rot * p * 2.04;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec4 base = ColorModulator;
    if (ShaderFlags.x > 0.5 && ShaderWorldB.w > 0.01) {
        vec3 dir = normalize(skyDir);
        if (dir.y <= 0.005) {
            base.rgb = mix(base.rgb, FogColor.rgb, clamp(ShaderWorldB.w * 0.95, 0.0, 1.0));
        } else {
            vec3 sunDir = normalize(SunDirData.xyz);
            float dayFac = SunDirData.w;
            float t = ShaderWorldB.x;
            float realFac = clamp(ShaderExtra.x, 0.0, 1.5);
            float up = clamp(dir.y, 0.0, 1.0);
            float horizonMask = pow(1.0 - up, mix(2.1, 1.75, min(realFac, 1.2)));
            float sunsetAmt = (1.0 - abs(dayFac * 2.0 - 1.0)) * smoothstep(0.02, 0.35, dayFac + 0.1);
            float rainFac = ShaderWorldB.y;

            vec3 dayZenith = vec3(0.08, 0.35, 0.95);
            vec3 sunsetHorizon = mix(vec3(1.16, 0.62, 0.34), vec3(1.25, 0.68, 0.42), min(realFac, 1.2));
            vec3 dayHoriz = mix(vec3(0.46, 0.76, 1.00), sunsetHorizon, sunsetAmt * 0.84);
            vec3 nightZenith = vec3(0.015, 0.036, 0.12);
            vec3 nightHoriz = mix(FogColor.rgb, vec3(0.05, 0.11, 0.25), 0.38);

            vec3 zenith = mix(nightZenith, dayZenith, dayFac);
            vec3 horizon = mix(nightHoriz, dayHoriz, smoothstep(0.08, 0.45, dayFac));
            vec3 gradSky = mix(zenith, horizon, horizonMask);
            gradSky = mix(gradSky, vec3(dot(gradSky, vec3(0.299, 0.587, 0.114))) * vec3(0.74, 0.80, 0.90), rainFac * 0.48);

            float sunAlign = clamp(dot(dir, sunDir), 0.0, 1.0);
            float mie = (pow(sunAlign, 8.0) * (0.30 + 0.16 * realFac) + pow(sunAlign, 48.0) * (0.48 + 0.24 * realFac) + pow(sunAlign, 280.0) * 0.95) * smoothstep(0.05, 0.25, dayFac + 0.1);
            float sunDisc = smoothstep(0.9968, 0.9988, sunAlign) * smoothstep(0.015, 0.10, up) * smoothstep(0.10, 0.35, dayFac) * (0.95 + 0.75 * realFac);
            vec3 mieColor = mix(vec3(0.26, 0.42, 0.78), mix(vec3(1.24, 0.66, 0.30), vec3(1.14, 1.00, 0.84), dayFac), smoothstep(0.05, 0.55, dayFac));
            gradSky += mieColor * ((mie + sunDisc) * (1.0 - rainFac * 0.82) * clamp(ShaderWorldB.w, 0.0, 1.2) * smoothstep(0.01, 0.10, up));

            float nightMask = 1.0 - smoothstep(0.05, 0.35, dayFac);
            if (nightMask > 0.01 && up > 0.06) {
                vec2 starCell = floor((dir.xz / (up + 0.16)) * 185.0);
                float starRand = hash21(starCell);
                if (starRand > 0.992) {
                    float twinkle = 0.65 + 0.35 * sin(t * 2.5 + starRand * 120.0);
                    gradSky += vec3(0.75, 0.88, 1.15) * ((starRand - 0.992) * 140.0 * twinkle * nightMask * smoothstep(0.06, 0.26, up) * (1.0 - rainFac));
                }
            }

            if (up > 0.025) {
                vec2 cuvBase = dir.xz / (up + 0.09) * 5.8 + vec2(t * 0.016, t * 0.008);
                vec2 cWarp = vec2(fbmClouds(cuvBase * 0.65 + vec2(3.2, 1.7)) - 0.5, fbmClouds(cuvBase * 0.65 + vec2(8.1, 4.4)) - 0.5) * (0.28 * mix(0.25, 1.0, min(realFac, 1.3)));
                vec2 cuv = cuvBase + cWarp;
                float cNoise = fbmClouds(cuv);
                float cMid = fbmClouds(cuv * 2.35 + vec2(4.7, -2.3));
                float cFine = fbmClouds(cuv * 5.10 + vec2(-1.8, 7.2));
                float rawCloud = cNoise * 0.65 + cMid * 0.25 + cFine * 0.10;
                float cThresh = mix(0.558, 0.538, min(realFac, 1.2)) - rainFac * 0.105;
                float cloudDensity = pow(smoothstep(cThresh, cThresh + 0.068, rawCloud), 0.85);
                vec2 sOff = sunDir.xz * 0.16;
                float sunSelf = fbmClouds(cuv + sOff) * 0.65 + fbmClouds((cuv + sOff) * 2.35 + vec2(4.7, -2.3)) * 0.25 + fbmClouds((cuv + sOff) * 5.10 + vec2(-1.8, 7.2)) * 0.10;
                float selfShadow = smoothstep(cThresh - 0.025, cThresh + 0.075, sunSelf);

                vec3 sunsetDark = vec3(0.42, 0.27, 0.44);
                vec3 sunsetLit = vec3(1.42, 0.66, 0.38);
                vec3 dayDark = vec3(0.58, 0.70, 0.86);
                vec3 dayLit = vec3(1.22, 1.19, 1.14);
                vec3 warmDark = mix(sunsetDark, dayDark, smoothstep(0.22, 0.85, dayFac));
                vec3 warmLit = mix(sunsetLit, dayLit, smoothstep(0.22, 0.85, dayFac));
                vec3 darkSide = mix(vec3(0.03, 0.06, 0.15), warmDark, smoothstep(0.05, 0.38, dayFac));
                vec3 litSide = mix(vec3(0.11, 0.18, 0.33), warmLit, smoothstep(0.05, 0.42, dayFac));
                litSide = mix(litSide, vec3(1.44, 0.72, 0.40), sunsetAmt * 0.72 * min(realFac, 1.2));
                darkSide = mix(darkSide, vec3(0.32, 0.36, 0.44) * max(0.25, dayFac), rainFac * 0.65);
                litSide = mix(litSide, vec3(0.62, 0.66, 0.74) * max(0.30, dayFac), rainFac * 0.55);
                vec3 cloudCol = mix(litSide, darkSide, selfShadow * mix(0.62, 0.82, min(realFac, 1.2)));
                cloudCol += mieColor * (pow(sunAlign, 6.0) * (0.36 + 0.20 * realFac) * smoothstep(0.05, 0.25, dayFac) * (1.0 - rainFac * 0.7));
                float horizFade = smoothstep(0.025, 0.20, up);
                gradSky = mix(gradSky, cloudCol, cloudDensity * horizFade * clamp(ShaderWorldB.w * 0.95, 0.0, 0.96));
            }

            float rimBlend = smoothstep(0.0, 0.045, up);
            gradSky = mix(FogColor.rgb, gradSky, rimBlend);
            base.rgb = mix(base.rgb, gradSky, clamp(ShaderWorldB.w * 0.98, 0.0, 1.0));
        }
        fragColor = vec4(base.rgb, base.a);
    } else {
        fragColor = apply_fog(base, sphericalVertexDistance, cylindricalVertexDistance, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);
    }
}
