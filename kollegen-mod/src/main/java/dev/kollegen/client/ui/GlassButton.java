package dev.kollegen.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;


public class GlassButton extends Button {
    public GlassButton(int x, int y, int w, int h, Component msg, OnPress p) {
        super(x, y, w, h, msg, p, DEFAULT_NARRATION);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hovered = isMouseOver(mx, my) && active;
        Glass.vanillaButton(g, getX(), getY(), width, height,
                Minecraft.getInstance().font, getMessage().getString(),
                hovered, selected, !active);
    }
}