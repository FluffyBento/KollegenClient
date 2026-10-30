package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.resources.Identifier;

public class KollegenShader extends Module {
    public static KollegenShader INSTANCE;

    public static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath("kollegen", "kollegen_shader");
    public static final Identifier BLUR_ID = Identifier.fromNamespaceAndPath("kollegen", "motion_blur");

    public final ModeSetting preset = new ModeSetting(
            "Shader-Profil",
            "Vorgefertigte Farbanpassungen oder eigener Shader.",
            new String[]{"Benutzerdefiniert", "Vibrant (Lebendig)", "Cinematic", "Klar & Kontrast", "Warm"},
            0
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
            0.4, 0.1, 1.0, 0.05,
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
            v -> Math.round(v * 100.0) + "%", null
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
            v -> Math.round(v * 100.0) + "%", null
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
}
