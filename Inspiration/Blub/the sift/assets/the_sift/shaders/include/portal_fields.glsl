// Shared procedural fields used by the portal and the loading backgrounds.
float siftHash(vec2 p) {return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float siftNoise(vec2 p) {
    vec2 i=floor(p), f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(siftHash(i),siftHash(i+vec2(1,0)),f.x),mix(siftHash(i+vec2(0,1)),siftHash(i+1.0),f.x),f.y);
}
float siftFbm(vec2 p) {
    float n=0.0,a=.5;
    for(int i=0;i<5;i++){n+=a*siftNoise(p);p=mat2(1.6,-1.2,1.2,1.6)*p;a*=.5;}
    return n;
}
vec3 siftPortalField(vec2 uv,vec2 view,float seconds) {
    vec2 p=uv*vec2(6.0,4.0);
    // Separate virtual depths, counter-moving wisps and softly merging turquoise pockets.
    vec2 warp=vec2(siftFbm(p*.7+vec2(seconds*.045,3)),siftFbm(p*.7+vec2(7,-seconds*.036)))-.5;
    float far=siftFbm(p*.75+view*.13+vec2(seconds*.055,seconds*.019));
    float middle=siftFbm(p*1.35+warp*1.8+view*.32+vec2(-seconds*.043,seconds*.062));
    float near=siftFbm(p*2.1-warp+view*.62+vec2(seconds*.032,-seconds*.049));
    float pockets=smoothstep(.38,.62,far*.35+middle*.4+near*.25);
    float wisps=smoothstep(.34,.66,middle)*smoothstep(.36,.63,near);
    vec3 color=mix(vec3(.94,.995,1),vec3(.20,.66,.76),clamp(pockets*.95+wisps*.35,0.0,1.0));
    float haze=smoothstep(.5,.73,siftFbm(p*.55+warp+vec2(-seconds*.02,0)));
    return mix(color,vec3(.99,1,1),haze*.65);
}
vec3 riftLoadingField(vec2 uv,float seconds) {
    vec2 p=uv*vec2(7,4);
    vec2 warp=vec2(siftFbm(p*.6+vec2(seconds*.13,0)),siftFbm(p*.6+vec2(4,-seconds*.12)));
    float n=siftFbm(p+warp*2.5+vec2(seconds*.17,-seconds*.09));
    vec3 energy=mix(vec3(.95,.57,.80),vec3(1,.87,.67),smoothstep(.25,.75,n));
    return mix(energy,vec3(1,.97,.98),smoothstep(.43,.69,siftFbm(p*.55-warp+seconds*.055))*.82);
}
