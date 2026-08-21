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

    private static final int SW = 210;
    private static final int R = 18;
    private static final int ROW_H = 58;
    private static final int SET_H = 34;

    private EditBox search;
    private GlassButton[] catBtns;
    private Button closeBtn;

    private int px, py, pw, ph, cx, cw;
    private int scroll = 0;
    private int maxScroll = 0;

    private static final class Row {
        final boolean isModule;
        final Module module;
        final Setting setting;
        final int y;
        final int h;
        final AbstractWidget widget;
        final AbstractWidget gear;

        Row(boolean isModule, Module module, Setting setting, int y, int h, AbstractWidget widget, AbstractWidget gear) {
            this.isModule = isModule;
            this.module = module;
            this.setting = setting;
            this.y = y;
            this.h = h;
            this.widget = widget;
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
        rebuild();
    }

    private int[] panel() {
        int w = Math.min(this.width - 70, 920);
        int h = Math.min(this.height - 70, 620);
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

        // Sidebar pills
        catBtns = new GlassButton[cats.length];
        for (int i = 0; i < cats.length; i++) {
            int bx = px + 14, by = py + 70 + i * 46, bw = SW - 28, bh = 38;
            final int idx = i;
            GlassButton b = new GlassButton(bx, by, bw, bh,
                    Component.literal(cats[i].icon + "  " + cats[i].display),
                    btn -> {
                        scroll = 0;
                        category = idx;
                        query = "";
                        if (search != null) search.setValue("");
                        rebuild();
                    });
            b.colors(Palette.PANEL, Palette.ACCENT, Palette.TEXT);
            b.selected(i == category);
            catBtns[i] = b;
            addRenderableWidget(b);
        }

        // Close
        closeBtn = Button.builder(Component.literal("✕"), btn -> close()).bounds(px + pw - 36, py + 14, 24, 24).build();
        addRenderableWidget(closeBtn);

        // Search
        search = new EditBox(this.font, cx + 14, py + 16, cw - 28, 22, Component.literal(""));
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

        // Rows
        int contentH = 0;
        int y = py + 54 - scroll;
        for (Module m : visibleModules()) {
            int toggleX = cx + cw - 60;
            int gearX = toggleX - 34;
            GlassToggle t = new GlassToggle(toggleX, y + (ROW_H - 28) / 2, 52, 28, m.enabled, on -> {
                m.enabled = on;
                if (on) m.onEnable();
                else m.onDisable();
                ModuleManager.save();
            });
            t.colors(Palette.ACCENT, Palette.MUTED);
            Button gear = Button.builder(Component.literal("⚙"), btn -> {
                if (expanded.contains(m.id)) expanded.remove(m.id);
                else expanded.add(m.id);
                rebuild();
            }).bounds(gearX, y + (ROW_H - 24) / 2, 28, 24).build();
            addRenderableWidget(t);
            addRenderableWidget(gear);
            rows.add(new Row(true, m, null, y, ROW_H, t, gear));
            y += ROW_H + 8;
            contentH += ROW_H + 8;

            if (expanded.contains(m.id)) {
                for (Setting s : m.settings()) {
                    AbstractWidget w = s.buildWidget(cx + 8, y, cw - 16, SET_H, this);
                    addRenderableWidget(w);
                    rows.add(new Row(false, m, s, y, SET_H, w, null));
                    y += SET_H + 6;
                    contentH += SET_H + 6;
                }
            }
        }
        int visibleH = ph - 68;
        maxScroll = Math.max(0, contentH - visibleH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
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
        // Panel mit dünner Leiste (modern, clean)
        Glass.fillRound(g, px, py, pw, ph, R, Palette.BORDER);
        Glass.fillRound(g, px + 1, py + 1, pw - 2, ph - 2, R - 1, Palette.tint(Palette.PANEL, 0xEE));
        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 12, Palette.tint(Palette.PANEL2, 0xD0));
        g.drawString(this.font, "KOLLEGEN", px + 20, py + 24, Palette.ACCENT, false);
        g.drawString(this.font, "Client", px + 20 + this.font.width("KOLLEGEN") + 6, py + 26, Palette.MUTED, false);

        String title = query.isEmpty() ? cats[category].display : "Suche: " + query;
        g.drawString(this.font, title, cx + 14, py + 20, Palette.TEXT, false);

        // Inhalt mit Scissor begrenzen
        int top = py + 50, bottom = py + ph - 12;
        g.enableScissor(cx, top, cx + cw, bottom);
        for (Row r : rows) {
            if (r.isModule) {
                boolean hov = mx >= cx + 8 && mx <= cx + cw - 8 && my >= r.y && my < r.y + r.h;
                Glass.fillRound(g, cx + 8, r.y, cw - 16, r.h, 10, Palette.tint(Palette.BORDER, 0xAA));
                Glass.fillRound(g, cx + 9, r.y + 1, cw - 18, r.h - 2, 9,
                        hov ? Palette.tint(Palette.PANEL2, 0x99) : Palette.tint(Palette.PANEL2, 0x66));
                g.drawString(this.font, r.module.name, cx + 22, r.y + 11, Palette.TEXT, false);
                g.drawString(this.font, trunc(r.module.description, cw - 200), cx + 22, r.y + 31, Palette.MUTED, false);
                if (r.module.risk != null) {
                    g.drawString(this.font, "⚠ " + trunc(r.module.risk, cw - 60), cx + 22, r.y + 45, Palette.DANGER, false);
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

        // Scrollbar
        if (maxScroll > 0) {
            int trackTop = top, trackBottom = bottom, trackH = trackBottom - trackTop;
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
