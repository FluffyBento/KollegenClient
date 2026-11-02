package dev.kollegen.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import java.nio.ByteBuffer;

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
    private static boolean prevGuide = false;
    private static boolean guideDiagDone = false;
    private static boolean prevCombo = false;

    
    
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
        } catch (Throwable t) {
            active = false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean a) {
        active = a;
        try {
            Path state = FabricLoader.getInstance().getGameDir()
                    .resolve("mods").resolve(".kollegen-controller");
            Files.writeString(state, a ? "on" : "off");
        } catch (Throwable ignored) {
        }
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

        // Immer scannen, damit GAMEPAD-State aktuell ist (auch im Spiel, ohne Screen).
        try { GamepadDetect.scan(GAMEPAD); } catch (Throwable ignored) {}

        // Kombi D-Pad-Unten + Start oeffnet das Kollegen-Menue.
        boolean comboNow = false;
        try {
            comboNow = (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW.GLFW_PRESS)
                    && (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_START) == GLFW.GLFW_PRESS);
        } catch (Throwable ignored) {
        }
        if (comboNow && !prevCombo && mc.gui.screen() == null) {
            try {
                mc.setScreenAndShow(new dev.kollegen.client.menu.KollegenMenuScreen(mc.gui.screen()));
            } catch (Throwable ignored) {
            }
        }
        prevCombo = comboNow;

        // PS-Taste (raw button 8) oeffnet das Kollegen-Menue.
        boolean guideNow = false;
        try {
            for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                if (!GLFW.glfwJoystickPresent(jid)) continue;
                ByteBuffer btns = GLFW.glfwGetJoystickButtons(jid);
                if (btns == null || btns.limit() <= 8) continue;
                if (btns.get(8) == GLFW.GLFW_PRESS) { guideNow = true; break; }
            }
        } catch (Throwable ignored) {
        }
        // Launcher-Forward als Fallback
        if (!guideNow) guideNow = readGuideButton();
        if (guideNow && !prevGuide && mc.gui.screen() == null) {
            try {
                mc.setScreenAndShow(new dev.kollegen.client.menu.KollegenMenuScreen(mc.gui.screen()));
            } catch (Throwable ignored) {
            }
        }
        prevGuide = guideNow;

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
            click(InputConstants.MOUSE_BUTTON_LEFT);
        } else if (x && !prevX && !clickQueued) {
            click(InputConstants.MOUSE_BUTTON_RIGHT);
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

    private static int clickButton = InputConstants.MOUSE_BUTTON_LEFT;
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

    private static boolean readGuideButton() {
        // 1) Launcher-Forward (.kollegen-gamepad)
        try {
            Path p = FabricLoader.getInstance().getGameDir().resolve("mods").resolve(".kollegen-gamepad");
            if (java.nio.file.Files.exists(p)) {
                String raw = java.nio.file.Files.readString(p);
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                if (o.has("guide") && o.get("guide").getAsBoolean()) {
                    long t = o.has("t") ? o.get("t").getAsLong() : 0L;
                    if (t == 0L || System.currentTimeMillis() - t <= 1500) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        // 2) Roh-GLFW: Guide-Button ist bei vielen Controllern der letzte Button.
        try {
            for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                if (!GLFW.glfwJoystickPresent(jid)) continue;
                ByteBuffer btns = GLFW.glfwGetJoystickButtons(jid);
                if (btns == null) continue;
                int n = btns.limit();
                // Diagnose: raw button state (throttled via kollegen guide diag)
                if (n > 0 && !guideDiagDone) {
                    StringBuilder sb = new StringBuilder("[kollegen-guide] jid=").append(jid)
                            .append(" rawButtons=").append(n).append(" pressed=[");
                    boolean any = false;
                    for (int i = 0; i < n; i++) {
                        if (btns.get(i) == GLFW.GLFW_PRESS) {
                            if (any) sb.append(",");
                            sb.append(i);
                            any = true;
                        }
                    }
                    sb.append("]");
                    KollegenMod.LOGGER.info(sb.toString());
                    if (any) guideDiagDone = true;
                }
                if (n <= 15) continue;
                // Kandidaten-Indizes fuer Guide/Home (variieren je nach Treiber)
                // PS4: raw button 8 = PS-Taste (laut Diagnose)
                for (int idx : new int[] { 8, n - 1, n - 2, 10, 11, 12, 13, 14, 15, 16, 17 }) {
                    if (idx < 0 || idx >= n) continue;
                    if (btns.get(idx) == GLFW.GLFW_PRESS) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
