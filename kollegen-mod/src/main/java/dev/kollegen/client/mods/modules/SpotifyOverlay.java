package dev.kollegen.client.mods.modules;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.BundleManager;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

/**
 * Schaltet das gebündelte Spotify-Overlay ein/aus. Der Toggle schreibt nur die
 * Wunsch-Datei ({@link BundleManager}); der Launcher deployed/entfernt das Jar
 * beim nächsten Spielstart – daher wirkt der Umschalt erst nach einem Neustart.
 * Das Overlay selbst zeigt den aktuell laufenden Song als HUD an und konfiguriert
 * sich über seinen eigenen ModMenu-Eintrag (unter "Kollegen Client").
 */
public final class SpotifyOverlay {

    private SpotifyOverlay() {
    }

    public static void register() {
        Module m = new Module("spotifyoverlay", "Spotify Overlay",
                "Zeigt den aktuell spielenden Spotify-Song als HUD-Overlay an (Konfiguration über ModMenu unter Kollegen Client). Wirkt nach Neustart.",
                Category.MISC) {
            {
                BundleManager.ensureLoaded();
                enabled = BundleManager.spotify;
            }

            @Override
            public void load(JsonObject o) {
                super.load(o);
                // Die Bundle-Datei bleibt die Quelle der Wahrheit – nicht der
                // Modul-Config-Cache (der Launcher schreibt sie ebenfalls).
                this.enabled = BundleManager.spotify;
                if (enabled) onEnable();
            }

            @Override
            public void onEnable() {
                BundleManager.spotify = true;
                BundleManager.save();
                risk = "Wirkt nach dem nächsten Spielstart.";
            }

            @Override
            public void onDisable() {
                BundleManager.spotify = false;
                BundleManager.save();
                risk = "Wirkt nach dem nächsten Spielstart.";
            }
        };
        ModuleManager.register(m);
    }
}
