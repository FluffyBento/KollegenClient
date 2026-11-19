package dev.kollegen.client.mods;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ModuleCommandHandler {
    private ModuleCommandHandler() {}

    public static void handle(String fullCommand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null || mc.gui.hud.getChat() == null) return;

        String trimmed = fullCommand.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length <= 1) {
            send(mc, "§6[Kollegen] §fVerwendung: §e.module <modul> [toggle|on|off|reset|<einstellung> <wert>]");
            StringBuilder sb = new StringBuilder("§7Verfügbare Module: §b");
            var list = ModuleManager.modules();
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i).id);
                if (i < list.size() - 1) sb.append("§7, §b");
            }
            send(mc, sb.toString());
            return;
        }

        String modId = parts[1].toLowerCase();
        Module m = null;
        for (Module candidate : ModuleManager.modules()) {
            if (candidate.id.equalsIgnoreCase(modId) || candidate.name.equalsIgnoreCase(modId)) {
                m = candidate;
                break;
            }
        }
        if (m == null) {
            send(mc, "§c[Kollegen] Modul '§e" + parts[1] + "§c' wurde nicht gefunden!");
            return;
        }

        if (parts.length == 2 || parts[2].equalsIgnoreCase("toggle")) {
            m.enabled = !m.enabled;
            if (m.enabled) {
                m.onEnable();
            } else {
                m.onDisable();
            }
            ModuleManager.save();
            send(mc, "§6[Kollegen] §fModul §e" + m.name + " §fist jetzt: " + (m.enabled ? "§aAktiviert" : "§cDeaktiviert"));
            return;
        }

        String action = parts[2];
        if (action.equalsIgnoreCase("on") || action.equalsIgnoreCase("enable") || action.equalsIgnoreCase("true")) {
            m.enabled = true;
            m.onEnable();
            ModuleManager.save();
            send(mc, "§6[Kollegen] §fModul §e" + m.name + " §aaktiviert");
            return;
        }
        if (action.equalsIgnoreCase("off") || action.equalsIgnoreCase("disable") || action.equalsIgnoreCase("false")) {
            m.enabled = false;
            m.onDisable();
            ModuleManager.save();
            send(mc, "§6[Kollegen] §fModul §e" + m.name + " §cdeaktiviert");
            return;
        }
        if (action.equalsIgnoreCase("reset")) {
            for (Setting s : m.settings()) {
                if (s instanceof BooleanSetting bs) bs.value = false;
                else if (s instanceof SliderSetting ss) ss.value = ss.min;
            }
            ModuleManager.save();
            send(mc, "§6[Kollegen] §fEinstellungen von §e" + m.name + " §fauf Standard zurückgesetzt.");
            return;
        }

        Setting targetSetting = null;
        for (Setting s : m.settings()) {
            if (s.name.equalsIgnoreCase(action) || s.name.replace(" ", "").equalsIgnoreCase(action)) {
                targetSetting = s;
                break;
            }
        }

        if (targetSetting == null) {
            send(mc, "§c[Kollegen] Unbekannte Aktion oder Einstellung '§e" + action + "§c' für §b" + m.name);
            StringBuilder sb = new StringBuilder("§7Mögliche Einstellungen: §e");
            for (int i = 0; i < m.settings().size(); i++) {
                sb.append(m.settings().get(i).name);
                if (i < m.settings().size() - 1) sb.append("§7, §e");
            }
            send(mc, sb.toString());
            return;
        }

        if (parts.length == 3) {
            send(mc, "§6[Kollegen] §b" + m.name + " §7-> §e" + targetSetting.name + " §f= §a" + targetSetting.valueText());
            return;
        }

        String val = parts[3];
        if (targetSetting instanceof BooleanSetting bs) {
            bs.value = val.equalsIgnoreCase("true") || val.equalsIgnoreCase("1") || val.equalsIgnoreCase("on") || val.equalsIgnoreCase("ja");
            ModuleManager.save();
            send(mc, "§6[Kollegen] §b" + m.name + " §7-> §e" + bs.name + " §fauf " + (bs.value ? "§aEin" : "§cAus") + " §fgesetzt.");
        } else if (targetSetting instanceof SliderSetting ss) {
            try {
                double num = Double.parseDouble(val.replace(",", "."));
                ss.value = Math.max(ss.min, Math.min(ss.max, num));
                ModuleManager.save();
                send(mc, "§6[Kollegen] §b" + m.name + " §7-> §e" + ss.name + " §fauf §a" + ss.value + " §fgesetzt.");
            } catch (Exception ex) {
                send(mc, "§c[Kollegen] Ungültige Zahl: §e" + val);
            }
        } else if (targetSetting instanceof NumberSetting ns) {
            try {
                double num = Double.parseDouble(val.replace(",", "."));
                ns.value = (int) Math.round(num);
                ModuleManager.save();
                send(mc, "§6[Kollegen] §b" + m.name + " §7-> §e" + ns.name + " §fauf §a" + ns.value + " §fgesetzt.");
            } catch (Exception ex) {
                send(mc, "§c[Kollegen] Ungültige Zahl: §e" + val);
            }
        } else if (targetSetting instanceof ModeSetting ms) {
            for (int i = 0; i < ms.options.length; i++) {
                if (ms.options[i].equalsIgnoreCase(val)) {
                    ms.index = i;
                    break;
                }
            }
            ModuleManager.save();
            send(mc, "§6[Kollegen] §b" + m.name + " §7-> §e" + ms.name + " §fauf §a" + ms.current() + " §fgesetzt.");
        } else if (targetSetting instanceof StringSetting st) {
            st.value = val;
            ModuleManager.save();
            send(mc, "§6[Kollegen] §b" + m.name + " §7-> §e" + st.name + " §fauf §a" + st.value + " §fgesetzt.");
        }
    }

    private static void send(Minecraft mc, String text) {
        if (mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
        }
    }
}
