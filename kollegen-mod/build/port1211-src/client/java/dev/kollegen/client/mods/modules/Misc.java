package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

public final class Misc {

    private Misc() {
    }

    public static void register() {
        InventoryColor.register();
        ChatHeads.register();
        Appleskin.register();
        AutoRemoveMods.register();
        ModuleManager.register(new ControllerSupport());
    }

    private static class ControllerSupport extends Module {
        ControllerSupport() {
            super("controllersupport", "Controller-Support", "Aktiviert Gamepad-Steuerung und Menue-Navigation.", Category.MISC);
            this.enabled = dev.kollegen.client.input.ControllerMode.isActive();
        }

        @Override
        public void onEnable() {
            dev.kollegen.client.input.ControllerMode.setActive(true);
        }

        @Override
        public void onDisable() {
            dev.kollegen.client.input.ControllerMode.setActive(false);
        }
    }
}

