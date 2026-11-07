package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.account.AccountManager;
import dev.kollegen.client.game.*;
import dev.kollegen.client.mcreal.McRealManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.party.PartyManager;
import dev.kollegen.client.presence.PrivacySettings;
import dev.kollegen.client.ui.AccountButton;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.SkinFaceRenderer;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class KollegenSocialScreen extends Screen {

    private final Screen parent;
    private static final int SW = 200;
    private static final int ROW_H = 50;
    private static final int GAP = 6;
    private static final String[] TABS = { "👥 Freunde", "📸 McReal", "🎉 Party", "🎮 Spiele", "📩 Anfragen", "🗂 Gruppen", "💬 Chats" };

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    private String backend = "";
    private String session = "";
    private boolean authed = false;
    private JsonObject state = new JsonObject();
    private String status = "Lade…";

    private int tab = 0;
    private boolean inThread = false;
    private String threadType = "";
    private String threadId = "";
    private String threadTitle = "";
    private long groupSinceMsg = 0;
    private boolean pollBusy = false;
    private long lastPoll = 0;

    private boolean showNewPostModal = false;
    private EditBox mcrealCaptionInput = null;
    private EditBox friendSearchInput = null;

    private final List<Entry> entries = new ArrayList<>();
    private final List<Rect> entryRects = new ArrayList<>();

    private int px, py, pw, ph, cx, cw, contentTop, contentBottom, sidebarTop, sidebarBottom;
    private int scroll = 0;
    private int maxScroll = 0;

    private Button refreshBtn;
    private Button backBtn;
    private EditBox codeInp;
    private EditBox groupInp;
    private EditBox chatInp;

    private static final class Entry {
        final String title;
        final String sub;
        final int color;
        final Runnable act;
        final String playerHead;
        final boolean online;
        final boolean isFriendCard;
        final Runnable partyAct;
        final Runnable chatAct;
        final Runnable joinAct;
        final String joinServer;
        final boolean isMcReal;
        final McRealManager.McRealPost mcRealPost;

        Entry(String title, String sub, int color, Runnable act) {
            this(title, sub, color, act, null, false, false, null, null, null, null, false, null);
        }

        Entry(String title, String sub, int color, Runnable act, String playerHead, boolean online, boolean isFriendCard,
              Runnable partyAct, Runnable chatAct, Runnable joinAct, String joinServer) {
            this(title, sub, color, act, playerHead, online, isFriendCard, partyAct, chatAct, joinAct, joinServer, false, null);
        }

        Entry(String title, String sub, int color, Runnable act, String playerHead, boolean online, boolean isFriendCard,
              Runnable partyAct, Runnable chatAct, Runnable joinAct, String joinServer, boolean isMcReal, McRealManager.McRealPost mcRealPost) {
            this.title = title;
            this.sub = sub;
            this.color = color;
            this.act = act;
            this.playerHead = playerHead;
            this.online = online;
            this.isFriendCard = isFriendCard;
            this.partyAct = partyAct;
            this.chatAct = chatAct;
            this.joinAct = joinAct;
            this.joinServer = joinServer;
            this.isMcReal = isMcReal;
            this.mcRealPost = mcRealPost;
        }
    }

    private static final class Rect {
        final int x, y, w, h;
        final Runnable act;

        Rect(int x, int y, int w, int h, Runnable act) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.act = act;
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public KollegenSocialScreen(Screen parent) {
        super(Component.literal("Kollegen Client – Soziales"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 40, 980);
        int h = Math.min(this.height - 40, 680);
        int y = (this.height - h) / 2;
        if (y < 12) y = 12;
        px = (this.width - w) / 2;
        py = y;
        pw = w;
        ph = h;

        cx = px + SW + 8;
        cw = pw - SW - 16;
        contentTop = py + 72;
        contentBottom = py + ph - 56;
        sidebarTop = py + 62;
        sidebarBottom = py + ph - 16;

        buildWidgets();
        loadClient();
    }

    private void buildWidgets() {
        clearWidgets();

        int topY = py + 14;

        boolean hidden = PrivacySettings.isHideServer();
        String privText = hidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich";
        GlassButton privBtn = new GlassButton(px + pw - 340, topY, 150, 24, Component.literal(privText), btn -> {
            PrivacySettings.toggleHideServer();
            boolean nowHidden = PrivacySettings.isHideServer();
            btn.setMessage(Component.literal(nowHidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich"));
            status = nowHidden ? "Server für Freunde verborgen." : "Server für Freunde sichtbar.";
        });
        addRenderableWidget(privBtn);

        GlassButton switcherBtn = new GlassButton(px + pw - 184, topY, 110, 24, Component.literal("🌐 Server wechseln"), btn ->
                Minecraft.getInstance().setScreenAndShow(new ServerSwitcherScreen(this)));
        addRenderableWidget(switcherBtn);

        refreshBtn = new GlassButton(px + pw - 68, topY, 26, 24, Component.literal("⟳"), btn -> {
            status = "Aktualisiere…";
            loadAll();
        });
        addRenderableWidget(refreshBtn);

        backBtn = new GlassButton(px + pw - 38, topY, 26, 24, Component.literal("✕"), btn -> {
            if (inThread) {
                inThread = false;
                rebuildEntries();
            } else {
                Minecraft.getInstance().setScreenAndShow(parent);
            }
        });
        addRenderableWidget(backBtn);

        if (tab == 0 && !inThread) {
            int inpY = py + ph - 44;
            int btnW = 100;
            codeInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 26, Component.literal("Freundes-Code"));
            codeInp.setMaxLength(10);
            codeInp.setHint(Component.literal("Freundes-Code eingeben…"));
            addRenderableWidget(codeInp);

            addRenderableWidget(new GlassButton(cx + cw - btnW - 6, inpY, btnW, 26, Component.literal("Hinzufügen"), btn -> apiAddFriend()));
        } else if (tab == 1 && !inThread) {
            codeInp = null;
            groupInp = null;
            chatInp = null;
            if (showNewPostModal) {
                int modalY = py + 80;
                int modalW = cw - 32;
                mcrealCaptionInput = new EditBox(this.font, cx + 16, modalY + 36, modalW - 110, 26, Component.literal("Caption"));
                mcrealCaptionInput.setMaxLength(140);
                mcrealCaptionInput.setHint(Component.literal("Was passiert gerade in deiner Welt?"));
                addRenderableWidget(mcrealCaptionInput);

                addRenderableWidget(new GlassButton(cx + 16 + modalW - 100, modalY + 36, 96, 26, Component.literal("📸 Posten"), btn -> {
                    String text = mcrealCaptionInput.getValue().trim();
                    if (!text.isEmpty()) {
                        String srv = getCurrentServerName();
                        McRealManager.addPost(text, srv);
                        showNewPostModal = false;
                        buildWidgets();
                        rebuildEntries();
                    }
                }));
            } else {
                mcrealCaptionInput = null;
            }
        } else if (tab == 5 && !inThread) {
            int inpY = py + ph - 44;
            int btnW = 100;
            groupInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 26, Component.literal("Gruppenname"));
            groupInp.setMaxLength(30);
            groupInp.setHint(Component.literal("Name der neuen Gruppe…"));
            addRenderableWidget(groupInp);

            addRenderableWidget(new GlassButton(cx + cw - btnW - 6, inpY, btnW, 26, Component.literal("Erstellen"), btn -> apiCreateGroup()));
        } else if (inThread) {
            int inpY = py + ph - 44;
            int btnW = 80;
            chatInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 26, Component.literal("Nachricht"));
            chatInp.setMaxLength(300);
            chatInp.setHint(Component.literal("Nachricht eingeben…"));
            addRenderableWidget(chatInp);

            addRenderableWidget(new GlassButton(cx + cw - btnW - 6, inpY, btnW, 26, Component.literal("Senden"), btn -> sendThread()));
        } else {
            codeInp = null;
            groupInp = null;
            chatInp = null;
            mcrealCaptionInput = null;
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
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, bl);

        int tabItemH = 34;
        for (int i = 0; i < TABS.length; i++) {
            int by = sidebarTop + i * (tabItemH + 4);
            if (mx >= px + 10 && mx <= px + SW - 10 && my >= by && my <= by + tabItemH) {
                if (tab != i || inThread) {
                    tab = i;
                    inThread = false;
                    scroll = 0;
                    showNewPostModal = false;
                    buildWidgets();
                    rebuildEntries();
                    return true;
                }
            }
        }

        if (tab == 1 && !inThread) {
            int newBtnX = cx + cw - 120;
            int newBtnY = py + 48;
            if (mx >= newBtnX && mx <= newBtnX + 112 && my >= newBtnY && my <= newBtnY + 24) {
                showNewPostModal = !showNewPostModal;
                buildWidgets();
                return true;
            }
        }

        for (Rect r : entryRects) {
            if (r.hit(mx, my)) {
                if (r.act != null) r.act.run();
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int listTop = py + 72 + (showNewPostModal && tab == 1 ? 84 : 0);
        if (maxScroll > 0 && mx >= cx && mx <= cx + cw && my >= listTop && my <= contentBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 26)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            if (showNewPostModal) {
                showNewPostModal = false;
                buildWidgets();
                return true;
            }
            if (inThread) {
                inThread = false;
                rebuildEntries();
                return true;
            }
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        if (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER || ki.isConfirmation()) {
            if (chatInp != null && chatInp.isFocused()) {
                sendThread();
                return true;
            }
            if (codeInp != null && codeInp.isFocused()) {
                apiAddFriend();
                return true;
            }
            if (groupInp != null && groupInp.isFocused()) {
                apiCreateGroup();
                return true;
            }
            if (mcrealCaptionInput != null && mcrealCaptionInput.isFocused()) {
                String text = mcrealCaptionInput.getValue().trim();
                if (!text.isEmpty()) {
                    McRealManager.addPost(text, getCurrentServerName());
                    showNewPostModal = false;
                    buildWidgets();
                    rebuildEntries();
                    return true;
                }
            }
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xDC));

        UiStyleManager.drawPanel(g, px, py, pw, ph, Palette.BG);

        int sepX = px + SW;
        if (UiStyleManager.isMinecraftStyle()) {
            g.fill(sepX, py + 10, sepX + 2, py + ph - 10, 0xFF2B2B2B);
            g.fill(sepX, py + 10, sepX + 1, py + ph - 10, 0xFF141414);
        } else {
            g.fill(sepX, py + 10, sepX + 1, py + ph - 10, Palette.tint(Palette.PANEL2, 0x88));
        }

        g.text(this.font, "FREUNDE & SOCIAL", px + 18, py + 20, Palette.ACCENT, true);
        g.text(this.font, inThread ? threadTitle : TABS[tab], cx + 12, py + 20, Palette.TEXT, true);

        if (tab == 1 && !inThread) {
            int newBtnX = cx + cw - 120;
            int newBtnY = py + 48;
            boolean hovNew = mx >= newBtnX && mx <= newBtnX + 112 && my >= newBtnY && my <= newBtnY + 24;
            Glass.fillRound(g, newBtnX, newBtnY, 112, 24, 6, hovNew ? Palette.GREEN : Palette.ACCENT);
            if (hovNew) Glass.ring(g, newBtnX, newBtnY, 112, 24, 6, 1.0f, 0xFFFFFFFF);
            Glass.textC(g, this.font, showNewPostModal ? "✕ Abbrechen" : "📸 Neuer Post", newBtnX + 56, newBtnY + 8, 0xFFFFFFFF, true);
        }

        int statusBarY = py + 46;
        if (tab != 1) {
            Glass.fillRound(g, cx + 8, statusBarY, cw - 16, 22, 5, Palette.tint(Palette.PANEL2, 0x88));
            g.text(this.font, trunc(status, cw - 32), cx + 16, statusBarY + 7, authed ? Palette.GREEN : Palette.MUTED, false);
        }

        int tabItemH = 34;
        g.enableScissor(px + 8, sidebarTop, px + SW - 8, sidebarBottom);
        for (int i = 0; i < TABS.length; i++) {
            int by = sidebarTop + i * (tabItemH + 4);
            boolean sel = i == tab && !inThread;
            boolean hov = mx >= px + 10 && mx <= px + SW - 10 && my >= by && my <= by + tabItemH;
            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, px + 10, by, SW - 20, tabItemH, hov ? 1.0f : 0.0f, sel, false);
                int ty = by + (tabItemH - this.font.lineHeight) / 2;
                g.text(this.font, TABS[i], px + 22, ty, hov ? 0xFFFFFFA0 : (sel ? 0xFFFFFFFF : Palette.TEXT), true);
            } else {
                int fill = sel ? Palette.ACCENT : (hov ? Palette.PANEL : Palette.PANEL2);
                Glass.fillRound(g, px + 10, by, SW - 20, tabItemH, 7, fill);
                if (sel) {
                    Glass.ring(g, px + 10, by, SW - 20, tabItemH, 7, 1.2f, 0xFFFFFFFF);
                }
                int ty = by + (tabItemH - this.font.lineHeight) / 2;
                g.text(this.font, TABS[i], px + 22, ty, sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED), false);
            }
        }
        g.disableScissor();

        if (showNewPostModal && tab == 1) {
            int modalY = py + 80;
            int modalW = cw - 32;
            UiStyleManager.drawCard(g, cx + 16, modalY, modalW, 76, 8, Palette.PANEL, 0.0f, null);
            g.text(this.font, "📸 Was gibt's Neues?", cx + 24, modalY + 12, Palette.TEXT, true);
        }

        int listTop = py + 74 + (showNewPostModal && tab == 1 ? 84 : 0);
        int listBottom = py + ph - 48;
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        entryRects.clear();
        int ex = cx + 8;
        int ew = cw - 16;
        int ey = listTop + 2 - scroll;
        for (Entry e : entries) {
            int itemH = e.isMcReal ? 72 : ROW_H;
            boolean vis = ey + itemH > listTop && ey < listBottom;
            if (vis) {
                boolean hov = mx >= ex && mx <= ex + ew && my >= ey && my <= ey + itemH;
                float h = hov ? 1.0f : 0.0f;
                UiStyleManager.drawCard(g, ex, ey, ew, itemH, 8, Palette.PANEL, h, null);

                if (e.isMcReal && e.mcRealPost != null) {
                    McRealManager.McRealPost p = e.mcRealPost;
                    int avatarX = ex + 10;
                    int avatarY = ey + 10;
                    SkinFaceRenderer.drawFace(g, p.authorName, null, avatarX, avatarY, 32);
                    Glass.ring(g, avatarX, avatarY, 32, 32, 6, 1.0f, Palette.ACCENT);

                    int headerTextX = avatarX + 40;
                    g.text(this.font, p.authorName, headerTextX, ey + 10, Palette.TEXT, true);

                    int nameW = this.font.width(p.authorName);
                    int badgeX = headerTextX + nameW + 8;
                    int srvW = this.font.width(p.serverTag) + 10;
                    Glass.badge(g, badgeX, ey + 9, srvW, 14, 4, Palette.PANEL2, Palette.ACCENT, this.font, p.serverTag);

                    String timeStr = p.getTimeAgo();
                    int timeW = this.font.width(timeStr);
                    g.text(this.font, timeStr, ex + ew - timeW - 14, ey + 10, Palette.MUTED, false);

                    g.text(this.font, trunc(p.caption, ew - 180), headerTextX, ey + 28, Palette.TEXT, false);

                    int likeBtnW = 60;
                    int likeBtnH = 22;
                    int likeBtnX = ex + ew - likeBtnW - 12;
                    int likeBtnY = ey + 42;
                    boolean hovLike = mx >= likeBtnX && mx <= likeBtnX + likeBtnW && my >= likeBtnY && my <= likeBtnY + likeBtnH;
                    int likeBg = p.likedByMe ? 0x44EF4444 : (hovLike ? Palette.PANEL2 : Palette.BG);
                    Glass.fillRound(g, likeBtnX, likeBtnY, likeBtnW, likeBtnH, 5, likeBg);
                    Glass.ring(g, likeBtnX, likeBtnY, likeBtnW, likeBtnH, 5, 0.8f, p.likedByMe ? 0xFFEF4444 : 0x33FFFFFF);
                    String heartStr = (p.likedByMe ? "❤️ " : "🤍 ") + p.likes;
                    Glass.textC(g, this.font, heartStr, likeBtnX + likeBtnW / 2, likeBtnY + 7, p.likedByMe ? 0xFFEF4444 : Palette.TEXT, false);

                    entryRects.add(new Rect(likeBtnX, likeBtnY, likeBtnW, likeBtnH, () -> {
                        McRealManager.toggleLike(p.id);
                        rebuildEntries();
                    }));

                    int commW = this.font.width("💬 " + p.comments.size());
                    g.text(this.font, "💬 " + p.comments.size() + " Kommentare", headerTextX, ey + 48, Palette.MUTED, false);

                } else {
                    int textStartX = ex + 14;
                    if (e.playerHead != null) {
                        SkinFaceRenderer.drawFace(g, e.playerHead, null, ex + 8, ey + 9, 32);
                        Glass.ring(g, ex + 8, ey + 9, 32, 32, 6, 0.8f, 0x44FFFFFF);

                        int dotCol = e.online ? Palette.GREEN : Palette.MUTED;
                        Glass.disc(g, ex + 37, ey + 37, 4.5f, Palette.BG);
                        Glass.disc(g, ex + 37, ey + 37, 3.5f, dotCol);
                        if (e.online) {
                            Glass.ring(g, ex + 37 - 4, ey + 37 - 4, 8, 8, 4, 0.6f, Palette.GREEN);
                        }
                        textStartX = ex + 48;
                    }

                    g.text(this.font, e.title, textStartX, ey + 11, e.color, true);
                    if (e.sub != null && !e.sub.isEmpty()) {
                        g.text(this.font, trunc(e.sub, ew - 240), textStartX, ey + 27, Palette.MUTED, false);
                    }

                    if (e.isFriendCard) {
                        int btnH = 24;
                        int curBx = ex + ew - 8;

                        if (e.chatAct != null) {
                            int cwBtn = 52;
                            curBx -= cwBtn;
                            int cby = ey + (ROW_H - btnH) / 2;
                            boolean cHov = mx >= curBx && mx <= curBx + cwBtn && my >= cby && my <= cby + btnH;
                            Glass.fillRound(g, curBx, cby, cwBtn, btnH, 6, cHov ? Palette.ACCENT : Palette.PANEL2);
                            int tw = this.font.width("💬 Chat");
                            g.text(this.font, "💬 Chat", curBx + (cwBtn - tw) / 2, cby + 7, cHov ? 0xFFFFFFFF : Palette.TEXT, false);
                            entryRects.add(new Rect(curBx, cby, cwBtn, btnH, e.chatAct));
                            curBx -= 6;
                        }

                        if (e.partyAct != null) {
                            int pwBtn = 62;
                            curBx -= pwBtn;
                            int pby = ey + (ROW_H - btnH) / 2;
                            boolean pHov = mx >= curBx && mx <= curBx + pwBtn && my >= pby && my <= pby + btnH;
                            Glass.fillRound(g, curBx, pby, pwBtn, btnH, 6, pHov ? Palette.ACCENT2 : Palette.PANEL2);
                            int tw = this.font.width("🎉 Party");
                            g.text(this.font, "🎉 Party", curBx + (pwBtn - tw) / 2, pby + 7, pHov ? 0xFFFFFFFF : Palette.TEXT, false);
                            entryRects.add(new Rect(curBx, pby, pwBtn, btnH, e.partyAct));
                            curBx -= 6;
                        }

                        if (e.joinAct != null) {
                            int jw = 82;
                            curBx -= jw;
                            int jby = ey + (ROW_H - btnH) / 2;
                            boolean jHov = mx >= curBx && mx <= curBx + jw && my >= jby && my <= jby + btnH;
                            Glass.fillRound(g, curBx, jby, jw, btnH, 6, jHov ? Palette.GREEN : Palette.ACCENT);
                            if (jHov) Glass.ring(g, curBx, jby, jw, btnH, 6, 1.0f, 0xFFFFFFFF);
                            int tw = this.font.width("🚀 Beitreten");
                            g.text(this.font, "🚀 Beitreten", curBx + (jw - tw) / 2, jby + 7, 0xFFFFFFFF, true);
                            entryRects.add(new Rect(curBx, jby, jw, btnH, e.joinAct));
                        }
                    } else if (e.act != null) {
                        entryRects.add(new Rect(ex, ey, ew, ROW_H, e.act));
                    }
                }
            }
            ey += itemH + GAP;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(32, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Glass.scrollbarTrack(g, cx + cw - 4, listTop, 4, trackH, 2);
            Glass.scrollbarThumb(g, cx + cw - 4, thumbY, 4, thumbH, 2, false);
        }

        if (inThread) pollGroupIfNeeded();

        super.extractRenderState(g, mx, my, pt);
    }

    private void rebuildEntries() {
        entries.clear();
        if (inThread) {
            loadThread();
            return;
        }
        switch (tab) {
            case 0 -> rebuildFriends();
            case 1 -> rebuildMcReal();
            case 2 -> rebuildParty();
            case 3 -> rebuildGames();
            case 4 -> rebuildRequests();
            case 5 -> rebuildGroups();
            default -> rebuildConvs();
        }
        computeScroll();
    }

    private void rebuildMcReal() {
        List<McRealManager.McRealPost> posts = McRealManager.getPosts();
        if (posts.isEmpty()) {
            entries.add(new Entry("Keine Beiträge vorhanden", "Erstelle oben den ersten McReal-Post!", Palette.MUTED, null));
        } else {
            for (McRealManager.McRealPost p : posts) {
                entries.add(new Entry(p.authorName, p.caption, Palette.TEXT, null, p.authorName, true, false, null, null, null, null, true, p));
            }
        }
    }

    private void rebuildGames() {
        entries.add(new Entry("🔴 Vier Gewinnt (Connect 4)", "Klassisches 4-in-einer-Reihe Duell gegen Freunde", Palette.ACCENT, () ->
                Minecraft.getInstance().setScreenAndShow(new Connect4Screen(this))));
        entries.add(new Entry("❌ Tic-Tac-Toe", "Schnelles 3x3 Duell gegen Freunde oder KI", Palette.TEXT, () ->
                Minecraft.getInstance().setScreenAndShow(new TicTacToeScreen(this))));
        entries.add(new Entry("♟ Schach (Chess)", "Strategisches Schachbrett-Spiel", Palette.BLUE, () ->
                Minecraft.getInstance().setScreenAndShow(new ChessScreen(this))));
        entries.add(new Entry("🎲 Mensch ärgere dich nicht", "Brettspiel-Klassiker für 2-4 Spieler", Palette.ACCENT, () ->
                Minecraft.getInstance().setScreenAndShow(new MenschAergereDichNichtScreen(this))));
        entries.add(new Entry("🐍 Snake", "Klassisches Schlangen-Minispiel", Palette.GREEN, () ->
                Minecraft.getInstance().setScreenAndShow(new SnakeScreen(this))));
        entries.add(new Entry("🧱 Tetris", "Blöcke stapeln & Highscore knacken", Palette.ACCENT, () ->
                Minecraft.getInstance().setScreenAndShow(new TetrisScreen(this))));
    }

    private void rebuildParty() {
        if (!PartyManager.inParty()) {
            entries.add(new Entry("Keine aktive Party", "Erstelle eine Party und lade Freunde ein, um gemeinsam auf Server zu springen!", Palette.MUTED, null));
            entries.add(new Entry("🎉 Neue Party erstellen", "Klicke hier, um eine eigene Party zu gründen", Palette.ACCENT, () -> {
                PartyManager.createParty();
                rebuildEntries();
            }));

            List<String> invites = PartyManager.getPendingInvites();
            if (!invites.isEmpty()) {
                entries.add(new Entry("Offene Party-Einladungen:", "", Palette.ACCENT2, null));
                for (String inv : invites) {
                    entries.add(new Entry("✉ Einladung von: " + inv, "Klicke zum Beitreten", Palette.GREEN, () -> {
                        PartyManager.acceptInvite(inv);
                        rebuildEntries();
                    }));
                }
            }
        } else {
            String lead = PartyManager.getLeader();
            boolean isLead = PartyManager.isLeader();
            entries.add(new Entry("⭐ Party-Anführer: " + lead, isLead ? "Du bist der Party-Anführer" : "Mitglied", Palette.ACCENT, null));

            for (String m : PartyManager.getMembers()) {
                boolean isL = m.equalsIgnoreCase(lead);
                entries.add(new Entry((isL ? "⭐ " : "👤 ") + m, isL ? "Anführer" : "Mitglied", Palette.TEXT, null, m, true, false, null, null, null, null));
            }

            entries.add(new Entry("🚪 Party verlassen", "Klicke hier, um die Party zu verlassen", Palette.DANGER, () -> {
                PartyManager.leaveParty();
                rebuildEntries();
            }));
        }
    }

    private void rebuildFriends() {
        JsonArray list = arr(state.get("friends"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject f = list.get(i).getAsJsonObject();
            String name = str(f, "name");
            if (name.isEmpty()) name = str(f, "mc_name");
            if (name.isEmpty()) name = "User " + str(f, "id");
            boolean online = f.has("online") && f.get("online").getAsBoolean();
            String server = f.has("server") && !f.get("server").isJsonNull() ? f.get("server").getAsString().trim() : "";
            String otherId = str(f, "discordId");
            boolean hasJoinableServer = online && !server.isEmpty() && !"hidden".equalsIgnoreCase(server);

            String friendName = name;
            String friendServer = server;
            String otherDid = otherId;

            String sub = online
                    ? (hasJoinableServer ? "🚀 Spielt auf: " + server : "● Online")
                    : "○ Offline";

            int color = friendColor(f, online ? Palette.TEXT : Palette.MUTED);

            Runnable joinAction = hasJoinableServer ? () -> ServerSwitcherScreen.switchServer(mc(), friendServer, friendName + "'s Server") : null;
            Runnable partyAction = () -> PartyManager.invite(friendName);
            Runnable chatAction = () -> openDm(otherDid, friendName);

            entries.add(new Entry(friendName, sub, color, () -> {
                if (hasJoinableServer && !dev.kollegen.client.KollegenMod.isShiftDown()) {
                    if (joinAction != null) joinAction.run();
                } else {
                    chatAction.run();
                }
            }, friendName, online, true, partyAction, chatAction, joinAction, hasJoinableServer ? friendServer : null));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine Freunde", "Füge unten einen Freundes-Code hinzu", Palette.MUTED, null));
        }
    }

    private static int friendColor(JsonObject f, int fallback) {
        String hex = str(f, "name_color");
        if (hex.startsWith("#") && hex.length() >= 7) {
            try {
                return 0xFF000000 | Integer.parseInt(hex.substring(1, 7), 16);
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private void rebuildRequests() {
        JsonArray list = arr(state.get("requests"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject r = list.get(i).getAsJsonObject();
            String id = str(r, "fromId");
            String name = str(r, "fromName");
            if (name.isEmpty()) name = "User " + id;
            entries.add(new Entry("✉ " + name, "Klicke zum Annehmen", Palette.GREEN, () -> apiAccept(id), name, false, false, null, null, null, null));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine Anfragen", "Aktuell liegen keine Freundschaftsanfragen vor", Palette.MUTED, null));
        }
    }

    private void rebuildGroups() {
        JsonArray list = arr(state.get("groups"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject g = list.get(i).getAsJsonObject();
            String id = str(g, "id");
            String name = str(g, "name");
            int mc = arr(g.get("members")).size();
            entries.add(new Entry("⛺ " + name, mc + " Mitglieder", Palette.TEXT, () -> openGroup(id, name)));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine Gruppen", "Erstelle unten eine neue Gruppe", Palette.MUTED, null));
        }
    }

    private void rebuildConvs() {
        JsonArray list = arr(state.get("convs"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject c = list.get(i).getAsJsonObject();
            String otherId = str(c, "otherId");
            String otherName = str(c, "otherName");
            if (otherName.isEmpty()) otherName = "User " + otherId;
            String last = str(c, "lastMessage");
            String fName = otherName;
            entries.add(new Entry("💬 " + otherName, last.isEmpty() ? "Keine Nachrichten" : last, Palette.TEXT, () -> openDm(otherId, fName), otherName, false, false, null, null, null, null));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine Chats", "Starte einen Chat über die Freundesliste", Palette.MUTED, null));
        }
    }

    private void computeScroll() {
        int need = 0;
        for (Entry e : entries) {
            need += (e.isMcReal ? 72 : ROW_H) + GAP;
        }
        int visible = contentBottom - (contentTop + (showNewPostModal && tab == 1 ? 84 : 0));
        maxScroll = Math.max(0, need - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    private void openDm(String otherId, String title) {
        if (otherId.isEmpty()) return;
        inThread = true;
        threadType = "dm";
        threadId = otherId;
        threadTitle = "💬 " + title;
        scroll = 0;
        buildWidgets();
        rebuildEntries();
    }

    private void openGroup(String groupId, String title) {
        inThread = true;
        threadType = "group";
        threadId = groupId;
        threadTitle = "⛺ " + title;
        groupSinceMsg = 0;
        scroll = 0;
        buildWidgets();
        rebuildEntries();
    }

    private void loadThread() {
        thread(() -> {
            List<String[]> lines = new ArrayList<>();
            if (threadType.equals("group")) {
                JsonObject v = getObj("/group/view?groupId=" + enc(threadId));
                if (v != null) {
                    int mcount = arr(v.get("members")).size();
                    lines.add(new String[]{ "⛺ " + threadTitle, (mcount == 0 ? "" : mcount + " Mitglieder") });
                }
            }
            if (threadType.equals("group")) {
                JsonObject poll = getObj("/group/poll?groupId=" + enc(threadId) + "&sinceMsg=0&sinceSig=0");
                if (poll != null && poll.has("messages")) {
                    buildLines(lines, poll.get("messages").getAsJsonArray());
                }
            } else {
                JsonArray msgs = getArr("/dm/messages?otherId=" + enc(threadId) + "&limit=40");
                if (msgs != null) {
                    buildLines(lines, msgs);
                }
            }
            mc().execute(() -> {
                entries.clear();
                for (String[] l : lines) {
                    entries.add(new Entry(l[0], l[1], Palette.TEXT, null));
                }
                computeScroll();
            });
        });
    }

    private void buildLines(List<String[]> out, JsonArray msgs) {
        for (JsonElement e : msgs) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String from = str(o, "fromName");
            if (from.isEmpty()) from = str(o, "from");
            String text = str(o, "text");
            out.add(new String[]{ from, text });
        }
    }

    private void sendThread() {
        if (chatInp == null) return;
        String t = chatInp.getValue().trim();
        if (t.isEmpty()) return;
        chatInp.setValue("");
        JsonObject body = new JsonObject();
        body.addProperty("text", t);
        if (threadType.equals("group")) body.addProperty("groupId", threadId);
        else body.addProperty("otherId", threadId);

        String path = threadType.equals("group") ? "/group/send" : "/dm/send";
        thread(() -> {
            post(path, body);
            loadThread();
        });
    }

    private void pollGroupIfNeeded() {
        if (!threadType.equals("group") || pollBusy) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < 1500) return;
        lastPoll = now;
        pollBusy = true;
        thread(() -> {
            try {
                JsonObject r = getObj("/group/poll?groupId=" + enc(threadId) + "&sinceMsg=" + groupSinceMsg + "&sinceSig=0");
                mc().execute(() -> {
                    if (r != null && r.has("messages")) {
                        List<String[]> adds = new ArrayList<>();
                        buildLines(adds, r.get("messages").getAsJsonArray());
                        for (JsonElement e2 : r.get("messages").getAsJsonArray()) {
                            if (e2.isJsonObject() && e2.getAsJsonObject().has("ts")) {
                                groupSinceMsg = Math.max(groupSinceMsg, e2.getAsJsonObject().get("ts").getAsLong());
                            }
                        }
                        for (String[] l : adds) entries.add(new Entry(l[0], l[1], Palette.TEXT, null));
                        computeScroll();
                    }
                });
            } finally {
                pollBusy = false;
            }
        });
    }

    private void apiAddFriend() {
        if (codeInp == null) return;
        String code = codeInp.getValue().trim().toUpperCase();
        if (code.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        thread(() -> {
            JsonObject r = post("/friends", body);
            mc().execute(() -> {
                status = r != null && !r.has("error") ? "Anfrage gesendet ✓" : "Anfrage fehlgeschlagen";
                loadAll();
            });
        });
    }

    private void apiCreateGroup() {
        if (groupInp == null) return;
        String name = groupInp.getValue().trim();
        if (name.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.add("memberIds", new JsonArray());
        thread(() -> {
            JsonObject r = post("/group/create", body);
            mc().execute(() -> {
                status = r != null && !r.has("error") ? "Gruppe erstellt ✓" : "Erstellen fehlgeschlagen";
                loadAll();
            });
        });
    }

    private void apiAccept(String fromId) {
        JsonObject body = new JsonObject();
        body.addProperty("from", fromId);
        thread(() -> {
            post("/friends/accept", body);
            loadAll();
        });
    }

    private void loadClient() {
        thread(() -> {
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                if (Files.exists(p)) {
                    JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                    backend = str(o, "backend");
                    session = str(o, "session");
                }
            } catch (Throwable ignored) {
            }
            if (backend.isEmpty()) backend = "https://kollegen.app";
            authed = !session.isEmpty();
            mc().execute(this::loadAll);
        });
    }

    private void loadAll() {
        if (!authed) {
            status = "Nicht eingeloggt (bitte im Launcher anmelden)";
            rebuildEntries();
            return;
        }
        thread(() -> {
            JsonObject s = new JsonObject();
            JsonArray friends = getArr("/friends");
            JsonArray reqs = getArr("/friends/requests");
            JsonArray groups = getArr("/groups");
            JsonArray convs = getArr("/dm/conversations");
            s.add("friends", friends != null ? friends : new JsonArray());
            s.add("requests", reqs != null ? reqs : new JsonArray());
            s.add("groups", groups != null ? groups : new JsonArray());
            s.add("convs", convs != null ? convs : new JsonArray());
            mc().execute(() -> {
                state = s;
                status = "Aktualisiert ✓";
                rebuildEntries();
            });
        });
    }

    private JsonObject getObj(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(backend + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + session)
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) return JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private JsonArray getArr(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(backend + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + session)
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonElement el = JsonParser.parseString(res.body());
                if (el.isJsonArray()) return el.getAsJsonArray();
                if (el.isJsonObject() && el.getAsJsonObject().has("data") && el.getAsJsonObject().get("data").isJsonArray()) {
                    return el.getAsJsonObject().getAsJsonArray("data");
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private JsonObject post(String path, JsonObject body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(backend + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + session)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return JsonParser.parseString(res.body()).getAsJsonObject();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : "";
    }

    private static JsonArray arr(JsonElement el) {
        return (el != null && el.isJsonArray()) ? el.getAsJsonArray() : new JsonArray();
    }

    private static String trunc(String s, int maxW) {
        if (s == null) return "";
        return Glass.trunc(Minecraft.getInstance().font, s, maxW);
    }

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    private void thread(Runnable r) {
        Thread t = new Thread(r, "kollegen-social");
        t.setDaemon(true);
        t.start();
    }
}
