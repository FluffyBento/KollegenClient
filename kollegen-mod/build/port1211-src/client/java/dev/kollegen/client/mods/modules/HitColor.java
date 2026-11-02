package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.util.ARGB;

public class HitColor extends Module {
    public static HitColor INSTANCE;

    public final SliderSetting preset = new SliderSetting(
            "Hit-Farbe",
            "Farbe beim Treffen / Schaden von Entities.",
            0.0, 0.0, 6.0, 1.0,
            v -> switch ((int) Math.round(v)) {
                case 1 -> "Neon Pink / Magenta";
                case 2 -> "Deep Purple / Lila";
                case 3 -> "Cyan / Aqua";
                case 4 -> "Gold / Gelb";
                case 5 -> "Reinweiß";
                case 6 -> "Smaragdgrün";
                default -> "Klassisch Rot";
            },
            v -> HitColorManager.update()
    );

    public final SliderSetting opacity = new SliderSetting(
            "Deckkraft (Intensität)",
            "Stärke der Schadens-Einfärbung.",
            0.70, 0.20, 1.00, 0.05,
            v -> Math.round(v * 100.0) + "%",
            v -> HitColorManager.update()
    );

    public HitColor() {
        super("hitcolor", "Hit Color", "Passe die Farbe an, in der Entities bei Treffern aufleuchten.", Category.VISUAL);
        INSTANCE = this;
        add(preset);
        add(opacity);
    }

    @Override
    public void onEnable() {
        HitColorManager.update();
    }

    @Override
    public void onDisable() {
        HitColorManager.update();
    }

    public static int getEffectiveColor() {
        if (INSTANCE == null || !INSTANCE.enabled) {
            return ARGB.color(179, 255, 0, 0);
        }

        int p = (int) Math.round(INSTANCE.preset.value);
        int rgb = switch (p) {
            case 1 -> 0xFF007F;
            case 2 -> 0x8A2BE2;
            case 3 -> 0x00E5FF;
            case 4 -> 0xFFD700;
            case 5 -> 0xFFFFFF;
            case 6 -> 0x00FF66;
            default -> 0xFF0000;
        };

        float a = (float) INSTANCE.opacity.value;
        int alpha = Math.max(20, Math.min(255, Math.round(a * 255.0f)));
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return ARGB.color(alpha, r, g, b);
    }
}
