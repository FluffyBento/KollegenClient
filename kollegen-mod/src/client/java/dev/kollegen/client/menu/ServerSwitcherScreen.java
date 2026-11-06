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
import dev.kollegen.client.ui.Icons;
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
    private static final int ROW_H = 46;
    private static final int GAP = 6;
    private static final String[] TABS = {"👥 Freunde auf Servern", "🌐 Gespeicherte Server", "⚡ Direktverbindung"};
    private static final String[][] PRESETS = {
            {"GOMMEHD.NET", "gommehd.net", "Deutschlands größtes Minigames-Netzwerk", "1.8 - 1.21"},
            {"HYPIXEL.NET", "hypixel.net", "BedWars, SkyBlock & Duels", "1.8 - 1.21"},
            {"PIKA-NETWORK.NET", "pika-network.net", "BedWars, Practice & Survival", "1.8 - 1.21"},
            {"PLAY.CUBECRAFT.NET", "play.cubecraft.net", "SkyWars, EggWars & Parkour", "1.12 - 1.21"},
            {"REWINSIDE.TV", "rewinside.tv", "BedWars & Community Server", "1.8 - 1.21"}
    };

    private int tab = 0;
    private int px, py, pw, ph, cx, cw, listTop, listBottom;
    private int scroll = 0;
    private int maxScroll = 0;

    private final List<ServerEntry> entries = new ArrayList<>();
    private final List<ActionRect> actionRects = new ArrayList<>();

    private EditBox directInput;
    private GlassButton directJoinBtn;
    private GlassButton privacyBtn;

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

        ServerEntry(String title, String subtitle, String ip, boolean joinable, String actionLabel, String badge, String friendName) {
            this.title = title;
            this.subtitle = subtitle;
            this.ip = ip;
            this.joinable = joinable;
            this.actionLabel = actionLabel;
            this.badge = badge;
            this.friendName = friendName;
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
        int w = Math.min(this.width - 24, 760);
        int h = Math.min(this.height - 24, 520);
        this.px = (this.width - w) / 2;
        this.py = (this.height - h) / 2;
        this.pw = w;
        this.ph = h;
        this.cx = px + 18;
        this.cw = pw - 36;
        this.listTop = py + 98;
        this.listBottom = py + ph - 16;

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

        GlassButton refresh = new GlassButton(px + pw - 108, topY, 26, 24, Component.literal("⟳"), btn -> {
            statusMsg = "Aktualisiere…";
            loadData();
        });
        addRenderableWidget(refresh);

        GlassButton close = new GlassButton(px + pw - 74, topY, 26, 24, Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreenAndShow(parent));
        addRenderableWidget(close);

        if (tab == 2) {
            int cardY = listTop + 4;
            int cardH = 68;
            int inpY = cardY + 30;
            int btnW = 104;
            directInput = new EditBox(this.font, cx + 16, inpY, cw - btnW - 44, 26, Component.literal("Server-IP"));
            directInput.setMaxLength(128);
            directInput.setHint(Component.literal("z.B. play.hypixel.net oder 127.0.0.1:25565…"));
            addRenderableWidget(directInput);

            directJoinBtn = new GlassButton(cx + cw - btnW - 16, inpY, btnW, 26, Component.literal("🚀 Beitreten"), btn -> {
                String ip = directInput.getValue().trim();
                if (!ip.isEmpty()) {
                    switchServer(Minecraft.getInstance(), ip, ip);
                }
            });
            addRenderableWidget(directJoinBtn);
        } else {
            directInput = null;
            directJoinBtn = null;
        }
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
                            loaded.add(new ServerEntry(name, "Spielt auf: " + server, server, true, "🚀 Beitreten", "Online", name));
                        } else if (online) {
                            loaded.add(new ServerEntry(name, "Online \u00b7 Server verborgen 🔒", "", false, "Privat", "Verborgen", name));
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
                    entries.add(new ServerEntry(data.name != null ? data.name : data.ip, sub, data.ip, true, "🚀 Beitreten", "Gespeichert", null));
                }
            }
            if (entries.isEmpty()) {
                statusMsg = "Keine Server in deiner Minecraft-Serverliste vorhanden.";
            } else {
                statusMsg = entries.size() + " gespeicherte Server gefunden.";
            }
        } catch (Throwable ignored) {
            statusMsg = "Fehler beim Laden der Serverliste.";
        }
    }

    private void loadPresets() {
        for (String[] preset : PRESETS) {
            entries.add(new ServerEntry(preset[0], preset[2], preset[1], true, "Verbinden", preset[3], null));
        }
        statusMsg = "Beliebte Multiplayer-Netzwerke:";
    }

    private void computeScroll() {
        int need = entries.size() * (ROW_H + GAP);
        int topOffset = tab == 2 ? 88 : 0;
        int visible = (listBottom - 4) - (listTop + topOffset);
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
            int fill = sel ? Palette.ACCENT : (hov ? Palette.PANEL2 : Palette.PANEL);
            Glass.fillRound(g, tx, tabY, tabW, 28, 7, fill);
            if (sel) {
                Glass.ring(g, tx, tabY, tabW, 28, 7, 1.2f, 0xFFFFFFFF);
            }
            int txtCol = sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
            int labelW = this.font.width(TABS[i]);
            g.text(this.font, TABS[i], tx + (tabW - labelW) / 2, tabY + 9, txtCol, false);
        }

        actionRects.clear();

        int startY = listTop;

        if (tab == 2) {
            int cardY = listTop;
            int cardH = 74;
            UiStyleManager.drawCard(g, cx, cardY, cw, cardH, 8, Palette.PANEL, 0.0f, null);
            Icons.draw(g, Icons.BOLT, cx + 16, cardY + 12, 14, Palette.ACCENT);
            g.text(this.font, "Direktverbindung per IP oder Domain", cx + 36, cardY + 13, Palette.TEXT, true);
            startY = cardY + cardH + 14;
            g.text(this.font, "⭐ Beliebte Multiplayer-Netzwerke:", cx + 4, startY - 10, Palette.MUTED, false);
            startY += 4;
        } else {
            g.text(this.font, statusMsg, cx + 4, py + 84, Palette.MUTED, false);
        }

        g.enableScissor(cx, startY, cx + cw, listBottom);

        int ey = startY + 2 - scroll;
        for (ServerEntry e : entries) {
            boolean vis = ey + ROW_H > startY && ey < listBottom;
            if (vis) {
                boolean hov = mx >= cx && mx <= cx + cw && my >= ey && my <= ey + ROW_H;
                float h = hov ? 1.0f : 0.0f;
                UiStyleManager.drawCard(g, cx, ey, cw, ROW_H, 8, Palette.PANEL, h, null);

                int iconX = cx + 10;
                int iconY = ey + (ROW_H - 28) / 2;
                int iconCol = e.joinable ? Palette.ACCENT : Palette.MUTED;
                Glass.fillRound(g, iconX, iconY, 28, 28, 6, Palette.tint(iconCol, 0x44));
                Glass.ring(g, iconX, iconY, 28, 28, 6, 1.0f, iconCol);

                String initial = e.title.isEmpty() ? "?" : e.title.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                int initW = this.font.width(initial);
                g.text(this.font, initial, iconX + (28 - initW) / 2, iconY + 9, iconCol, true);

                int textX = iconX + 36;
                g.text(this.font, Glass.trunc(this.font, e.title, cw - 240), textX, ey + 9, Palette.TEXT, true);
                g.text(this.font, Glass.trunc(this.font, e.subtitle, cw - 240), textX, ey + 24, Palette.MUTED, false);

                if (e.badge != null && !e.badge.isEmpty()) {
                    int bw = this.font.width(e.badge) + 12;
                    int bx = cx + cw - (e.joinable ? 180 : 80);
                    int by = ey + (ROW_H - 16) / 2;
                    Glass.badge(g, bx, by, bw, 16, 5, Palette.PANEL2, Palette.MUTED, this.font, e.badge);
                }

                if (e.joinable) {
                    if (e.friendName != null && !e.friendName.isEmpty()) {
                        int pBtnW = 68;
                        int pBtnH = 24;
                        int pbx = cx + cw - pBtnW - 98;
                        int pby = ey + (ROW_H - pBtnH) / 2;
                        boolean pHov = mx >= pbx && mx <= pbx + pBtnW && my >= pby && my <= pby + pBtnH;
                        Glass.fillRound(g, pbx, pby, pBtnW, pBtnH, 6, pHov ? Palette.ACCENT2 : Palette.PANEL2);
                        int ptw = this.font.width("🎉 Party");
                        g.text(this.font, "🎉 Party", pbx + (pBtnW - ptw) / 2, pby + (pBtnH - this.font.lineHeight) / 2, pHov ? 0xFFFFFFFF : Palette.TEXT, false);

                        String targetFriend = e.friendName;
                        actionRects.add(new ActionRect(pbx, pby, pBtnW, pBtnH, () ->
                                PartyManager.invite(targetFriend)));
                    }

                    int btnW = 86;
                    int btnH = 26;
                    int bx = cx + cw - btnW - 8;
                    int by = ey + (ROW_H - btnH) / 2;
                    boolean bHov = mx >= bx && mx <= bx + btnW && my >= by && my <= by + btnH;
                    int btnCol = bHov ? Palette.GREEN : Palette.ACCENT;
                    Glass.fillRound(g, bx, by, btnW, btnH, 6, btnCol);
                    if (bHov) {
                        Glass.ring(g, bx, by, btnW, btnH, 6, 1.0f, 0xFFFFFFFF);
                    }
                    int tw = this.font.width(e.actionLabel);
                    g.text(this.font, e.actionLabel, bx + (btnW - tw) / 2, by + (btnH - this.font.lineHeight) / 2, 0xFFFFFFFF, false);

                    String targetIp = e.ip;
                    String targetName = e.title;
                    actionRects.add(new ActionRect(bx, by, btnW, btnH, () ->
                            switchServer(Minecraft.getInstance(), targetIp, targetName)));
                } else {
                    int btnW = 64;
                    int btnH = 22;
                    int bx = cx + cw - btnW - 8;
                    int by = ey + (ROW_H - btnH) / 2;
                    Glass.fillRound(g, bx, by, btnW, btnH, 6, Palette.PANEL);
                    int tw = this.font.width(e.actionLabel);
                    g.text(this.font, e.actionLabel, bx + (btnW - tw) / 2, by + (btnH - this.font.lineHeight) / 2, Palette.MUTED, false);
                }
            }
            ey += ROW_H + GAP;
        }

        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = listBottom - startY;
            int thumbH = Math.max(28, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = startY + (int) ((trackH - thumbH) * (scroll / (double) maxScroll));
            Glass.scrollbarTrack(g, cx + cw - 4, startY, 4, trackH, 2);
            Glass.scrollbarThumb(g, cx + cw - 4, thumbY, 4, thumbH, 2, false);
        }

        super.extractRenderState(g, mx, my, pt);
    }

    public static void switchServer(Minecraft mc, String ip, String name) {
        if (ip == null || ip.trim().isEmpty()) return;
        String target = ip.trim();
        PartyManager.onLeaderSwitchServer(target);
        ServerData data = new ServerData(name != null && !name.isEmpty() ? name : target, target, ServerData.Type.OTHER);
        ServerAddress address = ServerAddress.parseString(target);
        if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
        }
        ConnectScreen.startConnecting(new TitleScreen(), mc, address, data, false, null);
    }
}
