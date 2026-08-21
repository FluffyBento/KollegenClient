package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/**
 * Basisklasse einer Moduleinstellung. Jede Einstellung kann sich selbst in
 * JSON speichern/laden und ein passendes Widget für das Menü bauen.
 */
public abstract class Setting {
    public final String name;
    public final String description;

    protected Setting(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public abstract void save(JsonObject o);

    public abstract void load(JsonObject o);

    /** Live-Wert als Text (vom Menü neben dem Control gezeichnet). */
    public String valueText() {
        return "";
    }

    /**
     * Baut das Steuerelement. (px, py) ist die linke Mitte des Control-Bereichs,
     * cw die verfügbare Breite, rowH die Zeilenhöhe.
     */
    public abstract AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen);

    /** Wird nach einer Änderung aufgerufen (speichert die Config). */
    protected void changed() {
        ModuleManager.save();
    }
}
