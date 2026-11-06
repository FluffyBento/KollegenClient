package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.party.PartyManager;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.Icons;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

import java.net.InetAddress;

public class HostWorldScreen extends Screen {

    private final Screen parent;
    private int px, py, pw, ph, cx, cw;
    private EditBox portInput;
    private GameType gameType = GameType.SURVIVAL;
    private boolean allowCommands = false;
    private boolean autoPartyWarp = true;
    private String statusMsg = "";

    public HostWorldScreen(Screen parent) {
        super(Component.literal("Welt hosten"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.pw = Math.min(this.width - 24, 640);
        this.ph = Math.min(this.height - 24, 460);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
        this.cx = this.px + 20;
        this.cw = this.pw - 40;

        buildWidgets();
    }

    private void buildWidgets() {
        clearWidgets();

        GlassButton close = new GlassButton(px + pw - 46, py + 12, 26, 24, Component.literal("✕"), btn ->
                Minecraft.getInstance().setScreenAndShow(parent));
        addRenderableWidget(close);

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && server.isPublished()) {
            int by = py + ph - 44;
            GlassButton stopBtn = new GlassButton(cx, by, (cw - 8) / 2, 28, Component.literal("🛑 Freigabe beenden"), btn -> {
                server.unpublishServer();
                KollegenPresence.syncServerPresence(null, null);
                statusMsg = "Freigabe beendet.";
                buildWidgets();
            });
            addRenderableWidget(stopBtn);

            GlassButton invitePartyBtn = new GlassButton(cx + (cw - 8) / 2 + 8, by, (cw - 8) / 2, 28, Component.literal("🎉 Party einladen"), btn -> {
                String ip = getHostIp();
                int port = server.getPort();
                PartyManager.onLeaderSwitchServer(ip + ":" + port);
                statusMsg = "Party-Mitglieder wurden zur Welt eingeladen!";
            });
            addRenderableWidget(invitePartyBtn);
        } else {
            int cardY = py + 70;
            int portY = cardY + 28;
            portInput = new EditBox(this.font, cx + 12, portY, 180, 24, Component.literal("Port"));
            portInput.setMaxLength(5);
            portInput.setValue("25565");
            addRenderableWidget(portInput);

            int btnY = cardY + 84;
            GlassButton modeBtn = new GlassButton(cx + 12, btnY, (cw - 32) / 2, 26, Component.literal(getGameModeLabel()), btn -> {
                cycleGameMode();
                btn.setMessage(Component.literal(getGameModeLabel()));
            });
            addRenderableWidget(modeBtn);

            GlassButton cheatBtn = new GlassButton(cx + 12 + (cw - 32) / 2 + 8, btnY, (cw - 32) / 2, 26, Component.literal(getCheatLabel()), btn -> {
                allowCommands = !allowCommands;
                btn.setMessage(Component.literal(getCheatLabel()));
            });
            addRenderableWidget(cheatBtn);

            GlassButton partyToggle = new GlassButton(cx + 12, btnY + 32, cw - 24, 26, Component.literal(getPartyWarpLabel()), btn -> {
                autoPartyWarp = !autoPartyWarp;
                btn.setMessage(Component.literal(getPartyWarpLabel()));
            });
            addRenderableWidget(partyToggle);

            int startY = py + ph - 44;
            GlassButton startBtn = new GlassButton(cx, startY, cw, 28, Component.literal("🚀 Welt jetzt freigeben & hosten"), btn ->
                    startHosting());
            addRenderableWidget(startBtn);
        }
    }

    private String getGameModeLabel() {
        return "🎮 Modus: " + switch (gameType) {
            case CREATIVE -> "Kreativ";
            case ADVENTURE -> "Abenteuer";
            case SPECTATOR -> "Zuschauer";
            default -> "Überleben";
        };
    }

    private void cycleGameMode() {
        gameType = switch (gameType) {
            case SURVIVAL -> GameType.CREATIVE;
            case CREATIVE -> GameType.ADVENTURE;
            case ADVENTURE -> GameType.SPECTATOR;
            default -> GameType.SURVIVAL;
        };
    }

    private String getCheatLabel() {
        return allowCommands ? "⚡ Cheats: Erlaubt" : "⚡ Cheats: Aus";
    }

    private String getPartyWarpLabel() {
        return autoPartyWarp ? "🎉 Party automatisch einladen: Aktiv ✓" : "🎉 Party automatisch einladen: Deaktiviert";
    }

    private void startHosting() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            statusMsg = "Keine lokale Welt gefunden.";
            return;
        }

