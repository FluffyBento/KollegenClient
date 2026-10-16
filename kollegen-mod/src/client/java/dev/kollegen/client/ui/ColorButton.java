package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ColorButton extends Button {
    public int color;
    private float hov;

    public ColorButton(int x, int y, int w, int h, int color, Runnable onClick) {
        super(x, y, w, h, (Component) Component.empty(), b -> onClick.run(), DEFAULT_NARRATION);
        this.color = color;
    }

    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.hov = Glass.step(this.hov, this.isMouseOver(mx, my) ? 1.0f : 0.0f, 14.0f);
        float x = this.getX();
        float y = this.getY();
        float r = this.height / 2.0f;
        Glass.rr(g, x, y, this.width, this.height, r, Glass.a(Palette.PANEL2, 0.8f));
        Glass.ring(g, x, y, this.width, this.height, r, 0.5f, Glass.mixA(0x38FFFFFF, Glass.a(Palette.ACCENT, 0.95f), this.hov));
        float pad = 3.0f;
        Glass.rr(g, x + pad, y + pad, this.width - pad * 2, this.height - pad * 2, r - pad, 0xFF000000 | this.color);
        Glass.rrGrad(g, x + pad, y + pad, this.width - pad * 2, (this.height - pad * 2) * 0.5f, r - pad, 0x40FFFFFF, 0x00FFFFFF);
    }
}
