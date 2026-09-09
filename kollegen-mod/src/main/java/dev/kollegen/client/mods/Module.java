package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;


public abstract class Module {
    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public boolean enabled = false;
    public int key = -1; 

    
    public String risk = null;

    
    public boolean locked = false;

    protected final List<Setting> settings = new ArrayList<>();
    protected final Minecraft mc = Minecraft.getInstance();

    protected Module(String id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void add(Setting s) {
        settings.add(s);
    }

    public List<Setting> settings() {
        return settings;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    
    public void onTick() {
    }

    
    public void onRenderHud(GuiGraphics g, float tickDelta) {
    }

    
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
        if (locked) enabled = true; 
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
