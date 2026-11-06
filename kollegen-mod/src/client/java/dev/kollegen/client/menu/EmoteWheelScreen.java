package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.emotes.Emote;
import dev.kollegen.client.emotes.EmoteManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;

public class EmoteWheelScreen extends Screen {

    private final Map<String, Float> anims = new HashMap<>();
    private float openAnim = 0.0f;
    private int lastHoveredSlot = -2;

    public EmoteWheelScreen() {
        super(Component.literal("Emote Wheel"));
    }

    private float hv(String id, boolean hov) {
        float cur = anims.getOrDefault(id, 0.0f);
        float nxt = Glass.step(cur, hov ? 1.0f : 0.0f, 16.0f);
        anims.put(id, nxt);
        return nxt;
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 6.0f);
        float open = Glass.ease(this.openAnim);

        g.fill(0, 0, this.width, this.height, Glass.tint(0x000000, (int) (130 * open)));
        Glass.alpha = open;

        float cx = this.width / 2.0f;
        float cy = this.height / 2.0f;

        float rInner = 36.0f;
        float rOuter = 135.0f;
        float rSlot = 88.0f;

        float dx = mx - cx;
        float dy = my - cy;
        float dist = (float) Math.hypot(dx, dy);

        boolean inCenter = dist < rInner;
        int hoveredSlot = -1;

        if (!inCenter && dist <= rOuter + 35.0f) {
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            float adjusted = (angle + 90.0f + 22.5f);
            while (adjusted < 0.0f) adjusted += 360.0f;
            adjusted = adjusted % 360.0f;
            hoveredSlot = (int) (adjusted / 45.0f) % 8;
        }

        if (hoveredSlot != this.lastHoveredSlot) {
            if (hoveredSlot >= 0 || inCenter) {
                playTickSound();
            }
            this.lastHoveredSlot = hoveredSlot;
        }

        Glass.disc(g, cx, cy, rOuter + 14.0f, 0x18000000);
        Glass.ring(g, cx - rOuter, cy - rOuter, rOuter * 2.0f, rOuter * 2.0f, rOuter, 1.0f, 0x22FFFFFF);
        Glass.ring(g, cx - rInner, cy - rInner, rInner * 2.0f, rInner * 2.0f, rInner, 1.0f, 0x33FFFFFF);

        String[] wheel = EmoteManager.getWheel();

        for (int i = 0; i < 8; i++) {
            float theta = (float) Math.toRadians(i * 45.0f - 90.0f);
            float sx = cx + (float) Math.cos(theta) * rSlot;
            float sy = cy + (float) Math.sin(theta) * rSlot;

            float cardW = 66.0f;
            float cardH = 44.0f;
            float cardX = sx - cardW / 2.0f;
            float cardY = sy - cardH / 2.0f;

            boolean isHov = (hoveredSlot == i);
            float hAnim = hv("slot:" + i, isHov);

            String id = (i < wheel.length) ? wheel[i] : null;
            Emote emote = (id != null) ? EmoteManager.getById(id) : null;
            boolean isActive = (emote != null && (EmoteManager.getActiveBaseEmote() == emote || EmoteManager.getActiveActionEmote() == emote));

            int bgCol = isActive ? Glass.a(Palette.ACCENT, 0.35f) : Glass.a(Palette.PANEL, 0.85f);
            if (hAnim > 0.01f) {
                bgCol = Glass.mix(bgCol, 0xFFFFFFFF, 0.12f * hAnim);
            }

            Glass.glassCard(g, cardX, cardY, cardW, cardH, 9, bgCol, Glass.opacity + 0.15f, 0.0f);

            if (isActive) {
                Glass.ring(g, cardX, cardY, cardW, cardH, 9, 1.4f, Palette.ACCENT);
            } else if (hAnim > 0.01f) {
                Glass.ring(g, cardX, cardY, cardW, cardH, 9, 1.0f, Glass.a(0xFFFFFFFF, 0.35f * hAnim));
            }

            if (emote != null) {
                Glass.textC(g, this.font, emote.icon, Math.round(sx - 16), Math.round(cardY + 8), 0xFFFFFFFF, false);
                Glass.badge(g, Math.round(cardX + cardW - 20), Math.round(cardY + 4), 16, 14, 7, Glass.a(Palette.ACCENT, 0.3f), 0xFFFFFFFF, this.font, emote.type.symbol);

                String tName = Glass.trunc(this.font, emote.name, (int) cardW - 8);
                Glass.textC(g, this.font, tName, Math.round(sx), Math.round(cardY + 24), isHov ? 0xFFFFFFFF : Palette.TEXT, true);
            } else {
                Glass.textC(g, this.font, "+", Math.round(sx), Math.round(cardY + 12), Palette.MUTED, false);
                Glass.textC(g, this.font, "Slot " + (i + 1), Math.round(sx), Math.round(cardY + 26), Palette.MUTED, false);
            }
        }

