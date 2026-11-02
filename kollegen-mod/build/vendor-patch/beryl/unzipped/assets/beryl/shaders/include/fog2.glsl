
vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

vec4 fog(vec4 color, float fragDistance, float fogEnd, vec4 fogColor) {

    //    //    float fog = exp(-1000.0/fragDistance);
    //    float fog = exp(-2.0*fogEnd/fragDistance);
    //    //    float fog = fragDistance / 1000.0;
    //    //    float fog = fragDistance / (10.0*fogEnd);
    //    fog = clamp(0.0, 1.0, fog);
    //    color.rgb = mix(color.rgb, fogColor.rgb, fog);
    float fog = smoothstep(0.8*fogEnd, 1.0*fogEnd, fragDistance);

    color = mix(color, fogColor, fog);

    return color;
}

float linear_fog(float fragDistance, float fogStart, float fogEnd) {
    if (fragDistance <= fogStart) {
        return 0.0f;
    }

    float fogFactor = (fragDistance - fogStart) / (fogEnd - fogStart);
    fogFactor = min(max(fogFactor, 0.0), 1.0);
    return fogFactor;
}

void atmospheric_fog(inout vec4 color, vec3 fragPos, float vertexDistance, const float fogFactor) {
    vec3 fragDir = normalize(fragPos);
    //        float RoUp = dot(fragDir, UpVector);
    //        RoUp = max(RoUp, 0.0);
    //
    //        float SoUp = max(dot(UpVector, LightDir), 0.0);
    float fogAmount = 1.0 - exp(-vertexDistance * fogFactor);

    vec3 fogColor;
    if (LightIntensity > 0.0) {
        float SdotFd = max(dot(fragDir, LightDir), 0.0);
        fogColor = mix(FogColor.rgb,
                       LightColor * 0.5 * FogColor.rgb,
                       pow(SdotFd,8.0) );
    }
    else {
        fogColor = FogColor.rgb;
    }

    color.rgb = mix(color.rgb, fogColor, fogAmount);

    //        float fogFactor = linear_fog(vertexDistance, FogEnvStart, FogEnvEnd);

    //        vec4 fogColor = vec4(getSkyColor(fragDir, LightDir, SkyColor.rgb, FogColor.rgb, NightFactor, RoUp, SoUp), 1.0);
    //        vec4 fogColor = FogColor;

    //        color = mix(color, fogColor, fogFactor);
    //        float fogFactor = linear_fog(vertexDistance, 0.2 * FogEnd, 2.0 * FogEnd);
    ////        color.rgb = mix(color.rgb, FogColor.rgb * LightColor * 0.1, fogFactor);
    //        color.rgb = mix(color.rgb, LightColor * 0.1, fogFactor);
}

void atmospheric_fog(inout vec4 color, vec3 fogColor, vec3 fragDir, float vertexDistance, const float fogFactor) {
    float fogAmount = 1.0 - exp(-vertexDistance * fogFactor);

    if (LightIntensity > 0.0) {
        float SdotFd = max(dot(fragDir, LightDir), 0.0);
        fogColor = mix(fogColor,
                       LightColor * 0.5 * fogColor,
                       pow(SdotFd,8.0) );
    }

    color.rgb = mix(color.rgb, fogColor, fogAmount);
}