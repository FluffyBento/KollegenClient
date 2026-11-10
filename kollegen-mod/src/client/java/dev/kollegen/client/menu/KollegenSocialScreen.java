package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.party.PartyManager;
import dev.kollegen.client.presence.PrivacySettings;
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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import dev.kollegen.client.friend.FriendManager;

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
import java.util.UUID;

public class KollegenSocialScreen extends Screen {

    private final Screen parent;
    private static final int SW = 180;
    private static final int ROW_H = 48;
    private static final int GAP = 6;
    private static final String[] TABS = { "👥 Freunde", "🎉 Party", "🎮 Spiele", "📩 Anfragen", "🗂 Gruppen", "💬 Chats" };

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    private String backend = "";
    private String session = "";
    private String myCode = "";
    private String myName = "";
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

    private String selectedInviteGameId = null;
    private String selectedInviteGameTitle = null;

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
        final UUID playerUuid;
        final boolean online;
        final boolean isFriendCard;
        final Runnable partyAct;
        final Runnable chatAct;
        final Runnable joinAct;
        final String joinServer;
        final Runnable removeAct;
        final Runnable inviteAct;
        final Runnable addFriendAct;
        final Runnable playAct;

        Entry(String title, String sub, int color, Runnable act) {
            this(title, sub, color, act, null, null, false, false, null, null, null, null, null, null, null, null);
        }

        Entry(String title, String sub, int color, Runnable act, String playerHead, UUID playerUuid, boolean online, boolean isFriendCard,
              Runnable partyAct, Runnable chatAct, Runnable joinAct, String joinServer, Runnable removeAct) {
            this(title, sub, color, act, playerHead, playerUuid, online, isFriendCard, partyAct, chatAct, joinAct, joinServer, removeAct, null, null, null);
        }

        Entry(String title, String sub, int color, Runnable act, String playerHead, UUID playerUuid, boolean online, boolean isFriendCard,
              Runnable partyAct, Runnable chatAct, Runnable joinAct, String joinServer, Runnable removeAct,
              Runnable inviteAct, Runnable addFriendAct, Runnable playAct) {
            this.title = title;
            this.sub = sub;
            this.color = color;
            this.act = act;
            this.playerHead = playerHead;
            this.playerUuid = playerUuid;
            this.online = online;
            this.isFriendCard = isFriendCard;
            this.partyAct = partyAct;
            this.chatAct = chatAct;
            this.joinAct = joinAct;
            this.joinServer = joinServer;
            this.removeAct = removeAct;
            this.inviteAct = inviteAct;
            this.addFriendAct = addFriendAct;
            this.playAct = playAct;
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
        this(parent, 0);
    }

    public KollegenSocialScreen(Screen parent, int initialTab) {
        super(Component.literal("Kollegen Client – Soziales"));
        this.parent = parent;
        this.tab = Math.max(0, Math.min(TABS.length - 1, initialTab));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 32, 940);
        int h = Math.min(this.height - 32, 620);
        int y = (this.height - h) / 2;
        if (y < 12) y = 12;
        px = (this.width - w) / 2;
        py = y;
        pw = w;
        ph = h;

        cx = px + SW + 8;
        cw = pw - SW - 16;
        contentTop = py + 74;
        contentBottom = py + ph - 52;
        sidebarTop = py + 56;
        sidebarBottom = py + ph - 16;

