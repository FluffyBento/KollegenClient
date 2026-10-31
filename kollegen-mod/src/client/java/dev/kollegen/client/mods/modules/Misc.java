package dev.kollegen.client.mods.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Misc {

    private Misc() {
    }

    public static void register() {
        InventoryColor.register();
        ChatHeads.register();
        Appleskin.register();
        AutoRemoveMods.register();
        ModuleManager.register(new ControllerSupport());
        ModuleManager.register(new SettingsSync());
    }

    private static class ControllerSupport extends Module {
        ControllerSupport() {
            super("controllersupport", "Controller-Support", "Aktiviert Gamepad-Steuerung und Menue-Navigation.", Category.MISC);
            this.enabled = dev.kollegen.client.input.ControllerMode.isActive();
        }

        @Override
        public void onEnable() {
            dev.kollegen.client.input.ControllerMode.setActive(true);
        }

        @Override
        public void onDisable() {
            dev.kollegen.client.input.ControllerMode.setActive(false);
        }
    }

    public static class SettingsSync extends Module {
        public SettingsSync() {
            super("settingssync", "Settings-Sync", "Synchronisiert Ingame-Settings und Tastenbelegungen zwischen Instanzen.", Category.MISC);
            this.enabled = readSyncSetting();
        }

        @Override
        public void onEnable() {
            writeSyncSetting(true);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gui != null && mc.gui.chatListener() != null) {
                try {
                    mc.gui.chatListener().handleSystemMessage(Component.literal("§a[Kollegen] §fSettings-Synchronisation aktiviert. Bitte Instanz neustarten, um synchronisierte Einstellungen zu laden."), false);
                } catch (Throwable ignored) {
                }
            }
        }

        @Override
        public void onDisable() {
            writeSyncSetting(false);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gui != null && mc.gui.chatListener() != null) {
                try {
                    mc.gui.chatListener().handleSystemMessage(Component.literal("§c[Kollegen] §fSettings-Synchronisation zwischen Instanzen deaktiviert."), false);
                } catch (Throwable ignored) {
                }
            }
        }

        private static Path getSettingsPath() {
            String home = System.getProperty("user.home", ".");
            return Path.of(home, ".local", "share", "kollegenclient", "settings.json");
        }

        private static boolean readSyncSetting() {
            try {
                Path p = getSettingsPath();
                if (Files.isRegularFile(p)) {
                    String content = Files.readString(p);
                    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                    if (obj.has("sync_ingame_settings")) {
                        return obj.get("sync_ingame_settings").getAsBoolean();
                    }
                }
            } catch (Throwable ignored) {
            }
            return true;
        }

        private static void writeSyncSetting(boolean enabled) {
            try {
                Path p = getSettingsPath();
                JsonObject obj;
                if (Files.isRegularFile(p)) {
                    obj = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                } else {
                    obj = new JsonObject();
                }
                obj.addProperty("sync_ingame_settings", enabled);
                if (p.getParent() != null) {
                    Files.createDirectories(p.getParent());
                }
                Files.writeString(p, obj.toString());
            } catch (Throwable ignored) {
            }
        }
    }
}
