package dev.kollegen.client.menu;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.config.KollegenConfig;
import dev.kollegen.client.feature.Fullbright;
import dev.kollegen.client.render.KollegenPostFX;
import dev.kollegen.client.social.SocialData;
import dev.kollegen.client.theme.ThemeSync;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.GlassSlider;
import dev.kollegen.client.ui.GlassToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Mod-Menü (über Rechts-Shift). NoRisk-/Feather-artige Glas-Optik in den
 * Launcher-Theme-Farben. Kategorien: Profil, Einstellungen, Info.
 *
 * Alle interaktiven Elemente sind echte Widgets (GlassToggle / GlassSlider /
 * GlassButton / EditBox), damit die Eingabe über Minecrafts Widget-System
 * zuverlässig funktioniert. Hintergrund durchsichtig (Welt bleibt sichtbar).
 */
public class KollegenMenuScreen extends Screen {

    private final Screen parent;

    private static final String[] CATS = {"Profil", "Einstellungen", "Info"};
    private int category = 1; // standard: Einstellungen

    private static final int TOGGLE_W = 52, TOGGLE_H = 28;
    private static final int R = 18;

    private GlassButton[] navbar = new GlassButton[CATS.length];
    private GlassButton closeBtn;
    private GlassButton copyBtn;

    private GlassToggle tLogo, tHi, tFb;
    private GlassSlider sSat, sHi;
    private EditBox hexBox;

    private int cntX, cntY, cntW, rowStep;
    private int togRX;
    private SocialData data = new SocialData();
    private Identifier cachedSkin;

    public KollegenMenuScreen(Screen parent) {
        super(Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ThemeSync.refresh();
        data = SocialData.load();
        cachedSkin = computeSkin();
        buildWidgets();
        setCategory(category);
    }

    private Identifier computeSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) return mc.player.getSkin().body().texturePath();
        return DefaultPlayerSkin.get(parseUuid(data.meUuid())).body().texturePath();
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ═══ Geometrie ═══

    private int[] panel() {
        int w = Math.min(this.width - 60, 720);
        int h = Math.min(this.height - 60, 470);
        return new int[]{(this.width - w) / 2, (this.height - h) / 2, w, h};
    }

    // ═══ Widgets (native) ═══

    private final java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> built = new java.util.ArrayList<>();

    private void resetWidgets() {
        for (net.minecraft.client.gui.components.events.GuiEventListener w : built) removeWidget(w);
        built.clear();
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T reg(T w) {
        built.add(w);
        return addRenderableWidget(w);
    }

    private void buildWidgets() {
        resetWidgets();
        int[] p = panel();
        int px = p[0], py = p[1], pw = p[2], ph = p[3];

        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int panelC = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int textC = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);

        // Navbar (Sidebar)
        int sw = 150;
        for (int i = 0; i < CATS.length; i++) {
            int bx = px + 14, by = py + 56 + i * 42, bw = sw - 28, bh = 34;
            final int idx = i;
            GlassButton b = new GlassButton(bx, by, bw, bh, Component.literal(CATS[i]), btn -> setCategory(idx));
            b.colors(panelC, accent, textC);
            navbar[i] = reg(b);
        }

        // Schließen
        closeBtn = new GlassButton(px + pw - 36, py + 14, 24, 24, Component.literal("✕"), btn -> close());
        closeBtn.colors(panelC, accent, textC);
        reg(closeBtn);

        // Inhaltsbereich
        cntX = px + sw + 16;
        cntY = py + 54;
        cntW = pw - sw - 32;
        rowStep = 46;
        togRX = cntX + cntW - TOGGLE_W;

        // ── Einstellungen-Widgets ──
        tLogo = new GlassToggle(togRX, cntY + 0 * rowStep + 2, TOGGLE_W, TOGGLE_H,
                KollegenMod.CONFIG.replaceLogo, on -> {
            KollegenMod.CONFIG.replaceLogo = on;
            KollegenMod.CONFIG.save();
        });
        tLogo.colors(accent, muted);

        int satW = Math.min(240, cntW - 60);
        sSat = new GlassSlider(cntX, cntY + 1 * rowStep + 14, satW, 18,
                KollegenMod.CONFIG.colorSaturation / 2.0f);
        sSat.accent(accent).onChanged(v -> setSaturation((float) (v * 2)));

        tHi = new GlassToggle(togRX, cntY + 2 * rowStep + 2, TOGGLE_W, TOGGLE_H,
                KollegenMod.CONFIG.colorHighlight, on -> {
            KollegenMod.CONFIG.colorHighlight = on;
            KollegenMod.CONFIG.save();
            KollegenPostFX.applyConfig();
        });
        tHi.colors(accent, muted);

        int hexW = Math.min(140, cntW - 40);
        hexBox = new EditBox(this.font, cntX, cntY + 3 * rowStep + 14, hexW, 20, Component.literal(""));
        hexBox.setMaxLength(7);
        hexBox.setValue(KollegenMod.CONFIG.highlightColor);
        hexBox.setTextColor(textC);
        hexBox.setResponder(this::tryApplyHex);

        int hiW = Math.min(240, cntW - 60);
        sHi = new GlassSlider(cntX, cntY + 4 * rowStep + 14, hiW, 18,
                KollegenMod.CONFIG.highlightAmount);
        sHi.accent(accent).onChanged(v -> setHighlightAmount((float) (double) v));

        tFb = new GlassToggle(togRX, cntY + 5 * rowStep + 2, TOGGLE_W, TOGGLE_H,
                KollegenMod.CONFIG.fullbright, on -> {
            KollegenMod.CONFIG.fullbright = on;
            KollegenMod.CONFIG.save();
            Fullbright.reconcile();
        });
        tFb.colors(accent, muted);

        reg(tLogo);
        reg(sSat);
        reg(tHi);
        reg(hexBox);
        reg(sHi);
        reg(tFb);

        // ── Profil: Code kopieren ──
        copyBtn = new GlassButton(cntX, cntY + 200, Math.min(180, cntW), 26,
                Component.literal("Code kopieren"), btn -> copyCode());
        copyBtn.colors(panelC, accent, textC);
        reg(copyBtn);
    }