        buildWidgets();
        loadClient();
    }

    private void buildWidgets() {
        clearWidgets();

        int topY = py + 14;

        boolean hidden = PrivacySettings.isHideServer();
        String privText = hidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich";
        GlassButton privBtn = new GlassButton(px + pw - 414, topY, 140, 24, Component.literal(privText), btn -> {
            PrivacySettings.toggleHideServer();
            boolean nowHidden = PrivacySettings.isHideServer();
            btn.setMessage(Component.literal(nowHidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich"));
            status = nowHidden ? "Server für Freunde verborgen." : "Server für Freunde sichtbar.";
        });
        addRenderableWidget(privBtn);

        GlassButton feedBtn = new GlassButton(px + pw - 268, topY, 70, 24, Component.literal("📸 Feed"), btn ->
                Minecraft.getInstance().setScreenAndShow(new dev.kollegen.client.menu.KollegenFeedScreen(this)));
        addRenderableWidget(feedBtn);

        GlassButton switcherBtn = new GlassButton(px + pw - 192, topY, 120, 24, Component.literal("🌐 Server wechseln"), btn ->
                Minecraft.getInstance().setScreenAndShow(new ServerSwitcherScreen(this)));
        addRenderableWidget(switcherBtn);

        refreshBtn = new GlassButton(px + pw - 66, topY, 26, 24, Component.literal("⟳"), btn -> {
            status = "Aktualisiere…";
            loadAll();
        });
        addRenderableWidget(refreshBtn);

        backBtn = new GlassButton(px + pw - 34, topY, 26, 24, Component.literal("✕"), btn -> {
            if (inThread) {
                inThread = false;
                rebuildEntries();
            } else if (selectedInviteGameId != null) {
                selectedInviteGameId = null;
                selectedInviteGameTitle = null;
                rebuildEntries();
            } else {
                Minecraft.getInstance().setScreenAndShow(parent);
            }
        });
        addRenderableWidget(backBtn);

        if (tab == 0 && !inThread) {
            int searchY = py + 46;
            int codeBtnW = 180;
            int searchW = cw - codeBtnW - 20;
            friendSearchInput = new EditBox(this.font, cx + 8, searchY, searchW, 24, Component.literal("Freunde durchsuchen"));
            friendSearchInput.setMaxLength(32);
            friendSearchInput.setHint(Component.literal("🔍 Freunde durchsuchen…"));
            friendSearchInput.setResponder(s -> rebuildEntries());
            addRenderableWidget(friendSearchInput);

            int inpY = py + ph - 42;
            int btnW = 100;
            codeInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 24, Component.literal("Freundes-Code"));
            codeInp.setMaxLength(12);
            codeInp.setHint(Component.literal("Freundes-Code eingeben (z. B. #ABC123)…"));
            addRenderableWidget(codeInp);

            addRenderableWidget(new GlassButton(cx + cw - btnW - 6, inpY, btnW, 24, Component.literal("+ Hinzufügen"), btn -> apiAddFriend()));
        } else if (tab == 4 && !inThread) {
            friendSearchInput = null;
            int inpY = py + ph - 42;
            int btnW = 100;
            groupInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 24, Component.literal("Gruppenname"));
            groupInp.setMaxLength(30);
            groupInp.setHint(Component.literal("Name der neuen Gruppe…"));
            addRenderableWidget(groupInp);

            addRenderableWidget(new GlassButton(cx + cw - btnW - 6, inpY, btnW, 24, Component.literal("Erstellen"), btn -> apiCreateGroup()));
        } else if (inThread) {
            friendSearchInput = null;
            int inpY = py + ph - 42;
            int btnW = 80;
            chatInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 24, Component.literal("Nachricht"));
            chatInp.setMaxLength(300);
            chatInp.setHint(Component.literal("Nachricht eingeben…"));
            addRenderableWidget(chatInp);

            addRenderableWidget(new GlassButton(cx + cw - btnW - 6, inpY, btnW, 24, Component.literal("Senden"), btn -> sendThread()));
        } else {
            friendSearchInput = null;
            codeInp = null;
            groupInp = null;
            chatInp = null;
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
                if (tab != i || inThread || selectedInviteGameId != null) {
                    tab = i;
                    inThread = false;
                    selectedInviteGameId = null;
                    selectedInviteGameTitle = null;
                    scroll = 0;
                    buildWidgets();
                    rebuildEntries();
                    return true;
                }
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
        int listTop = py + 74;
        if (maxScroll > 0 && mx >= cx && mx <= cx + cw && my >= listTop && my <= contentBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 26)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            if (inThread) {
                inThread = false;
                rebuildEntries();
                return true;
            }
            if (selectedInviteGameId != null) {
                selectedInviteGameId = null;
                selectedInviteGameTitle = null;
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

        if (status != null && !status.isEmpty()) {
            int statX = cx + 130;
            g.text(this.font, trunc(status, cw - 260), statX, py + 21, authed ? Palette.GREEN : Palette.MUTED, false);
        }

        entryRects.clear();

        if (tab == 0 && !inThread) {
            int codeBtnW = 180;
            int codeBtnX = cx + cw - codeBtnW - 8;
            int codeBtnY = py + 46;
            boolean hovCode = mx >= codeBtnX && mx <= codeBtnX + codeBtnW && my >= codeBtnY && my <= codeBtnY + 24;
            Glass.fillRound(g, codeBtnX, codeBtnY, codeBtnW, 24, 6, hovCode ? Palette.PANEL : Palette.PANEL2);
            Glass.ring(g, codeBtnX, codeBtnY, codeBtnW, 24, 6, 1.0f, hovCode ? Palette.ACCENT : 0x33FFFFFF);
            String displayCode = myCode.isEmpty() ? "---" : ("#" + myCode);
            Glass.textC(g, this.font, "Dein Code: " + displayCode + " 📋", codeBtnX + codeBtnW / 2, codeBtnY + 8, hovCode ? Palette.ACCENT : Palette.TEXT, false);

            entryRects.add(new Rect(codeBtnX, codeBtnY, codeBtnW, 24, () -> {
                if (!myCode.isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(myCode);
                    status = "✓ Dein Code #" + myCode + " in Zwischenablage kopiert!";
                }
            }));
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
                g.text(this.font, TABS[i], px + 18, ty, hov ? 0xFFFFFFA0 : (sel ? 0xFFFFFFFF : Palette.TEXT), true);
            } else {
                int fill = sel ? Palette.ACCENT : (hov ? Palette.PANEL : Palette.PANEL2);
                Glass.fillRound(g, px + 10, by, SW - 20, tabItemH, 7, fill);
                if (sel) {
                    Glass.ring(g, px + 10, by, SW - 20, tabItemH, 7, 1.2f, 0xFFFFFFFF);
                }
                int ty = by + (tabItemH - this.font.lineHeight) / 2;
                g.text(this.font, TABS[i], px + 18, ty, sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED), false);
            }
        }
        g.disableScissor();

        int listTop = py + 76;
        int listBottom = tab == 0 || tab == 4 || inThread ? (py + ph - 50) : (py + ph - 16);
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        int ex = cx + 8;
        int ew = cw - 16;
        int ey = listTop + 2 - scroll;
        for (Entry e : entries) {
            int itemH = ROW_H;
            boolean vis = ey + itemH > listTop && ey < listBottom;
            if (vis) {
                boolean hov = mx >= ex && mx <= ex + ew && my >= ey && my <= ey + itemH;
                float h = hov ? 1.0f : 0.0f;
                UiStyleManager.drawCard(g, ex, ey, ew, itemH, 8, Palette.PANEL, h, null);
                if (hov) {
                    Glass.ring(g, ex, ey, ew, itemH, 8, 1.0f, Palette.tint(Palette.ACCENT, 0x88));
                }

                int textStartX = ex + 14;
                if (e.playerHead != null) {
                    SkinFaceRenderer.drawFace(g, e.playerHead, e.playerUuid, ex + 8, ey + 8, 32);
                    Glass.ring(g, ex + 8, ey + 8, 32, 32, 6, 0.8f, 0x44FFFFFF);

                    int dotCol = e.online ? Palette.GREEN : Palette.MUTED;
                    Glass.disc(g, ex + 36, ey + 36, 4.5f, Palette.BG);
                    Glass.disc(g, ex + 36, ey + 36, 3.5f, dotCol);
                    if (e.online) {
                        Glass.ring(g, ex + 36 - 4, ey + 36 - 4, 8, 8, 4, 0.6f, Palette.GREEN);
                    }
                    textStartX = ex + 48;
                }

                    int btnH = 24;
                    int curBx = ex + ew - 8;

                    if (e.removeAct != null) {
                        int rwBtn = 22;
                        curBx -= rwBtn;
                        int rby = ey + (ROW_H - btnH) / 2;
                        boolean rHov = mx >= curBx && mx <= curBx + rwBtn && my >= rby && my <= rby + btnH;
                        Glass.fillRound(g, curBx, rby, rwBtn, btnH, 5, rHov ? Palette.DANGER : Palette.PANEL2);
                        Glass.textC(g, this.font, "✕", curBx + rwBtn / 2, rby + 7, rHov ? 0xFFFFFFFF : Palette.MUTED, false);
                        entryRects.add(new Rect(curBx, rby, rwBtn, btnH, e.removeAct));
                        curBx -= 6;
                    }

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
                        curBx -= 6;
                    }

                    if (e.addFriendAct != null) {
                        int fwBtn = 74;
                        curBx -= fwBtn;
                        int fby = ey + (ROW_H - btnH) / 2;
                        boolean fHov = mx >= curBx && mx <= curBx + fwBtn && my >= fby && my <= fby + btnH;
                        Glass.fillRound(g, curBx, fby, fwBtn, btnH, 6, fHov ? Palette.ACCENT : Palette.PANEL2);
                        int tw = this.font.width("+ Freund");
                        g.text(this.font, "+ Freund", curBx + (fwBtn - tw) / 2, fby + 7, fHov ? 0xFFFFFFFF : Palette.TEXT, false);
                        entryRects.add(new Rect(curBx, fby, fwBtn, btnH, e.addFriendAct));
                        curBx -= 6;
                    }

                    if (e.inviteAct != null) {
                        int iwBtn = 80;
                        curBx -= iwBtn;
                        int iby = ey + (ROW_H - btnH) / 2;
                        boolean iHov = mx >= curBx && mx <= curBx + iwBtn && my >= iby && my <= iby + btnH;
                        Glass.fillRound(g, curBx, iby, iwBtn, btnH, 6, iHov ? Palette.ACCENT2 : Palette.PANEL2);
                        int tw = this.font.width("✉ Einladen");
                        g.text(this.font, "✉ Einladen", curBx + (iwBtn - tw) / 2, iby + 7, iHov ? 0xFFFFFFFF : Palette.TEXT, false);
                        entryRects.add(new Rect(curBx, iby, iwBtn, btnH, e.inviteAct));
                        curBx -= 6;
                    }

                    if (e.playAct != null) {
                        int pwBtn = 76;
                        curBx -= pwBtn;
                        int pby = ey + (ROW_H - btnH) / 2;
                        boolean pHov = mx >= curBx && mx <= curBx + pwBtn && my >= pby && my <= pby + btnH;
                        Glass.fillRound(g, curBx, pby, pwBtn, btnH, 6, pHov ? Palette.GREEN : Palette.PANEL2);
                        if (pHov) Glass.ring(g, curBx, pby, pwBtn, btnH, 6, 1.0f, 0xFFFFFFFF);
                        int tw = this.font.width("▶ Spielen");
                        g.text(this.font, "▶ Spielen", curBx + (pwBtn - tw) / 2, pby + 7, pHov ? 0xFFFFFFFF : Palette.TEXT, false);
                        entryRects.add(new Rect(curBx, pby, pwBtn, btnH, e.playAct));
                        curBx -= 6;
                    }

                    int availTextW = Math.max(60, curBx - textStartX - 8);
                    g.text(this.font, trunc(e.title, availTextW), textStartX, ey + 10, e.color, true);
                    if (e.sub != null && !e.sub.isEmpty()) {
                        int subCol = e.online ? (e.joinServer != null ? Palette.GREEN : Palette.ACCENT) : Palette.MUTED;
                        g.text(this.font, trunc(e.sub, availTextW), textStartX, ey + 25, subCol, false);
                    }

                    if (curBx == ex + ew - 8 && e.act != null) {
                        entryRects.add(new Rect(ex, ey, ew, ROW_H, e.act));
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

    public void rebuildEntries() {
        entries.clear();
        if (inThread) {
            loadThread();
            return;
        }
        switch (tab) {
            case 0 -> rebuildFriends();
            case 1 -> rebuildParty();
            case 2 -> rebuildGames();
            case 3 -> rebuildRequests();
            case 4 -> rebuildGroups();
            default -> rebuildConvs();
        }
        computeScroll();
    }

    private void rebuildGames() {
        if (selectedInviteGameId != null) {
            rebuildGameInviteView();
            return;
        }

        entries.add(new Entry("♟ Schach", "Klassisches Schachspiel – Alleine gegen KI oder zu zweit online", Palette.TEXT, null, null, null, true, false, null, null, null, null, null,
                () -> { selectedInviteGameId = "chess"; selectedInviteGameTitle = "Schach"; rebuildEntries(); },
                null,
                () -> mc().setScreenAndShow(new dev.kollegen.client.game.ChessScreen(this))));

        entries.add(new Entry("🔴 Vier Gewinnt", "Verbinde 4 Steine in einer Reihe – Gegen KI oder Freunde", Palette.TEXT, null, null, null, true, false, null, null, null, null, null,
                () -> { selectedInviteGameId = "c4"; selectedInviteGameTitle = "Vier Gewinnt"; rebuildEntries(); },
                null,
                () -> mc().setScreenAndShow(new dev.kollegen.client.game.Connect4Screen(this))));

        entries.add(new Entry("🎲 Mensch ärgere Dich nicht", "Brettspiel-Klassiker für bis zu 4 Spieler online", Palette.TEXT, null, null, null, true, false, null, null, null, null, null,
                () -> { selectedInviteGameId = "madt"; selectedInviteGameTitle = "Mensch ärgere Dich nicht"; rebuildEntries(); },
                null,
                () -> mc().setScreenAndShow(new dev.kollegen.client.game.MenschAergereDichNichtScreen(this))));

        entries.add(new Entry("❌ Tic-Tac-Toe", "Schnelles 3x3 Duell gegen KI oder Kollegen", Palette.TEXT, null, null, null, true, false, null, null, null, null, null,
                () -> { selectedInviteGameId = "tictactoe"; selectedInviteGameTitle = "Tic-Tac-Toe"; rebuildEntries(); },
                null,
                () -> mc().setScreenAndShow(new dev.kollegen.client.game.TicTacToeScreen(this))));

        entries.add(new Entry("🐍 Snake", "Retro Arcade Klassiker – Fange Äpfel und jage den Highscore", Palette.TEXT, null, null, null, true, false, null, null, null, null, null,
                null,
                null,
                () -> mc().setScreenAndShow(new dev.kollegen.client.game.SnakeScreen(this))));

        entries.add(new Entry("🧱 Tetris", "Retro Block-Stapler Arcade Klassiker", Palette.TEXT, null, null, null, true, false, null, null, null, null, null,
                null,
                null,
                () -> mc().setScreenAndShow(new dev.kollegen.client.menu.TetrisScreen(this))));
    }

    private void rebuildGameInviteView() {
        entries.add(new Entry("← Zurück zur Spieleauswahl", "Klicke hier, um alle Minispiele anzuzeigen", Palette.ACCENT, () -> {
            selectedInviteGameId = null;
            selectedInviteGameTitle = null;
            rebuildEntries();
        }));

        entries.add(new Entry("✉ Spieler zu " + selectedInviteGameTitle + " einladen", "Wähle einen Freund oder Spieler auf dem Server aus:", Palette.MUTED, null));

        JsonArray list = arr(state.get("friends"));
        List<String> added = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            JsonObject f = list.get(i).getAsJsonObject();
            String name = str(f, "name");
            if (name.isEmpty()) name = str(f, "mc_name");
            if (name.isEmpty()) continue;
            boolean online = f.has("online") && f.get("online").getAsBoolean();
            String uuidStr = str(f, "uuid");
            UUID friendUuid = null;
            if (!uuidStr.isEmpty()) {
                try {
                    String formatted = uuidStr.contains("-") ? uuidStr : uuidStr.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
                    friendUuid = UUID.fromString(formatted);
                } catch (Throwable ignored) {
                }
            }
            final String fName = name;
            final UUID fUuid = friendUuid;
            added.add(name.toLowerCase());
            entries.add(new Entry(name, online ? "● Freund (Online)" : "○ Freund (Offline)", online ? Palette.TEXT : Palette.MUTED, null, name, friendUuid, online, false,
                    null, null, null, null, null,
                    () -> sendGameInvite(selectedInviteGameId, selectedInviteGameTitle, fName, fUuid),
                    null, null));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            List<PlayerInfo> others = new ArrayList<>();
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                if (pi.getProfile() == null) continue;
                String pName = pi.getProfile().name();
                if (pName.equalsIgnoreCase(myName) || (mc.player != null && pName.equalsIgnoreCase(mc.player.getGameProfile().name()))) continue;
                if (added.contains(pName.toLowerCase())) continue;
                UUID pUuid = pi.getProfile().id();
                if (!dev.kollegen.client.presence.KollegenPresence.isKollegen(pUuid, pName)) continue;
                others.add(pi);
            }
            if (!others.isEmpty()) {
                entries.add(new Entry("🌐 Spieler auf diesem Server (" + others.size() + ")", "Lade Spieler direkt vom aktuellen Server ein:", Palette.ACCENT, null));
                for (PlayerInfo pi : others) {
                    String pName = pi.getProfile().name();
                    UUID pUuid = pi.getProfile().id();
                    Runnable invAct = () -> sendGameInvite(selectedInviteGameId, selectedInviteGameTitle, pName, pUuid);
                    Runnable addAct = () -> sendFriendRequestInGame(pName, pUuid);
                    entries.add(new Entry(pName, "● Auf aktuellem Server", Palette.TEXT, null, pName, pUuid, true, false,
                            null, null, null, null, null,
                            invAct, addAct, null));
                }
            }
        }
    }

    private void rebuildParty() {
        List<String> invites = PartyManager.getPendingInvites();
        for (String inviter : invites) {
            entries.add(new Entry("✉ Einladung von " + inviter, "Klicken, um der Party beizutreten", Palette.ACCENT, () -> {
                PartyManager.acceptInvite(inviter);
                rebuildEntries();
            }));
        }

        boolean inParty = PartyManager.inParty();
        if (!inParty && invites.isEmpty()) {
            entries.add(new Entry("Keine aktive Party", "Lade Freunde über die Freundesliste in deine Party ein!", Palette.MUTED, null));
            return;
        }
        if (!inParty) return;

        boolean isLeader = PartyManager.isLeader();
        String leaderName = PartyManager.getLeader();
        entries.add(new Entry("👑 Gruppenleiter: " + (leaderName == null || leaderName.isEmpty() ? "Du" : leaderName),
                "Alle Party-Mitglieder folgen dem Leiter automatisch auf neue Server.", Palette.ACCENT, null));

        List<String> members = PartyManager.getMembers();
        for (String m : members) {
            boolean isL = m.equalsIgnoreCase(leaderName);
            String role = isL ? " (Leiter)" : "";
            entries.add(new Entry(m + role, "● In deiner Party", Palette.TEXT, null, m, null, true, false, null, null, null, null, null));
        }

        if (isLeader) {
            entries.add(new Entry("❌ Party auflösen", "Klicke hier, um die Party für alle zu beenden", Palette.DANGER, () -> {
                PartyManager.leaveParty();
                rebuildEntries();
            }));
        } else {
            entries.add(new Entry("🚪 Party verlassen", "Klicke hier, um die aktuelle Party zu verlassen", Palette.DANGER, () -> {
                PartyManager.leaveParty();
                rebuildEntries();
            }));
        }
    }

    private void rebuildFriends() {
        JsonArray list = arr(state.get("friends"));
        String searchFilter = friendSearchInput != null ? friendSearchInput.getValue().trim().toLowerCase() : "";
        for (int i = 0; i < list.size(); i++) {
            JsonObject f = list.get(i).getAsJsonObject();
            String name = str(f, "name");
            if (name.isEmpty()) name = str(f, "mc_name");
            if (name.isEmpty()) name = "User " + str(f, "id");
            if (!searchFilter.isEmpty() && !name.toLowerCase().contains(searchFilter)) {
                continue;
            }
            boolean online = f.has("online") && f.get("online").getAsBoolean();
            String server = f.has("server") && !f.get("server").isJsonNull() ? f.get("server").getAsString().trim() : "";
            String otherId = str(f, "discordId");
            String friendId = str(f, "id");
            String uuidStr = str(f, "uuid");
            UUID friendUuid = null;
            if (!uuidStr.isEmpty()) {
                try {
                    String formatted = uuidStr.contains("-") ? uuidStr : uuidStr.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
                    friendUuid = UUID.fromString(formatted);
                } catch (Throwable ignored) {
                }
            }
            boolean hasJoinableServer = online && !server.isEmpty() && !"hidden".equalsIgnoreCase(server);

            String friendName = name;
            String friendServer = server;
            String otherDid = otherId;

            String sub = online
                    ? (hasJoinableServer ? "🚀 Spielt auf: " + server : "● Online (Menü)")
                    : "○ Offline";

            int color = friendColor(f, online ? Palette.TEXT : Palette.MUTED);

            Runnable joinAction = hasJoinableServer ? () -> ServerSwitcherScreen.switchServer(mc(), friendServer, friendName + "'s Server") : null;
            Runnable partyAction = () -> PartyManager.invite(friendName);
            Runnable chatAction = () -> openDm(otherDid, friendName);
            Runnable removeAction = () -> apiRemoveFriend(friendId, friendName);

            entries.add(new Entry(friendName, sub, color, () -> {
                if (hasJoinableServer && !dev.kollegen.client.KollegenMod.isShiftDown()) {
                    if (joinAction != null) joinAction.run();
                } else {
                    chatAction.run();
                }
            }, friendName, friendUuid, online, true, partyAction, chatAction, joinAction, hasJoinableServer ? friendServer : null, removeAction));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            List<PlayerInfo> others = new ArrayList<>();
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                if (pi.getProfile() == null) continue;
                String pName = pi.getProfile().name();
                if (pName.equalsIgnoreCase(myName) || (mc.player != null && pName.equalsIgnoreCase(mc.player.getGameProfile().name()))) continue;
                if (FriendManager.isFriend(pName)) continue;
                UUID pUuid = pi.getProfile().id();
                if (!dev.kollegen.client.presence.KollegenPresence.isKollegen(pUuid, pName)) continue;
                boolean alreadyListed = false;
                for (Entry ex : entries) {
                    if (pName.equalsIgnoreCase(ex.title)) {
                        alreadyListed = true;
                        break;
                    }
                }
                if (!alreadyListed) others.add(pi);
            }
            if (!others.isEmpty()) {
                entries.add(new Entry("🌐 Spieler auf diesem Server (" + others.size() + ")", "Klicke [+ Freund] für eine Freundschaftsanfrage", Palette.ACCENT, null));
                for (PlayerInfo pi : others) {
                    String pName = pi.getProfile().name();
                    UUID pUuid = pi.getProfile().id();
                    Runnable addAct = () -> sendFriendRequestInGame(pName, pUuid);
                    Runnable partyAct = () -> PartyManager.invite(pName);
                    entries.add(new Entry(pName, "● Auf aktuellem Server", Palette.TEXT, null, pName, pUuid, true, false,
                            partyAct, null, null, null, null,
                            null, addAct, null));
                }
            }
        }
        if (entries.isEmpty()) {
            if (!searchFilter.isEmpty()) {
                entries.add(new Entry("Keine Freunde gefunden", "Kein Freund stimmt mit '" + searchFilter + "' überein.", Palette.MUTED, null));
            } else {
                entries.add(new Entry("Keine Freunde vorhanden", "Füge unten einen Freundes-Code hinzu oder teile deinen Code!", Palette.MUTED, null));
            }
        }
    }

    private static int friendColor(JsonObject f, int fallback) {
        String hex = str(f, "name_color");
        if (hex.isEmpty() && f.has("equipped") && f.get("equipped").isJsonObject()) {
            JsonObject eq = f.getAsJsonObject("equipped");
            if (eq.has("name_color") && eq.get("name_color").isJsonObject()) {
                JsonObject nc = eq.getAsJsonObject("name_color");
                if (nc.has("data") && nc.get("data").isJsonObject()) {
                    hex = str(nc.getAsJsonObject("data"), "accent");
                }
            }
        }
        if (hex.isEmpty()) return fallback;
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            return 0xFF000000 | (int) Long.parseLong(hex, 16);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void rebuildRequests() {
        JsonArray list = arr(state.get("requests"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject r = list.get(i).getAsJsonObject();
            String from = str(r, "fromName");
            if (from.isEmpty()) from = str(r, "fromId");
            String fromId = str(r, "fromId");
            entries.add(new Entry("Anfrage von " + from, "Klicken zum Annehmen", Palette.ACCENT, () -> apiAccept(fromId)));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine offenen Anfragen", "Du hast alle Freundschaftsanfragen beantwortet.", Palette.MUTED, null));
        }
    }

    private void rebuildGroups() {
        JsonArray list = arr(state.get("groups"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject g = list.get(i).getAsJsonObject();
            String id = str(g, "id");
            String name = str(g, "name");
            int members = arr(g.get("memberIds")).size();
            entries.add(new Entry(name.isEmpty() ? "Gruppe #" + id : name, members + " Mitglieder · Klicken zum Öffnen", Palette.TEXT, () -> openGroup(id, name)));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine Gruppen", "Erstelle unten eine neue Gruppe!", Palette.MUTED, null));
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
            String displayTitle = otherName;
            entries.add(new Entry(otherName, last.isEmpty() ? "Keine Nachrichten" : last, Palette.TEXT, () -> openDm(otherId, displayTitle)));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine Chats", "Starte einen Chat in der Freundesliste!", Palette.MUTED, null));
        }
    }

    private void computeScroll() {
        int need = 0;
        for (Entry e : entries) {
            need += ROW_H + GAP;
        }
        int listTop = py + 76;
        int listBottom = tab == 0 || tab == 4 || inThread ? (py + ph - 50) : (py + ph - 16);
        int visible = (listBottom - 4) - listTop;
        maxScroll = Math.max(0, need - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    private void openDm(String otherId, String title) {
        if (otherId == null || otherId.isEmpty()) return;
        inThread = true;
        threadType = "dm";
        threadId = otherId;
        threadTitle = "Chat mit " + title;
        buildWidgets();
        rebuildEntries();
    }

    private void openGroup(String groupId, String title) {
        inThread = true;
        threadType = "group";
        threadId = groupId;
        threadTitle = "Gruppe: " + title;
        groupSinceMsg = 0;
        buildWidgets();
        rebuildEntries();
    }

    private void loadThread() {
        if (!authed) return;
        thread(() -> {
            List<String[]> lines = new ArrayList<>();
            if ("dm".equals(threadType)) {
                JsonArray msgs = getArr("/dm/history?otherId=" + enc(threadId));
                buildLines(lines, msgs);
            } else if ("group".equals(threadType)) {
                JsonArray msgs = getArr("/group/" + enc(threadId) + "/messages");
                buildLines(lines, msgs);
            }
            mc().execute(() -> {
                entries.clear();
                if (lines.isEmpty()) {
                    entries.add(new Entry("Noch keine Nachrichten", "Schreibe die erste Nachricht unten!", Palette.MUTED, null));
                } else {
                    for (String[] line : lines) {
                        entries.add(new Entry(line[0], line[1], Palette.TEXT, null));
                    }
                }
                computeScroll();
            });
        });
    }

    private void buildLines(List<String[]> out, JsonArray msgs) {
        if (msgs == null) return;
        for (int i = 0; i < msgs.size(); i++) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            String sender = str(m, "senderName");
            if (sender.isEmpty()) sender = str(m, "senderId");
            String text = str(m, "text");
            out.add(new String[]{ sender, text });
        }
    }

    private void sendThread() {
        if (chatInp == null) return;
        String text = chatInp.getValue().trim();
        if (text.isEmpty()) return;
        chatInp.setValue("");
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        thread(() -> {
            if ("dm".equals(threadType)) {
                body.addProperty("toId", threadId);
                post("/dm/send", body);
            } else if ("group".equals(threadType)) {
                post("/group/" + enc(threadId) + "/message", body);
            }
            mc().execute(this::rebuildEntries);
        });
    }

    private void pollGroupIfNeeded() {
        if (!"group".equals(threadType) || !authed) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < 2000 || pollBusy) return;
        pollBusy = true;
        lastPoll = now;
        thread(() -> {
            try {
                JsonArray delta = getArr("/group/" + enc(threadId) + "/poll?since=" + groupSinceMsg);
                if (delta != null && delta.size() > 0) {
                    for (int i = 0; i < delta.size(); i++) {
                        long id = delta.get(i).getAsJsonObject().get("id").getAsLong();
                        if (id > groupSinceMsg) groupSinceMsg = id;
                    }
                    mc().execute(this::rebuildEntries);
                }
            } catch (Throwable ignored) {
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

    private void apiRemoveFriend(String friendId, String friendName) {
        if (friendId == null || friendId.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("id", friendId);
        body.addProperty("friendId", friendId);
        thread(() -> {
            post("/friends/remove", body);
            mc().execute(() -> {
                status = friendName + " aus Freundesliste entfernt.";
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
                    if (o.has("me") && o.get("me").isJsonObject()) {
                        JsonObject meObj = o.getAsJsonObject("me");
                        myCode = str(meObj, "code");
                        myName = str(meObj, "name");
                    }
                    if (myCode.isEmpty() && o.has("code")) {
                        myCode = str(o, "code");
                    }
                }
            } catch (Throwable ignored) {
            }
            if (backend.isEmpty()) backend = "https://kollegen.me";
            authed = !session.isEmpty();
            mc().execute(this::loadAll);
        });
    }

    private void loadAll() {
        dev.kollegen.client.presence.KollegenPresence.refreshAsync();
        if (!authed) {
            status = "Nicht angemeldet (bitte im Launcher einloggen)";
            rebuildEntries();
            return;
        }
        thread(() -> {
            JsonObject s = new JsonObject();
            JsonArray friends = getArr("/friends");
            JsonArray reqs = getArr("/friends/requests");
            JsonArray groups = getArr("/groups");
            JsonArray convs = getArr("/dm/conversations");
            JsonObject meProfile = getObj("/me");
            s.add("friends", friends != null ? friends : new JsonArray());
            s.add("requests", reqs != null ? reqs : new JsonArray());
            s.add("groups", groups != null ? groups : new JsonArray());
            s.add("convs", convs != null ? convs : new JsonArray());
            mc().execute(() -> {
                state = s;
                dev.kollegen.client.friend.FriendManager.ensureLoaded(true);
                if (meProfile != null) {
                    if (meProfile.has("code")) myCode = str(meProfile, "code");
                    if (meProfile.has("name")) myName = str(meProfile, "name");
                }
                int count = friends != null ? friends.size() : 0;
                status = count + " Freunde synchronisiert ✓";
                buildWidgets();
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

    private void sendGameInvite(String gameId, String gameTitle, String targetName, UUID targetUuid) {
        Minecraft mc = Minecraft.getInstance();
        String my = (mc.player != null && mc.player.getGameProfile() != null) ? mc.player.getGameProfile().name() : myName;
        if (my == null || my.isEmpty()) my = "Ein Kollege";
        if (mc.getConnection() != null) {
            try {
                mc.getConnection().sendChat("[KOLLEGEN_GAME_INV:" + gameId + ":" + gameTitle + ":" + my + "]");
            } catch (Throwable ignored) {
            }
        }
        sendDirectPayload(targetName, "[KOLLEGEN_GAME_INV:" + gameId + ":" + gameTitle + ":" + my + "]");
        status = "✓ Einladung zu " + gameTitle + " an " + targetName + " gesendet!";
        Screen gameScreen = switch (gameId.toLowerCase()) {
            case "chess" -> new dev.kollegen.client.game.ChessScreen(this);
            case "c4" -> new dev.kollegen.client.game.Connect4Screen(this);
            case "madt" -> new dev.kollegen.client.game.MenschAergereDichNichtScreen(this);
            case "tictactoe" -> new dev.kollegen.client.game.TicTacToeScreen(this);
            default -> null;
        };
        if (gameScreen != null) {
            mc.setScreenAndShow(gameScreen);
        }
    }

    private void sendFriendRequestInGame(String targetName, UUID targetUuid) {
        if (targetName == null || targetName.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        String my = (mc.player != null && mc.player.getGameProfile() != null) ? mc.player.getGameProfile().name() : myName;
        if (my == null || my.isEmpty()) my = "Ein Kollege";
        FriendManager.addFriendAsync(targetName, targetUuid != null ? targetUuid.toString() : null, ok -> {
            status = ok ? "✓ Anfrage an " + targetName + " gesendet!" : "Anfrage gesendet.";
            rebuildEntries();
        });
        if (mc.getConnection() != null) {
            try {
                mc.getConnection().sendChat("[KOLLEGEN_FRIEND_REQ:" + my + "]");
            } catch (Throwable ignored) {
            }
        }
        sendDirectPayload(targetName, "[KOLLEGEN_FRIEND_REQ:" + my + "]");
    }

    private void sendDirectPayload(String recipient, String message) {
        if (backend.isEmpty() || session.isEmpty() || recipient == null || recipient.isEmpty()) return;
        thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("recipient", recipient);
                body.addProperty("content", message);
                HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/internal/dm/send"))
                        .timeout(Duration.ofSeconds(4))
                        .header("Authorization", "Bearer " + session)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        });
    }

    private void thread(Runnable r) {
        Thread t = new Thread(r, "kollegen-social");
        t.setDaemon(true);
        t.start();
    }
}
