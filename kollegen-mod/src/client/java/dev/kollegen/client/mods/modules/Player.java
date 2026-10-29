package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

public final class Player {

    public static boolean hideOwnNametag = false;

    private Player() {
    }

    public static void register() {
        ModuleManager.register(new OwnNametag());
    }

    private static class OwnNametag extends Module {
        OwnNametag() {
            super("ownnametag", "Eigenen Nametag verbergen", "Blendet das eigene Namensschild in der Third-Person-Perspektive aus.", Category.VISUAL);
        }

        @Override
        public void onEnable() {
            hideOwnNametag = true;
        }

        @Override
        public void onDisable() {
            hideOwnNametag = false;
        }
    }
}
