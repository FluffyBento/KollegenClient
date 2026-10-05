package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;


public class ColorButton extends Button {
    public int color;

    public ColorButton(int x, int y, int w, int h, int color, Runnable onClick) {
        super(x, y, w, h, Component.empty(), b -> onClick.run(), DEFAULT_NARRATION);
        this.color = color;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isMouseOver(mx, my);
        int r = height / 2;

        int borderCol = hov ? Palette.ACCENT : Palette.BORDER;
        Glass.fillRound(g, getX(), getY(), width, height, r, borderCol);
        Glass.fillRound(g, getX() + 1, getY() + 1, width - 2, height - 2, Math.max(0, r - 1),
                hov ? Palette.tint(Palette.PANEL2, 0xE0) : Palette.PANEL);

        int pad = 4;
        Glass.fillRound(g, getX() + pad, getY() + pad, width - pad * 2, height - pad * 2, Math.max(0, r - pad), color);
        Glass.fillRound(g, getX() + pad, getY() + pad, width - pad * 2, pad, Math.max(0, r - pad), 0x22000000);

        if (hov) {
            Glass.fillRound(g, getX() + 2, getY() + 2, width - 4, height - 4, r - 2, 0x1AFFFFFF);
        }
    }
}