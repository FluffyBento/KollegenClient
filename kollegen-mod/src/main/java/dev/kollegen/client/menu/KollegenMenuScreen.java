package dev.kollegen.client.menu;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.config.KollegenConfig;
import dev.kollegen.client.render.KollegenPostFX;
import dev.kollegen.client.theme.ThemeSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * In-game Mod-Menü (Rechts-Shift) im Feather-Stil. Enthält:
 *  – Minecraft-Logo durch Logo.png ersetzen (Toggle)
 *  – Farb-Sättigung (Slider 0–200 %)
 *  – Spezifische Farb-Hervorhebung per Hex-Code + Stärke
 * Weitere Einstellungen folgen. Farben werden live aus dem Launcher gesynct.
 */
public class KollegenMenuScreen extends Screen {
    private final Screen parent;

    private int px, py;
    private int pw = 480, ph = 400;

    private int logoToggleX, logoToggleY;
    private static final int TOGGLE_W = 52, TOGGLE_H = 28;

    private int satX, satY, satW = 220, satH = 22;
    private int hiToggleX, hiToggleY;
    private int hiX, hiY, hiW, hiH;
    private int hexX, hexY, hexW = 96, hexH = 20;
    private int fbToggleX, fbToggleY;

    private boolean satDragging = false;
    private boolean hiDragging = false;
    private boolean hexFocused = false;
    private String hexBuf = "";

