package dev.kollegen.client.menu;

import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.stream.Collectors;


public class HudEditScreen extends Screen {
    private final Screen parent;

    public HudEditScreen() {
        this(null);
    }

    public HudEditScreen(Screen parent) {
        super(Component.literal("HUD bearbeiten"));
        this.parent = parent;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0) {
            HudModule hm = HudModule.moduleAt(event.x(), event.y());
            if (hm != null) {
                HudModule.dragging = hm;
                HudModule.dragOffX = (int) event.x() - (int) hm.offsetX.value;
                HudModule.dragOffY = (int) event.y() - (int) hm.offsetY.value;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && HudModule.dragging != null) {
            HudModule.dragging = null;
            ModuleManager.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == GLFW.GLFW_KEY_ESCAPE) {
            HudModule.editMode = false;
            ModuleManager.save();
            Minecraft.getInstance().setScreen(parent != null ? parent : new KollegenMenuScreen(null));
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        ModuleManager.renderHud(g, pt);

        for (HudModule hm : ModuleManager.modules().stream()
                .filter(m -> m instanceof HudModule)
                .map(m -> (HudModule) m)
                .filter(h -> h.enabled && (h.move.value || HudModule.editMode))
                .collect(Collectors.toList())) {
            int x = hm.lastX;
            int y = hm.lastY;
            int w = hm.lastW;
            int h = hm.lastH;

            boolean isDragging = HudModule.dragging == hm;
            boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;

            int pad = 6;
            int cx = x - pad;
            int cy = y - pad;
            int cw = w + pad * 2;
            int ch = h + pad * 2;

            int borderCol = isDragging ? Palette.ACCENT : (hov ? Palette.ACCENT2 : Palette.BORDER);
            int bgCol = isDragging ? Palette.tint(Palette.ACCENT, 0x30) : (hov ? Palette.tint(Palette.ACCENT, 0x15) : Palette.tint(Palette.BG, 0x60));

            Glass.dropShadow(g, cx - 2, cy - 2, cw + 4, ch + 4, 8, 2, 6);
            Glass.fillRound(g, cx, cy, cw, ch, 8, borderCol);
            Glass.fillRound(g, cx + 1, cy + 1, cw - 2, ch - 2, 7, bgCol);

            Glass.drawSelectionQuad(g, cx, cy, cw, ch, Palette.ACCENT);

            if (isDragging) {
                g.drawString(this.font, "▌ Verschieben", x + w / 2 - this.font.width("▌ Verschieben") / 2, cy - 20, Palette.ACCENT, true);
            } else if (hov) {
                g.drawString(this.font, "✎ Ziehen zum Verschieben", x + w / 2 - this.font.width("✎ Ziehen zum Verschieben") / 2, cy - 20, Palette.MUTED, true);
            }

            String name = hm.name;
            int tw = this.font.width(name);
            g.drawString(this.font, name, x + (w - tw) / 2, y + h + 6, Palette.TEXT, true);
        }

        String hint = "HUD bearbeiten · Elemente ziehen · Esc = zurück · Rechtsklick auf Element = Einstellungen";
        int tx = (this.width - this.font.width(hint)) / 2;
        int ty = this.height - 50;
        Glass.fillRound(g, tx - 20, ty - 10, this.font.width(hint) + 40, this.font.lineHeight + 20, 10, Palette.tint(Palette.BG, 0xE0));
        Glass.fillRound(g, tx - 19, ty - 9, this.font.width(hint) + 38, this.font.lineHeight + 18, 9, Palette.tint(Palette.PANEL, 0xF0));
        g.drawString(this.font, hint, tx, ty, Palette.TEXT, true);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}