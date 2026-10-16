package dev.kollegen.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class GlassButton extends Button {
    private int variant = 0;
    private float hov;

    public GlassButton(int x, int y, int w, int h, Component msg, Button.OnPress p) {
        super(x, y, w, h, msg, p, DEFAULT_NARRATION);
    }

    public GlassButton variant(int v) {
        this.variant = v;
        return this;
    }

    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        boolean hovered = this.isMouseOver(mx, my) && this.active;
        this.hov = Glass.step(this.hov, hovered ? 1.0f : 0.0f, 14.0f);
        float h = this.height;
        float r = Math.min(h / 2.0f, 9.0f);
        boolean primary = this.variant == 1;
        Glass.pill(g, this.getX(), this.getY(), this.width, h, this.hov, primary, !this.active);
        String label = this.getMessage().getString();
        int tw = Minecraft.getInstance().font.width(label);
        String shown = label;
        if (tw > this.width - 8) {
            shown = Glass.trunc(Minecraft.getInstance().font, label, this.width - 8);
        }
        Glass.textC(g, Minecraft.getInstance().font, shown, this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, !this.active ? dev.kollegen.client.mods.Palette.MUTED : (primary ? 0xFFFFFFFF : dev.kollegen.client.mods.Palette.TEXT), false);
    }
}
