package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.Setting;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.GlassToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KollegenMenuScreen extends Screen {
    private final Screen parent;

    private final Category[] cats = Category.values();
    private int category = 0;
    private String query = "";
    private final Set<String> expanded = new HashSet<>();

    private static final int SW = 220;
    private static final int R = 22;
    private static final int ROW_H = 58;
    private static final int SET_H = 34;

    private EditBox search;
    private GlassButton closeBtn;
    private Font FONT;

    private int px, py, pw, ph, cx, cw;
    private int scroll = 0;
    private int maxScroll = 0;
    private int catScroll = 0;
    private int maxCatScroll = 0;
    private int sidebarTop, sidebarBottom;
    private int catItemH = 42;
    private static final int CAT_GAP = 8;

    private int contentTop, contentBottom;

    private static final class Row {
        final boolean isModule;
        final Module module;
        final Setting setting;
        final int baseY;
        int y;
        final int h;
        final AbstractWidget widget;   // Steuerelement der Einstellung (oder null)
        final AbstractWidget toggle;    // Modul-An/Aus (oder null)
        final AbstractWidget gear;      // Modul-Aufklapp-Button (oder null)

        Row(boolean isModule, Module module, Setting setting, int baseY, int h,
            AbstractWidget widget, AbstractWidget toggle, AbstractWidget gear) {
            this.isModule = isModule;
            this.module = module;
            this.setting = setting;
            this.baseY = baseY;
            this.y = baseY;
            this.h = h;
            this.widget = widget;
            this.toggle = toggle;
            this.gear = gear;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    public KollegenMenuScreen(Screen parent) {
        super(Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Glatte (TTF-)Schrift statt der pixeligen Minecraft-Bitmap-Schrift,
        // damit das Menü exakt dem Launcher-Look entspricht.
        FONT = GlassButton.smoothFont();
        rebuild();
    }

    private int[] panel() {
        int w = Math.min(this.width - 70, 940);
        int h = Math.min(this.height - 70, 640);
        int y = (this.height - h) / 2 - 24;
        if (y < 8) y = 8;
        return new int[]{(this.width - w) / 2, y, w, h};
    }

    private List<Module> visibleModules() {
        List<Module> all = ModuleManager.modules();
        List<Module> out = new ArrayList<>();
        String q = query.trim().toLowerCase();
        for (Module m : all) {
            if (!q.isEmpty()) {
                if (m.name.toLowerCase().contains(q) || m.description.toLowerCase().contains(q)
                        || m.category.display.toLowerCase().contains(q)) {
                    out.add(m);
                }
            } else if (m.category == cats[category]) {
                out.add(m);
            }
        }
        return out;
    }

    private void rebuild() {
        clearWidgets();
        rows.clear();
        int[] p = panel();
        px = p[0]; py = p[1]; pw = p[2]; ph = p[3];
        cx = px + SW + 18;
        cw = pw - SW - 34;
        sidebarTop = py + 70;
        sidebarBottom = py + ph - 16;

        // Sidebar: Höhe an verfügbaren Platz anpassen (skalieren) + Scroll
        int avail = sidebarBottom - sidebarTop;
        int minH = 36;
        int need = cats.length * (minH + CAT_GAP);
        if (need <= avail) {
            catItemH = Math.max(minH, (avail - (cats.length - 1) * CAT_GAP) / cats.length);
            maxCatScroll = 0;
        } else {
            catItemH = minH;
            maxCatScroll = need - avail;
        }
        if (catScroll > maxCatScroll) catScroll = maxCatScroll;
        if (catScroll < 0) catScroll = 0;

        // Schließen (leeres Glas-Button als Hitbox, X wird manuell gezeichnet)
        closeBtn = new GlassButton(px + pw - 38, py + 14, 26, 26, Component.empty(), btn -> close());
        closeBtn.colors(Palette.PANEL2, Palette.ACCENT, Palette.TEXT);
        addRenderableWidget(closeBtn);

        // Suche
        search = new EditBox(FONT, cx + 14, py + 16, cw - 28, 24, Component.literal(""));
        search.setMaxLength(40);
        search.setHint(Component.literal("Suchen…"));
        search.setTextColor(Palette.TEXT);
        search.setBordered(false); // eigenes Glas-Menü statt Vanilla-Rechteck
        search.setValue(query);
        search.setResponder(t -> {
            query = t;
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        search.setFocused(true);

        // Inhalt
        contentTop = py + 52;
        contentBottom = py + ph - 14;
        int contentH = 0;
        int baseY = contentTop + 2;
        for (Module m : visibleModules()) {
            int toggleX = cx + cw - 62;
            int gearX = toggleX - 36;
            GlassToggle t = null;
            if (!m.locked) {
                t = new GlassToggle(toggleX, baseY + (ROW_H - 28) / 2, 54, 28, m.enabled, on -> {
                    m.enabled = on;
                    if (on) m.onEnable();
                    else m.onDisable();
                    ModuleManager.save();
                });
                t.colors(Palette.ACCENT, Palette.MUTED);
                addRenderableWidget(t);
            }
            GlassButton gear = new GlassButton(gearX, baseY + (ROW_H - 24) / 2, 30, 24,
                    Component.empty(), btn -> {
                if (expanded.contains(m.id)) expanded.remove(m.id);
                else expanded.add(m.id);
                rebuild();
            });
            gear.colors(Palette.PANEL2, Palette.ACCENT, Palette.TEXT);
            addRenderableWidget(gear);

            rows.add(new Row(true, m, null, baseY, ROW_H, null, t, gear));
            baseY += ROW_H + 8;
            contentH += ROW_H + 8;

            if (expanded.contains(m.id)) {
                for (Setting s : m.settings()) {
                    AbstractWidget w = s.buildWidget(cx + 8, baseY, cw - 16, SET_H, this);
                    addRenderableWidget(w);
                    rows.add(new Row(false, m, s, baseY, SET_H, w, null, null));
                    baseY += SET_H + 6;
                    contentH += SET_H + 6;
                }
            }
        }
        int visibleH = contentBottom - contentTop;
        maxScroll = Math.max(0, contentH - visibleH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
        positionWidgets();
    }

    /** Verschiebt alle Widgets anhand des aktuellen Scroll-Offsets (günstig,
     *  ohne das Menü komplett neu aufzubauen – das war die Ursache fürs Lag). */
    private void positionWidgets() {
        for (Row r : rows) {
            int y = r.baseY - scroll;
            boolean vis = y + r.h > contentTop && y < contentBottom;
            r.y = y;
            if (r.isModule) {
                if (r.toggle != null) {
                    r.toggle.setY(y + (ROW_H - 28) / 2);
                    r.toggle.visible = vis;
                }
                if (r.gear != null) {
                    r.gear.setY(y + (ROW_H - 24) / 2);
                    r.gear.visible = vis;
                }
            } else if (r.widget != null) {
                r.widget.setY(y);
                r.widget.visible = vis;
            }
        }
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        // Sidebar-Klick (manuell, da selbst gerendert + gescrollt)
        if (mx >= px && mx <= px + SW && my >= sidebarTop && my <= sidebarBottom) {
            int idx = (int) ((my - sidebarTop + catScroll) / (catItemH + CAT_GAP));
            if (idx >= 0 && idx < cats.length) {
                scroll = 0;
                category = idx;
                query = "";
                if (search != null) search.setValue("");
                rebuild();
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        // Über der Sidebar: diese scrollen, sonst Inhalt
        if (mx >= px && mx <= px + SW && my >= sidebarTop && my <= sidebarBottom && maxCatScroll > 0) {
            catScroll = Math.max(0, Math.min(maxCatScroll, catScroll - (int) (vertical * 28)));
            return true;
        }
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 24)));
            positionWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent ki) {
        if (dev.kollegen.client.mods.KeybindSetting.capturing != null) {
            dev.kollegen.client.mods.KeybindSetting cap = dev.kollegen.client.mods.KeybindSetting.capturing;
            cap.value = (ki.key() == InputConstants.KEY_ESCAPE) ? -1 : ki.key();
            dev.kollegen.client.mods.KeybindSetting.capturing = null;
            rebuild();
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0x20));

        // Weicher Akzent-Glow hinter dem Panel (Launcher-Look)
        Glass.glow(g, px, py, pw, ph, R, Palette.ACCENT, 10);

        // Panel
        Glass.fillRound(g, px, py, pw, ph, R, Palette.BORDER);
        Glass.fillRound(g, px + 1, py + 1, pw - 2, ph - 2, R - 1, Palette.tint(Palette.PANEL, 0xF2));
        // Sidebar-Fläche
        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 18, Palette.tint(Palette.PANEL2, 0xCC));
        // Akzentleiste oben
        Glass.fillRound(g, px + 8, py + 8, SW, 6, 4, Palette.tint(Palette.ACCENT, 0xE0));

        // Header-Text (Launcher-Stil: fett-blau "KOLLEGEN" + muted "Client")
        g.drawString(FONT, "KOLLEGEN", px + 22, py + 26, Palette.ACCENT, false);
        g.drawString(FONT, "Client", px + 22 + FONT.width("KOLLEGEN") + 6, py + 28, Palette.MUTED, false);

        String title = query.isEmpty() ? cats[category].display : "Suche: " + query;
        g.drawString(FONT, title, cx + 14, py + 22, Palette.TEXT, false);

        // Suchfeld-Hintergrund (Glas statt des Vanilla-Rechtecks)
        Glass.fillRound(g, cx + 10, py + 12, cw - 20, 32, 10, Palette.tint(Palette.PANEL2, 0xAA));

        // ── Sidebar (manuell, gescrollt, skaliert) ──
        g.enableScissor(px + 10, sidebarTop, px + SW - 2, sidebarBottom);
        for (int i = 0; i < cats.length; i++) {
            int by = sidebarTop + i * (catItemH + CAT_GAP) - catScroll;
            if (by + catItemH < sidebarTop || by > sidebarBottom) continue;
            boolean sel = i == category;
            if (sel) Glass.glow(g, px + 14, by, SW - 28, catItemH, 10, Palette.ACCENT, 16);
            Glass.fillRound(g, px + 14, by, SW - 28, catItemH, 13,
                    sel ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0x66));
            int ty = by + (catItemH - FONT.lineHeight) / 2;
            g.drawString(FONT, cats[i].icon + "  " + cats[i].display, px + 26, ty,
                    sel ? 0xFFffffff : Palette.TEXT, false);
        }
        g.disableScissor();
        // Sidebar-Scrollbar
        if (maxCatScroll > 0) {
            int trackH = sidebarBottom - sidebarTop;
            int thumbH = Math.max(20, (int) ((double) trackH * trackH / (trackH + maxCatScroll)));
            int thumbY = sidebarTop + (int) ((trackH - thumbH) * (catScroll / (double) maxCatScroll));
            Glass.fillRound(g, px + SW - 6, thumbY, 3, thumbH, 2, Palette.tint(Palette.ACCENT, 0xCC));
        }

        // ── Inhalt (gescissort) ──
        g.enableScissor(cx, contentTop, cx + cw, contentBottom);
        for (Row r : rows) {
            // nicht sichtbare Zeilen überspringen (sonst tausende g.fill-Aufrufe pro Frame)
            if (r.y + r.h <= contentTop || r.y >= contentBottom) continue;
            if (r.isModule) {
                boolean hov = mx >= cx + 8 && mx <= cx + cw - 8 && my >= r.y && my < r.y + r.h;
                Glass.fillRound(g, cx + 8, r.y, cw - 16, r.h, 13, Palette.tint(Palette.BORDER, 0xAA));
                Glass.fillRound(g, cx + 9, r.y + 1, cw - 18, r.h - 2, 12,
                        hov ? Palette.tint(Palette.PANEL2, 0x99) : Palette.tint(Palette.PANEL2, 0x66));
                g.drawString(FONT, r.module.name, cx + 22, r.y + 11, Palette.TEXT, false);
                g.drawString(FONT, trunc(r.module.description, cw - 220), cx + 22, r.y + 31, Palette.MUTED, false);

                // Risiko-Hinweis (Text; Dreieck wird nach den Widgets gezeichnet)
                if (r.module.risk != null) {
                    g.drawString(FONT, trunc(r.module.risk, cw - 60), cx + 38, r.y + 45, Palette.DANGER, false);
                }
            } else {
                g.drawString(FONT, r.setting.name, cx + 22, r.y + (r.h - FONT.lineHeight) / 2, Palette.TEXT, false);
                String vt = r.setting.valueText();
                if (!vt.isEmpty() && r.widget != null) {
                    int vx = r.widget.getX() - 8 - FONT.width(vt);
                    g.drawString(FONT, vt, vx, r.y + (r.h - FONT.lineHeight) / 2, Palette.MUTED, false);
                }
            }
        }
        g.disableScissor();

        // Inhalts-Scrollbar
        if (maxScroll > 0) {
            int trackTop = contentTop, trackBottom = contentBottom, trackH = trackBottom - trackTop;
            int thumbH = Math.max(24, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Glass.fillRound(g, cx + cw - 5, thumbY, 3, thumbH, 2, Palette.tint(Palette.ACCENT, 0xCC));
        }

        super.render(g, mx, my, pt);

        // Icons über den Widgets zeichnen (Toggle/Gear-Hintergründe sonst darüber)
        g.enableScissor(cx, contentTop, cx + cw, contentBottom);
        for (Row r : rows) {
            if (!r.isModule) continue;
            if (r.y + r.h <= contentTop || r.y >= contentBottom) continue;
            boolean hov = mx >= cx + 8 && mx <= cx + cw - 8 && my >= r.y && my < r.y + r.h;
            int gearCx = cx + cw - 98 + 15;
            int gearCy = r.y + ROW_H / 2;
            GlassButton.drawChevron(g, gearCx, gearCy, 6, expanded.contains(r.module.id),
                    hov ? Palette.TEXT : Palette.MUTED);
            if (r.module.locked) {
                drawLock(g, cx + cw - 40, r.y + (ROW_H - 16) / 2, 16, Palette.MUTED);
            }
            if (r.module.risk != null) {
                drawWarning(g, cx + 22, r.y + 41, 12, Palette.DANGER);
            }
        }
        g.disableScissor();

        // Schließen-X manuell über das (leere) Glas-Button zeichnen
        GlassButton.drawClose(g, px + pw - 38 + 4, py + 14 + 4, 18, Palette.TEXT);
    }

    private static void drawLock(GuiGraphics g, int x, int y, int s, int color) {
        int by = y + (int) (s * 0.38);
        int bh = (int) (s * 0.62);
        Glass.line(g, x + (int) (s * 0.22), y + (int) (s * 0.30), x + (int) (s * 0.22), by, color);
        Glass.line(g, x + (int) (s * 0.78), y + (int) (s * 0.30), x + (int) (s * 0.78), by, color);
        Glass.line(g, x + (int) (s * 0.22), y + (int) (s * 0.30), x + (int) (s * 0.78), y + (int) (s * 0.30), color);
        g.fill(x, by, s, bh, color);
    }

    private static void drawWarning(GuiGraphics g, int x, int y, int s, int color) {
        int midx = x + s / 2;
        Glass.line(g, x, y + s, midx, y, color);
        Glass.line(g, x + s, y + s, midx, y, color);
        Glass.line(g, x, y + s, x + s, y + s, color);
        Glass.line(g, midx, y + (int) (s * 0.35), midx, y + (int) (s * 0.62), color);
        g.fill(midx, y + (int) (s * 0.72), 1, 1, color);
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, Math.max(1, max - 1)) + "…" : s;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
