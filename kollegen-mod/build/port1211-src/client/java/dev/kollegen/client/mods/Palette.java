package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class Palette {
    public static int BG = -15921902;
    public static int PANEL = -15066588;
    public static int PANEL2 = -14606034;
    public static int BORDER = -13356998;
    public static int ACCENT = -678365;
    public static int ACCENT2 = -34304;
    public static int TEXT = -792104;
    public static int MUTED = -4609652;
    public static int DANGER = -42130;
    public static int GREEN = -12663699;
    public static int BLUE = -11885569;
    public static int SHADOW = 0x66000000;
    public static int HOVER_OVERLAY = 0x20FFFFFF;
    public static int PRESSED_OVERLAY = 0x40000000;
    public static int FOCUS_BORDER = -2131384797;
    public static int SCROLL_TRACK = 0x40000000;
    public static int SCROLL_THUMB = -2130706433;
    public static int SCROLL_THUMB_HOVER = -1593835521;
    public static int DISABLED_OVERLAY = 0x60000000;
    private static String currentTheme = "default";
    private static final Map<String, Theme> THEMES = new LinkedHashMap<String, Theme>();

    private Palette() {
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
            Palette.saveTheme();
        }
    }

    public static int tint(int argb, int alpha) {
        return alpha << 24 | argb & 0xFFFFFF;
    }

    public static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = (int)((float)ar + (float)(br - ar) * t);
        int g = (int)((float)ag + (float)(bg - ag) * t);
        int bl = (int)((float)ab + (float)(bb - ab) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    public static void loadTheme() {
        try {
            String themeId;
            Theme t;
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("kollegen-theme.json");
            if (!Files.exists(dir, new LinkOption[0])) {
                return;
            }
            JsonObject o = JsonParser.parseString((String)Files.readString(dir)).getAsJsonObject();
            if (o.has("theme") && (t = THEMES.get(themeId = o.get("theme").getAsString())) != null) {
                currentTheme = themeId;
                t.apply();
            }
            if (o.has("bg")) {
                BG = Palette.parse(o.get("bg").getAsString(), BG);
            }
            if (o.has("panel")) {
                PANEL = Palette.parse(o.get("panel").getAsString(), PANEL);
            }
            if (o.has("panel2")) {
                PANEL2 = Palette.parse(o.get("panel2").getAsString(), PANEL2);
            }
            if (o.has("border")) {
                BORDER = Palette.parse(o.get("border").getAsString(), BORDER);
            }
            if (o.has("accent")) {
                ACCENT = Palette.parse(o.get("accent").getAsString(), ACCENT);
            }
            if (o.has("accent2")) {
                ACCENT2 = Palette.parse(o.get("accent2").getAsString(), ACCENT2);
            }
            if (o.has("text")) {
                TEXT = Palette.parse(o.get("text").getAsString(), TEXT);
            }
            if (o.has("muted")) {
                MUTED = Palette.parse(o.get("muted").getAsString(), MUTED);
            }
            if (o.has("danger")) {
                DANGER = Palette.parse(o.get("danger").getAsString(), DANGER);
            }
            if (o.has("green")) {
                GREEN = Palette.parse(o.get("green").getAsString(), GREEN);
            }
            if (o.has("blue")) {
                BLUE = Palette.parse(o.get("blue").getAsString(), BLUE);
            }
            if (o.has("glass")) {
                dev.kollegen.client.ui.Glass.opacity = Math.max(0.15f, Math.min(0.95f, o.get("glass").getAsFloat()));
            }
        } catch (Throwable throwable) {
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
            o.addProperty("glass", Float.valueOf(dev.kollegen.client.ui.Glass.opacity));
            Files.createDirectories(dir.getParent(), new FileAttribute[0]);
            Files.writeString(dir, (CharSequence)o.toString(), new OpenOption[0]);
        } catch (Throwable throwable) {
        }
    }

    private static int parse(String s, int fallback) {
        try {
            Object h;
            Object object = h = s.startsWith("#") ? s.substring(1) : s;
            if (((String)h).startsWith("0x") || ((String)h).startsWith("0X")) {
                h = ((String)h).substring(2);
            }
            if (((String)h).length() == 6) {
                h = "FF" + (String)h;
            }
            return (int)Long.parseLong((String)h, 16);
        } catch (Throwable t) {
            return fallback;
        }
    }

    static {
        THEMES.put("default", new Theme("Default", "Default warm theme", -15921902, -15066588, -14606034, -13356998, -678365, -34304, -792104, -4609652, -42130, -12663699, -11885569));
        THEMES.put("dark", new Theme("Dark", "Pure dark theme", -16119286, -15461356, -14803426, -13816531, -4487428, -6461232, -1513240, -5197648, -3185031, -16524602, -10177034));
        THEMES.put("nord", new Theme("Nord", "Arctic, north-bluish theme", -13749184, -12893614, -12366754, -11774358, -7814960, -8281663, -2564375, -4669232, -4234902, -6046068, -10583636));
        THEMES.put("dracula", new Theme("Dracula", "Dark theme with purple accents", -14144970, -12302502, -12302502, -10325340, -4353031, -34362, -460558, -4342339, -43691, -11470213, -7607811));
        THEMES.put("gruvbox", new Theme("Gruvbox", "Retro groove colors", -14145496, -12830666, -11515579, -10068908, -2647775, -98279, -1320014, -5727868, -308940, -4670682, -8149608));
        THEMES.put("tokyo-night", new Theme("Tokyo Night", "Dark Tokyo night theme", -15066330, -14407621, -13683638, -12893599, -8740105, -4482313, -4142347, -5656106, -559474, -6369686, -13974562));
        THEMES.put("catppuccin-mocha", new Theme("Catppuccin Mocha", "Soothing pastel theme", -14803410, -13553084, -12236966, -10986640, -671001, -400721, -3287308, -4537634, -816216, -5839967, -7752454));
        THEMES.put("high-contrast", new Theme("High Contrast", "Accessibility-focused theme", -16777216, -15066598, -13421773, -1, -256, -22016, -1, -3355444, -65536, -16711936, -16711681));
        THEMES.put("minecraft", new Theme("Minecraft", "Vanilla Minecraft style", -15724528, -14671840, -13619152, -11184811, -11141121, -11141291, -1, -5592406, -43691, -11141291, -11184641));
    }

    public static class Theme {
        public final String id;
        public final String name;
        public final String description;
        public final int bg;
        public final int panel;
        public final int panel2;
        public final int border;
        public final int accent;
        public final int accent2;
        public final int text;
        public final int muted;
        public final int danger;
        public final int green;
        public final int blue;

        Theme(String name, String description, int bg, int panel, int panel2, int border, int accent, int accent2, int text, int muted, int danger, int green, int blue) {
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
            BG = this.bg;
            PANEL = this.panel;
            PANEL2 = this.panel2;
            BORDER = this.border;
            ACCENT = this.accent;
            ACCENT2 = this.accent2;
            TEXT = this.text;
            MUTED = this.muted;
            DANGER = this.danger;
            GREEN = this.green;
            BLUE = this.blue;
            SHADOW = Palette.tint(BG, 128);
            HOVER_OVERLAY = Palette.tint(ACCENT, 32);
            PRESSED_OVERLAY = Palette.tint(BG, 96);
            FOCUS_BORDER = Palette.tint(ACCENT, 128);
            SCROLL_TRACK = Palette.tint(BG, 64);
            SCROLL_THUMB = Palette.tint(ACCENT, 128);
            SCROLL_THUMB_HOVER = Palette.tint(ACCENT, 160);
            DISABLED_OVERLAY = Palette.tint(BG, 96);
        }
    }
}

