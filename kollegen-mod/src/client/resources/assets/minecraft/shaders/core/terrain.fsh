#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 localPos;
layout(location = 6) in vec4 terrainMeta;
layout(location = 7) in vec3 worldBlockPos;
layout(location = 8) flat in float matId;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

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

float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 4; i++) {
        v += a * valueNoise(p);
        p = rot * p * 2.04;
        a *= 0.5;
    }
    return v;
}

float ggxSpecular(vec3 N, vec3 L, vec3 V, float rough, float f0) {
    float nl = max(dot(N, L), 0.0);
    float nv = max(dot(N, V), 0.0);
    if (nl <= 0.0 || nv <= 0.0) {
        return 0.0;
    }
    vec3 H = normalize(L + V);
    float nh = max(dot(N, H), 0.0);
    float vh = max(dot(V, H), 0.0);
    float a = max(rough * rough, 0.015);
    float a2 = a * a;
    float denom = nh * nh * (a2 - 1.0) + 1.0;
    float D = a2 / (3.14159265 * denom * denom);
    float k = (rough + 1.0) * (rough + 1.0) * 0.125;
    float G = (nv / (nv * (1.0 - k) + k)) * (nl / (nl * (1.0 - k) + k));
    float F = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
    return min((D * G * F) / (4.0 * max(nv, 0.002)), 3.2);
}

vec3 computeLavaShade(vec3 viewDir, vec3 geomNormal, vec3 baseTex, float realFac) {
    float t = ShaderWorldB.x;
    bool isWall = abs(geomNormal.y) <= 0.55;
    vec2 lp = isWall
        ? (vec2(worldBlockPos.x + worldBlockPos.z, worldBlockPos.y * 1.15) * 1.45 + vec2(0.0, t * 0.85))
        : (worldBlockPos.xz * 1.55 + vec2(t * 0.09, t * 0.05));
    vec2 warp = vec2(fbm2(lp * 0.90 + vec2(t * 0.06, 1.7)), fbm2(lp * 0.90 + vec2(4.3, -t * 0.05))) * 0.75;
    vec2 mp = lp + warp;
    float nA = clamp((fbm2(mp * 1.45) - 0.36) / 0.28, 0.0, 1.0);
    float nB = clamp((fbm2(mp * 2.95 + vec2(5.2, -3.1)) - 0.36) / 0.28, 0.0, 1.0);
    float nDetail = clamp((fbm2(mp * 5.8 + vec2(-2.1, 4.4)) - 0.36) / 0.28, 0.0, 1.0);
    float flowField = nA * 0.68 + nB * 0.32;
    float crustMask = smoothstep(0.50, 0.74, flowField);
    float veinCore = 1.0 - smoothstep(0.0, 0.26, abs(nA - 0.45) * 2.0);
    float pulse = 0.90 + 0.10 * sin(t * 2.1 + mp.x * 1.7 - mp.y * 1.4);

    vec3 basaltRock = mix(vec3(0.015, 0.004, 0.003), vec3(0.048, 0.012, 0.005), nDetail);
    float ndotv = clamp(dot(viewDir, geomNormal), 0.0, 1.0);
    basaltRock *= 0.80 + 0.20 * clamp(geomNormal.y * 0.5 + 0.5, 0.0, 1.0);
    basaltRock += vec3(0.025, 0.006, 0.003) * pow(1.0 - ndotv, 2.5);

    vec3 crimsonRim = vec3(0.62, 0.025, 0.002);
    vec3 moltenOrange = vec3(1.55, 0.22, 0.008);
    vec3 hotCore = vec3(2.40, 0.82, 0.045);
    float heat = clamp((1.0 - flowField) * 1.15 * pulse + veinCore * 0.35, 0.0, 1.0);
    vec3 magma = mix(crimsonRim, moltenOrange, smoothstep(0.10, 0.62, heat));
    magma = mix(magma, hotCore, smoothstep(0.68, 0.98, heat));

    vec3 glowSeam = vec3(0.48, 0.05, 0.003) * (smoothstep(0.75, 0.48, flowField) * crustMask * pulse);
    vec3 lavaLinear = mix(magma, basaltRock + glowSeam, crustMask);
    float rBlend = smoothstep(0.0, 0.85, realFac);
    vec3 baseLin = pow(max(baseTex, vec3(0.0)), vec3(2.2)) * 1.35;
    vec3 outLin = mix(baseLin, lavaLinear, rBlend);
    float pk = max(outLin.r, max(outLin.g, outLin.b));
    if (pk > 0.88) {
        outLin *= (0.88 + (pk - 0.88) / (1.0 + (pk - 0.88) * 1.15)) / pk;
    }
    return pow(max(outLin, vec3(0.0)), vec3(1.0 / 2.2));
}

