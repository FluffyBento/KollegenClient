package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.Minecraft;

public final class Chat {

    private Chat() {
    }

    public static void register() {
        ModuleManager.register(new ClearChat());
    }

    /** Leert den Chat per Keybind. */
    private static class ClearChat extends Module {
        private final KeybindSetting key = new KeybindSetting("Taste", "Leert den Chat.");

        ClearChat() {
            super("clearchat", "Chat leeren", "Leert den Chat-Verlauf auf Tastendruck.", Category.CHAT);
            add(key);
        }

        @Override
        public void onKey() {
            try {
                if (mc.gui != null && mc.gui.getChat() != null) {
                    mc.gui.getChat().clearMessages(false);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
