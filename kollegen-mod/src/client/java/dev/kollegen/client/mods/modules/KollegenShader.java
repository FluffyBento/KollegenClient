package dev.kollegen.client.mods.modules;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import dev.kollegen.client.mixin.PostChainAccessor;
import dev.kollegen.client.mixin.PostPassAccessor;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KollegenShader extends Module {
    public static KollegenShader INSTANCE;

    public static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath("kollegen", "kollegen_shader");
    public static final Identifier BLUR_ID = Identifier.fromNamespaceAndPath("kollegen", "motion_blur");
    public static final Identifier VOXEL_SHADOW_TEX_ID = Identifier.fromNamespaceAndPath("kollegen", "textures/effect/voxel_shadow.png");
    private static final long START_NANOS = System.nanoTime();

    public static boolean shouldHideVanillaClouds() {
        return isShaderActive() && getEffectiveCloudShading() > 0.01f && getEffectiveRealism() >= 0.35f;
    }

    private static DynamicTexture voxelShadowTex;
    private static int voxOriginX = Integer.MIN_VALUE;
    private static int voxOriginY = Integer.MIN_VALUE;
    private static int voxOriginZ = Integer.MIN_VALUE;
    private static int voxSweepRow = 0;
    private static boolean voxReady = false;
    private static Level lastVoxLevel = null;
    private static final ConcurrentHashMap<Block, Boolean> SHADOW_CASTER_CACHE = new ConcurrentHashMap<>();

    private static boolean isShadowCasterBlock(Block block) {
        BlockState s = block.defaultBlockState();
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            return false;
        }
        if (s.is(BlockTags.LEAVES)) {
            return true;
        }
        if (s.is(BlockTags.IMPERMEABLE) || block == Blocks.GLASS || block == Blocks.TINTED_GLASS || block == Blocks.GLASS_PANE) {
            return false;
        }
        return s.isSolidRender() || s.canOcclude() || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS)
                || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS);
    }

    public static void ensureVoxelShadowTexture(Minecraft mc) {
        if (mc == null || mc.getTextureManager() == null) {
            return;
        }
        try {
            if (voxelShadowTex == null) {
                voxelShadowTex = new DynamicTexture("kollegen_voxel_shadow", 128, 256, true);
            }
            if (mc.getTextureManager().getTexture(VOXEL_SHADOW_TEX_ID) != voxelShadowTex) {
                mc.getTextureManager().register(VOXEL_SHADOW_TEX_ID, voxelShadowTex);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void updateVoxelShadow(Minecraft mc) {
        if (mc == null || mc.level == null || voxelShadowTex == null || mc.gameRenderer == null) {
            return;
        }
        if (getEffectiveRaytracingStrength() <= 0.01f) {
            return;
        }
        try {
            Camera camera = mc.gameRenderer.mainCamera();
            if (camera == null || !camera.isInitialized()) {
                return;
            }
            Vec3 cp = camera.position();
            int qx = Math.floorDiv(Mth.floor(cp.x), 16) * 16 - 64;
            int qy = Math.floorDiv(Mth.floor(cp.y), 8) * 8 - 24;
            int qz = Math.floorDiv(Mth.floor(cp.z), 16) * 16 - 64;

            boolean fullRebuild = (lastVoxLevel != mc.level) || (voxOriginX != qx) || (voxOriginY != qy) || (voxOriginZ != qz) || !voxReady;
            lastVoxLevel = mc.level;
            voxOriginX = qx;
            voxOriginY = qy;
            voxOriginZ = qz;

            NativeImage img = voxelShadowTex.getPixels();
            if (img == null) {
                return;
            }
            int startRow = fullRebuild ? 0 : voxSweepRow;
            int rowCount = fullRebuild ? 128 : 16;
            if (!fullRebuild) {
                voxSweepRow = (voxSweepRow + 16) & 127;
            } else {
                voxSweepRow = 0;
            }

            Level level = mc.level;
            int minY = level.getMinY();
            for (int r = 0; r < rowCount; r++) {
                int iz = (startRow + r) & 127;
                int wz = qz + iz;
                int cz = wz >> 4;
                int lz = wz & 15;
                for (int cxOff = 0; cxOff < 8; cxOff++) {
                    int wxBase = qx + (cxOff << 4);
                    int cx = wxBase >> 4;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    int baseIx = cxOff << 4;
                    if (chunk == null) {
                        for (int lx = 0; lx < 16; lx++) {
                            img.setPixelABGR(baseIx + lx, iz * 2, 0);
                            img.setPixelABGR(baseIx + lx, iz * 2 + 1, 0);
                        }
                        continue;
                    }
                    int[] loCols = new int[16];
                    int[] hiCols = new int[16];
                    for (int syBlock = 0; syBlock < 64; syBlock += 16) {
                        int wy = qy + syBlock;
                        int secIdx = chunk.getSectionIndex(wy);
                        if (wy < minY || secIdx < 0 || secIdx >= chunk.getSectionsCount()) {
                            continue;
                        }
                        LevelChunkSection sec = chunk.getSection(secIdx);
                        if (sec == null || sec.hasOnlyAir()) {
                            continue;
                        }
                        int baseLy = wy & 15;
                        for (int dy = 0; dy < 16; dy++) {
                            int iy = syBlock + dy;
                            int ly = baseLy + dy;
                            if (ly >= 16) {
                                int wy2 = qy + iy;
                                int secIdx2 = chunk.getSectionIndex(wy2);
                                if (secIdx2 < 0 || secIdx2 >= chunk.getSectionsCount()) {
                                    continue;
                                }
                                sec = chunk.getSection(secIdx2);
                                if (sec == null || sec.hasOnlyAir()) {
                                    continue;
                                }
                                ly = wy2 & 15;
                            }
                            int bit = 1 << (iy & 31);
                            boolean hi = iy >= 32;
                            for (int lx = 0; lx < 16; lx++) {
                                BlockState st = sec.getBlockState(lx, ly, lz);
                                if (!st.isAir()) {
                                    boolean solid = SHADOW_CASTER_CACHE.computeIfAbsent(st.getBlock(), KollegenShader::isShadowCasterBlock);
                                    if (solid) {
                                        if (hi) {
                                            hiCols[lx] |= bit;
                                        } else {
                                            loCols[lx] |= bit;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (int lx = 0; lx < 16; lx++) {
                        img.setPixelABGR(baseIx + lx, iz * 2, loCols[lx]);
                        img.setPixelABGR(baseIx + lx, iz * 2 + 1, hiCols[lx]);
                    }
                }
            }
            voxelShadowTex.upload();
            voxReady = true;
        } catch (Throwable ignored) {
        }
    }

    public final ModeSetting preset = new ModeSetting(
            "Shader-Profil",
            "Vollständige Welt-, Wasser-, Raytracing- und Licht-Profile.",
            new String[]{
                    "Complementary Cinema",
                    "SEUS PTGI Raytracing",
                    "BSL Sunset & God Rays",
                    "Bliss Nacht & Atmosphäre",
                    "Complementary Reimagined",
                    "Vibrant (Lebendig)",
                    "Cinematic Kino",
                    "Vanilla (Original)"
            },
            0,
            this::applyPreset
    );

    public final BooleanSetting saturationEnabled = new BooleanSetting(
            "Farbsättigung",
            "Erhöht oder verringert die Farbsättigung der Spielwelt.",
            true
    );

    public final SliderSetting saturation = new SliderSetting(
            "Sättigung",
            "Farbintensität (1.0 = normal, höher = lebendiger).",
            1.16, 0.0, 3.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final BooleanSetting vibranceEnabled = new BooleanSetting(
            "Vibrance",
            "Verstärkt schwache Farben dynamisch für mehr Farbtiefe.",
            true
    );

    public final SliderSetting vibrance = new SliderSetting(
            "Vibrance-Stärke",
            "Intensität der dynamischen Farbanhebung.",
            1.18, 0.5, 2.5, 0.05,
            v -> String.format("%.2f", v), null
    );

    public final BooleanSetting contrastEnabled = new BooleanSetting(
            "Kontrast",
            "Verstärkt Tiefen und Lichter.",
            true
    );

    public final SliderSetting contrast = new SliderSetting(
            "Kontrast-Stärke",
            "Intensität des Kontrasts.",
            1.12, 0.5, 2.0, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final BooleanSetting brightnessEnabled = new BooleanSetting(
            "Helligkeit",
            "Helligkeitsanpassung des Shaders.",
            false
    );

    public final SliderSetting brightness = new SliderSetting(
            "Helligkeits-Stärke",
            "Multiplikator der Bildhelligkeit.",
            1.00, 0.5, 2.0, 0.05,
            v -> String.format("%.2f", v), null
    );

    public final BooleanSetting exposureEnabled = new BooleanSetting(
            "Belichtung",
            "Kamera-Belichtung für hellere oder dunklere Szenen.",
            true
    );

    public final SliderSetting exposure = new SliderSetting(
            "Belichtungs-Wert",
            "Intensität der Belichtung.",
            1.06, 0.5, 2.0, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final BooleanSetting bloomEnabled = new BooleanSetting(
            "Bloom",
            "Mehrstufiges weiches Leuchten heller Lichtquellen und Reflexionen.",
            true
    );

    public final SliderSetting bloom = new SliderSetting(
            "Bloom-Stärke",
            "Intensität des Lichtleuchtens.",
            0.42, 0.0, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final BooleanSetting warmthEnabled = new BooleanSetting(
            "Farbtemperatur",
            "Warmer Sonnenlicht-Look oder kühle Nachtstimmung.",
            true
    );

    public final SliderSetting warmth = new SliderSetting(
            "Temperatur",
            "Farbwärme (1.0 = neutral, >1 = sonnig/golden, <1 = kühl).",
            1.05, 0.7, 1.4, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final BooleanSetting vignetteEnabled = new BooleanSetting(
            "Kino-Vignette",
            "Weiche dunkle Ecken für mehr Tiefenwirkung.",
            false
    );

    public final SliderSetting vignetteStrength = new SliderSetting(
            "Vignetten-Stärke",
            "Intensität der Randeindunklung.",
            0.12, 0.05, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final BooleanSetting motionBlurEnabled = new BooleanSetting(
            "Motion Blur",
            "Aktiviert flüssige Bewegungsunschärfe bei Kamerabewegungen.",
            false
    );

    public final SliderSetting motionBlurStrength = new SliderSetting(
            "Motion-Blur-Stärke",
            "Stärke des Unschärfe-Nachzieheffekts.",
            0.35, 0.05, 0.95, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting toneMap = new SliderSetting(
            "Tone Mapping",
            "Tonemapping-Algorithmus",
            1.0, 0.0, 3.0, 1.0,
            v -> v < 0.5 ? "Linear" : (v < 1.5 ? "ACES Filmic" : (v < 2.5 ? "Reinhard-Jodie" : "Uncharted 2")), null
    );

    public final SliderSetting gamma = new SliderSetting(
            "Gamma-Korrektur",
            "Tonwertkurve und Gammakorrektur.",
            1.14, 0.8, 1.6, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final SliderSetting godRays = new SliderSetting(
            "Sonnenstrahlen (3D)",
            "Volumetrische 3D-Sonnenstrahlen in der Luft durch Bäume und Berge.",
            0.90, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting ssao = new SliderSetting(
            "Ambient Occlusion (SSAO)",
            "Weiche Tiefenschatten in Ecken und Kanten.",
            0.60, 0.0, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting depthFog = new SliderSetting(
            "Atmosphärischer Nebel",
            "Realistischer Höhen- und Tiefendunst entfernter Landschaften.",
            0.40, 0.0, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting waterShimmer = new SliderSetting(
            "Wasser-Reflexionen",
            "Glattes Spiegelwasser, Kaustik, Fresnel und Sonnenspiegelung.",
            1.00, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final BooleanSetting wavingVegetation = new BooleanSetting(
            "Bewegende Pflanzen",
            "Gras, Laub, Ranken und Blumen wehen organisch im Wind.",
            true
    );

    public final BooleanSetting wavingWater = new BooleanSetting(
            "Bewegendes Wasser",
            "Echte Vertex-Wellen und animierte Wasser-Geometrie.",
            true
    );

    public final BooleanSetting sunLighting = new BooleanSetting(
            "Direktes Sonnenlicht",
            "Gerichtetes Sonnen- und Mondlicht mit warmen Kanten und kühlen Schatten.",
            true
    );

    public final SliderSetting windStrength = new SliderSetting(
            "Wind-Intensität",
            "Stärke der Pflanzen- und Laubbewegung im Wind.",
            1.05, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting pbrStrength = new SliderSetting(
            "Material-Relief (PBR)",
            "Prozedurale Normal-Maps, Spekularglanz und Oberflächentiefe auf Blöcken.",
            0.85, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting emissiveStrength = new SliderSetting(
            "Erze & Licht-Glow",
            "Selbstleuchten von Lava, Laternen, Glowstone und glänzenden Erzen.",
            1.00, 0.0, 2.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting foliageSss = new SliderSetting(
            "Blätter-Transluzenz (SSS)",
            "Warmes Durchscheinen von Sonnenlicht durch Laub und Pflanzen.",
            0.90, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting cloudShading = new SliderSetting(
            "Stilisierte Wolken & Himmel",
            "Volumetrische Wolken im Himmel, Sonnenuntergangs-Halo und Sternenhimmel.",
            1.00, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final BooleanSetting rainPuddles = new BooleanSetting(
            "Nasse Flächen bei Regen",
            "Regen erzeugt spiegelnde Nässe und Pfützen im Freien.",
            true
    );

    public final SliderSetting raytracingStrength = new SliderSetting(
            "Screen-Space Raytracing",
            "Raymarched Sonnenschatten, Wasser-SSR und indirektes GI-Farblicht.",
            1.05, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting realism = new SliderSetting(
            "Realismus",
            "Steuert Realismus-Tiefe: realistische Krusten-Lava, Sunset-Wolken, Sonnen-Glow, Lens Flare & Wasserspiegelung.",
            1.10, 0.0, 1.5, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public KollegenShader() {
        super("kollegenshader", "Kollegen Shader", "Nativer Vulkan-Shader-Ersatz: Screen-Space Raytracing, SSR-Spiegelwasser, Krusten-Lava, volumetrische Wolken, PBR & Bloom.", Category.VISUAL);
        INSTANCE = this;
        add(preset);
        add(wavingVegetation);
        add(wavingWater);
        add(sunLighting);
        add(rainPuddles);
        add(raytracingStrength);
        add(realism);
        add(windStrength);
        add(pbrStrength);
        add(emissiveStrength);
        add(foliageSss);
        add(cloudShading);
        add(saturationEnabled);
        add(saturation);
        add(vibranceEnabled);
        add(vibrance);
        add(contrastEnabled);
        add(contrast);
        add(brightnessEnabled);
        add(brightness);
        add(exposureEnabled);
        add(exposure);
        add(bloomEnabled);
        add(bloom);
        add(warmthEnabled);
        add(warmth);
        add(vignetteEnabled);
        add(vignetteStrength);
        add(motionBlurEnabled);
        add(motionBlurStrength);
        add(toneMap);
        add(gamma);
        add(godRays);
        add(ssao);
        add(depthFog);
        add(waterShimmer);
    }

    @Override
    public void onEnable() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameRenderer != null) {
                java.util.List<Identifier> req = mc.gameRenderer.getRequestedPostEffects();
                if (req != null) {
                    if (!req.contains(SHADER_ID)) {
                        req.add(SHADER_ID);
                    }
                    if (isMotionBlurActive() && !req.contains(BLUR_ID)) {
                        req.add(BLUR_ID);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameRenderer != null) {
                java.lang.reflect.Field f1 = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("requestedPostEffects");
                f1.setAccessible(true);
                Object req = f1.get(mc.gameRenderer);
                if (req instanceof java.util.List<?> list) {
                    list.removeIf(id -> SHADER_ID.equals(id) || BLUR_ID.equals(id));
                }
                java.lang.reflect.Field f2 = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("appliedPostEffects");
                f2.setAccessible(true);
                Object app = f2.get(mc.gameRenderer);
                if (app instanceof java.util.List<?> list) {
                    list.removeIf(chain -> chain instanceof PostChain pc && (SHADER_ID.equals(pc.id()) || BLUR_ID.equals(pc.id())));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void applyPreset(int index) {
        if (index == 7) {
            wavingVegetation.value = false;
            wavingWater.value = false;
            sunLighting.value = false;
            rainPuddles.value = false;
            saturationEnabled.value = false;
            saturation.value = 1.00;
            vibranceEnabled.value = false;
            vibrance.value = 1.00;
            contrastEnabled.value = false;
            contrast.value = 1.00;
            brightnessEnabled.value = false;
            brightness.value = 1.00;
            exposureEnabled.value = false;
            exposure.value = 1.00;
            bloomEnabled.value = false;
            bloom.value = 0.00;
            warmthEnabled.value = false;
            warmth.value = 1.00;
            vignetteEnabled.value = false;
            vignetteStrength.value = 0.00;
            toneMap.value = 0.0;
            gamma.value = 1.00;
            godRays.value = 0.00;
            ssao.value = 0.00;
            depthFog.value = 0.00;
            waterShimmer.value = 0.00;
            windStrength.value = 0.00;
            pbrStrength.value = 0.00;
            emissiveStrength.value = 0.00;
            foliageSss.value = 0.00;
            cloudShading.value = 0.00;
            raytracingStrength.value = 0.00;
            realism.value = 0.00;
            return;
        }
        wavingVegetation.value = true;
        wavingWater.value = true;
        sunLighting.value = true;
        rainPuddles.value = true;
        switch (index) {
            case 0 -> {
                saturationEnabled.value = true;
                saturation.value = 1.18;
                vibranceEnabled.value = true;
                vibrance.value = 1.20;
                contrastEnabled.value = true;
                contrast.value = 1.12;
                brightnessEnabled.value = false;
                brightness.value = 1.00;
                exposureEnabled.value = true;
                exposure.value = 1.06;
                bloomEnabled.value = true;
                bloom.value = 0.45;
                warmthEnabled.value = true;
                warmth.value = 1.05;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.10;
                toneMap.value = 1.0;
                gamma.value = 1.14;
                godRays.value = 0.95;
                ssao.value = 0.60;
                depthFog.value = 0.42;
                waterShimmer.value = 1.10;
                windStrength.value = 1.05;
                pbrStrength.value = 0.85;
                emissiveStrength.value = 1.00;
                foliageSss.value = 0.90;
                cloudShading.value = 1.15;
                raytracingStrength.value = 1.15;
                realism.value = 1.10;
            }
            case 1 -> {
                saturationEnabled.value = true;
                saturation.value = 1.15;
                vibranceEnabled.value = true;
                vibrance.value = 1.16;
                contrastEnabled.value = true;
                contrast.value = 1.18;
                brightnessEnabled.value = false;
                brightness.value = 1.00;
                exposureEnabled.value = true;
                exposure.value = 1.08;
                bloomEnabled.value = true;
                bloom.value = 0.48;
                warmthEnabled.value = true;
                warmth.value = 1.08;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.12;
                toneMap.value = 3.0;
                gamma.value = 1.14;
                godRays.value = 1.15;
                ssao.value = 0.72;
                depthFog.value = 0.48;
                waterShimmer.value = 1.25;
                windStrength.value = 1.00;
                pbrStrength.value = 1.15;
                emissiveStrength.value = 1.05;
                foliageSss.value = 0.90;
                cloudShading.value = 1.20;
                raytracingStrength.value = 1.35;
                realism.value = 1.25;
            }
            case 2 -> {
                saturationEnabled.value = true;
                saturation.value = 1.24;
                vibranceEnabled.value = true;
                vibrance.value = 1.22;
                contrastEnabled.value = true;
                contrast.value = 1.15;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.08;
                bloomEnabled.value = true;
                bloom.value = 0.56;
                warmthEnabled.value = true;
                warmth.value = 1.14;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.12;
                toneMap.value = 1.0;
                gamma.value = 1.16;
                godRays.value = 1.30;
                ssao.value = 0.58;
                depthFog.value = 0.58;
                waterShimmer.value = 1.20;
                windStrength.value = 1.05;
                pbrStrength.value = 0.80;
                emissiveStrength.value = 1.10;
                foliageSss.value = 0.95;
                cloudShading.value = 1.35;
                raytracingStrength.value = 1.10;
                realism.value = 1.25;
            }
            case 3 -> {
                saturationEnabled.value = true;
                saturation.value = 1.14;
                vibranceEnabled.value = true;
                vibrance.value = 1.15;
                contrastEnabled.value = true;
                contrast.value = 1.12;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.04;
                bloomEnabled.value = true;
                bloom.value = 0.58;
                warmthEnabled.value = true;
                warmth.value = 0.92;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.16;
                toneMap.value = 2.0;
                gamma.value = 1.16;
                godRays.value = 0.95;
                ssao.value = 0.55;
                depthFog.value = 0.72;
                waterShimmer.value = 0.95;
                windStrength.value = 0.95;
                pbrStrength.value = 0.70;
                emissiveStrength.value = 1.45;
                foliageSss.value = 0.85;
                cloudShading.value = 1.15;
                raytracingStrength.value = 1.00;
                realism.value = 1.10;
            }
            case 4 -> {
                saturationEnabled.value = true;
                saturation.value = 1.22;
                vibranceEnabled.value = true;
                vibrance.value = 1.18;
                contrastEnabled.value = true;
                contrast.value = 1.14;
                brightnessEnabled.value = false;
                brightness.value = 1.00;
                exposureEnabled.value = true;
                exposure.value = 1.06;
                bloomEnabled.value = true;
                bloom.value = 0.36;
                warmthEnabled.value = true;
                warmth.value = 1.05;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.10;
                toneMap.value = 1.0;
                gamma.value = 1.14;
                godRays.value = 0.80;
                ssao.value = 0.62;
                depthFog.value = 0.38;
                waterShimmer.value = 1.00;
                windStrength.value = 1.00;
                pbrStrength.value = 0.90;
                emissiveStrength.value = 1.00;
                foliageSss.value = 0.85;
                cloudShading.value = 0.90;
                raytracingStrength.value = 1.05;
                realism.value = 0.90;
            }
            case 5 -> {
                saturationEnabled.value = true;
                saturation.value = 1.38;
                vibranceEnabled.value = true;
                vibrance.value = 1.34;
                contrastEnabled.value = true;
                contrast.value = 1.16;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.06;
                bloomEnabled.value = true;
                bloom.value = 0.36;
                warmthEnabled.value = true;
                warmth.value = 1.04;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.08;
                toneMap.value = 1.0;
                gamma.value = 1.14;
                godRays.value = 0.80;
                ssao.value = 0.55;
                depthFog.value = 0.30;
                waterShimmer.value = 0.95;
                windStrength.value = 1.05;
                pbrStrength.value = 0.85;
                emissiveStrength.value = 1.05;
                foliageSss.value = 0.95;
                cloudShading.value = 0.95;
                raytracingStrength.value = 0.95;
                realism.value = 1.00;
            }
            case 6 -> {
                saturationEnabled.value = true;
                saturation.value = 1.15;
                vibranceEnabled.value = true;
                vibrance.value = 1.12;
                contrastEnabled.value = true;
                contrast.value = 1.24;
                brightnessEnabled.value = false;
                brightness.value = 0.98;
                exposureEnabled.value = true;
                exposure.value = 1.04;
                bloomEnabled.value = true;
                bloom.value = 0.42;
                warmthEnabled.value = true;
                warmth.value = 1.05;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.25;
                toneMap.value = 3.0;
                gamma.value = 1.12;
                godRays.value = 0.90;
                ssao.value = 0.75;
                depthFog.value = 0.48;
                waterShimmer.value = 0.95;
                windStrength.value = 0.90;
                pbrStrength.value = 0.95;
                emissiveStrength.value = 0.95;
                foliageSss.value = 0.80;
                cloudShading.value = 1.05;
                raytracingStrength.value = 1.15;
                realism.value = 1.20;
            }
        }
    }

    public static boolean isShaderActive() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static boolean isMotionBlurActive() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.motionBlurEnabled.value;
    }

    public static float getEffectiveSaturation() {
        if (!isShaderActive() || !INSTANCE.saturationEnabled.value) return 1.0f;
        return (float) INSTANCE.saturation.value;
    }

    public static float getEffectiveVibrance() {
        if (!isShaderActive() || !INSTANCE.vibranceEnabled.value) return 1.0f;
        return (float) INSTANCE.vibrance.value;
    }

    public static float getEffectiveContrast() {
        if (!isShaderActive() || !INSTANCE.contrastEnabled.value) return 1.0f;
        return (float) INSTANCE.contrast.value;
    }

    public static float getEffectiveBrightness() {
        if (!isShaderActive() || !INSTANCE.brightnessEnabled.value) return 1.0f;
        return (float) INSTANCE.brightness.value;
    }

    public static float getEffectiveExposure() {
        if (!isShaderActive() || !INSTANCE.exposureEnabled.value) return 1.0f;
        return (float) INSTANCE.exposure.value;
    }

    public static float getEffectiveBloom() {
        if (!isShaderActive() || !INSTANCE.bloomEnabled.value) return 0.0f;
        return (float) INSTANCE.bloom.value;
    }

    public static float getEffectiveWarmth() {
        if (!isShaderActive() || !INSTANCE.warmthEnabled.value) return 1.0f;
        return (float) INSTANCE.warmth.value;
    }

    public static float getEffectiveVignette() {
        if (!isShaderActive() || !INSTANCE.vignetteEnabled.value) return 0.0f;
        return (float) INSTANCE.vignetteStrength.value;
    }

    public static float getEffectiveToneMap() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.toneMap.value;
    }

    public static float getEffectiveGamma() {
        if (!isShaderActive()) return 1.0f;
        return (float) INSTANCE.gamma.value;
    }

    public static float getEffectiveGodRays() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.godRays.value;
    }

    public static float getEffectiveSSAO() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.ssao.value;
    }

    public static float getEffectiveDepthFog() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.depthFog.value;
    }

    public static float getEffectiveWaterShimmer() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.waterShimmer.value;
    }

    public static float getEffectiveWindStrength() {
        if (!isShaderActive() || !INSTANCE.wavingVegetation.value) return 0.0f;
        return (float) INSTANCE.windStrength.value;
    }

    public static float getEffectiveWavingWater() {
        if (!isShaderActive() || !INSTANCE.wavingWater.value) return 0.0f;
        return (float) Math.max(0.45, Math.min(1.6, INSTANCE.waterShimmer.value * 0.70 + 0.45));
    }

    public static float getEffectiveSunLighting() {
        if (!isShaderActive() || !INSTANCE.sunLighting.value) return 0.0f;
        return 1.0f;
    }

    public static float getEffectivePbrStrength() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.pbrStrength.value;
    }

    public static float getEffectiveEmissiveStrength() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.emissiveStrength.value;
    }

    public static float getEffectiveFoliageSss() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.foliageSss.value;
    }

    public static float getEffectiveCloudShading() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.cloudShading.value;
    }

    public static float getEffectiveRaytracingStrength() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.raytracingStrength.value;
    }

    public static float getEffectiveRealism() {
        if (!isShaderActive()) return 0.0f;
        return (float) INSTANCE.realism.value;
    }

    public static float getEffectiveMotionBlur() {
        if (!isMotionBlurActive()) return 0.0f;
        return (float) Math.max(0.05, Math.min(0.95, INSTANCE.motionBlurStrength.value));
    }

    private static final int MAX_RIPPLES = 16;
    private static final double RIPPLE_LIFETIME = 3.2;
    private static final WaterRippleSlot[] RIPPLE_SLOTS = new WaterRippleSlot[MAX_RIPPLES];
    private static final Map<Integer, WaterEntityState> WATER_ENTITY_STATES = new HashMap<>();
    private static double lastPruneSeconds = 0.0;
    private static final ThreadLocal<Integer> TERRAIN_TAG = ThreadLocal.withInitial(() -> 0);
    private static final ConcurrentHashMap<Block, Integer> BLOCK_MAT_CACHE = new ConcurrentHashMap<>();

    private static int resolveBaseMaterialClass(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        BlockState def = block.defaultBlockState();
        if (block == Blocks.LAVA || block == Blocks.LAVA_CAULDRON) {
            return 9;
        }
        if (block == Blocks.SEA_LANTERN || block == Blocks.END_ROD || block == Blocks.BEACON
                || block == Blocks.CONDUIT || block == Blocks.SOUL_LANTERN || block == Blocks.SOUL_TORCH
                || block == Blocks.SOUL_WALL_TORCH || block == Blocks.SOUL_FIRE || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.CRYING_OBSIDIAN || block == Blocks.RESPAWN_ANCHOR
                || block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.SCULK_CATALYST || block == Blocks.SCULK_SHRIEKER || block == Blocks.SCULK_SENSOR
                || block == Blocks.VERDANT_FROGLIGHT || block == Blocks.PEARLESCENT_FROGLIGHT
                || block == Blocks.ENCHANTING_TABLE
                || id.contains("amethyst_cluster") || id.contains("amethyst_bud")) {
            return 6;
        }
        if (block == Blocks.GLOWSTONE || block == Blocks.SHROOMLIGHT || block == Blocks.MAGMA_BLOCK
                || block == Blocks.JACK_O_LANTERN || block == Blocks.OCHRE_FROGLIGHT
                || block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.LANTERN
                || block == Blocks.CAMPFIRE || block == Blocks.FIRE) {
            return 5;
        }
        if (block == Blocks.REDSTONE_BLOCK || block == Blocks.REDSTONE_TORCH
                || block == Blocks.REDSTONE_WALL_TORCH || block == Blocks.REDSTONE_WIRE) {
            return 7;
        }
        if (def.is(BlockTags.ICE) || block == Blocks.GLASS || block == Blocks.TINTED_GLASS
                || block == Blocks.GLASS_PANE || block == Blocks.OBSIDIAN
                || block == Blocks.AMETHYST_BLOCK || block == Blocks.BUDDING_AMETHYST
                || block == Blocks.SLIME_BLOCK || block == Blocks.HONEY_BLOCK
                || id.contains("stained_glass") || id.contains("glazed_terracotta") || id.contains("prismarine")) {
            return 4;
        }
        if (def.is(BlockTags.GOLD_ORES) || def.is(BlockTags.IRON_ORES) || def.is(BlockTags.COPPER_ORES)
                || id.contains("diamond_ore") || id.contains("emerald_ore") || id.contains("lapis_ore")
                || id.contains("copper") || id.contains("iron_") || id.contains("_iron")
                || id.contains("gold_") || id.contains("_gold")
                || id.contains("diamond_block") || id.contains("emerald_block")
                || id.contains("netherite") || id.contains("lapis_block")
                || id.contains("anvil") || id.contains("cauldron") || id.contains("chain")
                || id.contains("rail") || block == Blocks.HOPPER) {
            return 3;
        }
        if (def.is(BlockTags.STONE_BRICKS) || def.is(BlockTags.TERRACOTTA)
                || id.contains("quartz") || id.contains("polished_") || id.contains("smooth_")
                || id.contains("concrete") || id.contains("bricks") || id.contains("tiles")
                || block == Blocks.CALCITE) {
            return 2;
        }
        if (def.is(BlockTags.PLANKS) || def.is(BlockTags.LOGS) || def.is(BlockTags.WOODEN_SLABS)
                || def.is(BlockTags.WOODEN_STAIRS) || def.is(BlockTags.WOODEN_FENCES)
                || def.is(BlockTags.WOODEN_DOORS) || def.is(BlockTags.WOODEN_TRAPDOORS)
                || block == Blocks.BARREL || block == Blocks.BOOKSHELF || block == Blocks.CHISELED_BOOKSHELF
                || block == Blocks.CRAFTING_TABLE || block == Blocks.LECTERN
                || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            return 1;
        }
        return 0;
    }

    public static void setTerrainFluidTag(FluidState fluid) {
        if (!isShaderActive() || fluid == null || fluid.isEmpty()) {
            TERRAIN_TAG.set(0);
            return;
        }
        if (fluid.is(FluidTags.LAVA)) {
            TERRAIN_TAG.set(9 << 11);
        } else {
            TERRAIN_TAG.set(0);
        }
    }

    public static void setTerrainBlockTag(BlockState state, net.minecraft.client.renderer.chunk.RenderSectionRegion region, BlockPos pos) {
        if (state == null || !isShaderActive()) {
            TERRAIN_TAG.set(0);
            return;
        }
        int tag = 0;
        if (state.is(BlockTags.LEAVES)) {
            tag |= 0x0100;
        } else if (state.is(Blocks.FIREFLY_BUSH)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.BUSH)
                || state.is(Blocks.SHORT_DRY_GRASS)
                || state.is(Blocks.TALL_DRY_GRASS)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)) {
            tag |= 0x0200;
        }
        boolean submerged = !state.getFluidState().isEmpty()
                || (region != null && pos != null && !region.getBlockState(pos.above()).getFluidState().isEmpty());
        if (submerged) {
            tag |= 0x0400;
        }
        int matClass = BLOCK_MAT_CACHE.computeIfAbsent(state.getBlock(), KollegenShader::resolveBaseMaterialClass);
        if (matClass == 0) {
            if (state.getFluidState().is(FluidTags.LAVA)) {
                matClass = 9;
            } else if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                matClass = (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) ? 7 : 5;
            } else if (state.getLightEmission() >= 6 && (tag & 0x0300) == 0) {
                matClass = 8;
            }
        }
        tag |= (matClass & 0xF) << 11;
        TERRAIN_TAG.set(tag);
    }

    public static int applyTerrainTag(int packedLight) {
        int tag = TERRAIN_TAG.get();
        if (tag == 0) {
            return packedLight;
        }
        return (packedLight & ~0x7F00) | tag;
    }

    static {
        for (int i = 0; i < MAX_RIPPLES; i++) {
            RIPPLE_SLOTS[i] = new WaterRippleSlot();
        }
    }

    private static final class WaterRippleSlot {
        double x;
        double y;
        double z;
        double spawnTime = -10.0;
        float amp = 0.0f;
    }

    private static final class WaterEntityState {
        boolean onSurface;
        boolean wasOnGround;
        double lastX;
        double lastY;
        double lastZ;
        double lastEmitX;
        double lastEmitZ;
        double lastRippleTime;
        double lastUpdateTime;
        double lastSeenTime;
    }

    private static double getUnwrappedSeconds() {
        return (System.nanoTime() - START_NANOS) * 1.0e-9;
    }

    private static float getContinuousSeconds() {
        return (float) (getUnwrappedSeconds() % 3600.0);
    }

    private static void spawnWaterRipple(double x, double y, double z, double now, float amp) {
        int bestIdx = -1;
        double lowestScore = Double.MAX_VALUE;
        for (int i = 0; i < MAX_RIPPLES; i++) {
            WaterRippleSlot s = RIPPLE_SLOTS[i];
            double age = now - s.spawnTime;
            if (age >= RIPPLE_LIFETIME || s.amp <= 0.01f) {
                bestIdx = i;
                break;
            }
            double rem = Math.max(0.0, 1.0 - age / RIPPLE_LIFETIME) * s.amp;
            if (rem < lowestScore) {
                lowestScore = rem;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            return;
        }
        WaterRippleSlot existing = RIPPLE_SLOTS[bestIdx];
        double existingAge = now - existing.spawnTime;
        if (existingAge < RIPPLE_LIFETIME && lowestScore > amp * 1.15) {
            return;
        }
        existing.x = x;
        existing.y = y;
        existing.z = z;
        existing.spawnTime = now;
        existing.amp = Math.max(0.15f, Math.min(1.85f, amp));
    }

    private static double findSurfaceWaterY(Level level, Entity entity) {
        double ex = entity.getX();
        double ey = entity.getY();
        double ez = entity.getZ();
        double h = Math.max(0.35, entity.getBbHeight());
        int bx = Mth.floor(ex);
        int bz = Mth.floor(ez);
        int minY = Mth.floor(ey - 0.12);
        int maxY = Mth.floor(ey + h + 0.12);
        double surfaceY = Double.NaN;
        for (int by = maxY; by >= minY; by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            FluidState fs = level.getFluidState(pos);
            if (fs.is(FluidTags.WATER)) {
                BlockPos above = pos.above();
                if (!level.getFluidState(above).is(FluidTags.WATER)) {
                    double topY = pos.getY() + fs.getHeight(level, pos);
                    boolean touchingWater = entity.isInWater() || (!entity.onGround() && ey <= topY + 0.03 && (ey + h) >= topY);
                    if (touchingWater && ey <= topY + 0.04 && (ey + h) >= topY - 0.18) {
                        surfaceY = topY;
                    }
                }
                break;
            }
        }
        return surfaceY;
    }

    private static void updateAndPopulateRipples(float[] outBuf, Vec3 cameraPos, float waterStrength) {
        double now = getUnwrappedSeconds();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && cameraPos != null && waterStrength > 0.05f) {
            try {
                float rainWetNow = (INSTANCE != null && INSTANCE.rainPuddles.value)
                        ? Math.max(0.0f, Math.min(1.0f, mc.level.getRainLevel(1.0f)))
                        : 0.0f;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity == null || !entity.isAlive()) continue;
                    double ex = entity.getX();
                    double ey = entity.getY();
                    double ez = entity.getZ();
                    double dxCam = ex - cameraPos.x;
                    double dzCam = ez - cameraPos.z;
                    if (dxCam * dxCam + dzCam * dzCam > 2704.0) continue;

                    double surfY = findSurfaceWaterY(mc.level, entity);
                    boolean onSurfaceNow = !Double.isNaN(surfY);
                    boolean isItemOrProj = entity instanceof ItemEntity || entity instanceof net.minecraft.world.entity.projectile.Projectile;
                    int id = entity.getId();
                    WaterEntityState st = WATER_ENTITY_STATES.get(id);
                    if (st == null) {
                        st = new WaterEntityState();
                        st.onSurface = onSurfaceNow;
                        st.wasOnGround = entity.onGround();
                        st.lastX = ex;
                        st.lastY = ey;
                        st.lastZ = ez;
                        st.lastEmitX = ex;
                        st.lastEmitZ = ez;
                        st.lastRippleTime = now - 0.4;
                        st.lastUpdateTime = now;
                        st.lastSeenTime = now;
                        WATER_ENTITY_STATES.put(id, st);
                        continue;
                    }

                    double dt = now - st.lastRippleTime;
                    double frameDt = Math.max(0.005, now - st.lastUpdateTime);
                    double horizSpeed = Math.hypot(ex - st.lastX, ez - st.lastZ) / frameDt;
                    double fallSpeed = Math.max(-entity.getDeltaMovement().y * 12.0, Math.max(0.0, (st.lastY - ey) / frameDt));
                    boolean onGroundNow = entity.onGround();

                    if (!st.onSurface && onSurfaceNow) {
                        float impactScale = (float) Math.min(1.65, 0.42 + fallSpeed * 0.11);
                        float typeBase = isItemOrProj ? 0.62f : Math.max(0.70f, Math.min(1.35f, entity.getBbWidth() * 1.35f));
                        float splashAmp = impactScale * typeBase * Math.max(0.65f, Math.min(1.15f, waterStrength));
                        spawnWaterRipple(ex, surfY, ez, now, splashAmp);
                        st.lastEmitX = ex;
                        st.lastEmitZ = ez;
                        st.lastRippleTime = now;
                    } else if (st.onSurface && !onSurfaceNow && dt >= 0.24) {
                        float exitAmp = (isItemOrProj ? 0.34f : 0.68f) * Math.max(0.65f, Math.min(1.10f, waterStrength));
                        spawnWaterRipple(ex, ey + 0.08, ez, now, exitAmp);
                        st.lastEmitX = ex;
                        st.lastEmitZ = ez;
                        st.lastRippleTime = now;
                    } else if (onSurfaceNow) {
                        double moved = Math.hypot(ex - st.lastEmitX, ez - st.lastEmitZ);
                        double vertStep = Math.abs(ey - st.lastY);
                        if ((moved >= 0.42 && dt >= 0.18) || (vertStep >= 0.055 && dt >= 0.24)) {
                            float speedFactor = (float) Math.max(0.32, Math.min(1.30, horizSpeed * 0.18 + vertStep * 2.2 + 0.26));
                            float sizeScale = isItemOrProj ? 0.65f : Math.max(0.68f, Math.min(1.25f, entity.getBbWidth() * 1.18f));
                            float moveAmp = speedFactor * sizeScale * Math.max(0.65f, Math.min(1.15f, waterStrength));
                            spawnWaterRipple(ex, surfY, ez, now, moveAmp);
                            st.lastEmitX = ex;
                            st.lastEmitZ = ez;
                            st.lastRippleTime = now;
                        } else if (isItemOrProj && vertStep >= 0.012 && dt >= 0.55) {
                            spawnWaterRipple(ex, surfY, ez, now, 0.26f * Math.max(0.65f, Math.min(1.10f, waterStrength)));
                            st.lastRippleTime = now;
                        }
                    } else if (rainWetNow > 0.15f && onGroundNow && mc.level.canSeeSky(BlockPos.containing(ex, ey + 0.18, ez))) {
                        double moved = Math.hypot(ex - st.lastEmitX, ez - st.lastEmitZ);
                        if (!st.wasOnGround) {
                            float pAmp = (float) Math.min(0.75, 0.40 + fallSpeed * 0.06) * rainWetNow;
                            spawnWaterRipple(ex, ey, ez, now, pAmp);
                            st.lastEmitX = ex;
                            st.lastEmitZ = ez;
                            st.lastRippleTime = now;
                        } else if (moved >= 0.46 && dt >= 0.22) {
                            float pAmp = (float) Math.min(0.65, 0.28 + horizSpeed * 0.075) * rainWetNow;
                            spawnWaterRipple(ex, ey, ez, now, pAmp);
                            st.lastEmitX = ex;
                            st.lastEmitZ = ez;
                            st.lastRippleTime = now;
                        }
                    }

                    st.onSurface = onSurfaceNow;
                    st.wasOnGround = onGroundNow;
                    st.lastX = ex;
                    st.lastY = ey;
                    st.lastZ = ez;
                    st.lastUpdateTime = now;
                    st.lastSeenTime = now;
                }
                if ((now - lastPruneSeconds) > 2.0) {
                    lastPruneSeconds = now;
                    WATER_ENTITY_STATES.entrySet().removeIf(e -> (now - e.getValue().lastSeenTime) > 3.0);
                }
            } catch (Throwable ignored) {
            }
        }

        double camX = cameraPos != null ? cameraPos.x : 0.0;
        double camY = cameraPos != null ? cameraPos.y : 0.0;
        double camZ = cameraPos != null ? cameraPos.z : 0.0;
        int slots = Math.min(MAX_RIPPLES, Math.max(0, (outBuf.length - 16) / 4));
        for (int i = 0; i < slots; i++) {
            int base = 16 + i * 4;
            WaterRippleSlot s = RIPPLE_SLOTS[i];
            double age = now - s.spawnTime;
            if (age >= 0.0 && age < RIPPLE_LIFETIME && s.amp > 0.01f && waterStrength > 0.05f) {
                outBuf[base] = (float) (s.x - camX);
                outBuf[base + 1] = (float) (s.y - camY);
                outBuf[base + 2] = (float) (s.z - camZ);
                float clampedAge = Math.max(0.002f, Math.min(3.4f, (float) age));
                float clampedAmp = Math.max(0.10f, Math.min(1.85f, s.amp));
                outBuf[base + 3] = Math.round(clampedAmp * 20.0f) * 4.0f + clampedAge;
            } else {
                outBuf[base] = 0.0f;
                outBuf[base + 1] = 0.0f;
                outBuf[base + 2] = 0.0f;
                outBuf[base + 3] = 0.0f;
            }
        }
    }

    private static void computeSunVectorAndRain(float partialTick, float[] outSunAndRain) {
        float sunX = 0.55f;
        float sunY = 0.75f;
        float sunZ = 0.36f;
        float celestialX = 0.55f;
        float celestialY = 0.75f;
        float celestialZ = 0.0f;
        float dayFactor = 1.0f;
        float rainWet = 0.0f;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.gameRenderer != null) {
            try {
                if (INSTANCE != null && INSTANCE.rainPuddles.value) {
                    rainWet = Math.max(0.0f, Math.min(1.0f, mc.level.getRainLevel(partialTick)));
                }
            } catch (Throwable ignored) {
            }
            try {
                Camera camera = mc.gameRenderer.mainCamera();
                if (camera != null && camera.isInitialized()) {
                    float rawSunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
                    float sunAngle = (float) Math.toRadians(rawSunAngle);
                    celestialX = (float) -Math.sin(sunAngle);
                    celestialY = (float) Math.cos(sunAngle);
                    celestialZ = 0.0f;
                    dayFactor = Math.max(0.0f, Math.min(1.0f, celestialY * 2.4f + 0.45f));
                    float keySign = celestialY >= -0.08f ? 1.0f : -1.0f;
                    float lx = celestialX * keySign;
                    float ly = Math.max(0.14f, celestialY * keySign);
                    float lz = 0.16f * keySign;
                    float len = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                    if (len > 0.0001f) {
                        sunX = lx / len;
                        sunY = ly / len;
                        sunZ = lz / len;
                    }
                    celestialX *= keySign;
                    celestialY *= keySign;
                }
            } catch (Throwable ignored) {
            }
        }
        outSunAndRain[0] = sunX;
        outSunAndRain[1] = sunY;
        outSunAndRain[2] = sunZ;
        outSunAndRain[3] = dayFactor;
        outSunAndRain[4] = rainWet;
        outSunAndRain[5] = celestialX;
        outSunAndRain[6] = celestialY;
        outSunAndRain[7] = celestialZ;
    }

    public static void populateGlobalUniforms(float[] out16, float partialTick) {
        populateGlobalUniforms(out16, null, partialTick);
    }

    public static void populateGlobalUniforms(float[] outBuf, Vec3 cameraPos, float partialTick) {
        if (!isShaderActive()) {
            java.util.Arrays.fill(outBuf, 0.0f);
            outBuf[5] = 1.0f;
            outBuf[14] = 1.0f;
            return;
        }
        float[] sunRain = new float[8];
        computeSunVectorAndRain(partialTick, sunRain);
        float shimmer = getEffectiveWaterShimmer();
        outBuf[0] = 1.0f;
        outBuf[1] = getEffectiveWindStrength();
        outBuf[2] = getEffectiveWavingWater();
        outBuf[3] = getEffectiveSunLighting();
        outBuf[4] = sunRain[0];
        outBuf[5] = sunRain[1];
        outBuf[6] = sunRain[2];
        outBuf[7] = sunRain[3];
        outBuf[8] = getEffectivePbrStrength();
        outBuf[9] = getEffectiveEmissiveStrength();
        outBuf[10] = getEffectiveFoliageSss();
        outBuf[11] = shimmer;
        outBuf[12] = getContinuousSeconds();
        outBuf[13] = sunRain[4];
        outBuf[14] = getEffectiveWarmth();
        outBuf[15] = getEffectiveCloudShading();
        if (outBuf.length >= 48) {
            updateAndPopulateRipples(outBuf, cameraPos, shimmer);
        }
        if (outBuf.length >= 84) {
            float dimType = 0.0f;
            float heldStr = 0.0f;
            float heldHue = 0.0f;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                var dim = mc.level.dimension();
                if (dim == Level.NETHER) {
                    dimType = 1.0f;
                } else if (dim == Level.END) {
                    dimType = 2.0f;
                }
                if (mc.player != null) {
                    float[] hMain = classifyHeldItem(mc.player.getMainHandItem());
                    float[] hOff = classifyHeldItem(mc.player.getOffhandItem());
                    if (hOff[0] > hMain[0]) {
                        heldStr = hOff[0];
                        heldHue = hOff[1];
                    } else {
                        heldStr = hMain[0];
                        heldHue = hMain[1];
                    }
                }
            }
            outBuf[80] = getEffectiveRealism();
            outBuf[81] = dimType;
            outBuf[82] = heldStr;
            outBuf[83] = heldHue;
        }
    }

    private static float[] classifyHeldItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new float[]{0.0f, 0.0f};
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("sea_lantern") || path.contains("end_rod") || path.contains("beacon")
                || path.contains("conduit") || path.contains("soul_lantern") || path.contains("soul_torch")
                || path.contains("soul_campfire") || path.contains("pearlescent_froglight")
                || path.contains("verdant_froglight")) {
            return new float[]{0.95f, 1.0f};
        }
        if (path.contains("redstone_torch") || path.contains("redstone_block")) {
            return new float[]{0.55f, 2.0f};
        }
        if (path.contains("lantern") || path.contains("torch") || path.contains("glowstone")
                || path.contains("lava_bucket") || path.contains("blaze_rod") || path.contains("shroomlight")
                || path.contains("jack_o_lantern") || path.contains("magma") || path.contains("campfire")
                || path.contains("ochre_froglight") || path.contains("candle")) {
            return new float[]{0.88f, 0.0f};
        }
        return new float[]{0.0f, 0.0f};
    }

    public static void updateShaderUniforms(PostChain chain) {
        if (chain == null) return;
        try {
            float sat = getEffectiveSaturation();
            float cont = getEffectiveContrast();
            float bright = getEffectiveBrightness();
            float vig = getEffectiveVignette();
            float bloom = getEffectiveBloom();
            float exp = getEffectiveExposure();
            float warm = getEffectiveWarmth();
            float vib = getEffectiveVibrance();
            float tm = getEffectiveToneMap();
            float gamma = getEffectiveGamma();
            float rays = getEffectiveGodRays();
            float ssao = getEffectiveSSAO();
            float fog = getEffectiveDepthFog();
            float shimmer = getEffectiveWaterShimmer();
            float rtStrength = getEffectiveRaytracingStrength();

            float sunScreenX = 0.5f;
            float sunScreenY = 0.25f;
            float sunVis = 0.0f;
            float gameTime = getContinuousSeconds();
            float farPlane = 256.0f;
            float[] sunRain = new float[8];
            float rainWet = 0.0f;
            float dayFactor = 1.0f;
            float tanHalfFov = (float) Math.tan(Math.toRadians(35.0));
            float aspect = 16.0f / 9.0f;
            float sunViewX = 0.0f;
            float sunViewY = 0.6f;
            float sunViewZ = -0.8f;
            float worldUpViewX = 0.0f;
            float worldUpViewY = 1.0f;
            float worldUpViewZ = 0.0f;
            float screenYFlip = 1.0f;

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null && mc.gameRenderer != null) {
                try {
                    var info = RenderSystem.getDevice().getDeviceInfo();
                    if (info != null) {
                        String bn = info.backendName();
                        if (info.isZZeroToOne() || (bn != null && bn.toLowerCase(java.util.Locale.ROOT).contains("vulkan"))) {
                            screenYFlip = -1.0f;
                        }
                    }
                } catch (Throwable ignored) {
                }
                float partialTick = 1.0f;
                try {
                    partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                } catch (Throwable ignored) {
                }
                computeSunVectorAndRain(partialTick, sunRain);
                dayFactor = sunRain[3];
                rainWet = sunRain[4];

                farPlane = (float) mc.options.getEffectiveRenderDistance() * 16.0f;
                if (farPlane < 32.0f) farPlane = 128.0f;
                aspect = (float) mc.getWindow().getWidth() / Math.max(1.0f, (float) mc.getWindow().getHeight());
                double fovDeg = mc.options.fov().get();
                fovDeg = Math.max(30.0, Math.min(130.0, fovDeg));
                tanHalfFov = (float) Math.tan(Math.toRadians(fovDeg * 0.5));

                try {
                    var grState = mc.gameRenderer.gameRenderState();
                    if (grState != null && grState.levelRenderState != null && grState.levelRenderState.cameraRenderState != null) {
                        var crs = grState.levelRenderState.cameraRenderState;
                        if (crs.depthFar > 16.0f) {
                            farPlane = crs.depthFar * 0.25f;
                        }
                        if (crs.projectionMatrix != null) {
                            float m11 = Math.abs(crs.projectionMatrix.m11());
                            float m00 = Math.abs(crs.projectionMatrix.m00());
                            if (m11 > 0.05f && m00 > 0.05f) {
                                tanHalfFov = 1.0f / m11;
                                aspect = m11 / m00;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }

                try {
                    Camera camera = mc.gameRenderer.mainCamera();
                    if (camera != null && camera.isInitialized()) {
                        Vector3fc fwd = camera.forwardVector();
                        Vector3fc up = camera.upVector();
                        Vector3fc left = camera.leftVector();

                        worldUpViewX = -left.y();
                        worldUpViewY = up.y();
                        worldUpViewZ = -fwd.y();

                        float sxW = sunRain[5];
                        float syW = sunRain[6];
                        float szW = sunRain[7];

                        sunViewX = -(sxW * left.x() + syW * left.y() + szW * left.z());
                        sunViewY = sxW * up.x() + syW * up.y() + szW * up.z();
                        sunViewZ = -(sxW * fwd.x() + syW * fwd.y() + szW * fwd.z());

                        float vLen = (float) Math.sqrt(sunViewX * sunViewX + sunViewY * sunViewY + sunViewZ * sunViewZ);
                        if (vLen > 0.0001f) {
                            sunViewX /= vLen;
                            sunViewY /= vLen;
                            sunViewZ /= vLen;
                        }

                        if (sunViewZ < -0.02f && dayFactor > 0.06f) {
                            float projZ = -sunViewZ;
                            float sx = 0.5f + 0.5f * (sunViewX / (projZ * tanHalfFov * aspect));
                            float sy = 0.5f + 0.5f * screenYFlip * (sunViewY / (projZ * tanHalfFov));
                            if (sx >= -0.6f && sx <= 1.6f && sy >= -0.6f && sy <= 1.6f) {
                                sunScreenX = sx;
                                sunScreenY = sy;
                                float daySunMask = Math.max(0.0f, Math.min(1.0f, (dayFactor - 0.06f) / 0.28f));
                                sunVis = Math.max(0.0f, Math.min(1.0f, projZ)) * daySunMask;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            float dimType = 0.0f;
            float camVoxX = 64.0f;
            float camVoxY = 24.0f;
            float camVoxZ = 64.0f;
            float camRightX = 1.0f;
            float camRightY = 0.0f;
            float camRightZ = 0.0f;
            float camUpX = 0.0f;
            float camUpY = 1.0f;
            float camUpZ = 0.0f;
            float camBackX = 0.0f;
            float camBackY = 0.0f;
            float camBackZ = 1.0f;
            float voxReadyFlag = voxReady ? 1.0f : 0.0f;
            if (mc != null && mc.level != null) {
                var dim = mc.level.dimension();
                if (dim == Level.NETHER) {
                    dimType = 1.0f;
                } else if (dim == Level.END) {
                    dimType = 2.0f;
                }
                try {
                    Camera camera = mc.gameRenderer.mainCamera();
                    if (camera != null && camera.isInitialized()) {
                        Vec3 cp = camera.position();
                        if (voxReady) {
                            camVoxX = (float) (cp.x - voxOriginX);
                            camVoxY = (float) (cp.y - voxOriginY);
                            camVoxZ = (float) (cp.z - voxOriginZ);
                        }
                        Vector3fc fwd = camera.forwardVector();
                        Vector3fc up = camera.upVector();
                        Vector3fc left = camera.leftVector();
                        camRightX = -left.x();
                        camRightY = -left.y();
                        camRightZ = -left.z();
                        camUpX = up.x();
                        camUpY = up.y();
                        camUpZ = up.z();
                        camBackX = -fwd.x();
                        camBackY = -fwd.y();
                        camBackZ = -fwd.z();
                    }
                } catch (Throwable ignored) {
                }
            }

            float[] cfg = new float[]{
                    sat, cont, bright, vig,
                    bloom, exp, warm, vib,
                    tm, gamma, rays, ssao,
                    fog, shimmer, gameTime, sunVis,
                    sunScreenX, sunScreenY, farPlane, rainWet,
                    dayFactor, getEffectiveEmissiveStrength(), tanHalfFov, aspect,
                    sunViewX, sunViewY, sunViewZ, rtStrength,
                    worldUpViewX, worldUpViewY, worldUpViewZ, screenYFlip,
                    getEffectiveRealism(), dimType, sunRain[0], sunRain[1],
                    sunRain[2], camVoxX, camVoxY, camVoxZ,
                    camRightX, camRightY, camRightZ, camUpX,
                    camUpY, camUpZ, camBackX, camBackY,
                    camBackZ, voxReadyFlag, 0.0f, 0.0f
            };

            for (PostPass pass : ((PostChainAccessor) chain).kollegen$getPasses()) {
                Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).kollegen$getCustomUniforms();
                if (uniforms == null) continue;
                GpuBuffer buf = uniforms.get("ShaderConfig");
                if (buf != null && !buf.isClosed()) {
                    uploadFloatArray(buf, cfg);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void uploadFloatArray(GpuBuffer buf, float[] values) {
        int maxBytes = (int) Math.min(buf.size(), (long) values.length * 4L);
        if (maxBytes <= 0) return;
        ByteBuffer bb = ByteBuffer.allocateDirect(maxBytes).order(ByteOrder.nativeOrder());
        int count = maxBytes / 4;
        for (int i = 0; i < count; i++) {
            bb.putFloat(values[i]);
        }
        bb.flip();

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(buf.slice(0, maxBytes), bb);
            encoder.submit();
            return;
        } catch (Throwable ignored) {
        }

        try (GpuBufferSlice.MappedView view = buf.map(0, maxBytes, false, true)) {
            ByteBuffer vbb = view.data();
            vbb.order(ByteOrder.nativeOrder());
            for (int i = 0; i < count; i++) {
                vbb.putFloat(i * 4, values[i]);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void updateBlurUniforms(PostChain chain) {
        if (chain == null) return;
        try {
            float blur = getEffectiveMotionBlur();
            for (PostPass pass : ((PostChainAccessor) chain).kollegen$getPasses()) {
                Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).kollegen$getCustomUniforms();
                if (uniforms == null) continue;
                GpuBuffer buf = uniforms.get("BlurConfig");
                if (buf == null || buf.isClosed()) continue;
                uploadFloatArray(buf, new float[]{blur});
            }
        } catch (Throwable ignored) {
        }
    }
}
