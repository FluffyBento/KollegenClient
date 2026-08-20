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

    public GlassButton(int x, int y, int w, int h, Component msg, OnPress p) {
        super(x, y, w, h, msg, p, DEFAULT_NARRATION);
    }

    public GlassButton colors(int panel, int accent, int text) {
        this.panel = panel;
        this.accent = accent;
        this.text = text;
        return this;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isMouseOver(mx, my);
        Glass.button(g, getX(), getY(), width, height, 9,
                Glass.tint(panel, hov ? 0xE0 : 0xC8),
                Glass.tint(accent, hov ? 0xD0 : 0xB0),
                text, Minecraft.getInstance().font, getMessage().getString(), hov, false);
    }
}
