package dev.kollegen.client.shader;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class KollegenShaderLoader {

    private static final KollegenShaderLoader INSTANCE = new KollegenShaderLoader();

    private String activeShaderPack = "(Deaktiviert)";
    private final List<String> cachedPacks = new ArrayList<>();
    private Pack cachedRuntimePack;

    private KollegenShaderLoader() {
    }

    public static KollegenShaderLoader getInstance() {
        return INSTANCE;
    }

    public Path getShaderpacksDir() {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("shaderpacks");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (Throwable ignored) {
            }
        }
        return dir;
    }

    public Path getRuntimePackDir() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("resourcepacks").resolve("kollegen_vulkan_shader");
    }

    public Path getConfigFile() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve("kollegen-shaders.properties");
    }

    public void init() {
        loadConfig();
        refreshPacks();
        if (isShaderPackActive()) {
            applyShaderPack(this.activeShaderPack, false);
        }
    }

    public synchronized List<String> refreshPacks() {
        List<String> list = new ArrayList<>();
        list.add("(Deaktiviert)");
        list.add("Kollegen Shader (Integriert)");
        Path dir = getShaderpacksDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (Files.isRegularFile(p) && name.toLowerCase().endsWith(".zip")) {
                        list.add(name);
                    } else if (Files.isDirectory(p) && (Files.isDirectory(p.resolve("shaders")) || Files.isRegularFile(p.resolve("shaders.properties")))) {
                        list.add(name);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        List<String> sorted = new ArrayList<>(list.subList(2, list.size()));
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        list.subList(2, list.size()).clear();
        list.addAll(sorted);

        this.cachedPacks.clear();
        this.cachedPacks.addAll(list);
        return this.cachedPacks;
    }

    public synchronized List<String> getShaderPacks() {
        if (this.cachedPacks.isEmpty()) {
            return refreshPacks();
        }
        return this.cachedPacks;
    }

    public String getActiveShaderPack() {
        return this.activeShaderPack != null ? this.activeShaderPack : "(Deaktiviert)";
    }

    public boolean isShaderPackActive() {
        String p = getActiveShaderPack();
        return !p.equalsIgnoreCase("(Deaktiviert)") && !p.toLowerCase().contains("deaktiviert");
    }

    public synchronized Pack getRuntimeShaderPack() {
        if (!isShaderPackActive()) {
            return null;
        }
        if (this.cachedRuntimePack != null) {
            return this.cachedRuntimePack;
        }
        try {
            Path dir = getRuntimePackDir();
            materializeRuntimePack(this.activeShaderPack);
            PackLocationInfo location = new PackLocationInfo(
                    "kollegen_vulkan_shader",
                    Component.literal("Kollegen Vulkan Shader"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );
            PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.TOP, false);
            this.cachedRuntimePack = Pack.readMetaAndCreate(
                    location,
                    new PathPackResources.PathResourcesSupplier(dir),
                    PackType.CLIENT_RESOURCES,
                    selectionConfig
            );
            return this.cachedRuntimePack;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void setShaderPack(String packName) {
        if (packName == null || packName.trim().isEmpty()) {
            packName = "(Deaktiviert)";
        }
        this.activeShaderPack = packName.trim();
        this.cachedRuntimePack = null;
        applyShaderPack(this.activeShaderPack, true);
        saveConfig();
        KollegenMod.LOGGER.info("Kollegen: Shaderpack aktiviert -> {}", this.activeShaderPack);

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                String msg = this.activeShaderPack.contains("Deaktiviert") ? "§7Shader deaktiviert" : "§aShader geladen: §f" + this.activeShaderPack;
                mc.player.sendOverlayMessage(Component.literal(msg));
            }
        } catch (Throwable ignored) {
        }
    }

    public void applyShaderPack(String packName, boolean reload) {
        if (packName == null || packName.equalsIgnoreCase("(Deaktiviert)") || packName.toLowerCase().contains("deaktiviert")) {
            this.activeShaderPack = "(Deaktiviert)";
            this.cachedRuntimePack = null;
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.enabled = false;
                KollegenShader.INSTANCE.onDisable();
            }
            cleanRuntimePackDir();
            if (reload) {
                triggerReload();
            }
            return;
        }

        this.activeShaderPack = packName;
        this.cachedRuntimePack = null;
        if (KollegenShader.INSTANCE != null) {
            KollegenShader.INSTANCE.enabled = true;
            KollegenShader.INSTANCE.onEnable();
        }

        String lower = packName.toLowerCase();
        if (lower.contains("kollegen") || lower.contains("integriert")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 0;
                KollegenShader.INSTANCE.applyPreset(0);
            }
        } else if (lower.contains("unbound")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 3;
                KollegenShader.INSTANCE.applyPreset(3);
            }
        } else if (lower.contains("reimagined") || lower.contains("complementary")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 2;
                KollegenShader.INSTANCE.applyPreset(2);
            }
        } else if (lower.contains("bsl")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 1;
                KollegenShader.INSTANCE.applyPreset(1);
            }
        } else if (lower.contains("bliss")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 4;
                KollegenShader.INSTANCE.applyPreset(4);
            }
        } else if (lower.contains("vibrant") || lower.contains("sildur")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 5;
                KollegenShader.INSTANCE.applyPreset(5);
            }
        } else if (lower.contains("cinema") || lower.contains("nostalgia") || lower.contains("kappa")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 6;
                KollegenShader.INSTANCE.applyPreset(6);
            }
        } else if (lower.contains("warm") || lower.contains("sun")) {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 7;
                KollegenShader.INSTANCE.applyPreset(7);
            }
        } else {
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.preset.index = 1;
                KollegenShader.INSTANCE.applyPreset(1);
            }
        }

        inspectPackProperties(packName);
        materializeRuntimePack(packName);

        if (reload) {
            triggerReload();
        }
    }

    private void cleanRuntimePackDir() {
        try {
            Path dir = getRuntimePackDir();
            if (Files.exists(dir)) {
                deleteRecursively(dir);
            }
        } catch (Throwable ignored) {
        }
    }

    private void deleteRecursively(Path p) {
        try {
            if (Files.isDirectory(p)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                    for (Path sub : ds) {
                        deleteRecursively(sub);
                    }
                }
            }
            Files.deleteIfExists(p);
        } catch (Throwable ignored) {
        }
    }

    private void materializeRuntimePack(String packName) {
        try {
            Path root = getRuntimePackDir();
            Path coreShaders = root.resolve("assets").resolve("minecraft").resolve("shaders").resolve("core");
            Files.createDirectories(coreShaders);

            int packFormat = SharedConstants.RESOURCE_PACK_FORMAT_MAJOR;
            if (packFormat <= 0) packFormat = 97;
            String mcmeta = "{\"pack\":{\"pack_format\":" + packFormat + ",\"description\":\"Kollegen Vulkan Shader Pipeline\"}}";
            Files.writeString(root.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String lower = packName.toLowerCase();
            boolean isComplementary = lower.contains("complementary") || lower.contains("reimagined") || lower.contains("unbound");
            boolean isBsl = lower.contains("bsl");
            boolean isBliss = lower.contains("bliss");

            float sunStrength = isComplementary ? 1.15f : (isBsl ? 1.08f : 1.05f);
            float waterWaveSpeed = isComplementary ? 1.8f : (isBsl ? 1.5f : 1.2f);

            String terrainVsh = generateTerrainVsh(waterWaveSpeed);
            String terrainFsh = generateTerrainFsh(sunStrength, isComplementary, isBsl, isBliss);
            String blockVsh = generateBlockVsh();
            String blockFsh = generateBlockFsh(sunStrength);
            String cloudsFsh = generateCloudsFsh();

            Files.writeString(coreShaders.resolve("terrain.vsh"), terrainVsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("terrain.fsh"), terrainFsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("block.vsh"), blockVsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("block.fsh"), blockFsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("clouds.fsh"), cloudsFsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable ignored) {
        }
    }

    private String generateTerrainVsh(float waveSpeed) {
        return "#version 330\n" +
                "#extension GL_ARB_separate_shader_objects : require\n\n" +
                "#include <minecraft:fog.glsl>\n" +
                "#include <minecraft:globals.glsl>\n" +
                "#include <minecraft:projection.glsl>\n" +
                "#include <minecraft:sample_lightmap.glsl>\n" +
                "#include <minecraft:terrainglobals.glsl>\n" +
                "#ifndef MULTIDRAW_TERRAIN\n" +
                "    #include <minecraft:chunksection.glsl>\n" +
                "#endif\n\n" +
                "layout(location = 0) in vec3 Position;\n" +
                "layout(location = 1) in vec4 Color;\n" +
                "layout(location = 2) in vec2 UV0;\n" +
                "layout(location = 3) in ivec2 UV2;\n" +
                "#ifdef MULTIDRAW_TERRAIN\n" +
                "layout(location = 4) in ivec3 ChunkPosition;\n" +
                "layout(location = 5) in float ChunkVisibility;\n" +
                "#endif\n\n" +
                "#ifndef OIT_ALPHA_ONLY\n" +
                "uniform sampler2D Sampler2;\n" +
                "#endif\n\n" +
                "layout(location = 0) out float sphericalVertexDistance;\n" +
                "layout(location = 1) out float cylindricalVertexDistance;\n" +
                "layout(location = 2) out vec4 vertexColor;\n" +
                "layout(location = 3) out vec2 texCoord0;\n" +
                "layout(location = 4) out float chunkVisibility;\n" +
                "layout(location = 5) out vec3 worldPos;\n" +
                "layout(location = 6) out vec2 lightCoords;\n\n" +
                "void main() {\n" +
                "    vec3 basePos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;\n" +
                "    worldPos = basePos;\n" +
                "    lightCoords = vec2(UV2) / 240.0;\n\n" +
                "    float skyLight = clamp(lightCoords.y, 0.0, 1.0);\n" +
                "    float t = GameTime * " + String.format(java.util.Locale.US, "%.1f", 2000.0f * waveSpeed) + ";\n" +
                "    vec3 animatedPos = basePos;\n\n" +
                "    if (skyLight > 0.35) {\n" +
                "        float waveStrength = (skyLight - 0.35) * 1.538;\n" +
                "        vec3 seed = basePos + vec3(CameraBlockPos);\n" +
                "        float w1 = sin(t * 1.8 + seed.x * 0.75 + seed.z * 0.5) * cos(t * 1.2 + seed.z * 0.8);\n" +
                "        float w2 = sin(t * 2.4 + seed.z * 1.2) * 0.5;\n" +
                "        animatedPos.x += (w1 + w2) * 0.035 * waveStrength;\n" +
                "        animatedPos.y += sin(t * 1.5 + seed.x * 0.9) * 0.02 * waveStrength;\n" +
                "        animatedPos.z += cos(t * 1.7 + seed.y * 0.8) * 0.025 * waveStrength;\n" +
                "    }\n\n" +
                "    gl_Position = ProjMat * ModelViewMat * vec4(animatedPos, 1.0);\n\n" +
                "    sphericalVertexDistance = fog_spherical_distance(animatedPos);\n" +
                "    cylindricalVertexDistance = fog_cylindrical_distance(animatedPos);\n" +
                "    #ifndef OIT_ALPHA_ONLY\n" +
                "    vertexColor = Color * sample_lightmap(Sampler2, UV2);\n" +
                "    #else\n" +
                "    vertexColor = Color;\n" +
                "    #endif\n" +
                "    texCoord0 = UV0;\n\n" +
                "    const float chunkFullyVisibleRange = 16.0;\n" +
                "    float dist = length(animatedPos);\n" +
                "    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));\n" +
                "}\n";
    }

    private String generateTerrainFsh(float sunMultiplier, boolean isComp, boolean isBsl, boolean isBliss) {
        String deepWater = isComp ? "vec3(0.05, 0.22, 0.44)" : (isBsl ? "vec3(0.04, 0.26, 0.48)" : "vec3(0.08, 0.25, 0.40)");
        String shallowWater = isComp ? "vec3(0.18, 0.65, 0.78)" : (isBsl ? "vec3(0.12, 0.68, 0.82)" : "vec3(0.20, 0.62, 0.68)");
        return "#version 330\n" +
                "#extension GL_ARB_separate_shader_objects : require\n\n" +
                "#include <minecraft:fog.glsl>\n" +
                "#include <minecraft:globals.glsl>\n" +
                "#include <minecraft:texture_sampling.glsl>\n" +
                "#include <minecraft:oit.glsl>\n" +
                "#include <minecraft:terrainglobals.glsl>\n" +
                "#ifndef MULTIDRAW_TERRAIN\n" +
                "    #include <minecraft:chunksection.glsl>\n" +
                "#endif\n\n" +
                "uniform sampler2D Sampler0;\n\n" +
                "layout(location = 0) in float sphericalVertexDistance;\n" +
                "layout(location = 1) in float cylindricalVertexDistance;\n" +
                "layout(location = 2) in vec4 vertexColor;\n" +
                "layout(location = 3) in vec2 texCoord0;\n" +
                "layout(location = 4) in float chunkVisibility;\n" +
                "layout(location = 5) in vec3 worldPos;\n" +
                "layout(location = 6) in vec2 lightCoords;\n\n" +
                "#ifndef OIT_ALPHA_ONLY\n" +
                "layout(location = 0) out vec4 fragColor;\n" +
                "#endif\n\n" +
                "vec4 calculateFinalColor(vec4 color) {\n" +
                "    #ifdef OIT_ACCUMULATE\n" +
                "    color = sampleColorForAccumulation(color);\n" +
                "    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);\n" +
                "    #else\n" +
                "    vec4 fogColor = FogColor;\n" +
                "    #endif\n" +
                "    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);\n" +
                "}\n\n" +
                "void main() {\n" +
                "    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;\n" +
                "    #ifndef OIT_ALPHA_ONLY\n" +
                "    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);\n" +
                "    #endif\n" +
                "    #ifdef ALPHA_CUTOUT\n" +
                "    if (color.a < ALPHA_CUTOUT) {\n" +
                "        discard;\n" +
                "    }\n" +
                "    #endif\n\n" +
                "    vec3 dX = dFdx(worldPos);\n" +
                "    vec3 dY = dFdy(worldPos);\n" +
                "    vec3 N = normalize(cross(dX, dY));\n" +
                "    if (!gl_FrontFacing) {\n" +
                "        N = -N;\n" +
                "    }\n\n" +
                "    vec3 sunDir = normalize(vec3(0.48, 0.78, 0.38));\n" +
                "    vec3 V = normalize(-worldPos);\n" +
                "    float NdotL = max(dot(N, sunDir), 0.0);\n" +
                "    float upFactor = max(N.y, 0.0);\n\n" +
                "    vec3 sunLight = vec3(1.10, 1.02, 0.92) * (" + String.format(java.util.Locale.US, "%.2f", sunMultiplier) + " * NdotL * 0.45 + 0.55);\n" +
                "    vec3 skyLight = vec3(0.85, 0.92, 1.05) * (upFactor * 0.3 + 0.7);\n" +
                "    vec3 naturalLighting = mix(skyLight, sunLight, lightCoords.y);\n\n" +
                "    float isWater = (color.a < 0.95 && color.a > 0.05) ? 1.0 : 0.0;\n" +
                "    if (isWater > 0.5) {\n" +
                "        float wT = GameTime * 1800.0;\n" +
                "        vec2 p = worldPos.xz * 0.7;\n" +
                "        float w1 = sin(p.x * 2.2 + wT * 1.6) * cos(p.y * 1.9 + wT * 1.3);\n" +
                "        float w2 = sin(p.x * 4.1 - wT * 2.2 + p.y * 2.7) * 0.5;\n" +
                "        vec3 waveN = normalize(N + vec3(w1 * 0.20, 0.0, w2 * 0.20));\n\n" +
                "        vec3 H = normalize(sunDir + V);\n" +
                "        float spec = pow(max(dot(waveN, H), 0.0), 64.0) * 2.0 * lightCoords.y;\n" +
                "        float fresnel = 0.04 + 0.96 * pow(1.0 - max(dot(V, waveN), 0.0), 4.5);\n\n" +
                "        vec3 deepWater = " + deepWater + ";\n" +
                "        vec3 shallowWater = " + shallowWater + ";\n" +
                "        vec3 waterColor = mix(deepWater, shallowWater, clamp(1.0 - sphericalVertexDistance / 36.0, 0.0, 1.0));\n\n" +
                "        color.rgb = mix(color.rgb * 0.45 + waterColor * 0.55, vec3(1.0, 0.98, 0.92), fresnel * 0.55);\n" +
                "        color.rgb += vec3(1.08, 1.02, 0.94) * spec;\n" +
                "        color.a = clamp(color.a * 0.85 + 0.15, 0.35, 0.90);\n" +
                "    } else {\n" +
                "        color.rgb *= naturalLighting;\n" +
                "        if (lightCoords.y > 0.5) {\n" +
                "            vec3 H = normalize(sunDir + V);\n" +
                "            float spec = pow(max(dot(N, H), 0.0), 32.0) * 0.18 * lightCoords.y;\n" +
                "            color.rgb += vec3(1.0, 0.96, 0.88) * spec;\n" +
                "        }\n" +
                "    }\n\n" +
                "    float torch = pow(lightCoords.x, 2.2);\n" +
                "    vec3 torchGlow = vec3(1.0, 0.65, 0.32) * (torch * 0.65);\n" +
                "    color.rgb += color.rgb * torchGlow;\n\n" +
                "    #ifdef OIT_ALPHA_ONLY\n" +
                "    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);\n" +
                "    #else\n" +
                "    fragColor = calculateFinalColor(color);\n" +
                "    #endif\n" +
                "}\n";
    }

    private String generateBlockVsh() {
        return "#version 330\n" +
                "#extension GL_ARB_separate_shader_objects : require\n\n" +
                "#include <minecraft:fog.glsl>\n" +
                "#include <minecraft:dynamictransforms.glsl>\n" +
                "#include <minecraft:projection.glsl>\n" +
                "#include <minecraft:sample_lightmap.glsl>\n\n" +
                "layout(location = 0) in vec3 Position;\n" +
                "layout(location = 1) in vec4 Color;\n" +
                "layout(location = 2) in vec2 UV0;\n" +
                "layout(location = 3) in ivec2 UV2;\n\n" +
                "#ifndef OIT_ALPHA_ONLY\n" +
                "uniform sampler2D Sampler2;\n" +
                "#endif\n\n" +
                "layout(location = 0) out float sphericalVertexDistance;\n" +
                "layout(location = 1) out float cylindricalVertexDistance;\n" +
                "layout(location = 2) out vec4 vertexColor;\n" +
                "layout(location = 3) out vec2 texCoord0;\n" +
                "layout(location = 4) out vec3 worldPos;\n" +
                "layout(location = 5) out vec2 lightCoords;\n\n" +
                "void main() {\n" +
                "    vec3 pos = Position + ModelOffset;\n" +
                "    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);\n\n" +
                "    sphericalVertexDistance = fog_spherical_distance(pos);\n" +
                "    cylindricalVertexDistance = fog_cylindrical_distance(pos);\n" +
                "    #ifndef OIT_ALPHA_ONLY\n" +
                "    vertexColor = Color * sample_lightmap(Sampler2, UV2);\n" +
                "    #else\n" +
                "    vertexColor = Color;\n" +
                "    #endif\n" +
                "    texCoord0 = UV0;\n" +
                "    worldPos = pos;\n" +
                "    lightCoords = vec2(UV2) / 240.0;\n" +
                "}\n";
    }

    private String generateBlockFsh(float sunMultiplier) {
        return "#version 330\n" +
                "#extension GL_ARB_separate_shader_objects : require\n\n" +
                "#include <minecraft:fog.glsl>\n" +
                "#include <minecraft:dynamictransforms.glsl>\n" +
                "#include <minecraft:oit.glsl>\n\n" +
                "uniform sampler2D Sampler0;\n\n" +
                "layout(location = 0) in float sphericalVertexDistance;\n" +
                "layout(location = 1) in float cylindricalVertexDistance;\n" +
                "layout(location = 2) in vec4 vertexColor;\n" +
                "layout(location = 3) in vec2 texCoord0;\n" +
                "layout(location = 4) in vec3 worldPos;\n" +
                "layout(location = 5) in vec2 lightCoords;\n\n" +
                "#ifndef OIT_ALPHA_ONLY\n" +
                "layout(location = 0) out vec4 fragColor;\n" +
                "#endif\n\n" +
                "vec4 calculateFinalColor(vec4 color) {\n" +
                "    #ifdef OIT_ACCUMULATE\n" +
                "    color = sampleColorForAccumulation(color);\n" +
                "    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);\n" +
                "    #else\n" +
                "    vec4 fogColor = FogColor;\n" +
                "    #endif\n" +
                "    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);\n" +
                "}\n\n" +
                "void main() {\n" +
                "    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;\n" +
                "    #ifdef ALPHA_CUTOUT\n" +
                "    if (color.a < ALPHA_CUTOUT) {\n" +
                "        discard;\n" +
                "    }\n" +
                "    #endif\n\n" +
                "    vec3 dX = dFdx(worldPos);\n" +
                "    vec3 dY = dFdy(worldPos);\n" +
                "    vec3 N = normalize(cross(dX, dY));\n" +
                "    if (!gl_FrontFacing) {\n" +
                "        N = -N;\n" +
                "    }\n\n" +
                "    vec3 sunDir = normalize(vec3(0.48, 0.78, 0.38));\n" +
                "    vec3 V = normalize(-worldPos);\n" +
                "    float NdotL = max(dot(N, sunDir), 0.0);\n" +
                "    float upFactor = max(N.y, 0.0);\n\n" +
                "    vec3 sunLight = vec3(1.10, 1.02, 0.92) * (" + String.format(java.util.Locale.US, "%.2f", sunMultiplier) + " * NdotL * 0.45 + 0.55);\n" +
                "    vec3 skyLight = vec3(0.85, 0.92, 1.05) * (upFactor * 0.3 + 0.7);\n" +
                "    vec3 naturalLighting = mix(skyLight, sunLight, lightCoords.y);\n" +
                "    color.rgb *= naturalLighting;\n\n" +
                "    float torch = pow(lightCoords.x, 2.2);\n" +
                "    vec3 torchGlow = vec3(1.0, 0.65, 0.32) * (torch * 0.65);\n" +
                "    color.rgb += color.rgb * torchGlow;\n\n" +
                "    #ifdef OIT_ALPHA_ONLY\n" +
                "    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);\n" +
                "    #else\n" +
                "    fragColor = calculateFinalColor(color);\n" +
                "    #endif\n" +
                "}\n";
    }

    private String generateCloudsFsh() {
        return "#version 330\n" +
                "#extension GL_ARB_separate_shader_objects : require\n\n" +
                "#include <minecraft:fog.glsl>\n" +
                "#include <minecraft:oit.glsl>\n\n" +
                "layout(location = 0) in float vertexDistance;\n" +
                "layout(location = 1) in vec4 vertexColor;\n\n" +
                "#ifndef OIT_ALPHA_ONLY\n" +
                "layout(location = 0) out vec4 fragColor;\n" +
                "#endif\n\n" +
                "vec4 calculateFinalColor(vec4 color) {\n" +
                "    #ifdef OIT_ACCUMULATE\n" +
                "    color = sampleColorForAccumulation(color);\n" +
                "    #endif\n" +
                "    return color;\n" +
                "}\n\n" +
                "void main() {\n" +
                "    vec4 color = vertexColor;\n" +
                "    color.rgb = mix(color.rgb, vec3(1.08, 1.02, 0.96), 0.30);\n" +
                "    #ifndef OIT_DEPTH_BOUNDS\n" +
                "    color.a *= 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);\n" +
                "    #endif\n\n" +
                "    #ifdef OIT_ALPHA_ONLY\n" +
                "    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);\n" +
                "    #else\n" +
                "    fragColor = calculateFinalColor(color);\n" +
                "    #endif\n" +
                "}\n";
    }

    private void inspectPackProperties(String packName) {
        Path packFile = getShaderpacksDir().resolve(packName);
        if (Files.exists(packFile)) {
            try {
                if (Files.isRegularFile(packFile) && packName.toLowerCase().endsWith(".zip")) {
                    try (ZipFile zip = new ZipFile(packFile.toFile())) {
                        ZipEntry entry = zip.getEntry("shaders/shaders.properties");
                        if (entry == null) {
                            entry = zip.getEntry("shaders.properties");
                        }
                        if (entry != null) {
                            try (InputStream in = zip.getInputStream(entry)) {
                                parsePropertiesStream(in);
                            }
                        }
                    }
                } else if (Files.isDirectory(packFile)) {
                    Path propPath = packFile.resolve("shaders").resolve("shaders.properties");
                    if (!Files.isRegularFile(propPath)) {
                        propPath = packFile.resolve("shaders.properties");
                    }
                    if (Files.isRegularFile(propPath)) {
                        try (InputStream in = Files.newInputStream(propPath)) {
                            parsePropertiesStream(in);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        Path sideTxt = getShaderpacksDir().resolve(packName + ".txt");
        if (Files.isRegularFile(sideTxt)) {
            try (InputStream in = Files.newInputStream(sideTxt)) {
                parsePropertiesStream(in);
            } catch (Throwable ignored) {
            }
        }
    }

    private void parsePropertiesStream(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim().toUpperCase();
                String v = line.substring(eq + 1).trim();

                if (KollegenShader.INSTANCE != null) {
                    if (k.contains("BLOOM")) {
                        boolean b = !"false".equalsIgnoreCase(v) && !"0".equals(v) && !"-1".equals(v);
                        KollegenShader.INSTANCE.bloomEnabled.value = b;
                        try {
                            double d = Double.parseDouble(v);
                            if (d > 0.0) {
                                KollegenShader.INSTANCE.bloom.value = Math.max(0.1, Math.min(1.0, d));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("MOTION_BLUR")) {
                        boolean b = "true".equalsIgnoreCase(v) || "1".equals(v) || "2".equals(v);
                        KollegenShader.INSTANCE.motionBlurEnabled.value = b;
                        try {
                            double d = Double.parseDouble(v);
                            if (d > 0.0) {
                                KollegenShader.INSTANCE.motionBlurStrength.value = Math.max(0.05, Math.min(0.95, d));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("VIGNETTE")) {
                        boolean b = "true".equalsIgnoreCase(v) || "1".equals(v);
                        KollegenShader.INSTANCE.vignetteEnabled.value = b;
                        try {
                            double d = Double.parseDouble(v);
                            if (d > 0.0) {
                                KollegenShader.INSTANCE.vignetteStrength.value = Math.max(0.05, Math.min(1.0, d));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("SATURATION") || k.contains("COLOR_SATURATION")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.saturationEnabled.value = true;
                            KollegenShader.INSTANCE.saturation.value = Math.max(0.5, Math.min(3.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("VIBRANCE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.vibranceEnabled.value = true;
                            KollegenShader.INSTANCE.vibrance.value = Math.max(0.5, Math.min(2.5, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("CONTRAST")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.contrastEnabled.value = true;
                            KollegenShader.INSTANCE.contrast.value = Math.max(0.5, Math.min(2.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("EXPOSURE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.exposureEnabled.value = true;
                            KollegenShader.INSTANCE.exposure.value = Math.max(0.5, Math.min(2.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("LIGHT_SHAFT") || k.contains("GODRAY") || k.contains("VOLUMETRIC_LIGHT")) {
                        boolean b = !"false".equalsIgnoreCase(v) && !"0".equals(v);
                        if (b) {
                            try {
                                double d = Double.parseDouble(v);
                                KollegenShader.INSTANCE.godRays.value = Math.max(0.1, Math.min(1.0, d));
                            } catch (NumberFormatException ignored) {
                                KollegenShader.INSTANCE.godRays.value = 0.65;
                            }
                        } else {
                            KollegenShader.INSTANCE.godRays.value = 0.0;
                        }
                    } else if (k.contains("AMBIENT_OCCLUSION") || k.contains("SSAO") || k.equals("AO")) {
                        boolean b = !"false".equalsIgnoreCase(v) && !"0".equals(v);
                        if (b) {
                            try {
                                double d = Double.parseDouble(v);
                                KollegenShader.INSTANCE.ssao.value = Math.max(0.1, Math.min(1.0, d));
                            } catch (NumberFormatException ignored) {
                                KollegenShader.INSTANCE.ssao.value = 0.50;
                            }
                        } else {
                            KollegenShader.INSTANCE.ssao.value = 0.0;
                        }
                    } else if (k.contains("FOG") || k.contains("ATMOSPHERE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.depthFog.value = Math.max(0.05, Math.min(1.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("WATER") || k.contains("WAVE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.waterShimmer.value = Math.max(0.05, Math.min(1.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void triggerReload() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            try {
                this.cachedRuntimePack = null;
                mc.reloadResourcePacks();
            } catch (Throwable ignored) {
            }
        });
    }

    public void loadConfig() {
        Path path = getConfigFile();
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            String p = props.getProperty("active_shaderpack", "(Deaktiviert)");
            if (p != null && !p.trim().isEmpty()) {
                this.activeShaderPack = p.trim();
            }
        } catch (Throwable ignored) {
        }
    }

    public void saveConfig() {
        Path path = getConfigFile();
        try {
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Properties props = new Properties();
            props.setProperty("active_shaderpack", getActiveShaderPack());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, null);
            }
        } catch (Throwable ignored) {
        }
    }
}
