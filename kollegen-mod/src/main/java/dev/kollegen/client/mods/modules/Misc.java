package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.ModuleManager;

public final class Misc {

    private Misc() {
    }

    public static void register() {
        // Discord Rich Presence läuft ab sofort automatisch (siehe KollegenMod.onInitializeClient),
        // daher ist hier kein eigenes Modul/Setting mehr nötig.
    }
}
