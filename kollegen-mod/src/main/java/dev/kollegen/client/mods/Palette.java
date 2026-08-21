package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Farbpalette für das Mod-Menü + HUD. Standardmäßig im Kollegen-Launcher-Look
 * (dunkles Anthrazit + orangener Akzent). Kann vom Launcher überschrieben
 * werden, indem dieser eine Datei {@code kollegen-theme.json} in das
 * Config-Verzeichnis schreibt (gleiche Ordnerstruktur wie die Module-Config).
 */
public final class Palette {
    private Palette() {
    }

    // Standard: Launcher-Look (dunkel + orangener Markenakzent #f5a623).
    public static int BG = 0xFF121212;
    public static int PANEL = 0xFF1e1e1e;
    public static int PANEL2 = 0xFF262626;
    public static int BORDER = 0xFF3a3a3a;
    public static int ACCENT = 0xFFf5a623;
    public static int ACCENT2 = 0xFFffc04d;
    public static int ACCENT_DARK = 0xFFa86a00;
    public static int TEXT = 0xFFf8fafc;
    public static int MUTED = 0xFF94a3b8;
    public static int DANGER = 0xFFef4444;
    public static int GREEN = 0xFF3ec46d;
    public static int BLUE = 0xFF4aa3ff;

    /** Setzt den Alpha-Kanal einer 0xAARRGGBB-Farbe. */
    public static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Lädt eine optionale Theme-Override-Datei (vom Launcher geschrieben). */
    public static void loadTheme() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("kollegen-theme.json");
            if (!Files.exists(dir)) return;
            JsonObject o = JsonParser.parseString(Files.readString(dir)).getAsJsonObject();
            if (o.has("bg")) BG = parse(o.get("bg").getAsString(), BG);
            if (o.has("panel")) PANEL = parse(o.get("panel").getAsString(), PANEL);
            if (o.has("panel2")) PANEL2 = parse(o.get("panel2").getAsString(), PANEL2);
            if (o.has("border")) BORDER = parse(o.get("border").getAsString(), BORDER);
            if (o.has("accent")) ACCENT = parse(o.get("accent").getAsString(), ACCENT);
            if (o.has("accent2")) ACCENT2 = parse(o.get("accent2").getAsString(), ACCENT2);
            if (o.has("text")) TEXT = parse(o.get("text").getAsString(), TEXT);
            if (o.has("muted")) MUTED = parse(o.get("muted").getAsString(), MUTED);
            if (o.has("danger")) DANGER = parse(o.get("danger").getAsString(), DANGER);
            if (o.has("green")) GREEN = parse(o.get("green").getAsString(), GREEN);
            if (o.has("blue")) BLUE = parse(o.get("blue").getAsString(), BLUE);
        } catch (Throwable ignored) {
        }
    }

    /** Parst "#rrggbb" / "rrggbb" (0xRRGGBB) oder "aarrggbb" (0xAARRGGBB). */
    private static int parse(String s, int fallback) {
        try {
            String h = s.startsWith("#") ? s.substring(1) : s;
            if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
            if (h.length() == 6) h = "FF" + h;
            return (int) Long.parseLong(h, 16);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
