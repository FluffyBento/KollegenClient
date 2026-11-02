package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.Icons;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ThemeSelectorScreen extends Screen {
    private final Screen parent;
    private final List<Palette.Theme> themes;
    private final Map<String, Float> anims = new HashMap<String, Float>();
    private float scrollT = 0.0f;
    private float scrollV = 0.0f;
    private float maxScroll = 0.0f;
    private float openAnim = 0.0f;
    private boolean dragSlider = false;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int cols;
    private float cardW;
    private static final int CARD_H = 58;
    private static final int GAP = 6;

    public ThemeSelectorScreen(Screen parent) {
        super((Component) Component.literal("Design"));
        this.parent = parent;
        this.themes = new ArrayList<Palette.Theme>(Palette.getThemes().values());
    }

    protected void init() {
        this.pw = Math.min(this.width - 12, 620);
        this.ph = Math.min(this.height - 12, 400);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
        this.cx = this.px + 12;
        this.cy = this.py + 66;
        this.cw = this.pw - 24;
        this.ch = this.py + this.ph - 12 - this.cy;
        this.cols = Math.max(1, Math.min(3, (this.cw + GAP) / (170 + GAP)));
        this.cardW = (this.cw - 6 - (this.cols - 1) * GAP) / (float) this.cols;
        int rows = (this.themes.size() + this.cols - 1) / this.cols;
        float total = rows * (CARD_H + GAP) - GAP;
        this.maxScroll = Math.max(0.0f, total - this.ch + 2);
        this.scrollT = Math.min(this.scrollT, this.maxScroll);
    }

    private float hv(String key, boolean on) {
        Float cur = this.anims.get(key);
        float c = cur == null ? 0.0f : cur;
        c = Glass.step(c, on ? 1.0f : 0.0f, 14.0f);
        this.anims.put(key, c);
        return c;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float sliderX() {
        return this.px + this.pw - 12 - 120;
    }

    private float sliderY() {
        return this.py + 40;
    }

    private void setSlider(double mx) {
        float v = (float) ((mx - this.sliderX()) / 120.0);
        v = Math.max(0.0f, Math.min(1.0f, v));
        Glass.opacity = 0.15f + v * 0.8f;
    }

    private void close() {
        Palette.saveTheme();
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (hit(mx, my, this.px + 12, this.py + 10, 22, 22)) {
                this.close();
                return true;
            }
            if (hit(mx, my, this.sliderX() - 4, this.sliderY() - 6, 128, 18)) {
                this.dragSlider = true;
                this.setSlider(mx);
                return true;
            }
            if (hit(mx, my, this.cx, this.cy, this.cw, this.ch)) {
                for (int i = 0; i < this.themes.size(); i++) {
                    float x = this.cx + (i % this.cols) * (this.cardW + GAP);
                    float y = this.cy + (i / this.cols) * (CARD_H + GAP) - this.scrollV;
                    if (hit(mx, my, x, y, this.cardW, CARD_H)) {
                        Palette.setTheme(this.themes.get(i).id);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, bl);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragSlider && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.setSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragSlider && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragSlider = false;
            Palette.saveTheme();
            return true;
        }
        return super.mouseReleased(event);
    }

    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (this.maxScroll > 0) {
            this.scrollT = Math.max(0.0f, Math.min(this.maxScroll, this.scrollT - (float) (vertical * 26.0)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
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

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 4.5f);
        this.scrollV = Glass.step(this.scrollV, this.scrollT, 20.0f);
        float open = Glass.ease(this.openAnim);
        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, (int) (140 * open)));
        Glass.alpha = open;
        Glass.glassCard(g, this.px, this.py, this.pw, this.ph, 14, Palette.BG, Math.min(0.92f, Glass.opacity + 0.12f), 1.0f);
        Glass.rrGrad(g, this.px + 1, this.py + 1, this.pw - 2, 100, 13, Glass.a(Palette.ACCENT, 0.10f), 0x00000000);
        boolean hb = hit(mx, my, this.px + 12, this.py + 10, 22, 22);
        Glass.pill(g, this.px + 12, this.py + 10, 22, 22, this.hv("back", hb), false, false);
        Icons.draw(g, Icons.BACK, this.px + 17, this.py + 15, 12, Palette.TEXT);
        Glass.textScaled(g, this.font, "Design", this.px + 42, this.py + 12, 1.4f, Palette.TEXT, true);
        Glass.text(g, this.font, "Thema wählen – Änderungen sind sofort live", this.px + 42, this.py + 30, Palette.MUTED, false);
        Glass.text(g, this.font, "Glas-Deckkraft", Math.round(this.sliderX() - this.font.width("Glas-Deckkraft") - 8), this.py + 36, Palette.MUTED, false);
        float sv = (Glass.opacity - 0.15f) / 0.8f;
        Glass.vanillaSlider(g, Math.round(this.sliderX()), Math.round(this.sliderY()) - 3, 120, 12, sv, hit(mx, my, this.sliderX(), this.sliderY() - 4, 120, 14), this.dragSlider);
        Glass.separator(g, this.px + 12, this.py + 58, this.pw - 24, 0x22FFFFFF);
        g.enableScissor(this.cx - 2, this.cy, this.cx + this.cw + 2, this.cy + this.ch);
        String cur = Palette.getCurrentTheme();
        for (int i = 0; i < this.themes.size(); i++) {
            Palette.Theme t = this.themes.get(i);
            float x = this.cx + (i % this.cols) * (this.cardW + GAP);
            float y = this.cy + (i / this.cols) * (CARD_H + GAP) - this.scrollV;
            if (y + CARD_H < this.cy - 2 || y > this.cy + this.ch + 2) {
                continue;
            }
            boolean sel = t.id.equals(cur);
            boolean hov = hit(mx, my, this.cx, this.cy, this.cw, this.ch) && hit(mx, my, x, y, this.cardW, CARD_H);
            float h = this.hv("t:" + t.id, hov);
            Glass.glassCard(g, x, y, this.cardW, CARD_H, 9, Glass.mix(t.panel, 0xFFFFFFFF, 0.04f * h), Math.min(0.95f, Glass.opacity + 0.2f), 0.0f);
            if (sel) {
                Glass.ring(g, x, y, this.cardW, CARD_H, 9, 1.0f, Glass.a(t.accent, 0.95f));
            } else if (h > 0.01f) {
                Glass.ring(g, x, y, this.cardW, CARD_H, 9, 0.5f, Glass.a(0xFFFFFFFF, 0.4f * h));
            }
            float pvx = x + 8;
            float pvy = y + 8;
            Glass.rrGrad(g, pvx, pvy, 42, 42, 7, 0xFF000000 | t.bg, 0xFF000000 | t.panel);
            Glass.rr(g, pvx + 5, pvy + 24, 32, 12, 6, 0xFF000000 | t.panel2);
            Glass.disc(g, pvx + 12, pvy + 30, 4, 0xFF000000 | t.accent);
            Glass.disc(g, pvx + 22, pvy + 30, 4, 0xFF000000 | t.accent2);
            Glass.disc(g, pvx + 32, pvy + 30, 4, 0xFF000000 | t.green);
            Glass.rr(g, pvx + 5, pvy + 7, 20, 3, 1.5f, Glass.a(0xFF000000 | t.text, 0.9f));
            Glass.rr(g, pvx + 5, pvy + 13, 12, 3, 1.5f, Glass.a(0xFF000000 | t.muted, 0.9f));
            Glass.ring(g, pvx, pvy, 42, 42, 7, 0.5f, 0x40FFFFFF);
            int tx = Math.round(x + 58);
            Glass.textBold(g, this.font, Glass.trunc(this.font, t.name, (int) this.cardW - 66), tx, Math.round(y + 12), Palette.TEXT, true);
            Glass.text(g, this.font, Glass.trunc(this.font, t.description, (int) this.cardW - 66), tx, Math.round(y + 25), Palette.MUTED, false);
            if (sel) {
                Glass.badge(g, tx, Math.round(y + CARD_H - 20), 38, 14, 7, t.accent, 0xFFFFFFFF, this.font, "Aktiv");
            }
        }
        g.disableScissor();
        if (this.maxScroll > 0) {
            Glass.vanillaScrollbar(g, this.cx + this.cw - 3, this.cy, 3, this.ch, this.scrollV, this.maxScroll, false, false);
        }
        Glass.alpha = 1.0f;
        super.extractRenderState(g, mx, my, pt);
    }
}
