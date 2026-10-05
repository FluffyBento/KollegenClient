package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;


public abstract class Setting {
    public final String name;
    public final String description;

    protected Setting(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public abstract void save(JsonObject o);

    public abstract void load(JsonObject o);

    
    public String valueText() {
        return "";
    }

    
    public abstract AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen);

    
    protected void changed() {
        ModuleManager.save();
    }
}
