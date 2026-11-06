package dev.kollegen.client.menu;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.account.AccountManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.SkinFaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountSwitcherScreen extends Screen {

    private final Screen parent;
    private final Map<String, Float> anims = new HashMap<>();

    private float openAnim = 0.0f;
    private float scrollT = 0.0f;
    private float scrollV = 0.0f;
    private float maxScroll = 0.0f;

    private int px, py, pw, ph;
    private int cx, cy, cw, ch;

    private AccountManager.Account switchingAccount = null;
    private AccountManager.DeviceCodeSession deviceSession = null;
    private boolean offlineInputActive = false;
    private EditBox offlineBox = null;
    private boolean codeCopied = false;
    private long codeCopiedTime = 0;

    private static final int CARD_H = 46;
    private static final int GAP = 6;

    public AccountSwitcherScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    protected void init() {
        AccountManager.loadAccounts();
        this.pw = Math.min(this.width - 24, 460);
        this.ph = Math.min(this.height - 24, 400);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;

        this.cx = this.px + 14;
        this.cy = this.py + 64;
        this.cw = this.pw - 28;
        this.ch = this.ph - 64 - 52;

        int count = AccountManager.getAccounts().size();
        float total = count * (CARD_H + GAP) - GAP;
        this.maxScroll = Math.max(0.0f, total - this.ch + 4);
        this.scrollT = Math.min(this.scrollT, this.maxScroll);

        this.offlineBox = new EditBox(this.font, this.px + 24, this.py + 110, this.pw - 48, 24, Component.literal("Benutzername"));
        this.offlineBox.setMaxLength(16);
        this.offlineBox.setVisible(false);
        this.addRenderableWidget(this.offlineBox);
    }

    private boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private float hv(String id, boolean hov) {
        float cur = anims.getOrDefault(id, 0.0f);
        float nxt = Glass.step(cur, hov ? 1.0f : 0.0f, 14.0f);
        anims.put(id, nxt);
        return nxt;
    }

    private void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
        } catch (Throwable ignored) {
        }
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 5.0f);
        this.scrollV = Glass.step(this.scrollV, this.scrollT, 20.0f);
        float open = Glass.ease(this.openAnim);

        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, (int) (155 * open)));
        Glass.alpha = open;

        Glass.glassCard(g, this.px, this.py, this.pw, this.ph, 14, Palette.BG, Math.min(0.94f, Glass.opacity + 0.14f), 1.0f);
        Glass.rrGrad(g, this.px + 1, this.py + 1, this.pw - 2, 70, 13, Glass.a(Palette.ACCENT, 0.12f), 0x00000000);

        boolean hClose = hit(mx, my, this.px + this.pw - 32, this.py + 12, 20, 20);
        Glass.pill(g, this.px + this.pw - 32, this.py + 12, 20, 20, hv("close", hClose), false, false);
        Glass.textC(g, this.font, "✕", this.px + this.pw - 22, this.py + 17, hClose ? 0xFFFFFFFF : Palette.MUTED, false);

        if (this.deviceSession != null) {
            renderDeviceCodeView(g, mx, my);
        } else if (this.offlineInputActive) {
            renderOfflineView(g, mx, my);
        } else {
            renderMainAccountList(g, mx, my);
        }

        Glass.alpha = 1.0f;
        super.extractRenderState(g, mx, my, pt);
    }

    private void renderMainAccountList(GuiGraphicsExtractor g, int mx, int my) {
        Glass.textScaled(g, this.font, "Account wechseln", this.px + 16, this.py + 14, 1.3f, Palette.TEXT, true);
        Glass.text(g, this.font, "Wähle einen Account zum Weiterschwitzen oder melde dich an.", this.px + 16, this.py + 32, Palette.MUTED, false);
        Glass.separator(g, this.px + 14, this.py + 52, this.pw - 28, 0x25FFFFFF);

        List<AccountManager.Account> list = AccountManager.getAccounts();
        int count = list.size();
        float total = count * (CARD_H + GAP) - GAP;
        this.maxScroll = Math.max(0.0f, total - this.ch + 4);

        g.enableScissor(this.cx - 2, this.cy, this.cx + this.cw + 2, this.cy + this.ch);

        for (int i = 0; i < list.size(); i++) {
            AccountManager.Account acc = list.get(i);
            float cardX = this.cx;
            float cardY = this.cy + i * (CARD_H + GAP) - this.scrollV;

            if (cardY + CARD_H < this.cy - 4 || cardY > this.cy + this.ch + 4) {
                continue;
            }

            boolean active = AccountManager.isActive(acc);
            boolean inBounds = hit(mx, my, this.cx, this.cy, this.cw, this.ch);
            boolean hCard = inBounds && hit(mx, my, cardX, cardY, this.cw, CARD_H);

            float hAnim = hv("card:" + acc.uuid + ":" + i, hCard);
            int bgCol = active ? Glass.a(Palette.ACCENT, 0.12f) : Glass.a(Palette.PANEL, 0.85f);
            if (hAnim > 0.01f) bgCol = Glass.mix(bgCol, 0xFFFFFFFF, 0.06f * hAnim);

            Glass.glassCard(g, cardX, cardY, this.cw, CARD_H, 8, bgCol, Glass.opacity + 0.1f, 0.0f);
            if (active) {
                Glass.ring(g, cardX, cardY, this.cw, CARD_H, 8, 1.0f, Glass.a(Palette.ACCENT, 0.85f));
            } else if (hAnim > 0.01f) {
                Glass.ring(g, cardX, cardY, this.cw, CARD_H, 8, 0.8f, Glass.a(0xFFFFFFFF, 0.25f * hAnim));
            }

            int faceX = Math.round(cardX + 8);
            int faceY = Math.round(cardY + (CARD_H - 28) / 2.0f);
            SkinFaceRenderer.drawFace(g, acc.username, acc.getParsedUuid(), faceX, faceY, 28);

            int textX = faceX + 36;
            Glass.textBold(g, this.font, acc.username, textX, Math.round(cardY + 10), 0xFFFFFFFF, true);

            String sub;
            int subCol = Palette.MUTED;
            if (this.switchingAccount == acc) {
                sub = "Wird aktiviert...";
                subCol = Palette.ACCENT;
            } else if (active) {
                sub = "Aktiver Account";
                subCol = 0xFF4ADE80;
            } else if (acc.isOffline) {
                sub = "Offline Account";
            } else {
                sub = "Microsoft Account";
            }
            Glass.text(g, this.font, sub, textX, Math.round(cardY + 25), subCol, false);

            if (active) {
                Glass.badge(g, Math.round(cardX + this.cw - 64), Math.round(cardY + (CARD_H - 18) / 2.0f), 52, 18, 9, Palette.ACCENT, 0xFFFFFFFF, this.font, "Aktiv ✓");
            } else if (this.switchingAccount == acc) {
                Glass.badge(g, Math.round(cardX + this.cw - 64), Math.round(cardY + (CARD_H - 18) / 2.0f), 52, 18, 9, 0xFF3B82F6, 0xFFFFFFFF, this.font, "Laden...");
            } else {
                boolean hBtn = inBounds && hit(mx, my, cardX + this.cw - 68, cardY + (CARD_H - 20) / 2.0f, 56, 20);
                Glass.pill(g, cardX + this.cw - 68, cardY + (CARD_H - 20) / 2.0f, 56, 20, hv("sw:" + i, hBtn || hCard), false, false);
                Glass.textC(g, this.font, "Wählen", Math.round(cardX + this.cw - 40), Math.round(cardY + (CARD_H - 8) / 2.0f), (hBtn || hCard) ? 0xFFFFFFFF : Palette.TEXT, false);
            }
        }

        g.disableScissor();

        if (this.maxScroll > 0) {
            Glass.vanillaScrollbar(g, this.cx + this.cw - 3, this.cy, 3, this.ch, this.scrollV, this.maxScroll, false, false);
        }

        float botY = this.py + this.ph - 42;
        Glass.separator(g, this.px + 14, Math.round(botY - 8), this.pw - 28, 0x25FFFFFF);

        float btnW = (this.pw - 36) / 2.0f;
        boolean hMs = hit(mx, my, this.px + 14, botY, btnW, 26);
        Glass.pill(g, this.px + 14, botY, btnW, 26, hv("add_ms", hMs), true, false);
        Glass.textC(g, this.font, "+ Microsoft Account", Math.round(this.px + 14 + btnW / 2.0f), Math.round(botY + 9), 0xFFFFFFFF, true);

        boolean hOff = hit(mx, my, this.px + 22 + btnW, botY, btnW, 26);
        Glass.pill(g, this.px + 22 + btnW, botY, btnW, 26, hv("add_off", hOff), false, false);
        Glass.textC(g, this.font, "+ Offline Account", Math.round(this.px + 22 + btnW + btnW / 2.0f), Math.round(botY + 9), hOff ? 0xFFFFFFFF : Palette.TEXT, false);
    }

    private void renderDeviceCodeView(GuiGraphicsExtractor g, int mx, int my) {
        Glass.textScaled(g, this.font, "Microsoft Anmeldung", this.px + 16, this.py + 14, 1.3f, Palette.TEXT, true);
        Glass.text(g, this.font, "Melde dich über deinen Browser mit deinem Microsoft-Konto an.", this.px + 16, this.py + 32, Palette.MUTED, false);
        Glass.separator(g, this.px + 14, this.py + 52, this.pw - 28, 0x25FFFFFF);

        if (this.deviceSession.finished) {
            Glass.textScaled(g, this.font, "✓ Anmeldung erfolgreich!", this.px + 24, this.py + 100, 1.2f, 0xFF4ADE80, true);
            Glass.text(g, this.font, "Dein Account wurde hinzugefügt und aktiviert.", this.px + 24, this.py + 124, Palette.TEXT, false);

            float bY = this.py + this.ph - 42;
            boolean hDone = hit(mx, my, this.px + 24, bY, this.pw - 48, 26);
            Glass.pill(g, this.px + 24, bY, this.pw - 48, 26, hv("dev_done", hDone), true, false);
            Glass.textC(g, this.font, "Fertig", this.px + this.pw / 2, Math.round(bY + 9), 0xFFFFFFFF, true);
            return;
        }

        if (this.deviceSession.error != null) {
            Glass.textScaled(g, this.font, "Fehler bei der Anmeldung", this.px + 24, this.py + 90, 1.1f, 0xFFEF4444, true);
            Glass.text(g, this.font, this.deviceSession.error, this.px + 24, this.py + 114, Palette.MUTED, false);

            float bY = this.py + this.ph - 42;
            boolean hBack = hit(mx, my, this.px + 24, bY, this.pw - 48, 26);
            Glass.pill(g, this.px + 24, bY, this.pw - 48, 26, hv("dev_err_back", hBack), false, false);
            Glass.textC(g, this.font, "Zurück zur Übersicht", this.px + this.pw / 2, Math.round(bY + 9), Palette.TEXT, false);
            return;
        }

        Glass.text(g, this.font, "1. Gehe auf:", this.px + 24, this.py + 74, Palette.MUTED, false);
        Glass.textBold(g, this.font, this.deviceSession.verificationUri != null ? this.deviceSession.verificationUri : "https://microsoft.com/link", this.px + 24, this.py + 88, Palette.ACCENT, true);

        Glass.text(g, this.font, "2. Gib dort diesen Code ein:", this.px + 24, this.py + 116, Palette.MUTED, false);

        float codeBoxW = this.pw - 48;
        float codeBoxH = 50;
        float codeBoxY = this.py + 130;
        Glass.glassCard(g, this.px + 24, codeBoxY, codeBoxW, codeBoxH, 10, Glass.a(Palette.ACCENT, 0.15f), 0.95f, 0.0f);
        Glass.ring(g, this.px + 24, codeBoxY, codeBoxW, codeBoxH, 10, 1.2f, Glass.a(Palette.ACCENT, 0.7f));

        String code = this.deviceSession.userCode != null ? this.deviceSession.userCode : "...";
        Glass.textScaled(g, this.font, code, this.px + this.pw / 2.0f - (this.font.width(code) * 1.8f) / 2.0f, codeBoxY + 14, 1.8f, 0xFFFFFFFF, true);

        float actW = (this.pw - 58) / 2.0f;
        float actY = codeBoxY + codeBoxH + 14;

        boolean hCopy = hit(mx, my, this.px + 24, actY, actW, 26);
        Glass.pill(g, this.px + 24, actY, actW, 26, hv("copy_code", hCopy), false, false);
        String copyLabel = (this.codeCopied && System.currentTimeMillis() - this.codeCopiedTime < 3000) ? "Kopiert! ✓" : "Code kopieren";
        Glass.textC(g, this.font, copyLabel, Math.round(this.px + 24 + actW / 2.0f), Math.round(actY + 9), hCopy ? 0xFFFFFFFF : Palette.TEXT, false);

        boolean hOpen = hit(mx, my, this.px + 34 + actW, actY, actW, 26);
        Glass.pill(g, this.px + 34 + actW, actY, actW, 26, hv("open_link", hOpen), true, false);
        Glass.textC(g, this.font, "Im Browser öffnen", Math.round(this.px + 34 + actW + actW / 2.0f), Math.round(actY + 9), 0xFFFFFFFF, true);

        long dots = (System.currentTimeMillis() / 400L) % 4L;
        String dotStr = ".".repeat((int) dots);
        Glass.textC(g, this.font, "Warte auf Bestätigung im Browser" + dotStr, this.px + this.pw / 2, Math.round(actY + 44), Palette.MUTED, false);

        float bY = this.py + this.ph - 42;
        boolean hCancel = hit(mx, my, this.px + 24, bY, this.pw - 48, 26);
        Glass.pill(g, this.px + 24, bY, this.pw - 48, 26, hv("dev_cancel", hCancel), false, false);
        Glass.textC(g, this.font, "Abbrechen", this.px + this.pw / 2, Math.round(bY + 9), Palette.MUTED, false);
    }

    private void renderOfflineView(GuiGraphicsExtractor g, int mx, int my) {
        Glass.textScaled(g, this.font, "Offline Account hinzufügen", this.px + 16, this.py + 14, 1.3f, Palette.TEXT, true);
        Glass.text(g, this.font, "Gib einen beliebigen Spielernamen ein für Offline- und LAN-Spiele.", this.px + 16, this.py + 32, Palette.MUTED, false);
        Glass.separator(g, this.px + 14, this.py + 52, this.pw - 28, 0x25FFFFFF);

        Glass.text(g, this.font, "Spielername (1-16 Zeichen):", this.px + 24, this.py + 84, Palette.TEXT, false);

        if (this.offlineBox != null) {
            this.offlineBox.setVisible(true);
        }

        float botY = this.py + this.ph - 42;
        float btnW = (this.pw - 58) / 2.0f;

        boolean valid = this.offlineBox != null && !this.offlineBox.getValue().trim().isEmpty();
        boolean hSave = valid && hit(mx, my, this.px + 24, botY, btnW, 26);
        Glass.pill(g, this.px + 24, botY, btnW, 26, hv("off_save", hSave), true, !valid);
        Glass.textC(g, this.font, "Account aktivieren", Math.round(this.px + 24 + btnW / 2.0f), Math.round(botY + 9), valid ? 0xFFFFFFFF : Palette.MUTED, valid);

        boolean hBack = hit(mx, my, this.px + 34 + btnW, botY, btnW, 26);
        Glass.pill(g, this.px + 34 + btnW, botY, btnW, 26, hv("off_cancel", hBack), false, false);
        Glass.textC(g, this.font, "Abbrechen", Math.round(this.px + 34 + btnW + btnW / 2.0f), Math.round(botY + 9), Palette.TEXT, false);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (hit(mx, my, this.px + this.pw - 32, this.py + 12, 20, 20)) {
            this.close();
            return true;
        }

        if (this.deviceSession != null) {
            if (this.deviceSession.finished) {
                float bY = this.py + this.ph - 42;
                if (hit(mx, my, this.px + 24, bY, this.pw - 48, 26)) {
                    this.deviceSession = null;
                    this.close();
                    return true;
                }
            } else if (this.deviceSession.error != null) {
                float bY = this.py + this.ph - 42;
                if (hit(mx, my, this.px + 24, bY, this.pw - 48, 26)) {
                    this.deviceSession = null;
                    return true;
                }
            } else {
                float codeBoxH = 50;
                float codeBoxY = this.py + 130;
                float actW = (this.pw - 58) / 2.0f;
                float actY = codeBoxY + codeBoxH + 14;

                if (hit(mx, my, this.px + 24, actY, actW, 26)) {
                    if (this.deviceSession.userCode != null) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(this.deviceSession.userCode);
                        this.codeCopied = true;
                        this.codeCopiedTime = System.currentTimeMillis();
                        playClickSound();
                    }
                    return true;
                }

                if (hit(mx, my, this.px + 34 + actW, actY, actW, 26)) {
                    String uri = this.deviceSession.verificationUri != null ? this.deviceSession.verificationUri : "https://microsoft.com/link";
                    openBrowser(uri);
                    playClickSound();
                    return true;
                }

                float bY = this.py + this.ph - 42;
                if (hit(mx, my, this.px + 24, bY, this.pw - 48, 26)) {
                    this.deviceSession.cancelled = true;
                    this.deviceSession = null;
                    playClickSound();
                    return true;
                }
            }
            return true;
        }

        if (this.offlineInputActive) {
            if (this.offlineBox != null && this.offlineBox.mouseClicked(event, bl)) {
                return true;
            }
            float botY = this.py + this.ph - 42;
            float btnW = (this.pw - 58) / 2.0f;

            if (hit(mx, my, this.px + 24, botY, btnW, 26)) {
                if (this.offlineBox != null && !this.offlineBox.getValue().trim().isEmpty()) {
                    AccountManager.addOfflineAccount(this.offlineBox.getValue().trim());
                    this.offlineInputActive = false;
                    this.offlineBox.setVisible(false);
                    playSuccessSound();
                    this.close();
                }
                return true;
            }

            if (hit(mx, my, this.px + 34 + btnW, botY, btnW, 26)) {
                this.offlineInputActive = false;
                if (this.offlineBox != null) this.offlineBox.setVisible(false);
                playClickSound();
                return true;
            }
            return true;
        }

        float botY = this.py + this.ph - 42;
        float btnW = (this.pw - 36) / 2.0f;

        if (hit(mx, my, this.px + 14, botY, btnW, 26)) {
            playClickSound();
            this.deviceSession = AccountManager.startDeviceCodeAuth();
            return true;
        }

        if (hit(mx, my, this.px + 22 + btnW, botY, btnW, 26)) {
            playClickSound();
            this.offlineInputActive = true;
            if (this.offlineBox != null) {
                this.offlineBox.setValue("");
                this.offlineBox.setVisible(true);
                this.setFocused(this.offlineBox);
            }
            return true;
        }

        if (hit(mx, my, this.cx, this.cy, this.cw, this.ch)) {
            List<AccountManager.Account> list = AccountManager.getAccounts();
            for (int i = 0; i < list.size(); i++) {
                AccountManager.Account acc = list.get(i);
                float cardX = this.cx;
                float cardY = this.cy + i * (CARD_H + GAP) - this.scrollV;

                if (hit(mx, my, cardX, cardY, this.cw, CARD_H)) {
                    if (AccountManager.isActive(acc)) {
                        playClickSound();
                        return true;
                    }
                    playClickSound();
                    this.switchingAccount = acc;
                    AccountManager.switchAccountAsync(acc).thenAccept(ok -> {
                        this.switchingAccount = null;
                        if (ok) {
                            playSuccessSound();
                            Minecraft.getInstance().execute(this::close);
                        }
                    });
                    return true;
                }
            }
        }

        return super.mouseClicked(event, bl);
    }

    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!this.offlineInputActive && this.deviceSession == null && this.maxScroll > 0) {
            this.scrollT = Math.max(0.0f, Math.min(this.maxScroll, this.scrollT - (float) (vertical * 26.0)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            this.close();
            return true;
        }
        if (this.offlineInputActive && this.offlineBox != null) {
            if (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER || ki.isConfirmation()) {
                if (!this.offlineBox.getValue().trim().isEmpty()) {
                    AccountManager.addOfflineAccount(this.offlineBox.getValue().trim());
                    this.offlineInputActive = false;
                    this.offlineBox.setVisible(false);
                    playSuccessSound();
                    this.close();
                    return true;
                }
            }
            if (this.offlineBox.keyPressed(ki)) {
                return true;
            }
        }
        return super.keyPressed(ki);
    }

    private void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void playSuccessSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f));
        }
    }

    public void close() {
        if (this.deviceSession != null) {
            this.deviceSession.cancelled = true;
            this.deviceSession = null;
        }
        if (this.offlineInputActive) {
            this.offlineInputActive = false;
            if (this.offlineBox != null) this.offlineBox.setVisible(false);
            return;
        }
        if (this.minecraft != null) {
            if (this.parent instanceof TitleScreen) {
                this.minecraft.setScreenAndShow(new TitleScreen());
            } else {
                this.minecraft.setScreenAndShow(this.parent);
            }
        }
    }

    public boolean isPauseScreen() {
        return false;
    }
}
