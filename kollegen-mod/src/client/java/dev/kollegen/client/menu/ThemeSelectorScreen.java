package dev.kollegen.client.menu;

import dev.kollegen.client.Version;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;


public class ThemeSelectorScreen extends Screen {
    private final Screen parent;

    private static final int THEME_W = 240;
    private static final int THEME_H = 130;
    private static final int GAP = 12;
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

        // Close hitbox - right side close button area
        if (button == 0) {
            int closeX = this.width - 100;
            int closeY = 20;
            if (mx >= closeX && mx <= closeX + 90 && my >= closeY && my <= closeY + 30) {
                close();
                return true;
            }
        }

        // Scrollbar hitbox - only scroll when over the entries area
        if (button == 0 && maxScroll > 0) {
            int sidebarX = (this.width - (COLS * THEME_W + (COLS - 1) * GAP)) / 2;
            // Only drag if clicking the scrollbar track area (left side of themes, not entries)
            if (mx >= sidebarX - 14 && mx <= sidebarX + 6) {
                dragScroll = true;
                dragStartY = (int) my;
                dragStartScroll = scroll;
                return true;
            }
        }

        int startX = (this.width - (COLS * THEME_W + (COLS - 1) * GAP)) / 2;
        int startY = 70 - scroll;

        for (int i = 0; i < themes.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = startX + col * (THEME_W + GAP);
            int y = startY + row * (THEME_H + GAP);

            // Check entry click (but not the close button area)
            if (mx >= x && mx <= x + THEME_W && my >= y && my <= y + THEME_H) {
                // Don't trigger if clicking near the close button
                if (mx >= this.width - 100 && mx <= this.width - 10 && my >= 20 && my <= 50) {
                    return true;
                }
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
            // Only scroll if mouse is over the entries grid area, not the right sidebar
            int sidebarX = (this.width - (COLS * THEME_W + (COLS - 1) * GAP)) / 2;
            int entriesStartX = sidebarX;
            int entriesEndX = sidebarX + (COLS * THEME_W + (COLS - 1) * GAP);
            if (mx >= entriesStartX && mx <= entriesEndX) {
                scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 24)));
                return true;
            }
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
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));

        int panelW = Math.min(this.width - 40, COLS * THEME_W + (COLS - 1) * GAP + 60);
        int panelH = Math.min(this.height - 40, 520);
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        Glass.dropShadow(g, px, py, panelW, panelH, 14, 6, 12);
        Glass.panelDark(g, px, py, panelW, panelH, 12);

        g.text(this.font, "Thema auswählen", px + (panelW - this.font.width("Thema auswählen")) / 2, py + 22, Palette.TEXT, false);
        g.text(this.font, "Klicke auf eine Karte für Live-Vorschau", px + (panelW - this.font.width("Klicke auf eine Karte für Live-Vorschau")) / 2, py + 42, Palette.MUTED, false);

        int startX = px + (panelW - (COLS * THEME_W + (COLS - 1) * GAP)) / 2;
        int startY = py + 55 - scroll;

        int contentTop = py + 50;
        int contentBottom = py + panelH - 40;
        g.enableScissor(px + 12, contentTop, px + panelW - 12, contentBottom);

        for (int i = 0; i < themes.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = startX + col * (THEME_W + GAP);
            int y = startY + row * (THEME_H + GAP);

            if (y + THEME_H < contentTop || y > contentBottom) continue;

            Palette.Theme t = themes.get(i);
            boolean isCurrent = t.id.equals(Palette.getCurrentTheme());
            boolean hov = mx >= x && mx <= x + THEME_W && my >= y && my <= y + THEME_H;

            // Minecraft-style card border
            int borderCol = isCurrent ? Palette.ACCENT : (hov ? Palette.ACCENT : Palette.BORDER);
            Glass.fillRound(g, x, y, THEME_W, THEME_H, 10, borderCol);
            Glass.fillRound(g, x + 1, y + 1, THEME_W - 2, THEME_H - 2, 9,
                    hov ? Palette.tint(Palette.PANEL2, 0x99) : Palette.tint(Palette.PANEL, 0xE0));

            int previewH = 70;
            Glass.fillRoundGradient(g, x + 8, y + 8, THEME_W - 16, previewH, 6, t.bg, t.panel);

            Glass.fillRound(g, x + 10, y + 8 + previewH - 28, THEME_W - 20, 24, 4, t.panel2);
            Glass.fillRound(g, x + 14, y + 8 + previewH - 28, 18, 18, 4, t.accent);
            Glass.fillRound(g, x + 44, y + 8 + previewH - 28, 18, 18, 4, t.accent2);
            Glass.fillRound(g, x + 76, y + 8 + previewH - 28, 18, 18, 4, t.green);

            int textY = y + 8 + previewH + 6;
            g.text(this.font, t.name, x + 14, textY, Palette.TEXT, false);
            g.text(this.font, t.description, x + 14, textY + this.font.lineHeight + 2, Palette.MUTED, false);

            if (isCurrent) {
                Glass.fillRound(g, x, y, THEME_W, THEME_H, 10, Palette.ACCENT);
                g.text(this.font, "✓ Aktiv", x + THEME_W - 16 - this.font.width("✓ Aktiv"), textY, Palette.ACCENT, false);
            }
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int trackTop = contentTop;
            int trackBottom = contentBottom;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(36, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            int scrollbarX = px + panelW - 12;
            Glass.scrollbarTrack(g, scrollbarX, trackTop, 6, trackH, 3);
            Glass.scrollbarThumb(g, scrollbarX + 1, thumbY, 4, thumbH, 2, dragScroll);
        }

        // Close button with Minecraft style
        GlassButton backBtn = new GlassButton(px + panelW - 100, py + panelH - 36, 90, 30, Component.literal("Zurück"), btn -> close());
        backBtn.extractRenderState(g, mx, my, pt);

        g.text(this.font, "v" + Version.get(), px + 16, py + panelH - 14, Palette.MUTED, false);

        super.extractRenderState(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}