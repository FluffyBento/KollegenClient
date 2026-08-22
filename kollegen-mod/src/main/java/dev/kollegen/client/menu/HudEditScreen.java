package dev.kollegen.client.menu;

import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Temporärer Bearbeitungsmodus fürs HUD: das Mod-Menü wird ausgeblendet und
 * die HUD-Elemente lassen sich direkt auf dem Spielbildschirm mit der Maus
 * verschieben. Escape kehrt zum Mod-Menü zurück; die Positionen werden
 * automatisch gespeichert.
 */
public class HudEditScreen extends Screen {
    public HudEditScreen() {
        super(Component.literal("Kollegen Client – HUD bearbeiten"));
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
            Minecraft.getInstance().setScreen(new KollegenMenuScreen(null));
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Spielwelt wird automatisch hinter dem (transparenten) Screen gezeichnet.
        ModuleManager.renderHud(g, pt);

        String hint = "HUD bearbeiten · Elemente ziehen · Esc = zurück";
        int tx = (this.width - this.font.width(hint)) / 2;
        int ty = this.height - 32;
        g.fill(tx - 12, ty - 6, tx + this.font.width(hint) + 12, ty + this.font.lineHeight + 6, 0x80000000);
        g.drawString(this.font, hint, tx, ty, 0xFFFFFFFF, true);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
