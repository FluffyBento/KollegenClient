package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    private static final int SW = 190;
    private static final int ROW_H = 34;
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
        int w = Math.min(this.width - 70, 940);
        int h = Math.min(this.height - 70, 640);
        int y = (this.height - h) / 2 - 24;
        if (y < 8) y = 8;
        px = (this.width - w) / 2;
        py = y;
        pw = w;
        ph = h;
        cx = px + SW + 18;
        cw = pw - SW - 34;
        sidebarTop = py + 64;
        sidebarBottom = py + ph - 16;
        contentTop = py + 56;
        contentBottom = py + ph - 34;
        tabItemH = Math.max(32, (sidebarBottom - sidebarTop - 3 * GAP) / 4);
        buildWidgets();
        loadClient();
    }

    private void buildWidgets() {
        clearWidgets();
        int bh = 26;
        int by = py + 16;
        refreshBtn = Button.builder(Component.literal("⟳"), btn -> {
            status = "Lade…";
            loadAll();
        }).bounds(px + pw - 66, by, bh, bh).build();
        backBtn = Button.builder(Component.literal("←"), btn -> {
            inThread = false;
            threadId = "";
            groupSinceMsg = 0;
            rebuildEntries();
            buildWidgets();
        }).bounds(px + pw - 96, by, bh, bh).build();
        backBtn.visible = inThread;
        Button closeBtn = Button.builder(Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreen(parent)).bounds(px + pw - 38, by, bh, bh).build();
        addRenderableWidget(refreshBtn);
        addRenderableWidget(backBtn);
        addRenderableWidget(closeBtn);

        int inpY = py + ph - 32;
        int btnW = 150;
        if (inThread) {
            Button send = Button.builder(Component.literal("Senden"), btn -> sendThread())
                    .bounds(cx + cw - btnW, inpY, btnW - 2, 24).build();
            addRenderableWidget(send);
            chatInp = new EditBox(this.font, cx + 14, inpY, cw - btnW - 22, 24, Component.literal("Nachricht"));
            chatInp.setMaxLength(2000);
            chatInp.setHint(Component.literal("Nachricht…"));
            addRenderableWidget(chatInp);
            chatInp.setFocused(true);
        } else if (tab == 0) {
            codeInp = new EditBox(this.font, cx + 14, inpY, cw - btnW - 22, 24, Component.literal("Freundes-Code"));
            codeInp.setMaxLength(16);
            codeInp.setHint(Component.literal("Freundes-Code…"));
            addRenderableWidget(codeInp);
            Button b = Button.builder(Component.literal("Hinzufügen"), btn -> apiAddFriend())
                    .bounds(cx + cw - btnW, inpY, btnW - 2, 24).build();
            addRenderableWidget(b);
        } else if (tab == 2) {
            groupInp = new EditBox(this.font, cx + 14, inpY, cw - btnW - 22, 24, Component.literal("Gruppenname"));
            groupInp.setMaxLength(40);
            groupInp.setHint(Component.literal("Gruppenname…"));
            addRenderableWidget(groupInp);
            Button b = Button.builder(Component.literal("Gruppe erstellen"), btn -> apiCreateGroup())
                    .bounds(cx + cw - btnW, inpY, btnW - 2, 24).build();
            addRenderableWidget(b);
        }
    }

    private void computeScroll() {
        int visible = (contentBottom - 6) - (contentTop + 62);
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
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0x20));
        Glass.fillRound(g, px, py, pw, ph, 20, Palette.BORDER);
        Glass.fillRound(g, px + 1, py + 1, pw - 2, ph - 2, 19, Palette.tint(Palette.PANEL, 0xF2));
        Glass.fillRound(g, px + 8, py + 8, SW, ph - 16, 14, Palette.tint(Palette.PANEL2, 0xCC));
        Glass.fillRound(g, px + 8, py + 8, SW, 6, 4, Palette.tint(Palette.ACCENT, 0xE0));

        g.drawString(this.font, "SOZIALES", px + 22, py + 26, Palette.ACCENT, false);
        g.drawString(this.font, "in-game", px + 22 + this.font.width("SOZIALES") + 6, py + 28, Palette.MUTED, false);

        g.drawString(this.font, inThread ? threadTitle : TABS[tab], cx + 14, py + 24, Palette.TEXT, false);

        g.fill(cx + 14, py + 40, cx + cw - 14, py + 50, Palette.tint(Palette.PANEL2, 0xAA));
        g.drawString(this.font, trunc(status, cw - 40), cx + 18, py + 43, authed ? Palette.GREEN : Palette.MUTED, false);

        g.enableScissor(px + 10, sidebarTop, px + SW - 2, sidebarBottom);
        for (int i = 0; i < TABS.length; i++) {
            int by = sidebarTop + i * (tabItemH + GAP);
            boolean sel = i == tab;
            Glass.fillRound(g, px + 14, by, SW - 28, tabItemH, 10,
                    sel ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0x66));
            int ty = by + (tabItemH - this.font.lineHeight) / 2;
            g.drawString(this.font, TABS[i], px + 26, ty, sel ? 0xffFFFFFF : Palette.TEXT, false);
        }
        g.disableScissor();

        int listTop = contentTop + 62;
        int listBottom = contentBottom - 40;
        g.enableScissor(cx, listTop, cx + cw, listBottom);
        entryRects.clear();
        int ex = cx + 10;
        int ew = cw - 20;
        int ey = listTop + 2 - scroll;
        for (Entry e : entries) {
            boolean vis = ey + ROW_H > listTop && ey < listBottom;
            if (vis) {
                boolean hov = mx >= ex && mx <= ex + ew && my >= ey && my <= ey + ROW_H;
                Glass.fillRound(g, ex, ey, ew, ROW_H, 10, Palette.tint(Palette.PANEL2, hov ? 0x99 : 0x66));
                g.drawString(this.font, e.title, ex + 12, ey + 5, e.color, false);
                if (e.sub != null && !e.sub.isEmpty()) {
                    g.drawString(this.font, trunc(e.sub, ew - 24), ex + 12, ey + 18, Palette.MUTED, false);
                }
            }
            if (e.act != null) entryRects.add(new Rect(ex, ey, ew, ROW_H, e.act));
            ey += ROW_H + GAP;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(22, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Glass.fillRound(g, cx + cw - 5, thumbY, 3, thumbH, 2, Palette.tint(Palette.ACCENT, 0xCC));
        }

        if (inThread) pollGroupIfNeeded();

        super.render(g, mx, my, pt);
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