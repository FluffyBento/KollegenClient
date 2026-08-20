package dev.kollegen.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Glas-Toggle (runder Schalter) als echtes Widget – damit die Eingabe über
 * Minecrafts Widget-System zuverlässig funktioniert.
 */
public class GlassToggle extends AbstractWidget {
    private boolean state;
    private final Consumer<Boolean> onChange;
    private int accent = 0xfff5a623;
    private int off = 0xff888888;

    public GlassToggle(int x, int y, int w, int h, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, w, h, Component.empty());
        this.state = initial;
        this.onChange = onChange;
    }

    public GlassToggle colors(int accent, int off) {
        this.accent = accent;
        this.off = off;
        return this;
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
        Glass.fillRound(g, getX(), getY(), width, height, r,
                state ? Glass.tint(accent, 0xD8) : Glass.tint(off, 0x70));
        int kx = state ? (getX() + width - height + 3) : (getX() + 3);
        g.fill(kx, getY() + 3, kx + height - 6, getY() + height - 3, 0xffffffff);
    }

    @Override
    public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
    }
}