        int port = 25565;
        if (portInput != null) {
            try {
                port = Integer.parseInt(portInput.getValue().trim());
            } catch (Throwable ignored) {
                port = 25565;
            }
        }

        boolean ok = server.publishServer(MinecraftServer.MultiplayerScope.LAN, allowCommands, port);
        if (ok) {
            server.setDefaultGameType(gameType);
            String hostIp = getHostIp();
            int actualPort = server.getPort();
            String address = hostIp + ":" + actualPort;
            KollegenPresence.syncServerPresence(address, mc.player != null ? mc.player.getGameProfile().name() : "Host");

            if (autoPartyWarp && PartyManager.inParty()) {
                PartyManager.onLeaderSwitchServer(address);
                statusMsg = "Welt auf Port " + actualPort + " freigegeben! Party wurde mitgenommen.";
            } else {
                statusMsg = "Welt auf Port " + actualPort + " erfolgreich freigegeben!";
            }
            buildWidgets();
        } else {
            statusMsg = "Fehler beim Freigeben der Welt (Port belegt?).";
        }
    }

    private String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable ignored) {
            return "127.0.0.1";
        }
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
        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xDC));

        UiStyleManager.drawPanel(g, px, py, pw, ph, Palette.BG);

        Icons.draw(g, Icons.GLOBE, px + 20, py + 16, 16, Palette.ACCENT);
        g.text(this.font, "SINGLEPLAYER-WELT HOSTEN", px + 44, py + 14, Palette.ACCENT, true);
        g.text(this.font, "Lade Freunde & Party direkt in deine Welt ein", px + 44, py + 26, Palette.MUTED, false);

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        boolean active = server != null && server.isPublished();

        int cardY = py + 54;
        int cardH = py + ph - 56 - cardY;
        UiStyleManager.drawCard(g, cx, cardY, cw, cardH, 8, Palette.PANEL, 0.0f, null);

        if (active) {
            int badgeY = cardY + 20;
            Glass.badge(g, cx + 16, badgeY, 130, 22, 6, Palette.GREEN, 0xFFFFFFFF, this.font, "● AKTIV GEHOSTET");

            String ip = getHostIp();
            int port = server.getPort();
            g.text(this.font, "Deine Welt ist jetzt im Netzwerk erreichbar unter:", cx + 16, badgeY + 36, Palette.TEXT, false);
            Glass.textScaled(g, this.font, ip + ":" + port, cx + 16, badgeY + 54, 1.4f, Palette.ACCENT, true);

            g.text(this.font, "Freunde sehen deinen Server jetzt im Server-Switcher unter 'Freunde auf Servern'.", cx + 16, badgeY + 90, Palette.MUTED, false);
            g.text(this.font, "Wenn du eine Party hast, kannst du alle Mitglieder mit einem Klick herholen.", cx + 16, badgeY + 104, Palette.MUTED, false);
        } else {
            g.text(this.font, "Port festlegen (Standard: 25565):", cx + 12, cardY + 14, Palette.TEXT, false);
            g.text(this.font, "Einstellungen für beitretende Spieler:", cx + 12, cardY + 68, Palette.TEXT, false);
        }

        if (!statusMsg.isEmpty()) {
            g.text(this.font, statusMsg, cx + 12, py + ph - 64, Palette.GREEN, false);
        }

        super.extractRenderState(g, mx, my, pt);
    }
}
