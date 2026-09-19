package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;


public class GlassButton extends Button {
    private boolean selected = false;
    private boolean disabled = false;
    private int variant = 0;

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

    public GlassButton variant(int v) {
        this.variant = v;
        return this;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        boolean hov = isMouseOver(mx, my) && !disabled;
        int r = height / 2;

        int bgColor, borderColor, textColor;

        if (disabled) {
            bgColor = Palette.tint(Palette.PANEL2, 0x80);
            borderColor = Palette.tint(Palette.BORDER, 0x80);
            textColor = Palette.MUTED;
        } else if (variant == 1) {
            if (selected) {
                bgColor = Palette.ACCENT;
                borderColor = Palette.ACCENT;
                textColor = 0xFFFFFFFF;
            } else if (hov) {
                bgColor = Palette.tint(Palette.ACCENT, 0x30);
                borderColor = Palette.tint(Palette.ACCENT, 0x80);
                textColor = Palette.ACCENT;
            } else {
                bgColor = Palette.tint(Palette.PANEL2, 0x80);
                borderColor = Palette.tint(Palette.ACCENT, 0x50);
                textColor = Palette.ACCENT;
            }
        } else if (variant == 2) {
            if (selected) {
                bgColor = Palette.DANGER;
                borderColor = Palette.DANGER;
                textColor = 0xFFFFFFFF;
            } else if (hov) {
                bgColor = Palette.tint(Palette.DANGER, 0x30);
                borderColor = Palette.tint(Palette.DANGER, 0x80);
                textColor = Palette.DANGER;
            } else {
                bgColor = Palette.tint(Palette.PANEL2, 0x80);
                borderColor = Palette.tint(Palette.DANGER, 0x50);
                textColor = Palette.DANGER;
            }
        } else {
            if (selected) {
                bgColor = Palette.ACCENT;
                borderColor = Palette.ACCENT;
                textColor = 0xFFFFFFFF;
            } else if (hov) {
                bgColor = Palette.tint(Palette.PANEL2, 0xE0);
                borderColor = Palette.ACCENT;
                textColor = Palette.TEXT;
            } else {
                bgColor = Palette.PANEL2;
                borderColor = Palette.BORDER;
                textColor = Palette.TEXT;
            }
        }

        Glass.fillRound(g, getX(), getY(), width, height, height / 2, borderColor);
        Glass.fillRound(g, getX() + 1, getY() + 1, width - 2, height - 2, Math.max(0, height / 2 - 1), bgColor);

        String label = getMessage().getString();
        int tw = Minecraft.getInstance().font.width(label);
        g.drawString(Minecraft.getInstance().font, label,
                getX() + (width - tw) / 2,
                getY() + (height - Minecraft.getInstance().font.lineHeight) / 2,
                textColor, false);
    }
}