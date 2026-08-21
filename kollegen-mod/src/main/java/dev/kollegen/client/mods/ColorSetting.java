package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import dev.kollegen.client.ui.ColorButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

public class ColorSetting extends Setting {
    public int value; // 0xAARRGGBB

    public ColorSetting(String name, String description, int def) {
        super(name, description);
        this.value = def;
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("value", value);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("value")) value = o.get("value").getAsInt();
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int h = 26;
        int w = 52;
        int x = px + cw - w - 8;
        int y = py + (rowH - h) / 2;
        return new ColorButton(x, y, w, h, value, () -> {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new dev.kollegen.client.menu.ColorPickerScreen(screen, this));
        });
    }
}
