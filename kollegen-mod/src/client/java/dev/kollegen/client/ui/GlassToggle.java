package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class GlassToggle extends AbstractWidget {
    private boolean state;
    private final Consumer<Boolean> onChange;
    private float anim;
    private float hov;
    private final int bw;

    public GlassToggle(int x, int y, int w, int h, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, w, h, (Component) Component.empty());
        this.state = initial;
        this.onChange = onChange;
        this.anim = initial ? 1.0f : 0.0f;
        this.bw = w;
    }

    public void setState(boolean s) {
        this.state = s;
    }

    public void onClick(MouseButtonEvent event, boolean bl) {
        this.state = !this.state;
        this.onChange.accept(this.state);
    }

    public static void drawSwitch(GuiGraphicsExtractor g, float x, float y, float w, float h, float t, float hov) {
        float r = h / 2.0f;
        int off = Glass.a(Palette.PANEL2, 0.9f);
        int offTop = Glass.mix(off, 0xFFFFFFFF, 0.08f);
        Glass.rrGrad(g, x, y, w, h, r, Glass.mixA(offTop, Glass.a(Palette.ACCENT, 0.95f), t), Glass.mixA(off, Glass.a(Palette.ACCENT2, 0.95f), t));
        Glass.ring(g, x, y, w, h, r, 0.5f, Glass.mixA(0x38FFFFFF, 0x66FFFFFF, t));
        float kr = r - 2.0f;
        float kx = x + 2.0f + kr + (w - 4.0f - kr * 2) * t;
        // PERF: Knopf-Schatten weggelassen (4 weiche Layer pro Switch pro Frame).
        Glass.disc(g, kx, y + r, kr + hov * 0.6f, 0xFFFFFFFF);
    }

    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.anim = Glass.step(this.anim, this.state ? 1.0f : 0.0f, 16.0f);
        this.hov = Glass.step(this.hov, this.isMouseOver(mx, my) ? 1.0f : 0.0f, 14.0f);
        float pw = Math.min(this.bw, 34.0f);
        float ph = Math.min(this.height, 18.0f);
        float px = this.getX() + this.bw - pw;
        float py = this.getY() + (this.height - ph) / 2.0f;
        drawSwitch(g, px, py, pw, ph, this.anim, this.hov);
    }

    public void updateWidgetNarration(NarrationElementOutput output) {
    }
}
