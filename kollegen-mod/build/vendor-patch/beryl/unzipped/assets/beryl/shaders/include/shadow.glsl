
vec3 distortShadowClipPos(vec3 shadowClipPos) {
//    float dist = max(length(shadowClipPos.xy) - 0.05f, 0.0f);
    float dist = length(shadowClipPos.xy);
    float distortionFactor = dist * ShadowDistortion; // distance from the center
    distortionFactor += (1.0f - ShadowDistortion);

    shadowClipPos.xy /= distortionFactor;
    shadowClipPos.z += 0.5;
    shadowClipPos.z *= 0.5;
    return shadowClipPos;
}