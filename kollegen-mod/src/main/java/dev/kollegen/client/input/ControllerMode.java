package dev.kollegen.client.input;

import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Controller-Modus (SteamDeck/Konsole).
 * <p>
 * Der Launcher schreibt je Instanz eine State-Datei {@code mods/.kollegen-controller}
 * mit dem Inhalt "on" oder "off". Ist "on" gesetzt (SteamDeck-Modus aktiv),
 * wird ein virtueller Cursor eingeschaltet, der sich per Gamepad (linker Stick
 * oder D-Pad) bewegen und mit der A-Taste klicken lässt. Das funktioniert in
 * jedem Screen – auch in normalen Minecraft-Menüs vom Spiel – weil wir den
 * echten OS-Cursor bewegen und Klicks über einen MouseHandler-Accessor
 * einspeisen.
 * <p>
 * Bewusst im Stil des Mods gehalten (GLFW direkt, ohne fabric-api): wie
 * {@link KollegenKeybind} und das Renderer-State-File in RendererManager.
 */
public final class ControllerMode {

    private static boolean active = false;

    private static double cursorX = 0, cursorY = 0;
    private static boolean firstTick = true;

    // Scroll-Rate-Limit (Millisekunden zwischen synthetischen Scroll-Events)
    private static long lastScrollAt = 0;
    private static final long SCROLL_INTERVAL_MS = 120;

    // A-Taste: Klick mit Press + Release (mit kurzem Abstand), damit Buttons/Toggles
    // zuverlässig auslösen. GLFW kann selbst keine Klicks erzeugen, daher hier.
    private static boolean clickQueued = false;
    private static long releaseAfter = 0;

    private static final GLFWGamepadState GAMEPAD = GLFWGamepadState.create();

    private ControllerMode() {
    }

    /** Liest die Launcher-State-Datei einmalig beim Start. */
    public static void init() {
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

    /**
     * Liest jede Tick das angeschlossene Gamepad und steuert den virtuellen Cursor.
     * Aufruf aus {@code KollegenMod.onTick()}.
     */
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

        // Gibt es ein verbundenes Gamepad?
        boolean padOk = false;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid)
                    && GLFW.glfwJoystickIsGamepad(jid)
                    && GLFW.glfwGetGamepadState(jid, GAMEPAD)) {
                padOk = true;
                applyGamepad(mc, window);
                break;
            }
        }
        // Klick auslösen (Press + Release mit Abstand)
        processClick(mc, window);
        if (!padOk) return;
    }

    private static void applyGamepad(Minecraft mc, long window) {
        float lx = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        float ly = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        // Toter Bereich, damit Stick-Drift nicht den Cursor wandern lässt
        float dead = 0.18f;
        float dx = (Math.abs(lx) < dead) ? 0 : lx;
        float dy = (Math.abs(ly) < dead) ? 0 : ly;

        // D-Pad als zusätzliche (diskrete) Steuerung
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT) == GLFW.GLFW_PRESS) dx = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) == GLFW.GLFW_PRESS) dx = 1;
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP) == GLFW.GLFW_PRESS) dy = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW.GLFW_PRESS) dy = 1;

        if (dx != 0 || dy != 0) {
            // Normieren, damit diagonale Bewegungen nicht schneller sind
            double len = Math.hypot(dx, dy);
            double nx = dx / len, ny = dy / len;
            // Cursor-Geschwindigkeit an den aktuellen Screen-Faktor koppeln
            double speed = Math.max(8.0, mc.getWindow().getScreenWidth() / 32.0);
            cursorX += nx * speed;
            cursorY += ny * speed;

            int w = mc.getWindow().getScreenWidth();
            int h = mc.getWindow().getScreenHeight();
            cursorX = Math.max(0, Math.min(w - 1, cursorX));
            cursorY = Math.max(0, Math.min(h - 1, cursorY));

            // OS-Cursor bewegen → Minecraft's onMove (Hover) feuert über das
            // normale GLFW-Polling der nächsten Frames von selbst.
            try {
                GLFW.glfwSetCursorPos(window, cursorX, cursorY);
            } catch (Throwable ignored) {
            }

            // Wenn vertikale Stick-Bewegung vorhanden, scrollen (für Menüs)
            try {
                if (mc.screen != null) {
                    // GUI-Koordinaten berechnen (Skalierung wie in HudModule)
                    int sw = mc.getWindow().getScreenWidth();
                    int gw = mc.getWindow().getGuiScaledWidth();
                    double guiX = cursorX * gw / sw;
                    double guiY = cursorY * gw / sw;

                    long now = System.currentTimeMillis();
                    // kleines Rate-Limit, damit Scrolls nicht zu schnell auslösen
                    if (now - lastScrollAt >= SCROLL_INTERVAL_MS) {
                        // ly ist -1..1, scroll-Richtung invertieren für natürliches Verhalten
                        double amount = -ny; // pos = down
                        // Bei analogem Stick skaliert scroll-Geschwindigkeit
                        double scrollAmount = 0.0;
                        if (Math.abs(amount) > 0.2) {
                            scrollAmount = amount * 3.0; // empirischer Faktor
                        }
                        if (scrollAmount != 0.0) {
                            // Seit 1.20.5 hat Screen.mouseScrolled 4 Parameter:
                            // (x, y, horizontal, vertical). Nur vertikal scrollen.
                            mc.screen.mouseScrolled(guiX, guiY, 0.0, scrollAmount);
                            lastScrollAt = now;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // A/Cross → Primärklick planen; B/Square → Rechtsklick
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
            // OS-Cursor noch einmal exakt auf die virtuelle Position setzen,
            // damit der Klick an der richtigen Stelle ankommt.
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
