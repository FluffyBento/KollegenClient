package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.HudModule;
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
    private static final int R = 20;
    private static final int ROW_H = 58;
    private static final int SET_H = 34;

    private EditBox search;
    private Button closeBtn;

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
        final int y;
        final int h;
        final AbstractWidget widget;

        Row(boolean isModule, Module module, Setting setting, int y, int h, AbstractWidget widget) {
            this.isModule = isModule;
            this.module = module;
            this.setting = setting;
            this.y = y;
            this.h = h;
            this.widget = widget;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    public KollegenMenuScreen(Screen parent) {
        super(Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
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

        // Schließen
        closeBtn = Button.builder(Component.literal("✕"), btn -> close()).bounds(px + pw - 38, py + 14, 26, 26).build();
        addRenderableWidget(closeBtn);

        // Suche
        search = new EditBox(this.font, cx + 14, py + 16, cw - 28, 24, Component.literal(""));
        search.setMaxLength(40);
        search.setHint(Component.literal("Suchen…"));
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
        boolean hudCat = cats[category] == Category.HUD;
        int topExtra = 0;
        if (hudCat) {
            topExtra = 46;
            int bx = cx + 14;
            int half = (cw - 28) / 2 - 4;
            Button editBtn = Button.builder(
                    Component.literal(HudModule.editMode ? "✓ Elemente verschieben" : "Elemente verschieben"),
                    btn -> {
                        HudModule.editMode = !HudModule.editMode;
                        rebuild();
                    }).bounds(bx, contentTop + 6, half, 32).build();
            addRenderableWidget(editBtn);
            Button arrBtn = Button.builder(Component.literal("Auto-Anordnen"),
                    btn -> HudModule.autoArrange()).bounds(bx + half + 8, contentTop + 6, half, 32).build();
            addRenderableWidget(arrBtn);
        }
        int contentH = 0;
        int y = contentTop + 2 + topExtra - scroll;
        for (Module m : visibleModules()) {
            int toggleX = cx + cw - 62;
            int gearX = toggleX - 36;
            boolean vis = y + ROW_H > contentTop && y < contentBottom;
            if (!m.locked && vis) {
                GlassToggle t = new GlassToggle(toggleX, y + (ROW_H - 28) / 2, 54, 28, m.enabled, on -> {
                    m.enabled = on;
                    if (on) m.onEnable();
                    else m.onDisable();
                    ModuleManager.save();
                });
                t.colors(Palette.ACCENT, Palette.MUTED);
                addRenderableWidget(t);
            }
            if (vis) {
                Button gear = Button.builder(Component.literal("⚙"), btn -> {
                    if (expanded.contains(m.id)) expanded.remove(m.id);
                    else expanded.add(m.id);
                    rebuild();
                }).bounds(gearX, y + (ROW_H - 24) / 2, 30, 24).build();
                addRenderableWidget(gear);
            }
            rows.add(new Row(true, m, null, y, ROW_H, null));
            y += ROW_H + 8;
            contentH += ROW_H + 8;

            if (expanded.contains(m.id)) {
                for (Setting s : m.settings()) {
                    boolean sv = y + SET_H > contentTop && y < contentBottom;
                    AbstractWidget w = null;
                    if (sv) {
                        w = s.buildWidget(cx + 8, y, cw - 16, SET_H, this);
                        addRenderableWidget(w);
                    }
                    rows.add(new Row(false, m, s, y, SET_H, w));
                    y += SET_H + 6;
                    contentH += SET_H + 6;
                }
            }
        }
        int visibleH = contentBottom - contentTop;
        maxScroll = Math.max(0, contentH - visibleH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
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
            rebuild();
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
        // Panel
        Glass.fillRound(g, px, py, pw, ph, R, Palette.BORDER);
        Glass.fillRound(g, px + 1, py + 1, pw - 2, ph - 2, R - 1, Palette.tint(Palette.PANEL, 0xF2));
        // Sidebar-Fläche
        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 14, Palette.tint(Palette.PANEL2, 0xCC));
        // Akzentleiste oben
        Glass.fillRound(g, px + 8, py + 8, SW, 6, 4, Palette.tint(Palette.ACCENT, 0xE0));

        // Header-Text (Launcher-Stil: fett-orange "KOLLEGEN" + muted "Client")
        g.drawString(this.font, "KOLLEGEN", px + 22, py + 26, Palette.ACCENT, false);
        g.drawString(this.font, "Client", px + 22 + this.font.width("KOLLEGEN") + 6, py + 28, Palette.MUTED, false);

        String title = query.isEmpty() ? cats[category].display : "Suche: " + query;
        g.drawString(this.font, title, cx + 14, py + 22, Palette.TEXT, false);

        // ── Sidebar (manuell, gescrollt, skaliert) ──
        g.enableScissor(px + 10, sidebarTop, px + SW - 2, sidebarBottom);
        for (int i = 0; i < cats.length; i++) {
            int by = sidebarTop + i * (catItemH + CAT_GAP) - catScroll;
            if (by + catItemH < sidebarTop || by > sidebarBottom) continue;
            boolean sel = i == category;
            Glass.fillRound(g, px + 14, by, SW - 28, catItemH, 10,
                    sel ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0x66));
            int ty = by + (catItemH - this.font.lineHeight) / 2;
            g.drawString(this.font, cats[i].icon + "  " + cats[i].display, px + 26, ty,
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
            if (r.isModule) {
                boolean hov = mx >= cx + 8 && mx <= cx + cw - 8 && my >= r.y && my < r.y + r.h;
                Glass.fillRound(g, cx + 8, r.y, cw - 16, r.h, 10, Palette.tint(Palette.BORDER, 0xAA));
                Glass.fillRound(g, cx + 9, r.y + 1, cw - 18, r.h - 2, 9,
                        hov ? Palette.tint(Palette.PANEL2, 0x99) : Palette.tint(Palette.PANEL2, 0x66));
                g.drawString(this.font, r.module.name, cx + 22, r.y + 11, Palette.TEXT, false);
                g.drawString(this.font, trunc(r.module.description, cw - 220), cx + 22, r.y + 31, Palette.MUTED, false);
                if (r.module.risk != null) {
                    g.drawString(this.font, "⚠ " + trunc(r.module.risk, cw - 60), cx + 22, r.y + 45, Palette.DANGER, false);
                }
                // Gesperrte Module: Schloss statt Toggle
                if (r.module.locked) {
                    g.drawString(this.font, "🔒", cx + cw - 56, r.y + (ROW_H - 28) / 2 + 6, Palette.MUTED, false);
                }
            } else {
                g.drawString(this.font, r.setting.name, cx + 22, r.y + (r.h - this.font.lineHeight) / 2, Palette.TEXT, false);
                String vt = r.setting.valueText();
                if (!vt.isEmpty() && r.widget != null) {
                    int vx = r.widget.getX() - 8 - this.font.width(vt);
                    g.drawString(this.font, vt, vx, r.y + (r.h - this.font.lineHeight) / 2, Palette.MUTED, false);
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
