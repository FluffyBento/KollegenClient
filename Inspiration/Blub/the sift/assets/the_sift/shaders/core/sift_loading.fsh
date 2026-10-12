#version 330
#moj_import <minecraft:globals.glsl>
#moj_import <the_sift:portal_fields.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    // Wall-clock encoded in vertex tint: animation continues while server worldgen waits.
    float t=dot(round(vertexColor.rgb*255.0),vec3(65536,256,1))/60.0;
    vec2 uv=texCoord0;
    uv.x=(uv.x-.5)*ScreenSize.x/max(ScreenSize.y,1.0)*.65+.5;
#ifdef RIFT_LOADING
    vec3 color=riftLoadingField(uv,t);
    color=mix(color,texture(Sampler0,texCoord0+vec2(sin(t*.1),cos(t*.13))*.008).rgb,.12);
#else
    vec3 color=siftPortalField(uv,vec2(sin(t*.09),cos(t*.07))*.14,t);
#endif
    fragColor=vec4(color*.56,1.0);
}
