package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.game.*;
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
    private static final int SW = 210;
    private static final int ROW_H = 46;
    private static final int GAP = 6;
    private static final String[] TABS = { "👥 Freunde", "📩 Anfragen", "🎮 Spiele", "🎉 Party", "🗂 Gruppen", "💬 Chats" };

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

        Entry(String title, String sub, int color, Runnable act) {
            this(title, sub, color, act, null, false, false, null, null, null, null);
        }

        Entry(String title, String sub, int color, Runnable act, String playerHead, boolean online, boolean isFriendCard,
              Runnable partyAct, Runnable chatAct, Runnable joinAct, String joinServer) {
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
        cx = px + SW + 14;
        cw = pw - SW - 30;
        sidebarTop = py + 58;
        sidebarBottom = py + ph - 14;
        contentTop = py + 48;
        contentBottom = py + ph - 28;
        buildWidgets();
        loadClient();
    }

    private void buildWidgets() {
        clearWidgets();
        int bh = 26;
        int by = py + 12;

        boolean hidden = PrivacySettings.isHideServer();
        String privText = hidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich";
        GlassButton privBtn = new GlassButton(px + pw - 386, by, 140, bh, Component.literal(privText), btn -> {
            PrivacySettings.toggleHideServer();
            boolean nowHidden = PrivacySettings.isHideServer();
            btn.setMessage(Component.literal(nowHidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich"));
            status = nowHidden ? "Server für Freunde verborgen ✓" : "Server für Freunde sichtbar ✓";
            rebuildEntries();
        });
        addRenderableWidget(privBtn);

        GlassButton swBtn = new GlassButton(px + pw - 236, by, 130, bh, Component.literal("🌐 Server wechseln"), btn ->
                Minecraft.getInstance().setScreenAndShow(new ServerSwitcherScreen(this)));
        addRenderableWidget(swBtn);

        backBtn = new GlassButton(px + pw - 98, by, 26, bh, Component.literal("←"), btn -> {
            inThread = false;
            threadId = "";
            groupSinceMsg = 0;
            rebuildEntries();
            buildWidgets();
        });
        backBtn.visible = inThread;

        refreshBtn = new GlassButton(px + pw - 68, by, 26, bh, Component.literal("⟳"), btn -> {
            status = "Lade…";
            loadAll();
        });

        GlassButton closeBtn = new GlassButton(px + pw - 38, by, 26, bh, Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreenAndShow(parent));

        addRenderableWidget(backBtn);
        addRenderableWidget(refreshBtn);
        addRenderableWidget(closeBtn);

        int inpY = py + ph - 38;
        int btnW = 130;
        if (inThread) {
            GlassButton send = new GlassButton(cx + cw - btnW, inpY, btnW, 26, Component.literal("Senden"), btn -> sendThread());
            addRenderableWidget(send);
            chatInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 26, Component.literal("Nachricht"));
            chatInp.setMaxLength(2000);
            chatInp.setHint(Component.literal("Nachricht eingeben…"));
            addRenderableWidget(chatInp);
            chatInp.setFocused(true);
        } else if (tab == 0 || tab == 1) {
            codeInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 26, Component.literal("Freundes-Code"));
            codeInp.setMaxLength(16);
            codeInp.setHint(Component.literal("Freundes-Code eingeben…"));
            addRenderableWidget(codeInp);
            GlassButton b = new GlassButton(cx + cw - btnW, inpY, btnW, 26, Component.literal("Hinzufügen"), btn -> apiAddFriend());
            addRenderableWidget(b);
        } else if (tab == 4) {
            groupInp = new EditBox(this.font, cx + 8, inpY, cw - btnW - 16, 26, Component.literal("Gruppenname"));
            groupInp.setMaxLength(40);
            groupInp.setHint(Component.literal("Neuer Gruppenname…"));
            addRenderableWidget(groupInp);
            GlassButton b = new GlassButton(cx + cw - btnW, inpY, btnW, 26, Component.literal("Gruppe erstellen"), btn -> apiCreateGroup());
            addRenderableWidget(b);
        }
    }

    private void computeScroll() {
        int visible = (contentBottom - 4) - (contentTop + 56);
        int need = entries.size() * (ROW_H + GAP);
        maxScroll = Math.max(0, need - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, bl);

        int tabItemH = 34;
        if (mx >= px && mx <= px + SW && my >= sidebarTop && my <= sidebarBottom) {
            int idx = (int) ((my - sidebarTop) / (tabItemH + 4));
            if (idx >= 0 && idx < TABS.length) {
                if (idx != tab) {
                    tab = idx;
                    inThread = false;
                    threadId = "";
                    groupSinceMsg = 0;
                    scroll = 0;
                    rebuildEntries();
                    buildWidgets();
                }
                return true;
            }
        }

        for (Rect r : entryRects) {
            if (r.hit(mx, my) && r.act != null) {
                r.act.run();
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (maxScroll > 0 && mx >= cx && mx <= cx + cw) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 26)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER || ki.isConfirmation()) {
            if (codeInp != null && codeInp.isFocused()) { apiAddFriend(); return true; }
            if (groupInp != null && groupInp.isFocused()) { apiCreateGroup(); return true; }
            if (chatInp != null && chatInp.isFocused()) { sendThread(); return true; }
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xDC));

        UiStyleManager.drawPanel(g, px, py, pw, ph, Palette.BG);

        int headerH = 54;
        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 10, Palette.tint(Palette.PANEL2, 0xAA));
        Glass.fillRound(g, px + 8, py + 8, SW, 3, 2, Palette.ACCENT);

        g.text(this.font, "FREUNDE & SOCIAL", px + 18, py + 22, Palette.ACCENT, true);
        g.text(this.font, inThread ? threadTitle : TABS[tab], cx + 12, py + 22, Palette.TEXT, true);

        int statusBarY = py + headerH;
        Glass.fillRound(g, cx + 8, statusBarY + 2, cw - 16, 28, 6, Palette.tint(Palette.PANEL2, 0xAA));
        g.text(this.font, trunc(status, cw - 32), cx + 14, statusBarY + 11, authed ? Palette.GREEN : Palette.MUTED, false);

        int tabItemH = 34;
        g.enableScissor(px + 8, sidebarTop, px + SW - 8, sidebarBottom);
        for (int i = 0; i < TABS.length; i++) {
            int by = sidebarTop + i * (tabItemH + 4);
            boolean sel = i == tab;
            boolean hov = mx >= px + 10 && mx <= px + SW - 10 && my >= by && my <= by + tabItemH;
            int fill = sel ? Palette.ACCENT : (hov ? Palette.PANEL : Palette.PANEL2);
            Glass.fillRound(g, px + 10, by, SW - 20, tabItemH, 7, fill);
            if (sel) {
                Glass.ring(g, px + 10, by, SW - 20, tabItemH, 7, 1.2f, 0xFFFFFFFF);
            }
            int ty = by + (tabItemH - this.font.lineHeight) / 2;
            g.text(this.font, TABS[i], px + 22, ty, sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED), false);
        }
        g.disableScissor();

        int listTop = py + headerH + 44;
        int listBottom = py + ph - 48;
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        entryRects.clear();
        int ex = cx + 8;
        int ew = cw - 16;
        int ey = listTop + 2 - scroll;
        for (Entry e : entries) {
            boolean vis = ey + ROW_H > listTop && ey < listBottom;
            if (vis) {
                boolean hov = mx >= ex && mx <= ex + ew && my >= ey && my <= ey + ROW_H;
                float h = hov ? 1.0f : 0.0f;
                UiStyleManager.drawCard(g, ex, ey, ew, ROW_H, 8, Palette.PANEL, h, null);

                int textStartX = ex + 14;
                if (e.playerHead != null) {
                    SkinFaceRenderer.drawFace(g, e.playerHead, null, ex + 8, ey + 9, 28);
                    Glass.ring(g, ex + 8, ey + 9, 28, 28, 5, 0.5f, 0x44FFFFFF);

                    int dotCol = e.online ? Palette.GREEN : Palette.MUTED;
                    Glass.disc(g, ex + 44, ey + 15, 3.5f, dotCol);
                    if (e.online) {
                        Glass.ring(g, ex + 44 - 4, ey + 15 - 4, 8, 8, 4, 0.5f, Palette.GREEN);
                    }
                    textStartX = ex + 54;
                }

                g.text(this.font, e.title, textStartX, ey + 9, e.color, true);
                if (e.sub != null && !e.sub.isEmpty()) {
                    g.text(this.font, trunc(e.sub, ew - 240), textStartX, ey + 24, Palette.MUTED, false);
                }

                if (e.isFriendCard) {
                    int btnH = 24;
                    int curBx = ex + ew - 8;

                    if (e.chatAct != null) {
                        int cwBtn = 48;
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
                        int pwBtn = 58;
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
                        int jw = 76;
                        curBx -= jw;
                        int jby = ey + (ROW_H - btnH) / 2;
                        boolean jHov = mx >= curBx && mx <= curBx + jw && my >= jby && my <= jby + btnH;
                        Glass.fillRound(g, curBx, jby, jw, btnH, 6, jHov ? Palette.GREEN : Palette.ACCENT);
                        int tw = this.font.width("🚀 Beitreten");
                        g.text(this.font, "🚀 Beitreten", curBx + (jw - tw) / 2, jby + 7, 0xFFFFFFFF, false);
                        entryRects.add(new Rect(curBx, jby, jw, btnH, e.joinAct));
                    }
                } else if (e.act != null) {
                    entryRects.add(new Rect(ex, ey, ew, ROW_H, e.act));
                }
            }
            ey += ROW_H + GAP;
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
        if (!authed) {
            entries.add(new Entry(status, "client.json aus ~/.kollegen lesen", Palette.MUTED, null));
            computeScroll();
            return;
        }
        if (inThread) {
            loadThread();
            return;
        }
        switch (tab) {
            case 0 -> rebuildFriends();
            case 1 -> rebuildRequests();
            case 2 -> rebuildGames();
            case 3 -> rebuildParty();
            case 4 -> rebuildGroups();
            default -> rebuildConvs();
        }
        computeScroll();
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
        try {
            JsonElement eq = f.get("equipped");
            if (eq == null || !eq.isJsonObject() || !eq.getAsJsonObject().has("name_color")) return fallback;
            JsonObject nc = eq.getAsJsonObject().get("name_color").getAsJsonObject();
            if (!nc.has("data")) return fallback;
            JsonObject data = nc.get("data").getAsJsonObject();
            if (!data.has("accent")) return fallback;
            String h = data.get("accent").getAsString();
            if (h.startsWith("#")) h = h.substring(1);
            if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
            if (h.length() == 6) h = "FF" + h;
            return (int) Long.parseLong(h, 16);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private void rebuildRequests() {
        JsonArray list = arr(state.get("requests"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject it = list.get(i).getAsJsonObject();
            JsonObject uq = it.has("user") && it.get("user").isJsonObject() ? it.get("user").getAsJsonObject() : null;
            JsonObject rq = it.has("request") && it.get("request").isJsonObject() ? it.get("request").getAsJsonObject() : null;
            String who = str(uq, "name");
            if (who.isEmpty()) who = str(uq, "mc_name");
            if (who.isEmpty()) who = "Unbekannt";
            String fromId = str(rq, "from");
            String code = str(uq, "code");
            String sub = code.isEmpty() ? "Anfrage offen" : "Code " + code;
            String f = fromId;
            entries.add(new Entry(who, sub, Palette.TEXT, () -> apiAccept(f)));
        }
        if (list.size() == 0) {
            entries.add(new Entry("Keine offenen Anfragen", null, Palette.MUTED, null));
        }
    }

    private void rebuildGroups() {
        JsonArray groupList = arr(state.get("groups"));
        for (int i = 0; i < groupList.size(); i++) {
            JsonObject g0 = groupList.get(i).getAsJsonObject();
            String id = str(g0, "id");
            String name = str(g0, "name");
            if (name.isEmpty()) name = "Gruppe";
            int mcount = g0.has("memberCount") ? g0.get("memberCount").getAsInt() : 0;
            String last = "";
            if (g0.has("last") && g0.get("last").isJsonObject()) {
                last = str(g0.get("last").getAsJsonObject(), "text");
            }
            String gid = id;
            String gname = name;
            entries.add(new Entry(name, (mcount == 0 ? "" : mcount + " Mitglieder \u00b7 ") + last, Palette.TEXT,
                    () -> openGroup(gid, gname)));
        }
        if (groupList.size() == 0) {
            entries.add(new Entry("Keine Gruppen", "Erstelle unten eine neue Gruppe", Palette.MUTED, null));
        }
    }

    private void rebuildConvs() {
        JsonArray convList = arr(state.get("convs"));
        for (int i = 0; i < convList.size(); i++) {
            JsonObject c = convList.get(i).getAsJsonObject();
            String otherId = str(c, "otherId");
            String name = str(c, "otherName");
            if (name.isEmpty()) name = "User " + otherId;
            String last = "";
            if (c.has("last") && c.get("last").isJsonObject()) {
                last = str(c.get("last").getAsJsonObject(), "text");
            }
            int unread = c.has("unread") ? c.get("unread").getAsInt() : 0;
            String title = (unread > 0 ? "[" + unread + "] " : "") + name;
            String oid = otherId;
            String oname = name;
            entries.add(new Entry(title, last, unread > 0 ? Palette.ACCENT : Palette.TEXT,
                    () -> openDm(oid, oname)));
        }
        if (convList.size() == 0) {
            entries.add(new Entry("Keine aktiven Chats", "Klicke auf einen Freund, um zu schreiben", Palette.MUTED, null));
        }
    }

    private void openDm(String otherId, String name) {
        if (otherId.isEmpty()) { status = "Kein DM-Kontakt verfügbar."; return; }
        tab = 5;
        inThread = true;
        threadType = "dm";
        threadId = otherId;
        threadTitle = name;
        loadThread();
        buildWidgets();
    }

    private void openGroup(String gid, String name) {
        inThread = true;
        threadType = "group";
        threadId = gid;
        threadTitle = name;
        groupSinceMsg = 0;
        loadThread();
        buildWidgets();
    }

    private void loadThread() {
        List<String[]> lines = new ArrayList<>();
        thread(() -> {
            if (threadType.equals("group")) {
                JsonObject v = getObj("/group/view?groupId=" + enc(threadId));
                if (v != null && v.has("members")) {
                    int mcount = arr(v.get("members")).size();
                    lines.add(new String[]{ "⛺ " + threadTitle, (mcount == 0 ? "" : mcount + " Mitglieder"), "0" });
                }
            }
            if (threadType.equals("group")) {
                JsonObject poll = getObj("/group/poll?groupId=" + enc(threadId) + "&sinceMsg=0&sinceSig=0");
                if (poll != null && poll.has("messages")) {
                    buildLines(lines, poll.get("messages").getAsJsonArray());
                }
            } else {
                JsonArray msgs = getArr("/dm/messages?otherId=" + enc(threadId) + "&limit=40");
                buildLines(lines, msgs);
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
