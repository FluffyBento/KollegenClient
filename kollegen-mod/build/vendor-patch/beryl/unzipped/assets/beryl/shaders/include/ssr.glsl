#define BINARY_SEARCH

#define TRACE_DIST 100
#define MAX_ITERATION 2000

#ifdef BINARY_SEARCH
    #define STEP_SIZE 4
    #define MAX_THICKNESS 0.002
#else
    #define STEP_SIZE 1
    #define MAX_THICKNESS 0.00005
#endif

#define BINARY_STEPS 4
#define MAX_THICKNESS_2 0.0002
#define MAX_THICKNESS_VS 2

float computeDepthVS(float z) {
    return -ProjMat[3][2] / (z + ProjMat[2][2]);
}

float computeDepthCS(float z) {
    return -ProjMat[3][2] / z - ProjMat[2][2];
}

float computeThicknessVS(float depth1, float depth2) {
    return computeDepthVS(depth1) - computeDepthVS(depth2);
}

float findIntersection_Linear2(vec3 samplePosInTS, vec3 vReflDirInTS, float maxTraceDistance, ivec2 viewSize, inout vec3 intersection);

float findIntersectionDynamic(vec3 samplePosInTS, vec3 vReflDirInTS, float maxTraceDistance, ivec2 viewSize, inout vec3 intersection);

float findIntersectionDynamicVS(vec3 samplePosInVS, vec3 samplePosInTS,
                              vec3 vReflDirInVS, vec3 vReflDirInTS,
                              float maxTraceDistance, ivec2 viewSize, inout vec3 intersection);

float findIntersectionDynamicVS2(vec3 samplePosInVS, vec3 samplePosInTS,
                                vec3 vReflDirInVS, vec3 vReflDirInTS,
                                float maxTraceDistance, ivec2 viewSize, inout vec3 intersection);

vec3 hash(vec3 a);

float hash(vec2 a) {
    a = fract(a * 0.5);
    a += dot(a, a.yx + 1.3);
    return fract(a.x * a.y + a.x);
}

vec3 ssr(vec3 fragPosVS, vec4 vReflectionInVS, vec3 skyColor) {

//    vReflectionInVS.xyz += 0.001 * hash(10 * fragPosVS);
    float noise = 0.001 * hash(gl_FragCoord.xy);
    vReflectionInVS.xyz += noise;

    vec4 samplePosInVS = vec4(fragPosVS.xyz, 1.0);
    vec4 vReflectionEndPosInVS = samplePosInVS + vReflectionInVS * TRACE_DIST;

    //    vReflectionEndPosInVS /= (-vReflectionEndPosInVS.z < 0.0 ? -vReflectionEndPosInVS.z : 1.0);

    vec4 vReflectionEndPosInCS = ProjMat * vec4(vReflectionEndPosInVS.xyz, 1.0);

    if(vReflectionEndPosInCS.w <= 0) {
        return skyColor;
    }

    vReflectionEndPosInCS /= vReflectionEndPosInCS.w;

    vec4 samplePosInCS = ProjMat * samplePosInVS;
    samplePosInCS /= samplePosInCS.w;

    vec3 vReflectionDir = normalize((vReflectionEndPosInCS - samplePosInCS).xyz);

    samplePosInCS.xy *= vec2(0.5f, -0.5f);
    samplePosInCS.xy += vec2(0.5f, 0.5f);

    vReflectionDir.xy *= vec2(0.5f, -0.5f);

    vec3 samplePosInTS = samplePosInCS.xyz;
    vec3 reflDirInTS = vReflectionDir;

    float maxDistance = reflDirInTS.x>=0 ? (1.1 - samplePosInTS.x)/ reflDirInTS.x  : -(samplePosInTS.x + 0.1)/ reflDirInTS.x;
    maxDistance = min(maxDistance, reflDirInTS.y<0 ? (-samplePosInTS.y/ reflDirInTS.y)  : ((1.0- samplePosInTS.y)/ reflDirInTS.y));
    maxDistance = min(maxDistance, reflDirInTS.z<0 ? (-samplePosInTS.z/ reflDirInTS.z) : ((1.0- samplePosInTS.z)/ reflDirInTS.z));

    vec3 intersection = vec3(0.0);
    ivec2 viewSize = textureSize(Framebuffer, 0);
//    float intensity = findIntersection_Linear2(samplePosInTS, reflDirInTS, maxDistance, viewSize, intersection);
//    float intensity = findIntersectionDynamic(samplePosInTS, reflDirInTS, maxDistance, viewSize, intersection);
//    float intensity = findIntersectionDynamicVS(samplePosInVS.xyz, samplePosInTS, vReflectionInVS.xyz, reflDirInTS, maxDistance, viewSize, intersection);
    float intensity = findIntersectionDynamicVS2(samplePosInVS.xyz, samplePosInTS, vReflectionInVS.xyz, reflDirInTS, maxDistance, viewSize, intersection);

    vec2 dCoords = smoothstep(0.5, 0.52, abs(vec2(0.5, 0.5) - intersection.xy) + vec2(0.0, 0.02));

    float screenEdgeFactor = clamp(1.0 - (dCoords.x + dCoords.y), 0.0, 1.0);
    intensity *= screenEdgeFactor;

//    intersection.xy += noise;
    vec3 reflectedPos = vec3(intersection.xy, intensity);

    vec3 reflectedColor = texture(Framebuffer, reflectedPos.xy).rgb;
    reflectedColor = mix(skyColor, reflectedColor, intensity);

    return reflectedColor;
}

