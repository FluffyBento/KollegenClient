package dev.kollegen.client.input.sdl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLMouse;
import com.mojang.blaze3d.platform.InputConstants;

public final class GLFW {
    public static final int GLFW_PRESS = 1;
    public static final int GLFW_RELEASE = 0;
    public static final int GLFW_JOYSTICK_1 = 0;
    public static final int GLFW_JOYSTICK_LAST = 15;
    public static final int GLFW_GAMEPAD_BUTTON_A = 0;
    public static final int GLFW_GAMEPAD_BUTTON_B = 1;
    public static final int GLFW_GAMEPAD_BUTTON_X = 2;
    public static final int GLFW_GAMEPAD_BUTTON_Y = 3;
    public static final int GLFW_GAMEPAD_BUTTON_LEFT_BUMPER = 4;
    public static final int GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER = 5;
    public static final int GLFW_GAMEPAD_BUTTON_BACK = 6;
    public static final int GLFW_GAMEPAD_BUTTON_START = 7;
    public static final int GLFW_GAMEPAD_BUTTON_GUIDE = 8;
    public static final int GLFW_GAMEPAD_BUTTON_LEFT_THUMB = 9;
    public static final int GLFW_GAMEPAD_BUTTON_RIGHT_THUMB = 10;
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_UP = 11;
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_RIGHT = 12;
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_DOWN = 13;
    public static final int GLFW_GAMEPAD_BUTTON_DPAD_LEFT = 14;
    public static final int GLFW_GAMEPAD_AXIS_LEFT_X = 0;
    public static final int GLFW_GAMEPAD_AXIS_LEFT_Y = 1;
    public static final int GLFW_GAMEPAD_AXIS_RIGHT_X = 2;
    public static final int GLFW_GAMEPAD_AXIS_RIGHT_Y = 3;
    public static final int GLFW_GAMEPAD_AXIS_LEFT_TRIGGER = 4;
    public static final int GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER = 5;
    public static final int GLFW_HAT_CENTERED = 0;
    public static final int GLFW_HAT_UP = 1;
    public static final int GLFW_HAT_RIGHT = 2;
    public static final int GLFW_HAT_DOWN = 4;
    public static final int GLFW_HAT_LEFT = 8;

    public static final int GLFW_KEY_SPACE = 32;
    public static final int GLFW_KEY_0 = 48;
    public static final int GLFW_KEY_9 = 57;
    public static final int GLFW_KEY_A = 65;
    public static final int GLFW_KEY_Z = 90;
    public static final int GLFW_KEY_ENTER = 257;
    public static final int GLFW_KEY_F1 = 290;
    public static final int GLFW_KEY_F25 = 314;
    public static final int GLFW_KEY_KP_ENTER = 335;
    public static final int GLFW_KEY_LEFT_SHIFT = 340;
    public static final int GLFW_KEY_LEFT_CONTROL = 341;
    public static final int GLFW_KEY_LEFT_ALT = 342;
    public static final int GLFW_KEY_RIGHT_SHIFT = 344;
    public static final int GLFW_KEY_RIGHT_CONTROL = 345;
    public static final int GLFW_KEY_RIGHT_ALT = 346;

    public static final class GLFWGamepadState {
        private final ByteBuffer buttons = ByteBuffer.allocateDirect(15).order(ByteOrder.nativeOrder());
        private final FloatBuffer axes = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();

        public ByteBuffer buttons() { return buttons; }
        public byte buttons(int b) { return buttons.get(b); }
        public FloatBuffer axes() { return axes; }
        public float axes(int a) { return axes.get(a); }
        public static GLFWGamepadState create() { return new GLFWGamepadState(); }
    }

    private static final FloatBuffer MOUSE_X = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final FloatBuffer MOUSE_Y = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asFloatBuffer();

    public static synchronized void glfwGetCursorPos(long window, double[] x, double[] y) {
        try {
            MOUSE_X.clear();
            MOUSE_Y.clear();
            SDLMouse.SDL_GetMouseState(MOUSE_X, MOUSE_Y);
            if (x != null && x.length > 0) x[0] = MOUSE_X.get(0);
            if (y != null && y.length > 0) y[0] = MOUSE_Y.get(0);
        } catch (Throwable ignored) {}
    }

