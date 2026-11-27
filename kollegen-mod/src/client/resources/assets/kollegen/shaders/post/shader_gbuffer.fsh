#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D VoxelShadowSampler;

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

float sampleDepthRaw(vec2 uv) {
    return texture(DepthSampler, clamp(uv, vec2(0.0005), vec2(0.9995))).r;
}

float linearizeDepth(float d, float zFar) {
    float zNear = 0.05;
    return (zNear * zFar) / max(zFar - d * (zFar - zNear), 0.0001);
}

vec3 viewPosFromUvZ(vec2 uv, float linZ) {
    vec2 ndc = vec2(uv.x * 2.0 - 1.0, (uv.y * 2.0 - 1.0) * ScreenYFlip);
    return vec3(ndc.x * Aspect * TanHalfFov * linZ, ndc.y * TanHalfFov * linZ, -linZ);
}

vec3 projectViewPos(vec3 vp) {
    float fwd = max(-vp.z, 0.001);
    vec2 suv = vec2(
        0.5 + 0.5 * (vp.x / (fwd * Aspect * TanHalfFov)),
        0.5 + 0.5 * ScreenYFlip * (vp.y / (fwd * TanHalfFov))
    );
    return vec3(suv, fwd);
}

float ign(vec2 coord) {
    vec3 magic = vec3(0.06711056, 0.00583715, 52.9829189);
    return fract(magic.z * fract(dot(coord, magic.xy)));
}

int sampleVoxelOccupied(ivec3 p) {
    if (p.x < 0 || p.x >= 128 || p.z < 0 || p.z >= 128 || p.y < 0 || p.y >= 64) {
        return -1;
    }
    int halfY = p.y >> 5;
    vec4 tex = texelFetch(VoxelShadowSampler, ivec2(p.x, p.z * 2 + halfY), 0);
    int byteIdx = (p.y >> 3) & 3;
    float ch = byteIdx == 0 ? tex.r : (byteIdx == 1 ? tex.g : (byteIdx == 2 ? tex.b : tex.a));
    int byteVal = int(ch * 255.0 + 0.5);
    return (byteVal >> (p.y & 7)) & 1;
}

float traceVoxelSunShadow(vec3 voxStart, vec3 sunW, vec3 tanU, vec3 tanV, float dither) {
    float accum = 0.0;
    const float golden = 2.3999632;
    for (int r = 0; r < 4; r++) {
        float fr = float(r);
        float ang = fr * golden + dither * 6.283185;
        float rad = sqrt((fr + 0.5) * 0.25) * 0.026;
        vec3 jOff = tanU * (cos(ang) * rad) + tanV * (sin(ang) * rad);
        vec3 rayDir = normalize(sunW + jOff);
        vec3 cur = voxStart + jOff * 3.0;
        float stepLen = 0.26;
        float hit = 0.0;
        for (int s = 0; s < 26; s++) {
            cur += rayDir * stepLen;
            stepLen = min(stepLen * 1.055, 1.05);
            ivec3 ip = ivec3(floor(cur));
            int occ = sampleVoxelOccupied(ip);
            if (occ < 0) {
                break;
            }
            if (occ == 1) {
                hit = 1.0;
                break;
            }
        }
        accum += hit;
    }
    return accum * 0.25;
}

