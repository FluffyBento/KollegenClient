package dev.kollegen.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.server.ServerListSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GuiCustomizationSettings {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("kollegen-gui-custom.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum ButtonStyle {
        LIQUID_GLASS("Liquid Glass"),
        MODERN_DARK("Modern Dark"),
        NEON_CYBER("Neon"),
        VANILLA("Vanilla (Standard)");

        public final String label;

        ButtonStyle(String label) {
            this.label = label;
        }
    }

    public enum ButtonShape {
        ROUND("Rund (Pill)"),
        ROUND_8("Gerundet (8px)"),
        SEMI_ROUND("Leicht gerundet (4px)"),
        SQUARE("Eckig (0px)");

        public final String label;

        ButtonShape(String label) {
            this.label = label;
        }
    }

    public enum ButtonBorder {
        ACCENT("Akzentfarbe"),
        SUBTLE("Subtil Weiß"),
        BRIGHT("Hell"),
        NONE("Keiner");

        public final String label;

        ButtonBorder(String label) {
            this.label = label;
        }
    }

    public enum ContainerStyle {
        LIQUID_GLASS("Liquid Glass"),
        MODERN_DARK("Modern Dark"),
        NEON("Neon"),
        VANILLA("Vanilla (Standard)");

        public final String label;

        ContainerStyle(String label) {
            this.label = label;
        }
    }

    public enum SlotStyle {
        MODERN("Gerundet (Modern)"),
        SUBTLE("Subtil"),
        VANILLA("Vanilla (Standard)");

        public final String label;

        SlotStyle(String label) {
            this.label = label;
        }
    }

    public enum ContainerShape {
        ROUND("Gerundet (10px)"),
        SEMI_ROUND("Leicht gerundet (5px)"),
        SQUARE("Eckig (0px)");

        public final String label;

        ContainerShape(String label) {
            this.label = label;
        }
    }

    public static final class AccentColorEntry {
        public final String name;
        public final int color;

        public AccentColorEntry(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    public static final AccentColorEntry[] ACCENT_PRESETS = {
            new AccentColorEntry("Blau", 0xFF3B82F6),
            new AccentColorEntry("Violett", 0xFF8B5CF6),
            new AccentColorEntry("Cyan", 0xFF06B6D4),
            new AccentColorEntry("Smaragd", 0xFF10B981),
            new AccentColorEntry("Rosa", 0xFFF43F5E),
            new AccentColorEntry("Gold", 0xFFF59E0B),
            new AccentColorEntry("Rot", 0xFFEF4444)
    };

    private static ButtonStyle buttonStyle = ButtonStyle.LIQUID_GLASS;
    private static ButtonShape buttonShape = ButtonShape.ROUND;
    private static ButtonBorder buttonBorder = ButtonBorder.ACCENT;
    private static float buttonOpacity = 0.65f;
    private static float glowStrength = 1.0f;
    private static int accentIndex = 0;

    private static ContainerStyle containerStyle = ContainerStyle.VANILLA;
    private static float containerOpacity = 0.65f;
    private static SlotStyle slotStyle = SlotStyle.VANILLA;
    private static ContainerShape containerShape = ContainerShape.SQUARE;

    private static boolean loaded = false;

    private GuiCustomizationSettings() {
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("buttonStyle")) {
                buttonStyle = ButtonStyle.valueOf(obj.get("buttonStyle").getAsString());
            }
            if (obj.has("buttonShape")) {
                buttonShape = ButtonShape.valueOf(obj.get("buttonShape").getAsString());
            }
            if (obj.has("buttonBorder")) {
                buttonBorder = ButtonBorder.valueOf(obj.get("buttonBorder").getAsString());
            }
            if (obj.has("buttonOpacity")) {
                buttonOpacity = Math.max(0.10f, Math.min(1.0f, obj.get("buttonOpacity").getAsFloat()));
            }
            if (obj.has("glowStrength")) {
                glowStrength = Math.max(0.0f, Math.min(3.0f, obj.get("glowStrength").getAsFloat()));
            } else if (obj.has("buttonGlow")) {
                glowStrength = obj.get("buttonGlow").getAsBoolean() ? 1.0f : 0.0f;
            }
            if (obj.has("accentIndex")) {
                accentIndex = Math.max(0, Math.min(ACCENT_PRESETS.length - 1, obj.get("accentIndex").getAsInt()));
            }
            if (obj.has("containerStyle")) {
                containerStyle = ContainerStyle.valueOf(obj.get("containerStyle").getAsString());
            }
            if (obj.has("containerOpacity")) {
                containerOpacity = Math.max(0.10f, Math.min(1.0f, obj.get("containerOpacity").getAsFloat()));
            }
            if (obj.has("slotStyle")) {
                slotStyle = SlotStyle.valueOf(obj.get("slotStyle").getAsString());
            }
            if (obj.has("containerShape")) {
                containerShape = ContainerShape.valueOf(obj.get("containerShape").getAsString());
            }
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        try {
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("buttonStyle", buttonStyle.name());
            obj.addProperty("buttonShape", buttonShape.name());
            obj.addProperty("buttonBorder", buttonBorder.name());
            obj.addProperty("buttonOpacity", buttonOpacity);
            obj.addProperty("glowStrength", glowStrength);
            obj.addProperty("buttonGlow", glowStrength > 0.01f);
            obj.addProperty("accentIndex", accentIndex);

            obj.addProperty("containerStyle", containerStyle.name());
            obj.addProperty("containerOpacity", containerOpacity);
            obj.addProperty("slotStyle", slotStyle.name());
            obj.addProperty("containerShape", containerShape.name());

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(obj, writer);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void resetToVanillaDefaults() {
        load();
        buttonStyle = ButtonStyle.VANILLA;
        buttonShape = ButtonShape.SQUARE;
        buttonBorder = ButtonBorder.NONE;
        buttonOpacity = 1.0f;
        glowStrength = 0.0f;
        containerStyle = ContainerStyle.VANILLA;
        containerOpacity = 1.0f;
        slotStyle = SlotStyle.VANILLA;
        containerShape = ContainerShape.SQUARE;
        save();
        ServerListSettings.resetDefaults();
        UiStyleManager.setGlobalStyle(UiStyle.MINECRAFT_VANILLA);
    }

    public static ButtonStyle getButtonStyle() {
        load();
        return buttonStyle;
    }

    public static void cycleButtonStyle() {
        load();
        ButtonStyle[] vals = ButtonStyle.values();
        buttonStyle = vals[(buttonStyle.ordinal() + 1) % vals.length];
        save();
    }

    public static ButtonShape getButtonShape() {
        load();
        return buttonShape;
    }

    public static void cycleButtonShape() {
        load();
        ButtonShape[] vals = ButtonShape.values();
        buttonShape = vals[(buttonShape.ordinal() + 1) % vals.length];
        save();
    }

    public static float getCornerRadius(int height) {
        load();
        return switch (buttonShape) {
            case ROUND -> Math.min(height / 2.0f, 12.0f);
            case ROUND_8 -> Math.min(8.0f, height / 2.0f);
            case SEMI_ROUND -> Math.min(4.0f, height / 3.0f);
            case SQUARE -> 0.0f;
        };
    }

    public static ButtonBorder getButtonBorder() {
        load();
        return buttonBorder;
    }

    public static void cycleButtonBorder() {
        load();
        ButtonBorder[] vals = ButtonBorder.values();
        buttonBorder = vals[(buttonBorder.ordinal() + 1) % vals.length];
        save();
    }

    public static float getButtonOpacity() {
        load();
        return buttonOpacity;
    }

    public static void setButtonOpacity(float opacity) {
        load();
        buttonOpacity = Math.max(0.10f, Math.min(1.0f, opacity));
        save();
    }

    public static void cycleButtonOpacity() {
        load();
        float[] options = { 0.35f, 0.50f, 0.65f, 0.80f, 1.0f };
        int idx = 2;
        for (int i = 0; i < options.length; i++) {
            if (Math.abs(options[i] - buttonOpacity) < 0.05f) {
                idx = i;
                break;
            }
        }
        buttonOpacity = options[(idx + 1) % options.length];
        save();
    }

    public static float getGlowStrength() {
        load();
        return glowStrength;
    }

    public static void setGlowStrength(float strength) {
        load();
        glowStrength = Math.max(0.0f, Math.min(3.0f, strength));
        save();
    }

    public static boolean isButtonGlow() {
        load();
        return glowStrength > 0.01f;
    }

    public static void toggleButtonGlow() {
        load();
        glowStrength = (glowStrength > 0.01f) ? 0.0f : 1.0f;
        save();
    }

    public static void cycleGlowStrength() {
        load();
        float[] presets = { 0.0f, 0.5f, 1.0f, 1.5f, 2.0f };
        int idx = 2;
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(presets[i] - glowStrength) < 0.1f) {
                idx = i;
                break;
            }
        }
        glowStrength = presets[(idx + 1) % presets.length];
        save();
    }

    public static String getGlowLabel() {
        load();
        if (glowStrength <= 0.01f) {
            return "Aus (0%)";
        }
        int pct = Math.round(glowStrength * 100.0f);
        if (pct == 50) return "50% (Subtil)";
        if (pct == 100) return "100% (Standard)";
        if (pct == 150) return "150% (Intensiv)";
        if (pct == 200) return "200% (Extrem)";
        return pct + "%";
    }

    public static ContainerStyle getContainerStyle() {
        load();
        return containerStyle;
    }

    public static void cycleContainerStyle() {
        load();
        ContainerStyle[] vals = ContainerStyle.values();
        containerStyle = vals[(containerStyle.ordinal() + 1) % vals.length];
        save();
    }

    public static float getContainerOpacity() {
        load();
        return containerOpacity;
    }

    public static void setContainerOpacity(float opacity) {
        load();
        containerOpacity = Math.max(0.10f, Math.min(1.0f, opacity));
        save();
    }

    public static void cycleContainerOpacity() {
        load();
        float[] options = { 0.40f, 0.65f, 0.80f, 0.88f, 1.0f };
        int idx = 3;
        for (int i = 0; i < options.length; i++) {
            if (Math.abs(options[i] - containerOpacity) < 0.05f) {
                idx = i;
                break;
            }
        }
        containerOpacity = options[(idx + 1) % options.length];
        save();
    }

    public static SlotStyle getSlotStyle() {
        load();
        return slotStyle;
    }

    public static void cycleSlotStyle() {
        load();
        SlotStyle[] vals = SlotStyle.values();
        slotStyle = vals[(slotStyle.ordinal() + 1) % vals.length];
        save();
    }

    public static ContainerShape getContainerShape() {
        load();
        return containerShape;
    }

    public static void cycleContainerShape() {
        load();
        ContainerShape[] vals = ContainerShape.values();
        containerShape = vals[(containerShape.ordinal() + 1) % vals.length];
        save();
    }

    public static float getContainerCornerRadius() {
        load();
        return switch (containerShape) {
            case ROUND -> 10.0f;
            case SEMI_ROUND -> 5.0f;
            case SQUARE -> 0.0f;
        };
    }

    public static String getAccentName() {
        load();
        return ACCENT_PRESETS[accentIndex].name;
    }

    public static int getAccentColor() {
        load();
        return ACCENT_PRESETS[accentIndex].color;
    }

    public static void cycleAccentColor() {
        load();
        accentIndex = (accentIndex + 1) % ACCENT_PRESETS.length;
        Palette.ACCENT = ACCENT_PRESETS[accentIndex].color;
        Palette.saveTheme();
        save();
    }

    public static void cycleUiStyle() {
        UiStyle[] vals = UiStyle.values();
        UiStyle cur = UiStyleManager.getGlobalStyle();
        int next = (cur.ordinal() + 1) % vals.length;
        UiStyleManager.setGlobalStyle(vals[next]);
    }

    public static void cycleTheme() {
        List<String> themeIds = new ArrayList<>(Palette.getThemes().keySet());
        if (themeIds.isEmpty()) return;
        String cur = Palette.getCurrentTheme();
        int idx = themeIds.indexOf(cur);
        int next = (idx + 1) % themeIds.size();
        Palette.setTheme(themeIds.get(next));
    }

    public static String getThemeDisplayName() {
        String cur = Palette.getCurrentTheme();
        Palette.Theme t = Palette.getThemes().get(cur);
        if (t != null && t.name != null) {
            return t.name;
        }
        return cur;
    }
}
