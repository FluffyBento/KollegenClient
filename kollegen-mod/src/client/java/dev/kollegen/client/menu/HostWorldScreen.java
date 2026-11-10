package dev.kollegen.client.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.party.PartyManager;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;

public class HostWorldScreen extends Screen {

    public static boolean cheats = false;
    public static int slots = 8;
    public static int port = 61223;
    public static boolean forceMods = false;
    public static boolean forceOnlineMode = true;
    public static int privacyMode = 0;
    public static String activeDomain = null;
    public static String activeToken = null;
    public static int activePort = 61223;

    private static final String[] PRIVACY_MODES = {
            "INVITE ONLY",
            "FRIENDS ONLY",
            "PUBLIC"
    };

    private final Screen parent;
    private int px, py, pw, ph;
    private boolean draggingSlots = false;
    private boolean draggingPort = false;
    private String toast = "";
    private long toastUntil = 0L;

    public HostWorldScreen(Screen parent) {
        super(Component.literal("HOST WORLD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.pw = Math.min(360, this.width - 24);
        this.ph = Math.min(330, this.height - 24);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && server.isPublished()) {
            if (activeToken == null || activeToken.isEmpty()) {
                activeToken = generateToken();
            }
            activePort = server.getPort();
        }
    }

    private void updateSlotsFromMouse(double mx) {
        int sx = px + pw - 150;
        int sw = 80;
        double t = Math.max(0.0, Math.min(1.0, (mx - sx) / (double) sw));
        slots = 1 + (int) Math.round(t * 19.0);
    }

    private void updatePortFromMouse(double mx) {
        int sx = px + pw - 150;
        int sw = 80;
        double t = Math.max(0.0, Math.min(1.0, (mx - sx) / (double) sw));
        port = 1024 + (int) Math.round(t * (65535.0 - 1024.0));
    }

    private static String generateToken() {
        String chars = "23456789abcdefghjkmnpqrstuvwxyz";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void showToast(String msg) {
        this.toast = msg;
        this.toastUntil = System.currentTimeMillis() + 3000L;
    }

    private void startHosting() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            showToast("Keine Singleplayer-Welt aktiv.");
            return;
        }

        boolean ok = server.publishServer(MinecraftServer.MultiplayerScope.LAN, cheats, port);
        if (!ok) {
            showToast("Port belegt oder Fehler beim Hosten.");
            return;
        }

        server.setDefaultGameType(GameType.SURVIVAL);
        activePort = server.getPort();
        if (activeToken == null || activeToken.isEmpty()) {
            activeToken = generateToken();
        }
        activeDomain = null;
        showToast("Welt gehostet! Warte auf Tunnel...");
    }

    public static void onTunnelDomainAssigned(String domain) {
        if (domain == null || domain.isEmpty()) return;
        if (domain.equalsIgnoreCase(activeDomain)) return;
        activeDomain = domain;

        Minecraft mc = Minecraft.getInstance();
        String playerName = mc.player != null ? mc.player.getGameProfile().name() : (mc.getUser() != null ? mc.getUser().getName() : "Host");
        KollegenPresence.syncServerPresence(activeDomain, playerName);

        if (PartyManager.inParty()) {
            PartyManager.onLeaderSwitchServer(activeDomain);
        }

        notifyBackendHosting(activeToken != null ? activeToken : activeDomain, activeDomain, activePort, PRIVACY_MODES[privacyMode], forceMods, forceOnlineMode, slots);
        sendHostedChatMessage(activeDomain);
    }

