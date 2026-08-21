package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Modul (Feature) mit eigenen Einstellungen. Entspricht dem, was NoRisk /
 * Feather / LabyMod als „Modul" bezeichnen – gebündelt mit Toggles, Slidern,
 * Modi, Farben und einem Keybind.
 */
public abstract class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public boolean enabled = false;
    public int key = -1; // GLFW key, -1 = keine

    /** Warnhinweis (z. B. "Server-Risiko") – wird im Menü rot markiert. */
    public String risk = null;

    protected final List<Setting> settings = new ArrayList<>();
    protected final Minecraft mc = Minecraft.getInstance();

    protected Module(String id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected void add(Setting s) {
        settings.add(s);
    }

    public List<Setting> settings() {
        return settings;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    /** Jeden Client-Tick (nur wenn enabled). */
    public void onTick() {
    }

    /** HUD-Render (nur wenn enabled). */
    public void onRenderHud(GuiGraphics g, float tickDelta) {
    }

    /** Wird aufgerufen, wenn eine Keybind dieses Moduls gedrückt wird. */
    public void onKey() {
    }

    public void save(JsonObject o) {
        o.addProperty("enabled", enabled);
        if (key != -1) o.addProperty("key", key);
        if (!settings.isEmpty()) {
            JsonObject s = new JsonObject();
            for (Setting set : settings) set.save(s);
            o.add("settings", s);
        }
    }

    public void load(JsonObject o) {
        if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
        if (o.has("key")) key = o.get("key").getAsInt();
        if (o.has("settings")) {
            JsonObject s = o.getAsJsonObject("settings");
            for (Setting set : settings) {
                if (s.has(set.name)) set.load(s.getAsJsonObject(set.name));
            }
        }
        if (enabled) onEnable();
    }
}
