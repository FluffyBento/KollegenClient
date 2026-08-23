package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Beta: räumt eigenständige Kopien gebündelter Mods (sowie veraltete/duplizierte
 * fabric-language-kotlin-Jars) im mods/-Ordner auf. Standardmäßig AUS – der
 * Kollegen-Client verändert die Mods des Nutzers nicht automatisch. Wer die
 * Aufräum-Automatik will, aktiviert dieses Modul; die Begleit-Mod schreibt die
 * Flag {@code mods/.kollegen-autoremove}, und der Launcher führt die
 * Bereinigung beim nächsten Start durch.
 */
public final class AutoRemoveMods extends Module {

    private static final Path FLAG = FabricLoader.getInstance()
            .getGameDir().resolve("mods").resolve(".kollegen-autoremove");

    private AutoRemoveMods() {
        super("autoremove", "Auto-Remove Mods (Beta)",
                "Räumt eigenständige Kopien gebündelter Mods und doppelte FLK-Jars im mods/-Ordner auf. Beta: standardmäßig aus, wir verändern deine Mods nicht automatisch. Wirkt beim nächsten Client-Start.",
                Category.MISC);
        this.risk = "Beta – entfernt ggf. eigenständige Mod-Dateien im mods/-Ordner";
    }

    public static void register() {
        ModuleManager.register(new AutoRemoveMods());
    }

    @Override
    public void onEnable() {
        try {
            Files.writeString(FLAG, "1");
        } catch (IOException e) {
            KollegenMod.LOGGER.warn("Kollegen: Auto-Remove-Flag konnte nicht gesetzt werden: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        try {
            Files.deleteIfExists(FLAG);
        } catch (IOException ignored) {
        }
    }
}