    private void stopHosting() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null && server.isPublished()) {
            server.unpublishServer();
        }
        try {
            Class<?> clazz = Class.forName("link.e4mc.E4mcClient");
            Object session = clazz.getField("session").get(null);
            if (session != null) {
                session.getClass().getMethod("stop").invoke(session);
            }
        } catch (Throwable ignored) {
        }
        KollegenPresence.syncServerPresence(null, null);
        activeDomain = null;
        activeToken = null;
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§cHosted World beendet."));
        }
        showToast("Hosting beendet.");
    }

    private void inviteFriends() {
        if (activeDomain == null) return;
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard("Komm auf meine gehostete Welt: " + activeDomain);
        if (PartyManager.inParty()) {
            PartyManager.onLeaderSwitchServer(activeDomain);
        }
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§aEinladungstext in die Zwischenablage kopiert!"));
        }
        showToast("Einladung kopiert!");
    }

    private void copyDomain() {
        if (activeDomain == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(activeDomain);
        showToast("Domain kopiert!");
    }

    public static void sendHostedChatMessage(String domain) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.hud == null || mc.gui.hud.getChat() == null) return;

        Component topBorder = Component.literal("§b§m----------------§r §bHosted World §b§m----------------§r");
        Component lineSuccess = Component.literal("§7• §fWorld hosted successfully");
        Component lineDomain = Component.literal("§7• §fDomain: §b§n" + domain + "§r");

        MutableComponent inviteBtn = Component.literal("§a[Invite Friends]§r")
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent.CopyToClipboard("Komm auf meine Welt: " + domain))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Klicken zum Kopieren des Einladungstextes"))));

        MutableComponent copyBtn = Component.literal("§b[Copy Domain]§r")
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent.CopyToClipboard(domain))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Klicken zum Kopieren der Domain (" + domain + ")"))));

        MutableComponent actionLine = Component.empty().append(inviteBtn).append(" ").append(copyBtn);
        Component bottomBorder = Component.literal("§b§m----------------------------------------------§r");

        mc.gui.hud.getChat().addClientSystemMessage(topBorder);
        mc.gui.hud.getChat().addClientSystemMessage(lineSuccess);
        mc.gui.hud.getChat().addClientSystemMessage(lineDomain);
        mc.gui.hud.getChat().addClientSystemMessage(actionLine);
        mc.gui.hud.getChat().addClientSystemMessage(bottomBorder);
    }

    private static void notifyBackendHosting(String token, String domain, int port, String privacy, boolean forceMods, boolean forceOnline, int slots) {
        Thread t = new Thread(() -> {
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                if (!Files.exists(p)) return;
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                String backend = o.has("backend") ? o.get("backend").getAsString() : "";
                String session = o.has("session") ? o.get("session").getAsString() : "";
                if (backend.isEmpty() || session.isEmpty()) return;

                JsonObject body = new JsonObject();
                body.addProperty("token", token);
                body.addProperty("domain", domain);
                body.addProperty("port", port);
                body.addProperty("privacy", privacy);
                body.addProperty("force_mods", forceMods);
                body.addProperty("force_online", forceOnline);
                body.addProperty("slots", slots);

                HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/host"))
                        .timeout(Duration.ofSeconds(4))
                        .header("Authorization", "Bearer " + session)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        }, "kollegen-host-sync");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (mx >= px + pw - 26 && mx <= px + pw - 10 && my >= py + 10 && my <= py + 26) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        boolean isHosting = server != null && server.isPublished();

        if (mx >= px + pw - 30 && mx <= px + pw - 16 && my >= py + 54 && my <= py + 68) {
            cheats = !cheats;
            return true;
        }

        if (mx >= px + pw - 150 && mx <= px + pw - 70 && my >= py + 72 && my <= py + 90) {
            draggingSlots = true;
            updateSlotsFromMouse(mx);
            return true;
        }

        if (mx >= px + pw - 150 && mx <= px + pw - 70 && my >= py + 94 && my <= py + 112) {
            draggingPort = true;
            updatePortFromMouse(mx);
            return true;
        }

        if (mx >= px + pw - 30 && mx <= px + pw - 16 && my >= py + 136 && my <= py + 150) {
            forceMods = !forceMods;
            return true;
        }

        if (mx >= px + pw - 30 && mx <= px + pw - 16 && my >= py + 156 && my <= py + 170) {
            forceOnlineMode = !forceOnlineMode;
            return true;
        }

        if (mx >= px + pw - 120 && mx <= px + pw - 14 && my >= py + 174 && my <= py + 192) {
            if (mx <= px + pw - 100) {
                privacyMode = (privacyMode + PRIVACY_MODES.length - 1) % PRIVACY_MODES.length;
            } else {
                privacyMode = (privacyMode + 1) % PRIVACY_MODES.length;
            }
            return true;
        }

        if (mx >= px + pw - 58 && mx <= px + pw - 14 && my >= py + 201 && my <= py + 219) {
            if (isHosting && activeDomain != null) {
                copyDomain();
            }
            return true;
        }

        if (mx >= px + 14 && mx <= px + pw - 14 && my >= py + 232 && my <= py + 260) {
            if (isHosting) {
                stopHosting();
            } else {
                startHosting();
            }
            return true;
        }

        if (mx >= px + 14 && mx <= px + pw - 14 && my >= py + 266 && my <= py + 292) {
            if (isHosting) {
                inviteFriends();
            }
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingSlots) {
            updateSlotsFromMouse(event.x());
            return true;
        }
        if (draggingPort) {
            updatePortFromMouse(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSlots = false;
        draggingPort = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0x66000000);

        g.fill(px, py, px + pw, py + ph, 0xD0121419);
        drawBorder(g, px, py, pw, ph, 0x44FFFFFF);

        g.text(this.font, "HOST WORLD", px + 14, py + 14, 0xFFFFFFFF, false);

        boolean hovClose = mx >= px + pw - 26 && mx <= px + pw - 10 && my >= py + 10 && my <= py + 26;
        if (hovClose) {
            g.fill(px + pw - 26, py + 10, px + pw - 10, py + 26, 0x33FFFFFF);
        }
        drawBorder(g, px + pw - 26, py + 10, 16, 16, hovClose ? 0xFFFFFFFF : 0x55FFFFFF);
        g.text(this.font, "✕", px + pw - 22, py + 14, 0xFFFFFFFF, false);

        g.fill(px + 12, py + 30, px + pw - 12, py + 31, 0x55FFFFFF);

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        boolean isHosting = server != null && server.isPublished();

        g.text(this.font, "WORLD OPTIONS ▾", px + 14, py + 40, 0xFFE2E8F0, false);
        g.fill(px + 18, py + 54, px + 19, py + 112, 0x44FFFFFF);

        g.text(this.font, "CHEATS", px + 28, py + 57, 0xFFFFFFFF, false);
        drawCheckbox(g, px + pw - 30, py + 54, cheats, mx, my);

        g.text(this.font, "SLOTS", px + 28, py + 77, 0xFFFFFFFF, false);
        drawSlider(g, px + pw - 150, py + 81, 80, (slots - 1) / 19.0f, mx, my, draggingSlots);
        drawValueBox(g, px + pw - 58, py + 72, 44, 18, "" + slots);

        g.text(this.font, "PORT", px + 28, py + 99, 0xFFFFFFFF, false);
        drawSlider(g, px + pw - 150, py + 103, 80, (float) ((port - 1024) / (65535.0 - 1024.0)), mx, my, draggingPort);
        drawValueBox(g, px + pw - 58, py + 94, 44, 18, "" + port);

        g.text(this.font, "KOLLEGEN CLIENT OPTIONS ▾", px + 14, py + 122, 0xFFE2E8F0, false);
        g.fill(px + 18, py + 136, px + 19, py + 194, 0x44FFFFFF);

        g.text(this.font, "FORCE MODS", px + 28, py + 139, 0xFFFFFFFF, false);
        drawCheckbox(g, px + pw - 30, py + 136, forceMods, mx, my);

        g.text(this.font, "FORCE ONLINE MODE", px + 28, py + 159, 0xFFFFFFFF, false);
        drawCheckbox(g, px + pw - 30, py + 156, forceOnlineMode, mx, my);

        g.text(this.font, "PRIVACY MODE", px + 28, py + 179, 0xFFFFFFFF, false);
        drawSelectorBox(g, px + pw - 120, py + 174, 106, 18, "◂ " + PRIVACY_MODES[privacyMode] + " ▸", mx, my);

        g.text(this.font, "DOMAIN", px + 14, py + 206, 0xFFFFFFFF, false);
        String domStr = isHosting ? (activeDomain != null ? activeDomain : "Verbinde Tunnel...") : "---";
        int domCol = isHosting ? (activeDomain != null ? 0xFF55FFFF : 0xFFFFAA00) : 0xFF777777;
        int domW = this.font.width(domStr);
        g.text(this.font, domStr, px + pw - 68 - domW, py + 206, domCol, false);
        drawButton(g, px + pw - 58, py + 201, 44, 18, isHosting && activeDomain != null ? "Copy" : "---", mx, my, isHosting && activeDomain != null);

        drawButton(g, px + 14, py + 232, pw - 28, 28, isHosting ? "Stop Hosting" : "Start Hosting", mx, my, true);
        drawButton(g, px + 14, py + 266, pw - 28, 26, isHosting && activeDomain != null ? "Invite Friends" : "---", mx, my, isHosting && activeDomain != null);

        if (System.currentTimeMillis() < toastUntil && !toast.isEmpty()) {
            int tw = this.font.width(toast);
            g.text(this.font, toast, px + (pw - tw) / 2, py + 300, 0xFF55FFFF, false);
        }

        super.extractRenderState(g, mx, my, pt);
    }

    private void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col);
        g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col);
        g.fill(x + w - 1, y, x + w, y + h, col);
    }

    private void drawCheckbox(GuiGraphicsExtractor g, int x, int y, boolean checked, int mx, int my) {
        boolean hov = mx >= x && mx <= x + 14 && my >= y && my <= y + 14;
        g.fill(x, y, x + 14, y + 14, 0x44000000);
        drawBorder(g, x, y, 14, 14, hov ? 0xFFFFFFFF : 0x77FFFFFF);
        if (checked) {
            g.text(this.font, "✓", x + 3, y + 3, 0xFF55FF55, false);
        }
    }

    private void drawSlider(GuiGraphicsExtractor g, int x, int y, int w, float val, int mx, int my, boolean dragging) {
        g.fill(x, y, x + w, y + 2, 0x55FFFFFF);
        float clamped = Math.max(0.0f, Math.min(1.0f, val));
        int knobX = x + (int) (clamped * (w - 4));
        boolean hov = mx >= knobX - 2 && mx <= knobX + 6 && my >= y - 6 && my <= y + 8;
        g.fill(knobX, y - 6, knobX + 4, y + 8, (hov || dragging) ? 0xFFFFFFFF : 0xFFCCCCCC);
        drawBorder(g, knobX, y - 6, 4, 14, 0xFFFFFFFF);
    }

    private void drawValueBox(GuiGraphicsExtractor g, int x, int y, int w, int h, String txt) {
        g.fill(x, y, x + w, y + h, 0x33000000);
        drawBorder(g, x, y, w, h, 0x55FFFFFF);
        int tw = this.font.width(txt);
        g.text(this.font, txt, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    private void drawSelectorBox(GuiGraphicsExtractor g, int x, int y, int w, int h, String txt, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hov ? 0x44FFFFFF : 0x33000000);
        drawBorder(g, x, y, w, h, hov ? 0xFFFFFFFF : 0x66FFFFFF);
        int tw = this.font.width(txt);
        g.text(this.font, txt, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String txt, int mx, int my, boolean enabled) {
        boolean hov = enabled && mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hov ? 0x44FFFFFF : 0x22000000);
        drawBorder(g, x, y, w, h, enabled ? (hov ? 0xFFFFFFFF : 0x77FFFFFF) : 0x33FFFFFF);
        int tw = this.font.width(txt);
        g.text(this.font, txt, x + (w - tw) / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF666666, false);
    }
}
