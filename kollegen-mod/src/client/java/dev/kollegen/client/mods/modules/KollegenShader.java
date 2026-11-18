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
            1.35, 0.0, 3.0, 0.05,
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
            1.25, 0.5, 2.5, 0.05,
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
            1.10, 0.5, 2.0, 0.02,
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
            1.06, 0.5, 2.0, 0.02,
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
            0.35, 0.0, 1.0, 0.05,
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
            1.06, 0.7, 1.4, 0.02,
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

    public KollegenShader() {
        super("kollegenshader", "Kollegen Shader", "Eigen anpassbarer Shader - Farbsättigung, Motion Blur, Kontrast und Bildfilter rein im Kollegen Client ohne Zusatz-Mods.", Category.VISUAL);
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
            case 1 -> {
                saturationEnabled.value = true;
                saturation.value = 1.32;
                vibranceEnabled.value = true;
                vibrance.value = 1.25;
                contrastEnabled.value = true;
                contrast.value = 1.10;
                brightnessEnabled.value = false;
                brightness.value = 1.02;
                exposureEnabled.value = true;
                exposure.value = 1.08;
                bloomEnabled.value = true;
                bloom.value = 0.35;
                warmthEnabled.value = true;
                warmth.value = 1.08;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.15;
            }
            case 2 -> {
                saturationEnabled.value = true;
                saturation.value = 1.38;
                vibranceEnabled.value = true;
                vibrance.value = 1.30;
                contrastEnabled.value = true;
                contrast.value = 1.15;
                brightnessEnabled.value = true;
                brightness.value = 1.04;
                exposureEnabled.value = true;
                exposure.value = 1.12;
                bloomEnabled.value = true;
                bloom.value = 0.40;
                warmthEnabled.value = true;
                warmth.value = 1.06;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.10;
            }
            case 3 -> {
                saturationEnabled.value = true;
                saturation.value = 1.42;
                vibranceEnabled.value = true;
                vibrance.value = 1.35;
                contrastEnabled.value = true;
                contrast.value = 1.18;
                brightnessEnabled.value = true;
                brightness.value = 1.04;
                exposureEnabled.value = true;
                exposure.value = 1.14;
                bloomEnabled.value = true;
                bloom.value = 0.45;
                warmthEnabled.value = true;
                warmth.value = 1.08;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.12;
            }
            case 4 -> {
                saturationEnabled.value = true;
                saturation.value = 1.26;
                vibranceEnabled.value = true;
                vibrance.value = 1.20;
                contrastEnabled.value = true;
                contrast.value = 1.08;
                brightnessEnabled.value = true;
                brightness.value = 1.06;
                exposureEnabled.value = true;
                exposure.value = 1.10;
                bloomEnabled.value = true;
                bloom.value = 0.48;
                warmthEnabled.value = true;
                warmth.value = 1.12;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.20;
            }
            case 5 -> {
                saturationEnabled.value = true;
                saturation.value = 1.45;
                vibranceEnabled.value = true;
                vibrance.value = 1.35;
                contrastEnabled.value = true;
                contrast.value = 1.12;
                brightnessEnabled.value = false;
                brightness.value = 1.05;
                exposureEnabled.value = true;
                exposure.value = 1.05;
                bloomEnabled.value = true;
                bloom.value = 0.25;
                warmthEnabled.value = true;
                warmth.value = 1.02;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.05;
            }
            case 6 -> {
                saturationEnabled.value = true;
                saturation.value = 1.15;
                vibranceEnabled.value = true;
                vibrance.value = 1.10;
                contrastEnabled.value = true;
                contrast.value = 1.22;
                brightnessEnabled.value = true;
                brightness.value = 0.98;
                exposureEnabled.value = true;
                exposure.value = 1.00;
                bloomEnabled.value = true;
                bloom.value = 0.35;
                warmthEnabled.value = true;
                warmth.value = 1.05;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.35;
            }
            case 7 -> {
                saturationEnabled.value = true;
                saturation.value = 1.30;
                vibranceEnabled.value = true;
                vibrance.value = 1.25;
                contrastEnabled.value = true;
                contrast.value = 1.08;
                brightnessEnabled.value = true;
                brightness.value = 1.08;
                exposureEnabled.value = true;
                exposure.value = 1.08;
                bloomEnabled.value = true;
                bloom.value = 0.30;
                warmthEnabled.value = true;
                warmth.value = 1.18;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.18;
            }
        }
    }

    public static boolean isShaderActive() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static boolean isMotionBlurActive() {
        return isShaderActive() && INSTANCE.motionBlurEnabled.value;
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

            for (PostPass pass : ((PostChainAccessor) chain).kollegen$getPasses()) {
                Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).kollegen$getCustomUniforms();
                if (uniforms == null) continue;
                GpuBuffer buf = uniforms.get("ShaderConfig");
                if (buf == null || buf.isClosed()) continue;

                uploadShaderConfig(buf, sat, cont, bright, vig, bloom, exp, warm, vib);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void uploadShaderConfig(GpuBuffer buf, float sat, float cont, float bright, float vig, float bloom, float exp, float warm, float vib) {
        ByteBuffer bb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        bb.putFloat(sat);
        bb.putFloat(cont);
        bb.putFloat(bright);
        bb.putFloat(vig);
        bb.putFloat(bloom);
        bb.putFloat(exp);
        bb.putFloat(warm);
        bb.putFloat(vib);
        bb.flip();

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(buf.slice(), bb);
            encoder.submit();
            return;
        } catch (Throwable ignored) {
        }

        try (GpuBufferSlice.MappedView view = buf.map(false, true)) {
            ByteBuffer vbb = view.data();
            vbb.order(ByteOrder.nativeOrder());
            vbb.putFloat(0, sat);
            vbb.putFloat(4, cont);
            vbb.putFloat(8, bright);
            vbb.putFloat(12, vig);
            vbb.putFloat(16, bloom);
            vbb.putFloat(20, exp);
            vbb.putFloat(24, warm);
            vbb.putFloat(28, vib);
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
        ByteBuffer bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        bb.putFloat(blur);
        bb.flip();

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(buf.slice(), bb);
            encoder.submit();
            return;
        } catch (Throwable ignored) {
        }

        try (GpuBufferSlice.MappedView view = buf.map(false, true)) {
            ByteBuffer vbb = view.data();
            vbb.order(ByteOrder.nativeOrder());
            vbb.putFloat(0, blur);
        } catch (Throwable ignored) {
        }
    }
}
