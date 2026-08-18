package dev.kollegen.client.mixin;

import dev.kollegen.client.hud.KollegenHud;
import dev.kollegen.client.menu.KollegenProfileScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Öffnet den Kollegen-Profil-Screen, wenn man im Spiel (kein Menü offen) auf
 * das Profil-Widget oben rechts klickt. OHNE fabric-api.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"))
    private void kollegen_client$onClick(long window, int button, int action, int modifiers, CallbackInfo ci) {
        // Nur linke Maustaste (0) und nur beim Drücken (action == 1).
        if (action != 1 || button != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != null) return;

        double mx = mc.mouseHandler.xpos();
        double my = mc.mouseHandler.ypos();

        if (mx >= KollegenHud.profileX && mx <= KollegenHud.profileX + KollegenHud.profileW
                && my >= KollegenHud.profileY && my <= KollegenHud.profileY + KollegenHud.profileH) {
            mc.setScreen(new KollegenProfileScreen());
        }
    }
}
