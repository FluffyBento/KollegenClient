package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.presence.PrivacySettings;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
    private static final int ROW_H = 40;
    private static final int GAP = 6;
    private static final String[] TABS = {"👥 Freunde auf Servern", "🌐 Gespeicherte Server", "⚡ Direktverbindung"};
    private static final String[] PRESET_SERVERS = {"gommehd.net", "hypixel.net", "pika-network.net", "play.cubecraft.net", "rewinside.tv"};

    private int tab = 0;
    private int px, py, pw, ph, cx, cw, listTop, listBottom;
    private int scroll = 0;
    private int maxScroll = 0;

    private final List<ServerEntry> entries = new ArrayList<>();
    private final List<ActionRect> actionRects = new ArrayList<>();

    private EditBox directInput;
    private Button privacyBtn;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private String statusMsg = "Lade Server…";

    private static final class ServerEntry {
        final String title;
        final String subtitle;
        final String ip;
        final boolean joinable;
        final String actionLabel;

        ServerEntry(String title, String subtitle, String ip, boolean joinable, String actionLabel) {
            this.title = title;
            this.subtitle = subtitle;
            this.ip = ip;
            this.joinable = joinable;
            this.actionLabel = actionLabel;
        }
    }

    private static final class ActionRect {
        int x, y, w, h;
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
        int w = Math.min(this.width - 40, 720);
        int h = Math.min(this.height - 40, 500);
        this.px = (this.width - w) / 2;
        this.py = (this.height - h) / 2;
        this.pw = w;
        this.ph = h;
        this.cx = px + 16;
        this.cw = pw - 32;
        this.listTop = py + 92;
        this.listBottom = py + ph - 20;

        buildWidgets();
        loadData();
    }

    private void buildWidgets() {
        clearWidgets();

        int topY = py + 12;

        boolean hidden = PrivacySettings.isHideServer();
        String privText = hidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich";
        privacyBtn = new GlassButton(px + pw - 240, topY, 150, 24, Component.literal(privText), btn -> {
            PrivacySettings.toggleHideServer();
            boolean nowHidden = PrivacySettings.isHideServer();
            privacyBtn.setMessage(Component.literal(nowHidden ? "🔒 Server: Verborgen" : "👁 Server: Öffentlich"));
            statusMsg = nowHidden ? "Dein Server ist jetzt für Freunde unsichtbar." : "Dein Server ist für Freunde sichtbar.";
        });
        addRenderableWidget(privacyBtn);

        GlassButton refresh = new GlassButton(px + pw - 84, topY, 24, 24, Component.literal("⟳"), btn -> {
            statusMsg = "Aktualisiere…";
            loadData();
        });
        addRenderableWidget(refresh);

        GlassButton close = new GlassButton(px + pw - 54, topY, 24, 24, Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreenAndShow(parent));
        addRenderableWidget(close);

        if (tab == 2) {
            int inpY = listTop + 36;
            directInput = new EditBox(this.font, cx + 8, inpY, cw - 128, 26, Component.literal("Server-IP"));
            directInput.setMaxLength(128);
            directInput.setHint(Component.literal("Server-Adresse eingeben (z.B. gommehd.net)…"));
            addRenderableWidget(directInput);

            GlassButton connect = new GlassButton(cx + cw - 112, inpY, 104, 26, Component.literal("🚀 Beitreten"), btn -> {
                String ip = directInput.getValue().trim();
                if (!ip.isEmpty()) {
                    switchServer(Minecraft.getInstance(), ip, ip);
                }
            });
            addRenderableWidget(connect);
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
                            loaded.add(new ServerEntry(name, "Spielt auf: " + server, server, true, "🚀 Beitreten"));
                        } else if (online) {
                            loaded.add(new ServerEntry(name, "Online \u00b7 Server ausgeblendet 🔒", "", false, "Privat"));
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
                    entries.add(new ServerEntry(data.name != null ? data.name : data.ip, sub, data.ip, true, "Beitreten"));
                }
            }
            if (entries.isEmpty()) {
                statusMsg = "Keine gespeicherten Server in deiner Serverliste vorhanden.";
            } else {
                statusMsg = entries.size() + " gespeicherte Server.";
            }
        } catch (Throwable ignored) {
            statusMsg = "Fehler beim Laden der Serverliste.";
        }
    }

    private void loadPresets() {
        for (String preset : PRESET_SERVERS) {
            entries.add(new ServerEntry(preset.toUpperCase(java.util.Locale.ROOT), preset, preset, true, "Verbinden"));
        }
        statusMsg = "Beliebte Multiplayer-Server:";
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
        int tabW = cw / TABS.length;
        if (my >= tabY && my <= tabY + 28 && mx >= cx && mx <= cx + cw) {
            int clickedTab = (int) ((mx - cx) / tabW);
            if (clickedTab >= 0 && clickedTab < TABS.length && clickedTab != tab) {
                tab = clickedTab;
                scroll = 0;
                buildWidgets();
                loadData();
                return true;
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
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 24)));
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
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));

        Glass.dropShadow(g, px, py, pw, ph, 16, 6, 12);
        Glass.panelDark(g, px, py, pw, ph, 16);

        Glass.fillRound(g, px + 12, py + 10, 4, 28, 2, Palette.ACCENT);
        g.text(this.font, "SERVER WECHSELN", px + 24, py + 14, Palette.ACCENT, false);
        g.text(this.font, "In-Game Server-Switcher ohne Menü-Umweg", px + 24, py + 26, Palette.MUTED, false);

        int tabY = py + 48;
        int tabW = cw / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = cx + i * tabW;
            boolean sel = i == tab;
            boolean hov = mx >= tx && mx <= tx + tabW - 4 && my >= tabY && my <= tabY + 28;
            int fill = sel ? Palette.ACCENT : (hov ? Palette.PANEL2 : Palette.PANEL);
            Glass.fillRound(g, tx, tabY, tabW - 4, 28, 6, fill);
            int txtCol = sel ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
            int labelW = this.font.width(TABS[i]);
            g.text(this.font, TABS[i], tx + (tabW - 4 - labelW) / 2, tabY + 9, txtCol, false);
        }

        g.text(this.font, statusMsg, cx + 8, py + 80, Palette.MUTED, false);

        int startY = listTop;
        if (tab == 2) startY += 40;

        actionRects.clear();
        g.enableScissor(cx, startY, cx + cw, listBottom);

        int ey = startY + 2 - scroll;
        for (ServerEntry e : entries) {
            boolean vis = ey + ROW_H > startY && ey < listBottom;
            if (vis) {
                boolean hov = mx >= cx && mx <= cx + cw && my >= ey && my <= ey + ROW_H;
                int borderCol = hov ? Palette.ACCENT : Palette.BORDER;
                Glass.fillRound(g, cx, ey, cw, ROW_H, 8, borderCol);
                Glass.fillRound(g, cx + 1, ey + 1, cw - 2, ROW_H - 2, 7,
                        hov ? Palette.tint(Palette.PANEL2, 0x88) : Palette.tint(Palette.PANEL2, 0x66));

                g.text(this.font, e.title, cx + 12, ey + 7, Palette.TEXT, false);
                g.text(this.font, e.subtitle, cx + 12, ey + 22, Palette.MUTED, false);

                if (e.joinable) {
                    int btnW = 90;
                    int btnH = 24;
                    int bx = cx + cw - btnW - 8;
                    int by = ey + (ROW_H - btnH) / 2;
                    boolean bHov = mx >= bx && mx <= bx + btnW && my >= by && my <= by + btnH;
                    Glass.fillRound(g, bx, by, btnW, btnH, 6, bHov ? Palette.GREEN : Palette.ACCENT);
                    int tw = this.font.width(e.actionLabel);
                    g.text(this.font, e.actionLabel, bx + (btnW - tw) / 2, by + (btnH - this.font.lineHeight) / 2, 0xFFFFFFFF, false);

                    actionRects.add(new ActionRect(bx, by, btnW, btnH, () ->
                            switchServer(Minecraft.getInstance(), e.ip, e.title)));
                } else {
                    int btnW = 70;
                    int btnH = 22;
                    int bx = cx + cw - btnW - 8;
                    int by = ey + (ROW_H - btnH) / 2;
                    Glass.fillRound(g, bx, by, btnW, btnH, 6, Palette.MUTED);
                    int tw = this.font.width(e.actionLabel);
                    g.text(this.font, e.actionLabel, bx + (btnW - tw) / 2, by + (btnH - this.font.lineHeight) / 2, 0xFF888888, false);
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
        ServerData data = new ServerData(name != null && !name.isEmpty() ? name : target, target, ServerData.Type.OTHER);
        ServerAddress address = ServerAddress.parseString(target);
        if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
        }
        ConnectScreen.startConnecting(new TitleScreen(), mc, address, data, false, null);
    }
}
