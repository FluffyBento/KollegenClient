package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.KollegenSocialScreen;
import dev.kollegen.client.menu.SocialButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Globaler Maus-Fang (GLFW-Callback). Öffnet das Social-Menü, wenn auf dem
 * Startbildschirm oder im Pausenmenü der „Freunde"-Button geklickt wird.
 * Da TitleScreen/PauseScreen mouseClicked nicht überschreiben (Interface-Default),
 * ist dieser zentrale Punkt der zuverlässige Weg – ganz ohne fabric-api.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "method_22684", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$social(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button != 0 || action != 1) return; // nur Linksklick (Press)
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (s instanceof TitleScreen || s instanceof PauseScreen) {
            MouseHandler mh = mc.mouseHandler;
            if (SocialButton.hit(mc, mh.xpos(), mh.ypos())) {
                mc.setScreen(new KollegenSocialScreen());
                ci.cancel();
            }
        }
    }
}
