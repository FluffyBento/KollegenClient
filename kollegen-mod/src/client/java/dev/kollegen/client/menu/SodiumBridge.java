package dev.kollegen.client.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

public final class SodiumBridge {

    private SodiumBridge() {
    }

    public static boolean isSodiumLoaded() {
        return FabricLoader.getInstance().isModLoaded("sodium");
    }

    public static Screen openSodiumScreen(Screen parent) {
        if (!isSodiumLoaded()) return null;
        try {
            Class<?> cls = Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen");
            Object screen = cls.getMethod("createScreen", Screen.class).invoke(null, parent);
            if (screen instanceof Screen s) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}