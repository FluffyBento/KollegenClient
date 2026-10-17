package dev.kollegen.client.game;

import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Gemeinsames Grundgeruest fuer die Kollegen-Spiele: Glas-Panel, Titel,
 * Statuszeile, Schliessen-Knopf und die gewoehnte Animation.
 */
public abstract class GameScreen extends Screen {

    protected final Screen parent;
    protected int px;
    protected int py;
    protected int pw;
    protected int ph;
    protected float openAnim = 0.0f;
    protected String status = "";
    protected int maxW = 0;
    protected int maxH = 0;

    protected GameScreen(Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected void frame(int maxW, int maxH) {
        this.maxW = maxW;
        this.maxH = maxH;
        layout();
    }

    protected void layout() {
        if (this.maxW <= 0 || this.maxH <= 0) {
            return;
        }
        this.pw = Math.max(120, Math.min(this.width - 12, this.maxW));
        this.ph = Math.max(120, Math.min(this.height - 12, this.maxH));
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
    }

    public boolean isPauseScreen() {
        return false;
    }

    protected static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Pill-Knopf; liefert zurueck, ob die Maus darauf liegt. */
    protected boolean drawButton(GuiGraphicsExtractor g, double mx, double my, float x, float y, float w, float h,
                                 String label, boolean primary) {
        boolean hov = hit(mx, my, x, y, w, h);
        Glass.pill(g, x, y, w, h, hov ? 1.0f : 0.0f, primary, false);
        Glass.textC(g, this.font, Glass.trunc(this.font, label, (int) w - 8), Math.round(x + w / 2),
                Math.round(y + (h - 8) / 2), primary ? 0xFFFFFFFF : Palette.TEXT, false);
        return hov;
    }

    protected void drawChrome(GuiGraphicsExtractor g, String title, String subtitle) {
        Glass.glassCard(g, this.px, this.py, this.pw, this.ph, 14, Palette.BG,
                Math.min(0.92f, Glass.opacity + 0.12f), 1.0f);
        Glass.rrGrad(g, this.px + 1, this.py + 1, this.pw - 2, 90, 13, Glass.a(Palette.ACCENT, 0.10f), 0x00000000);
        Glass.textScaled(g, this.font, title, this.px + 16, this.py + 12, 1.3f, Palette.TEXT, true);
        if (subtitle != null && !subtitle.isEmpty()) {
            Glass.text(g, this.font, Glass.trunc(this.font, subtitle, this.pw - 120), this.px + 16,
                    this.py + 30, Palette.MUTED, false);
        }
        if (this.status != null && !this.status.isEmpty()) {
            Glass.textR(g, this.font, Glass.trunc(this.font, this.status, this.pw - 200),
                    this.px + this.pw - 46, this.py + 16, Palette.ACCENT, false);
        }
    }

    /** X-Knopf oben rechts. */
    protected boolean closeClicked(MouseButtonEvent event) {
        return event.button() == 0 && hit(event.x(), event.y(), this.px + this.pw - 34, this.py + 12, 22, 22);
    }

    protected void drawClose(GuiGraphicsExtractor g) {
        float x = this.px + this.pw - 34;
        float y = this.py + 12;
        Glass.pill(g, x, y, 22, 22, 0.0f, false, false);
        Glass.line(g, x + 7, y + 7, x + 15, y + 15, 1.4f, Palette.TEXT);
        Glass.line(g, x + 15, y + 7, x + 7, y + 15, 1.4f, Palette.TEXT);
    }

    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == 256) {
            this.leave();
            return true;
        }
        return super.keyPressed(ki);
    }

    protected void leave() {
        closeGame();
        if (this.parent != null) {
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(this.parent);
        } else {
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(null);
        }
    }

    /** Wird beim Verlassen aufgerufen, um Netzwerk-Teilnahme zu beenden. */
    protected void closeGame() {
    }

    /** Rahmen mit Animation zeichnen und Zeichenflaeche fuer das Spielfeld liefern. */
    protected void beginFrame(GuiGraphicsExtractor g, int mx, int my, float pt) {
        layout();
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 5.0f);
        float open = Glass.ease(this.openAnim);
        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, (int) (140 * open)));
        Glass.alpha = open;
        g.pose().pushMatrix();
        g.pose().translate(0.0f, (1.0f - open) * 10.0f);
    }

    protected void endFrame(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        g.pose().popMatrix();
        Glass.alpha = 1.0f;
    }
}