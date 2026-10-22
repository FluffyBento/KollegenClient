package dev.kollegen.client.input;

import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.file.Files;
import java.nio.file.Path;


public final class ControllerMode {

    private static boolean active = false;

    private static double cursorX = 0, cursorY = 0;
    private static boolean firstTick = true;

    
    private static long lastScrollAt = 0;
    private static final long SCROLL_INTERVAL_MS = 55;
    private static boolean screenWasOpen = false;
    private static boolean prevA = false;
    private static boolean prevB = false;
    private static boolean prevX = false;

    
    
    private static boolean clickQueued = false;
    private static long releaseAfter = 0;

    private static final GLFWGamepadState GAMEPAD = GLFWGamepadState.create();

    private ControllerMode() {
    }

    
    public static void init() {
        GamepadDetect.ensureMappings();
        try {
            Path state = FabricLoader.getInstance().getGameDir()
                    .resolve("mods").resolve(".kollegen-controller");
            String s = Files.exists(state) ? Files.readString(state).trim() : "";
            active = s.equalsIgnoreCase("on");
            if (active) {
                KollegenMod.LOGGER.info("Kollegen Controller-Modus aktiv (SteamDeck).");
            }
        } catch (Throwable t) {
            KollegenMod.LOGGER.error("Konnte Controller-Zustand nicht lesen", t);
            active = false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    
    public static void tick(Minecraft mc) {
        if (!active) return;
        if (mc == null || mc.getWindow() == null) return;
        long window = mc.getWindow().handle();
        boolean screenOpen = mc.gui.screen() != null;
        if (firstTick) {
            int w = mc.getWindow().getScreenWidth();
            int h = mc.getWindow().getScreenHeight();
            cursorX = w / 2.0;
            cursorY = h / 2.0;
            firstTick = false;
        }

        if (screenOpen && !screenWasOpen) {
            syncCursor(window);
            prevA = true;
            prevB = true;
            prevX = true;
        }
        screenWasOpen = screenOpen;

        if (screenOpen) {
            int pad = GamepadDetect.scan(GAMEPAD);
            if (pad >= 0 || pad == GamepadDetect.FORWARDED) {
                applyGamepad(mc, window);
            }
        }
        processClick(mc, window);
    }

    private static void syncCursor(long window) {
        try {
            double[] x = new double[1];
            double[] y = new double[1];
            GLFW.glfwGetCursorPos(window, x, y);
            cursorX = x[0];
            cursorY = y[0];
        } catch (Throwable ignored) {
        }
    }

    private static void applyGamepad(Minecraft mc, long window) {
        float lx = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        float ly = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        float ry = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);

        float dead = 0.18f;
        float dx = (Math.abs(lx) < dead) ? 0 : lx;
        float dy = (Math.abs(ly) < dead) ? 0 : ly;
        boolean dpad = false;

        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT) == GLFW.GLFW_PRESS) { dx = -1; dpad = true; }
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) == GLFW.GLFW_PRESS) { dx = 1; dpad = true; }
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP) == GLFW.GLFW_PRESS) { dy = -1; dpad = true; }
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW.GLFW_PRESS) { dy = 1; dpad = true; }

        int sw = mc.getWindow().getScreenWidth();
        int sh = mc.getWindow().getScreenHeight();

        if (dx != 0 || dy != 0) {
            double len = Math.hypot(dx, dy);
            double nx = dx / len, ny = dy / len;
            double mag = Math.min(1.0, len);
            double top = Math.max(10.0, sw / 40.0);
            double speed = dpad ? top * 0.6 : Math.max(2.0, top * mag * mag);
            cursorX += nx * speed;
            cursorY += ny * speed;
            cursorX = Math.max(0, Math.min(sw - 1, cursorX));
            cursorY = Math.max(0, Math.min(sh - 1, cursorY));

            try {
                GLFW.glfwSetCursorPos(window, cursorX, cursorY);
            } catch (Throwable ignored) {
            }
        }

        if (Math.abs(ry) > 0.25f) {
            long now = System.currentTimeMillis();
            if (now - lastScrollAt >= SCROLL_INTERVAL_MS) {
                try {
                    int gw = mc.getWindow().getGuiScaledWidth();
                    double guiX = cursorX * gw / sw;
                    double guiY = cursorY * gw / sw;
                    mc.gui.screen().mouseScrolled(guiX, guiY, 0.0, -ry * 2.0);
                    lastScrollAt = now;
                } catch (Throwable ignored) {
                }
            }
        }

        boolean a = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS;
        boolean b = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS;
        boolean x = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_X) == GLFW.GLFW_PRESS;
        if (a && !prevA && !clickQueued) {
            click(0);
        } else if (x && !prevX && !clickQueued) {
            click(1);
        }
        if (b && !prevB) {
            try {
                mc.gui.screen().onClose();
            } catch (Throwable ignored) {
            }
        }
        prevA = a;
        prevB = b;
        prevX = x;
    }

    private static void click(int button) {
        clickQueued = true;
        clickButton = button;
        clickPressed = false;
    }

    private static int clickButton = 0;
    private static boolean clickPressed = false;

    private static void processClick(Minecraft mc, long window) {
        if (!clickQueued) return;
        long now = System.currentTimeMillis();
        if (!clickPressed) {
            
            
            try {
                GLFW.glfwSetCursorPos(window, cursorX, cursorY);
            } catch (Throwable ignored) {
            }
            try {
                ((dev.kollegen.client.mixin.MouseHandlerAccessor) mc.mouseHandler)
                        .kollegen$click(window, new MouseButtonInfo(clickButton, 0), GLFW.GLFW_PRESS);
            } catch (Throwable ignored) {
            }
            clickPressed = true;
            releaseAfter = now + 80;
        } else if (now >= releaseAfter) {
            try {
                ((dev.kollegen.client.mixin.MouseHandlerAccessor) mc.mouseHandler)
                        .kollegen$click(window, new MouseButtonInfo(clickButton, 0), GLFW.GLFW_RELEASE);
            } catch (Throwable ignored) {
            }
            clickQueued = false;
            clickPressed = false;
        }
    }
}
