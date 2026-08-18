package dev.kollegen.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class KollegenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kollegen.json");

    /** Whether the in-game Minecraft title logo is replaced by Logo.png. */
    public boolean replaceLogo = true;

    /** Farb-Sättigung des Spiels (0.0 = grau, 1.0 = normal, 2.0 = kräftig). */
    public float colorSaturation = 1.0f;

    /** Soll eine bestimmte Farbe (Hex-Code) besonders hervorgehoben werden? */
    public boolean colorHighlight = false;

    /** Der hervorzuhebende Farbton als Hex-Wert, z. B. "#FF5722". */
    public String highlightColor = "#FF5722";

    /** Stärke der Farbhervorhebung (0.0–1.0). */
    public float highlightAmount = 0.6f;

    /** Presence: sollen andere Kollegen-Client-Nutzer im Spiel markiert werden? */
    public boolean presenceEnabled = false;

    /**
     * Basis-URL des externen Presence-Backends, z. B.
     * "https://presence.kollegen.dev". Leer = Feature aus.
     * Kann auch über die Umgebungsvariable KOLLEGEN_PRESENCE_BACKEND
     * gesetzt werden (hat Vorrang vor dieser Einstellung).
     */
    public String presenceBackend = "";

    /**
     * Optionales Bearer-Token, das im Header "Authorization: Bearer …"
     * an das Backend geschickt wird. Leer = kein Token.
     */
    public String presenceToken = "";

    /** Kurzschreibweise: irgendein Farb-FX aktiv? (fürs Rendering) */
    public boolean colorFxActive() {
        return colorSaturation != 1.0f || colorHighlight;
    }

    public static KollegenConfig load() {
        KollegenConfig cfg = new KollegenConfig();
        try {
            if (Files.exists(PATH)) {
                KollegenConfig loaded = GSON.fromJson(Files.readString(PATH), KollegenConfig.class);
                if (loaded != null) cfg = loaded;
            }
        } catch (Exception ignored) {
            // fall back to defaults
        }
        // Sanitize loaded hex colors.
        if (cfg.highlightColor == null || !cfg.highlightColor.matches("^#[0-9a-fA-F]{6}$")) {
            cfg.highlightColor = "#FF5722";
        }
        cfg.colorSaturation = Math.max(0.0f, Math.min(2.0f, cfg.colorSaturation));
        cfg.highlightAmount = Math.max(0.0f, Math.min(1.0f, cfg.highlightAmount));
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
