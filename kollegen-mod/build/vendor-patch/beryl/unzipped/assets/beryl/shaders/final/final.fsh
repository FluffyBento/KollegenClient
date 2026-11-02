#version 450


layout(binding = 0) uniform sampler2D Sampler0;

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

float saturate(float f) {
    return clamp(f, 0.0, 1.0);
}

vec3 saturate(vec3 vec) {
    return vec3(saturate(vec.x), saturate(vec.y), saturate(vec.z));
}

vec3 reinhard_jodie(vec3 v)
{
    float l = luminance(v);
    vec3 tv = v / (1.0f + v);
    return lerp(v / (1.0f + l), tv, tv);
}

float A = 0.15;
float B = 0.50;
float C = 0.10;
float D = 0.20;
float E = 0.02;
float F = 0.30;
float W = 11.2;

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

// Modified aces tonemap
vec3 aces(vec3 x)
{
    const float a = 2.1;
    const float b = 0.15;
    const float c = 2.03;
    const float d = 0.8;
    const float e = 0.25;

    return saturate((x*(a*x+b))/(x*(c*x+d)+e));
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    color.rgb = aces(color.rgb);
    color.a = 1.0f;

    color.rgb = pow(color.rgb, vec3(1.0 / 2.2));

    fragColor = color;
}