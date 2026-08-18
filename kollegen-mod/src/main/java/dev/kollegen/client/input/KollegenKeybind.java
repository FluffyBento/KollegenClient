package dev.kollegen.client.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Liest die Rechts-Shift-Taste direkt vom GLFW-Fenster aus. Damit braucht der
 * Mod KEINE fabric-api (das fabric-Keybinding-API wäre sonst eine Pflicht-
 * Abhängigkeit, die in fremden Instanzen fehlen kann).
 */
public final class KollegenKeybind {

    private KollegenKeybind() {
    }

    public static boolean isRightShiftHeld() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            long handle = mc.getWindow().getWindow();
            return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }
}
