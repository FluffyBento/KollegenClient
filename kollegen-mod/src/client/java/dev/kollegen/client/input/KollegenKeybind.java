package dev.kollegen.client.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;


public final class KollegenKeybind {

    private KollegenKeybind() {
    }

    public static boolean isRightShiftHeld() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            long handle = mc.getWindow().handle();
            return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }
}
