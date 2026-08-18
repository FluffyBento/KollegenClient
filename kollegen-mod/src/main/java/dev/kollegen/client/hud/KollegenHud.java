package dev.kollegen.client.hud;

import dev.kollegen.client.social.SocialData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Zeichnet das Kollegen-HUD (ohne fabric-api) direkt in den vanilla
 * {@code Gui}-Render-Pfad:
 *  - oben rechts: eigenes Profil (Skin-Kopf, Name, UUID) – klickbar
 *  - oben links:  Freundesliste mit Server-Status
 *
 * Die Profil-Widget-Geometrie wird in statischen Feldern gehalten, damit der
 * {@code MouseHandlerMixin} den Klick exakt treffen kann.
 */
public final class KollegenHud {
    public static int profileX = 0;
    public static int profileY = 0;
    public static int profileW = 168;
    public static int profileH = 40;

    public static int friendsX = 0;
    public static int friendsY = 0;
    public static int friendsW = 160;
    public static int friendsH = 0;

    private static long lastLoad = 0;
    private static SocialData social = new SocialData();

    private KollegenHud() {
    }

    public static void render(GuiGraphics g, Minecraft mc) {
        // Nur im Spiel (kein Menü offen) – sonst überlagern wir Menüs.
        if (mc.screen != null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        long now = System.currentTimeMillis();
        if (now - lastLoad > 1000) {
            lastLoad = now;
            social = SocialData.load();
        }

        // ── Profil-Widget oben rechts ──
        profileW = 168;
        profileH = 40;
        profileX = sw - profileW - 6;
        profileY = 6;

        g.fill(profileX, profileY, profileX + profileW, profileY + profileH, 0x80000000);
        g.fill(profileX, profileY, profileX + profileW, profileY + 1, 0xFF4CAF50);

        var skin = mc.player != null ? mc.player.getSkinTextures().texture() : null;
        if (skin != null) {
            g.blit(skin, profileX + 4, profileY + 4, 32, 32, 8f, 8f, 8, 8, 64, 64);
        }

        String name = social.meName();
        if (name == null && mc.player != null) name = mc.player.getName().getString();
        g.drawString(mc.font, name != null ? name : "Spieler", profileX + 42, profileY + 6, 0xFFFFFFFF);

        String uuid = social.meUuid();
        if (uuid != null) {
            g.drawString(mc.font, truncate(uuid, 20), profileX + 42, profileY + 20, 0xFF9E9E9E);
        }
        g.drawString(mc.font, "Profil öffnen", profileX + 42, profileY + 30, 0xFF4CAF50);

        // ── Freundesliste oben links ──
        int fx = 6;
        int fy = 6;
        int listH = 16 + Math.max(1, social.friendCount()) * 14 + 6;
        friendsX = fx;
        friendsY = fy;
        friendsW = 160;
        friendsH = listH;
        g.fill(fx, fy, fx + 160, fy + listH, 0x80000000);
        g.drawString(mc.font, "Freunde", fx + 6, fy + 4, 0xFF4CAF50);

        int ly = fy + 18;
        for (SocialData.Friend f : social.friends()) {
            int col = f.online() ? 0xFF4CAF50 : 0xFF888888;
            g.drawString(mc.font, (f.online() ? "● " : "○ ") + truncate(f.name(), 18), fx + 6, ly, col);
            ly += 14;
        }
        if (social.friendCount() == 0) {
            g.drawString(mc.font, "(keine / nicht verbunden)", fx + 6, ly, 0xFF888888);
        }
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n - 1) + "…" : s;
    }
}
