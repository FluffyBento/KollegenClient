package dev.kollegen.client.mixin;

import dev.kollegen.client.hud.KollegenHud;
import dev.kollegen.client.menu.KollegenFriendsScreen;
import dev.kollegen.client.menu.KollegenProfileScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Öffnet Kollegen-Screens, wenn man im Spiel (kein Menü offen) auf die HUD-
 * Widgets klickt. OHNE fabric-api.
 *  - Profil-Widget oben rechts  -> Profil-Screen
 *  - Freundes-Widget oben links -> Freundesliste (mit „Joinen")
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
            return;
        }
        if (mx >= KollegenHud.friendsX && mx <= KollegenHud.friendsX + KollegenHud.friendsW
                && my >= KollegenHud.friendsY && my <= KollegenHud.friendsY + KollegenHud.friendsH) {
            mc.setScreen(new KollegenFriendsScreen());
        }
    }
}
