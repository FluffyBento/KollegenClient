package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.account.AccountManager;
import dev.kollegen.client.feed.FeedImageManager;
import dev.kollegen.client.feed.FeedManager;
import dev.kollegen.client.feed.JustNowManager;
import dev.kollegen.client.friend.FriendManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.SkinFaceRenderer;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class KollegenFeedScreen extends Screen {

    private final Screen parent;
    private int tab = 0;

    private int scroll = 0;
    private int maxScroll = 0;

    private boolean showCreateModal = false;
    private boolean modalAsJustNow = false;
    private String modalSelectedImage = null;
    private EditBox modalCaptionInput = null;

    private String replyingPostId = null;
    private EditBox commentInput = null;

    private final List<ClickableRect> clickables = new ArrayList<>();
    private final List<FeedManager.FeedPost> displayedPosts = new ArrayList<>();

    private static final class ClickableRect {
        final int x, y, w, h;
        final Runnable action;

        ClickableRect(int x, int y, int w, int h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public KollegenFeedScreen(Screen parent) {
        super(Component.literal("Kollegen Feed"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();
        clickables.clear();

        int pw = Math.min(680, this.width - 24);
        int ph = Math.min(540, this.height - 24);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        int topBtnH = 24;
        int topY = py + 12;

        addRenderableWidget(new GlassButton(px + pw - 30, topY, 22, topBtnH, Component.literal("✕"), btn -> {
            Minecraft.getInstance().setScreenAndShow(this.parent);
        }));

        addRenderableWidget(new GlassButton(px + pw - 136, topY, 100, topBtnH, Component.literal("📸 Neuer Post"), btn -> {
            openCreateModal(false);
        }));

        addRenderableWidget(new GlassButton(px + pw - 236, topY, 94, topBtnH, Component.literal("👥 Social Hub"), btn -> {
            Minecraft.getInstance().setScreenAndShow(new KollegenSocialScreen(this));
        }));

        if (showCreateModal) {
            int modalW = Math.min(520, pw - 32);
            int modalH = 260;
            int mx = (this.width - modalW) / 2;
            int my = (this.height - modalH) / 2;

            modalCaptionInput = new EditBox(this.font, mx + 16, my + 44, modalW - 32, 24, Component.literal("Caption"));
            modalCaptionInput.setMaxLength(140);
            modalCaptionInput.setHint(Component.literal("Was passiert gerade? Beschreibe deinen Moment…"));
            addRenderableWidget(modalCaptionInput);

            int btnY = my + modalH - 36;
            addRenderableWidget(new GlassButton(mx + modalW - 116, btnY, 100, 26, Component.literal("Veröffentlichen"), btn -> {
                submitNewPost();
            }));

            addRenderableWidget(new GlassButton(mx + modalW - 206, btnY, 84, 26, Component.literal("Abbrechen"), btn -> {
                showCreateModal = false;
                buildWidgets();
            }));
        } else {
            modalCaptionInput = null;
        }

        if (replyingPostId != null && !showCreateModal) {
            int inpW = pw - 130;
            int inpY = py + ph - 38;
            commentInput = new EditBox(this.font, px + 16, inpY, inpW, 24, Component.literal("Kommentar"));
            commentInput.setMaxLength(120);
            commentInput.setHint(Component.literal("Kommentar schreiben & Enter drücken…"));
            addRenderableWidget(commentInput);

            addRenderableWidget(new GlassButton(px + 16 + inpW + 8, inpY, 88, 24, Component.literal("Senden"), btn -> {
                submitComment();
            }));
        } else {
            commentInput = null;
        }

        refreshPostsList();
    }

    private void openCreateModal(boolean asJustNow) {
        this.showCreateModal = true;
        this.modalAsJustNow = asJustNow;
        File latest = FeedImageManager.getLatestScreenshot();
        this.modalSelectedImage = latest != null ? latest.getAbsolutePath() : null;
        buildWidgets();
    }

    private void submitNewPost() {
        if (modalCaptionInput == null) return;
        String cap = modalCaptionInput.getValue().trim();
        if (modalAsJustNow) {
            JustNowManager.postJustNow(modalSelectedImage, cap.isEmpty() ? "Tägliches JustNow!" : cap);
        } else {
            FeedManager.addFeedPost(cap, modalSelectedImage, getCurrentServerName());
        }
        showCreateModal = false;
        modalSelectedImage = null;
        modalAsJustNow = false;
        buildWidgets();
    }

    private void submitComment() {
        if (commentInput == null || replyingPostId == null) return;
        String text = commentInput.getValue().trim();
        if (!text.isEmpty()) {
            FeedManager.addComment(replyingPostId, text);
            commentInput.setValue("");
        }
        replyingPostId = null;
        buildWidgets();
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

    private void refreshPostsList() {
        displayedPosts.clear();
        String myName = AccountManager.getActiveUsername();
        List<FeedManager.FeedPost> all = FeedManager.getPosts();

        for (FeedManager.FeedPost p : all) {
            boolean isFriendOrMe = dev.kollegen.client.friend.FriendManager.isFriend(p.authorName)
                    || dev.kollegen.client.friend.FriendManager.isFriendByUuid(p.authorUuid)
                    || p.authorName.equalsIgnoreCase(myName);

            if (tab == 0) {
                if (p.isJustNow && isFriendOrMe) {
                    displayedPosts.add(p);
                }
            } else if (tab == 1) {
                if (!p.isJustNow && isFriendOrMe) {
                    displayedPosts.add(p);
                }
            } else if (tab == 2) {
                displayedPosts.add(p);
            } else if (tab == 3) {
                if (p.authorName.equalsIgnoreCase(myName)) {
                    displayedPosts.add(p);
                }
            }
        }
        computeScroll();
    }

    private void computeScroll() {
        int pw = Math.min(680, this.width - 24);
        int ph = Math.min(540, this.height - 24);
        int py = (this.height - ph) / 2;

        int bannerH = 76;
        int tabsH = 26;
        int listTop = py + 48 + bannerH + 8 + tabsH + 8;
        int listBottom = py + ph - (replyingPostId != null ? 46 : 14);

        int cardW = pw - 32;
        int totalH = 0;
        for (FeedManager.FeedPost p : displayedPosts) {
            totalH += getCardHeight(p, cardW) + 8;
        }

        int visibleH = Math.max(10, listBottom - listTop);
        maxScroll = Math.max(0, totalH - visibleH);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    private int getCardHeight(FeedManager.FeedPost p, int cardW) {
        int h = 54;
        if (p.caption != null && !p.caption.isEmpty()) {
            h += 16;
        }
        if (p.imagePath != null && !p.imagePath.isEmpty()) {
            int imgW = cardW - 24;
            int imgH = FeedImageManager.getDisplayHeight(p.imagePath, imgW, 320);
            h += imgH + 8;
        }
        if (!p.comments.isEmpty()) {
            h += Math.min(3, p.comments.size()) * 14 + 6;
        }
        return h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, bl);
        }

        double mx = event.x();
        double my = event.y();

        for (ClickableRect r : clickables) {
            if (r.hit(mx, my)) {
                if (r.action != null) r.action.run();
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int pw = Math.min(680, this.width - 24);
        int ph = Math.min(540, this.height - 24);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        int listTop = py + 48 + 76 + 8 + 26 + 8;
        int listBottom = py + ph - 14;

        if (maxScroll > 0 && mx >= px && mx <= px + pw && my >= listTop && my <= listBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 28)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            if (showCreateModal) {
                showCreateModal = false;
                buildWidgets();
                return true;
            }
            if (replyingPostId != null) {
                replyingPostId = null;
                buildWidgets();
                return true;
            }
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        if (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER || ki.isConfirmation()) {
            if (showCreateModal && modalCaptionInput != null && modalCaptionInput.isFocused()) {
                submitNewPost();
                return true;
            }
            if (commentInput != null && commentInput.isFocused()) {
                submitComment();
                return true;
            }
        }

        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int pw = Math.min(680, this.width - 24);
        int ph = Math.min(540, this.height - 24);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        UiStyleManager.drawCard(g, px, py, pw, ph, 12, Palette.BG, 0.0f, null);
        Glass.ring(g, px, py, pw, ph, 12, 1.2f, Palette.tint(Palette.ACCENT, 0x99));

        clickables.clear();

        g.text(this.font, "📸 KOLLEGEN FEED & JUSTNOW", px + 18, py + 14, Palette.ACCENT, true);
        g.text(this.font, "Teile Screenshots und JustNow-Momente mit deinen Freunden", px + 18, py + 27, Palette.MUTED, false);

        int bannerY = py + 46;
        int bannerH = 76;
        int bannerW = pw - 32;
        int bannerX = px + 16;

        renderJustNowHero(g, bannerX, bannerY, bannerW, bannerH, mx, my);

        int tabsY = bannerY + bannerH + 8;
        int tabsH = 26;
        renderTabs(g, bannerX, tabsY, bannerW, tabsH, mx, my);

        int listTop = tabsY + tabsH + 8;
        int listBottom = py + ph - (replyingPostId != null ? 46 : 14);
        renderPostsList(g, bannerX, listTop, bannerW, listBottom, mx, my);

        if (showCreateModal) {
            renderCreateModal(g, px, py, pw, ph, mx, my);
        }

        super.extractRenderState(g, mx, my, pt);
    }

    private void renderJustNowHero(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, int mx, int my) {
        boolean active = JustNowManager.isJustNowActive();
        boolean late = JustNowManager.canPostLate();
        boolean posted = JustNowManager.hasPostedToday();
        int streak = JustNowManager.getStreak();

        int bgCol = active ? 0x33A855F7 : (late ? 0x33EAB308 : (posted ? 0x2222C55E : Palette.PANEL));
        UiStyleManager.drawCard(g, bx, by, bw, bh, 8, bgCol, 0.0f, null);
        int ringCol = active ? Palette.ACCENT : (late ? Palette.ACCENT2 : (posted ? Palette.GREEN : Palette.PANEL2));
        Glass.ring(g, bx, by, bw, bh, 8, 1.0f, ringCol);

        int streakBadgeW = 96;
        Glass.badge(g, bx + 12, by + 10, streakBadgeW, 18, 5, 0x44000000, 0xFFF59E0B, this.font, "🔥 " + streak + " Tage Streak");

        int textX = bx + 12 + streakBadgeW + 10;
        if (active) {
            g.text(this.font, "⚡ JUSTNOW JETZT LIVE! Noch " + JustNowManager.getRemainingFormatted() + " übrig!", textX, by + 10, Palette.GREEN, true);
            int potCoins = JustNowManager.getCoinsForStreak(streak + 1);
            g.text(this.font, "Poste jetzt für +" + potCoins + " Coins! Streak steigt auf " + (streak + 1) + " Tage.", textX, by + 23, Palette.TEXT, false);
        } else if (late) {
            g.text(this.font, "⚠️ Heutiges JustNow abgelaufen!", textX, by + 10, Palette.ACCENT2, true);
            g.text(this.font, "Spät posten rettet deine Streak (" + streak + " Tage)! Keine Coins für verspätete Posts.", textX, by + 23, Palette.TEXT, false);
        } else if (posted) {
            g.text(this.font, "✓ Heutiges JustNow abgeschlossen!", textX, by + 10, Palette.GREEN, true);
            g.text(this.font, "Streak (" + streak + " Tage) ist sicher. Morgen wartet die nächste Belohnung!", textX, by + 23, Palette.TEXT, false);
        } else {
            g.text(this.font, "⏳ Tägliches JustNow wartet auf dich!", textX, by + 10, Palette.TEXT, true);
            g.text(this.font, "Wird heute zu einer zufälligen Uhrzeit ausgelöst. Du hast 5 Min. Zeit für extra Coins!", textX, by + 23, Palette.MUTED, false);
        }

        g.text(this.font, "Coins: Tag 1: 100🪙 · Tag 2: 150🪙 · Tag 5: 300🪙 · Tag 10: 400🪙 · Tag 15+: 500🪙 (Max)", bx + 12, by + 56, Palette.MUTED, false);

        int actBtnW = 140;
        int actBtnH = 26;
        int actBtnX = bx + bw - actBtnW - 12;
        int actBtnY = by + 10;
        boolean actHover = mx >= actBtnX && mx <= actBtnX + actBtnW && my >= actBtnY && my <= actBtnY + actBtnH;

        if (active) {
            Glass.fillRound(g, actBtnX, actBtnY, actBtnW, actBtnH, 6, actHover ? Palette.GREEN : Palette.ACCENT);
            Glass.ring(g, actBtnX, actBtnY, actBtnW, actBtnH, 6, 1.0f, 0xFFFFFFFF);
            Glass.textC(g, this.font, "⚡ Jetzt posten!", actBtnX + actBtnW / 2, actBtnY + 8, 0xFFFFFFFF, true);
            clickables.add(new ClickableRect(actBtnX, actBtnY, actBtnW, actBtnH, () -> openCreateModal(true)));
        } else if (late) {
            Glass.fillRound(g, actBtnX, actBtnY, actBtnW, actBtnH, 6, actHover ? Palette.ACCENT2 : 0xFF78350F);
            Glass.ring(g, actBtnX, actBtnY, actBtnW, actBtnH, 6, 1.0f, 0xFFFFFFFF);
            Glass.textC(g, this.font, "⚠️ Spät nachholen", actBtnX + actBtnW / 2, actBtnY + 8, 0xFFFFFFFF, true);
            clickables.add(new ClickableRect(actBtnX, actBtnY, actBtnW, actBtnH, () -> openCreateModal(true)));
        } else if (!posted) {
            Glass.fillRound(g, actBtnX, actBtnY, actBtnW, actBtnH, 6, actHover ? Palette.PANEL : Palette.PANEL2);
            Glass.ring(g, actBtnX, actBtnY, actBtnW, actBtnH, 6, 0.8f, 0x44FFFFFF);
            Glass.textC(g, this.font, "📸 Bild teilen", actBtnX + actBtnW / 2, actBtnY + 8, Palette.TEXT, false);
            clickables.add(new ClickableRect(actBtnX, actBtnY, actBtnW, actBtnH, () -> openCreateModal(false)));
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int tx, int ty, int tw, int th, int mx, int my) {
        String[] titles = { "⚡ JustNow", "📸 Feed", "🌐 Alle Beiträge", "👤 Meine Beiträge" };
        int tabW = 104;

        for (int i = 0; i < titles.length; i++) {
            int bx = tx + i * (tabW + 6);
            boolean sel = (this.tab == i);
            boolean hov = mx >= bx && mx <= bx + tabW && my >= ty && my <= ty + th;

            int bg = sel ? Palette.ACCENT : (hov ? Palette.PANEL : Palette.PANEL2);
            Glass.fillRound(g, bx, ty, tabW, th, 6, bg);
            if (sel) Glass.ring(g, bx, ty, tabW, th, 6, 1.0f, 0xFFFFFFFF);

            Glass.textC(g, this.font, titles[i], bx + tabW / 2, ty + 8, sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED), sel);

            final int tabIdx = i;
            clickables.add(new ClickableRect(bx, ty, tabW, th, () -> {
                this.tab = tabIdx;
                this.scroll = 0;
                refreshPostsList();
            }));
        }
    }

    private void renderPostsList(GuiGraphicsExtractor g, int lx, int ly, int lw, int lbottom, int mx, int my) {
        g.enableScissor(lx, ly, lx + lw, lbottom);

        if (displayedPosts.isEmpty()) {
            int msgY = ly + 40;
            String emptyTitle;
            String emptySub;
            if (tab == 0) {
                emptyTitle = "Keine JustNows von Freunden vorhanden";
                emptySub = "Warte auf den täglichen Alert oder poste selbst ein JustNow!";
            } else if (tab == 1) {
                emptyTitle = "Keine Feed-Beiträge von Freunden vorhanden";
                emptySub = "Deine Freunde haben noch keine Screenshots geteilt.";
            } else if (tab == 2) {
                emptyTitle = "Der Feed ist noch leer";
                emptySub = "Sei der Erste und teile deinen schönsten Moment!";
            } else {
                emptyTitle = "Du hast noch keine Beiträge gepostet";
                emptySub = "Drücke [F2] im Spiel oder klicke oben auf '📸 Neuer Post'!";
            }
            Glass.textC(g, this.font, emptyTitle, lx + lw / 2, msgY, Palette.TEXT, true);
            Glass.textC(g, this.font, emptySub, lx + lw / 2, msgY + 16, Palette.MUTED, false);
            g.disableScissor();
            return;
        }

        String myUsername = AccountManager.getActiveUsername();
        int curY = ly + 2 - scroll;

        for (FeedManager.FeedPost post : displayedPosts) {
            int cardH = getCardHeight(post, lw);
            boolean vis = curY + cardH > ly && curY < lbottom;

            if (vis) {
                renderPostCard(g, post, lx, curY, lw, cardH, mx, my, myUsername);
            }

            curY += cardH + 8;
        }

        g.disableScissor();
    }

    private void renderPostCard(GuiGraphicsExtractor g, FeedManager.FeedPost p, int cx, int cy, int cw, int ch, int mx, int my, String myName) {
        boolean hov = mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch;
        UiStyleManager.drawCard(g, cx, cy, cw, ch, 8, Palette.PANEL, hov ? 1.0f : 0.0f, null);
        if (hov) Glass.ring(g, cx, cy, cw, ch, 8, 1.0f, Palette.tint(Palette.ACCENT, 0x66));

        int avSize = 30;
        int avX = cx + 12;
        int avY = cy + 10;
        SkinFaceRenderer.drawFace(g, p.authorName, null, avX, avY, avSize);
        Glass.ring(g, avX, avY, avSize, avSize, 6, 1.0f, Palette.ACCENT);

        int textX = avX + avSize + 10;
        g.text(this.font, p.authorName, textX, cy + 10, Palette.TEXT, true);

        int nameW = this.font.width(p.authorName);
        int badgeX = textX + nameW + 8;

        if (p.isJustNow) {
            Glass.badge(g, badgeX, cy + 9, 64, 14, 4, 0x44A855F7, Palette.ACCENT, this.font, "⚡ JustNow");
            badgeX += 70;
            if (p.isLate) {
                Glass.badge(g, badgeX, cy + 9, 88, 14, 4, 0x44EAB308, Palette.ACCENT2, this.font, "⚠️ Zu spät (0🪙)");
                badgeX += 94;
            } else {
                String coinBadge = "✓ +" + p.coinsEarned + "🪙";
                int coinW = this.font.width(coinBadge) + 12;
                Glass.badge(g, badgeX, cy + 9, coinW, 14, 4, 0x4422C55E, Palette.GREEN, this.font, coinBadge);
                badgeX += coinW + 6;
            }
            if (p.streakDay > 0) {
                String strBadge = "🔥 Tag " + p.streakDay;
                int strW = this.font.width(strBadge) + 12;
                Glass.badge(g, badgeX, cy + 9, strW, 14, 4, 0x44F59E0B, 0xFFF59E0B, this.font, strBadge);
            }
        } else if (p.serverTag != null && !p.serverTag.isEmpty()) {
            int srvW = this.font.width(p.serverTag) + 12;
            Glass.badge(g, badgeX, cy + 9, srvW, 14, 4, Palette.PANEL2, Palette.MUTED, this.font, p.serverTag);
        }

        String timeStr = p.getTimeAgo();
        int timeW = this.font.width(timeStr);
        g.text(this.font, timeStr, cx + cw - timeW - 14, cy + 10, Palette.MUTED, false);

        int contentY = cy + 30;
        if (p.caption != null && !p.caption.isEmpty()) {
            g.text(this.font, p.caption, textX, contentY, Palette.TEXT, false);
            contentY += 18;
        }

        if (p.imagePath != null && !p.imagePath.isEmpty()) {
            int imgW = cw - 24;
            int imgH = FeedImageManager.getDisplayHeight(p.imagePath, imgW, 320);
            FeedImageManager.drawImage(g, p.imagePath, cx + 12, contentY, imgW, imgH, 6.0f);
            contentY += imgH + 8;
        }

        int likeBtnW = 64;
        int likeBtnH = 20;
        int likeBtnX = cx + 12;
        int likeBtnY = contentY;
        boolean hovLike = mx >= likeBtnX && mx <= likeBtnX + likeBtnW && my >= likeBtnY && my <= likeBtnY + likeBtnH;
        int likeBg = p.likedByMe ? 0x44EF4444 : (hovLike ? Palette.PANEL2 : Palette.BG);
        Glass.fillRound(g, likeBtnX, likeBtnY, likeBtnW, likeBtnH, 4, likeBg);
        Glass.ring(g, likeBtnX, likeBtnY, likeBtnW, likeBtnH, 4, 0.8f, p.likedByMe ? 0xFFEF4444 : 0x33FFFFFF);
        String likeTxt = (p.likedByMe ? "❤️ " : "🤍 ") + p.likes;
        Glass.textC(g, this.font, likeTxt, likeBtnX + likeBtnW / 2, likeBtnY + 6, p.likedByMe ? 0xFFEF4444 : Palette.TEXT, false);

        clickables.add(new ClickableRect(likeBtnX, likeBtnY, likeBtnW, likeBtnH, () -> {
            FeedManager.toggleLike(p.id);
            refreshPostsList();
        }));

        int commBtnW = 100;
        int commBtnX = likeBtnX + likeBtnW + 8;
        boolean hovComm = mx >= commBtnX && mx <= commBtnX + commBtnW && my >= likeBtnY && my <= likeBtnY + likeBtnH;
        Glass.fillRound(g, commBtnX, likeBtnY, commBtnW, likeBtnH, 4, hovComm ? Palette.PANEL2 : Palette.BG);
        Glass.ring(g, commBtnX, likeBtnY, commBtnW, likeBtnH, 4, 0.8f, 0x33FFFFFF);
        Glass.textC(g, this.font, "💬 " + p.comments.size() + " Antworten", commBtnX + commBtnW / 2, likeBtnY + 6, Palette.MUTED, false);

        clickables.add(new ClickableRect(commBtnX, likeBtnY, commBtnW, likeBtnH, () -> {
            replyingPostId = p.id;
            buildWidgets();
        }));

        if (p.authorName.equalsIgnoreCase(myName)) {
            int delBtnW = 22;
            int delBtnX = cx + cw - delBtnW - 12;
            boolean hovDel = mx >= delBtnX && mx <= delBtnX + delBtnW && my >= likeBtnY && my <= likeBtnY + likeBtnH;
            Glass.fillRound(g, delBtnX, likeBtnY, delBtnW, likeBtnH, 4, hovDel ? 0x44EF4444 : Palette.BG);
            Glass.textC(g, this.font, "🗑", delBtnX + delBtnW / 2, likeBtnY + 6, hovDel ? 0xFFEF4444 : Palette.MUTED, false);

            clickables.add(new ClickableRect(delBtnX, likeBtnY, delBtnW, likeBtnH, () -> {
                FeedManager.deletePost(p.id);
                refreshPostsList();
            }));
        }

        if (!p.comments.isEmpty()) {
            int commListY = likeBtnY + likeBtnH + 6;
            int maxShow = Math.min(3, p.comments.size());
            for (int i = 0; i < maxShow; i++) {
                String c = p.comments.get(p.comments.size() - maxShow + i);
                g.text(this.font, "↳ " + c, cx + 18, commListY + i * 14, Palette.MUTED, false);
            }
        }
    }

    private void renderCreateModal(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0xAA000000);

        int modalW = Math.min(520, pw - 32);
        int modalH = 260;
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        UiStyleManager.drawCard(g, modalX, modalY, modalW, modalH, 10, Palette.PANEL, 0.0f, null);
        Glass.ring(g, modalX, modalY, modalW, modalH, 10, 1.2f, Palette.ACCENT);

        String title = modalAsJustNow ? "⚡ Tägliches JustNow einreichen" : "📸 Neuen Beitrag verfassen";
        g.text(this.font, title, modalX + 16, modalY + 14, Palette.TEXT, true);

        g.text(this.font, "Screenshot auswählen:", modalX + 16, modalY + 80, Palette.MUTED, false);

        List<File> recents = FeedImageManager.getRecentScreenshots(6);
        int thumbW = 74;
        int thumbH = 46;
        int thumbY = modalY + 98;

        if (recents.isEmpty()) {
            g.text(this.font, "Keine Screenshots im Ordner gefunden. Drücke [F2] im Spiel!", modalX + 16, thumbY + 12, Palette.ACCENT2, false);
        } else {
            for (int i = 0; i < recents.size(); i++) {
                File f = recents.get(i);
                int tx = modalX + 16 + i * (thumbW + 8);
                boolean isSel = modalSelectedImage != null && modalSelectedImage.equals(f.getAbsolutePath());
                boolean hovThumb = mx >= tx && mx <= tx + thumbW && my >= thumbY && my <= thumbY + thumbH;

                FeedImageManager.drawImage(g, f.getAbsolutePath(), tx, thumbY, thumbW, thumbH, 4.0f);
                if (isSel) {
                    Glass.ring(g, tx, thumbY, thumbW, thumbH, 4.0f, 2.0f, Palette.GREEN);
                    Glass.badge(g, tx + thumbW - 16, thumbY + 4, 12, 12, 3, Palette.GREEN, 0xFFFFFFFF, this.font, "✓");
                } else if (hovThumb) {
                    Glass.ring(g, tx, thumbY, thumbW, thumbH, 4.0f, 1.2f, Palette.ACCENT);
                }

                clickables.add(new ClickableRect(tx, thumbY, thumbW, thumbH, () -> {
                    if (isSel) {
                        modalSelectedImage = null;
                    } else {
                        modalSelectedImage = f.getAbsolutePath();
                    }
                }));
            }
        }

        boolean canToggleJN = JustNowManager.isJustNowActive() || JustNowManager.canPostLate();
        if (canToggleJN) {
            int toggleY = modalY + 160;
            int toggleBoxW = 18;
            int toggleBoxH = 18;
            int tbx = modalX + 16;

            Glass.fillRound(g, tbx, toggleY, toggleBoxW, toggleBoxH, 4, modalAsJustNow ? Palette.ACCENT : Palette.PANEL2);
            Glass.ring(g, tbx, toggleY, toggleBoxW, toggleBoxH, 4, 1.0f, 0x44FFFFFF);
            if (modalAsJustNow) {
                Glass.textC(g, this.font, "✓", tbx + toggleBoxW / 2, toggleY + 5, 0xFFFFFFFF, true);
            }
            g.text(this.font, "⚡ Als JustNow-Post werten (rettet Streak & bringt Coins)", tbx + 26, toggleY + 5, Palette.TEXT, false);

            clickables.add(new ClickableRect(tbx, toggleY, 320, 20, () -> {
                modalAsJustNow = !modalAsJustNow;
            }));
        }
    }
}