vec3 BinarySearch2(inout vec3 rayPos, inout bool intersected, vec3 dir, vec2 viewSize)
{
    float depth;
    float thickness;

    vec4 projectedCoord;

    //    vec3 startPos = (hitIndex - 1) * dir + startPos;
    //    vec3 pos2 = pos1 + dir;
    //    vec3 t = pos1;

    //    dir *= 0.5;
    //    rayPos += dir;

    depth = texture(Depthbuffer, rayPos.xy).x;
    thickness = rayPos.z - depth;

    vec3 bRayPos = rayPos;
    float bThickness = thickness;

    for(int i = 0; i < BINARY_STEPS; i++)
    {

        //        projectedCoord = projection * vec4(hitCoord, 1.0);
        //        projectedCoord.xy /= projectedCoord.w;
        //        projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
        //
        //        depth = textureLod(gPosition, projectedCoord.xy, 2).z;

        dir *= 0.5;
        rayPos += thickness >= 0 ? -dir : +dir;

        depth = texture(Depthbuffer, rayPos.xy).x;
        //        depth = texelFetch(Depthbuffer, ivec2(rayPos.xy * viewSize), 0).x;
        thickness = rayPos.z - depth;

        bool b = abs(thickness) < abs(bThickness) && thickness >= 0;
        //        bool b = abs(thickness) < abs(bThickness);
        bRayPos = b ? rayPos : bRayPos;
        bThickness = b ? thickness : bThickness;
        rayPos = bRayPos;
        thickness = bThickness;

        //        dDepth = hitCoord.z - depth;
        //
        //        dir *= 0.5;
        //        if(dDepth > 0.0)
        //            hitCoord += dir;
        //        else
        //            hitCoord -= dir;
    }

    //    projectedCoord = projection * vec4(hitCoord, 1.0);
    //    projectedCoord.xy /= projectedCoord.w;
    //    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;

    //    thickness = abs(thickness);
    //    intersected = thickness < MAX_THICKNESS_2 ? true : false;
    //    return vec3(rayPos.xy, depth);

    thickness = abs(bThickness);
    depth = texture(Depthbuffer, bRayPos.xy).x;
    //    depth = texelFetch(Depthbuffer, ivec2(bRayPos.xy * viewSize), 0).x;
    //    intersected = thickness < MAX_THICKNESS_2 && bThickness >= 0;
//    intersected = thickness < MAX_THICKNESS_2;
    return vec3(bRayPos.xy, depth);
}

