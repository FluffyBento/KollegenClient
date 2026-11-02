package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class NumberSetting extends Setting {
    public int value;
    public final int min;
    public final int max;
    public final String suffix;
    private final Consumer<Integer> onChange;

    public NumberSetting(String name, String description, int def, int min, int max) {
        this(name, description, def, min, max, "", null);
    }

    public NumberSetting(String name, String description, int def, int min, int max, String suffix) {
        this(name, description, def, min, max, suffix, null);
    }

    public NumberSetting(String name, String description, int def, int min, int max, String suffix, Consumer<Integer> onChange) {
        super(name, description);
        this.value = Math.max(min, Math.min(max, def));
        this.min = min;
        this.max = max;
        this.suffix = suffix == null ? "" : suffix;
        this.onChange = onChange;
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("value", value);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("value")) {
            try {
                this.value = Math.max(min, Math.min(max, o.get("value").getAsInt()));
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public String valueText() {
        return value + suffix;
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int w = 72;
        int h = 20;
        int x = px + cw - w - 8;
        int y = py + (rowH - h) / 2;

        EditBox box = new EditBox(Minecraft.getInstance().font, x, y, w, h, Component.literal(""));
        box.setMaxLength(6);
        box.setValue(String.valueOf(value));
        box.setTextColor(Palette.TEXT);
        box.setBordered(true);
        box.setResponder(t -> {
            String digits = t.replaceAll("[^0-9]", "");
            if (!digits.equals(t)) {
                box.setValue(digits);
                return;
            }
            if (!digits.isEmpty()) {
                try {
                    int v = Integer.parseInt(digits);
                    if (v > max) {
                        v = max;
                        box.setValue(String.valueOf(v));
                    }
                    this.value = v;
                    if (onChange != null) onChange.accept(this.value);
                    changed();
                } catch (NumberFormatException ignored) {}
            }
        });
        return box;
    }
}
