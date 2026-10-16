package dev.kollegen.client.menu;

import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.Icons;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditScreen extends Screen {
    private final Screen parent;
    private final Map<String, Float> anims = new HashMap<String, Float>();
    private float openAnim = 0.0f;

    public HudEditScreen() {
        this(null);
    }

    public HudEditScreen(Screen parent) {
        super((Component) Component.literal("HUD bearbeiten"));
        this.parent = parent;
    }

    private float hv(String key, boolean on) {
        Float cur = this.anims.get(key);
        float c = cur == null ? 0.0f : cur;
        c = Glass.step(c, on ? 1.0f : 0.0f, 14.0f);
        this.anims.put(key, c);
        return c;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float barW() {
        return 14 + 14 + this.font.width("Auto-Anordnen") + 14 + 14 + this.font.width("Fertig") + 14 + 18;
    }

    private float barX() {
        return (this.width - this.barW()) / 2.0f;
    }

    private float barY() {
        return this.height - 36;
    }

    private float autoW() {
        return 14 + this.font.width("Auto-Anordnen") + 14;
    }

    private void finish() {
        HudModule.editMode = false;
        ModuleManager.save();
        Minecraft.getInstance().setScreenAndShow((Screen) (this.parent != null ? this.parent : new KollegenMenuScreen(null)));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            float bx = this.barX() + 9;
            float by = this.barY() + 4;
            if (hit(mx, my, bx, by, this.autoW(), 20)) {
                HudModule.autoArrange();
                return true;
            }
            float fx = bx + this.autoW() + 6;
            if (hit(mx, my, fx, by, 14 + this.font.width("Fertig") + 14, 20)) {
                this.finish();
                return true;
            }
            HudModule hm = HudModule.moduleAt(mx, my);
            if (hm != null) {
                HudModule.dragging = hm;
                HudModule.dragOffX = (int) mx - (int) hm.offsetX.value;
                HudModule.dragOffY = (int) my - (int) hm.offsetY.value;
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && HudModule.dragging != null) {
            HudModule.dragging = null;
            ModuleManager.save();
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == 256) {
            this.finish();
            return true;
        }
        return super.keyPressed(ki);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 5.0f);
        float open = Glass.ease(this.openAnim);
        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, (int) (90 * open)));
        Glass.alpha = 1.0f;
        float cxm = this.width / 2.0f;
        float cym = this.height / 2.0f;
        Glass.rr(g, cxm - 0.25f, 0, 0.5f, this.height, 0, 0x14FFFFFF);
        Glass.rr(g, 0, cym - 0.25f, this.width, 0.5f, 0, 0x14FFFFFF);
        ModuleManager.renderHud(g, pt);
        for (Module m : ModuleManager.modules()) {
            if (!(m instanceof HudModule)) {
                continue;
            }
            HudModule hm = (HudModule) m;
            if (!hm.enabled || (!hm.move.value && !HudModule.editMode)) {
                continue;
            }
            float x = hm.lastX;
            float y = hm.lastY;
            float w = hm.lastW;
            float h = hm.lastH;
            boolean dragging = HudModule.dragging == hm;
            boolean hov = hit(mx, my, x, y, w, h);
            float a = this.hv("h:" + hm.id, hov || dragging);
            int col = dragging ? Palette.ACCENT : Glass.mix(0xFFFFFFFF, Palette.ACCENT, a);
            float pad = 3.0f;
            Glass.rr(g, x - pad, y - pad, w + pad * 2, h + pad * 2, 9, Glass.a(Palette.ACCENT, dragging ? 0.22f : 0.08f + 0.10f * a));
            Glass.ring(g, x - pad, y - pad, w + pad * 2, h + pad * 2, 9, dragging ? 1.0f : 0.5f, Glass.a(col, dragging ? 1.0f : 0.5f + 0.5f * a));
            float cr = 2.2f;
            Glass.disc(g, x - pad, y - pad, cr, col);
            Glass.disc(g, x + w + pad, y - pad, cr, col);
            Glass.disc(g, x - pad, y + h + pad, cr, col);
            Glass.disc(g, x + w + pad, y + h + pad, cr, col);
            String label = hm.name;
            if (dragging) {
                label = hm.name + "  " + (int) hm.offsetX.value + ", " + (int) hm.offsetY.value;
            }
            float lw = this.font.width(label) + 14;
            float lx = x + w / 2.0f - lw / 2.0f;
            float ly = y - pad - 17;
            if (ly < 2) {
                ly = y + h + pad + 3;
            }
            lx = Math.max(2, Math.min(this.width - lw - 2, lx));
            Glass.glassCard(g, lx, ly, lw, 14, 7, Palette.BG, 0.85f, 0.0f);
            Glass.textC(g, this.font, label, Math.round(lx + lw / 2), Math.round(ly + 3), dragging ? Palette.ACCENT : Palette.TEXT, false);
        }
        float bx = this.barX();
        float by = this.barY();
        Glass.glassCard(g, bx, by, this.barW(), 28, 14, Palette.BG, 0.88f, 1.0f);
        Icons.draw(g, Icons.MOVE, bx + 10, by + 8, 12, Palette.ACCENT);
        float ax = bx + 9 + 12;
        boolean ha = hit(mx, my, bx + 9, by + 4, this.autoW(), 20);
        float x0 = bx + 9 + 8;
        Glass.pill(g, bx + 9 + 12, by + 4, this.autoW() - 12, 20, this.hv("auto", ha), false, false);
        Glass.textC(g, this.font, "Auto-Anordnen", Math.round(bx + 9 + 12 + (this.autoW() - 12) / 2), Math.round(by + 10), Palette.TEXT, false);
        float fx = bx + 9 + this.autoW() + 6;
        float fw = 14 + this.font.width("Fertig") + 14;
        boolean hf = hit(mx, my, fx, by + 4, fw, 20);
        Glass.pill(g, fx, by + 4, fw, 20, this.hv("done", hf), true, false);
        Glass.textC(g, this.font, "Fertig", Math.round(fx + fw / 2), Math.round(by + 10), 0xFFFFFFFF, false);
        Glass.textC(g, this.font, "Elemente mit der Maus ziehen", this.width / 2, Math.round(by - 12), 0x99FFFFFF, true);
    }
}
