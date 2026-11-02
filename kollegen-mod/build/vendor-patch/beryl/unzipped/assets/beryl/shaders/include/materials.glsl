struct Material {
    vec3 F0;
    float roughness;
    float metallic;
    float lightEmission;
    int lightingType;
    int id;
};


Material getMaterial(int blockId) {
    Material material;

    material.lightingType = 0;
    material.lightEmission = 0.0f;

    switch (blockId) {

        // Water
        case 1:
            material.F0 = vec3(0.04);
            material.roughness = 0.03;
            material.metallic = 0.0;
            material.id = 1;
            break;

        // Glass
        case 2:
            material.F0 = vec3(0.04);
            material.roughness = 0.03;
            material.metallic = 0.0;
            material.id = 2;
            break;

        // == Metals ==
        // Iron Block
        case 3:
            material.F0 = vec3(0.56);
            material.roughness = 0.1;
            material.metallic = 1.0;
            material.id = 3;
            break;
        // Gold Block
        case 4:
            material.F0 = vec3(1.00, 0.71, 0.29);
            material.roughness = 0.1;
            material.metallic = 1.0;
            material.id = 4;
            break;
        // Copper Block
        case 5:
            material.F0 = vec3(0.95, 0.64, 0.54);
            material.roughness = 0.1;
            material.metallic = 1.0;
            material.id = 5;
            break;

        // == Dielectrics ==
        // Grass
        case 10:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.85;
            material.metallic = 0.0;
            material.id = 10;
            break;
        // Dirt
        case 11:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.95;
            material.metallic = 0.0;
            material.id = 10;
            break;
        // Sand
        case 12:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.8;
            material.metallic = 0.0;
            material.id = 12;
            break;
        // Foliage
        case 13:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.85;
            material.metallic = 0.0;
            material.lightingType = 1;
            material.id = 13;
            break;

        // Emissive
        // Lava, fire
        case 14:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.85;
            material.metallic = 0.0;
            material.lightEmission = 5.0;
            material.id = 14;
            break;
        // Torch
        case 15:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.85;
            material.metallic = 0.0;
            material.lightEmission = 1.5;
            material.id = 15;
            break;
        // Glowstone
        case 16:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.85;
            material.metallic = 0.0;
            material.lightEmission = 0.5;
            material.id = 16;
            break;
        // Nether portal
        case 17:
            material.F0 = vec3(0.04, 0.04, 0.04);
            material.roughness = 0.85;
            material.metallic = 0.0;
            material.lightEmission = 1.0;
            material.id = 17;
            break;

        // Obsidian
        case 20:
            material.F0 = vec3(0.1, 0.1, 0.1);
            material.roughness = 0.1;
            material.metallic = 0.0;
            material.id = 20;
            break;

        default:
            material.F0 = vec3(0.04);
            material.roughness = 0.9;
            material.metallic = 0.0;
            material.id = 0;
            break;
    }

    return material;
}