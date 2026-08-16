package dev.kollegen.client.rpc;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;

/**
 * In-game Discord Rich Presence. Connects to the local Discord client via IPC
 * using the same application Client ID as the launcher. Kept intentionally
 * small: a single activity that reflects what the player is doing.
 */
public class KollegenRPC {
    private static DiscordRPC rpc;
    private static boolean ready = false;

    public static void start() {
        try {
            rpc = DiscordRPC.INSTANCE;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = (user) -> ready = true;
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
            setPlaying("Im Hauptmenü");
        } catch (Exception e) {
            KollegenMod.LOGGER.warn("Discord RPC konnte nicht gestartet werden: {}", e.getMessage());
        }
    }

    public static void setPlaying(String state) {
        if (rpc == null || !ready) return;
        DiscordRichPresence presence = new DiscordRichPresence();
        presence.state = state;
        presence.details = "Kollegen Client";
        presence.largeImageKey = "kollegen";
        presence.largeImageText = "Kollegen Client";
        rpc.Discord_UpdatePresence(presence);
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
