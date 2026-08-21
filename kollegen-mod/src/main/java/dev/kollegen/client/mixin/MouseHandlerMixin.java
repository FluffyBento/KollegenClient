package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.ClickTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zählt Maus-Klicks für die CPS-Anzeige (Keystrokes/CPS-Module). Greift direkt
 * auf den GLFW-Callback des MouseHandler zu – ohne fabric-api.
 */
@Mixin(net.minecraft.client.MouseHandler.class)
public class MouseHandlerMixin {

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
    }
}
