package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;


public class GlassSlider extends AbstractSliderButton {
    private int accent = Palette.ACCENT;
    private Consumer<Double> cb;

    public GlassSlider(int x, int y, int w, int h, double value) {
        super(x, y, w, h, Component.empty(), value);
    }

    public GlassSlider onChanged(Consumer<Double> c) {
        this.cb = c;
        return this;
    }

    public GlassSlider accent(int a) {
        this.accent = a;
        return this;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        int r = height / 2;
        boolean hov = isMouseOver(mx, my) || isFocused();

        int trackBg = hov ? Palette.tint(Palette.PANEL2, 0xC0) : Palette.tint(Palette.BORDER, 0xA0);
        Glass.fillRound(g, getX(), getY(), width, height, r, trackBg);

        int trackFillW = (int) (this.value * (width - 4));
        if (trackFillW > 0) {
            Glass.fillRound(g, getX() + 2, getY() + 2, trackFillW, height - 4, r - 2, accent);
        }

        int thumb = height + 6;
        int fx = getX() + 2 + (int) (this.value * (width - thumb - 2));
        int fy = getY() - 3;

        Glass.fillRound(g, fx, fy, thumb, thumb, thumb / 2, 0xFFFFFFFF);
        Glass.fillRound(g, fx + 1, fy + 1, thumb - 2, thumb - 2, thumb / 2 - 1, accent);

        if (hov) {
            Glass.fillRound(g, fx - 1, fy - 1, thumb + 2, thumb + 2, thumb / 2 + 1, 0x1AFFFFFF);
        }
    }

    @Override
    protected void updateMessage() {
    }

    @Override
    protected void applyValue() {
        if (cb != null) cb.accept(this.value);
    }

    @Override
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    }
}