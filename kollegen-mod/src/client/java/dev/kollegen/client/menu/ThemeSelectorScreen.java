package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.Icons;
import dev.kollegen.client.ui.UiStyle;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThemeSelectorScreen extends Screen {
    private final Screen parent;
    private final List<Palette.Theme> themes;
    private final Map<String, Float> anims = new HashMap<>();
    private int currentTab = 0;
    private static final String[] TABS = {"🎨 Farbschema", "📐 UI- & HUD-Stil"};

    private float scrollT = 0.0f;
    private float scrollV = 0.0f;
    private float maxScroll = 0.0f;
    private float openAnim = 0.0f;
    private boolean dragSlider = false;

    private int px, py, pw, ph, cx, cy, cw, ch, cols;
    private float cardW;
    private static final int CARD_H = 62;
    private static final int GAP = 8;

    public ThemeSelectorScreen(Screen parent) {
        super(Component.literal("Farbschema & Stil"));
        this.parent = parent;
        this.themes = new ArrayList<>(Palette.getThemes().values());
    }

    @Override
    protected void init() {
        this.pw = Math.min(this.width - 16, 680);
        this.ph = Math.min(this.height - 16, 440);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
        this.cx = this.px + 14;
        this.cy = this.py + 74;
        this.cw = this.pw - 28;
        this.ch = this.py + this.ph - 14 - this.cy;

        if (currentTab == 0) {
            this.cols = Math.max(1, Math.min(3, (this.cw + GAP) / (180 + GAP)));
            this.cardW = (this.cw - 6 - (this.cols - 1) * GAP) / (float) this.cols;
            int rows = (this.themes.size() + this.cols - 1) / this.cols;
            float total = rows * (CARD_H + GAP) - GAP;
            this.maxScroll = Math.max(0.0f, total - this.ch + 2);
        } else {
            this.cols = Math.max(1, Math.min(2, (this.cw + GAP) / (240 + GAP)));
            this.cardW = (this.cw - 6 - (this.cols - 1) * GAP) / (float) this.cols;
            int rows = (UiStyle.values().length + this.cols - 1) / this.cols;
            float total = rows * (74 + GAP) - GAP;
            this.maxScroll = Math.max(0.0f, total - this.ch + 2);
        }
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
        return this.px + this.pw - 14 - 110;
    }

    private float sliderY() {
        return this.py + 44;
    }

    private void setSlider(double mx) {
        float v = (float) ((mx - this.sliderX()) / 110.0);
        v = Math.max(0.0f, Math.min(1.0f, v));
        Glass.opacity = 0.15f + v * 0.8f;
    }

    private void close() {
        Palette.saveTheme();
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (hit(mx, my, this.px + 12, this.py + 10, 22, 22)) {
                this.close();
                return true;
            }
            if (hit(mx, my, this.sliderX() - 4, this.sliderY() - 6, 120, 18)) {
                this.dragSlider = true;
                this.setSlider(mx);
                return true;
            }

            int tabY = this.py + 40;
            for (int t = 0; t < TABS.length; t++) {
                int tx = this.px + 14 + t * 130;
                if (hit(mx, my, tx, tabY, 122, 24)) {
                    if (currentTab != t) {
                        currentTab = t;
                        scrollT = 0.0f;
                        scrollV = 0.0f;
                        init();
                    }
                    return true;
                }
            }

            if (hit(mx, my, this.cx, this.cy, this.cw, this.ch)) {
                if (currentTab == 0) {
                    for (int i = 0; i < this.themes.size(); i++) {
                        float x = this.cx + (i % this.cols) * (this.cardW + GAP);
                        float y = this.cy + (i / this.cols) * (CARD_H + GAP) - this.scrollV;
                        if (hit(mx, my, x, y, this.cardW, CARD_H)) {
                            Palette.setTheme(this.themes.get(i).id);
                            if ("minecraft".equalsIgnoreCase(this.themes.get(i).id)) {
                                UiStyleManager.setGlobalStyle(UiStyle.MINECRAFT_VANILLA);
                            }
                            return true;
                        }
                    }
                } else {
                    UiStyle[] styles = UiStyle.values();
                    for (int i = 0; i < styles.length; i++) {
                        float x = this.cx + (i % this.cols) * (this.cardW + GAP);
                        float y = this.cy + (i / this.cols) * (74 + GAP) - this.scrollV;
                        if (hit(mx, my, x, y, this.cardW, 74)) {
                            UiStyleManager.setGlobalStyle(styles[i]);
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragSlider && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.setSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragSlider && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragSlider = false;
            Palette.saveTheme();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (this.maxScroll > 0) {
            this.scrollT = Math.max(0.0f, Math.min(this.maxScroll, this.scrollT - (float) (vertical * 26.0)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            this.close();
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 4.5f);
        this.scrollV = Glass.step(this.scrollV, this.scrollT, 20.0f);
        float open = Glass.ease(this.openAnim);
        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, (int) (140 * open)));
        Glass.alpha = open;

        UiStyleManager.drawPanel(g, this.px, this.py, this.pw, this.ph, Palette.BG);

        boolean hb = hit(mx, my, this.px + 12, this.py + 10, 22, 22);
        Glass.pill(g, this.px + 12, this.py + 10, 22, 22, this.hv("back", hb), false, false);
        Icons.draw(g, Icons.BACK, this.px + 17, this.py + 15, 12, Palette.TEXT);

        Glass.textScaled(g, this.font, "Farbschema & Stil", this.px + 42, this.py + 12, 1.3f, Palette.TEXT, true);
        Glass.text(g, this.font, "Optik & Aussehen der Companion Mod & HUDs anpassen", this.px + 42, this.py + 27, Palette.MUTED, false);

        int tabY = this.py + 42;
        for (int t = 0; t < TABS.length; t++) {
            int tx = this.px + 14 + t * 130;
            boolean sel = currentTab == t;
            boolean hov = hit(mx, my, tx, tabY, 122, 24);
            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, tx, tabY, 122, 24, hov ? 1.0f : 0.0f, sel, false);
                int tc = hov ? 0xFFFFFFA0 : (sel ? 0xFFFFFFFF : Palette.TEXT);
                int tw = this.font.width(TABS[t]);
                Glass.text(g, this.font, TABS[t], tx + (122 - tw) / 2, tabY + 7, tc, true);
            } else {
                int bg = sel ? Palette.ACCENT : (hov ? Palette.PANEL2 : Palette.PANEL);
                Glass.fillRound(g, tx, tabY, 122, 24, 6, bg);
                int tc = sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
                int tw = this.font.width(TABS[t]);
                Glass.text(g, this.font, TABS[t], tx + (122 - tw) / 2, tabY + 7, tc, false);
            }
        }

        Glass.text(g, this.font, "Deckkraft", Math.round(this.sliderX() - this.font.width("Deckkraft") - 6), this.py + 48, Palette.MUTED, false);
        float sv = (Glass.opacity - 0.15f) / 0.8f;
        Glass.vanillaSlider(g, Math.round(this.sliderX()), Math.round(this.sliderY()) - 3, 110, 12, sv, hit(mx, my, this.sliderX(), this.sliderY() - 4, 110, 14), this.dragSlider);

        Glass.separator(g, this.px + 12, this.py + 68, this.pw - 24, 0x22FFFFFF);

        g.enableScissor(this.cx - 2, this.cy, this.cx + this.cw + 2, this.cy + this.ch);

        if (currentTab == 0) {
            String cur = Palette.getCurrentTheme();
            for (int i = 0; i < this.themes.size(); i++) {
                Palette.Theme t = this.themes.get(i);
                float x = this.cx + (i % this.cols) * (this.cardW + GAP);
                float y = this.cy + (i / this.cols) * (CARD_H + GAP) - this.scrollV;
                if (y + CARD_H < this.cy - 2 || y > this.cy + this.ch + 2) continue;

                boolean sel = t.id.equals(cur);
                boolean hov = hit(mx, my, this.cx, this.cy, this.cw, this.ch) && hit(mx, my, x, y, this.cardW, CARD_H);
                float h = this.hv("t:" + t.id, hov);

                UiStyleManager.drawCard(g, x, y, this.cardW, CARD_H, 8, t.panel, h, null);
                if (sel) {
                    Glass.ring(g, x, y, this.cardW, CARD_H, 8, 1.2f, Glass.a(t.accent, 0.95f));
                }

                float pvx = x + 8;
                float pvy = y + 8;
                Glass.rrGrad(g, pvx, pvy, 46, 46, 7, 0xFF000000 | t.bg, 0xFF000000 | t.panel);
                Glass.rr(g, pvx + 5, pvy + 26, 36, 14, 6, 0xFF000000 | t.panel2);
                Glass.disc(g, pvx + 12, pvy + 33, 4, 0xFF000000 | t.accent);
                Glass.disc(g, pvx + 23, pvy + 33, 4, 0xFF000000 | t.accent2);
                Glass.disc(g, pvx + 34, pvy + 33, 4, 0xFF000000 | t.green);
                Glass.rr(g, pvx + 5, pvy + 8, 22, 3, 1.5f, Glass.a(0xFF000000 | t.text, 0.9f));
                Glass.rr(g, pvx + 5, pvy + 14, 14, 3, 1.5f, Glass.a(0xFF000000 | t.muted, 0.9f));
                Glass.ring(g, pvx, pvy, 46, 46, 7, 0.5f, 0x40FFFFFF);

                int tx = Math.round(x + 62);
                Glass.textBold(g, this.font, Glass.trunc(this.font, t.name, (int) this.cardW - 70), tx, Math.round(y + 13), Palette.TEXT, true);
                Glass.text(g, this.font, Glass.trunc(this.font, t.description, (int) this.cardW - 70), tx, Math.round(y + 27), Palette.MUTED, false);
                if (sel) {
                    Glass.badge(g, tx, Math.round(y + CARD_H - 18), 42, 13, 6, t.accent, 0xFFFFFFFF, this.font, "Aktiv");
                }
            }
        } else {
            UiStyle curStyle = UiStyleManager.getGlobalStyle();
            UiStyle[] styles = UiStyle.values();
            for (int i = 0; i < styles.length; i++) {
                UiStyle s = styles[i];
                float x = this.cx + (i % this.cols) * (this.cardW + GAP);
                float y = this.cy + (i / this.cols) * (74 + GAP) - this.scrollV;
                if (y + 74 < this.cy - 2 || y > this.cy + this.ch + 2) continue;

                boolean sel = s == curStyle;
                boolean hov = hit(mx, my, this.cx, this.cy, this.cw, this.ch) && hit(mx, my, x, y, this.cardW, 74);
                float h = this.hv("s:" + s.name(), hov);

                UiStyleManager.drawCard(g, x, y, this.cardW, 74, 8, Palette.PANEL, h, s);
                if (sel) {
                    Glass.ring(g, x, y, this.cardW, 74, 8, 1.5f, Palette.ACCENT);
                }

                int tx = Math.round(x + 14);
                Glass.textBold(g, this.font, s.title, tx, Math.round(y + 12), Palette.TEXT, true);
                Glass.text(g, this.font, s.desc, tx, Math.round(y + 28), Palette.MUTED, false);
                Glass.text(g, this.font, "Vorschau: Menüs, Boxen & HUDs", tx, Math.round(y + 42), Palette.MUTED, false);

                if (sel) {
                    Glass.badge(g, Math.round(x + this.cardW - 64), Math.round(y + 12), 52, 16, 6, Palette.ACCENT, 0xFFFFFFFF, this.font, "Standard");
                }
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
