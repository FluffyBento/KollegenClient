vec3 normalVS = Normal;
vec3 vCamToSampleInVS = normalize(fragPos);
vec3 viewDir = -vCamToSampleInVS;
vec4 vReflectionInVS = vec4(reflect(vCamToSampleInVS, normalVS.xyz), 0.0);

// Sky reflection
float RoUp = dot(vReflectionInVS.xyz, UpVector);
RoUp = max(RoUp, 0.0);

//TODO precompute
float SoUp = max(dot(UpVector, LightDir), 0.0);
float RdotL =  max(dot(vReflectionInVS.xyz, LightDir), 0.0);
vec3 skyColor = getSkyColor(RdotL, RoUp, SoUp).rgb;

vec3 reflectedColor = skyColor;

#ifdef SSR
    reflectedColor = ssr(fragPos, vReflectionInVS, skyColor);
#endif

float roughness = 0.03;
float metallic = 0.0;
float ao = 1.0;
vec3 albedo = texColor.rgb * 0.1;

vec3 N = normalize(Normal);
vec3 V = viewDir;
vec3 H1 = normalize(V + vReflectionInVS.xyz);

float bias = (1.0 - max(dot(N, LightDir), 0.0)) * 0.001;
float shadow = ShadowCalculation(ShadowMap, posLightSpace, ShadowTexelSize, bias);

vec3 F0 = vec3(0.04);

vec3 radiance = reflectedColor.rgb;

vec3 color1 = LightingGGX2(viewDir, N, vReflectionInVS.xyz, N, albedo, reflectedColor.rgb, F0, roughness, metallic);

// Sun Lighting
radiance = LightColor * 0.6 * LightVisibility;

vec3 L = LightDir;

vec3 R = vReflectionInVS.xyz;

//    vec3 centerToRay = dot(L, R) * (R - L);
vec3 centerToRay = (R - L);
float radius = 0.042;
// closest point
float d = length(centerToRay);
vec3 O = centerToRay * saturate(radius / d);
L = L + O;
L = normalize(L);

vec3 H = normalize(V + L);

float NdotL = max(dot(N, L), 0.0);
float NdotV = max(dot(N, V), 0.0);
vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

//vec3 specular = LightingSpecularGGX(viewDir, N, L, H, NdotL, NdotV, radiance * 0.4, F, roughness, metallic);
float NdotH  = max(dot(N, H), 0.0);
vec3 specular = NdotL * NdotV * F / (1.01 - (NdotH * NdotH)) * radiance * 0.2 * length(texColor.rgb);

vec3 kD = vec3(1.0) - F;
vec3 diffuse = (kD * albedo * 0.31830988618) * radiance * NdotL;

vec3 color2 = (diffuse + specular) * shadow;

NdotV = abs(dot(N, V));
float alpha = (1.0 - NdotV) * 0.8 + texColor.a * 0.3;
alpha = min(alpha, 1.0);
color.a = alpha;

color.rgb = (color1 + color2) * light.z;
color.rgb += texColor.rgb * 0.3 * (light.y + light.x * vec3(1.0, 0.7, 0.5));

//color.rgb = normal.rgb;
//color.a = 1.0f;