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
            new String[]{"Benutzerdefiniert", "Vibrant (Lebendig)", "Cinematic", "Klar & Kontrast", "Warm"},
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

    public final BooleanSetting contrastEnabled = new BooleanSetting(
            "Kontrast",
            "Verstärkt Tiefen und Lichter.",
            true
    );

    public final SliderSetting contrast = new SliderSetting(
            "Kontrast-Stärke",
            "Intensität des Kontrasts.",
            1.08, 0.5, 2.0, 0.02,
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

    public final BooleanSetting vignetteEnabled = new BooleanSetting(
            "Kino-Vignette",
            "Weiche dunkle Ecken für mehr Tiefenwirkung.",
            false
    );

    public final SliderSetting vignetteStrength = new SliderSetting(
            "Vignetten-Stärke",
            "Intensität der Randeindunklung.",
            0.25, 0.05, 1.0, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public KollegenShader() {
        super("kollegenshader", "Kollegen Shader", "Eigen anpassbarer Shader - Farbsättigung, Motion Blur, Kontrast und Bildfilter rein im Kollegen Client ohne Zusatz-Mods.", Category.VISUAL);
        INSTANCE = this;
        add(preset);
        add(saturationEnabled);
        add(saturation);
        add(motionBlurEnabled);
        add(motionBlurStrength);
        add(contrastEnabled);
        add(contrast);
        add(brightnessEnabled);
        add(brightness);
        add(vignetteEnabled);
        add(vignetteStrength);
    }

    private void applyPreset(int index) {
        switch (index) {
            case 1 -> { // Vibrant
                saturationEnabled.value = true;
                saturation.value = 1.45;
                contrastEnabled.value = true;
                contrast.value = 1.08;
                brightnessEnabled.value = false;
                brightness.value = 1.05;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.20;
            }
            case 2 -> { // Cinematic
                saturationEnabled.value = true;
                saturation.value = 1.15;
                contrastEnabled.value = true;
                contrast.value = 1.18;
                brightnessEnabled.value = true;
                brightness.value = 0.98;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.35;
            }
            case 3 -> { // Klar & Kontrast
                saturationEnabled.value = true;
                saturation.value = 1.10;
                contrastEnabled.value = true;
                contrast.value = 1.25;
                brightnessEnabled.value = true;
                brightness.value = 1.02;
                vignetteEnabled.value = false;
                vignetteStrength.value = 0.15;
            }
            case 4 -> { // Warm
                saturationEnabled.value = true;
                saturation.value = 1.30;
                contrastEnabled.value = true;
                contrast.value = 1.05;
                brightnessEnabled.value = true;
                brightness.value = 1.10;
                vignetteEnabled.value = true;
                vignetteStrength.value = 0.20;
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

    public static float getEffectiveContrast() {
        if (!isShaderActive() || !INSTANCE.contrastEnabled.value) return 1.0f;
        return (float) INSTANCE.contrast.value;
    }

    public static float getEffectiveBrightness() {
        if (!isShaderActive() || !INSTANCE.brightnessEnabled.value) return 1.0f;
        return (float) INSTANCE.brightness.value;
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

            for (PostPass pass : ((PostChainAccessor) chain).kollegen$getPasses()) {
                Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).kollegen$getCustomUniforms();
                if (uniforms == null) continue;
                GpuBuffer buf = uniforms.get("ShaderConfig");
                if (buf == null || buf.isClosed()) continue;

                uploadShaderConfig(buf, sat, cont, bright, vig);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void uploadShaderConfig(GpuBuffer buf, float sat, float cont, float bright, float vig) {
        ByteBuffer bb = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        bb.putFloat(sat);
        bb.putFloat(cont);
        bb.putFloat(bright);
        bb.putFloat(vig);
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
