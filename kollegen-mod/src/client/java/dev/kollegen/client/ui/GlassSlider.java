package dev.kollegen.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;


public class GlassSlider extends AbstractSliderButton {
    private Consumer<Double> cb;

    public GlassSlider(int x, int y, int w, int h, double value) {
        super(x, y, w, h, Component.empty(), value);
    }

    public GlassSlider onChanged(Consumer<Double> c) {
        this.cb = c;
        return this;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        boolean hovered = isMouseOver(mx, my);
        boolean dragging = isFocused();
        Glass.vanillaSlider(g, getX(), getY(), width, height, (float) value, hovered, dragging);
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