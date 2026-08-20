package dev.kollegen.client.menu;

import dev.kollegen.client.theme.ThemeSync;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/**
 * Essential-artiger „Freunde"-Button (oben rechts). Erscheint auf dem
 * Startbildschirm und im Spiel (HUD), damit man das Social-Menü schnell
 * erreicht – unabhängig vom Mod-Menü (Rechts-Shift).
 */
public final class SocialButton {

    private SocialButton() {
    }

    public static int[] rect(Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int bw = 140, bh = 30;
        int bx = sw - 20 - bw;
        int by = 18;
        return new int[]{bx, by, bw, bh};
    }

    public static void draw(GuiGraphics g, Minecraft mc, int mx, int my) {
        int[] r = rect(mc);
        int bx = r[0], by = r[1], bw = r[2], bh = r[3];

        ThemeSync.refresh();
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int panel = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int text = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);

        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        Font font = mc.font;
        Glass.button(g, bx, by, bw, bh, 9,
                Glass.tint(panel, 0xCC), Glass.tint(accent, 0xAA), text, font,
                "Freunde", hover, false);
    }

    public static boolean hit(Minecraft mc, double mx, double my) {
        int[] r = rect(mc);
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }
}
