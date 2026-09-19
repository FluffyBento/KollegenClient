package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;


public class GlassToggle extends AbstractWidget {
    private boolean state;
    private float animProgress = 0f;
    private final Consumer<Boolean> onChange;

    public GlassToggle(int x, int y, int w, int h, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, w, h, Component.empty());
        this.state = initial;
        this.animProgress = initial ? 1f : 0f;
        this.onChange = onChange;
    }

    public void setState(boolean s) {
        this.state = s;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean bl) {
        state = !state;
        onChange.accept(state);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        int r = height / 2;
        boolean hov = isMouseOver(mx, my);

        float target = state ? 1f : 0f;
        animProgress += (target - animProgress) * Math.min(1f, pt * 12f);

        int trackColor = state ? Palette.ACCENT : Palette.PANEL2;
        int borderColor = hov ? Palette.ACCENT : Palette.BORDER;

        int trackBg = Glass.mix(Palette.PANEL2, trackColor, animProgress);
        int trackBorder = Glass.mix(Palette.BORDER, trackColor, animProgress * 0.8f);

        Glass.fillRound(g, getX(), getY(), width, height, r, borderColor);
        Glass.fillRound(g, getX() + 1, getY() + 1, width - 2, height - 2, Math.max(0, r - 1), trackBg);

        int knob = height - 6;
        int startX = getX() + 3;
        int endX = getX() + width - height + 3;
        int kx = Glass.mix(startX, endX, Glass.smoothStep(animProgress));

        Glass.fillRound(g, kx, getY() + 3, knob, knob, knob / 2, 0xFFFFFFFF);
        Glass.fillRound(g, kx + 1, getY() + 4, knob - 2, knob - 2, Math.max(0, knob / 2 - 1),
                state ? Palette.ACCENT : Palette.MUTED);

        if (hov) {
            Glass.fillRound(g, getX() + 2, getY() + 2, width - 4, height - 4, r - 2, 0x1AFFFFFF);
        }
    }

    @Override
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    }
}