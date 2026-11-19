package dev.kollegen.client.shader;

import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public class KollegenShaderLoader {

    public enum ShaderProfile {
        COMPLEMENTARY_UNBOUND,
        COMPLEMENTARY_REIMAGINED,
        BSL,
        BLISS,
        VIBRANT,
        DEFAULT
    }

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
        Path dir = getShaderpacksDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        list.add(name);
                    } else if (Files.isDirectory(p) && (Files.isDirectory(p.resolve("shaders")) || Files.isRegularFile(p.resolve("shaders.properties")))) {
                        list.add(name);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        List<String> sorted = new ArrayList<>(list.subList(1, list.size()));
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        list.subList(1, list.size()).clear();
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
        return !p.equalsIgnoreCase("(Deaktiviert)") && !p.toLowerCase(Locale.ROOT).contains("deaktiviert");
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
        KollegenMod.LOGGER.info("Kollegen: Shaderpack gewechselt -> {}", this.activeShaderPack);

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                String msg = !isShaderPackActive() ? "§7Shader deaktiviert" : "§aShader geladen: §f" + this.activeShaderPack;
                mc.player.sendOverlayMessage(Component.literal(msg));
            }
        } catch (Throwable ignored) {
        }
    }

    public void applyShaderPack(String packName, boolean reload) {
        if (packName == null || packName.equalsIgnoreCase("(Deaktiviert)") || packName.toLowerCase(Locale.ROOT).contains("deaktiviert")) {
            this.activeShaderPack = "(Deaktiviert)";
            this.cachedRuntimePack = null;
            cleanRuntimePackDir();
            if (reload) {
                triggerReload();
            }
            return;
        }

        this.activeShaderPack = packName;
        this.cachedRuntimePack = null;
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

    public ShaderProfile detectProfile(String name) {
        if (name == null) return ShaderProfile.DEFAULT;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("unbound")) {
            return ShaderProfile.COMPLEMENTARY_UNBOUND;
        }
        if (lower.contains("reimagined") || lower.contains("complementary")) {
            return ShaderProfile.COMPLEMENTARY_REIMAGINED;
        }
        if (lower.contains("bsl")) {
            return ShaderProfile.BSL;
        }
        if (lower.contains("bliss") || lower.contains("chocapic")) {
            return ShaderProfile.BLISS;
        }
        if (lower.contains("vibrant") || lower.contains("sildur")) {
            return ShaderProfile.VIBRANT;
        }
        return ShaderProfile.DEFAULT;
    }

    private void materializeRuntimePack(String packName) {
        try {
            Path root = getRuntimePackDir();
            Path coreShaders = root.resolve("assets").resolve("minecraft").resolve("shaders").resolve("core");
            Files.createDirectories(coreShaders);

            String mcmeta = "{\"pack\":{\"pack_format\":97,\"supported_formats\":[1,999],\"description\":\"Kollegen Vulkan Shader Pipeline\"}}";
            Files.writeString(root.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            ShaderProfile profile = detectProfile(packName);

            String terrainVsh = generateTerrainVsh(profile);
            String terrainFsh = generateTerrainFsh(profile);
            String blockVsh = generateBlockVsh();
            String blockFsh = generateBlockFsh(profile);
            String cloudsFsh = generateCloudsFsh(profile);

            Files.writeString(coreShaders.resolve("terrain.vsh"), terrainVsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("terrain.fsh"), terrainFsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("block.vsh"), blockVsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("block.fsh"), blockFsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(coreShaders.resolve("clouds.fsh"), cloudsFsh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable t) {
            KollegenMod.LOGGER.error("Kollegen: Fehler beim Materialisieren des Shaderpacks", t);
        }
    }

    private String generateTerrainVsh(ShaderProfile profile) {
        String waveSpeed;
        String waveAmp;
        switch (profile) {
            case COMPLEMENTARY_UNBOUND:
                waveSpeed = "2200.0";
                waveAmp = "0.032";
                break;
            case COMPLEMENTARY_REIMAGINED:
                waveSpeed = "1800.0";
                waveAmp = "0.020";
                break;
            case BSL:
                waveSpeed = "1400.0";
                waveAmp = "0.015";
                break;
            case BLISS:
                waveSpeed = "1100.0";
                waveAmp = "0.022";
                break;
            case VIBRANT:
                waveSpeed = "1900.0";
                waveAmp = "0.026";
                break;
            default:
                waveSpeed = "1600.0";
                waveAmp = "0.018";
                break;
        }

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
                "    vec3 animatedPos = basePos;\n" +
                "#ifdef ALPHA_CUTOUT\n" +
                "    if (lightCoords.y > 0.65) {\n" +
                "        float waveStrength = (lightCoords.y - 0.65) * 2.85;\n" +
                "        vec3 seed = basePos + vec3(CameraBlockPos);\n" +
                "        float t = GameTime * " + waveSpeed + ";\n" +
                "        float w = sin(t + seed.x * 0.45 + seed.z * 0.45) * " + waveAmp + " * waveStrength;\n" +
                "        animatedPos.x += w;\n" +
                "        animatedPos.z += w * 0.5;\n" +
                "    }\n" +
                "#endif\n\n" +
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

    private String generateTerrainFsh(ShaderProfile profile) {
        String sunLight;
        String skyLight;
        String deepWater;
        String shallowWater;
        String waterSpec;
        String waterFresnel;
        String waterAlpha;
        String torchGlow;
        String extraShading;

        switch (profile) {
            case COMPLEMENTARY_UNBOUND:
                sunLight = "vec3(1.28, 1.12, 0.90) * (NdotL * 0.55 + 0.45)";
                skyLight = "vec3(0.50, 0.66, 0.98) * (upFactor * 0.35 + 0.65)";
                deepWater = "vec3(0.02, 0.18, 0.48)";
                shallowWater = "vec3(0.12, 0.80, 0.94)";
                waterSpec = "pow(max(dot(waveN, H), 0.0), 80.0) * 3.8 * lightCoords.y";
                waterFresnel = "0.05 + 0.95 * pow(1.0 - max(dot(V, waveN), 0.0), 4.2)";
                waterAlpha = "clamp(color.a * 0.65 + 0.25, 0.40, 0.88)";
                torchGlow = "vec3(1.20, 0.70, 0.22) * (torch * 0.75)";
                extraShading = "        if (lightCoords.y > 0.4) {\n" +
                        "            vec3 H = normalize(sunDir + V);\n" +
                        "            float spec = pow(max(dot(N, H), 0.0), 48.0) * 0.26 * lightCoords.y;\n" +
                        "            color.rgb += vec3(1.05, 0.98, 0.90) * spec;\n" +
                        "        }\n";
                break;
            case COMPLEMENTARY_REIMAGINED:
                sunLight = "vec3(1.18, 1.08, 0.94) * (NdotL * 0.42 + 0.58)";
                skyLight = "vec3(0.65, 0.80, 1.05) * (upFactor * 0.30 + 0.70)";
                deepWater = "vec3(0.04, 0.28, 0.52)";
                shallowWater = "vec3(0.16, 0.72, 0.86)";
                waterSpec = "pow(max(dot(waveN, H), 0.0), 64.0) * 2.2 * lightCoords.y";
                waterFresnel = "0.04 + 0.96 * pow(1.0 - max(dot(V, waveN), 0.0), 4.5)";
                waterAlpha = "clamp(color.a * 0.70 + 0.20, 0.35, 0.85)";
                torchGlow = "vec3(1.12, 0.68, 0.28) * (torch * 0.65)";
                extraShading = "        if (lightCoords.y > 0.5) {\n" +
                        "            vec3 H = normalize(sunDir + V);\n" +
                        "            float spec = pow(max(dot(N, H), 0.0), 32.0) * 0.16 * lightCoords.y;\n" +
                        "            color.rgb += vec3(1.0, 0.96, 0.88) * spec;\n" +
                        "        }\n";
                break;
            case BSL:
                sunLight = "vec3(1.12, 1.02, 0.86) * (NdotL * 0.36 + 0.64)";
                skyLight = "vec3(0.72, 0.78, 0.86) * (upFactor * 0.25 + 0.75)";
                deepWater = "vec3(0.05, 0.35, 0.46)";
                shallowWater = "vec3(0.24, 0.76, 0.82)";
                waterSpec = "pow(max(dot(waveN, H), 0.0), 52.0) * 1.8 * lightCoords.y";
                waterFresnel = "0.04 + 0.96 * pow(1.0 - max(dot(V, waveN), 0.0), 4.0)";
                waterAlpha = "clamp(color.a * 0.55 + 0.15, 0.25, 0.75)";
                torchGlow = "vec3(1.06, 0.58, 0.22) * (torch * 0.60)";
                extraShading = "        float lum = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
                        "        float shadowDim = clamp(1.0 - (NdotL * 0.6 + lightCoords.y * 0.4), 0.0, 1.0);\n" +
                        "        color.rgb = mix(color.rgb, vec3(lum), 0.14 * shadowDim);\n";
                break;
            case BLISS:
                sunLight = "vec3(1.25, 1.12, 0.98) * (pow(NdotL, 0.70) * 0.35 + 0.65)";
                skyLight = "vec3(0.82, 0.88, 1.04) * (upFactor * 0.20 + 0.80)";
                deepWater = "vec3(0.02, 0.14, 0.24)";
                shallowWater = "vec3(0.10, 0.46, 0.60)";
                waterSpec = "pow(max(dot(waveN, H), 0.0), 40.0) * 1.6 * lightCoords.y";
                waterFresnel = "0.06 + 0.94 * pow(1.0 - max(dot(V, waveN), 0.0), 3.8)";
                waterAlpha = "clamp(color.a * 0.75 + 0.15, 0.30, 0.85)";
                torchGlow = "vec3(1.08, 0.76, 0.45) * (torch * 0.58)";
                extraShading = "        float haze = smoothstep(0.4, 1.0, sphericalVertexDistance / max(FogRenderDistanceEnd, 32.0));\n" +
                        "        color.rgb = mix(color.rgb, FogColor.rgb * 1.06, haze * 0.22);\n";
                break;
            case VIBRANT:
                sunLight = "vec3(1.26, 1.10, 0.86) * (NdotL * 0.48 + 0.52)";
                skyLight = "vec3(0.55, 0.72, 1.04) * (upFactor * 0.30 + 0.70)";
                deepWater = "vec3(0.02, 0.26, 0.52)";
                shallowWater = "vec3(0.14, 0.82, 0.90)";
                waterSpec = "pow(max(dot(waveN, H), 0.0), 72.0) * 2.8 * lightCoords.y";
                waterFresnel = "0.05 + 0.95 * pow(1.0 - max(dot(V, waveN), 0.0), 4.2)";
                waterAlpha = "clamp(color.a * 0.68 + 0.22, 0.35, 0.86)";
                torchGlow = "vec3(1.18, 0.64, 0.20) * (torch * 0.70)";
                extraShading = "";
                break;
            default:
                sunLight = "vec3(1.15, 1.06, 0.92) * (NdotL * 0.40 + 0.60)";
                skyLight = "vec3(0.68, 0.80, 1.02) * (upFactor * 0.28 + 0.72)";
                deepWater = "vec3(0.04, 0.26, 0.48)";
                shallowWater = "vec3(0.18, 0.70, 0.82)";
                waterSpec = "pow(max(dot(waveN, H), 0.0), 60.0) * 2.0 * lightCoords.y";
                waterFresnel = "0.04 + 0.96 * pow(1.0 - max(dot(V, waveN), 0.0), 4.2)";
                waterAlpha = "clamp(color.a * 0.70 + 0.18, 0.32, 0.84)";
                torchGlow = "vec3(1.10, 0.66, 0.28) * (torch * 0.62)";
                extraShading = "";
                break;
        }

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
                "    vec3 sunLight = " + sunLight + ";\n" +
                "    vec3 skyLight = " + skyLight + ";\n" +
                "    vec3 naturalLighting = mix(skyLight, sunLight, lightCoords.y);\n\n" +
                "    float isWater = (color.a < 0.95 && color.a > 0.05) ? 1.0 : 0.0;\n" +
                "    if (isWater > 0.5) {\n" +
                "        float wT = GameTime * 1800.0;\n" +
                "        vec2 p = worldPos.xz * 0.7;\n" +
                "        float w1 = sin(p.x * 2.2 + wT * 1.6) * cos(p.y * 1.9 + wT * 1.3);\n" +
                "        float w2 = sin(p.x * 4.1 - wT * 2.2 + p.y * 2.7) * 0.5;\n" +
                "        vec3 waveN = normalize(N + vec3(w1 * 0.20, 0.0, w2 * 0.20));\n\n" +
                "        vec3 H = normalize(sunDir + V);\n" +
                "        float spec = " + waterSpec + ";\n" +
                "        float fresnel = " + waterFresnel + ";\n\n" +
                "        vec3 deepWater = " + deepWater + ";\n" +
                "        vec3 shallowWater = " + shallowWater + ";\n" +
                "        vec3 waterColor = mix(deepWater, shallowWater, clamp(1.0 - sphericalVertexDistance / 36.0, 0.0, 1.0));\n\n" +
                "        color.rgb = mix(color.rgb * 0.45 + waterColor * 0.55, vec3(1.0, 0.98, 0.92), fresnel * 0.55);\n" +
                "        color.rgb += vec3(1.08, 1.02, 0.94) * spec;\n" +
                "        color.a = " + waterAlpha + ";\n" +
                "    } else {\n" +
                "        color.rgb *= naturalLighting;\n" +
                extraShading +
                "    }\n\n" +
                "    float torch = pow(lightCoords.x, 2.2);\n" +
                "    vec3 torchGlow = " + torchGlow + ";\n" +
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

    private String generateBlockFsh(ShaderProfile profile) {
        String sunLight;
        String skyLight;
        String torchGlow;

        switch (profile) {
            case COMPLEMENTARY_UNBOUND:
                sunLight = "vec3(1.28, 1.12, 0.90) * (NdotL * 0.55 + 0.45)";
                skyLight = "vec3(0.50, 0.66, 0.98) * (upFactor * 0.35 + 0.65)";
                torchGlow = "vec3(1.20, 0.70, 0.22) * (torch * 0.75)";
                break;
            case COMPLEMENTARY_REIMAGINED:
                sunLight = "vec3(1.18, 1.08, 0.94) * (NdotL * 0.42 + 0.58)";
                skyLight = "vec3(0.65, 0.80, 1.05) * (upFactor * 0.30 + 0.70)";
                torchGlow = "vec3(1.12, 0.68, 0.28) * (torch * 0.65)";
                break;
            case BSL:
                sunLight = "vec3(1.12, 1.02, 0.86) * (NdotL * 0.36 + 0.64)";
                skyLight = "vec3(0.72, 0.78, 0.86) * (upFactor * 0.25 + 0.75)";
                torchGlow = "vec3(1.06, 0.58, 0.22) * (torch * 0.60)";
                break;
            case BLISS:
                sunLight = "vec3(1.25, 1.12, 0.98) * (pow(NdotL, 0.70) * 0.35 + 0.65)";
                skyLight = "vec3(0.82, 0.88, 1.04) * (upFactor * 0.20 + 0.80)";
                torchGlow = "vec3(1.08, 0.76, 0.45) * (torch * 0.58)";
                break;
            case VIBRANT:
                sunLight = "vec3(1.26, 1.10, 0.86) * (NdotL * 0.48 + 0.52)";
                skyLight = "vec3(0.55, 0.72, 1.04) * (upFactor * 0.30 + 0.70)";
                torchGlow = "vec3(1.18, 0.64, 0.20) * (torch * 0.70)";
                break;
            default:
                sunLight = "vec3(1.15, 1.06, 0.92) * (NdotL * 0.40 + 0.60)";
                skyLight = "vec3(0.68, 0.80, 1.02) * (upFactor * 0.28 + 0.72)";
                torchGlow = "vec3(1.10, 0.66, 0.28) * (torch * 0.62)";
                break;
        }

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
                "    float NdotL = max(dot(N, sunDir), 0.0);\n" +
                "    float upFactor = max(N.y, 0.0);\n\n" +
                "    vec3 sunLight = " + sunLight + ";\n" +
                "    vec3 skyLight = " + skyLight + ";\n" +
                "    vec3 naturalLighting = mix(skyLight, sunLight, lightCoords.y);\n" +
                "    color.rgb *= naturalLighting;\n\n" +
                "    float torch = pow(lightCoords.x, 2.2);\n" +
                "    vec3 torchGlow = " + torchGlow + ";\n" +
                "    color.rgb += color.rgb * torchGlow;\n\n" +
                "    #ifdef OIT_ALPHA_ONLY\n" +
                "    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);\n" +
                "    #else\n" +
                "    fragColor = calculateFinalColor(color);\n" +
                "    #endif\n" +
                "}\n";
    }

    private String generateCloudsFsh(ShaderProfile profile) {
        String cloudTint;
        switch (profile) {
            case COMPLEMENTARY_UNBOUND:
                cloudTint = "vec3(1.12, 1.04, 0.94)";
                break;
            case COMPLEMENTARY_REIMAGINED:
                cloudTint = "vec3(1.08, 1.02, 0.96)";
                break;
            case BSL:
                cloudTint = "vec3(1.02, 1.02, 1.04)";
                break;
            case BLISS:
                cloudTint = "vec3(1.14, 1.06, 0.98)";
                break;
            case VIBRANT:
                cloudTint = "vec3(1.10, 1.02, 0.92)";
                break;
            default:
                cloudTint = "vec3(1.05, 1.02, 0.96)";
                break;
        }

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
                "    color.rgb = mix(color.rgb, " + cloudTint + ", 0.35);\n" +
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
