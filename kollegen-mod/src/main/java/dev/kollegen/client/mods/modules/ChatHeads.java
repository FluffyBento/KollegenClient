package dev.kollegen.client.mods.modules;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.BundleManager;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

/**
 * Schaltet die gebündelte Chat-Heads-Mod ein/aus (Spielerköpfe neben Chat-
 * Nachrichten). Wie Spotify Overlay: Toggle schreibt nur die Wunsch-Datei,
 * der Launcher setzt sie beim nächsten Spielstart um.
 */
public final class ChatHeads {

    private ChatHeads() {
    }

    public static void register() {
        Module m = new Module("chatheads", "Chat Heads",
                "Zeigt den Kopf des Sprechers neben jeder Chat-Nachricht. Wirkt nach Neustart.",
                Category.CHAT) {
            {
                BundleManager.ensureLoaded();
                enabled = BundleManager.chatheads;
            }

            @Override
            public void load(JsonObject o) {
                super.load(o);
                this.enabled = BundleManager.chatheads;
                if (enabled) onEnable();
            }

            @Override
            public void onEnable() {
                BundleManager.chatheads = true;
                BundleManager.save();
                risk = "Wirkt nach dem nächsten Spielstart.";
            }

            @Override
            public void onDisable() {
                BundleManager.chatheads = false;
                BundleManager.save();
                risk = "Wirkt nach dem nächsten Spielstart.";
            }
        };
        ModuleManager.register(m);
    }
}