void main() {
    vec4 inTexel = texture(InSampler, texCoord);
    vec3 srgbIn = inTexel.rgb;
    vec3 c = pow(max(srgbIn, vec3(0.0)), vec3(2.2));
    float rawDepth = sampleDepthRaw(texCoord);
    float zFar = max(FarPlane * 4.0, 128.0);
    float linZ = linearizeDepth(rawDepth, zFar);
    bool isSky = rawDepth >= 0.99998 || linZ >= zFar * 0.90;
    if (isSky) {
        linZ = zFar;
    }
    vec2 px = 1.0 / max(InSize, vec2(1.0, 1.0));
    float dither = ign(gl_FragCoord.xy);

    vec3 P = viewPosFromUvZ(texCoord, linZ);
    vec3 eyeDir = normalize(P);
    vec3 sunDir = normalize(vec3(SunViewX, SunViewY, SunViewZ));
    vec3 worldUp = normalize(vec3(WorldUpViewX, WorldUpViewY, WorldUpViewZ));
    float rtAmt = clamp(Raytracing, 0.0, 1.5);
    float realFac = clamp(Realism, 0.0, 1.5);
    float dimType = DimType;

    vec3 dPx = dFdx(P);
    vec3 dPy = dFdy(P);
    vec3 cr = cross(dPx, dPy);
    float crLen = length(cr);
    vec3 N = crLen > 1e-6 ? (cr / crLen) : worldUp;
    if (dot(N, -P) < 0.0) {
        N = -N;
    }

    float isHorizontal = smoothstep(0.86, 0.96, dot(N, worldUp));
    float edgeGrad = length(fwidth(srgbIn));
    float isSmooth = 1.0 - smoothstep(0.08, 0.24, edgeGrad);

    if (!isSky && (SSAO > 0.01 || rtAmt > 0.01) && linZ > 0.85 && linZ < 48.0) {
        float radiusPx = clamp(24.0 / max(linZ, 1.0), 2.0, 22.0);
        float giRadiusPx = clamp(42.0 / max(linZ, 1.0), 4.0, 36.0);
        float occ = 0.0;
        float weightSum = 0.0;
        vec3 bounceAccum = vec3(0.0);
        const float golden = 2.3999632;
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            float ang = fi * golden + dither * 6.283185;
            float frac = (fi + 0.4 + dither * 0.5) / 12.5;
            float r = frac * radiusPx;
            vec2 sampleUv = clamp(texCoord + vec2(cos(ang), sin(ang)) * (r * px), vec2(0.001), vec2(0.999));
            float sd = sampleDepthRaw(sampleUv);
            if (sd < 0.99998) {
                float sLin = linearizeDepth(sd, zFar);
                vec3 sP = viewPosFromUvZ(sampleUv, sLin);
                float dz = linZ - sLin;
                float rangeCheck = smoothstep(2.2, 0.18, abs(dz));
                float bias = 0.032 + linZ * 0.007;
                float hit = smoothstep(bias, bias + 0.62, dz) * rangeCheck;
                occ += hit;
                weightSum += 1.0;
            }
            if (rtAmt > 0.01 && linZ < 28.0) {
                vec2 gUv = clamp(texCoord + vec2(cos(ang + 0.5), sin(ang + 0.5)) * (frac * giRadiusPx * px), vec2(0.001), vec2(0.999));
                float gd = sampleDepthRaw(gUv);
                if (gd < 0.99998) {
                    float gLin = linearizeDepth(gd, zFar);
                    vec3 gP = viewPosFromUvZ(gUv, gLin);
                    vec3 gDelta = gP - P;
                    float dist3 = length(gDelta);
                    if (dist3 > 0.08 && dist3 < 5.0 && abs(linZ - gLin) < 2.2) {
                        vec3 gRaw = texture(InSampler, gUv).rgb;
                        vec3 gLinCol = pow(max(gRaw, vec3(0.0)), vec3(2.2));
                        float gLuma = dot(gLinCol, vec3(0.2126, 0.7152, 0.0722));
                        float emBoost = 1.0 + smoothstep(0.45, 0.95, gLuma) * 1.8;
                        float nd = max(dot(N, gDelta / dist3), 0.10);
                        float falloff = 1.0 / (1.0 + dist3 * dist3 * 0.38);
                        bounceAccum += gLinCol * (nd * falloff * emBoost);
                    }
                }
            }
        }
        float aoVal = weightSum > 0.0 ? (occ / weightSum) : 0.0;
        float distFade = smoothstep(48.0, 18.0, linZ);
        float ao = 1.0 - aoVal * SSAO * 0.68 * distFade;
        c *= clamp(ao, 0.28, 1.0);
        if (rtAmt > 0.01 && linZ < 28.0) {
            c += (c + vec3(0.03)) * (bounceAccum * (0.048 * rtAmt * smoothstep(28.0, 10.0, linZ)));
        }
    }

    if (!isSky && dimType < 0.5 && rtAmt > 0.01 && DayFactor > 0.12 && linZ > 0.6 && linZ < 58.0 && dot(N, sunDir) > 0.08) {
        vec3 camR = vec3(CamRightX, CamRightY, CamRightZ);
        vec3 camU = vec3(CamUpX, CamUpY, CamUpZ);
        vec3 camB = vec3(CamBackX, CamBackY, CamBackZ);
        float voxelHit = 0.0;
        if (VoxReady > 0.5) {
            vec3 relW = camR * P.x + camU * P.y + camB * P.z;
            vec3 worldN = normalize(camR * N.x + camU * N.y + camB * N.z);
            vec3 sunW = normalize(vec3(SunWorldX, SunWorldY, SunWorldZ));
            vec3 upRef = abs(sunW.y) > 0.92 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
            vec3 tanU = normalize(cross(upRef, sunW));
            vec3 tanV = cross(sunW, tanU);
            vec3 voxStart = vec3(CamVoxX, CamVoxY, CamVoxZ) + relW + worldN * 0.18 + sunW * 0.08;
            if (voxStart.x > 1.0 && voxStart.x < 127.0 && voxStart.z > 1.0 && voxStart.z < 127.0 && voxStart.y > 1.0 && voxStart.y < 63.0) {
                voxelHit = traceVoxelSunShadow(voxStart, sunW, tanU, tanV, dither);
            }
        }

        vec3 rayPos = P + N * (0.05 + linZ * 0.006);
        float stepLen = 0.15 + dither * 0.05;
        float ssHit = 0.0;
        for (int i = 0; i < 16; i++) {
            rayPos += sunDir * stepLen;
            stepLen *= 1.15;
            if (rayPos.z >= -0.06) {
                break;
            }
            vec3 proj = projectViewPos(rayPos);
            if (proj.x < 0.004 || proj.x > 0.996 || proj.y < 0.004 || proj.y > 0.996) {
                break;
            }
            float sd = sampleDepthRaw(proj.xy);
            if (sd < 0.99998) {
                float sLin = linearizeDepth(sd, zFar);
                float diff = proj.z - sLin;
                float minThick = 0.03 + sLin * 0.006;
                float maxThick = 1.15 + sLin * 0.045;
                if (diff > minThick && diff < maxThick) {
                    float w = 1.0 - float(i) / 17.0;
                    ssHit = max(ssHit, w);
                }
            }
        }
        float shadowHit = max(voxelHit, ssHit * 0.90);
        float sFade = smoothstep(58.0, 32.0, linZ) * smoothstep(0.08, 0.30, dot(N, sunDir)) * smoothstep(0.12, 0.38, DayFactor) * (1.0 - RainWet * 0.65);
        vec3 shadowTint = vec3(0.24, 0.35, 0.58);
        c = mix(c, c * shadowTint, shadowHit * sFade * clamp(rtAmt * 0.68, 0.0, 0.82));
    }

    if (!isSky && rtAmt > 0.01 && linZ > 0.9 && linZ < 105.0) {
        float blueDom = srgbIn.b - max(srgbIn.r * 1.03, srgbIn.g * 0.96);
        float waterMask = smoothstep(0.025, 0.085, blueDom) * isSmooth * smoothstep(0.05, 0.20, WaterShimmer);
        float puddleHint = smoothstep(0.015, 0.065, srgbIn.b - srgbIn.r * 1.02) * step(srgbIn.g * 0.97, srgbIn.b) * isHorizontal * isSmooth * smoothstep(0.05, 0.25, RainWet) * smoothstep(68.0, 24.0, linZ);
        float ndotvSurf = clamp(dot(-eyeDir, N), 0.0, 1.0);
        float archGloss = isSmooth * smoothstep(0.75, 0.15, ndotvSurf) * pow(1.0 - ndotvSurf, 3.2) * smoothstep(32.0, 8.0, linZ) * smoothstep(0.25, 0.9, rtAmt);
        bool isPuddlePix = puddleHint > 0.12 && waterMask < 0.25;
        float surfMask = max(max(waterMask, puddleHint * 0.88), archGloss * 0.42);
        float downView = dot(eyeDir, N);
        bool allowTrace = (waterMask > 0.12 || puddleHint > 0.12) ? (dot(eyeDir, worldUp) < -0.015) : (archGloss > 0.08 && downView < -0.02);
        if (surfMask > 0.08 && allowTrace) {
            bool isWetSurf = (waterMask > 0.12 || puddleHint > 0.12);
            vec3 surfN = isWetSurf ? worldUp : N;
            if (isWetSurf) {
                vec2 wUv = texCoord * vec2(Aspect, 1.0);
                float ripAmp = isPuddlePix ? 0.004 : (0.014 * WaterShimmer);
                float ripX = sin(wUv.x * 42.0 + wUv.y * 18.0 + GameTime * 2.1) * ripAmp;
                float ripZ = cos(wUv.y * 36.0 - wUv.x * 24.0 + GameTime * 1.7) * ripAmp;
                vec3 upCross = cross(-eyeDir, worldUp);
                float upLen = length(upCross);
                if (upLen > 0.05) {
                    vec3 rightView = upCross / upLen;
                    vec3 fwdHoriz = normalize(cross(worldUp, rightView));
                    surfN = normalize(worldUp + rightView * ripX + fwdHoriz * ripZ);
                }
            }
            vec3 reflDir = reflect(eyeDir, surfN);
            if (!isWetSurf || dot(reflDir, worldUp) > 0.004) {
                float estSurfZ = (isWetSurf && !isPuddlePix) ? clamp(1.85 / max(-dot(eyeDir, worldUp), 0.06), 0.9, linZ) : linZ;
                vec3 startPos = ((isWetSurf && !isPuddlePix) ? (eyeDir * estSurfZ) : P) + surfN * 0.08;
                vec3 rayPos = startPos;
                vec3 prevPos = rayPos;
                float stepLen = isWetSurf ? (isPuddlePix ? 0.16 : 0.24) : 0.20;
                stepLen += dither * 0.05;
                vec2 hitUv = vec2(-1.0);
                bool foundNear = false;
                for (int i = 0; i < 20; i++) {
                    prevPos = rayPos;
                    rayPos += reflDir * stepLen;
                    stepLen *= 1.18;
                    if (rayPos.z >= -0.08) {
                        break;
                    }
                    vec3 proj = projectViewPos(rayPos);
                    if (proj.x < 0.006 || proj.x > 0.994 || proj.y < 0.006 || proj.y > 0.994) {
                        break;
                    }
                    float sd = sampleDepthRaw(proj.xy);
                    if (sd < 0.99995) {
                        float sLin = linearizeDepth(sd, zFar);
                        float thickness = max(stepLen * 1.4, 0.35 + sLin * 0.03);
                        if (sLin > 0.85 && proj.z > sLin + 0.03 && proj.z < sLin + thickness) {
                            vec3 lo = prevPos;
                            vec3 hi = rayPos;
                            for (int b = 0; b < 4; b++) {
                                vec3 mid = (lo + hi) * 0.5;
                                vec3 mProj = projectViewPos(mid);
                                float mSd = sampleDepthRaw(mProj.xy);
                                float mLin = mSd >= 0.99995 ? zFar : linearizeDepth(mSd, zFar);
                                if (mProj.z > mLin) {
                                    hi = mid;
                                } else {
                                    lo = mid;
                                }
                            }
                            vec3 fineProj = projectViewPos(hi);
                            float fineSd = sampleDepthRaw(fineProj.xy);
                            if (fineSd < 0.99995) {
                                float fineLin = linearizeDepth(fineSd, zFar);
                                vec3 hitP = viewPosFromUvZ(fineProj.xy, fineLin);
                                if (fineLin > 0.85 && (!isWetSurf || dot(hitP - startPos, worldUp) >= -0.30)) {
                                    hitUv = fineProj.xy;
                                    foundNear = true;
                                }
                            }
                            break;
                        }
                    }
                }
                if (!foundNear && isWetSurf) {
                    vec3 reflFar = projectViewPos(reflDir * 64.0);
                    vec2 fUv = reflFar.xy;
                    if (fUv.x > 0.008 && fUv.x < 0.992 && fUv.y > 0.008 && fUv.y < 0.992) {
                        float rSd = sampleDepthRaw(fUv);
                        float rLin = rSd >= 0.99995 ? zFar : linearizeDepth(rSd, zFar);
                        if (rLin > max(estSurfZ * 0.65, 1.25)) {
                            hitUv = fUv;
                        }
                    }
                }
                if (hitUv.x > 0.008 && hitUv.x < 0.992 && hitUv.y > 0.008 && hitUv.y < 0.992) {
                    float edge = smoothstep(0.008, 0.08, hitUv.x) * smoothstep(0.992, 0.92, hitUv.x) *
                                 smoothstep(0.008, 0.08, hitUv.y) * smoothstep(0.992, 0.92, hitUv.y);
                    vec3 reflCol = pow(max(texture(InSampler, hitUv).rgb, vec3(0.0)), vec3(2.2));
                    float ndv = clamp(dot(-eyeDir, surfN), 0.0, 1.0);
                    float fres = isWetSurf
                        ? clamp(mix(0.20, 0.36, min(realFac, 1.2)) + 0.78 * pow(1.0 - ndv, 2.3), 0.0, 1.0)
                        : clamp(0.10 + 0.55 * pow(1.0 - ndv, 3.0), 0.0, 0.55);
                    float reflWeight = isWetSurf ? mix(0.68, 0.90, min(realFac, 1.2)) : 0.42;
                    c = mix(c, reflCol, clamp(edge * fres * surfMask * rtAmt * reflWeight * smoothstep(105.0, 52.0, linZ), 0.0, isWetSurf ? 0.88 : 0.34));
                }
            }
        }
    }

    if (!isSky && dimType < 0.5 && DepthFog > 0.01) {
        float fogDist = max(linZ - 34.0, 0.0);
        float fogRate = (0.0012 + 0.0032 * RainWet) * DepthFog * mix(0.85, 1.15, min(realFac, 1.2));
        float fogFactor = 1.0 - exp(-pow(fogDist * fogRate, 1.25));
        fogFactor = clamp(fogFactor, 0.0, mix(0.58, 0.78, RainWet));

        float sunCos = max(dot(eyeDir, sunDir), 0.0);
        float sunScatter = pow(sunCos, 6.0) * smoothstep(0.08, 0.45, DayFactor);
        float sunsetAmt = 1.0 - abs(DayFactor * 2.0 - 1.0);

        vec3 nightMist = vec3(0.010, 0.022, 0.065);
        vec3 violetPeach = mix(vec3(0.38, 0.18, 0.28), vec3(1.12, 0.42, 0.14), sunScatter);
        vec3 sunsetMist = mix(vec3(0.75, 0.28, 0.12), violetPeach, min(realFac, 1.1));
        vec3 dayMist = vec3(0.24, 0.44, 0.78);
        vec3 baseMist = mix(nightMist, mix(dayMist, sunsetMist, sunsetAmt * 0.72), smoothstep(0.05, 0.45, DayFactor));
        baseMist = mix(baseMist, vec3(0.22, 0.27, 0.34), RainWet * 0.65);
        vec3 skyHaze = mix(baseMist, mix(vec3(0.40, 0.58, 0.92), vec3(1.28, 0.74, 0.32), DayFactor), sunScatter * (0.55 + 0.16 * realFac) * (1.0 - RainWet * 0.7));
        c = mix(c, skyHaze, fogFactor);
    }

    if (dimType < 0.5 && GodRays > 0.01 && SunVisibility > 0.01 && DayFactor > 0.08) {
        float sunsetAmt = 1.0 - abs(DayFactor * 2.0 - 1.0);
        vec3 shaftTint = mix(
            vec3(1.32, 0.48, 0.12),
            vec3(1.28, 0.98, 0.52),
            smoothstep(0.18, 0.72, DayFactor)
        );
        shaftTint = mix(shaftTint, vec3(1.42, 0.58, 0.14), sunsetAmt * 0.48);

        vec2 sunPos = vec2(SunScreenX, SunScreenY);
        vec2 delta = (sunPos - texCoord) / 24.0;
        vec2 cur = texCoord + delta * dither;
        float rayAccum = 0.0;
        float decay = 1.0;
        for (int i = 0; i < 24; i++) {
            vec2 suv = clamp(cur, vec2(0.001), vec2(0.999));
            float d = sampleDepthRaw(suv);
            if (d >= 0.99992) {
                rayAccum += decay;
            }
            cur += delta;
            decay *= 0.93;
        }
        vec2 ar = vec2(Aspect, 1.0);
        float toSunDist = length((sunPos - texCoord) * ar);
        float sunCone = smoothstep(1.36, 0.0, toSunDist);
        float edgeFade = smoothstep(-0.45, 0.08, SunScreenX) * smoothstep(1.45, 0.92, SunScreenX) *
                         smoothstep(-0.45, 0.08, SunScreenY) * smoothstep(1.45, 0.92, SunScreenY);
        float rayScale = mix(0.42, 0.58, min(realFac, 1.25));
        float radialShaft = (rayAccum / 14.0) * GodRays * SunVisibility * sunCone * edgeFade * (1.0 - RainWet * 0.70) * rayScale;
        c += shaftTint * radialShaft;

        if (realFac > 0.25 && edgeFade > 0.01) {
            float sunClear = 0.0;
            sunClear += step(0.9999, sampleDepthRaw(clamp(sunPos, vec2(0.002), vec2(0.998))));
            sunClear += step(0.9999, sampleDepthRaw(clamp(sunPos + vec2(px.x * 8.0, 0.0), vec2(0.002), vec2(0.998))));
            sunClear += step(0.9999, sampleDepthRaw(clamp(sunPos - vec2(px.x * 8.0, 0.0), vec2(0.002), vec2(0.998))));
            sunClear += step(0.9999, sampleDepthRaw(clamp(sunPos + vec2(0.0, px.y * 8.0), vec2(0.002), vec2(0.998))));
            sunClear += step(0.9999, sampleDepthRaw(clamp(sunPos - vec2(0.0, px.y * 8.0), vec2(0.002), vec2(0.998))));
            sunClear /= 5.0;
            float flareGain = sunClear * SunVisibility * edgeFade * smoothstep(0.25, 1.0, realFac) * (1.0 - RainWet * 0.85);
            if (flareGain > 0.005) {
                float aureole = exp(-toSunDist * toSunDist * 9.5) * 0.14;
                vec2 oppSun = vec2(1.0) - sunPos;
                float ringDist = length((texCoord - oppSun) * ar);
                float haloArc = exp(-pow(ringDist - 0.26, 2.0) * 165.0) * 0.10;
                vec2 ghostPos = mix(sunPos, oppSun, 0.62);
                float ghostDist = length((texCoord - ghostPos) * ar);
                float ghostOrb = exp(-ghostDist * ghostDist * 95.0) * 0.08;
                c += shaftTint * (aureole * flareGain * min(GodRays, 1.0));
                c += vec3(0.22, 0.45, 1.15) * (haloArc * flareGain * 0.85);
                c += vec3(0.90, 0.64, 0.28) * (ghostOrb * flareGain);
            }
        }
    }

    float pk = max(c.r, max(c.g, c.b));
    if (pk > 0.90) {
        c *= (0.90 + (pk - 0.90) / (1.0 + (pk - 0.90) * 1.25)) / pk;
    }
    fragColor = vec4(pow(max(c, vec3(0.0)), vec3(1.0 / 2.2)), inTexel.a);
}