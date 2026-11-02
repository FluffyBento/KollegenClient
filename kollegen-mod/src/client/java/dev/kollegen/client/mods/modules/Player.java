package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

public final class Player {

    public static boolean hideOwnNametag = false;
    public static NametagModule NAMETAG;

    private Player() {
    }

    public static void register() {
        NAMETAG = new NametagModule();
        ModuleManager.register(NAMETAG);
    }

    public static class NametagModule extends Module {
        public final ModeSetting hideNametag = new ModeSetting("Nametag", "Welche Namensschilder ausgeblendet werden sollen.",
                new String[]{"Aus", "Nur eigener", "Nur andere", "Alle"}, 1);
        public final ModeSetting hideTitle = new ModeSetting("Titel", "Welche Kollegen-Titel ausgeblendet werden sollen.",
                new String[]{"Aus", "Nur eigener", "Nur andere", "Alle"}, 0);
        public final ModeSetting hideLevel = new ModeSetting("Level", "Welche Kollegen-Level ausgeblendet werden sollen.",
                new String[]{"Aus", "Nur eigener", "Nur andere", "Alle"}, 0);

        public NametagModule() {
            super("ownnametag", "Nametag verbergen", "Nametags, Kollegen-Titel und Level flexibel für sich selbst oder andere ausblenden.", Category.VISUAL);
            add(hideNametag);
            add(hideTitle);
            add(hideLevel);
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

    private static boolean shouldHide(int modeIndex, boolean isSelf) {
        return switch (modeIndex) {
            case 1 -> isSelf;   // Nur eigener
            case 2 -> !isSelf;  // Nur andere
            case 3 -> true;     // Alle
            default -> false;   // Aus (0)
        };
    }

    public static boolean shouldHideNametag(boolean isSelf) {
        if (NAMETAG == null || !NAMETAG.enabled) return false;
        return shouldHide(NAMETAG.hideNametag.index, isSelf);
    }

    public static boolean shouldHideTitle(boolean isSelf) {
        if (NAMETAG == null || !NAMETAG.enabled) return false;
        return shouldHide(NAMETAG.hideTitle.index, isSelf);
    }

    public static boolean shouldHideLevel(boolean isSelf) {
        if (NAMETAG == null || !NAMETAG.enabled) return false;
        return shouldHide(NAMETAG.hideLevel.index, isSelf);
    }
}