        float hStopAnim = hv("stop_center", inCenter);
        int stopCol = inCenter ? Glass.mix(0x66EF4444, 0xCCEF4444, hStopAnim) : 0x441E1E2E;
        Glass.disc(g, cx, cy, rInner - 2, stopCol);
        Glass.ring(g, cx - rInner + 2, cy - rInner + 2, (rInner - 2) * 2, (rInner - 2) * 2, rInner - 2, 1.2f, inCenter ? 0xFFEF4444 : 0x44FFFFFF);

        Glass.textC(g, this.font, "✕", Math.round(cx), Math.round(cy - 9), inCenter ? 0xFFFFFFFF : Palette.MUTED, true);
        Glass.textC(g, this.font, "Stop", Math.round(cx), Math.round(cy + 4), inCenter ? 0xFFFFFFFF : Palette.MUTED, false);

        float infoY = cy + rOuter + 26.0f;
        if (hoveredSlot >= 0 && hoveredSlot < wheel.length) {
            String id = wheel[hoveredSlot];
            Emote e = EmoteManager.getById(id);
            if (e != null) {
                String title = e.icon + " " + e.name + "  " + e.type.symbol;
                Glass.textC(g, this.font, title, Math.round(cx), Math.round(infoY), 0xFFFFFFFF, true);
                Glass.textC(g, this.font, e.description, Math.round(cx), Math.round(infoY + 14), Palette.MUTED, false);
            }
        } else if (inCenter) {
            Glass.textC(g, this.font, "✕ Alle Emotes beenden", Math.round(cx), Math.round(infoY), 0xFFEF4444, true);
            Glass.textC(g, this.font, "Stoppt laufende und fortdauernde Emotes sofort.", Math.round(cx), Math.round(infoY + 14), Palette.MUTED, false);
        } else {
            Glass.textC(g, this.font, "Wähle einen Emote aus dem Rad", Math.round(cx), Math.round(infoY), Palette.MUTED, false);
        }

        Glass.alpha = 1.0f;
        super.extractRenderState(g, mx, my, pt);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 1) {
            this.onClose();
            return true;
        }

        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            float cx = this.width / 2.0f;
            float cy = this.height / 2.0f;
            float dist = (float) Math.hypot(mx - cx, my - cy);

            if (dist < 36.0f) {
                EmoteManager.stopAllEmotes();
                this.onClose();
                return true;
            }

            String[] wheel = EmoteManager.getWheel();
            for (int i = 0; i < 8; i++) {
                float theta = (float) Math.toRadians(i * 45.0f - 90.0f);
                float sx = cx + (float) Math.cos(theta) * 88.0f;
                float sy = cy + (float) Math.sin(theta) * 88.0f;
                float cardW = 66.0f;
                float cardH = 44.0f;
                if (mx >= sx - cardW / 2.0f && mx <= sx + cardW / 2.0f && my >= sy - cardH / 2.0f && my <= sy + cardH / 2.0f) {
                    if (i < wheel.length && wheel[i] != null && !wheel[i].isEmpty()) {
                        Emote e = EmoteManager.getById(wheel[i]);
                        if (e != null) {
                            EmoteManager.playEmote(e);
                        }
                    }
                    this.onClose();
                    return true;
                }
            }

            if (dist <= 170.0f) {
                float angle = (float) Math.toDegrees(Math.atan2(my - cy, mx - cx));
                float adjusted = (angle + 90.0f + 22.5f);
                while (adjusted < 0.0f) adjusted += 360.0f;
                adjusted = adjusted % 360.0f;
                int slot = (int) (adjusted / 45.0f) % 8;

                if (slot >= 0 && slot < wheel.length) {
                    String id = wheel[slot];
                    Emote e = EmoteManager.getById(id);
                    if (e != null) {
                        EmoteManager.playEmote(e);
                    }
                }
                this.onClose();
                return true;
            }

            this.onClose();
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || event.isEscape()) {
            this.onClose();
            return true;
        }

        if (dev.kollegen.client.mods.modules.Emotes.INSTANCE != null) {
            int targetKey = dev.kollegen.client.mods.modules.Emotes.INSTANCE.hotkey.value;
            if (targetKey > 0 && event.key() == targetKey) {
                this.onClose();
                return true;
            }
        }

        int code = event.key();
        if (code >= InputConstants.KEY_1 && code <= InputConstants.KEY_8) {
            int slot = code - InputConstants.KEY_1;
            String[] wheel = EmoteManager.getWheel();
            if (slot >= 0 && slot < wheel.length) {
                String id = wheel[slot];
                Emote e = EmoteManager.getById(id);
                if (e != null) {
                    EmoteManager.playEmote(e);
                }
            }
            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    private void playTickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
        }
    }

    public boolean isPauseScreen() {
        return false;
    }
}
