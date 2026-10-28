package dev.kollegen.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Icons {
    public static final int GRID = 0;
    public static final int BARS = 1;
    public static final int TARGET = 2;
    public static final int GLOBE = 3;
    public static final int CHAT = 4;
    public static final int BOLT = 5;
    public static final int GEAR = 6;
    public static final int BAG = 7;
    public static final int SPARKLE = 8;
    public static final int PAW = 9;
    public static final int SEARCH = 10;
    public static final int CLOSE = 11;
    public static final int CHECK = 12;
    public static final int CHEVRON = 13;
    public static final int LOCK = 14;
    public static final int MOVE = 15;
    public static final int USER = 16;
    public static final int PALETTE = 17;
    public static final int COIN = 18;
    public static final int BACK = 19;
    public static final int SLIDERS = 20;
    public static final int HOME = 21;
    public static final int WARN = 22;
    public static final int CHEVRON_DOWN = 23;
    public static final int SNAKE = 30;

    private Icons() {
    }

    public static void draw(GuiGraphicsExtractor g, int id, float x, float y, float s, int c) {
        float u = s / 24.0f;
        float t = Math.max(1.0f, s / 13.0f);
        switch (id) {
            case GRID:
                Glass.rr(g, x + 3 * u, y + 3 * u, 8 * u, 8 * u, 2.2f * u, c);
                Glass.rr(g, x + 13 * u, y + 3 * u, 8 * u, 8 * u, 2.2f * u, c);
                Glass.rr(g, x + 3 * u, y + 13 * u, 8 * u, 8 * u, 2.2f * u, c);
                Glass.rr(g, x + 13 * u, y + 13 * u, 8 * u, 8 * u, 2.2f * u, c);
                break;
            case BARS:
                Glass.rr(g, x + 3 * u, y + 12 * u, 5 * u, 9 * u, 1.6f * u, c);
                Glass.rr(g, x + 9.5f * u, y + 4 * u, 5 * u, 17 * u, 1.6f * u, c);
                Glass.rr(g, x + 16 * u, y + 8 * u, 5 * u, 13 * u, 1.6f * u, c);
                break;
            case TARGET:
                Glass.ring(g, x + 3 * u, y + 3 * u, 18 * u, 18 * u, 9 * u, t, c);
                Glass.disc(g, x + 12 * u, y + 12 * u, 2.4f * u, c);
                Glass.line(g, x + 12 * u, y + 0.5f * u, x + 12 * u, y + 6 * u, t, c);
                Glass.line(g, x + 12 * u, y + 18 * u, x + 12 * u, y + 23.5f * u, t, c);
                Glass.line(g, x + 0.5f * u, y + 12 * u, x + 6 * u, y + 12 * u, t, c);
                Glass.line(g, x + 18 * u, y + 12 * u, x + 23.5f * u, y + 12 * u, t, c);
                break;
            case GLOBE:
                Glass.ring(g, x + 2.5f * u, y + 2.5f * u, 19 * u, 19 * u, 9.5f * u, t, c);
                Glass.ring(g, x + 8 * u, y + 2.5f * u, 8 * u, 19 * u, 4 * u, t, c);
                Glass.line(g, x + 3 * u, y + 12 * u, x + 21 * u, y + 12 * u, t, c);
                break;
            case CHAT:
                Glass.rr(g, x + 2.5f * u, y + 3.5f * u, 19 * u, 14 * u, 4 * u, c);
                Glass.line(g, x + 7 * u, y + 17 * u, x + 6 * u, y + 21.5f * u, t * 1.4f, c);
                Glass.line(g, x + 6 * u, y + 21.5f * u, x + 11.5f * u, y + 17.5f * u, t * 1.4f, c);
                Glass.rr(g, x + 7 * u, y + 8 * u, 10 * u, 1.8f * u, 0.9f * u, 0x66000000);
                Glass.rr(g, x + 7 * u, y + 11.5f * u, 6.5f * u, 1.8f * u, 0.9f * u, 0x66000000);
                break;
            case BOLT:
                Glass.line(g, x + 13.5f * u, y + 2 * u, x + 6 * u, y + 13 * u, t * 1.5f, c);
                Glass.line(g, x + 6 * u, y + 13 * u, x + 12 * u, y + 13 * u, t * 1.5f, c);
                Glass.line(g, x + 12 * u, y + 13 * u, x + 9.5f * u, y + 22 * u, t * 1.5f, c);
                Glass.line(g, x + 9.5f * u, y + 22 * u, x + 18.5f * u, y + 9.5f * u, t * 1.5f, c);
                Glass.line(g, x + 18.5f * u, y + 9.5f * u, x + 12.5f * u, y + 9.5f * u, t * 1.5f, c);
                Glass.line(g, x + 12.5f * u, y + 9.5f * u, x + 13.5f * u, y + 2 * u, t * 1.5f, c);
                break;
            case GEAR:
                Glass.ring(g, x + 6 * u, y + 6 * u, 12 * u, 12 * u, 6 * u, 2.6f * u, c);
                Glass.rr(g, x + 10.5f * u, y + 2 * u, 3 * u, 4 * u, 1 * u, c);
                Glass.rr(g, x + 10.5f * u, y + 18 * u, 3 * u, 4 * u, 1 * u, c);
                Glass.rr(g, x + 2 * u, y + 10.5f * u, 4 * u, 3 * u, 1 * u, c);
                Glass.rr(g, x + 18 * u, y + 10.5f * u, 4 * u, 3 * u, 1 * u, c);
                Glass.line(g, x + 5 * u, y + 5 * u, x + 7 * u, y + 7 * u, 2.4f * u, c);
                Glass.line(g, x + 19 * u, y + 5 * u, x + 17 * u, y + 7 * u, 2.4f * u, c);
                Glass.line(g, x + 5 * u, y + 19 * u, x + 7 * u, y + 17 * u, 2.4f * u, c);
                Glass.line(g, x + 19 * u, y + 19 * u, x + 17 * u, y + 17 * u, 2.4f * u, c);
                break;
            case BAG:
                Glass.ring(g, x + 7.5f * u, y + 2.5f * u, 9 * u, 11 * u, 4.5f * u, t * 1.2f, c);
                Glass.rr(g, x + 3.5f * u, y + 8.5f * u, 17 * u, 13 * u, 3 * u, c);
                break;
            case SPARKLE:
                Glass.line(g, x + 10 * u, y + 2 * u, x + 10 * u, y + 18 * u, t * 1.3f, c);
                Glass.line(g, x + 2 * u, y + 10 * u, x + 18 * u, y + 10 * u, t * 1.3f, c);
                Glass.line(g, x + 4.5f * u, y + 4.5f * u, x + 15.5f * u, y + 15.5f * u, t, c);
                Glass.line(g, x + 15.5f * u, y + 4.5f * u, x + 4.5f * u, y + 15.5f * u, t, c);
                Glass.disc(g, x + 19 * u, y + 18 * u, 2.4f * u, c);
                Glass.disc(g, x + 19.5f * u, y + 4.5f * u, 1.5f * u, c);
                break;
            case PAW:
                Glass.rr(g, x + 6.5f * u, y + 12 * u, 11 * u, 9 * u, 4.5f * u, c);
                Glass.disc(g, x + 5 * u, y + 9 * u, 2.4f * u, c);
                Glass.disc(g, x + 9.5f * u, y + 5 * u, 2.5f * u, c);
                Glass.disc(g, x + 14.5f * u, y + 5 * u, 2.5f * u, c);
                Glass.disc(g, x + 19 * u, y + 9 * u, 2.4f * u, c);
                break;
            case SEARCH:
                Glass.ring(g, x + 3 * u, y + 3 * u, 13 * u, 13 * u, 6.5f * u, t * 1.2f, c);
                Glass.line(g, x + 14.5f * u, y + 14.5f * u, x + 21 * u, y + 21 * u, t * 1.4f, c);
                break;
            case CLOSE:
                Glass.line(g, x + 5 * u, y + 5 * u, x + 19 * u, y + 19 * u, t * 1.3f, c);
                Glass.line(g, x + 19 * u, y + 5 * u, x + 5 * u, y + 19 * u, t * 1.3f, c);
                break;
            case CHECK:
                Glass.line(g, x + 4 * u, y + 12.5f * u, x + 9.5f * u, y + 18 * u, t * 1.4f, c);
                Glass.line(g, x + 9.5f * u, y + 18 * u, x + 20 * u, y + 6 * u, t * 1.4f, c);
                break;
            case CHEVRON:
                Glass.line(g, x + 9 * u, y + 5 * u, x + 16 * u, y + 12 * u, t * 1.3f, c);
                Glass.line(g, x + 16 * u, y + 12 * u, x + 9 * u, y + 19 * u, t * 1.3f, c);
                break;
            case CHEVRON_DOWN:
                Glass.line(g, x + 5 * u, y + 9 * u, x + 12 * u, y + 16 * u, t * 1.3f, c);
                Glass.line(g, x + 12 * u, y + 16 * u, x + 19 * u, y + 9 * u, t * 1.3f, c);
                break;
            case BACK:
                Glass.line(g, x + 15 * u, y + 5 * u, x + 8 * u, y + 12 * u, t * 1.3f, c);
                Glass.line(g, x + 8 * u, y + 12 * u, x + 15 * u, y + 19 * u, t * 1.3f, c);
                break;
            case LOCK:
                Glass.ring(g, x + 7 * u, y + 2.5f * u, 10 * u, 13 * u, 5 * u, t * 1.2f, c);
                Glass.rr(g, x + 4 * u, y + 10 * u, 16 * u, 11.5f * u, 3 * u, c);
                break;
            case MOVE:
                Glass.line(g, x + 12 * u, y + 2.5f * u, x + 12 * u, y + 21.5f * u, t * 1.2f, c);
                Glass.line(g, x + 2.5f * u, y + 12 * u, x + 21.5f * u, y + 12 * u, t * 1.2f, c);
                Glass.line(g, x + 9 * u, y + 5.5f * u, x + 12 * u, y + 2.5f * u, t * 1.2f, c);
                Glass.line(g, x + 15 * u, y + 5.5f * u, x + 12 * u, y + 2.5f * u, t * 1.2f, c);
                Glass.line(g, x + 9 * u, y + 18.5f * u, x + 12 * u, y + 21.5f * u, t * 1.2f, c);
                Glass.line(g, x + 15 * u, y + 18.5f * u, x + 12 * u, y + 21.5f * u, t * 1.2f, c);
                Glass.line(g, x + 5.5f * u, y + 9 * u, x + 2.5f * u, y + 12 * u, t * 1.2f, c);
                Glass.line(g, x + 5.5f * u, y + 15 * u, x + 2.5f * u, y + 12 * u, t * 1.2f, c);
                Glass.line(g, x + 18.5f * u, y + 9 * u, x + 21.5f * u, y + 12 * u, t * 1.2f, c);
                Glass.line(g, x + 18.5f * u, y + 15 * u, x + 21.5f * u, y + 12 * u, t * 1.2f, c);
                break;
            case USER:
                Glass.disc(g, x + 12 * u, y + 7.5f * u, 4.5f * u, c);
                Glass.rr(g, x + 4 * u, y + 14 * u, 16 * u, 8 * u, 4 * u, c);
                break;
            case PALETTE:
                Glass.ring(g, x + 2.5f * u, y + 2.5f * u, 19 * u, 19 * u, 9.5f * u, t * 1.2f, c);
                Glass.disc(g, x + 8 * u, y + 9 * u, 1.8f * u, c);
                Glass.disc(g, x + 13 * u, y + 7 * u, 1.8f * u, c);
                Glass.disc(g, x + 16.5f * u, y + 11.5f * u, 1.8f * u, c);
                Glass.disc(g, x + 8.5f * u, y + 14.5f * u, 1.8f * u, c);
                break;
            case COIN:
                Glass.ring(g, x + 3 * u, y + 3 * u, 18 * u, 18 * u, 9 * u, t * 1.3f, c);
                Glass.rr(g, x + 10.5f * u, y + 7 * u, 3 * u, 10 * u, 1.2f * u, c);
                break;
            case SLIDERS:
                Glass.line(g, x + 3 * u, y + 7 * u, x + 21 * u, y + 7 * u, t, c);
                Glass.line(g, x + 3 * u, y + 17 * u, x + 21 * u, y + 17 * u, t, c);
                Glass.disc(g, x + 9 * u, y + 7 * u, 3 * u, c);
                Glass.disc(g, x + 16 * u, y + 17 * u, 3 * u, c);
                break;
            case HOME:
                Glass.line(g, x + 2.5f * u, y + 11.5f * u, x + 12 * u, y + 3 * u, t * 1.3f, c);
                Glass.line(g, x + 21.5f * u, y + 11.5f * u, x + 12 * u, y + 3 * u, t * 1.3f, c);
                Glass.rr(g, x + 5.5f * u, y + 11 * u, 13 * u, 10 * u, 2 * u, c);
                break;
            case WARN:
                Glass.line(g, x + 12 * u, y + 3 * u, x + 22 * u, y + 20.5f * u, t * 1.2f, c);
                Glass.line(g, x + 12 * u, y + 3 * u, x + 2 * u, y + 20.5f * u, t * 1.2f, c);
                Glass.line(g, x + 2 * u, y + 20.5f * u, x + 22 * u, y + 20.5f * u, t * 1.2f, c);
                Glass.rr(g, x + 11 * u, y + 9 * u, 2 * u, 6 * u, 1 * u, c);
                Glass.disc(g, x + 12 * u, y + 17.5f * u, 1.2f * u, c);
                break;
            case SNAKE:
                Glass.line(g, x + 5 * u, y + 14 * u, x + 11 * u, y + 8 * u, t * 1.2f, c);
                Glass.line(g, x + 11 * u, y + 8 * u, x + 17 * u, y + 14 * u, t * 1.2f, c);
                Glass.line(g, x + 17 * u, y + 14 * u, x + 21 * u, y + 8 * u, t * 1.2f, c);
                Glass.disc(g, x + 5 * u, y + 14 * u, 2.2f * u, c);
                Glass.disc(g, x + 21 * u, y + 8 * u, 2.2f * u, c);
                break;
            default:
                break;
        }
    }

    public static int forCategory(String name) {
        switch (name) {
            case "VISUAL":
                return PALETTE;
            case "HUD":
                return BARS;
            case "GAMEPLAY":
                return TARGET;
            case "WORLD":
                return GLOBE;
            case "CHAT":
                return CHAT;
            case "PERFORMANCE":
                return BOLT;
            case "MISC":
                return GEAR;
            case "STORE":
                return BAG;
            case "COSMETICS":
                return SPARKLE;
            case "PETS":
                return PAW;
            default:
                return GRID;
        }
    }
}
