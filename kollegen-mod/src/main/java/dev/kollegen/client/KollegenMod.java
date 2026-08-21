package dev.kollegen.client;

import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.menu.KollegenMenuScreen;
import dev.kollegen.client.rpc.KollegenRPC;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Kollegen Client Mod. Bewusst OHNE fabric-api geschrieben, damit er in jede
 * Fabric-/Quilt-Instanz injiziert werden kann. Der Tick-Hook kommt aus
 * {@code MinecraftClientMixin}. Alle Features/Menü-Optionen liegen im
 * Package {@code dev.kollegen.client.mods} (Module + Settings) und werden zur
 * Laufzeit aus einer JSON-Config geladen.
 */
@Environment(EnvType.CLIENT)
public class KollegenMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("kollegen-client");
    public static final String MOD_ID = "kollegen-client";
    public static final String VERSION = "1.6.9";
    public static final long DISCORD_CLIENT_ID = 1538588736718373034L;

    private static boolean shiftWasDown = false;
    private static final Set<Integer> pressedKeys = new HashSet<>();
    private static boolean wasConnected = false;

    @Override
    public void onInitializeClient() {
        dev.kollegen.client.mods.Palette.loadTheme(); // ggf. Launcher-Theme übernehmen
        ModuleManager.registerAll(); // lädt Config + ruft onEnable für aktive Module
        KollegenRPC.start(); // Rich Presence läuft ab sofort (ohne extra Setting)
        LOGGER.info("Kollegen Client Mod initialisiert (Rechts-Shift = Menü).");
    }

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

        // ── Module-Ticks ──
        ModuleManager.tick();

        // ── HUD verschieben per Drag ──
        if (dev.kollegen.client.mods.HudModule.dragging != null) {
            try {
                dev.kollegen.client.mods.HudModule d = dev.kollegen.client.mods.HudModule.dragging;
                int nx = (int) dev.kollegen.client.mods.HudModule.cursorX - dev.kollegen.client.mods.HudModule.dragOffX;
                int ny = (int) dev.kollegen.client.mods.HudModule.cursorY - dev.kollegen.client.mods.HudModule.dragOffY;
                d.offsetX.value = Math.max(-2000, Math.min(2000, nx));
                d.offsetY.value = Math.max(-2000, Math.min(2000, ny));
            } catch (Throwable ignored) {
            }
        }

        // ── Keybinds der Module (Edge-Trigger) ──
        for (Module m : ModuleManager.modules()) {
            if (m.key >= 0) {
                boolean down = isKeyDown(m.key);
                boolean was = pressedKeys.contains(m.key);
                if (down && !was) m.onKey();
                if (down) pressedKeys.add(m.key);
                else pressedKeys.remove(m.key);
            }
        }

        // ── Rich Presence ──
        KollegenRPC.tick(mc);

        // ── Kollegen-Präsenz (Backend-Liste) bei Join/Leave ──
        boolean connected = mc.getConnection() != null;
        if (connected && !wasConnected) dev.kollegen.client.presence.KollegenPresence.join(mc);
        else if (!connected && wasConnected) dev.kollegen.client.presence.KollegenPresence.leave();
        wasConnected = connected;
    }

    private static boolean isKeyDown(int key) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            long handle = mc.getWindow().handle();
            return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }
}
