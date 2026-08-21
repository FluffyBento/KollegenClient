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

    /**
     * Abgerundeter Kasten mit weichen (anti-aliased) Ecken. Die geraden Kanten
     * werden crisp gefüllt, nur die Eckbögen bekommen pro Pixel eine
     * Abdeckung (Supersampling), damit sie rund und nicht pixelig wirken.
     * Achtung: g.fill() erwartet x2/y2 (exklusiv).
     */
    public static void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, w / 2);
        r = Math.min(r, h / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int alpha = (color >> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        // Gerade Kanten + Mitte crisp füllen (überlappend, deckt alles außer den
        // vier Eck-Quadranten ab).
        g.fill(x, y + r, x + w, y + h - r, color);
        g.fill(x + r, y, x + w, y + h, color);
        // Eckbögen mit Anti-Aliasing.
        aaCorner(g, x, y, r, 1, 1, rgb, alpha);            // oben links
        aaCorner(g, x + w - r, y, r, -1, 1, rgb, alpha);   // oben rechts
        aaCorner(g, x, y + h - r, r, 1, -1, rgb, alpha);   // unten links
        aaCorner(g, x + w - r, y + h - r, r, -1, -1, rgb, alpha); // unten rechts
    }

    private static void aaCorner(GuiGraphics g, int sx, int sy, int r, int dirX, int dirY,
                                 int rgb, int alpha) {
        int SS = 3;
        for (int yy = 0; yy < r; yy++) {
            for (int xx = 0; xx < r; xx++) {
                int px = sx + xx;
                int py = sy + yy;
                double dx = (dirX > 0) ? (r - 0.5 - xx) : (xx + 0.5);
                double dy = (dirY > 0) ? (r - 0.5 - yy) : (yy + 0.5);
                double dist = Math.sqrt(dx * dx + dy * dy);
                double cov;
                if (dist <= r - 1.0) {
                    cov = 1.0;
                } else if (dist >= r + 0.5) {
                    continue;
                } else {
                    double sum = 0;
                    for (int s = 0; s < SS; s++) {
                        for (int t = 0; t < SS; t++) {
                            double ax = (dirX > 0) ? (r - (xx + (s + 0.5) / SS)) : (xx + (s + 0.5) / SS);
                            double ay = (dirY > 0) ? (r - (yy + (t + 0.5) / SS)) : (yy + (t + 0.5) / SS);
                            if (Math.sqrt(ax * ax + ay * ay) <= r) sum += 1.0;
                        }
                    }
                    cov = sum / (SS * SS);
                }
                if (cov <= 0) continue;
                int a = (int) (cov * alpha);
                if (a > 255) a = 255;
                g.fill(px, py, px + 1, py + 1, (a << 24) | rgb);
            }
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
