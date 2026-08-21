package dev.kollegen.client.mods;

import dev.kollegen.client.mods.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Basis für HUD-Module: gemeinsame Position/Anker-Logik und Zeichen-Helfer.
 */
public abstract class HudModule extends Module {
    protected final ModeSetting position;
    protected final ColorSetting color;
    protected final BooleanSetting background;
    protected final ColorSetting backgroundColor;

    protected HudModule(String id, String name, String description) {
        super(id, name, description, Category.HUD);
        position = new ModeSetting("Position", "",
                new String[]{"Oben links", "Oben rechts", "Unten links", "Unten rechts"}, 0);
        color = new ColorSetting("Farbe", "", Palette.TEXT);
        background = new BooleanSetting("Hintergrund", "Zeichnet einen Hintergrund hinter dem Text.", true);
        backgroundColor = new ColorSetting("Hintergrundfarbe", "", Palette.tint(Palette.PANEL, 0xCC));
        add(position);
        add(color);
        add(background);
        add(backgroundColor);
    }

    protected int[] anchor(int screenW, int screenH, int panelW, int panelH) {
        int m = 6;
        return switch (position.index) {
            case 1 -> new int[]{screenW - panelW - m, m};
            case 2 -> new int[]{m, screenH - panelH - m};
            case 3 -> new int[]{screenW - panelW - m, screenH - panelH - m};
            default -> new int[]{m, m};
        };
    }

    protected void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (background.value) {
            dev.kollegen.client.ui.Glass.fillRound(g, x - 5, y - 5, w + 10, h + 10, 6, backgroundColor.value);
        }
    }

    protected void text(GuiGraphics g, String s, int x, int y) {
        g.drawString(mc.font, s, x, y, color.value, true);
    }

    protected int lineWidth(List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        return w;
    }

    protected void renderLines(GuiGraphics g, List<String> lines, int x, int y) {
        int w = lineWidth(lines);
        int h = lines.size() * (mc.font.lineHeight + 3);
        int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
        panel(g, a[0], a[1], w, h);
        int yy = a[1];
        for (String l : lines) {
            text(g, l, a[0], yy);
            yy += mc.font.lineHeight + 3;
        }
    }
}
