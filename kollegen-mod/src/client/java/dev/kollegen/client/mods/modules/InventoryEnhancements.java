package dev.kollegen.client.mods.modules;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryEnhancements extends Module {
    public static InventoryEnhancements INSTANCE;

    public final BooleanSetting searchBar = new BooleanSetting(
            "Suchleiste",
            "Zeigt eine Suchleiste im Inventar und in Truhen an.",
            true
    );

    public final BooleanSetting itemLock = new BooleanSetting(
            "Item-Sperre (Lock)",
            "Sperrt Items mit Taste, damit sie nicht versehentlich bewegt oder gedroppt werden.",
            true
    );

    public final KeybindSetting lockKey = new KeybindSetting(
            "Sperr-Taste",
            "Taste über einem Item drücken, um es zu sperren/entsperren.",
            InputConstants.KEY_L
    );

    private static final Map<String, Map<String, Item>> serverLocks = new ConcurrentHashMap<>();

    public InventoryEnhancements() {
        super("invenhance", "Inventar-Helfer", "Suchleiste und Item-Sperre für Kisten und Inventar.", Category.GAMEPLAY);
        INSTANCE = this;
        this.enabled = true;
        add(searchBar);
        add(itemLock);
        add(lockKey);
        loadFromFile();
    }

    public static String getCurrentServerKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return "default";
        try {
            if (mc.getCurrentServer() != null) {
                String ip = mc.getCurrentServer().ip;
                if (ip != null && !ip.isEmpty()) {
                    return "server:" + ip.trim().toLowerCase();
                }
            }
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                String name = mc.getSingleplayerServer().getWorldData().getLevelName();
                if (name != null && !name.isEmpty()) {
                    return "singleplayer:" + name.trim().toLowerCase();
                }
            }
            if (mc.level != null) {
                return "singleplayer";
            }
        } catch (Throwable ignored) {
        }
        return "default";
    }

    public static synchronized void loadFromFile() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("kollegen-item-locks.json");
            if (!Files.isRegularFile(file)) return;
            String content = Files.readString(file);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            serverLocks.clear();
            for (String serverKey : root.keySet()) {
                if (root.get(serverKey).isJsonObject()) {
                    JsonObject sObj = root.getAsJsonObject(serverKey);
                    Map<String, Item> map = new ConcurrentHashMap<>();
                    for (String slotKey : sObj.keySet()) {
                        String itemId = sObj.get(slotKey).getAsString();
                        Identifier id = Identifier.tryParse(itemId);
                        if (id != null) {
                            Item item = BuiltInRegistries.ITEM.getValue(id);
                            if (item != null && item != Items.AIR) {
                                map.put(slotKey, item);
                            }
                        }
                    }
                    serverLocks.put(serverKey, map);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void saveToFile() {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("kollegen-item-locks.json");
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Map<String, Item>> entry : serverLocks.entrySet()) {
                JsonObject sObj = new JsonObject();
                for (Map.Entry<String, Item> e : entry.getValue().entrySet()) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(e.getValue());
                    if (id != null) {
                        sObj.addProperty(e.getKey(), id.toString());
                    }
                }
                if (!sObj.entrySet().isEmpty()) {
                    root.add(entry.getKey(), sObj);
                }
            }
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void load(JsonObject o) {
        super.load(o);
        if (lockKey.value == 76 || lockKey.value == 49 || lockKey.value == 108) {
            lockKey.value = InputConstants.KEY_L;
        }
        loadFromFile();
    }

    @Override
    public void onKey() {
        if (!this.enabled || !itemLock.value) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getInventory() != null) {
            int sel = mc.player.getInventory().getSelectedSlot();
            toggleIndexLock(sel);
        }
    }

    public static boolean isSearchEnabled() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.searchBar.value;
    }

    public static boolean isLockEnabled() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.itemLock.value;
    }

    public static int getLockKeyCode() {
        if (INSTANCE == null) return InputConstants.KEY_L;
        return INSTANCE.lockKey.value;
    }

    private static boolean isPlayerInventory(Slot slot) {
        if (slot == null || slot.container == null) return false;
        if (slot.container instanceof Inventory) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && slot.container == mc.player.getInventory();
    }

    public static String getSlotKey(Slot slot) {
        if (slot == null) return null;
        if (isPlayerInventory(slot)) {
            return "p_" + slot.getContainerSlot();
        }
        return "m_" + slot.index;
    }

    public static boolean isSlotLocked(Slot slot) {
        if (!isLockEnabled() || slot == null) return false;
        String sKey = getCurrentServerKey();
        Map<String, Item> map = serverLocks.get(sKey);
        if (map == null) return false;
        String slotKey = getSlotKey(slot);
        return slotKey != null && map.containsKey(slotKey);
    }

    public static boolean isIndexLocked(int containerSlot) {
        if (!isLockEnabled()) return false;
        String sKey = getCurrentServerKey();
        Map<String, Item> map = serverLocks.get(sKey);
        if (map == null) return false;
        return map.containsKey("p_" + containerSlot);
    }

    public static Item getBoundItem(Slot slot) {
        if (!isLockEnabled() || slot == null) return null;
        String sKey = getCurrentServerKey();
        Map<String, Item> map = serverLocks.get(sKey);
        if (map == null) return null;
        String slotKey = getSlotKey(slot);
        return slotKey != null ? map.get(slotKey) : null;
    }

    public static boolean canAcceptItem(Slot slot, ItemStack stack) {
        if (!isLockEnabled() || slot == null) return true;
        if (stack == null || stack.isEmpty()) return true;
        if (!isSlotLocked(slot)) return true;
        Item bound = getBoundItem(slot);
        if (bound == null) return false;
        return stack.is(bound);
    }

    public static boolean toggleLock(Slot slot) {
        if (!isLockEnabled() || slot == null) return false;
        String sKey = getCurrentServerKey();
        Map<String, Item> map = serverLocks.computeIfAbsent(sKey, k -> new ConcurrentHashMap<>());
        String slotKey = getSlotKey(slot);
        if (slotKey == null) return false;

        if (map.containsKey(slotKey)) {
            map.remove(slotKey);
            saveToFile();
            return false;
        } else {
            Item item = slot.hasItem() ? slot.getItem().getItem() : null;
            if (item != null && item != Items.AIR) {
                map.put(slotKey, item);
                saveToFile();
                return true;
            }
            return false;
        }
    }

    public static boolean toggleIndexLock(int containerSlot) {
        if (!isLockEnabled()) return false;
        String sKey = getCurrentServerKey();
        Map<String, Item> map = serverLocks.computeIfAbsent(sKey, k -> new ConcurrentHashMap<>());
        String slotKey = "p_" + containerSlot;
        if (map.containsKey(slotKey)) {
            map.remove(slotKey);
            saveToFile();
            return false;
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getInventory() != null) {
                ItemStack item = mc.player.getInventory().getItem(containerSlot);
                if (!item.isEmpty() && item.getItem() != Items.AIR) {
                    map.put(slotKey, item.getItem());
                    saveToFile();
                    return true;
                }
            }
            return false;
        }
    }

    public static void ensureBound(Slot slot) {
        if (!isLockEnabled() || slot == null || !slot.hasItem()) return;
        String sKey = getCurrentServerKey();
        Map<String, Item> map = serverLocks.computeIfAbsent(sKey, k -> new ConcurrentHashMap<>());
        String slotKey = getSlotKey(slot);
        if (slotKey != null && map.containsKey(slotKey)) {
            Item bound = map.get(slotKey);
            if (bound == null || bound == Items.AIR) {
                map.put(slotKey, slot.getItem().getItem());
                saveToFile();
            }
        }
    }
}