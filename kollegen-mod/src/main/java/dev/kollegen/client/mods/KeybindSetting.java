package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import dev.kollegen.client.ui.GlassButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class KeybindSetting extends Setting {
    public int value = -1; // GLFW key, -1 = keine
    public static KeybindSetting capturing = null;

    public KeybindSetting(String name, String description) {
        super(name, description);
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("key", value);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("key")) value = o.get("key").getAsInt();
    }

    public static String keyName(int key) {
        if (key <= 0) return "Keine";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "Links-Shift";
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "Rechts-Shift";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL) return "Links-Strg";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL) return "Rechts-Strg";
        if (key == GLFW.GLFW_KEY_SPACE) return "Leertaste";
        if (key == GLFW.GLFW_KEY_LEFT_ALT) return "Links-Alt";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT) return "Rechts-Alt";
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return String.valueOf((char) ('A' + (key - GLFW.GLFW_KEY_A)));
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
            return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25)
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(key, 0);
        return n != null ? n.toUpperCase() : "Taste " + key;
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int w = Math.min(140, cw - 70);
        int h = 24;
        int x = px + cw - w - 8;
        int y = py + (rowH - h) / 2;
        GlassButton b = new GlassButton(x, y, w, h, Component.literal(capturing == this ? "…" : keyName(value)), btn -> {
            capturing = (capturing == this) ? null : this;
            btn.setMessage(Component.literal(capturing == this ? "…" : keyName(value)));
        });
        b.colors(Palette.PANEL2, Palette.ACCENT, Palette.TEXT);
        return b;
    }
}
