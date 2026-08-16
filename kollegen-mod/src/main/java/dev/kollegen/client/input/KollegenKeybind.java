package dev.kollegen.client.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KollegenKeybind {
    public static KeyMapping menuKey;

    public static void register() {
        menuKey = new KeyMapping(
                "key.kollegen.menu",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.kollegen"
        );
        KeyBindingHelper.registerKeyBinding(menuKey);
    }
}
