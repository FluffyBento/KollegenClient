#define PI 3.14159265359

const vec2 poissonDisk[16] = vec2[](
vec2( -0.94201624, -0.39906216 ),
vec2( 0.94558609, -0.76890725 ),
vec2( -0.094184101, -0.92938870 ),
vec2( 0.34495938, 0.29387760 ),
vec2( -0.91588581, 0.45771432 ),
vec2( -0.81544232, -0.87912464 ),
vec2( -0.38277543, 0.27676845 ),
vec2( 0.97484398, 0.75648379 ),
vec2( 0.44323325, -0.97511554 ),
vec2( 0.53742981, -0.47373420 ),
vec2( -0.26496911, -0.41893023 ),
vec2( 0.79197514, 0.19090188 ),
vec2( -0.24188840, 0.99706507 ),
vec2( -0.81409955, 0.91437590 ),
vec2( 0.19984126, 0.78641367 ),
vec2( 0.14383161, -0.14100790 )
);

float ShadowCalculation(sampler2DShadow shadowMap, vec4 fragPosLightSpace, float texelSize, float bias)
{
    //    fragPosLightSpace.xyz = distortShadowClipPos(fragPosLightSpace.xyz);

    // perform perspective divide
    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;

    float distX = abs(projCoords.x);
    float distY = abs(projCoords.y);
    float dist = max(distX, distY);

    // transform to [0,1] range
    projCoords.y = -projCoords.y;
    projCoords.xy = projCoords.xy * 0.5 + 0.5;

    if (projCoords.x < 0.0 || projCoords.x > 1.0
    || projCoords.y < 0.0 ||  projCoords.y > 1.0
    || projCoords.z < 0.0 || projCoords.z > 1.0)
    {
        return 1.0;
    }

    // PCF
    float currentDepth = projCoords.z;
    float shadow = 0.0;
    //TODO texture size uniform
    float pcfDepth = 0.0;

    bias += 0.002 * dist * dist;

    // Poisson fixed disc 4 taps
    //    vec2 texelSizeM = 1.3 * vec2(texelSize);
    //    for (int i = 0 ; i < 4; i++){
    ////        int index = int(4.0 * random(gl_FragCoord.xyy, i)) & 3;
    //        int index = i;
    //        vec3 pos = vec3(projCoords.xy + poissonDisk[index] * texelSizeM, currentDepth);
    //        shadow += texture(shadowMap, pos) * 0.25;
    //
    ////        pcfDepth = texture(shadowMap, projCoords.xy + poissonDisk[i] * texelSizeM).r;
    ////        shadow += currentDepth - bias > pcfDepth ? 0.0 : 0.2;
    //        //        shadow += currentDepth - bias < pcfDepth ? 0.0 : 0.2;
    //    }

    currentDepth -= bias;

    //    Poisson disc randomly rotated
//    float randomAngle = gl_FragCoord.x * (1.3 * PI) + gl_FragCoord.y * (2.0 * PI);
////    float randomAngle = gl_FragCoord.x * (0.1 * PI) + gl_FragCoord.y * (0.2 * PI);
//    vec2 randomBase = vec2(cos(randomAngle), sin(randomAngle));
//    mat2 R = mat2(randomBase.x, randomBase.y, -randomBase.y, randomBase.x);

    vec2 texelSizeM = 1.0 * fragPosLightSpace.w * vec2(texelSize);
    for (int i = 0 ; i < 4; i++){
//        vec3 pos = vec3(projCoords.xy + R * poissonDisk[i] * texelSizeM, currentDepth);
        vec3 pos = vec3(projCoords.xy + poissonDisk[i] * texelSizeM, currentDepth);
        shadow += texture(shadowMap, pos);
    }
    shadow *= 0.25;


    // fade on shadow map borders for smoother transition
    float intensity = 1 - clamp((dist - 0.9) / (1.0 - 0.9), 0.0, 1.0);
    shadow = 1 - shadow;
    shadow *= intensity;
    shadow = 1 - shadow;

    return shadow;
}

vec3 computeShadowColor(sampler2DShadow shadowMap, sampler2DShadow shadowMap1, sampler2D shadowMapColor, vec4 fragPosLightSpace, float texelSize, float bias)
{
    // perform perspective divide
    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;

    float distX = abs(projCoords.x);
    float distY = abs(projCoords.y);
    float dist = max(distX, distY);

    // transform to [0,1] range
    projCoords.y = -projCoords.y;
    projCoords.xy = projCoords.xy * 0.5 + 0.5;

    if (projCoords.x < 0.0 || projCoords.x > 1.0
    || projCoords.y < 0.0 ||  projCoords.y > 1.0
    || projCoords.z < 0.0 || projCoords.z > 1.0)
    {
        return vec3(1.0);
    }

    float currentDepth = projCoords.z;
    vec3 shadow = vec3(0.0);
    float pcfDepth = 0.0;
    float opaqueShadow = 0.0;

    bias += 0.002 * dist * dist;
    currentDepth -= bias;

    //    Poisson disc randomly rotated
//    float randomAngle = gl_FragCoord.x * (1.3 * PI) + gl_FragCoord.y * (2.0 * PI);
//    //    float randomAngle = gl_FragCoord.x * (0.1 * PI) + gl_FragCoord.y * (0.2 * PI);
//    vec2 randomBase = vec2(cos(randomAngle), sin(randomAngle));
//    mat2 R = mat2(randomBase.x, randomBase.y, -randomBase.y, randomBase.x);

    vec2 texelSizeM = 1.0 * fragPosLightSpace.w * vec2(texelSize);
    for (int i = 0 ; i < 4; i++){
//        vec3 smCoords = vec3(projCoords.xy + R * poissonDisk[i] * texelSizeM, currentDepth);
        vec3 smCoords = vec3(projCoords.xy + poissonDisk[i] * texelSizeM, currentDepth);
        float sampleShadowOp = texture(shadowMap, smCoords);
        sampleShadowOp = 1.0 - sampleShadowOp;

        opaqueShadow += sampleShadowOp;
        if (sampleShadowOp >= 1.0) {
//            opaqueShadow += sampleShadowOp;
            continue;
        }

        float sampleShadowTr = 1.0 - texture(shadowMap1, smCoords);
        sampleShadowTr *= 1.0 - sampleShadowOp;

        if (sampleShadowTr <= 1.0) {
            vec3 shadowColor = texture(shadowMapColor, smCoords.xy).rgb;
            shadowColor *= 1.5;
            shadowColor = mix(shadowColor, LightColor * 0.05, 0.5);

            shadow += shadowColor * 2 * sampleShadowTr;
            opaqueShadow += sampleShadowTr;
        }

    }
    opaqueShadow *= 0.25;
    shadow *= 0.25;



    //    // fade on shadow map borders for smoother transition
    //    float intensity = 1 - clamp((dist - 0.9) / (1.0 - 0.9), 0.0, 1.0);
    //    shadow = 1 - shadow;
    //    shadow *= intensity;
    //    shadow = vec3(1.0) - shadow;

    shadow += vec3(1.0 - opaqueShadow);
//    shadow += vec3(opaqueShadow);

    return shadow;
}