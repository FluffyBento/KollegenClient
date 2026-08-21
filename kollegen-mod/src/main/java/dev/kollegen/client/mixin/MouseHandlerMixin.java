package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.ClickTracker;
import dev.kollegen.client.mods.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zählt Maus-Klicks für die CPS-Anzeige (Keystrokes/CPS-Module) und steuert
 * das Verschieben von HUD-Modulen per Drag (sofern das Modul "Verschieben"
 * aktiviert ist und man sich nicht in einem Screen befindet).
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    /** Speichert die Maus-Position (Roh-Pixel) für die GUI-Skalierung. */
    private static double kollegen$cursorRawX = 0, kollegen$cursorRawY = 0;

    @Inject(method = "method_22686", at = @At("HEAD"))
    private void kollegen_client$cursor(long window, double x, double y, CallbackInfo ci) {
        kollegen$cursorRawX = x;
        kollegen$cursorRawY = y;
        Minecraft mc = Minecraft.getInstance();
        try {
            int sw = mc.getWindow().getScreenWidth();
            int gw = mc.getWindow().getGuiScaledWidth();
            HudModule.cursorX = x * gw / sw;
            HudModule.cursorY = y * gw / sw;
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "method_22684", at = @At("HEAD"))
    private void kollegen_client$cps(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != 0 && action != 1) return;
        boolean press = action == 1;
        if (button == 0) {
            if (press) ClickTracker.pressLeft();
            else ClickTracker.releaseLeft();
        } else if (button == 1) {
            if (press) ClickTracker.pressRight();
            else ClickTracker.releaseRight();
        }

        // HUD-Drag (nur links, nur im Spiel, nicht in Screens)
        if (button == 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                if (press) {
                    HudModule hm = HudModule.moduleAt(HudModule.cursorX, HudModule.cursorY);
                    if (hm != null) {
                        HudModule.dragging = hm;
                        HudModule.dragOffX = (int) HudModule.cursorX - (int) hm.offsetX.value;
                        HudModule.dragOffY = (int) HudModule.cursorY - (int) hm.offsetY.value;
                    }
                } else {
                    HudModule.dragging = null;
                }
            }
        }
    }
}
