package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.feed.FeedImageManager;
import dev.kollegen.client.feed.FeedManager;
import dev.kollegen.client.feed.JustNowManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.io.File;

public class ScreenshotShareScreen extends Screen {

    private final Screen parent;
    private final File screenshotFile;
    private final String imagePath;

    private boolean showFeedCaptionModal = false;
    private EditBox captionInput;

    public ScreenshotShareScreen(Screen parent, File screenshotFile) {
        super(Component.literal("Screenshot teilen"));
        this.parent = parent;
        this.screenshotFile = screenshotFile;
        this.imagePath = screenshotFile != null ? screenshotFile.getAbsolutePath() : "";
    }

    @Override
    protected void init() {
        clearWidgets();

        int topY = 14;
        int barW = 560;
        int barH = 50;
        int barX = (this.width - barW) / 2;

        int curX = barX + barW - 32;

        GlassButton closeBtn = new GlassButton(curX, topY + 12, 24, 26, Component.literal("✕"), btn -> {
            Minecraft.getInstance().setScreenAndShow(this.parent);
        });
        addRenderableWidget(closeBtn);
        curX -= 118;

        GlassButton feedBtn = new GlassButton(curX, topY + 12, 112, 26, Component.literal("📸 Im Feed"), btn -> {
            showFeedCaptionModal = !showFeedCaptionModal;
            init();
        });
        addRenderableWidget(feedBtn);
        curX -= 8;

        boolean jnActive = JustNowManager.isJustNowActive();
        boolean jnLate = JustNowManager.canPostLate();
        boolean jnDone = JustNowManager.hasPostedToday();

        String jnText;
        int btnW = 160;
        if (jnActive) {
            jnText = "⚡ JustNow (" + JustNowManager.getRemainingFormatted() + ")";
            btnW = 168;
        } else if (jnLate) {
            jnText = "⚡ JustNow (Zu spät)";
            btnW = 152;
        } else if (jnDone) {
            jnText = "⚡ JustNow erledigt ✓";
            btnW = 152;
        } else {
            jnText = "⚡ JustNow";
            btnW = 120;
        }

        curX -= btnW;
        GlassButton justNowBtn = new GlassButton(curX, topY + 12, btnW, 26, Component.literal(jnText), btn -> {
            if (!jnDone) {
                JustNowManager.postJustNow(this.imagePath, "Tägliches JustNow!");
                Minecraft.getInstance().setScreenAndShow(this.parent);
            }
        });
        if (jnDone) {
            justNowBtn.active = false;
        }
        addRenderableWidget(justNowBtn);

        if (showFeedCaptionModal) {
            int modalW = 420;
            int modalH = 80;
            int modalX = (this.width - modalW) / 2;
            int modalY = topY + barH + 12;

            captionInput = new EditBox(this.font, modalX + 14, modalY + 36, modalW - 120, 26, Component.literal("Caption"));
            captionInput.setMaxLength(140);
            captionInput.setHint(Component.literal("Beschreibe deinen Moment…"));
            addRenderableWidget(captionInput);

            GlassButton submitPostBtn = new GlassButton(modalX + modalW - 98, modalY + 36, 84, 26, Component.literal("Posten"), btn -> {
                String cap = captionInput != null ? captionInput.getValue().trim() : "";
                String srv = getCurrentServerName();
                FeedManager.addFeedPost(cap, this.imagePath, srv);
                Minecraft.getInstance().setScreenAndShow(this.parent);
            });
            addRenderableWidget(submitPostBtn);
        } else {
            captionInput = null;
        }
    }

    private String getCurrentServerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && mc.getCurrentServer().name != null) {
            return mc.getCurrentServer().name;
        }
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
            return mc.getCurrentServer().ip;
        }
        if (mc.hasSingleplayerServer()) {
            return "Singleplayer";
        }
        return "Minecraft";
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        if (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER || ki.isConfirmation()) {
            if (captionInput != null && captionInput.isFocused()) {
                String cap = captionInput.getValue().trim();
                FeedManager.addFeedPost(cap, this.imagePath, getCurrentServerName());
                Minecraft.getInstance().setScreenAndShow(this.parent);
                return true;
            }
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0x55000000);

        int topY = 14;
        int barW = 560;
        int barH = 50;
        int barX = (this.width - barW) / 2;

        UiStyleManager.drawCard(g, barX, topY, barW, barH, 10, Palette.PANEL, 0.0f, null);
        Glass.ring(g, barX, topY, barW, barH, 10, 1.2f, Palette.tint(Palette.ACCENT, 0xAA));

        int thumbW = 48;
        int thumbH = 30;
        int thumbX = barX + 12;
        int thumbY = topY + 10;
        if (this.imagePath != null && !this.imagePath.isEmpty()) {
            FeedImageManager.drawImage(g, this.imagePath, thumbX, thumbY, thumbW, thumbH, 4.0f);
        } else {
            Glass.fillRound(g, thumbX, thumbY, thumbW, thumbH, 4, Palette.PANEL2);
            Glass.textC(g, this.font, "📸", thumbX + thumbW / 2, thumbY + 11, 0xFFFFFFFF, false);
        }

        int textX = thumbX + thumbW + 12;
        g.text(this.font, "📸 Screenshot aufgenommen!", textX, topY + 12, Palette.TEXT, true);

        boolean jnActive = JustNowManager.isJustNowActive();
        if (jnActive) {
            g.text(this.font, "⚡ JustNow aktiv! +" + JustNowManager.getCoinsForStreak(JustNowManager.getStreak() + 1) + " Coins", textX, topY + 27, Palette.GREEN, false);
        } else if (JustNowManager.canPostLate()) {
            g.text(this.font, "⚠ 5 Min. vorbei – spät posten rettet Streak!", textX, topY + 27, Palette.ACCENT2, false);
        } else if (JustNowManager.hasPostedToday()) {
            g.text(this.font, "✓ Heutiges JustNow erledigt (Streak: " + JustNowManager.getStreak() + ")", textX, topY + 27, Palette.ACCENT, false);
        } else {
            g.text(this.font, "Teile dieses Bild mit deinen Kollegen", textX, topY + 27, Palette.MUTED, false);
        }

        if (showFeedCaptionModal) {
            int modalW = 420;
            int modalH = 76;
            int modalX = (this.width - modalW) / 2;
            int modalY = topY + barH + 12;

            UiStyleManager.drawCard(g, modalX, modalY, modalW, modalH, 8, Palette.PANEL, 0.0f, null);
            Glass.ring(g, modalX, modalY, modalW, modalH, 8, 1.0f, Palette.ACCENT);
            g.text(this.font, "📸 Bild im Feed teilen:", modalX + 14, modalY + 12, Palette.TEXT, true);
        }

        super.extractRenderState(g, mx, my, pt);
    }
}
