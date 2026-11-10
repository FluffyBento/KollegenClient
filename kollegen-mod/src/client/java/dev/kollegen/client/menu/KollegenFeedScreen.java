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

    private float scrollT = 0.0f;
    private float scrollV = 0.0f;
    private float maxScroll = 0.0f;
    private boolean draggingScroll = false;

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

        int topBtnH = 22;
        int topY = py + 10;

        addRenderableWidget(new GlassButton(px + pw - 30, topY, 22, topBtnH, Component.literal("✕"), btn -> {
            Minecraft.getInstance().setScreenAndShow(this.parent);
        }));

        addRenderableWidget(new GlassButton(px + pw - 132, topY, 98, topBtnH, Component.literal("📸 Neuer Post"), btn -> {
            openCreateModal(false);
        }));

        addRenderableWidget(new GlassButton(px + pw - 230, topY, 94, topBtnH, Component.literal("👥 Social Hub"), btn -> {
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
            int inpW = pw - 120;
            int inpY = py + ph - 34;
            commentInput = new EditBox(this.font, px + 14, inpY, inpW, 22, Component.literal("Kommentar"));
            commentInput.setMaxLength(120);
            commentInput.setHint(Component.literal("Kommentar schreiben & Enter drücken…"));
            addRenderableWidget(commentInput);

            addRenderableWidget(new GlassButton(px + 14 + inpW + 6, inpY, 80, 22, Component.literal("Senden"), btn -> {
                submitComment();
            }));
        } else {
            commentInput = null;
        }

        refreshPostsList();
        FeedManager.fetchRemotePosts(() -> {
            Minecraft.getInstance().execute(this::refreshPostsList);
        });
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
                if (p.isJustNow) {
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

        int bannerH = 42;
        int tabsH = 24;
        int listTop = py + 38 + bannerH + 6 + tabsH + 6;
        int listBottom = py + ph - (replyingPostId != null ? 40 : 10);

        int cardW = pw - 28 - 12;
        int totalH = 0;
        for (FeedManager.FeedPost p : displayedPosts) {
            totalH += getCardHeight(p, cardW) + 6;
        }

        int visibleH = Math.max(10, listBottom - listTop);
        maxScroll = Math.max(0.0f, (float) totalH - (float) visibleH);
        if (scrollT > maxScroll) scrollT = maxScroll;
        if (scrollT < 0.0f) scrollT = 0.0f;
    }

    private int getCardHeight(FeedManager.FeedPost p, int cardW) {
        int h = 40;
        if (p.caption != null && !p.caption.isEmpty()) {
            h += 16;
        }
        if (p.imagePath != null && !p.imagePath.isEmpty()) {
            int[] dims = FeedImageManager.getDimensions(p.imagePath);
            int origW = Math.max(1, dims[0]);
            int origH = Math.max(1, dims[1]);
            int maxImgW = Math.min(cardW - 20, 360);
            int maxImgH = 160;
            float scale = Math.min((float) maxImgW / origW, (float) maxImgH / origH);
            int imgH = Math.max(1, Math.round(origH * scale));
            h += imgH + 8;
        }
        h += 18 + 6;
        if (!p.comments.isEmpty()) {
            h += Math.min(3, p.comments.size()) * 13 + 2;
        }
        h += 6;
        return h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, bl);
        }

        double mx = event.x();
        double my = event.y();

        int pw = Math.min(680, this.width - 24);
        int ph = Math.min(540, this.height - 24);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int bannerH = 42;
        int tabsH = 24;
        int listTop = py + 38 + bannerH + 6 + tabsH + 6;
        int listBottom = py + ph - (replyingPostId != null ? 40 : 10);
        int listH = listBottom - listTop;
        int scrollbarW = 6;
        int scrollbarX = px + pw - 14 - scrollbarW;

        if (maxScroll > 0.0f && mx >= scrollbarX - 4 && mx <= scrollbarX + scrollbarW + 4 && my >= listTop && my <= listBottom) {
            this.draggingScroll = true;
            float thumbH = Math.max(18.0f, (float) listH * (float) listH / ((float) listH + maxScroll));
            float ratio = (float) (my - listTop - thumbH / 2.0) / Math.max(1.0f, (float) listH - thumbH);
            this.scrollT = Math.max(0.0f, Math.min(maxScroll, ratio * maxScroll));
            this.scrollV = this.scrollT;
            return true;
        }

        for (ClickableRect r : clickables) {
            if (r.hit(mx, my)) {
                if (r.action != null) r.action.run();
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingScroll && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int pw = Math.min(680, this.width - 24);
            int ph = Math.min(540, this.height - 24);
            int py = (this.height - ph) / 2;
            int bannerH = 42;
            int tabsH = 24;
            int listTop = py + 38 + bannerH + 6 + tabsH + 6;
            int listBottom = py + ph - (replyingPostId != null ? 40 : 10);
            int listH = listBottom - listTop;
            float thumbH = Math.max(18.0f, (float) listH * (float) listH / ((float) listH + maxScroll));
            float ratio = (float) (event.y() - listTop - thumbH / 2.0) / Math.max(1.0f, (float) listH - thumbH);
            this.scrollT = Math.max(0.0f, Math.min(maxScroll, ratio * maxScroll));
            this.scrollV = this.scrollT;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingScroll && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.draggingScroll = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int pw = Math.min(680, this.width - 24);
        int ph = Math.min(540, this.height - 24);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int bannerH = 42;
        int tabsH = 24;
        int listTop = py + 38 + bannerH + 6 + tabsH + 6;
        int listBottom = py + ph - 10;

        if (maxScroll > 0.0f && mx >= px && mx <= px + pw && my >= listTop && my <= listBottom) {
            scrollT = Math.max(0.0f, Math.min(maxScroll, scrollT - (float) (vertical * 44.0)));
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
        scrollV = Glass.step(scrollV, scrollT, 14.0f);

        g.fill(0, 0, this.width, this.height, 0x88000000);

        int pw = Math.min(680, this.width - 24);
        int ph = Math.min(540, this.height - 24);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        if (UiStyleManager.isMinecraftStyle()) {
            UiStyleManager.drawPanel(g, px, py, pw, ph, Palette.BG);
            g.text(this.font, "📸 KOLLEGEN FEED & JUSTNOW", px + 14, py + 11, 0xFF55FF55, true);
            g.text(this.font, "Teile Screenshots und JustNow-Momente mit deinen Freunden", px + 14, py + 23, 0xFFAAAAAA, false);
        } else {
            UiStyleManager.drawCard(g, px, py, pw, ph, 12, Palette.BG, 0.0f, null);
            Glass.ring(g, px, py, pw, ph, 12, 1.2f, Palette.tint(Palette.ACCENT, 0x99));
            g.text(this.font, "📸 KOLLEGEN FEED & JUSTNOW", px + 14, py + 11, Palette.ACCENT, true);
            g.text(this.font, "Teile Screenshots und JustNow-Momente mit deinen Freunden", px + 14, py + 23, Palette.MUTED, false);
        }

        clickables.clear();

        int bannerY = py + 38;
        int bannerH = 42;
        int bannerW = pw - 28;
        int bannerX = px + 14;

        renderJustNowHero(g, bannerX, bannerY, bannerW, bannerH, mx, my);

        int tabsY = bannerY + bannerH + 6;
        int tabsH = 24;
        renderTabs(g, bannerX, tabsY, bannerW, tabsH, mx, my);

        int listTop = tabsY + tabsH + 6;
        int listBottom = py + ph - (replyingPostId != null ? 40 : 10);
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
        UiStyleManager.drawCard(g, bx, by, bw, bh, 6, bgCol, 0.0f, null);
        if (!UiStyleManager.isMinecraftStyle()) {
            int ringCol = active ? Palette.ACCENT : (late ? Palette.ACCENT2 : (posted ? Palette.GREEN : Palette.PANEL2));
            Glass.ring(g, bx, by, bw, bh, 6, 1.0f, ringCol);
        }

        int streakBadgeW = 88;
        Glass.badge(g, bx + 10, by + 7, streakBadgeW, 16, 4, 0x44000000, 0xFFF59E0B, this.font, "🔥 " + streak + (streak == 1 ? " Tag" : " Tage"));

        int actBtnW = 110;
        int actBtnH = 22;
        int actBtnX = bx + bw - actBtnW - 8;
        int actBtnY = by + 10;
        boolean actHover = mx >= actBtnX && mx <= actBtnX + actBtnW && my >= actBtnY && my <= actBtnY + actBtnH;

        int textX = bx + 10 + streakBadgeW + 8;
        int maxTextW = actBtnX - textX - 8;

        if (active) {
            String title = "⚡ JUSTNOW LIVE! Noch " + JustNowManager.getRemainingFormatted() + "!";
            g.text(this.font, Glass.trunc(this.font, title, maxTextW), textX, by + 7, Palette.GREEN, true);
            int potCoins = JustNowManager.getCoinsForStreak(streak + 1);
            String sub = "Poste für +" + potCoins + " Coins! Streak steigt auf " + (streak + 1) + " Tage.";
            g.text(this.font, Glass.trunc(this.font, sub, maxTextW), textX, by + 21, Palette.TEXT, false);
        } else if (late) {
            g.text(this.font, Glass.trunc(this.font, "⚠ Heutiges JustNow abgelaufen!", maxTextW), textX, by + 7, Palette.ACCENT2, true);
            String sub = "Spät posten rettet deine Streak (" + streak + " Tage).";
            g.text(this.font, Glass.trunc(this.font, sub, maxTextW), textX, by + 21, Palette.TEXT, false);
        } else if (posted) {
            g.text(this.font, Glass.trunc(this.font, "✓ Heutiges JustNow abgeschlossen!", maxTextW), textX, by + 7, Palette.GREEN, true);
            String sub = "Streak gesichert. Morgen Belohnung · Coins: 1: 100🪙 · 2: 150🪙 · 5: 300🪙 · 15+: 500🪙";
            g.text(this.font, Glass.trunc(this.font, sub, maxTextW), textX, by + 21, Palette.MUTED, false);
        } else {
            g.text(this.font, Glass.trunc(this.font, "⏳ Tägliches JustNow wartet auf dich!", maxTextW), textX, by + 7, Palette.TEXT, true);
            String sub = "Zufälliger Alarm heute · 5 Min. Zeit für extra Coins!";
            g.text(this.font, Glass.trunc(this.font, sub, maxTextW), textX, by + 21, Palette.MUTED, false);
        }

        if (active) {
            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, actBtnX, actBtnY, actBtnW, actBtnH, actHover ? 1.0f : 0.0f, true, false);
                Glass.textC(g, this.font, "⚡ Jetzt posten!", actBtnX + actBtnW / 2, actBtnY + 7, actHover ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
            } else {
                Glass.fillRound(g, actBtnX, actBtnY, actBtnW, actBtnH, 4, actHover ? Palette.GREEN : Palette.ACCENT);
                Glass.ring(g, actBtnX, actBtnY, actBtnW, actBtnH, 4, 1.0f, 0xFFFFFFFF);
                Glass.textC(g, this.font, "⚡ Jetzt posten!", actBtnX + actBtnW / 2, actBtnY + 7, 0xFFFFFFFF, true);
            }
            clickables.add(new ClickableRect(actBtnX, actBtnY, actBtnW, actBtnH, () -> openCreateModal(true)));
        } else if (late) {
            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, actBtnX, actBtnY, actBtnW, actBtnH, actHover ? 1.0f : 0.0f, true, false);
                Glass.textC(g, this.font, "⚠ Nachholen", actBtnX + actBtnW / 2, actBtnY + 7, actHover ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
            } else {
                Glass.fillRound(g, actBtnX, actBtnY, actBtnW, actBtnH, 4, actHover ? Palette.ACCENT2 : 0xFF78350F);
                Glass.ring(g, actBtnX, actBtnY, actBtnW, actBtnH, 4, 1.0f, 0xFFFFFFFF);
                Glass.textC(g, this.font, "⚠ Nachholen", actBtnX + actBtnW / 2, actBtnY + 7, 0xFFFFFFFF, true);
            }
            clickables.add(new ClickableRect(actBtnX, actBtnY, actBtnW, actBtnH, () -> openCreateModal(true)));
        } else if (!posted) {
            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, actBtnX, actBtnY, actBtnW, actBtnH, actHover ? 1.0f : 0.0f, false, false);
                Glass.textC(g, this.font, "📸 Bild teilen", actBtnX + actBtnW / 2, actBtnY + 7, actHover ? 0xFFFFFFA0 : Palette.TEXT, true);
            } else {
                Glass.fillRound(g, actBtnX, actBtnY, actBtnW, actBtnH, 4, actHover ? Palette.PANEL : Palette.PANEL2);
                Glass.ring(g, actBtnX, actBtnY, actBtnW, actBtnH, 4, 0.8f, 0x44FFFFFF);
                Glass.textC(g, this.font, "📸 Bild teilen", actBtnX + actBtnW / 2, actBtnY + 7, Palette.TEXT, false);
            }
            clickables.add(new ClickableRect(actBtnX, actBtnY, actBtnW, actBtnH, () -> openCreateModal(false)));
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int tx, int ty, int tw, int th, int mx, int my) {
        String[] titles = { "⚡ JustNow", "📸 Feed", "🌐 Alle Beiträge", "👤 Meine Beiträge" };
        int gap = 6;
        int tabW = (tw - (titles.length - 1) * gap) / titles.length;

        for (int i = 0; i < titles.length; i++) {
            int bx = tx + i * (tabW + gap);
            boolean sel = (this.tab == i);
            boolean hov = mx >= bx && mx <= bx + tabW && my >= ty && my <= ty + th;

            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, bx, ty, tabW, th, hov ? 1.0f : 0.0f, sel, false);
                int tc = hov ? 0xFFFFFFA0 : (sel ? 0xFFFFFFFF : Palette.TEXT);
                Glass.textC(g, this.font, titles[i], bx + tabW / 2, ty + 7, tc, true);
            } else {
                int bg = sel ? Palette.ACCENT : (hov ? Palette.PANEL : Palette.PANEL2);
                Glass.fillRound(g, bx, ty, tabW, th, 4, bg);
                if (sel) Glass.ring(g, bx, ty, tabW, th, 4, 1.0f, 0xFFFFFFFF);
                Glass.textC(g, this.font, titles[i], bx + tabW / 2, ty + 7, sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED), sel);
            }

            final int tabIdx = i;
            clickables.add(new ClickableRect(bx, ty, tabW, th, () -> {
                this.tab = tabIdx;
                this.scrollT = 0.0f;
                this.scrollV = 0.0f;
                refreshPostsList();
            }));
        }
    }

    private void renderPostsList(GuiGraphicsExtractor g, int lx, int ly, int lw, int lbottom, int mx, int my) {
        int scrollbarW = 6;
        int scrollbarX = lx + lw - scrollbarW;
        int listH = lbottom - ly;
        int cardW = lw - (maxScroll > 0.0f ? 12 : 0);

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
        int curY = ly + 2 - Math.round(scrollV);

        for (FeedManager.FeedPost post : displayedPosts) {
            int cardH = getCardHeight(post, cardW);
            boolean vis = curY + cardH > ly && curY < lbottom;

            if (vis) {
                renderPostCard(g, post, lx, curY, cardW, cardH, mx, my, myUsername, ly, lbottom);
            }

            curY += cardH + 6;
        }

        g.disableScissor();

        if (maxScroll > 0.0f) {
            boolean hovScroll = mx >= scrollbarX - 4 && mx <= scrollbarX + scrollbarW + 4 && my >= ly && my <= lbottom;
            Glass.vanillaScrollbar(g, scrollbarX, ly, scrollbarW, listH, scrollV, maxScroll, hovScroll, draggingScroll);
        }
    }

    private void renderPostCard(GuiGraphicsExtractor g, FeedManager.FeedPost p, int cx, int cy, int cw, int ch, int mx, int my, String myName, int listTop, int listBottom) {
        boolean hov = mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch;
        UiStyleManager.drawCard(g, cx, cy, cw, ch, 6, Palette.PANEL, hov ? 1.0f : 0.0f, null);
        if (hov && !UiStyleManager.isMinecraftStyle()) {
            Glass.ring(g, cx, cy, cw, ch, 6, 1.0f, Palette.tint(Palette.ACCENT, 0x66));
        }

        int avSize = 24;
        int avX = cx + 10;
        int avY = cy + 8;
        SkinFaceRenderer.drawFace(g, p.authorName, null, avX, avY, avSize);
        if (!UiStyleManager.isMinecraftStyle()) {
            Glass.ring(g, avX, avY, avSize, avSize, 4, 1.0f, Palette.ACCENT);
        }

        int textX = avX + avSize + 8;
        g.text(this.font, p.authorName, textX, cy + 9, Palette.TEXT, true);

        int nameW = this.font.width(p.authorName);
        int badgeX = textX + nameW + 6;

        if (p.isJustNow) {
            Glass.badge(g, badgeX, cy + 8, 58, 13, 3, 0x44A855F7, Palette.ACCENT, this.font, "⚡ JustNow");
            badgeX += 62;
            if (p.isLate) {
                Glass.badge(g, badgeX, cy + 8, 80, 13, 3, 0x44EAB308, Palette.ACCENT2, this.font, "⚠ Zu spät");
                badgeX += 84;
            } else {
                String coinBadge = "✓ +" + p.coinsEarned + "🪙";
                int coinW = this.font.width(coinBadge) + 10;
                Glass.badge(g, badgeX, cy + 8, coinW, 13, 3, 0x4422C55E, Palette.GREEN, this.font, coinBadge);
                badgeX += coinW + 5;
            }
            if (p.streakDay > 0) {
                String strBadge = "🔥 Tag " + p.streakDay;
                int strW = this.font.width(strBadge) + 10;
                Glass.badge(g, badgeX, cy + 8, strW, 13, 3, 0x44F59E0B, 0xFFF59E0B, this.font, strBadge);
            }
        } else if (p.serverTag != null && !p.serverTag.isEmpty()) {
            int srvW = this.font.width(p.serverTag) + 10;
            Glass.badge(g, badgeX, cy + 8, srvW, 13, 3, Palette.PANEL2, Palette.MUTED, this.font, p.serverTag);
        }

        String timeStr = p.getTimeAgo();
        int timeW = this.font.width(timeStr);
        g.text(this.font, timeStr, cx + cw - timeW - 10, cy + 9, Palette.MUTED, false);

        if (!p.authorName.equalsIgnoreCase(myName)) {
            boolean isFr = FriendManager.isFriend(p.authorName) || FriendManager.isFriendByUuid(p.authorUuid);
            int frBtnW = isFr ? 70 : 88;
            int frBtnH = 14;
            int frBtnX = cx + cw - frBtnW - 10;
            int frBtnY = cy + 22;
            boolean hovFr = mx >= frBtnX && mx <= frBtnX + frBtnW && my >= frBtnY && my <= frBtnY + frBtnH;

            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, frBtnX, frBtnY, frBtnW, frBtnH, hovFr ? 1.0f : 0.0f, isFr, false);
                int btnTc = hovFr ? 0xFFFFFFA0 : (isFr ? 0xFF55FF55 : 0xFFFFFFFF);
                Glass.textC(g, this.font, isFr ? "✓ Freund" : "+ Freund", frBtnX + frBtnW / 2, frBtnY + 3, btnTc, true);
            } else {
                if (isFr) {
                    Glass.badge(g, frBtnX, frBtnY, frBtnW, frBtnH, 3, 0x3322C55E, Palette.GREEN, this.font, "✓ Befreundet");
                } else {
                    int bg = hovFr ? Palette.ACCENT : Palette.PANEL2;
                    Glass.fillRound(g, frBtnX, frBtnY, frBtnW, frBtnH, 3, bg);
                    Glass.ring(g, frBtnX, frBtnY, frBtnW, frBtnH, 3, 0.8f, hovFr ? 0xFFFFFFFF : Palette.ACCENT);
                    Glass.textC(g, this.font, "+ Freund adden", frBtnX + frBtnW / 2, frBtnY + 3, hovFr ? 0xFFFFFFFF : Palette.TEXT, false);
                }
            }

            if (!isFr && frBtnY >= listTop && frBtnY + frBtnH <= listBottom) {
                clickables.add(new ClickableRect(frBtnX, frBtnY, frBtnW, frBtnH, () -> {
                    FriendManager.addFriendAsync(p.authorName, p.authorUuid, ok -> {
                        refreshPostsList();
                    });
                }));
            }
        }

        int contentY = cy + 38;
        if (p.caption != null && !p.caption.isEmpty()) {
            g.text(this.font, p.caption, cx + 12, contentY, Palette.TEXT, false);
            contentY += 16;
        }

        if (p.imagePath != null && !p.imagePath.isEmpty()) {
            int[] dims = FeedImageManager.getDimensions(p.imagePath);
            int origW = Math.max(1, dims[0]);
            int origH = Math.max(1, dims[1]);
            int maxImgW = Math.min(cw - 20, 360);
            int maxImgH = 160;
            float scale = Math.min((float) maxImgW / origW, (float) maxImgH / origH);
            int imgW = Math.max(1, Math.round(origW * scale));
            int imgH = Math.max(1, Math.round(origH * scale));
            int imgX = cx + (cw - imgW) / 2;

            FeedImageManager.drawImage(g, p.imagePath, imgX, contentY, imgW, imgH, 4.0f);
            contentY += imgH + 8;
        }

        int btnH = 18;
        int likeBtnW = 56;
        int likeBtnX = cx + 10;
        int likeBtnY = contentY;
        boolean hovLike = mx >= likeBtnX && mx <= likeBtnX + likeBtnW && my >= likeBtnY && my <= likeBtnY + btnH;

        if (UiStyleManager.isMinecraftStyle()) {
            Glass.drawMinecraftButton(g, likeBtnX, likeBtnY, likeBtnW, btnH, hovLike ? 1.0f : 0.0f, p.likedByMe, false);
            int likeCol = p.likedByMe ? 0xFFFF5555 : (hovLike ? 0xFFFFFFA0 : 0xFFFFFFFF);
            Glass.textC(g, this.font, (p.likedByMe ? "❤ " : "♡ ") + p.likes, likeBtnX + likeBtnW / 2, likeBtnY + 5, likeCol, true);
        } else {
            int likeBg = p.likedByMe ? 0x44EF4444 : (hovLike ? Palette.PANEL2 : Palette.BG);
            Glass.fillRound(g, likeBtnX, likeBtnY, likeBtnW, btnH, 3, likeBg);
            Glass.ring(g, likeBtnX, likeBtnY, likeBtnW, btnH, 3, 0.8f, p.likedByMe ? 0xFFEF4444 : 0x33FFFFFF);
            Glass.textC(g, this.font, (p.likedByMe ? "❤ " : "♡ ") + p.likes, likeBtnX + likeBtnW / 2, likeBtnY + 5, p.likedByMe ? 0xFFEF4444 : Palette.TEXT, false);
        }

        if (likeBtnY >= listTop && likeBtnY + btnH <= listBottom) {
            clickables.add(new ClickableRect(likeBtnX, likeBtnY, likeBtnW, btnH, () -> {
                FeedManager.toggleLike(p.id);
                refreshPostsList();
            }));
        }

        int commBtnW = 90;
        int commBtnX = likeBtnX + likeBtnW + 6;
        boolean hovComm = mx >= commBtnX && mx <= commBtnX + commBtnW && my >= likeBtnY && my <= likeBtnY + btnH;

        if (UiStyleManager.isMinecraftStyle()) {
            Glass.drawMinecraftButton(g, commBtnX, likeBtnY, commBtnW, btnH, hovComm ? 1.0f : 0.0f, false, false);
            int commCol = hovComm ? 0xFFFFFFA0 : Palette.MUTED;
            Glass.textC(g, this.font, "💬 " + p.comments.size() + " Antworten", commBtnX + commBtnW / 2, likeBtnY + 5, commCol, true);
        } else {
            Glass.fillRound(g, commBtnX, likeBtnY, commBtnW, btnH, 3, hovComm ? Palette.PANEL2 : Palette.BG);
            Glass.ring(g, commBtnX, likeBtnY, commBtnW, btnH, 3, 0.8f, 0x33FFFFFF);
            Glass.textC(g, this.font, "💬 " + p.comments.size() + " Antworten", commBtnX + commBtnW / 2, likeBtnY + 5, Palette.MUTED, false);
        }

        if (likeBtnY >= listTop && likeBtnY + btnH <= listBottom) {
            clickables.add(new ClickableRect(commBtnX, likeBtnY, commBtnW, btnH, () -> {
                replyingPostId = p.id;
                buildWidgets();
            }));
        }

        if (p.authorName.equalsIgnoreCase(myName)) {
            int delBtnW = 20;
            int delBtnX = cx + cw - delBtnW - 10;
            boolean hovDel = mx >= delBtnX && mx <= delBtnX + delBtnW && my >= likeBtnY && my <= likeBtnY + btnH;

            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, delBtnX, likeBtnY, delBtnW, btnH, hovDel ? 1.0f : 0.0f, false, false);
                Glass.textC(g, this.font, "🗑", delBtnX + delBtnW / 2, likeBtnY + 5, hovDel ? 0xFFFF5555 : Palette.MUTED, true);
            } else {
                Glass.fillRound(g, delBtnX, likeBtnY, delBtnW, btnH, 3, hovDel ? 0x44EF4444 : Palette.BG);
                Glass.textC(g, this.font, "🗑", delBtnX + delBtnW / 2, likeBtnY + 5, hovDel ? 0xFFEF4444 : Palette.MUTED, false);
            }

            if (likeBtnY >= listTop && likeBtnY + btnH <= listBottom) {
                clickables.add(new ClickableRect(delBtnX, likeBtnY, delBtnW, btnH, () -> {
                    FeedManager.deletePost(p.id);
                    refreshPostsList();
                }));
            }
        }

        contentY += btnH + 6;

        if (!p.comments.isEmpty()) {
            int maxShow = Math.min(3, p.comments.size());
            for (int i = 0; i < maxShow; i++) {
                String c = p.comments.get(p.comments.size() - maxShow + i);
                g.text(this.font, "↳ " + c, cx + 14, contentY, Palette.MUTED, false);
                contentY += 13;
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
