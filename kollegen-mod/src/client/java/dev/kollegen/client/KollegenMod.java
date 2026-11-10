package dev.kollegen.client;

import dev.kollegen.client.mods.KeybindSetting;
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


@Environment(EnvType.CLIENT)
public class KollegenMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("kollegen-client");
    public static final String MOD_ID = "kollegen-client";
    public static final String VERSION = Version.get();
    public static final long DISCORD_CLIENT_ID = 1538588736718373034L;

    private static boolean shiftWasDown = false;
    private static final Set<Integer> pressedKeys = new HashSet<>();
    private static boolean wasConnected = false;
    private static boolean perfApplied = false;
    private static boolean threadFavoured = false;

    @Override
    public void onInitializeClient() {
        dev.kollegen.client.nativebridge.KollegenNative.init();
        dev.kollegen.client.server.ServerRestrictions.init();
        dev.kollegen.client.mods.Palette.loadTheme(); 
        ModuleManager.registerAll();
        
        
        dev.kollegen.client.mods.modules.        RendererManager.apply();
        dev.kollegen.client.input.ControllerMode.init(); 
        KollegenRPC.start(); 
        dev.kollegen.client.presence.KollegenPresence.loadLocalCapeConfig(); 
        cleanModMenu();
        LOGGER.info("Kollegen Client Mod initialisiert (Rechts-Shift = Menü).");
    }

    private static int modMenuCleanTick = 0;

    public static void cleanModMenu() {
        String[] internalIds = {
            "bobby", "kollegen-distance", "cloth-config", "clothconfig", "cloth-basic-math",
            "chat_heads", "chatheads", "placeholder-api", "placeholder_api",
            "silk-all", "silk", "silk-commands", "silk-core", "silk-game", "silk-igui",
            "silk-nbt", "silk-network", "silk-persistence",
            "fabric-language-kotlin", "fabriclanguagekotlin", "owo", "owolib", "owo-lib"
        };
        try {
            Class<?> mmClass = Class.forName("com.terraformersmc.modmenu.ModMenu");
            Object modsObj = mmClass.getField("MODS").get(null);
            Object rootModsObj = mmClass.getField("ROOT_MODS").get(null);
            Object pmapObj = mmClass.getField("PARENT_MAP").get(null);

            if (modsObj instanceof java.util.Map<?, ?> modsMap) {
                java.util.List<Object> toRemoveKeys = new java.util.ArrayList<>();
                for (Object k : modsMap.keySet()) {
                    if (k != null) {
                        String s = k.toString().toLowerCase(java.util.Locale.ROOT);
                        for (String id : internalIds) {
                            if (s.equals(id) || s.startsWith(id)) {
                                toRemoveKeys.add(k);
                                break;
                            }
                        }
                    }
                }
                for (Object k : toRemoveKeys) {
                    Object m = modsMap.remove(k);
                    if (rootModsObj instanceof java.util.Map<?, ?> rootMap) {
                        rootMap.remove(k);
                    }
                    if (pmapObj instanceof com.google.common.collect.Multimap<?, ?> mm && m != null) {
                        mm.removeAll(m);
                    }
                }
            }
            try {
                mmClass.getMethod("clearModCountCache").invoke(null);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cfgClass = Class.forName("com.terraformersmc.modmenu.config.ModMenuConfig");
            Object hiddenOption = cfgClass.getField("HIDDEN_MODS").get(null);
            java.lang.reflect.Method getVal = hiddenOption.getClass().getMethod("getValue");
            @SuppressWarnings("unchecked")
            java.util.Set<String> set = (java.util.Set<String>) getVal.invoke(hiddenOption);
            if (set != null) {
                for (String id : internalIds) {
                    set.add(id);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        if (++modMenuCleanTick % 20 == 0) {
            cleanModMenu();
        }

        if (!threadFavoured && dev.kollegen.client.mods.modules.Performance.KollegenTricks.shouldUseRustRuntime()) {
            threadFavoured = true;
            dev.kollegen.client.nativebridge.KollegenNative.favourRenderThread();
        }

        if (!perfApplied && mc.options != null) {
            perfApplied = true;
            for (Module m : ModuleManager.modules()) {
                if (m.enabled && !m.initialized) {
                    try {
                        m.onEnable();
                        m.initialized = true;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        
        dev.kollegen.client.input.GamepadDetect.ensureMappings();
        
        dev.kollegen.client.input.ControllerMode.tick(mc);
        
        dev.kollegen.client.input.GamepadInput.tick(mc);
        
        dev.kollegen.client.presence.PetRenderer.onTick(mc);
        dev.kollegen.client.presence.KollegenPresence.checkRefreshSignal();
        dev.kollegen.client.feed.JustNowManager.tick(mc);

        
        boolean shiftDown = dev.kollegen.client.input.KollegenKeybind.isRightShiftHeld();
        if (shiftDown && !shiftWasDown) {
            if (!(mc.gui.screen() instanceof KollegenMenuScreen)) {
                mc.setScreenAndShow(new KollegenMenuScreen(mc.gui.screen()));
            }
        }
        shiftWasDown = shiftDown;

        
        ModuleManager.tick();

        
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

        if (mc.gui != null && mc.gui.screen() == null) {
            boolean isCtrl = isCtrlDown();
            boolean isShift = isShiftDown();
            boolean isAlt = isAltDown();
            for (Module m : ModuleManager.modules()) {
                for (dev.kollegen.client.mods.Setting s : m.settings()) {
                    if (s instanceof KeybindSetting ks && ks.value >= 0) {
                        boolean down = isKeyDown(ks.value);
                        if (ks.ctrl && !isCtrl) down = false;
                        if (ks.shift && !isShift) down = false;
                        if (ks.alt && !isAlt) down = false;
                        int id = ks.getCompositeId();
                        boolean was = pressedKeys.contains(id);
                        if (down && !was) m.onKey();
                        if (down) pressedKeys.add(id);
                        else pressedKeys.remove(id);
                    }
                }
                if (m.key >= 0) {
                    boolean down = isKeyDown(m.key);
                    boolean was = pressedKeys.contains(m.key);
                    if (down && !was) m.onKey();
                    if (down) pressedKeys.add(m.key);
                    else pressedKeys.remove(m.key);
                }
            }
        } else {
            pressedKeys.removeIf(id -> !isKeyDown(id & 0xFFFF));
        }

        
        KollegenRPC.tick(mc);

        
        boolean connected = mc.getConnection() != null;
        if (connected && !wasConnected) dev.kollegen.client.presence.KollegenPresence.join(mc);
        else if (!connected && wasConnected) dev.kollegen.client.presence.KollegenPresence.leave();
        wasConnected = connected;
    }

    
    public static void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        dev.kollegen.client.input.GamepadInput.onFrame(mc);
        dev.kollegen.client.mods.modules.Visual.onFrame();
    }

    public static boolean isCtrlDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isAltDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
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
