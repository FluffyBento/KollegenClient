package dev.kollegen.client;

import dev.kollegen.client.config.KollegenConfig;
import dev.kollegen.client.input.KollegenKeybind;
import dev.kollegen.client.menu.KollegenMenuScreen;
import dev.kollegen.client.rpc.KollegenRPC;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class KollegenMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("kollegen-client");
    public static final String MOD_ID = "kollegen-client";
    // Same Discord application as the launcher so the rich presence is consistent.
    public static final long DISCORD_CLIENT_ID = 1538588736718373034L;
    public static KollegenConfig CONFIG = KollegenConfig.load();

    @Override
    public void onInitializeClient() {
        CONFIG = KollegenConfig.load();
        KollegenKeybind.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> KollegenRPC.start());
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> KollegenRPC.stop());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (KollegenKeybind.menuKey.consumeClick()) {
                client.setScreen(new KollegenMenuScreen(client.screen));
            }
        });

        LOGGER.info("Kollegen Client Mod initialisiert (Rechts-Shift = Menü).");
    }
}
