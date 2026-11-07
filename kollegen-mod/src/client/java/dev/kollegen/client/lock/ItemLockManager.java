package dev.kollegen.client.lock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ItemLockManager {

    private static final Map<Integer, String> lockedSlots = new HashMap<>();
    private static boolean loaded = false;

    private ItemLockManager() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("kollegen-item-locks.json");
    }

    public static boolean isLocked(int slotIndex) {
        ensureLoaded();
        return lockedSlots.containsKey(slotIndex);
    }

    public static boolean isLocked(Slot slot) {
        if (slot == null || !(slot.container instanceof Inventory)) return false;
        return isLocked(slot.getContainerSlot());
    }

    public static String getLockedItem(int slotIndex) {
        ensureLoaded();
        return lockedSlots.get(slotIndex);
    }

    public static Item getLockedItemType(int slotIndex) {
        String idStr = getLockedItem(slotIndex);
        if (idStr == null || idStr.isEmpty()) return Items.AIR;
        try {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(idStr));
        } catch (Throwable ignored) {
            return Items.AIR;
        }
    }

    public static boolean matches(Slot slot, ItemStack stack) {
        if (slot == null || !(slot.container instanceof Inventory)) return true;
        return matches(slot.getContainerSlot(), stack);
    }

    public static boolean matches(int slotIndex, ItemStack stack) {
        ensureLoaded();
        if (!lockedSlots.containsKey(slotIndex)) return true;
        if (stack == null || stack.isEmpty()) return true;
        String req = lockedSlots.get(slotIndex);
        String cur = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return req != null && req.equals(cur);
    }

    public static void toggleLock(Slot slot) {
        if (slot == null || !(slot.container instanceof Inventory)) return;
        int idx = slot.getContainerSlot();
        if (idx < 0 || idx > 40) return;
        ensureLoaded();

        Minecraft mc = Minecraft.getInstance();
        if (lockedSlots.containsKey(idx)) {
            lockedSlots.remove(idx);
            save();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 0.8f);
            }
            if (mc.gui != null && mc.gui.hud != null) {
                mc.gui.hud.setOverlayMessage(Component.literal("§a🔓 Slot " + (idx < 9 ? (idx + 1) : idx) + " entsperrt"), false);
                if (mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§7[Kollegen] §a🔓 Slot " + (idx < 9 ? (idx + 1) : idx) + " entsperrt"));
                }
            }
        } else {
            if (!slot.hasItem()) {
                if (mc.gui != null && mc.gui.hud != null) {
                    mc.gui.hud.setOverlayMessage(Component.literal("§cSlot ist leer. Lege zuerst ein Item hinein."), false);
                    if (mc.gui.hud.getChat() != null) {
                        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§7[Kollegen] §cSlot ist leer. Lege zuerst ein Item hinein, um es zu sperren."));
                    }
                }
                return;
            }
            ItemStack stack = slot.getItem();
            String idStr = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            lockedSlots.put(idx, idStr);
            save();
            String itemName = stack.getHoverName().getString();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f);
            }
            if (mc.gui != null && mc.gui.hud != null) {
                mc.gui.hud.setOverlayMessage(Component.literal("§6🔒 Slot gesperrt für: §e" + itemName), false);
                if (mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§7[Kollegen] §6🔒 Slot gesperrt & reserviert für: §e" + itemName));
                }
            }
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            for (String k : o.keySet()) {
                try {
                    int slot = Integer.parseInt(k);
                    lockedSlots.put(slot, o.get(k).getAsString());
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void save() {
        try {
            Path p = configPath();
            JsonObject o = new JsonObject();
            for (Map.Entry<Integer, String> e : lockedSlots.entrySet()) {
                o.addProperty(String.valueOf(e.getKey()), e.getValue());
            }
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, o.toString());
        } catch (Throwable ignored) {
        }
    }
}
