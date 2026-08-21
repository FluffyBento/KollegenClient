package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Glas-Button, der {@link Button} erweitert, damit Klick-Eingabe über Vanilla läuft.
 * Text wird mit der glatten (TTF-)Schrift gerendert, nicht mit der pixeligen Bitmap-Schrift.
 */
public class GlassButton extends Button {
    private int panel = Palette.PANEL2;
    private int accent = Palette.ACCENT;
    private int text = Palette.TEXT;
    private boolean selected = false;

    public GlassButton(int x, int y, int w, int h, Component msg, OnPress p) {
        super(x, y, w, h, msg, p, DEFAULT_NARRATION);
    }

    public GlassButton colors(int panel, int accent, int text) {
        this.panel = panel;
        this.accent = accent;
        this.text = text;
        return this;
    }

    public GlassButton selected(boolean s) {
        this.selected = s;
        return this;
    }

    private static Font SMOOTH;

    public static Font smoothFont() {
        if (SMOOTH != null) return SMOOTH;
        // Glatte (TTF-)Standardschrift von Minecraft – exakt wie im HUD, damit
        // der Text nicht pixelig (Bitmap-Font) aussieht. Die vorige Reflection-
        // Lösung lieferte teils die Bitmap-Variante zurück und wirkte daher kantig.
        SMOOTH = Minecraft.getInstance().font;
        return SMOOTH;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isMouseOver(mx, my);
        int fill = selected ? accent : panel;
        int txt = selected ? 0xffffffff : text;
        Glass.button(g, getX(), getY(), width, height, 13,
                Glass.tint(fill, selected ? 0xE6 : (hov ? 0xE0 : 0xC8)),
                Glass.tint(accent, hov ? 0xD0 : 0xB0),
                txt, smoothFont(), getMessage().getString(), hov, selected);
    }

    /** Hilfs-X für den Schließen-Button (gezeichnet, nicht als Glyph). */
    public static void drawClose(GuiGraphics g, int x, int y, int s, int color) {
        Glass.line(g, x, y, x + s, y + s, color);
        Glass.line(g, x + 1, y, x + s, y + s - 1, color);
        Glass.line(g, x + s, y, x, y + s, color);
        Glass.line(g, x + s - 1, y, x, y + s - 1, color);
    }

    /** Kleiner Chevron (auf/zu) – für Aufklapp-Indikator. */
    public static void drawChevron(GuiGraphics g, int cx, int cy, int s, boolean down, int color) {
        if (down) {
            Glass.line(g, cx - s, cy - s / 2, cx, cy + s / 2, color);
            Glass.line(g, cx + s, cy - s / 2, cx, cy + s / 2, color);
        } else {
            Glass.line(g, cx - s / 2, cy - s, cx + s / 2, cy, color);
            Glass.line(g, cx - s / 2, cy + s, cx + s / 2, cy, color);
        }
    }
}
