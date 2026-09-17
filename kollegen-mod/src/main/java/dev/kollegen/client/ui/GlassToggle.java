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
    private final Consumer<Boolean> onChange;

    public GlassToggle(int x, int y, int w, int h, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, w, h, Component.empty());
        this.state = initial;
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

        int trackColor = state ? Palette.ACCENT : Palette.PANEL2;
        int borderColor = hov ? Palette.ACCENT : Palette.BORDER;
        Glass.fillRound(g, getX(), getY(), width, height, r,
                state ? Glass.tint(trackColor, 0xD8) : Glass.tint(trackColor, 0x70));
        Glass.fillRound(g, getX() + 1, getY() + 1, width - 2, height - 2, Math.max(0, r - 1),
                state ? Glass.tint(trackColor, hov ? 0xE0 : 0xC8) : Glass.tint(Palette.PANEL2, hov ? 0x90 : 0x70));

        int knob = height - 6;
        int kx = state ? (getX() + width - height + 3) : (getX() + 3);
        Glass.fillRound(g, kx, getY() + 3, knob, knob, knob / 2, 0xFFFFFFFF);
        Glass.fillRound(g, kx + 1, getY() + 4, knob - 2, knob - 2, Math.max(0, knob / 2 - 1),
                state ? Palette.ACCENT : Palette.MUTED);
    }

    @Override
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    }
}