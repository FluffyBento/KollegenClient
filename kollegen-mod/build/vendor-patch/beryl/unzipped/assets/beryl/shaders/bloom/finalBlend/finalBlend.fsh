#version 450


layout(binding = 1) uniform sampler2D bloomTexture;
layout(binding = 2) uniform sampler2D framebuffer;

layout(binding = 0) uniform UniformBufferObject {
    float BloomStrength;
};

layout(location = 0) in vec2 texCoords;

layout(location = 0) out vec4 fragColor;

float luminance(vec3 v)
{
    return dot(v, vec3(0.2126f, 0.7152f, 0.0722f));
}

vec3 lerp(vec3 v, vec3 b, vec3 t)
{
    return v + t * (b - v);
}

void main() {

    vec3 hdrColor = texture(framebuffer, texCoords).rgb;
    vec3 bloomColor = texture(bloomTexture, texCoords).rgb;
    fragColor = vec4(mix(hdrColor, bloomColor, BloomStrength), 1.0); // linear interpolation
}