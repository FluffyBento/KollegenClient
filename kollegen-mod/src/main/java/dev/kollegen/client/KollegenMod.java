package dev.kollegen.client;

import dev.kollegen.client.config.KollegenConfig;
import dev.kollegen.client.join.KollegenJoin;
import dev.kollegen.client.menu.KollegenMenuScreen;
import dev.kollegen.client.render.KollegenPostFX;
import dev.kollegen.client.rpc.KollegenRPC;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kollegen Client Mod. Bewusst OHNE fabric-api geschrieben, damit er in jede
 * Fabric-/Quilt-Instanz injiziert werden kann (auch in Instanzen ohne
 * fabric-api). Der Tick-Hook kommt aus {@code MinecraftClientMixin}.
 *
 * Features:
 *  - Rechts-Shift öffnet das Kollegen-Mod-Menü (Sättigung, Farb-Hervorhebung …)
 *  - Discord Rich Presence mit Join-Secret (Freunde können „beitreten“)
 *  - Automatisches Verbinden, wenn eine join_request.json existiert
 */
@Environment(EnvType.CLIENT)
public class KollegenMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("kollegen-client");
    public static final String MOD_ID = "kollegen-client";
    public static final String VERSION = "1.0.0";
    // Same Discord application as the launcher so the rich presence is consistent.
    public static final long DISCORD_CLIENT_ID = 1538588736718373034L;
    public static KollegenConfig CONFIG = KollegenConfig.load();

    private static boolean shiftWasDown = false;

    @Override
    public void onInitializeClient() {
        CONFIG = KollegenConfig.load();
        KollegenRPC.start();
        // JVM-Sicherheitsnetz: RPC-Shutdown auch ohne fabric-lifecycle-Hook.
        Runtime.getRuntime().addShutdownHook(new Thread(KollegenRPC::stop));
        KollegenPostFX.applyConfig();
        dev.kollegen.client.feature.Fullbright.reconcile();
        LOGGER.info("Kollegen Client Mod initialisiert (Rechts-Shift = Menü).");
    }

    /**
     * Wird jeden Client-Tick aus {@code MinecraftClientMixin} aufgerufen
     * (Ersatz für fabric-api {@code ClientTickEvents}).
     */
    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // ── Rechts-Shift → Menü (mit Entprellung) ──
        boolean shiftDown = dev.kollegen.client.input.KollegenKeybind.isRightShiftHeld();
        if (shiftDown && !shiftWasDown) {
            if (!(mc.screen instanceof KollegenMenuScreen)) {
                mc.setScreen(new KollegenMenuScreen(mc.screen));
            }
        }
        shiftWasDown = shiftDown;

        // ── Farb-FX (Sättigung / Hervorhebung) anwenden ──
        KollegenPostFX.tick();

        // ── Fullbright-Zustand mit Config abgleichen ──
        dev.kollegen.client.feature.Fullbright.reconcile();

        // ── Join-Requests aus join_request.json abarbeiten ──
        KollegenJoin.tick(mc);

        // ── Rich Presence aktualisieren ──
        KollegenRPC.tick(mc);

        // ── Kollegen-Presence (Icon neben Namen in der Tab-Liste) ──
        dev.kollegen.client.presence.Presence.tick();
    }
}