vec2 computeRainRipples(vec2 xz, float t, float amount) {
    vec2 slope = vec2(0.0);
    const float cellSize = 0.52;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        vec2 q = xz / cellSize + vec2(0.37, 0.61) * fk;
        vec2 cell = floor(q);
        vec2 f = q - cell;
        float rate = 1.15 + 0.45 * hash21(cell + fk * 31.7);
        float phase = t * rate + hash21(cell * 1.37 + fk * 7.1);
        float life = fract(phase);
        float stepId = floor(phase);
        float spawnProb = hash21(cell + vec2(stepId * 13.7, fk * 53.1));
        if (spawnProb > amount * 0.72) {
            continue;
        }
        vec2 off = vec2(hash21(cell + vec2(3.1 + stepId, 1.9 + fk)), hash21(cell + vec2(7.7 + fk, 5.3 + stepId)));
        vec2 d = f - (0.22 + 0.56 * off);
        float r = length(d);
        float x = r - life * 0.44;
        float band = 1.0 - smoothstep(0.0, 0.13, abs(x));
        if (band <= 0.0 || r < 0.001) {
            continue;
        }
        float fade = (1.0 - life) * (1.0 - life) * smoothstep(0.0, 0.08, life);
        slope += (d / r) * (cos(x * 38.0) * band * fade);
    }
    return slope * 0.34;
}

vec4 computeEntityRipples(float vLen, float maxDist, float maxDy) {
    vec2 rippleSlope = vec2(0.0);
    float rippleFoam = 0.0;
    float rippleCaustic = 0.0;
    if (vLen < maxDist) {
        for (int i = 0; i < 16; i++) {
            vec4 rp = WaterRipples[i];
            if (rp.w > 0.01) {
                float rAge = mod(rp.w, 4.0);
                float rAmp = (rp.w - rAge) * 0.0125;
                vec2 diffXZ = localPos.xz - rp.xz;
                float distXZ = length(diffXZ);
                float maxR = 3.2 + rAmp * 4.5;
                if (distXZ < maxR && abs(localPos.y - rp.y) < maxDy) {
                    vec2 rDir = distXZ > 0.002 ? (diffXZ / distXZ) : vec2(0.0);
                    float speed = 2.4 + rAmp * 1.15;
                    float radius = 0.14 + rAge * speed;
                    float deltaR = distXZ - radius;
                    float lifeFade = smoothstep(3.2, 0.25, rAge) * smoothstep(0.0, 0.05, rAge);
                    float distFade = smoothstep(maxR, maxR * 0.45, distXZ);
                    float width = 1.4 / (0.85 + rAmp * 0.45);
                    float env = exp(-deltaR * deltaR * width) * lifeFade * distFade;
                    float freq = max(6.8, 12.5 - rAmp * 2.6);
                    float ph = deltaR * freq;
                    float dWave = (cos(ph) * freq - 2.0 * width * deltaR * sin(ph)) * env;
                    rippleSlope += rDir * clamp(dWave * 0.022 * rAmp, -0.35, 0.35);
                    float crest = max(sin(ph), 0.0) * env;
                    rippleFoam += crest * clamp(rAmp, 0.0, 1.5) * smoothstep(1.4, 0.02, rAge) * 0.28;
                    rippleCaustic += env * rAmp * 0.32;
                }
            }
        }
    }
    return vec4(rippleSlope, rippleFoam, rippleCaustic);
}

