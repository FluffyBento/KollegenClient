package dev.kollegen.client.mods;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.Palette;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Freitext-Einstellung (z. B. Nachrichten-Vorlagen für AutoText). Baut ein
 * EditBox-Widget, das den Wert live speichert.
 */
public class StringSetting extends Setting {
    public String value;
    private final int maxLength;

    public StringSetting(String name, String description, String def) {
        this(name, description, def, 128);
    }

    public StringSetting(String name, String description, String def, int maxLength) {
        super(name, description);
        this.value = def == null ? "" : def;
        this.maxLength = maxLength;
    }

    @Override
    public void save(JsonObject o) {
        o.addProperty("value", value == null ? "" : value);
    }

    @Override
    public void load(JsonObject o) {
        if (o.has("value")) value = o.get("value").getAsString();
    }

    @Override
    public String valueText() {
        return ""; // der Wert wird bereits im EditBox angezeigt
    }

    @Override
    public AbstractWidget buildWidget(int px, int py, int cw, int rowH, Screen screen) {
        int w = Math.max(80, cw - 70);
        int h = 20;
        int x = px + cw - w - 8;
        int y = py + (rowH - h) / 2;
        EditBox box = new EditBox(screen.font, x, y, w, h, Component.literal(""));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        String hint = description;
        if (hint.length() > 22) hint = hint.substring(0, 21) + "…";
        box.setHint(Component.literal(hint));
        box.setTextColor(Palette.TEXT);
        box.setResponder(t -> {
            value = t;
            changed();
        });
        return box;
    }
}
