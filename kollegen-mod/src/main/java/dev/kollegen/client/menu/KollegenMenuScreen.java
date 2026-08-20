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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Mod-Menü (über Rechts-Shift). Angelehnt an NoRisk-Clients Mod-Menü:
 * dunkle Glas-Optik, Sidebar mit Kategorie-Pills, Inhaltsbereich mit Titel +
 * Suche und einzelnen, abgerundeten Einstellungs-Zeilen (Label links, Control
 * rechts). Hintergrund durchsichtig (Welt bleibt sichtbar).
 */
public class KollegenMenuScreen extends Screen {

    private final Screen parent;

    private static final String[] CATS = {"Profil", "Einstellungen", "Info"};
    private int category = 1; // standard: Einstellungen

    private static final int TOGGLE_W = 52, TOGGLE_H = 28, SLIDER_H = 18;
    private static final int SW = 188;            // Sidebar-Breite
    private static final int R = 16;              // Panel-Radius
    private static final int ROW_H = 60, ROW_GAP = 8;

    private GlassButton[] navbar = new GlassButton[CATS.length];
    private GlassButton closeBtn;
    private GlassButton copyBtn;

    private GlassToggle tLogo, tHi, tFb;
    private GlassSlider sSat, sHi;
    private EditBox hexBox, searchBox;

    private final List<SettingRow> settings = new ArrayList<>();

    private int px, py, pw, ph;     // Panel-Rechteck
    private int cx, cy, cw;         // Inhalts-Rechteck
    private SocialData data = new SocialData();
    private Identifier cachedSkin;

    private static final class SettingRow {
        final String label;
        final List<AbstractWidget> widgets = new ArrayList<>();
        Supplier<String> value = () -> "";
        int y;
        boolean visible = true;

