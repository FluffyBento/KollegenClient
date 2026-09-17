package dev.kollegen.client.menu;

import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;


public class ThemeSelectorScreen extends Screen {
    private final Screen parent;

    private static final int THEME_W = 240;
    private static final int THEME_H = 140;
    private static final int GAP = 16;
    private static final int COLS = 3;

    private int scroll = 0;
    private int maxScroll = 0;
    private boolean dragScroll = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    private final List<Palette.Theme> themes;

    public ThemeSelectorScreen(Screen parent) {
        super(Component.literal("Thema auswählen"));
        this.parent = parent;
        this.themes = List.copyOf(Palette.getThemes().values());
    }

    @Override
    protected void init() {
        int totalH = ((themes.size() + COLS - 1) / COLS) * (THEME_H + GAP) + GAP;
        int visibleH = this.height - 120;
        maxScroll = Math.max(0, totalH - visibleH);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        if (button == 0 && maxScroll > 0) {
            int sidebarX = (this.width - (COLS * THEME_W + (COLS - 1) * GAP)) / 2 - 20;
            if (mx >= sidebarX - 10 && mx <= sidebarX + 6) {
                dragScroll = true;
                dragStartY = (int) my;
                dragStartScroll = scroll;
                return true;
            }
        }

        int startX = (this.width - (COLS * THEME_W + (COLS - 1) * GAP)) / 2;
        int startY = 80 - scroll;

        for (int i = 0; i < themes.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = startX + col * (THEME_W + GAP);
            int y = startY + row * (THEME_H + GAP);

            if (mx >= x && mx <= x + THEME_W && my >= y && my <= y + THEME_H) {
                Palette.setTheme(themes.get(i).id);
                rebuildWidgets();
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragScroll && event.button() == 0) {
            dragScroll = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragScroll && event.button() == 0) {
            int delta = (int) Math.round(dy);
            if (delta != 0) {
                scroll = Math.max(0, Math.min(maxScroll, dragStartScroll + delta));
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 24)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent ki) {
        if (ki.key() == 256) {
            close();
            return true;
        }
        return super.keyPressed(ki);
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));

        int panelW = Math.min(this.width - 80, COLS * THEME_W + (COLS - 1) * GAP + 60);
        int panelH = Math.min(this.height - 80, 520);
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        Glass.dropShadow(g, px, py, panelW, panelH, 12, 4, 8);
        Glass.panelVanilla(g, px, py, panelW, panelH, 12);

        g.drawString(this.font, "Thema auswählen", px + (panelW - this.font.width("Thema auswählen")) / 2, py + 20, Palette.TEXT, false);
        g.drawString(this.font, "Klicke auf ein Thema um es anzuwenden", px + (panelW - this.font.width("Klicke auf ein Thema um es anzuwenden")) / 2, py + 42, Palette.MUTED, false);

        int startX = px + (panelW - (COLS * THEME_W + (COLS - 1) * GAP)) / 2;
        int startY = py + 70 - scroll;

        g.enableScissor(px + 16, py + 68, px + panelW - 16, py + panelH - 16);

        for (int i = 0; i < themes.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = startX + col * (THEME_W + GAP);
            int y = startY + row * (THEME_H + GAP);

            if (y + THEME_H < py + 68 || y > py + panelH - 16) continue;

            Palette.Theme t = themes.get(i);
            boolean isCurrent = t.id.equals(Palette.getCurrentTheme());
            boolean hov = mx >= x && mx <= x + THEME_W && my >= y && my <= y + THEME_H;

            Glass.fillRound(g, x, y, THEME_W, THEME_H, 10, isCurrent ? Palette.FOCUS_BORDER : Palette.BORDER);
            Glass.fillRound(g, x + 1, y + 1, THEME_W - 2, THEME_H - 2, 9,
                    hov ? Palette.tint(Palette.PANEL2, 0x99) : Palette.tint(Palette.PANEL, 0xE0));

            int previewH = 80;
            Glass.fillRoundGradient(g, x + 10, y + 10, THEME_W - 20, previewH, 6, t.bg, t.panel);

            Glass.fillRound(g, x + 10, y + 10 + previewH - 30, THEME_W - 20, 30, 4, t.panel2);
            Glass.fillRound(g, x + 15, y + 10 + previewH - 25, 20, 20, 4, t.accent);
            Glass.fillRound(g, x + 45, y + 10 + previewH - 25, 20, 20, 4, t.accent2);
            Glass.fillRound(g, x + 75, y + 10 + previewH - 25, 20, 20, 4, t.green);

            int textY = y + 10 + previewH + 8;
            g.drawString(this.font, t.name, x + 15, textY, Palette.TEXT, false);
            g.drawString(this.font, t.description, x + 15, textY + this.font.lineHeight + 2, Palette.MUTED, false);

            if (isCurrent) {
                Glass.drawSelectionQuad(g, x, y, THEME_W, THEME_H, Palette.ACCENT);
                g.drawString(this.font, "✓ Aktiv", x + THEME_W - 15 - this.font.width("✓ Aktiv"), textY, Palette.ACCENT, false);
            }
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int trackTop = py + 68;
            int trackBottom = py + panelH - 16;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(40, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            int scrollbarX = px + panelW - 14;
            Glass.scrollbarTrack(g, scrollbarX, trackTop, 6, trackH, 3);
            Glass.scrollbarThumb(g, scrollbarX + 1, thumbY, 4, thumbH, 2, dragScroll);
        }

        Button backBtn = Button.builder(Component.literal("Zurück"), btn -> close())
                .bounds(px + panelW - 110, py + panelH - 40, 90, 28).build();
        backBtn.render(g, mx, my, pt);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}