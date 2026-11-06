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
    public boolean ctrl = false;
    public boolean shift = false;
    public boolean alt = false;
    public static KeybindSetting capturing = null;

    public KeybindSetting(String name, String description) {
        super(name, description);
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("key", value);
        if (ctrl) o.addProperty("ctrl", true);
        if (shift) o.addProperty("shift", true);
        if (alt) o.addProperty("alt", true);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("key")) value = o.get("key").getAsInt();
        ctrl = o.has("ctrl") && o.get("ctrl").getAsBoolean();
        shift = o.has("shift") && o.get("shift").getAsBoolean();
        alt = o.has("alt") && o.get("alt").getAsBoolean();
    }

    public int getCompositeId() {
        return value | (ctrl ? 0x10000 : 0) | (shift ? 0x20000 : 0) | (alt ? 0x40000 : 0);
    }

    public String getDisplayName() {
        if (value <= 0) return "Keine";
        StringBuilder sb = new StringBuilder();
        if (ctrl) sb.append("Strg+");
        if (shift) sb.append("Shift+");
        if (alt) sb.append("Alt+");
        sb.append(keyName(value));
        return sb.toString();
    }

    public static String rawKeyName(int key) {
        if (key <= 0) return "NONE";
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F24) {
            return "F" + (key - InputConstants.KEY_F1 + 1);
        }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (key - GLFW.GLFW_KEY_A)));
        }
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) {
            return String.valueOf((char) ('A' + (key - InputConstants.KEY_A)));
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
        }
        if (key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9) {
            return String.valueOf((char) ('0' + (key - InputConstants.KEY_0)));
        }
        String n = GLFW.glfwGetKeyName(key, 0);
        return n != null ? n.toUpperCase() : "Taste " + key;
    }

    public static String keyName(int key) {
        if (key <= 0) return "Keine";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == InputConstants.KEY_LSHIFT) return "Links-Shift";
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == InputConstants.KEY_RSHIFT) return "Rechts-Shift";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == InputConstants.KEY_LCONTROL) return "Links-Strg";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL || key == InputConstants.KEY_RCONTROL) return "Rechts-Strg";
        if (key == GLFW.GLFW_KEY_SPACE || key == InputConstants.KEY_SPACE) return "Leertaste";
        if (key == GLFW.GLFW_KEY_LEFT_ALT || key == InputConstants.KEY_LALT) return "Links-Alt";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT || key == InputConstants.KEY_RALT) return "Rechts-Alt";
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            return String.valueOf((char) ('A' + (key - GLFW.GLFW_KEY_A)));
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z)
            return String.valueOf((char) ('A' + (key - InputConstants.KEY_A)));
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
            return String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
        if (key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9)
            return String.valueOf((char) ('0' + (key - InputConstants.KEY_0)));
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25)
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F24)
            return "F" + (key - InputConstants.KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(key, 0);
        return n != null ? n.toUpperCase() : "Taste " + key;
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int w = Math.min(140, cw - 70);
        int h = 28;
        int x = px + cw - w - 8;
        int y = py + (rowH - h) / 2;
        GlassButton b = new GlassButton(x, y, w, h, Component.literal(capturing == this ? "…" : getDisplayName()), btn -> {
            capturing = (capturing == this) ? null : this;
            btn.setMessage(Component.literal(capturing == this ? "…" : getDisplayName()));
        });
        return b;
    }
}