float findIntersection_Linear2(vec3 samplePosInTS,
                               vec3 vReflDirInTS,
                               float maxTraceDistance,
                               ivec2 viewSize,
                                inout vec3 intersection)
{

    vec3 vReflectionEndPosInTS = samplePosInTS + vReflDirInTS * maxTraceDistance;

    vec3 dp = vReflectionEndPosInTS.xyz - samplePosInTS.xyz;
    ivec2 sampleScreenPos = ivec2(samplePosInTS.xy * viewSize.xy);
    ivec2 endPosScreenPos = ivec2(vReflectionEndPosInTS.xy * viewSize.xy);
    ivec2 dp2 = endPosScreenPos - sampleScreenPos;
    const int max_dist = max(abs(dp2.x), abs(dp2.y)) / STEP_SIZE;
    dp /= max_dist;

    vec4 rayPosInTS = vec4(samplePosInTS.xyz + dp, 0);
    vec4 vRayDirInTS = vec4(dp.xyz, 0);
    vec4 rayStartPos = rayPosInTS;

    // Version 0.
        int hitIndex = -1;
        for(int i = 0; i < max_dist && i < MAX_ITERATION; i++)
        {
            float depth = texture(Depthbuffer, rayPosInTS.xy).x;
//            ivec2 rayPosSS = ivec2(rayPosInTS.xy * viewSize.xy);
//            float depth = texelFetch(Depthbuffer, rayPosSS, 0).x;

//            float rayDepthVS = computeDepthVS(rayPosInTS.z);
//            float depthVS = computeDepthVS(depth);
////            float thickness = rayDepthVS - depthVS;
//            float thickness = -(rayDepthVS - depthVS);
//            if (thickness >= 0 && thickness < MAX_THICKNESS_VS)
            float thickness = rayPosInTS.z - depth;
            if (thickness>=0 && thickness< MAX_THICKNESS)
            {
                hitIndex = i;
                break;
            }

//            if (abs(thickness) < MAX_THICKNESS) {
//                hitIndex = i;
//                break;
//            }

            rayPosInTS += vRayDirInTS;
        }

//    int hitIndex = -1;
//    int i;
//    for(i = 0;i<=max_dist && i<MAX_ITERATION;i += 4)
//    {
//        float depth0 = 0;
//        float depth1 = 0;
//        float depth2 = 0;
//        float depth3 = 0;
//
//        vec4 rayPosInTS0 = rayPosInTS+vRayDirInTS*0;
//        vec4 rayPosInTS1 = rayPosInTS+vRayDirInTS*1;
//        vec4 rayPosInTS2 = rayPosInTS+vRayDirInTS*2;
//        vec4 rayPosInTS3 = rayPosInTS+vRayDirInTS*3;
//
//        depth3 = texture(Depthbuffer, rayPosInTS3.xy).x;
//        depth2 = texture(Depthbuffer, rayPosInTS2.xy).x;
//        depth1 = texture(Depthbuffer, rayPosInTS1.xy).x;
//        depth0 = texture(Depthbuffer, rayPosInTS0.xy).x;
//
//        //            depth3 = texelFetch(Depthbuffer, ivec2(rayPosInTS3.xy * viewSize.xy), 0).x;
//        //            depth2 = texelFetch(Depthbuffer, ivec2(rayPosInTS2.xy * viewSize.xy), 0).x;
//        //            depth1 = texelFetch(Depthbuffer, ivec2(rayPosInTS1.xy * viewSize.xy), 0).x;
//        //            depth0 = texelFetch(Depthbuffer, ivec2(rayPosInTS0.xy * viewSize.xy), 0).x;
//
//        {
//            float thickness = rayPosInTS3.z - depth3;
//            hitIndex = (thickness>=0 && thickness < MAX_THICKNESS) ? (i+3) : hitIndex;
//        }
//        {
//            float thickness = rayPosInTS2.z - depth2;
//            hitIndex = (thickness>=0 && thickness < MAX_THICKNESS) ? (i+2) : hitIndex;
//        }
//        {
//            float thickness = rayPosInTS1.z - depth1;
//            hitIndex = (thickness>=0 && thickness < MAX_THICKNESS) ? (i+1) : hitIndex;
//        }
//        {
//            float thickness = rayPosInTS0.z - depth0;
//            hitIndex = (thickness>=0 && thickness < MAX_THICKNESS) ? (i+0) : hitIndex;
//        }
//
//        if(hitIndex != -1) break;
//
//        rayPosInTS = rayPosInTS3 + vRayDirInTS;
//    }

    bool intersected = hitIndex >= 0;

    #ifndef BINARY_SEARCH
    intersection = rayStartPos.xyz + vRayDirInTS.xyz * hitIndex;
    #else
    vec3 pos1 = vec3(rayStartPos.xyz + vRayDirInTS.xyz * hitIndex);
    vec3 dir = vec3(vRayDirInTS.xyz);
    intersection = intersected ? BinarySearch2(pos1, intersected, dir, viewSize) : vec3(0.0);
    //    intersection = rayStartPos.xyz + vRayDirInTS.xyz * hitIndex;
    #endif

    //Debug
    ivec2 intersectScreenPos = ivec2(intersection.xy * viewSize.xy);

    float intensity = intersected ? 1 : 0;
    //    intensity = intersection.z >= 1.0 ? 0 : intensity;
    intensity = intersection.z >= 0.99995 ? 0 : intensity;
    //    float intensity = length(vRayDirInTS.xyz * hitIndex) / maxTraceDistance;
    //    intensity = intersected ? intensity : 0.0;

    //    intensity = clamp(1.0 - (intersection.z - 0.9995) / 0.00045, 0.0, 1.0);

//    float maxDepth = ProjMat[3][2] / (0.8*FogEnd) - ProjMat[2][2];
//    intensity = intersection.z >= maxDepth ? 0 : intensity;

    //Debug
    //    intensity = 1;
    ////    intersection = vReflectionEndPosInTS;
    //    intersection = samplePosInTS + 100 * vRayDirInTS.xyz;

    return intensity;

    //    return i / 1000.0;
}