    private void setCategory(int c) {
        category = c;
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int panelC = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int textC = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);

        for (int i = 0; i < navbar.length; i++) {
            boolean sel = i == category;
            navbar[i].colors(panelC, sel ? accent : muted, sel ? accent : textC);
        }

        boolean s = category == 1;
        tLogo.visible = tLogo.active = s;
        sSat.visible = sSat.active = s;
        tHi.visible = tHi.active = s;
        hexBox.visible = hexBox.active = s;
        sHi.visible = sHi.active = s;
        tFb.visible = tFb.active = s;

        copyBtn.visible = copyBtn.active = category == 0;
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

    private void tryApplyHex(String s) {
        if (s != null && s.matches("#[0-9a-fA-F]{6}")) {
            KollegenMod.CONFIG.highlightColor = s;
            KollegenMod.CONFIG.save();
            KollegenPostFX.applyConfig();
        }
    }

    private void copyCode() {
        String code = data.meCode();
        if (code != null && !code.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
        }
    }

    // ═══ Zeichnen ═══

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Durchsichtig lassen.
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        ThemeSync.refresh();
        int bg = ThemeSync.argb(ThemeSync.get("bg", "#0d0d12"), 0xff0d0d12);
        int panelC = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int border = ThemeSync.argb(ThemeSync.get("border", "#34303a"), 0xff34303a);
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int text = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);
        Font font = this.font;

        g.fill(0, 0, this.width, this.height, Glass.tint(bg, 0x16));

        int[] p = panel();
        Glass.panel(g, p[0], p[1], p[2], p[3], R,
                Glass.tint(panelC, 0xE0), Glass.tint(border, 0xCC), Glass.tint(accent, 0xFF));
        g.fill(p[0] + 2, p[1] + 2, p[2] - 4, 1, Glass.tint(0xffffff, 0x22));

        // Sidebar-Hintergrund
        int sw = 150;
        Glass.fillRound(g, p[0] + 10, p[1] + 50, sw - 20, p[3] - 70, 12, Glass.tint(panelC, 0x90));

        g.drawString(font, "KOLLEGEN", p[0] + 20, p[1] + 18, accent, false);
        g.drawString(font, "Client", p[0] + 20 + font.width("KOLLEGEN") + 6, p[1] + 20, muted, false);

        if (category == 0) renderProfile(g, accent, panelC, border, text, muted);
        else if (category == 1) renderSettingsLabels(g, accent, text, muted);
        else renderInfo(g, accent, text, muted);

        super.render(g, mx, my, pt);
    }

    private void renderSettingsLabels(GuiGraphics g, int accent, int text, int muted) {
        Font font = this.font;
        g.drawString(font, "Minecraft-Logo durch Logo.png", cntX, cntY + 0 * rowStep + 9, text, false);
        g.drawString(font, "Farb-Sättigung", cntX, cntY + 1 * rowStep, muted, false);
        g.drawString(font, Math.round(KollegenMod.CONFIG.colorSaturation * 100) + "%",
                cntX + sSat.getWidth() + 10, cntY + 1 * rowStep + 14 + 5, text, false);
        g.drawString(font, "Farbe hervorheben", cntX, cntY + 2 * rowStep + 9, text, false);
        g.drawString(font, "Hex-Farbe", cntX, cntY + 3 * rowStep, muted, false);
        int[] rgb = hexToRgb(KollegenMod.CONFIG.highlightColor);
        int sx = hexBox.getX() + hexBox.getWidth() + 8;
        g.fill(sx, hexBox.getY(), sx + 18, hexBox.getY() + 18, 0xff000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
        g.drawString(font, "Stärke", cntX, cntY + 4 * rowStep, muted, false);
        g.drawString(font, Math.round(KollegenMod.CONFIG.highlightAmount * 100) + "%",
                cntX + sHi.getWidth() + 10, cntY + 4 * rowStep + 14 + 5, text, false);
        g.drawString(font, "Fullbright (Gamma)", cntX, cntY + 5 * rowStep + 9, text, false);
    }

    private void renderProfile(GuiGraphics g, int accent, int panelC, int border, int text, int muted) {
        Font font = this.font;
        int x = cntX, y = cntY;

        int headSize = 64;
        if (cachedSkin != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, cachedSkin, x, y, 8f, 8f, headSize, headSize, 8, 8, 64, 64, 0);
            g.blit(RenderPipelines.GUI_TEXTURED, cachedSkin, x, y, 40f, 8f, headSize, headSize, 8, 8, 64, 64, 0);
            Glass.fillRound(g, x - 2, y - 2, headSize + 4, headSize + 4, 8, Glass.tint(0xffffff, 0x30));
        }

        int tx = x + headSize + 16, ty = y + 2;
        String name = data.meName();
        Minecraft mc = Minecraft.getInstance();
        if (name == null && mc.player != null) name = mc.player.getName().getString();
        g.drawString(font, name != null ? name : "Spieler", tx, ty, text, false);
        ty += 18;
        String uuid = data.meUuid();
        if (uuid != null) {
            g.drawString(font, "UUID: " + truncate(uuid, 28), tx, ty, muted, false);
            ty += 16;
        }
        String disc = data.discordName();
        if (disc != null) {
            g.drawString(font, "Discord: " + disc, tx, ty, accent, false);
            ty += 16;
        }
        ty = y + headSize + 12;
        g.drawString(font, "Verknüpfte Accounts", x, ty, accent, false);
        ty += 16;
        var accs = data.accounts();
        if (accs.isEmpty()) {
            g.drawString(font, "  (keine)", x, ty, muted, false);
        } else {
            for (var a : accs) {
                g.drawString(font, "  " + a.type + ": " + a.name, x, ty, text, false);
                ty += 16;
            }
        }
        ty += 8;
        g.drawString(font, "Freundes-Code: " + (data.meCode() != null ? data.meCode() : "-"), x, ty, accent, false);
    }

    private void renderInfo(GuiGraphics g, int accent, int text, int muted) {
        Font font = this.font;
        g.drawString(font, "Kollegen Client", cntX, cntY, accent, false);
        g.drawString(font, "Version " + KollegenMod.VERSION, cntX, cntY + 20, text, false);
        g.drawString(font, "Discord Rich Presence: aktiv", cntX, cntY + 40, muted, false);
        g.drawString(font, "Öffnen: Rechts-Shift  •  Social: Freunde-Button", cntX, cntY + 60, muted, false);
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

    private UUID parseUuid(String s) {
        if (s != null) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.fromString("8667ba71-b85d-4004-af54-457a9734eed7");
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n - 1) + "…" : s;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
