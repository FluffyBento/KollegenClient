package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerSwitcherScreen extends Screen {

    private final Screen parent;
    private static final int ROW_H = 54;
    private static final int GAP = 6;
    private static final String[] TABS = {"👥 Freunde auf Servern", "🌐 Gespeicherte Server", "⚡ Direktverbindung"};
    private static final String[][] PRESETS = {
            {"GommeHD.net", "gommehd.net", "Deutschlands größtes Minigames-Netzwerk", "1.8 - 1.21"},
            {"Hypixel.net", "hypixel.net", "BedWars, SkyBlock, Duels & Minigames", "1.8 - 1.21"},
            {"PikaNetwork", "pika-network.net", "BedWars, Practice & Survival", "1.8 - 1.21"},
            {"CubeCraft", "play.cubecraft.net", "SkyWars, EggWars & Parkour", "1.12 - 1.21"},
            {"Rewinside.tv", "rewinside.tv", "BedWars & Community Server", "1.8 - 1.21"}
    };

    private static final ExecutorService PING_POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "kollegen-pinger");
        t.setDaemon(true);
        return t;
    });

    private int tab = 0;
    private int px, py, pw, ph, cx, cw, listTop, listBottom;
    private int scroll = 0;
    private int maxScroll = 0;

    private final List<ServerEntry> allSavedServers = new ArrayList<>();
    private final List<ServerEntry> entries = new ArrayList<>();
    private final List<ActionRect> actionRects = new ArrayList<>();
    private final ServerStatusPinger pinger = new ServerStatusPinger();

    private EditBox searchBox;
    private EditBox directInput;
    private GlassButton directJoinBtn;
    private GlassButton privacyBtn;

    private long lastClickTime = 0;
    private String lastClickedIp = null;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private String statusMsg = "Lade Server…";

    private static final class ServerEntry {
        final ServerData serverData;
        final String title;
        final String subtitle;
        final String ip;
        final boolean joinable;
        final String actionLabel;
        final String badge;
        final String friendName;
        final boolean hidden;
        FaviconTexture iconTexture;
        byte[] lastIconBytes;

        ServerEntry(ServerData serverData, String title, String subtitle, String ip, boolean joinable, String actionLabel, String badge, String friendName) {
            this.serverData = serverData;
            this.ip = ip != null ? ip.trim() : "";
            this.hidden = PrivacySettings.isServerIpHidden(this.ip);
            if (this.hidden) {
                if (title != null && (title.equalsIgnoreCase(this.ip) || PrivacySettings.isServerIpHidden(title))) {
                    this.title = "••••••••";
                } else {
                    this.title = title != null ? PrivacySettings.censorIps(title) : "••••••••";
                }
                this.subtitle = subtitle != null ? PrivacySettings.censorIps(subtitle) : "••••••••";
            } else {
                this.title = title != null ? title : "";
                this.subtitle = subtitle != null ? subtitle : "";
            }
            this.joinable = joinable;
            this.actionLabel = actionLabel;
            this.badge = badge;
            this.friendName = friendName;

            if (this.serverData != null && !this.ip.isEmpty()) {
                try {
                    this.iconTexture = FaviconTexture.forServer(Minecraft.getInstance().getTextureManager(), this.ip);
                } catch (Throwable ignored) {
                    this.iconTexture = null;
                }
                byte[] icon = this.serverData.getIconBytes();
                if (icon != null && icon.length > 0) {
                    applyIcon(icon);
                }
            }
        }

        void applyIcon(byte[] bytes) {
            if (this.iconTexture == null) {
                try {
                    this.iconTexture = FaviconTexture.forServer(Minecraft.getInstance().getTextureManager(), this.ip);
                } catch (Throwable ignored) {
                    return;
                }
            }
            if (Arrays.equals(bytes, this.lastIconBytes)) return;
            if (bytes == null || bytes.length == 0) {
                try {
                    this.iconTexture.clear();
                } catch (Throwable ignored) {
                }
                this.lastIconBytes = null;
                return;
            }
            NativeImage img = null;
            try {
                img = NativeImage.read(bytes);
                if (img != null) {
                    if (img.getWidth() == 64 && img.getHeight() == 64) {
                        this.iconTexture.upload(img);
                        this.lastIconBytes = bytes;
                    } else {
                        img.close();
                    }
                }
            } catch (Throwable t) {
                if (img != null) {
                    try {
                        img.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        void close() {
            if (this.iconTexture != null) {
                try {
                    this.iconTexture.close();
                } catch (Throwable ignored) {
                }
                this.iconTexture = null;
            }
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
        int w = Math.min(this.width - 28, 840);
        int h = Math.min(this.height - 28, 560);
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
        privacyBtn = new GlassButton(px + pw - 230, topY, 150, 24, Component.literal(privText), btn -> {
            PrivacySettings.toggleHideServer();
            boolean nowHidden = PrivacySettings.isHideServer();
            privacyBtn.setMessage(Component.literal(nowHidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich"));
            statusMsg = nowHidden ? "Dein Server ist jetzt für Freunde unsichtbar." : "Dein Server ist für Freunde sichtbar.";
        });
        addRenderableWidget(privacyBtn);

        if (pw >= 640) {
            addRenderableWidget(new AccountButton(px + pw - 360, topY + 1, true, btn ->
                    Minecraft.getInstance().setScreenAndShow(new AccountSwitcherScreen(this))));
        }

        GlassButton refresh = new GlassButton(px + pw - 74, topY, 26, 24, Component.literal("⟳"), btn -> {
            statusMsg = "Aktualisiere…";
            loadData();
        });
        addRenderableWidget(refresh);

        GlassButton close = new GlassButton(px + pw - 42, topY, 26, 24, Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreenAndShow(parent));
        addRenderableWidget(close);

        if (tab == 1) {
            int searchY = py + 84;
            searchBox = new EditBox(this.font, cx + 8, searchY, cw - 16, 24, Component.literal("Server-Suche"));
            searchBox.setMaxLength(64);
            searchBox.setHint(Component.literal("🔍 Server durchsuchen (z. B. GommeHD, Hypixel, IP)…"));
            searchBox.setResponder(this::filterSavedServers);
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
                boolean matchTitle = e.title.toLowerCase().contains(q);
                boolean matchIp = !e.hidden && e.ip.toLowerCase().contains(q);
                boolean matchSub = e.subtitle.toLowerCase().contains(q);
                boolean matchMotd = e.serverData != null && e.serverData.motd != null && e.serverData.motd.getString().toLowerCase().contains(q);
                if (matchTitle || matchIp || matchSub || matchMotd) {
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
                            ServerData sd = new ServerData(name + "'s Server", server, ServerData.Type.OTHER);
                            ServerEntry entry = new ServerEntry(sd, name, "Spielt auf: " + server, server, true, "🚀 Beitreten", "Online", name);
                            pingServerSafely(entry);
                            loaded.add(entry);
                        } else if (online) {
                            loaded.add(new ServerEntry(null, name, "Online \u00b7 Server verborgen 🔒", "", false, "Privat", "Verborgen", name));
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

    private void closeSavedServers() {
        for (ServerEntry e : allSavedServers) {
            e.close();
        }
    }

    private void loadSavedServers() {
        closeSavedServers();
        allSavedServers.clear();
        try {
            ServerList list = new ServerList(Minecraft.getInstance());
            list.load();
            int count = list.size();
            for (int i = 0; i < count; i++) {
                ServerData data = list.get(i);
                if (data != null && data.ip != null && !data.ip.isEmpty()) {
                    String name = data.name != null && !data.name.isEmpty() ? data.name : data.ip;
                    String sub = (data.status != null && !data.status.getString().isEmpty()) ? data.status.getString() : "Gespeicherter Server";
                    ServerEntry entry = new ServerEntry(data, name, sub, data.ip, true, "🚀 Beitreten", "Gespeichert", null);
                    pingServerSafely(entry);
                    allSavedServers.add(entry);
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
        for (ServerEntry e : entries) {
            if (!allSavedServers.contains(e)) {
                e.close();
            }
        }
        entries.clear();
        for (String[] preset : PRESETS) {
            ServerData sd = new ServerData(preset[0], preset[1], ServerData.Type.OTHER);
            ServerEntry entry = new ServerEntry(sd, preset[0], preset[2], preset[1], true, "🚀 Beitreten", preset[3], null);
            pingServerSafely(entry);
            entries.add(entry);
        }
        statusMsg = "Beliebte Multiplayer-Netzwerke:";
    }

    private void pingServerSafely(ServerEntry entry) {
        if (entry == null || entry.serverData == null || entry.ip.isEmpty()) return;
        PING_POOL.submit(() -> {
            try {
                boolean nativeTransport = Minecraft.getInstance().options.useNativeTransport();
                pinger.pingServer(entry.serverData, () -> {
                    Minecraft.getInstance().execute(() -> {
                        byte[] bytes = entry.serverData.getIconBytes();
                        if (bytes != null && bytes.length > 0) {
                            entry.applyIcon(bytes);
                        }
                        computeScroll();
                    });
                }, () -> {
                    Minecraft.getInstance().execute(() -> {
                        byte[] bytes = entry.serverData.getIconBytes();
                        if (bytes != null && bytes.length > 0) {
                            entry.applyIcon(bytes);
                        }
                        computeScroll();
                    });
                }, EventLoopGroupHolder.remote(nativeTransport));
            } catch (Throwable ignored) {
            }
        });
    }

    private void computeScroll() {
        int need = entries.size() * (ROW_H + GAP);
        int visible = (listBottom - 4) - listTop;
        maxScroll = Math.max(0, need - visible);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public void tick() {
        super.tick();
        try {
            pinger.tick();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void removed() {
        super.removed();
        try {
            pinger.removeAll();
        } catch (Throwable ignored) {
        }
        closeSavedServers();
        for (ServerEntry e : entries) {
            e.close();
        }
        entries.clear();
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
        try {
            pinger.tick();
        } catch (Throwable ignored) {
        }
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
                if (hov && !UiStyleManager.isMinecraftStyle()) {
                    Glass.ring(g, cx, ey, cw, ROW_H, 8, 1.0f, Palette.tint(Palette.ACCENT, 0x88));
                }

                int iconBox = 36;
                int iconX = cx + 9;
                int iconY = ey + (ROW_H - iconBox) / 2;

                if (UiStyleManager.isMinecraftStyle()) {
                    g.fill(iconX - 1, iconY - 1, iconX + iconBox + 1, iconY + iconBox + 1, 0xFF000000);
                    g.fill(iconX, iconY, iconX + iconBox, iconY + iconBox, 0xFF141414);
                } else {
                    Glass.fillRound(g, iconX - 1, iconY - 1, iconBox + 2, iconBox + 2, 6, 0x33000000);
                    Glass.ring(g, iconX - 1, iconY - 1, iconBox + 2, iconBox + 2, 6, 1.0f, Palette.tint(Palette.ACCENT, 0x55));
                }

                if (e.friendName != null && !e.friendName.isEmpty()) {
                    SkinFaceRenderer.drawFace(g, e.friendName, null, iconX + 2, iconY + 2, 32);
                } else {
                    if (e.serverData != null) {
                        byte[] currentBytes = e.serverData.getIconBytes();
                        if (currentBytes != null && currentBytes.length > 0 && !Arrays.equals(currentBytes, e.lastIconBytes)) {
                            e.applyIcon(currentBytes);
                        }
                    }
                    Identifier loc = e.iconTexture != null ? e.iconTexture.textureLocation() : null;
                    if (loc == null) {
                        loc = Identifier.withDefaultNamespace("textures/misc/unknown_server.png");
                    }
                    try {
                        g.blit(RenderPipelines.GUI_TEXTURED, loc, iconX + 2, iconY + 2, 0.0f, 0.0f, 32, 32, 32, 32);
                    } catch (Throwable ignored) {
                        drawFallbackMonogram(g, e, iconX + 2, iconY + 2);
                    }
                }

                int rightBoundary = cx + cw - 12;

                if (e.joinable) {
                    int btnW = 92;
                    int btnH = 26;
                    int bx = rightBoundary - btnW;
                    int by = ey + (ROW_H - btnH) / 2;
                    boolean bHov = mx >= bx && mx <= bx + btnW && my >= by && my <= by + btnH;
                    if (UiStyleManager.isMinecraftStyle()) {
                        Glass.drawMinecraftButton(g, bx, by, btnW, btnH, bHov ? 1.0f : 0.0f, true, false);
                        int tw = this.font.width(e.actionLabel);
                        int tcol = bHov ? 0xFFFFFFA0 : 0xFFFFFFFF;
                        g.text(this.font, e.actionLabel, bx + (btnW - tw) / 2, by + (btnH - 8) / 2, tcol, true);
                    } else {
                        int btnCol = bHov ? Palette.GREEN : Palette.ACCENT;
                        Glass.fillRound(g, bx, by, btnW, btnH, 6, btnCol);
                        if (bHov) {
                            Glass.ring(g, bx, by, btnW, btnH, 6, 1.0f, 0xFFFFFFFF);
                        }
                        int tw = this.font.width(e.actionLabel);
                        g.text(this.font, e.actionLabel, bx + (btnW - tw) / 2, by + 8, 0xFFFFFFFF, true);
                    }

                    actionRects.add(new ActionRect(bx, by, btnW, btnH, () ->
                            switchServer(Minecraft.getInstance(), e.ip, e.title)));

                    rightBoundary = bx - 8;

                    if (e.friendName != null && !e.friendName.isEmpty()) {
                        int pBtnW = 74;
                        int pBtnH = 26;
                        int pbx = rightBoundary - pBtnW;
                        int pby = ey + (ROW_H - pBtnH) / 2;
                        boolean pHov = mx >= pbx && mx <= pbx + pBtnW && my >= pby && my <= pby + pBtnH;
                        if (UiStyleManager.isMinecraftStyle()) {
                            Glass.drawMinecraftButton(g, pbx, pby, pBtnW, pBtnH, pHov ? 1.0f : 0.0f, false, false);
                            int ptw = this.font.width("Party");
                            int ptcol = pHov ? 0xFFFFFFA0 : 0xFFFFFFFF;
                            g.text(this.font, "Party", pbx + (pBtnW - ptw) / 2, pby + (pBtnH - 8) / 2, ptcol, true);
                        } else {
                            Glass.fillRound(g, pbx, pby, pBtnW, pBtnH, 6, pHov ? Palette.ACCENT2 : Palette.PANEL2);
                            int ptw = this.font.width("🎉 Party");
                            g.text(this.font, "🎉 Party", pbx + (pBtnW - ptw) / 2, pby + 8, pHov ? 0xFFFFFFFF : Palette.TEXT, false);
                        }

                        String targetFriend = e.friendName;
                        actionRects.add(new ActionRect(pbx, pby, pBtnW, pBtnH, () ->
                                PartyManager.invite(targetFriend)));

                        rightBoundary = pbx - 8;
                    }
                }

                int textX = iconX + iconBox + 10;
                int maxTextW = rightBoundary - textX - 8;

                String titleStr = Glass.trunc(this.font, e.title, Math.max(50, maxTextW - 120));
                int titleCol = UiStyleManager.isMinecraftStyle() ? 0xFFFFFFFF : Palette.TEXT;
                g.text(this.font, titleStr, textX, ey + 6, titleCol, true);

                int badgeX = textX + this.font.width(titleStr) + 8;

                if (e.hidden) {
                    g.text(this.font, "🔒 Zensiert", badgeX, ey + 6, 0xFFFF7777, true);
                    badgeX += this.font.width("🔒 Zensiert") + 8;
                }

                if (e.serverData != null) {
                    if (e.serverData.ping > 0) {
                        int pingCol = e.serverData.ping < 80 ? 0xFF55FF55 : (e.serverData.ping < 160 ? 0xFFFFFF55 : 0xFFFF5555);
                        String pingStr = e.serverData.ping + " ms";
                        g.text(this.font, pingStr, badgeX, ey + 6, pingCol, false);
                        badgeX += this.font.width(pingStr) + 8;
                    } else if (e.serverData.ping < 0) {
                        g.text(this.font, "🔴 Offline", badgeX, ey + 6, 0xFFFF5555, false);
                        badgeX += this.font.width("🔴 Offline") + 8;
                    }

                    if (e.serverData.players != null) {
                        String plyStr = "👥 " + e.serverData.players.online() + "/" + e.serverData.players.max();
                        g.text(this.font, plyStr, badgeX, ey + 6, Palette.MUTED, false);
                    } else if (e.serverData.status != null && !e.serverData.status.getString().isEmpty()) {
                        String statStr = Glass.trunc(this.font, e.serverData.status.getString(), Math.max(20, rightBoundary - badgeX - 4));
                        g.text(this.font, statStr, badgeX, ey + 6, Palette.MUTED, false);
                    }
                } else if (e.badge != null && !e.badge.isEmpty()) {
                    g.text(this.font, e.badge, badgeX, ey + 6, Palette.MUTED, false);
                }

                int safeW = Math.max(40, maxTextW);
                if (e.serverData != null && e.serverData.motd != null && !e.serverData.motd.getString().isEmpty() && !e.serverData.motd.getString().equals("Pinge Server…")) {
                    List<FormattedCharSequence> lines = null;
                    try {
                        lines = this.font.split(e.serverData.motd, safeW);
                    } catch (Throwable ignored) {
                    }
                    if (lines != null && !lines.isEmpty()) {
                        g.text(this.font, lines.get(0), textX, ey + 21, 0xFFE0E0E0, false);
                        if (lines.size() > 1) {
                            g.text(this.font, lines.get(1), textX, ey + 35, 0xFFAAAAAA, false);
                        } else {
                            String subInfo = e.hidden ? "🔒 IP geschützt" : e.ip;
                            g.text(this.font, subInfo, textX, ey + 35, Palette.MUTED, false);
                        }
                    } else {
                        renderFallbackLines(g, e, textX, ey, safeW);
                    }
                } else {
                    renderFallbackLines(g, e, textX, ey, safeW);
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

    private void renderFallbackLines(GuiGraphicsExtractor g, ServerEntry e, int textX, int ey, int maxTextW) {
        String sub = e.hidden ? "••••••••" : (e.subtitle != null && !e.subtitle.equalsIgnoreCase(e.ip) ? e.subtitle : "Bereit zum Beitreten");
        g.text(this.font, Glass.trunc(this.font, sub, maxTextW), textX, ey + 21, Palette.MUTED, false);
        String ipDisplay = e.hidden ? "🔒 IP geschützt" : e.ip;
        g.text(this.font, ipDisplay, textX, ey + 35, Palette.MUTED, false);
    }

    private void drawFallbackMonogram(GuiGraphicsExtractor g, ServerEntry e, int x, int y) {
        int iconCol = e.joinable ? Palette.ACCENT : Palette.MUTED;
        if (UiStyleManager.isMinecraftStyle()) {
            g.fill(x, y, x + 32, y + 32, 0xFF1E1E1E);
            g.fill(x, y, x + 32, y + 1, 0xFF353535);
            g.fill(x, y, x + 1, y + 32, 0xFF353535);
            g.fill(x, y + 31, x + 32, y + 32, 0xFF101010);
            g.fill(x + 31, y, x + 32, y + 32, 0xFF101010);
        } else {
            Glass.fillRound(g, x, y, 32, 32, 6, Palette.tint(iconCol, 0x33));
            Glass.ring(g, x, y, 32, 32, 6, 1.0f, iconCol);
        }
        String monogram = e.title.length() >= 2 ? e.title.substring(0, 2).toUpperCase() : (e.title.isEmpty() ? "?" : e.title.toUpperCase());
        int initW = this.font.width(monogram);
        g.text(this.font, monogram, x + (32 - initW) / 2, y + 11, iconCol, true);
    }

    public static void switchServer(Minecraft mc, String serverAddress, String serverName) {
        if (serverAddress == null || serverAddress.trim().isEmpty()) return;
        String cleanAddr = serverAddress.trim();
        ServerData target = new ServerData(serverName != null ? serverName : cleanAddr, cleanAddr, ServerData.Type.OTHER);
        TitleScreen title = new TitleScreen();
        ServerAddress parsed = ServerAddress.parseString(cleanAddr);
        ConnectScreen.startConnecting(title, mc, parsed, target, false, null);
    }
}
