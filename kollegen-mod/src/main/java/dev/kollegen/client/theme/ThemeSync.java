package dev.kollegen.client.theme;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the launcher's live theme colors from `~/.kollegen-theme.json` (written
 * by the Kollegen Client launcher whenever its accent color changes). The mod
 * menu re-reads this on every render so its colors always match the launcher,
 * even when the user changes the launcher's accent color at runtime.
 */
public class ThemeSync {
    private static final Gson GSON = new Gson();
    private static final Map<String, String> colors = new HashMap<>();

    private static final String D_ACCENT = "#f5a623";
    private static final String D_ACCENT2 = "#ff7a00";
    private static final String D_BG = "#0d0d12";
    private static final String D_PANEL = "#1a1a24";
    private static final String D_PANEL2 = "#21212e";
    private static final String D_TEXT = "#f3e9d8";
    private static final String D_MUTED = "#b9a98c";
    private static final String D_BORDER = "#34303a";
    private static final String D_DANGER = "#ff5b6e";

    public static void refresh() {
        Path p = Path.of(System.getProperty("user.home"), ".kollegen-theme.json");
        if (!Files.exists(p)) {
            Path dd = FabricLoader.getInstance().getGameDir().resolve("kollegen-theme.json");
            if (Files.exists(dd)) p = dd;
        }
        try {
            if (Files.exists(p)) {
                Map<String, Object> m = GSON.fromJson(Files.readString(p), Map.class);
                if (m != null) {
                    colors.clear();
                    for (Map.Entry<String, Object> e : m.entrySet()) {
                        colors.put(e.getKey(), String.valueOf(e.getValue()));
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        colors.clear();
        colors.put("accent", D_ACCENT);
        colors.put("accent2", D_ACCENT2);
        colors.put("bg", D_BG);
        colors.put("panel", D_PANEL);
        colors.put("panel2", D_PANEL2);
        colors.put("text", D_TEXT);
        colors.put("muted", D_MUTED);
        colors.put("border", D_BORDER);
        colors.put("danger", D_DANGER);
    }

    public static String get(String key, String def) {
        return colors.getOrDefault(key, def);
    }

    /** Parses `#rrggbb` / `#aarrggbb` into a 0xAARRGGBB int. */
    public static int argb(String hex, int def) {
        if (hex == null || hex.isEmpty()) return def;
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 6) h = "ff" + h;
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (Exception ignored) {
        }
        return def;
    }
}
