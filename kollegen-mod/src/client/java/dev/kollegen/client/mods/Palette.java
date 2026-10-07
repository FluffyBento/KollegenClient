package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Palette {
    private Palette() {
    }

    public static int BG = 0xFF0d0d12;
    public static int PANEL = 0xFF1a1a24;
    public static int PANEL2 = 0xFF21212e;
    public static int BORDER = 0xFF34303a;
    public static int ACCENT = 0xFFf5a623;
    public static int ACCENT2 = 0xFFff7a00;
    public static int TEXT = 0xFFf3e9d8;
    public static int MUTED = 0xFFb9a98c;
    public static int DANGER = 0xFFff5b6e;
    public static int GREEN = 0xFF3ec46d;
    public static int BLUE = 0xFF4aa3ff;

    public static int SHADOW = 0x66000000;
    public static int HOVER_OVERLAY = 0x20ffffff;
    public static int PRESSED_OVERLAY = 0x40000000;
    public static int FOCUS_BORDER = 0x80f5a623;
    public static int SCROLL_TRACK = 0x40000000;
    public static int SCROLL_THUMB = 0x80ffffff;
    public static int SCROLL_THUMB_HOVER = 0xA0ffffff;
    public static int DISABLED_OVERLAY = 0x60000000;

    private static String currentTheme = "default";
    private static final Map<String, Theme> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put("default", new Theme("Default", "Default warm theme",
                0xFF0d0d12, 0xFF1a1a24, 0xFF21212e, 0xFF34303a,
                0xFFf5a623, 0xFFff7a00, 0xFFf3e9d8, 0xFFb9a98c,
                0xFFff5b6e, 0xFF3ec46d, 0xFF4aa3ff));

        THEMES.put("dark", new Theme("Dark", "Pure dark theme",
                0xFF0a0a0a, 0xFF141414, 0xFF1e1e1e, 0xFF2d2d2d,
                0xFFbb86fc, 0xFF9d68d0, 0xFFe8e8e8, 0xFFb0b0b0,
                0xFFcf6679, 0xFF03dac6, 0xFF64b5f6));

        THEMES.put("nord", new Theme("Nord", "Arctic, north-bluish theme",
                0xFF2e3440, 0xFF3b4252, 0xFF434c5e, 0xFF4c566a,
                0xFF88c0d0, 0xFF81a1c1, 0xFFd8dee9, 0xFFb8c0d0,
                0xFFbf616a, 0xFFa3be8c, 0xFF5e81ac));

        THEMES.put("dracula", new Theme("Dracula", "Dark theme with purple accents",
                0xFF282a36, 0xFF44475a, 0xFF44475a, 0xFF6272a4,
                0xFFbd93f9, 0xFFff79c6, 0xFFf8f8f2, 0xFFbdbdbd,
                0xFFff5555, 0xFF50fa7b, 0xFF8be9fd));

        THEMES.put("gruvbox", new Theme("Gruvbox", "Retro groove colors",
                0xFF282828, 0xFF3c3836, 0xFF504945, 0xFF665c54,
                0xFFd79921, 0xFFfe8019, 0xFFebdbb2, 0xFFa89984,
                0xFFfb4934, 0xFFb8bb26, 0xFF83a598));

        THEMES.put("tokyo-night", new Theme("Tokyo Night", "Dark Tokyo night theme",
                0xFF1a1b26, 0xFF24283b, 0xFF2f344a, 0xFF3b4261,
                0xFF7aa2f7, 0xFFbb9af7, 0xFFc0caf5, 0xFFa9b1d6,
                0xFFf7768e, 0xFF9ece6a, 0xFF2ac3de));

        THEMES.put("catppuccin-mocha", new Theme("Catppuccin Mocha", "Soothing pastel theme",
                0xFF1e1e2e, 0xFF313244, 0xFF45475a, 0xFF585b70,
                0xFFf5c2e7, 0xFFf9e2af, 0xFFcdd6f4, 0xFFbac2de,
                0xFFf38ba8, 0xFFa6e3a1, 0xFF89b4fa));

        THEMES.put("high-contrast", new Theme("High Contrast", "Accessibility-focused theme",
                0xFF000000, 0xFF1a1a1a, 0xFF333333, 0xFFffffff,
                0xFFffff00, 0xFFffaa00, 0xFFffffff, 0xFFcccccc,
                0xFFff0000, 0xFF00ff00, 0xFF00ffff));

        THEMES.put("minecraft", new Theme("Minecraft", "Vanilla Minecraft style",
                0xFF101010, 0xFF202020, 0xFF303030, 0xFF555555,
                0xFF55ffff, 0xFF55ff55, 0xFFffffff, 0xFFaaaaaa,
                0xFFff5555, 0xFF55ff55, 0xFF5555ff));
    }

    public static class Theme {
        public final String id;
        public final String name;
        public final String description;
        public final int bg, panel, panel2, border;
        public final int accent, accent2, text, muted;
        public final int danger, green, blue;

        Theme(String name, String description,
              int bg, int panel, int panel2, int border,
              int accent, int accent2, int text, int muted,
              int danger, int green, int blue) {
            this.id = name.toLowerCase().replace(" ", "-");
            this.name = name;
            this.description = description;
            this.bg = bg;
            this.panel = panel;
            this.panel2 = panel2;
            this.border = border;
            this.accent = accent;
            this.accent2 = accent2;
            this.text = text;
            this.muted = muted;
            this.danger = danger;
            this.green = green;
            this.blue = blue;
        }

        public void apply() {
            BG = bg;
            PANEL = panel;
            PANEL2 = panel2;
            BORDER = border;
            ACCENT = accent;
            ACCENT2 = accent2;
            TEXT = text;
            MUTED = muted;
            DANGER = danger;
            GREEN = green;
            BLUE = blue;

            SHADOW = tint(BG, 0x80);
            HOVER_OVERLAY = tint(ACCENT, 0x20);
            PRESSED_OVERLAY = tint(BG, 0x60);
            FOCUS_BORDER = tint(ACCENT, 0x80);
            SCROLL_TRACK = tint(BG, 0x40);
            SCROLL_THUMB = tint(ACCENT, 0x80);
            SCROLL_THUMB_HOVER = tint(ACCENT, 0xA0);
            DISABLED_OVERLAY = tint(BG, 0x60);
        }
    }

    public static Map<String, Theme> getThemes() {
        return THEMES;
    }

    public static String getCurrentTheme() {
        return currentTheme;
    }

    public static void setTheme(String id) {
        Theme t = THEMES.get(id);
        if (t != null) {
            currentTheme = id;
            t.apply();
            saveTheme();
        }
    }

    public static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | bl;
    }

    public static void loadTheme() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("kollegen-theme.json");
            if (!Files.exists(dir)) return;
            JsonObject o = JsonParser.parseString(Files.readString(dir)).getAsJsonObject();
            if (o.has("theme")) {
                String themeId = o.get("theme").getAsString();
                Theme t = THEMES.get(themeId);
                if (t != null) {
                    currentTheme = themeId;
                    t.apply();
                }
            }
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

    public static void saveTheme() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("kollegen-theme.json");
            JsonObject o = new JsonObject();
            o.addProperty("theme", currentTheme);
            o.addProperty("bg", String.format("#%06X", BG & 0xFFFFFF));
            o.addProperty("panel", String.format("#%06X", PANEL & 0xFFFFFF));
            o.addProperty("panel2", String.format("#%06X", PANEL2 & 0xFFFFFF));
            o.addProperty("border", String.format("#%06X", BORDER & 0xFFFFFF));
            o.addProperty("accent", String.format("#%06X", ACCENT & 0xFFFFFF));
            o.addProperty("accent2", String.format("#%06X", ACCENT2 & 0xFFFFFF));
            o.addProperty("text", String.format("#%06X", TEXT & 0xFFFFFF));
            o.addProperty("muted", String.format("#%06X", MUTED & 0xFFFFFF));
            o.addProperty("danger", String.format("#%06X", DANGER & 0xFFFFFF));
            o.addProperty("green", String.format("#%06X", GREEN & 0xFFFFFF));
            o.addProperty("blue", String.format("#%06X", BLUE & 0xFFFFFF));
            Files.createDirectories(dir.getParent());
            Files.writeString(dir, o.toString());
        } catch (Throwable ignored) {
        }
    }

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