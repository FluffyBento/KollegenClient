#version 330
#moj_import <minecraft:globals.glsl>
uniform sampler2D Sampler0;
in vec2 coord;
in vec4 material;
in vec3 viewPosition;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1,311.7)))*43758.5453); }
float noise(vec2 p) {
    vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i), hash(i+vec2(1,0)), f.x),
               mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
}
float fbm(vec2 p) {
    float n=0.0, a=0.5;
    for(int i=0;i<4;i++) { n+=a*noise(p); p=mat2(1.6,-1.2,1.2,1.6)*p; a*=0.5; }
    return n;
}
void main() {
    float t=GameTime*1200.0;
    vec2 p=coord*vec2(7.0,4.0);
    vec2 warp=vec2(fbm(p*.6+vec2(t*.13,0)),fbm(p*.6+vec2(4,-t*.12)));
    float n=fbm(p+warp*2.5+vec2(t*.17,-t*.09));
    vec3 energy=mix(vec3(0.95,0.57,0.80),vec3(1.0,0.87,0.67),smoothstep(.25,.75,n));
    energy=mix(energy,vec3(1.0,.97,.98),smoothstep(.43,.69,fbm(p*.55-warp+t*.055))*.82);
    float mode=material.r;
    if(mode>.85) { // Feathered colored bloom, no depth writes and no opaque cages.
        vec3 glow=mix(vec3(.54,.13,.92),vec3(1.0,.22,.37),n);
        glow=mix(glow,vec3(1.0,.67,.22),smoothstep(.52,.8,n));
        fragColor=vec4(glow,material.a*.40); return;
    }
    if(mode>.55) { fragColor=vec4(1.0,.98,.90,1.0); return; } // thin white-hot edge
    if(mode>.25) { // Orange walls are bright and opaque, never lit like copper.
        fragColor=vec4(mix(vec3(1.0,.65,.30),vec3(1.0,.87,.62),n),1.0); return;
    }
    // Image remains opaque; nearby blocks behind the rift cannot leak through it.
    float nearView=1.0-smoothstep(3.0,15.0,length(viewPosition));
    vec2 drift=(warp-.5)*.015;
    vec2 uv=clamp(coord+drift,vec2(.003),vec2(.997));
    vec3 destination=texture(Sampler0,uv).rgb;
    destination=mix(destination,vec3(1),.17);
    fragColor=vec4(mix(energy,destination,nearView*.78),1.0);
}
