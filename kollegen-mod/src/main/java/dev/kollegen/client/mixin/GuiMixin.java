package dev.kollegen.client.mixin;

import dev.kollegen.client.hud.KollegenHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD-Render-Hook OHNE fabric-api: injiziert am Ende von {@code Gui.render},
 * um das Kollegen-HUD (Profil oben rechts, Freunde oben links) zu zeichnen.
 */
@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("RETURN"))
    private void kollegen_client$hud(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        KollegenHud.render(guiGraphics, mc);
    }
}
