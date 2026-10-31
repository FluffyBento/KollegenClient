package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.ui.GlassButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;


public class KeybindSetting extends Setting {
    public int value = -1;
    public static KeybindSetting capturing = null;

    public KeybindSetting(String name, String description) {
        super(name, description);
    }

    public KeybindSetting(String name, String description, int def) {
        super(name, description);
        this.value = def;
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
        try {
            String name = InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
            if (name != null && !name.isEmpty() && !name.startsWith("key.keyboard.")) {
                return name;
            }
        } catch (Throwable ignored) {
        }
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == InputConstants.KEY_LSHIFT) return "Links-Shift";
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == InputConstants.KEY_RSHIFT) return "Rechts-Shift";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == InputConstants.KEY_LCONTROL) return "Links-Strg";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL || key == InputConstants.KEY_RCONTROL) return "Rechts-Strg";
        if (key == GLFW.GLFW_KEY_SPACE || key == InputConstants.KEY_SPACE) return "Leertaste";
        if (key == GLFW.GLFW_KEY_LEFT_ALT || key == InputConstants.KEY_LALT) return "Links-Alt";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT || key == InputConstants.KEY_RALT) return "Rechts-Alt";
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
        int h = 28;
        int x = px + cw - w - 8;
        int y = py + (rowH - h) / 2;
        GlassButton b = new GlassButton(x, y, w, h, Component.literal(capturing == this ? "…" : keyName(value)), btn -> {
            capturing = (capturing == this) ? null : this;
            btn.setMessage(Component.literal(capturing == this ? "…" : keyName(value)));
        });
        return b;
    }
}