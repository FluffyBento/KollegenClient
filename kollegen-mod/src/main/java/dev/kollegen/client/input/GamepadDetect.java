package dev.kollegen.client.input;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
     *   <li>Letzter Fallback: der vom Launcher gespiegelte Forward-State
     *       ({@code mods/.kollegen-gamepad}). Im Steam-Game-Mode sieht der
     *       Minecraft-Kindprozess sein Gerät nicht, weil Steam Input den
     *       virtuellen Controller an den Steam-registrierten Launcher routet.
     *       Der Launcher liest ihn dort und schreibt ihn als JSON – dieser
     *       Zustand wird hier in denselben {@link GLFWGamepadState} eingelesen
     *       (liefert dann ein fiktives Joystick-Ergebnis).</li>
     * </ul>
     *
     * @param state zu befüllender Gamepad-State (wird nur bei Erfolg geschrieben)
     * @return Joystick-Index, das Fiktiv-Ergebnis {@link #FORWARDED} oder -1
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
        if (state != null && readForwarded(state)) return FORWARDED;
        return -1;
    }

    /** Fiktiver Rückgabewert, wenn der gespiegelte Launcher-Zustand benutzt wird. */
    public static final int FORWARDED = -2;

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

    /**
     * Liest den vom Launcher gespiegelten Forward-State
     * ({@code mods/.kollegen-gamepad}) und füllt damit das übergebene
     * {@link GLFWGamepadState}-Objekt in GLFW-kanonischem Layout.
     * Die Daten kommen vom Steam-registrierten Launcher-Prozess, weil Steam
     * Input im Game Mode den virtuellen Controller nur dorthin routet – der
     * Minecraft-Kindprozess sieht sein Gerät nicht, GLFW findet also nichts.
     * <p>
     * Das JSON hat folgendes Format (vom Launcher, {@code forward_gamepad_json}):
     * <pre>
     * {"present":true,"t":&lt;epoch-ms&gt;,
     *  "axes":[LeftX,LeftY,RightX,RightY,LT,RT],
     *  "buttons":[15 Werte in GLFW-Gamepad-Button-Reihenfolge]}
     * </pre>
     *
     * @param state zu befüllender Gamepad-State (wird nur bei Erfolg geschrieben)
     * @return true, wenn der Forward-State frisch gelesen werden konnte
     */
    private static boolean readForwarded(GLFWGamepadState state) {
        try {
            Path p = FabricLoader.getInstance().getGameDir()
                    .resolve("mods").resolve(".kollegen-gamepad");
            if (!Files.exists(p)) return false;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            if (!o.has("present") || !o.get("present").getAsBoolean()) return false;
            long t = o.get("t").getAsLong();
            // Nur frische Daten nutzen (Launcher schreibt im 16ms-Takt); abgelaufene
            // States (Launcher geschlossen, Controller abgesteckt) ignorieren.
            if (System.currentTimeMillis() - t > 1500) return false;

            JsonArray axes = o.get("axes").getAsJsonArray();
            JsonArray buttons = o.get("buttons").getAsJsonArray();
            FloatBuffer dstAxes = state.axes();
            ByteBuffer dstButtons = state.buttons();
            for (int i = 0; i < 6 && i < axes.size(); i++) {
                dstAxes.put(i, axes.get(i).getAsFloat());
            }
            for (int i = 0; i < 15 && i < buttons.size(); i++) {
                dstButtons.put(i, buttons.get(i).getAsByte());
            }
            if (!forwardedLogged) {
                forwardedLogged = true;
                KollegenMod.LOGGER.info("[gamepad] Nutze Forward-Gamepad des Launchers (.kollegen-gamepad).");
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean forwardedLogged = false;

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