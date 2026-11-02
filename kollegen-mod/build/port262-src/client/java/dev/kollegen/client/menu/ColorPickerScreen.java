package dev.kollegen.client.menu;

import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final ColorSetting setting;
    private float h = 0.0f;
    private float s = 0.0f;
    private float v = 1.0f;
    private int current;
    private int dragBar = -1;
    private EditBox hex;
    private boolean syncing = false;
    private float openAnim = 0.0f;
    private final Map<String, Float> anims = new HashMap<String, Float>();
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int bx;
    private int bw;
    private int by0;

    public ColorPickerScreen(Screen parent, ColorSetting setting) {
        super((Component) Component.literal("Farbe wählen"));
        this.parent = parent;
        this.setting = setting;
        int c = setting.value;
        float[] hsv = rgbToHsv(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF);
        this.h = hsv[0];
        this.s = hsv[1];
        this.v = hsv[2];
        this.current = c;
    }

    protected void init() {
        this.pw = Math.min(this.width - 12, 340);
        this.ph = Math.min(this.height - 12, 230);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
        this.bx = this.px + 16;
        this.bw = this.pw - 32;
        this.by0 = this.py + 56;
        this.hex = new EditBox(this.font, this.px + 16 + 62, this.py + this.ph - 34, 60, 12, (Component) Component.literal(""));
        this.hex.setBordered(false);
        this.hex.setMaxLength(7);
        this.hex.setTextColor(Palette.TEXT);
        this.hex.setValue(this.hexString());
        this.hex.setResponder(o -> {
            if (this.syncing) {
                return;
            }
            String t = ((String) o).trim();
            if (t.startsWith("#")) {
                t = t.substring(1);
            }
            if (t.length() == 6) {
                try {
                    int c = 0xFF000000 | Integer.parseInt(t, 16);
                    float[] hsv = rgbToHsv(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF);
                    this.h = hsv[0];
                    this.s = hsv[1];
                    this.v = hsv[2];
                    this.current = c;
                    this.setting.value = c;
                    ModuleManager.save();
                } catch (Throwable ignored) {
                }
            }
        });
        this.addRenderableWidget(this.hex);
    }

    private String hexString() {
        return "#" + String.format("%06X", this.current & 0xFFFFFF);
    }

    private void update() {
        this.setting.value = this.current = hsvToRgb(this.h, this.s, this.v);
        ModuleManager.save();
        this.syncing = true;
        this.hex.setValue(this.hexString());
        this.syncing = false;
    }

    private void close() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float barY(int i) {
        return this.by0 + i * 34;
    }

    private void setBar(int i, double mx) {
        float t = (float) ((mx - this.bx) / this.bw);
        t = Math.max(0.0f, Math.min(1.0f, t));
        if (i == 0) {
            this.h = Math.min(0.9999f, t);
        } else if (i == 1) {
            this.s = t;
        } else {
            this.v = t;
        }
        this.update();
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < 3; i++) {
                if (hit(mx, my, this.bx - 6, this.barY(i) - 4, this.bw + 12, 22)) {
                    this.dragBar = i;
                    this.setBar(i, mx);
                    return true;
                }
            }
            float fw = 14 + this.font.width("Fertig") + 14;
            if (hit(mx, my, this.px + this.pw - 16 - fw, this.py + this.ph - 38, fw, 22)) {
                this.close();
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragBar >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.setBar(this.dragBar, event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragBar = -1;
        }
        return super.mouseReleased(event);
    }

    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            this.close();
            return true;
        }
        return super.keyPressed(ki);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private void drawBar(GuiGraphicsExtractor g, int i, String label, float value) {
        float y = this.barY(i);
        Glass.text(g, this.font, label, this.bx, Math.round(y - 3), Palette.MUTED, false);
        float by = y + 7;
        float bh = 10.0f;
        int n = Math.max(8, Math.min(120, this.bw / 2));
        float sw = (float) this.bw / n;
        int c0 = this.colorAt(i, 0.0f);
        int c1 = this.colorAt(i, 1.0f);
        Glass.disc(g, this.bx + bh / 2, by + bh / 2, bh / 2, c0);
        Glass.disc(g, this.bx + this.bw - bh / 2, by + bh / 2, bh / 2, c1);
        for (int k = 0; k < n; k++) {
            float t = (k + 0.5f) / n;
            float x0 = this.bx + k * sw;
            float x1 = this.bx + (k + 1) * sw;
            if (x1 < this.bx + bh / 2 || x0 > this.bx + this.bw - bh / 2) {
                continue;
            }
            g.fill(Math.round(x0), Math.round(by), Math.round(x1) + 1, Math.round(by + bh), this.colorAt(i, t));
        }
        Glass.ring(g, this.bx, by, this.bw, bh, bh / 2, 0.5f, 0x44FFFFFF);
        float kx = this.bx + bh / 2 + (this.bw - bh) * value;
        Glass.shadowSoft(g, kx - 6, by - 1, 12, 12, 6, 3, 1, 0x66000000);
        Glass.disc(g, kx, by + bh / 2, 6, 0xFFFFFFFF);
        Glass.disc(g, kx, by + bh / 2, 4, 0xFF000000 | this.colorAt(i, value));
    }

    private int colorAt(int bar, float t) {
        if (bar == 0) {
            return hsvToRgb(t, 1.0f, 1.0f);
        }
        if (bar == 1) {
            return hsvToRgb(this.h, t, this.v);
        }
        return hsvToRgb(this.h, this.s, t);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 5.0f);
        float open = Glass.ease(this.openAnim);
        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, (int) (140 * open)));
        Glass.alpha = open;
        Glass.glassCard(g, this.px, this.py, this.pw, this.ph, 14, Palette.BG, Math.min(0.92f, Glass.opacity + 0.14f), 1.0f);
        Glass.textScaled(g, this.font, "Farbe wählen", this.px + 16, this.py + 14, 1.3f, Palette.TEXT, true);
        Glass.rr(g, this.px + this.pw - 16 - 38, this.py + 12, 38, 22, 11, 0xFF000000 | this.current);
        Glass.ring(g, this.px + this.pw - 16 - 38, this.py + 12, 38, 22, 11, 0.5f, 0x66FFFFFF);
        this.drawBar(g, 0, "Farbton", this.h);
        this.drawBar(g, 1, "Sättigung", this.s);
        this.drawBar(g, 2, "Helligkeit", this.v);
        float hy = this.py + this.ph - 38;
        Glass.rr(g, this.px + 16, hy, 122, 22, 11, Glass.a(0x000000, 0.28f));
        Glass.ring(g, this.px + 16, hy, 122, 22, 11, 0.5f, this.hex.isFocused() ? Glass.a(Palette.ACCENT, 0.9f) : 0x28FFFFFF);
        Glass.text(g, this.font, "HEX", this.px + 24, Math.round(hy + 7), Palette.MUTED, false);
        float fw = 14 + this.font.width("Fertig") + 14;
        float fx = this.px + this.pw - 16 - fw;
        boolean hf = hit(mx, my, fx, hy, fw, 22);
        Float cur = this.anims.get("done");
        float hh = Glass.step(cur == null ? 0.0f : cur, hf ? 1.0f : 0.0f, 14.0f);
        this.anims.put("done", hh);
        Glass.pill(g, fx, hy, fw, 22, hh, true, false);
        Glass.textC(g, this.font, "Fertig", Math.round(fx + fw / 2), Math.round(hy + 7), 0xFFFFFFFF, false);
        Glass.alpha = 1.0f;
        super.extractRenderState(g, mx, my, pt);
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = (float) r / 255.0f;
        float gf = (float) g / 255.0f;
        float bf = (float) b / 255.0f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float hh = 0.0f;
        if (d != 0.0f) {
            hh = max == rf ? (gf - bf) / d % 6.0f : (max == gf ? (bf - rf) / d + 2.0f : (rf - gf) / d + 4.0f);
            hh *= 60.0f;
            if (hh < 0.0f) {
                hh += 360.0f;
            }
        }
        float ss = max == 0.0f ? 0.0f : d / max;
        return new float[]{hh % 360.0f / 360.0f, ss, max};
    }

    private static int hsvToRgb(float hh, float ss, float vv) {
        int h = (int) (hh * 360.0f);
        float c = vv * ss;
        float x = c * (1.0f - Math.abs((float) h / 60.0f % 2.0f - 1.0f));
        float m = vv - c;
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        if (h < 60) {
            r = c;
            g = x;
        } else if (h < 120) {
            r = x;
            g = c;
        } else if (h < 180) {
            g = c;
            b = x;
        } else if (h < 240) {
            g = x;
            b = c;
        } else {
            r = c;
            b = x;
        }
        int ri = Math.round((r + m) * 255.0f);
        int gi = Math.round((g + m) * 255.0f);
        int bi = Math.round((b + m) * 255.0f);
        return 0xFF000000 | ri << 16 | gi << 8 | bi;
    }
}
