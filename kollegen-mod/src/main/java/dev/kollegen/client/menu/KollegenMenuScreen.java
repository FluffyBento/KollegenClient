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

    private static final int SW = 200;
    private static final int R = 10;
    private static final int ROW_H = 52;
    private static final int SET_H = 36;

    private EditBox search;
    private Button closeBtn;
    private Button themeBtn;

    private int px, py, pw, ph, cx, cw;
    private int scroll = 0;
    private int maxScroll = 0;
    private int catScroll = 0;
    private int maxCatScroll = 0;
    private int sidebarTop, sidebarBottom;
    private int catItemH = 40;
    private boolean kollegen$dragActive = false;
    private int kollegen$dragStartY = 0;
    private int kollegen$dragStartScroll = 0;
    private static final int CAT_GAP = 6;

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
        int w = Math.min(this.width - 80, 980);
        int h = Math.min(this.height - 80, 680);
        int y = (this.height - h) / 2 - 20;
        if (y < 12) y = 12;
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
        cx = px + SW + 20;
        cw = pw - SW - 38;

        themeBtn = Button.builder(Component.literal("🎨 Thema"), btn -> {
            Minecraft.getInstance().setScreen(new ThemeSelectorScreen(this));
        }).bounds(px + 16, py + 14, SW - 32, 30).build();
        addRenderableWidget(themeBtn);

        Button socialBtn = Button.builder(Component.literal("🌐 Soziales"),
                btn -> Minecraft.getInstance().setScreen(new KollegenSocialScreen(this)))
                .bounds(px + 16, py + 50, SW - 32, 30).build();
        addRenderableWidget(socialBtn);

        sidebarTop = py + 90;
        sidebarBottom = py + ph - 16;

        int avail = sidebarBottom - sidebarTop;
        int minH = 34;
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

        closeBtn = Button.builder(Component.literal("✕"), btn -> close()).bounds(px + pw - 36, py + 12, 24, 24).build();
        addRenderableWidget(closeBtn);

        search = new EditBox(this.font, cx + 14, py + 16, cw - 28, 26, Component.literal(""));
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

        contentTop = py + 52;
        contentBottom = py + ph - 14;
        boolean hudCat = cats[category] == Category.HUD;
        int topExtra = 0;
        if (hudCat) {
            topExtra = 50;
            int bx = cx + 14;
            int half = (cw - 28) / 2 - 4;
            Button editBtn = Button.builder(
                    Component.literal(HudModule.editMode ? "✓ Elemente verschieben" : "Elemente verschieben"),
                    btn -> {
                        HudModule.editMode = true;
                        Minecraft.getInstance().setScreen(new HudEditScreen(this));
                    }).bounds(bx, contentTop + 8, half, 34).build();
            addRenderableWidget(editBtn);
            Button arrBtn = Button.builder(Component.literal("Auto-Anordnen"),
                    btn -> HudModule.autoArrange()).bounds(bx + half + 8, contentTop + 8, half, 34).build();
            addRenderableWidget(arrBtn);
        }
        int contentH = 0;
        int y = contentTop + 4 + topExtra - scroll;
        for (Module m : visibleModules()) {
            int toggleX = cx + cw - 68;
            int gearX = toggleX - 36;
            boolean vis = y + ROW_H > contentTop && y < contentBottom;
            if (!m.locked && vis) {
                GlassToggle t = new GlassToggle(toggleX, y + (ROW_H - 28) / 2, 58, 28, m.enabled, on -> {
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
                }).bounds(gearX, y + (ROW_H - 26) / 2, 30, 26).build();
                addRenderableWidget(gear);
            }
            rows.add(new Row(true, m, null, y, ROW_H, null));
            y += ROW_H + 10;
            contentH += ROW_H + 10;

            if (expanded.contains(m.id)) {
                for (Setting s : m.settings()) {
                    boolean sv = y + SET_H > contentTop && y < contentBottom;
                    AbstractWidget w = null;
                    if (sv) {
                        w = s.buildWidget(cx + 12, y, cw - 24, SET_H, this);
                        addRenderableWidget(w);
                    }
                    rows.add(new Row(false, m, s, y, SET_H, w));
                    y += SET_H + 8;
                    contentH += SET_H + 8;
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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (kollegen$dragActive && event.button() == 0) {
            int delta = (int) Math.round(dy);
            if (delta != 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll + delta));
                rebuild();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (kollegen$dragActive && event.button() == 0) {
            kollegen$dragActive = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        if (mx >= px + SW && mx <= px + pw - 16 && my >= contentTop && my <= contentBottom && maxScroll > 0 && button == 0) {
            kollegen$dragActive = true;
            kollegen$dragStartY = (int) my;
            kollegen$dragStartScroll = scroll;
            return true;
        }

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
        if (mx >= px && mx <= px + SW && my >= sidebarTop && my <= sidebarBottom && maxCatScroll > 0) {
            catScroll = Math.max(0, Math.min(maxCatScroll, catScroll - (int) (vertical * 24)));
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
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));

        Glass.dropShadow(g, px, py, pw, ph, R, 4, 8);
        Glass.panelVanilla(g, px, py, pw, ph, R);

        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 8, Palette.tint(Palette.PANEL2, 0xCC));

        Glass.fillRound(g, px + 8, py + 8, SW, 4, 2, Palette.tint(Palette.ACCENT, 0xE0));

        g.drawString(this.font, "KOLLEGEN", px + 18, py + 24, Palette.ACCENT, false);
        g.drawString(this.font, "Client", px + 18 + this.font.width("KOLLEGEN") + 4, py + 26, Palette.MUTED, false);

        String title = query.isEmpty() ? cats[category].display : "Suche: " + query;
        g.drawString(this.font, title, cx + 14, py + 22, Palette.TEXT, false);

        g.enableScissor(px + 10, sidebarTop, px + SW - 2, sidebarBottom);
        for (int i = 0; i < cats.length; i++) {
            int by = sidebarTop + i * (catItemH + CAT_GAP) - catScroll;
            if (by + catItemH < sidebarTop || by > sidebarBottom) continue;
            boolean sel = i == category;
            int fill = sel ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0x50);
            Glass.fillRound(g, px + 12, by, SW - 24, catItemH, 8, fill);
            int ty = by + (catItemH - this.font.lineHeight) / 2;
            g.drawString(this.font, cats[i].icon + "  " + cats[i].display, px + 22, ty,
                    sel ? 0xFFffffff : Palette.TEXT, false);
        }
        g.disableScissor();

        if (maxCatScroll > 0) {
            int trackH = sidebarBottom - sidebarTop;
            int thumbH = Math.max(24, (int) ((double) trackH * trackH / (trackH + maxCatScroll)));
            int thumbY = sidebarTop + (int) ((trackH - thumbH) * (catScroll / (double) maxCatScroll));
            Glass.scrollbarThumb(g, px + SW - 8, thumbY, 4, thumbH, 2, false);
        }

        g.enableScissor(cx, contentTop, cx + cw, contentBottom);
        for (Row r : rows) {
            if (r.isModule) {
                boolean hov = mx >= cx + 12 && mx <= cx + cw - 12 && my >= r.y && my < r.y + r.h;
                int borderCol = hov ? Palette.ACCENT : Palette.BORDER;
                Glass.fillRound(g, cx + 12, r.y, cw - 24, r.h, 8, borderCol);
                Glass.fillRound(g, cx + 13, r.y + 1, cw - 26, r.h - 2, 7,
                        hov ? Palette.tint(Palette.PANEL2, 0x99) : Palette.tint(Palette.PANEL2, 0x55));
                g.drawString(this.font, r.module.name, cx + 26, r.y + 10, Palette.TEXT, false);
                g.drawString(this.font, trunc(r.module.description, cw - 220), cx + 26, r.y + 28, Palette.MUTED, false);
                if (r.module.risk != null) {
                    g.drawString(this.font, "⚠ " + trunc(r.module.risk, cw - 60), cx + 26, r.y + 42, Palette.DANGER, false);
                }

                if (r.module.locked) {
                    g.drawString(this.font, "🔒", cx + cw - 60, r.y + (ROW_H - 28) / 2 + 6, Palette.MUTED, false);
                }
            } else {
                g.drawString(this.font, r.setting.name, cx + 26, r.y + (r.h - this.font.lineHeight) / 2, Palette.TEXT, false);
                String vt = r.setting.valueText();
                if (!vt.isEmpty() && r.widget != null) {
                    int vx = r.widget.getX() - 10 - this.font.width(vt);
                    g.drawString(this.font, vt, vx, r.y + (r.h - this.font.lineHeight) / 2, Palette.MUTED, false);
                }
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackTop = contentTop, trackBottom = contentBottom, trackH = trackBottom - trackTop;
            int thumbH = Math.max(28, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Glass.scrollbarThumb(g, cx + cw - 6, thumbY, 4, thumbH, 2, false);
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