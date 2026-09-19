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
import dev.kollegen.client.ui.GlassSlider;
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

    private static final int PANEL_W = 900;
    private static final int PANEL_H = 580;
    private static final int TAB_H = 40;
    private static final int HEADER_H = 60;
    private static final int CARD_H = 56;
    private static final int SETTING_H = 40;
    private static final int GAP = 12;
    private static final int PADDING = 20;
    private static final int RADIUS = 10;
    private static final int SCROLLBAR_W = 6;

    private EditBox search;
    private Button closeBtn;

    private int px, py, pw, ph;
    private int contentY, contentH, maxScroll, scroll = 0;
    private int tabScroll = 0, maxTabScroll = 0;
    private boolean draggingScroll = false, draggingTabScroll = false;
    private int dragStartY, dragStartScroll;
    private long lastRebuild = 0;

    private float tabAnimProgress = 0f;
    private int prevCategory = 0;

    private static final class Entry {
        final boolean isModule;
        final Module module;
        final Setting setting;
        final int y;
        final int h;
        final AbstractWidget widget;
        final int cardIndex;

        Entry(boolean isModule, Module module, Setting setting, int y, int h, AbstractWidget widget, int cardIndex) {
            this.isModule = isModule;
            this.module = module;
            this.setting = setting;
            this.y = y;
            this.h = h;
            this.widget = widget;
            this.cardIndex = cardIndex;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Module> visibleModules = new ArrayList<>();

    public KollegenMenuScreen(Screen parent) {
        super(Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
        search.setFocused(true);
    }

    private int[] panel() {
        int w = Math.min(this.width - 40, PANEL_W);
        int h = Math.min(this.height - 40, PANEL_H);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        return new int[]{x, y, w, h};
    }

    private void rebuild() {
        long now = System.currentTimeMillis();
        if (now - lastRebuild < 50) return;
        lastRebuild = now;

        clearWidgets();
        entries.clear();
        visibleModules.clear();

        int[] p = panel();
        px = p[0]; py = p[1]; pw = p[2]; ph = p[3];

        int tabBarY = py + HEADER_H;
        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;

        closeBtn = Button.builder(Component.literal("✕"), btn -> close())
                .bounds(px + pw - 44, py + 16, 28, 28).build();
        addRenderableWidget(closeBtn);

        search = new EditBox(this.font, px + PADDING + 12, py + 16, pw - PADDING * 2 - 60, 28, Component.literal(""));
        search.setMaxLength(40);
        search.setHint(Component.literal("Module suchen…"));
        search.setValue(query);
        search.setResponder(t -> {
            query = t;
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        search.setFocused(true);

        String q = query.trim().toLowerCase();
        for (Module m : ModuleManager.modules()) {
            if (!q.isEmpty()) {
                if (m.name.toLowerCase().contains(q) || m.description.toLowerCase().contains(q)
                        || m.category.display.toLowerCase().contains(q)) {
                    visibleModules.add(m);
                }
            } else if (m.category == cats[category]) {
                visibleModules.add(m);
            }
        }

        int y = contentTop - scroll;
        int cardIdx = 0;
        for (Module m : visibleModules) {
            boolean vis = y + CARD_H > contentTop && y < contentBottom;
            int toggleX = px + pw - PADDING - 68;
            int gearX = toggleX - 40;

            if (!m.locked && vis) {
                GlassToggle t = new GlassToggle(toggleX, y + (CARD_H - 28) / 2, 58, 28, m.enabled, on -> {
                    m.enabled = on;
                    if (on) m.onEnable();
                    else m.onDisable();
                    ModuleManager.save();
                });
                addRenderableWidget(t);
            }
            if (vis) {
                Button gear = Button.builder(Component.literal("⚙"), btn -> {
                    if (expanded.contains(m.id)) expanded.remove(m.id);
                    else expanded.add(m.id);
                    rebuild();
                }).bounds(gearX, y + (CARD_H - 28) / 2, 32, 28).build();
                addRenderableWidget(gear);
            }
            entries.add(new Entry(true, m, null, y, CARD_H, null, cardIdx++));
            y += CARD_H + GAP;

            if (expanded.contains(m.id)) {
                for (Setting s : m.settings()) {
                    boolean sv = y + SETTING_H > contentTop && y < contentBottom;
                    AbstractWidget w = null;
                    if (sv) {
                        w = s.buildWidget(px + PADDING + 12, y, pw - PADDING * 2 - 24, SETTING_H, this);
                        addRenderableWidget(w);
                    }
                    entries.add(new Entry(false, m, s, y, SETTING_H, w, cardIdx));
                    y += SETTING_H + GAP / 2;
                }
            }
        }

        contentH = y - contentTop + scroll;
        int visibleH = contentBottom - contentTop;
        maxScroll = Math.max(0, contentH - visibleH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll && event.button() == 0) {
            int delta = (int) Math.round(dy);
            if (delta != 0) {
                scroll = Math.max(0, Math.min(maxScroll, dragStartScroll + delta));
            }
            return true;
        }
        if (draggingTabScroll && event.button() == 0) {
            int delta = (int) Math.round(dx);
            if (delta != 0) {
                tabScroll = Math.max(0, Math.min(maxTabScroll, dragStartScroll + delta));
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0) {
            draggingScroll = false;
            draggingTabScroll = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        int tabBarY = py + HEADER_H;
        int tabBarX = px + PADDING;
        int tabBarW = pw - PADDING * 2;

        if (button == 0 && my >= tabBarY && my <= tabBarY + TAB_H && mx >= tabBarX && mx <= tabBarX + tabBarW) {
            int totalTabW = cats.length * 120;
            if (totalTabW > tabBarW && maxTabScroll > 0) {
                draggingTabScroll = true;
                dragStartY = (int) mx;
                dragStartScroll = tabScroll;
                return true;
            }
        }

        int tabX = tabBarX - tabScroll;
        for (int i = 0; i < cats.length; i++) {
            int tabW = 110;
            if (mx >= tabX && mx <= tabX + tabW && my >= tabBarY && my <= tabBarY + TAB_H) {
                if (i != category) {
                    prevCategory = category;
                    category = i;
                    tabAnimProgress = 0f;
                    scroll = 0;
                    query = "";
                    search.setValue("");
                    rebuild();
                }
                return true;
            }
            tabX += tabW + 8;
        }

        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;
        int scrollbarX = px + pw - SCROLLBAR_W - 8;

        if (button == 0 && maxScroll > 0) {
            if (mx >= scrollbarX - 4 && mx <= scrollbarX + SCROLLBAR_W + 4 && my >= contentTop && my <= contentBottom) {
                draggingScroll = true;
                dragStartY = (int) my;
                dragStartScroll = scroll;
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int tabBarY = py + HEADER_H;
        int tabBarX = px + PADDING;
        int tabBarW = pw - PADDING * 2;

        if (my >= tabBarY && my <= tabBarY + TAB_H && mx >= tabBarX && mx <= tabBarX + tabBarW && maxTabScroll > 0) {
            tabScroll = Math.max(0, Math.min(maxTabScroll, tabScroll - (int) (horizontal * 30)));
            return true;
        }

        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;
        if (mx >= px + PADDING && mx <= px + pw - PADDING && my >= contentTop && my <= contentBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 30)));
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
        if (ki.key() == InputConstants.KEY_LEFT && category > 0) {
            prevCategory = category;
            category--;
            tabAnimProgress = 0f;
            scroll = 0;
            query = "";
            search.setValue("");
            rebuild();
            return true;
        }
        if (ki.key() == InputConstants.KEY_RIGHT && category < cats.length - 1) {
            prevCategory = category;
            category++;
            tabAnimProgress = 0f;
            scroll = 0;
            query = "";
            search.setValue("");
            rebuild();
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        tabAnimProgress = Math.min(1f, tabAnimProgress + pt * 8f);

        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));

        Glass.dropShadow(g, px, py, pw, ph, RADIUS + 2, 6, 12);
        Glass.panelVanilla(g, px, py, pw, ph, RADIUS + 2);

        int tabBarY = py + HEADER_H;
        int tabBarX = px + PADDING;
        int tabBarW = pw - PADDING * 2;

        Glass.fillRound(g, tabBarX - 4, tabBarY - 4, tabBarW + 8, TAB_H + 8, 8, Palette.tint(Palette.PANEL2, 0x80));

        int totalTabW = cats.length * 118;
        maxTabScroll = Math.max(0, totalTabW - tabBarW);
        tabScroll = Math.max(0, Math.min(tabScroll, maxTabScroll));

        g.enableScissor(tabBarX, tabBarY, tabBarX + tabBarW, tabBarY + TAB_H);
        int tabX = tabBarX - tabScroll;
        for (int i = 0; i < cats.length; i++) {
            int tabW = 110;
            boolean sel = i == category;
            boolean hov = mx >= tabX && mx <= tabX + tabW && my >= tabBarY && my <= tabBarY + TAB_H;

            if (sel || hov) {
                Glass.fillRound(g, tabX, tabBarY + 2, tabW, TAB_H - 4, 6,
                        sel ? Palette.tint(Palette.ACCENT, 0xE0) : Palette.tint(Palette.ACCENT, 0x40));
            }
            int iconX = tabX + (tabW - this.font.width(cats[i].icon + " " + cats[i].display)) / 2;
            int iconY = tabBarY + (TAB_H - this.font.lineHeight) / 2;
            g.drawString(this.font, cats[i].icon + " " + cats[i].display, iconX, iconY,
                    sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED), false);
            tabX += tabW + 8;
        }
        g.disableScissor();

        if (maxTabScroll > 0) {
            int thumbW = Math.max(40, (int) ((double) tabBarW * tabBarW / (tabBarW + maxTabScroll)));
            int thumbX = tabBarX + (int) ((tabBarW - thumbW) * (tabScroll / (double) maxTabScroll));
            Glass.scrollbarTrack(g, tabBarX, tabBarY + TAB_H - 4, tabBarW, 4, 2);
            Glass.scrollbarThumb(g, thumbX, tabBarY + TAB_H - 4, thumbW, 4, 2, draggingTabScroll);
        }

        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;
        int contentX = px + PADDING;
        int contentW = pw - PADDING * 2;

        g.enableScissor(contentX, contentTop, contentX + contentW, contentBottom);

        for (Entry e : entries) {
            int ey = e.y;
            if (ey + e.h < contentTop || ey > contentBottom) continue;

            boolean hov = mx >= contentX && mx <= contentX + contentW && my >= ey && my < ey + e.h;

            if (e.isModule) {
                Module m = e.module;
                int cardW = contentW;
                int cardX = contentX;
                int cardY = ey;
                int cardH = e.h;

                Glass.fillRound(g, cardX, cardY, cardW, cardH, 8, Palette.BORDER);
                Glass.fillRound(g, cardX + 1, cardY + 1, cardW - 2, cardH - 2, 7,
                        hov ? Palette.tint(Palette.PANEL2, 0x99) : Palette.tint(Palette.PANEL2, 0x55));

                if (m.locked) {
                    Glass.fillRound(g, cardX + 4, cardY + (cardH - 20) / 2, 20, 20, 4, Palette.tint(Palette.MUTED, 0x80));
                    g.drawString(this.font, "🔒", cardX + 8, cardY + (cardH - this.font.lineHeight) / 2, Palette.MUTED, false);
                }

                int titleX = cardX + 16 + (m.locked ? 24 : 0);
                g.drawString(this.font, m.name, titleX, cardY + 10, Palette.TEXT, false);
                g.drawString(this.font, trunc(m.description, cardW - 200), titleX, cardY + 28, Palette.MUTED, false);
                if (m.risk != null) {
                    g.drawString(this.font, "⚠ " + trunc(m.risk, cardW - 80), titleX, cardY + 44, Palette.DANGER, false);
                }

                int badgeX = cardX + cardW - 100;
                if (m.enabled) {
                    Glass.fillRound(g, badgeX, cardY + (cardH - 18) / 2, 88, 18, 9, Palette.tint(Palette.GREEN, 0xE0));
                    g.drawString(this.font, "● Aktiv", badgeX + 18, cardY + (cardH - this.font.lineHeight) / 2, 0xFFFFFFFF, false);
                }
            } else {
                int cardW = contentW;
                int cardX = contentX;
                int cardY = ey;
                int cardH = e.h;

                Glass.fillRound(g, cardX, cardY, cardW, cardH, 6, Palette.tint(Palette.BORDER, 0x80));
                Glass.fillRound(g, cardX + 1, cardY + 1, cardW - 2, cardH - 2, 5,
                        Palette.tint(Palette.PANEL2, 0x70));

                g.drawString(this.font, e.setting.name, cardX + 16, cardY + (cardH - this.font.lineHeight) / 2, Palette.TEXT, false);

                String vt = e.setting.valueText();
                if (!vt.isEmpty() && e.widget != null) {
                    int vx = e.widget.getX() - 12 - this.font.width(vt);
                    if (vx < cardX + cardW / 2) vx = cardX + cardW / 2;
                    g.drawString(this.font, vt, vx, cardY + (cardH - this.font.lineHeight) / 2, Palette.MUTED, false);
                }
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = px + pw - SCROLLBAR_W - 8;
            int trackH = contentBottom - contentTop;
            int thumbH = Math.max(40, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = contentTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Glass.scrollbarTrack(g, scrollbarX, contentTop, SCROLLBAR_W, trackH, SCROLLBAR_W / 2);
            Glass.scrollbarThumb(g, scrollbarX, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W / 2, draggingScroll);
        }

        String title = query.isEmpty() ? cats[category].display : "Suche: " + query;
        g.drawString(this.font, title, px + PADDING + 12, py + 38, Palette.TEXT, false);
        g.drawString(this.font, visibleModules.size() + " Module", px + PADDING + 12 + this.font.width(title) + 16, py + 38, Palette.MUTED, false);

        g.drawString(this.font, "KOLLEGEN", px + PADDING + 12, py + 18, Palette.ACCENT, false);
        g.drawString(this.font, "Client", px + PADDING + 12 + this.font.width("KOLLEGEN") + 6, py + 18, Palette.MUTED, false);

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