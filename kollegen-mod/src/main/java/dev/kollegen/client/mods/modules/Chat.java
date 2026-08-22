package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class Chat {

    /** Aktuelles Chat-Eingabefeld – vom ChatScreenMixin gesetzt. */
    public static EditBox activeChatInput = null;

    private Chat() {
    }

    public static void register() {
        ModuleManager.register(new ClearChat());
        ModuleManager.register(new AutoText());
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

    /** Fügt per Hotkey vorgefertigte Nachrichten in den Chat ein. */
    private static class AutoText extends Module {
        private static final int SLOTS = 6;
        private final Slot[] slots = new Slot[SLOTS];

        AutoText() {
            super("autotext", "Schnellantwort", "Fügt per Hotkey vorgefertigte Nachrichten in den Chat ein.", Category.CHAT);
            for (int i = 0; i < SLOTS; i++) {
                slots[i] = new Slot(i + 1);
                add(slots[i].text);
                add(slots[i].key);
            }
        }

        @Override
        public void onKey() {
            if (!enabled) return;
            for (Slot s : slots) {
                if (s.key.value >= 0 && isDown(s.key.value)) {
                    send(s.text.value);
                    break;
                }
            }
        }

        private boolean isDown(int key) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
        }

        private void send(String text) {
            if (text == null || text.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            if (mc.screen instanceof ChatScreen && activeChatInput != null) {
                String cur = activeChatInput.getValue();
                activeChatInput.setValue(cur.isEmpty() ? text : cur + " " + text);
                return;
            }
            mc.setScreen(new ChatScreen(text));
        }

        private static class Slot {
            final StringSetting text;
            final KeybindSetting key;

            Slot(int i) {
                text = new StringSetting("Vorlage " + i, "Text der Vorlage " + i, "");
                key = new KeybindSetting("Taste " + i, "Hotkey für Vorlage " + i);
            }
        }
    }
}
