#ifndef MINECRAFT_GLOBALS_GLSL
#define MINECRAFT_GLOBALS_GLSL

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
    vec4 ShaderFlags;
    vec4 SunDirData;
    vec4 ShaderWorldA;
    vec4 ShaderWorldB;
    vec4 WaterRipples[16];
    vec4 ShaderExtra;
};

#endif
