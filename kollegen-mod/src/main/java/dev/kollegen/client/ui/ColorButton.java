package dev.kollegen.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Farb-Schalter: zeigt die aktuelle Farbe als Kachel und öffnet bei Klick den
 * ColorPicker. (Eigenständiges Widget, da der Standard-Button nur Text malen kann.)
 */
public class ColorButton extends Button {
    public int color;

    public ColorButton(int x, int y, int w, int h, int color, Runnable onClick) {
        super(x, y, w, h, Component.empty(), b -> onClick.run(), DEFAULT_NARRATION);
        this.color = color;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isMouseOver(mx, my);
        Glass.fillRound(g, getX(), getY(), width, height, 6,
                Glass.tint(hov ? 0xffffff : 0x000000, hov ? 0x33 : 0x22));
        int pad = 3;
        g.fill(getX() + pad, getY() + pad, getX() + width - pad, getY() + height - pad, color);
        g.fill(getX() + pad, getY() + pad, getX() + width - pad, getY() + pad + 1, 0x22000000);
    }
}
