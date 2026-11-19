package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.Minecraft;

public class UiScale extends Module {
    public static UiScale INSTANCE;

    public final ModeSetting containerScale = new ModeSetting(
            "Container-Skalierung",
            "Größe von Inventaren, Kisten und Werkbänken unabhängig vom HUD.",
            new String[]{"Auto / Standard", "Klein (1x)", "Normal (2x)", "Groß (3x)", "Sehr groß (4x)", "Riesig (5x)"},
            3,
            v -> updateScale()
    );

    public final ModeSetting hudScale = new ModeSetting(
            "HUD-Skalierung",
            "Größe von Hotbar, Chat und Spielanzeigen im Spiel.",
            new String[]{"Standard (Optionen)", "Klein (1x)", "Normal (2x)", "Groß (3x)", "Sehr groß (4x)"},
            0,
            v -> updateScale()
    );

    public final BooleanSetting quickButton = new BooleanSetting(
            "Schnellwahl-Button",
            "Zeigt einen Button in Inventaren zum schnellen Ändern der Skalierung.",
            true
    );

    public UiScale() {
        super("uiscale", "UI Scale", "Separate GUI-Skalierung für Inventare und Container unabhängig vom HUD.", Category.VISUAL);
        INSTANCE = this;
        add(containerScale);
        add(hudScale);
        add(quickButton);
    }

    @Override
    public void onEnable() {
        updateScale();
    }

    @Override
    public void onDisable() {
        updateScale();
    }

    private static void updateScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            mc.resizeGui();
        }
    }

    public static boolean isCustomScaleActive() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static int getTargetScale(boolean isContainer) {
        if (!isCustomScaleActive()) return 0;
        if (isContainer) {
            return INSTANCE.containerScale.index;
        } else {
            return INSTANCE.hudScale.index;
        }
    }

    public static void cycleContainerScale() {
        if (INSTANCE == null) return;
        INSTANCE.containerScale.index = (INSTANCE.containerScale.index + 1) % INSTANCE.containerScale.options.length;
        if (INSTANCE.containerScale.index == 0) {
            INSTANCE.containerScale.index = 1;
        }
        updateScale();
        ModuleManager.save();
    }

    public static boolean isQuickButtonEnabled() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.quickButton.value;
    }

    public static String getContainerScaleLabel() {
        if (INSTANCE == null) return "UI: 3x";
        int idx = INSTANCE.containerScale.index;
        if (idx == 0) return "UI: Auto";
        return "UI: " + idx + "x";
    }
}
