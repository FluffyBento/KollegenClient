package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.rpc.KollegenRPC;

public final class Misc {

    private Misc() {
    }

    public static void register() {
        ModuleManager.register(new DiscordRpc());
    }

    /** Steuert die Discord Rich Presence. */
    private static class DiscordRpc extends Module {
        DiscordRpc() {
            super("discordrpc", "Discord Rich Presence", "Zeigt Kollegen Client in Discord an.", Category.MISC);
            this.enabled = true;
        }

        @Override
        public void onEnable() {
            KollegenRPC.start();
        }

        @Override
        public void onDisable() {
            KollegenRPC.stop();
        }
    }
}
