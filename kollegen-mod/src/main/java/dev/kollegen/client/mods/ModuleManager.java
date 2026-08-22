package dev.kollegen.client.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet alle Module, lädt/speichert sie als JSON und ruft Tick- sowie
 * HUD-Render-Hooks auf. Bewusst OHNE fabric-api (nur net.minecraft + Gson +
 * FabricLoader für das Config-Verzeichnis).
 */
public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kollegen-modules.json");

    private ModuleManager() {
    }

    public static void register(Module m) {
        MODULES.add(m);
    }

    public static List<Module> modules() {
        return MODULES;
    }

    public static List<Module> modules(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.category == c) out.add(m);
        return out;
    }

    public static Module byId(String id) {
        for (Module m : MODULES) if (m.id.equals(id)) return m;
        return null;
    }

    public static void registerAll() {
        dev.kollegen.client.mods.modules.Visual.register();
        dev.kollegen.client.mods.modules.Hud.register();
        dev.kollegen.client.mods.modules.Gameplay.register();
        dev.kollegen.client.mods.modules.Player.register();
        dev.kollegen.client.mods.modules.World.register();
        dev.kollegen.client.mods.modules.Chat.register();
        dev.kollegen.client.mods.modules.Performance.register();
        dev.kollegen.client.mods.modules.Vulkan.register();
        dev.kollegen.client.mods.modules.Misc.register();
        dev.kollegen.client.mods.modules.InventoryTweaks.register();
        load();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        for (Module m : MODULES) {
            if (mc == null) break;
            if (m.enabled) {
                try {
                    m.onTick();
                } catch (Throwable t) {
                    // Ein fehlerhaftes Modul darf den Tick nicht crashen.
                }
            }
        }
    }

    public static void renderHud(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options != null && mc.options.hideGui) return;
        for (Module m : MODULES) {
            if (m.enabled) {
                try {
                    m.onRenderHud(g, tickDelta);
                } catch (Throwable t) {
                }
            }
        }
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            for (Module m : MODULES) {
                JsonObject o = new JsonObject();
                m.save(o);
                root.add(m.id, o);
            }
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (Exception ignored) {
        }
    }

    public static void load() {
        try {
            if (!Files.exists(PATH)) return;
            JsonObject root = GSON.fromJson(Files.readString(PATH), JsonObject.class);
            if (root == null) return;
            for (Module m : MODULES) {
                if (root.has(m.id)) {
                    try {
                        m.load(root.getAsJsonObject(m.id));
                    } catch (Throwable t) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
