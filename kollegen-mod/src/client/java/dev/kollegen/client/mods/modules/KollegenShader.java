package dev.kollegen.client.mods.modules;

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
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public class KollegenShader extends Module {
    public static KollegenShader INSTANCE;

    public static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath("kollegen", "kollegen_shader");
    public static final Identifier BLUR_ID = Identifier.fromNamespaceAndPath("kollegen", "motion_blur");

    public final ModeSetting preset = new ModeSetting(
            "Shader-Profil",
            "Vorgefertigte Farbanpassungen oder eigener Shader.",
            new String[]{
                    "Benutzerdefiniert",
                    "BSL Shaders Style",
                    "Complementary Reimagined",
                    "Complementary Unbound",
                    "Bliss Shaders Style",
                    "Vibrant (Lebendig)",
                    "Cinematic Kino",
                    "Warm & Sonnig"
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
            1.40, 0.0, 3.0, 0.05,
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
            1.30, 0.5, 2.5, 0.05,
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
            1.15, 0.5, 2.0, 0.02,
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
            1.05, 0.5, 2.0, 0.05,
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
            1.10, 0.5, 2.0, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final BooleanSetting bloomEnabled = new BooleanSetting(
            "Bloom",
            "Weiches Leuchten heller Lichtquellen und Reflexionen.",
            true
    );

    public final SliderSetting bloom = new SliderSetting(
            "Bloom-Stärke",
            "Intensität des Lichtleuchtens.",
            0.40, 0.0, 1.0, 0.05,
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
            1.08, 0.7, 1.4, 0.02,
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
            0.15, 0.05, 1.0, 0.05,
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
            0.4, 0.05, 0.95, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting toneMap = new SliderSetting(
            "Tone Mapping",
            "Tonemapping-Algorithmus",
            1.0, 0.0, 3.0, 1.0,
            v -> v < 0.5 ? "Linear" : (v < 1.5 ? "Beryl ACES" : (v < 2.5 ? "Reinhard-Jodie" : "Uncharted 2")), null
    );

    public final SliderSetting gamma = new SliderSetting(
            "Gamma-Korrektur",
            "Tonwertkurve und Gammakorrektur.",
            1.18, 0.8, 1.6, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final SliderSetting godRays = new SliderSetting(
            "Sonnenstrahlen (God Rays)",
            "Volumetrische Lichtstrahlen durch Bäume und Berge.",
            0.45, 0.0, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting ssao = new SliderSetting(
            "Ambient Occlusion (SSAO)",
            "Weiche Tiefenschatten in Ecken und Kanten.",
            0.40, 0.0, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting depthFog = new SliderSetting(
            "Atmosphärischer Tiefennebel",
            "Realistischer Dunst entfernter Landschaft basierend auf Tiefenpuffer.",
            0.35, 0.0, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting waterShimmer = new SliderSetting(
            "Wasser-Glanz & Schimmer",
            "Wellenreflexionen und sonnenabhängiges Glitzern.",
            0.35, 0.0, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public KollegenShader() {
        super("kollegenshader", "Kollegen Shader", "Eigen anpassbarer Vulkan Shader - Farbsättigung, Motion Blur, Bloom, Kontrast und Bildfilter rein im Kollegen Client.", Category.VISUAL);
        INSTANCE = this;
        add(preset);
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
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
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
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
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
                    list.removeIf(chain -> chain instanceof net.minecraft.client.renderer.PostChain pc && (SHADER_ID.equals(pc.id()) || BLUR_ID.equals(pc.id())));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void applyPreset(int index) {
        switch (index) {
            case 0 -> {
                saturationEnabled.value = true;
                saturation.value = 1.20;
                vibranceEnabled.value = true;
                vibrance.value = 1.18;
                contrastEnabled.value = true;
                contrast.value = 1.12;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.04;
                bloomEnabled.value = true;
                bloom.value = 0.25;
                warmthEnabled.value = true;
                warmth.value = 1.04;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.10;
                toneMap.value = 1.0;
                gamma.value = 1.18;
                godRays.value = 0.45;
                ssao.value = 0.40;
                depthFog.value = 0.35;
                waterShimmer.value = 0.35;
            }
            case 1 -> {
                saturationEnabled.value = true;
                saturation.value = 1.25;
                vibranceEnabled.value = true;
                vibrance.value = 1.20;
                contrastEnabled.value = true;
                contrast.value = 1.15;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.06;
                bloomEnabled.value = true;
                bloom.value = 0.30;
                warmthEnabled.value = true;
                warmth.value = 1.06;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.12;
                toneMap.value = 1.0;
                gamma.value = 1.16;
                godRays.value = 0.65;
                ssao.value = 0.50;
                depthFog.value = 0.45;
                waterShimmer.value = 0.50;
            }
            case 2 -> {
                saturationEnabled.value = true;
                saturation.value = 1.22;
                vibranceEnabled.value = true;
                vibrance.value = 1.18;
                contrastEnabled.value = true;
                contrast.value = 1.16;
                brightnessEnabled.value = false;
                brightness.value = 1.00;
                exposureEnabled.value = true;
                exposure.value = 1.04;
                bloomEnabled.value = true;
                bloom.value = 0.20;
                warmthEnabled.value = true;
                warmth.value = 1.02;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.10;
                toneMap.value = 2.0;
                gamma.value = 1.18;
                godRays.value = 0.40;
                ssao.value = 0.45;
                depthFog.value = 0.35;
                waterShimmer.value = 0.40;
            }
            case 3 -> {
                saturationEnabled.value = true;
                saturation.value = 1.32;
                vibranceEnabled.value = true;
                vibrance.value = 1.26;
                contrastEnabled.value = true;
                contrast.value = 1.18;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.08;
                bloomEnabled.value = true;
                bloom.value = 0.35;
                warmthEnabled.value = true;
                warmth.value = 1.05;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.12;
                toneMap.value = 1.0;
                gamma.value = 1.20;
                godRays.value = 0.75;
                ssao.value = 0.60;
                depthFog.value = 0.40;
                waterShimmer.value = 0.55;
            }
            case 4 -> {
                saturationEnabled.value = true;
                saturation.value = 1.18;
                vibranceEnabled.value = true;
                vibrance.value = 1.15;
                contrastEnabled.value = true;
                contrast.value = 1.08;
                brightnessEnabled.value = false;
                brightness.value = 1.04;
                exposureEnabled.value = true;
                exposure.value = 1.06;
                bloomEnabled.value = true;
                bloom.value = 0.30;
                warmthEnabled.value = true;
                warmth.value = 1.10;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.10;
                toneMap.value = 2.0;
                gamma.value = 1.22;
                godRays.value = 0.50;
                ssao.value = 0.30;
                depthFog.value = 0.65;
                waterShimmer.value = 0.35;
            }
            case 5 -> {
                saturationEnabled.value = true;
                saturation.value = 1.38;
                vibranceEnabled.value = true;
                vibrance.value = 1.35;
                contrastEnabled.value = true;
                contrast.value = 1.18;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.06;
                bloomEnabled.value = true;
                bloom.value = 0.25;
                warmthEnabled.value = true;
                warmth.value = 1.02;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.08;
                toneMap.value = 1.0;
                gamma.value = 1.15;
                godRays.value = 0.60;
                ssao.value = 0.50;
                depthFog.value = 0.30;
                waterShimmer.value = 0.45;
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
                bloom.value = 0.30;
                warmthEnabled.value = true;
                warmth.value = 1.04;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.25;
                toneMap.value = 3.0;
                gamma.value = 1.12;
                godRays.value = 0.40;
                ssao.value = 0.70;
                depthFog.value = 0.40;
                waterShimmer.value = 0.30;
            }
            case 7 -> {
                saturationEnabled.value = true;
                saturation.value = 1.25;
                vibranceEnabled.value = true;
                vibrance.value = 1.22;
                contrastEnabled.value = true;
                contrast.value = 1.12;
                brightnessEnabled.value = false;
                brightness.value = 1.04;
                exposureEnabled.value = true;
                exposure.value = 1.08;
                bloomEnabled.value = true;
                bloom.value = 0.35;
                warmthEnabled.value = true;
                warmth.value = 1.16;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.10;
                toneMap.value = 1.0;
                gamma.value = 1.20;
                godRays.value = 0.80;
                ssao.value = 0.45;
                depthFog.value = 0.50;
                waterShimmer.value = 0.60;
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

    public static float getEffectiveMotionBlur() {
        if (!isMotionBlurActive()) return 0.0f;
        return (float) Math.max(0.05, Math.min(0.95, INSTANCE.motionBlurStrength.value));
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

            float sunScreenX = 0.5f;
            float sunScreenY = 0.2f;
            float sunVis = 0.0f;
            float gameTime = (float) (System.currentTimeMillis() % 1000000L) / 1000.0f;
            float farPlane = 256.0f;

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null && mc.gameRenderer != null) {
                float partialTick = 1.0f;
                try {
                    partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                } catch (Throwable ignored) {
                }

                farPlane = (float) mc.options.getEffectiveRenderDistance() * 16.0f;
                if (farPlane < 32.0f) farPlane = 128.0f;

                try {
                    net.minecraft.client.Camera camera = mc.gameRenderer.mainCamera();
                    if (camera != null && camera.isInitialized()) {
                        float rawSunAngle = 0.0f;
                        try {
                            rawSunAngle = camera.attributeProbe().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, partialTick);
                        } catch (Throwable ignored) {
                        }
                        float sunAngle = (float) Math.toRadians(rawSunAngle);

                        float sunWorldY = (float) Math.cos(sunAngle);
                        float sunWorldZ = (float) Math.sin(sunAngle);

                        float yawRad = (float) Math.toRadians(camera.yRot());
                        float pitchRad = (float) Math.toRadians(camera.xRot());

                        float cosY = (float) Math.cos(yawRad);
                        float sinY = (float) Math.sin(yawRad);
                        float x1 = -sinY * sunWorldZ;
                        float z1 = cosY * sunWorldZ;
                        float y1 = sunWorldY;

                        float cosP = (float) Math.cos(pitchRad);
                        float sinP = (float) Math.sin(pitchRad);
                        float viewX = x1;
                        float viewY = cosP * y1 - sinP * z1;
                        float viewZ = sinP * y1 + cosP * z1;

                        if (viewZ < -0.05f) {
                            float fov = (float) Math.tan(Math.toRadians(mc.options.fov().get() * 0.5));
                            float aspect = (float) mc.getWindow().getWidth() / Math.max(1.0f, (float) mc.getWindow().getHeight());
                            float sx = 0.5f + (viewX / (-viewZ * fov * aspect)) * 0.5f;
                            float sy = 0.5f - (viewY / (-viewZ * fov)) * 0.5f;

                            if (sx >= -0.2f && sx <= 1.2f && sy >= -0.2f && sy <= 1.2f) {
                                sunScreenX = Math.max(0.0f, Math.min(1.0f, sx));
                                sunScreenY = Math.max(0.0f, Math.min(1.0f, sy));
                                sunVis = Math.max(0.0f, Math.min(1.0f, -viewZ));
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            for (PostPass pass : ((PostChainAccessor) chain).kollegen$getPasses()) {
                Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).kollegen$getCustomUniforms();
                if (uniforms == null) continue;
                GpuBuffer buf = uniforms.get("ShaderConfig");
                if (buf == null || buf.isClosed()) continue;

                uploadShaderConfig(buf, sat, cont, bright, vig, bloom, exp, warm, vib, tm, gamma, rays, ssao, fog, shimmer, gameTime, sunVis, sunScreenX, sunScreenY, farPlane, 0.0f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void uploadShaderConfig(GpuBuffer buf, float sat, float cont, float bright, float vig, float bloom, float exp, float warm, float vib, float tm, float gamma, float rays, float ssao, float fog, float shimmer, float time, float sunVis, float sunX, float sunY, float farPlane, float pad) {
        int maxLen = (int) Math.min(buf.size(), 80L);
        if (maxLen <= 0) return;
        ByteBuffer bb = ByteBuffer.allocateDirect(maxLen).order(ByteOrder.nativeOrder());
        if (maxLen >= 4) bb.putFloat(sat);
        if (maxLen >= 8) bb.putFloat(cont);
        if (maxLen >= 12) bb.putFloat(bright);
        if (maxLen >= 16) bb.putFloat(vig);
        if (maxLen >= 20) bb.putFloat(bloom);
        if (maxLen >= 24) bb.putFloat(exp);
        if (maxLen >= 28) bb.putFloat(warm);
        if (maxLen >= 32) bb.putFloat(vib);
        if (maxLen >= 36) bb.putFloat(tm);
        if (maxLen >= 40) bb.putFloat(gamma);
        if (maxLen >= 44) bb.putFloat(rays);
        if (maxLen >= 48) bb.putFloat(ssao);
        if (maxLen >= 52) bb.putFloat(fog);
        if (maxLen >= 56) bb.putFloat(shimmer);
        if (maxLen >= 60) bb.putFloat(time);
        if (maxLen >= 64) bb.putFloat(sunVis);
        if (maxLen >= 68) bb.putFloat(sunX);
        if (maxLen >= 72) bb.putFloat(sunY);
        if (maxLen >= 76) bb.putFloat(farPlane);
        if (maxLen >= 80) bb.putFloat(pad);
        bb.flip();

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(buf.slice(0, maxLen), bb);
            encoder.submit();
            return;
        } catch (Throwable ignored) {
        }

        try (GpuBufferSlice.MappedView view = buf.map(0, maxLen, false, true)) {
            ByteBuffer vbb = view.data();
            vbb.order(ByteOrder.nativeOrder());
            if (vbb.capacity() >= 4) vbb.putFloat(0, sat);
            if (vbb.capacity() >= 8) vbb.putFloat(4, cont);
            if (vbb.capacity() >= 12) vbb.putFloat(8, bright);
            if (vbb.capacity() >= 16) vbb.putFloat(12, vig);
            if (vbb.capacity() >= 20) vbb.putFloat(16, bloom);
            if (vbb.capacity() >= 24) vbb.putFloat(20, exp);
            if (vbb.capacity() >= 28) vbb.putFloat(24, warm);
            if (vbb.capacity() >= 32) vbb.putFloat(28, vib);
            if (vbb.capacity() >= 36) vbb.putFloat(32, tm);
            if (vbb.capacity() >= 40) vbb.putFloat(36, gamma);
            if (vbb.capacity() >= 44) vbb.putFloat(40, rays);
            if (vbb.capacity() >= 48) vbb.putFloat(44, ssao);
            if (vbb.capacity() >= 52) vbb.putFloat(48, fog);
            if (vbb.capacity() >= 56) vbb.putFloat(52, shimmer);
            if (vbb.capacity() >= 60) vbb.putFloat(56, time);
            if (vbb.capacity() >= 64) vbb.putFloat(60, sunVis);
            if (vbb.capacity() >= 68) vbb.putFloat(64, sunX);
            if (vbb.capacity() >= 72) vbb.putFloat(68, sunY);
            if (vbb.capacity() >= 76) vbb.putFloat(72, farPlane);
            if (vbb.capacity() >= 80) vbb.putFloat(76, pad);
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

                uploadBlurConfig(buf, blur);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void uploadBlurConfig(GpuBuffer buf, float blur) {
        int maxLen = (int) Math.min(buf.size(), 4L);
        if (maxLen <= 0) return;
        ByteBuffer bb = ByteBuffer.allocateDirect(maxLen).order(ByteOrder.nativeOrder());
        if (maxLen >= 4) bb.putFloat(blur);
        bb.flip();

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(buf.slice(0, maxLen), bb);
            encoder.submit();
            return;
        } catch (Throwable ignored) {
        }

        try (GpuBufferSlice.MappedView view = buf.map(0, maxLen, false, true)) {
            ByteBuffer vbb = view.data();
            vbb.order(ByteOrder.nativeOrder());
            if (vbb.capacity() >= 4) vbb.putFloat(0, blur);
        } catch (Throwable ignored) {
        }
    }
}
