package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.party.PartyManager;
import dev.kollegen.client.presence.PrivacySettings;
import dev.kollegen.client.ui.AccountButton;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.Icons;
import dev.kollegen.client.ui.SkinFaceRenderer;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ServerSwitcherScreen extends Screen {

    private final Screen parent;
    private static final int ROW_H = 50;
    private static final int GAP = 6;
    private static final String[] TABS = {"👥 Freunde auf Servern", "🌐 Gespeicherte Server", "⚡ Direktverbindung"};
    private static final String[][] PRESETS = {
            {"GommeHD.net", "gommehd.net", "Deutschlands größtes Minigames-Netzwerk", "1.8 - 1.21"},
            {"Hypixel.net", "hypixel.net", "BedWars, SkyBlock, Duels & Minigames", "1.8 - 1.21"},
            {"PikaNetwork", "pika-network.net", "BedWars, Practice & Survival", "1.8 - 1.21"},
            {"CubeCraft", "play.cubecraft.net", "SkyWars, EggWars & Parkour", "1.12 - 1.21"},
            {"Rewinside.tv", "rewinside.tv", "BedWars & Community Server", "1.8 - 1.21"}
    };

    private int tab = 0;
    private int px, py, pw, ph, cx, cw, listTop, listBottom;
    private int scroll = 0;
    private int maxScroll = 0;

    private final List<ServerEntry> allSavedServers = new ArrayList<>();
    private final List<ServerEntry> entries = new ArrayList<>();
    private final List<ActionRect> actionRects = new ArrayList<>();

    private EditBox searchBox;
    private EditBox directInput;
    private GlassButton directJoinBtn;
    private GlassButton privacyBtn;

    private long lastClickTime = 0;
    private String lastClickedIp = null;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private String statusMsg = "Lade Server…";

    private static final class ServerEntry {
        final String title;
        final String subtitle;
        final String ip;
        final boolean joinable;
        final String actionLabel;
        final String badge;
        final String friendName;
        final int ping;

        ServerEntry(String title, String subtitle, String ip, boolean joinable, String actionLabel, String badge, String friendName, int ping) {
            this.title = title;
            this.subtitle = subtitle;
            this.ip = ip;
            this.joinable = joinable;
            this.actionLabel = actionLabel;
            this.badge = badge;
            this.friendName = friendName;
            this.ping = ping;
        }
    }

    private static final class ActionRect {
        final int x, y, w, h;
        final Runnable action;

        ActionRect(int x, int y, int w, int h, Runnable action) {
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

    public ServerSwitcherScreen(Screen parent) {
        super(Component.literal("Server wechseln"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 28, 820);
        int h = Math.min(this.height - 28, 550);
        this.px = (this.width - w) / 2;
        this.py = (this.height - h) / 2;
        this.pw = w;
        this.ph = h;
        this.cx = px + 18;
        this.cw = pw - 36;

        buildWidgets();
        loadData();
    }

    private void buildWidgets() {
        clearWidgets();

        int topY = py + 12;

        boolean hidden = PrivacySettings.isHideServer();
        String privText = hidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich";
        privacyBtn = new GlassButton(px + pw - 270, topY, 150, 24, Component.literal(privText), btn -> {
            PrivacySettings.toggleHideServer();
            boolean nowHidden = PrivacySettings.isHideServer();
            privacyBtn.setMessage(Component.literal(nowHidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich"));
            statusMsg = nowHidden ? "Dein Server ist jetzt für Freunde unsichtbar." : "Dein Server ist für Freunde sichtbar.";
        });
        addRenderableWidget(privacyBtn);

        if (pw >= 640) {
            addRenderableWidget(new AccountButton(px + pw - 278, topY + 1, true, btn ->
                    Minecraft.getInstance().setScreenAndShow(new AccountSwitcherScreen(this))));
        }

        GlassButton refresh = new GlassButton(px + pw - 108, topY, 26, 24, Component.literal("⟳"), btn -> {
            statusMsg = "Aktualisiere…";
            loadData();
        });
        addRenderableWidget(refresh);

        GlassButton close = new GlassButton(px + pw - 74, topY, 26, 24, Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreenAndShow(parent));
        addRenderableWidget(close);

        if (tab == 1) {
            int searchY = py + 84;
            searchBox = new EditBox(this.font, cx + 8, searchY, cw - 16, 24, Component.literal("Server-Suche"));
            searchBox.setMaxLength(64);
            searchBox.setHint(Component.literal("🔍 Server durchsuchen (z. B. GommeHD, Hypixel, IP)…"));
            searchBox.setResponder(query -> filterSavedServers(query));
            addRenderableWidget(searchBox);
            listTop = py + 116;
            listBottom = py + ph - 16;
        } else if (tab == 2) {
            searchBox = null;
            int cardY = py + 84;
            int inpY = cardY + 30;
            int btnW = 110;
            int inpW = cw - btnW - 36;
            directInput = new EditBox(this.font, cx + 16, inpY, inpW, 26, Component.literal("Server-IP"));
            directInput.setMaxLength(128);
            directInput.setHint(Component.literal("z. B. gommehd.net oder play.hypixel.net:25565…"));
            addRenderableWidget(directInput);

            directJoinBtn = new GlassButton(cx + cw - btnW - 14, inpY, btnW, 26, Component.literal("🚀 Beitreten"), btn -> {
                String ip = directInput.getValue().trim();
                if (!ip.isEmpty()) {
                    switchServer(Minecraft.getInstance(), ip, ip);
                }
            });
            addRenderableWidget(directJoinBtn);
            listTop = py + 188;
            listBottom = py + ph - 16;
        } else {
            searchBox = null;
            directInput = null;
            directJoinBtn = null;
            listTop = py + 106;
            listBottom = py + ph - 16;
        }
    }

    private void filterSavedServers(String query) {
        entries.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            entries.addAll(allSavedServers);
        } else {
            for (ServerEntry e : allSavedServers) {
                if (e.title.toLowerCase().contains(q) || e.ip.toLowerCase().contains(q) || e.subtitle.toLowerCase().contains(q)) {
                    entries.add(e);
                }
            }
        }
        statusMsg = entries.size() + " von " + allSavedServers.size() + " Servern gefunden.";
        computeScroll();
    }

    private void loadData() {
        entries.clear();
        if (tab == 0) {
            loadFriendsServers();
        } else if (tab == 1) {
            loadSavedServers();
        } else {
            loadPresets();
        }
        computeScroll();
    }

    private void loadFriendsServers() {
        Thread t = new Thread(() -> {
            List<ServerEntry> loaded = new ArrayList<>();
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                JsonArray friends = null;
                if (Files.exists(p)) {
                    JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                    String backend = o.has("backend") ? o.get("backend").getAsString() : "";
                    String session = o.has("session") ? o.get("session").getAsString() : "";
                    if (!backend.isEmpty() && !session.isEmpty()) {
                        try {
                            HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/friends"))
                                    .timeout(Duration.ofSeconds(4))
                                    .header("Authorization", "Bearer " + session)
                                    .GET().build();
                            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                            if (res.statusCode() == 200 && res.body() != null) {
                                JsonElement el = JsonParser.parseString(res.body());
                                if (el.isJsonArray()) friends = el.getAsJsonArray();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    if (friends == null && o.has("friends") && o.get("friends").isJsonArray()) {
                        friends = o.getAsJsonArray("friends");
                    }
                }

                if (friends != null) {
                    for (JsonElement el : friends) {
                        if (!el.isJsonObject()) continue;
                        JsonObject f = el.getAsJsonObject();
                        String name = f.has("name") && !f.get("name").isJsonNull() ? f.get("name").getAsString() : "";
                        if (name.isEmpty() && f.has("mc_name") && !f.get("mc_name").isJsonNull()) name = f.get("mc_name").getAsString();
                        if (name.isEmpty()) name = "Kollege";

                        boolean online = f.has("online") && f.get("online").getAsBoolean();
                        String server = f.has("server") && !f.get("server").isJsonNull() ? f.get("server").getAsString().trim() : "";
                        boolean hasHidden = "hidden".equalsIgnoreCase(server) || server.isEmpty();

                        if (online && !hasHidden) {
                            loaded.add(new ServerEntry(name, "Spielt auf: " + server, server, true, "🚀 Beitreten", "Online", name, 18));
                        } else if (online) {
                            loaded.add(new ServerEntry(name, "Online \u00b7 Server verborgen 🔒", "", false, "Privat", "Verborgen", name, 0));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            Minecraft.getInstance().execute(() -> {
                entries.clear();
                entries.addAll(loaded);
                if (entries.isEmpty()) {
                    statusMsg = "Aktuell spielt kein Freund auf einem sichtbaren Server.";
                } else {
                    statusMsg = entries.size() + " Freunde online gefunden.";
                }
                computeScroll();
            });
        }, "kollegen-friends-servers");
        t.setDaemon(true);
        t.start();
    }

    private void loadSavedServers() {
        allSavedServers.clear();
        try {
            ServerList list = new ServerList(Minecraft.getInstance());
            list.load();
            int count = list.size();
            for (int i = 0; i < count; i++) {
                ServerData data = list.get(i);
                if (data != null && data.ip != null && !data.ip.isEmpty()) {
                    String sub = data.ip;
                    if (data.status != null && !data.status.getString().isEmpty()) {
                        sub += " \u00b7 " + data.status.getString();
                    }
                    allSavedServers.add(new ServerEntry(data.name != null ? data.name : data.ip, sub, data.ip, true, "🚀 Beitreten", "Gespeichert", null, 24));
                }
            }
            if (searchBox != null && !searchBox.getValue().trim().isEmpty()) {
                filterSavedServers(searchBox.getValue());
            } else {
                entries.clear();
                entries.addAll(allSavedServers);
                if (entries.isEmpty()) {
                    statusMsg = "Keine Server in deiner Minecraft-Serverliste vorhanden.";
                } else {
                    statusMsg = entries.size() + " gespeicherte Server gefunden.";
                }
            }
        } catch (Throwable ignored) {
            statusMsg = "Fehler beim Laden der Serverliste.";
        }
    }

    private void loadPresets() {
        for (String[] preset : PRESETS) {
            entries.add(new ServerEntry(preset[0], preset[2], preset[1], true, "🚀 Beitreten", preset[3], null, 16));
        }
        statusMsg = "Beliebte Multiplayer-Netzwerke:";
    }

    private void computeScroll() {
        int need = entries.size() * (ROW_H + GAP);
        int visible = (listBottom - 4) - listTop;
        maxScroll = Math.max(0, need - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, bl);

        int tabY = py + 48;
        int tabW = (cw - 8) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = cx + i * (tabW + 4);
            if (my >= tabY && my <= tabY + 28 && mx >= tx && mx <= tx + tabW) {
                if (tab != i) {
                    tab = i;
                    scroll = 0;
                    buildWidgets();
                    loadData();
                    return true;
                }
            }
        }

        for (ActionRect r : actionRects) {
            if (r.hit(mx, my)) {
                if (r.action != null) r.action.run();
                return true;
            }
        }

        int ey = listTop + 2 - scroll;
        for (ServerEntry e : entries) {
            if (my >= ey && my <= ey + ROW_H && mx >= cx && mx <= cx + cw) {
                long now = System.currentTimeMillis();
                if (e.ip.equals(lastClickedIp) && (now - lastClickTime) < 350) {
                    if (e.joinable) {
                        switchServer(Minecraft.getInstance(), e.ip, e.title);
                        return true;
                    }
                }
                lastClickedIp = e.ip;
                lastClickTime = now;
                break;
            }
            ey += ROW_H + GAP;
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (maxScroll > 0 && mx >= cx && mx <= cx + cw && my >= listTop && my <= listBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 26)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        if (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER || ki.isConfirmation()) {
            if (directInput != null && directInput.isFocused()) {
                String ip = directInput.getValue().trim();
                if (!ip.isEmpty()) {
                    switchServer(Minecraft.getInstance(), ip, ip);
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

        Icons.draw(g, Icons.GLOBE, px + 18, py + 16, 16, Palette.ACCENT);
        g.text(this.font, "SERVER-SWITCHER", px + 40, py + 14, Palette.ACCENT, true);
        g.text(this.font, "Nahtlos zwischen Servern wechseln & Freunde begleiten", px + 40, py + 26, Palette.MUTED, false);

        int tabY = py + 48;
        int tabW = (cw - 8) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = cx + i * (tabW + 4);
            boolean sel = i == tab;
            boolean hov = mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + 28;
            if (UiStyleManager.isMinecraftStyle()) {
                Glass.drawMinecraftButton(g, tx, tabY, tabW, 28, hov ? 1.0f : 0.0f, sel, false);
                int txtCol = hov ? 0xFFFFFFA0 : (sel ? 0xFFFFFFFF : Palette.TEXT);
                Glass.textC(g, this.font, TABS[i], tx + tabW / 2, tabY + 10, txtCol, true);
            } else {
                int fill = sel ? Palette.ACCENT : (hov ? Palette.PANEL2 : Palette.PANEL);
                Glass.fillRound(g, tx, tabY, tabW, 28, 7, fill);
                if (sel) {
                    Glass.ring(g, tx, tabY, tabW, 28, 7, 1.2f, 0xFFFFFFFF);
                }
                int txtCol = sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
                int labelW = this.font.width(TABS[i]);
                g.text(this.font, TABS[i], tx + (tabW - labelW) / 2, tabY + 9, txtCol, false);
            }
        }

        actionRects.clear();

        if (tab == 2) {
            int cardY = py + 84;
            int cardH = 68;
            UiStyleManager.drawCard(g, cx, cardY, cw, cardH, 8, Palette.PANEL, 0.0f, null);
            Icons.draw(g, Icons.BOLT, cx + 16, cardY + 10, 14, Palette.ACCENT);
            g.text(this.font, "DIREKTVERBINDUNG", cx + 36, cardY + 11, Palette.TEXT, true);
            g.text(this.font, "Gib eine Server-Adresse oder IP-Adresse ein:", cx + 160, cardY + 11, Palette.MUTED, false);

            int bannerY = cardY + cardH + 12;
            Icons.draw(g, Icons.SPARKLE, cx + 4, bannerY + 1, 12, Palette.ACCENT);
            g.text(this.font, "BELIEBTE MULTIPLAYER-NETZWERKE", cx + 22, bannerY + 2, Palette.ACCENT, true);
        } else if (tab == 0) {
            g.text(this.font, statusMsg, cx + 4, py + 86, Palette.MUTED, false);
        }

        g.enableScissor(cx, listTop, cx + cw, listBottom);

        int ey = listTop + 2 - scroll;
        for (ServerEntry e : entries) {
            boolean vis = ey + ROW_H > listTop && ey < listBottom;
            if (vis) {
                boolean hov = mx >= cx && mx <= cx + cw && my >= ey && my <= ey + ROW_H;
                float h = hov ? 1.0f : 0.0f;
                UiStyleManager.drawCard(g, cx, ey, cw, ROW_H, 8, Palette.PANEL, h, null);
                if (hov) {
                    Glass.ring(g, cx, ey, cw, ROW_H, 8, 1.0f, Palette.tint(Palette.ACCENT, 0x88));
                }

                int iconX = cx + 10;
                int iconY = ey + (ROW_H - 32) / 2;
                int iconCol = e.joinable ? Palette.ACCENT : Palette.MUTED;

                if (e.friendName != null && !e.friendName.isEmpty()) {
                    SkinFaceRenderer.drawFace(g, e.friendName, null, iconX, iconY, 32);
                    Glass.ring(g, iconX, iconY, 32, 32, 6, 1.0f, Palette.ACCENT);
                } else {
                    Glass.fillRound(g, iconX, iconY, 32, 32, 6, Palette.tint(iconCol, 0x33));
                    Glass.ring(g, iconX, iconY, 32, 32, 6, 1.0f, iconCol);
                    String monogram = e.title.length() >= 2 ? e.title.substring(0, 2).toUpperCase() : (e.title.isEmpty() ? "?" : e.title.toUpperCase());
                    int initW = this.font.width(monogram);
                    g.text(this.font, monogram, iconX + (32 - initW) / 2, iconY + 11, iconCol, true);
                }

                int textX = iconX + 42;
                g.text(this.font, Glass.trunc(this.font, e.title, cw - 260), textX, ey + 10, Palette.TEXT, true);
                g.text(this.font, Glass.trunc(this.font, e.subtitle, cw - 260), textX, ey + 26, Palette.MUTED, false);

                int rightBoundary = cx + cw - 12;

                if (e.joinable) {
                    int btnW = 92;
                    int btnH = 26;
                    int bx = rightBoundary - btnW;
                    int by = ey + (ROW_H - btnH) / 2;
                    boolean bHov = mx >= bx && mx <= bx + btnW && my >= by && my <= by + btnH;
                    int btnCol = bHov ? Palette.GREEN : Palette.ACCENT;
                    Glass.fillRound(g, bx, by, btnW, btnH, 6, btnCol);
                    if (bHov) {
                        Glass.ring(g, bx, by, btnW, btnH, 6, 1.0f, 0xFFFFFFFF);
                    }
                    int tw = this.font.width(e.actionLabel);
                    g.text(this.font, e.actionLabel, bx + (btnW - tw) / 2, by + 8, 0xFFFFFFFF, true);

                    actionRects.add(new ActionRect(bx, by, btnW, btnH, () ->
                            switchServer(Minecraft.getInstance(), e.ip, e.title)));

                    rightBoundary = bx - 8;

                    if (e.friendName != null && !e.friendName.isEmpty()) {
                        int pBtnW = 74;
                        int pBtnH = 26;
                        int pbx = rightBoundary - pBtnW;
                        int pby = ey + (ROW_H - pBtnH) / 2;
                        boolean pHov = mx >= pbx && mx <= pbx + pBtnW && my >= pby && my <= pby + pBtnH;
                        Glass.fillRound(g, pbx, pby, pBtnW, pBtnH, 6, pHov ? Palette.ACCENT2 : Palette.PANEL2);
                        int ptw = this.font.width("🎉 Party");
                        g.text(this.font, "🎉 Party", pbx + (pBtnW - ptw) / 2, pby + 8, pHov ? 0xFFFFFFFF : Palette.TEXT, false);

                        String targetFriend = e.friendName;
                        actionRects.add(new ActionRect(pbx, pby, pBtnW, pBtnH, () ->
                                PartyManager.invite(targetFriend)));

                        rightBoundary = pbx - 8;
                    }
                }

                if (e.badge != null && !e.badge.isEmpty()) {
                    int bw = this.font.width(e.badge) + 12;
                    int bx = rightBoundary - bw;
                    int by = ey + (ROW_H - 18) / 2;
                    Glass.badge(g, bx, by, bw, 18, 5, Palette.PANEL2, Palette.MUTED, this.font, e.badge);
                    rightBoundary = bx - 8;
                }

                if (e.ping > 0) {
                    int pingDotX = rightBoundary - 10;
                    int pingDotY = ey + ROW_H / 2;
                    Glass.disc(g, pingDotX, pingDotY, 3.5f, Palette.GREEN);
                    Glass.ring(g, pingDotX - 4, pingDotY - 4, 8, 8, 4, 0.5f, Palette.GREEN);
                }
            }
            ey += ROW_H + GAP;
        }

        if (entries.isEmpty() && tab == 0) {
            int emptyY = py + ph / 2 - 20;
            Icons.draw(g, Icons.USER, cx + cw / 2 - 14, emptyY - 24, 28, Palette.MUTED);
            Glass.textC(g, this.font, "Aktuell spielt kein Freund auf einem sichtbaren Server", cx + cw / 2, emptyY + 12, Palette.TEXT, true);
            Glass.textC(g, this.font, "Freunde können ihren Server im Freunde-Menü teilen.", cx + cw / 2, emptyY + 26, Palette.MUTED, false);
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(28, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Glass.scrollbarTrack(g, cx + cw - 4, listTop, 4, trackH, 2);
            Glass.scrollbarThumb(g, cx + cw - 4, thumbY, 4, thumbH, 2, false);
        }

        super.extractRenderState(g, mx, my, pt);
    }

    public static void switchServer(Minecraft mc, String serverAddress, String serverName) {
        if (serverAddress == null || serverAddress.trim().isEmpty()) return;
        String cleanAddr = serverAddress.trim();

        if (PartyManager.isLeader()) {
            PartyManager.onLeaderSwitchServer(cleanAddr);
        }

        if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
        }

        ServerData targetData = new ServerData(serverName != null ? serverName : cleanAddr, cleanAddr, ServerData.Type.OTHER);
        ServerAddress address = ServerAddress.parseString(cleanAddr);
        ConnectScreen.startConnecting(new TitleScreen(), mc, address, targetData, false, null);
    }
}
