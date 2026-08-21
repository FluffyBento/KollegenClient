package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import dev.kollegen.client.ui.GlassToggle;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

public class BooleanSetting extends Setting {
    public boolean value;
    private final Consumer<Boolean> onChange;

    public BooleanSetting(String name, String description, boolean def) {
        this(name, description, def, null);
    }

    public BooleanSetting(String name, String description, boolean def, Consumer<Boolean> onChange) {
        super(name, description);
        this.value = def;
        this.onChange = onChange;
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("value", value);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("value")) value = o.get("value").getAsBoolean();
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int w = 52, h = 28;
        int x = px + cw - w;
        int y = py + (rowH - h) / 2;
        GlassToggle t = new GlassToggle(x, y, w, h, value, on -> {
            value = on;
            if (onChange != null) onChange.accept(on);
            changed();
        });
        t.colors(Palette.ACCENT, Palette.MUTED);
        return t;
    }
}
