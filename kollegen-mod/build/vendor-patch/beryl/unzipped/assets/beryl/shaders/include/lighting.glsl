#define PI 3.14159265359

vec3 getMieScattering(const float RdotL) {
    float f = 10.0 + 200.0 * LightVisibility;
    return LightColor * 0.2 * min((1.0 / ((1 - RdotL) * f + 1.0)), 1.0);
}

vec4 getSkyColor(const float RdotL, float RdotUp, const float SoUp) {
    //TODO precompute and optimize
    vec4 color;

    float f1 = 1.0 - 0.25 / (40.0 * RdotUp * RdotUp + 0.25);
    color = mix(FogColor, SkyColor, f1);

    float m1 = (1 - SoUp) * 1.15;
    m1 = min(m1 * m1, 1.0);
    m1 = m1 * m1;

    float m2 = (1.0 - RdotUp) * 1.15;
    m2 = min(m2, 1.0);
    m2 = m2 * m2;
    m2 *= m1;
    m2 *= min(RdotL * 0.9 + 0.1, 1.0);
    m2 *= LightVisibility * (1.0 - NightFactor);

    vec4 sunsetColor = vec4(0.75f, 0.27f, 0.15f, 1.0f);
    color = mix(color, sunsetColor, m2);

    color.rgb += getMieScattering(RdotL);

    return color;
}

float saturate(float f) {
    return clamp(f, 0.0, 1.0);
}

float G1V(float dotNV, float k)
{
    return 1.0f/(dotNV*(1.0f-k)+k);
}

float LightingFuncGGX_REF(vec3 N, vec3 V, vec3 L, float roughness, float F0)
{
    float alpha = roughness*roughness;

    vec3 H = normalize(V+L);

    float dotNL = saturate(dot(N,L));
    float dotNV = saturate(dot(N,V));
    float dotNH = saturate(dot(N,H));
    float dotLH = saturate(dot(L,H));

    float F, D, vis;

    // D -> TrowbridgeReitz
    float alphaSqr = alpha*alpha;
    float pi = 3.14159f;
    float denom = dotNH * dotNH *(alphaSqr-1.0) + 1.0f;
    D = alphaSqr/(pi * denom * denom);

    // F -> Fresnel Schlick
    float dotLH5 = pow(1.0-dotLH, 5.0);
    F = F0 + (1.0-F0)*(dotLH5);

    // V -> vis Schlick Smith
    float k = alpha/2.0f;
    vis = G1V(dotNL,k)*G1V(dotNV,k);

    float specular = dotNL * D * F * vis;
    return specular;
}

//https://learnopengl.com/PBR/Theory

float DistributionGGX(vec3 N, vec3 H, float roughness)
{
    //Trowbridge-Reitz GGX normal distribution function
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;

    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    return num / denom;
}

float GeometrySchlickGGX(float NdotV, float roughness)
{
    float r = (roughness + 1.0);
    float k = (r*r) * 0.125;

    float num   = NdotV;
    float denom = NdotV * (1.0 - k) + k;

    return num / denom;
}

float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
{
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);

    return ggx1 * ggx2;
}

vec3 fresnelSchlick(float cosTheta, vec3 F0)
{
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 LightingGGX(vec3 V, vec3 N, vec3 L, vec3 albedo, vec3 radiance, vec3 F0,
                 float roughness, float metallic) {

    // reflectance equation
    vec3 Lo = vec3(0.0);
    vec3 H = normalize(V + L);

    // cook-torrance brdf
    float NDF = DistributionGGX(N, H, roughness);
    float G   = GeometrySmith(N, V, L, roughness);
    vec3 F    = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    vec3 numerator    = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.0001;
    vec3 specular     = numerator / denominator;

    // add to outgoing radiance Lo
    float NdotL = max(dot(N, L), 0.0);

    specular = min(NdotL * specular, kS);

    // Lo = (kD * albedo / PI + specular) * radiance * NdotL;
//    Lo = (kD * albedo * 0.31830988618 + specular) * radiance * NdotL;
    Lo = (kD * albedo * 0.31830988618) * NdotL + specular;
    Lo *= radiance;

    return Lo;
}

vec3 LightingGGX2(vec3 V, vec3 N, vec3 L, vec3 H, vec3 albedo, vec3 radiance, vec3 F0,
                  float roughness, float metallic)
{
    // reflectance equation
    vec3 Lo = vec3(0.0);

    // cook-torrance brdf
    //NDF
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;

    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    float NDF = num / denom;

    //G
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);

    float G = ggx1 * ggx2;

    vec3 F    = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    vec3 numerator    = NDF * G * F;
    float denominator = 4.0 * NdotV * NdotL + 0.0001;
    vec3 specular     = numerator / denominator;

    //    // add to outgoing radiance Lo
    //    float NdotL = max(dot(N, L), 0.0);

    specular = min(NdotL * specular, kS) * radiance;

    // Lo = (kD * albedo / PI + specular) * radiance * NdotL;
    //    Lo = (kD * albedo * 0.31830988618 + specular) * radiance * NdotL;
    Lo = (kD * albedo * 0.31830988618) * radiance * NdotL + specular;

    return Lo;
}

