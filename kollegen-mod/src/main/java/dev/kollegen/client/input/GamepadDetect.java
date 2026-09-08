package dev.kollegen.client.input;

import dev.kollegen.client.KollegenMod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Gemeinsame Gamepad-Erkennung für Cursor- und Gameplay-Steuerung.
 * <p>
 * GLFW ordnet ein angeschlossenes Gerät nur dann als "Gamepad" ein
 * ({@code glfwJoystickIsGamepad()}), wenn ein passendes Layout aus der
 * GameControllerDB geladen ist. Ohne Mapping bleiben Deck-Controller (die
 * SteamInput als "Steam Virtual Gamepad" exportiert) unsichtbar – das war die
 * Ursache, dass weder der Cursor- noch der Gameplay-Modus griff.
 * <p>
 * Deshalb lädt dieser Helper einmalig die eingebettete
 * {@code /data/gamecontrollerdb.txt} (SDL-DB + Steam-Deck-Zeilen) in GLFW,
 * erkennt zusätzlich bekannte Deck-Controller auch ohne Mapping über die
 * Valve-Vendor-ID bzw. den Gerätenamen und loggt die gefundenen Joysticks für
 * die Diagnose auf dem SteamDeck.
 */
public final class GamepadDetect {

    /** SDL-Form-GUID erster 4 Hex-Bytes: Valve = 0x28DE. */
    private static final String VALVE_VENDOR = "03000000de28";

    private static boolean mappingsLoaded = false;
    private static boolean diagnosed = false;

    private GamepadDetect() {
    }

    /** Lädt die eingebettete GameControllerDB einmalig in GLFW (idempotent). */
    public static void ensureMappings() {
        if (mappingsLoaded) return;
        mappingsLoaded = true;
        try (InputStream in = GamepadDetect.class.getResourceAsStream("/data/gamecontrollerdb.txt")) {
            if (in == null) {
                KollegenMod.LOGGER.warn("[gamepad] Keine gamecontrollerdb.txt im Mod gefunden.");
                return;
            }
            String db = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ByteBuffer bb = ByteBuffer.wrap(db.getBytes(StandardCharsets.UTF_8));
            boolean ok = GLFW.glfwUpdateGamepadMappings(bb);
            KollegenMod.LOGGER.info("[gamepad] GameControllerDB geladen ({} Zeichen), GLFW akzeptiert: {}",
                    db.length(), ok);
        } catch (Throwable t) {
            KollegenMod.LOGGER.error("[gamepad] gamecontrollerdb laden fehlgeschlagen", t);
        }
    }

    /**
     * Findet den Index eines brauchbaren Gamepads (GLFW_JOSTICK_1 .. LAST) und
     * füllt bei Erfolg das übergebene {@link GLFWGamepadState}-Objekt.
     * <ul>
     *   <li>Zuerst der normale Pfad: {@code glfwJoystickIsGamepad()} == true.</li>
     *   <li>Fallback: ein verbundener Joystick mit Valve-Vendor-ID bzw.
     *       Steam-artigem Namen (auch ohne Mapping).</li>
     * </ul>
     *
     * @param state zu befüllender Gamepad-State (wird nur bei Erfolg geschrieben)
     * @return Joystick-Index oder -1
     */
    public static int scan(GLFWGamepadState state) {
        ensureMappings();
        diagnose();
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            if (GLFW.glfwJoystickIsGamepad(jid)) {
                if (state != null) GLFW.glfwGetGamepadState(jid, state);
                return jid;
            }
            if (isLikelyValve(jid)) {
                if (state != null) copyRawState(jid, state);
                return jid;
            }
        }
        return -1;
    }

    /**
     * Füllt den Gamepad-State aus den rohen Joystick-Achsen/-Buttons eines
     * Valve/Steam-Geräts, dessen Layout aus der SDL-DB bekannt ist:
     * axes: 0=leftx, 1=lefty, 2=lefttrigger, 3=rightx, 4=righty, 5=righttrigger
     * buttons (XInput-Standard): 0=a,1=b,2=x,3=y,4=lb,5=rb,6=back,7=start,
     * 8=leftstick, 9=rightstick; D-Pad über Hat0 (0.1=up,0.2=right,0.4=down,
     * 0.8=left) wird gerundet auf die kanonischen D-Pad-Buttons gemappt.
     */
    private static void copyRawState(int jid, GLFWGamepadState state) {
        try {
            FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
            ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
            FloatBuffer dstAxes = state.axes();
            ByteBuffer dstButtons = state.buttons();
            if (axes.limit() >= 6) {
                // Steam-Layout → GLFW-kanonisch (LeftX=0,LeftY=1,RightX=2,RightY=3,LT=4,RT=5)
                dstAxes.put(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, axes.get(0));
                dstAxes.put(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, axes.get(1));
                dstAxes.put(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, axes.get(3));
                dstAxes.put(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, axes.get(4));
                dstAxes.put(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, axes.get(2));
                dstAxes.put(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, axes.get(5));
            }
            if (buttons.limit() >= 10) {
                for (int i = 0; i < 10; i++) {
                    dstButtons.put(i, (byte) (buttons.get(i) == GLFW.GLFW_PRESS ? 1 : 0));
                }
            }
            // D-Pad aus Hat0 (nur wenn kein Axis/Button bereits gesetzt ist).
            ByteBuffer rawHats = GLFW.glfwGetJoystickHats(jid);
            if (rawHats.limit() > 0 && rawHats.get(0) != GLFW.GLFW_HAT_CENTERED) {
                int hat = rawHats.get(0);
                dstButtons.put(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP,
                        (byte) ((hat & GLFW.GLFW_HAT_UP) != 0 ? 1 : 0));
                dstButtons.put(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT,
                        (byte) ((hat & GLFW.GLFW_HAT_RIGHT) != 0 ? 1 : 0));
                dstButtons.put(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN,
                        (byte) ((hat & GLFW.GLFW_HAT_DOWN) != 0 ? 1 : 0));
                dstButtons.put(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT,
                        (byte) ((hat & GLFW.GLFW_HAT_LEFT) != 0 ? 1 : 0));
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isLikelyValve(int jid) {
        try {
            String guid = GLFW.glfwGetJoystickGUID(jid);
            if (guid != null && guid.startsWith(VALVE_VENDOR)) return true;
            String name = GLFW.glfwGetJoystickName(jid);
            if (name != null) {
                String n = name.toLowerCase();
                if (n.contains("steam") || n.contains("valve")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Einmalige Diagnose-Ausgabe aller sichtbaren Joysticks ins Log. */
    private static void diagnose() {
        if (diagnosed) return;
        diagnosed = true;
        StringBuilder sb = new StringBuilder("[gamepad] Joystick-Diagnose:");
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            String name = GLFW.glfwGetJoystickName(jid);
            String guid = GLFW.glfwGetJoystickGUID(jid);
            boolean isGamepad = GLFW.glfwJoystickIsGamepad(jid);
            sb.append("\n  #").append(jid)
                    .append(" present, name='").append(name)
                    .append("', guid=").append(guid)
                    .append(", isGamepad=").append(isGamepad)
                    .append(", axes=").append(GLFW.glfwGetJoystickAxes(jid).limit())
                    .append(", buttons=").append(GLFW.glfwGetJoystickButtons(jid).limit());
        }
        if (sb.indexOf("present") == -1) sb.append(" keiner sichtbar.");
        KollegenMod.LOGGER.info(sb.toString());
    }
}