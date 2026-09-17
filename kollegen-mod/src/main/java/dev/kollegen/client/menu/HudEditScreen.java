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

            int borderCol = isDragging ? Palette.ACCENT : (hov ? Palette.ACCENT2 : Palette.BORDER);
            int bgCol = isDragging ? Palette.tint(Palette.ACCENT, 0x40) : (hov ? Palette.tint(Palette.ACCENT, 0x20) : Palette.tint(Palette.BG, 0x80));

            Glass.fillRound(g, x - 4, y - 4, w + 8, h + 8, 6, borderCol);
            Glass.fillRound(g, x - 3, y - 3, w + 6, h + 6, 5, bgCol);

            Glass.drawSelectionQuad(g, x - 4, y - 4, w + 8, h + 8, Palette.ACCENT);

            if (isDragging) {
                g.drawString(this.font, "▌ Ziehen", x + w / 2 - this.font.width("▌ Ziehen") / 2, y - 16, Palette.ACCENT, true);
            } else if (hov) {
                g.drawString(this.font, "✎ Ziehen zum Verschieben", x + w / 2 - this.font.width("✎ Ziehen zum Verschieben") / 2, y - 16, Palette.MUTED, true);
            }

            String name = hm.name;
            int tw = this.font.width(name);
            g.drawString(this.font, name, x + (w - tw) / 2, y + h + 4, Palette.TEXT, true);
        }

        String hint = "HUD bearbeiten · Elemente ziehen · Esc = zurück · Rechtsklick auf Element = Einstellungen";
        int tx = (this.width - this.font.width(hint)) / 2;
        int ty = this.height - 40;
        Glass.fillRound(g, tx - 16, ty - 8, this.font.width(hint) + 32, this.font.lineHeight + 16, 8, Palette.tint(Palette.BG, 0xE0));
        Glass.fillRound(g, tx - 15, ty - 7, this.font.width(hint) + 30, this.font.lineHeight + 14, 7, Palette.tint(Palette.PANEL, 0xF0));
        g.drawString(this.font, hint, tx, ty, Palette.TEXT, true);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}