        SettingRow(String label) {
            this.label = label;
        }
    }

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
        int w = Math.min(this.width - 70, 824);
        int h = Math.min(this.height - 70, 548);
        return new int[]{(this.width - w) / 2, (this.height - h) / 2, w, h};
    }

    // ═══ Widgets ═══

    private final List<net.minecraft.client.gui.components.events.GuiEventListener> built = new ArrayList<>();

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
        settings.clear();
        int[] p = panel();
        px = p[0]; py = p[1]; pw = p[2]; ph = p[3];

        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int panelC = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int panel2 = ThemeSync.argb(ThemeSync.get("panel2", "#21212e"), 0xff21212e);
        int textC = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);

        // Sidebar-Pills
        for (int i = 0; i < CATS.length; i++) {
            int bx = px + 14, by = py + 70 + i * 46, bw = SW - 28, bh = 38;
            final int idx = i;
            GlassButton b = new GlassButton(bx, by, bw, bh, Component.literal(CATS[i]), btn -> setCategory(idx));
            b.colors(panelC, accent, textC);
            navbar[i] = reg(b);
        }

        // Schließen
        closeBtn = new GlassButton(px + pw - 36, py + 14, 24, 24, Component.literal("✕"), btn -> close());
        closeBtn.colors(panelC, accent, textC);
        reg(closeBtn);

        // Inhalts-Geometrie
        cx = px + SW + 18;
        cw = pw - SW - 34;
        cy = py + 70;

        // Suche (nur Einstellungen)
        searchBox = new EditBox(this.font, cx + cw - 168, py + 18, 156, 22, Component.literal(""));
        searchBox.setMaxLength(32);
        searchBox.setTextColor(textC);
        searchBox.setResponder(this::onSearch);
        reg(searchBox);

        // ── Einstellungs-Zeilen ──
        int ry = cy + 8;

        // Logo ersetzen
        {
            SettingRow r = new SettingRow("Minecraft-Logo durch Logo.png");
            r.y = ry;
            tLogo = new GlassToggle(cx + cw - TOGGLE_W - 12, ry + (ROW_H - TOGGLE_H) / 2, TOGGLE_W, TOGGLE_H,
                    KollegenMod.CONFIG.replaceLogo, on -> {
                KollegenMod.CONFIG.replaceLogo = on;
                KollegenMod.CONFIG.save();
            });
            tLogo.colors(accent, muted);
            reg(tLogo);
            r.widgets.add(tLogo);
            settings.add(r);
        }
        ry += ROW_H + ROW_GAP;

        // Farb-Sättigung
        {
            SettingRow r = new SettingRow("Farb-Sättigung");
            r.y = ry;
            r.value = () -> Math.round(KollegenMod.CONFIG.colorSaturation * 100) + "%";
            int sw = Math.min(240, cw - 60);
            sSat = new GlassSlider(cx + cw - sw - 12, ry + (ROW_H - SLIDER_H) / 2, sw, SLIDER_H,
                    KollegenMod.CONFIG.colorSaturation / 2.0f);
            sSat.accent(accent).onChanged(v -> setSaturation((float) (v * 2)));
            reg(sSat);
            r.widgets.add(sSat);
            settings.add(r);
        }
        ry += ROW_H + ROW_GAP;

        // Farbe hervorheben
        {
            SettingRow r = new SettingRow("Farbe hervorheben");
            r.y = ry;
            tHi = new GlassToggle(cx + cw - TOGGLE_W - 12, ry + (ROW_H - TOGGLE_H) / 2, TOGGLE_W, TOGGLE_H,
                    KollegenMod.CONFIG.colorHighlight, on -> {
                KollegenMod.CONFIG.colorHighlight = on;
                KollegenMod.CONFIG.save();
                KollegenPostFX.applyConfig();
            });
            tHi.colors(accent, muted);
            reg(tHi);
            r.widgets.add(tHi);
            settings.add(r);
        }
        ry += ROW_H + ROW_GAP;

        // Hex-Farbe
        {
            SettingRow r = new SettingRow("Hex-Farbe");
            r.y = ry;
            r.value = () -> KollegenMod.CONFIG.highlightColor;
            int hw = Math.min(140, cw - 40);
            hexBox = new EditBox(this.font, cx + cw - hw - 12, ry + (ROW_H - 20) / 2, hw, 20, Component.literal(""));
            hexBox.setMaxLength(7);
            hexBox.setValue(KollegenMod.CONFIG.highlightColor);
            hexBox.setTextColor(textC);
            hexBox.setResponder(this::tryApplyHex);
            reg(hexBox);
            r.widgets.add(hexBox);
            settings.add(r);
        }
        ry += ROW_H + ROW_GAP;

        // Stärke
        {
            SettingRow r = new SettingRow("Stärke");
            r.y = ry;
            r.value = () -> Math.round(KollegenMod.CONFIG.highlightAmount * 100) + "%";
            int sw = Math.min(240, cw - 60);
            sHi = new GlassSlider(cx + cw - sw - 12, ry + (ROW_H - SLIDER_H) / 2, sw, SLIDER_H,
                    KollegenMod.CONFIG.highlightAmount);
            sHi.accent(accent).onChanged(v -> setHighlightAmount((float) (double) v));
            reg(sHi);
            r.widgets.add(sHi);
            settings.add(r);
        }
        ry += ROW_H + ROW_GAP;

        // Fullbright
        {
            SettingRow r = new SettingRow("Fullbright (Gamma)");
            r.y = ry;
            tFb = new GlassToggle(cx + cw - TOGGLE_W - 12, ry + (ROW_H - TOGGLE_H) / 2, TOGGLE_W, TOGGLE_H,
                    KollegenMod.CONFIG.fullbright, on -> {
                KollegenMod.CONFIG.fullbright = on;
                KollegenMod.CONFIG.save();
                Fullbright.reconcile();
            });
            tFb.colors(accent, muted);
            reg(tFb);
            r.widgets.add(tFb);
            settings.add(r);
        }

        // Profil: Code kopieren
        copyBtn = new GlassButton(cx + 14, py + ph - 46, Math.min(190, cw), 28,
                Component.literal("Freundes-Code kopieren"), btn -> copyCode());
        copyBtn.colors(panelC, accent, textC);
        reg(copyBtn);
    }

    private void onSearch(String q) {
        String ql = (q == null ? "" : q).toLowerCase();
        for (SettingRow r : settings) {
            r.visible = ql.isEmpty() || r.label.toLowerCase().contains(ql);
            for (AbstractWidget w : r.widgets) {
                w.visible = w.active = r.visible;
            }
        }
    }

    private void setCategory(int c) {
        category = c;
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int panelC = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int textC = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);

        for (int i = 0; i < navbar.length; i++) {
            navbar[i].selected(i == category);
        }

        boolean s = category == 1;
        for (SettingRow r : settings) {
            boolean v = s && r.visible;
            r.visible = v || !s; // bei Suche bleibt sichtbarkeitsfilter erhalten
            for (AbstractWidget w : r.widgets) w.visible = w.active = s;
        }
        if (s) onSearch(searchBox != null ? searchBox.getValue() : "");

        copyBtn.visible = copyBtn.active = category == 0;
        searchBox.visible = searchBox.active = s;
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
        int panel2 = ThemeSync.argb(ThemeSync.get("panel2", "#21212e"), 0xff21212e);
        int border = ThemeSync.argb(ThemeSync.get("border", "#34303a"), 0xff34303a);
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int text = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);
        Font font = this.font;

        // Dunkler, leicht transparenter Backdrop (Welt bleibt sichtbar)
        g.fill(0, 0, this.width, this.height, Glass.tint(bg, 0x18));

        int[] p = panel();
        px = p[0]; py = p[1]; pw = p[2]; ph = p[3];
        cx = px + SW + 18;
        cw = pw - SW - 34;
        cy = py + 70;

        // Haupt-Panel
        Glass.panel(g, px, py, pw, ph, R,
                Glass.tint(panelC, 0xE0), Glass.tint(border, 0xCC), Glass.tint(accent, 0xFF));
        g.fill(px + 2, py + 2, pw - 4, 1, Glass.tint(0xffffff, 0x22));

        // Sidebar-Panel
        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 12, Glass.tint(panel2, 0xCC));
        g.drawString(font, "KOLLEGEN", px + 20, py + 24, accent, false);
        g.drawString(font, "Client", px + 20 + font.width("KOLLEGEN") + 6, py + 26, muted, false);
        g.fill(px + 16, py + 54, SW - 16, 1, Glass.tint(border, 0x80));

        // Inhalts-Header
        String title = CATS[category];
        g.drawString(font, title, cx + 14, py + 20, text, false);
        if (category == 1 && searchBox.getValue().isEmpty()) {
            g.drawString(font, "Suche…", cx + cw - 168 + 6, py + 24, muted, false);
        }

        if (category == 0) renderProfile(g, accent, panelC, border, text, muted);
        else if (category == 1) renderSettings(g, accent, panel2, border, text, muted, mx, my);
        else renderInfo(g, accent, text, muted);

        super.render(g, mx, my, pt);
    }

    private void renderSettings(GuiGraphics g, int accent, int panel2, int border, int text, int muted, int mx, int my) {
        Font font = this.font;
        int i = 0;
        for (SettingRow r : settings) {
            if (!r.visible) {
                i++;
                continue;
            }
            boolean hov = mx >= cx + 8 && mx <= cx + cw - 8 && my >= r.y && my < r.y + ROW_H;
            int rowBg = hov ? Glass.tint(panel2, 0x6E) : Glass.tint(panel2, 0x52);
            Glass.fillRound(g, cx + 8, r.y, cw - 16, ROW_H, 12, rowBg);
            g.drawString(font, r.label, cx + 22, r.y + 14, text, false);
            String val = r.value.get();
            if (!val.isEmpty()) {
                g.drawString(font, val, cx + 22, r.y + 36, muted, false);
            }
            if (r.widgets.contains(hexBox) && hexBox.getValue().matches("#[0-9a-fA-F]{6}")) {
                int[] rgb = hexToRgb(hexBox.getValue());
                g.fill(hexBox.getX() + hexBox.getWidth() + 8, hexBox.getY(), 18, 18,
                        0xff000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
            }
            if (i < settings.size() - 1) {
                g.fill(cx + 8, r.y + ROW_H, cx + cw - 8, r.y + ROW_H + 1, Glass.tint(border, 0x55));
            }
            i++;
        }
    }

    private void renderProfile(GuiGraphics g, int accent, int panelC, int border, int text, int muted) {
        Font font = this.font;
        int x = cx + 14, y = cy + 8;

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
        ty = y + headSize + 14;
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
        int x = cx + 14, y = cy + 10;
        g.drawString(font, "Kollegen Client", x, y, accent, false);
        g.drawString(font, "Version " + KollegenMod.VERSION, x, y + 22, text, false);
        g.drawString(font, "Discord Rich Presence: aktiv", x, y + 44, muted, false);
        g.drawString(font, "Öffnen: Rechts-Shift  •  Social: Freunde-Button", x, y + 64, muted, false);
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
