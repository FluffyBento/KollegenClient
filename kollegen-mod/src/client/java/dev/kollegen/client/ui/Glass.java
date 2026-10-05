package dev.kollegen.client.ui;

import dev.kollegen.client.mods.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    public static void fillRound(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
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

    public static void fillRoundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
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

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
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

    public static void panelVanilla(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
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

    public static void panelDark(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        fillRound(g, x, y, w, h, r, Palette.BORDER);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), Palette.PANEL);
    }

    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int radius) {
        int layers = Math.min(radius, 8);
        for (int i = layers; i > 0; i--) {
            int alpha = (int) (0x18 * (1f - (float) i / layers));
            int col = tint(Palette.SHADOW, alpha);
            fillRound(g, x - i, y - i, w + i * 2, h + i * 2, r + i, col);
        }
    }

    public static void dropShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int offset, int radius) {
        int layers = Math.min(radius, 8);
        for (int i = layers; i > 0; i--) {
            int alpha = (int) (0x20 * (1f - (float) i / layers));
            int col = tint(Palette.SHADOW, alpha);
            fillRound(g, x - i + offset, y - i + offset, w + i * 2, h + i * 2, r + i, col);
        }
    }

    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
                              int fill, int border, int text, Font font, String label,
                              boolean hover, boolean selected) {
        int f = selected ? tint(fill, 0xE6) : (hover ? tint(fill, 0xB0) : fill);
        int b = selected ? border : (hover ? tint(border, 0xCC) : border);
        fillRound(g, x, y, w, h, r, b);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), f);
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2, text, false);
    }

    public static void buttonVanilla(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
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
        g.text(font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2, textCol, false);
    }

    public static void checkbox(GuiGraphicsExtractor g, int x, int y, int size, boolean checked,
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

    public static void sliderTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        fillRound(g, x, y, w, h, r, color);
    }

    public static void sliderThumb(GuiGraphicsExtractor g, int x, int y, int size, int r, int color, boolean hover) {
        int col = hover ? tint(color, 0xE0) : color;
        fillRound(g, x, y, size, size, r, col);
        if (hover) {
            fillRound(g, x + 1, y + 1, size - 2, size - 2, Math.max(0, r - 1), tint(Palette.TEXT, 0x30));
        }
    }

    public static void scrollbarTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        fillRound(g, x, y, w, h, r, Palette.SCROLL_TRACK);
    }

    public static void scrollbarThumb(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, boolean hover) {
        int col = hover ? Palette.SCROLL_THUMB_HOVER : Palette.SCROLL_THUMB;
        fillRound(g, x, y, w, h, r, col);
    }

    public static void tooltipBackground(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
        fillRound(g, x, y, w, h, r, Palette.BORDER);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), tint(Palette.BG, 0xF0));
        dropShadow(g, x, y, w, h, r, 2, 6);
    }

    public static void drawHollowRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawSelectionQuad(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
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

    public static void separator(GuiGraphicsExtractor g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, tint(color, 0x40));
    }

    // Vanilla-style button (matches Minecraft's button rendering)
    public static void vanillaButton(GuiGraphicsExtractor g, int x, int y, int w, int h, Font font, String label,
                                      boolean hovered, boolean focused, boolean disabled) {
        // Button background - matches vanilla Minecraft button
        int baseColor = 0xFF000000;
        if (disabled) {
            g.fill(x, y, x + w, y + h, 0xFF7F7F7F);
        } else if (hovered) {
            g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
        } else {
            g.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        }
        
        // Border
        g.fill(x, y, x + w, y + 1, 0xFF404040);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF404040);
        g.fill(x, y, x + 1, y + h, 0xFF404040);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF404040);
        
        // Inner highlight
        if (!disabled) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
        }
        
        // Text
        int textColor = disabled ? 0xFF808080 : 0xFFE0E0E0;
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2, textColor, true);
    }

    // Vanilla-style slider (matches Minecraft's options slider)
    public static void vanillaSlider(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                      float value, boolean hovered, boolean dragging) {
        int trackY = y + h / 2 - 2;
        int trackH = 4;
        
        // Track background
        fillRound(g, x, trackY, w, trackH, 2, 0xFF3A3A3A);
        fillRound(g, x + 1, trackY + 1, w - 2, trackH - 2, 1, 0xFF2A2A2A);
        
        // Track fill (progress)
        int fillW = (int) (value * (w - 4));
        if (fillW > 0) {
            fillRound(g, x + 2, trackY + 1, fillW, trackH - 2, 1, 0xFF5555FF);
        }
        
        // Thumb
        int thumbW = 12;
        int thumbH = 16;
        int thumbX = x + 2 + (int) (value * (w - thumbW - 4));
        int thumbY = y + (h - thumbH) / 2;
        
        int thumbColor = 0xFF8888FF;
        if (dragging) thumbColor = 0xFFAAAAFF;
        else if (hovered) thumbColor = 0xFF9999FF;
        
        fillRound(g, thumbX, thumbY, thumbW, thumbH, 3, 0xFF555555);
        fillRound(g, thumbX + 1, thumbY + 1, thumbW - 2, thumbH - 2, 2, thumbColor);
    }

    // Vanilla-style checkbox/toggle (matches Minecraft's checkbox)
    public static void vanillaCheckbox(GuiGraphicsExtractor g, int x, int y, int size, boolean checked,
                                        boolean hovered, boolean focused) {
        // Box
        int borderColor = focused ? 0xFF5555FF : (hovered ? 0xFF8888FF : 0xFF808080);
        fillRound(g, x, y, size, size, 2, borderColor);
        fillRound(g, x + 1, y + 1, size - 2, size - 2, 1, 0xFF1A1A1A);
        
        if (checked) {
            // Checkmark
            g.fill(x + size / 2 - 1, y + 2, x + size / 2 + 1, y + size - 2, 0xFF00FF00);
            g.fill(x + 2, y + size / 2 - 1, x + size - 2, y + size / 2 + 1, 0xFF00FF00);
        }
        
        if (focused) {
            fillRound(g, x, y, size, size, 3, 0xFF5555FF);
        }
    }

    // Vanilla-style scrollbar (matches Minecraft's scrollbar)
    public static void vanillaScrollbar(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                         float scroll, float maxScroll, boolean hovered, boolean dragging) {
        if (maxScroll <= 0) return;
        
        int trackW = w;
        int trackX = x;
        int trackY = y;
        int trackH = h;
        
        // Track
        fillRound(g, trackX, trackY, trackW, trackH, 2, 0xFF1A1A1A);
        
        // Thumb
        float scrollRatio = maxScroll > 0 ? scroll / maxScroll : 0;
        int thumbH = Math.max(30, (int) ((float) trackH * trackH / (trackH + maxScroll)));
        int thumbY = trackY + (int) (scrollRatio * (trackH - thumbH));
        int thumbW = w - 4;
        int thumbX = x + 2;
        
        int thumbColor = dragging ? 0xFF777777 : (hovered ? 0xFF666666 : 0xFF555555);
        fillRound(g, thumbX, thumbY, thumbW, thumbH, 2, thumbColor);
    }

    // Vanilla-style tooltip background
    public static void vanillaTooltip(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        fillRound(g, x, y, w, h, 4, 0xFF323232);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, 3, 0xFF1E1E1E);
        // Border
        g.fill(x, y, x + w, y + 1, 0xFF505050);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF505050);
        g.fill(x, y, x + 1, y + h, 0xFF505050);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF505050);
    }

    // Vanilla-style panel (matches Minecraft's GUI panel)
    public static void vanillaPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        // Background
        g.fill(x, y, x + w, y + h, 0xE0101010);
        // Border
        g.fill(x, y, x + w, y + 1, 0xFF505050);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF505050);
        g.fill(x, y, x + 1, y + h, 0xFF505050);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF505050);
        // Inner border
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF303030);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF303030);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF303030);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF303030);
    }

    // Vanilla-style tab (matches Minecraft's tab buttons)
    public static void vanillaTab(GuiGraphicsExtractor g, int x, int y, int w, int h, Font font, String label,
                                   boolean selected, boolean hovered) {
        int bgColor = selected ? 0xFF3A3A3A : (hovered ? 0xFF2A2A2A : 0xFF1A1A1A);
        g.fill(x, y, x + w, y + h, bgColor);
        
        // Border
        int borderColor = selected ? 0xFF5555FF : 0xFF505050;
        g.fill(x, y, x + w, y + 1, borderColor);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF505050);
        g.fill(x, y, x + 1, y + h, 0xFF505050);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF505050);
        
        // Text
        int textColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFAAAAAA : 0xFFAAAAAA);
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2, textColor, true);
    }

    // Vanilla-style badge (matches Minecraft's badges)
    public static void badge(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
                             int bgColor, int textColor, Font font, String text) {
        fillRound(g, x, y, w, h, r, bgColor);
        int tw = font.width(text);
        g.text(font, text, x + (w - tw) / 2, y + (h - font.lineHeight) / 2, textColor, false);
    }
}