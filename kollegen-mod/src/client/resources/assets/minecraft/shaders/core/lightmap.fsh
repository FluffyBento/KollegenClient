#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

layout(std140) uniform LightmapInfo {
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float BossOverlayWorldDarkeningFactor;
    float BrightnessFactor;
    vec3 BlockLightTint;
    vec3 SkyLightColor;
    vec3 AmbientColor;
    vec3 NightVisionColor;
} lightmapInfo;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

float get_brightness(float level) {
    return level / (4.0 - 3.0 * level);
}

vec3 notGamma(vec3 color) {
    float maxComponent = max(max(color.x, color.y), color.z);
    float maxInverted = 1.0f - maxComponent;
    float maxScaled = 1.0f - maxInverted * maxInverted * maxInverted * maxInverted;
    return color * (maxScaled / maxComponent);
}

float parabolicMixFactor(float level) {
    return (2.0 * level - 1.0) * (2.0 * level - 1.0);
}

void main() {
    float block_level = floor(texCoord.x * 16) / 15;
    float sky_level = floor(texCoord.y * 16) / 15;

    float block_brightness = get_brightness(block_level) * lightmapInfo.BlockFactor;
    float sky_brightness = get_brightness(sky_level) * lightmapInfo.SkyFactor;

    vec3 nightVisionColor = lightmapInfo.NightVisionColor * lightmapInfo.NightVisionFactor;
    vec3 color = max(lightmapInfo.AmbientColor, nightVisionColor);

    if (ShaderFlags.x > 0.5) {
        float nightBlend = 1.0 - smoothstep(0.18, 0.52, lightmapInfo.SkyFactor);
        vec3 skyTint = mix(lightmapInfo.SkyLightColor, lightmapInfo.SkyLightColor * vec3(0.76, 0.88, 1.18), nightBlend);
        color = mix(color, color * vec3(0.78, 0.86, 1.08), 0.45);
        color += skyTint * sky_brightness;

        vec3 warmEmber = mix(vec3(1.0, 0.54, 0.22), vec3(1.0, 0.86, 0.64), pow(block_level, 0.85));
        vec3 shaderBlockCol = mix(lightmapInfo.BlockLightTint, warmEmber, 0.72 * clamp(ShaderWorldB.z - 0.5, 0.3, 1.0));
        color += shaderBlockCol * (block_brightness * (1.0 + 0.08 * block_level));
    } else {
        color += lightmapInfo.SkyLightColor * sky_brightness;
        vec3 BlockLightColor = mix(lightmapInfo.BlockLightTint, vec3(1.0), 0.9 * parabolicMixFactor(block_level));
        color += BlockLightColor * block_brightness;
    }

    color = mix(color, color * vec3(0.7, 0.6, 0.6), lightmapInfo.BossOverlayWorldDarkeningFactor);
    color = color - vec3(lightmapInfo.DarknessScale);

    color = clamp(color, 0.0, 1.0);
    vec3 notGamma = notGamma(color);
    color = mix(color, notGamma, lightmapInfo.BrightnessFactor);

    fragColor = vec4(color, 1.0);
}
