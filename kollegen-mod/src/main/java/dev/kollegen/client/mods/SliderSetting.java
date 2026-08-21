package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import dev.kollegen.client.ui.GlassSlider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

public class SliderSetting extends Setting {
    public double value;
    public final double min;
    public final double max;
    public final double step;
    private final java.util.function.Function<Double, String> format;
    private final java.util.function.Consumer<Double> onChange;

    public SliderSetting(String name, String description, double def, double min, double max, double step) {
        this(name, description, def, min, max, step, v -> String.valueOf(Math.round(v * 100) / 100.0), null);
    }

    public SliderSetting(String name, String description, double def, double min, double max, double step,
                         java.util.function.Function<Double, String> format, java.util.function.Consumer<Double> onChange) {
        super(name, description);
        this.value = def;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.onChange = onChange;
    }

    private double clamp(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) v = Math.round(v / step) * step;
        return v;
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("value", value);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("value")) value = o.get("value").getAsDouble();
    }

    @Override
    public String valueText() {
        return format == null ? String.valueOf(Math.round(value * 100) / 100.0) : format.apply(value);
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int w = Math.min(220, cw - 70);
        int h = 18;
        int x = px + cw - w - 64;
        int y = py + (rowH - h) / 2;
        double norm = (value - min) / (max - min);
        GlassSlider s = new GlassSlider(x, y, w, h, Math.max(0, Math.min(1, norm)));
        s.accent(Palette.ACCENT).onChanged(d -> {
            value = clamp(min + d * (max - min));
            if (onChange != null) onChange.accept(value);
            changed();
        });
        return s;
    }
}
