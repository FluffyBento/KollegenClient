package dev.kollegen.client.input;

import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.file.Files;
import java.nio.file.Path;


public final class ControllerMode {

    private static boolean active = false;

    private static double cursorX = 0, cursorY = 0;
    private static boolean firstTick = true;

    
    private static long lastScrollAt = 0;
    private static final long SCROLL_INTERVAL_MS = 120;

    
    
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
        if (firstTick) {
            int w = mc.getWindow().getScreenWidth();
            int h = mc.getWindow().getScreenHeight();
            cursorX = w / 2.0;
            cursorY = h / 2.0;
            firstTick = false;
        }

        
        
        boolean padOk = false;
        int pad = GamepadDetect.scan(GAMEPAD);
        if (pad >= 0 || pad == GamepadDetect.FORWARDED) {
            padOk = true;
            applyGamepad(mc, window);
        }
        
        processClick(mc, window);
        if (!padOk) return;
    }

    private static void applyGamepad(Minecraft mc, long window) {
        float lx = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        float ly = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        
        float dead = 0.18f;
        float dx = (Math.abs(lx) < dead) ? 0 : lx;
        float dy = (Math.abs(ly) < dead) ? 0 : ly;

        
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT) == GLFW.GLFW_PRESS) dx = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) == GLFW.GLFW_PRESS) dx = 1;
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP) == GLFW.GLFW_PRESS) dy = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW.GLFW_PRESS) dy = 1;

        if (dx != 0 || dy != 0) {
            
            double len = Math.hypot(dx, dy);
            double nx = dx / len, ny = dy / len;
            
            double speed = Math.max(8.0, mc.getWindow().getScreenWidth() / 32.0);
            cursorX += nx * speed;
            cursorY += ny * speed;

            int w = mc.getWindow().getScreenWidth();
            int h = mc.getWindow().getScreenHeight();
            cursorX = Math.max(0, Math.min(w - 1, cursorX));
            cursorY = Math.max(0, Math.min(h - 1, cursorY));

            
            
            try {
                GLFW.glfwSetCursorPos(window, cursorX, cursorY);
            } catch (Throwable ignored) {
            }

            
            try {
                if (mc.screen != null) {
                    
                    int sw = mc.getWindow().getScreenWidth();
                    int gw = mc.getWindow().getGuiScaledWidth();
                    double guiX = cursorX * gw / sw;
                    double guiY = cursorY * gw / sw;

                    long now = System.currentTimeMillis();
                    
                    if (now - lastScrollAt >= SCROLL_INTERVAL_MS) {
                        
                        double amount = -ny; 
                        
                        double scrollAmount = 0.0;
                        if (Math.abs(amount) > 0.2) {
                            scrollAmount = amount * 3.0; 
                        }
                        if (scrollAmount != 0.0) {
                            
                            
                            mc.screen.mouseScrolled(guiX, guiY, 0.0, scrollAmount);
                            lastScrollAt = now;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        
        boolean a = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS;
        boolean b = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS;
        if (a && !clickQueued) {
            click(0);
        } else if (b && !clickQueued) {
            click(1);
        }
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
                        .kollegen$click(window, clickButton, GLFW.GLFW_PRESS, 0);
            } catch (Throwable ignored) {
            }
            clickPressed = true;
            releaseAfter = now + 80;
        } else if (now >= releaseAfter) {
            try {
                ((dev.kollegen.client.mixin.MouseHandlerAccessor) mc.mouseHandler)
                        .kollegen$click(window, clickButton, GLFW.GLFW_RELEASE, 0);
            } catch (Throwable ignored) {
            }
            clickQueued = false;
            clickPressed = false;
        }
    }
}
