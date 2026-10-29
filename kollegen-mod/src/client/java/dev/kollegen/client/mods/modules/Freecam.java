package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.server.ServerRestrictions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class Freecam extends Module {
    public static Freecam INSTANCE;

    public static double x, y, z;
    public static double prevX, prevY, prevZ;
    public static float yaw, pitch;
    public static float prevYaw, prevPitch;

    public final SliderSetting speed = new SliderSetting(
            "Geschwindigkeit",
            "Bewegungsgeschwindigkeit der freien Kamera.",
            1.0, 0.1, 5.0, 0.1
    );

    public final BooleanSetting sprintBoost = new BooleanSetting(
            "Sprint-Boost",
            "Schnellere Bewegung beim Halten der Sprint-Taste.",
            true
    );

    public final BooleanSetting freezePlayer = new BooleanSetting(
            "Spieler einfrieren",
            "Verhindert Spielerbewegungen während Freecam aktiv ist.",
            true
    );

    private static long lastWarnTime = 0;

    public Freecam() {
        super("freecam", "Freecam", "Erlaubt freies Bewegen der Kamera unabhängig vom Spieler.", Category.VISUAL);
        INSTANCE = this;
        this.add(speed);
        this.add(sprintBoost);
        this.add(freezePlayer);
    }

    @Override
    public boolean isLocked() {
        return super.isLocked() || ServerRestrictions.isFreecamBlockedCurrent();
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.enabled && !INSTANCE.isLocked();
    }

    public static boolean isFreezePlayer() {
        return isActive() && INSTANCE.freezePlayer.value;
    }

    public static void notifyLocked() {
        long now = System.currentTimeMillis();
        if (now - lastWarnTime < 2000) return;
        lastWarnTime = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendOverlayMessage(
                    Component.literal("§c[Kollegen Client] Freecam ist auf diesem Server nicht gestattet.")
            );
        }
    }

    @Override
    public void onEnable() {
        if (isLocked()) {
            this.enabled = false;
            notifyLocked();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            this.enabled = false;
            return;
        }

        x = mc.player.getX();
        y = mc.player.getEyeY();
        z = mc.player.getZ();
        prevX = x;
        prevY = y;
        prevZ = z;

        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        prevYaw = yaw;
        prevPitch = pitch;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onKey() {
        if (isLocked()) {
            if (this.enabled) {
                this.enabled = false;
                onDisable();
            }
            notifyLocked();
            return;
        }
        super.onKey();
    }

    @Override
    public void onTick() {
        if (isLocked()) {
            if (this.enabled) {
                this.enabled = false;
                onDisable();
                notifyLocked();
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            if (this.enabled) {
                this.enabled = false;
                onDisable();
            }
            return;
        }

        prevX = x;
        prevY = y;
        prevZ = z;
        prevYaw = yaw;
        prevPitch = pitch;

        if (mc.gui != null && mc.gui.screen() != null) {
            return;
        }

        double moveSpeed = speed.value * 0.5;
        if (sprintBoost.value && mc.options.keySprint.isDown()) {
            moveSpeed *= 2.5;
        }

        double forward = 0;
        double strafe = 0;
        double vertical = 0;

        if (mc.options.keyUp.isDown()) forward += 1;
        if (mc.options.keyDown.isDown()) forward -= 1;
        if (mc.options.keyLeft.isDown()) strafe += 1;
        if (mc.options.keyRight.isDown()) strafe -= 1;
        if (mc.options.keyJump.isDown()) vertical += 1;
        if (mc.options.keyShift.isDown()) vertical -= 1;

        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len > 0.0001) {
            forward /= len;
            strafe /= len;
        }

        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double dx = (-sin * forward + cos * strafe) * moveSpeed;
        double dz = (cos * forward + sin * strafe) * moveSpeed;
        double dy = vertical * moveSpeed;

        x += dx;
        y += dy;
        z += dz;
    }

    public static void onTurn(double dx, double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
        double f = sensitivity * sensitivity * sensitivity * 8.0;
        double dYaw = dx * f;
        double dPitch = dy * f;
        if (mc.options.invertMouseX().get()) dYaw = -dYaw;
        if (mc.options.invertMouseY().get()) dPitch = -dPitch;

        yaw = (float) (yaw + dYaw * 0.15);
        pitch = (float) (pitch + dPitch * 0.15);
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    public static double getX(float pt) {
        return prevX + (x - prevX) * pt;
    }

    public static double getY(float pt) {
        return prevY + (y - prevY) * pt;
    }

    public static double getZ(float pt) {
        return prevZ + (z - prevZ) * pt;
    }

    public static float getYaw(float pt) {
        return yaw;
    }

    public static float getPitch(float pt) {
        return pitch;
    }
}
