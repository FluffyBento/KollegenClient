#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

const int FLAG_MASK_DIR = 7;
const int FLAG_INSIDE_FACE = 1 << 4;
const int FLAG_USE_TOP_COLOR = 1 << 5;
const int FLAG_EXTRA_Z = 1 << 6;
const int FLAG_EXTRA_X = 1 << 7;

layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec3 CloudOffset;
    vec3 CellSize;
};

uniform isamplerBuffer CloudFaces;

out float vertexDistance;
out vec4 vertexColor;

const vec3[] vertices = vec3[](
    vec3(1, 0, 0), vec3(1, 0, 1), vec3(0, 0, 1), vec3(0, 0, 0),
    vec3(0, 1, 0), vec3(0, 1, 1), vec3(1, 1, 1), vec3(1, 1, 0),
    vec3(0, 0, 0), vec3(0, 1, 0), vec3(1, 1, 0), vec3(1, 0, 0),
    vec3(1, 0, 1), vec3(1, 1, 1), vec3(0, 1, 1), vec3(0, 0, 1),
    vec3(0, 0, 1), vec3(0, 1, 1), vec3(0, 1, 0), vec3(0, 0, 0),
    vec3(1, 0, 0), vec3(1, 1, 0), vec3(1, 1, 1), vec3(1, 0, 1)
);

const vec4[] faceColors = vec4[](
    vec4(0.70, 0.70, 0.70, 1.0), vec4(1.00, 1.00, 1.00, 1.0),
    vec4(0.80, 0.80, 0.80, 1.0), vec4(0.80, 0.80, 0.80, 1.0),
    vec4(0.90, 0.90, 0.90, 1.0), vec4(0.90, 0.90, 0.90, 1.0)
);

vec3 ichor_cloud_color(vec2 worldXZ) {
    float t = GameTime * 95.0;
    float broad = sin(dot(worldXZ, vec2(0.021, 0.014)) + t * 0.31)
                + sin(dot(worldXZ, vec2(-0.013, 0.026)) - t * 0.23)
                + sin(dot(worldXZ, vec2(0.033, -0.019)) + t * 0.17);
    float detail = sin(dot(worldXZ, vec2(0.049, 0.037)) - t * 0.41)
                 + sin(dot(worldXZ, vec2(-0.041, 0.052)) + t * 0.34);
    float pinkBlob = smoothstep(-0.48, 0.56, broad / 3.0 + detail * 0.10);
    float goldBlob = smoothstep(0.57, 0.94,
            sin(dot(worldXZ, vec2(0.017, -0.029)) + t * 0.19)
            * 0.72 + sin(dot(worldXZ, vec2(0.036, 0.011)) - t * 0.27) * 0.28);
    float mintBlob = smoothstep(0.58, 0.96,
            sin(dot(worldXZ, vec2(-0.024, -0.018)) + t * 0.22));

    vec3 cyan = vec3(0.03, 0.93, 1.00);
    vec3 pink = vec3(1.00, 0.08, 0.82);
    vec3 gold = vec3(1.00, 0.58, 0.06);
    vec3 mint = vec3(0.28, 1.00, 0.70);
    vec3 color = mix(cyan, pink, pinkBlob);
    color = mix(color, gold, goldBlob * 0.76);
    return mix(color, mint, mintBlob * (1.0 - goldBlob) * 0.62);
}

void main() {
    int quadVertex = gl_VertexID % 4;
    int index = (gl_VertexID / 4) * 3;
    int cellX = texelFetch(CloudFaces, index).r;
    int cellZ = texelFetch(CloudFaces, index + 1).r;
    int dirAndFlags = texelFetch(CloudFaces, index + 2).r;
    int direction = dirAndFlags & FLAG_MASK_DIR;
    bool isInsideFace = (dirAndFlags & FLAG_INSIDE_FACE) == FLAG_INSIDE_FACE;
    bool useTopColor = (dirAndFlags & FLAG_USE_TOP_COLOR) == FLAG_USE_TOP_COLOR;
    cellX = (cellX << 1) | ((dirAndFlags & FLAG_EXTRA_X) >> 7);
    cellZ = (cellZ << 1) | ((dirAndFlags & FLAG_EXTRA_Z) >> 6);
    vec3 faceVertex = vertices[(direction * 4) + (isInsideFace ? 3 - quadVertex : quadVertex)];
    vec3 pos = (faceVertex * CellSize) + (vec3(cellX, 0, cellZ) * CellSize) + CloudOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vec2 worldXZ = pos.xz + vec2(CameraBlockPos.x, CameraBlockPos.z) + CameraOffset.xz;
    vec4 faceShade = useTopColor ? faceColors[1] : faceColors[direction];
    vertexDistance = fog_spherical_distance(pos);
    vertexColor = faceShade * vec4(ichor_cloud_color(worldXZ), CloudColor.a);
    vertexColor.rgb *= CloudColor.rgb;
}
