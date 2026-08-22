package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;

/**
 * Faerbt den Hintergrund ALLER Container-Bildschirme (Inventar, Kiste, Werkbank
 * ...) mit einer einstellbaren Farbe und Deckkraft. Die eigentliche Einfaebung
 * erfolgt in ContainerScreenMixin.
 */
public final class InventoryColor {

    private InventoryColor() {
    }

    public static final ColorSetting color = new ColorSetting(
            "Inventar-Farbe", "Faerbt den Hintergrund der Container.", 0x80_2A_3A_6A);
    public static final SliderSetting opacity = new SliderSetting(
            "Deckkraft", "Wie stark die Farbe deckt (5–100 %).", 50.0, 5.0, 100.0, 1.0);
    public static boolean enabled = false;

    public static void register() {
        Module m = new Module("inventorycolor", "Inventar-Farbe",
                "Faerbt den Hintergrund aller Container (Inventar, Kisten, Werkbank) mit einer eigenen Farbe.", Category.MISC) {
            @Override
            public void onEnable() {
                enabled = true;
            }

            @Override
            public void onDisable() {
                enabled = false;
            }
        };
        m.add(color);
        m.add(opacity);
        ModuleManager.register(m);
    }
}
