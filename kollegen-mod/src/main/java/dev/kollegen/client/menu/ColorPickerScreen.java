package dev.kollegen.client.menu;

import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final ColorSetting setting;

    private float h = 0, s = 0, v = 1;
    private int current;

    private GlassSlider sh, ss, sv;
    private AbstractWidget preview;

    public ColorPickerScreen(Screen parent, ColorSetting setting) {
        super(Component.literal("Farbe wählen"));
        this.parent = parent;
        this.setting = setting;
        int c = setting.value;
        float[] hsv = rgbToHsv((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        current = c;
    }

    @Override
    protected void init() {
        int w = 360, hgt = 240;
        int x = (this.width - w) / 2;
        int y = (this.height - hgt) / 2;

        sh = new GlassSlider(x + 20, y + 60, w - 40, 18, h).accent(Palette.ACCENT)
                .onChanged(d -> {
                    h = d.floatValue();
                    update();
                });
        ss = new GlassSlider(x + 20, y + 110, w - 40, 18, s).accent(Palette.ACCENT)
                .onChanged(d -> {
                    s = d.floatValue();
                    update();
                });
        sv = new GlassSlider(x + 20, y + 160, w - 40, 18, v).accent(Palette.ACCENT)
                .onChanged(d -> {
                    v = d.floatValue();
                    update();
                });
        addRenderableWidget(sh);
        addRenderableWidget(ss);
        addRenderableWidget(sv);

        preview = Button.builder(Component.literal("Fertig"), btn -> close())
                .bounds(x + w - 110, y + hgt - 36, 90, 26).build();
        addRenderableWidget(preview);
    }

    private void update() {
        current = hsvToRgb(h, s, v);
        setting.value = current;
        dev.kollegen.client.mods.ModuleManager.save();
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));
        int w = 360, hgt = 240;
        int x = (this.width - w) / 2;
        int y = (this.height - hgt) / 2;
        Glass.fillRound(g, x, y, w, hgt, 14, Palette.PANEL);
        g.drawString(this.font, "Farbe wählen", x + 20, y + 22, Palette.TEXT, false);
        g.drawString(this.font, "Farbton", x + 20, y + 46, Palette.MUTED, false);
        g.drawString(this.font, "Sättigung", x + 20, y + 96, Palette.MUTED, false);
        g.drawString(this.font, "Helligkeit", x + 20, y + 146, Palette.MUTED, false);
        // Vorschau
        g.fill(x + 20, y + hgt - 40, 40, 26, current);
        g.fill(x + 20, y + hgt - 40, 40, 1, 0x22000000);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float hh = 0;
        if (d != 0) {
            if (max == rf) hh = ((gf - bf) / d) % 6;
            else if (max == gf) hh = (bf - rf) / d + 2;
            else hh = (rf - gf) / d + 4;
            hh *= 60;
            if (hh < 0) hh += 360;
        }
        float ss = max == 0 ? 0 : d / max;
        return new float[]{(hh % 360) / 360f, ss, max};
    }

    private static int hsvToRgb(float hh, float ss, float vv) {
        int h = (int) (hh * 360);
        float c = vv * ss;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = vv - c;
        float r = 0, g = 0, b = 0;
        if (h < 60) { r = c; g = x; }
        else if (h < 120) { r = x; g = c; }
        else if (h < 180) { g = c; b = x; }
        else if (h < 240) { g = x; b = c; }
        else if (h < 300) { r = x; b = c; }
        else { r = c; b = x; }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
