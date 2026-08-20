package dev.kollegen.client.menu;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.join.KollegenJoin;
import dev.kollegen.client.social.SocialData;
import dev.kollegen.client.theme.ThemeSync;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Essential-/NoRisk-artiges Social-Menü. Eigenes Profil oben rechts, Freundesliste
 * mit Status + Join (native Buttons), und Hinzufügen per Freundes-Code.
 * Hintergrund durchsichtig (Welt bleibt sichtbar).
 */
public class KollegenSocialScreen extends Screen {

    private final Screen parent;
    private SocialData data = new SocialData();
    private Identifier cachedSkin;

    private String codeBuf = "";
    private String status = "";
    private long statusUntil = 0;

    private GlassButton closeBtn;
    private GlassButton copyBtn;
    private GlassButton addBtn;
    private EditBox codeBox;
    private final List<GlassButton> joinButtons = new ArrayList<>();

    private int cardX, cardY, cardW = 180, cardH = 78;
    private int listX, listY, listW, listBottom;

    private static final int R = 16;

    public KollegenSocialScreen() {
        super(Component.literal("Kollegen Soziale"));
        this.parent = Minecraft.getInstance().screen;
    }

    @Override
    protected void init() {
        ThemeSync.refresh();
        data = SocialData.load();
        cachedSkin = computeSkin();
        buildWidgets();
    }

    private final java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> built = new java.util.ArrayList<>();

    private void resetWidgets() {
        for (net.minecraft.client.gui.components.events.GuiEventListener w : built) removeWidget(w);
        built.clear();
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T reg(T w) {
        built.add(w);
        return addRenderableWidget(w);
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
        int w = Math.min(this.width - 50, 560);
        int h = Math.min(this.height - 50, 520);
        return new int[]{(this.width - w) / 2, (this.height - h) / 2, w, h};
    }

    // ═══ Widgets (native) ═══

    private void buildWidgets() {
        resetWidgets();
        int[] p = panel();
        int px = p[0], py = p[1], pw = p[2], ph = p[3];

        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int panelC = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int textC = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);

        closeBtn = new GlassButton(px + pw - 36, py + 14, 24, 24, Component.literal("✕"), b -> close());
        closeBtn.colors(panelC, accent, textC);
        reg(closeBtn);

        // Profil-Karte oben rechts
        cardX = px + pw - 20 - cardW;
        cardY = py + 48;
        copyBtn = new GlassButton(cardX + (cardW - 150) / 2, cardY + cardH - 22 - 6, 150, 22,
                Component.literal("Code kopieren"), b -> copyCode());
        copyBtn.colors(panelC, accent, textC);
        reg(copyBtn);

        // Code eingeben + Hinzufügen (unten)
        int by = py + ph - 44;
        int boxW = pw - 40 - 110;
        codeBox = new EditBox(this.font, px + 20, by, boxW, 28, Component.literal(""));
        codeBox.setMaxLength(64);
        codeBox.setValue(codeBuf);
        codeBox.setTextColor(textC);
        codeBox.setHint(Component.literal("Freundes-Code eingeben…"));
        codeBox.setResponder(s -> codeBuf = s);
        reg(codeBox);

        addBtn = new GlassButton(px + 20 + boxW + 8, by, 102, 28,
                Component.literal("Hinzufügen"), b -> submit());
        addBtn.colors(panelC, accent, textC);
        reg(addBtn);

        // Freundesliste + Join-Buttons
        listX = px + 20;
        listY = py + 50 + 16;
        listW = pw - 40;
        listBottom = py + ph - 70;

        int rowH = 34;
        int ly = listY;
        for (SocialData.Friend f : data.friends()) {
            if (ly > listBottom) break;
            if (f.online() && f.server != null) {
                final String server = f.server;
                GlassButton jb = new GlassButton(listX + listW - 84 - 8, ly + 2, 84, 22,
                        Component.literal("Joinen"), b -> KollegenJoin.joinServer(server));
                jb.colors(panelC, accent, accent);
                joinButtons.add(jb);
                reg(jb);
            }
            ly += rowH;
        }
    }

