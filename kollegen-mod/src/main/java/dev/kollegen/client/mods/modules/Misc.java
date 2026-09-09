package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.ModuleManager;

public final class Misc {

    private Misc() {
    }

    public static void register() {
        InventoryColor.register();
        SpotifyOverlay.register();
        ChatHeads.register();
        Appleskin.register();
        AutoRemoveMods.register();
        
        
    }
}