vec4 computeWaterShade(vec3 viewDir, float vLen, vec3 geomNormal) {
    float t = ShaderWorldB.x;
    vec2 wp = worldBlockPos.xz;
    float realFac = clamp(ShaderExtra.x, 0.0, 1.5);
    float reflStrength = clamp(ShaderWorldA.w * mix(0.92, 1.15, min(realFac, 1.2)), 0.0, 1.5);
    float dayFac = SunDirData.w;
    vec3 sunDir = normalize(SunDirData.xyz);
    float sunsetAmt = (1.0 - abs(dayFac * 2.0 - 1.0)) * smoothstep(0.0, 0.35, dayFac + 0.1);
    float distSmooth = smoothstep(85.0, 16.0, vLen);

    vec2 p1 = wp * 0.85 + vec2(t * 0.34, t * 0.21);
    vec2 p2 = wp * 1.90 + vec2(-t * 0.44, t * 0.31);
    float eps = 0.09;
    float h0 = fbm2(p1) * 0.65 + fbm2(p2) * 0.35;
    float hx = fbm2(p1 + vec2(eps, 0.0)) * 0.65 + fbm2(p2 + vec2(eps, 0.0)) * 0.35;
    float hz = fbm2(p1 + vec2(0.0, eps)) * 0.65 + fbm2(p2 + vec2(0.0, eps)) * 0.35;
    float slopeAmp = 1.20 * max(0.35, reflStrength) * (0.28 + 0.72 * distSmooth);
    vec4 er = computeEntityRipples(vLen, 68.0, 1.8);
    vec2 rippleSlope = er.xy;
    float rippleFoam = er.z;
    float rippleCaustic = er.w;
    vec3 baseN = abs(geomNormal.y) > 0.45 ? vec3(0.0, sign(geomNormal.y), 0.0) : geomNormal;
    vec2 rainWaterSlope = vec2(0.0);
    if (ShaderWorldB.y > 0.01 && vLen < 42.0) {
        float waterRainVis = ShaderWorldB.y * smoothstep(0.50, 0.92, terrainMeta.y) * smoothstep(42.0, 10.0, vLen);
        rainWaterSlope = computeRainRipples(wp, t, ShaderWorldB.y) * (waterRainVis * 0.75);
    }
    vec3 waterN = normalize(vec3((h0 - hx) * slopeAmp - rippleSlope.x - rainWaterSlope.x, 1.0, (h0 - hz) * slopeAmp - rippleSlope.y - rainWaterSlope.y));
    if (abs(geomNormal.y) <= 0.45) {
        waterN = normalize(baseN + vec3(h0 - hx, h0 - hz, h0 - hx) * (slopeAmp * 0.35));
    }

    vec3 reflDir = reflect(-viewDir, waterN);
    float reflUp = clamp(reflDir.y, 0.0, 1.0);
    vec3 skyTop = mix(vec3(0.010, 0.025, 0.09), vec3(0.055, 0.24, 0.85), dayFac);
    vec3 twilight = vec3(1.12, 0.38, 0.14);
    vec3 dayHoriz = mix(twilight, vec3(0.30, 0.64, 0.98), smoothstep(0.18, 0.72, dayFac));
    vec3 skyHoriz = mix(vec3(0.02, 0.045, 0.12), dayHoriz, smoothstep(0.05, 0.35, dayFac));
    vec3 envRefl = mix(skyHoriz, skyTop, pow(reflUp, 0.58));

    if (ShaderWorldB.w > 0.05) {
        vec2 cuvBase = reflDir.xz / (reflUp + 0.09) * 5.8 + vec2(t * 0.016, t * 0.008);
        vec2 cWarp = vec2(fbm2(cuvBase * 0.65 + vec2(3.2, 1.7)) - 0.5, fbm2(cuvBase * 0.65 + vec2(8.1, 4.4)) - 0.5) * (0.28 * mix(0.25, 1.0, min(realFac, 1.3)));
        vec2 cuv = cuvBase + cWarp;
        float rawCloud = fbm2(cuv) * 0.65 + fbm2(cuv * 2.35 + vec2(4.7, -2.3)) * 0.25 + fbm2(cuv * 5.10 + vec2(-1.8, 7.2)) * 0.10;
        float cThresh = mix(0.558, 0.538, min(realFac, 1.2)) - ShaderWorldB.y * 0.105;
        float cDensity = pow(smoothstep(cThresh, cThresh + 0.068, rawCloud), 0.85);
        vec2 sOff = sunDir.xz * 0.16;
        float sunSelf = fbm2(cuv + sOff) * 0.65 + fbm2((cuv + sOff) * 2.35 + vec2(4.7, -2.3)) * 0.25 + fbm2((cuv + sOff) * 5.10 + vec2(-1.8, 7.2)) * 0.10;
        float selfShadow = smoothstep(cThresh - 0.025, cThresh + 0.075, sunSelf);
        vec3 sunsetDark = vec3(0.22, 0.10, 0.22);
        vec3 sunsetLit = vec3(1.45, 0.42, 0.14);
        vec3 dayDark = vec3(0.32, 0.45, 0.68);
        vec3 dayLit = vec3(1.18, 1.14, 1.08);
        vec3 warmDark = mix(sunsetDark, dayDark, smoothstep(0.22, 0.85, dayFac));
        vec3 warmLit = mix(sunsetLit, dayLit, smoothstep(0.22, 0.85, dayFac));
        vec3 cloudBase = mix(vec3(0.03, 0.05, 0.12), mix(warmLit, warmDark, selfShadow * mix(0.62, 0.82, min(realFac, 1.2))), smoothstep(0.05, 0.42, dayFac));
        cloudBase = mix(cloudBase, vec3(1.42, 0.50, 0.18), sunsetAmt * 0.62 * (1.0 - selfShadow * 0.65));
        envRefl = mix(envRefl, cloudBase, cDensity * smoothstep(0.025, 0.20, reflUp) * 0.90 * min(1.0, ShaderWorldB.w));
    }

    float sunAlign = clamp(dot(reflDir, sunDir), 0.0, 1.0);
    vec3 mieHalo = mix(vec3(0.14, 0.26, 0.65), mix(vec3(1.25, 0.48, 0.14), vec3(1.18, 0.96, 0.74), dayFac), smoothstep(0.05, 0.50, dayFac));
    envRefl += mieHalo * (pow(sunAlign, 12.0) * 0.30 + pow(sunAlign, 90.0) * 0.65) * smoothstep(0.04, 0.22, dayFac + 0.08);

    float ndotv = clamp(dot(viewDir, waterN), 0.0, 1.0);
    float fresnel = clamp(0.04 + 0.96 * pow(1.0 - ndotv, 3.6), 0.0, 1.0);
    float sunSpec = ggxSpecular(waterN, sunDir, viewDir, 0.08, 0.025) * 1.45;

    float cA = abs(sin(wp.x * 2.4 + t * 1.8 + cos(wp.y * 2.1 - t * 1.4)));
    float cB = abs(cos(wp.y * 2.6 - t * 1.6 + sin(wp.x * 1.9 + t * 1.2)));
    float caustic = smoothstep(0.68, 0.98, (cA + cB) * 0.5);

    float unifiedSky = mix(0.18, 1.0, smoothstep(0.08, 0.88, terrainMeta.y));
    vec3 nightDeep = vec3(0.004, 0.015, 0.045);
    vec3 dayDeep = vec3(0.008, 0.12, 0.24);
    vec3 nightShal = vec3(0.008, 0.038, 0.08);
    vec3 dayShal = vec3(0.016, 0.24, 0.38);
    vec3 deepCyan = mix(nightDeep, dayDeep, dayFac) * unifiedSky;
    vec3 shallowTurq = mix(nightShal, dayShal, dayFac) * unifiedSky;
    vec3 waterBody = mix(shallowTurq, deepCyan, 1.0 - ndotv);
    waterBody += vec3(0.08, 0.26, 0.34) * ((caustic * 0.42 + rippleCaustic * 0.65) * max(0.20, dayFac) * unifiedSky);

    float refAmt = clamp(fresnel * min(1.0, reflStrength * 0.92), 0.0, 0.94);
    vec3 finalWater = mix(waterBody, envRefl, refAmt);
    vec3 specCol = mix(vec3(0.55, 0.72, 1.00), vec3(1.45, 1.12, 0.78), dayFac);
    finalWater += specCol * (sunSpec * reflStrength * unifiedSky * max(0.25, dayFac));
    vec3 foamCol = mix(vec3(0.12, 0.28, 0.46), vec3(0.62, 0.82, 0.94), max(0.25, dayFac)) * unifiedSky;
    finalWater += foamCol * (clamp(rippleFoam, 0.0, 0.80) * min(1.3, reflStrength));

    float pk = max(finalWater.r, max(finalWater.g, finalWater.b));
    if (pk > 0.88) {
        finalWater *= (0.88 + (pk - 0.88) / (1.0 + (pk - 0.88) * 1.25)) / pk;
    }
    vec3 finalSrgb = pow(max(finalWater, vec3(0.0)), vec3(1.0 / 2.2));
    float smoothAlpha = clamp(mix(0.36, 0.94, fresnel) + rippleFoam * 0.18, 0.36, 0.96);
    return vec4(finalSrgb, smoothAlpha);
}

