package dev.kollegen.client.input;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mixin.ClientInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.util.ArrayList;
import java.util.List;


public final class GamepadInput {

    private static final GLFWGamepadState GAMEPAD = GLFWGamepadState.create();

    
    private static double sensScale = 0.6;

    
    private static boolean previousAttackHeld = false;
    private static boolean previousUseHeld = false;
    private static boolean previousHotbarLeftHeld = false;
    private static boolean previousHotbarRightHeld = false;
    private static boolean previousPerspectiveHeld = false;
    private static boolean previousInventoryHeld = false;
    private static boolean previousChatHeld = false;
    private static boolean previousPauseHeld = false;

    private static int selectedSlot = -1;
    private static boolean slotDirty = false;

    
    
    private static float moveX = 0f, moveY = 0f;

    
    private static boolean fwd, back, left, right;
    private static boolean jump, sneak, sprint;

    private static final float DEADZONE = 0.18f;
    private static final float BUTTON_THRESHOLD = 0.4f;

    private GamepadInput() {
    }

    
    public static boolean isActive(Minecraft mc) {
        if (!ControllerMode.isActive()) return false;
        return isAnyGamepadPresent();
    }

    
    public static boolean isAnyGamepadPresent() {
        return GamepadDetect.scan(GAMEPAD) != -1;
    }

    
    public static boolean takeOverMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return false;
        if (mc.screen != null) return false;
        if (!ControllerMode.isActive()) return false;
        return isAnyGamepadPresent();
    }

    
    public static void applyMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        LocalPlayer player = mc.player;
        if (!(player.input instanceof ClientInputAccessor)) return;

        readStick(DEADZONE);

        fwd = moveY < -BUTTON_THRESHOLD;
        back = moveY > BUTTON_THRESHOLD;
        left = moveX < -BUTTON_THRESHOLD;
        right = moveX > BUTTON_THRESHOLD;

        
        
        jump = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS;
        sneak = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS;
        sprint = isSprintPressed();

        Input keyPresses = new Input(fwd, back, left, right, jump, sneak, sprint);
        ((ClientInputAccessor) player.input).kollegen$setKeyPresses(keyPresses);

        
        
        float forwardImpulse = -moveY; 
        float strafeImpulse = moveX;
        ((ClientInputAccessor) player.input)
                .kollegen$setMoveVector(new Vec2(strafeImpulse, forwardImpulse));
    }

    private static boolean isSprintPressed() {
        
        return GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB) == GLFW.GLFW_PRESS;
    }

    
    private static void readStick(float dead) {
        float lx = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        float ly = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        moveX = (Math.abs(lx) < dead) ? 0 : (lx > 0 ? 1 : -1) * (Math.abs(lx));
        moveY = (Math.abs(ly) < dead) ? 0 : (ly > 0 ? 1 : -1) * (Math.abs(ly));
        
        float dx = 0, dy = 0;
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT) == GLFW.GLFW_PRESS) dx = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) == GLFW.GLFW_PRESS) dx = 1;
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP) == GLFW.GLFW_PRESS) dy = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW.GLFW_PRESS) dy = 1;
        if (dx != 0) moveX = dx;
        if (dy != 0) moveY = dy;
    }

    
    public static void onFrame(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!ControllerMode.isActive()) return;
        if (!isAnyGamepadPresent()) return;
        turnCamera(mc);
    }

    
    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; 
        if (!ControllerMode.isActive()) return;
        if (!isAnyGamepadPresent()) return;

        
        turnCamera(mc);

        
        
        boolean attackHeld = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.5f;
        boolean useHeld = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.5f;
        boolean hotbarLeft = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW.GLFW_PRESS;
        boolean hotbarRight = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) == GLFW.GLFW_PRESS;
        boolean perspective = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB) == GLFW.GLFW_PRESS;
        boolean inventory = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_X) == GLFW.GLFW_PRESS;
        boolean chat = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_BACK) == GLFW.GLFW_PRESS;
        boolean pause = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_START) == GLFW.GLFW_PRESS;

        handleAttack(mc, attackHeld);
        handleUse(mc, useHeld);
        handleHotbar(mc, hotbarLeft, hotbarRight);
        handlePerspective(mc, perspective);
        handleInventory(mc, inventory);
        handleChat(mc, chat);
        handlePause(mc, pause);

        previousAttackHeld = attackHeld;
        previousUseHeld = useHeld;
        previousHotbarLeftHeld = hotbarLeft;
        previousHotbarRightHeld = hotbarRight;
        previousPerspectiveHeld = perspective;
        previousInventoryHeld = inventory;
        previousChatHeld = chat;
        previousPauseHeld = pause;
    }

    private static void turnCamera(Minecraft mc) {
        try {
            float rx = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X);
            float ry = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
            if (Math.abs(rx) < DEADZONE) rx = 0;
            if (Math.abs(ry) < DEADZONE) ry = 0;
            if (rx == 0 && ry == 0) return;

            double sens = mc.options.sensitivity().get();
            sensScale = sens;
            
            
            
            
            double factor = 2.5;
            mc.player.turn(rx * sensScale * factor, -ry * sensScale * factor);
        } catch (Throwable ignored) {
        }
    }

    private static void handleAttack(Minecraft mc, boolean held) {
        
        
        
        try {
            if (held) ((dev.kollegen.client.mixin.MinecraftAccessor) mc).kollegen$startAttack();
        } catch (Throwable ignored) {
        }
    }

    private static void handleUse(Minecraft mc, boolean held) {
        
        
        boolean released = !held && previousUseHeld;
        try {
            if (held) ((dev.kollegen.client.mixin.MinecraftAccessor) mc).kollegen$startUseItem();
            else if (released) mc.player.stopUsingItem();
        } catch (Throwable ignored) {
        }
    }

    private static void handleHotbar(Minecraft mc, boolean left, boolean right) {
        boolean l = left && !previousHotbarLeftHeld;
        boolean r = right && !previousHotbarRightHeld;
        if (!l && !r) return;
        try {
            int slot = mc.player.getInventory().getSelectedSlot();
            if (l) slot = (slot - 1 + 9) % 9;
            if (r) slot = (slot + 1) % 9;
            mc.player.getInventory().setSelectedSlot(slot);
        } catch (Throwable ignored) {
        }
    }

    private static void handlePerspective(Minecraft mc, boolean held) {
        if (!(held && !previousPerspectiveHeld)) return;
        try {
            mc.options.setCameraType(mc.options.getCameraType().cycle());
        } catch (Throwable ignored) {
        }
    }

    private static void handleInventory(Minecraft mc, boolean held) {
        if (!(held && !previousInventoryHeld)) return;
        try {
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        } catch (Throwable ignored) {
        }
    }

    private static void handleChat(Minecraft mc, boolean held) {
        if (!(held && !previousChatHeld)) return;
        try {
            mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
        } catch (Throwable ignored) {
        }
    }

    private static void handlePause(Minecraft mc, boolean held) {
        if (!(held && !previousPauseHeld)) return;
        try {
            mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
        } catch (Throwable ignored) {
        }
    }
}