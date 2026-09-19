package dev.kollegen.client.menu;

import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.GlassSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final ColorSetting setting;

    private float h = 0, s = 0, v = 1;
    private int current;

    private GlassSlider sh, ss, sv;
    private GlassButton doneBtn;

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
        int w = 400, hgt = 320;
        int x = (this.width - w) / 2;
        int y = (this.height - hgt) / 2;

        sh = new GlassSlider(x + 28, y + 95, w - 56, 22, h).accent(Palette.ACCENT)
                .onChanged(d -> { h = d.floatValue(); update(); });
        ss = new GlassSlider(x + 28, y + 155, w - 56, 22, s).accent(Palette.ACCENT)
                .onChanged(d -> { s = d.floatValue(); update(); });
        sv = new GlassSlider(x + 28, y + 215, w - 56, 22, v).accent(Palette.ACCENT)
                .onChanged(d -> { v = d.floatValue(); update(); });
        addRenderableWidget(sh);
        addRenderableWidget(ss);
        addRenderableWidget(sv);

        doneBtn = new GlassButton(x + w - 110, y + hgt - 46, 90, 32, Component.literal("Fertig"), btn -> close());
        addRenderableWidget(doneBtn);
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

        int w = 400, hgt = 320;
        int x = (this.width - w) / 2;
        int y = (this.height - hgt) / 2;

        Glass.dropShadow(g, x, y, w, hgt, 16, 6, 14);
        Glass.panelVanilla(g, x, y, w, hgt, 16);

        g.drawString(this.font, "Farbe wählen", x + (w - this.font.width("Farbe wählen")) / 2, y + 22, Palette.TEXT, false);

        int hueX = x + 28;
        int hueY = y + 75;
        int hueW = w - 56;
        int hueH = 22;
        for (int i = 0; i < hueW; i++) {
            float hue = (float) i / hueW;
            int col = hsvToRgb(hue, 1, 1);
            Glass.fillRound(g, hueX + i, hueY, 1, hueH, 2, col);
        }
        g.drawString(this.font, "Farbton", hueX, hueY - 18, Palette.MUTED, false);

        int satX = x + 28;
        int satY = y + 140;
        int satW = w - 56;
        int satH = 22;
        for (int i = 0; i < satW; i++) {
            float sat = (float) i / satW;
            int col = hsvToRgb(h, sat, 1);
            Glass.fillRound(g, satX + i, satY, 1, satH, 2, col);
        }
        g.drawString(this.font, "Sättigung", satX, satY - 18, Palette.MUTED, false);

        int valX = x + 28;
        int valY = y + 205;
        int valW = w - 56;
        int valH = 22;
        for (int i = 0; i < valW; i++) {
            float val = (float) i / valW;
            int col = hsvToRgb(h, s, val);
            Glass.fillRound(g, valX + i, valY, 1, valH, 2, col);
        }
        g.drawString(this.font, "Helligkeit", valX, valY - 18, Palette.MUTED, false);

        int previewX = x + 28;
        int previewY = y + hgt - 68;
        int previewW = 64;
        int previewH = 36;
        Glass.fillRound(g, previewX, previewY, previewW, previewH, 8, current);
        Glass.fillRound(g, previewX + 1, previewY + 1, previewW - 2, previewH - 2, 7, current);
        g.drawString(this.font, "#" + Integer.toHexString(current & 0xFFFFFF).toUpperCase(),
                previewX + previewW + 14, previewY + (previewH - this.font.lineHeight) / 2, Palette.MUTED, false);

        int rgbX = x + w - 180;
        int rgbY = y + hgt - 68;
        int r = (current >> 16) & 0xFF;
        int gr = (current >> 8) & 0xFF;
        int b = current & 0xFF;
        g.drawString(this.font, "RGB: " + r + ", " + gr + ", " + b, rgbX, rgbY, Palette.MUTED, false);
        g.drawString(this.font, "HSV: " + Math.round(h * 360) + "°, " + Math.round(s * 100) + "%, " + Math.round(v * 100) + "%", rgbX, rgbY + this.font.lineHeight + 4, Palette.MUTED, false);

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