package dev.kollegen.client.scenery;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;

public final class F2HoldHandler {

    private static final long HOLD_THRESHOLD_MS = 350L;
    private static boolean f2Down = false;
    private static long f2PressTime = 0L;
    private static boolean menuOpened = false;

    private F2HoldHandler() {
    }

    public static boolean isF2Down() {
        return f2Down;
    }

    public static boolean onKey(int action, KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return false;
        }

        InputConstants.Key key = InputConstants.getKey(event);
        boolean matchesF2 = (mc.options.keyScreenshot != null && mc.options.keyScreenshot.matches(key))
                || event.key() == InputConstants.KEY_F2;

        if (!matchesF2) {
            return false;
        }

        if (action == 1) {
            f2Down = true;
            f2PressTime = System.currentTimeMillis();
            menuOpened = false;
            return true;
        } else if (action == 0) {
            if (f2Down) {
                f2Down = false;
                if (!menuOpened) {
                    Screenshot.grab(mc, false);
                } else if (mc.gui != null && mc.gui.screen() instanceof F2ActionScreen screen) {
                    screen.onF2Released();
                }
                return true;
            }
        } else if (action == 2) {
            if (f2Down) {
                return true;
            }
        }

        return false;
    }

    public static void onRenderTick() {
        if (!f2Down || menuOpened) {
            return;
        }

        long elapsed = System.currentTimeMillis() - f2PressTime;
        if (elapsed >= HOLD_THRESHOLD_MS) {
            menuOpened = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.setScreenAndShow(new F2ActionScreen());
            }
        }
    }

    public static void reset() {
        f2Down = false;
        f2PressTime = 0L;
        menuOpened = false;
    }
}
