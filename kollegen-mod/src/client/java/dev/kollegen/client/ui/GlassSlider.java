package dev.kollegen.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class GlassSlider extends AbstractSliderButton {
    private Consumer<Double> cb;

    public GlassSlider(int x, int y, int w, int h, double value) {
        super(x, y, w, h, (Component) Component.empty(), value);
    }

    public GlassSlider onChanged(Consumer<Double> c) {
        this.cb = c;
        return this;
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        boolean hovered = this.isMouseOver(mx, my);
        boolean dragging = this.isFocused();
        Glass.vanillaSlider(g, this.getX(), this.getY(), this.width, this.height, (float) this.value, hovered, dragging);
    }

    protected void updateMessage() {
    }

    protected void applyValue() {
        if (this.cb != null) {
            this.cb.accept(this.value);
        }
    }

    public void updateWidgetNarration(NarrationElementOutput output) {
    }
}
