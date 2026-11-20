package dev.kollegen.client.scenery;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.modules.Clips;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class F2ActionScreen extends Screen {

    private float card1HoverAnim = 0.0f;
    private float card2HoverAnim = 0.0f;

    public F2ActionScreen() {
        super(Component.literal("F2 Schnellauswahl"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int pw = 390;
        int ph = 180;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        Glass.rr(g, 0, 0, this.width, this.height, 0, 0x66000000);

        Glass.shadowSoft(g, px, py, pw, ph, 14.0f, 16.0f, 2.0f, 0x88000000);
        Glass.glassCard(g, px, py, pw, ph, 14.0f, Palette.PANEL, 0.92f, 0.15f, Glass.a(Palette.BORDER, 0.6f));

        int badgeW = 150;
        int badgeH = 18;
        int badgeX = px + (pw - badgeW) / 2;
        int badgeY = py + 12;
        Glass.rr(g, badgeX, badgeY, badgeW, badgeH, 9.0f, Glass.a(Palette.ACCENT, 0.18f));
        Glass.ring(g, badgeX, badgeY, badgeW, badgeH, 9.0f, 0.8f, Glass.a(Palette.ACCENT, 0.65f));
        Glass.textC(g, this.font, "⚡ Schnellauswahl", badgeX + badgeW / 2, badgeY + 5, Palette.ACCENT, true);

        int cardW = 170;
        int cardH = 96;
        int card1X = px + 18;
        int card2X = px + pw - cardW - 18;
        int cardY = py + 42;

        boolean hov1 = mouseX >= card1X && mouseX <= card1X + cardW && mouseY >= cardY && mouseY <= cardY + cardH;
        boolean hov2 = mouseX >= card2X && mouseX <= card2X + cardW && mouseY >= cardY && mouseY <= cardY + cardH;

        float target1 = hov1 ? 1.0f : 0.0f;
        float target2 = hov2 ? 1.0f : 0.0f;
        this.card1HoverAnim += (target1 - this.card1HoverAnim) * 0.25f;
        this.card2HoverAnim += (target2 - this.card2HoverAnim) * 0.25f;

        renderCard(g, card1X, cardY, cardW, cardH, "Clippen", "Replay-Clip aufnehmen", "[Taste 1]", 0xFF38BDF8, this.card1HoverAnim, true);
        renderCard(g, card2X, cardY, cardW, cardH, "Szenerie", "360° Menü-Hintergrund", "[Taste 2]", 0xFF10B981, this.card2HoverAnim, false);

        Glass.textC(g, this.font, "[1] Clippen  •  [2] Szenerie  •  [ESC] Schließen", px + pw / 2, py + ph - 18, 0x88FFFFFF, false);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphicsExtractor g, int x, int y, int w, int h, String title, String desc, String keyHint, int accent, float hoverAnim, boolean isClip) {
        int cardBg = Glass.mix(0x25FFFFFF, accent, 0.12f * hoverAnim);
        Glass.rr(g, x, y, w, h, 10.0f, cardBg);

        int borderCol = hoverAnim > 0.01f ? Glass.mix(0x35FFFFFF, accent, hoverAnim) : 0x22FFFFFF;
        Glass.ring(g, x, y, w, h, 10.0f, 1.0f + 0.5f * hoverAnim, borderCol);

        if (hoverAnim > 0.01f) {
            Glass.shadowSoft(g, x, y, w, h, 10.0f, 6.0f * hoverAnim, 1.0f, Glass.a(accent, 0.25f * hoverAnim));
        }

        int iconY = y + 16;
        if (isClip) {
            Icons.draw(g, Icons.TARGET, x + (w - 20) / 2.0f, iconY, 20, accent);
        } else {
            Icons.draw(g, Icons.GLOBE, x + (w - 20) / 2.0f, iconY, 20, accent);
        }

        Glass.textC(g, this.font, title, x + w / 2, y + 42, 0xFFFFFFFF, true);
        Glass.textC(g, this.font, desc, x + w / 2, y + 56, 0xAAFFFFFF, false);

        int pillW = 46;
        int pillH = 14;
        int pillX = x + (w - pillW) / 2;
        int pillY = y + 72;
        Glass.rr(g, pillX, pillY, pillW, pillH, 7.0f, Glass.a(accent, 0.22f));
        Glass.ring(g, pillX, pillY, pillW, pillH, 7.0f, 0.5f, Glass.a(accent, 0.70f));
        Glass.textC(g, this.font, keyHint, pillX + pillW / 2, pillY + 3, accent, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int pw = 390;
            int ph = 180;
            int px = (this.width - pw) / 2;
            int py = (this.height - ph) / 2;
            int cardW = 170;
            int cardH = 96;
            int card1X = px + 18;
            int card2X = px + pw - cardW - 18;
            int cardY = py + 42;

            double mx = event.x();
            double my = event.y();

            if (mx >= card1X && mx <= card1X + cardW && my >= cardY && my <= cardY + cardH) {
                triggerClipAction();
                return true;
            }
            if (mx >= card2X && mx <= card2X + cardW && my >= cardY && my <= cardY + cardH) {
                triggerSceneryAction();
                return true;
            }

            if (mx < px || mx > px + pw || my < py || my > py + ph) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public void onF2Released() {
        if (this.minecraft == null) return;
        double mx = this.minecraft.mouseHandler.xpos() * (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth();
        double my = this.minecraft.mouseHandler.ypos() * (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight();

        int pw = 390;
        int px = (this.width - pw) / 2;
        int py = (this.height - 180) / 2;
        int cardW = 170;
        int cardH = 96;
        int card1X = px + 18;
        int card2X = px + pw - cardW - 18;
        int cardY = py + 42;

        if (mx >= card1X && mx <= card1X + cardW && my >= cardY && my <= cardY + cardH) {
            triggerClipAction();
        } else if (mx >= card2X && mx <= card2X + cardW && my >= cardY && my <= cardY + cardH) {
            triggerSceneryAction();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_1 || key == InputConstants.KEY_NUMPAD1 || key == InputConstants.KEY_C) {
            triggerClipAction();
            return true;
        }
        if (key == InputConstants.KEY_2 || key == InputConstants.KEY_NUMPAD2 || key == InputConstants.KEY_S) {
            triggerSceneryAction();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE || event.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void triggerClipAction() {
        this.onClose();
        if (Clips.INSTANCE != null) {
            Clips.INSTANCE.triggerClip();
        }
    }

    private void triggerSceneryAction() {
        this.onClose();
        SceneryManager.captureCurrentScenery();
    }
}