void main() {
    vec2 pxSize = 1.0 / max(vec2(TextureSize), vec2(1.0));
    vec4 texCol = UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, pxSize) : sampleNearest(Sampler0, texCoord0, pxSize);
    vec4 color = texCol * vertexColor;
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    if (ShaderFlags.x > 0.5 && color.a > 0.01) {
        vec3 dPosX = dFdx(localPos);
        vec3 dPosY = dFdy(localPos);
        vec3 cr = cross(dPosY * 1024.0, dPosX * 1024.0);
        float crLen = length(cr);
        vec3 geomNormal = crLen > 1e-8 ? (cr / crLen) : vec3(0.0, 1.0, 0.0);
        if (dot(geomNormal, -localPos) < 0.0) {
            geomNormal = -geomNormal;
        }
        float vLen = length(localPos);
        vec3 viewDir = vLen > 1e-5 ? (-localPos / vLen) : vec3(0.0, 1.0, 0.0);
        int mClass = int(matId + 0.5);

        if (terrainMeta.z > 0.20 && ShaderWorldA.w > 0.05) {
            vec4 wShade = computeWaterShade(viewDir, vLen, geomNormal);
            float wBlend = terrainMeta.z * smoothstep(0.05, 0.35, ShaderWorldA.w);
            color.a = mix(color.a, wShade.a, wBlend);
            #ifndef OIT_ALPHA_ONLY
            color.rgb = mix(color.rgb, wShade.rgb, terrainMeta.z);
            #endif
        }
        #ifndef OIT_ALPHA_ONLY
        else if (terrainMeta.z <= 0.20) {
            float realFac = clamp(ShaderExtra.x, 0.0, 1.5);
            float dimType = ShaderExtra.y;
            float blockL = terrainMeta.x;
            float fy = fract(worldBlockPos.y + 0.002);
            bool fluidTop = geomNormal.y > 0.55 && fy > 0.03 && fy < 0.97;
            bool lavaHueTop = blockL >= 0.945 && texCol.r > 0.66 && texCol.b < 0.24 && (texCol.r - texCol.b) > 0.48 && texCol.g < texCol.r * 0.84;
            bool lavaHueSide = abs(geomNormal.y) <= 0.55 && blockL >= 0.955 && texCol.r > 0.74 && texCol.b < 0.18 && (texCol.r - texCol.b) > 0.62 && texCol.g < texCol.r * 0.78;
            bool isLavaSurf = false;
            #ifndef ALPHA_CUTOUT
            isLavaSurf = realFac > 0.01 && (mClass == 9 || (fluidTop && lavaHueTop) || lavaHueSide);
            #endif
            if (isLavaSurf) {
                color.rgb = computeLavaShade(viewDir, geomNormal, color.rgb, realFac);
            } else {
                float nearDetail = smoothstep(52.0, 14.0, vLen);
                vec3 N = geomNormal;
                if (ShaderWorldA.x > 0.01 && nearDetail > 0.01) {
                    vec2 dUvX = dFdx(texCoord0);
                    vec2 dUvY = dFdy(texCoord0);
                    ivec2 tSize = max(TextureSize, ivec2(1));
                    vec2 tcPx = texCoord0 * vec2(tSize);
                    ivec2 px = clamp(ivec2(floor(tcPx)), ivec2(0), tSize - 1);
                    ivec2 tileMin = ivec2(px.x & ~15, px.y & ~15);
                    ivec2 tileMax = min(tileMin + ivec2(15), tSize - 1);
                    vec2 f = fract(tcPx);
                    vec3 linW = vec3(0.2126, 0.7152, 0.0722);
                    vec4 cC = texelFetch(Sampler0, px, 0);
                    vec4 cL = texelFetch(Sampler0, max(px - ivec2(1, 0), tileMin), 0);
                    vec4 cR = texelFetch(Sampler0, min(px + ivec2(1, 0), tileMax), 0);
                    vec4 cD = texelFetch(Sampler0, max(px - ivec2(0, 1), tileMin), 0);
                    vec4 cU = texelFetch(Sampler0, min(px + ivec2(0, 1), tileMax), 0);
                    float alphaGuard = smoothstep(0.75, 0.95, min(min(cC.a, min(cL.a, cR.a)), min(cD.a, cU.a)));
                    float hC = dot(pow(max(cC.rgb, vec3(0.0)), vec3(2.2)), linW);
                    float hL = dot(pow(max(cL.rgb, vec3(0.0)), vec3(2.2)), linW);
                    float hR = dot(pow(max(cR.rgb, vec3(0.0)), vec3(2.2)), linW);
                    float hD = dot(pow(max(cD.rgb, vec3(0.0)), vec3(2.2)), linW);
                    float hU = dot(pow(max(cU.rgb, vec3(0.0)), vec3(2.2)), linW);
                    float wL = smoothstep(0.32, 0.0, f.x);
                    float wR = smoothstep(0.68, 1.0, f.x);
                    float wD = smoothstep(0.32, 0.0, f.y);
                    float wU = smoothstep(0.68, 1.0, f.y);
                    vec2 dH = vec2(
                        (hC - hL) * wL + (hR - hC) * wR + (hR - hL) * 0.16,
                        (hC - hD) * wD + (hU - hC) * wU + (hU - hD) * 0.16
                    );
                    dH = clamp(dH, vec2(-0.34), vec2(0.34)) * alphaGuard;
                    vec3 dp2perp = cross(dPosY, geomNormal);
                    vec3 dp1perp = cross(geomNormal, dPosX);
                    vec3 T = dp2perp * dUvX.x + dp1perp * dUvY.x;
                    vec3 B = dp2perp * dUvX.y + dp1perp * dUvY.y;
                    float maxTB = max(dot(T, T), dot(B, B));
                    if (maxTB > 1e-8) {
                        float invmax = inversesqrt(maxTB);
                        N = normalize(geomNormal + (T * (-dH.x) + B * (-dH.y)) * (invmax * ShaderWorldA.x * 1.15 * nearDetail));
                    }
                }

                vec3 sunDir = normalize(SunDirData.xyz);
                float dayFac = SunDirData.w;
                float skyPow = pow(terrainMeta.y, 1.65);
                float skyOpen = smoothstep(0.14, 0.90, terrainMeta.y);
                float voxelSunShadow = smoothstep(0.08, 0.42, terrainMeta.y);
                float ndl = dot(N, sunDir);
                float leafLike = step(0.25, terrainMeta.w);
                float isTrueFoliage = step(0.75, terrainMeta.w);
                #ifdef ALPHA_CUTOUT
                if (ALPHA_CUTOUT > 0.25 && leafLike < 0.5) {
                    float texSat = max(texCol.r, max(texCol.g, texCol.b)) - min(texCol.r, min(texCol.g, texCol.b));
                    leafLike = smoothstep(0.08, 0.24, texSat) * step(0.18, max(texCol.g, texCol.r));
                }
                #endif

                vec3 albedo = pow(max(texCol.rgb, vec3(0.0)), vec3(2.2)) * pow(max(vertexColor.rgb, vec3(0.0)), vec3(1.32));
                albedo = mix(albedo, pow(max(FogColor.rgb, vec3(0.0)), vec3(2.2)) * vertexColor.a, 1.0 - chunkVisibility);

                float rain = ShaderWorldB.y;
                float sunY = clamp(sunDir.y, 0.0, 1.0);
                vec3 daySun = mix(vec3(1.62, 0.80, 0.32), vec3(1.42, 1.24, 0.94), smoothstep(0.14, 0.80, sunY));
                vec3 nightSun = vec3(0.06, 0.10, 0.22);
                vec3 sunCol = mix(nightSun, daySun, dayFac) * mix(1.0, 0.30, rain);
                vec3 skyFill = mix(vec3(0.015, 0.025, 0.062), vec3(0.24, 0.37, 0.60), dayFac) * (0.68 + 0.32 * max(N.y, 0.0));
                skyFill = mix(skyFill, vec3(dot(skyFill, vec3(0.2126, 0.7152, 0.0722))) * vec3(0.84, 0.90, 0.98), rain * 0.55);

                float caveMask = 1.0 - smoothstep(0.0, 0.45, terrainMeta.y);
                float dBlock = max(15.0 * (1.0 - blockL) - 1.0, 0.0);
                float kFalloff = mix(2.1, 2.9, caveMask);
                float torchPool = (1.65 / (1.0 + dBlock * dBlock / (kFalloff * kFalloff)) * smoothstep(mix(0.08, 0.03, caveMask), mix(0.50, 0.36, caveMask), blockL) + mix(0.06, 0.16, caveMask) * blockL * blockL) * (0.76 + 0.24 * clamp(N.y * 0.5 + 0.5, 0.0, 1.0));
                vec3 warmTorch = mix(vec3(1.42, 0.66, 0.26), vec3(1.22, 0.96, 0.68), blockL * 0.65);

                vec3 lighting;
                if (dimType > 0.5 && dimType < 1.5) {
                    lighting = vec3(0.14, 0.068, 0.042) * (0.74 + 0.26 * clamp(N.y * 0.5 + 0.5, 0.0, 1.0)) + warmTorch * (torchPool * 1.25);
                } else if (dimType >= 1.5) {
                    lighting = vec3(0.085, 0.072, 0.145) * (0.74 + 0.26 * clamp(N.y * 0.5 + 0.5, 0.0, 1.0)) + warmTorch * torchPool;
                } else {
                    float directMask = (leafLike > 0.5 ? smoothstep(-0.18, 0.62, ndl) : clamp(ndl, 0.0, 1.0)) * voxelSunShadow;
                    vec3 caveAmbient = vec3(0.020, 0.026, 0.046) * (0.85 + 0.15 * N.y);
                    vec3 bounceSun = sunCol * (sunY * (0.04 + 0.08 * (0.5 - 0.5 * N.y)) * skyPow);
                    lighting = caveAmbient + skyPow * (skyFill + bounceSun + sunCol * (directMask * ShaderFlags.w)) + warmTorch * torchPool;
                }

                if (ShaderExtra.z > 0.05 && vLen < 14.0) {
                    float att = clamp(1.0 - vLen / 13.5, 0.0, 1.0);
                    att = att * att / (1.0 + vLen * vLen * 0.14);
                    float faceH = 0.25 + 0.75 * max(dot(N, viewDir), 0.0);
                    vec3 hCol = ShaderExtra.w > 1.5 ? vec3(1.30, 0.26, 0.20) : (ShaderExtra.w > 0.5 ? vec3(0.42, 1.24, 1.38) : vec3(1.42, 0.78, 0.34));
                    lighting += hCol * (att * faceH * ShaderExtra.z * 0.85);
                }

                vec3 surface = albedo * lighting;

                if (leafLike > 0.5 && ShaderWorldA.z > 0.01 && dimType < 0.5) {
                    float sssScale = mix(0.28, 0.62, isTrueFoliage);
                    float sss = pow(clamp(dot(-viewDir, sunDir) * 0.5 + 0.5, 0.0, 1.0), 3.0);
                    surface += albedo * vec3(0.55, 0.64, 0.18) * (sss * skyOpen * voxelSunShadow * max(0.22, dayFac) * (1.0 - rain * 0.6) * ShaderWorldA.z * sssScale);
                }

                float puddleRange = smoothstep(74.0, 20.0, vLen);
                float rainMask = rain * smoothstep(0.62, 0.94, terrainMeta.y) * puddleRange;
                float upMask = smoothstep(0.86, 0.97, geomNormal.y);
                float fyPuddle = fract(worldBlockPos.y + 0.002);
                bool onFlatBlockTop = fyPuddle < 0.03 || fyPuddle > 0.97 || abs(fyPuddle - 0.5) < 0.03;
                bool notSubmerged = terrainMeta.w >= -0.25;
                float allowPuddleGround = (isTrueFoliage < 0.5 && onFlatBlockTop && notSubmerged && dimType < 0.5) ? 1.0 : 0.0;
                #ifdef ALPHA_CUTOUT
                allowPuddleGround = 0.0;
                #endif
                float wetBase = rainMask * upMask * allowPuddleGround;
                float puddleField = fbm2(worldBlockPos.xz * 0.24) * 0.72 + fbm2(worldBlockPos.xz * 0.62 + vec2(17.3, 9.1)) * 0.28;
                float puddle = smoothstep(0.505, 0.555, puddleField) * wetBase;
                float dampZone = smoothstep(0.42, 0.52, puddleField) * wetBase;
                surface = mix(surface, surface * 0.62, dampZone) * mix(1.0, 0.45, puddle);
                float wet = clamp(max(dampZone * 0.65, puddle), 0.0, 1.0);

                if (puddle > 0.03) {
                    vec2 rainSlope = computeRainRipples(worldBlockPos.xz, ShaderWorldB.x, rain) * smoothstep(38.0, 10.0, vLen);
                    vec4 pEnt = computeEntityRipples(vLen, 42.0, 0.65);
                    N = normalize(mix(N, normalize(vec3(-rainSlope.x - pEnt.x * 1.35, 1.0, -rainSlope.y - pEnt.y * 1.35)), puddle * 0.94));
                    vec3 pReflDir = reflect(-viewDir, N);
                    float pUp = clamp(pReflDir.y, 0.0, 1.0);
                    vec3 pSkyTop = mix(vec3(0.012, 0.032, 0.10), vec3(0.16, 0.32, 0.64), dayFac);
                    vec3 pSkyHor = mix(vec3(0.025, 0.055, 0.14), vec3(0.32, 0.48, 0.72), dayFac);
                    vec3 pSkyCol = mix(pSkyHor, pSkyTop, pow(pUp, 0.55));
                    float pFres = clamp(0.24 + 0.76 * pow(1.0 - clamp(dot(viewDir, N), 0.0, 1.0), 2.5), 0.0, 1.0);
                    vec3 pSplash = mix(vec3(0.20, 0.35, 0.55), vec3(0.70, 0.82, 0.96), max(0.3, dayFac));
                    surface = mix(surface, pSkyCol + pSplash * (pEnt.z * 0.45), puddle * pFres * 0.88);
                }

                float rough = 0.56;
                float f0 = 0.032;
                float specMult = 0.25 * (1.0 - leafLike * 0.85);
                if (mClass == 1) {
                    rough = 0.36;
                    f0 = 0.048;
                    specMult = 0.55;
                } else if (mClass == 2) {
                    rough = 0.22;
                    f0 = 0.075;
                    specMult = 0.95;
                } else if (mClass == 3) {
                    rough = 0.17;
                    f0 = 0.24;
                    specMult = 1.45;
                } else if (mClass == 4) {
                    rough = 0.09;
                    f0 = 0.14;
                    specMult = 1.65;
                }
                #ifdef ALPHA_CUTOUT
                if (mClass != 4) {
                    rough = 0.68;
                    specMult *= 0.25;
                }
                #endif
                rough = mix(rough, 0.13, wet);
                f0 = mix(f0, 0.09, wet);
                specMult = mix(specMult, 1.15, wet);

                if (ShaderWorldA.x > 0.01 && nearDetail > 0.01) {
                    float sunSpec = ggxSpecular(N, sunDir, viewDir, rough, f0) * specMult * voxelSunShadow * skyPow * ShaderWorldA.x * nearDetail;
                    vec3 specTint = (mClass == 3) ? mix(sunCol, albedo * sunCol * 2.2, 0.45) : sunCol;
                    surface += specTint * sunSpec;

                    if (mClass >= 1 && mClass <= 4 || wet > 0.05) {
                        float ndotv = clamp(dot(N, viewDir), 0.0, 1.0);
                        float archFres = (f0 + (min(f0 * 4.5 + 0.06, 0.42) - f0) * pow(1.0 - ndotv, 4.0));
                        vec3 envCol = mix(warmTorch * (torchPool * 0.55), skyFill * 1.35, clamp(skyPow, 0.0, 1.0));
                        vec3 hRoom = normalize(vec3(0.18, 0.92, 0.34));
                        float roomGlint = ggxSpecular(N, hRoom, viewDir, max(rough, 0.18), f0) * 0.35;
                        float indoorVis = clamp(blockL * 0.85 + skyPow * 0.75 + wet * 0.8, 0.0, 1.0);
                        surface += envCol * ((archFres + roomGlint) * specMult * 0.42 * indoorVis * ShaderWorldA.x * nearDetail);
                    }
                }

                if (ShaderWorldA.y > 0.01) {
                    float maxTex = max(texCol.r, max(texCol.g, texCol.b));
                    float minTex = min(texCol.r, min(texCol.g, texCol.b));
                    float satTex = maxTex > 0.001 ? (maxTex - minTex) / maxTex : 0.0;
                    float emStr = ShaderWorldA.y;
                    vec3 emAdd = vec3(0.0);
                    float flicker = 1.0;
                    if (mClass == 5) {
                        float warmCore = smoothstep(0.38, 0.88, maxTex) * smoothstep(0.10, 0.65, texCol.r - texCol.b * 0.45 + maxTex * 0.25);
                        flicker = 1.0 + 0.035 * sin(ShaderWorldB.x * 7.2 + worldBlockPos.x * 2.1 + worldBlockPos.z * 3.5);
                        emAdd = mix(albedo, vec3(1.35, 0.68, 0.18), 0.35) * (warmCore * 1.45 * emStr);
                    } else if (mClass == 6) {
                        float coolCore = smoothstep(0.32, 0.85, maxTex);
                        float coolBias = smoothstep(0.05, 0.45, texCol.b + texCol.g - texCol.r * 0.75);
                        emAdd = albedo * (coolCore * mix(0.65, 1.85, coolBias) * 1.35 * emStr);
                    } else if (mClass == 7) {
                        float redCore = smoothstep(0.12, 0.55, texCol.r - max(texCol.g, texCol.b)) * smoothstep(0.30, 0.80, texCol.r);
                        emAdd = vec3(1.35, 0.14, 0.08) * (redCore * 1.35 * emStr);
                    } else if (mClass == 8 || (blockL >= 0.95 && maxTex > 0.65 && leafLike < 0.5)) {
                        emAdd = albedo * (smoothstep(0.45, 0.92, maxTex) * 1.15 * emStr);
                    } else if (satTex > 0.52 && maxTex > 0.62 && leafLike < 0.5 && blockL < 0.90) {
                        emAdd = albedo * (smoothstep(0.52, 0.85, satTex) * 0.22 * emStr);
                    }
                    surface = surface * flicker + emAdd;
                }

                float pk = max(surface.r, max(surface.g, surface.b));
                if (pk > 0.88) {
                    surface *= (0.88 + (pk - 0.88) / (1.0 + (pk - 0.88) * 1.25)) / pk;
                }
                color.rgb = pow(max(surface, vec3(0.0)), vec3(1.0 / 2.2));
            }
        }
        #endif
    }

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}