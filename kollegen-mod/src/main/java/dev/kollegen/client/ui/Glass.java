package dev.kollegen.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Kleine Sammlung zum Zeichnen von "Liquid-Glass"-Flächen (abgerundete,
 * halbtransparente Panels mit dünner Leiste und dezentem Glanz). Alle Farben
 * werden als 0xAARRGGBB erwartet.
 */
public final class Glass {

    private Glass() {
    }

    /** Ersetzt den Alpha-Kanal einer 0xAARRGGBB-Farbe. */
    public static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Mischt zwei Farben (a gewichtet durch t in [0,1]). */
    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | bl;
    }

    /** Scanline-basierter abgerundeter Kasten (gefüllt). */
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

    /**
     * Glas-Panel: gefüllter abgerundeter Kasten mit dünner, hellerer Leiste
     * (1px) und einem dezenten Glanz im oberen Bereich.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int r,
                             int fill, int border, int sheen) {
        // Border (etwas größer, dahinter)
        fillRound(g, x, y, w, h, r, border);
        // Füllung (1px nach innen)
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
        // Glanz: obere Hälfte leicht aufhellen
        int sheenH = Math.max(8, h / 3);
        int steps = Math.min(sheenH, 60);
        for (int i = 0; i < steps; i++) {
            float t = (float) i / (float) steps;
            int a = (int) (tint(sheen, 0xFF) != 0 ? (40 * (1f - t)) : 0);
            int col = tint(sheen, Math.max(0, Math.min(255, a)));
            g.fill(x + 2, y + 2 + i, x + w - 2, y + 3 + i, col);
        }
    }

    /** Glas-Button (abgerundet, halbtransparent). */
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

    /** Zeichnet eine beliebige Linie (Bresenham) – 1px, für Vektor-Icons. */
    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            g.fill(x0, y0, 1, 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    /** Weicher Glanz/Halo (mehrere konzentrische, abnehmend transparente Ränder). */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, int strength) {
        for (int i = 1; i <= 3; i++) {
            int pad = i * 3;
            fillRound(g, x - pad, y - pad, w + pad * 2, h + pad * 2, r + pad, tint(color, strength / i));
        }
    }
}
