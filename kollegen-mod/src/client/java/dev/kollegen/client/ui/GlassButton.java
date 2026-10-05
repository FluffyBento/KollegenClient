package dev.kollegen.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;


public class GlassButton extends Button {
    private int variant = 0; // 0=primary, 1=outline, 2=danger

    public GlassButton(int x, int y, int w, int h, Component msg, OnPress p) {
        super(x, y, w, h, msg, p, DEFAULT_NARRATION);
    }

    public GlassButton variant(int v) {
        this.variant = v;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float pt) {
        boolean hovered = isMouseOver(mx, my) && active;
        Glass.vanillaButton(g, getX(), getY(), width, height,
                Minecraft.getInstance().font, getMessage().getString(),
                hovered, false, !active);
    }
}