package dev.kollegen.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Glas-Button, der {@link Button} erweitert, damit Klick-Eingabe über Vanilla läuft.
 */
public class GlassButton extends Button {
    private int panel = 0xff1a1a24;
    private int accent = 0xfff5a623;
    private int text = 0xfff3e9d8;
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

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isMouseOver(mx, my);
        int fill = selected ? accent : panel;
        int txt = selected ? 0xffffffff : text;
        Glass.button(g, getX(), getY(), width, height, 9,
                Glass.tint(fill, selected ? 0xE6 : (hov ? 0xE0 : 0xC8)),
                Glass.tint(accent, hov ? 0xD0 : 0xB0),
                txt, Minecraft.getInstance().font, getMessage().getString(), hov, selected);
    }
}
