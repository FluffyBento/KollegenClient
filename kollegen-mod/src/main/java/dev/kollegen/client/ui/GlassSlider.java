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
        boolean hov = isMouseOver(mx, my);

        Glass.sliderTrack(g, getX(), getY(), width, height, r,
                hov ? Glass.tint(Palette.PANEL2, 0xA0) : Glass.tint(Palette.BORDER, 0x80));

        int thumb = height + 4;
        int fx = getX() + (int) (this.value * (width - thumb));
        Glass.sliderThumb(g, fx, getY() - 2, thumb, thumb / 2, accent, hov);
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