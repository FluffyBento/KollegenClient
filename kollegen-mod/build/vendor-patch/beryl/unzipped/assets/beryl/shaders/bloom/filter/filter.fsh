#version 450


layout(binding = 1) uniform sampler2D Sampler0;
layout(binding = 2) uniform sampler2D Sampler3;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

#define Contrast 2.2
#define Brightness 1.0

float luminance(vec3 v)
{
    return dot(v, vec3(0.2126f, 0.7152f, 0.0722f));
}

vec3 lerp(vec3 v, vec3 b, vec3 t)
{
    return v + t * (b - v);
}

float naive_lerp(float a, float b, float t)
{
    return a + t * (b - a);
}

vec3 reinhard_jodie(vec3 v)
{
    float l = luminance(v);
    vec3 tv = v / (1.0f + v);
    return lerp(v / (1.0f + l), tv, tv);
}

vec3 Uncharted2Tonemap(vec3 x) {
    x*= Brightness;
    float A = 0.28;
    float B = 0.29;
    float C = 0.10;
    float D = 0.2;
    float E = 0.025;
    float F = 0.35;
    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
}

//vec3 reinhardExtended(vec3 color) {
//    color.rgb = color.rbg / (color.rgb + vec3(1.0f));
//}

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    if(color.r + color.g + color.b < 2.0f) {
        color = vec4(0.0f);
    }

    color.a = 1.0f;

    fragColor = color;
}