    private void copyCode() {
        String code = data.meCode();
        if (code != null && !code.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            setStatus("Freundes-Code kopiert");
        }
    }

    private void submit() {
        String code = codeBuf.trim();
        if (code.isEmpty()) {
            setStatus("Bitte Code eingeben");
            return;
        }
        try {
            String home = System.getProperty("user.home", ".");
            Path dir = Path.of(home, ".kollegen");
            Files.createDirectories(dir);
            Map<String, String> m = new HashMap<>();
            m.put("code", code);
            Files.writeString(dir.resolve("friend_add.json"), new Gson().toJson(m));
            setStatus("Anfrage gesendet – der Launcher verarbeitet sie.");
            codeBuf = "";
            if (codeBox != null) codeBox.setValue("");
        } catch (Exception e) {
            setStatus("Fehler beim Schreiben");
        }
    }

    private void setStatus(String s) {
        this.status = s;
        this.statusUntil = System.currentTimeMillis() + 4000;
    }

    // ═══ Zeichnen ═══

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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

        g.drawString(font, "Soziale", p[0] + 20, p[1] + 18, accent, false);
        g.drawString(font, "Freunde", p[0] + 20 + font.width("Soziale") + 6, p[1] + 20, muted, false);

        // Eigenes Profil (Essential-Stil)
        Glass.fillRound(g, cardX, cardY, cardW, cardH, 10, Glass.tint(panelC, 0xD0));
        Glass.fillRound(g, cardX, cardY, cardW, 2, 2, Glass.tint(accent, 0xFF));
        int head = 56;
        if (cachedSkin != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, cachedSkin, cardX + 10, cardY + 11, 8f, 8f, head, head, 8, 8, 64, 64, 0);
            g.blit(RenderPipelines.GUI_TEXTURED, cachedSkin, cardX + 10, cardY + 11, 40f, 8f, head, head, 8, 8, 64, 64, 0);
        }
        int txx = cardX + 10 + head + 8;
        String name = data.meName();
        Minecraft mc = Minecraft.getInstance();
        if (name == null && mc.player != null) name = mc.player.getName().getString();
        g.drawString(font, name != null ? truncate(name, 12) : "Spieler", txx, cardY + 12, text, false);
        String disc = data.discordName();
        if (disc != null) g.drawString(font, "@" + truncate(disc, 12), txx, cardY + 28, accent, false);
        else g.drawString(font, truncate(data.meCode() != null ? data.meCode() : "-", 14), txx, cardY + 28, muted, false);

        // Freundesliste
        g.drawString(font, "Freunde (" + data.friendCount() + ")", listX, listY - 16, accent, false);
        if (data.friendCount() == 0) {
            g.drawString(font, "Noch keine – Code oben rechts kopieren & teilen.", listX, listY, muted, false);
        } else {
            int rowH = 34;
            int ly = listY;
            for (SocialData.Friend f : data.friends()) {
                if (ly > listBottom) break;
                boolean online = f.online();
                Glass.fillRound(g, listX, ly, listW, rowH - 6, 8, Glass.tint(panelC, online ? 0xC8 : 0x98));
                g.drawString(font, (online ? "● " : "○ ") + (f.name != null ? f.name : "?"),
                        listX + 12, ly + 6, online ? accent : muted, false);
                if (online && f.server != null) {
                    g.drawString(font, f.server, listX + 12, ly + 6 + font.lineHeight, muted, false);
                }
                ly += rowH;
            }
        }

        if (System.currentTimeMillis() < statusUntil && !status.isEmpty()) {
            int sw = font.width(status);
            g.drawString(font, status, p[0] + p[2] / 2 - sw / 2, by_label(p) - 16, accent, false);
        }

        super.render(g, mx, my, pt);
    }

    private int by_label(int[] p) {
        return p[1] + p[3] - 44;
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
