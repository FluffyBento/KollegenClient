package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.Minecraft;

public final class Gameplay {

    private Gameplay() {
    }

    public static void register() {
        ModuleManager.register(new AutoSprint());
        ModuleManager.register(new AutoJump());
        ModuleManager.register(new ToggleSprint());
        ModuleManager.register(new ToggleSneak());
        ModuleManager.register(new SafeWalk());
        ModuleManager.register(new AutoWalk());
    }

    private static class AutoSprint extends Module {
        AutoSprint() {
            super("autosprint", "Auto-Sprint", "Sprintet automatisch, sobald man vorwärts läuft.", Category.GAMEPLAY);
        }

        @Override
        public void onTick() {
            if (mc.player != null && !mc.player.isCrouching()
                    && mc.options.keyUp.isDown() && !mc.player.isSprinting()) {
                mc.player.setSprinting(true);
            }
        }
    }

    private static class AutoJump extends Module {
        AutoJump() {
            super("autojump", "Auto-Jump", "Springt automatisch über Blöcke (wie die Option).", Category.GAMEPLAY);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) mc.options.autoJump().set(true);
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.autoJump().set(false);
        }
    }

    private static class ToggleSprint extends Module {
        private final KeybindSetting key = new KeybindSetting("Taste", "Schaltet Dauersprint um.");
        private boolean toggled = false;

        ToggleSprint() {
            super("togglesprint", "Toggle-Sprint", "Taste schaltet permanenten Sprint an/aus.", Category.GAMEPLAY);
            add(key);
        }

        @Override
        public void onKey() {
            toggled = !toggled;
        }

        @Override
        public void onTick() {
            if (mc.player != null && toggled && !mc.player.isCrouching()) {
                mc.player.setSprinting(true);
            }
        }

        @Override
        public void onDisable() {
            toggled = false;
        }
    }

    private static class ToggleSneak extends Module {
        private final KeybindSetting key = new KeybindSetting("Taste", "Schaltet Dauerschleichen um.");
        private boolean toggled = false;

        ToggleSneak() {
            super("togglesneak", "Toggle-Schleichen", "Taste schaltet permanentes Schleichen an/aus.", Category.GAMEPLAY);
            add(key);
        }

        @Override
        public void onKey() {
            toggled = !toggled;
            if (mc.player != null) mc.options.keyShift.setDown(toggled);
        }

        @Override
        public void onDisable() {
            toggled = false;
            if (mc.player != null) mc.options.keyShift.setDown(false);
        }
    }

    private static class SafeWalk extends Module {
        SafeWalk() {
            super("safewalk", "Safe Walk", "Schleicht automatisch am Rand von Klippen.", Category.GAMEPLAY);
            risk = "Nicht auf Servern mit Anti-Cheat nutzen (Kick-Risiko)!";
        }

        @Override
        public void onTick() {
            if (mc.player == null || !mc.player.onGround()) return;
            try {
                double x = mc.player.getX(), z = mc.player.getZ();
                float yaw = (float) Math.toRadians(mc.player.getYRot());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);
                int bx = (int) Math.floor(x + fx * 0.6);
                int bz = (int) Math.floor(z + fz * 0.6);
                int by = (int) Math.floor(mc.player.getY() - 0.1);
                var state = mc.level.getBlockState(new net.minecraft.core.BlockPos(bx, by, bz));
                var below = mc.level.getBlockState(new net.minecraft.core.BlockPos(bx, by - 1, bz));
                boolean edge = state.isAir() && !below.isAir();
                boolean moving = mc.options.keyUp.isDown() || mc.options.keyLeft.isDown()
                        || mc.options.keyRight.isDown() || mc.options.keyDown.isDown();
                if (edge && moving && !mc.player.isCrouching()) {
                    mc.options.keyShift.setDown(true);
                } else if (mc.player.isShiftKeyDown() && !mc.player.isCrouching()) {
                    mc.options.keyShift.setDown(false);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static class AutoWalk extends Module {
        AutoWalk() {
            super("autowalk", "Auto-Walk", "Läuft dauerhaft vorwärts.", Category.GAMEPLAY);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) mc.options.keyUp.setDown(true);
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.keyUp.setDown(false);
        }
    }
}
