package dev.kollegen.client.mods;

/**
 * Lokale Farbpalette für das Mod-Menü (eigenständig, ohne externe Theme-Datei).
 * Alle Werte als 0xAARRGGBB.
 */
public final class Palette {
    private Palette() {
    }

    public static final int BG = 0xFF0d0d12;
    public static final int PANEL = 0xFF1a1a24;
    public static final int PANEL2 = 0xFF21212e;
    public static final int BORDER = 0xFF34303a;
    public static final int ACCENT = 0xFFf5a623;
    public static final int ACCENT2 = 0xFFff7a00;
    public static final int TEXT = 0xFFf3e9d8;
    public static final int MUTED = 0xFFb9a98c;
    public static final int DANGER = 0xFFff5b6e;
    public static final int GREEN = 0xFF3ec46d;
    public static final int BLUE = 0xFF4aa3ff;

    /** Setzt den Alpha-Kanal einer 0xAARRGGBB-Farbe. */
    public static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