vec3 LightingGGX2(vec3 V, vec3 N, vec3 L, vec3 albedo, vec3 radiance, vec3 F0,
                  float roughness, float metallic) {

    vec3 H = normalize(V + L);
    vec3 res = LightingGGX2(V, N, L, H, albedo, radiance, F0, roughness, metallic);
    return res;
}

// Re-adaptation for directional light of UE4 Sphere light ggx
vec3 LightingSpecularGGX(vec3 V, vec3 N, vec3 L, vec3 H, float NdotL, float NdotV, vec3 radiance, vec3 F,
                                float roughness, float metallic) {

    // reflectance equation
    vec3 Lo = vec3(0.0);

    // cook-torrance brdf
    //NDF
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;

    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    float NDF = num / denom;

    // G
//    float NdotV = max(dot(N, V), 0.0);
//    float NdotL = max(dot(N, L), 0.0);
    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);

    float G = ggx1 * ggx2;

//    vec3 F    = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    vec3 numerator    = NDF * G * F;
    float denominator = 4.0 * NdotV + 0.0001;
    vec3 specular     = numerator / denominator;

    specular = min(specular, 1.0) * radiance;

    return specular;
}

vec3 LightingSphereGGX(vec3 V, vec3 N, vec3 L, vec3 albedo, vec3 radiance, vec3 F0,
                       float roughness, float metallic) {

    vec3 R = reflect(-V, N);
    //    vec3 centerToRay = dot(L, R) * (R - L);
    vec3 centerToRay = (R - L);
    float radius = 0.042;
    //closest point
    float d = length(centerToRay);
    vec3 O = centerToRay * saturate(radius / d);
    L = L + O;
    L = normalize(L);

    // reflectance equation
    vec3 Lo = vec3(0.0);
    vec3 H = normalize(V + L);

    // cook-torrance brdf
    //NDF
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;

    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    float NDF = num / denom;

    //G
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);

    float G = ggx1 * ggx2;

    vec3 F    = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    vec3 numerator    = NDF * G * F;
    float denominator = 4.0 * NdotV * NdotL + 0.0001;
    vec3 specular     = numerator / denominator;

    //    // add to outgoing radiance Lo
    //    float NdotL = max(dot(N, L), 0.0);

    //    specular = min(NdotL * specular, kS) * radiance;
    specular = min(specular * NdotL, 1.0) * radiance;

    // Lo = (kD * albedo / PI + specular) * radiance * NdotL;
    //    Lo = (kD * albedo * 0.31830988618 + specular) * radiance * NdotL;

    Lo = (kD * albedo * 0.31830988618) * radiance * NdotL + specular;

    return Lo;
}

vec3 LightingSphereGGX2(vec3 V, vec3 N, vec3 L, vec3 albedo, vec3 radiance, vec3 F0,
                       float roughness, float metallic, int lightingType) {

    vec3 R = reflect(-V, N);
    //    vec3 centerToRay = dot(L, R) * (R - L);
    vec3 centerToRay = (R - L);
    #ifdef TEXTURED_SUN
    float radius = 0.105;
    #else
    float radius = 0.025;
    #endif

    //closest point
    float d = length(centerToRay);
    vec3 O = centerToRay * saturate(radius / d);
    L = L + O;
    L = normalize(L);

    // reflectance equation
    vec3 Lo = vec3(0.0);
    vec3 H = normalize(V + L);

    // cook-torrance brdf
    //NDF
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;

    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    float NDF = num / denom;

    //G
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);

    // Foliage lighting
//    if (lightingType == 1) {
//        NdotL = dot(UpVector, L) * 0.8;
//    }
    NdotL = lightingType == 1 ? (dot(UpVector, L) * 0.8) : NdotL;


    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);

    float G = ggx1 * ggx2;

    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    vec3 numerator    = NDF * G * F;
    float denominator = 4.0 * NdotV * NdotL + 0.0001;
    vec3 specular     = numerator / denominator;

    //    // add to outgoing radiance Lo
    //    float NdotL = max(dot(N, L), 0.0);

    //    specular = min(NdotL * specular, kS) * radiance;
    specular = min(specular * NdotL, 1.0) * radiance;

    // Lo = (kD * albedo / PI + specular) * radiance * NdotL;
    //    Lo = (kD * albedo * 0.31830988618 + specular) * radiance * NdotL;

    Lo = (kD * albedo * 0.31830988618) * radiance * NdotL + specular;

    return Lo;
}