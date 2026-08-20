package dev.kollegen.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Glas-Slider (Wert 0..1) als echtes {@link AbstractSliderButton}-Widget.
 * Die Eingabe (Klick/Drag) wird vom Vanilla-Slider übernommen, wir malen nur um.
 */
public class GlassSlider extends AbstractSliderButton {
    private int accent = 0xfff5a623;
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
        Glass.fillRound(g, getX(), getY(), width, height, r, 0x802b2b34);
        int fx = getX() + (int) (this.value * (width - 10));
        Glass.fillRound(g, fx, getY() + 1, 10, height - 2, (height - 2) / 2, Glass.tint(accent, 0xF0));
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
