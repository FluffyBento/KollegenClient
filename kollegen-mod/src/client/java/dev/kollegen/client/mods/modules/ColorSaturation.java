package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;

import java.util.Locale;

public class ColorSaturation extends Module {
    public static volatile ColorSaturation INSTANCE;

    public final SliderSetting saturation = new SliderSetting(
            "Sättigung",
            "Farbintensität (1.0 = normal, höher = lebendiger).",
            1.40, 0.0, 3.0, 0.05,
            v -> Math.round(v * 100.0) + "%",
            v -> syncToShader()
    );

    public final SliderSetting contrast = new SliderSetting(
            "Kontrast",
            "Tiefen und Lichter hervorheben.",
            1.15, 0.5, 2.0, 0.02,
            v -> String.format(Locale.ROOT, "%.2f", v),
            v -> syncToShader()
    );

    public final SliderSetting brightness = new SliderSetting(
            "Helligkeit",
            "Gesamthelligkeit des Bildes.",
            1.05, 0.5, 2.0, 0.05,
            v -> String.format(Locale.ROOT, "%.2f", v),
            v -> syncToShader()
    );

    public final SliderSetting vibrance = new SliderSetting(
            "Vibrance",
            "Dynamische Anhebung schwacher Farben.",
            1.25, 0.5, 2.5, 0.05,
            v -> String.format(Locale.ROOT, "%.2f", v),
            v -> syncToShader()
    );

    public ColorSaturation() {
        super("colorsaturation", "Color Saturation", "Passt Farbsättigung, Kontrast und Helligkeit der Spielwelt an.", Category.VISUAL);
        INSTANCE = this;
        add(saturation);
        add(contrast);
        add(brightness);
        add(vibrance);
    }

    @Override
    public void onEnable() {
        syncToShader();
    }

    @Override
    public void onDisable() {
        if (KollegenShader.INSTANCE != null) {
            KollegenShader.INSTANCE.saturationEnabled.value = false;
            KollegenShader.INSTANCE.contrastEnabled.value = false;
            KollegenShader.INSTANCE.brightnessEnabled.value = false;
            KollegenShader.INSTANCE.vibranceEnabled.value = false;
        }
    }

    @Override
    public void onTick() {
        if (enabled) {
            syncToShader();
        }
    }

    private void syncToShader() {
        if (KollegenShader.INSTANCE == null) return;
        KollegenShader.INSTANCE.saturationEnabled.value = true;
        KollegenShader.INSTANCE.saturation.value = saturation.value;
        KollegenShader.INSTANCE.contrastEnabled.value = true;
        KollegenShader.INSTANCE.contrast.value = contrast.value;
        KollegenShader.INSTANCE.brightnessEnabled.value = true;
        KollegenShader.INSTANCE.brightness.value = brightness.value;
        KollegenShader.INSTANCE.vibranceEnabled.value = true;
        KollegenShader.INSTANCE.vibrance.value = vibrance.value;
        if (!KollegenShader.INSTANCE.enabled) {
            KollegenShader.INSTANCE.enabled = true;
            KollegenShader.INSTANCE.onEnable();
        }
    }
}