float findIntersectionDynamicVS(vec3 samplePosInVS,
                              vec3 samplePosInTS,
                              vec3 vReflDirInVS,
                              vec3 vReflDirInTS,
                              float maxTraceDistance,
                              ivec2 viewSize,
                              inout vec3 intersection)
{

    vec3 rayStepVS = vReflDirInVS;
    vec3 rayDirTS = vReflDirInTS;
    float invRayStepZ = 1.0 / rayDirTS.z;

    vec3 vReflectionEndPosInTS = samplePosInTS + vReflDirInTS * maxTraceDistance;

    rayStepVS *= 0.25 + clamp((-samplePosInVS.z - 10.0) * 0.2, 0.0, 0.5);
    vec3 rayVS = samplePosInVS + rayStepVS;

    vec3 rayTS;
    float thicknessTreshold = 3.0;
    int hitIndex = -1;
    float thickness;
    for (int i = 0; i < 40; i++)
    {
        float rayDepthCS = computeDepthCS(rayVS.z);
        // interpolation factor
        float f = (rayDepthCS - samplePosInTS.z) * invRayStepZ;
        rayTS = samplePosInTS + f * rayDirTS;

        float depth = texture(Depthbuffer, rayTS.xy).x;
        float rayDepthVS = rayVS.z;
        float depthVS = computeDepthVS(depth);
        thickness = -(rayDepthVS - depthVS);

        float targetThickness = 0.1 + max(-rayVS.z - 10.0, 0.0)  * 0.1;
        if (abs(thickness) < targetThickness || abs(rayTS.y) >= 1.0) {
            hitIndex = i;
            break;
        }

        if (abs(thickness) < thicknessTreshold)
        {
            rayStepVS *= 0.25;
            thicknessTreshold *= 0.25;
        }

        rayStepVS *= 2;
        thicknessTreshold *= 2;
        rayVS += rayStepVS;
    }

    bool intersected = true;

    float targetThickness = 1.0 - rayVS.z * 0.5;
    if (abs(thickness) < targetThickness) {
        intersected = true;
    } else {
        intersected = false;
    }

    intersection = rayTS;

    //Debug
//    ivec2 intersectScreenPos = ivec2(intersection.xy * viewSize.xy);

    float intensity = intersected ? 1 : 0;
    intensity = intersection.z >= 0.99995 ? 0 : intensity;

    return intensity;
}

