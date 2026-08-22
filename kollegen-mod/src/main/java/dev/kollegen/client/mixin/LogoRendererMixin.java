package dev.kollegen.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.kollegen.client.ui.LogoDraw;

/**
 * Ersetzt das vanilla Minecraft-Logo im Hauptmenue durch das eigene
 * assets/kollegen/logo.png (Titelbildschirm-Overlay). Die Textur wird
 * seitenveraeltniskonform auf die gewuenschte Anzeigebreite skaliert.
 */
@Mixin(net.minecraft.client.gui.components.LogoRenderer.class)
public class LogoRendererMixin {

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$renderLogo(GuiGraphics gui, int x, float alpha, int y, CallbackInfo ci) {
        ci.cancel();
        int targetW = Math.min(384, gui.guiWidth() - 20);
        int drawX = (gui.guiWidth() - targetW) / 2;
        LogoDraw.draw(gui, drawX, 16, targetW);
    }
}
