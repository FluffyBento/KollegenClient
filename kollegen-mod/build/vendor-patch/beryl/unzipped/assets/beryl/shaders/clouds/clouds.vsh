#version 450

layout(binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    mat4 ModelViewMat;
    vec3 ModelOffset;
};

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    vec3 LightDir;
    vec3 LightColor;
    vec4 SkyColor;
    vec4 FogColor;
    vec3 UpVector;
    float FogStart;
    float FogEnd;
    float LightVisibility;
    float NightFactor;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec3 fragPos;
layout(location = 2) out vec3 normal;
layout(location = 3) out float vertexDistance;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);
    fragPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    normal = (ModelViewMat * vec4(Normal, 0.0)).xyz;

    vec3 viewPos = Position + ModelOffset;
    vertexDistance = length(viewPos.xyz);

    vertexColor = Color * ColorModulator;
}
