package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;

/**
 * Verschiebt das Survival-Inventar (samt Ruestung und 2x2-Crafting) um einen
 * konfigurierbaren Versatz. Experimentell: greift ueber Reflection an die
 * finalen Slot-Positionen, damit Klicks mit der Anzeige mitwandern.
 */
public final class InventoryTweaks {

    private InventoryTweaks() {
    }

    public static void register() {
        ModuleManager.register(new Tweaks());
    }

    private static class Tweaks extends Module {
        final SliderSetting offX = new SliderSetting("Versatz X", "Verschiebt Inventar, Ruestung und Crafting horizontal.", 0, -400, 400, 1);
        final SliderSetting offY = new SliderSetting("Versatz Y", "Verschiebt Inventar, Ruestung und Crafting vertikal.", 0, -400, 400, 1);

        Tweaks() {
            super("inventorytweaks", "Inventar-Verschiebung", "Verschiebt das Survival-Inventar per Versatz (experimentell).", Category.VISUAL);
            add(offX);
            add(offY);
        }

        @Override
        public void onTick() {
            InventoryLayout.offX = (int) offX.value;
            InventoryLayout.offY = (int) offY.value;
        }

        @Override
        public void onDisable() {
            InventoryLayout.offX = 0;
            InventoryLayout.offY = 0;
        }
    }
}
