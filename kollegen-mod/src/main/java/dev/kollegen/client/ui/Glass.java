package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphics.Pose;

public final class Glass {

    private Glass() {
    }

    public static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | bl;
    }

    public static void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, w / 2);
        r = Math.min(r, h / 2);
        for (int yy = y; yy < y + h; yy++) {
            int dy = Math.min(yy - y, (y + h - 1) - yy);
            int inset = 0;
            if (dy < r) {
                int d = r - dy;
                inset = (int) Math.round(r - Math.sqrt((double) (r * r - d * d)));
            }
            g.fill(x + inset, yy, x + w - inset, yy + 1, color);
        }
    }

    public static void fillRoundGradient(GuiGraphics g, int x, int y, int w, int h, int r,
                                         int topColor, int bottomColor) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, w / 2);
        r = Math.min(r, h / 2);
        for (int yy = y; yy < y + h; yy++) {
            int dy = Math.min(yy - y, (y + h - 1) - yy);
            int inset = 0;
            if (dy < r) {
                int d = r - dy;
                inset = (int) Math.round(r - Math.sqrt((double) (r * r - d * d)));
            }
            float t = (float) (yy - y) / (float) h;
            int col = mix(topColor, bottomColor, t);
            g.fill(x + inset, yy, x + w - inset, yy + 1, col);
        }
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int r,
                             int fill, int border, int sheen) {
        fillRound(g, x, y, w, h, r, border);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
        int sheenH = Math.max(8, h / 3);
        int steps = Math.min(sheenH, 60);
        for (int i = 0; i < steps; i++) {
            float t = (float) i / (float) steps;
            int a = (int) (40 * (1f - t));
            int col = tint(sheen, Math.max(0, Math.min(255, a)));
            g.fill(x + 2, y + 2 + i, x + w - 2, y + 3 + i, col);
        }
    }

    public static void panelVanilla(GuiGraphics g, int x, int y, int w, int h, int r) {
        fillRound(g, x, y, w, h, r, Palette.BORDER);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Palette.PANEL);
        int sheenH = Math.max(8, h / 3);
        int steps = Math.min(sheenH, 60);
        for (int i = 0; i < steps; i++) {
            float t = (float) i / (float) steps;
            int a = (int) (30 * (1f - t));
            int col = tint(Palette.ACCENT, Math.max(0, Math.min(255, a)));
            g.fill(x + 2, y + 2 + i, x + w - 2, y + 3 + i, col);
        }
    }

    public static void panelDark(GuiGraphics g, int x, int y, int w, int h, int r) {
        fillRound(g, x, y, w, h, r, Palette.BORDER);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Palette.PANEL);
    }

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int r, int radius) {
        int layers = Math.min(radius, 8);
        for (int i = layers; i > 0; i--) {
            int alpha = (int) (0x18 * (1f - (float) i / layers));
            int col = tint(Palette.SHADOW, alpha);
            fillRound(g, x - i, y - i, w + i * 2, h + i * 2, r + i, col);
        }
    }

    public static void dropShadow(GuiGraphics g, int x, int y, int w, int h, int r, int offset, int radius) {
        int layers = Math.min(radius, 8);
        for (int i = layers; i > 0; i--) {
            int alpha = (int) (0x20 * (1f - (float) i / layers));
            int col = tint(Palette.SHADOW, alpha);
            fillRound(g, x - i + offset, y - i + offset, w + i * 2, h + i * 2, r + i, col);
        }
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, int r,
                              int fill, int border, int text, Font font, String label,
                              boolean hover, boolean selected) {
        int f = selected ? tint(fill, 0xE6) : (hover ? tint(fill, 0xB0) : fill);
        int b = selected ? border : (hover ? tint(border, 0xCC) : border);
        fillRound(g, x, y, w, h, r, b);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), f);
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2, text, false);
    }

    public static void buttonVanilla(GuiGraphics g, int x, int y, int w, int h, int r,
                                     Font font, String label, boolean hover, boolean selected,
                                     boolean disabled) {
        int baseFill = selected ? Palette.ACCENT : Palette.PANEL2;
        int baseBorder = selected ? Palette.ACCENT : Palette.BORDER;
        int textCol = disabled ? Palette.MUTED : (selected ? 0xFFFFFFFF : Palette.TEXT);

        if (disabled) {
            fillRound(g, x, y, w, h, r, tint(baseBorder, 0x80));
            fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), tint(baseFill, 0x80));
        } else if (selected) {
            fillRound(g, x, y, w, h, r, baseBorder);
            fillRoundGradient(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1),
                    tint(Palette.ACCENT, 0xE6), tint(Palette.ACCENT2, 0xE6));
        } else if (hover) {
            fillRound(g, x, y, w, h, r, baseBorder);
            fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), tint(baseFill, 0xE0));
        } else {
            fillRound(g, x, y, w, h, r, baseBorder);
            fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), baseFill);
        }

        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2, textCol, false);
    }

    public static void checkbox(GuiGraphics g, int x, int y, int size, boolean checked,
                                boolean hover, boolean focused) {
        int borderCol = focused ? Palette.FOCUS_BORDER : (hover ? Palette.ACCENT : Palette.BORDER);
        int fillCol = checked ? Palette.ACCENT : Palette.PANEL2;
        fillRound(g, x, y, size, size, 4, borderCol);
        fillRound(g, x + 1, y + 1, size - 2, size - 2, 3,
                hover ? tint(fillCol, 0xE0) : fillCol);
        if (checked) {
            g.fill(x + size / 2 - 1, y + size / 2 - 3, x + size / 2 + 1, y + size / 2 + 3, 0xFFFFFFFF);
            g.fill(x + size / 2 - 3, y + size / 2 - 1, x + size / 2 + 3, y + size / 2 + 1, 0xFFFFFFFF);
        }
    }

    public static void sliderTrack(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        fillRound(g, x, y, w, h, r, color);
    }

    public static void sliderThumb(GuiGraphics g, int x, int y, int size, int r, int color, boolean hover) {
        int col = hover ? tint(color, 0xE0) : color;
        fillRound(g, x, y, size, size, r, col);
        if (hover) {
            fillRound(g, x + 1, y + 1, size - 2, size - 2, Math.max(0, r - 1), tint(Palette.TEXT, 0x30));
        }
    }

    public static void scrollbarTrack(GuiGraphics g, int x, int y, int w, int h, int r) {
        fillRound(g, x, y, w, h, r, Palette.SCROLL_TRACK);
    }

    public static void scrollbarThumb(GuiGraphics g, int x, int y, int w, int h, int r, boolean hover) {
        int col = hover ? Palette.SCROLL_THUMB_HOVER : Palette.SCROLL_THUMB;
        fillRound(g, x, y, w, h, r, col);
    }

    public static void tooltipBackground(GuiGraphics g, int x, int y, int w, int h, int r) {
        fillRound(g, x, y, w, h, r, Palette.BORDER);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), tint(Palette.BG, 0xF0));
        dropShadow(g, x, y, w, h, r, 2, 6);
    }

    public static void drawHollowRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawSelectionQuad(GuiGraphics g, int x, int y, int w, int h, int color) {
        int thick = 2;
        int corner = 8;
        g.fill(x, y, x + corner, y + thick, color);
        g.fill(x + w - corner, y, x + w, y + thick, color);
        g.fill(x, y, x + thick, y + corner, color);
        g.fill(x + w - thick, y, x + w, y + corner, color);
        g.fill(x, y + h - thick, x + corner, y + h, color);
        g.fill(x + w - corner, y + h - thick, x + w, y + h, color);
        g.fill(x, y + h - corner, x + thick, y + h, color);
        g.fill(x + w - thick, y + h - corner, x + w, y + h, color);
    }

    public static void separator(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, tint(color, 0x40));
    }
}