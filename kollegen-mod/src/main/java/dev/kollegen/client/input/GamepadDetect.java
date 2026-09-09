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


public final class GamepadDetect {

    
    private static final String VALVE_VENDOR = "03000000de28";

    private static boolean mappingsLoaded = false;
    private static boolean diagnosed = false;

    private GamepadDetect() {
    }

    
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

    
    public static final int FORWARDED = -2;

    
    private static void copyRawState(int jid, GLFWGamepadState state) {
        try {
            FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
            ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
            FloatBuffer dstAxes = state.axes();
            ByteBuffer dstButtons = state.buttons();
            if (axes.limit() >= 6) {
                
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

    
    private static boolean readForwarded(GLFWGamepadState state) {
        try {
            Path p = FabricLoader.getInstance().getGameDir()
                    .resolve("mods").resolve(".kollegen-gamepad");
            if (!Files.exists(p)) return false;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            if (!o.has("present") || !o.get("present").getAsBoolean()) return false;
            long t = o.get("t").getAsLong();
            
            
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