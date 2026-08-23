package dev.kollegen.client.rpc;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mixin.MinecraftAccessor;
import dev.kollegen.client.mixin.ServerDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * In-game Discord Rich Presence. Verbindet sich über IPC mit dem lokalen
 * Discord-Client (gleiche Application Client ID wie der Launcher).
 *
 * Die Activity ist immer als „Kollegen Client“ markiert (details) und trägt –
 * sobald man auf einem Multiplayer-Server ist – die Server-Adresse als
 * joinSecret. Dadurch sehen Freunde im Launcher/Mod „⚡ Kollegen Client“ und
 * können per „Freund beitreten“ direkt in den Server springen.
 */
public class KollegenRPC {
    private static DiscordRPC rpc;
    private static boolean ready = false;

    private static String lastState = "";
    private static String lastServer = "";
    private static boolean lastInServer = false;
    private static long startTime = 0;

    public static void start() {
        try {
            rpc = DiscordRPC.INSTANCE;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = () -> ready = true;
            rpc.Discord_Initialize(String.valueOf(KollegenMod.DISCORD_CLIENT_ID), handlers, true, "");
            Thread t = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    rpc.Discord_RunCallbacks();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "kollegen-rpc");
            t.setDaemon(true);
            t.start();
            startTime = System.currentTimeMillis() / 1000;
            push("Im Hauptmenü", "", false);
        } catch (Exception e) {
            KollegenMod.LOGGER.warn("Discord RPC konnte nicht gestartet werden: {}", e.getMessage());
        }
    }

    /**
     * Aktualisiert die Presence (nur wenn sich etwas geändert hat). Wird pro
     * Client-Tick aus {@code KollegenMod.onTick()} aufgerufen.
     */
    public static void tick(Minecraft mc) {
        if (rpc == null || !ready) return;

        String state;
        String server = "";
        boolean inServer = false;
        try {
            if (mc.getConnection() != null) {
                if (mc.hasSingleplayerServer()) {
                    state = "Im Einzelspieler";
                } else {
                    // Auf einem (Multiplayer-)Server – Adresse als Join-Secret.
                    String ip = currentServerIp(mc);
                    if (ip != null && !ip.isEmpty()) {
                        server = ip;
                        state = "Im Server";
                        inServer = true;
                    } else {
                        state = "Im Mehrspieler";
                    }
                }
            } else if (mc.hasSingleplayerServer()) {
                state = "Im Einzelspieler";
            } else {
                state = "Im Hauptmenü";
            }
        } catch (Throwable t) {
            state = "Im Hauptmenü";
        }

        if (state.equals(lastState) && server.equals(lastServer) && inServer == lastInServer) {
            return;
        }
        lastState = state;
        lastServer = server;
        lastInServer = inServer;
        push(state, server, inServer);
    }

    private static void push(String state, String server, boolean inServer) {
        if (rpc == null || !ready) return;
        try {
            DiscordRichPresence presence = new DiscordRichPresence();
            presence.state = state;
            presence.details = "Kollegen Client";
            presence.startTimestamp = startTime;
            presence.largeImageKey = "kollegen";
            presence.largeImageText = "Kollegen Client";
            if (inServer && server != null && !server.isEmpty()) {
                presence.partyId = server;
                presence.partySize = 1;
                presence.partyMax = 10;
                presence.joinSecret = server;
            }
            rpc.Discord_UpdatePresence(presence);
        } catch (Exception ignored) {
        }
    }
//PART2
    /**
     * Holt die aktuelle Server-Adresse ueber @Accessor-Mixins (refmap-remapped).
     * Reflection ueber Class.getMethod(...) wuerde unter Intermediary mit
     * NoSuchMethodException scheitern.
     */
    private static String currentServerIp(Minecraft mc) {
        try {
            ServerData serverData = ((MinecraftAccessor) mc).kollegen$getCurrentServer();
            if (serverData == null) return null;
            String ip = ((ServerDataAccessor) serverData).kollegen$getIp();
            return (ip != null && !ip.isEmpty()) ? ip : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void stop() {
        if (rpc != null) {
            try {
                rpc.Discord_Shutdown();
            } catch (Exception ignored) {
            }
            rpc = null;
        }
    }
}
