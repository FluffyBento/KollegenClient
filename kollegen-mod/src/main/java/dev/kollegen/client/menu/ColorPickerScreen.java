package dev.kollegen.client.menu;

import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
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
    private Button doneBtn;

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
        int w = 380, hgt = 280;
        int x = (this.width - w) / 2;
        int y = (this.height - hgt) / 2;

        sh = new GlassSlider(x + 24, y + 80, w - 48, 20, h).accent(Palette.ACCENT)
                .onChanged(d -> {
                    h = d.floatValue();
                    update();
                });
        ss = new GlassSlider(x + 24, y + 135, w - 48, 20, s).accent(Palette.ACCENT)
                .onChanged(d -> {
                    s = d.floatValue();
                    update();
                });
        sv = new GlassSlider(x + 24, y + 190, w - 48, 20, v).accent(Palette.ACCENT)
                .onChanged(d -> {
                    v = d.floatValue();
                    update();
                });
        addRenderableWidget(sh);
        addRenderableWidget(ss);
        addRenderableWidget(sv);

        doneBtn = Button.builder(Component.literal("Fertig"), btn -> close())
                .bounds(x + w - 110, y + hgt - 40, 90, 30).build();
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

        int w = 380, hgt = 280;
        int x = (this.width - w) / 2;
        int y = (this.height - hgt) / 2;

        Glass.dropShadow(g, x, y, w, hgt, 14, 4, 8);
        Glass.panelVanilla(g, x, y, w, hgt, 14);

        g.drawString(this.font, "Farbe wählen", x + (w - this.font.width("Farbe wählen")) / 2, y + 20, Palette.TEXT, false);

        int hueX = x + 24;
        int hueY = y + 64;
        int hueW = w - 48;
        int hueH = 18;
        for (int i = 0; i < hueW; i++) {
            float hue = (float) i / hueW;
            int col = hsvToRgb(hue, 1, 1);
            Glass.fillRound(g, hueX + i, hueY, 1, hueH, 2, col);
        }
        g.drawString(this.font, "Farbton", hueX, hueY - 16, Palette.MUTED, false);

        int satX = x + 24;
        int satY = y + 119;
        int satW = w - 48;
        int satH = 18;
        for (int i = 0; i < satW; i++) {
            float sat = (float) i / satW;
            int col = hsvToRgb(h, sat, 1);
            Glass.fillRound(g, satX + i, satY, 1, satH, 2, col);
        }
        g.drawString(this.font, "Sättigung", satX, satY - 16, Palette.MUTED, false);

        int valX = x + 24;
        int valY = y + 174;
        int valW = w - 48;
        int valH = 18;
        for (int i = 0; i < valW; i++) {
            float val = (float) i / valW;
            int col = hsvToRgb(h, s, val);
            Glass.fillRound(g, valX + i, valY, 1, valH, 2, col);
        }
        g.drawString(this.font, "Helligkeit", valX, valY - 16, Palette.MUTED, false);

        int previewX = x + 24;
        int previewY = y + hgt - 56;
        int previewW = 48;
        int previewH = 28;
        Glass.fillRound(g, previewX, previewY, previewW, previewH, 6, current);
        Glass.fillRound(g, previewX + 1, previewY + 1, previewW - 2, previewH - 2, 5, current);
        g.drawString(this.font, "#" + Integer.toHexString(current & 0xFFFFFF).toUpperCase(),
                previewX + previewW + 10, previewY + (previewH - this.font.lineHeight) / 2, Palette.MUTED, false);

        int rgbX = x + w - 160;
        int rgbY = y + hgt - 56;
        int r = (current >> 16) & 0xFF;
        int gr = (current >> 8) & 0xFF;
        int b = current & 0xFF;
        g.drawString(this.font, "R: " + r + "  G: " + gr + "  B: " + b,
                rgbX, rgbY, Palette.MUTED, false);
        g.drawString(this.font, "H: " + Math.round(h * 360) + "°  S: " + Math.round(s * 100) + "%  V: " + Math.round(v * 100) + "%",
                rgbX, rgbY + this.font.lineHeight + 2, Palette.MUTED, false);

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