package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;

public class FovModifier extends Module {
    public static FovModifier INSTANCE;

    public final BooleanSetting sprintFov = new BooleanSetting("Sprint-FOV", "Verändert das Sichtfeld beim Sprinten.", true);
    public final SliderSetting sprintModifier = new SliderSetting("Sprint-Stärke", "Intensität des Sprint-FOV-Effekts.", 1.0, 0.0, 2.0, 0.05, v -> Math.round(v * 100.0) + "%", null);

    public final BooleanSetting speedFov = new BooleanSetting("Geschwindigkeits-FOV", "Verändert das Sichtfeld bei Schnelligkeits-Effekten.", true);
    public final SliderSetting speedModifier = new SliderSetting("Geschwindigkeit-Stärke", "Intensität des Schnelligkeits-Effekts.", 1.0, 0.0, 2.0, 0.05, v -> Math.round(v * 100.0) + "%", null);

    public final BooleanSetting bowFov = new BooleanSetting("Bogen-Zoom", "Zoomt heran beim Spannen des Bogens.", true);
    public final SliderSetting bowModifier = new SliderSetting("Bogen-Zoom-Stärke", "Intensität des Bogen-Zoom-Effekts.", 1.0, 0.0, 2.0, 0.05, v -> Math.round(v * 100.0) + "%", null);

    public final BooleanSetting flyFov = new BooleanSetting("Flug-FOV", "Verändert das Sichtfeld beim Fliegen.", true);

    public FovModifier() {
        super("fovmodifier", "FOV-Effekte anpassen", "Sprint-, Geschwindigkeits-, Bogen- und Flug-FOV einzeln regulieren.", Category.VISUAL);
        INSTANCE = this;
        add(sprintFov);
        add(sprintModifier);
        add(speedFov);
        add(speedModifier);
        add(bowFov);
        add(bowModifier);
        add(flyFov);
    }

    public static float modifyFov(float originalModifier, boolean isSprinting, boolean isUsingBow, boolean isFlying) {
        if (INSTANCE == null || !INSTANCE.enabled) return originalModifier;

        float mod = 1.0f;
        if (originalModifier > 1.0f) {
            float delta = originalModifier - 1.0f;
            if (isSprinting && !INSTANCE.sprintFov.value) {
                delta = 0.0f;
            } else if (isSprinting) {
                delta *= (float) INSTANCE.sprintModifier.value;
            }
            if (!INSTANCE.speedFov.value) {
                delta = 0.0f;
            } else {
                delta *= (float) INSTANCE.speedModifier.value;
            }
            mod += delta;
        } else if (originalModifier < 1.0f) {
            if (isUsingBow) {
                if (!INSTANCE.bowFov.value) {
                    mod = 1.0f;
                } else {
                    float zoomDelta = 1.0f - originalModifier;
                    mod = 1.0f - (zoomDelta * (float) INSTANCE.bowModifier.value);
                }
            } else {
                mod = originalModifier;
            }
        }
        if (isFlying && !INSTANCE.flyFov.value && mod > 1.05f) {
            mod /= 1.1f;
        }
        return Math.max(0.1f, Math.min(2.0f, mod));
    }
}
