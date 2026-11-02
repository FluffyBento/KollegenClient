package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public final class Glass {
    public static float alpha = 1.0f;
    public static float opacity = 0.58f;
    public static float dt = 0.016f;
    private static long lastNs = 0L;
    // PERF-DEBUG: Zähler pro Frame (wird vom Menü zurückgesetzt/geloggt).
    public static int fillCount = 0;
    public static int primCount = 0;

    private Glass() {
    }

    private static float cachedK = 1.0f;
    private static final int[] FULL_BUF = new int[128];
    private static final int[] IFULL_BUF = new int[128];

    public static void frame() {
        long now = System.nanoTime();
        if (lastNs != 0L) {
            float d = (now - lastNs) / 1.0e9f;
            dt = Math.max(0.0005f, Math.min(0.1f, d));
        }
        lastNs = now;
        try {
            net.minecraft.client.Minecraft mc = Minecraft.getInstance();
            int gw = mc.getWindow().getGuiScaledWidth();
            if (gw > 0) {
                float v = (float) mc.getWindow().getScreenWidth() / (float) gw;
                cachedK = Math.min(Math.max(1.0f, v), 2.0f);
            } else {
                cachedK = 1.0f;
            }
        } catch (Throwable t) {
            cachedK = 1.0f;
        }
    }

    public static float k() {
        return cachedK;
    }

    public static float step(float cur, float target, float rate) {
        float d = target - cur;
        if (Math.abs(d) < 0.002f) {
            return target;
        }
        float f = 1.0f - (float) Math.exp(-rate * dt);
        return cur + d * f;
    }

    public static float ease(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    public static int tint(int argb, int alpha) {
        return alpha << 24 | argb & 0xFFFFFF;
    }

    public static int a(int argb, float mul) {
        int al = (int) ((argb >>> 24) * mul);
        if (al < 0) {
            al = 0;
        }
        if (al > 255) {
            al = 255;
        }
        return al << 24 | argb & 0xFFFFFF;
    }

    public static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    public static int mixA(int a, int b, float t) {
        int aa = a >>> 24;
        int ba = b >>> 24;
        int al = (int) (aa + (ba - aa) * t);
        return al << 24 | mix(a, b, t) & 0xFFFFFF;
    }

    public static int lighten(int c, float t) {
        return (c & 0xFF000000) | mix(c, 0xFFFFFFFF, t) & 0xFFFFFF;
    }

    public static int darken(int c, float t) {
        return (c & 0xFF000000) | mix(c, 0xFF000000, t) & 0xFFFFFF;
    }

    private static int fa(int c) {
        if (alpha >= 0.999f) {
            return c;
        }
        return a(c, alpha);
    }

    private static void box(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
        if (x1 <= x0 || y1 <= y0 || (c >>> 24) == 0) {
            return;
        }
        fillCount++;
        g.fill(x0, y0, x1, y1, c);
    }

    private static void grad(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c1, int c2) {
        if (x1 <= x0 || y1 <= y0 || ((c1 >>> 24) == 0 && (c2 >>> 24) == 0)) {
            return;
        }
        fillCount++;
        if (c1 == c2) {
            g.fill(x0, y0, x1, y1, c1);
        } else {
            g.fillGradient(x0, y0, x1, y1, c1, c2);
        }
    }

    private static float inset(int R, int i) {
        float dy = R - (i + 0.5f);
        float dx = (float) Math.sqrt(Math.max(0.0f, (float) R * R - dy * dy));
        return R - dx;
    }

    static void pBegin(GuiGraphicsExtractor g) {
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        float k = k();
        p.scale(1.0f / k, 1.0f / k);
    }

    static void pEnd(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    static void rrPhys(GuiGraphicsExtractor g, int X0, int Y0, int X1, int Y1, int R, int c1, int c2, boolean aa) {
        int W = X1 - X0;
        int H = Y1 - Y0;
        if (W <= 0 || H <= 0) {
            return;
        }
        R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
        boolean grad = c1 != c2;
        if (R == 0) {
            grad(g, X0, Y0, X1, Y1, c1, c2);
            return;
        }

        int[] full = R <= FULL_BUF.length ? FULL_BUF : new int[R];
        for (int i = 0; i < R; i++) {
            full[i] = Math.round(inset(R, i));
        }

        int k = R;
        while (k > 0 && full[k - 1] == 0) {
            k--;
        }

        if (grad) {
            for (int i = 0; i < k; i++) {
                int yt = Y0 + i;
                int ct = mixA(c1, c2, (i + 0.5f) / H);
                box(g, X0 + full[i], yt, X1 - full[i], yt + 1, ct);
            }
            int my0 = Y0 + k;
            int my1 = Y1 - k;
            if (my1 > my0) {
                int ct = mixA(c1, c2, (float) k / H);
                int cb = mixA(c1, c2, (float) (H - k) / H);
                grad(g, X0, my0, X1, my1, ct, cb);
            }
            for (int i = 0; i < k; i++) {
                int yb = Y1 - 1 - i;
                int cb = mixA(c1, c2, (H - i - 0.5f) / H);
                box(g, X0 + full[i], yb, X1 - full[i], yb + 1, cb);
            }
        } else {
            for (int i = 0; i < k; ) {
                int start = i;
                int f = full[start];
                while (i + 1 < k && full[i + 1] == f) {
                    i++;
                }
                int count = i - start + 1;
                box(g, X0 + f, Y0 + start, X1 - f, Y0 + start + count, c1);
                box(g, X0 + f, Y1 - start - count, X1 - f, Y1 - start, c1);
                i++;
            }
            int my0 = Y0 + k;
            int my1 = Y1 - k;
            if (my1 > my0) {
                box(g, X0, my0, X1, my1, c1);
            }
        }
    }

    static void ringPhys(GuiGraphicsExtractor g, int X0, int Y0, int X1, int Y1, int R, int T, int c, boolean aa) {
        int W = X1 - X0;
        int H = Y1 - Y0;
        if (W <= 0 || H <= 0 || (c >>> 24) == 0) {
            return;
        }
        R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
        T = Math.max(1, Math.min(T, Math.min(W, H) / 2));
        if (R == 0 || T >= Math.min(W, H) / 2) {
            box(g, X0, Y0, X1, Y0 + T, c);
            box(g, X0, Y1 - T, X1, Y1, c);
            box(g, X0, Y0 + T, X0 + T, Y1 - T, c);
            box(g, X1 - T, Y0 + T, X1, Y1 - T, c);
            return;
        }
        T = Math.min(T, R);
        int Ri = R - T;

        int[] oFull = R <= FULL_BUF.length ? FULL_BUF : new int[R];
        for (int i = 0; i < R; i++) {
            oFull[i] = Math.round(inset(R, i));
        }
        int[] iFull = R <= IFULL_BUF.length ? IFULL_BUF : new int[R];
        for (int i = T; i < R; i++) {
            int j = i - T;
            iFull[i] = j < Ri ? Math.round(inset(Ri, j)) : 0;
        }

        int k = R;
        while (k > T && oFull[k - 1] == 0 && (Ri <= 0 || (k - 1 - T < Ri && iFull[k - 1] == 0))) {
            k--;
        }

        for (int i = 0; i < Math.min(T, k); i++) {
            int oL = X0 + oFull[i];
            int oR = X1 - oFull[i];
            box(g, oL, Y0 + i, oR, Y0 + i + 1, c);
            box(g, oL, Y1 - 1 - i, oR, Y1 - i, c);
        }

        for (int i = T; i < k; ) {
            int start = i;
            int oL = X0 + oFull[start];
            int oR = X1 - oFull[start];
            int iL = X0 + T + iFull[start];
            int iR = X1 - T - iFull[start];
            while (i + 1 < k && oFull[i + 1] == oFull[start] && iFull[i + 1] == iFull[start]) {
                i++;
            }
            int count = i - start + 1;
            int yt = Y0 + start;
            int yb = Y1 - start - count;
            if (iL > oL) {
                box(g, oL, yt, iL, yt + count, c);
                box(g, oL, yb, iL, yb + count, c);
            }
            if (oR > iR) {
                box(g, iR, yt, oR, yt + count, c);
                box(g, iR, yb, oR, yb + count, c);
            }
            i++;
        }

        int vy0 = Y0 + k;
        int vy1 = Y1 - k;
        if (vy1 > vy0) {
            box(g, X0, vy0, X0 + T, vy1, c);
            box(g, X1 - T, vy0, X1, vy1, c);
        }
    }

    private static int P(float v, float k) {
        return Math.round(v * k);
    }

    public static void rr(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
        rrGrad(g, x, y, w, h, r, color, color);
    }

    public static void rrGrad(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        top = fa(top);
        bottom = fa(bottom);
        if ((top >>> 24) == 0 && (bottom >>> 24) == 0) {
            return;
        }
        primCount++;
        float k = k();
        int X0 = P(x, k);
        int Y0 = P(y, k);
        int X1 = P(x + w, k);
        int Y1 = P(y + h, k);
        if (Math.abs(w - h) < 0.001f) {
            int side = X1 - X0;
            Y1 = Y0 + side;
        }
        pBegin(g);
        rrPhys(g, X0, Y0, X1, Y1, P(r, k), top, bottom, false);
        pEnd(g);
    }

    public static void ring(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float thick, int color) {
        color = fa(color);
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        primCount++;
        float k = k();
        int X0 = P(x, k);
        int Y0 = P(y, k);
        int X1 = P(x + w, k);
        int Y1 = P(y + h, k);
        if (Math.abs(w - h) < 0.001f) {
            int side = X1 - X0;
            Y1 = Y0 + side;
        }
        pBegin(g);
        ringPhys(g, X0, Y0, X1, Y1, P(r, k), Math.max(1, P(thick, k)), color, false);
        pEnd(g);
    }

    public static void shadowSoft(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float spread, float dy, int color) {
        color = fa(color);
        if ((color >>> 24) == 0) {
            return;
        }
        float k = k();
        int T = Math.max(1, P(spread / 4.0f, k));
        int L = 4;
        pBegin(g);
        int X0 = P(x, k);
        int Y0 = P(y + dy, k);
        int X1 = P(x + w, k);
        int Y1 = P(y + dy + h, k);
        int R = P(r, k);
        for (int n = 1; n <= L; n++) {
            float f = 1.0f - (n - 1) / (float) L;
            int c = a(color, f * f * 0.9f);
            int e = (n - 1) * T;
            ringPhys(g, X0 - e - T, Y0 - e - T, X1 + e + T, Y1 + e + T, R + e + T, T, c, false);
        }
        pEnd(g);
    }

    public static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float thick, int color) {
        color = fa(color);
        if ((color >>> 24) == 0) {
            return;
        }
        primCount++;
        float k = k();
        pBegin(g);
        float ax = x0 * k;
        float ay = y0 * k;
        float bx = x1 * k;
        float by = y1 * k;
        int t = Math.max(1, Math.round(thick * k));
        // Achsenparallele Linien (der Normalfall bei Icons/Rahmen) als
        // einzelnes Rechteck statt DDA-Pixel — identisches Bild, ~20x weniger Fills.
        if (Math.abs(by - ay) < 0.5f) {
            int y = Math.round(ay - t / 2.0f);
            int xa = Math.round(Math.min(ax, bx));
            int xb = Math.round(Math.max(ax, bx));
            box(g, xa, y, xb + 1, y + t, color);
            pEnd(g);
            return;
        }
        if (Math.abs(bx - ax) < 0.5f) {
            int x = Math.round(ax - t / 2.0f);
            int ya = Math.round(Math.min(ay, by));
            int yb = Math.round(Math.max(ay, by));
            box(g, x, ya, x + t, yb + 1, color);
            pEnd(g);
            return;
        }
        int steps = (int) Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        if (steps < 1) {
            steps = 1;
        }
        int px = Integer.MIN_VALUE;
        int py = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            int cx = Math.round(ax + (bx - ax) * f - t / 2.0f);
            int cy = Math.round(ay + (by - ay) * f - t / 2.0f);
            if (cx == px && cy == py) {
                continue;
            }
            px = cx;
            py = cy;
            box(g, cx, cy, cx + t, cy + t, color);
        }
        pEnd(g);
    }

    public static void disc(GuiGraphicsExtractor g, float cx, float cy, float rad, int color) {
        color = fa(color);
        if ((color >>> 24) == 0 || rad <= 0.0f) {
            return;
        }
        primCount++;
        float k = k();
        int R = Math.max(1, Math.round(rad * k));
        int Xc = Math.round(cx * k);
        int Yc = Math.round(cy * k);
        pBegin(g);
        rrPhys(g, Xc - R, Yc - R, Xc + R, Yc + R, R, color, color, false);
        pEnd(g);
    }

    public static void fillRound(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        rr(g, x, y, w, h, r, color);
    }

    public static void fillRoundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int topColor, int bottomColor) {
        rrGrad(g, x, y, w, h, r, topColor, bottomColor);
    }

    public static void glassCard(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int tint, float op, float shadow) {
        glassCard(g, x, y, w, h, r, tint, op, shadow, 0x38FFFFFF);
    }

    public static void glassCard(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int tint, float op, float shadow, int ringCol) {
        if (shadow > 0.0f) {
            shadowSoft(g, x, y, w, h, r, 10.0f, 3.0f, Palette.tint(0x000000, (int) (70 * shadow)));
        }
        int top = Palette.mix(Palette.mix(tint, 0xFFFFFFFF, 0.07f), 0xFFFFFFFF, 0.10f);
        int bot = Palette.mix(Palette.mix(tint, 0xFF000000, 0.12f), 0xFF000000, 0.03f);
        rrGrad(g, x, y, w, h, r, tint(top, (int) (255 * Math.min(1.0f, op))), tint(bot, (int) (255 * Math.min(1.0f, op * 0.92f))));
        if ((ringCol >>> 24) != 0) {
            ring(g, x, y, w, h, r, 0.5f, ringCol);
        }
        float k = k();
        if (w > r * 2 + 4) {
            pBegin(g);
            int X0 = P(x + r, k);
            int X1 = P(x + w - r, k);
            int Y = P(y, k) + 1;
            box(g, X0, Y, X1, Y + 1, fa(0x44FFFFFF));
            pEnd(g);
        }
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border, int sheen) {
        glassCard(g, x, y, w, h, r, fill, (fill >>> 24) / 255.0f, 0.0f);
    }

    public static void panelVanilla(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        glassCard(g, x, y, w, h, r, Palette.PANEL, opacity + 0.12f, 0.6f);
    }

    public static void panelDark(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        glassCard(g, x, y, w, h, r, Palette.PANEL, opacity, 0.0f);
    }

    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int radius) {
        shadowSoft(g, x, y, w, h, r, radius, 0.0f, 0x55000000);
    }

    public static void dropShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int offset, int radius) {
        shadowSoft(g, x, y, w, h, r, radius, offset, 0x66000000);
    }

    public static int textW(Font font, String s) {
        return font.width(s);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        color = fa(color);
        if ((color >>> 24) < 10 || s == null || s.isEmpty()) {
            return;
        }
        primCount++;
        g.text(font, s, x, y, color, shadow);
    }

    public static void textC(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color, boolean shadow) {
        text(g, font, s, cx - font.width(s) / 2, y, color, shadow);
    }

    public static void textR(GuiGraphicsExtractor g, Font font, String s, int rx, int y, int color, boolean shadow) {
        text(g, font, s, rx - font.width(s), y, color, shadow);
    }

    public static void textMid(GuiGraphicsExtractor g, Font font, String s, int x, int y, int h, int color, boolean shadow) {
        text(g, font, s, x, y + (h - 8) / 2, color, shadow);
    }

    public static void textScaled(GuiGraphicsExtractor g, Font font, String s, float x, float y, float sc, int color, boolean shadow) {
        color = fa(color);
        if ((color >>> 24) < 10 || s == null || s.isEmpty()) {
            return;
        }
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(sc, sc);
        g.text(font, s, 0, 0, color, shadow);
        p.popMatrix();
    }

    public static void textBold(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        color = fa(color);
        if ((color >>> 24) < 10 || s == null || s.isEmpty()) {
            return;
        }
        primCount++;
        Matrix3x2fStack p = g.pose();
        g.text(font, s, x, y, color, shadow);
        p.pushMatrix();
        p.translate(0.5f, 0.0f);
        g.text(font, s, x, y, color, false);
        p.popMatrix();
    }

    public static String trunc(Font font, String s, int maxW) {
        if (s == null) {
            return "";
        }
        if (font.width(s) <= maxW) {
            return s;
        }
        // PERF: Binäre Suche statt linearem Kürzen — trunc() läuft pro Frame für
        // dutzende Strings; linear ist O(n^2) durch wiederholtes Neuvermessen.
        int ew = font.width("...");
        int lo = 1;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (font.width(s.substring(0, mid)) + ew <= maxW) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, lo) + "...";
    }

    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border, int text, Font font, String label, boolean hover, boolean selected) {
        int f = selected ? tint(fill, 230) : (hover ? tint(fill, 176) : fill);
        rr(g, x, y, w, h, r, f);
        ring(g, x, y, w, h, r, 0.5f, border);
        textC(g, font, label, x + w / 2, y + (h - 8) / 2, text, false);
    }

    public static void pill(GuiGraphicsExtractor g, float x, float y, float w, float h, float hov, boolean primary, boolean disabled) {
        float r = Math.min(w, h) / 2.0f;
        if (primary) {
            // PERF: Glanz in Verlauf eingebacken (ein rrGrad statt zwei).
            int c1 = Palette.ACCENT;
            int c2 = Palette.ACCENT2;
            int top = mix(lighten(c1, 0.1f + 0.12f * hov), 0xFFFFFFFF, 0.10f);
            int bot = mix(c1, c2, 0.8f);
            rrGrad(g, x, y, w, h, r, a(top, disabled ? 0.4f : 0.95f), a(bot, disabled ? 0.4f : 0.95f));
            ring(g, x, y, w, h, r, 0.5f, 0x55FFFFFF);
        } else {
            // PERF: Solider Button + Glanz eingebacken (kein separater Sheen-Pass).
            int base = mix(mix(Palette.PANEL2, 0xFFFFFFFF, 0.04f + 0.12f * hov), 0xFFFFFFFF, 0.08f);
            rr(g, x, y, w, h, r, a(base, disabled ? 0.35f : 0.75f));
            ring(g, x, y, w, h, r, 0.5f, mixA(0x30FFFFFF, a(Palette.ACCENT, 0.9f), hov * 0.8f));
        }
    }

    public static void buttonVanilla(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, Font font, String label, boolean hover, boolean selected, boolean disabled) {
        pill(g, x, y, w, h, hover ? 1.0f : 0.0f, selected, disabled);
        textC(g, font, label, x + w / 2, y + (h - 8) / 2, disabled ? Palette.MUTED : (selected ? 0xFFFFFFFF : Palette.TEXT), false);
    }

    public static void vanillaButton(GuiGraphicsExtractor g, int x, int y, int w, int h, Font font, String label, boolean hovered, boolean focused, boolean disabled) {
        float r = Math.min(h / 2.0f, 8.0f);
        rr(g, x, y, w, h, r, a(mix(Palette.PANEL2, 0xFFFFFFFF, hovered ? 0.14f : 0.05f), disabled ? 0.35f : 0.78f));
        rrGrad(g, x, y, w, h * 0.5f, r, 0x1CFFFFFF, 0x00FFFFFF);
        ring(g, x, y, w, h, r, 0.5f, hovered && !disabled ? a(Palette.ACCENT, 0.9f) : (focused ? a(Palette.ACCENT, 0.6f) : 0x30FFFFFF));
        textC(g, font, label, x + w / 2, y + (h - 8) / 2, disabled ? Palette.MUTED : Palette.TEXT, false);
    }

    public static void checkbox(GuiGraphicsExtractor g, int x, int y, int size, boolean checked, boolean hover, boolean focused) {
        vanillaCheckbox(g, x, y, size, checked, hover, focused);
    }

    public static void sliderTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        rr(g, x, y, w, h, r, color);
    }

    public static void sliderThumb(GuiGraphicsExtractor g, int x, int y, int size, int r, int color, boolean hover) {
        disc(g, x + size / 2.0f, y + size / 2.0f, size / 2.0f, hover ? lighten(color, 0.2f) : color);
    }

    public static void scrollbarTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        rr(g, x, y, w, h, r, 0x22FFFFFF);
    }

    public static void scrollbarThumb(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, boolean hover) {
        rr(g, x, y, w, h, r, hover ? a(Palette.ACCENT, 0.9f) : 0x66FFFFFF);
    }

    public static void tooltipBackground(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        glassCard(g, x, y, w, h, r, Palette.BG, 0.9f, 0.8f);
    }

    public static void drawHollowRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        ring(g, x, y, w, h, 0, 0.5f, color);
    }

    public static void drawSelectionQuad(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        float s = 3.0f;
        float t = 1.0f;
        line(g, x, y, x + s, y, t, color);
        line(g, x, y, x, y + s, t, color);
        line(g, x + w, y, x + w - s, y, t, color);
        line(g, x + w, y, x + w, y + s, t, color);
        line(g, x, y + h, x + s, y + h, t, color);
        line(g, x, y + h, x, y + h - s, t, color);
        line(g, x + w, y + h, x + w - s, y + h, t, color);
        line(g, x + w, y + h, x + w, y + h - s, t, color);
    }

    public static void separator(GuiGraphicsExtractor g, int x, int y, int w, int color) {
        color = fa(color);
        if ((color >>> 24) == 0 || w <= 0) {
            return;
        }
        float k = k();
        pBegin(g);
        box(g, P(x, k), P(y, k), P(x + w, k), P(y, k) + 1, color);
        pEnd(g);
    }

    public static void vanillaSlider(GuiGraphicsExtractor g, int x, int y, int w, int h, float value, boolean hovered, boolean dragging) {
        float th = 4.0f;
        float ty = y + (h - th) / 2.0f;
        rr(g, x, ty, w, th, th / 2, 0x33FFFFFF);
        float v = Math.max(0.0f, Math.min(1.0f, value));
        float knob = Math.min(12.0f, h);
        float kx = x + knob / 2 + (w - knob) * v;
        float fw = kx - x;
        rrGrad(g, x, ty, fw, th, th / 2, a(Palette.ACCENT, 0.95f), a(Palette.ACCENT2, 0.95f));
        if (hovered || dragging) {
            disc(g, kx, y + h / 2.0f, knob / 2 + 2, a(Palette.ACCENT, 0.25f));
        }
        disc(g, kx, y + h / 2.0f, knob / 2, 0xFFFFFFFF);
        disc(g, kx, y + h / 2.0f, knob / 2 - 2.5f, Palette.ACCENT);
    }

    public static void vanillaCheckbox(GuiGraphicsExtractor g, int x, int y, int size, boolean checked, boolean hovered, boolean focused) {
        float r = Math.max(3.0f, size / 4.0f);
        if (checked) {
            rrGrad(g, x, y, size, size, r, Palette.ACCENT, Palette.ACCENT2);
            line(g, x + size * 0.27f, y + size * 0.52f, x + size * 0.44f, y + size * 0.7f, 1.5f, 0xFFFFFFFF);
            line(g, x + size * 0.44f, y + size * 0.7f, x + size * 0.74f, y + size * 0.3f, 1.5f, 0xFFFFFFFF);
        } else {
            rr(g, x, y, size, size, r, 0x22FFFFFF);
        }
        ring(g, x, y, size, size, r, 0.5f, hovered ? a(Palette.ACCENT, 0.9f) : (focused ? a(Palette.ACCENT, 0.7f) : 0x40FFFFFF));
    }

    public static void vanillaScrollbar(GuiGraphicsExtractor g, int x, int y, int w, int h, float scroll, float maxScroll, boolean hovered, boolean dragging) {
        rr(g, x, y, w, h, w / 2.0f, 0x1CFFFFFF);
        if (maxScroll <= 0) {
            return;
        }
        float thumbH = Math.max(18.0f, h * h / (h + maxScroll));
        float ty = y + (h - thumbH) * (scroll / maxScroll);
        rr(g, x, ty, w, thumbH, w / 2.0f, dragging || hovered ? a(Palette.ACCENT, 0.95f) : 0x77FFFFFF);
    }

    public static void vanillaTooltip(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        tooltipBackground(g, x, y, w, h, 5);
    }

    public static void vanillaPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        glassCard(g, x, y, w, h, 12, Palette.PANEL, opacity + 0.1f, 1.0f);
    }

    public static void vanillaTab(GuiGraphicsExtractor g, int x, int y, int w, int h, Font font, String label, boolean selected, boolean hovered) {
        if (selected) {
            rrGrad(g, x, y, w, h, h / 2.0f, a(Palette.ACCENT, 0.9f), a(Palette.ACCENT2, 0.9f));
        } else if (hovered) {
            rr(g, x, y, w, h, h / 2.0f, 0x22FFFFFF);
        }
        textC(g, font, label, x + w / 2, y + (h - 8) / 2, selected ? 0xFFFFFFFF : Palette.TEXT, false);
    }

    public static void badge(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int bgColor, int textColor, Font font, String text) {
        rr(g, x, y, w, h, h / 2.0f, a(bgColor, 0.28f));
        ring(g, x, y, w, h, h / 2.0f, 0.5f, a(bgColor, 0.8f));
        textC(g, font, text, x + w / 2, y + (h - 8) / 2, textColor, false);
    }

    public static boolean in(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