    public static void glfwSetCursorPos(long window, double x, double y) {
        try {
            SDLMouse.SDL_WarpMouseInWindow(window, (float) x, (float) y);
        } catch (Throwable ignored) {}
    }

    public static boolean glfwUpdateGamepadMappings(ByteBuffer bb) {
        try {
            return SDLGamepad.SDL_AddGamepadMapping(bb) >= 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean glfwJoystickPresent(int jid) {
        try {
            java.nio.IntBuffer pads = SDLGamepad.SDL_GetGamepads();
            return pads != null && jid >= 0 && jid < pads.limit();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean glfwJoystickIsGamepad(int jid) {
        return glfwJoystickPresent(jid);
    }

    public static String glfwGetJoystickName(int jid) {
        try {
            java.nio.IntBuffer pads = SDLGamepad.SDL_GetGamepads();
            if (pads != null && jid >= 0 && jid < pads.limit()) {
                return SDLGamepad.SDL_GetGamepadNameForID(pads.get(jid));
            }
        } catch (Throwable ignored) {}
        return "Gamepad " + jid;
    }

    public static String glfwGetJoystickGUID(int jid) {
        return "";
    }

    public static FloatBuffer glfwGetJoystickAxes(int jid) {
        return ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public static ByteBuffer glfwGetJoystickButtons(int jid) {
        return ByteBuffer.allocateDirect(15).order(ByteOrder.nativeOrder());
    }

    public static ByteBuffer glfwGetJoystickHats(int jid) {
        return ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
    }

    public static void glfwGetGamepadState(int jid, GLFWGamepadState state) {
        if (state == null) return;
        try {
            java.nio.IntBuffer pads = SDLGamepad.SDL_GetGamepads();
            if (pads == null || jid < 0 || jid >= pads.limit()) return;
            long pad = SDLGamepad.SDL_OpenGamepad(pads.get(jid));
            if (pad == 0) return;

            ByteBuffer b = state.buttons();
            b.put(GLFW_GAMEPAD_BUTTON_A, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_B, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_X, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_WEST) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_Y, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_BACK, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_BACK) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_START, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_START) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_GUIDE, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_GUIDE) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_LEFT_THUMB, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_RIGHT_THUMB, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_DPAD_UP, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_DPAD_DOWN, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN) ? 1 : 0));
            b.put(GLFW_GAMEPAD_BUTTON_DPAD_LEFT, (byte) (SDLGamepad.SDL_GetGamepadButton(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT) ? 1 : 0));

            FloatBuffer a = state.axes();
            a.put(GLFW_GAMEPAD_AXIS_LEFT_X, SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX) / 32767.0f);
            a.put(GLFW_GAMEPAD_AXIS_LEFT_Y, SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY) / 32767.0f);
            a.put(GLFW_GAMEPAD_AXIS_RIGHT_X, SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX) / 32767.0f);
            a.put(GLFW_GAMEPAD_AXIS_RIGHT_Y, SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY) / 32767.0f);
            a.put(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, Math.max(0.0f, SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER) / 32767.0f));
            a.put(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, Math.max(0.0f, SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) / 32767.0f));
        } catch (Throwable ignored) {}
    }

    public static int glfwGetKey(long window, int key) {
        try {
            int k = key;
            if (key == GLFW_KEY_RIGHT_SHIFT || key == InputConstants.KEY_RSHIFT) k = InputConstants.KEY_RSHIFT;
            else if (key == GLFW_KEY_LEFT_SHIFT || key == InputConstants.KEY_LSHIFT) k = InputConstants.KEY_LSHIFT;
            else if (key == GLFW_KEY_RIGHT_CONTROL || key == InputConstants.KEY_RCONTROL) k = InputConstants.KEY_RCONTROL;
            else if (key == GLFW_KEY_LEFT_CONTROL || key == InputConstants.KEY_LCONTROL) k = InputConstants.KEY_LCONTROL;
            else if (key == GLFW_KEY_SPACE || key == InputConstants.KEY_SPACE) k = InputConstants.KEY_SPACE;
            return InputConstants.isKeyDown(k) ? GLFW_PRESS : GLFW_RELEASE;
        } catch (Throwable t) {
            return GLFW_RELEASE;
        }
    }

    public static String glfwGetKeyName(int key, int scancode) {
        try {
            return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
        } catch (Throwable t) {
            return null;
        }
    }
}
