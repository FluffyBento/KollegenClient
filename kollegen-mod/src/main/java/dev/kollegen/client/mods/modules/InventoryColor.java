package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

/**
 * Faerbt den Hintergrund des Survival-Inventars mit einer einstellbaren Farbe.
 * Die eigentliche Einfaebung erfolgt in InventoryScreenMixin.renderBg.
 */
public final class InventoryColor {

    private InventoryColor() {
    }

    public static final ColorSetting color = new ColorSetting(
            "Inventar-Farbe", "Faerbt den Hintergrund des Survival-Inventars.", 0x80_2A_3A_6A);
    public static boolean enabled = false;

    public static void register() {
        Module m = new Module("inventorycolor", "Inventar-Farbe",
                "Faerbt den Inventar-Hintergrund mit einer eigenen Farbe.", Category.MISC) {
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
        ModuleManager.register(m);
    }
}