float findIntersectionDynamicVS2(vec3 samplePosInVS,
                                vec3 samplePosInTS,
                                vec3 vReflDirInVS,
                                vec3 vReflDirInTS,
                                float maxTraceDistance,
                                ivec2 viewSize,
                                inout vec3 intersection)
{

    vec3 rayStepVS = vReflDirInVS;
    vec3 rayDirTS = vReflDirInTS;
    float invRayStepZ = 1.0 / rayDirTS.z;

    float oThicknessTreshold = 0.1;
    rayStepVS *= 0.25 + clamp((-samplePosInVS.z - 10.0) * 0.02, 0.0, 0.5);
    //    rayStepVS *= 0.25;
    vec3 oRayStepVS = rayStepVS;

    int level = 4;
    oRayStepVS /= 16;
    vec3 rayVS = samplePosInVS + oRayStepVS * (1 << level);

//    int maxLevel = min(4 + int(abs(2.0 / vReflDirInVS.y)), 10);
//    float thicknessMul = 0.25 + min(0.01 / vReflDirInVS.y, 0.15);
    float thicknessMul = 0.2;
    int maxLevel = 16;

    vec3 rayTS;
    float thicknessTreshold = oThicknessTreshold * (1 << level);
    int hitIndex = -1;
    float thickness;
    for (int i = 0; i < 40; i++)
    {
        float rayDepthCS = computeDepthCS(rayVS.z);
        // interpolation factor
        float f = (rayDepthCS - samplePosInTS.z) * invRayStepZ;
        rayTS = samplePosInTS + f * rayDirTS;

        float depth = texture(Depthbuffer, rayTS.xy).x;

        float rayDepthVS = rayVS.z;
        float depthVS = computeDepthVS(depth);
        thickness = -(rayDepthVS - depthVS);

        float targetThickness = 0.2 + max((-rayVS.z + samplePosInVS.z), 0.0) * thicknessMul;

        if (abs(thickness) < targetThickness || abs(rayTS.y) >= 1.0 || level == 0) {
            hitIndex = i;
            break;
        }

        if (abs(thickness) < thicknessTreshold)
        {
            level = max(level - 2, 0);
        }

        level = min(level + 1, maxLevel);

        int mult = (1 << level);
        rayStepVS = oRayStepVS * mult;
        thicknessTreshold = oThicknessTreshold * mult;
        rayVS += rayStepVS;
    }

    bool intersected = true;

    float targetThickness = 0.2 + max((-rayVS.z + samplePosInVS.z), 0.0) * thicknessMul;
    if (abs(thickness) < targetThickness) {
        intersected = true;
    } else {
        intersected = false;
    }

    intersection = rayTS;

    //Debug
//    ivec2 intersectScreenPos = ivec2(intersection.xy * viewSize.xy);

    float intensity = intersected ? 1 : 0;
    intensity = intersection.z >= 0.99995 ? 0 : intensity;

    return intensity;
}

float findIntersectionDynamic(vec3 samplePosInTS,
                               vec3 vReflDirInTS,
                               float maxTraceDistance,
                               ivec2 viewSize,
                                inout vec3 intersection)
{

    vec3 vReflectionEndPosInTS = samplePosInTS + vReflDirInTS * maxTraceDistance;

    vec3 dp = vReflectionEndPosInTS.xyz - samplePosInTS.xyz;
    ivec2 sampleScreenPos = ivec2(samplePosInTS.xy * viewSize.xy);
    ivec2 endPosScreenPos = ivec2(vReflectionEndPosInTS.xy * viewSize.xy);
    ivec2 dp2 = endPosScreenPos - sampleScreenPos;
    const int max_dist = max(abs(dp2.x), abs(dp2.y)) / STEP_SIZE;
    dp /= max_dist;

    vec3 rayPosInTS = samplePosInTS.xyz + dp;
    vec3 vRayDirInTS = dp.xyz;
    vec3 rayStep = dp.xyz;
    vec3 rayStartPos = rayPosInTS;

    float thicknessTreshold = 0.002;
    int hitIndex = -1;
    for (int i = 0; i < 100; i++)
    {
        float depth = texture(Depthbuffer, rayPosInTS.xy).x;

        float thickness = rayPosInTS.z - depth;
        if (thickness >= 0 && thickness < 0.0005)
        {
            hitIndex = i;
            break;
        }

        if (abs(thickness) < thicknessTreshold) {
            rayStep *= 0.25;
            thicknessTreshold *= 0.25;
        }

        rayStep *= 2;
        thicknessTreshold *= 2;

        rayPosInTS += vRayDirInTS;
    }

    bool intersected = hitIndex >= 0;

    intersection = rayPosInTS;

    //Debug
    ivec2 intersectScreenPos = ivec2(intersection.xy * viewSize.xy);

    float intensity = intersected ? 1 : 0;
    intensity = intersection.z >= 0.99995 ? 0 : intensity;

    return intensity;
}