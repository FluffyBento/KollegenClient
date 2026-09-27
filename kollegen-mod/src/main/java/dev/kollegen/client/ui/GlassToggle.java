package dev.kollegen.client.ui;

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
        Glass.vanillaCheckbox(g, getX(), getY(), height, state, isMouseOver(mx, my), isFocused());
    }

    @Override
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    }
}