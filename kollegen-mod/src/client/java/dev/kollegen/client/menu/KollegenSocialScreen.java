package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


public class KollegenSocialScreen extends Screen {
    private final Screen parent;

    private static final int SW = 200;
    private static final int ROW_H = 36;
    private static final int GAP = 6;
    private static final String[] TABS = { "👥 Freunde", "📩 Anfragen", "🗂 Gruppen", "💬 Chats" };

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    private String backend = "";
    private String session = "";
    private JsonObject me;
    private boolean authed = false;
    private String status = "Launcher nicht verbunden – client.json fehlt.";

    private JsonObject state = new JsonObject();

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
    private int tabItemH;
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

        Entry(String title, String sub, int color, Runnable act) {
            this.title = title;
            this.sub = sub;
            this.color = color;
            this.act = act;
        }
    }

    private static final class Rect {
        int x, y, w, h;
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
        int w = Math.min(this.width - 50, 960);
        int h = Math.min(this.height - 50, 660);
        int y = (this.height - h) / 2;
        if (y < 12) y = 12;
        px = (this.width - w) / 2;
        py = y;
        pw = w;
        ph = h;
        cx = px + SW + 14;
        cw = pw - SW - 30;
        int headerH = 54;
        sidebarTop = py + 56;
        sidebarBottom = py + ph - 14;
        contentTop = py + 48;
        contentBottom = py + ph - 28;
        tabItemH = Math.max(34, (sidebarBottom - sidebarTop - 3 * GAP) / 4);
        buildWidgets();
        loadClient();
    }

    private void buildWidgets() {
        clearWidgets();
        int bh = 28;
        int by = py + 10;
        refreshBtn = new GlassButton(px + pw - 60, by, 26, bh, Component.literal("⟳"), btn -> {
            status = "Lade…";
            loadAll();
        });
        backBtn = new GlassButton(px + pw - 94, by, 26, bh, Component.literal("←"), btn -> {
            inThread = false;
            threadId = "";
            groupSinceMsg = 0;
            rebuildEntries();
            buildWidgets();
        });
        backBtn.visible = inThread;
        GlassButton closeBtn = new GlassButton(px + pw - 30, by, 26, bh, Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreenAndShow(parent));
        addRenderableWidget(refreshBtn);
        addRenderableWidget(backBtn);
        addRenderableWidget(closeBtn);

        int inpY = py + ph - 36;
        int btnW = 140;
        if (inThread) {
            GlassButton send = new GlassButton(cx + cw - btnW, inpY, btnW - 2, 28, Component.literal("Senden"), btn -> sendThread());
            addRenderableWidget(send);
            chatInp = new EditBox(this.font, cx + 10, inpY, cw - btnW - 18, 26, Component.literal("Nachricht"));
            chatInp.setMaxLength(2000);
            chatInp.setHint(Component.literal("Nachricht…"));
            addRenderableWidget(chatInp);
            chatInp.setFocused(true);
        } else if (tab == 0) {
            codeInp = new EditBox(this.font, cx + 10, inpY, cw - btnW - 18, 26, Component.literal("Freundes-Code"));
            codeInp.setMaxLength(16);
            codeInp.setHint(Component.literal("Freundes-Code…"));
            addRenderableWidget(codeInp);
            GlassButton b = new GlassButton(cx + cw - btnW, inpY, btnW - 2, 28, Component.literal("Hinzufügen"), btn -> apiAddFriend());
            addRenderableWidget(b);
        } else if (tab == 2) {
            groupInp = new EditBox(this.font, cx + 10, inpY, cw - btnW - 18, 26, Component.literal("Gruppenname"));
            groupInp.setMaxLength(40);
            groupInp.setHint(Component.literal("Gruppenname…"));
            addRenderableWidget(groupInp);
            GlassButton b = new GlassButton(cx + cw - btnW, inpY, btnW - 2, 28, Component.literal("Gruppe erstellen"), btn -> apiCreateGroup());
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
        if (event.button() != 0) return super.mouseClicked(event, bl);
        
        // Tab click in sidebar - with safe hitbox
        if (mx >= px && mx <= px + SW && my >= sidebarTop && my <= sidebarBottom) {
            int idx = (int) ((my - sidebarTop) / (tabItemH + GAP));
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
        
        // Entry clicks - check before scrollbar drag
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
        // Only scroll if mouse is over the entries area, not the right sidebar
        if (maxScroll > 0 && mx >= cx && mx <= cx + cw) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 24)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == GLFW.GLFW_KEY_ENTER || ki.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (codeInp != null && codeInp.isFocused()) { apiAddFriend(); return true; }
            if (groupInp != null && groupInp.isFocused()) { apiCreateGroup(); return true; }
            if (chatInp != null && chatInp.isFocused()) { sendThread(); return true; }
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));

        // Main panel with Minecraft-style design
        Glass.dropShadow(g, px, py, pw, ph, 16, 6, 12);
        Glass.panelDark(g, px, py, pw, ph, 16);

        int headerH = 54;
        // Header background with accent line
        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 12, Palette.tint(Palette.PANEL2, 0xBB));
        Glass.fillRound(g, px + 8, py + 8, SW, 4, 3, Palette.tint(Palette.ACCENT, 0xDD));

        g.text(this.font, "SOZIALES", px + 22, py + 24, Palette.ACCENT, false);
        g.text(this.font, "in-game", px + 22 + this.font.width("SOZIALES") + 6, py + 26, Palette.MUTED, false);

        // Tab selection in sidebar
        g.text(this.font, inThread ? threadTitle : TABS[tab], cx + 14, py + 22, Palette.TEXT, false);

        // Status bar
        int statusBarY = py + headerH;
        Glass.fillRound(g, cx + 8, statusBarY + 2, cw - 16, 32, 6, Palette.tint(Palette.PANEL2, 0xAA));
        g.text(this.font, trunc(status, cw - 32), cx + 12, statusBarY + 13, authed ? Palette.GREEN : Palette.MUTED, false);

        // Sidebar tabs
        int sidebarTop = py + headerH + 44;
        int sidebarBottom = py + ph - 14;
        int tabItemH = Math.max(34, (sidebarBottom - sidebarTop - 3 * GAP) / 4);

        g.enableScissor(px + 8, sidebarTop, px + SW - 8, sidebarBottom);
        for (int i = 0; i < TABS.length; i++) {
            int by = sidebarTop + i * (tabItemH + GAP);
            boolean sel = i == tab;
            boolean hov = mx >= px + 10 && mx <= px + SW - 10 && my >= by && my <= by + tabItemH;
            // Minecraft-style tab: gold background when selected, border when hovered
            int fill = sel ? Palette.ACCENT : (hov ? Palette.ACCENT : Palette.PANEL2);
            Glass.fillRound(g, px + 10, by, SW - 20, tabItemH, 8, fill);
            int ty = by + (tabItemH - this.font.lineHeight) / 2;
            g.text(this.font, TABS[i], px + 22, ty, sel ? 0xFFFFFFFF : ( hov ? Palette.TEXT : Palette.MUTED), false);
        }
        g.disableScissor();

        // Entries list area
        int listTop = py + headerH + 52;
        int listBottom = py + ph - 18;
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        entryRects.clear();
        int ex = cx + 8;
        int ew = cw - 16;
        int ey = listTop + 2 - scroll;
        for (Entry e : entries) {
            boolean vis = ey + ROW_H > listTop && ey < listBottom;
            if (vis) {
                boolean hov = mx >= ex && mx <= ex + ew && my >= ey && my <= ey + ROW_H;
                // Minecraft-style entry: gold border when hovered
                int borderCol = hov ? Palette.ACCENT : Palette.BORDER;
                Glass.fillRound(g, ex, ey, ew, ROW_H, 8, borderCol);
                Glass.fillRound(g, ex + 1, ey + 1, ew - 2, ROW_H - 2, 7,
                        hov ? Palette.tint(Palette.PANEL2, 0x88) : Palette.tint(Palette.PANEL2, 0x66));
                g.text(this.font, e.title, ex + 14, ey + 5, e.color, false);
                if (e.sub != null && !e.sub.isEmpty()) {
                    g.text(this.font, trunc(e.sub, ew - 28), ex + 14, ey + 19, Palette.MUTED, false);
                }
            }
            if (e.act != null) entryRects.add(new Rect(ex, ey, ew, ROW_H, e.act));
            ey += ROW_H + GAP;
        }
        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(32, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            // Minecraft-style scrollbar
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
        switch (tab) {
            case 0 -> rebuildFriends();
            case 1 -> rebuildRequests();
            case 2 -> rebuildGroups();
            default -> rebuildConvs();
        }
        computeScroll();
    }

    private void rebuildFriends() {
        JsonArray list = arr(state.get("friends"));
        JsonArray calls = arr(state.get("calls"));
        for (int i = 0; i < list.size(); i++) {
            JsonObject f = list.get(i).getAsJsonObject();
            String name = str(f, "name");
            if (name.isEmpty()) name = str(f, "mc_name");
            if (name.isEmpty()) name = "User " + str(f, "id");
            boolean online = f.has("online") && f.get("online").getAsBoolean();
            String server = f.has("server") && !f.get("server").isJsonNull() ? f.get("server").getAsString() : "";
            String otherId = str(f, "discordId");
            boolean inCall = false;
            for (JsonElement ce : calls) {
                if (!ce.isJsonObject()) continue;
                JsonObject c = ce.getAsJsonObject();
                if (!c.has("direct") || !c.get("direct").getAsBoolean()) continue;
                JsonElement pe = c.get("peer");
                if (pe != null && pe.isJsonObject() && otherId.equals(str(pe.getAsJsonObject(), "discordId"))) {
                    inCall = true;
                    break;
                }
            }
            String sub = online
                    ? ("\u25CF Online" + (server.isEmpty() ? "" : " \u00b7 " + server))
                    : ("\u25CB Offline" + (server.isEmpty() ? "" : " \u00b7 " + server)) + (inCall ? " \u00b7 \uD83D\uDCDE" : "");
            if (inCall) name = "\uD83D\uDCDE " + name;
            String friendName = name;
            int color = friendColor(f, online ? Palette.GREEN : Palette.MUTED);
            entries.add(new Entry(name, sub, color,
                    () -> openDm(otherId, friendName)));
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
            String finalName = name;
            entries.add(new Entry(name, mcount + " Mitglieder" + (last.isEmpty() ? "" : " \u00b7 " + trunc(last, 36)),
                    Palette.BLUE, () -> openGroup(id, finalName)));
        }
        if (groupList.size() == 0) {
            entries.add(new Entry("Keine Gruppen", "Lege oben eine Gruppe an (Mitglieder im Launcher freischalten)", Palette.MUTED, null));
        }
    }

    private void rebuildConvs() {
        JsonArray convs = arr(state.get("convs"));
        for (int i = 0; i < convs.size(); i++) {
            JsonObject c0 = convs.get(i).getAsJsonObject();
            JsonObject uq = c0.has("user") && c0.get("user").isJsonObject() ? c0.get("user").getAsJsonObject() : null;
            String name = str(uq, "name");
            if (name.isEmpty()) name = str(uq, "mc_name");
            if (name.isEmpty()) name = "Chat";
            String otherId = str(uq, "discordId");
            String last = "";
            if (c0.has("last") && c0.get("last").isJsonObject()) {
                JsonObject l = c0.get("last").getAsJsonObject();
                String t = str(l, "text");
                if (l.has("ts") && !l.get("ts").isJsonNull()) t = time(l.get("ts").getAsLong()) + " " + t;
                last = t;
            }
            String friendName = name;
            entries.add(new Entry(name, trunc(last.isEmpty() ? "Keine Nachrichten" : last, 56),
                    Palette.BLUE, () -> openDm(otherId, friendName)));
        }
        if (convs.size() == 0) {
            entries.add(new Entry("Keine Chats", "Öffne das Profil eines Freundes im Launcher", Palette.MUTED, null));
        }
    }

    private void openDm(String otherId, String name) {
        if (otherId.isEmpty()) { status = "Kein DM-Kontakt verfügbar."; return; }
        tab = 3;
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
                    JsonArray msgs = poll.get("messages").getAsJsonArray();
                    buildLines(lines, msgs);
                    long last = 0;
                    for (JsonElement e2 : msgs) {
                        if (e2.isJsonObject() && e2.getAsJsonObject().has("ts")) {
                            last = Math.max(last, e2.getAsJsonObject().get("ts").getAsLong());
                        }
                    }
                    groupSinceMsg = last;
                }
            } else {
                JsonArray msgs = getArr("/dm/messages?other=" + enc(threadId));
                buildLines(lines, msgs);
            }
            mc().execute(() -> {
                entries.clear();
                if (lines.isEmpty()) {
                    entries.add(new Entry("Noch keine Nachrichten", "Schreib die erste!", Palette.MUTED, null));
                } else {
                    for (String[] l : lines) {
                        int color = l[2].equals("0") ? Palette.MUTED : Palette.TEXT;
                        entries.add(new Entry(l[0], l[1], color, null));
                    }
                }
                computeScroll();
            });
        });
    }

    private void buildLines(List<String[]> out, JsonArray arr) {
        for (JsonElement e2 : arr) {
            if (!e2.isJsonObject()) continue;
            JsonObject m = e2.getAsJsonObject();
            String from = str(m, "from");
            String text = str(m, "text");
            String who = meId().equals(from) ? "Du" : fromName(from);
            String stamp = m.has("ts") && !m.get("ts").isJsonNull() ? time(m.get("ts").getAsLong()) : "";
            out.add(new String[]{ who + (stamp.isEmpty() ? "" : " \u00b7 " + stamp), trunc(text, 120), "1" });
        }
    }

    private void sendThread() {
        if (threadId.isEmpty()) return;
        String text = chatInp != null ? chatInp.getValue().trim() : "";
        if (text.isEmpty()) return;
        chatInp.setValue("");
        JsonObject body = new JsonObject();
        if (threadType.equals("group")) {
            body.addProperty("groupId", threadId);
            body.addProperty("text", text);
            thread(() -> {
                JsonObject r = post("/group/send", body);
                if (r != null && !r.has("error")) loadThread();
            });
        } else {
            body.addProperty("to_id", threadId);
            body.addProperty("text", text);
            thread(() -> {
                JsonObject r = post("/dm/send", body);
                if (r != null && !r.has("error")) loadThread();
            });
        }
    }

    private void pollGroupIfNeeded() {
        if (!authed || pollBusy || !threadType.equals("group") || threadId.isEmpty()) return;
        if (System.currentTimeMillis() - lastPoll < 2500) return;
        lastPoll = System.currentTimeMillis();
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
        if (fromId.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("from_id", fromId);
        thread(() -> {
            JsonObject r = post("/friend/accept", body);
            mc().execute(() -> {
                status = r != null && !r.has("error") ? "Anfrage angenommen ✓" : "Fehler";
                loadAll();
            });
        });
    }

    

    private void loadAll() {
        if (!authed) return;
        thread(() -> {
            JsonObject snap = new JsonObject();
            snap.add("friends", getArr("/friends"));
            snap.add("requests", getArr("/friend/requests"));
            snap.add("groups", getArr("/groups"));
            snap.add("convs", getArr("/dm/conversations"));
            snap.add("calls", getArr("/call/direct/active"));
            mc().execute(() -> {
                state = snap;
                if (inThread) loadThread();
                else { rebuildEntries(); status = "Aktualisiert ✓"; }
            });
        });
    }

    private JsonArray getArr(String path) {
        if (!authed) return new JsonArray();
        JsonElement e = getJson(path, true);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private JsonObject getObj(String path) {
        JsonElement e = authed ? getJson(path, false) : null;
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private JsonElement getJson(String path, boolean wantArray) {
        if (backend.isEmpty() || session.isEmpty()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(backend + path))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + session)
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null && !res.body().isEmpty()) {
                JsonElement e = JsonParser.parseString(res.body());
                if (wantArray && !e.isJsonArray()) return new JsonArray();
                return e;
            }
            if (res.statusCode() == 401) {
                mc().execute(() -> {
                    authed = false;
                    status = "Session abgelaufen – Launcher neu verbinden.";
                    rebuildEntries();
                });
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private JsonObject post(String path, JsonObject body) {
        if (backend.isEmpty() || session.isEmpty()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(backend + path))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + session)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString()))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.body() != null && !res.body().isEmpty()) {
                return JsonParser.parseString(res.body()).getAsJsonObject();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    

    private void loadClient() {
        thread(() -> {
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                if (!Files.exists(p)) {
                    mc().execute(() -> { status = "client.json fehlt – Launcher einmal starten."; rebuildEntries(); });
                    return;
                }
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                String b = str(o, "backend");
                String s = str(o, "session");
                JsonObject meObj = o.has("me") && o.get("me").isJsonObject() ? o.get("me").getAsJsonObject() : new JsonObject();
                mc().execute(() -> {
                    backend = b;
                    session = s;
                    me = meObj;
                    authed = !backend.isEmpty() && !session.isEmpty();
                    status = authed ? "Verbunden mit " + backend : "Keine Session – Launcher neu verbinden.";
                    rebuildEntries();
                    if (authed) loadAll();
                });
            } catch (Throwable t) {
                mc().execute(() -> { status = "client.json konnte nicht gelesen werden."; rebuildEntries(); });
            }
        });
    }

    

    private JsonArray arr(JsonElement e) {
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private String meId() {
        if (me != null && me.has("discordId")) {
            JsonElement id = me.get("discordId");
            if (id.isJsonPrimitive()) return id.getAsString();
        }
        return "";
    }

    private String fromName(String id) {
        for (JsonElement e2 : arr(state.get("friends"))) {
            if (!e2.isJsonObject()) continue;
            JsonObject f = e2.getAsJsonObject();
            String did = str(f, "discordId");
            if (did.equals(id)) {
                String n = str(f, "name");
                if (n.isEmpty()) n = str(f, "mc_name");
                return n.isEmpty() ? "Spieler" : n;
            }
        }
        return "Spieler " + (id.length() > 6 ? id.substring(id.length() - 6) : id);
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static String time(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, Math.max(1, max - 1)) + "…" : s;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void thread(Runnable r) {
        Thread t = new Thread(r, "kollegen-social");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}