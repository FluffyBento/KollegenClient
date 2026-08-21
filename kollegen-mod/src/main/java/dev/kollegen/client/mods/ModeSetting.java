package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModeSetting extends Setting {
    public final String[] options;
    public int index;
    private final java.util.function.Consumer<Integer> onChange;

    public ModeSetting(String name, String description, String[] options, int def) {
        this(name, description, options, def, null);
    }

    public ModeSetting(String name, String description, String[] options, int def,
                       java.util.function.Consumer<Integer> onChange) {
        super(name, description);
        this.options = options;
        this.index = Math.max(0, Math.min(options.length - 1, def));
        this.onChange = onChange;
    }

    public String current() {
        return options[index];
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("index", index);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("index")) index = Math.max(0, Math.min(options.length - 1, o.get("index").getAsInt()));
    }

    @Override
    public String valueText() {
        return current();
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int w = Math.min(150, cw - 70);
        int h = 24;
        int x = px + cw - w - 8;
        int y = py + (rowH - h) / 2;
        Button b = Button.builder(Component.literal(current()), btn -> {
            index = (index + 1) % options.length;
            btn.setMessage(Component.literal(current()));
            if (onChange != null) onChange.accept(index);
            changed();
        }).bounds(x, y, w, h).build();
        return b;
    }
}
