package dev.kollegen.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class GlassButton extends Button {
    private boolean selected = false;
    private boolean disabled = false;

    public GlassButton(int x, int y, int w, int h, Component msg, OnPress p) {
        super(x, y, w, h, msg, p, DEFAULT_NARRATION);
    }

    public GlassButton selected(boolean s) {
        this.selected = s;
        return this;
    }

    public GlassButton disabled(boolean d) {
        this.disabled = d;
        this.active = !d;
        return this;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isMouseOver(mx, my) && !disabled;
        Glass.buttonVanilla(g, getX(), getY(), width, height, 8,
                Minecraft.getInstance().font, getMessage().getString(), hov, selected, disabled);
    }
}