    public KollegenMenuScreen(Screen parent) {
        super(Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ThemeSync.refresh();
        int bw = 200;
        this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> close())
                .bounds(this.width / 2 - bw / 2, this.height - 48, bw, 28)
                .build());
        hexBuf = KollegenMod.CONFIG.highlightColor;
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ═══ Maus / Tastatur ═══

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inRect(mx, my, logoToggleX, logoToggleY, TOGGLE_W, TOGGLE_H)) {
            KollegenMod.CONFIG.replaceLogo = !KollegenMod.CONFIG.replaceLogo;
            KollegenMod.CONFIG.save();
            return true;
        }
        if (inRect(mx, my, fbToggleX, fbToggleY, TOGGLE_W, TOGGLE_H)) {
            KollegenMod.CONFIG.fullbright = !KollegenMod.CONFIG.fullbright;
            KollegenMod.CONFIG.save();
            dev.kollegen.client.feature.Fullbright.reconcile();
            return true;
        }
        if (inRect(mx, my, satX, satY, satW, satH)) {
            hexFocused = false;
            setSaturation((float) ((mx - satX) / (double) satW));
            satDragging = true;
            return true;
        }
        if (inRect(mx, my, hiToggleX, hiToggleY, TOGGLE_W, TOGGLE_H)) {
            KollegenMod.CONFIG.colorHighlight = !KollegenMod.CONFIG.colorHighlight;
            KollegenMod.CONFIG.save();
            return true;
        }
        if (inRect(mx, my, hiX, hiY, hiW, hiH)) {
            hexFocused = false;
            setHighlightAmount((float) ((mx - hiX) / (double) hiW));
            hiDragging = true;
            return true;
        }
        if (inRect(mx, my, hexX, hexY, hexW, hexH)) {
            hexFocused = true;
            return true;
        }
        hexFocused = false;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        satDragging = false;
        hiDragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double delX, double delY) {
        if (satDragging) {
            setSaturation((float) ((mx - satX) / (double) satW));
            return true;
        }
        if (hiDragging) {
            setHighlightAmount((float) ((mx - hiX) / (double) hiW));
            return true;
        }
        return super.mouseDragged(mx, my, button, delX, delY);
    }

    private void setSaturation(float v) {
        KollegenMod.CONFIG.colorSaturation = Math.max(0.0f, Math.min(2.0f, v));
        KollegenMod.CONFIG.save();
        KollegenPostFX.applyConfig();
    }

    private void setHighlightAmount(float v) {
        KollegenMod.CONFIG.highlightAmount = Math.max(0.0f, Math.min(1.0f, v));
        KollegenMod.CONFIG.save();
        KollegenPostFX.applyConfig();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hexFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !hexBuf.isEmpty()) {
                hexBuf = hexBuf.substring(0, hexBuf.length() - 1);
                tryApplyHex();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                hexFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (hexFocused) {
            String c = String.valueOf(codePoint);
            if (c.matches("[0-9a-fA-F#]") && hexBuf.length() < 7) {
                if (!(c.equals("#") && hexBuf.contains("#"))) {
                    hexBuf += c;
                }
                tryApplyHex();
                return true;
            }
            return false;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void tryApplyHex() {
        if (hexBuf.matches("#[0-9a-fA-F]{6}")) {
            KollegenMod.CONFIG.highlightColor = hexBuf;
            KollegenMod.CONFIG.save();
            KollegenPostFX.applyConfig();
        }
    }
//PART2
    // ═══ Zeichnen ═══

    @Override
    public void render(GuiGraphics gg, int mx, int my, float delta) {
        ThemeSync.refresh(); // live sync with launcher colors

        int bg = ThemeSync.argb(ThemeSync.get("bg", "#0d0d12"), 0xff0d0d12);
        int panel = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int border = ThemeSync.argb(ThemeSync.get("border", "#34303a"), 0xff34303a);
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int text = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);

        gg.fill(0, 0, this.width, this.height, bg);

        int pw = Math.min(this.pw, this.width - 40);
        int ph = Math.min(this.ph, this.height - 28);
        this.px = (this.width - pw) / 2;
        this.py = (this.height - ph) / 2;
        gg.fill(px, py, px + pw, py + ph, panel);
        gg.fill(px, py, px + pw, py + 2, accent);          // accent top bar
        gg.fill(px, py, px + 2, py + ph, border);
        gg.fill(px + pw - 2, py, px + pw, py + ph, border);
        gg.fill(px, py + ph - 2, px + pw, py + ph, border);

        int cx = px + pw / 2;
        gg.drawCenteredString(this.font, "Kollegen Client", cx, py + 18, accent);
        gg.drawCenteredString(this.font, "Einstellungen", cx, py + 34, muted);

        int left = px + 24;

        // ── Zeile 1: Logo ──
        int row1Y = py + 58;
        logoToggleX = px + pw - 28 - TOGGLE_W;
        logoToggleY = row1Y;
        drawToggle(gg, logoToggleX, logoToggleY, KollegenMod.CONFIG.replaceLogo, accent, muted);
        gg.drawString(this.font, "Minecraft-Logo durch Logo.png ersetzen", left, row1Y + 7, text);

        // ── Zeile 2: Farb-Sättigung ──
        int satLabelY = row1Y + 44;
        gg.drawString(this.font, "Farb-Sättigung", left, satLabelY, muted);
        satX = left;
        satY = satLabelY + 12;
        satW = Math.min(230, pw - left - 70);
        drawSlider(gg, satX, satY, satW, 20, KollegenMod.CONFIG.colorSaturation / 2.0f);
        int pct = Math.round(KollegenMod.CONFIG.colorSaturation * 100);
        gg.drawString(this.font, pct + "%", satX + satW + 10, satY + 5, text);

        // ── Zeile 3: Farbe hervorheben ──
        int row3Y = row1Y + 108;
        hiToggleX = px + pw - 28 - TOGGLE_W;
        hiToggleY = row3Y;
        drawToggle(gg, hiToggleX, hiToggleY, KollegenMod.CONFIG.colorHighlight, accent, muted);
        gg.drawString(this.font, "Farbe hervorheben", left, row3Y + 7, text);

        // Hex-Eingabe + Farbfeld
        int hexLabelY = row3Y + 34;
        gg.drawString(this.font, "Hex-Farbe", left, hexLabelY, muted);
        hexX = left;
        hexY = hexLabelY + 12;
        hexW = Math.min(90, pw - left - 60);
        gg.fill(hexX, hexY, hexX + hexW, hexY + hexH,
                hexFocused ? 0xff2c2c36 : 0xff14141c);
        gg.drawString(this.font, hexBuf + (hexFocused ? "_" : ""), hexX + 3, hexY + 7, text);

        int swatchX = hexX + hexW + 8;
        int swatchY = hexY;
        int[] r = hexToRgb(KollegenMod.CONFIG.highlightColor);
        gg.fill(swatchX, swatchY, swatchX + 18, swatchY + 18,
                0xff000000 | (r[0] << 16) | (r[1] << 8) | r[2]);

        // ── Zeile 4: Hervorhebungs-Stärke ──
        int hiLabelY = hexLabelY + 44;
        gg.drawString(this.font, "Stärke", left, hiLabelY, muted);
        hiX = left;
        hiY = hiLabelY + 12;
        hiW = Math.min(230, pw - left - 70);
        hiH = 22;
        drawSlider(gg, hiX, hiY, hiW, hiH, KollegenMod.CONFIG.highlightAmount);
        int pct2 = Math.round(KollegenMod.CONFIG.highlightAmount * 100);
        gg.drawString(this.font, pct2 + "%", hiX + hiW + 10, hiY + 8, text);

        // ── Zeile 5: Fullbright ──
        int fbY = hiLabelY + 44;
        fbToggleX = px + pw - 28 - TOGGLE_W;
        fbToggleY = fbY;
        drawToggle(gg, fbToggleX, fbToggleY, KollegenMod.CONFIG.fullbright, accent, muted);
        gg.drawString(this.font, "Fullbright (Gamma)", left, fbY + 7, text);

        gg.drawCenteredString(this.font, "Einstellungen werden laufend erweitert…", cx, py + ph - 30, muted);

        super.render(gg, mx, my, delta);
    }

    private void drawToggle(GuiGraphics gg, int x, int y, boolean on, int accent, int muted) {
        gg.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, on ? accent : muted);
        int kx = on ? (x + TOGGLE_W - TOGGLE_H + 3) : (x + 3);
        gg.fill(kx, y + 3, kx + TOGGLE_H - 6, y + TOGGLE_H - 3, 0xffffffff);
    }

    private void drawSlider(GuiGraphics gg, int x, int y, int w, int h, float val) {
        gg.fill(x, y, x + w, y + h, 0xff2b2b34);
        int fx = x + (int) (Math.max(0, Math.min(1, val)) * (w - 8));
        gg.fill(fx, y + 1, fx + 8, y + h - 1, ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623));
    }

    private int[] hexToRgb(String hex) {
        int[] out = new int[]{255, 87, 34};
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 6) {
                out[0] = Integer.parseInt(h.substring(0, 2), 16);
                out[1] = Integer.parseInt(h.substring(2, 4), 16);
                out[2] = Integer.parseInt(h.substring(4, 6), 16);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
